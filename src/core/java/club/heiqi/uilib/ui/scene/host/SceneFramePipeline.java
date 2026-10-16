package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
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

    private enum InputKind { POINTER, NAVIGATION, EDIT, FOCUS_LOST, ESCAPE }

    /** 后续动作在当前事件及其Effect结算后执行；对象本身不承担输入路由。 */
    private static final class FollowUp {
        enum Kind { HOST, NAVIGATION }
        final Kind kind;
        final Runnable host;
        final SceneNavigationEvent navigation;

        private FollowUp(Kind kind, Runnable host, SceneNavigationEvent navigation) {
            this.kind = kind;
            this.host = host;
            this.navigation = navigation;
        }

        static FollowUp host(Runnable action) {
            return new FollowUp(Kind.HOST, Objects.requireNonNull(action, "fallback"), null);
        }

        static FollowUp navigation(SceneNavigationEvent event) {
            return new FollowUp(Kind.NAVIGATION, null, Objects.requireNonNull(event, "fallback"));
        }
    }

    private static final class QueuedInput {
        final InputKind kind;
        final ScenePointerEvent pointer;
        final SceneNavigationEvent navigation;
        final SceneEditEvent edit;
        final FollowUp followUp;
        boolean consumed;

        private QueuedInput(InputKind kind, ScenePointerEvent pointer, SceneNavigationEvent navigation,
                            SceneEditEvent edit, FollowUp followUp) {
            this.kind = kind;
            this.pointer = pointer;
            this.navigation = navigation;
            this.edit = edit;
            this.followUp = followUp;
        }

        static QueuedInput pointer(ScenePointerEvent event) {
            return new QueuedInput(InputKind.POINTER, Objects.requireNonNull(event, "event"), null, null, null);
        }

        static QueuedInput navigation(SceneNavigationEvent event) {
            return new QueuedInput(InputKind.NAVIGATION, null, Objects.requireNonNull(event, "event"), null, null);
        }

        static QueuedInput edit(SceneEditEvent event, FollowUp followUp) {
            return new QueuedInput(InputKind.EDIT, null, null, Objects.requireNonNull(event, "event"), followUp);
        }

        static QueuedInput focusLost() {
            return new QueuedInput(InputKind.FOCUS_LOST, null, null, null, null);
        }

        static QueuedInput escape(Runnable fallback) {
            return new QueuedInput(InputKind.ESCAPE, null, null, null, FollowUp.host(fallback));
        }

        boolean preparesPointerRelease() {
            return kind == InputKind.POINTER && pointer.kind == ScenePointerEvent.Kind.UP && pointer.button == 0;
        }

        boolean requiresTopLevelSettlement() {
            return followUp != null || preparesPointerRelease();
        }

        void discard(ScenePointerRouter router) {
            if (kind == InputKind.POINTER) router.discardAfterFailure(pointer);
        }
    }

    private final ArrayDeque<QueuedInput> inputs = new ArrayDeque<>();

    private static final class InputBatch {
        final SceneRuntime.InputFrame activeInput = new SceneRuntime.InputFrame();
        final List<QueuedInput> events;
        final boolean independent;
        final long frameNanos = System.nanoTime();
        boolean failed, released;

        InputBatch(List<QueuedInput> events, boolean independent) {
            this.events = events;
            this.independent = independent;
        }
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
    private long preparedNanos;

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
            inputs.add(QueuedInput.pointer(event));
        }
    }

    /** 宿主失焦与指针、编辑共享采集顺序，不能立即清焦点越过待结算输入。 */
    public void focusLost() {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.focusLost());
        }
    }

    /** 与指针共享采集顺序；不从宿主另建焦点表。 */
    public void navigation(SceneNavigationEvent event) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.navigation(event));
        }
    }

    /** 文本与命令使用唯一当前焦点，消费后结算结果再执行宿主fallback。 */
    public void edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event, Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.edit(event, fallback == null ? null : FollowUp.host(fallback)));
        }
    }

    /** 控件未消费时在本事件结算后导航/激活，不将fallback重排到下一帧。 */
    public void editOrNavigate(
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event, SceneNavigationEvent fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.edit(event, FollowUp.navigation(fallback)));
        }
    }

    /** ESC与指针/导航同序；fallback仅在未消费、Effects成功且此管线仍有效时于域内执行。 */
    public void escape(Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.escape(fallback));
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
        InputBatch batch = takeBatch();
        for (int i = 0; i < batch.events.size(); i++) {
            final int index = i;
            QueuedInput event = batch.events.get(index);
            // 释放坐标先进入位置意图；独立顶层操作使其 Effect 在 UP 前结算，随后 route 正式重排。
            if (event.preparesPointerRelease())
                submit(batch.independent, () -> preparePointerRelease(batch, event), failure -> failBatch(batch, index));
            // 路由失败仍须记录当前物理事件；后续动作失败时当前事件已结算。
            submit(batch.independent, () -> route(batch, event), failure -> failBatch(batch, index));
            if (event.followUp != null)
                submit(batch.independent, () -> followUp(batch, event), failure -> failBatch(batch, index + 1));
        }
        Runnable replay = () -> replay(batch);
        submit(batch.independent, replay, failure -> failBatch(batch, batch.events.size()));
    }

    private InputBatch takeBatch() {
        List<QueuedInput> events = new ArrayList<>();
        synchronized (inputs) {
            checkOpen();
            while (!inputs.isEmpty()) events.add(inputs.remove());
        }
        // 有后续动作或释放前位置准备的事件需要顶层Effect结算；已排队批次不能被同步批次越过。
        synchronized (inputs) {
            boolean independent = pendingIndependent > 0
                    || events.stream().anyMatch(QueuedInput::requiresTopLevelSettlement);
            if (independent) pendingIndependent++;
            return new InputBatch(events, independent);
        }
    }

    private void preparePointerRelease(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive()) return;
        runtime.pointerRouter().prepareRelease(event.pointer, prepare(batch.frameNanos).geometry);
    }

    private void route(InputBatch batch, QueuedInput event) {
        if (batch.failed) return;
        ScenePointerRouter router = runtime.pointerRouter();
        if (isClosed() || !runtime.alive()) {
            event.discard(router);
            return;
        }
        SceneGeometryView geometry = prepare(batch.frameNanos).geometry;
        switch (event.kind) {
            case FOCUS_LOST:
                try { router.route(ScenePointerEvent.cancel(), geometry); }
                finally { runtime.focusRouter().clear(); }
                break;
            case EDIT:
                event.consumed = runtime.dispatchEdit(event.edit);
                break;
            case POINTER:
                router.route(event.pointer, geometry);
                break;
            case NAVIGATION:
                navigate(event.navigation);
                break;
            case ESCAPE:
                event.consumed = runtime.dispatchEscape();
                break;
        }
    }

    private void followUp(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive() || event.consumed) return;
        // 沿当前事件结算，不能重新排到下一帧而越过已采集的文字。
        switch (event.followUp.kind) {
            case HOST:
                event.followUp.host.run();
                break;
            case NAVIGATION:
                navigate(event.followUp.navigation);
                break;
        }
    }

    private void navigate(SceneNavigationEvent event) {
        if (event == SceneNavigationEvent.ACTIVATE) {
            if (!runtime.dispatchEdit(SceneEditEvent.command(SceneEditEvent.Command.ACTIVATE_ONLY, false, null)))
                runtime.focusRouter().activate();
        }
        else runtime.focusRouter().navigate(event == SceneNavigationEvent.PREVIOUS);
    }

    private void failBatch(InputBatch batch, int firstDiscard) {
        batch.failed = true;
        if (batch.independent) release(batch);
        ScenePointerRouter router = runtime.pointerRouter();
        // 导航后的Effect也可能失败；即使剩余只有导航，旧物理owner仍必须取消。
        Throwable cleanupFailure = null;
        try {
            router.discardAfterFailure(ScenePointerEvent.cancel());
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        try {
            runtime.cancelInputFrame(batch.activeInput);
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) cleanupFailure = failure;
            else if (failure != cleanupFailure && !java.util.Arrays.asList(cleanupFailure.getSuppressed()).contains(failure))
                cleanupFailure.addSuppressed(failure);
        }
        // captureLost 扩展可能失败；仍须登记完整物理尾部，避免丢 UP 或第二 DOWN。
        for (int tail = firstDiscard; tail < batch.events.size(); tail++) {
            try {
                batch.events.get(tail).discard(router);
            } catch (RuntimeException | Error failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else if (failure != cleanupFailure) cleanupFailure.addSuppressed(failure);
            }
        }
        // 交给原失败边界保留业务原异常，并将清理异常作为 suppressed 传播。
        if (cleanupFailure instanceof Error) throw (Error) cleanupFailure;
        if (cleanupFailure instanceof RuntimeException) throw (RuntimeException) cleanupFailure;
    }

    private void replay(InputBatch batch) {
        try {
            if (!batch.failed && !isClosed() && runtime.alive()) host.replay(prepare(batch.frameNanos, batch.activeInput).paint);
        } finally {
            if (batch.independent) release(batch);
        }
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
        // 读取当前呈现不推进几何动画；下一宿主frame才提供新的时间样本。
        return prepare(presentation == null ? System.nanoTime() : preparedNanos);
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

    private ScenePresentation prepare(long frameNanos) { return prepare(frameNanos, null); }

    private ScenePresentation prepare(long frameNanos, SceneRuntime.InputFrame activeInput) {
        return prepare(frameNanos, activeInput, true);
    }

    private ScenePresentation prepare(long frameNanos, SceneRuntime.InputFrame activeInput, boolean advanceInput) {
        preparedNanos = frameNanos;
        int w = host.width(), h = host.height();
        runtime.prepareViewport(w, h);
        runtime.preparePresentation(frameNanos);
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
            runtime.preparePresentation(frameNanos);
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
            if (geometryDirty) continue;
            // 单项导航也消费一次意图；其滚动或范围投影仍在同一声明结算循环中闭合。
            prepared = runtime.revealNavigation(prepared, () -> {
                SceneGeometry updated = layoutGeometry(w, h, nextMetrics);
                arrangedRevision[0] = runtime.geometryRevision();
                runtime.prepareInputGeometry(updated);
                return updated;
            });
            runtime.preparePresentation(frameNanos);
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
        } while (geometryDirty);
        if (presentation == null || revision != runtime.revision() || prepared != presentation.geometry) {
            SceneGeometry located = prepared;
            prepared = runtime.domain().readOnly(() -> layout.refreshTextStyles(located));
            runtime.prepareInputGeometry(prepared);
        }
        if (activeInput != null) {
            long beforeInput = runtime.revision();
            if (advanceInput) runtime.advanceInputFrame(prepared, frameNanos, activeInput);
            else runtime.refreshInputFrameGeometry(prepared, activeInput);
            // 普通 value 绑定只登记 dirtyBindings，先结算才能判断命中样式是否使几何/绘制失效。
            runtime.preparePresentation(frameNanos);
            // 时间及滚动只推进一次；后续重排仅同步本批参与者，使候选与最终画面同代。
            // 同步产生的呈现依赖仍沿原结算入口收敛，失败复用同一 activeInput 取消记录。
            if (beforeInput != runtime.revision()) return prepare(frameNanos, activeInput, false);
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
