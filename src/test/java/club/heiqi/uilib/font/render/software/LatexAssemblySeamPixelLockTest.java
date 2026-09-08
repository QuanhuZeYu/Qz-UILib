package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import club.heiqi.uilib.font.latex.layout.GlyphElem;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit.RenderResult;

/**
 * 伸缩结构接缝的像素常驻锁：稳定竖直列的逐行亮度必须恒定，不得在接缝处周期性加深。
 *
 * <p><b>为什么必须有本锁</b>：B4 的接缝判据原先只存在于一次性构建产物的报告里
 * （{@code build/reports/latex-plan-completion/b3b4-visual/.../verification.json} 的
 * {@code width50_min/max} 与 {@code coverage_min/max}），{@code src/test} 无对应断言，
 * 换机重跑就失去保护。合法 connector overlap 一旦在输出侧重复 source-over，
 * 接缝处会周期性变亮/变宽，而「有墨水」「连通」这类弱判据抓不到。</p>
 *
 * <p><b>口径</b>：先断言公式确实走了部件拼接（存在带 clip 的字形），再在真实软件光栅化结果里
 * 自动检出「稳定竖直列」——该列存在一段长度不少于图像高度四分之一的连续行、且这段内逐行亮度
 * 完全相等（直条中段的特征；弯钩过渡段的亮度随形状变化，不会被选中）。把 x 相邻且行段重叠的
 * 稳定列聚成一组，在该组的公共行段上逐行比较亮度总和：接缝重复叠加会让某一行偏高，立即失败。
 * 判据不钉具体像素宽度——那随平台字体度量变化，钉死会变成跨机假红。</p>
 *
 * <p>渲染链路与真机同源（{@code TextLayoutService} → {@code GlyphGenerator} → 软件页装配 →
 * {@code DefaultFontRendererAdapter} → 软件光栅化），但仍是 headless 软件路径，
 * 不等于 GPU shader 或真机观感验收。</p>
 */
public class LatexAssemblySeamPixelLockTest {

    private static final int FONT = 16;
    /** 与 B3/B4 验收矩阵同一真实倍率：16px × 4 = 64px，正好等于图集基准字号。 */
    private static final float SCALE = 4.0F;
    /** 50% 黑度阈值（背景 {@code 0xFF202020} 亮度约 32）。 */
    private static final int THRESHOLD = 128;

    @BeforeClass
    public static void enableMathFont() {
        LatexSoftwareRenderKit.enableMathFont();
    }

    @AfterClass
    public static void releaseShared() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void stitchedRoundParenthesisKeepsConstantStemBrightness() {
        assertStemBrightnessConstant("\\left(\\begin{matrix}a\\\\b\\\\c\\\\d\\\\e\\\\f\\\\g\\\\h"
                + "\\\\i\\\\j\\end{matrix}\\right)");
    }

    @Test
    public void stitchedRadicalKeepsConstantStemBrightness() {
        assertStemBrightnessConstant("\\sqrt{\\begin{matrix}a\\\\b\\\\c\\\\d\\\\e\\\\f\\\\g\\\\h"
                + "\\\\i\\\\j\\end{matrix}}");
    }

    private static void assertStemBrightnessConstant(String tex) {
        MathBox box = LatexSoftwareRenderKit.layout(tex, FONT);
        int stitched = 0;
        for (GlyphElem glyph : box.getGlyphs()) {
            if (glyph.getMathGlyphClip() != null) {
                stitched++;
            }
        }
        Assert.assertTrue("公式未走部件拼接（带 clip 字形不足），像素判据失去对象: " + tex, stitched >= 2);

        RenderResult result = LatexSoftwareRenderKit.render("<latex>" + tex + "</latex>", FONT, true, SCALE);
        List<int[]> groups = stableColumnGroups(result);
        Assert.assertFalse("未检出稳定竖直列，判据无法成立: " + tex, groups.isEmpty());
        int compared = 0;
        for (int[] group : groups) {
            compared += assertGroupRowsConstant(result, group[0], group[1], group[2], group[3], tex);
        }
        Assert.assertTrue("公共行段太短，判据未真正覆盖接缝周期: " + tex, compared >= 2);
    }

    /**
     * 检出稳定竖直列并聚组，返回 {@code [fromX, toX, fromY, toY]}：
     * 组内每列都有一段「连续、逐行亮度相等、长度 >= height/4」的行段，且组内列行段两两重叠。
     */
    private static List<int[]> stableColumnGroups(RenderResult result) {
        int minimumRun = Math.max(8, result.height / 4);
        List<int[]> columns = new ArrayList<int[]>();
        for (int x = 0; x < result.width; x++) {
            int runStart = -1;
            int runValue = -1;
            int bestStart = -1;
            int bestEnd = -1;
            for (int y = 0; y <= result.height; y++) {
                int value = y < result.height ? luminance(result.pixels[y * result.width + x]) : -1;
                if (value >= THRESHOLD && value == runValue) {
                    continue;
                }
                if (runStart >= 0 && y - 1 - runStart > bestEnd - bestStart) {
                    bestStart = runStart;
                    bestEnd = y - 1;
                }
                runStart = value >= THRESHOLD ? y : -1;
                runValue = value;
            }
            if (bestStart >= 0 && bestEnd - bestStart + 1 >= minimumRun) {
                columns.add(new int[] {x, bestStart, bestEnd});
            }
        }
        List<int[]> groups = new ArrayList<int[]>();
        int index = 0;
        while (index < columns.size()) {
            int last = index;
            int fromY = columns.get(index)[1];
            int toY = columns.get(index)[2];
            while (last + 1 < columns.size() && columns.get(last + 1)[0] == columns.get(last)[0] + 1) {
                int nextFrom = Math.max(fromY, columns.get(last + 1)[1]);
                int nextTo = Math.min(toY, columns.get(last + 1)[2]);
                if (nextTo - nextFrom < minimumRun / 2) {
                    break;
                }
                fromY = nextFrom;
                toY = nextTo;
                last++;
            }
            groups.add(new int[] {columns.get(index)[0], columns.get(last)[0], fromY, toY});
            index = last + 1;
        }
        return groups;
    }

    /** 在组公共行段上逐行比较亮度总和；返回实际比较过的行数。 */
    private static int assertGroupRowsConstant(RenderResult result, int fromX, int toX, int fromY, int toY,
            String tex) {
        int expected = -1;
        int compared = 0;
        for (int y = fromY; y <= toY; y++) {
            int total = 0;
            for (int x = fromX; x <= toX; x++) {
                total += luminance(result.pixels[y * result.width + x]);
            }
            if (expected < 0) {
                expected = total;
                continue;
            }
            Assert.assertEquals("直条行 " + y + "（x=" + fromX + ".." + toX
                    + "）亮度总和出现接缝加深: " + tex, expected, total);
            compared++;
        }
        return compared;
    }

    /**
     * Rec.601 亮度。不能用 alpha 通道：软件帧背景 {@code 0xFF202020} 的 alpha 同样是 0xFF，
     * 用它判墨水会让「每一列都是直条」恒真，判据退化成假绿。
     */
    private static int luminance(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }
}
