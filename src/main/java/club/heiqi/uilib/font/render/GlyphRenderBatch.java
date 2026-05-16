package club.heiqi.uilib.font.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphPage;

/**
 * 同一字符页的批渲染数据。
 */
public final class GlyphRenderBatch {

    private static final int DEFAULT_QUAD_CAPACITY = 64;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = 3;
    private static final int UV_FLOATS_PER_VERTEX = 2;
    private static final int COLOR_FLOATS_PER_VERTEX = 4;
    private static final int UV_BOUNDS_FLOATS_PER_VERTEX = 4;
    private static final int GLYPH_FLAGS_FLOATS_PER_VERTEX = 1;
    private static final int INDICES_PER_QUAD = 6;

    private final GlyphPage glyphPage;
    private float[] vertexData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * FLOATS_PER_VERTEX];
    private float[] uvData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * UV_FLOATS_PER_VERTEX];
    private float[] colorData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * COLOR_FLOATS_PER_VERTEX];
    private float[] uvBoundsData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * UV_BOUNDS_FLOATS_PER_VERTEX];
    private float[] glyphFlagsData = new float[DEFAULT_QUAD_CAPACITY * VERTICES_PER_QUAD * GLYPH_FLAGS_FLOATS_PER_VERTEX];
    private int[] indexData = new int[DEFAULT_QUAD_CAPACITY * INDICES_PER_QUAD];
    private int quadCount;

    /**
     * 创建字符页批次。
     *
     * @param glyphPage 字符页
     */
    public GlyphRenderBatch(GlyphPage glyphPage) {
        this.glyphPage = glyphPage;
    }

    /**
     * 追加一个四边形字形。
     *
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param z 绘制 Z
     * @param charSize 字体大小
     * @param italic 是否斜体
     * @param u0 起始 U
     * @param u1 结束 U
     * @param v0 起始 V
     * @param v1 结束 V
     * @param red 红色
     * @param green 绿色
     * @param blue 蓝色
     * @param alpha 透明度
     * @param coloredGlyphFlag 彩色字形标记
     */
    public void addQuad(float x, float y, float z, float charSize, boolean italic, float u0, float u1, float v0,
            float v1, float red, float green, float blue, float alpha, float coloredGlyphFlag) {
        ensureCapacity(quadCount + 1);

        int vertexBase = quadCount * VERTICES_PER_QUAD;
        int vertexFloatBase = vertexBase * FLOATS_PER_VERTEX;
        int uvFloatBase = vertexBase * UV_FLOATS_PER_VERTEX;
        int colorFloatBase = vertexBase * COLOR_FLOATS_PER_VERTEX;
        int uvBoundsFloatBase = vertexBase * UV_BOUNDS_FLOATS_PER_VERTEX;
        int glyphFlagFloatBase = vertexBase * GLYPH_FLAGS_FLOATS_PER_VERTEX;
        int indexBase = quadCount * INDICES_PER_QUAD;

        float italicOffset = italic ? resolveItalicOffset(charSize) : 0.0F;
        float leftX = x + italicOffset;
        float rightX = x + charSize + italicOffset;

        writeVertex(vertexFloatBase, leftX, y, z);
        writeVertex(vertexFloatBase + FLOATS_PER_VERTEX, x, y + charSize, z);
        writeVertex(vertexFloatBase + (FLOATS_PER_VERTEX * 2), x + charSize, y + charSize, z);
        writeVertex(vertexFloatBase + (FLOATS_PER_VERTEX * 3), rightX, y, z);

        writeUv(uvFloatBase, u0, v0);
        writeUv(uvFloatBase + UV_FLOATS_PER_VERTEX, u0, v1);
        writeUv(uvFloatBase + (UV_FLOATS_PER_VERTEX * 2), u1, v1);
        writeUv(uvFloatBase + (UV_FLOATS_PER_VERTEX * 3), u1, v0);

        writeColor(colorFloatBase, red, green, blue, alpha);
        writeColor(colorFloatBase + COLOR_FLOATS_PER_VERTEX, red, green, blue, alpha);
        writeColor(colorFloatBase + (COLOR_FLOATS_PER_VERTEX * 2), red, green, blue, alpha);
        writeColor(colorFloatBase + (COLOR_FLOATS_PER_VERTEX * 3), red, green, blue, alpha);

        writeUvBounds(uvBoundsFloatBase, u0, v0, u1, v1);
        writeUvBounds(uvBoundsFloatBase + UV_BOUNDS_FLOATS_PER_VERTEX, u0, v0, u1, v1);
        writeUvBounds(uvBoundsFloatBase + (UV_BOUNDS_FLOATS_PER_VERTEX * 2), u0, v0, u1, v1);
        writeUvBounds(uvBoundsFloatBase + (UV_BOUNDS_FLOATS_PER_VERTEX * 3), u0, v0, u1, v1);

        glyphFlagsData[glyphFlagFloatBase] = coloredGlyphFlag;
        glyphFlagsData[glyphFlagFloatBase + 1] = coloredGlyphFlag;
        glyphFlagsData[glyphFlagFloatBase + 2] = coloredGlyphFlag;
        glyphFlagsData[glyphFlagFloatBase + 3] = coloredGlyphFlag;

        indexData[indexBase] = vertexBase;
        indexData[indexBase + 1] = vertexBase + 1;
        indexData[indexBase + 2] = vertexBase + 2;
        indexData[indexBase + 3] = vertexBase + 2;
        indexData[indexBase + 4] = vertexBase + 3;
        indexData[indexBase + 5] = vertexBase;

        quadCount++;
    }

    public GlyphPage getGlyphPage() {
        return glyphPage;
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

    public void writeToBuffers(FloatBuffer vertexBuffer, FloatBuffer uvBuffer, FloatBuffer colorBuffer,
            FloatBuffer uvBoundsBuffer, FloatBuffer glyphFlagsBuffer, IntBuffer indexBuffer) {
        int vertexFloatCount = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
        int uvFloatCount = quadCount * VERTICES_PER_QUAD * UV_FLOATS_PER_VERTEX;
        int colorFloatCount = quadCount * VERTICES_PER_QUAD * COLOR_FLOATS_PER_VERTEX;
        int uvBoundsFloatCount = quadCount * VERTICES_PER_QUAD * UV_BOUNDS_FLOATS_PER_VERTEX;
        int glyphFlagsFloatCount = quadCount * VERTICES_PER_QUAD * GLYPH_FLAGS_FLOATS_PER_VERTEX;
        int indexCount = quadCount * INDICES_PER_QUAD;

        vertexBuffer.put(vertexData, 0, vertexFloatCount);
        uvBuffer.put(uvData, 0, uvFloatCount);
        colorBuffer.put(colorData, 0, colorFloatCount);
        uvBoundsBuffer.put(uvBoundsData, 0, uvBoundsFloatCount);
        glyphFlagsBuffer.put(glyphFlagsData, 0, glyphFlagsFloatCount);
        indexBuffer.put(indexData, 0, indexCount);
    }

    private void ensureCapacity(int targetQuadCount) {
        if (targetQuadCount <= (vertexData.length / (VERTICES_PER_QUAD * FLOATS_PER_VERTEX))) {
            return;
        }
        int nextCapacity = Math.max(DEFAULT_QUAD_CAPACITY, vertexData.length / (VERTICES_PER_QUAD * FLOATS_PER_VERTEX));
        while (nextCapacity < targetQuadCount) {
            nextCapacity *= 2;
        }
        vertexData = grow(vertexData, nextCapacity * VERTICES_PER_QUAD * FLOATS_PER_VERTEX);
        uvData = grow(uvData, nextCapacity * VERTICES_PER_QUAD * UV_FLOATS_PER_VERTEX);
        colorData = grow(colorData, nextCapacity * VERTICES_PER_QUAD * COLOR_FLOATS_PER_VERTEX);
        uvBoundsData = grow(uvBoundsData, nextCapacity * VERTICES_PER_QUAD * UV_BOUNDS_FLOATS_PER_VERTEX);
        glyphFlagsData = grow(glyphFlagsData, nextCapacity * VERTICES_PER_QUAD * GLYPH_FLAGS_FLOATS_PER_VERTEX);
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

    private void writeVertex(int offset, float x, float y, float z) {
        vertexData[offset] = x;
        vertexData[offset + 1] = y;
        vertexData[offset + 2] = z;
    }

    private void writeUv(int offset, float u, float v) {
        uvData[offset] = u;
        uvData[offset + 1] = v;
    }

    private void writeColor(int offset, float red, float green, float blue, float alpha) {
        colorData[offset] = red;
        colorData[offset + 1] = green;
        colorData[offset + 2] = blue;
        colorData[offset + 3] = alpha;
    }

    private void writeUvBounds(int offset, float u0, float v0, float u1, float v1) {
        uvBoundsData[offset] = u0;
        uvBoundsData[offset + 1] = v0;
        uvBoundsData[offset + 2] = u1;
        uvBoundsData[offset + 3] = v1;
    }
}
