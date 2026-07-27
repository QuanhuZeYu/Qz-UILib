package club.heiqi.uilib.ui.container.experimental.storage;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;

/** Experimental 平台无关 storage SPI；容量、merge、排序与 key 分配由 backend 负责。 */
public interface LongContainerStorage {
    /** 返回当前确认快照；实现不得把 carried 或 MC Slot 纳入其中。 */
    LongContainerSnapshot snapshot();
    /**
     * 插入 requested（必须大于 0）的物品。EXACT 必须全量或零变化，UP_TO 可 partial；返回操作后快照。
     */
    TransferResult insert(ItemDescriptor item, long requested, TransferMode mode);
    /**
     * 提取 requested（必须大于 0）的 key。EXACT 必须全量或零变化，UP_TO 可 partial；stale/missing key 仅返回 NOT_FOUND；返回操作后快照。
     */
    TransferResult extract(EntryKey key, long requested, TransferMode mode);
}
