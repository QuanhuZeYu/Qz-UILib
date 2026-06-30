package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 keyed 列表协调引擎（路 B / 批量版）——forEach 的结构协调核心。
 *
 * <p>按 key 对齐新旧子项，复用 key 不变项的 {@link SceneNode} 与生命周期作用域、<b>不重建</b>
 * （守 I7：干净子树被跳过）；用最长递增子序列（LIS）求出相对顺序不变的稳定项，使移动量最小。</p>
 *
 * <h3>与旧栈 {@code ui.component.KeyedListReconciler} 的核心差异：路 A → 路 B</h3>
 * <p>旧栈是<b>路 A（副作用驱动）</b>：reorder 阶段从右往左逐个 {@code container.insertBefore}，
 * 每一步都是一次独立的 DOM 结构副作用。本引擎是<b>路 B（批量提交）</b>：先把目标顺序整体算成
 * 一个 {@code finalOrder} 列表，最后<b>只调用一次</b>
 * {@link SceneNode#applyChildReconcile(List, Set)} 原子提交——结构变更收敛到单一出口，
 * 不再有逐项 insertBefore 的中间态。LIS 下标计算（{@link #computeStablePositions}）逐行照搬旧栈，
 * 与具体节点模型无关。</p>
 *
 * <h3>关键约束①（最高危，T5b 同型陷阱）：item 作用域的 onCleanup 绝不碰 DOM 结构</h3>
 * <p>本引擎下，<b>结构变更的单一出口是 {@link SceneNode#applyChildReconcile}</b>，
 * <b>生命周期回收的单一出口是 {@link Owner#dispose()}</b>，两者严格解耦。删除项时：
 * 该项不出现在 {@code finalOrder}，applyChildReconcile 已把它的 parent 置 null 完成结构摘除；
 * 随后调 {@code rec.owner.dispose()} 只回收该项内部 effect 的生命周期。
 * 因此 {@link #createItem} <b>绝不</b>在 item 作用域上登记「removeChild / 任何摘除节点」的 onCleanup。</p>
 * <p>原因：若 onCleanup 再去 removeChild，就会出现<b>两条 DOM 摘除路径</b>。当前虽无害
 * （applyChildReconcile 已置 null，第二次摘除 no-op），但 Phase 3 若让 applyChildReconcile 改为
 * 「保留被移除节点做退场动画」，两条路径会直接打架。<b>这条红线写死，禁止后续在 createItem 里补
 * 任何结构操作 onCleanup</b>。</p>
 *
 * <h3>关键约束②：reconcile 自身是纯结构协调，不订阅任何信号</h3>
 * <p>本方法体<b>不直接读取任何 signal</b>：item 的构建被收在 {@link #createItem} 内、由调用方用
 * {@code Effect.untrack(...)} 包裹整个 reconcile 调用（接线见 SceneRuntime.forEach）。因此单个 item
 * 内部读取的 signal 不会回流成「列表」的依赖——单项变化只重跑该项自己的 effect，不触发整列表重协调
 * （守 I5：杜绝全列表 diff）。</p>
 *
 * <h3>关键约束③：insertedOrMoved 在路 B 下不驱动标脏</h3>
 * <p>本引擎照常计算 {@code insertedOrMoved} 并传入 applyChildReconcile，纯为 API 契约完整 +
 * 为 Phase 3 预留口子。但<b>不要期待它驱动任何标脏</b>：几何变化的唯一权威判定源是
 * {@code SceneLayoutEngine} 的几何 equals 闸门（详见 {@link SceneNode#applyChildReconcile} 文档）。</p>
 *
 * @param <T> 列表项数据类型
 */
final class SceneKeyedListReconciler<T> {

    /** 列表挂载容器：结构协调严格收窄在此节点的子节点范围内。 */
    private final SceneNode container;
    /** 项 → 稳定唯一 key 的映射。 */
    private final Function<? super T, ?> keyFn;
    /** 项数据 → 项根节点的构建函数（新栈无 UiDocument 参数，节点直接构造）。 */
    private final Function<? super T, SceneNode> itemComponent;
    /** 列表作用域：所有 item 子作用域的父级，随整列表卸载一并清理。 */
    private final Owner listOwner;

    /** 当前已挂载项，按当前子序列顺序排列。 */
    private List<ItemRecord> current = new ArrayList<>();

    /**
     * 构造列表协调引擎。
     *
     * @param container     列表挂载容器（协调范围严格限定于此节点的子节点）
     * @param keyFn         项 → 稳定唯一 key 的映射（同一次列表内 key 不得重复）
     * @param itemComponent 项数据 → 项根节点的构建函数，每个 key 仅执行一次
     * @param listOwner     列表生命周期作用域，item 子作用域均挂在其下
     */
    SceneKeyedListReconciler(SceneNode container,
                             Function<? super T, ?> keyFn,
                             Function<? super T, SceneNode> itemComponent,
                             Owner listOwner) {
        this.container = Objects.requireNonNull(container, "container");
        this.keyFn = Objects.requireNonNull(keyFn, "keyFn");
        this.itemComponent = Objects.requireNonNull(itemComponent, "itemComponent");
        this.listOwner = Objects.requireNonNull(listOwner, "listOwner");
    }

    /**
     * 用新的项序列协调容器子节点：复用 key 不变项、为新 key 建项、删除消失项、最小移动重排，
     * 最后<b>一次</b> {@link SceneNode#applyChildReconcile} 原子提交目标顺序。
     *
     * <p>必须在<b>非追踪</b>上下文调用（item 构建/更新读取的 signal 不得回流为列表订阅，见约束②）。
     * 本方法体不直接订阅任何 signal，是纯结构协调。</p>
     *
     * @param items 新项序列（{@code null} 视为空）
     */
    void reconcile(List<T> items) {
        List<T> newItems = (items != null) ? items : Collections.emptyList();
        int newSize = newItems.size();

        // 1. 索引当前已挂载项：key → 记录、key → 旧位置
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

        // 2. 遍历新项：命中旧 key 复用记录，未命中新建；重复 key 直接抛错（照搬旧栈）
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

        // 3. 删除消失项：旧记录中 key 不在新序列的 → 只 dispose 其作用域回收内部 effect。
        //    结构摘除交给下方 applyChildReconcile（删除项不在 finalOrder，parent 被置 null）。
        //    绝不在此处或 item onCleanup 里调任何 DOM 结构操作（见约束①）。
        for (ItemRecord rec : current) {
            if (!seenKeys.contains(rec.key)) {
                rec.owner.dispose();
            }
        }

        // 4. LIS 求稳定项（相对顺序不变、零移动）
        boolean[] stable = computeStablePositions(oldIndex, reused, newSize);

        // 5. 构建批量提交载荷：finalOrder 为目标顺序的全部节点；
        //    insertedOrMoved 收集「新建项 或 复用但非稳定（被移动）项」——
        //    路 B 下它不驱动标脏，仅为 API 契约完整 + Phase 3 留口子（见约束③）。
        List<SceneNode> finalOrder = new ArrayList<>(newSize);
        Set<SceneNode> insertedOrMoved = new HashSet<>(Math.max(4, newSize * 2));
        for (int i = 0; i < newSize; i++) {
            SceneNode node = next.get(i).node;
            finalOrder.add(node);
            if (!reused[i] || !stable[i]) {
                insertedOrMoved.add(node);
            }
        }

        // 6. 一次性原子提交目标顺序（结构变更的单一出口）
        container.applyChildReconcile(finalOrder, insertedOrMoved);

        // 7. 切换当前快照
        current = next;
    }

    /**
     * 为一个新 key 构建列表项：建独立子作用域、在其内构造项节点。
     *
     * <p><b>约束①红线</b>：本方法<b>绝不</b>在 {@code itemOwner} 上登记任何「摘除节点 / removeChild /
     * 修改父子结构」的 onCleanup。item 的结构摘除由 {@link SceneNode#applyChildReconcile} 独占负责，
     * itemOwner.dispose() 只回收项内部 effect 的生命周期。两条职责严格解耦，禁止在此合并。</p>
     *
     * @param key  项的稳定唯一 key
     * @param item 项数据快照
     * @return 新建的项记录
     */
    private ItemRecord createItem(Object key, T item) {
        Owner itemOwner = listOwner.createChild();
        SceneNode[] holder = new SceneNode[1];
        itemOwner.run(() -> {
            SceneNode node = itemComponent.apply(item);
            holder[0] = Objects.requireNonNull(node, "forEach item root");
        });
        // 注意：此处不登记任何结构相关 onCleanup（见类文档约束① / 本方法 Javadoc）。
        return new ItemRecord(key, itemOwner, holder[0]);
    }

    /**
     * 求新顺序中「相对位置无需改变」的项（最长递增子序列，仅在复用项的旧下标上计算）。
     *
     * <p><b>照搬自 {@code ui.component.KeyedListReconciler}，纯下标计算、与节点模型无关、零改动。</b></p>
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

    /** 单个已挂载列表项：其 key、生命周期作用域与场景树根节点。 */
    private static final class ItemRecord {
        /** 项的稳定唯一 key。 */
        final Object key;
        /** 项的生命周期作用域，dispose 时回收项内部 effect。 */
        final Owner owner;
        /** 项的场景树根节点。 */
        final SceneNode node;

        ItemRecord(Object key, Owner owner, SceneNode node) {
            this.key = key;
            this.owner = owner;
            this.node = node;
        }
    }
}
