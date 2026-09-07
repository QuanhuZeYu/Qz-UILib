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
 *
 * <p>C7 起每行另带「本体来源」标记(结构通道 / 正则兜底)，气泡装配处据此决定是否回读组件
 * 文本，见 {@link GroupLine#isStructured()}。</p>
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

    /** 组内一行:消息记录 + 去前缀后的消息本体(系统消息 = 全文) + 本体来源标记。 */
    public static final class GroupLine {

        private final ChatLineRecord record;
        private final String rest;
        /**
         * C7:rest 是否来自 {@link StructuredChatReader} 的原版结构参数
         * ({@code getFormatArgs()[1]})。
         *
         * <p>true ⇒ 本体已是「组件里的原始文本」，直接进 markdown；装配处不得再回读
         * {@code record.getPlainText()}/{@code getFormattedText()}——前者对翻译组件是
         * StatCollector 语言表查找，后者还逐组件注样式码(§ 残渣的唯一来源)。
         * false ⇒ 正则兜底或系统行，走旧装配口径。</p>
         */
        private final boolean structured;

        private GroupLine(ChatLineRecord record, String rest, boolean structured) {
            this.record = record;
            this.rest = rest;
            this.structured = structured;
        }

        /** @return 消息记录 */
        public ChatLineRecord getRecord() {
            return record;
        }

        /** @return 去发送者前缀后的消息本体(气泡内显示;系统消息 = 全文) */
        public String getRest() {
            return rest;
        }

        /** @return true = 本体取自原版结构(C7 结构通道;包内装配读端，公共面零变化) */
        boolean isStructured() {
            return structured;
        }
    }

    private final String sender;
    private final Alignment alignment;
    private final List<GroupLine> lines;

    private MessageGroupModel(String sender, Alignment alignment, ChatLineRecord record, String rest,
            boolean structured) {
        this.sender = sender;
        this.alignment = alignment;
        this.lines = new ArrayList<GroupLine>();
        this.lines.add(new GroupLine(record, rest, structured));
    }

    /**
     * 玩家消息组(非系统)。
     *
     * @param sender     发送者名(非空)
     * @param isSelf     是否本地玩家
     * @param record     组内首条消息
     * @param rest       去前缀后的消息本体(unformatted 源:结构 args[1] 或正则 rest)
     * @param structured 本体是否来自 {@link StructuredChatReader}(C7 两条通道的分尺点)
     */
    static MessageGroupModel player(String sender, boolean isSelf, ChatLineRecord record, String rest,
            boolean structured) {
        return new MessageGroupModel(sender, isSelf ? Alignment.SELF_RIGHT : Alignment.OTHER_LEFT,
                record, rest, structured);
    }

    /**
     * 系统消息组(每条独立,本体 = 全文)。
     *
     * @param record 消息
     */
    static MessageGroupModel system(ChatLineRecord record) {
        return new MessageGroupModel(null, Alignment.SYSTEM_CENTER, record, record.getPlainText(),
                false);
    }

    /** 追加一条消息(仅同发送者合并路径调用)。 */
    void addLine(ChatLineRecord record, String rest, boolean structured) {
        lines.add(new GroupLine(record, rest, structured));
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
