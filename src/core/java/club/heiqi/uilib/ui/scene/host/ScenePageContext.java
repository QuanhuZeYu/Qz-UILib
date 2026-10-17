package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationSource;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 页面作用域与声明接入；外部业务状态和宿主服务仅在明确own时归本次页面。 */
public final class ScenePageContext {
    private final ReactiveDomain domain;
    private final Owner scope;
    private final SceneRuntime runtime;
    private final club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews;
    private LocalizationSource localizationSource;
    private LocalizationBinding localization;
    private final ReadableSignal<ThemeMode> themeMode;
    private final ReadableSignal<SceneTheme> theme;

    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime,
            ThemeConfiguration themes) {
        this(domain, scope, runtime, themes, null);
    }

    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime, ThemeConfiguration themes,
            club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews) {
        this.mediaViews = mediaViews;
        this.domain = domain;
        this.scope = scope;
        this.runtime = runtime;
        themeMode = themes == null ? domain.signal(ThemeMode.LIGHT)
                : themes.bind(domain, scope);
        theme = domain.computed(scope, () -> themeMode.get().sceneTheme());
        runtime.scrollbarTheme(theme);
    }

    /** 应用配置的页面域投影；局部配色可由此派生，不抢占全局主题权威。 */
    public ReadableSignal<ThemeMode> themeMode() { return themeMode; }
    public ReadableSignal<SceneTheme> theme() { return theme; }

    public ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionPolicy> motionPolicy() { return runtime.motionPolicy(); }
    public void motionPolicy(ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionPolicy> source) { runtime.motionPolicy(source); }

    /** 显式选择本页唯一来源，绑定归页面；共享流仅在明确 own 时才由页面关闭。 */
    public LocalizationBinding localization(LocalizationSource source) {
        domain.checkWrite();
        Objects.requireNonNull(source, "source");
        if (localization == null) {
            localization = new LocalizationBinding(domain, scope, source);
            localizationSource = source;
        } else if (localizationSource != source) {
            throw new IllegalStateException("页面语言绑定已选择其他来源");
        }
        return localization;
    }

    /** 页面响应式适配使用宿主根可用空间；普通子项仍由正式布局分配。 */
    public ReadableSignal<Integer> viewportWidth() { return runtime.viewportWidth(); }
    public ReadableSignal<Integer> viewportHeight() { return runtime.viewportHeight(); }

    /** 借用宿主泵；绑定仍随正式 NodeBinding 挂载/卸载，不转移媒体会话所有权。 */
    public club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews() {
        domain.checkAccess();
        if (mediaViews == null) throw new IllegalStateException("宿主未装配媒体呈现服务");
        return mediaViews;
    }

    public ReactiveDomain domain() {
        return domain;
    }

    public Owner scope() {
        domain.checkAccess();
        return scope;
    }

    /** 正式扩展复用同一runtime；普通内容通过mount声明根，不另建运行环境。 */
    public SceneRuntime runtime() {
        domain.checkAccess();
        return runtime;
    }

    public MountHandle mount(SceneNode root) {
        domain.checkWrite();
        return runtime.attach(null, root);
    }

    /** 本次页面拥有根逻辑树；结束挂载后释放，不结束调用方保留的子树。 */
    public MountHandle mountOwned(SceneNode root) {
        domain.checkWrite();
        Objects.requireNonNull(root, "root");
        // 初始化失败同样沿页面已有 scope 清理，保留原错误及清理 suppressed。
        Object logicalScope = new Object();
        scope.own(() -> {
            // 移动已交接生命周期责任，旧页面声明不能销毁新归属中的对象。
            if (root.logicalOwner() == logicalScope) root.destroy();
        });
        return runtime.attachOwned(root, logicalScope);
    }

    public void own(AutoCloseable resource) {
        domain.checkWrite();
        scope.own(resource);
    }
}
