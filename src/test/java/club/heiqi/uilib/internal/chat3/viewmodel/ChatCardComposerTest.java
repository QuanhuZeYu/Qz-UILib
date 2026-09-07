package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

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
        // C7 收口：正则兜底的玩家行本体改取 getPlainText() 的 rest，不再从
        // ChatComponentText.getFormattedText()（尾部自带 §r 重置码）上切片 ⇒ 气泡文本零 §。
        Assert.assertEquals(Arrays.asList("hello world"), composed.getMessages().get(0).getDisplayLines());
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

    // ==================== TB1:HUD 常驻消息(默认开启,TTL 淡出关闭) ====================

    /** 常驻模式(hudPersistMessages=true):HUD 形态(applyTtl=true)下消息早已越过 TTL 仍 alpha 恒满。 */
    @Test
    public void persistModeKeepsFullAlphaEvenWhenTtlElapsed() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> old"), 1, NOW - 60_000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

        Assert.assertEquals("常驻模式 TTL 不生效:alpha 恒满", 255, composed.getAlpha());
        Assert.assertTrue(composed.isVisible());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** persist=false 还原旧行为:HUD 形态越过 TTL 淡出结束 alpha 归零(与 shouldComposeHeaderAndStrippedLines 对照)。 */
    @Test
    public void ttlFadeAppliesWhenPersistDisabled() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> old"), 1, NOW - 60_000L);
            MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

            ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true);

            Assert.assertEquals("persist=false:过 TTL 淡出结束 alpha=0", 0, composed.getAlpha());
            Assert.assertFalse("persist=false:过期组不可见", composed.isVisible());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
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

    // ==================== TB2:预算驱动 HUD 显示时长(hudAlpha 纯函数 / compose 预算注入) ====================

    /** 未进入 HUD(start < 0 = 消息尚未以 HUD 形态渲染):恒满,与可见时钟无关。 */
    @Test
    public void hudAlphaStaysFullWhenNeverEnteredHud() {
        Assert.assertEquals("未进入恒满", 255, ChatCardComposer.hudAlpha(-1L, 12_000L, 800L, 5_000L));
        Assert.assertEquals("任意可见时钟均恒满", 255, ChatCardComposer.hudAlpha(-1L, 12_000L, 800L, 99_999L));
        Assert.assertEquals("预算 0 也恒满(渲染前不消耗)", 255, ChatCardComposer.hudAlpha(-1L, 0L, 800L, 99_999L));
    }

    /** 预算内恒满;恰好耗尽(remaining=0)时 easeInQuad p=0 仍恒满;预算内 fade<=0 也恒满(预算优先)。 */
    @Test
    public void hudAlphaStaysFullInsideBudgetAndAtExhaustion() {
        long start = 1_000L;
        long budget = 12_000L;
        Assert.assertEquals("预算内恒满", 255, ChatCardComposer.hudAlpha(start, budget, 800L, 5_000L));
        Assert.assertEquals("恰好耗尽(remaining=0)→ p=0 恒满", 255,
                ChatCardComposer.hudAlpha(start, budget, 800L, start + budget));
        Assert.assertEquals("预算内 fade<=0 恒满(预算优先)", 255,
                ChatCardComposer.hudAlpha(start, budget, 0L, 5_000L));
    }

    /** 预算耗尽后按 easeInQuad 淡出:1/4、1/2、3/4 窗严格递减,端点归零(与 fadeAlpha 同款整数 floor)。 */
    @Test
    public void hudAlphaFadesWithEaseInQuadLadder() {
        long start = 1_000L;
        long budget = 12_000L;
        long fade = 1_000L;
        int q1 = ChatCardComposer.hudAlpha(start, budget, fade, start + budget + 250L);
        int q2 = ChatCardComposer.hudAlpha(start, budget, fade, start + budget + 500L);
        int q3 = ChatCardComposer.hudAlpha(start, budget, fade, start + budget + 750L);
        Assert.assertEquals("p=0.25 → 239", 239, q1);
        Assert.assertEquals("p=0.5 → 191", 191, q2);
        Assert.assertEquals("p=0.75 → 111(floor(111.5625))", 111, q3);
        Assert.assertTrue("1/4 > 1/2 > 3/4 严格递减(慢启动)", q1 > q2 && q2 > q3);
        Assert.assertEquals("淡出窗结束归零", 0,
                ChatCardComposer.hudAlpha(start, budget, fade, start + budget + fade));
        Assert.assertEquals("超淡出窗归零", 0,
                ChatCardComposer.hudAlpha(start, budget, fade, start + budget + fade + 1L));
    }

    /** fade <= 0:预算耗尽即消失(与 fadeAlpha 同语义)。 */
    @Test
    public void hudAlphaDropsToZeroWhenExhaustedAndFadeDisabled() {
        long start = 1_000L;
        long budget = 12_000L;
        Assert.assertEquals("fade=0 已耗尽 → 0", 0, ChatCardComposer.hudAlpha(start, budget, 0L, start + budget + 1L));
        Assert.assertEquals("fade<0 已耗尽 → 0", 0, ChatCardComposer.hudAlpha(start, budget, -1L, start + budget + 1L));
    }

    /** compose 6 参重载:HudBudget 注入 → alpha 恒满(淡出交给渲染层 hudAlpha)且组携带预算/start。 */
    @Test
    public void composeWithHudBudgetCarriesLifecycleFields() {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> budgeted"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.HudBudget budget = new ChatCardComposer.HudBudget(12_000L, 42L);
        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, true, budget);

        Assert.assertEquals("预算注入后 alpha 恒满(合成时刻无 DONE 组)", 255, composed.getAlpha());
        Assert.assertEquals("组携带显示预算", 12_000L, composed.getBudgetMillis());
        Assert.assertEquals("组携带首次进入 HUD 的可见时钟", 42L, composed.getHudVisibleStartMillis());
        Assert.assertTrue("首次 HUD 合成默认播放入场动画", composed.isEnterOnMount());
        Assert.assertTrue(composed.isVisible());
    }

    /** compose 6 参重载:budget=null 与旧 5 参行为一致;容器形态(applyTtl=false)不注入预算。 */
    @Test
    public void composeWithoutHudBudgetKeepsLegacyBehavior() {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentText("<Steve> legacy"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);

        ChatCardComposer.ComposedGroup withNull = composer.compose(group, NOW, 1000, true, null);
        ChatCardComposer.ComposedGroup legacy = composer.compose(group, NOW, 1000, true);

        Assert.assertEquals("budget=null → 旧 5 参路径(alpha 一致)", legacy.getAlpha(), withNull.getAlpha());
        Assert.assertEquals("未注入预算默认 0", 0L, withNull.getBudgetMillis());
        Assert.assertEquals("未注入 start 默认 -1(未进入 HUD)", -1L, withNull.getHudVisibleStartMillis());
        Assert.assertTrue("默认入场动画开", withNull.isEnterOnMount());

        ChatCardComposer.ComposedGroup container = composer.compose(group, NOW, 1000, false,
                new ChatCardComposer.HudBudget(12_000L, 7L));
        Assert.assertEquals("容器形态 alpha 恒满", 255, container.getAlpha());
        Assert.assertEquals("容器形态不注入预算", 0L, container.getBudgetMillis());
        Assert.assertEquals("容器形态不注入 start", -1L, container.getHudVisibleStartMillis());
        Assert.assertTrue("容器形态入场动画默认开", container.isEnterOnMount());
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
        // C7 收口后本体无 §r（旧期望 "yyyyy\u00a7r" 的来源正是 formatted 文本尾部重置码）
        Assert.assertEquals("yyyyy", lines.get(7));
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

// ==================== C7：气泡装配的三条内容分支 ====================

    /**
     * 结构命中分支：气泡 displayText 与切行输入都必须是 {@code getFormatArgs()[1]} 的原文，
     * 不再经「formatted 文本 + § 跳跃切片」（旧 § 残渣唯一来源）。
     */
    @Test
    public void structuredLineUsesRawContentVerbatim() {
        String section = String.valueOf((char) 0x00A7);
        ChatLineRecord record = new ChatLineRecord(new ChatComponentTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Steve"), "- item"}), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, false);

        ChatCardComposer.MessageLines message = composed.getMessages().get(0);
        Assert.assertEquals("- item", message.getDisplayText());
        Assert.assertEquals("切行输入与 markdown 输入同源同值", "- item",
                message.getDisplayLines().get(0));
        Assert.assertFalse("结构通道 displayText 不得带任何 §: " + message.getDisplayText(),
                message.getDisplayText().contains(section));
        // 对照：同一形态若走旧口径（formatted 切片）必然带 §r——证明本锁不是空断言。
        Assert.assertTrue("正例对照（formatted 文本自带 §，旧口径的残渣来源）",
                record.getFormattedText().contains(section));
    }

    /** 正则兜底分支：本体 = plain 的 rest（unformatted 源），旧 §r 尾注随 formatted 切片一并消失。 */
    @Test
    public void fallbackLineUsesPlainRestWithoutFormattedCodes() {
        String section = String.valueOf((char) 0x00A7);
        ChatLineRecord record = new ChatLineRecord(
                new ChatComponentText("<Steve> 兜底内容"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        ChatCardComposer.MessageLines message = composer.compose(group, NOW, 1000, false)
                .getMessages().get(0);

        Assert.assertEquals("兜底本体 = 正则 rest", "兜底内容", message.getDisplayText());
        Assert.assertFalse("不再从 formatted 文本上切片 ⇒ 无 §r 尾注: " + message.getDisplayText(),
                message.getDisplayText().contains(section));
    }

    /** 系统/广播分支：整条 formatted 文本原样交给原版解析链（定案 1），行为逐旧。 */
    @Test
    public void systemLineKeepsFormattedTextForVanillaChain() {
        String section = String.valueOf((char) 0x00A7);
        ChatLineRecord record = new ChatLineRecord(
                new ChatComponentText("[公告] 维护通知"), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        ChatCardComposer.MessageLines message = composer.compose(group, NOW, 1000, false)
                .getMessages().get(0);

        Assert.assertEquals("系统行走原版链，保留 § 样式码（ChatComponentText 尾注 §r）",
                "[公告] 维护通知" + section + "r", message.getDisplayText());
    }


    // ==================== C8 通道③：markdown 行的装配分支 ====================

    /**
     * 锁 8 前半（装配内容与 pipeline 输入同源 + 形状）：markdown 记录装配出的
     * displayText 必须恰 = {@code args[0]} 原文（含 **、\n、中文、URL 形状），
     * 不带系统行那套 formatted 尾巴（§r）；组无组头、无 sender，形态 = MARKDOWN_LEFT。
     */
    @Test
    public void markdownLineComposesWithRawArgsTextAndNoHeader() {
        String md = "# 公告 **粗**\n第二行 中文 http://a.co";
        ChatLineRecord record = new ChatLineRecord(
                new ChatComponentTranslation(club.heiqi.uilib.api.chat.ChatAccess.MARKDOWN_CHAT_KEY,
                        new Object[] {md}), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        ChatCardComposer.ComposedGroup composed = composer.compose(group, NOW, 1000, false);

        Assert.assertEquals(MessageGroupModel.Alignment.MARKDOWN_LEFT, composed.getAlignment());
        Assert.assertNull("markdown 组无 sender", composed.getSender());
        Assert.assertEquals("无组头名", "", composed.getHeaderName());
        Assert.assertEquals("无组头时间", "", composed.getHeaderTime());
        ChatCardComposer.MessageLines message = composed.getMessages().get(0);
        Assert.assertEquals("displayText = args[0] 原文（逐字，含换行）", md, message.getDisplayText());
        Assert.assertEquals("同一字符串喂 pipeline（cacheKey 单轨自然覆盖）", md,
                message.getDisplayText());
    }

    /**
     * 锁 8 后半（§ 规则对注入内容同样成立）：递交含 § 字面 ⇒ displayText 原样带 §、
     * 零 formatted 追加（对照系统行分支会被 {@code getFormattedText()} 尾注 §r——
     * markdown 分支不得出现该形状）。
     */
    @Test
    public void markdownTextKeepsSectionLiteralWithoutFormattedTail() {
        String section = String.valueOf((char) 0x00A7);
        String md = section + "ab **x**";
        ChatLineRecord record = new ChatLineRecord(
                new ChatComponentTranslation(club.heiqi.uilib.api.chat.ChatAccess.MARKDOWN_CHAT_KEY,
                        new Object[] {md}), 1, NOW - 5000L);
        MessageGroupModel group = new MessageGrouper().group(Arrays.asList(record), "Alex").get(0);
        ChatCardComposer.MessageLines message = composer.compose(group, NOW, 1000, false)
                .getMessages().get(0);
        Assert.assertEquals("§ 原样字面，不剥不转", md, message.getDisplayText());
        Assert.assertFalse("无 formatted 尾注（不进原版渲染文本通道）: " + message.getDisplayText(),
                message.getDisplayText().endsWith(section + "r"));
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