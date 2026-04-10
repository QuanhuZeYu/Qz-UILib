package club.heiqi.uilib.font.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageSlot;
import club.heiqi.uilib.font.shader.FontShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 字体批渲染器。
 */
public class FontBatchRenderer {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<GlyphPage, GlyphRenderBatch> currentBatches = new LinkedHashMap<GlyphPage, GlyphRenderBatch>();
    private final FontRenderTool renderTool = new FontRenderTool();
    private final FontRenderStateGuard stateGuard = new FontRenderStateGuard();
    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(1024 * 64 * 3);
    private final FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(1024 * 64 * 2);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(1024 * 64 * 4);
    private final FloatBuffer uvBoundsBuffer = BufferUtils.createFloatBuffer(1024 * 64 * 4);
    private final FloatBuffer glyphFlagsBuffer = BufferUtils.createFloatBuffer(1024 * 64 * 1);
    private final IntBuffer indexBuffer = BufferUtils.createIntBuffer(1024 * 64 * 6);
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private int quadCount = 0;

    /**
     * 初始化批渲染器。
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            renderTool.initialize();
        }
    }

    /**
     * 重新装载批渲染器状态。
     */
    public void reload() {
        if (!initialized.get()) {
            initialize();
            return;
        }
        clearFrame();
    }

    /**
     * 收集一个字符四边形到当前帧。
     *
     * @param glyphPage 字符页
     * @param pageSlot 槽位信息
     * @param x 绘制起点 X
     * @param y 绘制起点 Y
     * @param charSize 字体显示尺寸
     * @param color 文本颜色
     * @param italic 是否斜体
     * @param glyphInfo 字符度量信息
     */
    public void collect(
            GlyphPage glyphPage,
            GlyphPageSlot pageSlot,
            float x,
            float y,
            float charSize,
            int color,
            boolean italic,
            GlyphInfo glyphInfo) {
        initialize();

        float textureSize = glyphPage.getTextureSize();
        float u0 = (pageSlot.getX() + 1.0F) / textureSize;
        float u1 = (pageSlot.getX() + pageSlot.getWidth() - 1.0F) / textureSize;
        float v0 = (pageSlot.getY() + 1.0F) / textureSize;
        float v1 = (pageSlot.getY() + pageSlot.getHeight() - 1.0F) / textureSize;

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        float[] vertex = new float[] {
                italic ? x + 2.0F : x, y, z,
                x, y + charSize, z,
                x + charSize, y + charSize, z,
                italic ? x + charSize + 2.0F : x + charSize, y, z
        };
        float[] uv = new float[] { u0, v0, u0, v1, u1, v1, u1, v0 };
        float[] vertexColor = new float[] {
                red, green, blue, alpha,
                red, green, blue, alpha,
                red, green, blue, alpha,
                red, green, blue, alpha
        };
        float[] uvBounds = new float[] {
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1
        };
        float coloredGlyphFlag = glyphInfo != null && glyphInfo.isColoredGlyph() ? 1.0F : 0.0F;
        float[] glyphFlags = new float[] { coloredGlyphFlag, coloredGlyphFlag, coloredGlyphFlag, coloredGlyphFlag };
        int[] index = new int[] { 0, 1, 2, 2, 3, 0 };

        GlyphRenderBatch batch = currentBatches.get(glyphPage);
        if (batch == null) {
            batch = new GlyphRenderBatch(glyphPage);
            currentBatches.put(glyphPage, batch);
        }
        batch.addQuad(new GlyphQuad(glyphPage, vertex, uv, vertexColor, uvBounds, glyphFlags, index));
        quadCount++;
    }

    /**
     * 生成当前帧渲染快照。
     *
     * @return 渲染快照
     */
    public RenderFrameSnapshot snapshot() {
        return new RenderFrameSnapshot(new ArrayList<GlyphRenderBatch>(currentBatches.values()));
    }

    /**
     * 提交当前帧批次。
     *
     * @return 提交的四边形数量
     */
    public int flush(FontShaderProgram shaderProgram) {
        initialize();
        shaderProgram.initialize();

        int flushedQuadCount = quadCount;
        if (flushedQuadCount <= 0) {
            return 0;
        }

        stateGuard.push();
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            shaderProgram.bind();
            setupUniforms(shaderProgram);
            for (GlyphRenderBatch batch : currentBatches.values()) {
                GlyphPage page = batch.getGlyphPage();
                if (page == null || batch.isEmpty()) {
                    continue;
                }

                prepareBuffers(batch);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, page.getTextureId());
                shaderProgram.setUniformI("mainTex", 0);
                renderTool.render(vertexBuffer, uvBuffer, colorBuffer, uvBoundsBuffer, glyphFlagsBuffer, indexBuffer,
                        indexBuffer.limit());
            }
            shaderProgram.unbind();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            stateGuard.pop();
        }

        if (flushedQuadCount > 0) {
            MyMod.LOG.debug("提交字体批次：batchCount={} quadCount={}", Integer.valueOf(currentBatches.size()), Integer.valueOf(flushedQuadCount));
        }
        clearFrame();
        return flushedQuadCount;
    }

    /**
     * 清空当前帧缓存。
     */
    public void clearFrame() {
        currentBatches.clear();
        quadCount = 0;
    }

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取当前帧四边形数量。
     *
     * @return 四边形数量
     */
    public int getQuadCount() {
        return quadCount;
    }

    private void setupUniforms(FontShaderProgram shaderProgram) {
        modelViewBuffer.clear();
        projectionBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        modelViewBuffer.flip();
        projectionBuffer.flip();

        shaderProgram.setUniformM4f("modelview", new Matrix4f(modelViewBuffer));
        shaderProgram.setUniformM4f("projection", new Matrix4f(projectionBuffer));
        shaderProgram.setUniformF("brightnessGain", (float) FontConfig.brightnessGain);
        shaderProgram.setUniformVec2("smoothRange", new Vector2f((float) FontConfig.smoothRangeMin, (float) FontConfig.smoothRangeMax));
        shaderProgram.setUniformI("aaMode", FontConfig.aaMode);
        shaderProgram.setUniformF("aaStrength", (float) (FontConfig.aaStrength / 120.0D));
        shaderProgram.setUniformVec2("textureSize", new Vector2f((float) (FontConfig.awtCharSize * 64.0D), (float) (FontConfig.awtCharSize * 64.0D)));
    }

    private void prepareBuffers(GlyphRenderBatch batch) {
        vertexBuffer.clear();
        uvBuffer.clear();
        colorBuffer.clear();
        uvBoundsBuffer.clear();
        glyphFlagsBuffer.clear();
        indexBuffer.clear();

        int vertexOffset = 0;
        for (GlyphQuad quad : batch.getGlyphQuads()) {
            vertexBuffer.put(quad.getVertex());
            uvBuffer.put(quad.getUv());
            colorBuffer.put(quad.getColor());
            uvBoundsBuffer.put(quad.getUvBounds());
            glyphFlagsBuffer.put(quad.getGlyphFlags());
            for (int index : quad.getIndex()) {
                indexBuffer.put(index + vertexOffset);
            }
            vertexOffset += 4;
        }

        vertexBuffer.flip();
        uvBuffer.flip();
        colorBuffer.flip();
        uvBoundsBuffer.flip();
        glyphFlagsBuffer.flip();
        indexBuffer.flip();
    }
}
