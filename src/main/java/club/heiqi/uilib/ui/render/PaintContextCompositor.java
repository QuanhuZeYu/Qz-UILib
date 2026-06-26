package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.opengl.GL11;

/**
 * HTML-like paint context 离屏合成器。
 *
 * <p>该对象由宿主会话跨帧复用，按需借出同尺寸 {@link UiRenderTarget}。文档作者只接触 CSS-like opacity，
 * 不直接感知 FBO、framebuffer 或 OpenGL 状态。</p>
 */
public final class PaintContextCompositor {

    private final Deque<PaintContextFrame> frameStack = new ArrayDeque<PaintContextFrame>();
    private final List<UiRenderTarget> layerPool = new ArrayList<UiRenderTarget>();
    private int borrowedLayerCount;
    private boolean disabledForFrame;

    /**
     * 开始新一帧 paint context 回放。
     */
    public void beginFrame() {
        finishFrame();
        borrowedLayerCount = 0;
        disabledForFrame = false;
    }

    /**
     * 结束当前帧，防御性清理仍未弹出的 paint context。
     */
    public void finishFrame() {
        while (!frameStack.isEmpty()) {
            popGroupOpacity();
        }
        borrowedLayerCount = 0;
    }

    /**
     * 释放已缓存的离屏层资源。
     */
    public void close() {
        finishFrame();
        for (UiRenderTarget renderTarget : layerPool) {
            renderTarget.close();
        }
        layerPool.clear();
    }

    /**
     * 获取当前缓存的离屏层数量（诊断用，FBO 泄漏检测）。
     *
     * @return 池中 FBO 层数
     */
    public int __getPooledLayerCount() {
        return layerPool.size();
    }

    /**
     * 获取当前帧已借用的层数（诊断用）。
     *
     * @return 借用计数
     */
    public int __getBorrowedLayerCount() {
        return borrowedLayerCount;
    }

    void pushGroupOpacity(int screenWidth, int screenHeight, int left, int top, int right, int bottom,
            float opacity, ClipSnapshot clipSnapshot) {
        int clampedLeft = clampInt(Math.min(left, right), 0, screenWidth);
        int clampedTop = clampInt(Math.min(top, bottom), 0, screenHeight);
        int clampedRight = clampInt(Math.max(left, right), 0, screenWidth);
        int clampedBottom = clampInt(Math.max(top, bottom), 0, screenHeight);
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        if (disabledForFrame || clampedOpacity <= 0.0F || clampedOpacity >= 0.999F
                || clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
            frameStack.push(PaintContextFrame.inactive());
            return;
        }

        int layerIndex = borrowedLayerCount;
        UiRenderTarget layer = null;
        boolean layerBegun = false;
        try {
            layer = borrowLayer(screenWidth, screenHeight);
            int parentFramebufferId = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
            layer.begin();
            layerBegun = true;
            UiRenderContext.applyClipSnapshot(clipSnapshot, screenHeight);
            frameStack.push(PaintContextFrame.active(layerIndex, layer, clampedLeft, clampedTop, clampedRight,
                    clampedBottom, clampedOpacity, parentFramebufferId));
        } catch (RuntimeException exception) {
            if (layerBegun && layer != null) {
                layer.end();
            }
            disabledForFrame = true;
            borrowedLayerCount = layerIndex;
            frameStack.push(PaintContextFrame.inactive());
        } catch (LinkageError error) {
            if (layerBegun && layer != null) {
                layer.end();
            }
            disabledForFrame = true;
            borrowedLayerCount = layerIndex;
            frameStack.push(PaintContextFrame.inactive());
        }
    }

    boolean popGroupOpacity() {
        if (frameStack.isEmpty()) {
            return false;
        }
        PaintContextFrame frame = frameStack.pop();
        if (!frame.active) {
            return false;
        }
        frame.layer.end();
        frame.layer.compositeToCurrentFramebuffer(frame.left, frame.top, frame.right, frame.bottom,
                frame.opacity);
        borrowedLayerCount = Math.min(borrowedLayerCount, frame.layerIndex);
        return true;
    }

    boolean isCurrentLayerActive() {
        return !frameStack.isEmpty() && frameStack.peek().active;
    }

    int getCurrentBackdropReadFramebufferId() {
        if (!isCurrentLayerActive()) {
            return -1;
        }
        return frameStack.peek().parentFramebufferId;
    }

    UiRenderTarget borrowIsolatedLayer(int screenWidth, int screenHeight) {
        return borrowLayer(screenWidth, screenHeight);
    }

    void releaseIsolatedLayer(UiRenderTarget layer) {
        if (layer == null || borrowedLayerCount <= 0) {
            return;
        }
        borrowedLayerCount--;
    }

    private UiRenderTarget borrowLayer(int screenWidth, int screenHeight) {
        UiRenderTarget layer;
        if (borrowedLayerCount < layerPool.size()) {
            layer = layerPool.get(borrowedLayerCount);
        } else {
            layer = new UiRenderTarget();
            layerPool.add(layer);
        }
        borrowedLayerCount++;
        layer.ensureSize(screenWidth, screenHeight);
        return layer;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * paint context 帧栈条目。
     */
    private static final class PaintContextFrame {

        private final int layerIndex;
        private final UiRenderTarget layer;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final float opacity;
        private final int parentFramebufferId;
        private final boolean active;

        private PaintContextFrame(int layerIndex, UiRenderTarget layer, int left, int top, int right, int bottom,
                float opacity, int parentFramebufferId, boolean active) {
            this.layerIndex = layerIndex;
            this.layer = layer;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.opacity = opacity;
            this.parentFramebufferId = parentFramebufferId;
            this.active = active;
        }

        private static PaintContextFrame inactive() {
            return new PaintContextFrame(-1, null, 0, 0, 0, 0, 1.0F, 0, false);
        }

        private static PaintContextFrame active(int layerIndex, UiRenderTarget layer, int left, int top, int right,
                int bottom, float opacity, int parentFramebufferId) {
            return new PaintContextFrame(layerIndex, layer, left, top, right, bottom, opacity, parentFramebufferId,
                    true);
        }
    }
}
