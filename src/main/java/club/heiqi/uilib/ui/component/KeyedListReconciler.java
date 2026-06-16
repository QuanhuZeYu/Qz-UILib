package club.heiqi.uilib.ui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Owner;

/**
 * keyed 列表协调器（信条三，I5）：按 key 对齐新旧子节点，只增删移动变化项；
 * 稳定项保留其 DOM 节点与生命周期作用域、<b>不重建</b>（守 I7：干净子树被跳过）。
 *
 * <p><b>红线（I5）</b>：本协调器的全部 DOM 操作严格收窄在 {@code container} 的子节点范围内
 * （{@code insertBefore}/{@code appendChild}/{@code removeChild}），绝不触达列表外节点，
 * 更不退化成全树 diff。每次只比较「列表本身」的新旧 key 序列；单个 item 内部的 signal 变化
 * 由该 item 自己的 effect 处理，<b>不会</b>回流触发整列表重协调——这依赖调用方在 reconcile
 * 外层用 untrack 隔离 item 构建期对 signal 的读取（见 {@code UiComponentRuntime.forEach}）。</p>
 *
 * <p><b>最小移动</b>：用最长递增子序列（LIS）求出在新旧顺序中相对位置不变的稳定项，
 * 这些项零移动；只有真正改变相对顺序的项才 {@code insertBefore} 重定位（守 I7/I8）。</p>
 *
 * @param <T> 列表项数据类型
 */
final class KeyedListReconciler<T> {

    private final UiDocument document;
    private final ElementNode container;
    private final Function<? super T, ?> keyFn;
    private final BiFunction<UiDocument, ? super T, ElementNode> itemComponent;
    private final Owner listOwner;

    /** 当前已挂载项，按 DOM 顺序排列。 */
    private List<ItemRecord> current = new ArrayList<>();

    KeyedListReconciler(UiDocument document,
                        ElementNode container,
                        Function<? super T, ?> keyFn,
                        BiFunction<UiDocument, ? super T, ElementNode> itemComponent,
                        Owner listOwner) {
        this.document = document;
        this.container = container;
        this.keyFn = keyFn;
        this.itemComponent = itemComponent;
        this.listOwner = listOwner;
    }

    /**
     * 用新的项序列协调 DOM：复用 key 不变项、为新 key 建项、删除消失项、最小移动重排。
     *
     * <p>必须在<b>非追踪</b>上下文调用（item 构建/更新读取的 signal 不得回流为列表订阅）。</p>
     *
     * @param items 新项序列（{@code null} 视为空）
     */
    void reconcile(List<T> items) {
        List<T> newItems = (items != null) ? items : Collections.emptyList();
        int newSize = newItems.size();

        Map<Object, ItemRecord> oldByKey = new HashMap<>();
        Map<Object, Integer> oldIndexByKey = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            ItemRecord rec = current.get(i);
            oldByKey.put(rec.key, rec);
            oldIndexByKey.put(rec.key, i);
        }

        List<ItemRecord> next = new ArrayList<>(newSize);
        Set<Object> seenKeys = new HashSet<>(Math.max(4, newSize * 2));
        boolean[] reused = new boolean[newSize];
        int[] oldIndex = new int[newSize];

        for (int i = 0; i < newSize; i++) {
            T item = newItems.get(i);
            Object key = keyFn.apply(item);
            if (!seenKeys.add(key)) {
                throw new IllegalStateException(
                        "forEach 检测到重复 key [" + key + "]：key 必须唯一，否则无法对齐新旧项");
            }
            ItemRecord existing = oldByKey.get(key);
            if (existing != null) {
                next.add(existing);
                reused[i] = true;
                oldIndex[i] = oldIndexByKey.get(key);
            } else {
                next.add(createItem(key, item));
                reused[i] = false;
                oldIndex[i] = -1;
            }
        }

        // 删除：旧记录中不再出现的 key → dispose 其作用域（onCleanup 摘 DOM）
        for (ItemRecord rec : current) {
            if (!seenKeys.contains(rec.key)) {
                rec.owner.dispose();
            }
        }

        reorder(next, reused, oldIndex);
        current = next;
    }

    private ItemRecord createItem(Object key, T item) {
        Owner itemOwner = listOwner.createChild();
        ElementNode[] holder = new ElementNode[1];
        itemOwner.run(() -> {
            ElementNode node = itemComponent.apply(document, item);
            holder[0] = Objects.requireNonNull(node, "forEach item root");
        });
        ElementNode node = holder[0];
        itemOwner.onCleanup(() -> {
            if (node.getParent() != null) {
                node.getParent().removeChild(node);
            }
        });
        return new ItemRecord(key, itemOwner, node);
    }

    /**
     * 把 {@code next} 中各项的 DOM 节点排成目标顺序，<b>只移动必要的项</b>。
     *
     * <p>先用 LIS 求出在新旧顺序中相对位置不变的「稳定项」（这些节点零移动，守 I7/I8），
     * 再从右往左遍历：新项、以及不在稳定子序列中的复用项，{@code insertBefore} 到其右邻节点之前；
     * 稳定项原地不动。从右往左保证「右邻」节点已就位，可直接作为插入锚点。</p>
     */
    private void reorder(List<ItemRecord> next, boolean[] reused, int[] oldIndex) {
        int n = next.size();
        boolean[] stable = computeStablePositions(oldIndex, reused, n);
        ElementNode rightNeighbor = null;
        for (int i = n - 1; i >= 0; i--) {
            ItemRecord rec = next.get(i);
            boolean needsMove = !reused[i] || !stable[i];
            if (needsMove) {
                // referenceChild 为 null 时 insertBefore 退化为 append（DOM 语义），正确处理末项。
                container.insertBefore(rec.node, rightNeighbor);
            }
            rightNeighbor = rec.node;
        }
    }

    /**
     * 求新顺序中「相对位置无需改变」的项（最长递增子序列，仅在复用项的旧下标上计算）。
     *
     * @return 长度为 {@code n} 的布尔数组，{@code true} 表示该新顺序位置是稳定项（不移动）
     */
    private static boolean[] computeStablePositions(int[] oldIndex, boolean[] reused, int n) {
        boolean[] stable = new boolean[n];
        // 收集复用项：seqValues = 其旧下标（互异），seqPos = 其在新顺序中的位置
        List<Integer> seqValues = new ArrayList<>();
        List<Integer> seqPos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (reused[i]) {
                seqValues.add(oldIndex[i]);
                seqPos.add(i);
            }
        }
        int m = seqValues.size();
        if (m == 0) {
            return stable;
        }
        // patience sorting 求 LIS（严格递增），带前驱指针以重建子序列
        int[] tails = new int[m];   // tails[k]：长度为 k+1 的递增子序列的最小尾元素在 seq 中的下标
        int[] prev = new int[m];    // 前驱指针（seq 下标）
        int len = 0;
        for (int i = 0; i < m; i++) {
            int value = seqValues.get(i);
            int lo = 0;
            int hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (seqValues.get(tails[mid]) < value) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            prev[i] = (lo > 0) ? tails[lo - 1] : -1;
            tails[lo] = i;
            if (lo == len) {
                len++;
            }
        }
        // 从最长子序列尾部沿前驱回溯，标记稳定位置
        int k = tails[len - 1];
        while (k >= 0) {
            stable[seqPos.get(k)] = true;
            k = prev[k];
        }
        return stable;
    }

    /** 单个已挂载列表项：其 key、生命周期作用域与 DOM 根节点。 */
    private static final class ItemRecord {
        final Object key;
        final Owner owner;
        final ElementNode node;

        ItemRecord(Object key, Owner owner, ElementNode node) {
            this.key = key;
            this.owner = owner;
            this.node = node;
        }
    }
}
