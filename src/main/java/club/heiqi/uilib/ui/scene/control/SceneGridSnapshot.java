package club.heiqi.uilib.ui.scene.control;

import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;

/**
 * SceneGridSnapshot —— 「行数据 + 索引」同源快照（同一 Computed 派生）。
 *
 * <h3>同一快照原则</h3>
 * <p>行内容、下标解析、图标实时派生、hover/选中解析全部读同一份快照，杜绝「同一帧内不同单元
 * 读到不同代数据」的隐式漂移。数据变化 → 一次 O(N) 建索引 → 全部单元在该快照上 O(1) 查询；
 * 快照被替换后旧索引不可达 → GC（不跨屏、不累积）。</p>
 *
 * @param items        数据快照（非 null；全局或窗口切片）
 * @param index        key → 全局下标索引（非 null；与 items 同源）
 * @param windowOffset 快照在全局序列中的起始下标（≥0）
 */
@Desugar
public record SceneGridSnapshot(List<Item> items, SceneItemIndex index, int windowOffset) {

    /** 防御性：items 非 null、windowOffset 归一。 */
    public SceneGridSnapshot {
        items = items == null ? Collections.<Item>emptyList() : items;
        windowOffset = Math.max(0, windowOffset);
    }

    /**
     * 以全局下标 0 建快照。
     *
     * @param items 数据快照
     * @return 快照（非 null）
     */
    public static SceneGridSnapshot of(List<Item> items) {
        return of(items, 0);
    }

    /**
     * 建快照（items 与 index 一次派生，同源）。
     *
     * @param items        数据快照
     * @param windowOffset 快照在全局序列中的起始下标
     * @return 快照（非 null）
     */
    public static SceneGridSnapshot of(List<Item> items, int windowOffset) {
        List<Item> safe = items == null ? Collections.<Item>emptyList() : items;
        int offset = Math.max(0, windowOffset);
        return new SceneGridSnapshot(safe, SceneItemIndex.of(safe, offset), offset);
    }

    /** @return 快照项数 */
    public int size() {
        return items.size();
    }
}
