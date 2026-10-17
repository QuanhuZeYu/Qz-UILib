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
    public static final class Options {
        public final String filterName,extensions;
        public Options(){this("","");}
        private Options(String name,String extensions){this.filterName=Objects.requireNonNull(name);this.extensions=Objects.requireNonNull(extensions);}
        public Options filter(String name,String extensions){return new Options(name,extensions);}
    }
    private final Options options;
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;
    private final FilePickerSelection selection;

    private SceneFilePicker(ScenePageContext context, ReadableSignal<SceneTheme> theme, FilePickerSelection selection,Options options) {
        this.options=options;this.context = context; this.theme = theme; this.selection = selection;
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            Consumer<Path> onSelected, Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), onSelected, onCancelled);
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            ReadableSignal<SceneTheme> theme, Consumer<Path> onSelected, Runnable onCancelled) {
        return create(context,initialDirectory,theme,new Options(),onSelected,onCancelled);
    }
    public static SceneNode create(ScenePageContext context,Path initialDirectory,ReadableSignal<SceneTheme> theme,Options options,Consumer<Path> onSelected,Runnable onCancelled){
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(initialDirectory, "initialDirectory");
        Objects.requireNonNull(theme, "theme");
        FilePickerSelection selection = new FilePickerSelection(context.domain(), initialDirectory,
                Objects.requireNonNull(onSelected, "onSelected"), Objects.requireNonNull(onCancelled, "onCancelled"));
        return new SceneFilePicker(context,theme,selection,Objects.requireNonNull(options)).createNode();
    }
    private SceneNode createNode() {
        FilePickerStyle style=new FilePickerStyle(context,theme);
        SceneNode field=SceneTextField.create(context,selection.path,
                ()->style.colors.get().editorTheme(),()->false,
                selection::go,SceneTextField.SubmitPolicy.CONFIRM,()->"输入目录或文件路径",false);
        SceneNode heading=FilePickerStyle.row(14).add(style.icon(SceneIconButton.Symbol.FOLDER))
                .add(FilePickerStyle.column(5).add(style.title("选择本地文件"))
                        .add(style.label(()->"浏览文件夹，选择需要打开的文件。",.9f,true).bind(NodeBinding.value(()->context.viewportHeight().get()>=800,SceneNode::visible))));
        SceneNode header=FilePickerStyle.column(8).padding(12).add(heading)
                .add(navigation(style)).add(FilePickerStyle.row(10).add(style.field(field))
                        .add(style.action("前往",null,()->selection.go(selection.path.request(true)),FilePickerStyle.Role.OUTLINE)));
        SceneNode search=SceneTextField.create(context,selection.search,()->style.colors.get().editorTheme(),()->false,selection::query,SceneTextField.SubmitPolicy.CONFIRM,()->"搜索当前目录…",false);
        header.add(FilePickerStyle.row(8).add(style.field(search)).add(style.action("清除",null,selection::clearQuery,FilePickerStyle.Role.GHOST).bind(NodeBinding.value(()->!selection.search.snapshot().get().text.isEmpty(),SceneNode::enabled))).add(style.action(()->selection.order.get().label,null,selection::cycleOrder,FilePickerStyle.Role.OUTLINE))
                .add(style.action(()->selection.extension.get().isEmpty()?"所有文件":options.filterName,null,()->{selection.extension.set(selection.extension.get().isEmpty()?options.extensions:"");selection.refresh();},FilePickerStyle.Role.GHOST).bind(NodeBinding.value(()->!options.extensions.isEmpty(),SceneNode::visible))));
        SceneNode crumbs=FilePickerStyle.row(3).scrollable(true);
        crumbs.bind(NodeBinding.repeat(()->{java.util.ArrayList<Path> chain=new java.util.ArrayList<>();for(Path cursor=selection.directory.get();cursor!=null;cursor=cursor.getParent())chain.add(cursor);java.util.Collections.reverse(chain);if(chain.size()>3)chain.subList(1,chain.size()-2).clear();return chain;},part->part,item->SceneTooltip.attach(context,style.action(()->(item.get().equals(selection.directory.get().getParent())&&selection.directory.get().getNameCount()>2?"… / ":"")+(item.get().getFileName()==null?item.get().toString():item.get().getFileName().toString()),null,()->selection.navigate(item.get()),FilePickerStyle.Role.GHOST).size(Size.fill(1),Size.natural()),()->SceneTooltip.Content.text(item.get().toString()),()->new SceneTooltip.Options(),theme)));header.add(crumbs.bind(NodeBinding.value(()->context.viewportHeight().get()>=700,SceneNode::visible)));
        SceneNode content=FilePickerStyle.column(10).size(Size.fill(1),Size.fill(1)).padding(12)
                .add(style.label(()->"名称",.86f,true))
                .add(listing(style)).add(style.label(selection::feedback,.86f,true));
        SceneNode panel=FilePickerStyle.column(0).control(new Panel(style.colors))
                .add(header).add(style.line()).add(content).add(style.line()).add(footer(style));
        SceneNode modal=SceneLayer.create(context,()->new SceneLayer.Policy(0,SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1),Size.fill(1)).padding(12).background(SceneAppearance.MODAL_SCRIM).modal(true)
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
        SceneNode back=style.action("后退",null,()->selection.history(-1),FilePickerStyle.Role.GHOST).bind(NodeBinding.value(()->selection.backCount.get()>0&&!selection.busy.get(),SceneNode::enabled));
        SceneNode forward=style.action("前进",null,()->selection.history(1),FilePickerStyle.Role.GHOST).bind(NodeBinding.value(()->selection.forwardCount.get()>0&&!selection.busy.get(),SceneNode::enabled));
        return FilePickerStyle.row(8).add(back).add(forward)
                .add(style.action("上一级",SceneIconButton.Symbol.CHEVRON_LEFT,selection::up,FilePickerStyle.Role.OUTLINE))
                .add(style.action("根目录",SceneIconButton.Symbol.MONITOR,()->selection.showRoots(0),FilePickerStyle.Role.GHOST))
                .add(new SceneNode().size(Size.fill(1),Size.natural()))
                .add(style.action("刷新",null,selection::refresh,FilePickerStyle.Role.GHOST));
    }
    private SceneNode listing(FilePickerStyle style) {
        SceneNode items=FilePickerStyle.column(4);
        items.bind(NodeBinding.repeat(selection.entries,entry->entry.path,item->style.entry(item,
                ()->item.get().path.equals(selection.currentSelection()),()->selection.activate(item.get()))));
        SceneScrollArea.Area listing=SceneScrollArea.create(context,items,
                ()->new SceneScrollArea.Options().dimensions(7,24,1)
                        .colors(0,style.colors.get().border,style.colors.get().muted),theme);
        SceneNode viewport=listing.viewport(SceneNode.Axis.COLUMN);
        viewport.bind(NodeBinding.value(()->{selection.revision.get();return selection.scroll.get();},SceneNode::scrollOffset));
        viewport.bind((runtime,node,owner)->{selection.scrollPosition=()->runtime.scrollOffset(node);return new BindingActivation(){public void refresh(){}public void close(){selection.scrollPosition=()->0;}};});
        items.control(new ListNavigation(selection,context));
        return listing.node();
    }
    private SceneNode footer(FilePickerStyle style) {
        SceneNode previous=style.action("上一页",null,()->selection.turn(-1),FilePickerStyle.Role.GHOST);
        previous.bind(NodeBinding.value(()->!selection.busy.get()&&selection.previous.get(),SceneNode::enabled));
        SceneNode following=style.action("下一页",null,()->selection.turn(1),FilePickerStyle.Role.GHOST);
        following.bind(NodeBinding.value(()->!selection.busy.get()&&selection.next.get(),SceneNode::enabled));
        SceneNode pages=FilePickerStyle.row(6).add(style.label(()->"第 "+(selection.page.get()+1)+" 页",.86f,true)
                .size(Size.fill(1),Size.natural())).add(previous).add(following);
        SceneNode choose=style.action("选择文件",null,selection::confirm,FilePickerStyle.Role.PRIMARY);
        choose.bind(NodeBinding.value(()->!selection.busy.get(),SceneNode::enabled));
        SceneNode actions=FilePickerStyle.row(10)
                .add(style.ellipsis(selection::targetLabel,.86f,true)
                        .size(Size.fill(1),Size.natural()))
                .add(style.action("取消",null,selection::cancel,FilePickerStyle.Role.OUTLINE)).add(choose);
        return FilePickerStyle.column(8).padding(12).add(pages).add(actions);
    }
    /** 只在列表内按钮焦点上委派；文本字段与组字沿其本来的编辑目标。 */
    private static final class ListNavigation extends SceneAppearance.Flow {
        private final FilePickerSelection selection;
        private String prefix="";private long typedAt;
        ListNavigation(FilePickerSelection selection,ScenePageContext context){this.selection=selection;}
        @Override public boolean editActivator(SceneNode node,SceneNode focused,club.heiqi.uilib.ui.scene.input.SceneEditEvent event){
            if(event.compositionEvent||event.compositionSource!=null||focused.control()==null||focused.control().acceptsTextInput(focused))return false;
            if(event.command==null)return event.text!=null&&!event.text.isEmpty();
            switch(event.command){case UP:case DOWN:case HOME:case END:case PAGE_UP:case PAGE_DOWN:return true;default:return false;}
        }
        @Override public boolean edit(SceneNode node,club.heiqi.uilib.ui.scene.input.SceneEditEvent event,SceneGeometryView geometry){
            if(selection.busy.get())return true;
            java.util.List<SceneNode> targets=new java.util.ArrayList<>();for(SceneNode row:node.children())for(SceneNode child:row.children())if(child.focusable()&&child.clickAction()!=null&&node.runtime().interactive(child))targets.add(child);
            if(targets.isEmpty())return true;
            SceneNode focused=node.runtime().focusRouter().focused().get();int current=targets.indexOf(focused),next=current;
            if(event.command==null){
                long now=System.nanoTime();if(now-typedAt>800_000_000L)prefix="";typedAt=now;prefix+=event.text.toLowerCase(java.util.Locale.ROOT);
                if(prefix.length()>128)prefix=prefix.substring(prefix.length()-128);
                for(int i=1;i<=targets.size();i++){int index=(Math.max(0,current)+i)%targets.size();if(targets.get(index).text().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)){next=index;break;}}
            }else switch(event.command){case UP:next=Math.max(0,current-1);break;case DOWN:next=Math.min(targets.size()-1,current+1);break;case HOME:next=0;break;case END:next=targets.size()-1;break;case PAGE_UP:next=Math.max(0,current-8);break;case PAGE_DOWN:next=Math.min(targets.size()-1,current+8);break;default:return false;}
            if(next>=0)node.runtime().focusRouter().navigateTo(node.runtime().captureMount(targets.get(next)));return true;
        }
    }
    /** 正式输入帧领取目录结果；表面装饰和普通流继续走同一扩展合同。 */
    private final class Panel extends FilePickerStyle.Surface {
        Panel(ReadableSignal<FilePickerStyle.Palette> colors){super(colors,FilePickerStyle.Role.PANEL,null,()->false);}
        @Override public long nextInputNanos(SceneNode node){return selection.waiting()?0:Long.MAX_VALUE;}
        @Override public void inputFrame(SceneNode node,SceneGeometryView geometry,long now){selection.receiveCompletion();}
    }
}
