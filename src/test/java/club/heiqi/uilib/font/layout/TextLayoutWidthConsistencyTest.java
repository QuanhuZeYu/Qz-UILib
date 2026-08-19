package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 跨层宽度一致性契约：measure 侧 advance 原语累加 = getSegmentWidth = getStringWidth，
 * render 侧（DefaultFontRendererAdapter measuredWidths 公式）与统一原语数值恒等。
 *
 * <p>模型：{@code A/B/C} 固定 raw 宽 1.0（charSize 坐标系），无空格路径；
 * 断言覆盖 letterSpacing 与 superscript（SUP_SUB_SCALE）场景，锁死 5a 统一原语口径。</p>
 */
public class TextLayoutWidthConsistencyTest {

    @Test
    public void advanceAccumulationMatchesSegmentWidthWithLetterSpacing() {
        TextLayoutService service = createService('A', 'B', 'C');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextStyle style = plainStyle();
        style.setLetterSpacing(3);

        double accumulated = service.resolveAdvance('A', style, baseSize)
                + service.resolveAdvance('B', style, baseSize)
                + service.resolveAdvance('C', style, baseSize);
        double segmentWidth = service.getSegmentWidth(new TextSegment("ABC", style), baseSize);

        Assert.assertEquals(segmentWidth, accumulated, 0.001D);
        // 宽 1.0/码点 + 每码点后 3px 字距 ×3
        Assert.assertEquals(12.0D, accumulated, 0.001D);
    }

    @Test
    public void advanceAccumulationMatchesStringWidthWithoutSpacing() {
        TextLayoutService service = createService('A', 'B', 'C');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextStyle style = plainStyle();

        double accumulated = service.resolveAdvance('A', style, baseSize)
                + service.resolveAdvance('B', style, baseSize)
                + service.resolveAdvance('C', style, baseSize);
        int stringWidth = service.getStringWidth("ABC", new TextMeasureStyle(baseSize,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL));

        Assert.assertEquals(stringWidth, accumulated, 0.001D);
        Assert.assertEquals(3.0D, accumulated, 0.001D);
    }

    @Test
    public void renderSideFormulaMatchesUnifiedAdvance() {
        TextLayoutService service = createService('A', 'B');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextStyle style = plainStyle();
        style.setLetterSpacing(5);

        // DefaultFontRendererAdapter measuredWidths 旧公式：纯字宽 + letterSpacing；
        // 5a 后 render 侧直接经 resolveAdvance 取推进宽度，两者必须数值恒等（renderScale=1）。
        // resolveSegmentCodepointWidth 是 font.api 包私有，此处用其公共等价物 getCodepointWidth。
        double oldFormula = service.getCodepointWidth('A', style, baseSize) + style.getLetterSpacing();
        double unified = service.resolveAdvance('A', style, baseSize);

        Assert.assertEquals(oldFormula, unified, 0.001D);
    }

    @Test
    public void superscriptAdvanceScalesBySupSubFactor() {
        TextLayoutService service = createService('A');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextStyle plain = plainStyle();
        TextStyle sup = plainStyle();
        sup.setSuperscript(true);
        int effective = Math.max(1, (int) Math.round(baseSize * TextStyle.SUP_SUB_SCALE));

        double plainAdvance = service.resolveAdvance('A', plain, baseSize);
        double supAdvance = service.resolveAdvance('A', sup, baseSize);
        double supSegmentWidth = service.getSegmentWidth(new TextSegment("A", sup), baseSize);

        Assert.assertEquals(plainAdvance * effective / (double) baseSize, supAdvance, 0.001D);
        Assert.assertEquals(supSegmentWidth, supAdvance, 0.001D);
    }

    private static TextStyle plainStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        float[] boldWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.BOLD);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
            boldWidths[codepoint] = 1.0F;
        }
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
