package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.ScenePointerRouter;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 事件顺序、脏声明、几何准备与最终绘制由框架协调；状态无需帧提交。 */
public final class SceneFramePipeline implements AutoCloseable {
    private final SceneRuntime runtime;
    private final SceneHost host;
    private final SceneLayoutEngine layout;
    private final SceneTextMeasurer text;

    private static final class QueuedInput {
        final ScenePointerEvent pointer;
        final SceneNavigationEvent navigation;
        final Runnable fallback;
        club.heiqi.uilib.ui.scene.input.SceneEditEvent edit;
        SceneNavigationEvent navigationFallback;
        boolean consumed, focusLost;

        QueuedInput(ScenePointerEvent pointer, SceneNavigationEvent navigation) {
            this(pointer, navigation, null);
        }

        QueuedInput(ScenePointerEvent pointer, SceneNavigationEvent navigation, Runnable fallback) {
            this.pointer = pointer;
            this.navigation = navigation;
            this.fallback = fallback;
        }

        void discard(ScenePointerRouter router) {
            if (pointer != null) router.discardAfterFailure(pointer);
        }
    }

    private final ArrayDeque<QueuedInput> inputs = new ArrayDeque<>();

    private static final class InputBatch {
        boolean failed, released;
    }

    private int pendingIndependent;

    private void release(InputBatch batch) {
        synchronized (inputs) {
            if (!batch.released) {
                batch.released = true;
                pendingIndependent--;
            }
        }
    }

    private boolean closed;
    private ScenePresentation presentation;
    private long revision = -1, geometryRevision = -1, metrics = -1, generation;
    private int width = -1, height = -1;

    public SceneFramePipeline(SceneRuntime runtime, SceneHost host, SceneTextMeasurer measurer) {
        this.runtime = runtime;
        this.host = host;
        this.text = measurer;
        this.layout = new SceneLayoutEngine(measurer);
    }

    /** 宿主在域外按采集顺序递交typed输入，所有物理按钮均需递交。 */
    public void pointer(ScenePointerEvent event) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(new QueuedInput(Objects.requireNonNull(event, "event"), null));
        }
    }

    /** 宿主失焦与指针、编辑共享采集顺序，不能立即清焦点越过待结算输入。 */
    public void focusLost() {
        synchronized (inputs) {
            checkOpen();
            QueuedInput event = new QueuedInput(null, null);
            event.focusLost = true;
            inputs.add(event);
        }
    }

    /** 与指针共享采集顺序；不从宿主另建焦点表。 */
    public void navigation(SceneNavigationEvent event) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(new QueuedInput(null, Objects.requireNonNull(event, "event")));
        }
    }

    /** 文本与命令使用唯一当前焦点，消费后结算结果再执行宿主fallback。 */
    public void edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event, Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            QueuedInput input = new QueuedInput(null, null, fallback);
            input.edit = Objects.requireNonNull(event, "event");
            inputs.add(input);
        }
    }

    /** 控件未消费时在本事件结算后导航/激活，不将fallback重排到下一帧。 */
    public void editOrNavigate(
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event, SceneNavigationEvent fallback) {
        synchronized (inputs) {
            checkOpen();
            QueuedInput input = new QueuedInput(null, null, () -> {});
            input.edit = Objects.requireNonNull(event, "event");
            input.navigationFallback = Objects.requireNonNull(fallback, "fallback");
            inputs.add(input);
        }
    }

    /** ESC与指针/导航同序；fallback仅在未消费、Effects成功且此管线仍有效时于域内执行。 */
    public void escape(Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(new QueuedInput(null, null, Objects.requireNonNull(fallback, "fallback")));
        }
    }

    /** 完整synthetic activation入口，不是DOWN；已有物理手势时吞掉，不绕过owner。 */
    public void click(int x, int y) {
        pointer(ScenePointerEvent.syntheticActivation(x, y));
    }

    /** 保留现行滚轮行为，与pointer事件共享采集顺序和prepare。 */
    public void scroll(int x, int y, int delta) {
        pointer(ScenePointerEvent.scroll(x, y, delta));
    }

    /**
     * 每事件独立domain.execute→prepare→route，前handler状态影响下一事件。
     * handler失败时取消owner、仅记本批剩余物理尾部并外抛，不丢已采集的第二键。
     */
    public void frame() {
        synchronized (inputs) {
            checkOpen();
        }
        host.prepareResources();
        List<QueuedInput> events = new ArrayList<>();
        synchronized (inputs) {
            checkOpen();
            while (!inputs.isEmpty()) events.add(inputs.remove());
        }
        // ESC批次需要顶层结算后结果；旧纯pointer/nav批次保留域内同步frame语义。
        boolean independent;
        synchronized (inputs) {
            independent =
                    pendingIndependent > 0
                            || events.stream().anyMatch(event -> event.fallback != null);
            if (independent) pendingIndependent++;
        }
        InputBatch batch = new InputBatch();
        for (int i = 0; i < events.size(); i++) {
            final int index = i;
            submit(
                    independent,
                    () -> {
                        if (batch.failed) return;
                        ScenePointerRouter router = runtime.pointerRouter();
                        QueuedInput event = events.get(index);
                        if (isClosed() || !runtime.alive()) {
                            event.discard(router);
                            return;
                        }
                        SceneGeometryView geometry = prepare().geometry;
                        if (event.focusLost) {
                            try { router.route(ScenePointerEvent.cancel(), geometry); }
                            finally { runtime.focusRouter().clear(); }
                        }
                        else if (event.edit != null) event.consumed = runtime.dispatchEdit(event.edit);
                        else if (event.pointer != null) router.route(event.pointer, geometry);
                        else if (event.navigation == SceneNavigationEvent.ACTIVATE)
                            runtime.focusRouter().activate();
                        else if (event.navigation != null)
                            runtime.focusRouter()
                                    .navigate(event.navigation == SceneNavigationEvent.PREVIOUS);
                        else event.consumed = runtime.dispatchEscape();
                    },
                    failure -> {
                        batch.failed = true;
                        if (independent) release(batch);
                        ScenePointerRouter router = runtime.pointerRouter();
                        // 导航后的业务Effect也可能失败；即使剩余只有导航，旧物理owner仍必须取消。
                        router.discardAfterFailure(ScenePointerEvent.cancel());
                        for (int tail = index; tail < events.size(); tail++)
                            events.get(tail).discard(router);
                    });
            QueuedInput event = events.get(index);
            if (event.fallback != null)
                submit(
                        independent,
                        () -> {
                            if (!batch.failed
                                    && !isClosed()
                                    && runtime.alive()
                                    && !event.consumed) {
                                // 导航/按钮fallback沿本事件结算，不能重新排入下一帧越过后续已采集文字。
                                if (event.navigationFallback == SceneNavigationEvent.ACTIVATE)
                                    runtime.focusRouter().activate();
                                else if (event.navigationFallback != null)
                                    runtime.focusRouter()
                                            .navigate(
                                                    event.navigationFallback
                                                            == SceneNavigationEvent.PREVIOUS);
                                else event.fallback.run();
                            }
                        },
                        failure -> {
                            batch.failed = true;
                            if (independent) release(batch);
                            ScenePointerRouter router = runtime.pointerRouter();
                            router.discardAfterFailure(ScenePointerEvent.cancel());
                            for (int tail = index + 1; tail < events.size(); tail++)
                                events.get(tail).discard(router);
                        });
        }
        Runnable replay =
                () -> {
                    try {
                        if (!batch.failed && !isClosed() && runtime.alive())
                            host.replay(prepare().paint);
                    } finally {
                        if (independent) release(batch);
                    }
                };
        if (independent) runtime.domain().enqueue(replay);
        else runtime.domain().execute(replay);
    }

    private void submit(
            boolean independent, Runnable action, java.util.function.Consumer<Throwable> failed) {
        if (independent) runtime.domain().enqueue(action, failed);
        else runtime.domain().execute(action, failed);
    }

    public ScenePresentation current() {
        runtime.domain().checkAccess();
        synchronized (inputs) {
            checkOpen();
        }
        return prepare();
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("帧管线已释放");
    }

    private boolean isClosed() {
        synchronized (inputs) {
            return closed;
        }
    }

    /**
     * 页面关闭时丢弃未处理输入并取消owner，不执行frame或replay；不关闭业务runtime。
     * 生命周期所有者随后关闭runtime；重复close安全，pending域任务也不再访问host。
     */
    @Override
    public void close() {
        List<QueuedInput> pending = new ArrayList<>();
        synchronized (inputs) {
            if (closed) return;
            closed = true;
            while (!inputs.isEmpty()) pending.add(inputs.remove());
        }
        runtime.domain()
                .execute(
                        () -> {
                            ScenePointerRouter router = runtime.pointerRouter();
                            // close不执行业务，但不能丢掉已采集的第二键；另一管线仍共享该物理段。
                            for (QueuedInput event : pending) event.discard(router);
                            router.discardAfterFailure(ScenePointerEvent.cancel());
                            presentation = null;
                        });
    }

    private SceneGeometry layoutGeometry(int w, int h, long metricsRevision) {
        SceneGeometry geometry =
                runtime.domain()
                        .readOnly(
                                () ->
                                        layout.layout(
                                                runtime.roots(),
                                                w,
                                                h,
                                                metricsRevision,
                                                runtime.measureRevision()));
        // 布局只读，唯一runtime记录在布局返回后发布；显露重排也复用这一入口。
        runtime.prepareScrollPositions(geometry);
        return geometry;
    }

    /** 捕获占位仍沿同一控件paint遍历。被捕获子树不再进入主绘制，主输入geometry不变。 */
    private PaintPlan paintGeometry(SceneGeometryView geometry, long metricsRevision) {
        PaintLayers layers = paintEntries(geometry.ordered(), geometry, metricsRevision);
        PaintPlanBuilder result = new PaintPlanBuilder();
        append(result, layers.content);
        append(result, layers.overlay);
        return result.build();
    }

    /** 控件内容保持树序，所有overlay在全页内容之后保持原overlay树序。 */
    private static final class PaintLayers {
        final PaintPlan content, overlay;

        PaintLayers(PaintPlan content, PaintPlan overlay) {
            this.content = content;
            this.overlay = overlay;
        }
    }

    private void append(PaintPlanBuilder target, PaintPlan plan) {
        for (PaintCommand command : plan.commands()) target.add(command);
    }

    private PaintLayers paintEntries(
            List<SceneGeometryView.Entry> ordered,
            SceneGeometryView geometry,
            long metricsRevision) {
        PaintPlanBuilder paint = new PaintPlanBuilder(), overlay = new PaintPlanBuilder();
        java.util.Map<SceneNode, PaintPlan> childOverlays = new java.util.IdentityHashMap<>();
        java.util.Set<SceneNode> captured =
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<SceneNode, Boolean>());
        ArrayDeque<SceneNode> open = new ArrayDeque<>();
        for (SceneGeometryView.Entry entry : ordered) {
            if (insideCaptured(entry.node, captured)) continue;
            // 只消费已定位条目；按唯一树祖先关系反向关闭，不重走逻辑children补出幻影。
            while (!open.isEmpty() && !inSubtree(entry.node, open.peek()))
                paintAfterChildren(open.pop(), geometry, paint);
            open.push(entry.node);
            if (entry.node.control() != null) {
                entry.node.control().preparePaint(entry.node, geometry, text, metricsRevision);
                SceneNode child = entry.node.control().paintFallbackChild(entry.node);
                PaintLayers fallback = null;
                if (child != null) {
                    if (child.parent() != entry.node)
                        throw new IllegalArgumentException("占位必须是控件直属逻辑子树");
                    captured.add(child);
                    SceneGeometryView context = geometry;
                    List<SceneGeometryView.Entry> source = ordered;
                    if (geometry.get(child) == null) {
                        SceneGeometry local =
                                layout.captureSubtree(
                                        child, entry.box, entry.clip, geometry.viewport());
                        source = local.ordered();
                        // get提供已定位备用区域，ordered仍为主输入事实；不能以备用子集重新裁决hover。
                        context =
                                new SceneGeometryView() {
                                    @Override
                                    public Entry get(SceneNode node) {
                                        Entry value = local.get(node);
                                        return value == null ? geometry.get(node) : value;
                                    }

                                    @Override
                                    public List<Entry> ordered() {
                                        return geometry.ordered();
                                    }

                                    @Override
                                    public LayoutBox viewport() {
                                        return geometry.viewport();
                                    }
                                };
                    }
                    List<SceneGeometryView.Entry> entries = new ArrayList<>();
                    for (SceneGeometryView.Entry candidate : source)
                        if (inSubtree(candidate.node, child)) entries.add(candidate);
                    fallback = paintEntries(entries, context, metricsRevision);
                }
                PaintPlanBuilder own = new PaintPlanBuilder();
                entry.node
                        .control()
                        .paint(
                                entry.node,
                                geometry,
                                own,
                                fallback == null ? null : fallback.content);
                PaintPlan ownPlan = own.build();
                append(paint, ownPlan);
                if (fallback != null && !fallback.overlay.commands().isEmpty()) {
                    PaintCommand source = null;
                    for (PaintCommand command : ownPlan.commands())
                        if (command.kind == PaintCommand.Kind.IMAGE
                                && command.pendingPaint == fallback.content
                                && command.failedPaint == fallback.content) {
                            source = command;
                            break;
                        }
                    // CPU没有内容时控件已无条件选择备用；有IMAGE时只投影同一次原图选择。
                    childOverlays.put(
                            entry.node,
                            source == null
                                    ? fallback.overlay
                                    : new PaintPlanBuilder()
                                            .add(
                                                    PaintCommand.imageBranchOverlay(
                                                            source,
                                                            fallback.overlay,
                                                            fallback.overlay))
                                            .build());
                }
                continue;
            }
            if ((entry.node.background() >>> 24) != 0)
                paint.add(PaintCommand.rect(entry.box, entry.clip, entry.node.background()));
            if (!entry.text.lines().isEmpty()) {
                int padding = entry.node.padding();
                paint.add(
                        PaintCommand.text(
                                new LayoutBox(
                                        entry.box.x + padding,
                                        entry.box.y + padding,
                                        Math.max(0, entry.box.width - padding * 2),
                                        Math.max(0, entry.box.height - padding * 2)),
                                entry.clip,
                                entry.text));
            }
        }
        while (!open.isEmpty()) paintAfterChildren(open.pop(), geometry, paint);
        for (SceneGeometryView.Entry entry : ordered)
            if (!insideCaptured(entry.node, captured) && entry.node.control() != null) {
                entry.node.control().paintOverlay(entry.node, geometry, overlay, text);
                PaintPlan childOverlay = childOverlays.get(entry.node);
                if (childOverlay != null) append(overlay, childOverlay);
            }
        return new PaintLayers(paint.build(), overlay.build());
    }

    private void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        if (node.control() != null) node.control().paintAfterChildren(node, geometry, target);
    }

    private boolean insideCaptured(SceneNode node, java.util.Set<SceneNode> captured) {
        if (captured.isEmpty()) return false;
        for (SceneNode current = node; current != null; current = current.parent())
            if (captured.contains(current)) return true;
        return false;
    }

    private boolean inSubtree(SceneNode node, SceneNode root) {
        for (SceneNode current = node; current != null; current = current.parent())
            if (current == root) return true;
        return false;
    }

    private ScenePresentation prepare() {
        runtime.preparePresentation();
        int w = host.width(), h = host.height();
        if (w < 0 || h < 0) throw new IllegalArgumentException("宿主尺寸不能为负");
        long nextMetrics = host.metricsRevision();
        boolean geometryDirty =
                presentation == null
                        || geometryRevision != runtime.geometryRevision()
                        || width != w
                        || height != h
                        || metrics != nextMetrics;
        SceneGeometry prepared =
                geometryDirty ? layoutGeometry(w, h, nextMetrics) : presentation.geometry;
        // 单项目标导航不一定改变paint revision，仍须消费一次显露意图。
        // 此时声明/Effect已结算；换owner或旧mount的意图不会挪动先前目标。
        final SceneGeometry geometry =
                runtime.revealNavigation(prepared, () -> layoutGeometry(w, h, nextMetrics));
        if (presentation == null
                || revision != runtime.revision()
                || geometry != presentation.geometry
                || width != w
                || height != h
                || metrics != nextMetrics) {
            PaintPlan paint = runtime.domain().readOnly(() -> paintGeometry(geometry, nextMetrics));
            presentation = new ScenePresentation(++generation, geometry, paint);
            revision = runtime.revision();
            geometryRevision = runtime.geometryRevision();
            width = w;
            height = h;
            metrics = nextMetrics;
        }
        runtime.pointerRouter().prepare(presentation.geometry);
        return presentation;
    }
}
