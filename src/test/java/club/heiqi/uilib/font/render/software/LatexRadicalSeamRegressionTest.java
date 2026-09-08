package club.heiqi.uilib.font.render.software;

import java.util.ArrayDeque;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.render.GlyphRenderBatch;

/**
 * 根号接头必须连接实心斜笔，quad/ink 包围盒相交或极弱 alpha 接触都不足以证明无缝。
 * 使用生产 adapter 的真实 renderScale，保持布局字号不变；软件侧沿用真实 atlas 双线性采样。
 * shader 的多抽头 AA/smoothstep 不在软件场地内，因此同时守半覆盖和较强覆盖的连通性。
 */
public class LatexRadicalSeamRegressionTest {

    @AfterClass
    public static void releaseTables() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void radicalStrokeJoinsBarAtHighRenderScale() {
        String[] formulas = { "\\sqrt{y}", "\\sqrt{\\sqrt{x}+1}", "\\sqrt[3]{y}",
                "z^{\\sqrt{y}}", "\\frac{1}{\\sqrt{y}}" };
        for (int size : new int[] { 12, 14, 16, 18, 24 }) {
            for (float scale : new float[] { 4.0F, 8.0F }) {
                for (String formula : formulas) {
                    LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(
                            "<latex>" + formula + "</latex>", size, true, scale);
                    GlyphRenderBatch rules = result.collector.getDecorationBatch();
                    float[] vertices = rules.copyVertexData();
                    int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
                    int rootCount = formula.startsWith("\\frac") ? rules.getQuadCount() - 1 : rules.getQuadCount();
                    Assert.assertTrue("必须实际收集到根号横线", rootCount > 0);
                    for (int q = 0; q < rootCount; q++) {
                        int offset = q * GlyphRenderBatch.VERTICES_PER_QUAD * stride;
                        float left = vertices[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
                        float top = vertices[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
                        float bottom = vertices[offset + stride + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
                        for (int threshold : new int[] { 144, 199 }) {
                            Assert.assertTrue("根号斜笔与横线断开: " + formula + " size=" + size
                                    + " renderScale=" + scale + " rule=" + q + " threshold=" + threshold,
                                    joinsStroke(result, left, top, bottom, size * scale, threshold));
                        }
                    }
                }
            }
        }
    }

    /**
     * 从横线接头右侧沿八邻域强墨水走到左下斜笔（斜笔可沿对角连续）；ROI 排除内容及其他横线，
     * 避免远处字符的偶然连通掩盖接缝。阈值是白色在 #202020 上的约 50%/75% 覆盖。
     */
    private static boolean joinsStroke(LatexSoftwareRenderKit.RenderResult r, float left, float top,
            float bottom, float em, int threshold) {
        int minX = Math.max(0, (int) Math.floor(left - em * 0.5F));
        int maxX = Math.min(r.width - 1, (int) Math.ceil(left + em * 0.12F));
        int minY = Math.max(0, (int) Math.floor(top - em * 0.08F));
        int targetY = (int) Math.ceil(bottom + em * 0.15F);
        int maxY = Math.min(r.height - 1, targetY + 2);
        int startX = Math.min(maxX, (int) Math.ceil(left + em * 0.08F));
        int startY = (int) Math.floor((top + bottom) / 2.0F);
        boolean[] seen = new boolean[r.pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(Integer.valueOf(startY * r.width + startX));
        while (!queue.isEmpty()) {
            int index = queue.removeFirst().intValue();
            int x = index % r.width;
            int y = index / r.width;
            if (x < minX || x > maxX || y < minY || y > maxY || seen[index]
                    || (r.pixels[index] & 255) < threshold) {
                continue;
            }
            seen[index] = true;
            if (x < left && y >= targetY) {
                return true;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dy != 0) && x + dx >= minX && x + dx <= maxX
                            && y + dy >= minY && y + dy <= maxY) {
                        queue.add(Integer.valueOf(index + dy * r.width + dx));
                    }
                }
            }
        }
        return false;
    }
}
