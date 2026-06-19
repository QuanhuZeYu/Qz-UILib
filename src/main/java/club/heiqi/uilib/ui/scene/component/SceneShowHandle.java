package club.heiqi.uilib.ui.scene.component;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;

/**
 * 条件渲染（show）句柄，对标新栈 {@link MountHandle} / {@link Binding} 的句柄风格，
 * 由 SceneRuntime 的 show 方法返回。
 *
 * <p>持有 show 的条件作用域 {@code condOwner}，提供 {@link #dispose()} 卸载整个条件渲染：
 * 一次 {@code condOwner.dispose()} 会递归销毁其下所有内容子作用域（含内容节点的 onCleanup 摘除），
 * 并触发 condOwner 自身登记的 onCleanup——anchor 占位节点的清理也交由该 onCleanup 完成
 * （由 show 方法在 condOwner 上登记）。</p>
 *
 * <h3>与 show 引擎的设计约束</h3>
 * <p>show 不走 {@link club.heiqi.uilib.ui.scene.node.SceneNode#applyChildReconcile}，而是用
 * anchor 占位 + insertBefore/removeChild 副作用驱动（详见 {@link SceneConditionalRenderer} 类
 * 文档）。因此本句柄的 dispose 语义是「释放条件作用域」，由作用域的 onCleanup 链负责把内容节点与
 * anchor 从场景树摘除，绝不批量替换 parent 的 children 列表。</p>
 */
public final class SceneShowHandle {

    /** 条件作用域：show 的生命周期根，dispose 时递归清理内容子作用域与 anchor。 */
    private final Owner condOwner;

    /**
     * 构造 show 句柄。
     *
     * @param condOwner 条件作用域，不可为 null
     */
    SceneShowHandle(Owner condOwner) {
        this.condOwner = Objects.requireNonNull(condOwner, "condOwner");
    }

    /**
     * 卸载整个条件渲染：递归销毁条件作用域，其下内容子作用域的 onCleanup 摘除内容节点，
     * condOwner 自身的 onCleanup 摘除 anchor 占位节点。重复调用安全（Owner.dispose 幂等）。
     */
    public void dispose() {
        condOwner.dispose();
    }

    /** @return 条件渲染是否已卸载。 */
    public boolean isDisposed() {
        return condOwner.isDisposed();
    }
}
