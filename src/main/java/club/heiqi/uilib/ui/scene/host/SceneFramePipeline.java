package club.heiqi.uilib.ui.scene.host;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
/** 事件顺序、脏声明、几何准备与最终绘制由框架协调；状态无需帧提交。 */
public final class SceneFramePipeline {
    private final SceneRuntime runtime;
    private final SceneHost host;
    private final SceneLayoutEngine layout;
    private final SceneHitTester hits = new SceneHitTester();
    private final ArrayDeque<int[]> clicks = new ArrayDeque<>();
    private ScenePresentation presentation;
    private long revision = -1, geometryRevision = -1, metrics = -1, generation;
    private int width = -1, height = -1;
    public SceneFramePipeline(SceneRuntime runtime, SceneHost host, SceneTextMeasurer measurer) {
        this.runtime = runtime; this.host = host; this.layout = new SceneLayoutEngine(measurer);
    }
    public void click(int x, int y) { synchronized (clicks) { clicks.add(new int[] {0, x, y, 0}); } }
    public void scroll(int x, int y, int delta) { synchronized (clicks) { clicks.add(new int[] {1, x, y, delta}); } }
    /** 调用方在域外提交；每个事件为独立业务操作，随后 prepare 读取当前来源。 */
    public void frame() {
        List<int[]> events = new ArrayList<>();
        synchronized (clicks) { while (!clicks.isEmpty()) events.add(clicks.remove()); }
        for (int[] point : events) runtime.domain().execute(() -> {
            ScenePresentation current = prepare();
            SceneNode hit = point[0] == 0 ? hits.hit(runtime, current.geometry, point[1], point[2]) : hits.hitScroll(runtime, current.geometry, point[1], point[2]);
            if (hit != null) { if (point[0] == 0) runtime.dispatchClick(hit); else runtime.dispatchScroll(hit, point[3]); }
        });
        runtime.domain().execute(() -> host.replay(prepare().paint));
    }
    public ScenePresentation current() { runtime.domain().checkAccess(); return prepare(); }
    private ScenePresentation prepare() {
        runtime.preparePresentation();
        int w = host.width(), h = host.height();
        if (w < 0 || h < 0) throw new IllegalArgumentException("宿主尺寸不能为负");
        long nextMetrics = host.metricsRevision();
        if (presentation == null || revision != runtime.revision() || width != w || height != h || metrics != nextMetrics) {
            boolean geometryDirty = presentation == null || geometryRevision != runtime.geometryRevision() || width != w || height != h || metrics != nextMetrics;
            SceneGeometry geometry = geometryDirty ? runtime.domain().readOnly(() -> layout.layout(runtime.roots(), w, h)) : presentation.geometry;
            if (geometryDirty) runtime.prepareScrollPositions(geometry);
            PaintPlanBuilder paint = new PaintPlanBuilder();
            for (SceneGeometryView.Entry entry : geometry.ordered()) {
                if (entry.node.control() != null) {
                    runtime.domain().readOnly(() -> { entry.node.control().paint(entry.node, geometry, paint); return null; });
                    continue;
                }
                if ((entry.node.background() >>> 24) != 0) paint.add(PaintCommand.rect(entry.box, entry.clip, entry.node.background()));
                if (!entry.text.lines().isEmpty()) {
                    int padding = entry.node.padding();
                    paint.add(PaintCommand.text(new LayoutBox(entry.box.x + padding, entry.box.y + padding,
                        Math.max(0, entry.box.width - padding * 2), Math.max(0, entry.box.height - padding * 2)), entry.clip, entry.text));
                }
            }
            presentation = new ScenePresentation(++generation, geometry, paint.build());
            revision = runtime.revision(); geometryRevision = runtime.geometryRevision(); width = w; height = h; metrics = nextMetrics;
        }
        return presentation;
    }
}
