package club.heiqi.uilib.font.render;

import club.heiqi.uilib.font.page.GlyphPage;

/**
 * 单个字符四边形渲染数据。
 */
public class GlyphQuad {

    private final GlyphPage glyphPage;
    private final float[] vertex;
    private final float[] uv;
    private final float[] color;
    private final float[] uvBounds;
    private final float[] glyphFlags;
    private final int[] index;

    /**
     * 创建字符四边形数据。
     *
     * @param pageIndex 字符页索引
     * @param vertex 顶点坐标
     * @param uv 纹理坐标
     * @param color 顶点颜色
     * @param uvBounds UV 边界
     * @param glyphFlags 渲染类型标记，0 为单色字形，1 为彩色字形，2 为纯色装饰线
     * @param index 索引数据
     */
    public GlyphQuad(GlyphPage glyphPage, float[] vertex, float[] uv, float[] color, float[] uvBounds, float[] glyphFlags,
            int[] index) {
        this.glyphPage = glyphPage;
        this.vertex = vertex;
        this.uv = uv;
        this.color = color;
        this.uvBounds = uvBounds;
        this.glyphFlags = glyphFlags;
        this.index = index;
    }

    public GlyphPage getGlyphPage() {
        return glyphPage;
    }

    public float[] getVertex() {
        return vertex;
    }

    public float[] getUv() {
        return uv;
    }

    public float[] getColor() {
        return color;
    }

    public float[] getUvBounds() {
        return uvBounds;
    }

    public float[] getGlyphFlags() {
        return glyphFlags;
    }

    public int[] getIndex() {
        return index;
    }
}
