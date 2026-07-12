package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * fontSort 的纯 Java 顺序模型。
 *
 * <p>模型只处理发现快照、草稿合并、筛选和顺序映射，不读取 FontConfig，也不参与字体运行时
 * 规划。所有名称均以 {@code trim + lowercase(Locale.ENGLISH)} 作为 identity，显示值始终取
 * 当前发现快照中的首个 canonical display name。</p>
 */
public final class FontSortOrderModel {

    private FontSortOrderModel() {
    }

    /**
     * 冻结当次发现顺序：去掉空白项和重复 identity，保留首项的显示名称。
     *
     * @param discovered 当次发现顺序，可为 null
     * @return 不可变 canonical 发现顺序
     */
    public static List<String> freezeDiscovered(List<String> discovered) {
        List<String> result = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        if (discovered != null) {
            for (String value : discovered) {
                String display = displayName(value);
                if (!display.isEmpty() && seen.add(identity(display))) {
                    result.add(display);
                }
            }
        }
        return immutable(result);
    }

    /**
     * 将旧草稿与 frozen discovered 合并：有效旧顺序优先，新发现按 snapshot 顺序追加。
     *
     * @param discovered frozen discovered snapshot
     * @param oldDraft   旧草稿，可为 null
     * @return canonical merged order
     */
    public static List<String> merge(List<String> discovered, List<String> oldDraft) {
        List<String> frozen = freezeDiscovered(discovered);
        Map<String, String> canonical = new HashMap<String, String>();
        for (String value : frozen) {
            canonical.put(identity(value), value);
        }

        List<String> result = new ArrayList<String>(frozen.size());
        Set<String> used = new HashSet<String>();
        if (oldDraft != null) {
            for (String value : oldDraft) {
                String key = identity(value);
                String display = canonical.get(key);
                if (display != null && used.add(key)) {
                    result.add(display);
                }
            }
        }
        for (String value : frozen) {
            if (used.add(identity(value))) {
                result.add(value);
            }
        }
        return immutable(result);
    }

    /**
     * 对完整顺序做大小写不敏感的 contains 筛选。
     *
     * @param fullOrder 完整顺序
     * @param filter    筛选文本，可为 null
     * @return 筛选投影，不修改完整顺序
     */
    public static List<String> filter(List<String> fullOrder, String filter) {
        String needle = identity(filter);
        List<String> result = new ArrayList<String>();
        if (fullOrder != null) {
            for (String value : fullOrder) {
                if (identity(value).contains(needle)) {
                    result.add(value);
                }
            }
        }
        return immutable(result);
    }

    /**
     * 将完整列表中的项移动到 0-based 目标位置。
     *
     * @param fullOrder 完整顺序
     * @param fromIndex 来源位置
     * @param targetIndex 目标位置，越界会 clamp
     * @return 移动后的完整顺序
     */
    public static List<String> move(List<String> fullOrder, int fromIndex, int targetIndex) {
        List<String> result = copy(fullOrder);
        if (result.isEmpty() || fromIndex < 0 || fromIndex >= result.size()) {
            return immutable(result);
        }
        int target = Math.max(0, Math.min(result.size() - 1, targetIndex));
        String value = result.remove(fromIndex);
        result.add(target, value);
        return immutable(result);
    }

    /**
     * 使用 identity 将项移动到 1-based 全量位置。
     *
     * @param fullOrder 完整顺序
     * @param itemIdentity 要移动的项 identity
     * @param oneBasedTarget 1-based 目标位置
     * @return 移动后的完整顺序
     */
    public static List<String> moveToOneBased(List<String> fullOrder, String itemIdentity,
                                               int oneBasedTarget) {
        int from = indexOfIdentity(fullOrder, itemIdentity);
        if (from < 0) {
            return immutable(copy(fullOrder));
        }
        return move(fullOrder, from, oneBasedTarget - 1);
    }

    /**
     * 严格解析 1-based 索引输入。只接受 ASCII 十进制数字，拒绝空白、小数、e/E 和符号，
     * 并把合法越界值 clamp 到 {@code [1, size]}。
     *
     * @param raw 原始输入
     * @param size 完整列表大小
     * @return clamp 后目标；非法输入或空列表返回 null
     */
    public static Integer parseOneBasedTarget(String raw, int size) {
        if (size <= 0 || raw == null || raw.isEmpty()) {
            return null;
        }
        long value = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            value = Math.min((long) size + 1L, value * 10L + (c - '0'));
        }
        if (value < 1L) {
            return Integer.valueOf(1);
        }
        return Integer.valueOf((int) Math.min((long) size, value));
    }

    /**
     * 将筛选投影中的移动映射回完整顺序，隐藏项保持原位置和相对顺序。
     *
     * @param fullOrder 完整顺序
     * @param visibleBefore MOVE 前冻结的可见投影
     * @param fromVisibleIndex 可见来源位置
     * @param targetVisibleIndex 可见目标位置
     * @return 映射后的完整顺序
     */
    public static List<String> moveVisible(List<String> fullOrder, List<String> visibleBefore,
                                           int fromVisibleIndex, int targetVisibleIndex) {
        List<String> visibleAfter = move(visibleBefore, fromVisibleIndex, targetVisibleIndex);
        return applyVisibleOrder(fullOrder, visibleBefore, visibleAfter);
    }

    /**
     * 把新的可见顺序写回完整列表的可见槽位，隐藏项不被删除或重排。
     *
     * @param fullOrder 完整顺序
     * @param visibleBefore MOVE 前可见项
     * @param visibleAfter MOVE 后可见项
     * @return 映射后的完整顺序
     */
    public static List<String> applyVisibleOrder(List<String> fullOrder, List<String> visibleBefore,
                                                  List<String> visibleAfter) {
        List<String> result = copy(fullOrder);
        if (visibleBefore == null || visibleAfter == null
                || visibleBefore.size() != visibleAfter.size()) {
            return immutable(result);
        }
        Set<String> visibleKeys = new HashSet<String>();
        for (String value : visibleBefore) {
            visibleKeys.add(identity(value));
        }
        Set<String> afterKeys = new HashSet<String>();
        List<String> replacement = new ArrayList<String>(visibleAfter.size());
        for (String value : visibleAfter) {
            String key = identity(value);
            if (!visibleKeys.contains(key) || !afterKeys.add(key)) {
                return immutable(result);
            }
            replacement.add(value);
        }
        if (afterKeys.size() != visibleKeys.size()) {
            return immutable(result);
        }

        int replacementIndex = 0;
        for (int i = 0; i < result.size(); i++) {
            if (visibleKeys.contains(identity(result.get(i)))) {
                result.set(i, replacement.get(replacementIndex++));
            }
        }
        return immutable(result);
    }

    /**
     * 返回名称 identity。Locale.ENGLISH 是协议的一部分，避免 Turkish locale 改变语义。
     *
     * @param value 名称
     * @return trim 后的英文小写 identity
     */
    public static String identity(String value) {
        return displayName(value).toLowerCase(Locale.ENGLISH);
    }

    /**
     * 返回 canonical display name。
     *
     * @param value 原名称
     * @return trim 后名称，null 为空串
     */
    public static String displayName(String value) {
        return value == null ? "" : value.trim();
    }

    private static int indexOfIdentity(List<String> values, String target) {
        String key = identity(target);
        if (values == null) {
            return -1;
        }
        for (int i = 0; i < values.size(); i++) {
            if (identity(values.get(i)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
