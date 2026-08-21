package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
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

    /** 混排行规则线锚定公式段起点：文本在前时横线不得左飞回行首（「只有第一个卡片正常」回归）。 */
    @Test
    public void fracBarAnchoredToLatexSegmentInMixedLine() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "分数：<latex>\\frac{1}{2}</latex> 尾部文本", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("混排分数应有规则线", !rules.isEmpty());
        Quad rule = rules.get(0);
        // 分子 = 规则线上方且紧邻的 0.7× 字形
        Quad numerator = null;
        for (Quad glyph : glyphs) {
            if (glyph.bottom <= rule.top + 1) {
                numerator = glyph;
                break;
            }
        }
        Assert.assertNotNull("应有分子字形", numerator);
        // 布局中横线左端与分子左端同 x（sideSpace 对齐）；渲染侧段起点偏移后两者应保持重合
        int delta = rule.left - numerator.left;
        Assert.assertTrue("横线左端应锚定公式段（与分子同 x），实测偏移=" + delta + "px",
                Math.abs(delta) <= 2);
    }

    /** 根号横线左端锚定 √ 勾的 ink 右缘（回归：此前漏 bearingX，横线吃进勾内约 2px）。 */
    @Test
    public void sqrtBarAnchoredToRadicalInkRightEdge() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\sqrt{x}</latex>", BASE_SIZE);
        List<Quad> glyphs = collectGlyphQuads(result);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        Assert.assertTrue("根号应有规则线与字形", !rules.isEmpty() && !glyphs.isEmpty());
        Quad radical = glyphs.get(0); // 布局顺序：根号字形最先
        Quad rule = rules.get(0);
        int delta = rule.left - radical.right;
        Assert.assertTrue("横线左端应与 √ ink 右缘重合（实测差=" + delta + "px）",
                Math.abs(delta) <= 1);
    }

    /** 普通定界符 ink 中心钉数学轴（回归：基线裸放导致真机括号偏上）。 */
    @Test
    public void plainDelimitersCenteredOnMathAxis() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>(x)</latex>", BASE_SIZE);
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有括号与变量字形", glyphs.size() >= 3);
        Quad open = glyphs.get(0);
        Quad x = glyphs.get(1);
        int delta = open.centerY() - x.centerY();
        Assert.assertTrue("括号 ink 中心应与 x ink 中心重合于数学轴（实测差=" + delta + "px）",
                Math.abs(delta) <= 1);
    }

    /** 大运算符（∑∫）ink 中心锚定数学轴（回归：inkCenterOffsetY 符号口径漂移导致 ±8px 偏移）。 */
    @Test
    public void bigOperatorAxisCenteredByInk() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\frac{1}{2} + \\sum_{i=1}^{n} i</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有分数规则线", !rules.isEmpty());
        Quad bar = rules.get(0);
        int axis = bar.centerY();
        Quad big = null;
        for (Quad glyph : glyphs) {
            if (glyph.height() >= 12 && glyph.left >= bar.right) {
                big = glyph;
                break;
            }
        }
        Assert.assertNotNull("应找到 ∑ 字形 quad", big);
        int delta = big.centerY() - axis;
        Assert.assertTrue("∑ ink 中心应落在数学轴（分数线上）±2px，实测差=" + delta,
                Math.abs(delta) <= 2);
    }

    /** 契约回归：inkCenterOffsetY 实现口径必须与渲染 quad bearingY 一致（y 向下）。 */
    @Test
    public void inkCenterOffsetYMatchesQuadBearingContract() {
        LatexSoftwareRenderKit.RenderResult r = LatexSoftwareRenderKit.render(
                "<latex>(x)|y|\\sum_{i=1}^{n}\\int_0^1\\sqrt{x}\\{z\\}</latex>", BASE_SIZE);
        club.heiqi.uilib.font.layout.TextStyle style = new club.heiqi.uilib.font.layout.TextStyle();
        style.resetAll(0xFFFFFFFF);
        MathMetrics m = LatexSoftwareRenderKit.currentService().createMathMetrics(style, BASE_SIZE);
        short[] bearingY = r.tables.bearingYArray(FontType.NORMAL);
        short[] inkH = r.tables.inkHeightArray(FontType.NORMAL);
        int awt = (int) LatexSoftwareRenderKit.currentAwtCharSize();
        int checked = 0;
        for (int cp : new int[] { '(', ')', '{', '|', 0x2211, 0x222B, 0x221A }) {
            if (inkH[cp] <= 0) {
                continue; // 字形未装配（无 ink 数据）不参与契约
            }
            String text = new String(Character.toChars(cp));
            float expected = (bearingY[cp] + inkH[cp] / 2.0F) * BASE_SIZE / (float) awt;
            float actual = m.inkCenterOffsetY(text, BASE_SIZE);
            Assert.assertEquals("inkCenterOffsetY 应与 quad bearingY 同口径（y 向下）: " + text,
                    expected, actual, 0.5F);
            checked++;
        }
        Assert.assertTrue("至少应契约校验 3 个字形", checked >= 3);
    }

    /** 重音 skew 视觉对齐：^ 的 ink 中心落在斜体 x 的 ink 中心上方（±2px）。 */
    @Test
    public void hatSkewAlignsWithItalicBaseCenter() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\hat{x}</latex>", BASE_SIZE);
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有基底与重音字形", glyphs.size() >= 2);
        Quad base = glyphs.get(0); // 布局顺序：基底 x 在前、重音 ^ 在后
        Quad accent = glyphs.get(1);
        int delta = accent.centerX() - base.centerX();
        Assert.assertTrue("重音 ink 中心应落在斜体基底 ink 中心上方（实测差=" + delta + "px）",
                Math.abs(delta) <= 2);
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

    /** 横线位置约束：规则线 quad 顶点 y 落在整像素行、厚度 ≥1px（光栅取整漂移防护）。 */
    @Test
    public void rulesArePinnedToIntegerPixelRows() {
        String[] formulas = { "\\frac{1}{2}", "\\sqrt{x}", "\\sqrt{x^2+y^2}", "\\frac{a}{b}+\\frac{c}{d}",
                "\\overline{AB}", "\\underline{x}" };
        for (String formula : formulas) {
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                    "<latex>" + formula + "</latex>", BASE_SIZE);
            GlyphRenderBatch batch = result.collector.getDecorationBatch();
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            float[] vertices = batch.copyVertexData();
            int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
            for (int quad = 0; quad < batch.getQuadCount(); quad++) {
                float top = Float.MAX_VALUE;
                float bottom = -Float.MAX_VALUE;
                for (int vertex = 0; vertex < GlyphRenderBatch.VERTICES_PER_QUAD; vertex++) {
                    int offset = (quad * GlyphRenderBatch.VERTICES_PER_QUAD + vertex) * stride;
                    float y = vertices[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
                Assert.assertEquals("规则线顶应落在整像素行: " + formula, Math.round(top), top, 0.001F);
                Assert.assertTrue("规则线厚度应 ≥1px: " + formula, bottom - top >= 1.0F);
            }
        }
    }

    /** 伸缩括号按 ink 中心对齐数学轴：括号视觉中心应落在分数横线（= 轴）上。 */
    @Test
    public void fencesAreAxisCenteredByInk() throws Exception {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\left( \\frac{a}{b} \\right)</latex>", BASE_SIZE);
        club.heiqi.uilib.font.latex.layout.MathBox box = LatexSoftwareRenderKit.layout(
                "\\left( \\frac{a}{b} \\right)", BASE_SIZE);
        StringBuilder report = new StringBuilder();
        for (club.heiqi.uilib.font.latex.layout.GlyphElem g : box.getGlyphs()) {
            report.append("box glyph ").append(g.getText()).append(" x=").append(g.getX())
                    .append(" y=").append(g.getY()).append(" s=").append(g.getSizeScale())
                    .append(System.lineSeparator());
        }
        for (club.heiqi.uilib.font.latex.layout.RuleElem rule : box.getRules()) {
            report.append("box rule x=").append(rule.getX()).append(" y=").append(rule.getY())
                    .append(" w=").append(rule.getWidth()).append(System.lineSeparator());
        }
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        report.append("render quads:").append(System.lineSeparator());
        for (Quad quad : glyphs) {
            report.append("  quad y=").append(quad.top).append("..").append(quad.bottom)
                    .append(" h=").append(quad.height()).append(System.lineSeparator());
        }
        for (Quad rule : rules) {
            report.append("  rule y=").append(rule.top).append("..").append(rule.bottom)
                    .append(System.lineSeparator());
        }
        java.nio.file.Files.write(new java.io.File("build/reports/latex-render/fence-dump.txt").toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Assert.assertTrue("应有分数横线", !rules.isEmpty());
        Quad bar = rules.get(0);
        int axisY = bar.centerY();
        int fenceCount = 0;
        for (Quad quad : glyphs) {
            if (quad.height() > 12) {
                fenceCount++;
                Assert.assertTrue("伸缩括号中心应落在数学轴（±2 容差）: fenceCenter=" + quad.centerY()
                        + " axis=" + axisY, Math.abs(quad.centerY() - axisY) <= 2);
            }
        }
        Assert.assertEquals("应有 2 个伸缩括号", 2, fenceCount);
    }

    /** 横线端点对齐视觉 ink：分数横线右端覆盖斜体字形的 ink 右缘（左右不精准修复）。 */
    @Test
    public void fracBarCoversItalicInkEdge() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\frac{a}{b}</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("分数应有规则线", !rules.isEmpty());
        Assert.assertTrue("分数应有分子分母字形", glyphs.size() >= 2);
        Quad bar = rules.get(0);
        int inkRight = maxRight(glyphs);
        Assert.assertTrue("横线右端应覆盖斜体 ink 右缘（+1 容差）: barRight=" + bar.right + " inkRight="
                + inkRight, bar.right >= inkRight - 1);
        int inkLeft = minLeft(glyphs);
        Assert.assertTrue("横线左端应不超出内容左缘（+1 容差）", bar.left <= inkLeft + 1);
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

    /** 混排行几何诊断：14px 卡片行（文本 + 行内公式）逐 quad dump y 范围，
     * 供核对文本基线/公式基线/行高是否对齐（场地卡片「下面有问题」定位用）。 */
    @Test
    public void dumpsMixedLineGeometry() throws Exception {
        String[] lines = {
                "分数：<latex>\\frac{1}{2} + \\frac{a}{b}</latex>",
                "平方根：<latex>\\sqrt{x} + \\sqrt{x^2+y^2}</latex>",
                "矩阵：<latex>\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}</latex>",
                "分段：<latex>f(x) = \\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}</latex>",
                "伸缩括号：<latex>\\left( \\frac{a}{b} \\right) \\left[ x \\right]</latex>",
                "组合数：<latex>\\binom{n}{k} = \\frac{n!}{k!(n-k)!}</latex>",
                "重音：<latex>\\hat{x} + \\bar{y} + \\vec{v} + \\dot{z} + \\tilde{w}</latex>",
                "上划线：<latex>\\overline{AB} + \\underline{x}</latex>",
                "中文：<latex>\\text{速度} = \\frac{\\Delta s}{\\Delta t}</latex>",
        };
        java.io.File out = new java.io.File("build/reports/latex-render/mixed-line.txt");
        StringBuilder report = new StringBuilder();
        for (String line : lines) {
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(line, 14);
            report.append("== ").append(line).append(" (").append(result.width).append("x")
                    .append(result.height).append(") ==\n");
            List<Quad> glyphs = collectGlyphQuads(result);
            for (Quad quad : glyphs) {
                report.append(String.format(java.util.Locale.ROOT, "  glyph y=%d..%d x=%d..%d%n",
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom), Integer.valueOf(quad.left),
                        Integer.valueOf(quad.right)));
            }
            for (Quad quad : collectQuads(result.collector.getDecorationBatch())) {
                report.append(String.format(java.util.Locale.ROOT, "  rule  y=%d..%d x=%d..%d%n",
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom), Integer.valueOf(quad.left),
                        Integer.valueOf(quad.right)));
            }
        }
        java.nio.file.Files.write(out.toPath(), report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 诊断：根号横线左端 vs radical ink 右缘、定界符 ink 表与各类括号 quad 几何（真机目检辅助）。 */
    @Test
    public void dumpsSqrtBarAndDelimiterDiagnostics() throws Exception {
        StringBuilder report = new StringBuilder();
        double awt = LatexSoftwareRenderKit.currentAwtCharSize();
        float scale = (float) (BASE_SIZE / awt);
        report.append("awtCharSize=").append(awt).append(" renderScale=").append(scale)
                .append(System.lineSeparator());

        String[] sqrtForms = { "\\sqrt{x}", "\\sqrt{x^2+y^2}" };
        for (String formula : sqrtForms) {
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                    "<latex>" + formula + "</latex>", BASE_SIZE);
            report.append("== ").append(formula).append(" ==").append(System.lineSeparator());
            for (Quad quad : collectGlyphQuads(result)) {
                report.append(String.format(java.util.Locale.ROOT, "  glyph x=%d..%d y=%d..%d%n",
                        Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
            }
            for (Quad quad : collectQuads(result.collector.getDecorationBatch())) {
                report.append(String.format(java.util.Locale.ROOT, "  rule  x=%d..%d y=%d..%d%n",
                        Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
            }
        }

        LatexSoftwareRenderKit.RenderResult sample = LatexSoftwareRenderKit.render(
                "<latex>(x)</latex>", BASE_SIZE);
        GlyphRuntimeTables tables = sample.tables;
        short[] bearingX = tables.bearingXArray(FontType.NORMAL);
        short[] bearingY = tables.bearingYArray(FontType.NORMAL);
        short[] inkW = tables.inkWidthArray(FontType.NORMAL);
        short[] inkH = tables.inkHeightArray(FontType.NORMAL);
        int[] delimiters = { '(', ')', '[', ']', '{', '}', '|', 0x221A, 0x27E8, 0x27E9,
                0x2308, 0x2309, 0x230A, 0x230B };
        report.append("== ink tables (awt 口径) ==").append(System.lineSeparator());
        for (int cp : delimiters) {
            String glyph = new String(Character.toChars(cp));
            report.append(String.format(java.util.Locale.ROOT,
                    "  U+%04X %s bearingX=%d bearingY=%d inkW=%d inkH=%d inkCenterUp=%d%n",
                    Integer.valueOf(cp), glyph,
                    Integer.valueOf(bearingX[cp]), Integer.valueOf(bearingY[cp]),
                    Integer.valueOf(inkW[cp]), Integer.valueOf(inkH[cp]),
                    Integer.valueOf(bearingY[cp] + inkH[cp] / 2)));
        }

        String[] fenceForms = { "(x+1)", "\\left(\\frac{1}{2}\\right)", "\\left[x\\right]",
                "\\binom{n}{k}", "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
                "\\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}",
                "|x|", "\\langle x \\rangle" };
        for (String formula : fenceForms) {
            report.append("== ").append(formula).append(" ==").append(System.lineSeparator());
            LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                    "<latex>" + formula + "</latex>", BASE_SIZE);
            for (Quad quad : collectGlyphQuads(result)) {
                report.append(String.format(java.util.Locale.ROOT, "  glyph x=%d..%d y=%d..%d%n",
                        Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
            }
            for (Quad quad : collectQuads(result.collector.getDecorationBatch())) {
                report.append(String.format(java.util.Locale.ROOT, "  rule  x=%d..%d y=%d..%d%n",
                        Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                        Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
            }
        }
        java.io.File out = new java.io.File("build/reports/latex-render/diag-sqrt-delimiter.txt");
        java.nio.file.Files.write(out.toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 诊断：cases 花括号 ink 几何（真机「分段花括号偏上」定位）。 */
    @Test
    public void dumpsCasesBraceGeometry() throws Exception {
        StringBuilder report = new StringBuilder();
        String formula = "\\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}";
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>" + formula + "</latex>", BASE_SIZE);
        GlyphRuntimeTables tables = result.tables;
        double awt = LatexSoftwareRenderKit.currentAwtCharSize();
        report.append("awtCharSize=").append(awt).append(System.lineSeparator());
        short[] bearingX = tables.bearingXArray(FontType.NORMAL);
        short[] bearingY = tables.bearingYArray(FontType.NORMAL);
        short[] inkW = tables.inkWidthArray(FontType.NORMAL);
        short[] inkH = tables.inkHeightArray(FontType.NORMAL);
        for (int cp : new int[] { '{', '(', 'x', '-' }) {
            String glyph = new String(Character.toChars(cp));
            report.append(String.format(java.util.Locale.ROOT,
                    "  U+%04X %s bearingX=%d bearingY=%d inkW=%d inkH=%d inkCenterUp=%d%n",
                    Integer.valueOf(cp), glyph,
                    Integer.valueOf(bearingX[cp]), Integer.valueOf(bearingY[cp]),
                    Integer.valueOf(inkW[cp]), Integer.valueOf(inkH[cp]),
                    Integer.valueOf(bearingY[cp] + inkH[cp] / 2)));
        }
        report.append("== glyph quads ==").append(System.lineSeparator());
        for (Quad quad : collectGlyphQuads(result)) {
            report.append(String.format(java.util.Locale.ROOT, "  glyph x=%d..%d y=%d..%d%n",
                    Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                    Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
        }
        for (Quad quad : collectQuads(result.collector.getDecorationBatch())) {
            report.append(String.format(java.util.Locale.ROOT, "  rule  x=%d..%d y=%d..%d%n",
                    Integer.valueOf(quad.left), Integer.valueOf(quad.right),
                    Integer.valueOf(quad.top), Integer.valueOf(quad.bottom)));
        }
        // 布局盒 dump：花括号 glyph 的 scale 与 y
        club.heiqi.uilib.font.latex.layout.MathBox box = LatexSoftwareRenderKit.layout(formula, BASE_SIZE);
        report.append("== layout box ==").append(System.lineSeparator());
        report.append("box w=").append(box.getWidth()).append(" h=").append(box.getHeight())
                .append(" d=").append(box.getDepth()).append(System.lineSeparator());
        for (club.heiqi.uilib.font.latex.layout.GlyphElem elem : box.getGlyphs()) {
            report.append(String.format(java.util.Locale.ROOT, "  glyph %s x=%s y=%s scale=%s%n",
                    elem.getText(), Float.valueOf(elem.getX()), Float.valueOf(elem.getY()),
                    Float.valueOf(elem.getSizeScale())));
        }
        java.io.File out = new java.io.File("build/reports/latex-render/diag-cases-brace.txt");
        java.nio.file.Files.write(out.toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 诊断：渲染 cases/pmatrix/left-right 对照 PNG（视觉验收）。 */
    @Test
    public void rendersCasesBraceComparisonPngs() throws Exception {
        java.io.File dir = new java.io.File("build/reports/latex-render");
        dir.mkdirs();
        String[][] forms = {
                { "cases", "\\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}" },
                { "pmatrix", "\\begin{pmatrix} x & y \\\\ -x & z \\end{pmatrix}" },
                { "leftright", "\\left\\{ \\frac{a}{b} \\right\\}" },
                { "binombrace", "\\left\\{ x \\right\\} \\quad (x) \\quad \\left( x \\right)" },
        };
        for (String[] pair : forms) {
            LatexSoftwareRenderKit.renderToPng("<latex>" + pair[1] + "</latex>", 16,
                    new java.io.File(dir, "brace-" + pair[0] + ".png"));
        }
    }

    /** 诊断：cases 像素级花括号/内容 ink 中心对比。 */
    @Test
    public void analyzesCasesBracePixelCenters() throws Exception {
        StringBuilder report = new StringBuilder();
        Object[][] forms = {
                { "cases", "\\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}", Integer.valueOf(19) },
                { "pmatrix", "\\begin{pmatrix} x & y \\\\ -x & z \\end{pmatrix}", Integer.valueOf(18) },
                { "leftright", "\\left\\{ \\frac{a}{b} \\right\\}", Integer.valueOf(12) },
        };
        for (Object[] spec : forms) {
            LatexSoftwareRenderKit.RenderResult r = LatexSoftwareRenderKit.render(
                    "<latex>" + spec[1] + "</latex>", BASE_SIZE);
            int split = ((Integer) spec[2]).intValue();
            int braceTop = Integer.MAX_VALUE, braceBottom = Integer.MIN_VALUE;
            int contentTop = Integer.MAX_VALUE, contentBottom = Integer.MIN_VALUE;
            for (int y = 0; y < r.height; y++) {
                for (int x = 0; x < r.width; x++) {
                    if (r.pixels[y * r.width + x] == 0xFF202020) {
                        continue;
                    }
                    if (x <= split) {
                        braceTop = Math.min(braceTop, y);
                        braceBottom = Math.max(braceBottom, y);
                    } else {
                        contentTop = Math.min(contentTop, y);
                        contentBottom = Math.max(contentBottom, y);
                    }
                }
            }
            report.append(String.valueOf(spec[0]))
                    .append(": brace y=").append(braceTop).append("..").append(braceBottom)
                    .append(" center=").append((braceTop + braceBottom) / 2)
                    .append(" | content y=").append(contentTop).append("..").append(contentBottom)
                    .append(" center=").append((contentTop + contentBottom) / 2)
                    .append(" | diff=").append((braceTop + braceBottom - contentTop - contentBottom) / 2)
                    .append(System.lineSeparator());
        }
        java.io.File out = new java.io.File("build/reports/latex-render/diag-cases-pixels.txt");
        java.nio.file.Files.write(out.toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 裸大运算符（无上下标）也轴居中（回归：此前只在 limits 路径轴居中，裸 \sum 按基线裸放）。 */
    @Test
    public void bareSumCentersOnAxis() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\frac{1}{2}+\\sum</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有分数规则线", !rules.isEmpty());
        Quad bar = rules.get(0);
        Quad big = null;
        for (Quad glyph : glyphs) {
            if (glyph.height() >= 12 && (big == null || glyph.height() > big.height())) {
                big = glyph;
            }
        }
        Assert.assertNotNull("应找到裸 ∑ 字形 quad", big);
        int delta = big.centerY() - bar.centerY();
        Assert.assertTrue("裸 ∑ ink 中心应落在数学轴（分数线上）±2px，实测差=" + delta,
                Math.abs(delta) <= 2);
    }

    /** \nolimits 侧挂：符号仍轴居中（脚本右下），回归符号轴位置。 */
    @Test
    public void nolimitsSumKeepsAxisCenter() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\frac{1}{2}+\\sum\\nolimits_{i=1}^n</latex>", BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有规则线与字形", !rules.isEmpty() && glyphs.size() >= 3);
        Quad bar = rules.get(0);
        Quad big = null;
        for (Quad glyph : glyphs) {
            if (glyph.height() >= 10 && glyph.left >= bar.right
                    && (big == null || glyph.height() > big.height())) {
                big = glyph;
            }
        }
        Assert.assertNotNull("应找到 ∑ 字形 quad", big);
        int delta = big.centerY() - bar.centerY();
        Assert.assertTrue("\nolimits 的 ∑ ink 中心仍应在数学轴 ±2px，实测差=" + delta,
                Math.abs(delta) <= 2);
    }

    /** \middle 中间定界符与两侧同 minHeight 且轴居中。 */
    @Test
    public void middleDelimiterCentersOnAxis() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\left\\{\\frac{a}{b}\\middle|\\frac{c}{d}\\right\\}</latex>",
                BASE_SIZE);
        List<Quad> rules = collectQuads(result.collector.getDecorationBatch());
        List<Quad> glyphs = collectGlyphQuads(result);
        Assert.assertTrue("应有规则线与字形", !rules.isEmpty() && !glyphs.isEmpty());
        Quad bar = rules.get(0);
        int fenceCount = 0;
        for (Quad glyph : glyphs) {
            if (glyph.height() > 12) {
                fenceCount++;
                Assert.assertTrue("伸缩定界符（含 \\middle）中心应落在数学轴 ±2px: center="
                        + glyph.centerY() + " axis=" + bar.centerY(),
                        Math.abs(glyph.centerY() - bar.centerY()) <= 2);
            }
        }
        Assert.assertEquals("应有 3 个伸缩定界符（左右 + 中间）", 3, fenceCount);
    }

    /** cases 两列左对齐渲染 smoke（布局级精确断言见 MathLayoutServiceTest）。 */
    @Test
    public void casesColumnsRenderLeftAligned() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\begin{cases}x&x>0\\\\-x&x\\leq0\\end{cases}</latex>", BASE_SIZE);
        Assert.assertTrue("cases 应有墨水像素", result.inkPixelCount(0xFF202020) > 0);
        Assert.assertTrue("cases 应有收集 quad", result.collector.getQuadCount() > 0);
    }

    /** array 列说明渲染 smoke。 */
    @Test
    public void arrayColumnAlignsRender() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                "<latex>\\begin{array}{lr}a&bb\\\\cc&d\\end{array}</latex>", BASE_SIZE);
        Assert.assertTrue("array 应有墨水像素", result.inkPixelCount(0xFF202020) > 0);
        Assert.assertTrue("array 应有收集 quad", result.collector.getQuadCount() > 0);
    }

    /** 诊断：AWT 字形视觉边界 vs 生成器 ink 表口径对比（回退锚定修复前提验证）。 */
    @Test
    public void comparesAwtVisualBoundsWithInkTables() throws Exception {
        StringBuilder report = new StringBuilder();
        java.awt.Font base = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 14);
        float awt = (float) LatexSoftwareRenderKit.currentAwtCharSize();
        java.awt.Font font64 = base.deriveFont(awt);
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(
                font64.getTransform(), true, false);
        int[] cps = { '(', ')', '{', '|', 0x221A };
        LatexSoftwareRenderKit.RenderResult r = LatexSoftwareRenderKit.render(
                "<latex>(x)\\{y\\}\\sqrt{x}|z|</latex>", BASE_SIZE);
        short[] bearingX = r.tables.bearingXArray(FontType.NORMAL);
        short[] bearingY = r.tables.bearingYArray(FontType.NORMAL);
        short[] inkW = r.tables.inkWidthArray(FontType.NORMAL);
        short[] inkH = r.tables.inkHeightArray(FontType.NORMAL);
        for (int cp : cps) {
            String glyph = new String(Character.toChars(cp));
            java.awt.font.GlyphVector gv = font64.createGlyphVector(frc, new int[] { cp });
            java.awt.geom.Rectangle2D b = gv.getVisualBounds();
            report.append(String.format(java.util.Locale.ROOT,
                    "%s table: bx=%d by=%d w=%d h=%d centerUp=%d | awt: x=%.2f y=%.2f w=%.2f h=%.2f centerUp=%.2f%n",
                    glyph,
                    Integer.valueOf(bearingX[cp]), Integer.valueOf(bearingY[cp]),
                    Integer.valueOf(inkW[cp]), Integer.valueOf(inkH[cp]),
                    Integer.valueOf(bearingY[cp] + inkH[cp] / 2),
                    Double.valueOf(b.getX()), Double.valueOf(b.getY()),
                    Double.valueOf(b.getWidth()), Double.valueOf(b.getHeight()),
                    Double.valueOf(b.getCenterY())));
        }
        java.io.File out = new java.io.File("build/reports/latex-render/diag-awt-vs-table.txt");
        java.nio.file.Files.write(out.toPath(),
                report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
