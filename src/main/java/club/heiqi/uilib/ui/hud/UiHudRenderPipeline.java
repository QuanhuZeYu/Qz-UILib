package club.heiqi.uilib.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * HUD 文档渲染流水线。
 */
final class UiHudRenderPipeline {

    private final PaintContextCompositor paintContextCompositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
    private UiRenderTarget deferredPostMainRenderTarget;

    /**
     * 绘制共享 HUD 场景。
     *
     * @param widget 共享 HUD widget
     * @param partialTicks 插值帧参数
     * @param runtimeAdapters 运行时适配器
     * @param latestMouseX 最近鼠标 X
     * @param latestMouseY 最近鼠标 Y
     */
    synchronized void renderVisibleLayers(HtmlLikeDocumentWidget widget, float partialTicks,
            UiRuntimeAdapters runtimeAdapters, int latestMouseX, int latestMouseY) {
        if (widget == null || runtimeAdapters == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        ScaledResolution scaledResolution = new ScaledResolution(minecraft, nativeWidth, nativeHeight);
        int guiWidth = scaledResolution.getScaledWidth();
        int guiHeight = scaledResolution.getScaledHeight();
        int mouseX = UiInputService.getInstance().getMouseX();
        int mouseY = UiInputService.getInstance().getMouseY();
        int resolvedMouseX = mouseX > 0 ? Math.min(mouseX, nativeWidth) : Math.min(Math.max(0, latestMouseX), nativeWidth);
        int resolvedMouseY = mouseY > 0 ? Math.min(mouseY, nativeHeight) : Math.min(Math.max(0, latestMouseY), nativeHeight);

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            DocumentHostRenderSupport.prepareMainUiRenderState();
            paintContextCompositor.beginFrame();
            mainLayerSnapshotService.beginFrame();
            try {
                widget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
                performanceMonitor.beginFrame("hud_shared", guiWidth, guiHeight, nativeWidth, nativeHeight);
                try {
                    UiRenderContext context = DocumentHostRenderSupport.createRenderContext(nativeWidth, nativeHeight,
                            resolvedMouseX, resolvedMouseY, partialTicks, paintContextCompositor,
                            mainLayerSnapshotService, runtimeAdapters);
                    widget.render(context);
                    flushDeferredPostMainPasses(context, nativeWidth, nativeHeight);
                } finally {
                    performanceMonitor.finishFrame();
                }
            } finally {
                mainLayerSnapshotService.finishFrame();
                paintContextCompositor.finishFrame();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private void flushDeferredPostMainPasses(UiRenderContext context, int nativeWidth, int nativeHeight) {
        if (context == null || !context.hasDeferredPostMainPasses()) {
            return;
        }
        UiRenderTarget renderTarget = getOrCreateDeferredPostMainRenderTarget();
        DocumentHostRenderSupport.DeferredPostMainReplayBatch replayBatch = prepareDeferredPostMainPasses(context,
                renderTarget::ensureSize, nativeWidth, nativeHeight);
        if (replayBatch.isEmpty()) {
            return;
        }
        DocumentHostRenderSupport.flushDeferredPostMainPasses(replayBatch, renderTarget, nativeWidth, nativeHeight);
    }

    /**
     * 准备 HUD 主后置回放批次并同步目标尺寸。
     *
     * @param context 当前渲染上下文
     * @param renderTargetSizer HUD 后置离屏目标尺寸同步器
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     * @return 已提取的回放批次
     */
    DocumentHostRenderSupport.DeferredPostMainReplayBatch prepareDeferredPostMainPasses(UiRenderContext context,
            UiHudDocumentHost.DeferredPostMainRenderTarget renderTargetSizer, int nativeWidth, int nativeHeight) {
        DocumentHostRenderSupport.DeferredPostMainReplayBatch replayBatch = DocumentHostRenderSupport
                .drainDeferredPostMainReplayBatch(context);
        if (!replayBatch.isEmpty()) {
            renderTargetSizer.ensureSize(nativeWidth, nativeHeight);
        }
        return replayBatch;
    }

    private UiRenderTarget getOrCreateDeferredPostMainRenderTarget() {
        if (deferredPostMainRenderTarget == null) {
            deferredPostMainRenderTarget = new UiRenderTarget();
        }
        return deferredPostMainRenderTarget;
    }
}
