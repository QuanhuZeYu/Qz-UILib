package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.gallery.GalleryJourney;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
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
                try (ScenePageSession session = ScenePageSession.open(domain, host, fonts, new TestGallery(() -> images))) {
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
                    System.out.println("GALLERY host=automatic run="+number+" sharedDeclaration=true sharedJourney=true");
                }
            });
        }
    }
    private void boundedLayouts() throws Exception {
        // 仅取得现有包内声明工厂；实际 Frame/Grid、子测量和最终布局均用生产实现。
        Class<?> style = Class.forName("club.heiqi.uilib.testing.gallery.GalleryStyle");
        java.lang.reflect.Method frame = style.getDeclaredMethod("frame", SceneNode.class,
                ReadableSignal.class);
        java.lang.reflect.Method grid = style.getDeclaredMethod("grid");
        frame.setAccessible(true);
        grid.setAccessible(true);
        ReactiveDomain domain = ReactiveDomain.direct();
        Signal<SceneTheme>[] theme =
                new Signal[1];
        domain.execute(() -> theme[0] = domain.signal(SceneTheme.light()));
        Size fixed = Size.fixed(120).bounds(80, 120);
        boundedLayout(domain, (SceneNode) frame.invoke(null, new SceneNode().size(fixed, Size.fixed(20)), theme[0]), 42, 120);
        boundedLayout(domain, (SceneNode) frame.invoke(null, new SceneNode().size(fixed, Size.fixed(20)), theme[0]), 202, 120);
        boundedLayout(domain, ((SceneNode) grid.invoke(null)).add(new SceneNode().size(fixed, Size.fixed(20))), 40, 120);
        boundedLayout(domain, ((SceneNode) grid.invoke(null)).add(new SceneNode().size(fixed, Size.fixed(20))), 200, 120);
        boundedLayout(domain, (SceneNode) frame.invoke(null, new SceneNode().size(Size.natural().bounds(80, 120), Size.fixed(20)), theme[0]), 202, 80);
        boundedLayout(domain, ((SceneNode) grid.invoke(null)).add(new SceneNode().size(Size.fill(1).bounds(80, 120), Size.fixed(20))), 40, 80);
        boundedLayout(domain, ((SceneNode) grid.invoke(null)).add(new SceneNode().size(Size.fill(1).bounds(80, 120), Size.fixed(20))), 200, 120);
    }

    private static void boundedLayout(ReactiveDomain domain, SceneNode parent, int parentWidth, int expectedWidth) {
        parent.size(Size.fixed(parentWidth), Size.natural());
        domain.execute(() -> {
            SceneGeometryView geometry = new SceneLayoutEngine(new SoftwareSceneScenario.SoftwareHost())
                    .layout(java.util.Collections.singletonList(parent), 300, 300);
            SceneNode child = parent.children().get(0);
            require(geometry.get(child).box.width == expectedWidth && geometry.get(child).box.height == 20,
                    "真实广场容器覆盖了子尺寸或边界");
        });
    }
    public static void main(String[] args) throws Exception { new GalleryScenario().run(); }
}
