package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Effect;

/**
 * Signal→节点属性 绑定句柄，由 {@link SceneRuntime#bind} 返回。
 *
 * <p>持有内部 {@link Effect} 引用，用于：
 * <ul>
 *   <li>{@link #dispose()}：退订 signal、从调度器注销，切断绑定链路</li>
 *   <li>{@link #isDisposed()}：判断绑定是否已释放</li>
 * </ul>
 */
public class Binding {

    private final Effect effect;

    Binding(Effect effect) {
        this.effect = effect;
    }

    /** 退订绑定：销毁内部 effect，切断与 signal 的订阅关系。 */
    public void dispose() {
        effect.dispose();
    }

    /** @return 绑定是否已释放 */
    public boolean isDisposed() {
        return effect.isDisposed();
    }
}
