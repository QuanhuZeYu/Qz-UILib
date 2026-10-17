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
import java.util.PriorityQueue;
import java.util.Comparator;
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
        final long size,modified;
        Entry(Path path,boolean directory){this(path,directory,-1,-1);}
        Entry(Path path,boolean directory,long size,long modified){this.path=path;this.directory=directory;this.size=size;this.modified=modified;}
        String type(){if(directory)return "目录";String name=name();int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(java.util.Locale.ROOT);}
        String name() { return path.getFileName() == null ? path.toString() : path.getFileName().toString(); }
    }
    static final class Result {
        final long request, page;
        final Path directory, selected, verified;
        final boolean roots, previous, next;
        final List<Entry> entries;
        final String error;
        final FilePickerQuery query;
        Result(long request, long page, Path directory, boolean roots, boolean previous, boolean next,
                List<Entry> entries, Path selected, Path verified, String error,FilePickerQuery query) {
            this.request = request; this.page = page; this.directory = directory;
            this.roots = roots; this.previous=previous; this.next = next; this.entries = Collections.unmodifiableList(entries);
            this.selected = selected; this.verified = verified; this.error = error;this.query=query;
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
    void browse(long request, Path directory, FilePickerQuery query) {
        submit(request, () -> list(request, directory, query, null));
    }
    void locate(long request, Path path) {
        submit(request, () -> {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (attributes.isDirectory()) return list(request, path, FilePickerQuery.first(), null);
            if (!attributes.isRegularFile()) throw new IOException("请选择目录或普通文件");
            Path parent = path.getParent();
            if (parent == null) throw new IOException("文件没有可浏览的上级目录");
            return list(request, parent, FilePickerQuery.around(new Entry(path,false,attributes.size(),attributes.lastModifiedTime().toMillis())), path);
        });
    }
    void verify(long request, Path path) {
        submit(request, () -> {
            if (!Files.readAttributes(path, BasicFileAttributes.class).isRegularFile())
                throw new IOException("请选择存在的普通文件");
            return new Result(request, 0, null, false, false, false, Collections.emptyList(), null, path, null,null);
        });
    }
    void roots(long request, FileSystem fileSystem, FilePickerQuery query) {
        submit(request, () -> collect(request, null, true, query, fileSystem.getRootDirectories().iterator(), null));
    }
    private Result list(long request, Path directory, FilePickerQuery query, Path selected) throws IOException {
        checkCancelled();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return collect(request, directory, false, query, stream.iterator(), selected);
        }
    }
    private Result collect(long request,Path directory,boolean roots,FilePickerQuery query,Iterator<Path> paths,Path selected){
        Comparator<Entry> order=query.order;
        boolean backwards=query.direction==FilePickerQuery.Direction.PREVIOUS;
        int limit=PAGE_SIZE+1;
        PriorityQueue<Entry> candidates=new PriorityQueue<>(limit,backwards?order:order.reversed());
        long before=0;boolean opposite=false;
        while(paths.hasNext()){
            checkCancelled();
            Path path=paths.next().toAbsolutePath().normalize();
            Entry entry;
            try{BasicFileAttributes attributes=Files.readAttributes(path,BasicFileAttributes.class);entry=new Entry(path,roots||attributes.isDirectory(),attributes.size(),attributes.lastModifiedTime().toMillis());}
            catch(IOException|SecurityException inaccessible){boolean directoryEntry=roots;try{directoryEntry|=Files.isDirectory(path);}catch(SecurityException unknownKind){/* 属性失败仍可显示未知项，不把无权读取伪装为0。 */}entry=new Entry(path,directoryEntry);}
            if(!roots&&!query.accepts(entry))continue;
            int compared=query.boundary==null?1:order.compare(entry,query.boundary);
            if(compared<0)before++;
            boolean eligible=query.boundary==null||backwards&&compared<0
                    ||!backwards&&(compared>0||query.direction==FilePickerQuery.Direction.AROUND&&compared==0);
            if(!eligible){opposite=true;continue;}
            if(candidates.size()<limit)candidates.add(entry);
            else if(backwards?order.compare(entry,candidates.peek())>0:order.compare(entry,candidates.peek())<0){
                candidates.poll();candidates.add(entry);
            }
        }
        checkCancelled();
        List<Entry> entries=new ArrayList<>(candidates);entries.sort(order);
        boolean more=entries.size()>PAGE_SIZE;
        if(more)entries.remove(backwards?0:entries.size()-1);
        long page=query.direction==FilePickerQuery.Direction.AROUND?before/PAGE_SIZE:query.page;
        return new Result(request,page,directory,roots,backwards?more:opposite,backwards?opposite:more,entries,selected,null,null,query);
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
                publish(new Result(request, 0, null, false, false, false, Collections.emptyList(), null, null, reason,null));
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
