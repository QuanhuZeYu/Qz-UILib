package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * SceneListOps —— scene 控件层列表状态通用操作。
 *
 * <p>用于收口 scene 中重复的 null 安全读取、防御性拷贝、增删边界判断和当前行快照定位逻辑。
 * 已验证调用方默认不迁移，仅为 scene 新栈内重复模式提供统一入口。</p>
 */
public final class SceneListOps {

    /** 纯静态工具类，禁止实例化。 */
    private SceneListOps() {
    }

    /**
     * 返回 null 安全列表视图。
     *
     * @param list 原列表，可为 null
     * @param <T>  元素类型
     * @return 原列表或空列表
     */
    static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }

    /**
     * 返回列表的不可变拷贝。null 返回空列表。
     * 用于构造器/Props 的防御性拷贝，确保外部传入的列表不会被后续修改影响内部状态。
     *
     * @param list 原列表，可为 null
     * @param <T>  元素类型
     * @return 不可变列表副本
     */
    public static <T> List<T> immutableCopy(List<T> list) {
        return list == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /**
     * 判断列表是否允许新增元素。
     *
     * @param list    当前列表，可为 null
     * @param maxSize 最大数量，非正表示无限制
     * @param <T>     元素类型
     * @return true 表示允许新增
     */
    static <T> boolean canAdd(List<T> list, int maxSize) {
        return maxSize <= 0 || safeList(list).size() < maxSize;
    }

    /**
     * 判断列表是否允许删除元素。
     *
     * @param list    当前列表，可为 null
     * @param minSize 最小数量，非正表示无限制
     * @param <T>     元素类型
     * @return true 表示允许删除
     */
    static <T> boolean canRemove(List<T> list, int minSize) {
        return minSize <= 0 || safeList(list).size() > minSize;
    }

    /**
     * 按 equals 语义读取当前元素快照。
     *
     * @param list     当前列表，可为 null
     * @param fallback 兜底元素
     * @param <T>      元素类型
     * @return 当前元素或兜底元素
     */
    static <T> T current(List<T> list, T fallback) {
        return current(list, fallback, SceneListOps::equalsItem);
    }

    /**
     * 按调用方提供的身份判断读取当前元素快照。
     *
     * @param list       当前列表，可为 null
     * @param fallback   兜底元素
     * @param sameItem   判断两个元素是否为同一业务项
     * @param <T>        元素类型
     * @return 当前元素或兜底元素
     */
    static <T> T current(List<T> list, T fallback, BiPredicate<T, T> sameItem) {
        for (T item : safeList(list)) {
            if (sameItem.test(item, fallback)) {
                return item;
            }
        }
        return fallback;
    }

    /**
     * null 安全 equals 判断。
     *
     * @param item     候选元素
     * @param fallback 兜底元素
     * @param <T>      元素类型
     * @return true 表示相等
     */
    private static <T> boolean equalsItem(T item, T fallback) {
        return item == fallback || item != null && item.equals(fallback);
    }
}
