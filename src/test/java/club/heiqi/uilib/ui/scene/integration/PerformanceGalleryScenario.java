package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.PerformanceGallery;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/** 实际字体、正式输入与管线；无GL，结果不能外推为GPU或MC性能。 */
public final class PerformanceGalleryScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new PerformanceGalleryScenario().run(args); }
    @Override protected void runTests() throws Exception {
        int scale = Integer.getInteger("performance.scale", 50);
        int warmup = Integer.getInteger("performance.warmup", 30);
        int frames = Integer.getInteger("performance.frames", 120);
        Path output = Paths.get(System.getProperty("performance.output", "build/reports/performance-core"));
        Files.createDirectories(output);
        FontTextService fonts = own(new FontTextService(FontTextService.defaultSources(getClass().getClassLoader(), 1), club.heiqi.uilib.host.font.IsolatedFontEngine.shared()));
        MeasuredText text = new MeasuredText(fonts);
        ReactiveDomain domain = ReactiveDomain.direct();
        final int[] sequence = {0};
        PerformanceGallery gallery = new PerformanceGallery("core-real-font", "actions+session.frame; replay=capture-only; no GPU",
                System.getProperty("uilib.version", "unknown"), json -> {
                    try { Files.write(output.resolve(String.format("core-run-%03d.json", sequence[0]++)), json.getBytes(StandardCharsets.UTF_8)); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
        SceneHost host = new SceneHost() {
            public int width() { return 1100; }
            public int height() { return 800; }
            public long metricsRevision() { return 1; }
            public void replay(PaintPlan plan) { if (plan.commands().isEmpty()) throw new AssertionError("压测画面为空"); }
        };
        ScenePageSession session = own(ScenePageSession.open(domain, host, text, gallery));
        session.frame();
        PerformanceGallery.Driver driver = new PerformanceGallery.Driver() {
            int x, y;
            public void initialize(SceneNode target) {
                domain.execute(() -> {
                    SceneGeometryView.Entry e = session.pipeline().current().geometry.get(target);
                    LayoutBox b = e.box;
                    x = b.x + (target.padding() > 0 ? 2 : Math.max(1, b.width / 2)); y = b.y + Math.max(1, Math.min(20, b.height / 2));
                    if (target.scrollable()) require(e.scrollMaximum > 0, "压力列表/文档未超出实际视口");
                });
                session.pointer(ScenePointerEvent.move(x, y));
                if (!target.scrollable()) {
                    session.pointer(ScenePointerEvent.down(0, x, y)); session.pointer(ScenePointerEvent.up(0, x, y));
                }
            }
            public void scroll(int delta) { session.pointer(ScenePointerEvent.scroll(x, y, delta)); }
            public void edit(String input, SceneEditEvent.Command command) {
                session.edit(input == null ? SceneEditEvent.command(command, false, null) : SceneEditEvent.text(input));
            }
        };
        for (PerformanceGallery.Load load : PerformanceGallery.Load.values()) scenario(load.name(), () -> {
            gallery.start(load, scale, warmup, frames);
            long sampleCalls = 0, sampleTextNanos = 0; int rendered = 0;
            while (gallery.running()) {
                long calls = text.calls, nanos = text.nanos; int sampled = gallery.sampleCount();
                gallery.frame(session::frame, driver, host.width(), host.height()); rendered++;
                if (gallery.sampleCount() > sampled) { sampleCalls += text.calls - calls; sampleTextNanos += text.nanos - nanos; }
            }
            require(gallery.sampleCount() == frames && rendered == 1 + warmup + frames, "首帧/预热/采样计数不一致");
            require(gallery.lastReport().contains("\"pathObserved\":true"), "真实负载未发生变化：" + load);
            String metric = "{\"load\":\"" + load + "\",\"scale\":" + scale + ",\"sampleFrames\":" + frames
                    + ",\"textRequests\":" + sampleCalls + ",\"textRequestNanos\":" + sampleTextNanos + "}";
            Files.write(output.resolve(load.name().toLowerCase(java.util.Locale.ROOT) + "-text.json"), metric.getBytes(StandardCharsets.UTF_8));
            System.out.println("PERFORMANCE " + metric);
        });
        scenario("中断与重跑隔离", () -> {
            gallery.start(PerformanceGallery.Load.IDLE, 50, 0, 3);
            gallery.frame(session::frame, driver, host.width(), host.height()); gallery.stop();
            require(gallery.lastReport().contains("\"complete\":false"), "中断被记作完成");
            require(gallery.target() == null, "停止残留压力树引用");
            session.frame();
            gallery.start(PerformanceGallery.Load.IDLE, 50, 0, 3);
            while (gallery.running()) gallery.frame(session::frame, driver, host.width(), host.height());
            require(gallery.sampleCount() == 3, "样本串轮");
        });
        scenario("关闭滚动负载中断与释放", () -> {
            gallery.start(PerformanceGallery.Load.SCROLL, 50, 0, 3);
            gallery.frame(session::frame, driver, host.width(), host.height());
            session.close();
            require(!gallery.running() && gallery.target() == null, "关闭残留负载");
            require(gallery.lastReport().contains("\"complete\":false"), "关闭未保存中断身份");
        });
    }
    private static final class MeasuredText implements SceneTextMeasurer {
        final SceneTextMeasurer delegate; long calls, nanos;
        MeasuredText(SceneTextMeasurer delegate) { this.delegate = delegate; }
        public TextStyle resolveStyle(TextStyle style) { return delegate.resolveStyle(style); }
        public MathFont mathFont(TextStyle style) { return delegate.mathFont(style); }
        public TextLayout layout(String text, TextStyle style, int width) {
            long begin = System.nanoTime(); calls++;
            try { return delegate.layout(text, style, width); } finally { nanos += System.nanoTime() - begin; }
        }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            long begin = System.nanoTime(); calls++;
            try { return delegate.layout(text, style, width, policy); } finally { nanos += System.nanoTime() - begin; }
        }
        public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
            long begin = System.nanoTime(); calls++;
            try { return delegate.layout(text, width, policy); } finally { nanos += System.nanoTime() - begin; }
        }
    }
}
