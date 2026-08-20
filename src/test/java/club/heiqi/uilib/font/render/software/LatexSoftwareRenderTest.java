package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.font.render.GlyphRenderBatch;

/**
 * LaTeX headless 软件渲染验收测试（LatexPage 演示公式全集）。
 *
 * <p>链路与真机同源：TextLayoutService 度量 → GlyphGenerator 字形 → 真 skyline 页装配 →
 * DefaultFontRendererAdapter.renderSegmentsToCollector（真机同一展平/几何/收集逻辑）→
 * FontSoftwareRasterizer 光栅化 → 像素断言。布局瑕疵（错位/分离/越界）在像素与
 * quad 几何上直接可见，不依赖真机目检。</p>
 */
public class LatexSoftwareRenderTest {

    private static final int BASE_SIZE = 16;

    /** 释放共享字形表（约 123MiB），避免后续测试类 OOM。 */
    @AfterClass
    public static void releaseSharedTables() {
        LatexSoftwareRenderKit.resetShared();
    }

    /** 全部演示公式：渲染不抛异常、推进为正、有墨水、quad 数 > 0。 */
    @Test
    public void rendersAllShowcaseFormulasWithInk() {
        String[] formulas = LatexShowcaseFormulas.all();
        Assert.assertTrue("公式清单不应为空", formulas.length > 0);
        for (String formula : formulas) {
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                    "<latex>" + formula + "</latex>", BASE_SIZE);
            Assert.assertTrue("公式推进应为正: " + formula, result.advanceWidth > 0);
            Assert.assertTrue("公式应有墨水像素: " + formula, result.inkPixelCount(0xFF202020) > 0);
            Assert.assertTrue("公式应有收集 quad: " + formula, result.collector.getQuadCount() > 0);
        }
    }

    /** 分数：规则线水平、位于分子与分母之间、横跨两者。 */
    @Test
    public void fracRendersHorizontalRuleBetweenNumeratorAndDenominator() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\frac{1}{2}</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("分数应有规则线（decoration quad）", !rules.isEmpty());
        Assert.assertTrue("分数应有分子分母字形", glyphs.size() >= 2);

        Quad rule = rules.get(0);
        Assert.assertTrue("规则线应水平（厚度 1-2px）", rule.height() >= 1 && rule.height() <= 2);
        Assert.assertTrue("规则线宽应为正", rule.width() > 0);

        // 分子组 = 规则线上方字形；分母组 = 规则线下方字形
        List<Quad> numerator = new ArrayList<Quad>();
        List<Quad> denominator = new ArrayList<Quad>();
        for (Quad glyph : glyphs) {
            if (glyph.bottom <= rule.top + 1) {
                numerator.add(glyph);
            } else if (glyph.top >= rule.bottom - 1) {
                denominator.add(glyph);
            }
        }
        Assert.assertTrue("分子字形应位于规则线上方", !numerator.isEmpty());
        Assert.assertTrue("分母字形应位于规则线下方", !denominator.isEmpty());

        int numLeft = minLeft(numerator);
        int numRight = maxRight(numerator);
        int denLeft = minLeft(denominator);
        int denRight = maxRight(denominator);
        int barLeft = rule.left;
        int barRight = rule.right;
        Assert.assertTrue("规则线左端应不超出分子分母最宽范围（+2 容差）", barLeft <= Math.min(numLeft, denLeft) + 2);
        Assert.assertTrue("规则线右端应覆盖分子分母最宽范围（-2 容差）", barRight >= Math.max(numRight, denRight) - 2);
    }

    /** 根号：规则线位于被开方内容上方。 */
    @Test
    public void sqrtRendersRuleAboveRadicand() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\sqrt{x}</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("根号应有规则线", !rules.isEmpty());
        Assert.assertTrue("根号应有字形", !glyphs.isEmpty());
        Quad rule = rules.get(0);
        for (Quad glyph : glyphs) {
            Assert.assertTrue("被开方字形应不高于规则线下沿（+2 容差）",
                    glyph.top >= rule.bottom - 2 || glyph.top < rule.top);
        }
    }

    /** 上标：字形缩小且位于基底右上方。 */
    @Test
    public void superscriptIsShrunkAndRaised() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>x^2</latex>", BASE_SIZE);
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("x^2 应有 2 个字形 quad", glyphs.size() >= 2);
        Quad base = null;
        Quad sup = null;
        for (Quad glyph : glyphs) {
            if (base == null || glyph.height() > base.height()) {
                base = glyph;
            }
        }
        for (Quad glyph : glyphs) {
            if (glyph != base && glyph.top < base.top && glyph.left >= base.left) {
                sup = glyph;
            }
        }
        Assert.assertNotNull("应存在位于基字右上方的上标 quad", sup);
        Assert.assertTrue("上标字号应小于基字（0.7× 缩放）", sup.height() < base.height());
        Assert.assertTrue("上标中心应高于基字顶部", sup.centerY() < base.top);
    }

    /** 渲染确定性：同输入两次渲染像素全等（ink 与 ink 框两模式）。 */
    @Test
    public void rendersDeterministically() {
        String text = "<latex>\\frac{a}{b} + \\sqrt{x^2+1}</latex>";
        for (boolean realGlyphs : new boolean[] {true, false}) {
            LatexSoftwareRenderKit.RenderResult first = LatexSoftwareRenderKit.render(text, BASE_SIZE, realGlyphs);
            LatexSoftwareRenderKit.RenderResult second = LatexSoftwareRenderKit.render(text, BASE_SIZE, realGlyphs);
            Assert.assertEquals("两次渲染宽度应一致", first.width, second.width);
            Assert.assertEquals("两次渲染高度应一致", first.height, second.height);
            Assert.assertTrue("两次渲染像素应全等", Arrays.equals(first.pixels, second.pixels));
        }
    }

    /** 颜色继承：外层 color 标签应作用于公式墨水。 */
    @Test
    public void colorInheritanceTintsFormulaInk() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<color=#FF0000><latex>x^2</latex></color>", BASE_SIZE);
        int redDominant = 0;
        for (int pixel : result.pixels) {
            int r = pixel >> 16 & 255;
            int g = pixel >> 8 & 255;
            int b = pixel & 255;
            if (r > 180 && g < 100 && b < 100) {
                redDominant++;
            }
        }
        Assert.assertTrue("公式墨水应呈现红色主导像素", redDominant > 0);
    }

    /** 全部公式写出 PNG 供目检（build/reports/latex-render/）。 */
    @Test
    public void writesShowcasePngsForInspection() throws Exception {
        File outDir = new File("build/reports/latex-render");
        String[] formulas = LatexShowcaseFormulas.all();
        for (int index = 0; index < formulas.length; index++) {
            File out = new File(outDir, String.format("%02d-formula.png", Integer.valueOf(index)));
            LatexSoftwareRenderKit.renderToPng("<latex>" + formulas[index] + "</latex>", BASE_SIZE, out);
            Assert.assertTrue("PNG 应已写出: " + out, out.isFile() && out.length() > 0);
        }
    }

    /** 墨水行分布诊断：每公式渲染后按行统计墨水像素，写 profiles.txt 供数值化目检。 */
    @Test
    public void writesInkRowProfiles() throws Exception {
        File outDir = new File("build/reports/latex-render");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + outDir);
        }
        StringBuilder report = new StringBuilder();
        String[] formulas = LatexShowcaseFormulas.all();
        for (int index = 0; index < formulas.length; index++) {
            String formula = formulas[index];
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                    "<latex>" + formula + "</latex>", BASE_SIZE, false);
            report.append(String.format("[%02d] %s (advance=%d, %dx%d)%n", Integer.valueOf(index), formula,
                    Integer.valueOf(result.advanceWidth), Integer.valueOf(result.width),
                    Integer.valueOf(result.height)));
            for (int y = 0; y < result.height; y++) {
                int ink = 0;
                for (int x = 0; x < result.width; x++) {
                    if (result.pixels[y * result.width + x] != 0xFF202020) {
                        ink++;
                    }
                }
                if (ink > 0) {
                    StringBuilder bar = new StringBuilder();
                    int ticks = Math.min(60, ink);
                    for (int tick = 0; tick < ticks; tick++) {
                        bar.append('#');
                    }
                    report.append(String.format("  y=%03d ink=%03d %s%n", Integer.valueOf(y),
                            Integer.valueOf(ink), bar.toString()));
                }
            }
            report.append(System.lineSeparator());
        }
        // \sqrt[3]{x} 根指数定位：dump 全部收集 quad（几何验收定位用）
        LatexSoftwareRenderKit.RenderResult root = LatexSoftwareRenderKit.render(
                "<latex>\\sqrt[3]{x}</latex>", BASE_SIZE, false);
        StringBuilder quads = new StringBuilder();
        for (int index = 0; index < root.collector.getActivePageCount(); index++) {
            GlyphRenderBatch batch = root.collector.getActiveBatch(index);
            if (batch == null) {
                continue;
            }
            for (Quad quad : readQuads(batch)) {
                quads.append(String.format("  quad type=%d x=%d..%d y=%d..%d%n",
                        Integer.valueOf(quad.renderType), Integer.valueOf(quad.left),
                        Integer.valueOf(quad.right), Integer.valueOf(quad.top),
                        Integer.valueOf(quad.bottom)));
            }
        }
        for (Quad quad : readQuads(root.collector.getDecorationBatch())) {
            quads.append(String.format("  rule x=%d..%d y=%d..%d%n", Integer.valueOf(quad.left),
                    Integer.valueOf(quad.right), Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
        }
        report.append("== sqrt[3]{x} quads ==%n").append(quads).append(System.lineSeparator());

        java.nio.file.Files.write(new File(outDir, "profiles.txt").toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 盒级几何诊断：\sqrt[3]{x} 的 MathBox 内部数值（布局层验收定位）。 */
    @Test
    public void dumpsSqrtIndexLayoutGeometry() throws Exception {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\sqrt[3]{x}</latex>", BASE_SIZE, false);
        club.heiqi.uilib.font.latex.layout.MathBox box = LatexSoftwareRenderKit.layout("\\sqrt[3]{x}", BASE_SIZE);
        StringBuilder report = new StringBuilder();
        report.append("box width=").append(box.getWidth()).append(" height=").append(box.getHeight())
                .append(" depth=").append(box.getDepth()).append(System.lineSeparator());
        for (club.heiqi.uilib.font.latex.layout.GlyphElem elem : box.getGlyphs()) {
            report.append("glyph text=").append(elem.getText()).append(" x=").append(elem.getX())
                    .append(" y=").append(elem.getY()).append(" scale=").append(elem.getSizeScale())
                    .append(System.lineSeparator());
        }
        for (club.heiqi.uilib.font.latex.layout.RuleElem rule : box.getRules()) {
            report.append("rule x=").append(rule.getX()).append(" y=").append(rule.getY())
                    .append(" w=").append(rule.getWidth()).append(" t=").append(rule.getThickness())
                    .append(System.lineSeparator());
        }
        java.nio.file.Files.write(new java.io.File("build/reports/latex-render/sqrt-index-box.txt").toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** quad 快照（收集侧验收口径）。 */
    static final class Quad {

        final int left;
        final int top;
        final int right;
        final int bottom;
        final int renderType;

        Quad(int left, int top, int right, int bottom, int renderType) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.renderType = renderType;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        int centerX() {
            return (left + right) / 2;
        }

        int centerY() {
            return (top + bottom) / 2;
        }
    }

    static List<Quad> collectQuads(GlyphRenderBatch batch) {
        return readQuads(batch);
    }

    static List<Quad> collectGlyphQuads(LatexSoftwareRenderKit.RenderResult result) {
        List<Quad> glyphs = new ArrayList<Quad>();
        for (int index = 0; index < result.collector.getActivePageCount(); index++) {
            GlyphRenderBatch batch = result.collector.getActiveBatch(index);
            if (batch == null) {
                continue;
            }
            for (Quad quad : readQuads(batch)) {
                if (quad.renderType <= GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH) {
                    glyphs.add(quad);
                }
            }
        }
        return glyphs;
    }

    static List<Quad> readQuads(GlyphRenderBatch batch) {
        List<Quad> quads = new ArrayList<Quad>();
        if (batch == null || batch.isEmpty()) {
            return quads;
        }
        float[] v = batch.copyVertexData();
        int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
        for (int quad = 0; quad < batch.getQuadCount(); quad++) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int vertex = 0; vertex < GlyphRenderBatch.VERTICES_PER_QUAD; vertex++) {
                int offset = (quad * GlyphRenderBatch.VERTICES_PER_QUAD + vertex) * stride;
                int x = Math.round(v[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS]);
                int y = Math.round(v[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
            int renderType = Math.round(v[quad * GlyphRenderBatch.VERTICES_PER_QUAD * stride
                    + GlyphRenderBatch.GLYPH_FLAGS_OFFSET_FLOATS]);
            quads.add(new Quad(minX, minY, maxX, maxY, renderType));
        }
        return quads;
    }

    private static int minLeft(List<Quad> quads) {
        int value = Integer.MAX_VALUE;
        for (Quad quad : quads) {
            value = Math.min(value, quad.left);
        }
        return value;
    }

    private static int maxRight(List<Quad> quads) {
        int value = Integer.MIN_VALUE;
        for (Quad quad : quads) {
            value = Math.max(value, quad.right);
        }
        return value;
    }
}
