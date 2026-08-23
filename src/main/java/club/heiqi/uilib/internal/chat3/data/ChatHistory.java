package club.heiqi.uilib.internal.chat3.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.IChatComponent;

/**
 * 聊天 3.0 自有历史存储(零原版字段)。
 *
 * <p>语义:</p>
 * <ul>
 *   <li>新行在前(index 0 = 最新),容量裁剪(超容量裁最旧);</li>
 *   <li>滚动:scrollOffset 表示「自底部向上偏移的行数」,0 = 停在底部(最新);scrollBy 下限 0;
 *       可视窗口裁剪由视图模型几何完成,历史只负责偏移状态;</li>
 *   <li>线程安全:任意线程 append(网络/第三方 mod),渲染线程读快照。</li>
 * </ul>
 */
public final class ChatHistory {

    /** 默认容量(与原版聊天行数量同量级)。 */
    public static final int DEFAULT_CAPACITY = 100;

    private final int capacity;

    /** 时间序:index 0 = 最新。 */
    private final List<ChatLineRecord> lines = new ArrayList<ChatLineRecord>();

    /** 自底部向上偏移的行数(0 = 未滚动)。 */
    private int scrollOffset = 0;

    public ChatHistory() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * @param capacity 容量上限(≥1),超容量裁最旧
     */
    public ChatHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity 必须 ≥ 1");
        }
        this.capacity = capacity;
    }

    /**
     * 追加消息(新行在前,超容量裁最旧)。
     *
     * @param component 消息组件(非空)
     * @param messageId 原版消息 ID
     */
    public synchronized void append(IChatComponent component, int messageId) {
        append(new ChatLineRecord(component, messageId, System.currentTimeMillis()));
    }

    /**
     * 追加行记录(测试/自定义时钟入口)。
     *
     * @param record 行记录(非空)
     */
    public synchronized void append(ChatLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record 不能为空");
        }
        lines.add(0, record);
        while (lines.size() > capacity) {
            lines.remove(lines.size() - 1);
        }
    }

    /**
     * 按消息 ID 精确删除(原版 deleteChatLine 语义)。
     *
     * @param messageId 消息 ID
     * @return 是否删除到至少一条
     */
    public synchronized boolean deleteById(int messageId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getMessageId() == messageId) {
                lines.remove(i);
                return true;
            }
        }
        return false;
    }

    /** 清空历史并复位滚动。 */
    public synchronized void clear() {
        lines.clear();
        scrollOffset = 0;
    }

    /**
     * 滚动偏移(原版 scroll 语义:n &gt; 0 向旧消息,n &lt; 0 向新消息;下限 0)。
     *
     * @param amount 偏移增量
     */
    public synchronized void scrollBy(int amount) {
        scrollOffset = Math.max(0, scrollOffset + amount);
    }

    /** 复位滚动到底部(最新)。 */
    public synchronized void resetScroll() {
        scrollOffset = 0;
    }

    /** @return 当前滚动偏移(行数,0 = 底部) */
    public synchronized int getScroll() {
        return scrollOffset;
    }

    /** @return 是否已滚动离开底部 */
    public synchronized boolean isScrolled() {
        return scrollOffset > 0;
    }

    /** @return 当前行数 */
    public synchronized int size() {
        return lines.size();
    }

    /**
     * @return 不可变快照(时间序,index 0 = 最新);快照与内部列表解耦
     */
    public synchronized List<ChatLineRecord> snapshot() {
        return new ArrayList<ChatLineRecord>(lines);
    }
}
