package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final LinkedHashSet<RequestAttempt> completed = new LinkedHashSet<>();
    // 登记只随活跃 Lease；拒绝不占资源条目，也不进入自动重试队列。
    private final LinkedHashSet<Subscription> consumers = new LinkedHashSet<>();
    private long request, bytes;
    private boolean closed;

    private static final class Entry {
        final ImageSource source;
        final List<Subscription> subscriptions = new ArrayList<>();
        ImageState state;
        RequestAttempt attempt;

        Entry(ImageSource source) {
            this.source = source;
        }
    }

    private enum RequestPhase {
        PENDING,
        STARTED,
        COMPLETED,
        PUBLISHED,
        CANCELLED
    }

    /** 一次请求拥有自己的资格、异步资源和完成产物；所有可变字段由service监视器保护。
     * Entry只保存当前发布快照。完成产物转成当前快照时沿用同一份计费，取消只释放本请求。
     * 对应图像规格第1.2/1.3节的迟到结果与共享生命周期边界。
     */
    private final class RequestAttempt {
        final Entry entry;
        final long request;
        RequestPhase phase;
        Future<?> future;
        ImageLoader.Task snapshot;
        ImageState completion;
        long retainedBytes;

        RequestAttempt(Entry entry, long request, RuntimeException cleanup) {
            this.entry = entry;
            this.request = request;
            phase = cleanup == null ? RequestPhase.PENDING : RequestPhase.PUBLISHED;
            publish(cleanup == null
                    ? ImageState.loading(entry.source, request)
                    : ImageState.failure(entry.source, request, false, "cleanup", cleanup.toString()));
        }

        boolean current() {
            return !closed && phase != RequestPhase.CANCELLED
                    && entries.get(entry.source) == entry && entry.attempt == this;
        }

        boolean claimStart() {
            if (!current() || phase != RequestPhase.PENDING) return false;
            phase = RequestPhase.STARTED;
            return true;
        }

        // completion与Entry当前快照属于同一次请求；转交发布不重复计费。
        private void account(ImageState next) {
            long nextBytes = next == null || next.content == null ? 0 : next.content.bytes();
            bytes += nextBytes - retainedBytes;
            retainedBytes = nextBytes;
        }

        void publish(ImageState next) {
            account(next);
            entry.state = next;
        }

        // 先解除归属再调用close，异常或重复取消不会再次释放；Task自身仍可在call中幂等关闭。
        private void closeSnapshot() throws Exception {
            ImageLoader.Task owned = snapshot;
            snapshot = null;
            if (owned != null) owned.close();
        }

        RuntimeException cancel() {
            if (phase == RequestPhase.CANCELLED) return null;
            phase = RequestPhase.CANCELLED;
            completed.remove(this);
            completion = null;
            account(null);
            RuntimeException failure = null;
            try {
                closeSnapshot();
            } catch (Exception cleanup) {
                failure = new IllegalStateException("资源快照释放失败: " + entry.source, cleanup);
            }
            Future<?> owned = future;
            future = null;
            if (owned != null) {
                try {
                    owned.cancel(true);
                } catch (RuntimeException cleanup) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                } finally {
                    workers.purge();
                }
            }
            return failure;
        }

        private ImageState failure(Exception error) {
            ImageLoader.Failure f = error instanceof ImageLoader.Failure
                    ? (ImageLoader.Failure) error : null;
            return ImageState.failure(entry.source, request, f != null && f.unsupported,
                    f == null ? "load" : f.stage, error.toString());
        }

        private void complete(ImageState next) {
            synchronized (SharedImageService.this) {
                if (!current() || phase != RequestPhase.STARTED) return;
                if (next.content != null) {
                    long required = next.content.bytes();
                    while (required > budget.bytes - bytes && evict()) {}
                    if (!current()) return;
                    if (required > budget.bytes - bytes)
                        next = ImageState.failure(entry.source, request, false, "budget",
                                "解码内容超出共享字节预算");
                }
                account(next);
                completion = next;
                phase = RequestPhase.COMPLETED;
                completed.add(this);
            }
        }

        ImageState publishCompletion() {
            if (!current() || phase != RequestPhase.COMPLETED) return null;
            ImageState next = completion;
            completion = null;
            future = null;
            phase = RequestPhase.PUBLISHED;
            publish(next);
            return next;
        }

        void start() {
            synchronized (SharedImageService.this) {
                if (!current() || phase != RequestPhase.STARTED) return;
            }
            try {
                Callable<ImageContent> load = loader.prepare(entry.source);
                synchronized (SharedImageService.this) {
                    // prepare在锁外执行；其迟到快照仍归此attempt，不能清理后来的请求。
                    snapshot = load instanceof ImageLoader.Task ? (ImageLoader.Task) load : null;
                    if (!current()) {
                        closeSnapshot();
                        return;
                    }
                    future = workers.submit(() -> {
                        try {
                            complete(ImageState.ready(entry.source, request, load.call()));
                        } catch (Exception error) {
                            complete(failure(error));
                        }
                    });
                }
            } catch (Exception error) {
                synchronized (SharedImageService.this) {
                    try {
                        closeSnapshot();
                    } catch (Exception cleanup) {
                        error.addSuppressed(cleanup);
                    }
                    complete(failure(error));
                }
            }
        }
    }

    private final class Subscription implements Lease {
        final ImageSource source;
        Entry entry;
        ImageState rejection;
        final ReactiveDomain domain;
        final Signal<ImageState> state;
        volatile boolean alive = true;

        Subscription(ImageSource source, Entry entry, ReactiveDomain domain, ImageState initial) {
            this.source = source;
            this.entry = entry;
            this.domain = domain;
            rejection = entry == null ? initial : null;
            state = domain.signal(initial);
            consumers.add(this);
            if (entry != null) entry.subscriptions.add(this);
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
                                    && (entry == null ? rejection == next
                                            : entry.attempt.current() && entry.state == next)) state.set(next);
                        }
                    });
        }

        @Override
        public void close() {
            domain.checkAccess();
            synchronized (SharedImageService.this) {
                if (!alive) return;
                alive = false;
                consumers.remove(this);
                Entry owned = entry;
                entry = null;
                rejection = null;
                if (owned != null) {
                    owned.subscriptions.remove(this);
                    if (owned.subscriptions.isEmpty()
                            && (owned.state.status == ImageState.Status.LOADING
                                    || owned.source.kind == ImageSource.Kind.BITMAP)) {
                        RuntimeException failure = owned.attempt.cancel();
                        entries.remove(owned.source);
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
        if (entry == null) entry = admit(source);
        return new Subscription(source, entry, domain, entry == null ? budgetFailure(source) : entry.state);
    }

    private ImageState budgetFailure(ImageSource source) {
        return ImageState.failure(source, ++request, false, "budget", "共享图像条目预算已满");
    }

    private Entry admit(ImageSource source) {
        while (entries.size() >= budget.entries && evict()) {}
        if (entries.size() >= budget.entries) return null;
        Entry entry = new Entry(source);
        entries.put(source, entry);
        restart(entry);
        return entry;
    }

    private void restart(Entry entry) {
        RuntimeException cleanup = entry.attempt == null ? null : entry.attempt.cancel();
        entry.attempt = new RequestAttempt(entry, ++request, cleanup);
    }

    @Override
    public void reload(ImageSource source) {
        List<Subscription> subscribers;
        ImageState next;
        synchronized (this) {
            checkOpen();
            subscribers = new ArrayList<>();
            for (Subscription s : consumers) if (s.source.equals(source)) subscribers.add(s);
            Entry entry = entries.get(source);
            if (subscribers.isEmpty()) {
                if (entry == null) return;
                RuntimeException cleanup = entry.attempt.cancel();
                entries.remove(source);
                if (cleanup != null) System.err.println(cleanup);
                return;
            }
            if (entry == null) entry = admit(source);
            else restart(entry);
            next = entry == null ? budgetFailure(source) : entry.state;
            for (Subscription s : subscribers) {
                if (entry != null && s.entry != entry) {
                    s.entry = entry;
                    entry.subscriptions.add(s);
                }
                s.rejection = entry == null ? next : null;
            }
        }
        for (Subscription s : subscribers) s.publish(next);
    }

    /** 资源包换代只重启资源依赖；HTTP/调用方独立位图不随资源包取消。 */
    public void resourcesReloaded() {
        LinkedHashSet<ImageSource> sources = new LinkedHashSet<>();
        synchronized (this) {
            checkOpen();
            for (Entry e : entries.values())
                if (e.source.kind == ImageSource.Kind.RESOURCE
                        || e.source.kind == ImageSource.Kind.ITEM) sources.add(e.source);
            for (Subscription s : consumers)
                if (s.source.kind == ImageSource.Kind.RESOURCE
                        || s.source.kind == ImageSource.Kind.ITEM) sources.add(s.source);
        }
        for (ImageSource s : sources) reload(s);
    }

    private boolean evict() {
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.subscriptions.isEmpty()) {
                RuntimeException cleanup = e.attempt.cancel();
                it.remove();
                if (cleanup != null) System.err.println(cleanup);
                return true;
            }
        }
        return false;
    }

    /** 唯一宿主资源入口；不逐控件轮询。每次限额收完成、启动任务及安全snapshot。 */
    public void drain() {
        checkHost();
        List<Subscription> subscriptions = new ArrayList<>();
        List<ImageState> notifications = new ArrayList<>();
        synchronized (this) {
            checkOpen();
            Iterator<RequestAttempt> iterator = completed.iterator();
            int n = 0;
            while (iterator.hasNext() && n++ < budget.completions) {
                RequestAttempt attempt = iterator.next();
                iterator.remove();
                ImageState next = attempt.publishCompletion();
                if (next == null) continue;
                for (Subscription s : attempt.entry.subscriptions) {
                    subscriptions.add(s);
                    notifications.add(next);
                }
            }
        }
        for (int i = 0; i < subscriptions.size(); i++)
            subscriptions.get(i).publish(notifications.get(i));
        List<RequestAttempt> start = new ArrayList<>();
        synchronized (this) {
            for (Entry entry : entries.values())
                if (start.size() < budget.completions && entry.attempt.claimStart())
                    start.add(entry.attempt);
        }
        for (RequestAttempt attempt : start) attempt.start();
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
                if (entry.attempt.current()
                        && entry.state.content != null
                        && entry.state.content.identity == identity) {
                    ImageState current = entry.state;
                    ImageState next =
                            failure != null
                                    ? ImageState.failure(
                                            entry.source,
                                            current.request,
                                            unsupported,
                                            stage,
                                            failure)
                                    : pending
                                            ? ImageState.uploading(current)
                                            : ImageState.ready(
                                                    entry.source, current.request, current.content);
                    if (current.status == next.status && current.stage.equals(next.stage)) continue;
                    entry.attempt.publish(next);
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
                RuntimeException cleanup = e.attempt.cancel();
                if (cleanup != null) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            }
        } finally {
            for (Subscription s : consumers) {
                s.alive = false;
                s.entry = null;
                s.rejection = null;
            }
            consumers.clear();
            for (Entry e : entries.values()) e.subscriptions.clear();
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
