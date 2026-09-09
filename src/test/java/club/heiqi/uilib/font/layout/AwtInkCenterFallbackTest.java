package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 冷态 ink 中心的字形身份与无字体回退契约。
 *
 * <p>有匹配字体时，用独立的字符串字形轮廓 oracle 验证码点到字形的映射；无匹配字体时，
 * 应返回盒中心。Dialog 的字符覆盖取决于宿主，不能要求缺少 CJK 字体的 CI 将 missing glyph
 * 轮廓当成真实字符中心。支持状态由基础字体独立判定，不能仅随 matcher 的结果选择预期。</p>
 *
 * <p>码点与 gid 不同并不保证中心不同。判别力用例在当前 JVM 寻找能区分字符串重载与错误
 * int[] 重载的样本，再验证被测入口；无字体用例以空目录和非对称度量确定触发非零盒中心。
 * 两者分别守住错误重载与常量零回退，不依赖历史像素读数、固定物理字体或跳过断言。</p>
 */
public class AwtInkCenterFallbackTest {

    private static final int SIZE_PX = 14;
    private static final float TOLERANCE = 0.01F;

    @Test
    public void coldOpeningParenthesis() {
        assertColdCodepoint('(');
    }

    @Test
    public void coldClosingParenthesis() {
        assertColdCodepoint(')');
    }

    @Test
    public void coldLowercaseX() {
        assertColdCodepoint('x');
    }

    @Test
    public void coldUppercaseW() {
        assertColdCodepoint('W');
    }

    @Test
    public void coldCjkBase() {
        assertColdCodepoint(0x57FA);
    }

    @Test
    public void coldCjkMiddle() {
        assertColdCodepoint(0x4E2D);
    }

    @Test
    public void coldSquareRoot() {
        assertColdCodepoint(0x221A);
    }

    @Test
    public void coldSupplementaryFraktur() {
        assertColdCodepoint(0x1D51E);
    }

    @Test
    public void stringMappingRejectsCodepointAsGlyphId() {
        Font font = new Font("Dialog", Font.PLAIN, SIZE_PX);
        // 先尝试原有 ASCII 反证，再在可打印 BMP 范围寻找有判别力的样本。
        int[] preferred = { '(', ')', 'x', 'W' };
        for (int codepoint : preferred) {
            if (distinguishesGlyphId(font, codepoint)) {
                assertColdCodepoint(codepoint);
                return;
            }
        }
        for (int codepoint = 0x20; codepoint < 0xD800; codepoint++) {
            if (!Character.isISOControl(codepoint) && distinguishesGlyphId(font, codepoint)) {
                assertColdCodepoint(codepoint);
                return;
            }
        }
        Assert.fail("当前 JVM 的 Dialog 样本无法区分字符串映射与码点当 gid，不能将空转记为通过");
    }

    @Test
    public void missingFontUsesNonzeroBoxCenter() {
        ColdFixture fixture = new ColdFixture();
        float atlasSize = (float) FontRuntimeSettings.capture().getAwtCharSize();
        // 由构造确定 UI ascent=SIZE_PX、descent=0；ink 表仍为空。
        fixture.tables.ascentNormal = atlasSize;
        fixture.tables.descentNormal = 0.0F;
        float actual = fixture.metrics.inkCenterOffsetY("x", SIZE_PX);
        Assert.assertNull("空目录必须无匹配字体", fixture.matcher.match(1, 'x', FontType.NORMAL));
        Assert.assertEquals("无字体时必须使用非对称度量的盒中心", -SIZE_PX / 2.0F, actual, TOLERANCE);
    }

    private static void assertColdCodepoint(int codepoint) {
        Font font = new Font("Dialog", Font.PLAIN, SIZE_PX);
        ColdFixture fixture = new ColdFixture(font);
        String text = new String(Character.toChars(codepoint));
        // 先走被测入口，避免前置 match 把冷态匹配预热成缓存命中。
        float actual = fixture.metrics.inkCenterOffsetY(text, SIZE_PX);
        boolean supported = font.canDisplay(codepoint);
        Font matched = fixture.matcher.match(1, codepoint, FontType.NORMAL);
        String tag = "U+" + Integer.toHexString(codepoint) + " supported=" + supported + " matched=" + matched;
        if (supported) {
            Assert.assertEquals(tag + " 可显示字符必须匹配夹具字体", font, matched);
        } else {
            Assert.assertNull(tag + " 不可显示字符必须无匹配字体", matched);
        }
        // 默认夹具 ascent/descent 由空表构造为零；缺字不能拿豆腐块轮廓作为 oracle。
        float expected = supported ? stringGlyphCenter(font, text) : 0.0F;
        Assert.assertEquals(tag + " actual=" + actual + " expected=" + expected
                        + " missingGlyphCenter=" + glyphCodeCenter(font, font.getMissingGlyphCode()),
                expected, actual, TOLERANCE);
    }

    private static boolean distinguishesGlyphId(Font font, int codepoint) {
        return font.canDisplay(codepoint)
                && Math.abs(stringGlyphCenter(font, new String(Character.toChars(codepoint)))
                        - glyphCodeCenter(font, codepoint)) > TOLERANCE;
    }

    /** 独立 oracle：同字体、同字号、同 FRC 的字符串轮廓中心。 */
    private static float stringGlyphCenter(Font font, String text) {
        Font sized = font.deriveFont(Font.PLAIN, (float) SIZE_PX);
        FontRenderContext frc = new FontRenderContext(sized.getTransform(), true, false);
        return center(sized.createGlyphVector(frc, text).getVisualBounds());
    }

    /** gid 路径只作错误重载反证及 missing glyph 诊断；中心相等不能证明字形身份相同。 */
    private static float glyphCodeCenter(Font font, int glyphCode) {
        Font sized = font.deriveFont(Font.PLAIN, (float) SIZE_PX);
        FontRenderContext frc = new FontRenderContext(sized.getTransform(), true, false);
        return center(sized.createGlyphVector(frc, new int[] { glyphCode }).getVisualBounds());
    }

    private static float center(Rectangle2D bounds) {
        return (float) (bounds.getY() + bounds.getHeight() / 2.0D);
    }

    /** 只建字体目录与空字形表，不装配字形。 */
    private static final class ColdFixture {

        final FontMatcher matcher;
        final GlyphRuntimeTables tables;
        final MathMetrics metrics;

        ColdFixture(Font... fonts) {
            FontCatalog catalog = new FontCatalog();
            catalog.replaceAll(Arrays.asList(fonts));
            DerivedFontCache cache = new DerivedFontCache(catalog);
            GlyphPageManager pages = new GlyphPageManager();
            tables = pages.getRuntimeTables();
            matcher = new FontMatcher(catalog, cache);
            matcher.setRuntimeTables(1, tables);
            TextLayoutService service = new TextLayoutService(matcher, pages, cache);
            service.setRuntimeVersion(1);
            TextStyle style = new TextStyle();
            style.resetAll(0xFFFFFFFF);
            metrics = service.createMathMetrics(style, SIZE_PX);
        }
    }
}
