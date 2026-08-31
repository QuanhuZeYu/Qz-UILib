package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已发送消息历史(输入上下键回显,vanilla getSentHistory 语义)。
 *
 * <p>语义:新发送消息按时间序追加到尾部、与上一条相同跳过、超上限裁最旧;光标 cursor 指向
 * 「空槽」(初始 = size);recall(-1) 上一条、recall(+1) 下一条,首次翻入历史暂存当前输入
 * (原版 historyBuffer),光标回到 size 时恢复暂存草稿而非清空(T4)。打开输入屏时经
 * {@link #syncFrom(List)} 从 facade 继承的原版发送列表全量同步
 * (覆盖第三方 mod 直调 addToSentMessages 的条目),发送路径自身经 {@link #add(String)} 增量追加。</p>
 */
public final class ChatSentHistory {

    /** 历史上限(vanilla 口径)。 */
    public static final int MAX_ENTRIES = 100;

    private final List<String> messages = new ArrayList<String>();
    private int cursor;
    /** 进入历史时的当前输入暂存(原版 historyBuffer;null = 无暂存)。 */
    private String draft;

    /**
     * 追加已发送消息(时间序尾插 + 相邻重复跳过 + 超上限裁最旧;光标复位空槽)。
     *
     * @param message 消息文本(null 忽略)
     */
    public synchronized void add(String message) {
        if (message == null) {
            return;
        }
        if (!messages.isEmpty() && messages.get(messages.size() - 1).equals(message)) {
            return;
        }
        messages.add(message);
        while (messages.size() > MAX_ENTRIES) {
            messages.remove(0);
        }
        cursor = messages.size();
    }

    /**
     * 按相对方向回显历史(vanilla getSentHistory 语义,无草稿暂存参数)。
     *
     * @param direction -1 = 上一条,+1 = 下一条
     * @return 命中历史消息;光标到空槽恢复暂存草稿;无暂存返回 null(无效操作哨兵)
     */
    public synchronized String recall(int direction) {
        return recall(direction, null);
    }

    /**
     * 按相对方向回显历史(vanilla getSentHistory 语义,带草稿暂存)。
     *
     * <p>首次从空槽翻入历史时暂存当前输入(原版 historyBuffer);回到最底(index=size)时
     * 恢复暂存文本而非清空,并清掉暂存(T4 历史草稿恢复)。</p>
     *
     * <p>I3 草稿清空修复:底槽且无暂存({@code draft == null})返回 null 无效操作哨兵
     * (vanilla getSentHistory 越界返回 null 同款);空串是合法暂存值,恢复仍返回 ""。
     * 入历史暂存条件仅当 draft == null 且 currentInput != null 时生效,使「无暂存」与
     * 「空草稿」天然区分;单参 recall(direction) 传 null 不暂存。</p>
     *
     * @param direction    -1 = 上一条,+1 = 下一条
     * @param currentInput 当前输入文本(进入历史时暂存用;null 不暂存)
     * @return 命中历史消息;光标到空槽恢复暂存草稿;无暂存返回 null(无效操作哨兵)
     */
    public synchronized String recall(int direction, String currentInput) {
        cursor = Math.max(0, Math.min(cursor + direction, messages.size()));
        if (cursor >= messages.size()) {
            if (draft == null) {
                return null;
            }
            String restored = draft;
            draft = null;
            return restored;
        }
        if (draft == null && currentInput != null) {
            draft = currentInput;
        }
        return messages.get(cursor);
    }

    /** 复位光标到空槽并清草稿暂存(打开输入屏时)。 */
    public synchronized void resetCursor() {
        cursor = messages.size();
        draft = null;
    }

    /**
     * 从原版发送列表全量同步(打开输入屏时;覆盖第三方直调条目),光标复位空槽。
     *
     * @param vanillaSent 原版发送列表(时间序,最旧在前)
     */
    public synchronized void syncFrom(List<String> vanillaSent) {
        messages.clear();
        if (vanillaSent != null) {
            messages.addAll(vanillaSent);
            while (messages.size() > MAX_ENTRIES) {
                messages.remove(0);
            }
        }
        cursor = messages.size();
        draft = null;
    }

    /** @return 当前历史条数 */
    public synchronized int size() {
        return messages.size();
    }

    /** @return 只读快照(时间序,最旧在前) */
    public synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<String>(messages));
    }
}
