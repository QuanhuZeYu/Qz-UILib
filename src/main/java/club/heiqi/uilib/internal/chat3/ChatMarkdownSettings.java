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

    // ==================== Telegram 观感参数(设计 §6,进程级,S6 真机校准) ====================

    /** 聊天气泡字号(px)。 */
    private static volatile int chatFontSizePx = 13;
    /** 行距附加(px;行高 = 字号 + 行距)。 */
    private static volatile int chatLineSpacingPx = 5;
    /** 聊天窗口宽 = 视口宽 × 比例(用户定:约 1/8,随窗口缩放动态)。 */
    private static volatile double chatWidthRatio = 0.125;
    /** 聊天窗口最小宽(px)。 */
    private static volatile int minChatWidthPx = 200;
    /** 聊天窗口距屏幕边缘边距(px)。 */
    private static volatile int chatMarginPx = 10;
    /** 气泡水平内边距(px)。 */
    private static volatile int bubblePaddingX = 10;
    /** 气泡垂直内边距(px)。 */
    private static volatile int bubblePaddingY = 6;
    /** 气泡圆角半径(px)。 */
    private static volatile int bubbleCornerRadius = 12;
    /** 组内消息间距(px,紧密堆叠)。 */
    private static volatile int groupInnerGapPx = 2;
    /** HUD 形态组间距(px,紧密堆叠)。 */
    private static volatile int groupGapHudPx = 4;
    /** 容器形态组间距(px)。 */
    private static volatile int groupGapContainerPx = 8;
    /** HUD 形态存活窗口(ms,自组内最新消息起)。 */
    private static volatile long hudTtlMillis = 10000L;
    /** HUD 形态淡出时长(ms)。 */
    private static volatile long hudFadeMillis = 500L;
    /** 容器高 = 视口高 × 比例(用户定:约 1/2,随窗口缩放动态)。 */
    private static volatile double containerHeightRatio = 0.5;
    /** 容器最小高(px)。 */
    private static volatile int minContainerHeightPx = 160;
    /** 容器背景(ARGB)。 */
    private static volatile int containerBgArgb = 0xD91B1B1F;
    /** 容器描边(ARGB)。 */
    private static volatile int containerBorderArgb = 0x12FFFFFF;
    /** 容器圆角半径(px)。 */
    private static volatile int containerCornerRadius = 12;
    /** 自己的消息气泡(ARGB,主题蓝)。 */
    private static volatile int bubbleSelfArgb = 0xE63390EC;
    /** 他人消息气泡(ARGB,深灰)。 */
    private static volatile int bubbleOtherArgb = 0xE61C2733;
    /** 系统消息文字(ARGB,居中灰白)。 */
    private static volatile int systemTextArgb = 0x8AFFFFFF;
    /** 时间戳文字(ARGB,灰白小字)。 */
    private static volatile int timeTextArgb = 0x8AFFFFFF;
    /** 收起动画时长(ms,HUD 气泡收起)。 */
    private static volatile long collapseAnimMillis = 150L;
    /** 弹出动画时长(ms,容器弹出)。 */
    private static volatile long popAnimMillis = 250L;

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

    /**
     * @param viewportWidth 视口宽(逻辑 px)
     * @return 聊天窗口宽(px)= max(最小宽, 视口宽 × 比例)
     */
    public static int chatWidthFor(int viewportWidth) {
        return Math.max(minChatWidthPx, (int) Math.round(viewportWidth * chatWidthRatio));
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

    /** @return 组头字号(px)= max(10, 气泡字号 - 2) */
    public static int getChatHeaderFontSizePx() {
        return Math.max(10, chatFontSizePx - 2);
    }

    /** @return 收起动画时长(ms) */
    public static long getCollapseAnimMillis() {
        return collapseAnimMillis;
    }

    /** @return 弹出动画时长(ms) */
    public static long getPopAnimMillis() {
        return popAnimMillis;
    }
}
