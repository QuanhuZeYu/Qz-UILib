package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 冷态 ink 中心的字形身份契约测试。
 *
 * <p><b>锁什么</b>：字形页 ink 表未就绪时，{@code MathMetrics.inkCenterOffsetY} 的 AWT 回退必须
 * 按「字符串 → 字形」映射取轮廓中心。{@code Font.createGlyphVector(FontRenderContext, int[])}
 * 收的是 <b>glyph code</b>，把 Unicode 码点当 gid 传入会取到无关字形（小码点）或 missing glyph
 * （CJK / 数学符号一律落到同一个豆腐块边界），定界符轴锚定随之系统性偏移。</p>
 *
 * <p><b>凭什么不是同义反复</b>：oracle 独立构造同字体、同字号、同 FRC 的字符串字形轮廓中心，
 * 与被测入口零共享实现；样本跨 ASCII、CJK 全角、数学符号与 supplementary 平面，
 * 只有走字符串映射才能同时满足全部样本。</p>
 *
 * <p><b>负向验证</b>：把 {@code TextLayoutService#awtInkCenterOffsetY} 改回
 * {@code new int[] { codepoint }}，本测试对 {@code (} 偏 −1.39px、对 {@code x} 偏 −1.32px、
 * 对 {@code 基} 偏 +0.88px，断言必红。</p>
 */
public class AwtInkCenterFallbackTest {

    private static final int SIZE_PX = 14;
    private static final float TOLERANCE = 0.01F;

    @Test
    public void coldInkCenterUsesStringGlyphMapping() {
        TextLayoutService service = createColdService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        MathMetrics metrics = service.createMathMetrics(style, SIZE_PX);
        int[] codepoints = { '(', ')', 'x', 'W', 0x57FA, 0x4E2D, 0x221A, 0x1D51E };
        for (int codepoint : codepoints) {
            String text = new String(Character.toChars(codepoint));
            float actual = metrics.inkCenterOffsetY(text, SIZE_PX);
            float expected = stringGlyphCenter(text);
            Assert.assertEquals("U+" + Integer.toHexString(codepoint)
                            + " 冷态 ink 中心必须按字符串字形映射取值: actual=" + actual
                            + " expected=" + expected + " missingGlyphCenter=" + missingGlyphCenter(),
                    expected, actual, TOLERANCE);
        }
    }

    /** 独立 oracle：同字体、同字号、同 FRC 的字符串字形轮廓中心（y 向下口径）。 */
    private static float stringGlyphCenter(String text) {
        Font sized = new Font("Dialog", Font.PLAIN, 14).deriveFont(Font.PLAIN, (float) SIZE_PX);
        FontRenderContext frc = new FontRenderContext(sized.getTransform(), true, false);
        Rectangle2D bounds = sized.createGlyphVector(frc, text).getVisualBounds();
        return (float) (bounds.getY() + bounds.getHeight() / 2.0D);
    }

    /** missing glyph 的中心，用于在失败信息里直指「取到豆腐块」这一具体错因。 */
    private static float missingGlyphCenter() {
        Font sized = new Font("Dialog", Font.PLAIN, 14).deriveFont(Font.PLAIN, (float) SIZE_PX);
        FontRenderContext frc = new FontRenderContext(sized.getTransform(), true, false);
        Rectangle2D bounds = sized.createGlyphVector(frc, new int[] { 0x1D51E }).getVisualBounds();
        return (float) (bounds.getY() + bounds.getHeight() / 2.0D);
    }

    /**
     * 冷态装配：只建字体目录与空字形表，不装配任何字形，故 ink 表全 0，
     * {@code inkCenterOffsetY} 必然走 AWT 回退分支。
     */
    private static TextLayoutService createColdService() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
        fontMatcher.setRuntimeTables(1, glyphPageManager.getRuntimeTables());
        TextLayoutService service = new TextLayoutService(fontMatcher, glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
