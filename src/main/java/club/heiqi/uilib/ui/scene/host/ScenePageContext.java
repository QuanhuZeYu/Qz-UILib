package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 页面作用域与声明接入；外部业务状态和宿主服务仅在明确own时归本次页面。 */
public final class ScenePageContext {
    private final ReactiveDomain domain;
    private final Owner scope;
    private final SceneRuntime runtime;
    private final SceneHost host;
    private club.heiqi.uilib.ui.scene.localization.LocalizationBinding localization;
    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime, SceneHost host) {
        this.domain = domain; this.scope = scope; this.runtime = runtime; this.host = host;
    }
    /** 每次页面惰性拥有一个绑定；共享宿主来源仍归宿主生命周期。 */
    public club.heiqi.uilib.ui.scene.localization.LocalizationBinding localization() {
        domain.checkWrite();
        if (localization == null) localization = new club.heiqi.uilib.ui.scene.localization.LocalizationBinding(domain, scope, host.localization());
        return localization;
    }
    public ReactiveDomain domain() { return domain; }
    public Owner scope() { domain.checkAccess(); return scope; }
    /** 正式扩展复用同一runtime；普通内容通过mount声明根，不另建运行环境。 */
    public SceneRuntime runtime() { domain.checkAccess(); return runtime; }
    public MountHandle mount(SceneNode root) { domain.checkWrite(); return runtime.attach(null, root); }
    public void own(AutoCloseable resource) { domain.checkWrite(); scope.own(resource); }
}
