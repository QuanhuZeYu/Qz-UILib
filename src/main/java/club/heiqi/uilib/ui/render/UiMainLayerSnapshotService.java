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
 * 同帧已捕获的较大 block 区域也可作为临时 atlas，供后续被其覆盖的较小区域继续采样；
 * 多个已捕获 tile 能覆盖同一次请求时，也会组装成新的局部 atlas，缺失 tile 才从当前 read framebuffer 复制。
 * 一旦两次 backdrop 之间有新的 UI 绘制写入，就必须重新捕获，避免后续元素采样到旧主层。
 * 文档作者层仍只暴露 CSS-like backdrop 语义，不接触纹理、FBO 或 OpenGL 状态。</p>
 */
public final class UiMainLayerSnapshotService {

    private static final float[][] FILTER_BLUR_SAMPLES = new float[][] {
            { 0.0F, 0.40F },
            { -1.0F, 0.24F },
            { 1.0F, 0.24F },
            { -2.0F, 0.06F },
            { 2.0F, 0.06F }
    };

    /**
     * 池容量保护上限。
     *
     * <p>避免异常关屏路径导致 {@link FrameSnapshot} 持续累积，进而让 GL 纹理 / FBO 无界增长。
     * 命中上限时优先驱逐当前帧未活跃的最旧 snapshot 复用其槽位；全部活跃时放弃当帧捕获并降级为
     * 直接读取主层。</p>
     */
    private static final int MAX_POOLED_SNAPSHOTS = 32;

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
    MainLayerSnapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId) {
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
    MainLayerSnapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId,
            int contentRevision) {
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
    MainLayerSnapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId,
            int contentRevision, SampleRegion sampleRegion) {
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
    MainLayerSnapshot acquireSnapshot(int screenWidth, int screenHeight, int requestedReadFramebufferId,
            int contentRevision, SampleRegion sampleRegion, int blurRadius) {
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
        TileCoveragePlan tileCoveragePlan = resolveTileCoveragePlan(readFramebufferId, reusableRegion,
                contentRevision, downsampleFactor, blurRadius);
        FrameSnapshotMatch capturedSnapshotMatch = findCapturedSnapshot(readFramebufferId, reusableRegion,
                contentRevision, downsampleFactor, blurRadius);
        if (capturedSnapshotMatch != null) {
            FrameSnapshot capturedSnapshot = capturedSnapshotMatch.snapshot;
            capturedSnapshot.activeUseCount++;
            SampleRegion capturedRegion = toSampleRegion(capturedSnapshot);
            String matchedRegionDetail = capturedSnapshotMatch.exactMatch ? capturedSnapshot.regionDetail
                    : formatAtlasRegionDetail(capturedSnapshot.regionDetail);
            String tileDetail = formatTileDetail(tileCoveragePlan, tileCoveragePlan.getTileCount(), 0);
            return MainLayerSnapshot.reused(capturedSnapshot.textureId, capturedRegion, readFramebufferId,
                    contentRevision, capturedSnapshot.textureWidth, capturedSnapshot.textureHeight,
                    capturedSnapshot.downsampleFactor, capturedSnapshot.filterDetail, matchedRegionDetail, tileDetail);
        }

        FrameSnapshot snapshot = findReusableSnapshot();
        if (snapshot == null) {
            snapshot = allocateOrEvictSnapshot();
            if (snapshot == null) {
                lastFailureDetail = "snapshot-pool-exhausted";
                return null;
            }
        }
        String tileDetail = formatTileDetail(tileCoveragePlan, 0, tileCoveragePlan.getTileCount());
        if (!captureSnapshot(snapshot, screenHeight, reusableRegion, readFramebufferId, contentRevision,
                downsampleFactor, blurRadius, regionDetail, tileCoveragePlan)) {
            return null;
        }
        return MainLayerSnapshot.captured(snapshot.textureId, reusableRegion, readFramebufferId, contentRevision,
                snapshot.textureWidth, snapshot.textureHeight, snapshot.downsampleFactor, snapshot.filterDetail,
                snapshot.regionDetail, snapshot.tileDetail);
    }

    /**
     * 释放当前绘制调用持有的快照使用权。
     *
     * @param snapshot 快照
     */
    void releaseSnapshot(MainLayerSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (FrameSnapshot frameSnapshot : snapshots) {
            if (frameSnapshot.textureId == snapshot.getTextureId() && frameSnapshot.activeUseCount > 0) {
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
        return UiMainLayerSnapshotGeometry.resolveSampleRegion(screenWidth, screenHeight, left, top, right, bottom,
                blurRadius);
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
        return UiMainLayerSnapshotGeometry.resolveBlockAlignedSampleRegion(screenWidth, screenHeight, sampleRegion);
    }

    /**
     * 判断指定快照尺寸是否在当前保护限制内。
     *
     * @param width 快照宽度
     * @param height 快照高度
     * @return 是否允许创建快照
     */
    static boolean isSnapshotSizeAllowed(int width, int height) {
        return UiMainLayerSnapshotGeometry.isSnapshotSizeAllowed(width, height);
    }

    /**
     * 判断局部采样区域尺寸是否在当前保护限制内。
     *
     * @param sampleRegion 采样区域
     * @return 是否允许创建快照
     */
    static boolean isSnapshotSizeAllowed(SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.isSnapshotSizeAllowed(sampleRegion);
    }

    /**
     * 将 top-left UI 坐标系中的采样区域转换为 OpenGL copy 的源 Y 坐标。
     *
     * @param screenHeight 屏幕高度
     * @param sampleRegion 采样区域
     * @return OpenGL 底部原点坐标系中的源 Y
     */
    static int resolveCopySourceY(int screenHeight, SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.resolveCopySourceY(screenHeight, sampleRegion);
    }

    /**
     * 按模糊半径决定滤镜快照降采样倍率。
     *
     * @param blurRadius 模糊半径
     * @return 降采样倍率
     */
    static int resolveDownsampleFactor(int blurRadius) {
        return UiMainLayerSnapshotGeometry.resolveDownsampleFactor(blurRadius);
    }

    /**
     * 计算降采样后的纹理边长。
     *
     * @param sourceSize 原始边长
     * @param downsampleFactor 降采样倍率
     * @return 降采样后边长
     */
    static int resolveDownsampledSize(int sourceSize, int downsampleFactor) {
        return UiMainLayerSnapshotGeometry.resolveDownsampledSize(sourceSize, downsampleFactor);
    }

    /**
     * 计算降采样滤镜 pass 内部使用的 separable blur 半径。
     *
     * @param blurRadius 作者侧 blur 半径
     * @param downsampleFactor 降采样倍率
     * @return filter pass 半径；为 0 表示不需要独立 blur pass
     */
    static int resolveFilterPassRadius(int blurRadius, int downsampleFactor) {
        return UiMainLayerSnapshotGeometry.resolveFilterPassRadius(blurRadius, downsampleFactor);
    }

    /**
     * 将采样区域映射到 128px tile 网格。
     *
     * @param sampleRegion 采样区域
     * @return tile 区域；无效区域返回空 tile 区域
     */
    static TileRegion resolveTileRegion(SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.resolveTileRegion(sampleRegion);
    }

    /**
     * 计算采样区域覆盖的 tile 数量。
     *
     * @param sampleRegion 采样区域
     * @return tile 数量
     */
    static int resolveTileCount(SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.resolveTileCount(sampleRegion);
    }

    /**
     * 将 tile 网格范围裁剪到具体采样区域内的像素范围。
     *
     * @param sampleRegion 采样区域
     * @param tileLeft 左侧 tile 坐标
     * @param tileTop 顶部 tile 坐标
     * @param tileRight 右侧 tile 坐标
     * @param tileBottom 底部 tile 坐标
     * @return 裁剪后的像素区域；无交集时返回 null
     */
    static SampleRegion resolveTileSampleRegion(SampleRegion sampleRegion, int tileLeft, int tileTop,
            int tileRight, int tileBottom) {
        return UiMainLayerSnapshotGeometry.resolveTileSampleRegion(sampleRegion, tileLeft, tileTop, tileRight,
                tileBottom);
    }

    /**
     * 将 top-left UI 子区域映射到 atlas 纹理的 bottom-left Y 偏移。
     *
     * @param atlasRegion atlas 采样区域
     * @param copiedRegion 要写入的子区域
     * @return OpenGL 纹理底部原点坐标系中的目标 Y 偏移
     */
    static int resolveTextureCopyTargetY(SampleRegion atlasRegion, SampleRegion copiedRegion) {
        return UiMainLayerSnapshotGeometry.resolveTextureCopyTargetY(atlasRegion, copiedRegion);
    }

    /**
     * 计算请求 tile 区域已经被哪些既有 tile 区域覆盖。
     *
     * @param requestedTileRegion 请求 tile 区域
     * @param coveredTileRegions 已捕获 tile 区域列表
     * @return tile 覆盖计划
     */
    static TileCoveragePlan resolveTileCoverage(TileRegion requestedTileRegion,
            List<TileRegion> coveredTileRegions) {
        return UiMainLayerSnapshotGeometry.resolveTileCoverage(requestedTileRegion, coveredTileRegions);
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

    /**
     * 分配新 snapshot 槽，未达上限时直接 new；达到上限时驱逐当前帧未活跃的最旧 snapshot 复用其槽位。
     *
     * @return 可用 snapshot 槽；池已满且全部活跃时返回 null
     */
    private FrameSnapshot allocateOrEvictSnapshot() {
        if (snapshots.size() < MAX_POOLED_SNAPSHOTS) {
            FrameSnapshot newSnapshot = new FrameSnapshot();
            snapshots.add(newSnapshot);
            return newSnapshot;
        }
        FrameSnapshot oldestEvictableSnapshot = null;
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.activeUseCount > 0) {
                continue;
            }
            if (oldestEvictableSnapshot == null
                    || snapshot.capturedFrameId < oldestEvictableSnapshot.capturedFrameId) {
                oldestEvictableSnapshot = snapshot;
            }
        }
        if (oldestEvictableSnapshot == null) {
            return null;
        }
        closeSnapshot(oldestEvictableSnapshot);
        return oldestEvictableSnapshot;
    }

    private TileCoveragePlan resolveTileCoveragePlan(int readFramebufferId, SampleRegion sampleRegion,
            int contentRevision, int downsampleFactor, int blurRadius) {
        List<TileRegion> coveredTileRegions = new ArrayList<TileRegion>();
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.capturedFrameId == frameId && snapshot.readFramebufferId == readFramebufferId
                    && snapshot.contentRevision == contentRevision
                    && snapshot.requestedDownsampleFactor == downsampleFactor && snapshot.blurRadius == blurRadius
                    && snapshot.textureId != 0) {
                coveredTileRegions.add(resolveTileRegion(toSampleRegion(snapshot)));
            }
        }
        return resolveTileCoverage(resolveTileRegion(sampleRegion), coveredTileRegions);
    }

    private boolean captureSnapshot(FrameSnapshot snapshot, int screenHeight, SampleRegion sampleRegion,
            int readFramebufferId, int contentRevision, int requestedDownsampleFactor, int blurRadius,
            String regionDetail, TileCoveragePlan tileCoveragePlan) {
        int width = sampleRegion.getWidth();
        int height = sampleRegion.getHeight();
        List<TileAssemblyEntry> tileAssemblyEntries = resolveTileAssemblyEntries(readFramebufferId, sampleRegion,
                contentRevision, requestedDownsampleFactor, blurRadius);
        int reusableTileCount = countReusableTileEntries(tileAssemblyEntries);
        boolean assembleTileAtlas = reusableTileCount > 0;
        String resolvedRegionDetail = assembleTileAtlas ? formatTileAtlasRegionDetail(regionDetail) : regionDetail;
        String resolvedTileDetail = formatTileDetail(tileCoveragePlan, 0, resolveTileCount(sampleRegion));
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
            if (assembleTileAtlas) {
                int copiedTileCount = copyMissingTileEntriesFromFramebuffer(snapshot, screenHeight, readFramebufferId,
                        previousReadFramebufferId, sampleRegion, tileAssemblyEntries);
                reusableTileCount = renderCoveredTileEntriesToAtlas(snapshot, sampleRegion, tileAssemblyEntries);
                resolvedTileDetail = formatTileDetail(tileCoveragePlan, reusableTileCount, copiedTileCount);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.sourceTextureId);
            } else {
                if (previousReadFramebufferId != readFramebufferId) {
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferId);
                }
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, sampleRegion.getLeft(),
                        resolveCopySourceY(screenHeight, sampleRegion), width, height);
            }
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
            snapshot.regionDetail = resolvedRegionDetail;
            snapshot.tileDetail = resolvedTileDetail;
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

    /**
     * 为当前请求生成每个 tile 的 atlas 组装来源。
     *
     * @param readFramebufferId read framebuffer
     * @param sampleRegion 请求采样区域
     * @param contentRevision 当前内容版本
     * @param downsampleFactor 请求降采样倍率
     * @param blurRadius 请求模糊半径
     * @return tile 组装条目列表
     */
    private List<TileAssemblyEntry> resolveTileAssemblyEntries(int readFramebufferId, SampleRegion sampleRegion,
            int contentRevision, int downsampleFactor, int blurRadius) {
        List<TileAssemblyEntry> entries = new ArrayList<TileAssemblyEntry>();
        TileRegion requestedTileRegion = resolveTileRegion(sampleRegion);
        for (int tileY = requestedTileRegion.getTileTop(); tileY < requestedTileRegion.getTileBottom(); tileY++) {
            for (int tileX = requestedTileRegion.getTileLeft(); tileX < requestedTileRegion.getTileRight(); tileX++) {
                SampleRegion tileSampleRegion = resolveTileSampleRegion(sampleRegion, tileX, tileY, tileX + 1,
                        tileY + 1);
                if (tileSampleRegion == null) {
                    continue;
                }
                FrameSnapshot sourceSnapshot = findTileSourceSnapshot(readFramebufferId, tileSampleRegion,
                        contentRevision, downsampleFactor, blurRadius);
                entries.add(new TileAssemblyEntry(tileSampleRegion, sourceSnapshot));
            }
        }
        return entries;
    }

    /**
     * 统计可以从既有 snapshot 复用的 tile 数。
     *
     * @param entries tile 组装条目
     * @return 可复用 tile 数
     */
    private static int countReusableTileEntries(List<TileAssemblyEntry> entries) {
        int reusableTileCount = 0;
        if (entries == null) {
            return 0;
        }
        for (TileAssemblyEntry entry : entries) {
            if (entry != null && entry.sourceSnapshot != null) {
                reusableTileCount++;
            }
        }
        return reusableTileCount;
    }

    /**
     * 查找能完整覆盖指定 tile 像素区域的既有 snapshot。
     *
     * @param readFramebufferId read framebuffer
     * @param tileSampleRegion tile 像素区域
     * @param contentRevision 当前内容版本
     * @param downsampleFactor 请求降采样倍率
     * @param blurRadius 请求模糊半径
     * @return 最小覆盖 snapshot；没有时返回 null
     */
    private FrameSnapshot findTileSourceSnapshot(int readFramebufferId, SampleRegion tileSampleRegion,
            int contentRevision, int downsampleFactor, int blurRadius) {
        FrameSnapshot sourceSnapshot = null;
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.capturedFrameId == frameId && snapshot.readFramebufferId == readFramebufferId
                    && snapshot.contentRevision == contentRevision
                    && snapshot.requestedDownsampleFactor == downsampleFactor && snapshot.blurRadius == blurRadius
                    && snapshot.sourceTextureId != 0 && containsSampleRegion(toSampleRegion(snapshot),
                            tileSampleRegion) && isBetterContainingSnapshot(sourceSnapshot, snapshot)) {
                sourceSnapshot = snapshot;
            }
        }
        return sourceSnapshot;
    }

    /**
     * 把缺失 tile 从当前 read framebuffer 写入 atlas 原始纹理。
     *
     * @param screenHeight 屏幕高度
     * @param readFramebufferId read framebuffer
     * @param previousReadFramebufferId 原 read framebuffer
     * @param atlasRegion atlas 采样区域
     * @param entries tile 组装条目
     * @return 从 framebuffer 复制的 tile 数
     */
    private int copyMissingTileEntriesFromFramebuffer(FrameSnapshot snapshot, int screenHeight, int readFramebufferId,
            int previousReadFramebufferId, SampleRegion atlasRegion, List<TileAssemblyEntry> entries) {
        int copiedTileCount = 0;
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.sourceTextureId);
        if (previousReadFramebufferId != readFramebufferId) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferId);
        }
        for (TileAssemblyEntry entry : entries) {
            if (entry == null || entry.sourceSnapshot != null) {
                continue;
            }
            SampleRegion copiedRegion = entry.sampleRegion;
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    copiedRegion.getLeft() - atlasRegion.getLeft(), resolveTextureCopyTargetY(atlasRegion,
                            copiedRegion), copiedRegion.getLeft(), resolveCopySourceY(screenHeight, copiedRegion),
                    copiedRegion.getWidth(), copiedRegion.getHeight());
            copiedTileCount++;
        }
        return copiedTileCount;
    }

    /**
     * 把可复用 tile 从既有 snapshot 源纹理绘制到新的 atlas 原始纹理。
     *
     * @param snapshot 目标 snapshot
     * @param atlasRegion atlas 采样区域
     * @param entries tile 组装条目
     * @return 从既有 snapshot 复用的 tile 数
     */
    private int renderCoveredTileEntriesToAtlas(FrameSnapshot snapshot, SampleRegion atlasRegion,
            List<TileAssemblyEntry> entries) {
        int reusableTileCount = 0;
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        ensureTextureAssemblyFramebuffer(snapshot);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.filterFramebufferId);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                snapshot.sourceTextureId, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            disableFilterPassForCurrentFrame("tile-atlas-fbo-incomplete:" + status);
            throw new IllegalStateException("tile atlas fbo incomplete: " + status);
        }

        GL20.glUseProgram(0);
        GL11.glViewport(0, 0, atlasRegion.getWidth(), atlasRegion.getHeight());
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        for (TileAssemblyEntry entry : entries) {
            if (entry == null || entry.sourceSnapshot == null) {
                continue;
            }
            renderSnapshotTileToAtlas(atlasRegion, entry.sampleRegion, entry.sourceSnapshot);
            reusableTileCount++;
        }
        return reusableTileCount;
    }

    /**
     * 确保 snapshot 有可用于 tile atlas 组装的 FBO。
     *
     * @param snapshot 目标 snapshot
     */
    private void ensureTextureAssemblyFramebuffer(FrameSnapshot snapshot) {
        if (snapshot.filterFramebufferId == 0) {
            snapshot.filterFramebufferId = GL30.glGenFramebuffers();
            if (snapshot.filterFramebufferId == 0) {
                throw new IllegalStateException("tile atlas framebuffer allocation failed");
            }
        }
    }

    /**
     * 从既有 snapshot 源纹理中绘制一个 tile 子区域到目标 atlas。
     *
     * @param atlasRegion atlas 采样区域
     * @param tileSampleRegion tile 像素区域
     * @param sourceSnapshot 来源 snapshot
     */
    private static void renderSnapshotTileToAtlas(SampleRegion atlasRegion, SampleRegion tileSampleRegion,
            FrameSnapshot sourceSnapshot) {
        SampleRegion sourceRegion = toSampleRegion(sourceSnapshot);
        float leftU = clampFloat(((float) tileSampleRegion.getLeft() - (float) sourceRegion.getLeft())
                / (float) Math.max(1, sourceRegion.getWidth()), 0.0F, 1.0F);
        float rightU = clampFloat(((float) tileSampleRegion.getRight() - (float) sourceRegion.getLeft())
                / (float) Math.max(1, sourceRegion.getWidth()), 0.0F, 1.0F);
        float topV = clampFloat(1.0F - ((float) tileSampleRegion.getTop() - (float) sourceRegion.getTop())
                / (float) Math.max(1, sourceRegion.getHeight()), 0.0F, 1.0F);
        float bottomV = clampFloat(1.0F - ((float) tileSampleRegion.getBottom() - (float) sourceRegion.getTop())
                / (float) Math.max(1, sourceRegion.getHeight()), 0.0F, 1.0F);
        int targetLeft = tileSampleRegion.getLeft() - atlasRegion.getLeft();
        int targetTop = tileSampleRegion.getTop() - atlasRegion.getTop();
        int targetRight = tileSampleRegion.getRight() - atlasRegion.getLeft();
        int targetBottom = tileSampleRegion.getBottom() - atlasRegion.getTop();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceSnapshot.sourceTextureId);
        drawAtlasTextureQuad(atlasRegion.getWidth(), atlasRegion.getHeight(), targetLeft, targetTop, targetRight,
                targetBottom, leftU, rightU, topV, bottomV);
    }

    /**
     * 在当前 draw framebuffer 上按 top-left 坐标绘制纹理子矩形。
     *
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param left 目标左侧
     * @param top 目标顶部
     * @param right 目标右侧
     * @param bottom 目标底部
     * @param leftU 源纹理左侧 U
     * @param rightU 源纹理右侧 U
     * @param topV 源纹理顶部 V
     * @param bottomV 源纹理底部 V
     */
    private static void drawAtlasTextureQuad(int targetWidth, int targetHeight, int left, int top, int right,
            int bottom, float leftU, float rightU, float topV, float bottomV) {
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
            GL11.glTexCoord2f(leftU, bottomV);
            GL11.glVertex2f((float) left, (float) bottom);
            GL11.glTexCoord2f(rightU, bottomV);
            GL11.glVertex2f((float) right, (float) bottom);
            GL11.glTexCoord2f(rightU, topV);
            GL11.glVertex2f((float) right, (float) top);
            GL11.glTexCoord2f(leftU, topV);
            GL11.glVertex2f((float) left, (float) top);
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
        int safeFactor = Math.max(1, Math.min(UiMainLayerSnapshotGeometry.MAX_DOWNSAMPLE_FACTOR,
                requestedDownsampleFactor));
        int targetWidth = resolveDownsampledSize(width, safeFactor);
        int targetHeight = resolveDownsampledSize(height, safeFactor);
        if (targetWidth >= width && targetHeight >= height) {
            return 1;
        }
        return safeFactor;
    }

    private static SampleRegion resolveReusableSampleRegion(int screenWidth, int screenHeight,
            SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.resolveReusableSampleRegion(screenWidth, screenHeight, sampleRegion);
    }

    private static String formatRegionDetail(SampleRegion requestedRegion, SampleRegion reusableRegion) {
        return UiMainLayerSnapshotGeometry.formatRegionDetail(requestedRegion, reusableRegion);
    }

    private static String formatAtlasRegionDetail(String regionDetail) {
        return UiMainLayerSnapshotGeometry.formatAtlasRegionDetail(regionDetail);
    }

    private static String formatTileAtlasRegionDetail(String regionDetail) {
        return UiMainLayerSnapshotGeometry.formatTileAtlasRegionDetail(regionDetail);
    }

    private static String formatTileDetail(TileCoveragePlan tileCoveragePlan, int reusedTileCount,
            int copiedTileCount) {
        return UiMainLayerSnapshotGeometry.formatTileDetail(tileCoveragePlan, reusedTileCount, copiedTileCount);
    }

    /**
     * 判断外层采样区域是否完整覆盖内层采样区域。
     *
     * @param outerRegion 外层区域
     * @param innerRegion 内层区域
     * @return 是否可由外层区域作为临时 atlas 承载内层区域采样
     */
    static boolean containsSampleRegion(SampleRegion outerRegion, SampleRegion innerRegion) {
        return UiMainLayerSnapshotGeometry.containsSampleRegion(outerRegion, innerRegion);
    }

    private static boolean isSameSampleRegion(SampleRegion firstRegion, SampleRegion secondRegion) {
        return UiMainLayerSnapshotGeometry.isSameSampleRegion(firstRegion, secondRegion);
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

    private static SampleRegion resolveFullScreenSampleRegion(int screenWidth, int screenHeight) {
        return UiMainLayerSnapshotGeometry.resolveFullScreenSampleRegion(screenWidth, screenHeight);
    }

    private static boolean isSnapshotRegionWithinScreen(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        return UiMainLayerSnapshotGeometry.isSnapshotRegionWithinScreen(screenWidth, screenHeight, sampleRegion);
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * 单个 atlas tile 的数据来源。
     */
    private static final class TileAssemblyEntry {

        private final SampleRegion sampleRegion;
        private final FrameSnapshot sourceSnapshot;

        private TileAssemblyEntry(SampleRegion sampleRegion, FrameSnapshot sourceSnapshot) {
            this.sampleRegion = sampleRegion;
            this.sourceSnapshot = sourceSnapshot;
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
        private String tileDetail = "tiles=0 covered=0 missing=0 reused=0 copied=0";
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
