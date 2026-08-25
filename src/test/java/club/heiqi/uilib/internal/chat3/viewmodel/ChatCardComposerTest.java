package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * ChatCardComposer 契约测试:组头文本/名字配色/去前缀切分/存活与淡出 alpha 各区间。
 */
public class ChatCardComposerTest {

    private static final long NOW = 1_700_000_000_000L;

    private final ChatCardComposer composer = new ChatCardComposer(new ChatLineLayouter(fixedMeasure(), 13));

    @Test
    public void shouldComposeHeaderAndStrippedLines() {
        long arrived = NOW - 5000L; // 存活窗口内
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> hello world"), 1, arrived);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

        Assert.assertEquals(MessageGroupModel.Alignment.OTHER_LEFT, composed.getAlignment());
        Assert.assertEquals("Steve", composed.getSender());
        Assert.assertEquals("Steve", composed.getHeaderName());
        Assert.assertEquals(ChatClock.formatTime(arrived), composed.getHeaderTime());
        Assert.assertEquals("Steve " + ChatClock.formatTime(arrived), composed.getHeaderText());
        Assert.assertEquals(SenderColorPalette.colorFor("Steve"), composed.getNameColor());
        Assert.assertEquals(1, composed.getMessages().size());
        // ChatComponentText.getFormattedText 尾部带 §r 重置码(真实口径,渲染时被解析为样式重置)
        Assert.assertEquals(Arrays.asList("hello world\u00a7r"), composed.getMessages().get(0).getDisplayLines());
        Assert.assertTrue(composed.isVisible());
    }

    @Test
    public void shouldComposeSystemGroupWithoutHeader() {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("[公告] 维护通知"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

        Assert.assertEquals(MessageGroupModel.Alignment.SYSTEM_CENTER, composed.getAlignment());
        Assert.assertEquals("", composed.getHeaderName());
        Assert.assertEquals("", composed.getHeaderTime());
        Assert.assertEquals("", composed.getHeaderText());
        Assert.assertEquals(0xFFFFFFFF, composed.getNameColor());
        Assert.assertEquals(Arrays.asList("[公告] 维护通知\u00a7r"), composed.getMessages().get(0).getDisplayLines());
    }

    @Test
    public void shouldComposeSelfGroupWithGrayName() {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> me"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Steve").get(0);

        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

        Assert.assertEquals(MessageGroupModel.Alignment.SELF_RIGHT, composed.getAlignment());
        Assert.assertEquals(SenderColorPalette.SELF_NAME_ARGB, composed.getNameColor());
        // 自己的消息:名字用灰色(与蓝色气泡区分);showSelfName 默认 false → 组头仅时间戳
        Assert.assertEquals("", composed.getHeaderName());
        Assert.assertEquals(ChatClock.formatTime(NOW - 5000L), composed.getHeaderTime());
        Assert.assertEquals(ChatClock.formatTime(NOW - 5000L), composed.getHeaderText());
    }

    @Test
    public void selfGroupHeaderShowsNameWhenShowSelfNameEnabled() throws Exception {
        // 临时注入 showSelfName=true(静态配置,反射改 + try/finally 恢复默认),断言名字段出现
        java.lang.reflect.Field field = ChatMarkdownSettings.class.getDeclaredField("showSelfName");
        field.setAccessible(true);
        boolean previous = field.getBoolean(null);
        try {
            field.setBoolean(null, true);
            long arrived = NOW - 5000L;
            ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> me"), 1, arrived);
            MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Steve").get(0);
            ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

            Assert.assertEquals("Steve", composed.getHeaderName());
            Assert.assertEquals("Steve " + ChatClock.formatTime(arrived), composed.getHeaderText());
        } finally {
            field.setBoolean(null, previous);
        }
    }

    @Test
    public void shouldKeepFullAlphaWhenTtlDisabled() {
        long arrived = NOW - 60_000L; // 早已过期
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> old"), 1, arrived);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, false);

        Assert.assertEquals("容器形态 alpha 恒满", 255, composed.getAlpha());
        Assert.assertTrue(composed.isVisible());
    }

    @Test
    public void fadeColorCombinesBaseAlpha() {
        Assert.assertEquals(0xE61C2733, ChatCardComposer.fadeColor(0xE61C2733, 255));
        Assert.assertEquals(0x731C2733, ChatCardComposer.fadeColor(0xE61C2733, 128));
        Assert.assertEquals(0x001C2733, ChatCardComposer.fadeColor(0xE61C2733, 0));
    }

    @Test
    public void fadeAlphaCoversTtlFadeAndExpiry() {
        long ttl = 10_000L;
        long fade = 500L;
        Assert.assertEquals(255, ChatCardComposer.fadeAlpha(NOW - 5_000L, NOW, ttl, fade, 255));
        Assert.assertEquals(255, ChatCardComposer.fadeAlpha(NOW - 9_999L, NOW, ttl, fade, 255));
        // 淡出中点:easeInQuad p=0.5 → floor(255×(1-0.25)) = 191
        Assert.assertEquals(191, ChatCardComposer.fadeAlpha(NOW - 10_250L, NOW, ttl, fade, 255));
        // 淡出结束即归零
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 10_500L, NOW, ttl, fade, 255));
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 20_000L, NOW, ttl, fade, 255));
    }

    @Test
    public void fadeAlphaFollowsEaseInQuadLadder() {
        // 设计稿 §5.3 淡出 alpha 阶梯:floor(255×(1-p²)) 整数截断;TTL 窗内恒 255
        long ttl = 10_000L;
        long fade = 1_000L;
        Assert.assertEquals("p=0(刚过期)恒满", 255, ChatCardComposer.fadeAlpha(NOW - ttl, NOW, ttl, fade, 255));
        Assert.assertEquals("p=0.25 → 239", 239, ChatCardComposer.fadeAlpha(NOW - (ttl + 250L), NOW, ttl, fade, 255));
        Assert.assertEquals("p=0.5 → 191", 191, ChatCardComposer.fadeAlpha(NOW - (ttl + 500L), NOW, ttl, fade, 255));
        // §5.3 表 p=0.75 写 112,但 §4.3 floor 语义为 floor(111.5625)=111——按 floor 实现
        Assert.assertEquals("p=0.75 → 111(floor(111.5625))", 111, ChatCardComposer.fadeAlpha(NOW - (ttl + 750L), NOW, ttl, fade, 255));
        Assert.assertEquals("p=0.9 → 48", 48, ChatCardComposer.fadeAlpha(NOW - (ttl + 900L), NOW, ttl, fade, 255));
        Assert.assertEquals("p=1 → 0", 0, ChatCardComposer.fadeAlpha(NOW - (ttl + 1_000L), NOW, ttl, fade, 255));
        // 慢启动:前 25% 只降 16/255,后 25% 降 111/255——easeInQuad 阶梯形态
        Assert.assertTrue("前半段降幅 < 后半段(慢启动)",
                ChatCardComposer.fadeAlpha(NOW - (ttl + 250L), NOW, ttl, fade, 255)
                        - ChatCardComposer.fadeAlpha(NOW - ttl, NOW, ttl, fade, 255)
                        > ChatCardComposer.fadeAlpha(NOW - (ttl + 1_000L), NOW, ttl, fade, 255)
                        - ChatCardComposer.fadeAlpha(NOW - (ttl + 750L), NOW, ttl, fade, 255));
    }

    @Test
    public void fadeAlphaHandlesZeroFadeWindow() {
        Assert.assertEquals(255, ChatCardComposer.fadeAlpha(NOW - 5_000L, NOW, 10_000L, 0L, 255));
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 10_001L, NOW, 10_000L, 0L, 255));
    }

    // ==================== T6a:气泡 hover 3% 白叠加(设计稿 §2.1 overlay-hover) ====================

    @Test
    public void mixWithWhiteKeepsBaseColorAtZeroBlend() {
        Assert.assertEquals("t=0 恒等", 0xF2242B33, ChatCardComposer.mixWithWhite(0xF2242B33, 0.0F));
        Assert.assertEquals("alpha 通道保持", 0xF2, (ChatCardComposer.mixWithWhite(0xF2242B33, 0.03F) >>> 24) & 0xFF);
    }

    @Test
    public void mixWithWhiteBlendsThreePercentWhite() {
        // 他人气泡 0xF2242B33 + 3% 白:R 36→43(0x2B) G 43→49(0x31) B 51→57(0x39),alpha F2 不变
        Assert.assertEquals(0xF22B3139, ChatCardComposer.mixWithWhite(0xF2242B33, 0.03F));
        // 自己气泡 0xF2272F3A + 3% 白:R 39→45(0x2D) G 47→53(0x35) B 58→64(0x40)
        Assert.assertEquals(0xF22D3540, ChatCardComposer.mixWithWhite(0xF2272F3A, 0.03F));
    }

    @Test
    public void mixWithWhiteClampsBlendAndSaturates() {
        Assert.assertEquals("t 越界夹取", 0xF2242B33, ChatCardComposer.mixWithWhite(0xF2242B33, -1.0F));
        Assert.assertEquals("t=1 全白(RGB 255)", 0xFFFFFFFF,
                ChatCardComposer.mixWithWhite(0xFF123456, 1.0F));
        Assert.assertEquals("t 超 1 夹取后同样饱和为白", 0xFFFFFFFF,
                ChatCardComposer.mixWithWhite(0xFF000000, 2.0F));
    }

    @Test
    public void hoveredBubbleColorPrecomputesThreePercentWhite() {
        Assert.assertEquals("他人气泡 hover 底色", 0xF22B3139,
                ChatCardComposer.hoveredBubbleColor(ChatMarkdownSettings.getBubbleOtherArgb()));
        Assert.assertEquals("自己气泡 hover 底色", 0xF22D3540,
                ChatCardComposer.hoveredBubbleColor(ChatMarkdownSettings.getBubbleSelfArgb()));
    }

    // ==================== P2-4:hover 颜色插值纯函数 ====================

    @Test
    public void interpolateArgbInterpolatesChannelsWithEndpoints() {
        Assert.assertEquals("t≤0 恒 from", 0xF2242B33,
                ChatCardComposer.interpolateArgb(0xF2242B33, 0xF22B3139, -0.5F));
        Assert.assertEquals("t≥1 恒 to", 0xF22B3139,
                ChatCardComposer.interpolateArgb(0xF2242B33, 0xF22B3139, 1.5F));
        // 链接色 0xFF7AB8F5 → hover 0xFF9CCBF8 @ t=0.5:
        // R 122→156=139(0x8B),G 184→203=194(0xC2),B 245→248=247(0xF7)
        Assert.assertEquals(0xFF8BC2F7,
                ChatCardComposer.interpolateArgb(0xFF7AB8F5, 0xFF9CCBF8, 0.5F));
    }

    @Test
    public void interpolateSegmentsLerpsOnlyLinkSegments() {
        TextStyle plain = new TextStyle();
        plain.setColor(0xFFFFFFFF);
        TextStyle linkBase = plain.copy();
        linkBase.setColor(0xFF7AB8F5);
        linkBase.setLink("http://a.co");
        TextStyle linkHover = plain.copy();
        linkHover.setColor(0xFF9CCBF8);
        linkHover.setLink("http://a.co");
        linkHover.setUnderline(true);
        List<TextSegment> base = Arrays.asList(
                new TextSegment("a ", plain), new TextSegment("http://a.co", linkBase));
        List<TextSegment> hover = Arrays.asList(
                new TextSegment("a ", plain), new TextSegment("http://a.co", linkHover));
        List<TextSegment> mid = ChatCardComposer.interpolateSegments(base, hover, 0.5F);
        Assert.assertEquals("中间态段数同构", 2, mid.size());
        Assert.assertSame("非 link 段原引用透传", hover.get(0), mid.get(0));
        Assert.assertEquals("link 段中间色 = 0.5 通道插值", 0xFF8BC2F7,
                mid.get(1).getStyle().getColor());
        Assert.assertTrue("下划线随目标态", mid.get(1).getStyle().isUnderline());
        Assert.assertEquals("link 字段保留", "http://a.co", mid.get(1).getStyle().getLink());
        // 端态零分配复用
        Assert.assertSame("t=0 复用 base", base,
                ChatCardComposer.interpolateSegments(base, hover, 0.0F));
        Assert.assertSame("t=1 复用 hover", hover,
                ChatCardComposer.interpolateSegments(base, hover, 1.0F));
    }

    // ==================== T8:单条消息 8 行截断 + 省略号(设计稿 §5.4,验收 22) ====================

    /** 组装 45 字符无空格文本(约定 maxLine=20:5 字符/行 → 9 行)。 */
    private static String longText() {
        StringBuilder sb = new StringBuilder(45);
        for (int i = 0; i < 45; i++) {
            sb.append('x');
        }
        return sb.toString();
    }

    private ChatCardComposer.ComposedGroup composeText(String text, boolean applyTtl) {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> " + text), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        return composer.compose(group, NOW, 20, applyTtl);
    }

    @Test
    public void hudClampsLongMessageToEightLinesWithEllipsis() {
        ChatCardComposer.ComposedGroup composed = composeText(longText(), true);

        ChatCardComposer.MessageLines message = composed.getMessages().get(0);
        List<String> lines = message.getDisplayLines();
        Assert.assertEquals("HUD 单条消息 9 行截断到 8 行", 8, lines.size());
        // 行1-7 保持切分原样(每行 5 字符)
        Assert.assertEquals("xxxxx", lines.get(0));
        // 第 8 行 = 裁剪(5 字符宽 20 > 可用 20-12=8 → 保留 2 字符)+ 省略号,宽度不超过行宽上限
        Assert.assertEquals("xx...", lines.get(7));
        Assert.assertTrue("省略号末行宽度不超行宽上限",
                4 * lines.get(7).length() <= 20);
    }

    @Test
    public void hudKeepsEightLinesWithoutEllipsisWhenExactlyEight() {
        // 40 字符 = 恰好 8 行(每行 5 字符):行数不超上限,不加省略号(CSS line-clamp 语义)
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append('y');
        }
        ChatCardComposer.ComposedGroup composed = composeText(sb.toString(), true);

        List<String> lines = composed.getMessages().get(0).getDisplayLines();
        Assert.assertEquals(8, lines.size());
        Assert.assertEquals("yyyyy\u00a7r", lines.get(7)); // ChatComponentText 尾部 §r 重置码(真实口径)
    }

    @Test
    public void containerKeepsAllLinesUnclamped() {
        ChatCardComposer.ComposedGroup composed = composeText(longText(), false);

        List<String> lines = composed.getMessages().get(0).getDisplayLines();
        Assert.assertEquals("容器形态同一消息完整显示(验收 22)", 9, lines.size());
        Assert.assertEquals("yyyyy".replace('y', 'x'), lines.get(7));
        Assert.assertFalse("容器形态末行无省略号", lines.get(8).endsWith(ChatCardComposer.ELLIPSIS));
    }

    @Test
    public void ellipsisLineKeepsFormatCodePairsIntact() {
        // 7 个 \n 硬断出行 1-7(每行 5 字符),§b(零宽)落在第 8 行首:
        // 行 8 = §b + 5 字符宽 20 > 可用 8 → 裁剪保留 §b + 2 字符 + 省略号
        StringBuilder sb = new StringBuilder(47);
        for (int i = 0; i < 7; i++) {
            sb.append("xxxxx\n");
        }
        sb.append("\u00a7b");
        for (int i = 0; i < 13; i++) {
            sb.append('x');
        }
        ChatCardComposer.ComposedGroup composed = composeText(sb.toString(), true);

        List<String> lines = composed.getMessages().get(0).getDisplayLines();
        Assert.assertEquals(8, lines.size());
        Assert.assertEquals("格式码对不可拆且保留在裁剪行首", "\u00a7bxx...", lines.get(7));
    }

    private static ChatLineLayouter.Measure fixedMeasure() {
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                int effective = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                        i++;
                        continue;
                    }
                    effective++;
                }
                return effective * 4;
            }

            @Override
            public int epoch() {
                return 0;
            }
        };
    }
}
