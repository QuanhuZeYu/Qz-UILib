package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 一次选择的唯一状态与目录工作 owner；界面只消费这些信号并发布选择意图。 */
final class FilePickerSelection {
    final SceneEditState path;
    final Signal<List<FilePickerDirectory.Entry>> entries;
    final Signal<String> message;
    final Signal<Path> directory, selected;
    final Signal<Boolean> busy, showing, roots, next;
    final Signal<Long> page, revision;
    private final Consumer<Path> onSelected;
    private final Runnable onCancelled;
    private FilePickerDirectory reader;
    private SceneEditState.Submission pendingPath;
    private long request;
    private boolean attached, finished;

    FilePickerSelection(ReactiveDomain domain, Path initialDirectory, Consumer<Path> onSelected, Runnable onCancelled) {
        this.onSelected = onSelected; this.onCancelled = onCancelled;
        Path initial = initialDirectory.toAbsolutePath().normalize();
        directory = domain.signal(initial); selected = domain.signal(null);
        path = new SceneEditState(domain, initial.toString());
        entries = domain.signal(Collections.emptyList());
        message = domain.signal("正在读取目录…");
        busy = domain.signal(false); showing = domain.signal(true);
        roots = domain.signal(false); next = domain.signal(false);
        page = domain.signal(0L); revision = domain.signal(0L);
    }
    void attach() {
        attached = true; finished = false; showing.set(true);
        reader = new FilePickerDirectory();
        browse(directory.get(), 0);
    }
    void detach() {
        attached = false; request++;
        if (reader != null) { reader.close(); reader = null; }
    }
    private boolean active() { return attached && !finished; }
    boolean waiting() { return active() && busy.get(); }
    /** 只在宿主正式输入帧领取；工作线程不访问选择信号或节点。 */
    void receiveCompletion() {
        if (!active()) return;
        FilePickerDirectory.Result result = reader.take();
        if (result != null) receive(result);
    }
    private long begin(String status) {
        if (pendingPath != null) path.reject(pendingPath, "路径导航已更改");
        pendingPath = null;
        long id = ++request;
        revision.set(id); busy.set(true); next.set(false); selected.set(null);
        entries.set(Collections.emptyList()); message.set(status);
        return id;
    }
    private void browse(Path target, long targetPage) {
        if (!active()) return;
        Path absolute = target.toAbsolutePath().normalize();
        path.reset(absolute.toString());
        reader.browse(begin("正在读取目录…"), absolute, targetPage);
    }
    void activate(FilePickerDirectory.Entry entry) {
        if (entry.directory) browse(entry.path, 0);
        else {
            selected.set(entry.path); path.reset(entry.path.toString());
            message.set("已选择：" + entry.name());
        }
    }
    void go(SceneEditState.Submission submission) {
        if (!active()) return;
        try {
            Path target = resolve(submission.text);
            long id = begin("正在读取目录…");
            pendingPath = submission;
            reader.locate(id, target);
        } catch (IllegalArgumentException | SecurityException failure) {
            invalidPath(submission, failure);
        }
    }
    private void invalidPath(SceneEditState.Submission submission, RuntimeException failure) {
        begin("无法访问路径：" + failure.getMessage());
        reader.cancelRead(); busy.set(false);
        path.reject(submission, message.get());
    }
    private Path resolve(String raw) {
        if (raw.isEmpty()) throw new IllegalArgumentException("请输入目录或文件路径");
        Path result = raw.regionMatches(true, 0, "file:", 0, 5)
                ? Paths.get(URI.create(raw)) : directory.get().getFileSystem().getPath(raw);
        return (result.isAbsolute() ? result : directory.get().resolve(result)).toAbsolutePath().normalize();
    }
    void showRoots(long targetPage) {
        if (active()) reader.roots(begin("正在读取根目录…"), directory.get().getFileSystem(), targetPage);
    }
    void up() {
        if (!active()) return;
        Path parent = directory.get().getParent();
        if (parent == null || roots.get()) showRoots(0); else browse(parent, 0);
    }
    void refresh() {
        if (roots.get()) showRoots(0); else browse(directory.get(), 0);
    }
    void turn(int direction) {
        if (!active() || busy.get()) return;
        long target = page.get() + direction;
        if (target < 0 || direction > 0 && !next.get()) return;
        if (roots.get()) showRoots(target); else browse(directory.get(), target);
    }
    void confirm() {
        if (!active() || busy.get()) return;
        SceneEditState.Submission submission = path.request(true);
        try {
            Path target = resolve(submission.text);
            long id = begin("正在核验文件…");
            pendingPath = submission; reader.verify(id, target);
        } catch (IllegalArgumentException | SecurityException failure) { invalidPath(submission, failure); }
    }
    private void receive(FilePickerDirectory.Result result) {
        if (!active() || result.request != request) return;
        busy.set(false);
        SceneEditState.Submission submission = pendingPath; pendingPath = null;
        if (result.error != null) {
            message.set("无法访问路径：" + result.error);
            if (submission != null) path.reject(submission, message.get());
        } else if (result.verified != null) {
            if (submission != null) path.accept(submission, result.verified.toString());
            finish(); onSelected.accept(result.verified);
        } else applyListing(result, submission);
    }
    private void applyListing(FilePickerDirectory.Result result, SceneEditState.Submission submission) {
        roots.set(result.roots); page.set(result.page); next.set(result.next);
        entries.set(result.entries); selected.set(result.selected);
        if (result.directory != null) {
            directory.set(result.directory);
            String normalized = (result.selected == null ? result.directory : result.selected).toString();
            if (submission != null) path.accept(submission, normalized);
            else path.synchronizeExternal(normalized);
        }
        message.set(listingMessage(result));
    }
    private static String listingMessage(FilePickerDirectory.Result result) {
        if (result.selected != null) return "已选择：" + result.selected.getFileName();
        if (result.entries.isEmpty()) return result.roots ? "没有可用根目录。" : "空目录";
        return (result.roots ? "根目录 / 盘符" : "单击目录进入，选择文件后确认。")
                + "（本页 " + result.entries.size() + " 项）";
    }
    private void finish() { finished = true; showing.set(false); detach(); }
    void cancel() { if (active()) { finish(); onCancelled.run(); } }
}
