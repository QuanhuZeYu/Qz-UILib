package club.heiqi.uilib.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Mouse;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;

/**
 * 游戏内 HUD 文档宿主。
 */
public final class UiHudDocumentHost {

    private static final UiHudDocumentHost INSTANCE = new UiHudDocumentHost();

    private final List<HudEntry> entries = new ArrayList<HudEntry>();
    private final UiRenderContext.PaintContextCompositor paintContextCompositor = new UiRenderContext.PaintContextCompositor();
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
    private UiRenderTarget deferredPostMainRenderTarget;

    private UiHudDocumentHost() {}

    /**
     * 返回 HUD 宿主单例。
     *
     * @return HUD 宿主
     */
    public static UiHudDocumentHost getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个 HUD 文档。
     *
     * @param layerType HUD 层类型
     * @param contentBuilder 文档内容构建器
     * @return 注册句柄
     */
    public UiHudDocumentRegistration register(UiHudLayerType layerType, UiHudDocumentContentBuilder contentBuilder) {
        return register(layerType, contentBuilder, DefaultTextMeasureService.getInstance(),
                UiRuntimeAdapters.minecraftDefaults());
    }

    /**
     * 使用显式环境注册一个 HUD 文档。
     *
     * @param layerType HUD 层类型
     * @param contentBuilder 文档内容构建器
     * @param textMeasureService 文本测量服务
     * @param runtimeAdapters 运行时适配器
     * @return 注册句柄
     */
    public synchronized UiHudDocumentRegistration register(UiHudLayerType layerType,
            UiHudDocumentContentBuilder contentBuilder, TextMeasureService textMeasureService,
            UiRuntimeAdapters runtimeAdapters) {
        UiHudLayerType resolvedLayerType = Objects.requireNonNull(layerType, "layerType");
        UiDocument document = UiDocument.create();
        applyDefaultRootContract(document, resolvedLayerType);
        Objects.requireNonNull(contentBuilder, "contentBuilder").build(document);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 180,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        widget.setViewportRootScrollingEnabled(false);
        widget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        HudEntry entry = new HudEntry(resolvedLayerType, widget, Objects.requireNonNull(runtimeAdapters,
                "runtimeAdapters"));
        entries.add(entry);
        UiLayoutInvalidationRegistry.registerRoot(widget);
        return new RegistrationHandle(entry);
    }

    /**
     * 在输入帧中刷新交互层输入。
     *
     * @param frame 输入快照
     */
    public synchronized void handleInputFrame(UiInputFrame frame) {
        if (frame == null || entries.isEmpty()) {
            return;
        }
        updateLatestPointer(frame);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        GuiScreen currentScreen = minecraft.currentScreen;
        UiHudScreenCategory screenCategory = classifyScreen(currentScreen);
        boolean interactiveEnabled = isInteractiveInputEnabled(currentScreen);
        if (!interactiveEnabled) {
            clearInteractiveStates();
            return;
        }
        routeInteractiveEntries(frame, screenCategory, new ArrayList<HudEntry>(entries));
    }

    synchronized void handleInputFrameForTest(UiInputFrame frame, UiHudScreenCategory screenCategory, int width,
            int height) {
        if (frame == null || entries.isEmpty()) {
            return;
        }
        updateLatestPointer(frame);
        for (HudEntry entry : entries) {
            entry.widget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
        }
        routeInteractiveEntries(frame, screenCategory, new ArrayList<HudEntry>(entries));
    }

    private void updateLatestPointer(UiInputFrame frame) {
        for (HudEntry entry : entries) {
            entry.latestMouseX = frame.getMouseX();
            entry.latestMouseY = frame.getMouseY();
        }
    }

    private void routeInteractiveEntries(UiInputFrame frame, UiHudScreenCategory screenCategory,
            List<HudEntry> entrySnapshot) {
        for (HudEntry entry : entrySnapshot) {
            if (!entries.contains(entry)) {
                continue;
            }
            if (entry.layerType != UiHudLayerType.INTERACTIVE || !entry.isVisibleIn(screenCategory)) {
                continue;
            }
            UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
            performanceMonitor.beginInputRouting(entry.getRuntimeName(), frame);
            try {
                entry.inputRouter.route(entry.widget, frame);
            } finally {
                performanceMonitor.finishInputRouting();
            }
        }
    }

    /**
     * 在纯游戏 HUD 阶段绘制可见层。
     *
     * @param partialTicks 插值帧参数
     */
    public void renderHud(float partialTicks) {
        renderVisibleLayers(UiHudScreenCategory.INGAME, partialTicks);
    }

    /**
     * 在普通 GuiScreen 上方绘制可见层。
     *
     * @param partialTicks 插值帧参数
     */
    public void renderOnScreen(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        renderVisibleLayers(classifyScreen(minecraft.currentScreen), partialTicks);
    }

    /**
     * 判断当前是否存在可见的 HUD 文档。
     *
     * @param currentScreen 当前屏幕
     * @return 是否存在可见层
     */
    public synchronized boolean hasVisibleLayer(GuiScreen currentScreen) {
        UiHudScreenCategory screenCategory = classifyScreen(currentScreen);
        for (HudEntry entry : entries) {
            if (entry.isVisibleIn(screenCategory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前屏幕的 HUD 分类。
     *
     * @param screen 当前屏幕
     * @return 屏幕分类
     */
    public static UiHudScreenCategory classifyScreen(GuiScreen screen) {
        return classifyScreen(screen, screen == null ? null : screen.getClass().getName());
    }

    /**
     * 根据屏幕实例与类名推断 HUD 分类。
     *
     * <p>该辅助入口供测试和未来兼容层使用，避免单元测试必须触发 Minecraft 原版 GUI 静态初始化。</p>
     *
     * @param screen 当前屏幕实例
     * @param screenClassName 当前屏幕类名
     * @return 屏幕分类
     */
    static UiHudScreenCategory classifyScreen(Object screen, String screenClassName) {
        if (screen == null && (screenClassName == null || screenClassName.isEmpty())) {
            return UiHudScreenCategory.INGAME;
        }
        if (screen instanceof GuiContainer || screen instanceof GuiChat) {
            return UiHudScreenCategory.CONTAINER;
        }
        if (screen instanceof GuiIngameMenu || screen instanceof GuiMainMenu) {
            return UiHudScreenCategory.MENU;
        }
        if ("net.minecraft.client.gui.inventory.GuiContainer".equals(screenClassName)
                || "net.minecraft.client.gui.GuiChat".equals(screenClassName)
                || (screenClassName != null && screenClassName.startsWith("net.minecraft.client.gui.inventory."))) {
            return UiHudScreenCategory.CONTAINER;
        }
        if ("net.minecraft.client.gui.GuiIngameMenu".equals(screenClassName)
                || "net.minecraft.client.gui.GuiMainMenu".equals(screenClassName)
                || (screenClassName != null && screenClassName.startsWith("net.minecraft.client.gui.Gui"))) {
            return UiHudScreenCategory.MENU;
        }
        if (screen == null) {
            return UiHudScreenCategory.INGAME;
        }
        return UiHudScreenCategory.MENU;
    }

    private synchronized void renderVisibleLayers(UiHudScreenCategory screenCategory, float partialTicks) {
        if (entries.isEmpty() || screenCategory == UiHudScreenCategory.MENU) {
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
        int fallbackMouseX = mouseX;
        int fallbackMouseY = mouseY;
        if (!entries.isEmpty()) {
            fallbackMouseX = resolveFallbackMouseX(mouseX, nativeWidth);
            fallbackMouseY = resolveFallbackMouseY(mouseY, nativeHeight);
        }

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
            prepareMainUiRenderState();
            paintContextCompositor.beginFrame();
            mainLayerSnapshotService.beginFrame();
            try {
                for (HudEntry entry : entries) {
                    if (!entry.isVisibleIn(screenCategory)) {
                        continue;
                    }
                    entry.widget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
                    performanceMonitor.beginFrame(entry.getRuntimeName(), guiWidth, guiHeight, nativeWidth, nativeHeight);
                    try {
                        UiRenderContext context = new UiRenderContext(nativeWidth, nativeHeight,
                                resolveEntryMouseX(entry, fallbackMouseX, nativeWidth),
                                resolveEntryMouseY(entry, fallbackMouseY, nativeHeight),
                                partialTicks, paintContextCompositor, mainLayerSnapshotService, entry.runtimeAdapters);
                        entry.widget.render(context);
                        flushDeferredPostMainPasses(context, nativeWidth, nativeHeight);
                    } finally {
                        performanceMonitor.finishFrame();
                    }
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
        renderTarget.ensureSize(nativeWidth, nativeHeight);
        List<UiRenderContext.DeferredPostMainPass> deferredPasses = context.drainDeferredPostMainPasses();
        if (deferredPasses.isEmpty()) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        renderTarget.begin();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            try {
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
            renderTarget.end();
            GL11.glMatrixMode(previousMatrixMode);
        }
        renderTarget.compositeToCurrentFramebuffer();
        context.notifyMainLayerContentChanged();
    }

    private synchronized void unregister(HudEntry entry) {
        if (entry == null) {
            return;
        }
        if (entries.remove(entry)) {
            entry.inputRouter.clearInteractionState();
            UiLayoutInvalidationRegistry.unregisterRoot(entry.widget);
        }
    }

    private synchronized void clearInteractiveStates() {
        for (HudEntry entry : entries) {
            if (entry.layerType == UiHudLayerType.INTERACTIVE) {
                entry.inputRouter.clearInteractionState();
            }
        }
    }

    private static void applyDefaultRootContract(UiDocument document, UiHudLayerType layerType) {
        ElementNode root = Objects.requireNonNull(document, "document").getRootElement();
        root.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        root.setAttribute("data-hud-layer", layerType.name().toLowerCase());
        if (layerType == UiHudLayerType.PASSIVE) {
            root.setAttribute("data-hit-test-hidden", "true");
        }
    }

    private static boolean isInteractiveInputEnabled(GuiScreen currentScreen) {
        return isInteractiveInputEnabled(currentScreen, currentScreen == null ? null : currentScreen.getClass().getName(),
                Mouse.isGrabbed());
    }

    static boolean isInteractiveInputEnabled(Object screen, String screenClassName, boolean mouseGrabbed) {
        return (screen != null || screenClassName != null) && !mouseGrabbed;
    }

    private UiRenderTarget getOrCreateDeferredPostMainRenderTarget() {
        if (deferredPostMainRenderTarget == null) {
            deferredPostMainRenderTarget = new UiRenderTarget();
        }
        return deferredPostMainRenderTarget;
    }

    private int resolveFallbackMouseX(int mouseX, int nativeWidth) {
        if (mouseX > 0) {
            return Math.min(mouseX, nativeWidth);
        }
        for (HudEntry entry : entries) {
            if (entry.latestMouseX > 0) {
                return Math.min(entry.latestMouseX, nativeWidth);
            }
        }
        return 0;
    }

    private int resolveFallbackMouseY(int mouseY, int nativeHeight) {
        if (mouseY > 0) {
            return Math.min(mouseY, nativeHeight);
        }
        for (HudEntry entry : entries) {
            if (entry.latestMouseY > 0) {
                return Math.min(entry.latestMouseY, nativeHeight);
            }
        }
        return 0;
    }

    private static int resolveEntryMouseX(HudEntry entry, int fallbackMouseX, int nativeWidth) {
        if (entry != null && entry.latestMouseX > 0) {
            return Math.min(entry.latestMouseX, nativeWidth);
        }
        return fallbackMouseX;
    }

    private static int resolveEntryMouseY(HudEntry entry, int fallbackMouseY, int nativeHeight) {
        if (entry != null && entry.latestMouseY > 0) {
            return Math.min(entry.latestMouseY, nativeHeight);
        }
        return fallbackMouseY;
    }

    private static void prepareMainUiRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

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

    /**
     * HUD 文档内容构建器。
     */
    public interface UiHudDocumentContentBuilder {

        /**
         * 组装 HUD 文档内容。
         *
         * @param document HUD 文档
         */
        void build(UiDocument document);
    }

    private static final class HudEntry {

        private final UiHudLayerType layerType;
        private final HtmlLikeDocumentWidget widget;
        private final UiInputRouter inputRouter = new UiInputRouter();
        private final UiRuntimeAdapters runtimeAdapters;
        private int latestMouseX;
        private int latestMouseY;

        private HudEntry(UiHudLayerType layerType, HtmlLikeDocumentWidget widget, UiRuntimeAdapters runtimeAdapters) {
            this.layerType = layerType;
            this.widget = widget;
            this.runtimeAdapters = runtimeAdapters;
        }

        private boolean isVisibleIn(UiHudScreenCategory screenCategory) {
            if (screenCategory == UiHudScreenCategory.MENU) {
                return false;
            }
            if (layerType == UiHudLayerType.PASSIVE) {
                return screenCategory == UiHudScreenCategory.INGAME;
            }
            return screenCategory == UiHudScreenCategory.INGAME || screenCategory == UiHudScreenCategory.CONTAINER;
        }

        private String getRuntimeName() {
            return layerType == UiHudLayerType.PASSIVE ? "hud_passive" : "hud_interactive";
        }
    }

    private final class RegistrationHandle implements UiHudDocumentRegistration {

        private final HudEntry entry;
        private boolean unregistered;

        private RegistrationHandle(HudEntry entry) {
            this.entry = entry;
        }

        @Override
        public void unregister() {
            if (unregistered) {
                return;
            }
            unregistered = true;
            UiHudDocumentHost.this.unregister(entry);
        }
    }
}
