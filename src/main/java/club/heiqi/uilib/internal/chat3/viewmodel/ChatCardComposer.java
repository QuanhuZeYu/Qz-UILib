package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 气泡合成器(L2 视图模型,纯函数):消息组 → 可渲染组(组头 + 切分行 + 存活 alpha)。
 *
 * <p>职责:组头文本(发送者名 + HH:mm)、名字配色、每行「去前缀 + 切分」、存活/淡出 alpha。
 * 几何(宽高/坐标/命中)由 {@link ChatGeometry} 完成。</p>
 */
public final class ChatCardComposer {

    /** 组内一条消息的渲染数据:记录 + 切分后的显示行(去前缀,保留格式码)。 */
    public static final class MessageLines {

        private final ChatLineRecord record;
        private final List<String> displayLines;
        private final float maxLineWidth;

        private MessageLines(ChatLineRecord record, List<String> displayLines, float maxLineWidth) {
            this.record = record;
            this.displayLines = displayLines;
            this.maxLineWidth = maxLineWidth;
        }

        /** @return 消息记录(命中检测回投事件链用) */
        public ChatLineRecord getRecord() {
            return record;
        }

        /** @return 切分后的显示行(时间正序) */
        public List<String> getDisplayLines() {
            return displayLines;
        }

        /** @return 消息最宽行宽(px,气泡宽度依据) */
        public float getMaxLineWidth() {
            return maxLineWidth;
        }
    }

    /** 合成后的组(几何无关)。 */
    public static final class ComposedGroup {

        private final MessageGroupModel.Alignment alignment;
        private final String sender;
        private final String headerText;
        private final int nameColor;
        private final List<MessageLines> messages;
        private final long latestMillis;
        private final int alpha;

        private ComposedGroup(MessageGroupModel.Alignment alignment, String sender, String headerText,
                int nameColor, List<MessageLines> messages, long latestMillis, int alpha) {
            this.alignment = alignment;
            this.sender = sender;
            this.headerText = headerText;
            this.nameColor = nameColor;
            this.messages = messages;
            this.latestMillis = latestMillis;
            this.alpha = alpha;
        }

        /** @return 组对齐 */
        public MessageGroupModel.Alignment getAlignment() {
            return alignment;
        }

        /** @return 发送者名(系统组为 null) */
        public String getSender() {
            return sender;
        }

        /** @return 组头文本(玩家组 = "名字 HH:mm";系统组 = 空串) */
        public String getHeaderText() {
            return headerText;
        }

        /** @return 发送者名颜色(ARGB) */
        public int getNameColor() {
            return nameColor;
        }

        /** @return 组内消息(时间正序) */
        public List<MessageLines> getMessages() {
            return Collections.unmodifiableList(messages);
        }

        /** @return 组内最新消息到达时刻 */
        public long getLatestMillis() {
            return latestMillis;
        }

        /** @return 存活/淡出 alpha(0..255) */
        public int getAlpha() {
            return alpha;
        }

        /** @return alpha &gt; 0(HUD 形态过期组不渲染) */
        public boolean isVisible() {
            return alpha > 0;
        }
    }

    private final ChatLineLayouter layouter;

    /**
     * @param layouter 行切分器(与渲染同源度量)
     */
    public ChatCardComposer(ChatLineLayouter layouter) {
        this.layouter = layouter;
    }

    /**
     * @param group         消息组
     * @param nowMillis     当前时刻
     * @param maxLineWidthPx 单行最大宽度(窗口宽 - 2×边距 - 2×内边距)
     * @return 合成组;HUD 形态过期(alpha = 0)仍返回(容器形态不裁剪)
     */
    public ComposedGroup compose(MessageGroupModel group, long nowMillis, int maxLineWidthPx) {
        long latestMillis = group.getLatestMillis();
        int alpha = fadeAlpha(latestMillis, nowMillis,
                ChatMarkdownSettings.getHudTtlMillis(), ChatMarkdownSettings.getHudFadeMillis(), 255);
        MessageGroupModel.Alignment alignment = group.getAlignment();
        String headerText = "";
        int nameColor = 0xFFFFFFFF;
        if (alignment != MessageGroupModel.Alignment.SYSTEM_CENTER) {
            String sender = group.getSender();
            nameColor = alignment == MessageGroupModel.Alignment.SELF_RIGHT
                    ? SenderColorPalette.SELF_NAME_ARGB : SenderColorPalette.colorFor(sender);
            headerText = sender + " " + ChatClock.formatTime(latestMillis);
        }
        List<MessageLines> messages = new ArrayList<MessageLines>();
        for (MessageGroupModel.GroupLine line : group.getLines()) {
            ChatLineRecord record = line.getRecord();
            String display = displayText(line);
            List<String> lines = layouter.layout(display, maxLineWidthPx);
            float maxLineWidth = 0.0F;
            for (String textLine : lines) {
                maxLineWidth = Math.max(maxLineWidth, layouter.measureWidth(textLine));
            }
            messages.add(new MessageLines(record, lines, maxLineWidth));
        }
        return new ComposedGroup(alignment, group.getSender(), headerText, nameColor, messages, latestMillis, alpha);
    }

    /**
     * 气泡内显示文本:去「&lt;名字&gt; 」前缀(按有效字符数剥离,保留 § 样式码)。
     * 系统消息显示全文。
     */
    private static String displayText(MessageGroupModel.GroupLine line) {
        String plain = line.getRecord().getPlainText();
        String rest = line.getRest();
        if (plain.length() == rest.length()) {
            return line.getRecord().getFormattedText();
        }
        return FormatPrefixStripper.strip(line.getRecord().getFormattedText(), plain.length() - rest.length());
    }

    /**
     * 存活/淡出 alpha(纯函数,HUD 形态):TTL 窗口内恒满,过期后线性降,淡出窗结束归零。
     *
     * <p>截断语义:乘法除法均为整数运算,降幅向下取整(与上轮 alpha 截断口径一致)。</p>
     *
     * @param latestMillis 组内最新消息到达时刻
     * @param nowMillis    当前时刻
     * @param ttlMillis    存活窗口
     * @param fadeMillis   淡出时长(≤0 视为过期即消失)
     * @param maxAlpha     alpha 上限(255)
     * @return 0..maxAlpha
     */
    public static int fadeAlpha(long latestMillis, long nowMillis, long ttlMillis, long fadeMillis, int maxAlpha) {
        long age = nowMillis - latestMillis;
        if (age < ttlMillis) {
            return maxAlpha;
        }
        if (fadeMillis <= 0) {
            return 0;
        }
        long elapsed = age - ttlMillis;
        if (elapsed >= fadeMillis) {
            return 0;
        }
        return maxAlpha - (int) ((long) maxAlpha * elapsed / fadeMillis);
    }
}
