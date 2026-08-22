package club.heiqi.uilib.font.render.software;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import club.heiqi.uilib.font.render.GlyphRenderBatch;

/**
 * 字体指令流软件光栅化器（纯 JVM，headless 渲染验收场地）。
 *
 * <p>消费 {@link SoftwareRenderFrame}（与真机批渲染器同源的收集侧指令流快照），
 * 按与 {@code shader/fontF.frag} 一致的语义光栅化到 CPU 像素缓冲：</p>
 * <ul>
 *   <li>decoration（renderType 2）：纯色 quad（{@code Color} 直出）；</li>
 *   <li>colored glyph（renderType 1）：RGB 取纹理采样，alpha = tex.a × quad.a；</li>
 *   <li>mono glyph（renderType 0）：RGB 取 quad 颜色，alpha = tex.a × quad.a；</li>
 *   <li>采样越出 uvBounds（slot clip 边界）的片元丢弃（shader {@code safeSample} 硬墙语义）；</li>
 *   <li>纹理放大为双线性采样（与真机 {@code GL_TEXTURE_MAG_FILTER=GL_LINEAR} 对齐）；</li>
 *   <li>straight-alpha src-over 混合。</li>
 * </ul>
 *
 * <p>字形无纹理源时回退「ink 框模式」：按 quad 几何与颜色半透明填充，仅用于布局/几何验收。
 * 多抽头 AA 与 smoothstep 属真机 shader 的抗锯齿近似，软件侧默认不做（保证跨机器确定性）。</p>
 */
public final class FontSoftwareRasterizer {

    private static final float INK_BOX_ALPHA = 0.85F;
    private static final float NO_TEXTURE_COLORED_R = 1.0F;
    private static final float NO_TEXTURE_COLORED_G = 0.45F;
    private static final float NO_TEXTURE_COLORED_B = 0.85F;

    private FontSoftwareRasterizer() {}

    /**
     * 光栅化帧（无纹理源，字形走 ink 框模式）。
     *
     * @param frame 渲染帧
     * @return 逐行 ARGB 像素缓冲（长度 = width×height）
     */
    public static int[] render(SoftwareRenderFrame frame) {
        return render(frame, null);
    }

    /**
     * 光栅化帧。
     *
     * @param frame    渲染帧
     * @param textures 字符页纹理像素来源；null 表示字形走 ink 框模式
     * @return 逐行 ARGB 像素缓冲（长度 = width×height）
     */
    public static int[] render(SoftwareRenderFrame frame, GlyphTextureSource textures) {
        if (frame == null) {
            throw new IllegalArgumentException("frame 不得为 null");
        }
        int width = frame.getWidth();
        int height = frame.getHeight();
        int background = frame.getBackgroundArgb() | 0xFF000000;
        int[] buffer = new int[width * height];
        java.util.Arrays.fill(buffer, background);
        for (SoftwareRenderFrame.BatchSnapshot batch : frame.getBatches()) {
            SoftwarePageTexture page = textures == null ? null : textures.resolve(batch.getTextureId());
            rasterizeBatch(buffer, width, height, batch, page);
        }
        return buffer;
    }

    private static void rasterizeBatch(int[] buffer, int width, int height,
            SoftwareRenderFrame.BatchSnapshot batch, SoftwarePageTexture page) {
        float[] v = batch.getVertexData();
        int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
        int[] pagePixels = page == null ? null : page.getArgb();
        int pageSize = page == null ? 0 : page.getSize();
        for (int quad = 0; quad < batch.getQuadCount(); quad++) {
            int base = quad * GlyphRenderBatch.VERTICES_PER_QUAD * stride;
            rasterizeQuad(buffer, width, height, v, base, stride, pagePixels, pageSize);
        }
    }

    private static void rasterizeQuad(int[] buffer, int width, int height, float[] v, int base, int stride,
            int[] pagePixels, int pageSize) {
        float renderType = v[base + GlyphRenderBatch.GLYPH_FLAGS_OFFSET_FLOATS];
        // 四顶点顺序：左上、左下、右下、右上（与 GlyphRenderBatch.addQuad 一致）
        double x0 = v[base + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
        double y0 = v[base + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
        double x1 = v[base + stride + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
        double y1 = v[base + stride + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
        double x2 = v[base + stride * 2 + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
        double y2 = v[base + stride * 2 + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];
        double x3 = v[base + stride * 3 + GlyphRenderBatch.POSITION_OFFSET_FLOATS];
        double y3 = v[base + stride * 3 + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1];

        rasterizeTriangle(buffer, width, height, v, base, stride, renderType, pagePixels, pageSize,
                x0, y0, x1, y1, x2, y2);
        rasterizeTriangle(buffer, width, height, v, base, stride, renderType, pagePixels, pageSize,
                x2, y2, x3, y3, x0, y0);
    }

    private static void rasterizeTriangle(int[] buffer, int width, int height, float[] v, int base, int stride,
            float renderType, int[] pagePixels, int pageSize, double ax, double ay, double bx, double by,
            double cx, double cy) {
        int minX = Math.max(0, (int) Math.floor(Math.min(Math.min(ax, bx), cx)));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(Math.max(ax, bx), cx)) - 1);
        int minY = Math.max(0, (int) Math.floor(Math.min(Math.min(ay, by), cy)));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(Math.max(ay, by), cy)) - 1);
        if (minX > maxX || minY > maxY) {
            return;
        }
        double area = edge(ax, ay, bx, by, cx, cy);
        if (Math.abs(area) < 1.0e-9) {
            return;
        }
        for (int py = minY; py <= maxY; py++) {
            double sampleY = py + 0.5;
            for (int px = minX; px <= maxX; px++) {
                double sampleX = px + 0.5;
                double w0 = edge(bx, by, cx, cy, sampleX, sampleY) / area;
                double w1 = edge(cx, cy, ax, ay, sampleX, sampleY) / area;
                double w2 = edge(ax, ay, bx, by, sampleX, sampleY) / area;
                if (w0 < -1.0e-9 || w1 < -1.0e-9 || w2 < -1.0e-9) {
                    continue;
                }
                int argb = shade(v, base, stride, renderType, pagePixels, pageSize, w0, w1, w2);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                blend(buffer, py * width + px, argb);
            }
        }
    }

    private static int shade(float[] v, int base, int stride, float renderType, int[] pagePixels, int pageSize,
            double w0, double w1, double w2) {
        int uOff = GlyphRenderBatch.UV_OFFSET_FLOATS;
        int cOff = GlyphRenderBatch.COLOR_OFFSET_FLOATS;
        double u = v[base + uOff] * w0 + v[base + stride + uOff] * w1 + v[base + stride * 2 + uOff] * w2;
        double vv = v[base + uOff + 1] * w0 + v[base + stride + uOff + 1] * w1
                + v[base + stride * 2 + uOff + 1] * w2;
        double r = v[base + cOff] * w0 + v[base + stride + cOff] * w1 + v[base + stride * 2 + cOff] * w2;
        double g = v[base + cOff + 1] * w0 + v[base + stride + cOff + 1] * w1 + v[base + stride * 2 + cOff + 1] * w2;
        double b = v[base + cOff + 2] * w0 + v[base + stride + cOff + 2] * w1 + v[base + stride * 2 + cOff + 2] * w2;
        double a = v[base + cOff + 3] * w0 + v[base + stride + cOff + 3] * w1 + v[base + stride * 2 + cOff + 3] * w2;

        if (renderType > GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH) {
            // decoration：纯色直出
            return pack(r, g, b, a);
        }

        // uvBounds（slot clip 边界）硬墙：越界片元丢弃
        int bOff = GlyphRenderBatch.UV_BOUNDS_OFFSET_FLOATS;
        double u0 = v[base + bOff];
        double v0 = v[base + bOff + 1];
        double u1 = v[base + bOff + 2];
        double v1 = v[base + bOff + 3];
        if (u < u0 || u > u1 || vv < v0 || vv > v1) {
            return 0;
        }
        if (pagePixels == null || pageSize <= 0) {
            // ink 框模式：无纹理源时以 quad 颜色近似字形占位（几何验收）
            if (renderType > GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH) {
                return pack(NO_TEXTURE_COLORED_R, NO_TEXTURE_COLORED_G, NO_TEXTURE_COLORED_B,
                        INK_BOX_ALPHA * a);
            }
            return pack(r, g, b, INK_BOX_ALPHA * a);
        }

        int texel = sampleBilinear(pagePixels, pageSize, u, vv);
        int ta = texel >>> 24;
        if (ta == 0) {
            return 0;
        }
        if (renderType > GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH) {
            // colored glyph：RGB 取纹理，alpha = tex.a × quad.a
            return pack((texel >> 16 & 255) / 255.0, (texel >> 8 & 255) / 255.0, (texel & 255) / 255.0,
                    ta / 255.0 * a);
        }
        // mono glyph：RGB 取 quad 颜色，alpha = tex.a × quad.a
        return pack(r, g, b, ta / 255.0 * a);
    }

    private static int sampleBilinear(int[] pagePixels, int pageSize, double u, double v) {
        double fu = u * pageSize - 0.5;
        double fv = v * pageSize - 0.5;
        int x0 = (int) Math.floor(fu);
        int y0 = (int) Math.floor(fv);
        double fx = fu - x0;
        double fy = fv - y0;
        return bilinear(pagePixels, pageSize, x0, y0, x0 + 1, y0 + 1, fx, fy);
    }

    private static int bilinear(int[] pagePixels, int pageSize, int x0, int y0, int x1, int y1, double fx,
            double fy) {
        int c00 = texelClamp(pagePixels, pageSize, x0, y0);
        int c10 = texelClamp(pagePixels, pageSize, x1, y0);
        int c01 = texelClamp(pagePixels, pageSize, x0, y1);
        int c11 = texelClamp(pagePixels, pageSize, x1, y1);
        double w00 = (1.0 - fx) * (1.0 - fy);
        double w10 = fx * (1.0 - fy);
        double w01 = (1.0 - fx) * fy;
        double w11 = fx * fy;
        double a = (c00 >>> 24) * w00 + (c10 >>> 24) * w10 + (c01 >>> 24) * w01 + (c11 >>> 24) * w11;
        double r = (c00 >> 16 & 255) * w00 + (c10 >> 16 & 255) * w10 + (c01 >> 16 & 255) * w01
                + (c11 >> 16 & 255) * w11;
        double g = (c00 >> 8 & 255) * w00 + (c10 >> 8 & 255) * w10 + (c01 >> 8 & 255) * w01
                + (c11 >> 8 & 255) * w11;
        double b = (c00 & 255) * w00 + (c10 & 255) * w10 + (c01 & 255) * w01 + (c11 & 255) * w11;
        return pack(r / 255.0, g / 255.0, b / 255.0, a / 255.0);
    }

    private static int texelClamp(int[] pagePixels, int pageSize, int x, int y) {
        if (x < 0 || y < 0 || x >= pageSize || y >= pageSize) {
            return 0;
        }
        return pagePixels[y * pageSize + x];
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static int pack(double r, double g, double b, double a) {
        int ir = clampByte(r * 255.0);
        int ig = clampByte(g * 255.0);
        int ib = clampByte(b * 255.0);
        int ia = clampByte(a * 255.0);
        return ia << 24 | ir << 16 | ig << 8 | ib;
    }

    private static int clampByte(double value) {
        if (value <= 0.0) {
            return 0;
        }
        if (value >= 255.0) {
            return 255;
        }
        return (int) Math.round(value);
    }

    private static void blend(int[] buffer, int index, int src) {
        int dst = buffer[index];
        int sa = src >>> 24;
        if (sa == 255) {
            buffer[index] = src;
            return;
        }
        if (sa == 0) {
            return;
        }
        int invA = 255 - sa;
        int dr = ((src >> 16 & 255) * sa + (dst >> 16 & 255) * invA + 127) / 255;
        int dg = ((src >> 8 & 255) * sa + (dst >> 8 & 255) * invA + 127) / 255;
        int db = ((src & 255) * sa + (dst & 255) * invA + 127) / 255;
        buffer[index] = 0xFF000000 | dr << 16 | dg << 8 | db;
    }

    /** 转 {@link BufferedImage}（TYPE_INT_ARGB）。 */
    public static BufferedImage toImage(int[] argb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }

    /** 写 PNG（覆盖已有文件）。 */
    public static void writePng(int[] argb, int width, int height, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        if (!ImageIO.write(toImage(argb, width, height), "png", out)) {
            throw new IOException("无可用 PNG 编码器: " + out);
        }
    }
}
