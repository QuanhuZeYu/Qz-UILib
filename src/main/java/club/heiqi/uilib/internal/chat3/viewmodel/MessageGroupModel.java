package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 消息组模型(L2 视图模型,纯数据):相邻同发送者合并的 Telegram 气泡组。
 *
 * <p>行序 = 时间正序(最旧在上,最新在下);组存活/淡出以组内最新消息的
 * {@link #getLatestMillis()} 驱动。每行携带去前缀后的消息本体(气泡内只显示本体)。</p>
 */
public final class MessageGroupModel {

    /** 组对齐:自己靠右 / 他人靠左 / 系统居中(无气泡)。 */
    public enum Alignment {
        /** 自己的消息组:右对齐 + 主题蓝气泡。 */
        SELF_RIGHT,
        /** 他人的消息组:左对齐 + 深灰气泡。 */
        OTHER_LEFT,
        /** 系统/广播消息:居中灰白小字,无气泡,每条独立。 */
        SYSTEM_CENTER
    }

    /** 组内一行:消息记录 + 去前缀后的消息本体(系统消息 = 全文)。 */
    public static final class GroupLine {

        private final ChatLineRecord record;
        private final String rest;

        private GroupLine(ChatLineRecord record, String rest) {
            this.record = record;
            this.rest = rest;
        }

        /** @return 消息记录 */
        public ChatLineRecord getRecord() {
            return record;
        }

        /** @return 去发送者前缀后的消息本体(气泡内显示;系统消息 = 全文) */
        public String getRest() {
            return rest;
        }
    }

    private final String sender;
    private final Alignment alignment;
    private final List<GroupLine> lines;

    private MessageGroupModel(String sender, Alignment alignment, ChatLineRecord record, String rest) {
        this.sender = sender;
        this.alignment = alignment;
        this.lines = new ArrayList<GroupLine>();
        this.lines.add(new GroupLine(record, rest));
    }

    /**
     * 玩家消息组(非系统)。
     *
     * @param sender    发送者名(非空)
     * @param isSelf    是否本地玩家
     * @param record    组内首条消息
     * @param rest      去前缀后的消息本体
     */
    static MessageGroupModel player(String sender, boolean isSelf, ChatLineRecord record, String rest) {
        return new MessageGroupModel(sender, isSelf ? Alignment.SELF_RIGHT : Alignment.OTHER_LEFT, record, rest);
    }

    /**
     * 系统消息组(每条独立,本体 = 全文)。
     *
     * @param record 消息
     */
    static MessageGroupModel system(ChatLineRecord record) {
        return new MessageGroupModel(null, Alignment.SYSTEM_CENTER, record, record.getPlainText());
    }

    /** 追加一条消息(仅同发送者合并路径调用)。 */
    void addLine(ChatLineRecord record, String rest) {
        lines.add(new GroupLine(record, rest));
    }

    /** @return 发送者名(系统组为 null) */
    public String getSender() {
        return sender;
    }

    /** @return 组对齐 */
    public Alignment getAlignment() {
        return alignment;
    }

    /** @return 组内消息(时间正序,不可变视图) */
    public List<GroupLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** @return 组内最新消息到达时刻(存活/淡出驱动) */
    public long getLatestMillis() {
        long latest = 0L;
        for (GroupLine line : lines) {
            latest = Math.max(latest, line.getRecord().getArrivedWallMillis());
        }
        return latest;
    }
}
