package club.heiqi.uilib.ui.host;

import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import club.heiqi.uilib.ui.render.DeferredPostMainPass;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * 文档宿主共享的渲染运行时支持。
 */
public final class DocumentHostRenderSupport {

    /**
     * 一次性主后置回放批次。
     */
    public static final class DeferredPostMainReplayBatch {

        private static final DeferredPostMainReplayBatch EMPTY = new DeferredPostMainReplayBatch(null,
                Collections.<DeferredPostMainPass>emptyList());

        private final UiRenderContext context;
        private final List<DeferredPostMainPass> deferredPasses;
        private boolean replayClaimed;

        private DeferredPostMainReplayBatch(UiRenderContext context,
                List<DeferredPostMainPass> deferredPasses) {
            this.context = context;
            this.deferredPasses = deferredPasses;
        }

        /**
         * 当前批次是否为空。
         *
         * @return 是否无待回放内容
         */
        public boolean isEmpty() {
            return deferredPasses.isEmpty() || replayClaimed;
        }

        private List<DeferredPostMainPass> claimPasses() {
            if (deferredPasses.isEmpty() || replayClaimed) {
                return Collections.emptyList();
            }
            replayClaimed = true;
            return deferredPasses;
        }

        private void notifyReplayCompleted() {
            if (context != null) {
                context.notifyMainLayerContentChanged();
            }
        }
    }

    private DocumentHostRenderSupport() {}

    /**
     * 创建宿主渲染帧使用的上下文。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTicks 插值帧参数
     * @param paintContextCompositor paint context 合成器
     * @param mainLayerSnapshotService 主层快照服务
     * @param runtimeAdapters 运行时适配器
     * @return 渲染上下文
     */
    public static UiRenderContext createRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY,
            float partialTicks, PaintContextCompositor paintContextCompositor,
            UiMainLayerSnapshotService mainLayerSnapshotService, UiRuntimeAdapters runtimeAdapters) {
        return createRenderContext(screenWidth, screenHeight, mouseX, mouseY, partialTicks,
                paintContextCompositor, mainLayerSnapshotService, runtimeAdapters,
                BackdropBlurPolicy.inheritGlobal());
    }

    /**
     * 创建宿主渲染帧使用的上下文。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTicks 插值帧参数
     * @param paintContextCompositor paint context 合成器
     * @param mainLayerSnapshotService 主层快照服务
     * @param runtimeAdapters 运行时适配器
     * @param backdropBlurPolicy 页面级背景模糊策略
     * @return 渲染上下文
     */
    public static UiRenderContext createRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY,
            float partialTicks, PaintContextCompositor paintContextCompositor,
            UiMainLayerSnapshotService mainLayerSnapshotService, UiRuntimeAdapters runtimeAdapters,
            BackdropBlurPolicy backdropBlurPolicy) {
        return new UiRenderContext(screenWidth, screenHeight, mouseX, mouseY, partialTicks,
                paintContextCompositor, mainLayerSnapshotService, runtimeAdapters, backdropBlurPolicy);
    }

    /**
     * 准备主 UI 层稳定的 2D OpenGL 状态。
     */
    public static void prepareMainUiRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 从当前渲染上下文中提取一次性主后置回放批次。
     *
     * @param context 当前渲染上下文
     * @return 回放批次；无内容时返回空批次
     */
    public static DeferredPostMainReplayBatch drainDeferredPostMainReplayBatch(UiRenderContext context) {
        if (context == null) {
            return DeferredPostMainReplayBatch.EMPTY;
        }

        List<DeferredPostMainPass> deferredPasses = context.drainDeferredPostMainPasses();
        if (deferredPasses.isEmpty()) {
            return DeferredPostMainReplayBatch.EMPTY;
        }
        return new DeferredPostMainReplayBatch(context, deferredPasses);
    }

    /**
     * 在不依赖宿主 OpenGL 离屏目标的情况下回放主后置批次。
     *
     * <p>该入口主要用于共享消费语义测试：验证 deferred pass 会被真实执行，
     * 且回放完成后会推动主层内容版本递增。</p>
     *
     * @param replayBatch 已提取的回放批次
     */
    public static void replayDeferredPostMainPasses(DeferredPostMainReplayBatch replayBatch) {
        List<DeferredPostMainPass> deferredPasses = claimDeferredPostMainPasses(replayBatch);
        if (deferredPasses.isEmpty()) {
            return;
        }
        for (DeferredPostMainPass deferredPass : deferredPasses) {
            deferredPass.replay();
        }
        replayBatch.notifyReplayCompleted();
    }

    /**
     * 在主 UI 层完成后回放补充绘制层。
     *
     * @param context 当前渲染上下文
     * @param deferredRenderTarget 主后置离屏目标
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     */
    public static void flushDeferredPostMainPasses(UiRenderContext context, UiRenderTarget deferredRenderTarget,
            int nativeWidth, int nativeHeight) {
        flushDeferredPostMainPasses(drainDeferredPostMainReplayBatch(context), deferredRenderTarget, nativeWidth,
                nativeHeight);
    }

    /**
     * 在主 UI 层完成后回放已提取的补充绘制批次。
     *
     * @param replayBatch 已提取的回放批次
     * @param deferredRenderTarget 主后置离屏目标
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     */
    public static void flushDeferredPostMainPasses(DeferredPostMainReplayBatch replayBatch,
            UiRenderTarget deferredRenderTarget, int nativeWidth, int nativeHeight) {
        if (deferredRenderTarget == null) {
            return;
        }

        List<DeferredPostMainPass> deferredPasses = claimDeferredPostMainPasses(replayBatch);
        if (deferredPasses.isEmpty()) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        deferredRenderTarget.begin();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    for (DeferredPostMainPass deferredPass : deferredPasses) {
                        prepareDeferredPostMainReplayState(nativeWidth, nativeHeight);
                        UiRenderContext.applyClipSnapshot(deferredPass.getClipSnapshot(), nativeHeight);
                        deferredPass.replay();
                    }
                    UiRenderContext.clearClipState();
                } finally {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(previousMatrixMode);
            }
        } finally {
            deferredRenderTarget.end();
            GL11.glMatrixMode(previousMatrixMode);
        }

        deferredRenderTarget.compositeToCurrentFramebuffer();
        replayBatch.notifyReplayCompleted();
    }

    /**
     * 关闭共享渲染资源。
     *
     * @param paintContextCompositor paint context 合成器
     * @param mainLayerSnapshotService 主层快照服务
     * @param deferredRenderTarget 主后置离屏目标
     */
    public static void closeSharedRenderResources(PaintContextCompositor paintContextCompositor,
            UiMainLayerSnapshotService mainLayerSnapshotService, UiRenderTarget deferredRenderTarget) {
        if (deferredRenderTarget != null) {
            deferredRenderTarget.close();
        }
        if (paintContextCompositor != null) {
            paintContextCompositor.close();
        }
        if (mainLayerSnapshotService != null) {
            mainLayerSnapshotService.close();
        }
    }

    /**
     * 准备单个主后置回放批次的稳定 2D 初始状态。
     */
    private static void prepareDeferredPostMainReplayState(int nativeWidth, int nativeHeight) {
        UiRenderContext.clearClipState();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClearDepth(1.0D);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<DeferredPostMainPass> claimDeferredPostMainPasses(
            DeferredPostMainReplayBatch replayBatch) {
        if (replayBatch == null) {
            return Collections.emptyList();
        }
        return replayBatch.claimPasses();
    }
}
