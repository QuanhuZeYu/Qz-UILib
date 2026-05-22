package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 已打开 UI 根节点的布局失效注册表。
 *
 * <p>底层使用 {@link WeakHashMap} 包装的 Set，因此当 UI 根节点不再被强引用时会被 GC 自动剔除。
 * 由于 {@code WeakHashMap} 不是线程安全的，且 GC 触发的隐式 entry 清理与显式
 * {@link #registerRoot}/{@link #unregisterRoot}/{@link #invalidateAll} 之间存在并发风险，
 * 所有访问 {@link #ROOTS} 的入口必须持有 {@link #LOCK}。</p>
 */
public final class UiLayoutInvalidationRegistry {

    private static final Object LOCK = new Object();
    private static final Set<Widget> ROOTS = Collections.newSetFromMap(new WeakHashMap<Widget, Boolean>());

    private UiLayoutInvalidationRegistry() {}

    /**
     * 注册当前活跃的 UI 根节点。
     *
     * @param root 根节点
     */
    public static void registerRoot(Widget root) {
        if (root == null) {
            return;
        }
        synchronized (LOCK) {
            ROOTS.add(root);
        }
    }

    /**
     * 取消注册当前活跃的 UI 根节点。
     *
     * @param root 根节点
     */
    public static void unregisterRoot(Widget root) {
        if (root == null) {
            return;
        }
        synchronized (LOCK) {
            ROOTS.remove(root);
        }
    }

    /**
     * 递归失效全部已注册 UI 树的布局缓存。
     *
     * @return 失效的根节点数量
     */
    public static int invalidateAll() {
        List<Widget> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<Widget>(ROOTS);
        }
        for (Widget root : snapshot) {
            if (root != null) {
                root.invalidateLayoutTree();
            }
        }
        return snapshot.size();
    }
}
