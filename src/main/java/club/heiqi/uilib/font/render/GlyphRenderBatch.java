package club.heiqi.uilib.font.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import club.heiqi.uilib.font.config.FontConfig;
/**
 * 同一字符页的批渲染数据。
 */
public final class GlyphRenderBatch {

    private static final int DEFAULT_QUAD_CAPACITY = 64;
    static final int VERTICES_PER_QUAD = 4;
    static final int POSITION_COMPONENT_COUNT = 3;
    static final int UV_COMPONENT_COUNT = 2;
    static final int COLOR_COMPONENT_COUNT = 4;
    static final int UV_BOUNDS_COMPONENT_COUNT = 4;
    static final int GLYPH_FLAGS_COMPONENT_COUNT = 1;
    static final float RENDER_TYPE_MONOCHROME_GLYPH = 0.0F;
    static final float RENDER_TYPE_COLORED_GLYPH = 1.0F;
    static final float RENDER_TYPE_DECORATION = 2.0F;
    static final int INDICES_PER_QUAD = 6;
    static final int POSITION_OFFSET_FLOATS = 0;
    static final int UV_OFFSET_FLOATS = POSITION_OFFSET_FLOATS + POSITION_COMPONENT_COUNT;
    static final int COLOR_OFFSET_FLOATS = UV_OFFSET_FLOATS + UV_COMPONENT_COUNT;
    static final int UV_BOUNDS_OFFSET_FLOATS = COLOR_OFFSET_FLOATS + COLOR_COMPONENT_COUNT;
    static final int GLYPH_FLAGS_OFFSET_FLOATS = UV_BOUNDS_OFFSET_FLOATS + UV_BOUNDS_COMPONENT_COUNT;
    static final int VERTEX_STRIDE_FLOATS = GLYPH_FLAGS_OFFSET_FLOATS + GLYPH_FLAGS_COMPONENT_COUNT;
    static final int POSITION_OFFSET_BYTES = POSITION_OFFSET_FLOATS * Float.BYTES;
    static final int UV_OFFSET_BYTES = UV_OFFSET_FLOATS * Float.BYTES;
    static final int COLOR_OFFSET_BYTES = COLOR_OFFSET_FLOATS * Float.BYTES;
    static final int UV_BOUNDS_OFFSET_BYTES = UV_BOUNDS_OFFSET_FLOATS * Float.BYTES;
    static final int GLYPH_FLAGS_OFFSET_BYTES = GLYPH_FLAGS_OFFSET_FLOATS * Float.BYTES;
    static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_FLOATS * Float.BYTES;

    private int textureId;
    private float[] vertexData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS];
    private int[] indexData = new int[DEFAULT_QUAD_CAPACITY * INDICES_PER_QUAD];
    private int quadCount;

    /**
     * 创建可复用批次。
     */
    public GlyphRenderBatch() {}

    /**
     * 设置当前批次对应纹理。
     *
     * @param textureId 纹理 ID
     */
    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    /**
     * 追加一个四边形字形。
     *
     * <p>顶点 texCoord（u0/u1/v0/v1）与 uvBounds clip 边界（clipU0/clipU1/clipV0/clipV1）语义不同：</p>
     * <ul>
     *   <li>texCoord 用 ink ± INK_BLEED，决定 quad 几何与纹理对齐。</li>
     *   <li>uvBounds 用整个 slot 范围，放行 shader safeSample 在 mipmap 降采样时对 padding 羽化区的采样，
     *       避免硬裁边。两者不要合并。</li>
     * </ul>
     *
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param z 绘制 Z
     * @param width 字形屏幕宽度
     * @param height 字形屏幕高度
     * @param italic 是否斜体
     * @param u0 起始 U（顶点 texCoord，ink ± bleed）
     * @param u1 结束 U（顶点 texCoord，ink ± bleed）
     * @param v0 起始 V（顶点 texCoord，ink ± bleed）
     * @param v1 结束 V（顶点 texCoord，ink ± bleed）
     * @param clipU0 uvBounds 左边界（slot 范围）
     * @param clipU1 uvBounds 右边界（slot 范围）
     * @param clipV0 uvBounds 上边界（slot 范围）
     * @param clipV1 uvBounds 下边界（slot 范围）
     * @param red 红色
     * @param green 绿色
     * @param blue 蓝色
     * @param alpha 透明度
     * @param renderType 渲染类型，0 为单色字形，1 为彩色字形，2 为纯色装饰线
     */
    public void addQuad(float x, float y, float z, float width, float height, boolean italic, float u0, float u1, float v0,
            float v1, float clipU0, float clipU1, float clipV0, float clipV1, float red, float green, float blue,
            float alpha, float renderType) {
        ensureCapacity(quadCount + 1);

        int vertexBase = quadCount * VERTICES_PER_QUAD;
        int vertexFloatBase = vertexBase * VERTEX_STRIDE_FLOATS;
        int indexBase = quadCount * INDICES_PER_QUAD;

        float italicOffset = italic ? resolveItalicOffset(height) : 0.0F;
        float leftX = x + italicOffset;
        float rightX = x + width + italicOffset;

        writeVertex(vertexFloatBase, leftX, y, z, u0, v0, red, green, blue, alpha, clipU0, clipV0, clipU1, clipV1,
                renderType);
        writeVertex(vertexFloatBase + VERTEX_STRIDE_FLOATS, x, y + height, z, u0, v1, red, green, blue, alpha,
                clipU0, clipV0, clipU1, clipV1, renderType);
        writeVertex(vertexFloatBase + (VERTEX_STRIDE_FLOATS * 2), x + width, y + height, z, u1, v1, red,
                green, blue, alpha, clipU0, clipV0, clipU1, clipV1, renderType);
        writeVertex(vertexFloatBase + (VERTEX_STRIDE_FLOATS * 3), rightX, y, z, u1, v0, red, green, blue, alpha,
                clipU0, clipV0, clipU1, clipV1, renderType);

        indexData[indexBase] = vertexBase;
        indexData[indexBase + 1] = vertexBase + 1;
        indexData[indexBase + 2] = vertexBase + 2;
        indexData[indexBase + 3] = vertexBase + 2;
        indexData[indexBase + 4] = vertexBase + 3;
        indexData[indexBase + 5] = vertexBase;

        quadCount++;
    }

    /**
     * 追加一个纯色矩形四边形。
     *
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param z 绘制 Z
     * @param width 矩形宽度
     * @param height 矩形高度
     * @param red 红色
     * @param green 绿色
     * @param blue 蓝色
     * @param alpha 透明度
     * @param renderType 渲染类型，装饰线应使用 2
     */
    public void addRectangleQuad(float x, float y, float z, float width, float height, float red, float green,
            float blue, float alpha, float renderType) {
        ensureCapacity(quadCount + 1);

        int vertexBase = quadCount * VERTICES_PER_QUAD;
        int vertexFloatBase = vertexBase * VERTEX_STRIDE_FLOATS;
        int indexBase = quadCount * INDICES_PER_QUAD;
        float rightX = x + width;
        float bottomY = y + height;

        writeVertex(vertexFloatBase, x, y, z, 0.0F, 0.0F, red, green, blue, alpha, 0.0F, 0.0F, 0.0F, 0.0F,
                renderType);
        writeVertex(vertexFloatBase + VERTEX_STRIDE_FLOATS, x, bottomY, z, 0.0F, 0.0F, red, green, blue, alpha,
                0.0F, 0.0F, 0.0F, 0.0F, renderType);
        writeVertex(vertexFloatBase + (VERTEX_STRIDE_FLOATS * 2), rightX, bottomY, z, 0.0F, 0.0F, red, green,
                blue, alpha, 0.0F, 0.0F, 0.0F, 0.0F, renderType);
        writeVertex(vertexFloatBase + (VERTEX_STRIDE_FLOATS * 3), rightX, y, z, 0.0F, 0.0F, red, green, blue,
                alpha, 0.0F, 0.0F, 0.0F, 0.0F, renderType);

        indexData[indexBase] = vertexBase;
        indexData[indexBase + 1] = vertexBase + 1;
        indexData[indexBase + 2] = vertexBase + 2;
        indexData[indexBase + 3] = vertexBase + 2;
        indexData[indexBase + 4] = vertexBase + 3;
        indexData[indexBase + 5] = vertexBase;

        quadCount++;
    }

    public int getTextureId() {
        return textureId;
    }

    public boolean isEmpty() {
        return quadCount <= 0;
    }

    public int getQuadCount() {
        return quadCount;
    }

    public int getIndexCount() {
        return quadCount * INDICES_PER_QUAD;
    }

    public int getVertexFloatCount() {
        return quadCount * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS;
    }

    public void writeToBuffers(FloatBuffer vertexBuffer, IntBuffer indexBuffer) {
        vertexBuffer.put(vertexData, 0, getVertexFloatCount());
        indexBuffer.put(indexData, 0, getIndexCount());
    }

    /**
     * 清空当前批次内容并复用已分配数组。
     */
    public void clear() {
        quadCount = 0;
        textureId = 0;
    }

    private void ensureCapacity(int targetQuadCount) {
        if (targetQuadCount <= (vertexData.length / (VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS))) {
            return;
        }
        int nextCapacity = Math.max(DEFAULT_QUAD_CAPACITY, vertexData.length / (VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS));
        while (nextCapacity < targetQuadCount) {
            nextCapacity *= 2;
        }
        vertexData = grow(vertexData, nextCapacity * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS);
        indexData = grow(indexData, nextCapacity * INDICES_PER_QUAD);
    }

    private float resolveItalicOffset(float charSize) {
        float baseCharSize = Math.max(1.0F, (float) FontConfig.charSize);
        return 2.0F * charSize / baseCharSize;
    }

    private static float[] grow(float[] original, int newLength) {
        float[] expanded = new float[newLength];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private static int[] grow(int[] original, int newLength) {
        int[] expanded = new int[newLength];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private void writeVertex(int offset, float x, float y, float z, float u, float v, float red, float green,
            float blue, float alpha, float u0, float v0, float u1, float v1, float renderType) {
        vertexData[offset + POSITION_OFFSET_FLOATS] = x;
        vertexData[offset + POSITION_OFFSET_FLOATS + 1] = y;
        vertexData[offset + POSITION_OFFSET_FLOATS + 2] = z;
        vertexData[offset + UV_OFFSET_FLOATS] = u;
        vertexData[offset + UV_OFFSET_FLOATS + 1] = v;
        vertexData[offset + COLOR_OFFSET_FLOATS] = red;
        vertexData[offset + COLOR_OFFSET_FLOATS + 1] = green;
        vertexData[offset + COLOR_OFFSET_FLOATS + 2] = blue;
        vertexData[offset + COLOR_OFFSET_FLOATS + 3] = alpha;
        vertexData[offset + UV_BOUNDS_OFFSET_FLOATS] = u0;
        vertexData[offset + UV_BOUNDS_OFFSET_FLOATS + 1] = v0;
        vertexData[offset + UV_BOUNDS_OFFSET_FLOATS + 2] = u1;
        vertexData[offset + UV_BOUNDS_OFFSET_FLOATS + 3] = v1;
        vertexData[offset + GLYPH_FLAGS_OFFSET_FLOATS] = renderType;
    }
}
