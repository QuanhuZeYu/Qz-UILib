package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.control.FilePickerHistory.Location;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 一次选择的唯一状态与目录工作 owner；界面只消费这些信号并发布选择意图。 */
final class FilePickerSelection {
    final SceneEditState path,search;
    final Signal<String> extension,searchTerm;
    final Signal<FilePickerOrder> order;
    final Signal<List<FilePickerDirectory.Entry>> entries;
    final Signal<String> message;
    final Signal<Path> directory, selected;
    final Signal<Boolean> busy, showing, roots, previous, next;
    final Signal<Long> page, revision;
    final Signal<Integer> scroll,backCount,forwardCount;
    java.util.function.IntSupplier scrollPosition=()->0;
    private final FilePickerHistory history=new FilePickerHistory();
    private FilePickerQuery readingQuery=FilePickerQuery.first();
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
        path = new SceneEditState(domain, initial.toString());search=new SceneEditState(domain,"");
        extension=domain.signal("");searchTerm=domain.signal("");order=domain.signal(FilePickerOrder.NAME);
        scroll=domain.signal(0);backCount=domain.signal(0);forwardCount=domain.signal(0);
        entries = domain.signal(Collections.emptyList());
        message = domain.signal("正在读取目录…");
        busy = domain.signal(false); showing = domain.signal(true);
        roots = domain.signal(false); previous=domain.signal(false); next = domain.signal(false);
        page = domain.signal(0L); revision = domain.signal(0L);
    }
    void attach() {
        attached = true; finished = false; showing.set(true);
        reader = new FilePickerDirectory();
        browse(directory.get(), FilePickerQuery.first());
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
    private long begin(String status){return begin(status,true);}
    private long begin(String status,boolean listingChanges) {
        if (pendingPath != null) path.reject(pendingPath, "路径导航已更改");
        pendingPath = null;
        long id = ++request;
        busy.set(true);
        if(listingChanges){
            history.capture(currentSelection(),scrollPosition.getAsInt());
            revision.set(id);previous.set(false);next.set(false);selected.set(null);entries.set(Collections.emptyList());
        }
        message.set(status);
        return id;
    }
    private void browse(Path target, FilePickerQuery query) {
        if (!active()) return;
        history.beginNavigation();Path absolute = target.toAbsolutePath().normalize();
        readingQuery=query.filter(searchTerm.get(),extension.get()).order(order.get());
        long id=begin("正在读取目录…");path.reset(absolute.toString());reader.browse(id,absolute,readingQuery);
    }
    void activate(FilePickerDirectory.Entry entry) {
        if (entry.directory) browse(entry.path, FilePickerQuery.first());
        else {
            selected.set(entry.path); path.reset(entry.path.toString());
            message.set("已选择：" + entry.name());
        }
    }
    void go(SceneEditState.Submission submission) {
        if (!active()) return;
        try {
            history.cancelRestore();Path target = resolve(submission.text);
            long id = begin("正在读取目录…");
            pendingPath = submission;readingQuery=FilePickerQuery.first();search.reset("");searchTerm.set("");extension.set("");order.set(FilePickerOrder.NAME);
            reader.locate(id, target);
        } catch (IllegalArgumentException | SecurityException failure) {
            invalidPath(submission, failure);
        }
    }
    private void invalidPath(SceneEditState.Submission submission, RuntimeException failure) {
        begin("无法访问路径：" + failure.getMessage(),false);
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
        if(active()){history.cancelRestore();readingQuery=FilePickerQuery.first();reader.roots(begin("正在读取根目录…"),directory.get().getFileSystem(),readingQuery);}
    }
    void navigate(Path directory){browse(directory,FilePickerQuery.first());}
    void history(int direction){
        if(!active()||busy.get())return;Location restoring=history.request(direction);if(restoring==null)return;
        search.reset(restoring.query.text);searchTerm.set(restoring.query.text);extension.set(restoring.query.extensions);order.set(restoring.query.order);readingQuery=restoring.query;
        long id=begin("正在返回浏览位置…");path.reset(restoring.directory.toString());
        if(restoring.roots)reader.roots(id,restoring.directory.getFileSystem(),readingQuery);else reader.browse(id,restoring.directory,readingQuery);
    }
    void up() {
        if (!active()) return;
        Path parent = directory.get().getParent();
        if (parent == null || roots.get()) showRoots(0); else browse(parent, FilePickerQuery.first());
    }
    void query(SceneEditState.Submission submission){if(search.snapshot().get().composing)return;if(search.accept(submission,submission.text)){searchTerm.set(submission.text.trim());refresh();}}
    void clearQuery(){search.reset("");searchTerm.set("");refresh();}
    void cycleOrder(){FilePickerOrder[] values=FilePickerOrder.VALUES;int i=java.util.Arrays.asList(values).indexOf(order.get());order.set(values[(i+1)%values.length]);refresh();}
    void refresh() {
        boolean same=history.sameQuery(searchTerm.get(),extension.get(),order.get());
        Location keep=same?history.snapshot(currentSelection(),scrollPosition.getAsInt()):null;
        if(roots.get())showRoots(0);else browse(directory.get(),same?history.query():FilePickerQuery.first());history.refresh(keep);
    }
    void turn(int direction) {
        if (!active() || busy.get()) return;
        if(entries.get().isEmpty()||direction>0&&!next.get()||direction<0&&!previous.get())return;
        List<FilePickerDirectory.Entry> shown=entries.get();
        FilePickerQuery query=direction>0?FilePickerQuery.after(page.get()+1,shown.get(shown.size()-1))
                :FilePickerQuery.before(page.get()-1,shown.get(0));
        if(roots.get())reader.roots(begin("正在读取根目录…"),directory.get().getFileSystem(),query);
        else browse(directory.get(),query);
    }
    void confirm() {
        if (!active() || busy.get()) return;
        SceneEditState.Submission submission = path.request(true);
        try {
            Path target = resolve(submission.text);
            long id = begin("正在核验文件…",false);
            pendingPath = submission; reader.verify(id, target);
        } catch (IllegalArgumentException | SecurityException failure) { invalidPath(submission, failure); }
    }
    private void receive(FilePickerDirectory.Result result) {
        if (!active() || result.request != request) return;
        busy.set(false);
        SceneEditState.Submission submission = pendingPath; pendingPath = null;
        if (result.error != null) {
            String reason="无法访问路径："+result.error;
            history.beginNavigation();message.set(submission==null||path.reject(submission,reason)?reason:"路径已更改，请重新确认。");
        } else if (result.verified != null) {
            // 编辑状态已拥有当前提交判断；旧核验不能关闭用户后来编辑的路径。
            if(submission==null||!path.accept(submission,result.verified.toString())){
                message.set("路径已更改，请重新确认。");return;
            }
            if(!active()||result.request!=request)return;
            finish(); onSelected.accept(result.verified);
        } else applyListing(result, submission);
    }
    private void applyListing(FilePickerDirectory.Result result, SceneEditState.Submission submission) {
        String normalized=result.directory==null?null:(result.selected==null?result.directory:result.selected).toString();
        if(submission!=null&&normalized!=null&&!path.accept(submission,normalized)){
            message.set("路径已更改，请重新前往或确认。");return;
        }
        Location restore=history.acceptListing(result);
        backCount.set(history.backCount());forwardCount.set(history.forwardCount());
        publishListing(result,submission,normalized);
        restoreSelection(result,restore);
        int position=restore==null?0:restore.scroll;
        // locate实际可能执行AROUND；历史保存worker已执行边界，不保存前往时的FIRST意图。
        readingQuery=result.query;scroll.set(position);
        history.current(directory.get(),result.roots,result.query,selected.get(),position);
        message.set(listingMessage(result));
    }
    private void publishListing(FilePickerDirectory.Result result,SceneEditState.Submission submission,String normalized){
        roots.set(result.roots);page.set(result.page);previous.set(result.previous);next.set(result.next);
        entries.set(result.entries);selected.set(result.selected);
        if(result.directory==null)return;
        directory.set(result.directory);
        if(submission==null)path.synchronizeExternal(normalized);
    }
    private void restoreSelection(FilePickerDirectory.Result result,Location restore){
        if(restore==null||restore.selected==null)return;
        Path candidate=restore.selected;
        if(result.entries.stream().anyMatch(entry->entry.path.equals(candidate))){selected.set(candidate);path.reset(candidate.toString());}
    }
    private String listingMessage(FilePickerDirectory.Result result) {
        if (result.selected != null) return "已选择：" + result.selected.getFileName();
        if(result.entries.isEmpty()&&(!searchTerm.get().isEmpty()||!extension.get().isEmpty()))return "没有匹配项，可清除搜索或切换所有文件。";
        if(result.entries.isEmpty())return result.page>0?"当前页内容已变化，请刷新或返回上一页。":result.roots?"没有可用根目录。":"空目录";
        return (result.roots ? "根目录 / 盘符" : "单击目录进入，选择文件后确认。")
                + "（本页 " + result.entries.size() + " 项）";
    }
    Path currentSelection(){
        Path chosen=selected.get();
        if(chosen==null)return null;
        try{return chosen.equals(resolve(path.snapshot().get().text))?chosen:null;}
        catch(IllegalArgumentException|SecurityException invalid){return null;}
    }
    String targetLabel(){
        String raw=path.snapshot().get().text;
        if(raw.isEmpty())return "尚未选择文件";
        try{
            Path target=resolve(raw);
            if(target.equals(directory.get())&&currentSelection()==null)return "尚未选择文件";
            String name=target.getFileName()==null?target.toString():target.getFileName().toString();
            return (currentSelection()==null?"待核验：":"将打开：")+name;
        }catch(IllegalArgumentException|SecurityException invalid){return "请检查待打开的文件路径";}
    }
    String feedback(){
        if(selected.get()!=null&&currentSelection()==null)return "路径已更改，确认时将核验当前输入。";
        return message.get();
    }
    private void finish() { finished = true; showing.set(false); detach(); }
    void cancel() { if (active()) { finish(); onCancelled.run(); } }
}
