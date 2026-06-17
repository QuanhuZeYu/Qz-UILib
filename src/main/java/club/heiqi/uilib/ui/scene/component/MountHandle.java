package club.heiqi.uilib.ui.scene.component;

import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;

/**
 * 组件挂载句柄，由 {@link SceneRuntime#mount} 返回。
 *
 * <p>持有挂载作用域与根节点引用，提供：
 * <ul>
 *   <li>{@link #getRoot()}：获取组件产出的根 {@link SceneNode}</li>
 *   <li>{@link #dispose()}：卸载组件、递归回收该作用域内所有 effect 并自动从父节点摘除（I3）</li>
 * </ul>
 */
public class MountHandle {

    private final Owner scope;
    private final club.heiqi.uilib.ui.scene.node.SceneNode root;

    MountHandle(Owner scope, club.heiqi.uilib.ui.scene.node.SceneNode root) {
        this.scope = scope;
        this.root = root;
    }

    /** @return 组件产出的根节点 */
    public club.heiqi.uilib.ui.scene.node.SceneNode getRoot() {
        return root;
    }

    /**
     * 卸载组件：递归销毁该作用域内所有子 Owner / effect / cleanup 回调，
     * 并从父节点摘除根节点。
     */
    public void dispose() {
        scope.dispose();
    }
}
