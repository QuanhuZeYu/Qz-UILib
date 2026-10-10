package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;

/** 共享CPU内容owner。worker只写有界完成入口；宿主线程drain才投各domain，绝不从worker运行direct域。
 * 缓存、排队和实际RGBA总字节均有界。活跃消费者超预算明确失败，不静默删除或每帧重试。
 */
public final class SharedImageService implements ImageService, AutoCloseable {
    public static final class Budget {
        public final int workers, queue, entries, completions;
        public final long bytes;

        public Budget(int workers, int queue, int entries, int completions, long bytes) {
            if (workers < 1 || queue < 1 || entries < 1 || completions < 1 || bytes < 4)
                throw new IllegalArgumentException("无效图像预算");
            this.workers = workers;
            this.queue = queue;
            this.entries = entries;
            this.completions = completions;
            this.bytes = bytes;
        }

        public static Budget defaults() {
            return new Budget(2, 32, 128, 16, 64L * 1024 * 1024);
        }
    }

    private final Thread host = Thread.currentThread();
    private final ImageLoader loader;
    private final Budget budget;
    private final ThreadPoolExecutor workers;
    private final LinkedHashMap<ImageSource, Entry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<Entry, ImageState> completed = new LinkedHashMap<>();
    private long request, bytes;
    private boolean closed;

    private static final class Entry {
        final ImageSource source;
        final List<Subscription> subscriptions = new ArrayList<>();
        ImageState state;
        long request;
        boolean pending;
        Future<?> task;
        ImageLoader.Task snapshot;

        Entry(ImageSource source) {
            this.source = source;
        }
    }

    private final class Subscription implements Lease {
        final Entry entry;
        final ReactiveDomain domain;
        final Signal<ImageState> state;
        volatile boolean alive = true;

        Subscription(Entry entry, ReactiveDomain domain, ImageState initial) {
            this.entry = entry;
            this.domain = domain;
            state = domain.signal(initial);
        }

        @Override
        public ReadableSignal<ImageState> state() {
            domain.checkAccess();
            return state;
        }

        void publish(ImageState next) {
            domain.enqueue(
                    () -> {
                        synchronized (SharedImageService.this) {
                            if (!closed
                                    && alive
                                    && entry.request == next.request
                                    && entry.state == next) state.set(next);
                        }
                    });
        }

        @Override
        public void close() {
            domain.checkAccess();
            synchronized (SharedImageService.this) {
                if (!alive) return;
                alive = false;
                if (entry != null) {
                    entry.subscriptions.remove(this);
                    if (entry.subscriptions.isEmpty()
                            && (entry.state.status == ImageState.Status.LOADING
                                    || entry.source.kind == ImageSource.Kind.BITMAP)) {
                        RuntimeException failure = cancel(entry);
                        if (entry.state.content != null) bytes -= entry.state.content.bytes();
                        entries.remove(entry.source);
                        if (failure != null) throw failure;
                    }
                }
            }
        }
    }

    public SharedImageService(ImageLoader loader, Budget budget) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.budget = Objects.requireNonNull(budget, "budget");
        workers =
                new ThreadPoolExecutor(
                        budget.workers,
                        budget.workers,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(budget.queue),
                        r -> {
                            Thread t = new Thread(r, "uilib-image-loader");
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("图像服务已关闭");
    }

    private void checkHost() {
        if (Thread.currentThread() != host) throw new IllegalStateException("图像资源边界仅限宿主线程");
    }

    @Override
    public synchronized Lease acquire(ImageSource source, ReactiveDomain domain) {
        domain.checkWrite();
        checkOpen();
        Objects.requireNonNull(source, "source");
        Entry entry = entries.get(source);
        if (entry == null) {
            while (entries.size() >= budget.entries && evict()) {}
            if (entries.size() >= budget.entries)
                return new Subscription(
                        null,
                        domain,
                        ImageState.failure(source, ++request, false, "budget", "共享图像条目预算已满"));
            entry = new Entry(source);
            entries.put(source, entry);
            restart(entry);
        }
        Subscription subscription = new Subscription(entry, domain, entry.state);
        entry.subscriptions.add(subscription);
        return subscription;
    }

    private RuntimeException cancel(Entry entry) {
        RuntimeException failure = null;
        ImageLoader.Task snapshot = entry.snapshot;
        entry.snapshot = null;
        if (snapshot != null) {
            try {
                snapshot.close();
            } catch (Exception cleanup) {
                failure = new IllegalStateException("资源快照释放失败: " + entry.source, cleanup);
            }
        }
        if (entry.task != null) {
            try {
                entry.task.cancel(true);
            } catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            } finally {
                entry.task = null;
                workers.purge();
            }
        }
        ImageState ready = completed.remove(entry);
        if (ready != null && ready.content != null) bytes -= ready.content.bytes();
        entry.pending = false;
        return failure;
    }

    private void restart(Entry entry) {
        RuntimeException cleanup = cancel(entry);
        if (entry.state != null && entry.state.content != null)
            bytes -= entry.state.content.bytes();
        entry.request = ++request;
        entry.state =
                cleanup == null
                        ? ImageState.loading(entry.source, entry.request)
                        : ImageState.failure(
                                entry.source, entry.request, false, "cleanup", cleanup.toString());
        entry.pending = cleanup == null;
    }

    @Override
    public void reload(ImageSource source) {
        List<Subscription> subscribers;
        ImageState next;
        synchronized (this) {
            checkOpen();
            Entry entry = entries.get(source);
            if (entry == null) return;
            if (entry.subscriptions.isEmpty()) {
                RuntimeException cleanup = cancel(entry);
                if (entry.state.content != null) bytes -= entry.state.content.bytes();
                entries.remove(source);
                if (cleanup != null) System.err.println(cleanup);
                return;
            }
            restart(entry);
            next = entry.state;
            subscribers = new ArrayList<>(entry.subscriptions);
        }
        for (Subscription s : subscribers) s.publish(next);
    }

    /** 资源包换代只重启资源依赖；HTTP/调用方独立位图不随资源包取消。 */
    public void resourcesReloaded() {
        List<ImageSource> sources = new ArrayList<>();
        synchronized (this) {
            checkOpen();
            for (Entry e : entries.values())
                if (e.source.kind == ImageSource.Kind.RESOURCE
                        || e.source.kind == ImageSource.Kind.ITEM) sources.add(e.source);
        }
        for (ImageSource s : sources) reload(s);
    }

    private boolean evict() {
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.subscriptions.isEmpty()) {
                RuntimeException cleanup = cancel(e);
                if (e.state.content != null) bytes -= e.state.content.bytes();
                it.remove();
                if (cleanup != null) System.err.println(cleanup);
                return true;
            }
        }
        return false;
    }

    private void complete(Entry entry, long generation, ImageState next) {
        synchronized (this) {
            if (closed || entries.get(entry.source) != entry || entry.request != generation) return;
            if (next.content != null) {
                while (bytes + next.content.bytes() > budget.bytes && evict()) {}
                if (bytes + next.content.bytes() > budget.bytes)
                    next =
                            ImageState.failure(
                                    entry.source, generation, false, "budget", "解码内容超出共享字节预算");
                else bytes += next.content.bytes();
            }
            completed.put(entry, next);
        }
    }

    private ImageState failure(Entry entry, long generation, Exception error) {
        ImageLoader.Failure f =
                error instanceof ImageLoader.Failure ? (ImageLoader.Failure) error : null;
        return ImageState.failure(
                entry.source,
                generation,
                f != null && f.unsupported,
                f == null ? "load" : f.stage,
                error.toString());
    }

    /** 唯一宿主资源入口；不逐控件轮询。每次限额收完成、启动任务及安全snapshot。 */
    public void drain() {
        checkHost();
        List<Subscription> subscriptions = new ArrayList<>();
        List<ImageState> notifications = new ArrayList<>();
        synchronized (this) {
            checkOpen();
            Iterator<Map.Entry<Entry, ImageState>> iterator = completed.entrySet().iterator();
            int n = 0;
            while (iterator.hasNext() && n++ < budget.completions) {
                Map.Entry<Entry, ImageState> value = iterator.next();
                Entry entry = value.getKey();
                entry.state = value.getValue();
                entry.task = null;
                for (Subscription s : entry.subscriptions) {
                    subscriptions.add(s);
                    notifications.add(entry.state);
                }
                iterator.remove();
            }
        }
        for (int i = 0; i < subscriptions.size(); i++)
            subscriptions.get(i).publish(notifications.get(i));
        List<Entry> start = new ArrayList<>();
        synchronized (this) {
            for (Entry e : entries.values())
                if (e.pending && start.size() < budget.completions) {
                    e.pending = false;
                    start.add(e);
                }
        }
        for (Entry entry : start) {
            long generation;
            synchronized (this) {
                generation = entry.request;
            }
            try {
                Callable<ImageContent> load = loader.prepare(entry.source);
                synchronized (this) {
                    if (closed || entry.request != generation) {
                        if (load instanceof ImageLoader.Task) ((ImageLoader.Task) load).close();
                        continue;
                    }
                    entry.snapshot =
                            load instanceof ImageLoader.Task ? (ImageLoader.Task) load : null;
                    entry.task =
                            workers.submit(
                                    () -> {
                                        try {
                                            complete(
                                                    entry,
                                                    generation,
                                                    ImageState.ready(
                                                            entry.source, generation, load.call()));
                                        } catch (Exception error) {
                                            complete(
                                                    entry,
                                                    generation,
                                                    failure(entry, generation, error));
                                        }
                                    });
                }
            } catch (Exception error) {
                if (entry.snapshot != null) {
                    try {
                        entry.snapshot.close();
                    } catch (Exception cleanup) {
                        error.addSuppressed(cleanup);
                    }
                    entry.snapshot = null;
                }
                complete(entry, generation, failure(entry, generation, error));
            }
        }
    }

    /** 宿主呈现结果只作用于仍使用同一不可变内容的当前请求；旧plan错误不能覆盖reload。 */
    public void presentation(long identity, boolean pending, String failure) {
        presentation(identity, pending, "upload", failure);
    }

    public void presentation(long identity, boolean pending, String stage, String failure) {
        presentation(identity, pending, stage, false, failure);
    }

    public void presentation(
            long identity, boolean pending, String stage, boolean unsupported, String failure) {
        checkHost();
        List<Subscription> subscriptions = new ArrayList<>();
        List<ImageState> notifications = new ArrayList<>();
        synchronized (this) {
            if (closed) return;
            for (Entry entry : entries.values())
                if (entry.state.content != null && entry.state.content.identity == identity) {
                    ImageState current = entry.state;
                    ImageState next =
                            failure != null
                                    ? ImageState.failure(
                                            entry.source,
                                            entry.request,
                                            unsupported,
                                            stage,
                                            failure)
                                    : pending
                                            ? ImageState.uploading(current)
                                            : ImageState.ready(
                                                    entry.source, entry.request, current.content);
                    if (current.status == next.status && current.stage.equals(next.stage)) continue;
                    if (next.content == null) bytes -= current.content.bytes();
                    entry.state = next;
                    for (Subscription s : entry.subscriptions) {
                        subscriptions.add(s);
                        notifications.add(next);
                    }
                }
        }
        for (int i = 0; i < subscriptions.size(); i++)
            subscriptions.get(i).publish(notifications.get(i));
    }

    public synchronized long retainedBytes() {
        return bytes;
    }

    public synchronized int entryCount() {
        return entries.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        checkHost();
        closed = true;
        RuntimeException failure = null;
        try {
            for (Entry e : entries.values()) {
                RuntimeException cleanup = cancel(e);
                if (cleanup != null) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            }
        } finally {
            entries.clear();
            completed.clear();
            bytes = 0;
            workers.shutdownNow();
        }
        if (failure != null) throw failure;
    }

    public boolean awaitClose(long milliseconds) throws InterruptedException {
        return workers.awaitTermination(milliseconds, TimeUnit.MILLISECONDS);
    }
}
