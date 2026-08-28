package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 消息生命周期注册表(L2 视图模型):sequenceId → MessageLifecycle。
 *
 * <p>生命周期与 {@link ChatLineRecord#getSequenceId()} 绑定,消息离史(历史容量裁剪)
 * 即 {@link #purge(List)} 联动移除,防泄漏;过期移除本身走 HUD 队列队首弹出,
 * 注册表只承担按序列号查询/新建与裁剪联动。主线程使用,无需加锁。</p>
 */
public final class MessageLifecycleRegistry {

    /** sequenceId → 消息生命周期(与历史容量裁剪联动)。 */
    private final Map<Long, MessageLifecycle> lifecycles = new HashMap<Long, MessageLifecycle>();

    /**
     * 取生命周期;不存在则按默认预算新建并登记。
     *
     * @param sequenceId          消息序列号(ChatLineRecord.sequenceId)
     * @param defaultBudgetMillis 新建时的默认显示预算(ms)
     * @return 该序列号既有实例或新建实例
     */
    public MessageLifecycle ensure(long sequenceId, long defaultBudgetMillis) {
        MessageLifecycle lifecycle = lifecycles.get(sequenceId);
        if (lifecycle == null) {
            lifecycle = new MessageLifecycle(defaultBudgetMillis);
            lifecycles.put(sequenceId, lifecycle);
        }
        return lifecycle;
    }

    /**
     * @param sequenceId 消息序列号
     * @return 对应生命周期;不存在返回 null
     */
    public MessageLifecycle get(long sequenceId) {
        return lifecycles.get(sequenceId);
    }

    /**
     * 仅保留快照内的序列号(历史容量裁剪联动,防泄漏)。
     *
     * @param snapshot 历史快照(时间序);快照外序列号的生命周期被移除
     */
    public void purge(List<ChatLineRecord> snapshot) {
        HashSet<Long> alive = new HashSet<Long>();
        for (ChatLineRecord record : snapshot) {
            if (record != null) {
                alive.add(record.getSequenceId());
            }
        }
        lifecycles.keySet().retainAll(alive);
    }

    /** 清空注册表。 */
    public void clear() {
        lifecycles.clear();
    }

    /** @return 当前登记的生命周期数 */
    public int size() {
        return lifecycles.size();
    }
}
