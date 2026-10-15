package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
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
    private final ScenePaintCollector paint;

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
        this.layout = new SceneLayoutEngine(measurer, runtime::scrollOffset, runtime::nodeMeasureRevision);
        this.paint = new ScenePaintCollector(layout, measurer);
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
                            try {
                                for (QueuedInput event : pending) event.discard(router);
                                router.discardAfterFailure(ScenePointerEvent.cancel());
                            } finally {
                                presentation = null;
                                layout.clearCaches();
                            }
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
                                                runtime.wholeMeasureRevision()));
        // 布局只读，唯一runtime记录在布局返回后发布；显露重排也复用这一入口。
        runtime.prepareScrollPositions(geometry);
        return geometry;
    }

    private ScenePresentation prepare() {
        int w = host.width(), h = host.height();
        runtime.prepareViewport(w, h);
        runtime.preparePresentation();
        long nextMetrics = host.metricsRevision();
        boolean geometryDirty =
                presentation == null
                        || geometryRevision != runtime.geometryRevision()
                        || width != w
                        || height != h
                        || metrics != nextMetrics;
        SceneGeometry prepared = geometryDirty ? null : presentation.geometry;
        long[] arrangedRevision = {runtime.geometryRevision()};
        do {
            if (geometryDirty) {
                prepared = layoutGeometry(w, h, nextMetrics);
                arrangedRevision[0] = runtime.geometryRevision();
            }
            runtime.prepareInputGeometry(prepared);
            // 范围投影沿原绑定结算；只有其真实布局依赖变化才继续求下一批事实。
            runtime.preparePresentation();
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
            if (geometryDirty) continue;
            // 单项导航也消费一次意图；其滚动或范围投影仍在同一声明结算循环中闭合。
            prepared = runtime.revealNavigation(prepared, () -> {
                SceneGeometry updated = layoutGeometry(w, h, nextMetrics);
                arrangedRevision[0] = runtime.geometryRevision();
                runtime.prepareInputGeometry(updated);
                return updated;
            });
            runtime.preparePresentation();
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
        } while (geometryDirty);
        if (presentation == null || revision != runtime.revision() || prepared != presentation.geometry) {
            SceneGeometry located = prepared;
            prepared = runtime.domain().readOnly(() -> layout.refreshTextStyles(located));
            runtime.prepareInputGeometry(prepared);
        }
        final SceneGeometry geometry = prepared;
        if (presentation == null
                || revision != runtime.revision()
                || geometry != presentation.geometry
                || width != w
                || height != h
                || metrics != nextMetrics
                || System.nanoTime() >= paint.nextPaintNanos()) {
            PaintPlan paint = runtime.domain().readOnly(() -> this.paint.collect(geometry, nextMetrics));
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
