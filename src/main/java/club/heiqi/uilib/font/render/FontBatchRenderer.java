package club.heiqi.uilib.font.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.shader.FontShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import club.heiqi.uilib.font.FontRuntimeDiagnostics;

/**
 * 字体批渲染器。
 */
public class FontBatchRenderer {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<GlyphPage, GlyphRenderBatch> currentBatches = new LinkedHashMap<GlyphPage, GlyphRenderBatch>();
    private final GlyphRenderBatch decorationBatch = new GlyphRenderBatch(null);
    private final List<PageRenderCommand> renderCommands = new ArrayList<PageRenderCommand>();
    private final FontRenderTool renderTool = new FontRenderTool();
    private final FontRenderStateGuard stateGuard = new FontRenderStateGuard();
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(1024 * 64 * GlyphRenderBatch.VERTEX_STRIDE_FLOATS);
    private IntBuffer indexBuffer = BufferUtils.createIntBuffer(1024 * 64 * GlyphRenderBatch.INDICES_PER_QUAD);
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private int quadCount = 0;
    private int lastFlushPageSubmitCount = 0;
    private int lastFlushDrawCallCount = 0;
    private int lastFlushTextureSwitchCount = 0;
    private int builtPageCommandCount = 0;

    /**
     * 初始化批渲染器。
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            stateGuard.run(new Runnable() {
                @Override
                public void run() {
                    renderTool.initialize();
                }
            });
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
     * 释放批渲染器持有的底层 GL 资源。
     */
    public void dispose() {
        clearFrame();
        renderTool.dispose();
        initialized.set(false);
    }

    /**
     * 收集一个 direct-index 定位的字符四边形到当前帧。
     *
     * @param glyphPage 字符页
     * @param slotX 槽位 X
     * @param slotY 槽位 Y
     * @param slotWidth 槽位宽度
     * @param slotHeight 槽位高度
     * @param x 绘制起点 X
     * @param y 绘制起点 Y
     * @param charSize 字体显示尺寸
     * @param color 文本颜色
     * @param italic 是否斜体
     * @param glyphFlags 字形标记
     */
    public void collect(
            GlyphPage glyphPage,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight,
            float x,
            float y,
            float charSize,
            int color,
            boolean italic,
            byte glyphFlags) {
        initialize();

        float textureSize = glyphPage.getTextureSize();
        float u0 = (slotX + 1.0F) / textureSize;
        float u1 = (slotX + slotWidth - 1.0F) / textureSize;
        float v0 = (slotY + 1.0F) / textureSize;
        float v1 = (slotY + slotHeight - 1.0F) / textureSize;

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        float renderType = (glyphFlags & GlyphRuntimeTables.GLYPH_FLAG_COLORED) != 0
                ? GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH
                : GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH;

        GlyphRenderBatch batch = currentBatches.get(glyphPage);
        if (batch == null) {
            batch = new GlyphRenderBatch(glyphPage);
            currentBatches.put(glyphPage, batch);
        }
        batch.addQuad(x, y, z, charSize, italic, u0, u1, v0, v1, red, green, blue, alpha, renderType);
        quadCount++;
    }

    /**
     * 收集一个纯色文本装饰线矩形到当前帧。
     *
     * <p>装饰线不依附任何字符页，flush 时固定排在字形页命令之后，保持“字形先绘制、装饰线后覆盖”的旧语义。</p>
     *
     * @param x 起始 X
     * @param y 起始 Y
     * @param width 线条宽度
     * @param height 线条高度
     * @param color 文本颜色
     */
    public void collectDecoration(float x, float y, float width, float height, int color) {
        initialize();

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        decorationBatch.addRectangleQuad(x, y, z, width, height, red, green, blue, alpha,
                GlyphRenderBatch.RENDER_TYPE_DECORATION);
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
            recordLastFlushStats(0, 0, 0);
            return 0;
        }

        int commandCount = buildRenderCommands();
        int pageSubmitCount = builtPageCommandCount;
        if (commandCount <= 0) {
            recordLastFlushStats(0, 0, 0);
            clearFrame();
            return 0;
        }

        int drawCallCount = 0;
        int textureSwitchCount = 0;
        stateGuard.push();
        try {
            FontRenderStateSupport.prepareTextRenderState();

            shaderProgram.bind();
            setupUniforms(shaderProgram);
            shaderProgram.setUniformI("mainTex", 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int boundTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            for (int index = 0; index < commandCount; index++) {
                PageRenderCommand command = renderCommands.get(index);
                int textureId = command.getTextureId();
                if (boundTextureId != textureId) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                    boundTextureId = textureId;
                    textureSwitchCount++;
                }

                prepareBuffers(command);
                FontRuntimeDiagnostics.logFlushState(shaderProgram.getShaderProgramId(),
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                        textureId,
                        GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                        GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                        GL11.glGetError(),
                        command.getQuadCount());
                renderTool.render(vertexBuffer, indexBuffer, indexBuffer.limit());
                drawCallCount++;
            }
            shaderProgram.unbind();
        } finally {
            stateGuard.pop();
            clearRenderCommands(commandCount);
        }

        recordLastFlushStats(pageSubmitCount, drawCallCount, textureSwitchCount);
        if (flushedQuadCount > 0) {
            MyMod.LOG.debug("提交字体批次：pageCommands={} renderCommands={} drawCalls={} textureSwitches={} quadCount={}",
                    Integer.valueOf(pageSubmitCount), Integer.valueOf(commandCount), Integer.valueOf(drawCallCount),
                    Integer.valueOf(textureSwitchCount), Integer.valueOf(flushedQuadCount));
        }
        clearFrame();
        return flushedQuadCount;
    }

    /**
     * 清空当前帧缓存。
     */
    public void clearFrame() {
        currentBatches.clear();
        decorationBatch.clear();
        quadCount = 0;
    }

    /**
     * 仅清空当前帧已收集的装饰线。
     */
    public void clearDecorationQuads() {
        int decorationQuadCount = decorationBatch.getQuadCount();
        if (decorationQuadCount <= 0) {
            return;
        }
        decorationBatch.clear();
        quadCount = Math.max(0, quadCount - decorationQuadCount);
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

    /**
     * 获取上次 flush 实际提交的字符页命令数量。
     *
     * @return 字符页提交数量
     */
    public int getLastFlushPageSubmitCount() {
        return lastFlushPageSubmitCount;
    }

    /**
     * 获取上次 flush 实际触发的 draw call 数量。
     *
     * @return draw call 数量
     */
    public int getLastFlushDrawCallCount() {
        return lastFlushDrawCallCount;
    }

    /**
     * 获取上次 flush 内实际发生的纹理切换数量。
     *
     * @return 纹理切换数量
     */
    public int getLastFlushTextureSwitchCount() {
        return lastFlushTextureSwitchCount;
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

    private int buildRenderCommands() {
        int commandCount = 0;
        builtPageCommandCount = 0;
        for (GlyphRenderBatch batch : currentBatches.values()) {
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            GlyphPage page = batch.getGlyphPage();
            if (page == null || page.getTextureId() <= 0) {
                continue;
            }

            PageRenderCommand command = obtainRenderCommand(commandCount);
            command.reset(page, batch, page.getTextureId());
            commandCount++;
        }
        builtPageCommandCount = commandCount;
        if (!decorationBatch.isEmpty()) {
            PageRenderCommand command = obtainRenderCommand(commandCount);
            command.reset(null, decorationBatch, 0);
            commandCount++;
        }
        return commandCount;
    }

    private PageRenderCommand obtainRenderCommand(int index) {
        while (index >= renderCommands.size()) {
            renderCommands.add(new PageRenderCommand());
        }
        return renderCommands.get(index);
    }

    private void clearRenderCommands(int commandCount) {
        int safeCommandCount = Math.min(commandCount, renderCommands.size());
        for (int index = 0; index < safeCommandCount; index++) {
            renderCommands.get(index).clear();
        }
    }

    private void recordLastFlushStats(int pageSubmitCount, int drawCallCount, int textureSwitchCount) {
        lastFlushPageSubmitCount = pageSubmitCount;
        lastFlushDrawCallCount = drawCallCount;
        lastFlushTextureSwitchCount = textureSwitchCount;
    }

    private void prepareBuffers(PageRenderCommand command) {
        vertexBuffer = ensureFloatCapacity(vertexBuffer, command.getVertexFloatCount());
        indexBuffer = ensureIntCapacity(indexBuffer, command.getIndexCount());
        vertexBuffer.clear();
        indexBuffer.clear();
        command.writeToBuffers(vertexBuffer, indexBuffer);

        vertexBuffer.flip();
        indexBuffer.flip();
    }

    private FloatBuffer ensureFloatCapacity(FloatBuffer buffer, int targetCapacity) {
        if (targetCapacity <= buffer.capacity()) {
            return buffer;
        }
        int nextCapacity = buffer.capacity();
        while (nextCapacity < targetCapacity) {
            nextCapacity *= 2;
        }
        return BufferUtils.createFloatBuffer(nextCapacity);
    }

    private IntBuffer ensureIntCapacity(IntBuffer buffer, int targetCapacity) {
        if (targetCapacity <= buffer.capacity()) {
            return buffer;
        }
        int nextCapacity = buffer.capacity();
        while (nextCapacity < targetCapacity) {
            nextCapacity *= 2;
        }
        return BufferUtils.createIntBuffer(nextCapacity);
    }

    /**
     * 一次 flush 内的单页绘制命令。
     */
    private static final class PageRenderCommand {

        private GlyphPage page;
        private GlyphRenderBatch batch;
        private int textureId;

        private void reset(GlyphPage page, GlyphRenderBatch batch, int textureId) {
            this.page = page;
            this.batch = batch;
            this.textureId = textureId;
        }

        private void clear() {
            page = null;
            batch = null;
            textureId = 0;
        }

        private int getTextureId() {
            return textureId;
        }

        private int getQuadCount() {
            return batch == null ? 0 : batch.getQuadCount();
        }

        private int getIndexCount() {
            return batch == null ? 0 : batch.getIndexCount();
        }

        private int getVertexFloatCount() {
            return batch == null ? 0 : batch.getVertexFloatCount();
        }

        private void writeToBuffers(FloatBuffer vertexBuffer, IntBuffer indexBuffer) {
            if (batch != null) {
                batch.writeToBuffers(vertexBuffer, indexBuffer);
            }
        }
    }
}
