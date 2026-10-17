package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可复用的本地文件选择组合控件，行为归 specs/content/2026-10-07-uilib-theme-spec.md。
 * 作为可见来源节点的直属孩子声明，正式所属层占用当前视口并隔离后台输入。
 * 一次挂载对应一次选择；确认或取消先结束本次工作并隐藏，再通知调用方。
 * 调用方通常用 ownedChildren 移除已结束控件；卸载不补发取消或提交。
 */
public final class SceneFilePicker {
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;
    private final FilePickerSelection selection;

    private SceneFilePicker(ScenePageContext context, ReadableSignal<SceneTheme> theme, FilePickerSelection selection) {
        this.context = context; this.theme = theme; this.selection = selection;
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            Consumer<Path> onSelected, Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), onSelected, onCancelled);
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            ReadableSignal<SceneTheme> theme, Consumer<Path> onSelected, Runnable onCancelled) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(initialDirectory, "initialDirectory");
        Objects.requireNonNull(theme, "theme");
        FilePickerSelection selection = new FilePickerSelection(context.domain(), initialDirectory,
                Objects.requireNonNull(onSelected, "onSelected"), Objects.requireNonNull(onCancelled, "onCancelled"));
        return new SceneFilePicker(context, theme, selection).createNode();
    }
    private SceneNode createNode() {
        FilePickerStyle style=new FilePickerStyle(context,theme);
        SceneNode field=SceneTextField.create(context,selection.path,
                ()->style.colors.get().editorTheme(),()->false,
                selection::go,SceneTextField.SubmitPolicy.CONFIRM,()->"输入目录或文件路径",false);
        SceneNode heading=FilePickerStyle.row(14).add(style.icon(SceneIconButton.Symbol.FOLDER))
                .add(FilePickerStyle.column(5).add(style.title("选择本地文件"))
                        .add(style.label(()->"浏览文件夹，选择需要打开的文件。",.9f,true)));
        SceneNode header=FilePickerStyle.column(18).padding(24).add(heading)
                .add(navigation(style)).add(FilePickerStyle.row(10).add(style.field(field))
                        .add(style.action("前往",null,()->selection.go(selection.path.request(true)),FilePickerStyle.Role.OUTLINE)));
        SceneNode content=FilePickerStyle.column(10).size(Size.fill(1),Size.fill(1)).padding(24)
                .add(style.label(()->"名称",.86f,true))
                .add(listing(style)).add(style.label(selection.message,.86f,true));
        SceneNode panel=FilePickerStyle.column(0).control(new Panel(style.colors))
                .add(header).add(style.line()).add(content).add(style.line()).add(footer(style));
        SceneNode modal=SceneLayer.create(context,()->new SceneLayer.Policy(0,SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1),Size.fill(1)).padding(24).background(0x80000000).modal(true)
                .initialFocus(field).onEscape(()->{selection.cancel();return true;}).add(style.centered(panel))
                .bind(NodeBinding.value(selection.showing,SceneNode::visible)).own(style.scope);
        modal.bind((runtime,node,owner)->{
            selection.attach();
            return new BindingActivation(){
                @Override public void refresh(){}
                @Override public void close(){selection.detach();}
            };
        });
        return modal;
    }
    private SceneNode navigation(FilePickerStyle style) {
        return FilePickerStyle.row(8)
                .add(style.action("上一级",SceneIconButton.Symbol.CHEVRON_LEFT,selection::up,FilePickerStyle.Role.OUTLINE))
                .add(style.action("根目录",SceneIconButton.Symbol.MONITOR,()->selection.showRoots(0),FilePickerStyle.Role.GHOST))
                .add(new SceneNode().size(Size.fill(1),Size.natural()))
                .add(style.action("刷新",null,selection::refresh,FilePickerStyle.Role.GHOST));
    }
    private SceneNode listing(FilePickerStyle style) {
        SceneNode items=FilePickerStyle.column(4);
        items.bind(NodeBinding.repeat(selection.entries,entry->entry.path,item->style.entry(item,
                ()->item.get().path.equals(selection.selected.get()),()->selection.activate(item.get()))));
        SceneScrollArea.Area listing=SceneScrollArea.create(context,items,
                ()->new SceneScrollArea.Options().dimensions(7,24,1)
                        .colors(0,style.colors.get().border,style.colors.get().muted),theme);
        listing.viewport(SceneNode.Axis.COLUMN).bind(NodeBinding.value(()->{
            selection.revision.get();return 0;
        },SceneNode::scrollOffset));
        return listing.node();
    }
    private SceneNode footer(FilePickerStyle style) {
        SceneNode previous=style.action("上一页",null,()->selection.turn(-1),FilePickerStyle.Role.GHOST);
        previous.bind(NodeBinding.value(()->!selection.busy.get()&&selection.page.get()>0,SceneNode::enabled));
        SceneNode following=style.action("下一页",null,()->selection.turn(1),FilePickerStyle.Role.GHOST);
        following.bind(NodeBinding.value(()->!selection.busy.get()&&selection.next.get(),SceneNode::enabled));
        SceneNode pages=FilePickerStyle.row(6).add(style.label(()->"第 "+(selection.page.get()+1)+" 页",.86f,true)
                .size(Size.fill(1),Size.natural())).add(previous).add(following);
        SceneNode choose=style.action("选择文件",null,selection::confirm,FilePickerStyle.Role.PRIMARY);
        choose.bind(NodeBinding.value(()->!selection.busy.get(),SceneNode::enabled));
        SceneNode actions=FilePickerStyle.row(10)
                .add(style.ellipsis(()->selection.selected.get()==null?"尚未选择文件":selection.selected.get().getFileName().toString(),.86f,true)
                        .size(Size.fill(1),Size.natural()))
                .add(style.action("取消",null,selection::cancel,FilePickerStyle.Role.OUTLINE)).add(choose);
        return FilePickerStyle.column(12).padding(20).add(pages).add(actions);
    }
    /** 正式输入帧领取目录结果；表面装饰和普通流继续走同一扩展合同。 */
    private final class Panel extends FilePickerStyle.Surface {
        Panel(ReadableSignal<FilePickerStyle.Palette> colors){super(colors,FilePickerStyle.Role.PANEL,null,()->false);}
        @Override public long nextInputNanos(SceneNode node){return selection.waiting()?0:Long.MAX_VALUE;}
        @Override public void inputFrame(SceneNode node,SceneGeometryView geometry,long now){selection.receiveCompletion();}
    }
}
