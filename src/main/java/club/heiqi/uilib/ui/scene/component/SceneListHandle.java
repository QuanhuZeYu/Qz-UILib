package club.heiqi.uilib.ui.scene.component;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;

/**
 * keyed 动态列表（forEach）句柄，对标新栈 {@link SceneShowHandle} 的句柄风格，
 * 由 SceneRuntime 的 forEach 方法返回。
 *
 * <p>持有列表作用域 {@code listOwner}，提供 {@link #dispose()} 卸载整个列表：
 * 一次 {@code listOwner.dispose()} 会递归销毁其下所有 item 子作用域，回收每项内部 effect
 * 与列表的 reconcile effect。</p>
 *
 * <h3>与 forEach 引擎的设计约束</h3>
 * <p>列表的结构摘除由 {@link club.heiqi.uilib.ui.scene.node.SceneNode#applyChildReconcile}
 * 独占负责（路 B：批量提交），item 作用域的 onCleanup <b>不碰任何 DOM 结构</b>
 * （详见 {@link SceneKeyedListReconciler} 类文档约束①）。因此本句柄 dispose 的语义是
 * 「释放列表作用域、回收全部 effect 生命周期」，节点从场景树的摘除已在协调阶段完成，
 * 不在 dispose 路径上重复操作结构。</p>
 */
public final class SceneListHandle {

    /** 列表作用域：forEach 的生命周期根，dispose 时递归清理 item 子作用域与 reconcile effect。 */
    private final Owner listOwner;

    /**
     * 构造 forEach 列表句柄。
     *
     * @param listOwner 列表作用域，不可为 null
     */
    SceneListHandle(Owner listOwner) {
        this.listOwner = Objects.requireNonNull(listOwner, "listOwner");
    }

    /**
     * 卸载整个列表：递归销毁列表作用域，回收所有 item 子作用域内部 effect 与 reconcile effect。
     * 重复调用安全（Owner.dispose 幂等）。
     */
    public void dispose() {
        listOwner.dispose();
    }

    /** @return 列表是否已卸载。 */
    public boolean isDisposed() {
        return listOwner.isDisposed();
    }
}
