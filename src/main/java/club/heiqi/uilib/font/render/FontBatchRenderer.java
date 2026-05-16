package club.heiqi.uilib.font.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.shader.FontShaderProgram;

/**
 * 字体批渲染器。
 */
public class FontBatchRenderer {

    private static final byte ACTIVE_TYPE_NORMAL = 0;
    private static final byte ACTIVE_TYPE_BOLD = 1;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private GlyphRenderBatch[] normalPageBatches = new GlyphRenderBatch[4];
    private GlyphRenderBatch[] boldPageBatches = new GlyphRenderBatch[4];
    private int[] activePageIndices = new int[8];
    private byte[] activePageTypes = new byte[8];
    private boolean[] activeNormalPages = new boolean[4];
    private boolean[] activeBoldPages = new boolean[4];
    private int activePageCount;
    private final GlyphRenderBatch decorationBatch = new GlyphRenderBatch();
    private final FontRenderTool renderTool = new FontRenderTool();
    private final FontRenderStateGuard stateGuard = new FontRenderStateGuard();
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(1024 * 64 * GlyphRenderBatch.VERTEX_STRIDE_FLOATS);
    private IntBuffer indexBuffer = BufferUtils.createIntBuffer(1024 * 64 * GlyphRenderBatch.INDICES_PER_QUAD);
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer identityModelViewBuffer = createIdentityMatrixBuffer();
    private final FloatBuffer internalUiProjectionBuffer = createIdentityMatrixBuffer();
    private int quadCount = 0;
    private int lastFlushPageSubmitCount = 0;
    private int lastFlushDrawCallCount = 0;
    private int lastFlushTextureBindCount = 0;
    private boolean assumeInternalUiMatrices = false;

    /**
     * 设置是否按 UILib 内部屏幕坐标路径使用缓存矩阵，避免每次 flush 查询固定管线矩阵。
     *
     * @param assumeInternalUiMatrices 是否使用内部 UI 矩阵
     */
    public void setAssumeInternalUiMatrices(boolean assumeInternalUiMatrices) {
        this.assumeInternalUiMatrices = assumeInternalUiMatrices;
    }

    /**
     * 判断当前是否走内部 UI 矩阵缓存路径。
     *
     * @return 是否使用内部 UI 矩阵
     */
    public boolean isAssumingInternalUiMatrices() {
        return assumeInternalUiMatrices;
    }

    /**
     * 配置内部 UI 渲染路径使用的正交投影矩阵。
     *
     * @param width 渲染目标宽度
     * @param height 渲染目标高度
     */
    public void configureInternalUiProjection(int width, int height) {
        writeOrthoProjection(internalUiProjectionBuffer, Math.max(1, width), Math.max(1, height));
    }

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
     * @param fontType 字重类型
     * @param pageIndex 字符页索引
     * @param textureId 字符页纹理 ID
     * @param textureSize 字符页纹理边长
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
            FontType fontType,
            int pageIndex,
            int textureId,
            int textureSize,
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
        if (pageIndex < 0 || textureId <= 0 || textureSize <= 0) {
            return;
        }
        initialize();

        float resolvedTextureSize = (float) textureSize;
        float u0 = (slotX + 1.0F) / resolvedTextureSize;
        float u1 = (slotX + slotWidth - 1.0F) / resolvedTextureSize;
        float v0 = (slotY + 1.0F) / resolvedTextureSize;
        float v1 = (slotY + slotHeight - 1.0F) / resolvedTextureSize;

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        float renderType = (glyphFlags & GlyphRuntimeTables.GLYPH_FLAG_COLORED) != 0
                ? GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH
                : GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH;

        GlyphRenderBatch batch = obtainPageBatch(fontType, pageIndex, textureId);
        batch.addQuad(x, y, z, charSize, italic, u0, u1, v0, v1, red, green, blue, alpha, renderType);
        quadCount++;
    }

    /**
     * 收集一个纯色文本装饰线矩形到当前帧。
     *
     * <p>装饰线不依附任何字符页，flush 时固定排在字形页批次之后，保持“字形先绘制、装饰线后覆盖”的旧语义。</p>
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
     * 在自带状态保护边界中提交当前帧批次。
     *
     * @param shaderProgram 字体 shader
     * @return 提交的四边形数量
     */
    public int flush(FontShaderProgram shaderProgram) {
        stateGuard.push();
        try {
            return flushWithinActiveState(shaderProgram);
        } finally {
            stateGuard.pop();
        }
    }

    /**
     * 在调用方已经建立的状态保护边界内提交当前帧批次。
     *
     * @param shaderProgram 字体 shader
     * @return 提交的四边形数量
     */
    public int flushWithinActiveState(FontShaderProgram shaderProgram) {
        initialize();
        shaderProgram.initialize();

        int flushedQuadCount = quadCount;
        if (flushedQuadCount <= 0) {
            recordLastFlushStats(0, 0, 0);
            return 0;
        }
        if (activePageCount <= 0 && decorationBatch.isEmpty()) {
            recordLastFlushStats(0, 0, 0);
            clearFrame();
            return 0;
        }

        int pageSubmitCount = 0;
        int drawCallCount = 0;
        int textureBindCount = 0;
        int boundTextureId = Integer.MIN_VALUE;
        FontRenderStateSupport.prepareTextRenderState();

        shaderProgram.bind();
        try {
            setupUniforms(shaderProgram);
            shaderProgram.setUniformI("mainTex", 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            for (int index = 0; index < activePageCount; index++) {
                GlyphRenderBatch batch = getActiveBatch(index);
                if (batch == null || batch.isEmpty() || batch.getTextureId() <= 0) {
                    continue;
                }
                int nextBoundTextureId = bindTextureIfNeeded(batch.getTextureId(), boundTextureId);
                if (nextBoundTextureId != boundTextureId) {
                    textureBindCount++;
                }
                boundTextureId = nextBoundTextureId;
                renderBatch(shaderProgram, batch);
                pageSubmitCount++;
                drawCallCount++;
            }

            if (!decorationBatch.isEmpty()) {
                renderBatch(shaderProgram, decorationBatch);
                drawCallCount++;
            }
        } finally {
            shaderProgram.unbind();
        }

        recordLastFlushStats(pageSubmitCount, drawCallCount, textureBindCount);
        if (flushedQuadCount > 0) {
            MyMod.LOG.debug("提交字体批次：pageBatches={} drawCalls={} textureBinds={} quadCount={} "
                    + "internalMatrices={}", Integer.valueOf(pageSubmitCount), Integer.valueOf(drawCallCount),
                    Integer.valueOf(textureBindCount), Integer.valueOf(flushedQuadCount),
                    Boolean.valueOf(assumeInternalUiMatrices));
        }
        clearFrame();
        return flushedQuadCount;
    }

    /**
     * 清空当前帧缓存。
     */
    public void clearFrame() {
        for (int index = 0; index < activePageCount; index++) {
            GlyphRenderBatch batch = getActiveBatch(index);
            if (batch != null) {
                batch.clear();
            }
            clearActiveMarker(index);
        }
        activePageCount = 0;
        decorationBatch.clear();
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

    /**
     * 获取上次 flush 实际提交的字符页批次数量。
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
     * 获取上次 flush 内由字体批渲染器执行的纹理绑定次数。
     *
     * @return 纹理绑定数量
     */
    public int getLastFlushTextureBindCount() {
        return lastFlushTextureBindCount;
    }

    private GlyphRenderBatch obtainPageBatch(FontType fontType, int pageIndex, int textureId) {
        GlyphRenderBatch[] batches = ensurePageBatchCapacity(fontType, pageIndex + 1);
        GlyphRenderBatch batch = batches[pageIndex];
        if (batch == null) {
            batch = new GlyphRenderBatch();
            batches[pageIndex] = batch;
        }
        if (markPageActive(fontType, pageIndex)) {
            appendActivePage(fontType, pageIndex);
        }
        batch.setTextureId(textureId);
        return batch;
    }

    private GlyphRenderBatch[] ensurePageBatchCapacity(FontType fontType, int minCapacity) {
        if (fontType == FontType.BOLD) {
            if (boldPageBatches.length < minCapacity) {
                boldPageBatches = grow(boldPageBatches, minCapacity);
                activeBoldPages = grow(activeBoldPages, minCapacity);
            }
            return boldPageBatches;
        }
        if (normalPageBatches.length < minCapacity) {
            normalPageBatches = grow(normalPageBatches, minCapacity);
            activeNormalPages = grow(activeNormalPages, minCapacity);
        }
        return normalPageBatches;
    }

    private boolean markPageActive(FontType fontType, int pageIndex) {
        boolean[] activePages = fontType == FontType.BOLD ? activeBoldPages : activeNormalPages;
        if (activePages[pageIndex]) {
            return false;
        }
        activePages[pageIndex] = true;
        return true;
    }

    private void appendActivePage(FontType fontType, int pageIndex) {
        if (activePageCount >= activePageIndices.length) {
            activePageIndices = grow(activePageIndices, activePageCount + 1);
            activePageTypes = grow(activePageTypes, activePageCount + 1);
        }
        activePageIndices[activePageCount] = pageIndex;
        activePageTypes[activePageCount] = fontType == FontType.BOLD ? ACTIVE_TYPE_BOLD : ACTIVE_TYPE_NORMAL;
        activePageCount++;
    }

    private GlyphRenderBatch getActiveBatch(int activeIndex) {
        if (activeIndex < 0 || activeIndex >= activePageCount) {
            return null;
        }
        int pageIndex = activePageIndices[activeIndex];
        GlyphRenderBatch[] batches = activePageTypes[activeIndex] == ACTIVE_TYPE_BOLD ? boldPageBatches
                : normalPageBatches;
        if (pageIndex < 0 || pageIndex >= batches.length) {
            return null;
        }
        return batches[pageIndex];
    }

    private void clearActiveMarker(int activeIndex) {
        int pageIndex = activePageIndices[activeIndex];
        boolean[] activePages = activePageTypes[activeIndex] == ACTIVE_TYPE_BOLD ? activeBoldPages
                : activeNormalPages;
        if (pageIndex >= 0 && pageIndex < activePages.length) {
            activePages[pageIndex] = false;
        }
    }

    private int bindTextureIfNeeded(int textureId, int boundTextureId) {
        if (boundTextureId != textureId) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            return textureId;
        }
        return boundTextureId;
    }

    private void setupUniforms(FontShaderProgram shaderProgram) {
        FloatBuffer modelViewUniform = modelViewBuffer;
        FloatBuffer projectionUniform = projectionBuffer;
        if (assumeInternalUiMatrices) {
            modelViewUniform = identityModelViewBuffer;
            projectionUniform = internalUiProjectionBuffer;
        } else {
            modelViewBuffer.clear();
            projectionBuffer.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
            modelViewBuffer.flip();
            projectionBuffer.flip();
        }

        shaderProgram.setUniformM4f("modelview", modelViewUniform);
        shaderProgram.setUniformM4f("projection", projectionUniform);
        shaderProgram.setUniformF("brightnessGain", (float) FontConfig.brightnessGain);
        shaderProgram.setUniformVec2("smoothRange", (float) FontConfig.smoothRangeMin,
                (float) FontConfig.smoothRangeMax);
        shaderProgram.setUniformI("aaMode", FontConfig.aaMode);
        shaderProgram.setUniformF("aaStrength", (float) (FontConfig.aaStrength / 120.0D));
    }

    private void renderBatch(FontShaderProgram shaderProgram, GlyphRenderBatch batch) {
        prepareBuffers(batch);
        if (FontRuntimeDiagnostics.shouldLogFlushState()) {
            FontRuntimeDiagnostics.logFlushState(shaderProgram.getShaderProgramId(),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    batch.getTextureId(),
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetError(),
                    batch.getQuadCount());
        }
        renderTool.render(vertexBuffer, indexBuffer, indexBuffer.limit());
    }

    private void recordLastFlushStats(int pageSubmitCount, int drawCallCount, int textureBindCount) {
        lastFlushPageSubmitCount = pageSubmitCount;
        lastFlushDrawCallCount = drawCallCount;
        lastFlushTextureBindCount = textureBindCount;
    }

    private void prepareBuffers(GlyphRenderBatch batch) {
        vertexBuffer = ensureFloatCapacity(vertexBuffer, batch.getVertexFloatCount());
        indexBuffer = ensureIntCapacity(indexBuffer, batch.getIndexCount());
        vertexBuffer.clear();
        indexBuffer.clear();
        batch.writeToBuffers(vertexBuffer, indexBuffer);

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

    private static GlyphRenderBatch[] grow(GlyphRenderBatch[] original, int minCapacity) {
        int nextCapacity = original.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        GlyphRenderBatch[] expanded = new GlyphRenderBatch[nextCapacity];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private static int[] grow(int[] original, int minCapacity) {
        int nextCapacity = original.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        int[] expanded = new int[nextCapacity];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private static byte[] grow(byte[] original, int minCapacity) {
        int nextCapacity = original.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        byte[] expanded = new byte[nextCapacity];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private static boolean[] grow(boolean[] original, int minCapacity) {
        int nextCapacity = original.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        boolean[] expanded = new boolean[nextCapacity];
        System.arraycopy(original, 0, expanded, 0, original.length);
        return expanded;
    }

    private static FloatBuffer createIdentityMatrixBuffer() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(1.0F).put(0.0F).put(0.0F).put(0.0F);
        buffer.put(0.0F).put(1.0F).put(0.0F).put(0.0F);
        buffer.put(0.0F).put(0.0F).put(1.0F).put(0.0F);
        buffer.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
        buffer.flip();
        return buffer;
    }

    private static void writeOrthoProjection(FloatBuffer buffer, int width, int height) {
        buffer.clear();
        buffer.put(2.0F / (float) width).put(0.0F).put(0.0F).put(0.0F);
        buffer.put(0.0F).put(-2.0F / (float) height).put(0.0F).put(0.0F);
        buffer.put(0.0F).put(0.0F).put(-0.001F).put(0.0F);
        buffer.put(-1.0F).put(1.0F).put(0.0F).put(1.0F);
        buffer.flip();
    }
}
