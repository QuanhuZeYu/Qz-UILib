package club.heiqi.uilib.ui.scene.host;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.runtime.ScenePointerRouter;
import java.util.Objects;
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
public final class SceneFramePipeline implements AutoCloseable {
    private final SceneRuntime runtime;
    private final SceneHost host;
    private final SceneLayoutEngine layout;
    private final ArrayDeque<ScenePointerEvent> inputs = new ArrayDeque<>();
    private static final class InputBatch { boolean failed; }
    private boolean closed;
    private ScenePresentation presentation;
    private long revision = -1, geometryRevision = -1, metrics = -1, generation;
    private int width = -1, height = -1;
    public SceneFramePipeline(SceneRuntime runtime, SceneHost host, SceneTextMeasurer measurer) {
        this.runtime = runtime; this.host = host; this.layout = new SceneLayoutEngine(measurer);
    }
    /** 宿主在域外按采集顺序递交typed输入，所有物理按钮均需递交。 */
    public void pointer(ScenePointerEvent event) { synchronized (inputs) { checkOpen(); inputs.add(Objects.requireNonNull(event, "event")); } }
    /** 完整synthetic activation入口，不是DOWN；已有物理手势时吞掉，不绕过owner。 */
    public void click(int x, int y) { pointer(ScenePointerEvent.syntheticActivation(x, y)); }
    /** 保留现行滚轮行为，与pointer事件共享采集顺序和prepare。 */
    public void scroll(int x, int y, int delta) { pointer(ScenePointerEvent.scroll(x, y, delta)); }
    /** 每事件独立domain.execute→prepare→route，前handler状态影响下一事件。
     * handler失败时取消owner、仅记本批剩余物理尾部并外抛，不丢已采集的第二键。 */
    public void frame() {
        List<ScenePointerEvent> events = new ArrayList<>();
        synchronized (inputs) { checkOpen(); while (!inputs.isEmpty()) events.add(inputs.remove()); }
        InputBatch batch = new InputBatch();
        for (int i = 0; i < events.size(); i++) {
            final int index = i;
            runtime.domain().execute(() -> {
                if (batch.failed) return;
                ScenePointerRouter router = runtime.pointerRouter();
                if (isClosed()) { router.discardAfterFailure(events.get(index)); return; }
                router.route(events.get(index), prepare().geometry);
            }, failure -> {
                batch.failed = true;
                ScenePointerRouter router = runtime.pointerRouter();
                for (int tail = index; tail < events.size(); tail++) router.discardAfterFailure(events.get(tail));
            });
        }
        runtime.domain().execute(() -> { if (!batch.failed && !isClosed()) host.replay(prepare().paint); });
    }
    public ScenePresentation current() { runtime.domain().checkAccess(); synchronized (inputs) { checkOpen(); } return prepare(); }
    private void checkOpen() { if (closed) throw new IllegalStateException("帧管线已释放"); }
    private boolean isClosed() { synchronized (inputs) { return closed; } }
    /** 页面关闭时丢弃未处理输入并取消owner，不执行frame或replay；不关闭业务runtime。
     * 生命周期所有者随后关闭runtime；重复close安全，pending域任务也不再访问host。 */
    @Override public void close() {
        List<ScenePointerEvent> pending = new ArrayList<>();
        synchronized (inputs) {
            if (closed) return; closed = true;
            while (!inputs.isEmpty()) pending.add(inputs.remove());
        }
        runtime.domain().execute(() -> {
            ScenePointerRouter router = runtime.pointerRouter();
            // close不执行业务，但不能丢掉已采集的第二键；另一管线仍共享该物理段。
            for (ScenePointerEvent event : pending) router.discardAfterFailure(event);
            router.discardAfterFailure(ScenePointerEvent.cancel());
            presentation = null;
        });
    }
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
        runtime.pointerRouter().prepare(presentation.geometry);
        return presentation;
    }
}
