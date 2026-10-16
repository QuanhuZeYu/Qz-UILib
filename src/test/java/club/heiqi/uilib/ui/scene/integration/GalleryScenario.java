package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.gallery.GalleryJourney;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
/** 同一共享广场在无窗口自动机运行；使用实际字体和图像，不加载游戏或创建GL。 */
public final class GalleryScenario extends TestApplication {
    private static final class Host implements SceneHost {
        final SharedImageService images;
        PaintPlan paint;
        Host(SharedImageService images) { this.images = images; }
        public int width() { return 1400; }
        public int height() { return 1100; }
        public long metricsRevision() { return 1; }
        public void prepareResources() { images.drain(); }
        public void replay(PaintPlan plan) { paint = plan; }
    }
    protected void runTests() throws Exception {
        scenario("广场Frame/Grid遵守子固定尺寸与硬边界", this::boundedLayouts);
        ClassLoader loader = getClass().getClassLoader();
        FontTextService fonts = own(new FontTextService(FontTextService.defaultSources(loader, 1), IsolatedFontEngine.shared()));
        SharedImageService images = own(new SharedImageService(new JavaImageLoader(id -> {
            int colon = id.indexOf(':');
            java.io.InputStream stream = loader.getResourceAsStream("assets/"+id.substring(0,colon)+"/"+id.substring(colon+1));
            if (stream == null) throw new java.io.IOException("共享图像缺失："+id);
            return stream;
        }, JavaImageLoader.Budget.defaults()), SharedImageService.Budget.defaults()));
        for (int run = 0; run < 2; run++) {
            final int number = run;
            scenario("共享旅程与运行隔离#"+number, () -> {
                ReactiveDomain domain = ReactiveDomain.direct();
                Host host = new Host(images);
                java.nio.file.Path path=java.nio.file.Files.createTempDirectory("uilib-reference-gallery-").resolve("settings.yaml");
                java.util.List<club.heiqi.uilib.ui.scene.text.font.FontSource> builtins=FontTextService.defaultSources(loader,1);
                club.heiqi.uilib.ui.config.ConfigKey<java.util.List<String>> fontKey=
                        club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration.key(builtins);
                final club.heiqi.uilib.ui.config.ConfigSession[] sessions={null};
                domain.execute(() -> sessions[0]=new club.heiqi.uilib.ui.config.ConfigSession(domain,
                        club.heiqi.uilib.ui.config.ConfigSchema.of(fontKey,club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY)));
                club.heiqi.uilib.ui.config.ConfigSession config=sessions[0];
                domain.execute(() -> {try{config.save(path);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}});
                final club.heiqi.uilib.ui.settings.SettingsConfiguration[] settings={null};
                domain.execute(() -> settings[0]=new club.heiqi.uilib.ui.settings.SettingsConfiguration(domain,config,fontKey,null,path,
                        club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration.defaults(builtins),values -> {}));
                try (ScenePageSession session = ScenePageSession.open(domain, host, fonts, new TestGallery(() -> settings[0]))) {
                    GalleryJourney.run(new GalleryJourney.Driver() {
                        public void frame() { session.frame(); }
                        public SceneNode focused() { final SceneNode[] value = {null}; domain.execute(() -> value[0] = session.runtime().focusRouter().focused().get()); return value[0]; }
                        public PaintPlan paint() { return host.paint; }
                        public SceneGeometryView geometry() { final SceneGeometryView[] result = {null}; domain.execute(() -> result[0] = session.pipeline().current().geometry); return result[0]; }
                        public void click(int x, int y) { session.pointer(ScenePointerEvent.down(0,x,y)); session.pointer(ScenePointerEvent.up(0,x,y)); }
                        public void scroll(int x, int y, int delta) { session.pointer(ScenePointerEvent.scroll(x,y,delta)); }
                        public void replaceText(String value) { session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL,false,null)); session.edit(SceneEditEvent.text(value)); }
                        public void command(SceneEditEvent.Command command, boolean shift) { session.edit(SceneEditEvent.command(command,shift,null)); }
                        public void typeText(String value) { session.edit(SceneEditEvent.text(value)); }
                        public void escape() { session.escape(() -> { throw new AssertionError("共享旅程意外关闭页面"); }); }
                    });
                    try(ScenePageSession mediaPage=ScenePageSession.open(domain,host,fonts,new TestGallery(() -> settings[0],TestGallery.Page.MEDIA))) {
                        mediaPage.frame();
                        require(host.paint.commands().stream().anyMatch(command -> command.kind==club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.TEXT
                                && command.text.logicalText.contains("当前入口未提供媒体播放")),"无媒体旧入口没有清楚状态");
                        require(host.paint.commands().stream().noneMatch(command -> command.kind==club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO),"仅进入目录启动了媒体");
                    }
                    System.out.println("GALLERY host=automatic run="+number+" sharedDeclaration=true sharedJourney=true mediaOptional=true");
                } finally { java.nio.file.Files.deleteIfExists(path);java.nio.file.Files.deleteIfExists(path.getParent()); }
            });
        }
    }
    private void boundedLayouts() throws Exception {
        // 取得包内声明工厂后经正式页面挂载，动态策略不冻结为构造期快照。
        Class<?> style = Class.forName("club.heiqi.uilib.testing.gallery.GalleryStyle");
        java.lang.reflect.Method frame = style.getDeclaredMethod("frame", SceneNode.class, ReadableSignal.class);
        java.lang.reflect.Method grid = style.getDeclaredMethod("grid",
                club.heiqi.uilib.ui.scene.host.ScenePageContext.class, ReadableSignal.class);
        frame.setAccessible(true);
        grid.setAccessible(true);
        Size fixed = Size.fixed(120).bounds(80, 120);
        boundedLayout(frame, fixed, 42, 120);
        boundedLayout(frame, fixed, 202, 120);
        boundedLayout(grid, fixed, 40, 120);
        boundedLayout(grid, fixed, 200, 120);
        boundedLayout(frame, Size.natural().bounds(80, 120), 202, 80);
        // 公共策略的最小槽宽在超窄父盒仍保留，子项自己的 max 继续生效。
        boundedLayout(grid, Size.fill(1).bounds(80, 120), 40, 120);
        boundedLayout(grid, Size.fill(1).bounds(80, 120), 200, 120);
    }

    private static void boundedLayout(java.lang.reflect.Method factory, Size childWidth,
                                      int parentWidth, int expectedWidth) {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        SceneNode[] parent = {null};
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
            SceneNode child = new SceneNode().size(childWidth, Size.fixed(20));
            try {
                parent[0] = factory.getName().equals("grid")
                        ? ((SceneNode) factory.invoke(null, context, theme)).add(child)
                        : (SceneNode) factory.invoke(null, child, theme);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            context.mountOwned(parent[0].size(Size.fixed(parentWidth), Size.natural()));
        })) {
            page.frame();
            domain.execute(() -> {
                SceneGeometryView geometry = page.pipeline().current().geometry;
                SceneNode child = parent[0].children().get(0);
                require(geometry.get(child).box.width == expectedWidth && geometry.get(child).box.height == 20,
                        "真实广场容器覆盖了子尺寸或边界");
            });
        }
    }
    public static void main(String[] args) throws Exception { new GalleryScenario().run(); }
}
