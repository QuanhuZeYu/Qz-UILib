package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.internal.image.HostImageResourceEpoch;
import club.heiqi.uilib.ui.image.HostImageRenderSession;

/**
 * HTML-like paint context 离屏合成器。
 *
 * <p>该对象由宿主会话跨帧复用，按需借出同尺寸 {@link UiRenderTarget}。文档作者只接触 CSS-like opacity，
 * 不直接感知 FBO、framebuffer 或 OpenGL 状态。</p>
 */
public final class PaintContextCompositor {

    /** 离屏层工厂测试缝。 */
    interface LayerFactory { UiRenderTarget create(); }

    /** 帧类型：opacity 合成层或 transform 离屏图层 */
    private enum FrameKind { OPACITY, TRANSFORM }

    private final Deque<PaintContextFrame> frameStack = new ArrayDeque<PaintContextFrame>();
    private final List<UiRenderTarget> layerPool = new ArrayList<UiRenderTarget>();
    private final List<UiRenderTarget> pendingCloseLayers = new ArrayList<UiRenderTarget>();
    private int borrowedLayerCount;
    private boolean disabledForFrame;
    private Throwable pendingLayerCleanupFailure;
    private final HostImageRenderSession hostImageRenderSession = new HostImageRenderSession();
    private int hostImageResourceEpoch = HostImageResourceEpoch.current();
    private final LayerFactory layerFactory;

    /** 创建生产合成器。 */
    public PaintContextCompositor() { this(UiRenderTarget::new); }

    /** 创建注入离屏层的测试合成器。 */
    PaintContextCompositor(LayerFactory layerFactory) {
        this.layerFactory = layerFactory == null ? UiRenderTarget::new : layerFactory;
    }

    /**
     * 开始新一帧 paint context 回放。
     */
    public void beginFrame() {
        finishFrame();
        borrowedLayerCount = 0;
        disabledForFrame = false;
        pendingLayerCleanupFailure = retryPendingCloseLayers();
        if (pendingLayerCleanupFailure != null) {
            disabledForFrame = true;
        }
        hostImageRenderSession.beginFrame();
        int currentResourceEpoch = HostImageResourceEpoch.current();
        if (currentResourceEpoch != hostImageResourceEpoch && !hostImageRenderSession.hasPendingCleanup()) {
            try {
                hostImageRenderSession.clear();
                hostImageResourceEpoch = currentResourceEpoch;
            } catch (RuntimeException ignored) {
                // session 保留失败 owner；首次 item miss 会返回 ABORT_FRAME，下一帧再重试。
            } catch (LinkageError ignored) {
                // 可选 GL 链接失败同样走 session 的 cleanup barrier。
            }
        }
    }

    /**
     * 结束当前帧，防御性清理仍未弹出的 paint context。
     */
    public void finishFrame() {
        while (!frameStack.isEmpty()) {
            PaintContextFrame frame = frameStack.peek();
            if (!frame.active) {
                // inactive frame 从未 borrow layer，直接弹出无副作用（语义上比走具体 pop 方法更清晰）
                frameStack.pop();
                continue;
            }
            if (frame.kind == FrameKind.TRANSFORM) {
                popTransformLayer();
            } else {
                popGroupOpacity();
            }
        }
        borrowedLayerCount = 0;
    }

    /**
     * 释放已缓存的离屏层资源。
     */
    public void close() {
        Throwable[] firstFailure = new Throwable[1];
        while (!frameStack.isEmpty()) {
            int sizeBefore = frameStack.size();
            try {
                PaintContextFrame frame = frameStack.peek();
                if (!frame.active) {
                    frameStack.pop();
                } else if (frame.kind == FrameKind.TRANSFORM) {
                    popTransformLayer();
                } else {
                    popGroupOpacity();
                }
            } catch (Throwable failure) {
                rememberFailure(firstFailure, failure);
                if (frameStack.size() == sizeBefore) {
                    frameStack.pop();
                }
            }
        }
        borrowedLayerCount = 0;
        List<UiRenderTarget> ownedLayers = new ArrayList<UiRenderTarget>(layerPool);
        for (UiRenderTarget renderTarget : pendingCloseLayers) {
            if (!ownedLayers.contains(renderTarget)) {
                ownedLayers.add(renderTarget);
            }
        }
        layerPool.clear();
        pendingCloseLayers.clear();
        for (UiRenderTarget renderTarget : ownedLayers) {
            try {
                renderTarget.close();
            } catch (Throwable failure) {
                rememberFailure(firstFailure, failure);
                retainPendingCloseLayer(renderTarget);
            }
        }
        pendingLayerCleanupFailure = firstFailure[0];
        try {
            hostImageRenderSession.close();
        } catch (Throwable failure) {
            rememberFailure(firstFailure, failure);
        }
        rethrow(firstFailure[0]);
    }

    /**
     * 获取当前缓存的离屏层数量（诊断用，FBO 泄漏检测）。
     *
     * @return 池中 FBO 层数
     */
    public int __getPooledLayerCount() {
        return layerPool.size();
    }

    /** @return 已从复用池隔离、等待 close 重试的层数 */
    public int __getPendingCloseLayerCount() {
        return pendingCloseLayers.size();
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
            frameStack.push(PaintContextFrame.inactive(FrameKind.OPACITY, screenHeight));
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
            frameStack.push(PaintContextFrame.activeOpacity(layerIndex, layer, clampedLeft, clampedTop, clampedRight,
                    clampedBottom, clampedOpacity, parentFramebufferId, screenHeight));
        } catch (RuntimeException exception) {
            if (layerBegun && layer != null) {
                layer.end();
            }
            disabledForFrame = true;
            borrowedLayerCount = layerIndex;
            frameStack.push(PaintContextFrame.inactive(FrameKind.OPACITY, screenHeight));
        } catch (LinkageError error) {
            if (layerBegun && layer != null) {
                layer.end();
            }
            disabledForFrame = true;
            borrowedLayerCount = layerIndex;
            frameStack.push(PaintContextFrame.inactive(FrameKind.OPACITY, screenHeight));
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

    /**
     * 进入 transform 离屏图层作用域（B6 FBO 方案）。
     *
     * <p>借 FBO 离屏层 + MODELVIEW 归 I + 重建父 clip，使段内 scissor 在未变换坐标系下轴对齐正确裁剪。
     * FBO 不可用时降级为 inactive（保留 clip 放弃 transform），段内子树在外层 MODELVIEW 下直画。</p>
     */
    void pushTransformLayer(int screenWidth, int screenHeight, int left, int top, int right, int bottom,
            float translateX, float translateY, float rotateDegrees,
            float scaleX, float scaleY, float originXRatio, float originYRatio,
            ClipSnapshot clipSnapshot) {
        int clampedLeft = clampInt(Math.min(left, right), 0, screenWidth);
        int clampedTop = clampInt(Math.min(top, bottom), 0, screenHeight);
        int clampedRight = clampInt(Math.max(left, right), 0, screenWidth);
        int clampedBottom = clampInt(Math.max(top, bottom), 0, screenHeight);
        if (disabledForFrame || clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
            // 降级：保留 clip 放弃 transform。push inactive frame，不进 FBO，不压 T。
            // 段内子树在外层 MODELVIEW（无本层 T，但可能有祖先 T）下直画。
            // 无祖先 transform 时 clip 正确；有祖先 transform 时 scissor 不跟随祖先 T 旋转，
            // 退化为原 scissor 错位行为（降级 best-effort，FBO 不可用是极端场景）。
            frameStack.push(PaintContextFrame.inactive(FrameKind.TRANSFORM, screenHeight));
            return;
        }

        int layerIndex = borrowedLayerCount;
        UiRenderTarget layer = null;
        boolean layerBegun = false;
        boolean modelviewPushed = false;
        try {
            layer = borrowLayer(screenWidth, screenHeight);
            int parentFramebufferId = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
            layer.begin();
            layerBegun = true;
            // MODELVIEW 归 I（覆盖外层可能存在的祖先 T），保留 PROJECTION（段内子树命令+scissor 用屏幕坐标）
            // glPushAttrib 不保存矩阵栈，需手动 push/pop MODELVIEW（仿 drawHostImage :619-630）
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelviewPushed = true;
            GL11.glLoadIdentity();
            UiRenderContext.applyClipSnapshot(clipSnapshot, screenHeight);
            frameStack.push(PaintContextFrame.activeTransform(layerIndex, layer,
                    clampedLeft, clampedTop, clampedRight, clampedBottom,
                    translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio,
                    parentFramebufferId, clipSnapshot, screenHeight));
        } catch (RuntimeException exception) {
            cleanupTransformLayerFailure(layer, layerBegun, modelviewPushed, layerIndex);
            disabledForFrame = true;
            frameStack.push(PaintContextFrame.inactive(FrameKind.TRANSFORM, screenHeight));
        } catch (LinkageError error) {
            cleanupTransformLayerFailure(layer, layerBegun, modelviewPushed, layerIndex);
            disabledForFrame = true;
            frameStack.push(PaintContextFrame.inactive(FrameKind.TRANSFORM, screenHeight));
        }
    }

    /** FBO 借用/begin 失败时的防御性清理 */
    private void cleanupTransformLayerFailure(UiRenderTarget layer, boolean layerBegun,
            boolean modelviewPushed, int layerIndex) {
        if (modelviewPushed) {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }
        if (layerBegun && layer != null) {
            layer.end();
        }
        borrowedLayerCount = layerIndex;
    }

    /**
     * 退出 transform 离屏图层作用域（B6 FBO 方案）。
     *
     * <p>时序：MODELVIEW pop（恢复外层）→ end（切回父 FBO）→ applyClipSnapshot(父clip) →
     * pushTransform(T)（origin 三明治）→ composite 回贴（quad 吃 T 旋转，父 clip 二次裁切）→
     * popTransform。inactive frame 直接弹出无操作。</p>
     */
    boolean popTransformLayer() {
        if (frameStack.isEmpty()) {
            return false;
        }
        PaintContextFrame frame = frameStack.pop();
        if (!frame.active) {
            return false;
        }
        // 1. MODELVIEW pop（弹出段内的 I，恢复外层 MODELVIEW——不含本层 T）
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        // 2. end（切回父 FBO）
        frame.layer.end();
        // 3. applyClipSnapshot(父clip)（主 FBO 上重建父 clip，供回贴二次裁切）
        UiRenderContext.applyClipSnapshot(frame.clipSnapshot, frame.screenHeight);
        // 4. pushTransform(T)（压 T 矩阵，origin 三明治，与 UiRenderContext.pushTransform 同构）
        // 显式 glMatrixMode(MODELVIEW)：end() 的 glPopAttrib 可能恢复 matrix mode 到 begin 前状态
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        float originX = frame.left + frame.originXRatio * (frame.right - frame.left);
        float originY = frame.top + frame.originYRatio * (frame.bottom - frame.top);
        GL11.glTranslatef(originX + frame.translateX, originY + frame.translateY, 0.0f);
        GL11.glRotatef(frame.rotateDegrees, 0.0f, 0.0f, 1.0f);
        GL11.glScalef(frame.scaleX, frame.scaleY, 1.0f);
        GL11.glTranslatef(-originX, -originY, 0.0f);
        // 5. composite 回贴（quad 吃 T 旋转，父 clip 二次裁切——带参版 :171 不关 scissor 是物理基础）
        frame.layer.compositeToCurrentFramebuffer(frame.left, frame.top, frame.right, frame.bottom, 1.0F);
        // 6. popTransform（弹 T）
        GL11.glPopMatrix();
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

    /** 恢复可信度已丢失时从池中永久移除该隔离层，禁止后续帧复用。 */
    void discardIsolatedLayer(UiRenderTarget layer) {
        if (layer == null) {
            return;
        }
        int index = layerPool.indexOf(layer);
        if (index >= 0) {
            layerPool.remove(index);
            if (index < borrowedLayerCount) {
                borrowedLayerCount--;
            }
        }
        Throwable failure = closeOrRetainLayer(layer);
        rethrow(failure);
    }

    Throwable getPendingLayerCleanupFailure() {
        return pendingLayerCleanupFailure;
    }

    /** @return 当前 compositor 跨帧持有的宿主图片会话 */
    HostImageRenderSession getHostImageRenderSession() {
        return hostImageRenderSession;
    }

    private UiRenderTarget borrowLayer(int screenWidth, int screenHeight) {
        if (!pendingCloseLayers.isEmpty()) {
            throw new IllegalStateException("paint layer cleanup retry pending", pendingLayerCleanupFailure);
        }
        UiRenderTarget layer;
        boolean pooled = borrowedLayerCount < layerPool.size();
        if (pooled) {
            layer = layerPool.get(borrowedLayerCount);
        } else {
            layer = layerFactory.create();
        }
        try {
            layer.ensureSize(screenWidth, screenHeight);
        } catch (RuntimeException failure) {
            discardFailedBorrow(layer, pooled, failure);
            throw failure;
        } catch (LinkageError failure) {
            discardFailedBorrow(layer, pooled, failure);
            throw failure;
        } catch (Error failure) {
            discardFailedBorrow(layer, pooled, failure);
            throw failure;
        }
        if (!pooled) {
            layerPool.add(layer);
        }
        borrowedLayerCount++;
        return layer;
    }

    private void discardFailedBorrow(UiRenderTarget layer, boolean pooled, Throwable failure) {
        if (pooled) {
            layerPool.remove(layer);
        }
        try {
            Throwable cleanupFailure = closeOrRetainLayer(layer);
            if (cleanupFailure != null && cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        } catch (Error cleanupFailure) {
            if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
            throw cleanupFailure;
        }
    }

    private Throwable retryPendingCloseLayers() {
        Throwable[] firstFailure = new Throwable[1];
        List<UiRenderTarget> retry = new ArrayList<UiRenderTarget>(pendingCloseLayers);
        pendingCloseLayers.clear();
        for (UiRenderTarget layer : retry) {
            try {
                layer.close();
            } catch (RuntimeException failure) {
                rememberFailure(firstFailure, failure);
                retainPendingCloseLayer(layer);
            } catch (LinkageError failure) {
                rememberFailure(firstFailure, failure);
                retainPendingCloseLayer(layer);
            } catch (Error failure) {
                rememberFailure(firstFailure, failure);
                retainPendingCloseLayer(layer);
            }
        }
        if (isFatal(firstFailure[0])) throw (Error) firstFailure[0];
        return firstFailure[0];
    }

    private Throwable closeOrRetainLayer(UiRenderTarget layer) {
        if (layer == null) {
            return null;
        }
        try {
            layer.close();
            return null;
        } catch (RuntimeException failure) {
            retainPendingCloseLayer(layer);
            pendingLayerCleanupFailure = failure;
            return failure;
        } catch (LinkageError failure) {
            retainPendingCloseLayer(layer);
            pendingLayerCleanupFailure = failure;
            return failure;
        } catch (Error failure) {
            retainPendingCloseLayer(layer);
            pendingLayerCleanupFailure = failure;
            throw failure;
        }
    }

    private void retainPendingCloseLayer(UiRenderTarget layer) {
        if (layer != null && !pendingCloseLayers.contains(layer)) {
            pendingCloseLayers.add(layer);
        }
    }

    private static void rememberFailure(Throwable[] firstFailure, Throwable failure) {
        if (firstFailure[0] == null) {
            firstFailure[0] = failure;
        } else if (isFatal(failure) && !isFatal(firstFailure[0])) {
            if (firstFailure[0] != failure) failure.addSuppressed(firstFailure[0]);
            firstFailure[0] = failure;
        } else if (firstFailure[0] != failure) {
            firstFailure[0].addSuppressed(failure);
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("paint context close failed", failure);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * paint context 帧栈条目。
     */
    private static final class PaintContextFrame {

        private final FrameKind kind;
        private final int layerIndex;
        private final UiRenderTarget layer;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final float opacity;
        private final int parentFramebufferId;
        private final boolean active;
        /** 父 clip 快照，transform frame POP 时重建父 clip 供回贴二次裁切 */
        private final ClipSnapshot clipSnapshot;
        /** 入帧时的屏幕高度，transform frame POP 时 applyClipSnapshot 用（与 clipSnapshot 同级存帧，避免嵌套覆盖） */
        private final int screenHeight;
        // === transform 分量（TRANSFORM frame 专用，OPACITY frame 默认值） ===
        private final float translateX;
        private final float translateY;
        private final float rotateDegrees;
        private final float scaleX;
        private final float scaleY;
        private final float originXRatio;
        private final float originYRatio;

        private PaintContextFrame(FrameKind kind, int layerIndex, UiRenderTarget layer,
                int left, int top, int right, int bottom,
                float opacity, int parentFramebufferId, boolean active, ClipSnapshot clipSnapshot,
                int screenHeight,
                float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio) {
            this.kind = kind;
            this.layerIndex = layerIndex;
            this.layer = layer;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.opacity = opacity;
            this.parentFramebufferId = parentFramebufferId;
            this.active = active;
            this.clipSnapshot = clipSnapshot;
            this.screenHeight = screenHeight;
            this.translateX = translateX;
            this.translateY = translateY;
            this.rotateDegrees = rotateDegrees;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.originXRatio = originXRatio;
            this.originYRatio = originYRatio;
        }

        private static PaintContextFrame inactive(FrameKind kind, int screenHeight) {
            return new PaintContextFrame(kind, -1, null, 0, 0, 0, 0, 1.0F, 0, false, null,
                    screenHeight,
                    0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.5f, 0.5f);
        }

        private static PaintContextFrame activeOpacity(int layerIndex, UiRenderTarget layer, int left, int top,
                int right, int bottom, float opacity, int parentFramebufferId, int screenHeight) {
            return new PaintContextFrame(FrameKind.OPACITY, layerIndex, layer, left, top, right, bottom,
                    opacity, parentFramebufferId, true, null,
                    screenHeight,
                    0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.5f, 0.5f);
        }

        private static PaintContextFrame activeTransform(int layerIndex, UiRenderTarget layer,
                int left, int top, int right, int bottom,
                float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio,
                int parentFramebufferId, ClipSnapshot clipSnapshot, int screenHeight) {
            return new PaintContextFrame(FrameKind.TRANSFORM, layerIndex, layer, left, top, right, bottom,
                    1.0F, parentFramebufferId, true, clipSnapshot,
                    screenHeight,
                    translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio);
        }
    }
}
