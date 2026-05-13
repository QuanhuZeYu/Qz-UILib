package club.heiqi.uilib.ui.host;

import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * 文档宿主共享的渲染运行时支持。
 */
public final class DocumentHostRenderSupport {

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
            float partialTicks, UiRenderContext.PaintContextCompositor paintContextCompositor,
            UiMainLayerSnapshotService mainLayerSnapshotService, UiRuntimeAdapters runtimeAdapters) {
        return new UiRenderContext(screenWidth, screenHeight, mouseX, mouseY, partialTicks,
                paintContextCompositor, mainLayerSnapshotService, runtimeAdapters);
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
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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
        if (context == null || deferredRenderTarget == null || !context.hasDeferredPostMainPasses()) {
            return;
        }

        List<UiRenderContext.DeferredPostMainPass> deferredPasses = context.drainDeferredPostMainPasses();
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
                    for (UiRenderContext.DeferredPostMainPass deferredPass : deferredPasses) {
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
        context.notifyMainLayerContentChanged();
    }

    /**
     * 关闭共享渲染资源。
     *
     * @param paintContextCompositor paint context 合成器
     * @param mainLayerSnapshotService 主层快照服务
     * @param deferredRenderTarget 主后置离屏目标
     */
    public static void closeSharedRenderResources(UiRenderContext.PaintContextCompositor paintContextCompositor,
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
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
