package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 正式页面运行环境；软件/GPU/游戏共用装配、事件结算与结束。
 * 初始化是独立域操作，构造末尾Effect失败也归同一清理边界；不依赖UI帧物化状态。 */
public final class ScenePageSession implements AutoCloseable {
    private final ReactiveDomain domain;
    private volatile boolean ended;
    private volatile SceneRuntime runtime;
    private volatile SceneFramePipeline pipeline;
    private Owner scope;

    private ScenePageSession(ReactiveDomain domain) {
        this.domain = domain;
    }

    public static ScenePageSession open(
            ReactiveDomain domain, SceneHost host, SceneTextMeasurer text, ScenePage page) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(page, "page");
        ScenePageSession session = new ScenePageSession(domain);
        domain.enqueue(
                () -> {
                    if (session.ended) return;
                    session.scope = domain.owner();
                    session.runtime = new SceneRuntime(domain);
                    session.runtime.prepareViewport(host.width(), host.height());
                    session.pipeline = new SceneFramePipeline(session.runtime, host, text);
                    page.build(new ScenePageContext(domain, session.scope, session.runtime, host.themeConfiguration(), host.mediaViews()));
                },
                failure -> {
                    session.ended = true;
                    session.release();
                });
        return session;
    }

    public ReactiveDomain domain() {
        return domain;
    }

    public boolean ended() {
        return ended;
    }

    /** 供正式扩展与底层合同验收观察同一权威；普通页面无需装配或关闭它们。 */
    public SceneRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("页面尚未初始化");
        return runtime;
    }

    public SceneFramePipeline pipeline() {
        if (pipeline == null) throw new IllegalStateException("页面尚未初始化");
        return pipeline;
    }

    private void dispatch(Runnable action) {
        domain.checkDispatch();
        if (ended) return;
        // 外部executor尚未构造时，动作排在初始化之后；失败/关闭使迟到动作失效。
        if (pipeline == null)
            domain.enqueue(
                    () -> {
                        if (!ended) action.run();
                    });
        else action.run();
    }

    public void pointer(ScenePointerEvent event) {
        Objects.requireNonNull(event, "event");
        dispatch(() -> pipeline().pointer(event));
    }

    /** 原生窗口失焦按输入采集顺序取消手势并清理键盘焦点。 */
    public void focusLost() {
        dispatch(() -> pipeline().focusLost());
    }

    public void navigation(SceneNavigationEvent event) {
        Objects.requireNonNull(event, "event");
        dispatch(() -> pipeline().navigation(event));
    }

    public void edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event) {
        Objects.requireNonNull(event, "event");
        dispatch(() -> pipeline().edit(event, null));
    }

    public void edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event, Runnable fallback) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(fallback, "fallback");
        dispatch(
                () ->
                        pipeline()
                                .edit(
                                        event,
                                        () -> {
                                            if (!ended) fallback.run();
                                        }));
    }

    /** 标准宿主输入回退在同一次事件中结算，避免后续文字投递旧焦点。 */
    public void editOrNavigate(
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event, SceneNavigationEvent fallback) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(fallback, "fallback");
        dispatch(() -> pipeline().editOrNavigate(event, fallback));
    }

    public void escape(Runnable fallback) {
        Objects.requireNonNull(fallback, "fallback");
        dispatch(
                () ->
                        pipeline()
                                .escape(
                                        () -> {
                                            if (!ended) fallback.run();
                                        }));
    }

    /** 宿主绘制/resize均从同一入口驱动；已结束的迟到绘制不访问资源。 */
    public void frame() {
        dispatch(() -> pipeline().frame());
    }

    @Override
    public void close() {
        domain.checkDispatch();
        if (ended) return;
        ended = true;
        // 先立即关闭输入队列，清理树延到handler之后；即使handler/Effect失败也执行。
        Throwable failure = null;
        try {
            if (pipeline != null) pipeline.close();
        } catch (RuntimeException | Error next) {
            failure = next;
        }
        // 即使取消/Effect失败也尝试其余释放；保留最先失败，不在Error后留下页面资源。
        final Throwable first = failure;
        try {
            domain.deferCleanup(this::release);
        } catch (RuntimeException | Error next) {
            if (first == null) throw next;
            if (next != first) first.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    private void release() {
        Throwable failure = null;
        AutoCloseable[] resources = {pipeline, runtime, scope};
        pipeline = null;
        runtime = null;
        scope = null;
        for (AutoCloseable resource : resources)
            if (resource != null) {
                try {
                    resource.close();
                } catch (Throwable next) {
                    if (failure == null) failure = next;
                    else if (next != failure) failure.addSuppressed(next);
                }
            }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("页面释放失败", failure);
    }
}
