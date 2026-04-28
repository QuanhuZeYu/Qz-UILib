package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * 当前 UI 主层的同帧快照服务。
 *
 * <p>该服务只属于渲染后端，用于让 `backdrop-filter` 元素在当前 UI 主层内容未变化时复用已复制纹理。
 * 一旦两次 backdrop 之间有新的 UI 绘制写入，就必须重新捕获，避免后续元素采样到旧主层。
 * 文档作者层仍只暴露 CSS-like backdrop 语义，不接触纹理、FBO 或 OpenGL 状态。</p>
 */
public final class UiMainLayerSnapshotService {

    private static final int MAX_SNAPSHOT_EDGE = 4096;
    private static final int MAX_SNAPSHOT_PIXELS = 4096 * 4096;

    private final List<FrameSnapshot> snapshots = new ArrayList<FrameSnapshot>();
    private int frameId;
    private boolean frameActive;
    private boolean disabledForFrame;
    private String lastFailureDetail = "not-run";

    /**
     * 开始新一帧快照复用窗口。
     */
    public void beginFrame() {
        frameActive = true;
        disabledForFrame = false;
        lastFailureDetail = "not-run";
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
            if (snapshot.textureId != 0) {
                GL11.glDeleteTextures(snapshot.textureId);
                snapshot.textureId = 0;
            }
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

        int readFramebufferId = resolveReadFramebufferId(requestedReadFramebufferId);
        FrameSnapshot capturedSnapshot = findCapturedSnapshot(readFramebufferId, sampleRegion,
                contentRevision);
        if (capturedSnapshot != null) {
            capturedSnapshot.activeUseCount++;
            return Snapshot.reused(capturedSnapshot.textureId, sampleRegion, readFramebufferId, contentRevision);
        }

        FrameSnapshot snapshot = findReusableSnapshot();
        if (snapshot == null) {
            snapshot = new FrameSnapshot();
            snapshots.add(snapshot);
        }
        if (!captureSnapshot(snapshot, screenHeight, sampleRegion, readFramebufferId, contentRevision)) {
            return null;
        }
        return Snapshot.captured(snapshot.textureId, sampleRegion, readFramebufferId, contentRevision);
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

    private FrameSnapshot findCapturedSnapshot(int readFramebufferId, SampleRegion sampleRegion,
            int contentRevision) {
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.capturedFrameId == frameId && snapshot.readFramebufferId == readFramebufferId
                    && snapshot.sampleLeft == sampleRegion.getLeft() && snapshot.sampleTop == sampleRegion.getTop()
                    && snapshot.width == sampleRegion.getWidth() && snapshot.height == sampleRegion.getHeight()
                    && snapshot.contentRevision == contentRevision && snapshot.textureId != 0) {
                return snapshot;
            }
        }
        return null;
    }

    private FrameSnapshot findReusableSnapshot() {
        for (FrameSnapshot snapshot : snapshots) {
            if (snapshot.activeUseCount <= 0) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean captureSnapshot(FrameSnapshot snapshot, int screenHeight, SampleRegion sampleRegion,
            int readFramebufferId,
            int contentRevision) {
        int width = sampleRegion.getWidth();
        int height = sampleRegion.getHeight();
        int previousTexture = 0;
        int previousReadFramebufferId = -1;
        boolean textureBindingCaptured = false;
        boolean readFramebufferCaptured = false;
        try {
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureBindingCaptured = true;
            previousReadFramebufferId = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            readFramebufferCaptured = true;
            if (snapshot.textureId == 0) {
                snapshot.textureId = GL11.glGenTextures();
                if (snapshot.textureId == 0) {
                    lastFailureDetail = "texture-allocation-failed";
                    return false;
                }
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (snapshot.width != width || snapshot.height != height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            if (previousReadFramebufferId != readFramebufferId) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferId);
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, sampleRegion.getLeft(),
                    resolveCopySourceY(screenHeight, sampleRegion), width, height);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

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
            if (readFramebufferCaptured) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebufferId);
            }
            if (textureBindingCaptured) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
        }
    }

    private void disableForCurrentFrame(String detail) {
        disabledForFrame = true;
        lastFailureDetail = detail;
    }

    private static int resolveReadFramebufferId(int requestedReadFramebufferId) {
        if (requestedReadFramebufferId >= 0) {
            return requestedReadFramebufferId;
        }
        return GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
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

    /**
     * 单帧主层快照。
     */
    static final class Snapshot {

        private final int textureId;
        private final int sampleLeft;
        private final int sampleTop;
        private final int width;
        private final int height;
        private final int readFramebufferId;
        private final int contentRevision;
        private final boolean reused;

        private Snapshot(int textureId, int sampleLeft, int sampleTop, int width, int height,
                int readFramebufferId, int contentRevision, boolean reused) {
            this.textureId = textureId;
            this.sampleLeft = sampleLeft;
            this.sampleTop = sampleTop;
            this.width = width;
            this.height = height;
            this.readFramebufferId = readFramebufferId;
            this.contentRevision = contentRevision;
            this.reused = reused;
        }

        private static Snapshot captured(int textureId, SampleRegion sampleRegion, int readFramebufferId,
                int contentRevision) {
            return new Snapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                    sampleRegion.getHeight(), readFramebufferId, contentRevision, false);
        }

        private static Snapshot reused(int textureId, SampleRegion sampleRegion, int readFramebufferId,
                int contentRevision) {
            return new Snapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                    sampleRegion.getHeight(), readFramebufferId, contentRevision, true);
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

        int getReadFramebufferId() {
            return readFramebufferId;
        }

        int getContentRevision() {
            return contentRevision;
        }

        boolean isReused() {
            return reused;
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
        private int sampleLeft;
        private int sampleTop;
        private int width;
        private int height;
        private int readFramebufferId = -1;
        private int contentRevision;
        private int capturedFrameId;
        private int activeUseCount;
    }
}
