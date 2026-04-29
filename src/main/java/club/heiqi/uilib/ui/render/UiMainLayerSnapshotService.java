package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 当前 UI 主层的同帧快照服务。
 *
 * <p>该服务只属于渲染后端，用于让 `backdrop-filter` 元素在当前 UI 主层内容未变化时复用已复制纹理。
 * 同帧已捕获的较大 block 区域也可作为临时 atlas，供后续被其覆盖的较小区域继续采样。
 * 一旦两次 backdrop 之间有新的 UI 绘制写入，就必须重新捕获，避免后续元素采样到旧主层。
 * 文档作者层仍只暴露 CSS-like backdrop 语义，不接触纹理、FBO 或 OpenGL 状态。</p>
 */
public final class UiMainLayerSnapshotService {

    private static final int MAX_SNAPSHOT_EDGE = 4096;
    private static final int MAX_SNAPSHOT_PIXELS = 4096 * 4096;
    private static final int SNAPSHOT_BLOCK_SIZE = 128;
    private static final int MAX_DOWNSAMPLE_FACTOR = 4;
    private static final int MEDIUM_BLUR_DOWNSAMPLE_THRESHOLD = 18;
    private static final int LARGE_BLUR_DOWNSAMPLE_THRESHOLD = 34;
    private static final float[][] FILTER_BLUR_SAMPLES = new float[][] {
            { 0.0F, 0.40F },
            { -1.0F, 0.24F },
            { 1.0F, 0.24F },
            { -2.0F, 0.06F },
            { 2.0F, 0.06F }
    };

    private final List<FrameSnapshot> snapshots = new ArrayList<FrameSnapshot>();
    private int frameId;
    private boolean frameActive;
    private boolean disabledForFrame;
    private boolean filterPassDisabledForFrame;
    private String lastFailureDetail = "not-run";
    private String lastFilterPassFailureDetail = "";

    /**
     * 开始新一帧快照复用窗口。
     */
    public void beginFrame() {
        frameActive = true;
        disabledForFrame = false;
        filterPassDisabledForFrame = false;
        lastFailureDetail = "not-run";
        lastFilterPassFailureDetail = "";
        if (frameId == Integer.MAX_VALUE) {
            frameId = 0;
            for (FrameSnapshot snapshot : snapshots) {
                snapshot.capturedFrameId = 0;
            }
        }
        frameId++;
        for (FrameSnapshot snapshot : snapshots) {
            snapshot.activeUseCount = 0;
        }
    }

    /**
     * 结束当前帧。
     */
    public void finishFrame() {
        frameActive = false;
        for (FrameSnapshot snapshot : snapshots) {
            snapshot.activeUseCount = 0;
        }
    }

    /**
     * 释放服务持有的纹理资源。
     */
    public void close() {
        finishFrame();
        for (FrameSnapshot snapshot : snapshots) {
            closeSnapshot(snapshot);
        }
        snapshots.clear();
    }

    /**
     * 获取当前帧可复用的主 UI 层快照。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param requestedReadFramebufferId 指定读取 FBO；小于 0 时使用当前 read framebuffer
     * @return 快照；获取失败时返回 null
     */
    Snapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId) {
        return acquireSnapshot(screenWidth, screenHeight, requestedReadFramebufferId, 0);
    }

    /**
     * 获取当前帧可复用的主 UI 层快照。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param requestedReadFramebufferId 指定读取 FBO；小于 0 时使用当前 read framebuffer
     * @param contentRevision 当前读取目标的内容版本
     * @return 快照；获取失败时返回 null
     */
    Snapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId, int contentRevision) {
        return acquireSnapshot(screenWidth, screenHeight, requestedReadFramebufferId, contentRevision,
                resolveFullScreenSampleRegion(screenWidth, screenHeight));
    }

    /**
     * 获取当前帧可复用的局部主 UI 层快照。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param requestedReadFramebufferId 指定读取 FBO；小于 0 时使用当前 read framebuffer
     * @param contentRevision 当前读取目标的内容版本
     * @param sampleRegion 需要复制的 UI 采样区域
     * @return 快照；获取失败时返回 null
     */
    Snapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId, int contentRevision,
            SampleRegion sampleRegion) {
        return acquireSnapshot(screenWidth, screenHeight, requestedReadFramebufferId, contentRevision, sampleRegion, 0);
    }

    /**
     * 获取当前帧可复用的局部主 UI 层滤镜快照。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param requestedReadFramebufferId 指定读取 FBO；小于 0 时使用当前 read framebuffer
     * @param contentRevision 当前读取目标的内容版本
     * @param sampleRegion 需要复制的 UI 采样区域
     * @param blurRadius 当前 backdrop 模糊半径，用于决定是否降采样
     * @return 快照；获取失败时返回 null
     */
    Snapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId, int contentRevision,
            SampleRegion sampleRegion, int blurRadius) {
        if (sampleRegion == null || !isSnapshotRegionWithinScreen(screenWidth, screenHeight, sampleRegion)) {
            lastFailureDetail = "snapshot-region-invalid";
            return null;
        }
        if (!isSnapshotSizeAllowed(sampleRegion)) {
            lastFailureDetail = "snapshot-too-large: " + sampleRegion.getWidth() + "x" + sampleRegion.getHeight();
            return null;
        }
        if (disabledForFrame) {
            lastFailureDetail = "disabled-for-frame";
            return null;
        }
        if (!frameActive) {
            beginFrame();
        }

        SampleRegion reusableRegion = resolveReusableSampleRegion(screenWidth, screenHeight, sampleRegion);
        String regionDetail = formatRegionDetail(sampleRegion, reusableRegion);
        int readFramebufferId = resolveReadFramebufferId(requestedReadFramebufferId);
        int downsampleFactor = resolveDownsampleFactor(blurRadius);
        FrameSnapshotMatch capturedSnapshotMatch = findCapturedSnapshot(readFramebufferId, reusableRegion,
                contentRevision, downsampleFactor, blurRadius);
        if (capturedSnapshotMatch != null) {
            FrameSnapshot capturedSnapshot = capturedSnapshotMatch.snapshot;
            capturedSnapshot.activeUseCount++;
            SampleRegion capturedRegion = toSampleRegion(capturedSnapshot);
            String matchedRegionDetail = capturedSnapshotMatch.exactMatch ? capturedSnapshot.regionDetail
                    : formatAtlasRegionDetail(capturedSnapshot.regionDetail);
            String tileDetail = formatTileDetail(reusableRegion, true);
            return Snapshot.reused(capturedSnapshot.textureId, capturedRegion, readFramebufferId, contentRevision,
                    capturedSnapshot.textureWidth, capturedSnapshot.textureHeight, capturedSnapshot.downsampleFactor,
                    capturedSnapshot.filterDetail, matchedRegionDetail, tileDetail);
        }

        FrameSnapshot snapshot = findReusableSnapshot();
        if (snapshot == null) {
            snapshot = new FrameSnapshot();
            snapshots.add(snapshot);
        }
        String tileDetail = formatTileDetail(reusableRegion, false);
        if (!captureSnapshot(snapshot, screenHeight, reusableRegion, readFramebufferId, contentRevision,
                downsampleFactor, blurRadius, regionDetail, tileDetail)) {
            return null;
        }
        return Snapshot.captured(snapshot.textureId, reusableRegion, readFramebufferId, contentRevision,
                snapshot.textureWidth, snapshot.textureHeight, snapshot.downsampleFactor, snapshot.filterDetail,
                snapshot.regionDetail, snapshot.tileDetail);
    }

    /**
     * 释放当前绘制调用持有的快照使用权。
     *
     * @param snapshot 快照
     */
    void releaseSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (FrameSnapshot frameSnapshot : snapshots) {
            if (frameSnapshot.textureId == snapshot.textureId && frameSnapshot.activeUseCount > 0) {
                frameSnapshot.activeUseCount--;
                return;
            }
        }
    }

    /**
     * 返回最近一次失败说明。
     *
     * @return 失败说明
     */
    String getLastFailureDetail() {
        return lastFailureDetail;
    }

    /**
     * 按 backdrop 半径解析扩张后的采样区域。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param left 元素左侧
     * @param top 元素顶部
     * @param right 元素右侧
     * @param bottom 元素底部
     * @param blurRadius 模糊半径
     * @return 采样区域；无有效采样区域时返回 null
     */
    static SampleRegion resolveSampleRegion(int screenWidth, int screenHeight, int left, int top, int right,
            int bottom, int blurRadius) {
        int sampleInset = Math.max(1, Math.min(64, blurRadius + resolveSampleStep(blurRadius)));
        int sampleLeft = clampInt(left - sampleInset, 0, screenWidth);
        int sampleTop = clampInt(top - sampleInset, 0, screenHeight);
        int sampleRight = clampInt(right + sampleInset, 0, screenWidth);
        int sampleBottom = clampInt(bottom + sampleInset, 0, screenHeight);
        if (sampleRight <= sampleLeft || sampleBottom <= sampleTop) {
            return null;
        }
        return new SampleRegion(sampleLeft, sampleTop, sampleRight, sampleBottom);
    }

    /**
     * 将局部采样区域扩展到固定 block 边界，提升相近 glass 元素的快照复用率。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param sampleRegion 原始采样区域
     * @return block 对齐后的采样区域；无法对齐时返回原始区域
     */
    static SampleRegion resolveBlockAlignedSampleRegion(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        if (sampleRegion == null) {
            return null;
        }
        int sampleLeft = alignDown(sampleRegion.getLeft(), SNAPSHOT_BLOCK_SIZE);
        int sampleTop = alignDown(sampleRegion.getTop(), SNAPSHOT_BLOCK_SIZE);
        int sampleRight = alignUp(sampleRegion.getRight(), SNAPSHOT_BLOCK_SIZE);
        int sampleBottom = alignUp(sampleRegion.getBottom(), SNAPSHOT_BLOCK_SIZE);
        int alignedLeft = clampInt(sampleLeft, 0, screenWidth);
        int alignedTop = clampInt(sampleTop, 0, screenHeight);
        int alignedRight = clampInt(sampleRight, 0, screenWidth);
        int alignedBottom = clampInt(sampleBottom, 0, screenHeight);
        if (alignedRight <= alignedLeft || alignedBottom <= alignedTop) {
            return sampleRegion;
        }
        return new SampleRegion(alignedLeft, alignedTop, alignedRight, alignedBottom);
    }

    /**
     * 判断指定快照尺寸是否在当前保护限制内。
     *
     * @param width 快照宽度
     * @param height 快照高度
     * @return 是否允许创建快照
     */
    static boolean isSnapshotSizeAllowed(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (width > MAX_SNAPSHOT_EDGE || height > MAX_SNAPSHOT_EDGE) {
            return false;
        }
        return (long) width * (long) height <= MAX_SNAPSHOT_PIXELS;
    }

    /**
     * 判断局部采样区域尺寸是否在当前保护限制内。
     *
     * @param sampleRegion 采样区域
     * @return 是否允许创建快照
     */
    static boolean isSnapshotSizeAllowed(SampleRegion sampleRegion) {
        return sampleRegion != null && isSnapshotSizeAllowed(sampleRegion.getWidth(), sampleRegion.getHeight());
    }

    /**
     * 将 top-left UI 坐标系中的采样区域转换为 OpenGL copy 的源 Y 坐标。
     *
     * @param screenHeight 屏幕高度
     * @param sampleRegion 采样区域
     * @return OpenGL 底部原点坐标系中的源 Y
     */
    static int resolveCopySourceY(int screenHeight, SampleRegion sampleRegion) {
        if (sampleRegion == null) {
            return 0;
        }
        return screenHeight - sampleRegion.getBottom();
    }

    /**
     * 按模糊半径决定滤镜快照降采样倍率。
     *
     * @param blurRadius 模糊半径
     * @return 降采样倍率
     */
    static int resolveDownsampleFactor(int blurRadius) {
        if (blurRadius < MEDIUM_BLUR_DOWNSAMPLE_THRESHOLD) {
            return 1;
        }
        if (blurRadius < LARGE_BLUR_DOWNSAMPLE_THRESHOLD) {
            return 2;
        }
        return MAX_DOWNSAMPLE_FACTOR;
    }

    /**
     * 计算降采样后的纹理边长。
     *
     * @param sourceSize 原始边长
     * @param downsampleFactor 降采样倍率
     * @return 降采样后边长
     */
    static int resolveDownsampledSize(int sourceSize, int downsampleFactor) {
        int safeFactor = Math.max(1, Math.min(MAX_DOWNSAMPLE_FACTOR, downsampleFactor));
        return Math.max(1, (Math.max(1, sourceSize) + safeFactor - 1) / safeFactor);
    }

    /**
     * 计算降采样滤镜 pass 内部使用的 separable blur 半径。
     *
     * @param blurRadius 作者侧 blur 半径
     * @param downsampleFactor 降采样倍率
     * @return filter pass 半径；为 0 表示不需要独立 blur pass
     */
    static int resolveFilterPassRadius(int blurRadius, int downsampleFactor) {
        if (blurRadius <= 0 || downsampleFactor <= 1) {
            return 0;
        }
        return Math.max(1, Math.min(8, Math.round((float) blurRadius / (float) (downsampleFactor * 5))));
    }

    /**
     * 将采样区域映射到 128px tile 网格。
     *
     * @param sampleRegion 采样区域
     * @return tile 区域；无效区域返回空 tile 区域
     */
    static TileRegion resolveTileRegion(SampleRegion sampleRegion) {
        if (sampleRegion == null || sampleRegion.getRight() <= sampleRegion.getLeft()
                || sampleRegion.getBottom() <= sampleRegion.getTop()) {
            return new TileRegion(0, 0, 0, 0);
        }
        return new TileRegion(sampleRegion.getLeft() / SNAPSHOT_BLOCK_SIZE,
                sampleRegion.getTop() / SNAPSHOT_BLOCK_SIZE,
                alignUp(sampleRegion.getRight(), SNAPSHOT_BLOCK_SIZE) / SNAPSHOT_BLOCK_SIZE,
                alignUp(sampleRegion.getBottom(), SNAPSHOT_BLOCK_SIZE) / SNAPSHOT_BLOCK_SIZE);
    }

    /**
     * 计算采样区域覆盖的 tile 数量。
     *
     * @param sampleRegion 采样区域
     * @return tile 数量
     */
    static int resolveTileCount(SampleRegion sampleRegion) {
        return resolveTileRegion(sampleRegion).getTileCount();
    }

    private FrameSnapshotMatch findCapturedSnapshot(int readFramebufferId, SampleRegion sampleRegion,
            int contentRevision, int downsampleFactor, int blurRadius) {
        FrameSnapshot containingSnapshot = null;
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.capturedFrameId == frameId && snapshot.readFramebufferId == readFramebufferId
                    && snapshot.contentRevision == contentRevision
                    && snapshot.requestedDownsampleFactor == downsampleFactor && snapshot.blurRadius == blurRadius
                    && snapshot.textureId != 0) {
                SampleRegion capturedRegion = toSampleRegion(snapshot);
                if (isSameSampleRegion(capturedRegion, sampleRegion)) {
                    return new FrameSnapshotMatch(snapshot, true);
                }
                if (containsSampleRegion(capturedRegion, sampleRegion)
                        && isBetterContainingSnapshot(containingSnapshot, snapshot)) {
                    containingSnapshot = snapshot;
                }
            }
        }
        if (containingSnapshot == null) {
            return null;
        }
        return new FrameSnapshotMatch(containingSnapshot, false);
    }

    private FrameSnapshot findReusableSnapshot() {
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.activeUseCount <= 0 && snapshot.capturedFrameId != frameId) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean captureSnapshot(FrameSnapshot snapshot, int screenHeight, SampleRegion sampleRegion,
            int readFramebufferId, int contentRevision, int requestedDownsampleFactor, int blurRadius,
            String regionDetail, String tileDetail) {
        int width = sampleRegion.getWidth();
        int height = sampleRegion.getHeight();
        int previousTexture = 0;
        int previousReadFramebufferId = -1;
        int previousDrawFramebufferId = -1;
        int previousActiveTexture = GL13.GL_TEXTURE0;
        int previousProgram = 0;
        IntBuffer previousViewport = BufferUtils.createIntBuffer(16);
        boolean textureBindingCaptured = false;
        boolean readFramebufferCaptured = false;
        boolean drawFramebufferCaptured = false;
        boolean activeTextureCaptured = false;
        boolean programCaptured = false;
        boolean viewportCaptured = false;
        boolean attribCaptured = false;
        try {
            previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            activeTextureCaptured = true;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureBindingCaptured = true;
            previousReadFramebufferId = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            readFramebufferCaptured = true;
            previousDrawFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            drawFramebufferCaptured = true;
            previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            programCaptured = true;
            GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
            viewportCaptured = true;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribCaptured = true;
            if (snapshot.sourceTextureId == 0) {
                snapshot.sourceTextureId = GL11.glGenTextures();
                if (snapshot.sourceTextureId == 0) {
                    lastFailureDetail = "texture-allocation-failed";
                    return false;
                }
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.sourceTextureId);
            configureLinearTexture();
            if (snapshot.sourceWidth != width || snapshot.sourceHeight != height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                snapshot.sourceWidth = width;
                snapshot.sourceHeight = height;
            }
            if (previousReadFramebufferId != readFramebufferId) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferId);
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, sampleRegion.getLeft(),
                    resolveCopySourceY(screenHeight, sampleRegion), width, height);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

            int downsampleFactor = resolveEffectiveDownsampleFactor(width, height, requestedDownsampleFactor);
            snapshot.textureId = snapshot.sourceTextureId;
            snapshot.textureWidth = width;
            snapshot.textureHeight = height;
            snapshot.requestedDownsampleFactor = requestedDownsampleFactor;
            snapshot.downsampleFactor = 1;
            snapshot.blurRadius = blurRadius;
            snapshot.filterPassRadius = 0;
            snapshot.filterDetail = "raw";
            snapshot.regionDetail = regionDetail;
            snapshot.tileDetail = tileDetail;
            if (downsampleFactor > 1) {
                if (downsampleSnapshot(snapshot, downsampleFactor, blurRadius)) {
                    snapshot.filterDetail = "downsample" + snapshot.downsampleFactor + " "
                            + snapshot.textureWidth + "x" + snapshot.textureHeight
                            + "+sepBlur" + snapshot.filterPassRadius;
                } else if (!lastFilterPassFailureDetail.isEmpty()) {
                    snapshot.filterDetail = "raw, filter-unavailable=" + lastFilterPassFailureDetail;
                }
            }

            snapshot.sampleLeft = sampleRegion.getLeft();
            snapshot.sampleTop = sampleRegion.getTop();
            snapshot.width = width;
            snapshot.height = height;
            snapshot.readFramebufferId = readFramebufferId;
            snapshot.contentRevision = contentRevision;
            snapshot.capturedFrameId = frameId;
            snapshot.activeUseCount = 1;
            return true;
        } catch (RuntimeException exception) {
            disableForCurrentFrame("snapshot-copy-failed: " + exception.getClass().getSimpleName());
            return false;
        } catch (LinkageError error) {
            disableForCurrentFrame("snapshot-copy-failed: " + error.getClass().getSimpleName());
            return false;
        } finally {
            if (attribCaptured) {
                GL11.glPopAttrib();
            }
            if (programCaptured) {
                GL20.glUseProgram(previousProgram);
            }
            if (viewportCaptured) {
                GL11.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2),
                        previousViewport.get(3));
            }
            if (drawFramebufferCaptured) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebufferId);
            }
            if (readFramebufferCaptured) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebufferId);
            }
            if (textureBindingCaptured) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
            if (activeTextureCaptured) {
                GL13.glActiveTexture(previousActiveTexture);
            }
        }
    }

    private boolean downsampleSnapshot(FrameSnapshot snapshot, int downsampleFactor, int blurRadius) {
        if (filterPassDisabledForFrame) {
            return false;
        }
        int targetWidth = resolveDownsampledSize(snapshot.sourceWidth, downsampleFactor);
        int targetHeight = resolveDownsampledSize(snapshot.sourceHeight, downsampleFactor);
        if (targetWidth >= snapshot.sourceWidth && targetHeight >= snapshot.sourceHeight) {
            return false;
        }
        int filterPassRadius = resolveFilterPassRadius(blurRadius, downsampleFactor);

        try {
            ensureDownsampleTarget(snapshot, targetWidth, targetHeight);
            if (filterPassRadius <= 0) {
                renderFilterPass(snapshot, snapshot.sourceTextureId, snapshot.filteredTextureId, snapshot.sourceWidth,
                        snapshot.sourceHeight, targetWidth, targetHeight, 0, true);
            } else {
                renderFilterPass(snapshot, snapshot.sourceTextureId, snapshot.intermediateTextureId,
                        snapshot.sourceWidth, snapshot.sourceHeight, targetWidth, targetHeight, filterPassRadius, true);
                renderFilterPass(snapshot, snapshot.intermediateTextureId, snapshot.filteredTextureId,
                        targetWidth, targetHeight, targetWidth, targetHeight, filterPassRadius, false);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.filteredTextureId);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            snapshot.textureId = snapshot.filteredTextureId;
            snapshot.textureWidth = targetWidth;
            snapshot.textureHeight = targetHeight;
            snapshot.downsampleFactor = downsampleFactor;
            snapshot.filterPassRadius = filterPassRadius;
            return true;
        } catch (RuntimeException exception) {
            disableFilterPassForCurrentFrame(exception.getClass().getSimpleName());
            return false;
        } catch (LinkageError error) {
            disableFilterPassForCurrentFrame(error.getClass().getSimpleName());
            return false;
        }
    }

    private void renderFilterPass(FrameSnapshot snapshot, int inputTextureId, int outputTextureId, int inputWidth,
            int inputHeight, int outputWidth, int outputHeight, int filterPassRadius, boolean horizontal) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.filterFramebufferId);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                outputTextureId, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            disableFilterPassForCurrentFrame("fbo-incomplete:" + status);
            throw new IllegalStateException("filter pass fbo incomplete: " + status);
        }

        GL20.glUseProgram(0);
        GL11.glViewport(0, 0, outputWidth, outputHeight);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTextureId);
        if (filterPassRadius <= 0) {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawFilterTextureQuad(outputWidth, outputHeight, 0.0F, 0.0F);
            return;
        }

        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        for (float[] sample : FILTER_BLUR_SAMPLES) {
            float weight = sample[1];
            float offsetPixels = sample[0] * (float) filterPassRadius;
            float offsetU = horizontal ? offsetPixels / (float) Math.max(1, inputWidth) : 0.0F;
            float offsetV = horizontal ? 0.0F : offsetPixels / (float) Math.max(1, inputHeight);
            GL11.glColor4f(weight, weight, weight, weight);
            drawFilterTextureQuad(outputWidth, outputHeight, offsetU, offsetV);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void ensureDownsampleTarget(FrameSnapshot snapshot, int targetWidth, int targetHeight) {
        if (snapshot.filteredTextureId == 0) {
            snapshot.filteredTextureId = GL11.glGenTextures();
            if (snapshot.filteredTextureId == 0) {
                throw new IllegalStateException("filter texture allocation failed");
            }
        }
        if (snapshot.intermediateTextureId == 0) {
            snapshot.intermediateTextureId = GL11.glGenTextures();
            if (snapshot.intermediateTextureId == 0) {
                throw new IllegalStateException("filter intermediate texture allocation failed");
            }
        }
        if (snapshot.filterFramebufferId == 0) {
            snapshot.filterFramebufferId = GL30.glGenFramebuffers();
            if (snapshot.filterFramebufferId == 0) {
                throw new IllegalStateException("filter framebuffer allocation failed");
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.filteredTextureId);
        configureLinearTexture();
        if (snapshot.filteredWidth != targetWidth || snapshot.filteredHeight != targetHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, targetWidth, targetHeight, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            snapshot.filteredWidth = targetWidth;
            snapshot.filteredHeight = targetHeight;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.intermediateTextureId);
        configureLinearTexture();
        if (snapshot.intermediateWidth != targetWidth || snapshot.intermediateHeight != targetHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, targetWidth, targetHeight, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            snapshot.intermediateWidth = targetWidth;
            snapshot.intermediateHeight = targetHeight;
        }
    }

    private static void configureLinearTexture() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void drawFilterTextureQuad(int targetWidth, int targetHeight, float offsetU, float offsetV) {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean projectionPushed = false;
        boolean modelViewPushed = false;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, (double) targetWidth, (double) targetHeight, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelViewPushed = true;
            GL11.glLoadIdentity();

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(offsetU, offsetV);
            GL11.glVertex2f(0.0F, (float) targetHeight);
            GL11.glTexCoord2f(1.0F + offsetU, offsetV);
            GL11.glVertex2f((float) targetWidth, (float) targetHeight);
            GL11.glTexCoord2f(1.0F + offsetU, 1.0F + offsetV);
            GL11.glVertex2f((float) targetWidth, 0.0F);
            GL11.glTexCoord2f(offsetU, 1.0F + offsetV);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glEnd();
        } finally {
            if (modelViewPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projectionPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private void disableForCurrentFrame(String detail) {
        disabledForFrame = true;
        lastFailureDetail = detail;
    }

    private void disableFilterPassForCurrentFrame(String detail) {
        if (filterPassDisabledForFrame && !lastFilterPassFailureDetail.isEmpty()) {
            return;
        }
        filterPassDisabledForFrame = true;
        lastFilterPassFailureDetail = detail == null ? "unknown" : detail;
    }

    private void closeSnapshot(FrameSnapshot snapshot) {
        if (snapshot.sourceTextureId != 0) {
            GL11.glDeleteTextures(snapshot.sourceTextureId);
            snapshot.sourceTextureId = 0;
        }
        if (snapshot.filteredTextureId != 0) {
            GL11.glDeleteTextures(snapshot.filteredTextureId);
            snapshot.filteredTextureId = 0;
        }
        if (snapshot.intermediateTextureId != 0) {
            GL11.glDeleteTextures(snapshot.intermediateTextureId);
            snapshot.intermediateTextureId = 0;
        }
        if (snapshot.filterFramebufferId != 0) {
            GL30.glDeleteFramebuffers(snapshot.filterFramebufferId);
            snapshot.filterFramebufferId = 0;
        }
        snapshot.textureId = 0;
    }

    private static int resolveReadFramebufferId(int requestedReadFramebufferId) {
        if (requestedReadFramebufferId >= 0) {
            return requestedReadFramebufferId;
        }
        return GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    }

    private static int resolveEffectiveDownsampleFactor(int width, int height, int requestedDownsampleFactor) {
        int safeFactor = Math.max(1, Math.min(MAX_DOWNSAMPLE_FACTOR, requestedDownsampleFactor));
        int targetWidth = resolveDownsampledSize(width, safeFactor);
        int targetHeight = resolveDownsampledSize(height, safeFactor);
        if (targetWidth >= width && targetHeight >= height) {
            return 1;
        }
        return safeFactor;
    }

    private static SampleRegion resolveReusableSampleRegion(int screenWidth, int screenHeight,
            SampleRegion sampleRegion) {
        SampleRegion blockRegion = resolveBlockAlignedSampleRegion(screenWidth, screenHeight, sampleRegion);
        if (isSnapshotSizeAllowed(blockRegion)) {
            return blockRegion;
        }
        return sampleRegion;
    }

    private static String formatRegionDetail(SampleRegion requestedRegion, SampleRegion reusableRegion) {
        if (requestedRegion == null || reusableRegion == null) {
            return "exact";
        }
        if (requestedRegion.getLeft() == reusableRegion.getLeft() && requestedRegion.getTop() == reusableRegion.getTop()
                && requestedRegion.getRight() == reusableRegion.getRight()
                && requestedRegion.getBottom() == reusableRegion.getBottom()) {
            return "exact";
        }
        return "block" + SNAPSHOT_BLOCK_SIZE;
    }

    private static String formatAtlasRegionDetail(String regionDetail) {
        if (regionDetail == null || regionDetail.isEmpty()) {
            return "atlas";
        }
        if (regionDetail.startsWith("atlas-")) {
            return regionDetail;
        }
        return "atlas-" + regionDetail;
    }

    private static String formatTileDetail(SampleRegion sampleRegion, boolean reused) {
        int tileCount = resolveTileCount(sampleRegion);
        int reusedTileCount = reused ? tileCount : 0;
        int capturedTileCount = reused ? 0 : tileCount;
        return "tiles=" + tileCount + " reused=" + reusedTileCount + " captured=" + capturedTileCount;
    }

    /**
     * 判断外层采样区域是否完整覆盖内层采样区域。
     *
     * @param outerRegion 外层区域
     * @param innerRegion 内层区域
     * @return 是否可由外层区域作为临时 atlas 承载内层区域采样
     */
    static boolean containsSampleRegion(SampleRegion outerRegion, SampleRegion innerRegion) {
        return outerRegion != null && innerRegion != null
                && outerRegion.getLeft() <= innerRegion.getLeft()
                && outerRegion.getTop() <= innerRegion.getTop()
                && outerRegion.getRight() >= innerRegion.getRight()
                && outerRegion.getBottom() >= innerRegion.getBottom();
    }

    private static boolean isSameSampleRegion(SampleRegion firstRegion, SampleRegion secondRegion) {
        return firstRegion != null && secondRegion != null
                && firstRegion.getLeft() == secondRegion.getLeft()
                && firstRegion.getTop() == secondRegion.getTop()
                && firstRegion.getRight() == secondRegion.getRight()
                && firstRegion.getBottom() == secondRegion.getBottom();
    }

    private static boolean isBetterContainingSnapshot(FrameSnapshot currentSnapshot, FrameSnapshot candidateSnapshot) {
        if (currentSnapshot == null) {
            return true;
        }
        long currentArea = (long) currentSnapshot.width * (long) currentSnapshot.height;
        long candidateArea = (long) candidateSnapshot.width * (long) candidateSnapshot.height;
        return candidateArea < currentArea;
    }

    private static SampleRegion toSampleRegion(FrameSnapshot snapshot) {
        return new SampleRegion(snapshot.sampleLeft, snapshot.sampleTop, snapshot.sampleLeft + snapshot.width,
                snapshot.sampleTop + snapshot.height);
    }

    private static int resolveSampleStep(int blurRadius) {
        return Math.max(1, Math.min(12, Math.round(Math.max(1, blurRadius) / 2.5F)));
    }

    private static SampleRegion resolveFullScreenSampleRegion(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        return new SampleRegion(0, 0, screenWidth, screenHeight);
    }

    private static boolean isSnapshotRegionWithinScreen(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        return screenWidth > 0 && screenHeight > 0
                && sampleRegion.getLeft() >= 0 && sampleRegion.getTop() >= 0
                && sampleRegion.getRight() <= screenWidth && sampleRegion.getBottom() <= screenHeight
                && sampleRegion.getRight() > sampleRegion.getLeft()
                && sampleRegion.getBottom() > sampleRegion.getTop();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int alignDown(int value, int blockSize) {
        if (blockSize <= 0) {
            return value;
        }
        return value / blockSize * blockSize;
    }

    private static int alignUp(int value, int blockSize) {
        if (blockSize <= 0) {
            return value;
        }
        return (value + blockSize - 1) / blockSize * blockSize;
    }

    /**
     * 单帧主层快照。
     */
    static final class Snapshot {

        private final int textureId;
        private final int sampleLeft;
        private final int sampleTop;
        private final int width;
        private final int height;
        private final int textureWidth;
        private final int textureHeight;
        private final int readFramebufferId;
        private final int contentRevision;
        private final int downsampleFactor;
        private final String filterDetail;
        private final String regionDetail;
        private final String tileDetail;
        private final boolean reused;

        private Snapshot(int textureId, int sampleLeft, int sampleTop, int width, int height,
                int readFramebufferId, int contentRevision, int textureWidth, int textureHeight,
                int downsampleFactor, String filterDetail, String regionDetail, String tileDetail, boolean reused) {
            this.textureId = textureId;
            this.sampleLeft = sampleLeft;
            this.sampleTop = sampleTop;
            this.width = width;
            this.height = height;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.readFramebufferId = readFramebufferId;
            this.contentRevision = contentRevision;
            this.downsampleFactor = downsampleFactor;
            this.filterDetail = filterDetail == null ? "raw" : filterDetail;
            this.regionDetail = regionDetail == null ? "exact" : regionDetail;
            this.tileDetail = tileDetail == null ? "tiles=0 reused=0 captured=0" : tileDetail;
            this.reused = reused;
        }

        private static Snapshot captured(int textureId, SampleRegion sampleRegion, int readFramebufferId,
                int contentRevision, int textureWidth, int textureHeight, int downsampleFactor, String filterDetail,
                String regionDetail, String tileDetail) {
            return new Snapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                    sampleRegion.getHeight(), readFramebufferId, contentRevision, textureWidth, textureHeight,
                    downsampleFactor, filterDetail, regionDetail, tileDetail, false);
        }

        private static Snapshot reused(int textureId, SampleRegion sampleRegion, int readFramebufferId,
                int contentRevision, int textureWidth, int textureHeight, int downsampleFactor, String filterDetail,
                String regionDetail, String tileDetail) {
            return new Snapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                    sampleRegion.getHeight(), readFramebufferId, contentRevision, textureWidth, textureHeight,
                    downsampleFactor, filterDetail, regionDetail, tileDetail, true);
        }

        int getTextureId() {
            return textureId;
        }

        int getSampleLeft() {
            return sampleLeft;
        }

        int getSampleTop() {
            return sampleTop;
        }

        int getWidth() {
            return width;
        }

        int getHeight() {
            return height;
        }

        int getTextureWidth() {
            return textureWidth;
        }

        int getTextureHeight() {
            return textureHeight;
        }

        int getReadFramebufferId() {
            return readFramebufferId;
        }

        int getContentRevision() {
            return contentRevision;
        }

        int getDownsampleFactor() {
            return downsampleFactor;
        }

        String getFilterDetail() {
            return filterDetail;
        }

        String getRegionDetail() {
            return regionDetail;
        }

        String getTileDetail() {
            return tileDetail;
        }

        boolean isReused() {
            return reused;
        }
    }

    /**
     * Backdrop 采样区域对应的 tile 网格范围。
     */
    static final class TileRegion {

        private final int tileLeft;
        private final int tileTop;
        private final int tileRight;
        private final int tileBottom;

        private TileRegion(int tileLeft, int tileTop, int tileRight, int tileBottom) {
            this.tileLeft = tileLeft;
            this.tileTop = tileTop;
            this.tileRight = tileRight;
            this.tileBottom = tileBottom;
        }

        int getTileLeft() {
            return tileLeft;
        }

        int getTileTop() {
            return tileTop;
        }

        int getTileRight() {
            return tileRight;
        }

        int getTileBottom() {
            return tileBottom;
        }

        int getTileWidth() {
            return Math.max(0, tileRight - tileLeft);
        }

        int getTileHeight() {
            return Math.max(0, tileBottom - tileTop);
        }

        int getTileCount() {
            return getTileWidth() * getTileHeight();
        }
    }

    /**
     * Backdrop 采样区域。
     */
    static final class SampleRegion {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private SampleRegion(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int getLeft() {
            return left;
        }

        int getTop() {
            return top;
        }

        int getRight() {
            return right;
        }

        int getBottom() {
            return bottom;
        }

        int getWidth() {
            return right - left;
        }

        int getHeight() {
            return bottom - top;
        }
    }

    /**
     * 复用池中的快照槽。
     */
    private static final class FrameSnapshot {

        private int textureId;
        private int sourceTextureId;
        private int filteredTextureId;
        private int intermediateTextureId;
        private int filterFramebufferId;
        private int sampleLeft;
        private int sampleTop;
        private int width;
        private int height;
        private int sourceWidth;
        private int sourceHeight;
        private int filteredWidth;
        private int filteredHeight;
        private int intermediateWidth;
        private int intermediateHeight;
        private int textureWidth;
        private int textureHeight;
        private int readFramebufferId = -1;
        private int contentRevision;
        private int blurRadius;
        private int requestedDownsampleFactor = 1;
        private int downsampleFactor = 1;
        private int filterPassRadius;
        private String filterDetail = "raw";
        private String regionDetail = "exact";
        private String tileDetail = "tiles=0 reused=0 captured=0";
        private int capturedFrameId;
        private int activeUseCount;
    }

    /**
     * 当前帧已捕获快照的匹配结果。
     */
    private static final class FrameSnapshotMatch {

        private final FrameSnapshot snapshot;
        private final boolean exactMatch;

        private FrameSnapshotMatch(FrameSnapshot snapshot, boolean exactMatch) {
            this.snapshot = snapshot;
            this.exactMatch = exactMatch;
        }
    }
}
