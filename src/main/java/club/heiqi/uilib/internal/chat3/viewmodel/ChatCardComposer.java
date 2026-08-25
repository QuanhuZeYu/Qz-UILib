package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 气泡合成器(L2 视图模型,纯函数):消息组 → 可渲染组(组头 + 切分行 + 存活 alpha)。
 *
 * <p>职责:组头文本(发送者名 + HH:mm)、名字配色、每行「去前缀 + 切分」、存活/淡出 alpha。
 * 几何(宽高/坐标/命中)由渲染层 scene 树完成(旧 ChatGeometry 已删除,生产路径无引用)。</p>
 */
public final class ChatCardComposer {

    /** HUD 形态单条消息最大显示行数(设计稿 §5.4:maxLines=8,超出末行省略号;容器形态完整显示)。 */
    public static final int HUD_MAX_LINES = 8;

    /** 截断末行省略号(与 SceneLineClamp.ELLIPSIS 同款三 ASCII 点,任何字体都有字形)。 */
    public static final String ELLIPSIS = "...";

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
        private final String headerName;
        private final String headerTime;
        private final int nameColor;
        private final List<MessageLines> messages;
        private final long latestMillis;
        private final int alpha;

        private ComposedGroup(MessageGroupModel.Alignment alignment, String sender, String headerName,
                String headerTime, int nameColor, List<MessageLines> messages, long latestMillis, int alpha) {
            this.alignment = alignment;
            this.sender = sender;
            this.headerName = headerName;
            this.headerTime = headerTime;
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

        /** @return 组头发送者名段(他人组 = 发送者;自己组 = showSelfName 配置;系统组 = 空串) */
        public String getHeaderName() {
            return headerName;
        }

        /** @return 组头时间戳段(HH:mm;系统组 = 空串) */
        public String getHeaderTime() {
            return headerTime;
        }

        /** @return 组头文本(兼容用):名字非空 → "名字 HH:mm";否则仅时间戳 */
        public String getHeaderText() {
            return headerName.isEmpty() ? headerTime : headerName + " " + headerTime;
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
    /** 系统消息行切分器(font-system 12px 口径;null = 回退 body 切分器,旧行为)。 */
    private final ChatLineLayouter systemLayouter;

    /**
     * @param layouter 行切分器(与渲染同源度量)
     */
    public ChatCardComposer(ChatLineLayouter layouter) {
        this(layouter, null);
    }

    /**
     * @param layouter       气泡消息行切分器(body 字号口径)
     * @param systemLayouter 系统消息行切分器(system 字号口径;null = 回退 layouter)
     */
    public ChatCardComposer(ChatLineLayouter layouter, ChatLineLayouter systemLayouter) {
        this.layouter = layouter;
        this.systemLayouter = systemLayouter;
    }

    /**
     * @param group         消息组
     * @param nowMillis     当前时刻
     * @param maxLineWidthPx 单行最大宽度(窗口宽 - 2×边距 - 2×内边距)
     * @param applyTtl       true = HUD 形态(12s 存活 + easeInQuad 淡出);false = 容器形态(alpha 恒 255)
     * @return 合成组
     */
    public ComposedGroup compose(MessageGroupModel group, long nowMillis, int maxLineWidthPx, boolean applyTtl) {
        long latestMillis = group.getLatestMillis();
        int alpha = applyTtl ? fadeAlpha(latestMillis, nowMillis,
                ChatMarkdownSettings.getHudTtlMillis(), ChatMarkdownSettings.getHudFadeMillis(), 255) : 255;
        MessageGroupModel.Alignment alignment = group.getAlignment();
        String headerName = "";
        String headerTime = "";
        int nameColor = 0xFFFFFFFF;
        if (alignment != MessageGroupModel.Alignment.SYSTEM_CENTER) {
            String sender = group.getSender();
            nameColor = alignment == MessageGroupModel.Alignment.SELF_RIGHT
                    ? SenderColorPalette.SELF_NAME_ARGB : SenderColorPalette.colorFor(sender);
            headerTime = ChatClock.formatTime(latestMillis);
            // 自己组默认不显示名字(位置已表达归属,设计稿 §3.3/showSelfName 默认 false)
            headerName = alignment == MessageGroupModel.Alignment.SELF_RIGHT
                    && !ChatMarkdownSettings.isShowSelfName() ? "" : sender;
        }
        // SYSTEM_CENTER:无组头(headerName/headerTime 空,nameColor 白)
        // K3 三轮:系统消息按 font-system 12px 口径切分(切分与渲染同源),
        // 系统行切分器未注入时回退 body 切分器(旧行为)
        ChatLineLayouter active = alignment == MessageGroupModel.Alignment.SYSTEM_CENTER
                && systemLayouter != null ? systemLayouter : layouter;
        List<MessageLines> messages = new ArrayList<MessageLines>();
        for (MessageGroupModel.GroupLine line : group.getLines()) {
            ChatLineRecord record = line.getRecord();
            String display = displayText(line);
            List<String> lines = active.layout(display, maxLineWidthPx);
            if (applyTtl) {
                lines = clampHudLines(lines, maxLineWidthPx);
            }
            float maxLineWidth = 0.0F;
            for (String textLine : lines) {
                maxLineWidth = Math.max(maxLineWidth, active.measureWidth(textLine));
            }
            messages.add(new MessageLines(record, lines, maxLineWidth));
        }
        return new ComposedGroup(alignment, group.getSender(), headerName, headerTime, nameColor, messages,
                latestMillis, alpha);
    }

    /**
     * HUD 单条消息行数截断(设计稿 §5.4):超过 {@link #HUD_MAX_LINES} 行时保留前 8 行,
     * 末行按行宽上限裁剪后追加省略号(与 SceneLineClamp 语义一致:行数恰好等于上限不截断)。
     * 容器形态(applyTtl=false)不调用,同一消息完整显示(验收 22)。
     *
     * @param lines         切分后的显示行(layouter 输出,不可变)
     * @param maxLineWidthPx 单行最大宽度(与 layouter.layout 同款行宽上限,省略号不回填超宽)
     * @return 截断后的行列表(新列表);未超限返回原列表语义(零拷贝)
     */
    private List<String> clampHudLines(List<String> lines, float maxLineWidthPx) {
        if (lines.size() <= HUD_MAX_LINES) {
            return lines;
        }
        List<String> kept = new ArrayList<String>(lines.subList(0, HUD_MAX_LINES));
        String last = kept.get(HUD_MAX_LINES - 1);
        kept.set(HUD_MAX_LINES - 1, ellipsizeTail(last, maxLineWidthPx));
        return kept;
    }

    /**
     * 末行追加省略号:行宽未超可用宽(行宽上限 - 省略号宽)时原行 + 省略号;
     * 超限时逐字符裁剪(§ 格式码对零宽且不可拆)到可用宽,再追加省略号。
     */
    private String ellipsizeTail(String line, float maxLineWidthPx) {
        float ellipsisWidth = layouter.measureWidth(ELLIPSIS);
        float available = maxLineWidthPx - ellipsisWidth;
        if (layouter.measureWidth(line) <= available) {
            return line + ELLIPSIS;
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < line.length();) {
            char ch = line.charAt(i);
            if (ch == '\u00a7' && i + 1 < line.length()) {
                // 格式码对:零宽、不可拆,始终保留
                kept.append(ch).append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            if (layouter.measureWidth(kept.toString() + ch) > available) {
                break;
            }
            kept.append(ch);
            i++;
        }
        return kept.toString() + ELLIPSIS;
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
     * 颜色 alpha 烘焙(纯函数):基础 alpha × 淡出因子(整数截断),保留 RGB。
     *
     * <p>半透明基础色(如气泡 E6)与淡出因子组合:alpha = 255 时结果 = 基础色本身。</p>
     *
     * @param baseArgb 基础色(ARGB)
     * @param alpha    淡出因子(0..255)
     * @return 烘焙后的 ARGB
     */
    public static int fadeColor(int baseArgb, int alpha) {
        int factor = Math.max(0, Math.min(255, alpha));
        int baseAlpha = (baseArgb >>> 24) & 0xFF;
        int combined = (baseAlpha * factor) / 255;
        return (baseArgb & 0x00FFFFFF) | (combined << 24);
    }

    /**
     * 白插值叠加(纯函数,hover 叠加层用):RGB 各通道向纯白按 t 线性插值,
     * alpha 通道保持基础色(设计稿 §2.1 overlay-hover = 3% 白,t 预计算一次)。
     *
     * <p>语义 = 底色与 0x08FFFFFF(3% 白)混合的可预计算近似:叠加层不透明度 8/255 ≈ 3.1%,
     * 取 t=0.03(3%)与设计稿「3% 白」一致;t 为 0 时结果 = 基础色本身,实现零开销。</p>
     *
     * @param baseArgb 基础色(ARGB)
     * @param t        白插值比例(0..1,越界夹取)
     * @return 混合后的 ARGB
     */
    public static int mixWithWhite(int baseArgb, float t) {
        float blend = Math.max(0.0F, Math.min(1.0F, t));
        int alpha = (baseArgb >>> 24) & 0xFF;
        int r = (baseArgb >> 16) & 0xFF;
        int g = (baseArgb >> 8) & 0xFF;
        int b = baseArgb & 0xFF;
        r = (int) (r + (255 - r) * blend + 0.5F);
        g = (int) (g + (255 - g) * blend + 0.5F);
        b = (int) (b + (255 - b) * blend + 0.5F);
        return (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * 气泡 hover 底色(纯函数,预计算一次):基础气泡色 + 3% 白叠加。
     *
     * @param baseArgb 气泡基础色(ARGB,如 0xF2242B33)
     * @return hover 底色(ARGB)
     */
    public static int hoveredBubbleColor(int baseArgb) {
        return mixWithWhite(baseArgb, 0.03F);
    }

    /**
     * ARGB 逐通道线性插值(纯函数,P2-4 hover 颜色插值用;设计稿 §4.1:气泡叠加 100ms /
     * 链接提亮 80ms 的 easeOutQuad 中间态按通道 lerp)。
     *
     * @param from 起点色(ARGB)
     * @param to   终点色(ARGB)
     * @param t    进度(越界夹取 [0,1];t=0 恒返回 from、t=1 恒返回 to)
     * @return 插值色(ARGB,每通道四舍五入)
     */
    public static int interpolateArgb(int from, int to, float t) {
        if (t <= 0.0F) {
            return from;
        }
        if (t >= 1.0F) {
            return to;
        }
        int a = interpolateChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = interpolateChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = interpolateChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = interpolateChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateChannel(int from, int to, float t) {
        return from + Math.round((to - from) * t);
    }

    /**
     * 段流颜色插值(纯函数,P2-4 链接 hover 提亮中间态):以 hover 为模板(同段数,由
     * {@link ChatUrlLinkifier#hoverLinkify} 保证),仅 link 段颜色在 base↔hover 间插值;
     * 非 link 段(含 LaTeX/code 段)原引用透传——下划线等样式位随目标态(hover 模板),
     * 设计稿 §4.1 只要求颜色插值。
     *
     * @param base  基础段流(不可变)
     * @param hover hover 段流(同结构)
     * @param t     进度(越界夹取;t≤0 恒返回 base、t≥1 恒返回 hover)
     * @return 插值段流(中间态新列表,端态零分配复用)
     */
    public static List<TextSegment> interpolateSegments(List<TextSegment> base, List<TextSegment> hover, float t) {
        if (t <= 0.0F) {
            return base;
        }
        if (t >= 1.0F) {
            return hover;
        }
        int size = Math.min(base.size(), hover.size());
        List<TextSegment> out = new ArrayList<TextSegment>(hover.size());
        for (int i = 0; i < size; i++) {
            TextSegment hoverSegment = hover.get(i);
            if (hoverSegment.isLatex() || hoverSegment.getStyle().getLink() == null) {
                out.add(hoverSegment); // 非 link 段(含 LaTeX/code)原引用透传
                continue;
            }
            TextStyle style = hoverSegment.getStyle().copy();
            style.setColor(interpolateArgb(base.get(i).getStyle().getColor(),
                    hoverSegment.getStyle().getColor(), t));
            out.add(new TextSegment(hoverSegment.getText(), style));
        }
        for (int i = size; i < hover.size(); i++) {
            out.add(hover.get(i)); // 防御兜底:结构恒等时不可达
        }
        return out;
    }

    /**
     * 段流 alpha 烘焙(纯函数):alpha ≥ 255 零分配复用原列表。
     *
     * @param base  基础段流(不可变)
     * @param alpha 目标 alpha(0..255)
     * @return 烘焙后的段流(alpha ≥ 255 时同引用)
     */
    public static List<TextSegment> fadeSegments(List<TextSegment> base, int alpha) {
        if (alpha >= 255) {
            return base;
        }
        List<TextSegment> faded = new ArrayList<TextSegment>(base.size());
        for (TextSegment segment : base) {
            TextStyle style = segment.getStyle().copy();
            style.setColor(fadeColor(style.getColor(), alpha));
            faded.add(new TextSegment(segment.getText(), style));
        }
        return faded;
    }

    /**
     * 存活/淡出 alpha(纯函数,HUD 形态):TTL 窗口内恒满,过期后按 easeInQuad(1-p²) 慢启动降,
     * 淡出窗结束归零。设计稿 §4.3/§5.3:alpha = floor(255 × (1-p²)),p 从 0→1。
     *
     * <p>截断语义:乘法除法均为整数运算(等价于 floor),与上轮 alpha 截断口径一致;
     * 曲线与 {@code Animator.easeInQuad} 同族(p²),此处就地展开避免 viewmodel → view 反向依赖。</p>
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
        // alpha = floor(maxAlpha × (1 - p²)),p = elapsed/fade;整数运算 = 逐点向下取整
        long fadeSq = fadeMillis * fadeMillis;
        long elapsedSq = elapsed * elapsed;
        return (int) ((long) maxAlpha * (fadeSq - elapsedSq) / fadeSq);
    }
}
