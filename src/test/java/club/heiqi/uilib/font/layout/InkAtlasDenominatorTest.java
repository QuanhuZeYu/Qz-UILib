package club.heiqi.uilib.font.layout;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;

/**
 * ink 表换算分母契约测试。
 *
 * <p><b>锁什么</b>：{@code MathMetrics} 的 ink 接口把字形页像素表换算到显示像素时，分母必须是
 * 表数据的真实基准 —— AWT 字形生成用的整数点阵 {@code FontRuntimeSettings#getGlyphSize()}
 * （{@code GlyphGenerator} 以该尺寸光栅化并扫描像素得到 ink 表）。此前用
 * {@code (int) getAwtCharSize()} 截断，在 awtCharSize 非整数时与生成基准相差 ceil/trunc 倍，
 * 同一份 ink 表在测量侧与绘制侧（{@code DefaultFontRendererAdapter} 传
 * {@code getGlyphSize()} 作 defaultGlyphSize）被换成两把尺子。</p>
 *
 * <p><b>为什么必须非整数 awtCharSize</b>：整数取值下 ceil == trunc，本锁无法区分两种口径；
 * {@code 64.5} 使 ceil=65、trunc=64，比值 1.5625%，24px 字号下 inkWidth 差 0.25px，
 * 远超 1e-3 容差。</p>
 *
 * <p><b>负向验证</b>：把分母改回 {@code (int) currentSettings().getAwtCharSize()}，
 * 本测试在 inkWidth / inkHeight / inkCenterOffsetY / italicOverhang / inkLeftBearing 上必红。</p>
 */
public class InkAtlasDenominatorTest {

    private static final int SIZE_PX = 24;
    private static final double NON_INTEGER_AWT_CHAR_SIZE = 64.5D;
    private static final float TOLERANCE = 1.0E-3F;
    private static final int[] PROBES = { 'A', 'x', '(', ')', 0x221A };
    private static final String SOURCE = "<latex>(x)\\sqrt{x}A</latex>";

    private static double savedAwtCharSize;

    @BeforeClass
    public static void setUpClass() {
        savedAwtCharSize = FontConfig.awtCharSize;
        FontConfig.awtCharSize = NON_INTEGER_AWT_CHAR_SIZE;
        FontConfig.refreshDerivedRuleSet();
        LatexSoftwareRenderKit.resetShared();
    }

    @AfterClass
    public static void tearDownClass() {
        FontConfig.awtCharSize = savedAwtCharSize;
        FontConfig.refreshDerivedRuleSet();
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void inkMetricsDivideByRasterGlyphSize() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(SOURCE, SIZE_PX);
        FontRuntimeSettings settings = FontRuntimeSettings.capture();
        int raster = settings.getGlyphSize();
        int truncated = Math.max(1, (int) settings.getAwtCharSize());
        Assert.assertTrue("本锁必须在 ceil/trunc 分叉区间生效: raster=" + raster
                + " truncated=" + truncated, raster != truncated);

        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        MathMetrics metrics = LatexSoftwareRenderKit.currentService().createMathMetrics(style, SIZE_PX);

        short[] inkWidths = result.tables.inkWidthArray(FontType.NORMAL);
        short[] inkHeights = result.tables.inkHeightArray(FontType.NORMAL);
        short[] bearingsX = result.tables.bearingXArray(FontType.NORMAL);
        short[] bearingsY = result.tables.bearingYArray(FontType.NORMAL);
        float scale = (float) SIZE_PX / (float) raster;

        List<String> covered = new ArrayList<String>();
        for (int codepoint : PROBES) {
            if (inkWidths[codepoint] <= 0 || inkHeights[codepoint] <= 0) {
                continue;
            }
            String glyph = new String(Character.toChars(codepoint));
            String tag = "U+" + Integer.toHexString(codepoint) + "(" + glyph + ")";
            assertMetric(tag, "inkWidth", inkWidths[codepoint] * scale, metrics.inkWidth(glyph, SIZE_PX));
            assertMetric(tag, "inkHeight", inkHeights[codepoint] * scale,
                    metrics.inkHeight(glyph, SIZE_PX));
            assertMetric(tag, "inkLeftBearing", bearingsX[codepoint] * scale,
                    metrics.inkLeftBearing(glyph, SIZE_PX));
            assertMetric(tag, "inkCenterOffsetY",
                    (bearingsY[codepoint] + inkHeights[codepoint] / 2.0F) * scale,
                    metrics.inkCenterOffsetY(glyph, SIZE_PX));
            assertMetric(tag, "italicOverhang", 0.25F * inkHeights[codepoint] * scale,
                    metrics.italicOverhang(glyph, SIZE_PX));
            covered.add(glyph);
        }
        Assert.assertTrue("至少要有样本字形装配成功: " + covered, covered.size() >= 4);
    }

    private static void assertMetric(String tag, String metric, float expected, float actual) {
        Assert.assertEquals(metric + " " + tag + " 必须以 getGlyphSize() 为分母", expected, actual,
                TOLERANCE);
    }
}
