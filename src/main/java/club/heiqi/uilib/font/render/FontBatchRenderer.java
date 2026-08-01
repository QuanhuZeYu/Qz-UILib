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
import club.heiqi.uilib.font.FontRuntimeSettings;
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

    /**
     * ink 边缘 UV/几何协同外扩量（atlas 像素）。UV 与 quad 几何同步外扩该像素数，
     * 放宽 shader uvBounds 硬墙，让 mipmap 降采样时能采到 ink 子区外 padding 的自然渗透过渡，避免硬裁边。
     */
    private static final float INK_BLEED = 1.0F;

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
     * @param width  渲染目标宽度
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
     * <p>此路径把整个 slot 当作 ink 区域（inkLeftInSlot=0），UV 外扩会落到 slot 外 gap 而非羽化区，
     * 不参与 ink bleed 外扩契约。仅保留兼容性，新路径应使用 {@link #collectBaselineAlignedGlyph}。</p>
     *
     * @param fontType         字重类型
     * @param pageIndex        字符页索引
     * @param textureId        字符页纹理 ID
     * @param textureSize      字符页纹理边长
     * @param slotX            槽位 X
     * @param slotY            槽位 Y
     * @param slotWidth        槽位宽度
     * @param slotHeight       槽位高度
     * @param atlasBaselineX   槽位内基线 X
     * @param atlasBaselineY   槽位内基线 Y
     * @param lineBaselineY    默认字符格内文本基线 Y，atlas 像素，按 defaultGlyphSize 到 charSize 比例换算为显示像素
     * @param defaultGlyphSize 默认字符格大小
     * @param x                绘制起点 X
     * @param y                绘制起点 Y
     * @param charSize         字体显示尺寸
     * @param color            文本颜色
     * @param italic           是否斜体
     * @param glyphFlags       字形标记
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
            int atlasBaselineX,
            int atlasBaselineY,
            int lineBaselineY,
            int defaultGlyphSize,
            float x,
            float y,
            float charSize,
            int color,
            boolean italic,
            byte glyphFlags) {
        collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize, slotX, slotY, slotWidth, slotHeight,
                atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize, slotWidth, slotHeight,
                -atlasBaselineX, -atlasBaselineY, x, y, charSize, color, italic, glyphFlags);
    }

    /**
     * 按 atlas 基线契约收集一个 direct-index 定位的字符四边形到当前帧。
     *
     * @param fontType         字重类型
     * @param pageIndex        字符页索引
     * @param textureId        字符页纹理 ID
     * @param textureSize      字符页纹理边长
     * @param slotX            槽位 X
     * @param slotY            槽位 Y
     * @param slotWidth        槽位宽度
     * @param slotHeight       槽位高度
     * @param atlasBaselineX   槽位内基线 X
     * @param atlasBaselineY   槽位内基线 Y
     * @param lineBaselineY    默认字符格内文本基线 Y，atlas 像素，按 defaultGlyphSize 到 charSize 比例换算为显示像素
     * @param defaultGlyphSize 默认字符格大小
     * @param inkWidth         ink 区域宽度
     * @param inkHeight        ink 区域高度
     * @param bearingX         ink 左边缘相对基线 X 的偏移
     * @param bearingY         ink 上边缘相对基线 Y 的偏移
     * @param x                绘制起点 X
     * @param y                绘制起点 Y
     * @param charSize         字体显示尺寸
     * @param color            文本颜色
     * @param italic           是否斜体
     * @param glyphFlags       字形标记
     */
    public void collectBaselineAlignedGlyph(
            FontType fontType,
            int pageIndex,
            int textureId,
            int textureSize,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight,
            int atlasBaselineX,
            int atlasBaselineY,
            int lineBaselineY,
            int defaultGlyphSize,
            int inkWidth,
            int inkHeight,
            int bearingX,
            int bearingY,
            float x,
            float y,
            float charSize,
            int color,
            boolean italic,
            byte glyphFlags) {
        collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize, slotX, slotY, slotWidth, slotHeight,
                atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize, inkWidth, inkHeight, bearingX,
                bearingY, x, y, charSize, color, italic, glyphFlags,
                (float) FontRuntimeSettings.capture().getCharSize());
    }

    /**
     * 按 generation 基准字号收集字形，避免 italic 几何回读 live FontConfig。
     */
    public void collectBaselineAlignedGlyph(
            FontType fontType,
            int pageIndex,
            int textureId,
            int textureSize,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight,
            int atlasBaselineX,
            int atlasBaselineY,
            int lineBaselineY,
            int defaultGlyphSize,
            int inkWidth,
            int inkHeight,
            int bearingX,
            int bearingY,
            float x,
            float y,
            float charSize,
            int color,
            boolean italic,
            byte glyphFlags,
            float baseCharSize) {
        if (pageIndex < 0 || textureId <= 0 || textureSize <= 0 || slotWidth <= 0 || slotHeight <= 0
                || inkWidth <= 0 || inkHeight <= 0) {
            return;
        }
        initialize();

        GlyphQuadMetrics metrics = resolveGlyphQuadMetrics(textureSize, slotX, slotY, slotWidth, slotHeight,
                atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize, inkWidth, inkHeight, bearingX,
                bearingY, x, y, charSize);

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        float renderType = (glyphFlags & GlyphRuntimeTables.GLYPH_FLAG_COLORED) != 0
                ? GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH
                : GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH;
        GlyphRenderBatch batch = obtainPageBatch(fontType, pageIndex, textureId);
        batch.addQuad(metrics.quadX, metrics.quadY, z, metrics.renderWidth, metrics.renderHeight, italic, metrics.u0,
                metrics.u1, metrics.v0, metrics.v1, metrics.clipU0, metrics.clipU1, metrics.clipV0, metrics.clipV1,
                red, green, blue, alpha, renderType, baseCharSize);
        quadCount++;
    }

    /**
     * 按 atlas 基线契约计算单个字形的屏幕 quad 与 UV。
     *
     * @param textureSize      字符页纹理边长
     * @param slotX            槽位 X
     * @param slotY            槽位 Y
     * @param slotWidth        槽位宽度
     * @param slotHeight       槽位高度
     * @param atlasBaselineX   槽位内基线 X
     * @param atlasBaselineY   槽位内基线 Y
     * @param lineBaselineY    默认字符格内文本基线 Y，atlas 像素，按 defaultGlyphSize 到 charSize 比例换算为显示像素
     * @param defaultGlyphSize 默认字符格大小
     * @param inkWidth         ink 区域宽度
     * @param inkHeight        ink 区域高度
     * @param bearingX         ink 左边缘相对基线 X 的偏移
     * @param bearingY         ink 上边缘相对基线 Y 的偏移
     * @param x                绘制起点 X
     * @param y                绘制起点 Y
     * @param charSize         字体显示尺寸
     * @return 字形 quad 几何
     */
    static GlyphQuadMetrics resolveGlyphQuadMetrics(int textureSize, int slotX, int slotY, int slotWidth,
                                                     int slotHeight, int atlasBaselineX, int atlasBaselineY, int lineBaselineY, int defaultGlyphSize,
                                                     int inkWidth, int inkHeight, int bearingX, int bearingY, float x, float y, float charSize) {
        float resolvedTextureSize = (float) textureSize;
        float glyphScale = charSize / Math.max(1.0F, (float) defaultGlyphSize);
        float baselineY = y + ((float) lineBaselineY * glyphScale);
        float inkLeftInSlot = (float) (atlasBaselineX + bearingX);
        float inkTopInSlot = (float) (atlasBaselineY + bearingY);
        float quadX = x + ((float) bearingX * glyphScale);
        float quadY = baselineY + ((float) bearingY * glyphScale);
        float renderWidth = (float) inkWidth * glyphScale;
        float renderHeight = (float) inkHeight * glyphScale;
        // UV 与几何协同外扩 INK_BLEED 像素，放宽 uvBounds 硬墙，采到 ink 子区外 padding 的自然渗透过渡
        float bleedUv = INK_BLEED / resolvedTextureSize;
        float bleedGeometry = INK_BLEED * glyphScale;
        // 顶点 texCoord：ink 子区 ± INK_BLEED，用于 quad 几何与纹理对齐（mipmap 缩放时仍只覆盖 ink 邻域）
        float u0 = ((float) slotX + inkLeftInSlot) / resolvedTextureSize - bleedUv;
        float u1 = ((float) slotX + inkLeftInSlot + (float) inkWidth) / resolvedTextureSize + bleedUv;
        float v0 = ((float) slotY + inkTopInSlot) / resolvedTextureSize - bleedUv;
        float v1 = ((float) slotY + inkTopInSlot + (float) inkHeight) / resolvedTextureSize + bleedUv;
        // uvBounds（clip 边界）：整个 slot 范围（含完整 padding），放行 mipmap 降采样时落到 padding 羽化区的采样点，
        // 避免 safeSample 把物理存在的低 alpha 羽化尾巴当作越界归零导致硬裁边。
        // 注意：texCoord 与 uvBounds 语义不同，不要合并——前者决定 quad 纹理对齐，后者决定 shader 采样放行区间。
        float clipU0 = (float) slotX / resolvedTextureSize;
        float clipU1 = ((float) slotX + (float) slotWidth) / resolvedTextureSize;
        float clipV0 = (float) slotY / resolvedTextureSize;
        float clipV1 = ((float) slotY + (float) slotHeight) / resolvedTextureSize;
        quadX -= bleedGeometry;
        quadY -= bleedGeometry;
        renderWidth += 2.0F * bleedGeometry;
        renderHeight += 2.0F * bleedGeometry;
        return new GlyphQuadMetrics(u0, u1, v0, v1, clipU0, clipU1, clipV0, clipV1, quadX, quadY, renderWidth,
                renderHeight);
    }

    /**
     * 收集一个纯色文本装饰线矩形到当前帧。
     *
     * <p>装饰线不依附任何字符页，flush 时固定排在字形页批次之后，保持“字形先绘制、装饰线后覆盖”的旧语义。</p>
     *
     * @param x      起始 X
     * @param y      起始 Y
     * @param width  线条宽度
     * @param height 线条高度
     * @param color  文本颜色
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
        if (flushedQuadCount > 0 && FontRuntimeDiagnostics.shouldLogFlushBatchStats()) {
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

    /**
     * 字形屏幕 quad 与 UV 几何。
     *
     * <p>字段语义分两组，不可混淆：</p>
     * <ul>
     *   <li>u0/u1/v0/v1：顶点 texCoord 用的 ink ± INK_BLEED 边界，决定 quad 几何与纹理对齐。</li>
     *   <li>clipU0/clipU1/clipV0/clipV1：传给 shader 的 uvBounds clip 边界，用整个 slot 范围，
     *       放行 mipmap 降采样时落到 padding 羽化区的采样点，避免 safeSample 硬裁边。</li>
     * </ul>
     */
    static final class GlyphQuadMetrics {

        final float u0;
        final float u1;
        final float v0;
        final float v1;
        final float clipU0;
        final float clipU1;
        final float clipV0;
        final float clipV1;
        final float quadX;
        final float quadY;
        final float renderWidth;
        final float renderHeight;

        private GlyphQuadMetrics(float u0, float u1, float v0, float v1, float clipU0, float clipU1, float clipV0,
                                 float clipV1, float quadX, float quadY, float renderWidth, float renderHeight) {
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.clipU0 = clipU0;
            this.clipU1 = clipU1;
            this.clipV0 = clipV0;
            this.clipV1 = clipV1;
            this.quadX = quadX;
            this.quadY = quadY;
            this.renderWidth = renderWidth;
            this.renderHeight = renderHeight;
        }
    }
}
