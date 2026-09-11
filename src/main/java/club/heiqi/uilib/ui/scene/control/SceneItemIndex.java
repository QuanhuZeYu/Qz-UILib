package club.heiqi.uilib.ui.scene.control;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;

/**
 * SceneItemIndex —— key → 全局下标的 O(1) 查表（唯一索引实现）。
 *
 * <h3>为什么需要</h3>
 * <p>虚拟化后每个挂载单元都要按 key 解析「我在全局序列里的下标」（选中态派生、点击回写）
 * 或「我的当前快照」（图标源实时派生）。线性反查会让挂载成本退化为 O(可视 × N)；
 * 索引把每次解析降为 O(1)，且建表只在数据快照变化时发生一次（O(N)）。</p>
 *
 * <h3>结构判据（不是"计数为 0"的真空真）</h3>
 * <p>{@link #of} 一次遍历把 key→下标与下标→项 收进内部数组/哈希表；此后
 * {@link #indexOf}/{@link #itemAt}/{@link #itemAtGlobal} **完全不触碰来源 List**
 * （守卫测试用计数列表包装验证 {@code get} 调用数 == 0）。</p>
 *
 * <h3>坐标语义</h3>
 * <p>{@code windowOffset} 是数据快照在全局序列中的起始下标（窗口切片场景）；
 * {@link #indexOf} 返回**全局下标** = {@code windowOffset + 局部下标}（未命中 -1），
 * {@link #itemAtGlobal} 接受全局下标。</p>
 *
 * <h3>所有权</h3>
 * <p>与数据快照同寿命：随控件 Owner 回收、随快照替换不可达 → GC；不跨屏、不累积。</p>
 */
public final class SceneItemIndex {

    /** 下标 → 项（内部快照，不引用来源 List）。 */
    private final Item[] slots;
    /** key → 局部下标（重复 key 首项胜）。 */
    private final Map<Object, Integer> indices;
    /** 本快照在全局序列中的起始下标。 */
    private final int windowOffset;

    private SceneItemIndex(Item[] slots, Map<Object, Integer> indices, int windowOffset) {
        this.slots = slots;
        this.indices = indices;
        this.windowOffset = windowOffset;
    }

    /**
     * 以全局下标 0 建索引。
     *
     * @param items 数据快照（可为 null，按空处理）
     * @return 索引（非 null）
     */
    public static SceneItemIndex of(List<Item> items) {
        return of(items, 0);
    }

    /**
     * 建索引。
     *
     * @param items        数据快照（可为 null，按空处理）
     * @param windowOffset 快照在全局序列中的起始下标（&lt;0 按 0）
     * @return 索引（非 null）
     */
    public static SceneItemIndex of(List<Item> items, int windowOffset) {
        int offset = Math.max(0, windowOffset);
        int size = items == null ? 0 : items.size();
        Item[] slots = new Item[size];
        Map<Object, Integer> indices = new HashMap<Object, Integer>(Math.max(16, size * 2));
        if (items != null) {
            for (int i = 0; i < size; i++) {
                Item item = items.get(i);
                slots[i] = item;
                if (item == null || item.key() == null) {
                    continue;
                }
                // 重复 key 首项胜：与线性反查（首个命中）语义一致。
                if (!indices.containsKey(item.key())) {
                    indices.put(item.key(), Integer.valueOf(i));
                }
            }
        }
        return new SceneItemIndex(slots, indices, offset);
    }

    /**
     * 按 key 查全局下标。
     *
     * @param key 项 key（null 直接未命中）
     * @return 全局下标；未命中 -1
     */
    public int indexOf(Object key) {
        if (key == null) {
            return -1;
        }
        Integer local = indices.get(key);
        return local == null ? -1 : windowOffset + local.intValue();
    }

    /**
     * 按 key 取当前快照项（单元图标/标签的实时派生入口，O(1)）。
     *
     * @param key 项 key
     * @return 命中项；未命中 null
     */
    public Item itemAt(Object key) {
        if (key == null) {
            return null;
        }
        Integer local = indices.get(key);
        return local == null ? null : slots[local.intValue()];
    }

    /**
     * 按全局下标取项。
     *
     * @param globalIndex 全局下标
     * @return 命中项；越界/未命中 null
     */
    public Item itemAtGlobal(int globalIndex) {
        int local = globalIndex - windowOffset;
        if (local < 0 || local >= slots.length) {
            return null;
        }
        return slots[local];
    }

    /** @return 快照项数（局部规模） */
    public int size() {
        return slots.length;
    }

    /** @return 快照在全局序列中的起始下标 */
    public int windowOffset() {
        return windowOffset;
    }
}
