package club.heiqi.uilib.ui.scene.control;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 文件事实只在本次挂载的单一工作线程读取；不持有节点，也不跨请求累计整个目录。 */
final class FilePickerDirectory implements AutoCloseable {
    static final int PAGE_SIZE = 64;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    static final class Entry {
        final Path path;
        final boolean directory;
        Entry(Path path, boolean directory) { this.path = path; this.directory = directory; }
        String name() { return path.getFileName() == null ? path.toString() : path.getFileName().toString(); }
    }
    static final class Result {
        final long request, page;
        final Path directory, selected, verified;
        final boolean roots, next;
        final List<Entry> entries;
        final String error;
        Result(long request, long page, Path directory, boolean roots, boolean next,
                List<Entry> entries, Path selected, Path verified, String error) {
            this.request = request; this.page = page; this.directory = directory;
            this.roots = roots; this.next = next; this.entries = Collections.unmodifiableList(entries);
            this.selected = selected; this.verified = verified; this.error = error;
        }
    }
    private interface Read { Result run() throws IOException; }
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Result> completion = new AtomicReference<>();
    private Future<?> latest;

    FilePickerDirectory() {
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "uilib-file-picker-" + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true); return thread;
        });
    }
    void browse(long request, Path directory, long page) {
        submit(request, () -> list(request, directory, page, null));
    }
    void locate(long request, Path path) {
        submit(request, () -> {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (attributes.isDirectory()) return list(request, path, 0, null);
            if (!attributes.isRegularFile()) throw new IOException("请选择目录或普通文件");
            Path parent = path.getParent();
            if (parent == null) throw new IOException("文件没有可浏览的上级目录");
            return list(request, parent, 0, path);
        });
    }
    void verify(long request, Path path) {
        submit(request, () -> {
            if (!Files.readAttributes(path, BasicFileAttributes.class).isRegularFile())
                throw new IOException("请选择存在的普通文件");
            return new Result(request, 0, null, false, false, Collections.emptyList(), null, path, null);
        });
    }
    void roots(long request, FileSystem fileSystem, long page) {
        submit(request, () -> collect(request, null, true, page, fileSystem.getRootDirectories().iterator(), null));
    }
    private Result list(long request, Path directory, long page, Path selected) throws IOException {
        checkCancelled();
        // 每次进入、刷新或翻页重新取得事实；仅保留当前批及一个“还有下一批”的判断。
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return collect(request, directory, false, page, stream.iterator(), selected);
        }
    }
    private Result collect(long request, Path directory, boolean roots, long page,
            Iterator<Path> paths, Path selected) {
        long offset = Math.multiplyExact(page, (long) PAGE_SIZE), skipped = 0;
        List<Entry> entries = new ArrayList<>(PAGE_SIZE);
        while (paths.hasNext()) {
            checkCancelled();
            Path path = paths.next().toAbsolutePath().normalize();
            if (skipped < offset) { skipped++; continue; }
            if (entries.size() == PAGE_SIZE)
                return new Result(request, page, directory, roots, true, entries, selected, null, null);
            entries.add(new Entry(path, roots || Files.isDirectory(path)));
        }
        checkCancelled();
        return new Result(request, page, directory, roots, false, entries, selected, null, null);
    }
    void cancelRead() {
        if (latest != null) latest.cancel(true);
        latest = null;
        worker.getQueue().clear();
        completion.set(null);
    }
    private void submit(long request, Read read) {
        if (closed.get()) return;
        cancelRead();
        latest = worker.submit(() -> {
            try {
                Result result = read.run(); checkCancelled(); publish(result);
            } catch (CancellationException ignored) {
                // 取消不发布错误，也不让旧目录恢复当前界面。
            } catch (IOException | SecurityException | IllegalArgumentException | DirectoryIteratorException failure) {
                if (closed.get() || Thread.currentThread().isInterrupted()) return;
                String reason = failure.getMessage();
                if (reason == null || reason.isEmpty()) reason = failure.getClass().getSimpleName();
                publish(new Result(request, 0, null, false, false, Collections.emptyList(), null, null, reason));
            }
        });
    }
    private void checkCancelled() {
        if (closed.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    private void publish(Result result) {
        if (!closed.get()) completion.set(result);
    }
    /** 只由宿主正式输入帧领取。direct 状态域不能把工作线程变成 UI 操作线程。 */
    Result take() {
        Result result = completion.getAndSet(null);
        return closed.get() ? null : result;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        completion.set(null);
        if (latest != null) latest.cancel(true);
        latest = null;
        worker.shutdownNow();
        // DirectoryStream 由工作调用的 try-with-resources 关闭；渲染线程不等待文件系统 I/O。
    }
}
