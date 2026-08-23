package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

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
        // 自己的消息:名字用灰色(与蓝色气泡区分),组头仍为「名字 时间」
        Assert.assertEquals("Steve " + ChatClock.formatTime(NOW - 5000L), composed.getHeaderText());
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
        // 淡出中点:255 - 255*250/500 = 128(整数截断)
        Assert.assertEquals(128, ChatCardComposer.fadeAlpha(NOW - 10_250L, NOW, ttl, fade, 255));
        // 淡出结束即归零
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 10_500L, NOW, ttl, fade, 255));
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 20_000L, NOW, ttl, fade, 255));
    }

    @Test
    public void fadeAlphaHandlesZeroFadeWindow() {
        Assert.assertEquals(255, ChatCardComposer.fadeAlpha(NOW - 5_000L, NOW, 10_000L, 0L, 255));
        Assert.assertEquals(0, ChatCardComposer.fadeAlpha(NOW - 10_001L, NOW, 10_000L, 0L, 255));
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
