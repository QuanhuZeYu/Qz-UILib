package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 已打开 UI 根节点的布局失效注册表。
 */
public final class UiLayoutInvalidationRegistry {

    private static final Set<Widget> ROOTS = Collections.newSetFromMap(new WeakHashMap<Widget, Boolean>());

    private UiLayoutInvalidationRegistry() {}

    /**
     * 注册当前活跃的 UI 根节点。
     *
     * @param root 根节点
     */
    public static synchronized void registerRoot(Widget root) {
        if (root == null) {
            return;
        }
        ROOTS.add(root);
    }

    /**
     * 取消注册当前活跃的 UI 根节点。
     *
     * @param root 根节点
     */
    public static synchronized void unregisterRoot(Widget root) {
        if (root == null) {
            return;
        }
        ROOTS.remove(root);
    }

    /**
     * 递归失效全部已注册 UI 树的布局缓存。
     *
     * @return 失效的根节点数量
     */
    public static synchronized int invalidateAll() {
        List<Widget> snapshot = new ArrayList<Widget>(ROOTS);
        for (Widget root : snapshot) {
            if (root != null) {
                root.invalidateLayoutTree();
            }
        }
        return snapshot.size();
    }
}
