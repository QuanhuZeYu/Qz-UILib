package club.heiqi.uilib.font.render.software;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.render.GlyphRenderBatch;

/**
 * 软件光栅器「三角形-属性绑定」钉死断言（headless 出图缺陷的机器证据，不依赖看图）。
 *
 * <p><b>钉死的缺陷</b>：{@code FontSoftwareRasterizer} 把 quad 拆成两个三角形
 * （T1=TL,BL,BR 与 T2=BR,TR,TL）后，片元属性（UV/颜色）必须按<b>本三角形自己的顶点</b>
 * 做重心插值。历史实现 shade() 恒把权重 w0/w1/w2 绑到 quad 顶点槽 0/1/2（TL/BL/BR），
 * 于是 T2（屏幕右上半区）实际插值成 (BR→UV_TL, TR→UV_BL, TL→UV_BR) —— 解析可证即
 * 整个 UV 窗口被 180° 旋转采样（u,v → 1-u,1-v）。指纹：非 180° 旋转对称的字形
 * （e→ǝ、CJK 右上半沿对角错切）必坏；对称字形（0、8、一、=、I）与不吃纹理的
 * decoration quad 恰好正常——与 build/reports 下全部历史出图一致。</p>
 *
 * <p><b>断言结构</b>（防「扫到空报成功」）：
 * 正对照 = 180° 旋转对称纹理用例 + decoration 纯色用例 + 逐字形左下半区（T1）一致率
 * （缺陷存在时这些也通过，证明判据不是恒红）；
 * 反 ∅ 地板 = 显式计数——T2 像素数、逐字形 T2 内 atlas 真墨数、全页墨水像素、
 * 「缺陷绑定预测值 ≠ 正确期望值」的像素数，全部设 ≥N 地板。</p>
 *
 * <p><b>几何选择说明</b>：合成 quad 用 8×6（非正方形）——像素中心恰落在 TL→BR
 * 对角线上的像素会被两个三角形各画一次（二次混合），3(px+0.5)=4(py+0.5) 无整数解，
 * 从根上避开该二义区；真管线用例显式跳过对角邻域像素。</p>
 */
public class FontSoftwareRasterizerSamplingTest {

    private static final int BG = 0xFF000000;
    private static final int CANVAS_W = 8;
    private static final int CANVAS_H = 6;

    @AfterClass
    public static void releaseSharedTables() {
        LatexSoftwareRenderKit.resetShared();
    }

    // ==================== 用例 1：合成 quad，逐像素精确 oracle ====================

    /** 4×4 全异色页纹理 + 覆盖 8×6 画布的 colored-glyph quad：每个像素必须等于正确仿射 UV 的双线性采样值。 */
    @Test
    public void quadAttributeInterpolationBindsEachTriangleToItsOwnVertices() {
        int[] texels = patternTexture(4, false);
        int[] out = rasterizeTexturedQuad(CANVAS_W, CANVAS_H, texels, 4);

        int tri2Pixels = 0;
        int defectDiscriminated = 0;
        for (int py = 0; py < CANVAS_H; py++) {
            for (int px = 0; px < CANVAS_W; px++) {
                double u = (px + 0.5) / CANVAS_W;
                double v = (py + 0.5) / CANVAS_H;
                int expected = blendOver(BG, bilinear(texels, 4, u, v));
                int actual = out[py * CANVAS_W + px];
                Assert.assertTrue("像素(" + px + "," + py + ") 必须等于正确 UV 仿射插值: 期望="
                        + Integer.toHexString(expected) + " 实测=" + Integer.toHexString(actual)
                        + "（右上半区被 180° 旋转采样即此缺陷指纹）", close(expected, actual, 2));
                if (u > v) {
                    tri2Pixels++;
                    // 缺陷绑定（w0→TL,w1→BL,w2→BR 但权重来自 (BR,TR,TL)）解析可证等价于采样 (1-u,1-v)
                    int buggy = blendOver(BG, bilinear(texels, 4, 1.0 - u, 1.0 - v));
                    if (!close(expected, buggy, 4)) {
                        defectDiscriminated++;
                    }
                }
            }
        }
        Assert.assertTrue("T2(右上半区)像素地板: 实测=" + tri2Pixels, tri2Pixels >= 20);
        Assert.assertTrue("判别力地板——缺陷绑定输出必须与正确期望在足够多像素上不同（否则本断言是空跑）: 实测="
                + defectDiscriminated, defectDiscriminated >= 10);
    }

    /** 正对照：180° 旋转对称纹理下，正确期望与缺陷绑定输出重合——判据不恒红，oracle 不过敏。 */
    @Test
    public void rotationSymmetricTextureIsInvisibleToTheDefectAsPositiveControl() {
        int[] texels = patternTexture(4, true);
        int[] out = rasterizeTexturedQuad(CANVAS_W, CANVAS_H, texels, 4);
        int discriminated = 0;
        for (int py = 0; py < CANVAS_H; py++) {
            for (int px = 0; px < CANVAS_W; px++) {
                double u = (px + 0.5) / CANVAS_W;
                double v = (py + 0.5) / CANVAS_H;
                int expected = blendOver(BG, bilinear(texels, 4, u, v));
                Assert.assertTrue("对称纹理像素(" + px + "," + py + ") 必须正确",
                        close(expected, out[py * CANVAS_W + px], 2));
                int buggy = blendOver(BG, bilinear(texels, 4, 1.0 - u, 1.0 - v));
                if (!close(expected, buggy, 4)) {
                    discriminated++;
                }
            }
        }
        Assert.assertTrue("正对照地板——对称纹理下缺陷应完全不可见: 差异像素实测=" + discriminated,
                discriminated == 0);
    }

    /** 正对照：decoration quad（renderType=2，不吃纹理）纯色直出逐位正确。 */
    @Test
    public void decorationQuadIsUnaffectedBySampling() {
        GlyphRenderBatch batch = new GlyphRenderBatch();
        batch.setTextureId(9);
        batch.addRectangleQuad(0, 0, 0, CANVAS_W, CANVAS_H, 0.2F, 0.4F, 0.6F, 1.0F,
                GlyphRenderBatch.RENDER_TYPE_DECORATION);
        SoftwareRenderFrame frame = new SoftwareRenderFrame(CANVAS_W, CANVAS_H, BG);
        frame.addBatch(batch);
        int[] out = FontSoftwareRasterizer.render(frame, null);
        int want = 0xFF336699;
        int hits = 0;
        for (int pixel : out) {
            Assert.assertEquals("decoration 纯色 quad 必须逐位直出", want, pixel);
            hits++;
        }
        Assert.assertTrue("覆盖地板: 实测=" + hits, hits == CANVAS_W * CANVAS_H);
    }

    // ==================== 用例 2：真管线「AEFI」逐字形采样窗口 ====================

    /**
     * 真装配链渲染 "AEFI"（16px）：逐字形 quad 把屏幕右上半区（T2）像素与 atlas 窗口
     * （由该 quad 顶点 texCoord 反推的正确采样位）逐一比对，一致率必须 ≥0.85；
     * 左下半区（T1）同判据作正对照（缺陷存在时 T1 也全对）。I 为 180° 对称正对照字形。
     */
    @Test
    public void realPipelineGlyphQuadsSampleAtlasWindowAtCorrectPositions() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render("AEFI", 16);
        int ink = result.inkPixelCount(0xFF202020);
        // 4 字形 @16px 正确渲染实测 ≈183；空跑为个位数，150 为反 ∅ 地板
        Assert.assertTrue("全页墨水像素地板（反 ∅）: 实测=" + ink, ink >= 150);

        int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
        int glyphsChecked = 0;
        for (int batchIndex = 0; batchIndex < result.collector.getActivePageCount(); batchIndex++) {
            GlyphRenderBatch batch = result.collector.getActiveBatch(batchIndex);
            if (batch == null) {
                continue;
            }
            SoftwarePageTexture page = shared.gl.resolve(batch.getTextureId());
            Assert.assertNotNull("真管线批次必须能解析页纹理（否则本测试是空跑）", page);
            float[] v = batch.copyVertexData();
            for (int quad = 0; quad < batch.getQuadCount(); quad++) {
                int base = quad * 4 * stride;
                float renderType = v[base + GlyphRenderBatch.GLYPH_FLAGS_OFFSET_FLOATS];
                if (renderType > GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH) {
                    continue;
                }
                glyphsChecked++;
                checkOneGlyphQuad(result, page, v, base, stride, glyphsChecked);
            }
        }
        Assert.assertTrue("必须逐字形检查 ≥4 个字形 quad（A/E/F/I），实测=" + glyphsChecked,
                glyphsChecked >= 4);
    }

    private static void checkOneGlyphQuad(LatexSoftwareRenderKit.RenderResult result,
            SoftwarePageTexture page, float[] v, int base, int stride, int glyphNo) {
        double qx = v[base + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
        double qy = v[base + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
        double qw = v[base + 2 * stride + GlyphRenderBatch.POSITION_OFFSET_FLOATS] - qx;
        double qh = v[base + stride + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1] - qy;
        double u0 = v[base + GlyphRenderBatch.UV_OFFSET_FLOATS];
        double v0 = v[base + GlyphRenderBatch.UV_OFFSET_FLOATS + 1];
        double u1 = v[base + 2 * stride + GlyphRenderBatch.UV_OFFSET_FLOATS];
        double v1 = v[base + 2 * stride + GlyphRenderBatch.UV_OFFSET_FLOATS + 1];
        double quadAlpha = v[base + GlyphRenderBatch.COLOR_OFFSET_FLOATS + 3];
        int size = page.getSize();
        int[] px = page.getArgb();
        int tri1 = 0;
        int tri1Ok = 0;
        int tri2 = 0;
        int tri2Ok = 0;
        int tri2Ink = 0;
        double maxGroundA = 0.0;
        for (int y = 0; y < result.height; y++) {
            for (int x = 0; x < result.width; x++) {
                double sx = x + 0.5;
                double sy = y + 0.5;
                if (sx < qx || sx > qx + qw || sy < qy || sy > qy + qh) {
                    continue;
                }
                double cross = qw * (sy - qy) - qh * (sx - qx);
                if (Math.abs(cross) < 1.0e-6 * Math.max(1.0, qw * qh)) {
                    continue; // 对角邻域双三角形重叠区，不判
                }
                double u = u0 + (sx - qx) / qw * (u1 - u0);
                double vv = v0 + (sy - qy) / qh * (v1 - v0);
                double groundA = bilinearAlpha(px, size, u, vv) * quadAlpha;
                maxGroundA = Math.max(maxGroundA, groundA);
                int argb = result.pixels[y * result.width + x];
                double renderedA = ((argb >> 16 & 0xFF) - 0x20) / 223.0;
                boolean groundInk = groundA >= 0.5;
                boolean renderedInk = renderedA >= 0.5;
                boolean upperRight = cross < 0;
                if (upperRight) {
                    tri2++;
                    if (groundInk) {
                        tri2Ink++;
                    }
                    if (groundInk == renderedInk) {
                        tri2Ok++;
                    }
                } else {
                    tri1++;
                    if (groundInk == renderedInk) {
                        tri1Ok++;
                    }
                }
            }
        }
        String diag = " quad=(" + qx + "," + qy + "," + qw + "," + qh + ") uv=(" + u0 + "," + v0
                + ")-(" + u1 + "," + v1 + ") alpha=" + quadAlpha + " tri1=" + tri1 + " tri2=" + tri2
                + " tri2Ink=" + tri2Ink + " maxGroundA=" + maxGroundA;
        Assert.assertTrue("字形#" + glyphNo + " 右上半区像素地板: 实测=" + tri2 + diag, tri2 >= 12);
        Assert.assertTrue("字形#" + glyphNo + " 右上半区 atlas 真墨地板（反 ∅）: 实测=" + tri2Ink + diag,
                tri2Ink >= 4);
        double rate2 = tri2 == 0 ? 1.0 : (double) tri2Ok / tri2;
        double rate1 = tri1 == 0 ? 1.0 : (double) tri1Ok / tri1;
        Assert.assertTrue("字形#" + glyphNo + " 右上半区采样一致率必须 ≥0.85（右上半区=被缺陷 180° 旋转的区域）: 实测="
                + rate2 + " (" + tri2Ok + "/" + tri2 + ")" + diag, rate2 >= 0.85);
        Assert.assertTrue("字形#" + glyphNo + " 左下半区一致率正对照 ≥0.85: 实测=" + rate1, rate1 >= 0.85);
    }

    // ==================== 独立 oracle（不复用被测代码） ====================

    private static int[] rasterizeTexturedQuad(int canvasW, int canvasH, int[] texels, int texSize) {
        GlyphRenderBatch batch = new GlyphRenderBatch();
        batch.setTextureId(7);
        batch.addQuad(0, 0, 0, canvasW, canvasH, false, 0.0F, 1.0F, 0.0F, 1.0F,
                0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
                GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH);
        SoftwareRenderFrame frame = new SoftwareRenderFrame(canvasW, canvasH, BG);
        frame.addBatch(batch);
        final int[] capturedTexels = texels;
        final int capturedSize = texSize;
        GlyphTextureSource source = new GlyphTextureSource() {
            @Override
            public SoftwarePageTexture resolve(int textureId) {
                return new SoftwarePageTexture(capturedSize, capturedTexels.clone());
            }
        };
        return FontSoftwareRasterizer.render(frame, source);
    }

    /** 4×4 纹理；symmetric=true 时按 c(tx,ty)==c(3-tx,3-ty) 构造 180° 旋转对称图案。 */
    private static int[] patternTexture(int size, boolean symmetric) {
        int[] texels = new int[size * size];
        for (int ty = 0; ty < size; ty++) {
            for (int tx = 0; tx < size; tx++) {
                int mx = tx;
                int my = ty;
                int ox = size - 1 - tx;
                int oy = size - 1 - ty;
                // 每对 180° 镜像点取 (y,x) 字典序小者为代表 → 严格 c(t)==c(3-t)
                if (symmetric && (oy < my || (oy == my && ox < mx))) {
                    mx = ox;
                    my = oy;
                }
                int r = 40 + mx * 60;
                int g = 40 + my * 60;
                int b = 40 + (mx * 2 + my) * 37 % 200;
                texels[ty * size + tx] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return texels;
    }

    /** GL texel-center 双线性（与真机 MAG=LINEAR 同约定）；越界取透明黑（=CLAMP_TO_BORDER 默认边界色）。 */
    private static int bilinear(int[] texels, int size, double u, double v) {
        double fu = u * size - 0.5;
        double fv = v * size - 0.5;
        int x0 = (int) Math.floor(fu);
        int y0 = (int) Math.floor(fv);
        double fx = fu - x0;
        double fy = fv - y0;
        double a = channel(texels, size, x0, y0, 24) * (1 - fx) * (1 - fy)
                + channel(texels, size, x0 + 1, y0, 24) * fx * (1 - fy)
                + channel(texels, size, x0, y0 + 1, 24) * (1 - fx) * fy
                + channel(texels, size, x0 + 1, y0 + 1, 24) * fx * fy;
        double r = channel(texels, size, x0, y0, 16) * (1 - fx) * (1 - fy)
                + channel(texels, size, x0 + 1, y0, 16) * fx * (1 - fy)
                + channel(texels, size, x0, y0 + 1, 16) * (1 - fx) * fy
                + channel(texels, size, x0 + 1, y0 + 1, 16) * fx * fy;
        double g = channel(texels, size, x0, y0, 8) * (1 - fx) * (1 - fy)
                + channel(texels, size, x0 + 1, y0, 8) * fx * (1 - fy)
                + channel(texels, size, x0, y0 + 1, 8) * (1 - fx) * fy
                + channel(texels, size, x0 + 1, y0 + 1, 8) * fx * fy;
        double b = channel(texels, size, x0, y0, 0) * (1 - fx) * (1 - fy)
                + channel(texels, size, x0 + 1, y0, 0) * fx * (1 - fy)
                + channel(texels, size, x0, y0 + 1, 0) * (1 - fx) * fy
                + channel(texels, size, x0 + 1, y0 + 1, 0) * fx * fy;
        return clamp255(a) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
    }

    private static double bilinearAlpha(int[] texels, int size, double u, double v) {
        return (bilinear(texels, size, u, v) >>> 24) / 255.0;
    }

    private static double channel(int[] texels, int size, int x, int y, int shift) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return 0.0;
        }
        return texels[y * size + x] >> shift & 0xFF;
    }

    /** straight-alpha src-over 到不透明底（与光栅器 blend 同式）。 */
    private static int blendOver(int background, int src) {
        int sa = src >>> 24;
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return background;
        }
        int invA = 255 - sa;
        int r = ((src >> 16 & 255) * sa + (background >> 16 & 255) * invA + 127) / 255;
        int g = ((src >> 8 & 255) * sa + (background >> 8 & 255) * invA + 127) / 255;
        int b = ((src & 255) * sa + (background & 255) * invA + 127) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int clamp255(double value) {
        if (value <= 0.0) {
            return 0;
        }
        return value >= 255.0 ? 255 : (int) Math.round(value);
    }

    private static boolean close(int a, int b, int tolerance) {
        return Math.abs((a >> 24) - (b >> 24)) <= tolerance
                && Math.abs((a >> 16 & 255) - (b >> 16 & 255)) <= tolerance
                && Math.abs((a >> 8 & 255) - (b >> 8 & 255)) <= tolerance
                && Math.abs((a & 255) - (b & 255)) <= tolerance;
    }
}
