package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** 新 Content 出口的 display 几何；通过真实字体盒和公开命令检查，不读取 L2 私有行盒。 */
public class MarkdownDisplayLayoutTest {
    private static final int FONT = 16;
    private static final String TEX = "\\frac{a+b}{c+d}";
    private int budget;

    @Before public void before() {
        budget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void after() { FontConfig.widthCacheMissBudgetPerWindow = budget; }
    @AfterClass public static void releaseShared() { LatexSoftwareRenderKit.resetShared(); }
    private static TextLayoutService service() { return LatexSoftwareRenderKit.currentService(); }
    private static MarkdownPainter.ContentLayout layout(String source, int width) {
        return layout(source, width, new TextStyle());
    }
    private static MarkdownPainter.ContentLayout layout(String source, int width, TextStyle base) {
        return MarkdownPainter.layoutContent(MarkdownDocument.parse(source).toLayoutContent(null, base),
                service(), width, FONT);
    }
    private static List<PaintCommand> texts(MarkdownPainter.ContentLayout plan) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand c : plan.getCommands()) if (c.getType() == PaintCommandType.SEGMENTS) out.add(c);
        return out;
    }
    private static PaintCommand formula(MarkdownPainter.ContentLayout plan) {
        for (PaintCommand c : texts(plan)) for (TextSegment s : c.getSegments()) if (s.isLatex()) return c;
        throw new AssertionError("missing formula");
    }
    private static TextSegment latex(PaintCommand command) {
        for (TextSegment s : command.getSegments()) if (s.isLatex()) return s;
        throw new AssertionError("missing formula segment");
    }
    private static void assertCentered(PaintCommand formula, int bodyLeft, int right) {
        assertCentered(formula, bodyLeft, right, 2.0);
    }

    /**
     * 居中偏移落在整数列（{@code MarkdownLineLayout.withLeftInsetPx} 是 int），当「可用宽 − 公式宽」
     * 带小数时单侧截断不足 1px，但<b>两侧留白差是截断量的两倍</b>——实测 1.375px（320 宽、
     * 公式宽 40.625 时 leftMargin=139.0 / rightMargin=140.375）。上界取 2px，不能用 1px。
     * 带引用/列表缩进的用例另叠加左 ink 越量保护（{@code Math.max(leftOverhang, center)}），仍在此上界内。
     */
    private static void assertCentered(PaintCommand formula, int bodyLeft, int right, double tolerance) {
        double advance = service().getSegmentWidth(latex(formula), FONT);
        double leftMargin = formula.getLeft() - bodyLeft;
        double rightMargin = right - formula.getLeft() - advance;
        assertTrue("fixture fits body column", leftMargin > 0);
        assertEquals("正文列两侧留白对称偏差超界（整像素截断 1px，另加左 ink 越量保护）",
                leftMargin, rightMargin, tolerance);
    }

    @Test public void displayCentersInBodyColumnIncludingQuoteAndListInsets() {
        int width = 320;
        assertCentered(formula(layout("$$" + TEX + "$$", width)), 0, width);
        // 同一容器中的普通正文提供正文列 oracle，不复制列表缩进算法。
        String source = "> - $$" + TEX + "$$\n>   tail";
        MarkdownPainter.ContentLayout plan = layout(source, width);
        PaintCommand tail = null;
        for (PaintCommand c : texts(plan)) for (TextSegment s : c.getSegments()) {
            if ("tail".equals(s.getText())) tail = c;
        }
        assertNotNull(tail);
        assertCentered(formula(plan), tail.getLeft(), width);
    }

    @Test public void overflowStaysAtomicAndKeepsSizeWithInkInsideScrollWidth() {
        String source = "$$" + TEX + "$$";
        MarkdownPainter.ContentLayout wide = layout(source, 320);
        MarkdownPainter.ContentLayout narrow = layout(source, 1);
        PaintCommand command = formula(narrow);
        TextSegment segment = latex(command);
        MathBox box = service().getLatexBox(segment, FONT);
        assertEquals(1, texts(narrow).size());
        assertEquals(MathStyleOverride.DISPLAY, segment.getLatexMathStyle());
        assertEquals(latex(formula(wide)).getStyle().resolveEffectiveFontSizePx(FONT),
                segment.getStyle().resolveEffectiveFontSizePx(FONT));
        assertEquals(wide.getHeightPx(), narrow.getHeightPx());
        assertEquals("超宽仅为左墨迹留位，不继续居中", Math.ceil(box.getLeftInkOverhang()), command.getLeft(), 0.0);
        assertTrue(command.getLeft() - box.getLeftInkOverhang() >= 0);
        assertTrue(narrow.getWidthPx() >= command.getLeft() + box.getWidth() + box.getRightInkOverhang());
        assertTrue(narrow.getHeightPx() >= box.getTotalHeight());
    }

    @Test public void firstMathListMarkerPaintsOnceWithoutAnExtraRow() {
        MarkdownPainter.ContentLayout plain = layout("$$" + TEX + "$$", 320);
        MarkdownPainter.ContentLayout list = layout("- $$" + TEX + "$$", 320);
        assertEquals("首公式列表项不能先占一行 marker", plain.getHeightPx(), list.getHeightPx());
        assertEquals(2, texts(list).size());
        PaintCommand math = formula(list);
        PaintCommand marker = texts(list).get(0) == math ? texts(list).get(1) : texts(list).get(0);
        assertEquals(1, marker.getSegments().size());
        assertFalse(marker.getSegments().get(0).isLatex());
        assertEquals(0, marker.getLeft());
        assertTrue(marker.getLeft() < math.getLeft());
        assertEquals(0, marker.getTop());
    }

    @Test public void linkedDisplayUsesCenteredInkAndWholeRowWithoutFakeLinks() {
        TextStyle base = new TextStyle();
        base.setLink("https://formula.test");
        MarkdownPainter.ContentLayout plan = layout("$$" + TEX + "$$", 320, base);
        PaintCommand math = formula(plan);
        MathBox box = service().getLatexBox(latex(math), FONT);
        PaintCommand link = null;
        for (PaintCommand c : plan.getCommands()) if (c.getType() == PaintCommandType.LINK_REGION) {
            assertNull("单公式只有一个命中区", link);
            link = c;
        }
        assertNotNull(link);
        assertEquals("https://formula.test", link.getLinkUrl());
        assertTrue(link.getLeft() <= math.getLeft() - box.getLeftInkOverhang());
        assertTrue(link.getRight() >= math.getLeft() + box.getWidth() + box.getRightInkOverhang());
        assertEquals(0, link.getTop());
        assertEquals(plan.getHeightPx(), link.getBottom());
        for (PaintCommand c : layout("$$" + TEX + "$$", 320).getCommands()) {
            assertNotEquals(PaintCommandType.LINK_REGION, c.getType());
        }
    }
}
