package club.heiqi.uilib.internal.chat3;

/**
 * 聊天系统 3.0 配置(进程级开关;观感参数在 S2/S3 阶段按设计规格补充)。
 *
 * <p>关闭后安装器把原版实例写回 GuiIngame.persistantChatGUI,原版对话框整套回归(逃生舱语义,
 * 用户裁决)。默认开。</p>
 */
public final class ChatMarkdownSettings {

    /** 聊天 3.0 接管总开关(默认开;off = 逃生舱,回退原版整套)。 */
    private static volatile boolean enabled = true;

    // ==================== 布局/形态参数(设计稿 §7 参数表,进程级) ====================

    /** 聊天气泡字号(px;font-body 13)。 */
    private static volatile int chatFontSizePx = 13;
    /** 行距附加(px;行高 = 字号 + 行距)。 */
    private static volatile int chatLineSpacingPx = 5;
    /** 聊天窗口宽 = 视口宽 × 比例(用户定:约 1/4,随窗口缩放动态)。 */
    private static volatile double chatWidthRatio = 0.25;
    /** 聊天窗口最小宽(逻辑 px;仅极小窗口兜底,不干扰比例——guiScale 下 min 过大曾把 1/4 顶成 1/3)。 */
    private static volatile int minChatWidthPx = 160;
    /** 聊天窗口最大宽(逻辑 px;新增封顶:4K 宽屏下 25% 不再无限拉长)。 */
    private static volatile int chatWidthMaxPx = 360;
    /** 聊天窗口距屏幕边缘边距(px)。 */
    private static volatile int chatMarginPx = 10;
    /** 气泡水平内边距(px;内边距定值 10)。 */
    private static volatile int bubblePaddingX = 10;
    /** 气泡垂直内边距(px;设计 6→5,纵向收 1px 提升密度)。 */
    private static volatile int bubblePaddingY = 5;
    /** 气泡圆角半径(px;r-lg 12)。 */
    private static volatile int bubbleCornerRadius = 12;
    /** 组内消息间距(px,紧密堆叠;sp-1 2)。 */
    private static volatile int groupInnerGapPx = 2;
    /** HUD 形态组间距(px,紧密堆叠;sp-3 4)。 */
    private static volatile int groupGapHudPx = 4;
    /** 容器形态组间距(px;sp-4 8)。 */
    private static volatile int groupGapContainerPx = 8;
    /** 组头名字字号(px;font-name 12)。 */
    private static volatile int nameFontSizePx = 12;
    /** 组头时间戳字号(px;font-meta 10)。 */
    private static volatile int timestampFontSizePx = 10;
    /** 系统消息字号(px;font-system 12)。 */
    private static volatile int systemFontSizePx = 12;
    /** 气泡内小圆角(px;r-inner 4,P1 圆角分级用)。 */
    private static volatile int bubbleInnerCornerRadiusPx = 4;
    /** 气泡最大宽 = 组内容宽 × 比例(P1 maxWidth 用)。 */
    private static volatile double bubbleMaxWidthRatio = 0.85;
    /** 行内 LaTeX 公式行高上限系数(设计稿 §3.5:渲染高 > 行高×1.6 触发缩放重排)。 */
    private static volatile float latexMaxLineHeightFactor = 1.6F;
    /** 行内 LaTeX 公式缩放系数(设计稿 §3.5:超限公式按 0.85 缩放重排)。 */
    private static volatile float latexShrinkFactor = 0.85F;
    /** HUD 形态存活窗口(ms,自组内最新消息起;TTL 10000→12000 给足阅读时间)。 */
    private static volatile long hudTtlMillis = 12000L;
    /** HUD 形态淡出时长(ms;fade 500→800 配合 easeInQuad)。 */
    private static volatile long hudFadeMillis = 800L;
    /** HUD 堆叠高度上限 = 视口高 × 比例(P1 刷屏让位用)。 */
    private static volatile double hudMaxHeightRatio = 0.5;
    /** HUD 组出生 enter 动画时长(ms;P1 opacity 通道用)。 */
    private static volatile long enterAnimMillis = 180L;
    /** 收起动画时长(ms,HUD 气泡收起;设计 160)。 */
    private static volatile long collapseAnimMillis = 160L;
    /** 弹出动画时长(ms,容器弹出/收回;设计 240)。 */
    private static volatile long popAnimMillis = 240L;
    /** 容器关闭动画时长(ms;设计稿 §4.1 closing 140,easeOutQuad 淡出+下滑)。 */
    private static volatile long closingAnimMillis = 140L;
    /** 滚轮一格滚动行数(设计:3 行;Shift 一格 1 行)。 */
    private static volatile int scrollWheelLines = 3;
    /** 平滑滚动时长(ms;行单位滚动的 120ms easeOutQuad,T5b;0 或负 = 瞬移语义)。 */
    private static volatile long smoothScrollMillis = 120L;
    /** 滚动条自动隐藏静止时长(ms;设计:静止 1200 后 300ms 淡出,P1 滚动条用)。 */
    private static volatile long scrollbarAutoHideMillis = 1200L;
    /** 容器高 = 视口高 × 比例(用户定:约 1/2,随窗口缩放动态)。 */
    private static volatile double containerHeightRatio = 0.5;
    /** 容器最小高(px)。 */
    private static volatile int minContainerHeightPx = 160;
    /** 输入条区高(px;设计稿 §6.2:输入条区高 40 贴容器底)。 */
    private static volatile int inputBarHeightPx = 40;
    /** 输入条圆角(px;设计稿 §2.1/§3.2:r-md 8)。 */
    private static volatile int inputCornerRadiusPx = 8;

    /** 自己气泡视觉风格(§10 已拍板:方案A accent)。 */
    public enum SelfBubbleStyle {
        /** 方案A:暗底 0xF2272F3A + 右侧 2px 强调条(已拍板)。 */
        ACCENT,
        /** 方案B:降饱和雾蓝底(备案)。 */
        CLASSIC
    }

    /** 自己气泡风格(已拍板 = accent;P1 强调条渲染用)。 */
    private static volatile SelfBubbleStyle selfBubbleStyle = SelfBubbleStyle.ACCENT;
    /** 自己组头是否显示名字(默认 false,位置已表达归属;P1 组头语义用)。 */
    private static volatile boolean showSelfName = false;

    // ==================== 色板(设计稿 §2.1 全部令牌,进程级) ====================

    /** 容器面板底(bg-container,95% 不透明冷蓝灰)。 */
    private static volatile int containerBgArgb = 0xF2171B20;
    /** 容器 1px 描边(border-container,10% 白)。 */
    private static volatile int containerBorderArgb = 0x1AFFFFFF;
    /** 容器圆角半径(px;r-lg 12)。 */
    private static volatile int containerCornerRadius = 12;
    /** 他人消息气泡底(bg-bubble-other,HUD 与容器同值)。 */
    private static volatile int bubbleOtherArgb = 0xF2242B33;
    /** 自己消息气泡底(bg-bubble-self-A,方案A 暗底配强调条)。 */
    private static volatile int bubbleSelfArgb = 0xF2272F3A;
    /** 自己气泡底·方案B(降饱和雾蓝,拍板备选未启用)。 */
    private static volatile int bubbleSelfAltArgb = 0xF2445C78;
    /** 自己气泡右侧 2px 强调条(accent-bar-self,方案A)。 */
    private static volatile int accentBarSelfArgb = 0xFF6B9BD8;
    /** 气泡 hover 叠加层(overlay-hover,3% 白,P1 hover 用)。 */
    private static volatile int overlayHoverArgb = 0x08FFFFFF;
    /** 正文兜底色(text-primary,91% 灰白替代纯白)。 */
    private static volatile int textPrimaryArgb = 0xFFE6E8EB;
    /** 组头辅助信息/元信息(text-secondary)。 */
    private static volatile int textSecondaryArgb = 0xFF9AA0A8;
    /** 时间戳文字(text-timestamp,实色,废弃半透明)。 */
    private static volatile int timeTextArgb = 0xFF8B929A;
    /** 系统消息文字(text-system,实色灰)。 */
    private static volatile int systemTextArgb = 0xFFB8BDC4;
    /** 自己名字色(text-name-self,比正文暗 20%)。 */
    private static volatile int textNameSelfArgb = 0xFFAAB3BC;
    /** 链接默认色(text-link)。 */
    private static volatile int linkArgb = 0xFF7AB8F5;
    /** 链接 hover 提亮色(text-link-hover)。 */
    private static volatile int linkHoverArgb = 0xFF9CCBF8;
    /** 行内 code 衬底(bg-code,15% 白)。 */
    private static volatile int codeBackgroundArgb = 0x26FFFFFF;
    /** 引用块左侧竖条(bar-quote,25% 白)。 */
    private static volatile int quoteBarArgb = 0x40FFFFFF;
    /** 滚动条滑块常态色(scrollbar-thumb)。 */
    private static volatile int scrollbarThumbArgb = 0x40FFFFFF;
    /** 滚动条滑块 hover 色(scrollbar-thumb-hover)。 */
    private static volatile int scrollbarThumbHoverArgb = 0x66FFFFFF;
    /** 滚动条滑块拖拽中色(scrollbar-thumb-drag)。 */
    private static volatile int scrollbarThumbDragArgb = 0x80FFFFFF;
    /** 输入条顶部分隔线(divider-input,8% 白)。 */
    private static volatile int dividerInputArgb = 0x14FFFFFF;
    /** 新消息提示文字色(设计稿 §5.1「↓ N 条新消息」,同 text-secondary 灰字)。 */
    private static volatile int newMessageHintArgb = 0xFF9AA0A8;
    /** 输入条底(bg-input,实色,比容器底亮一档)。 */
    private static volatile int inputBackgroundArgb = 0xFF1E232A;
    /** 输入条 focus 描边(border-input-focus,25% 强调蓝)。 */
    private static volatile int inputFocusBorderArgb = 0x406B9BD8;
    /** 输入占位文字(text-input-placeholder)。 */
    private static volatile int inputPlaceholderArgb = 0xFF6E757E;
    /** 文本选中底(selection-text,25% 链接蓝)。 */
    private static volatile int selectionBackgroundArgb = 0x407AB8F5;

    private ChatMarkdownSettings() {
    }

    /** @return 聊天 3.0 接管是否启用 */
    public static boolean isEnabled() {
        return enabled;
    }

    /** 设置聊天 3.0 接管开关(下一渲染帧生效)。 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** @return 聊天气泡字号(px) */
    public static int getChatFontSizePx() {
        return chatFontSizePx;
    }

    /** @return 行距附加(px) */
    public static int getChatLineSpacingPx() {
        return chatLineSpacingPx;
    }

    /** @return 行高(px)= 字号 + 行距 */
    public static int getChatLineHeightPx() {
        return chatFontSizePx + chatLineSpacingPx;
    }

    /** @return 系统消息行高(px,设计稿 §2.2 font-system 12/16)= 系统字号 + 4 */
    public static int getSystemLineHeightPx() {
        return systemFontSizePx + 4;
    }

    /**
     * @param viewportWidth 视口宽(逻辑 px)
     * @return 聊天窗口宽(px),设计稿 §5.5 分段:
     *         视口宽 &lt; 360 → 视口宽 × 0.5(比下限 160 更小,窄屏适配);
     *         [360, 800) → 下限 minChatWidthPx;
     *         ≥ 800 → clamp(视口宽 × 比例, 最小宽 .. 最大宽),新增 360 封顶:
     *         4K 宽屏下 25% 不再无限拉长
     */
    public static int chatWidthFor(int viewportWidth) {
        if (viewportWidth < 360) {
            return Math.max(1, (int) Math.round(viewportWidth * 0.5));
        }
        if (viewportWidth < 800) {
            return minChatWidthPx;
        }
        int ratioWidth = (int) Math.round(viewportWidth * chatWidthRatio);
        return Math.max(minChatWidthPx, Math.min(ratioWidth, chatWidthMaxPx));
    }

    /** @return 聊天窗口距屏幕边缘边距(px) */
    public static int getChatMarginPx() {
        return chatMarginPx;
    }

    /** @return 气泡水平内边距(px) */
    public static int getBubblePaddingX() {
        return bubblePaddingX;
    }

    /** @return 气泡垂直内边距(px) */
    public static int getBubblePaddingY() {
        return bubblePaddingY;
    }

    /** @return 气泡圆角半径(px) */
    public static int getBubbleCornerRadius() {
        return bubbleCornerRadius;
    }

    /** @return 组内消息间距(px) */
    public static int getGroupInnerGapPx() {
        return groupInnerGapPx;
    }

    /** @return HUD 形态组间距(px) */
    public static int getGroupGapHudPx() {
        return groupGapHudPx;
    }

    /** @return 容器形态组间距(px) */
    public static int getGroupGapContainerPx() {
        return groupGapContainerPx;
    }

    /** @return HUD 形态存活窗口(ms) */
    public static long getHudTtlMillis() {
        return hudTtlMillis;
    }

    /** @return HUD 形态淡出时长(ms) */
    public static long getHudFadeMillis() {
        return hudFadeMillis;
    }

    /** @return 聊天窗口最大宽(逻辑 px,360 封顶) */
    public static int getChatWidthMaxPx() {
        return chatWidthMaxPx;
    }

    /** @return 行内 LaTeX 公式行高上限系数(行高×系数 = 缩放阈值;设计稿 §3.5 默认 1.6)。 */
    public static float getLatexMaxLineHeightFactor() {
        return latexMaxLineHeightFactor;
    }

    /** @return 行内 LaTeX 公式缩放系数(超限公式按此系数缩放重排;设计稿 §3.5 默认 0.85)。 */
    public static float getLatexShrinkFactor() {
        return latexShrinkFactor;
    }

    /** @return HUD 堆叠高度上限比例(视口高 × 比例) */
    public static double getHudMaxHeightRatio() {
        return hudMaxHeightRatio;
    }

    /** @return HUD 组出生 enter 动画时长(ms) */
    public static long getEnterAnimMillis() {
        return enterAnimMillis;
    }

    /** @return 滚轮一格滚动行数 */
    public static int getScrollWheelLines() {
        return scrollWheelLines;
    }

    /**
     * @return 平滑滚动时长(ms,clamp 0..500;0 或负 = 瞬移语义,不启动平滑)
     */
    public static long getSmoothScrollMillis() {
        return Math.max(0L, Math.min(500L, smoothScrollMillis));
    }

    /** @return 滚动条自动隐藏静止时长(ms) */
    public static long getScrollbarAutoHideMillis() {
        return scrollbarAutoHideMillis;
    }

    /** @return 自己气泡视觉风格 */
    public static SelfBubbleStyle getSelfBubbleStyle() {
        return selfBubbleStyle;
    }

    /** @return 自己组头是否显示名字 */
    public static boolean isShowSelfName() {
        return showSelfName;
    }

    /** @return 组头名字字号(px) */
    public static int getNameFontSizePx() {
        return nameFontSizePx;
    }

    /** @return 组头时间戳字号(px) */
    public static int getTimestampFontSizePx() {
        return timestampFontSizePx;
    }

    /** @return 系统消息字号(px) */
    public static int getSystemFontSizePx() {
        return systemFontSizePx;
    }

    /** @return 气泡内小圆角(px) */
    public static int getBubbleInnerCornerRadiusPx() {
        return bubbleInnerCornerRadiusPx;
    }

    /** @return 气泡最大宽比例(组内容宽 × 比例) */
    public static double getBubbleMaxWidthRatio() {
        return bubbleMaxWidthRatio;
    }

    /**
     * @param viewportHeight 视口高(逻辑 px)
     * @return 容器高(px)= max(最小高, 视口高 × 比例)
     */
    public static int containerHeightFor(int viewportHeight) {
        return Math.max(minContainerHeightPx, (int) Math.round(viewportHeight * containerHeightRatio));
    }

    /** @return 容器背景(ARGB) */
    public static int getContainerBgArgb() {
        return containerBgArgb;
    }

    /** @return 容器描边(ARGB) */
    public static int getContainerBorderArgb() {
        return containerBorderArgb;
    }

    /** @return 容器圆角半径(px) */
    public static int getContainerCornerRadius() {
        return containerCornerRadius;
    }

    /** @return 自己的消息气泡(ARGB) */
    public static int getBubbleSelfArgb() {
        return bubbleSelfArgb;
    }

    /** @return 他人消息气泡(ARGB) */
    public static int getBubbleOtherArgb() {
        return bubbleOtherArgb;
    }

    /** @return 系统消息文字(ARGB) */
    public static int getSystemTextArgb() {
        return systemTextArgb;
    }

    /** @return 时间戳文字(ARGB) */
    public static int getTimeTextArgb() {
        return timeTextArgb;
    }

    /** @return 自己气泡底·方案B(ARGB,备案未启用) */
    public static int getBubbleSelfAltArgb() {
        return bubbleSelfAltArgb;
    }

    /** @return 自己气泡右侧强调条(ARGB,方案A) */
    public static int getAccentBarSelfArgb() {
        return accentBarSelfArgb;
    }

    /** @return 气泡 hover 叠加层(ARGB,P1 hover 用) */
    public static int getOverlayHoverArgb() {
        return overlayHoverArgb;
    }

    /** @return 正文兜底色(ARGB) */
    public static int getTextPrimaryArgb() {
        return textPrimaryArgb;
    }

    /** @return 组头辅助信息/元信息色(ARGB) */
    public static int getTextSecondaryArgb() {
        return textSecondaryArgb;
    }

    /** @return 自己名字色(ARGB) */
    public static int getTextNameSelfArgb() {
        return textNameSelfArgb;
    }

    /** @return 链接默认色(ARGB) */
    public static int getLinkArgb() {
        return linkArgb;
    }

    /** @return 链接 hover 提亮色(ARGB) */
    public static int getLinkHoverArgb() {
        return linkHoverArgb;
    }

    /** @return 行内 code 衬底(ARGB) */
    public static int getCodeBackgroundArgb() {
        return codeBackgroundArgb;
    }

    /** @return 引用块左侧竖条(ARGB) */
    public static int getQuoteBarArgb() {
        return quoteBarArgb;
    }

    /** @return 滚动条滑块常态色(ARGB) */
    public static int getScrollbarThumbArgb() {
        return scrollbarThumbArgb;
    }

    /** @return 滚动条滑块 hover 色(ARGB) */
    public static int getScrollbarThumbHoverArgb() {
        return scrollbarThumbHoverArgb;
    }

    /** @return 滚动条滑块拖拽中色(ARGB) */
    public static int getScrollbarThumbDragArgb() {
        return scrollbarThumbDragArgb;
    }

    /** @return 输入条顶部分隔线色(ARGB) */
    public static int getDividerInputArgb() {
        return dividerInputArgb;
    }

    /** @return 输入条区高(px) */
    public static int getInputBarHeightPx() {
        return inputBarHeightPx;
    }

    /** @return 输入条圆角半径(px,r-md 8) */
    public static int getInputCornerRadiusPx() {
        return inputCornerRadiusPx;
    }

    /** @return 新消息提示文字色(ARGB) */
    public static int getNewMessageHintArgb() {
        return newMessageHintArgb;
    }

    /** @return 输入条底色(ARGB) */
    public static int getInputBackgroundArgb() {
        return inputBackgroundArgb;
    }

    /** @return 输入条 focus 描边色(ARGB) */
    public static int getInputFocusBorderArgb() {
        return inputFocusBorderArgb;
    }

    /** @return 输入占位文字色(ARGB) */
    public static int getInputPlaceholderArgb() {
        return inputPlaceholderArgb;
    }

    /** @return 文本选中底色(ARGB) */
    public static int getSelectionBackgroundArgb() {
        return selectionBackgroundArgb;
    }

    /** @return 组头字号(px)= max(10, 名字字号);now 12px 半粗(设计 font-name) */
    public static int getChatHeaderFontSizePx() {
        return Math.max(10, nameFontSizePx);
    }

    /** @return 收起动画时长(ms) */
    public static long getCollapseAnimMillis() {
        return collapseAnimMillis;
    }

    /** @return 弹出动画时长(ms) */
    public static long getPopAnimMillis() {
        return popAnimMillis;
    }

    /** @return 容器关闭动画时长(ms) */
    public static long getClosingAnimMillis() {
        return closingAnimMillis;
    }
}
