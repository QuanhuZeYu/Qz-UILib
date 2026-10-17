package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.settings.SettingsPage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 仅收录有真实参考的应用页面；页面内容复用正式组件，入口不保留旧示例分派。 */
public final class TestGallery implements ScenePage {
    public enum Page {
        HOME("场景概览","选择一个真实参考案例"),
        MAILBOX("收件箱","查找、阅读和整理团队邮件"),
        SETTINGS("配置","管理字体、外观与当前宿主配置"),
        DRAGGABLE("拖拽","拾取、移动与释放普通内容"),
        DROPPABLE("接收区","选择有效目标并放下内容"),
        SORTABLE("排序","重排列表、网格与跨容器内容"),
        SCROLL_AREA("滚动区","浏览纵横内容、嵌套视口与动态滚动条"),
        MEDIA("媒体播放","播放视频与音频、暂停和跳转"),
        TOOLTIP("Tooltip","悬停与键盘提示、方向避让和完整说明");
        public final String title,purpose;
        Page(String title,String purpose){this.title=title;this.purpose=purpose;}
        String reference(){
            switch(this){
                case MEDIA:return "参考：HTML video / 媒体控制";
                case TOOLTIP:return "参考：shadcn Base / Tooltip";
                case MAILBOX:return "参考：Gmail";
                case DRAGGABLE:return "参考：dnd-kit / Draggable";
                case DROPPABLE:return "参考：dnd-kit / Droppable";
                case SORTABLE:return "参考：dnd-kit / Sortable";
                case SCROLL_AREA:return "参考：SimpleBar / Examples";
                default:return "参考：Shadcn Admin / Settings";
            }
        }
    }
    private final GalleryEnvironment environment;
    private final Page initial;
    private Signal<Page> selected;
    private SettingsPage settingsPage;
    public TestGallery(GalleryEnvironment environment){this(environment,Page.HOME);}
    public TestGallery(GalleryEnvironment environment,Page initial){this.environment=environment;this.initial=initial;}
    @Override public void build(ScenePageContext context) {
        selected=context.domain().signal(initial);
        settingsPage=new SettingsPage(environment.settings(),() -> selected.set(Page.HOME));
        context.own(() -> {selected=null;settingsPage=null;});
        Signal<ThemeMode> preview=context.domain().signal(null);
        ReadableSignal<ThemeMode> global=context.themeMode();
        context.domain().effect(context.scope(),() -> {global.get();preview.set(null);});
        ReadableSignal<ThemeMode> mode=context.domain().computed(context.scope(),() -> preview.get()==null?global.get():preview.get());
        ReadableSignal<Boolean> dark=context.domain().computed(context.scope(),() -> mode.get().isDark());
        ReadableSignal<SceneTheme> theme=context.domain().computed(context.scope(),() -> GalleryStyle.theme(dark.get()));
        MediaPage media=new MediaPage(context,environment,theme);
        Consumer<Page> navigate=target -> {if(selected.get()==Page.MEDIA&&target!=Page.MEDIA)media.stop();selected.set(target);};
        SceneNode shell=new SceneNode().size(Size.fill(1),Size.fill(1))
                .bind(NodeBinding.value(dark,(n,value) -> n.background(value?GalleryStyle.DARK_BACKGROUND:GalleryStyle.LIGHT_BACKGROUND)));
        SceneNode header=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).padding(24).gap(16);
        header.bind(NodeBinding.value(()->selected.get()==Page.HOME,SceneNode::visible));
        header.add(GalleryStyle.label(context,()->"QZ / 场景工作台",theme,20,false).size(Size.fill(1),Size.natural()));
        header.add(GalleryStyle.button(context,()->"场景概览",theme,()->navigate.accept(Page.HOME)).clickFocus(false));
        header.add(GalleryStyle.button(context,()->dark.get()?"预览浅色":"预览深色",theme,
                ()->preview.set(dark.get()?ThemeMode.LIGHT:ThemeMode.DARK)));
        shell.add(header);
        SceneNode deck=new SceneNode().size(Size.fill(1),Size.fill(1)).control(new Layers());
        for(Page id:Page.values()) {
            SceneNode page;
            switch(id){
                case HOME:page=directory(context,theme,navigate);break;
                case MEDIA:page=media.create(() -> navigate.accept(Page.HOME));break;
                case SETTINGS:page=settingsPage.create(context);break;
                case DRAGGABLE:page=DraggablePage.create(context,theme,() -> navigate.accept(Page.HOME));break;
                case DROPPABLE:page=DroppablePage.create(context,theme,() -> navigate.accept(Page.HOME));break;
                case SORTABLE:page=SortablePage.create(context,theme,() -> navigate.accept(Page.HOME));break;
                case SCROLL_AREA:page=ScrollAreaPage.create(context,theme,() -> navigate.accept(Page.HOME));break;
                case TOOLTIP:page=TooltipPage.create(context,theme,() -> navigate.accept(Page.HOME));break;
                case MAILBOX:page=club.heiqi.uilib.testing.gallery.mail.MailboxPage.create(context,theme,mode,
                        () -> navigate.accept(Page.HOME),() -> preview.set(dark.get()?ThemeMode.LIGHT:ThemeMode.DARK));break;
                default:throw new AssertionError(id);
            }
            context.own(page::destroy);
            SceneNode layer=new SceneNode().size(Size.fill(1),Size.fill(1)).control(new Layers());
            layer.bind(NodeBinding.children(() -> selected.get()==id?Collections.singletonList(page):Collections.emptyList()));
            deck.add(layer);
        }
        shell.add(deck);context.mountOwned(shell);
    }
    /** 原生宿主请求按当前页面转交；确认完成动作由窗口提供，不混入目录导航。 */
    public void requestClose(Runnable complete){
        environment.settings().domain().execute(() -> {
            if(selected.get()==Page.SETTINGS)settingsPage.requestClose(complete);else complete.run();
        });
    }
    public void requestReload(Runnable complete){
        environment.settings().domain().execute(() -> {
            if(selected.get()==Page.SETTINGS)settingsPage.requestReload();else complete.run();
        });
    }
    private static SceneNode directory(ScenePageContext context,ReadableSignal<SceneTheme> theme,Consumer<Page> navigate) {
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(28).gap(24).scrollable(true);
        root.add(GalleryStyle.header(context,theme,"探索场景","从日常应用到细节交互，选择一个场景开始体验。",null));
        root.add(group(context,theme,navigate,"应用体验","完整的工作流程，共同的界面语言。",Page.MAILBOX,Page.SETTINGS,Page.MEDIA));
        root.add(group(context,theme,navigate,"交互组件","拖动、放置、排序与滚动，在真实内容里试一试。",Page.DRAGGABLE,Page.DROPPABLE,Page.SORTABLE,Page.SCROLL_AREA,Page.TOOLTIP));
        return root;
    }
    private static SceneNode group(ScenePageContext context,ReadableSignal<SceneTheme> theme,Consumer<Page> navigate,String title,String description,Page... entries){
        SceneNode section=new SceneNode().size(Size.fill(1),Size.natural()).gap(16);
        section.add(GalleryStyle.label(context,()->title,theme,20,false));
        section.add(GalleryStyle.label(context,()->description,theme,14,true));
        SceneNode grid=GalleryStyle.grid(context,theme);
        for(Page id:entries){
            SceneNode card=GalleryStyle.card(theme).gap(14);
            club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol icon;
            switch(id){
                case MAILBOX:icon=club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.MAIL;break;
                case SETTINGS:icon=club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.SLIDERS;break;
                case MEDIA:icon=club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.MONITOR;break;
                default:icon=club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.SORT;break;
            }
            final club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol symbol=icon;
            card.add(club.heiqi.uilib.ui.scene.control.SceneIconButton.graphic(context,()->symbol,theme).size(Size.fixed(24),Size.fixed(24)));
            card.add(GalleryStyle.label(context,()->id.title,theme,20,false));
            card.add(GalleryStyle.label(context,()->id.purpose,theme,14,true));
            card.add(GalleryStyle.button(context,()->"打开"+id.title,theme,()->navigate.accept(id)).clickFocus(false));
            grid.add(GalleryStyle.frame(card,theme));
        }
        return section.add(grid);
    }

    /** 多根页面复用正式控件扩展端口，模态仍按原根顺序叠放；几何与输入由scene唯一引擎维护。 */
    private static final class Layers implements SceneControl {
        @Override
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        @Override
        public MeasuredSize measure(
                SceneNode node,
                Constraints limits,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            int width = 0, height = 0;
            for (SceneNode child : children.flowChildren())
                if (child.visible()) {
                    MeasuredSize size = children.measureChild(child, limits);
                    width = Math.max(width, size.width);
                    height = Math.max(height, size.height);
                }
            return new MeasuredSize(width, height);
        }

        private static int resolve(Size size, int available, int natural) {
            return size.constrain(
                    size.mode == Size.Mode.FIXED
                            ? size.value
                            : size.mode == Size.Mode.FILL ? available : natural);
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int padding = node.padding();
            int width = Math.max(0, allocated.width - padding * 2);
            int height = Math.max(0, allocated.height - padding * 2);
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : children.flowChildren())
                if (child.visible()) {
                    MeasuredSize measured = children.measureChild(child, new Constraints(width, height));
                    placed.add(new ChildPlacements.Entry(child, new LayoutBox(padding, padding,
                            resolve(child.width(), width, measured.width),
                            resolve(child.height(), height, measured.height))));
                }
            return new ChildPlacements(placed);
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}