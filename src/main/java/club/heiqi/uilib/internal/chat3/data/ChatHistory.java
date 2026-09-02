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
    /**
     * 行域滚动上限(显示行),由视图按真实内容几何回填。
     *
     * <p>默认 {@code Integer.MAX_VALUE} = **几何未知时不设限**,而不是 0。取 0 会让任何
     * 首次布局之前的滚动(以及不经容器布局的调用方)被凭空拦腰截断,那是拿"我还不知道"
     * 冒充"我知道不能滚"。已知上限后严格 clamp。</p>
     */
    private int maxScrollOffset = Integer.MAX_VALUE;

    /** 序列号分配器(进程内单调递增,每条入史记录唯一)。 */
    private long nextSequence = 1L;

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
     * <p>messageId 替换语义(原版 setChatLine 口径,2026-08 Tab 补全 R3 落地):
     * 非 0 id 先删同 id 旧行再插入(同 id 覆盖打印不刷屏,原版候选列表 id=1 同款);
     * id=0 恒追加——普通聊天消息真机 id 恒 0,不可互相替换。</p>
     *
     * @param component 消息组件(非空)
     * @param messageId 原版消息 ID
     */
    public synchronized void append(IChatComponent component, int messageId) {
        if (messageId != 0) {
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).getMessageId() == messageId) {
                    lines.remove(i);
                }
            }
        }
        lines.add(0, new ChatLineRecord(component, messageId, System.currentTimeMillis(), nextSequence++));
        trimToCapacity();
    }

    /**
     * 追加行记录(测试/自定义时钟入口);入史时分配唯一递增序列号。
     *
     * @param record 行记录(非空)
     */
    public synchronized void append(ChatLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record 不能为空");
        }
        lines.add(0, record.withSequence(nextSequence++));
        trimToCapacity();
    }

    private void trimToCapacity() {
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
     * 滚动偏移(原版 scroll 语义:n &gt; 0 向旧消息,n &lt; 0 向新消息;区间 [0, maxScrollOffset])。
     *
     * <p><b>上限必须在这里,不能在渲染投影里。</b>旧实现只有下限 0,注释写着"上限由视口偏移
     * clamp 折算保证" —— 那是把权威约束委托给了显示层:渲染看着正常(到顶就停),行域却一路
     * 无界累加,玩家要往回滚得先把这些**看不见的死值**全部消费掉,表现为"滚不动/要滚很久才动"。
     * 上限由视图按真实几何回填({@link #setMaxScrollOffset});几何未知时保持 0。</p>
     *
     * @param amount 偏移增量
     */
    public synchronized void scrollBy(int amount) {
        scrollOffset = clampScroll(scrollOffset + amount);
    }

    /** 行域 clamp:[0, maxScrollOffset](上限为负时按 0 处理)。 */
    private int clampScroll(int value) {
        return Math.max(0, Math.min(value, Math.max(0, maxScrollOffset)));
    }

    /**
     * 回填行域滚动上限(单位:显示行,由视图按真实内容几何算出)。
     *
     * <p>内容变少、窗口变高、历史裁剪都会让上限收缩;此时越界的当前偏移必须**当场拉回**,
     * 否则又回到"账上留着看不见的死值"。</p>
     *
     * @param lines 可向上滚动的最大行数(负值按 0)
     * @return 本次回填是否把当前偏移拉回过(调用方据此决定是否通知视图重算)
     */
    public synchronized boolean setMaxScrollOffset(int lines) {
        int ceiling = Math.max(0, lines);
        maxScrollOffset = ceiling;
        int clamped = clampScroll(scrollOffset);
        boolean pulledBack = clamped != scrollOffset;
        scrollOffset = clamped;
        return pulledBack;
    }

    /** @return 当前行域滚动上限(显示行) */
    public synchronized int getMaxScrollOffset() {
        return maxScrollOffset;
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
