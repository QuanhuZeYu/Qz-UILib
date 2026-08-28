package club.heiqi.uilib.internal.chat3;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatMarkdownSettings 契约测试(T1):§7 参数表默认值 / chatWidth 三档 clamp / §2.1 色板全量令牌。
 *
 * <p>全部字段为进程级 volatile 无 setter,测试断言默认值不被污染。</p>
 */
public class ChatMarkdownSettingsTest {

    @Test
    public void defaultsMatchDesignParameterTable() {
        // 存活/淡出:TTL 10000→12000、fade 500→800;TB1 消息常驻默认开启(false 还原旧 TTL)
        Assert.assertEquals(12000L, ChatMarkdownSettings.getHudTtlMillis());
        Assert.assertEquals(800L, ChatMarkdownSettings.getHudFadeMillis());
        Assert.assertTrue(ChatMarkdownSettings.isHudPersistMessages());
        // 内边距:气泡纵向 6→5,横向不变
        Assert.assertEquals(5, ChatMarkdownSettings.getBubblePaddingY());
        Assert.assertEquals(10, ChatMarkdownSettings.getBubblePaddingX());
        // 宽度:新增 360 封顶
        Assert.assertEquals(360, ChatMarkdownSettings.getChatWidthMaxPx());
        // 新增参数(P1 用,本轮落常量)
        Assert.assertEquals(0.5, ChatMarkdownSettings.getHudMaxHeightRatio(), 0.0001);
        // 滚轮默认 7(设计稿 §10.1 拍板改回原版 ×7;Shift 一格 1 行)
        Assert.assertEquals(7, ChatMarkdownSettings.getScrollWheelLines());
        Assert.assertEquals(1200L, ChatMarkdownSettings.getScrollbarAutoHideMillis());
        Assert.assertEquals(ChatMarkdownSettings.SelfBubbleStyle.ACCENT, ChatMarkdownSettings.getSelfBubbleStyle());
        Assert.assertFalse(ChatMarkdownSettings.isShowSelfName());
        Assert.assertEquals(180L, ChatMarkdownSettings.getEnterAnimMillis());
        // 动画时长对齐 §7:pop 240 / collapse 160(原 250/150)
        Assert.assertEquals(240L, ChatMarkdownSettings.getPopAnimMillis());
        Assert.assertEquals(160L, ChatMarkdownSettings.getCollapseAnimMillis());
        // 字号体系:组头名字 12(原推导 11)
        Assert.assertEquals(12, ChatMarkdownSettings.getNameFontSizePx());
        Assert.assertEquals(12, ChatMarkdownSettings.getChatHeaderFontSizePx());
        Assert.assertEquals(10, ChatMarkdownSettings.getTimestampFontSizePx());
        Assert.assertEquals(12, ChatMarkdownSettings.getSystemFontSizePx());
        // 保留不变项(§7 同值)
        Assert.assertEquals(10, ChatMarkdownSettings.getChatMarginPx());
        Assert.assertEquals(13, ChatMarkdownSettings.getChatFontSizePx());
        Assert.assertEquals(5, ChatMarkdownSettings.getChatLineSpacingPx());
        Assert.assertEquals(18, ChatMarkdownSettings.getChatLineHeightPx());
        Assert.assertEquals(12, ChatMarkdownSettings.getBubbleCornerRadius());
        Assert.assertEquals(2, ChatMarkdownSettings.getGroupInnerGapPx());
        Assert.assertEquals(4, ChatMarkdownSettings.getGroupGapHudPx());
        Assert.assertEquals(8, ChatMarkdownSettings.getGroupGapContainerPx());
        Assert.assertEquals(4, ChatMarkdownSettings.getBubbleInnerCornerRadiusPx());
        Assert.assertEquals(0.85, ChatMarkdownSettings.getBubbleMaxWidthRatio(), 0.0001);
        Assert.assertEquals(12, ChatMarkdownSettings.getContainerCornerRadius());
        Assert.assertEquals(450, ChatMarkdownSettings.containerHeightFor(900));
        Assert.assertEquals(160, ChatMarkdownSettings.containerHeightFor(100));
    }

    /** TB1:常驻开关 setter 往返(进程级配置切换后恢复,与 enabled 同款 setter 语义)。 */
    @Test
    public void hudPersistMessagesSetterRoundTrips() {
        boolean previous = ChatMarkdownSettings.isHudPersistMessages();
        try {
            ChatMarkdownSettings.setHudPersistMessages(false);
            Assert.assertFalse("关闭常驻 = 还原旧 TTL 行为", ChatMarkdownSettings.isHudPersistMessages());
            ChatMarkdownSettings.setHudPersistMessages(true);
            Assert.assertTrue("重新开启常驻", ChatMarkdownSettings.isHudPersistMessages());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(previous);
        }
    }

    /**
     * §5.5 三档分段:<360 → 视口宽×0.5(比下限更小);[360,800) → 下限 160;
     * ≥800 → clamp(等比, 160..360)。
     */
    @Test
    public void chatWidthFollowsSection55Segments() {
        // 极窄视口(<360):chatWidth = 视口宽 × 0.5(设计稿 §5.5 窄屏适配,比下限 160 更小)
        Assert.assertEquals(50, ChatMarkdownSettings.chatWidthFor(100));
        Assert.assertEquals(100, ChatMarkdownSettings.chatWidthFor(200));
        // 边界:359 → round(179.5) = 180
        Assert.assertEquals(180, ChatMarkdownSettings.chatWidthFor(359));
        // [360,800):固定下限 160(不吃等比,如 661×0.25=165 仍取 160)
        Assert.assertEquals(160, ChatMarkdownSettings.chatWidthFor(360));
        Assert.assertEquals(160, ChatMarkdownSettings.chatWidthFor(640));
        Assert.assertEquals(160, ChatMarkdownSettings.chatWidthFor(661));
        Assert.assertEquals(160, ChatMarkdownSettings.chatWidthFor(799));
        // ≥800:clamp(等比 1/4, 160..360);800×0.25 = 200
        Assert.assertEquals(200, ChatMarkdownSettings.chatWidthFor(800));
        // 新增封顶:1600/3840 均封 360(历史 4K 无上限回归点)
        Assert.assertEquals(360, ChatMarkdownSettings.chatWidthFor(1600));
        Assert.assertEquals(360, ChatMarkdownSettings.chatWidthFor(3840));
    }

    @Test
    public void paletteMatchesDesignTokens() {
        // 容器
        Assert.assertEquals(0xF2171B20, ChatMarkdownSettings.getContainerBgArgb());
        Assert.assertEquals(0x1AFFFFFF, ChatMarkdownSettings.getContainerBorderArgb());
        // 气泡(三级明度阶梯:容器 < 他人 < 自己)
        Assert.assertEquals(0xF2242B33, ChatMarkdownSettings.getBubbleOtherArgb());
        Assert.assertEquals(0xF2272F3A, ChatMarkdownSettings.getBubbleSelfArgb());
        Assert.assertEquals(0xF2445C78, ChatMarkdownSettings.getBubbleSelfAltArgb());
        Assert.assertEquals(0xFF6B9BD8, ChatMarkdownSettings.getAccentBarSelfArgb());
        Assert.assertEquals(0x08FFFFFF, ChatMarkdownSettings.getOverlayHoverArgb());
        // 文字三级灰阶
        Assert.assertEquals(0xFFE6E8EB, ChatMarkdownSettings.getTextPrimaryArgb());
        Assert.assertEquals(0xFF9AA0A8, ChatMarkdownSettings.getTextSecondaryArgb());
        Assert.assertEquals(0xFF8B929A, ChatMarkdownSettings.getTimeTextArgb());
        Assert.assertEquals(0xFFB8BDC4, ChatMarkdownSettings.getSystemTextArgb());
        Assert.assertEquals(0xFFAAB3BC, ChatMarkdownSettings.getTextNameSelfArgb());
        // 链接
        Assert.assertEquals(0xFF7AB8F5, ChatMarkdownSettings.getLinkArgb());
        Assert.assertEquals(0xFF9CCBF8, ChatMarkdownSettings.getLinkHoverArgb());
        // 行内装饰
        Assert.assertEquals(0x26FFFFFF, ChatMarkdownSettings.getCodeBackgroundArgb());
        Assert.assertEquals(0x40FFFFFF, ChatMarkdownSettings.getQuoteBarArgb());
        // 滚动条三态
        Assert.assertEquals(0x40FFFFFF, ChatMarkdownSettings.getScrollbarThumbArgb());
        Assert.assertEquals(0x66FFFFFF, ChatMarkdownSettings.getScrollbarThumbHoverArgb());
        Assert.assertEquals(0x80FFFFFF, ChatMarkdownSettings.getScrollbarThumbDragArgb());
        // 输入条
        Assert.assertEquals(0x14FFFFFF, ChatMarkdownSettings.getDividerInputArgb());
        Assert.assertEquals(0xFF1E232A, ChatMarkdownSettings.getInputBackgroundArgb());
        Assert.assertEquals(0x406B9BD8, ChatMarkdownSettings.getInputFocusBorderArgb());
        Assert.assertEquals(0xFF6E757E, ChatMarkdownSettings.getInputPlaceholderArgb());
        Assert.assertEquals(0x407AB8F5, ChatMarkdownSettings.getSelectionBackgroundArgb());
    }
}
