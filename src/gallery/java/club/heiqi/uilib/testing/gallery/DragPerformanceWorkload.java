package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 独立拖拽压测声明；使用正式长列表和Draggable，不扩充原性能广场的全负载集合。 */
public final class DragPerformanceWorkload implements ScenePage {
    private final int scale;
    private SceneDraggable.State state;
    private SceneNode firstContent;
    private int contentLength;
    private long declarationNanos;

    public DragPerformanceWorkload(int scale) {
        if (scale != 50 && scale != 200 && scale != 1000) throw new IllegalArgumentException("规模须为50/200/1000");
        this.scale = scale;
    }

    @Override public void build(ScenePageContext context) {
        long begin = System.nanoTime();
        ReadableSignal<SceneTheme> theme = context.domain().computed(context.scope(),
                () -> GalleryStyle.theme(context.themeMode().get() == club.heiqi.uilib.ui.scene.control.ThemeMode.DARK));
        SceneNode list = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(16).gap(5).scrollable(true).clip(true);
        for (int i = 0; i < scale; i++) {
            String text = "条目 " + i + " / 字体排序材料核对 Café é العربية אבג · 工坊记录";
            contentLength += text.length();
            SceneNode body = new SceneNode().size(Size.fill(1), Size.natural()).padding(10)
                    .background(i % 2 == 0 ? 0xffeef2ff : 0xfff8fafc);
            body.add(GalleryStyle.label(context, () -> text, theme, 15, false).size(Size.fill(1), Size.natural()));
            SceneDraggable.State current = SceneDraggable.state(context.domain());
            list.add(SceneDraggable.create(context, body, current).size(Size.fill(1), Size.natural()));
            if (i == 0) { state = current; firstContent = body; }
        }
        context.mountOwned(list);
        declarationNanos = System.nanoTime() - begin;
    }

    public SceneNode firstContent() { return firstContent; }
    /** 只在页面域内、宿主计时结束后读取。 */
    public boolean active() { return state.snapshot().get().active; }
    public String report(String version, int warmup, int frames, boolean complete, int observedChanges,
            int width, int height, long firstFrameNanos, PerformanceSamples samples,
            PerformanceReport.Snapshot before, PerformanceReport.Snapshot after) {
        return PerformanceReport.json("desktop-glfw-hidden",
                "actions+complete window.frame; GLFW callbacks,poll,clear,presentation,swap; vsync=0; no independent GPU timer",
                version, "DRAGGABLE_LONG_LIST", scale, warmup, frames, complete, observedChanges > 0,
                observedChanges, contentLength, width, height, declarationNanos, firstFrameNanos, samples, before, after);
    }
}
