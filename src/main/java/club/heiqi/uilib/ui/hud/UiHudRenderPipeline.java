package club.heiqi.uilib.ui.hud;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;

/**
 * HUD 文档渲染流水线。
 */
final class UiHudRenderPipeline {

    private final PaintContextCompositor paintContextCompositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
    private UiRenderTarget deferredPostMainRenderTarget;

    /**
     * 绘制当前屏幕分类下可见的 HUD 层。
     *
     * @param entries HUD 注册项
     * @param screenCategory 当前屏幕分类
     * @param partialTicks 插值帧参数
     */
    synchronized void renderVisibleLayers(List<UiHudDocumentHost.HudEntry> entries,
            UiHudScreenCategory screenCategory, float partialTicks) {
        if (entries == null || entries.isEmpty() || screenCategory == UiHudScreenCategory.MENU) {
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
        int fallbackMouseX = resolveFallbackMouseX(entries, mouseX, nativeWidth);
        int fallbackMouseY = resolveFallbackMouseY(entries, mouseY, nativeHeight);

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
                renderEntries(entries, screenCategory, partialTicks, nativeWidth, nativeHeight, guiWidth, guiHeight,
                        fallbackMouseX, fallbackMouseY, performanceMonitor);
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

    private void renderEntries(List<UiHudDocumentHost.HudEntry> entries, UiHudScreenCategory screenCategory,
            float partialTicks, int nativeWidth, int nativeHeight, int guiWidth, int guiHeight, int fallbackMouseX,
            int fallbackMouseY, UiPerformanceMonitor performanceMonitor) {
        for (UiHudDocumentHost.HudEntry entry : entries) {
            if (!entry.isVisibleIn(screenCategory)) {
                continue;
            }
            entry.widget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
            performanceMonitor.beginFrame(entry.getRuntimeName(), guiWidth, guiHeight, nativeWidth, nativeHeight);
            try {
                UiRenderContext context = DocumentHostRenderSupport.createRenderContext(nativeWidth, nativeHeight,
                        resolveEntryMouseX(entry, fallbackMouseX, nativeWidth),
                        resolveEntryMouseY(entry, fallbackMouseY, nativeHeight),
                        partialTicks, paintContextCompositor, mainLayerSnapshotService, entry.runtimeAdapters);
                entry.widget.render(context);
                flushDeferredPostMainPasses(context, nativeWidth, nativeHeight);
            } finally {
                performanceMonitor.finishFrame();
            }
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

    private static int resolveFallbackMouseX(List<UiHudDocumentHost.HudEntry> entries, int mouseX, int nativeWidth) {
        if (mouseX > 0) {
            return Math.min(mouseX, nativeWidth);
        }
        for (UiHudDocumentHost.HudEntry entry : entries) {
            if (entry.interactionSession.getLatestMouseX() > 0) {
                return Math.min(entry.interactionSession.getLatestMouseX(), nativeWidth);
            }
        }
        return 0;
    }

    private static int resolveFallbackMouseY(List<UiHudDocumentHost.HudEntry> entries, int mouseY, int nativeHeight) {
        if (mouseY > 0) {
            return Math.min(mouseY, nativeHeight);
        }
        for (UiHudDocumentHost.HudEntry entry : entries) {
            if (entry.interactionSession.getLatestMouseY() > 0) {
                return Math.min(entry.interactionSession.getLatestMouseY(), nativeHeight);
            }
        }
        return 0;
    }

    private static int resolveEntryMouseX(UiHudDocumentHost.HudEntry entry, int fallbackMouseX, int nativeWidth) {
        if (entry != null && entry.interactionSession.getLatestMouseX() > 0) {
            return Math.min(entry.interactionSession.getLatestMouseX(), nativeWidth);
        }
        return fallbackMouseX;
    }

    private static int resolveEntryMouseY(UiHudDocumentHost.HudEntry entry, int fallbackMouseY, int nativeHeight) {
        if (entry != null && entry.interactionSession.getLatestMouseY() > 0) {
            return Math.min(entry.interactionSession.getLatestMouseY(), nativeHeight);
        }
        return fallbackMouseY;
    }
}
