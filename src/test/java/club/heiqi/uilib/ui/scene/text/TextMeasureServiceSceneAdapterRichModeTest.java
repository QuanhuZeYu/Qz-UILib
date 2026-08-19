package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * {@link TextMeasureServiceSceneAdapter} 富文本拆行委托与模式映射测试。
 */
public class TextMeasureServiceSceneAdapterRichModeTest {

    @Test
    public void shouldDelegateSplitLinesWithModeMapping() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        delegate.nextLines = java.util.Arrays.asList("a", "b");
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        List<String> lines = adapter.splitLines("ab", 16, 100, 2);

        Assert.assertEquals(java.util.Arrays.asList("a", "b"), lines);
        Assert.assertEquals("ab", delegate.lastText);
        Assert.assertEquals(100, delegate.lastWrapWidth);
        Assert.assertEquals(TextContentMode.RICH_TAGS, delegate.lastMode);
        Assert.assertEquals(1, delegate.threeArgCallCount);
    }

    @Test
    public void shouldDelegateHardLineBreakSplitWithoutWrap() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        delegate.nextLines = java.util.Arrays.asList("<b>a</b>", "<b>b</b>");
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        List<String> lines = adapter.splitLines("<b>a<br>b</b>", 16, 0, 2);

        Assert.assertEquals(java.util.Arrays.asList("<b>a</b>", "<b>b</b>"), lines);
        Assert.assertEquals("<b>a<br>b</b>", delegate.lastText);
        // 非 wrap 以无限宽委托：软换行不触发、硬换行仍拆行
        Assert.assertEquals(Integer.MAX_VALUE, delegate.lastWrapWidth);
        Assert.assertEquals(TextContentMode.RICH_TAGS, delegate.lastMode);
    }

    @Test
    public void shouldDelegateTrimToWidthWithModeMapping() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        String result = adapter.trimToWidth("abcdef", 16, 30, 2);

        Assert.assertEquals("trimmed", result);
        Assert.assertEquals("abcdef", delegate.lastTrimText);
        Assert.assertEquals(30, delegate.lastTrimWidth);
        Assert.assertEquals(TextContentMode.RICH_TAGS, delegate.lastTrimStyle.getTextContentMode());
    }

    @Test
    public void shouldDelegateTrimToWidthWithFontSizePx() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        adapter.trimToWidth("abcdef", 24, 30, 2);

        // 修复回归：字号必须透传（无 style 重载按基准字号裁剪，非基准字号下省略号测距错误）
        Assert.assertEquals(24, delegate.lastTrimStyle.getFontSizePx());
    }

    @Test
    public void shouldMapLinkRegionsToSceneTypes() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        delegate.nextLinkRegions = java.util.Arrays.asList(
                new club.heiqi.uilib.ui.text.TextLinkRegion(8, 16, "https://a.b"));
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        List<TextLinkRegion> regions = adapter.linkRegions("<a=https://a.b>x</a>", 16, 2);

        Assert.assertEquals(1, regions.size());
        Assert.assertEquals(8, regions.get(0).getStartX());
        Assert.assertEquals(16, regions.get(0).getWidth());
        Assert.assertEquals("https://a.b", regions.get(0).getUrl());
    }

    @Test
    public void shouldMapTextModeCodes() {
        RecordingTextMeasureService delegate = new RecordingTextMeasureService();
        TextMeasureServiceSceneAdapter adapter = new TextMeasureServiceSceneAdapter(delegate);

        adapter.splitLines("x", 16, 50, 1);
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, delegate.lastMode);
        adapter.splitLines("x", 16, 50, 0);
        Assert.assertEquals(TextContentMode.UILIB_RAW, delegate.lastMode);
        adapter.splitLines("x", 16, 50, 9);
        Assert.assertEquals(TextContentMode.UILIB_RAW, delegate.lastMode);
    }

    /** 记录三参换行委托调用的测量服务替身。 */
    private static final class RecordingTextMeasureService implements TextMeasureService {

        private String lastText;
        private int lastWrapWidth;
        private TextContentMode lastMode;
        private int threeArgCallCount;
        private List<String> nextLines = new ArrayList<String>();
        private String lastTrimText;
        private int lastTrimWidth;
        private club.heiqi.uilib.ui.text.TextMeasureStyle lastTrimStyle;
        private java.util.List<club.heiqi.uilib.ui.text.TextLinkRegion> nextLinkRegions =
                new java.util.ArrayList<club.heiqi.uilib.ui.text.TextLinkRegion>();

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return 0;
        }

        @Override
        public int getLineHeight() {
            return 16;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
            lastText = text;
            lastWrapWidth = wrapWidth;
            lastMode = textContentMode;
            threeArgCallCount++;
            return new ArrayList<String>(nextLines);
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth,
                club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            lastTrimText = text;
            lastTrimWidth = targetWidth;
            lastTrimStyle = style;
            return "trimmed";
        }

        @Override
        public java.util.List<club.heiqi.uilib.ui.text.TextLinkRegion> getLinkRegions(String line,
                club.heiqi.uilib.ui.text.TextMeasureStyle style) {
            return new java.util.ArrayList<club.heiqi.uilib.ui.text.TextLinkRegion>(nextLinkRegions);
        }
    }
}
