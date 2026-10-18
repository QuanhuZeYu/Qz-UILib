package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 一次组字联系的身份与终止；草稿和焦点仍分别归编辑状态与唯一路由器。
 * 对应编辑规格第 2 节：来源、挂载、状态及基线必须同时有效，迟到事件不能重投当前焦点。 */
final class SceneComposition {
    private static final class Link {
        final SceneCompositionSource source;
        final MountHandle mount;
        final SceneEditState state;
        final Object baseline;

        Link(SceneCompositionSource source, MountHandle mount, SceneEditState state) {
            this.source = source;
            this.mount = mount;
            this.state = state;
            baseline = state.inputBaseline();
        }
    }

    private final SceneRuntime runtime;
    private Link active;

    SceneComposition(SceneRuntime runtime) { this.runtime = runtime; }

    /** null 表示该来源已失效或 BEGIN 无合法目标；调用者消费事件，不再按当前焦点回退。 */
    SceneNode target(SceneEditEvent event, SceneNode focused) {
        if (event.compositionPhase == SceneEditEvent.CompositionPhase.BEGIN) {
            if (!event.compositionSource.beginOnce()) return null;
            invalidate(null);
            if (!accepts(focused)) return null;
            active = new Link(event.compositionSource, runtime.captureMount(focused),
                    ((SceneCompositionTarget) focused.control()).compositionState());
        } else if (active == null || event.compositionSource != active.source) return null;
        return active.mount.node();
    }

    private boolean accepts(SceneNode node) {
        return node != null && runtime.interactive(node) && node.control() != null
                && node.control().acceptsTextInput(node)
                && node.control() instanceof SceneCompositionTarget;
    }

    void revalidate() {
        runtime.domain().checkAccess();
        Link link = active;
        if (link == null) return;
        SceneNode node = link.mount.node();
        if (!link.mount.active() || runtime.focusRouter().focused().get() != node || !accepts(node)
                || ((SceneCompositionTarget) node.control()).compositionState() != link.state
                || link.state.inputBaseline() != link.baseline)
            invalidate(null);
    }

    /** 先结束身份再清呈现；reset/cancel 的新基线不受旧联系清理影响。 */
    void invalidate(SceneNode target) {
        runtime.domain().checkWrite();
        Link link = active;
        if (link == null || target != null && link.mount.node() != target) return;
        active = null;
        if (link.state.inputBaseline() == link.baseline) link.state.composition("", false);
    }

    /** 最终事件即使控件回调失败也结束原联系，不清理后来建立的来源。 */
    void finish(SceneEditEvent event) {
        if (active != null && active.source == event.compositionSource
                && (event.compositionPhase == SceneEditEvent.CompositionPhase.COMMIT
                    || event.compositionPhase == SceneEditEvent.CompositionPhase.CANCEL))
            invalidate(null);
    }
}
