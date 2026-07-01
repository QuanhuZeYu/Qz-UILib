package club.heiqi.uilib.ui.scene.input;

/**
 * 输入事件绑定句柄，由 {@link SceneInputRouter#on} / {@link SceneRuntime#on} 返回。
 *
 * <p>与 {@code club.heiqi.uilib.ui.scene.runtime.Binding}（持有 Effect）
 * 对偶但不同：InputBinding 不创建响应式订阅，只持有一个退订 Runnable，
 * 用于从路由器注册表中移除 handler。</p>
 *
 * <p>{@link #dispose()} 幂等：重复调用无副作用。</p>
 */
public class InputBinding {

    /** 退订回调，null 表示已释放 */
    private Runnable onDispose;

    /**
     * 包级构造器。
     *
     * @param onDispose 退订回调
     */
    InputBinding(Runnable onDispose) {
        this.onDispose = onDispose;
    }

    /**
     * 退订此绑定：从路由器注册表中移除 handler。
     *
     * <p>幂等操作：重复调用无副作用。</p>
     */
    public void dispose() {
        if (onDispose != null) {
            onDispose.run();
            onDispose = null;
        }
    }

    /**
     * @return 是否已退订
     */
    public boolean isDisposed() {
        return onDispose == null;
    }
}
