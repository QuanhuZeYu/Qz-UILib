package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 字符页。
 */
public class GlyphPage {

    /**
     * 主线程专用零数据纹理缓冲。
     *
     * <p>{@link #ensureTexture()} 只在渲染主线程调用，因此无并发风险。
     * 按需扩容，避免每次创建新页都分配 + 填零一块 direct buffer。</p>
     */
    private static ByteBuffer renderThreadEmptyBuffer;

    private final int runtimeVersion;
    private final int pageIndex;
    private final int textureSize;
    private final int glyphSize;
    private final int lerpMode;
    private final int slotGap;
    private final GlApi gl;
    private ByteBuffer uploadBuffer;
    private int textureId;
    private int uncommittedTextureId;
    private int nextSlotIndex = 0;
    private SkylineNode skylineHead;
    private SlotReservation activeReservation;
    private boolean allocationClosed;
    private boolean batchActive;
    private boolean batchAttribPushed;
    private boolean batchClientAttribPushed;
    private boolean batchMipmapDirty;

    /**
     * 上传路径的精确服务器属性掩码。
     *
     * <p>上传只触碰纹理绑定 / texParameter / 纹理对象（{@code GL_TEXTURE_BIT}）与 client
     * unpack state（{@link #prepareUnpackState()}，由 {@code pushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT)}
     * 覆盖），无需 GL_ALL_ATTRIB_BITS 全量保存。texImage2D/texSubImage2D 修改的是纹理对象内容，
     * 不在 attrib 栈管辖范围内。</p>
     */
    private static final int UPLOAD_ATTRIB_MASK = GL11.GL_TEXTURE_BIT;

    /**
     * 创建字符页。
     *
     * @param pageIndex 页索引
     * @param textureSize 纹理边长
     * @param glyphSize 字符格大小
     */
    public GlyphPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize) {
        this(runtimeVersion, pageIndex, textureSize, glyphSize, FontRuntimeSettings.capture().getLerpMode());
    }

    /**
     * 创建绑定 generation 采样设置的字符页。
     *
     * @param runtimeVersion 运行时版本
     * @param pageIndex 页索引
     * @param textureSize 纹理边长
     * @param glyphSize 字符格大小
     * @param lerpMode atlas 采样模式
     */
    public GlyphPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize, int lerpMode) {
        this(runtimeVersion, pageIndex, textureSize, glyphSize, lerpMode, LwjglGlApi.INSTANCE);
    }

    GlyphPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize, int lerpMode, GlApi gl) {
        if (gl == null) {
            throw new IllegalArgumentException("gl facade 不得为 null");
        }
        this.runtimeVersion = runtimeVersion;
        this.pageIndex = pageIndex;
        this.textureSize = textureSize;
        this.glyphSize = glyphSize;
        this.lerpMode = lerpMode;
        this.slotGap = 1;
        this.gl = gl;

        uploadBuffer = null;
        textureId = 0;
        uncommittedTextureId = 0;
        skylineHead = createInitialSkyline();
    }

    /**
     * 判断当前页是否还能分配新槽位。
     *
     * @return 是否还能分配
     */
    public boolean canAllocate() {
        return canAllocate(1, 1);
    }

    /**
     * 判断当前页是否能容纳指定大小的槽位。
     *
     * @param slotWidth 槽位宽度
     * @param slotHeight 槽位高度
     * @return 是否能容纳
     */
    public boolean canAllocate(int slotWidth, int slotHeight) {
        return !allocationClosed && activeReservation == null && probeSlot(slotWidth, slotHeight).fits;
    }

    /**
     * 分配下一个可用槽位。
     *
     * @return 槽位信息
     */
    public GlyphSlot allocateSlot(int slotWidth, int slotHeight) {
        SlotReservation reservation = reserveSlot(slotWidth, slotHeight);
        reservation.commit();
        reservation.seal();
        return reservation.getSlot();
    }

    SlotReservation reserveSlot(int slotWidth, int slotHeight) {
        if (allocationClosed) {
            throw new IllegalStateException("字符页因 upload rollback 不完整而停止分配");
        }
        if (activeReservation != null) {
            throw new IllegalStateException("字符页已有未结算 slot reservation");
        }
        SlotProbe probe = probeSlot(slotWidth, slotHeight);
        if (!probe.fits) {
            throw new IllegalStateException("字符页容量不足");
        }
        SlotReservation reservation = new SlotReservation(this, nextSlotIndex, snapshotSkyline(),
                new GlyphSlot(nextSlotIndex, probe.x, probe.y, probe.width, probe.height));
        activeReservation = reservation;
        return reservation;
    }

    /**
     * 将字符图像上传到纹理页。
     *
     * @param slot 已分配槽位
     * @param token 请求 token
     * @param image 字符图像
     */
    public void upload(GlyphSlot slot, GlyphRequestToken token, BufferedImage image) {
        ByteBuffer pixels = image == null ? null : toByteBuffer(image);
        validateUpload(slot, token, image == null ? 0 : image.getWidth(), image == null ? 0 : image.getHeight(),
                pixels);
        uploadPixels(slot, token, pixels, image);
    }

    void upload(GlyphSlot slot, GlyphUploadPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("upload plan 不得为 null");
        }
        if (batchActive) {
            uploadInBatch(slot, plan);
            return;
        }
        GlyphRequestToken token = plan.getToken();
        ByteBuffer pixels = copyToUploadBuffer(plan.getRgbaPixels());
        validateUpload(slot, token, plan.getGlyphInfo().getSlotWidth(), plan.getGlyphInfo().getSlotHeight(), pixels);
        BufferedImage diagnosticImage = FontRuntimeDiagnostics.shouldLogGlyphUpload() ? plan.createImage() : null;
        uploadPixels(slot, token, pixels, diagnosticImage);
    }

    /** 清除已完成 GL 写入但未能发布 residency 的槽位；批内委托给批实现（不单独重建 mipmap）。 */
    void rollbackUploadedRegion(GlyphSlot slot) {
        if (batchActive) {
            rollbackUploadedRegionInBatch(slot);
            return;
        }
        boolean attribPushed = false;
        boolean clientAttribPushed = false;
        Throwable failure = null;
        try {
            requireNoGlError("upload_rollback_entry");
            gl.pushAttrib(UPLOAD_ATTRIB_MASK);
            requireNoGlError("upload_rollback_attrib_push");
            attribPushed = true;
            gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            requireNoGlError("upload_rollback_client_attrib_push");
            clientAttribPushed = true;
            clearUploadedRegionPixels(slot);
        } catch (RuntimeException exception) {
            failure = exception;
        } catch (Error error) {
            failure = error;
        } finally {
            Throwable restoreFailure = restoreGlState(clientAttribPushed, attribPushed);
            if (restoreFailure != null) {
                failure = appendFailure(failure, restoreFailure);
            }
        }
        if (failure != null) {
            allocationClosed = true;
            throwUnchecked(failure);
        }
    }

    boolean isBatchActive() {
        return batchActive;
    }

    /**
     * 进入批上传：push GL 状态、确保纹理、绑定并准备 unpack state。
     *
     * <p>批内上传不再逐 glyph push/pop 或重建 mipmap；{@link #endBatchUpload()} 统一结算。
     * 幂等：已处于批内时直接返回。</p>
     */
    void beginBatchUpload() {
        if (allocationClosed) {
            throw new IllegalStateException("字符页因 upload rollback 不完整而停止使用");
        }
        if (batchActive) {
            return;
        }
        requireNoGlError("batch_entry");
        Throwable failure = null;
        try {
            gl.pushAttrib(UPLOAD_ATTRIB_MASK);
            requireNoGlError("batch_attrib_push");
            batchAttribPushed = true;
            gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            requireNoGlError("batch_client_attrib_push");
            batchClientAttribPushed = true;
            ensureTexture();
            gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
            requireNoGlError("batch_bind");
            prepareUnpackState();
            requireNoGlError("batch_unpack_state");
            batchActive = true;
            batchMipmapDirty = false;
        } catch (RuntimeException exception) {
            failure = exception;
            throw exception;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            if (!batchActive) {
                Throwable restoreFailure = restoreGlState(batchClientAttribPushed, batchAttribPushed);
                batchAttribPushed = false;
                batchClientAttribPushed = false;
                if (restoreFailure != null) {
                    if (failure != null) {
                        failure.addSuppressed(restoreFailure);
                    } else {
                        throwUnchecked(restoreFailure);
                    }
                }
            }
        }
    }

    /**
     * 批内上传：仅校验并写入槽位像素，不做 attrib push/pop 与 mipmap 重建。
     */
    void uploadInBatch(GlyphSlot slot, GlyphUploadPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("upload plan 不得为 null");
        }
        if (!batchActive) {
            throw new IllegalStateException("字符页未处于批上传");
        }
        GlyphRequestToken token = plan.getToken();
        ByteBuffer pixels = copyToUploadBuffer(plan.getRgbaPixels());
        validateUpload(slot, token, plan.getGlyphInfo().getSlotWidth(), plan.getGlyphInfo().getSlotHeight(), pixels);
        BufferedImage diagnosticImage = FontRuntimeDiagnostics.shouldLogGlyphUpload() ? plan.createImage() : null;
        boolean pixelsWritten = false;
        try {
            gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
            requireNoGlError("batch_upload_bind");
            prepareUnpackState();
            requireNoGlError("batch_upload_unpack_state");
            gl.texSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    slot.getX(),
                    slot.getY(),
                    slot.getWidth(),
                    slot.getHeight(),
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels);
            pixelsWritten = true;
            requireNoGlError("batch_upload_pixels");
            batchMipmapDirty = true;
            if (FontRuntimeDiagnostics.shouldLogGlyphUpload()) {
                FontRuntimeDiagnostics.logGlyphUpload(token, textureId, true, GL11.GL_NO_ERROR,
                        diagnosticImage);
            }
        } catch (RuntimeException exception) {
            if (pixelsWritten && clearUploadedRegionInBatch(slot, exception)) {
                pixelsWritten = false;
            }
            throw exception;
        } catch (Error error) {
            if (pixelsWritten && clearUploadedRegionInBatch(slot, error)) {
                pixelsWritten = false;
            }
            throw error;
        }
    }

    /**
     * 结束批上传：统一重建 mipmap、校验纹理、解绑并恢复 GL 状态。
     *
     * <p>任何失败都会尽力恢复状态后以异常或 Error 抛出；调用方对失败页执行页级灾难恢复。</p>
     */
    void endBatchUpload() {
        if (!batchActive) {
            return;
        }
        Throwable failure = null;
        try {
            if (textureId != 0) {
                gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
                requireNoGlError("batch_end_bind");
                if (batchMipmapDirty) {
                    gl.generateMipmap(GL11.GL_TEXTURE_2D);
                    requireNoGlError("batch_mipmap");
                }
                boolean textureValid = gl.isTexture(textureId);
                requireNoGlError("batch_texture_validation");
                if (!textureValid) {
                    throw new GlyphUploadException("batch_texture_validation", 0, "GL 未确认 atlas texture 有效");
                }
                gl.bindTexture(GL11.GL_TEXTURE_2D, 0);
                requireNoGlError("batch_unbind");
            }
        } catch (RuntimeException exception) {
            failure = exception;
        } catch (Error error) {
            failure = error;
        } finally {
            batchActive = false;
            Throwable restoreFailure = restoreGlState(batchClientAttribPushed, batchAttribPushed);
            batchAttribPushed = false;
            batchClientAttribPushed = false;
            batchMipmapDirty = false;
            if (restoreFailure != null) {
                failure = appendFailure(failure, restoreFailure);
            }
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    /** 批内清除已完成 GL 写入但未能发布 residency 的槽位（不单独重建 mipmap，由批次结算统一处理）。 */
    void rollbackUploadedRegionInBatch(GlyphSlot slot) {
        if (!batchActive) {
            throw new IllegalStateException("字符页未处于批上传");
        }
        try {
            clearUploadedRegionPixelsInBatch(slot);
        } catch (RuntimeException exception) {
            allocationClosed = true;
            throw exception;
        } catch (Error error) {
            allocationClosed = true;
            throw error;
        }
    }

    private boolean clearUploadedRegionInBatch(GlyphSlot slot, Throwable originalFailure) {
        try {
            clearUploadedRegionPixelsInBatch(slot);
            return true;
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            allocationClosed = true;
            return false;
        } catch (Error cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            allocationClosed = true;
            return false;
        }
    }

    private void clearUploadedRegionPixelsInBatch(GlyphSlot slot) {
        gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
        requireNoGlError("batch_rollback_bind");
        prepareUnpackState();
        requireNoGlError("batch_rollback_unpack_state");
        ByteBuffer emptySlot = obtainRenderThreadEmptyBuffer(requiredRgbaBytes(slot.getWidth(), slot.getHeight()));
        gl.texSubImage2D(GL11.GL_TEXTURE_2D, 0, slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight(),
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, emptySlot);
        requireNoGlError("batch_rollback_pixels");
        batchMipmapDirty = true;
    }

    private void validateUpload(GlyphSlot slot, GlyphRequestToken token, int imageWidth, int imageHeight,
            ByteBuffer pixels) {
        if (slot == null || slot.getSlotIndex() < 0) {
            throw new IllegalStateException("字符未分配页槽位");
        }
        if (token == null || token.getGeneration() != runtimeVersion) {
            throw new IllegalArgumentException("字符请求 token 与 atlas generation 不一致");
        }
        if (pixels == null || imageWidth != slot.getWidth() || imageHeight != slot.getHeight()
                || pixels.remaining() != requiredRgbaBytes(slot.getWidth(), slot.getHeight())) {
            throw new IllegalArgumentException("字符图像尺寸与页槽位不一致");
        }
    }

    private void uploadPixels(GlyphSlot slot, GlyphRequestToken token, ByteBuffer pixels,
            BufferedImage diagnosticImage) {
        boolean logUploadDiagnostics = FontRuntimeDiagnostics.shouldLogGlyphUpload();
        boolean attribPushed = false;
        boolean clientAttribPushed = false;
        boolean pixelsWritten = false;
        Throwable failure = null;
        try {
            requireNoGlError("upload_entry");
            gl.pushAttrib(UPLOAD_ATTRIB_MASK);
            requireNoGlError("upload_attrib_push");
            attribPushed = true;
            gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            requireNoGlError("upload_client_attrib_push");
            clientAttribPushed = true;
            ensureTexture();
            gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
            requireNoGlError("upload_bind");
            prepareUnpackState();
            requireNoGlError("upload_unpack_state");
            gl.texSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    slot.getX(),
                    slot.getY(),
                    slot.getWidth(),
                    slot.getHeight(),
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels);
            pixelsWritten = true;
            requireNoGlError("upload_pixels");
            gl.generateMipmap(GL11.GL_TEXTURE_2D);
            requireNoGlError("upload_mipmap");
            boolean textureValid = gl.isTexture(textureId);
            requireNoGlError("upload_texture_validation");
            if (!textureValid) {
                throw new GlyphUploadException("upload_texture_validation", 0, "GL 未确认 atlas texture 有效");
            }
            if (logUploadDiagnostics) {
                FontRuntimeDiagnostics.logGlyphUpload(token, textureId, true, GL11.GL_NO_ERROR,
                        diagnosticImage);
            }
            gl.bindTexture(GL11.GL_TEXTURE_2D, 0);
            requireNoGlError("upload_unbind");
        } catch (RuntimeException exception) {
            failure = exception;
            if (pixelsWritten && clearUploadedRegion(slot, exception)) {
                pixelsWritten = false;
            }
            throw exception;
        } catch (Error error) {
            failure = error;
            if (pixelsWritten && clearUploadedRegion(slot, error)) {
                pixelsWritten = false;
            }
            throw error;
        } finally {
            Throwable restoreFailure = restoreGlState(clientAttribPushed, attribPushed);
            if (restoreFailure != null) {
                if (pixelsWritten) {
                    try {
                        rollbackUploadedRegion(slot);
                        pixelsWritten = false;
                    } catch (RuntimeException cleanupFailure) {
                        restoreFailure.addSuppressed(cleanupFailure);
                        allocationClosed = true;
                    } catch (Error cleanupFailure) {
                        restoreFailure.addSuppressed(cleanupFailure);
                        allocationClosed = true;
                    }
                }
                if (failure != null) {
                    failure.addSuppressed(restoreFailure);
                } else {
                    throwUnchecked(restoreFailure);
                }
            }
        }
    }

    /**
     * 释放纹理页资源。
     */
    public void close() {
        if (activeReservation != null) {
            throw new IllegalStateException("字符页存在未结算 slot reservation，不能关闭");
        }
        if (textureId == 0 && uncommittedTextureId == 0) {
            resetAllocator();
            return;
        }
        requireNoGlError("texture_close_entry");
        Throwable failure = null;
        if (textureId != 0) {
            int readyTexture = textureId;
            try {
                deleteTexture(readyTexture, "texture_close_ready");
                textureId = 0;
            } catch (RuntimeException exception) {
                failure = exception;
            } catch (Error error) {
                failure = error;
            }
        }
        if (uncommittedTextureId != 0) {
            int pendingTexture = uncommittedTextureId;
            try {
                deleteTexture(pendingTexture, "texture_close_uncommitted");
                uncommittedTextureId = 0;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            } catch (Error error) {
                failure = appendFailure(failure, error);
            }
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
        resetAllocator();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public int getTextureSize() {
        return textureSize;
    }

    public int getGlyphSize() {
        return glyphSize;
    }

    public int getTextureId() {
        return allocationClosed ? 0 : textureId;
    }

    /**
     * 获取或创建字符页纹理。
     *
     * @return 字符页纹理 ID
     */
    public int getOrCreateTextureId() {
        ensureTexture();
        return textureId;
    }

    private void ensureTexture() {
        if (allocationClosed) {
            throw new IllegalStateException("字符页因 upload rollback 不完整而停止使用");
        }
        if (textureId != 0) {
            return;
        }
        cleanupUncommittedTexture();
        requireNoGlError("texture_init_entry");
        int candidateTexture = gl.genTexture();
        if (candidateTexture != 0) {
            uncommittedTextureId = candidateTexture;
        }
        try {
            requireNoGlError("texture_generate");
            if (candidateTexture == 0) {
                throw new GlyphUploadException("texture_generate", 0, "glGenTextures 返回 0");
            }
            gl.bindTexture(GL11.GL_TEXTURE_2D, candidateTexture);
            requireNoGlError("texture_bind");
            ByteBuffer emptyTexture = obtainRenderThreadEmptyBuffer(requiredRgbaBytes(textureSize, textureSize));
            prepareUnpackState();
            requireNoGlError("texture_unpack_state");
            gl.texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureSize, textureSize, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, emptyTexture);
            requireNoGlError("texture_allocate");
            gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, getLerpMode());
            gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
            gl.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
            requireNoGlError("texture_parameters");
            boolean textureValid = gl.isTexture(candidateTexture);
            requireNoGlError("texture_validation");
            if (!textureValid) {
                throw new GlyphUploadException("texture_validation", 0, "GL 未确认新 atlas texture 有效");
            }
            gl.bindTexture(GL11.GL_TEXTURE_2D, 0);
            requireNoGlError("texture_unbind");
            textureId = candidateTexture;
            uncommittedTextureId = 0;
        } catch (RuntimeException exception) {
            rollbackUncommittedTexture(exception);
            throw exception;
        } catch (Error error) {
            rollbackUncommittedTexture(error);
            throw error;
        }
    }

    private void prepareUnpackState() {
        gl.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        gl.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        gl.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        gl.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        gl.pixelStore(GL11.GL_UNPACK_SWAP_BYTES, 0);
        gl.pixelStore(GL11.GL_UNPACK_LSB_FIRST, 0);
    }

    private int getLerpMode() {
        switch (lerpMode) {
            case 0:
                return GL11.GL_NEAREST_MIPMAP_NEAREST;
            case 1:
                return GL11.GL_LINEAR_MIPMAP_NEAREST;
            case 2:
                return GL11.GL_NEAREST_MIPMAP_LINEAR;
            default:
                return GL11.GL_LINEAR_MIPMAP_LINEAR;
        }
    }

    /**
     * 按 STB 同款 skyline bottom-left 策略寻找槽位。
     *
     * <p>天际线由按 X 排序的水平段链表维护（每段记录左端 X 与高度 Y）。分配时把
     * {@code slotGap} 并入占位尺寸（w+gap、h+gap），遍历各段左端点为候选 X，取覆盖区间
     * 天际线最低者（严格更低才替换，天然最左优先）。最低候选仍放不下则整页放不下。</p>
     */
    /**
     * 按 STB 同款 skyline bottom-left 策略寻找槽位。
     *
     * <p>天际线由按 X 排序的水平段链表维护，节点 {@code (x, y)} 表示区间
     * {@code [x, next.x)} 的天际线高度 {@code y}。分配时把 {@code slotGap} 并入占位宽度，
     * 遍历各段左端点为候选 X，取覆盖区间天际线最低者（严格更低才替换，天然最左优先）。
     * 最低候选仍放不下则整页放不下。页边缘不强制 gap：槽位自身尺寸判定容量，gap 仅抬升天际线。</p>
     */
    private SlotProbe probeSlot(int slotWidth, int slotHeight) {
        int safeWidth = Math.max(1, slotWidth);
        int safeHeight = Math.max(1, slotHeight);
        if (safeWidth > textureSize || safeHeight > textureSize) {
            return new SlotProbe(false, 0, 0, safeWidth, safeHeight);
        }
        int paddedWidth = safeWidth + slotGap;

        int bestX = -1;
        int bestY = Integer.MAX_VALUE;
        SkylineNode node = skylineHead;
        while (node.x + safeWidth <= textureSize) {
            int candidateY = maxSkylineY(node, node.x, paddedWidth);
            if (candidateY < bestY) {
                bestY = candidateY;
                bestX = node.x;
            }
            node = node.next;
        }
        if (bestX < 0 || bestY + safeHeight > textureSize) {
            return new SlotProbe(false, 0, 0, safeWidth, safeHeight);
        }
        return new SlotProbe(true, bestX, bestY, safeWidth, safeHeight);
    }

    /** 计算以 {@code from} 段左端 {@code x0} 起、宽 {@code width} 的覆盖区间内天际线最高值（右缘夹到页边）。 */
    private int maxSkylineY(SkylineNode from, int x0, int width) {
        int x1 = Math.min(x0 + width, textureSize);
        int maxY = 0;
        SkylineNode node = from;
        while (node.x < x1) {
            maxY = Math.max(maxY, node.y);
            node = node.next;
        }
        return maxY;
    }

    /**
     * 放置矩形后抬升天际线：把覆盖区间内各段合并为一条新水平段，右缘夹到页边。
     *
     * <p>尾节点高度继承下一段高度：{@code [x+width, next.x)} 属于未吞并区间，
     * 其天际线仍为下一段原高度。</p>
     *
     * @param x     占位矩形左端
     * @param y     占位矩形顶端
     * @param width 占位矩形宽度（含 gap）
     */
    private void addSkylineLevel(int x, int y, int width) {
        int levelRight = Math.min(x + width, textureSize);
        SkylineNode tail = skylineHead;
        while (tail.next != null && tail.next.x <= x) {
            tail = tail.next;
        }
        if (tail.x != x) {
            SkylineNode inserted = new SkylineNode(x, tail.y);
            inserted.next = tail.next;
            tail.next = inserted;
            tail = inserted;
        }
        int originalTailY = tail.y;
        tail.y = Math.max(tail.y, y);
        boolean merged = false;
        SkylineNode cursor = tail.next;
        while (cursor != null && cursor.x < levelRight) {
            merged = true;
            y = Math.max(cursor.y, y);
            tail.y = Math.max(tail.y, y);
            if (cursor.next != null && cursor.next.x > levelRight) {
                // 横跨占位右边界的段：截断为 [levelRight, next.x) 保留原高度，
                // 其左半 [cursor.x, levelRight) 已并入 tail 段。
                cursor.x = levelRight;
                break;
            }
            tail.next = cursor.next;
            cursor = tail.next;
        }
        if (tail.next == null || tail.next.x > levelRight) {
            // 占位完全落在 tail 段内（未吞并任何段）时，剩余右侧区间继承 tail 段抬升前高度；
            // 吞并后剩余区间由下一段表示，继承其高度。
            int endY = merged ? tail.next.y : originalTailY;
            SkylineNode end = new SkylineNode(levelRight, tail.next == null ? 0 : endY);
            end.next = tail.next;
            tail.next = end;
        }
    }

    /** 深拷贝当前天际线，供 reservation 回退恢复。 */
    private SkylineNode snapshotSkyline() {
        SkylineNode snapshotHead = null;
        SkylineNode snapshotTail = null;
        for (SkylineNode node = skylineHead; node != null; node = node.next) {
            SkylineNode copy = new SkylineNode(node.x, node.y);
            if (snapshotHead == null) {
                snapshotHead = copy;
            } else {
                snapshotTail.next = copy;
            }
            snapshotTail = copy;
        }
        return snapshotHead;
    }

    private void restoreSkyline(SkylineNode snapshot) {
        skylineHead = snapshot;
    }

    private SkylineNode createInitialSkyline() {
        SkylineNode head = new SkylineNode(0, 0);
        head.next = new SkylineNode(textureSize, 0);
        return head;
    }

    /** 诊断用：导出当前天际线段的 (x, y) 序列。 */
    String describeSkyline() {
        StringBuilder builder = new StringBuilder();
        for (SkylineNode node = skylineHead; node != null; node = node.next) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('(').append(node.x).append(',').append(node.y).append(')');
        }
        return builder.toString();
    }

    private ByteBuffer toByteBuffer(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = converted.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            image = converted;
        }

        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        int requiredCapacity = requiredRgbaBytes(image.getWidth(), image.getHeight());
        if (uploadBuffer == null || uploadBuffer.capacity() < requiredCapacity) {
            uploadBuffer = ByteBuffer.allocateDirect(requiredCapacity);
        }
        ByteBuffer buffer = uploadBuffer;
        buffer.clear();

        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();
        return buffer;
    }

    private ByteBuffer copyToUploadBuffer(ByteBuffer source) {
        if (source == null) {
            return null;
        }
        int requiredCapacity = source.remaining();
        if (uploadBuffer == null || uploadBuffer.capacity() < requiredCapacity) {
            uploadBuffer = ByteBuffer.allocateDirect(requiredCapacity);
        }
        uploadBuffer.clear();
        uploadBuffer.put(source);
        uploadBuffer.flip();
        return uploadBuffer;
    }

    private static ByteBuffer obtainRenderThreadEmptyBuffer(int requiredCapacity) {
        if (renderThreadEmptyBuffer != null && renderThreadEmptyBuffer.capacity() >= requiredCapacity) {
            renderThreadEmptyBuffer.clear();
            renderThreadEmptyBuffer.limit(requiredCapacity);
            return renderThreadEmptyBuffer;
        }
        renderThreadEmptyBuffer = createEmptyTextureBuffer(requiredCapacity);
        return renderThreadEmptyBuffer;
    }

    private static ByteBuffer createEmptyTextureBuffer(int capacity) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
        for (int index = 0; index < buffer.capacity(); index++) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        return buffer;
    }

    int getCommittedSlotCount() {
        return nextSlotIndex;
    }

    boolean hasTextureOwnership() {
        return textureId != 0 || uncommittedTextureId != 0;
    }

    boolean isAllocationClosed() {
        return allocationClosed;
    }

    private void resetAllocator() {
        nextSlotIndex = 0;
        skylineHead = createInitialSkyline();
        allocationClosed = false;
    }

    private void commitReservation(SlotReservation reservation) {
        if (activeReservation != reservation || reservation.page != this
                || nextSlotIndex != reservation.previousSlotIndex) {
            throw new IllegalStateException("slot reservation 提交时页 allocator 已变化");
        }
        GlyphSlot slot = reservation.slot;
        addSkylineLevel(slot.getX(), slot.getY() + slot.getHeight() + slotGap, slot.getWidth() + slotGap);
        nextSlotIndex = reservation.previousSlotIndex + 1;
        activeReservation = null;
    }

    private void rollbackReservation(SlotReservation reservation) {
        if (reservation.sealed) {
            throw new IllegalStateException("已发布的 slot reservation 不能回滚");
        }
        if (!reservation.committed) {
            if (activeReservation == reservation) {
                activeReservation = null;
            }
            return;
        }
        if (activeReservation != null || nextSlotIndex != reservation.previousSlotIndex + 1) {
            throw new IllegalStateException("slot reservation 回滚时页 allocator 已继续推进");
        }
        restoreSkyline(reservation.skylineSnapshot);
        nextSlotIndex = reservation.previousSlotIndex;
        reservation.committed = false;
    }

    private void cleanupUncommittedTexture() {
        if (uncommittedTextureId == 0) {
            return;
        }
        int candidateTexture = uncommittedTextureId;
        deleteTexture(candidateTexture, "texture_orphan_cleanup");
        uncommittedTextureId = 0;
    }

    private void rollbackUncommittedTexture(Throwable originalFailure) {
        if (uncommittedTextureId == 0) {
            return;
        }
        int candidateTexture = uncommittedTextureId;
        try {
            deleteTexture(candidateTexture, "texture_init_rollback");
            uncommittedTextureId = 0;
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        } catch (Error cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void deleteTexture(int targetTextureId, String phase) {
        gl.deleteTexture(targetTextureId);
        requireNoGlError(phase);
    }

    private boolean clearUploadedRegion(GlyphSlot slot, Throwable originalFailure) {
        try {
            clearUploadedRegionPixels(slot);
            return true;
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            allocationClosed = true;
            return false;
        } catch (Error cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            allocationClosed = true;
            return false;
        }
    }

    private void clearUploadedRegionPixels(GlyphSlot slot) {
        gl.bindTexture(GL11.GL_TEXTURE_2D, textureId);
        requireNoGlError("upload_rollback_bind");
        prepareUnpackState();
        requireNoGlError("upload_rollback_unpack_state");
        ByteBuffer emptySlot = obtainRenderThreadEmptyBuffer(requiredRgbaBytes(slot.getWidth(), slot.getHeight()));
        gl.texSubImage2D(GL11.GL_TEXTURE_2D, 0, slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight(),
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, emptySlot);
        requireNoGlError("upload_rollback_pixels");
        gl.generateMipmap(GL11.GL_TEXTURE_2D);
        requireNoGlError("upload_rollback_mipmap");
        gl.bindTexture(GL11.GL_TEXTURE_2D, 0);
        requireNoGlError("upload_rollback_unbind");
    }

    private Throwable restoreGlState(boolean clientAttribPushed, boolean attribPushed) {
        Throwable failure = null;
        if (clientAttribPushed) {
            try {
                gl.popClientAttrib();
            } catch (RuntimeException exception) {
                failure = exception;
            } catch (Error error) {
                failure = error;
            }
        }
        if (attribPushed) {
            try {
                gl.popAttrib();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            } catch (Error error) {
                failure = appendFailure(failure, error);
            }
        }
        try {
            requireNoGlError("upload_state_restore");
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        } catch (Error error) {
            failure = appendFailure(failure, error);
        }
        return failure;
    }

    private void requireNoGlError(String phase) {
        int glError = gl.getError();
        if (glError != GL11.GL_NO_ERROR) {
            throw new GlyphUploadException(phase, glError, "OpenGL error during glyph upload");
        }
    }

    private static Throwable appendFailure(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private static int requiredRgbaBytes(int width, int height) {
        long requiredBytes = (long) width * (long) height * 4L;
        if (width <= 0 || height <= 0 || requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("RGBA buffer 尺寸无效或超过 direct buffer 上限");
        }
        return (int) requiredBytes;
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError("unexpected checked throwable", throwable);
    }

    /**
     * 字符页内已分配槽位。
     */
    public static final class GlyphSlot {

        private final int slotIndex;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private GlyphSlot(int slotIndex, int x, int y, int width, int height) {
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    static final class SlotReservation {

        private final GlyphPage page;
        private final int previousSlotIndex;
        private final SkylineNode skylineSnapshot;
        private final GlyphSlot slot;
        private boolean committed;
        private boolean sealed;

        private SlotReservation(GlyphPage page, int previousSlotIndex, SkylineNode skylineSnapshot, GlyphSlot slot) {
            this.page = page;
            this.previousSlotIndex = previousSlotIndex;
            this.skylineSnapshot = skylineSnapshot;
            this.slot = slot;
        }

        GlyphSlot getSlot() {
            return slot;
        }

        void commit() {
            if (committed || sealed) {
                throw new IllegalStateException("slot reservation 已结算");
            }
            page.commitReservation(this);
            committed = true;
        }

        void rollback() {
            page.rollbackReservation(this);
        }

        void seal() {
            if (!committed || sealed) {
                throw new IllegalStateException("只有已提交 slot reservation 可以发布");
            }
            sealed = true;
        }
    }

    private static final class SlotProbe {

        private final boolean fits;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private SlotProbe(boolean fits, int x, int y, int width, int height) {
            this.fits = fits;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** 天际线水平段节点（左端 X、高度 Y、右邻链）；X 可被占位右边界截断。 */
    private static final class SkylineNode {

        private int x;
        private int y;
        private SkylineNode next;

        private SkylineNode(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    interface GlApi {

        void pushAttrib(int mask);

        void pushClientAttrib(int mask);

        void popClientAttrib();

        void popAttrib();

        int genTexture();

        void bindTexture(int target, int texture);

        void pixelStore(int parameter, int value);

        void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format,
                int type, ByteBuffer pixels);

        void texParameter(int target, int parameter, int value);

        void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
                ByteBuffer pixels);

        void generateMipmap(int target);

        boolean isTexture(int texture);

        void deleteTexture(int texture);

        int getError();
    }

    private static final class LwjglGlApi implements GlApi {

        private static final LwjglGlApi INSTANCE = new LwjglGlApi();

        @Override
        public void pushAttrib(int mask) {
            GL11.glPushAttrib(mask);
        }

        @Override
        public void pushClientAttrib(int mask) {
            GL11.glPushClientAttrib(mask);
        }

        @Override
        public void popClientAttrib() {
            GL11.glPopClientAttrib();
        }

        @Override
        public void popAttrib() {
            GL11.glPopAttrib();
        }

        @Override
        public int genTexture() {
            return GL11.glGenTextures();
        }

        @Override
        public void bindTexture(int target, int texture) {
            GL11.glBindTexture(target, texture);
        }

        @Override
        public void pixelStore(int parameter, int value) {
            GL11.glPixelStorei(parameter, value);
        }

        @Override
        public void texImage2D(int target, int level, int internalFormat, int width, int height, int border,
                int format, int type, ByteBuffer pixels) {
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        }

        @Override
        public void texParameter(int target, int parameter, int value) {
            GL11.glTexParameteri(target, parameter, value);
        }

        @Override
        public void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
                ByteBuffer pixels) {
            GL11.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels);
        }

        @Override
        public void generateMipmap(int target) {
            GL30.glGenerateMipmap(target);
        }

        @Override
        public boolean isTexture(int texture) {
            return GL11.glIsTexture(texture);
        }

        @Override
        public void deleteTexture(int texture) {
            GL11.glDeleteTextures(texture);
        }

        @Override
        public int getError() {
            return GL11.glGetError();
        }
    }

    static final class GlyphUploadException extends IllegalStateException {

        private static final long serialVersionUID = 1L;
        private final String phase;
        private final int glError;

        private GlyphUploadException(String phase, int glError, String message) {
            super(message + ": phase=" + phase + " glError=" + glError);
            this.phase = phase;
            this.glError = glError;
        }

        String getPhase() {
            return phase;
        }

        int getGlError() {
            return glError;
        }
    }
}
