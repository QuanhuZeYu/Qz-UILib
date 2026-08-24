package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathLayoutService;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * LaTeX 片段在 {@link TextLayoutService} 的解析与测量集成测试（M3）。
 */
public class TextLayoutServiceLatexTest {

    @Test
    public void shouldParseLatexSegmentInRichMode() {
        TextLayoutService service = createService();
        List<TextSegment> segments = service.parseSegments("前<latex>\\frac{a}{b}</latex>后",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        Assert.assertEquals(3, segments.size());
        Assert.assertFalse(segments.get(0).isLatex());
        Assert.assertEquals("前", segments.get(0).getText());
        Assert.assertTrue(segments.get(1).isLatex());
        Assert.assertEquals("\\frac{a}{b}", segments.get(1).getLatexSource());
        Assert.assertFalse(segments.get(2).isLatex());
    }

    @Test
    public void shouldMeasureLatexWidthEqualToLayout() {
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        List<TextSegment> segments = service.parseSegments("<latex>\\frac{a}{b}</latex>",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        TextSegment latex = segments.get(0);
        Assert.assertTrue(latex.isLatex());
        int fontSizePx = 16;
        MathBox expected = new MathLayoutService().layout(LatexParser.parse(latex.getLatexSource()),
                fontSizePx, service.createMathMetrics(style, fontSizePx));
        double measured = service.getSegmentWidth(latex, fontSizePx);
        Assert.assertEquals(expected.getWidth(), measured, 0.5D);
        Assert.assertTrue(measured > 0.0D);
    }

    @Test
    public void shouldMeasureMixedTextAndLatex() {
        TextLayoutService service = createService();
        List<TextSegment> segments = service.parseSegments("a<latex>x^2</latex>b",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        double total = 0.0D;
        for (TextSegment segment : segments) {
            total += service.getSegmentWidth(segment, 16);
        }
        Assert.assertTrue(total > 0.0D);
        // 混排宽度 = 各段宽度之和
        double plain = service.getStringWidth("ab", TextContentMode.UILIB_RAW);
        Assert.assertTrue(total > plain);
    }

    @Test
    public void shouldProvideConsistentMathMetrics() {
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        club.heiqi.uilib.font.latex.layout.MathMetrics metrics = service.createMathMetrics(style, 16);
        // advance 随字号线性缩放（宽度表值 × sizePx/基准 charSize）
        Assert.assertEquals(metrics.advance("a", 9) * 16.0F / 9.0F, metrics.advance("a", 16), 0.5F);
        Assert.assertTrue(metrics.advance("a", 16) > 0.0F);
        // 测试环境未发布字体 metrics（ascent/descent 表值为 0），只断言有限值不抛异常；
        // 真机由 FontService 发布 generation 后为正值。
        Assert.assertTrue(Float.isFinite(metrics.ascent(16)));
        Assert.assertTrue(Float.isFinite(metrics.descent(16)));
    }

    @Test
    public void shouldKeepLatexSourceThroughSerialize() {
        TextLayoutService service = createService();
        List<TextSegment> segments = service.parseSegments("a<latex>\\sum_{i=1}^{n} i</latex>b",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        String serialized = RichTextTagParser.serialize(segments, null);
        Assert.assertTrue(serialized.contains("<latex>"));
        Assert.assertTrue(serialized.contains("</latex>"));
        // 往返可再解析出相同 latex 源码
        List<TextSegment> reparsed = service.parseSegments(serialized, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        String latexSource = null;
        for (TextSegment segment : reparsed) {
            if (segment.isLatex()) {
                latexSource = segment.getLatexSource();
            }
        }
        Assert.assertEquals("\\sum_{i=1}^{n} i", latexSource);
    }

    @Test
    public void shouldKeepLatexAtomicInWrap() {
        TextLayoutService service = createService();
        String wrapped = service.wrapFormattedStringToWidth("a<latex>x^2</latex>b", 16,
                TextContentMode.RICH_TAGS);
        List<String> lines = Arrays.asList(wrapped.split("\\n", -1));
        Assert.assertEquals(3, lines.size());
        Assert.assertEquals("a", lines.get(0));
        Assert.assertEquals("<latex>x^2</latex>", lines.get(1));
        Assert.assertEquals("b", lines.get(2));
    }

    @Test
    public void shouldKeepLatexAtomicInTrim() {
        TextLayoutService service = createService();
        String trimmed = service.trimStringToWidth("a<latex>x^2</latex>b", 10,
                TextContentMode.RICH_TAGS);
        Assert.assertEquals("a", trimmed);
    }

    @Test
    public void shouldIncludeLatexBoxInRichLineHeight() {
        TextLayoutService service = createService();
        int baseSize = (int) club.heiqi.uilib.font.FontRuntimeSettings.capture().getCharSize();
        TextMeasureStyle richStyle = club.heiqi.uilib.ui.text.TextMeasureStyle.fontSizePx(baseSize)
                .withTextContentMode(TextContentMode.RICH_TAGS);
        int withLatex = service.getLineHeight("A<latex>\\frac{a}{b}</latex>", richStyle);
        int withoutLatex = service.getLineHeight("A", richStyle);
        Assert.assertTrue("含公式行高不应低于纯文本行高", withLatex >= withoutLatex);
    }

    @Test
    public void shouldAddLinePadToLatexLineHeight() {
        // 盒度量 ink 化后公式盒总高=内容墨水高，行高必须附加行距余量（上下各 0.1em），
        // 否则多行公式零间距（24px 压力卡多行分数视觉重叠）
        TextLayoutService service = createService();
        int baseSize = (int) club.heiqi.uilib.font.FontRuntimeSettings.capture().getCharSize();
        TextMeasureStyle richStyle = club.heiqi.uilib.ui.text.TextMeasureStyle.fontSizePx(baseSize)
                .withTextContentMode(TextContentMode.RICH_TAGS);
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        MathBox box = new MathLayoutService().layout(LatexParser.parse("\\frac{a}{b}"), baseSize,
                service.createMathMetrics(style, baseSize));
        int withLatex = service.getLineHeight("<latex>\\frac{a}{b}</latex>", richStyle);
        int expectedMin = (int) Math.ceil(box.getTotalHeight() + 0.2F * baseSize);
        Assert.assertTrue("公式行高应 ≥ 盒总高 + 0.2em 行距余量: withLatex=" + withLatex
                + " expectedMin=" + expectedMin, withLatex >= expectedMin - 0.5);
    }

    // ==================== T8:行内公式行高约束(设计稿 §3.5:>1.6×行高按 0.85 缩放重排) ====================

    @Test
    public void shouldShrinkLatexSegmentWhenBoxExceedsLineHeightFactor() {
        TextLayoutService service = createService();
        int baseSize = 16;
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        List<TextSegment> segments = service.parseSegments("<latex>\\frac{a}{b}</latex>",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        TextSegment latex = segments.get(0);
        Assert.assertTrue(latex.isLatex());
        MathBox original = new MathLayoutService().layout(LatexParser.parse(latex.getLatexSource()),
                baseSize, service.createMathMetrics(style, baseSize));

        // 阈值设得比盒高低(行高 1 × 系数 1.0) → 必触发缩放:段字号 16 → round(16×0.85)=14
        List<TextSegment> constrained = service.applyLatexLineHeightConstraint(
                segments, baseSize, 1, 1.0F, 0.85F);
        TextSegment shrunk = constrained.get(0);
        Assert.assertTrue(shrunk.isLatex());
        Assert.assertEquals("超限公式段字号按 0.85 缩放", 14,
                shrunk.getStyle().resolveEffectiveFontSizePx(baseSize));

        // 缩放后盒高/宽度等比收敛(布局字号锚定,LatexCache 按键分号)
        MathBox shrunkBox = new MathLayoutService().layout(LatexParser.parse(shrunk.getLatexSource()),
                14, service.createMathMetrics(style, 14));
        Assert.assertTrue("缩放后盒高应小于原盒高", shrunkBox.getTotalHeight() < original.getTotalHeight());
        double originalWidth = service.getSegmentWidth(latex, baseSize);
        double shrunkWidth = service.getSegmentWidth(shrunk, baseSize);
        Assert.assertTrue("缩放后宽度应小于原宽度(测量与渲染同源)", shrunkWidth < originalWidth);
    }

    @Test
    public void shouldKeepSegmentsUnchangedWhenWithinLineHeightThreshold() {
        TextLayoutService service = createService();
        int baseSize = 16;
        List<TextSegment> segments = service.parseSegments("a<latex>x^2</latex>b",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        // 阈值极大 → 不触发 → 必须零拷贝返回原列表
        List<TextSegment> constrained = service.applyLatexLineHeightConstraint(
                segments, baseSize, 10_000, 1.6F, 0.85F);
        Assert.assertSame("未超限必须零拷贝(原列表身份)", segments, constrained);
        // 无 latex 段流同样零拷贝
        List<TextSegment> plain = service.parseSegments("hello",
                0xFFFFFFFF, TextContentMode.UILIB_RAW);
        Assert.assertSame(plain, service.applyLatexLineHeightConstraint(
                plain, baseSize, 10_000, 1.6F, 0.85F));
    }

    @Test
    public void shouldShrinkAtMostOnceWhenStillOverThreshold() {
        // 缩放后仍超限:保持缩放结果不再递归(截断+省略号按行高上限 clamp 降级)
        TextLayoutService service = createService();
        int baseSize = 16;
        List<TextSegment> segments = service.parseSegments("<latex>\\frac{a}{b}</latex>",
                0xFFFFFFFF, TextContentMode.RICH_TAGS);
        List<TextSegment> constrained = service.applyLatexLineHeightConstraint(
                segments, baseSize, 1, 1.0F, 0.85F);
        Assert.assertEquals("只缩放一次(0.85),不递归", 14,
                constrained.get(0).getStyle().resolveEffectiveFontSizePx(baseSize));
    }

    private static TextLayoutService createService() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        normalWidths['a'] = 8.0F;
        normalWidths['b'] = 8.0F;
        normalWidths['x'] = 8.0F;
        normalWidths['2'] = 8.0F;
        normalWidths['+'] = 8.0F;
        FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
        fontMatcher.setRuntimeTables(1, glyphPageManager.getRuntimeTables());
        TextLayoutService service = new TextLayoutService(fontMatcher, glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
