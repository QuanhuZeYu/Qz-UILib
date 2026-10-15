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
        SETTINGS("配置","管理字体、外观与当前宿主配置");
        public final String title,purpose;
        Page(String title,String purpose){this.title=title;this.purpose=purpose;}
        String reference(){return this==MAILBOX?"参考：Gmail":"参考：Shadcn Admin / Settings";}
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
        Consumer<Page> navigate=selected::set;
        SceneNode shell=new SceneNode().size(Size.fill(1),Size.fill(1))
                .bind(NodeBinding.value(dark,(n,value) -> n.background(value?GalleryStyle.DARK_BACKGROUND:GalleryStyle.LIGHT_BACKGROUND)));
        SceneNode header=new SceneNode().size(Size.fill(1),Size.natural()).padding(24).gap(12);
        header.bind(NodeBinding.value(() -> selected.get()==Page.HOME,SceneNode::visible));
        header.add(GalleryStyle.label(context,() -> "QZ / 场景工作台",theme,24,false));
        header.add(GalleryStyle.label(context,() -> "同一声明 · 自动化 / Desktop / Minecraft",theme,13,true));
        SceneNode actions=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
        actions.add(GalleryStyle.button(context,() -> "场景概览",theme,() -> navigate.accept(Page.HOME)).clickFocus(false));
        actions.add(GalleryStyle.button(context,() -> dark.get()?"预览浅色":"预览深色",theme,
                () -> preview.set(dark.get()?ThemeMode.LIGHT:ThemeMode.DARK)));
        header.add(actions);shell.add(header);
        SceneNode deck=new SceneNode().size(Size.fill(1),Size.fill(1)).control(new Layers());
        for(Page id:Page.values()) {
            SceneNode page=id==Page.HOME?directory(context,theme,navigate):id==Page.SETTINGS
                    ?settingsPage.create(context)
                    :club.heiqi.uilib.testing.gallery.mail.MailboxPage.create(context,theme,mode,
                            () -> navigate.accept(Page.HOME),() -> preview.set(dark.get()?ThemeMode.LIGHT:ThemeMode.DARK));
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
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(24).gap(16).scrollable(true);
        root.add(GalleryStyle.label(context,() -> "探索场景",theme,28,false));
        root.add(GalleryStyle.label(context,() -> "从真实产品参考出发，检验可操作的应用页面。",theme,15,true));
        SceneNode grid=GalleryStyle.grid(context,theme);
        for(Page id:Page.values())if(id!=Page.HOME) {
            SceneNode card=GalleryStyle.card(theme);
            card.add(GalleryStyle.label(context,() -> id.title,theme,20,false));
            card.add(GalleryStyle.label(context,() -> id.purpose,theme,15,true));
            card.add(GalleryStyle.label(context,id::reference,theme,13,true));
            card.add(GalleryStyle.button(context,() -> "打开"+id.title,theme,() -> navigate.accept(id)).clickFocus(false));
            grid.add(GalleryStyle.frame(card,theme));
        }
        return root.add(grid);
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