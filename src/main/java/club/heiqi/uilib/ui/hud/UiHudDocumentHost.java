package club.heiqi.uilib.ui.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjglx.input.Mouse;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.host.DocumentHostInteractionSession;
import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.host.DocumentHostWidgetFactory;
import club.heiqi.uilib.ui.input.UiHostInputCaptureParticipant;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import club.heiqi.uilib.ui.input.UiNativeTextInputInspector;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;
import cpw.mods.fml.client.config.GuiConfig;

/**
 * 游戏内 HUD 文档宿主。
 *
 * @apiNote 类本身对外暴露 {@link #getInstance()} 与 {@link #register} 系列稳定 API；
 *          {@link #handleInputFrame}、{@link #handleImmediateKeyboardInput}、
 *          {@link #handleImmediateMouseInput}、{@link #renderHud}、{@link #renderOnScreen}
 *          仅供框架内部 forge 事件钩子调用，业务代码不应直接触发。LTS 不承诺这些钩子方法的兼容性。
 */
public final class UiHudDocumentHost implements UiHostInputCaptureParticipant {

    private static final UiHudDocumentHost INSTANCE = new UiHudDocumentHost();
    private static final String RUNTIME_NAME_INTERACTIVE = "hud_interactive";
    private static final String RUNTIME_NAME_PASSIVE = "hud_passive";
    private static final String RUNTIME_NAME_SHARED = "hud_shared";
    private static final String REGISTRATION_ID_ATTRIBUTE = "data-qz-hud-registration-id";
    private static final String LAYER_ROOT_ATTRIBUTE = "data-qz-hud-layer-root";
    private static final String HOST_SHELL_ATTRIBUTE = "data-qz-hud-host-shell";

    private final List<HudEntry> entries = new ArrayList<HudEntry>();
    private final UiHudRenderPipeline renderPipeline = new UiHudRenderPipeline();
    private final DocumentHostInteractionSession interactionSession = new DocumentHostInteractionSession();

    private boolean hudTextInputRequested;
    private HudEntry activeMouseEntry;
    private HudEntry activeKeyboardEntry;
    private HudEntry hoveredMouseEntry;
    private HudHostScreenSession screenSession = HudHostScreenSession.empty();

    private UiDocument sharedDocument;
    private HtmlLikeDocumentWidget sharedWidget;
    private ElementNode passiveLayerRoot;
    private ElementNode interactiveLayerRoot;
    private UiRuntimeAdapters sharedRuntimeAdapters;

    private long nextRegistrationId = 1L;
    private int routingDepth;
    private boolean pendingInteractionReset;
    private boolean pendingSceneDestroy;

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
     * <p>共享宿主会为每个注册项创建一棵独立的挂载根子树；所有 HUD 共享同一份底层文档与 widget，
     * 但作者只应操作当前注册项的 {@link UiHudMountContext#getMountRoot()}。</p>
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
     * <p>共享宿主生命周期内只有一套底层 widget 环境；并存 HUD 会复用首个活动注册项建立的
     * {@link TextMeasureService} 与 {@link UiRuntimeAdapters}，直到全部 HUD 注销后下一次重新初始化。</p>
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
        UiHudDocumentContentBuilder resolvedBuilder = Objects.requireNonNull(contentBuilder, "contentBuilder");
        ensureSharedScene(Objects.requireNonNull(textMeasureService, "textMeasureService"),
                Objects.requireNonNull(runtimeAdapters, "runtimeAdapters"));

        String registrationId = Long.toString(nextRegistrationId++);
        ElementNode hostShell = sharedDocument.div();
        ElementNode mountRoot = sharedDocument.div();
        applyHostShellContract(hostShell, registrationId);
        applyDefaultMountRootContract(mountRoot, resolvedLayerType, registrationId);
        hostShell.append(mountRoot);

        ElementNode layerRoot = resolvedLayerType == UiHudLayerType.PASSIVE ? passiveLayerRoot : interactiveLayerRoot;
        layerRoot.append(hostShell);

        HudEntry entry = new HudEntry(registrationId, resolvedLayerType, hostShell, mountRoot);
        entries.add(entry);
        try {
            resolvedBuilder.build(new UiHudMountContext(sharedDocument, mountRoot, resolvedLayerType, registrationId));
            syncEntryVisibility(resolveCurrentScreenCategory());
            return new RegistrationHandle(entry);
        } catch (RuntimeException exception) {
            entries.remove(entry);
            layerRoot.removeChild(hostShell);
            if (entries.isEmpty()) {
                destroySharedScene();
            }
            throw exception;
        }
    }

    /**
     * 在输入帧中刷新交互层输入。
     *
     * @param frame 输入快照
     * @apiNote 仅供框架内部输入分发链路调用，业务代码不应直接触发。LTS 不承诺签名稳定。
     */
    public synchronized void handleInputFrame(UiInputFrame frame) {
        if (frame == null || entries.isEmpty() || sharedWidget == null) {
            clearInteractiveStates();
            return;
        }
        updateLatestPointer(frame);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        GuiScreen currentScreen = minecraft.currentScreen;
        HudInputContext inputContext = createInputContext(currentScreen);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return;
        }
        routeMouseFrame(frame, inputContext);
        updateHudKeyboardCaptureState(inputContext.screenCategory);
        if (UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            UiInputFrame textFrame = extractCollectedTextFrame(frame);
            if (textFrame != null) {
                routeKeyboardFrame(textFrame, inputContext);
                updateHudKeyboardCaptureState(inputContext.screenCategory);
            }
        }
    }

    /**
     * 在宿主原生 `handleKeyboardInput()` 调用栈内即时路由当前键盘事件。
     *
     * <p>当前交互 HUD 的稳定产品约束是“必须先通过鼠标命中建立 HUD 焦点，之后才会继续接管键盘”。
     * 因此这里不会支持纯键盘首次进入 HUD，也不会在 HUD 尚未聚焦时直接抢占宿主首个键盘事件。</p>
     *
     * @param currentScreen 当前宿主界面
     * @param frame 当前键盘事件对应的即时输入快照
     * @return 是否应阻断宿主继续处理该键盘事件
     * @apiNote 仅供框架内部 forge 事件钩子调用，业务代码不应直接触发。LTS 不承诺签名稳定。
     */
    public synchronized boolean handleImmediateKeyboardInput(GuiScreen currentScreen, UiInputFrame frame) {
        if (frame == null || entries.isEmpty() || sharedWidget == null) {
            return false;
        }
        HudInputContext inputContext = createInputContext(currentScreen);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return false;
        }
        if (!UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            return false;
        }
        updateLatestPointer(frame);
        return routeImmediateKeyboardFrame(inputContext, frame,
                UiNativeTextInputInspector.hasFocusedTextInput(currentScreen),
                UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured());
    }

    /**
     * 在宿主原生 `handleMouseInput()` 调用栈内即时路由当前鼠标事件。
     *
     * @param currentScreen 当前宿主界面
     * @param frame 当前鼠标事件对应的即时输入快照
     * @return 是否应阻断宿主继续处理该鼠标事件
     * @apiNote 仅供框架内部 forge 事件钩子调用，业务代码不应直接触发。LTS 不承诺签名稳定。
     */
    public synchronized boolean handleImmediateMouseInput(GuiScreen currentScreen, UiInputFrame frame) {
        if (frame == null || frame.getMouseEvents().isEmpty() || entries.isEmpty() || sharedWidget == null) {
            return false;
        }
        updateLatestPointer(frame);
        HudInputContext inputContext = createInputContext(currentScreen);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return false;
        }
        HudMouseDecision mouseDecision = resolveImmediateMouseDecision(inputContext, frame);
        if (!mouseDecision.shouldCapture) {
            if (mouseDecision.shouldClearFocus) {
                clearInteractiveStates();
                restoreNativeTextInputFocusAfterHudRelease(currentScreen, mouseDecision);
            }
            return false;
        }
        if (mouseDecision.shouldBlurNativeTextInput) {
            UiNativeTextInputInspector.blurFocusedTextInputs(currentScreen);
        }
        routeMouseFrame(frame, inputContext);
        updateHudKeyboardCaptureState(inputContext.screenCategory);
        return true;
    }

    synchronized boolean handleImmediateMouseInputForTest(UiInputFrame frame, UiHudScreenCategory screenCategory) {
        if (frame == null || frame.getMouseEvents().isEmpty() || entries.isEmpty() || sharedWidget == null) {
            return false;
        }
        updateLatestPointer(frame);
        HudInputContext inputContext = createInputContextForTest(screenCategory);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return false;
        }
        HudMouseDecision mouseDecision = resolveImmediateMouseDecision(inputContext, frame);
        if (!mouseDecision.shouldCapture) {
            if (mouseDecision.shouldClearFocus) {
                clearInteractiveStates();
            }
            return false;
        }
        routeMouseFrame(frame, inputContext);
        updateHudKeyboardCaptureState(inputContext.screenCategory);
        return true;
    }

    synchronized boolean handleImmediateKeyboardInputForTest(UiInputFrame frame, UiHudScreenCategory screenCategory) {
        if (frame == null || entries.isEmpty() || sharedWidget == null) {
            return false;
        }
        HudInputContext inputContext = createInputContextForTest(screenCategory);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return false;
        }
        if (!UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            return false;
        }
        updateLatestPointer(frame);
        return routeImmediateKeyboardFrame(inputContext, frame, false,
                UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured());
    }

    synchronized void handleInputFrameForTest(UiInputFrame frame, UiHudScreenCategory screenCategory, int width,
            int height) {
        if (frame == null || entries.isEmpty() || sharedWidget == null) {
            return;
        }
        updateLatestPointer(frame);
        HudInputContext inputContext = createInputContextForTest(screenCategory);
        syncScreenSession(inputContext);
        syncEntryVisibility(inputContext.screenCategory);
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return;
        }
        applyViewportBounds(width, height);
        routeMouseFrame(frame, inputContext);
        updateHudKeyboardCaptureState(inputContext.screenCategory);
        UiInputFrame keyboardFrame = extractKeyboardFrame(frame);
        if (keyboardFrame != null && UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            routeKeyboardFrame(keyboardFrame, inputContext);
        }
        updateHudKeyboardCaptureState(inputContext.screenCategory);
    }

    private boolean routeImmediateKeyboardFrame(HudInputContext inputContext, UiInputFrame frame,
            boolean nativeTextInputFocused, boolean uiLibKeyboardCaptured) {
        if (!screenSession.screenHudFocusEstablished) {
            return false;
        }
        if (inputContext.nativeTextInputFocused && !UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            return false;
        }
        UiInputFrame routedFrame = filterKeyboardInput(frame, nativeTextInputFocused, uiLibKeyboardCaptured);
        if (routedFrame == null || (routedFrame.getKeyEvents().isEmpty() && routedFrame.getTextEvents().isEmpty())) {
            return false;
        }
        routeKeyboardFrame(routedFrame, inputContext);
        updateHudKeyboardCaptureState(inputContext.screenCategory);
        return UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured();
    }

    private void routeMouseFrame(UiInputFrame frame, HudInputContext inputContext) {
        if (frame == null || frame.getMouseEvents().isEmpty() || sharedWidget == null) {
            return;
        }
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return;
        }
        if (inputContext.syncNativeViewportBounds) {
            applyCurrentViewportBounds();
        }
        boolean primaryDown = isPrimaryMouseButtonDown(frame);
        boolean primaryUp = isPrimaryMouseButtonUp(frame);
        if (primaryDown) {
            HudEntry targetEntry = resolveMouseTargetEntry(inputContext, frame.getMouseX(), frame.getMouseY());
            if (targetEntry == null) {
                clearInteractiveStates();
                screenSession = screenSession.withHudFocusEstablished(false);
                return;
            }
            hoveredMouseEntry = targetEntry;
            activeMouseEntry = targetEntry;
            routeSharedSceneFrame(targetEntry.getRuntimeName(), mouseOnlyFrame(frame));
            refreshActiveKeyboardEntry(inputContext.screenCategory);
            screenSession = screenSession.withHudFocusEstablished(activeKeyboardEntry != null);
            return;
        }

        HudEntry targetEntry = resolveMouseFrameTargetEntry(inputContext, frame.getMouseX(), frame.getMouseY());
        if (targetEntry == null) {
            updateHoveredMouseEntry(null);
            return;
        }
        updateHoveredMouseEntry(targetEntry);
        routeSharedSceneFrame(targetEntry.getRuntimeName(), mouseOnlyFrame(frame));
        refreshActiveKeyboardEntry(inputContext.screenCategory);
        screenSession = screenSession.withHudFocusEstablished(activeKeyboardEntry != null);
        if (primaryUp) {
            activeMouseEntry = null;
        }
    }

    private void routeKeyboardFrame(UiInputFrame frame, HudInputContext inputContext) {
        if (frame == null || (frame.getKeyEvents().isEmpty() && frame.getTextEvents().isEmpty())
                || sharedWidget == null) {
            return;
        }
        if (!inputContext.interactiveInputEnabled) {
            clearInteractiveStates();
            return;
        }
        HudEntry targetEntry = resolveKeyboardTargetEntry(inputContext);
        if (targetEntry == null) {
            return;
        }
        routeSharedSceneFrame(targetEntry.getRuntimeName(), frame);
        refreshActiveKeyboardEntry(inputContext.screenCategory);
        screenSession = screenSession.withHudFocusEstablished(activeKeyboardEntry != null);
    }

    private void routeSharedSceneFrame(String runtimeName, UiInputFrame frame) {
        if (sharedWidget == null || frame == null) {
            return;
        }
        routingDepth++;
        try {
            interactionSession.route(runtimeName == null ? RUNTIME_NAME_SHARED : runtimeName, sharedWidget, frame);
        } finally {
            routingDepth--;
            if (routingDepth <= 0) {
                flushPendingStateResets();
            }
        }
    }

    private void flushPendingStateResets() {
        if (pendingSceneDestroy) {
            pendingSceneDestroy = false;
            pendingInteractionReset = false;
            interactionSession.clearInteractionState();
            destroySharedScene();
            return;
        }
        if (pendingInteractionReset) {
            pendingInteractionReset = false;
            interactionSession.clearInteractionState();
        }
    }

    private void scheduleInteractionReset() {
        if (routingDepth > 0) {
            pendingInteractionReset = true;
            return;
        }
        interactionSession.clearInteractionState();
    }

    private void scheduleSceneDestroy() {
        if (routingDepth > 0) {
            pendingSceneDestroy = true;
            return;
        }
        interactionSession.clearInteractionState();
        destroySharedScene();
    }

    private static UiInputFrame mouseOnlyFrame(UiInputFrame frame) {
        return new UiInputFrame(frame.getMouseX(), frame.getMouseY(), frame.getMouseEvents(),
                Collections.<club.heiqi.uilib.ui.event.UiKeyEvent>emptyList(),
                Collections.<club.heiqi.uilib.ui.event.UiTextInputEvent>emptyList());
    }

    private UiInputFrame extractKeyboardFrame(UiInputFrame frame) {
        if (frame == null) {
            return null;
        }
        if (frame.getKeyEvents().isEmpty() && frame.getTextEvents().isEmpty()) {
            return null;
        }
        return new UiInputFrame(frame.getMouseX(), frame.getMouseY(),
                Collections.<club.heiqi.uilib.ui.event.UiMouseEvent>emptyList(),
                frame.getKeyEvents(), frame.getTextEvents());
    }

    private UiInputFrame extractCollectedTextFrame(UiInputFrame frame) {
        if (frame == null || frame.getTextEvents().isEmpty()) {
            return null;
        }
        return new UiInputFrame(frame.getMouseX(), frame.getMouseY(),
                Collections.<club.heiqi.uilib.ui.event.UiMouseEvent>emptyList(),
                Collections.<club.heiqi.uilib.ui.event.UiKeyEvent>emptyList(),
                frame.getTextEvents());
    }

    private HudMouseDecision resolveImmediateMouseDecision(HudInputContext inputContext, UiInputFrame frame) {
        if (!inputContext.interactiveInputEnabled) {
            return HudMouseDecision.release();
        }
        if (frame != null && isPrimaryMouseButtonDown(frame)) {
            HudEntry targetEntry = resolveMouseTargetEntry(inputContext, frame.getMouseX(), frame.getMouseY());
            return targetEntry == null
                    ? HudMouseDecision.missAndClearFocus(currentScreenSupportsNativeRefocus(inputContext.currentScreen))
                    : HudMouseDecision.capture(inputContext.nativeTextInputFocused);
        }
        if (frame != null && isPrimaryMouseButtonUp(frame)) {
            return isInteractiveEntryAvailable(activeMouseEntry, inputContext.screenCategory)
                    ? HudMouseDecision.capture()
                    : HudMouseDecision.release();
        }
        return resolveMouseTargetEntry(inputContext, frame == null ? 0 : frame.getMouseX(),
                frame == null ? 0 : frame.getMouseY()) == null
                ? HudMouseDecision.release()
                : HudMouseDecision.capture();
    }

    private HudEntry resolveMouseFrameTargetEntry(HudInputContext inputContext, int mouseX, int mouseY) {
        if (isInteractiveEntryAvailable(activeMouseEntry, inputContext.screenCategory)) {
            return activeMouseEntry;
        }
        return resolveMouseTargetEntry(inputContext, mouseX, mouseY);
    }

    private HudEntry resolveMouseTargetEntry(HudInputContext inputContext, int mouseX, int mouseY) {
        if (!inputContext.interactiveInputEnabled || sharedWidget == null) {
            return null;
        }
        if (inputContext.syncNativeViewportBounds) {
            applyCurrentViewportBounds();
        }
        for (int index = inputContext.entrySnapshot.size() - 1; index >= 0; index--) {
            HudEntry entry = inputContext.entrySnapshot.get(index);
            if (!isInteractiveEntryAvailable(entry, inputContext.screenCategory)) {
                continue;
            }
            ElementNode hitElement = sharedWidget.findElementAtWithin(entry.mountRoot, mouseX, mouseY);
            if (shouldCaptureHit(entry, hitElement)) {
                return entry;
            }
        }
        return null;
    }

    private boolean shouldCaptureHit(HudEntry entry, ElementNode hitElement) {
        if (entry == null || hitElement == null || hitElement == entry.mountRoot) {
            return false;
        }
        return hitElement != null && (sharedWidget == null || !sharedWidget.isPassthroughHit(hitElement));
    }

    private void refreshActiveKeyboardEntry(UiHudScreenCategory screenCategory) {
        activeKeyboardEntry = resolveFocusedInteractiveEntry(screenCategory);
    }

    private HudEntry resolveKeyboardTargetEntry(HudInputContext inputContext) {
        activeKeyboardEntry = resolveFocusedInteractiveEntry(inputContext.screenCategory);
        return activeKeyboardEntry;
    }

    private HudEntry resolveFocusedInteractiveEntry(UiHudScreenCategory screenCategory) {
        if (sharedWidget == null) {
            return null;
        }
        HudEntry entry = resolveEntryForElement(sharedWidget.getFocusedElement());
        return isInteractiveEntryAvailable(entry, screenCategory) ? entry : null;
    }

    static UiInputFrame filterKeyboardInput(UiInputFrame frame, boolean nativeTextInputFocused,
            boolean uiLibKeyboardCaptured) {
        if (frame == null || !nativeTextInputFocused || uiLibKeyboardCaptured) {
            return frame;
        }
        if (frame.getKeyEvents().isEmpty() && frame.getTextEvents().isEmpty()) {
            return frame;
        }
        return new UiInputFrame(frame.getMouseX(), frame.getMouseY(), frame.getMouseEvents(),
                Collections.<club.heiqi.uilib.ui.event.UiKeyEvent>emptyList(),
                Collections.<club.heiqi.uilib.ui.event.UiTextInputEvent>emptyList());
    }

    private void syncScreenSession(HudInputContext inputContext) {
        HudHostScreenSession nextSession = HudHostScreenSession.from(inputContext);
        if (screenSession.isSameScreen(nextSession)) {
            screenSession = screenSession.withSnapshot(nextSession);
            if (screenSession.shouldReleaseHudCaptureForNativeTextInput()) {
                clearInteractiveStates();
            }
            return;
        }
        screenSession = nextSession;
        clearInteractiveStates();
    }

    private void updateLatestPointer(UiInputFrame frame) {
        interactionSession.recordPointer(frame);
    }

    private HudInputContext createInputContext(GuiScreen currentScreen) {
        UiHudScreenCategory screenCategory = classifyScreen(currentScreen);
        boolean interactiveInputEnabled = isInteractiveInputEnabled(currentScreen);
        boolean nativeTextInputFocused = shouldInspectNativeTextInput(screenCategory, interactiveInputEnabled)
                && UiNativeTextInputInspector.hasFocusedTextInput(currentScreen);
        return new HudInputContext(currentScreen, currentScreen == null ? null : currentScreen.getClass().getName(),
                screenCategory, interactiveInputEnabled, new ArrayList<HudEntry>(entries), true,
                nativeTextInputFocused);
    }

    /**
     * 判断当前 HUD 输入上下文是否需要探测宿主原生文本框焦点。
     *
     * @param screenCategory 当前屏幕分类
     * @param interactiveInputEnabled HUD 交互输入是否接通
     * @return 是否需要执行原生文本框焦点探测
     */
    static boolean shouldInspectNativeTextInput(UiHudScreenCategory screenCategory, boolean interactiveInputEnabled) {
        return screenCategory == UiHudScreenCategory.CONTAINER && interactiveInputEnabled;
    }

    private HudInputContext createInputContextForTest(UiHudScreenCategory screenCategory) {
        return new HudInputContext(null, null, screenCategory, screenCategory == UiHudScreenCategory.CONTAINER,
                new ArrayList<HudEntry>(entries), false, false);
    }

    /**
     * 返回首个交互 HUD 对应的 HTML-like 组件，供诊断读取滚动与输入状态。
     *
     * @return 首个交互 HUD 组件；不存在时返回 null
     */
    public synchronized HtmlLikeDocumentWidget getFirstInteractiveWidgetForDiagnostics() {
        if (sharedWidget == null) {
            return null;
        }
        for (HudEntry entry : entries) {
            if (entry.layerType == UiHudLayerType.INTERACTIVE) {
                return sharedWidget;
            }
        }
        return null;
    }

    /**
     * 返回当前 HUD 输入态诊断快照。
     *
     * @return HUD 输入态诊断快照
     */
    public synchronized HudInputDiagnosticsSnapshot getInputDiagnosticsSnapshot() {
        String screenClassName = screenSession.screenClassName;
        boolean nativeTextInputFocused = screenSession.nativeTextInputFocused;
        boolean hudKeyboardCaptured = UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured();
        String focusedHudElementTag = resolveFocusedHudElementTag();
        String activeHudName = activeKeyboardEntry == null ? "none" : activeKeyboardEntry.getDiagnosticName();
        return new HudInputDiagnosticsSnapshot(screenClassName, nativeTextInputFocused, hudKeyboardCaptured,
                activeHudName, focusedHudElementTag, screenSession.screenHudFocusEstablished);
    }

    private String resolveFocusedHudElementTag() {
        if (sharedWidget == null) {
            return "none";
        }
        ElementNode focusedElement = sharedWidget.getFocusedElement();
        HudEntry entry = resolveEntryForElement(focusedElement);
        if (entry == null || !entries.contains(entry)) {
            return "none";
        }
        return focusedElement == null ? "none" : focusedElement.getTagName();
    }

    /**
     * 在纯游戏 HUD 阶段绘制可见层。
     *
     * @param partialTicks 插值帧参数
     * @apiNote 仅供框架内部 forge {@code RenderGameOverlayEvent} 钩子调用，业务代码不应直接触发。
     */
    public synchronized void renderHud(float partialTicks) {
        renderVisibleLayers(UiHudScreenCategory.INGAME, partialTicks);
    }

    /**
     * 在普通 GuiScreen 上方绘制可见层。
     *
     * @param partialTicks 插值帧参数
     * @apiNote 仅供框架内部 forge {@code GuiScreenEvent.DrawScreenEvent.Post} 钩子调用，业务代码不应直接触发。
     */
    public synchronized void renderOnScreen(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        renderVisibleLayers(classifyScreen(minecraft.currentScreen), partialTicks);
    }

    private void renderVisibleLayers(UiHudScreenCategory screenCategory, float partialTicks) {
        if (sharedWidget == null || sharedRuntimeAdapters == null || screenCategory == UiHudScreenCategory.MENU) {
            syncEntryVisibility(screenCategory);
            return;
        }
        syncEntryVisibility(screenCategory);
        if (!hasVisibleEntryIn(screenCategory)) {
            return;
        }
        renderPipeline.renderVisibleLayers(sharedWidget, partialTicks, sharedRuntimeAdapters,
                interactionSession.getLatestMouseX(), interactionSession.getLatestMouseY());
    }

    private boolean hasVisibleEntryIn(UiHudScreenCategory screenCategory) {
        for (HudEntry entry : entries) {
            if (entry.isVisibleIn(screenCategory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前是否存在可见的 HUD 文档。
     *
     * @param currentScreen 当前屏幕
     * @return 是否存在可见层
     */
    public synchronized boolean hasVisibleLayer(GuiScreen currentScreen) {
        return hasVisibleLayerIn(classifyScreen(currentScreen));
    }

    synchronized boolean hasVisibleLayerForTest(Object screen, String screenClassName) {
        return hasVisibleLayerIn(classifyScreen(screen, screenClassName));
    }

    private boolean hasVisibleLayerIn(UiHudScreenCategory screenCategory) {
        for (HudEntry entry : entries) {
            if (entry.isVisibleIn(screenCategory)) {
                return true;
            }
        }
        return false;
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
            DeferredPostMainRenderTarget renderTargetSizer, int nativeWidth, int nativeHeight) {
        return renderPipeline.prepareDeferredPostMainPasses(context, renderTargetSizer, nativeWidth, nativeHeight);
    }

    private synchronized void unregister(HudEntry entry) {
        if (entry == null || sharedDocument == null) {
            return;
        }
        if (!entries.remove(entry)) {
            return;
        }
        detachTopLayerDescendants(entry.mountRoot);
        if (entry.hostShell.getParent() != null) {
            entry.hostShell.getParent().removeChild(entry.hostShell);
        }

        boolean shouldResetInteraction = entry == activeMouseEntry || entry == activeKeyboardEntry
                || entry == hoveredMouseEntry || entry.contains(resolveFocusedElement());

        if (activeMouseEntry == entry) {
            activeMouseEntry = null;
        }
        if (activeKeyboardEntry == entry) {
            activeKeyboardEntry = null;
        }
        if (hoveredMouseEntry == entry) {
            hoveredMouseEntry = null;
        }

        if (shouldResetInteraction) {
            scheduleInteractionReset();
        }

        if (entries.isEmpty()) {
            clearActiveInteractionEntries();
            screenSession = screenSession.withHudFocusEstablished(false);
            UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
            syncHudTextInputRequest(false);
            scheduleSceneDestroy();
            return;
        }
        updateHudKeyboardCaptureState(screenSession.screenCategory);
    }

    /**
     * 清空全部 HUD 注册并复位输入捕获状态。
     *
     * <p>用于客户端断开连接、退出到主菜单等生命周期切换：HUD 入口本身要求调用方手动 {@code unregister()}，
     * 但宿主切换世界时旧 HUD 的 widget 与会话已经失去意义，需要在显式钩子上一次性清理。</p>
     */
    public synchronized void clearAllRegistrations() {
        if (entries.isEmpty()) {
            UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
            syncHudTextInputRequest(false);
            screenSession = HudHostScreenSession.empty();
            destroySharedScene();
            return;
        }
        for (HudEntry entry : new ArrayList<HudEntry>(entries)) {
            try {
                detachTopLayerDescendants(entry.mountRoot);
                if (entry.hostShell.getParent() != null) {
                    entry.hostShell.getParent().removeChild(entry.hostShell);
                }
            } catch (RuntimeException exception) {
                // 清理路径不应抛出，最坏情况只是单个子树未释放，继续推进其余清理。
            }
        }
        entries.clear();
        clearActiveInteractionEntries();
        screenSession = HudHostScreenSession.empty();
        UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
        syncHudTextInputRequest(false);
        scheduleSceneDestroy();
    }

    private void clearInteractiveStates() {
        clearActiveInteractionEntries();
        if (sharedWidget != null) {
            scheduleInteractionReset();
        }
        screenSession = screenSession.withHudFocusEstablished(false);
        UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
        syncHudTextInputRequest(false);
    }

    private void updateHudKeyboardCaptureState(UiHudScreenCategory screenCategory) {
        activeKeyboardEntry = resolveFocusedInteractiveEntry(screenCategory);
        boolean captured = activeKeyboardEntry != null;
        if (!captured) {
            screenSession = screenSession.withHudFocusEstablished(false);
        }
        UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(captured);
        syncHudTextInputRequest(captured);
    }

    private void clearActiveInteractionEntries() {
        hoveredMouseEntry = null;
        activeMouseEntry = null;
        activeKeyboardEntry = null;
    }

    private void updateHoveredMouseEntry(HudEntry nextHoveredEntry) {
        if (hoveredMouseEntry == nextHoveredEntry) {
            return;
        }
        if (nextHoveredEntry == null && sharedWidget != null) {
            sharedWidget.onMouseLeave();
        }
        hoveredMouseEntry = nextHoveredEntry;
    }

    private void syncHudTextInputRequest(boolean shouldRequest) {
        if (hudTextInputRequested == shouldRequest) {
            return;
        }
        hudTextInputRequested = shouldRequest;
        UiKeyboardCaptureState.getInstance().setHudTextInputRequested(shouldRequest);
        if (shouldRequest) {
            UiInputService.getInstance().beginTextInput();
            return;
        }
        if (!UiKeyboardCaptureState.getInstance().shouldKeepTextInputActive()) {
            UiInputService.getInstance().endTextInput();
        }
    }

    private void ensureSharedScene(TextMeasureService textMeasureService, UiRuntimeAdapters runtimeAdapters) {
        if (sharedDocument != null && sharedWidget != null) {
            return;
        }
        sharedDocument = UiDocument.create();
        sharedDocument.setDefaultTextContentMode(TextContentMode.UILIB_RAW);
        applySharedRootContract(sharedDocument.getRootElement());

        passiveLayerRoot = sharedDocument.div();
        interactiveLayerRoot = sharedDocument.div();
        applyLayerRootContract(passiveLayerRoot, UiHudLayerType.PASSIVE);
        applyLayerRootContract(interactiveLayerRoot, UiHudLayerType.INTERACTIVE);
        sharedDocument.getRootElement().append(passiveLayerRoot);
        sharedDocument.getRootElement().append(interactiveLayerRoot);

        sharedWidget = DocumentHostWidgetFactory.createViewportDocumentWidget(sharedDocument, 320, 180,
                textMeasureService, false);
        sharedRuntimeAdapters = runtimeAdapters;
        UiLayoutInvalidationRegistry.registerRoot(sharedWidget);
    }

    private void destroySharedScene() {
        if (sharedWidget != null) {
            UiLayoutInvalidationRegistry.unregisterRoot(sharedWidget);
        }
        sharedDocument = null;
        sharedWidget = null;
        passiveLayerRoot = null;
        interactiveLayerRoot = null;
        sharedRuntimeAdapters = null;
        pendingInteractionReset = false;
        pendingSceneDestroy = false;
    }

    private void detachTopLayerDescendants(ElementNode subtreeRoot) {
        if (sharedDocument == null || subtreeRoot == null) {
            return;
        }
        for (ElementNode topLayerElement : new ArrayList<ElementNode>(sharedDocument.__getTopLayerElements())) {
            if (isElementWithinSubtree(topLayerElement, subtreeRoot)) {
                sharedDocument.__hideTopLayerElement(topLayerElement);
            }
        }
    }

    private void syncEntryVisibility(UiHudScreenCategory screenCategory) {
        for (HudEntry entry : entries) {
            entry.setHostVisible(entry.isVisibleIn(screenCategory));
        }
    }

    private ElementNode resolveFocusedElement() {
        return sharedWidget == null ? null : sharedWidget.getFocusedElement();
    }

    private HudEntry resolveEntryForElement(ElementNode element) {
        if (element == null) {
            return null;
        }
        for (DocumentNode current = element; current != null; current = current.getParent()) {
            for (HudEntry entry : entries) {
                if (entry.mountRoot == current || entry.hostShell == current) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static boolean isElementWithinSubtree(ElementNode element, ElementNode subtreeRoot) {
        if (element == null || subtreeRoot == null) {
            return false;
        }
        for (DocumentNode current = element; current != null; current = current.getParent()) {
            if (current == subtreeRoot) {
                return true;
            }
        }
        return false;
    }

    private static void applySharedRootContract(ElementNode root) {
        root.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setPointerEvents(UiPointerEvents.NONE);
        root.setAttribute("data-qz-hud-shared-root", "true");
    }

    private static void applyLayerRootContract(ElementNode layerRoot, UiHudLayerType layerType) {
        layerRoot.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setPointerEvents(UiPointerEvents.NONE);
        layerRoot.setAttribute(LAYER_ROOT_ATTRIBUTE, layerType.name().toLowerCase());
        if (layerType == UiHudLayerType.PASSIVE) {
            layerRoot.setAttribute("data-hit-test-hidden", "true");
        }
    }

    private static void applyHostShellContract(ElementNode hostShell, String registrationId) {
        hostShell.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setPointerEvents(UiPointerEvents.NONE);
        hostShell.setAttribute(REGISTRATION_ID_ATTRIBUTE, registrationId);
        hostShell.setAttribute(HOST_SHELL_ATTRIBUTE, "true");
    }

    private static void applyDefaultMountRootContract(ElementNode mountRoot, UiHudLayerType layerType,
            String registrationId) {
        mountRoot.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setPointerEvents(layerType == UiHudLayerType.PASSIVE ? UiPointerEvents.NONE : UiPointerEvents.AUTO);
        mountRoot.setAttribute(REGISTRATION_ID_ATTRIBUTE, registrationId);
        mountRoot.setAttribute("data-hud-layer", layerType.name().toLowerCase());
        if (layerType == UiHudLayerType.PASSIVE) {
            mountRoot.setAttribute("data-hit-test-hidden", "true");
        }
    }

    private void applyCurrentViewportBounds() {
        Minecraft minecraft;
        try {
            minecraft = Minecraft.getMinecraft();
        } catch (RuntimeException exception) {
            return;
        } catch (LinkageError error) {
            return;
        }
        if (minecraft == null) {
            return;
        }
        applyViewportBounds(Math.max(1, minecraft.displayWidth), Math.max(1, minecraft.displayHeight));
    }

    private void applyViewportBounds(int width, int height) {
        if (sharedWidget != null) {
            sharedWidget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
        }
    }

    private UiHudScreenCategory resolveCurrentScreenCategory() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            return classifyScreen(minecraft == null ? null : minecraft.currentScreen);
        } catch (RuntimeException exception) {
            return screenSession.screenCategory;
        } catch (LinkageError error) {
            return screenSession.screenCategory;
        }
    }

    private static boolean isInteractiveInputEnabled(GuiScreen currentScreen) {
        return isInteractiveInputEnabled((Object) currentScreen,
                currentScreen == null ? null : currentScreen.getClass().getName(), Mouse.isGrabbed());
    }

    @Override
    public boolean isHostInputCaptureEnabled(GuiScreen currentScreen, String screenClassName, boolean mouseGrabbed) {
        return isInteractiveInputEnabled((Object) currentScreen, screenClassName, mouseGrabbed);
    }

    public static boolean isInteractiveInputEnabled(Object screen, String screenClassName, boolean mouseGrabbed) {
        return classifyScreen(screen, screenClassName) == UiHudScreenCategory.CONTAINER && !mouseGrabbed;
    }

    private boolean isInteractiveEntryAvailable(HudEntry entry, UiHudScreenCategory screenCategory) {
        return entry != null && entries.contains(entry) && entry.layerType == UiHudLayerType.INTERACTIVE
                && entry.isVisibleIn(screenCategory);
    }

    private void restoreNativeTextInputFocusAfterHudRelease(GuiScreen currentScreen, HudMouseDecision mouseDecision) {
        if (mouseDecision == null || !mouseDecision.shouldRestoreNativeTextInputFocus) {
            return;
        }
        UiNativeTextInputInspector.focusPreferredTextInput(currentScreen);
    }

    private boolean currentScreenSupportsNativeRefocus(GuiScreen currentScreen) {
        return UiNativeTextInputInspector.supportsPreferredTextInputRefocus(currentScreen,
                currentScreen == null ? null : currentScreen.getClass().getName());
    }

    /**
     * 判断当前屏幕的 HUD 分类。
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
        if (isHiddenHudMenuScreen(screen, screenClassName)) {
            return UiHudScreenCategory.MENU;
        }
        if ("net.minecraft.client.gui.inventory.GuiContainer".equals(screenClassName)
                || "net.minecraft.client.gui.GuiChat".equals(screenClassName)
                || (screenClassName != null && screenClassName.startsWith("net.minecraft.client.gui.inventory."))) {
            return UiHudScreenCategory.CONTAINER;
        }
        if (screen == null) {
            return UiHudScreenCategory.INGAME;
        }
        return UiHudScreenCategory.CONTAINER;
    }

    private static boolean isHiddenHudMenuScreen(Object screen, String screenClassName) {
        if (screen instanceof GuiIngameMenu
                || screen instanceof GuiMainMenu
                || screen instanceof GuiSelectWorld
                || screen instanceof GuiMultiplayer
                || screen instanceof GuiConfig) {
            return true;
        }
        if (screenClassName == null || screenClassName.isEmpty()) {
            return false;
        }
        return "net.minecraft.client.gui.GuiIngameMenu".equals(screenClassName)
                || "net.minecraft.client.gui.GuiSelectWorld".equals(screenClassName)
                || "net.minecraft.client.gui.GuiMultiplayer".equals(screenClassName)
                || isKnownMainMenuScreenClass(screenClassName)
                || screenClassName.startsWith("cpw.mods.fml.client.config.")
                || screenClassName.startsWith("club.heiqi.uilib.config.");
    }

    private static boolean isKnownMainMenuScreenClass(String screenClassName) {
        return "net.minecraft.client.gui.GuiMainMenu".equals(screenClassName)
                || "galaxyspace.core.gui.GSGuiMainMenu".equals(screenClassName)
                || "net.minecraft.client.gui.screen.TitleScreen".equals(screenClassName)
                || "net.minecraft.client.gui.screens.TitleScreen".equals(screenClassName);
    }

    private boolean isPrimaryMouseButtonDown(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (club.heiqi.uilib.ui.event.UiMouseEvent mouseEvent : frame.getMouseEvents()) {
            if (mouseEvent != null
                    && mouseEvent.getAction() == club.heiqi.uilib.ui.event.UiMouseEvent.Action.BUTTON_DOWN
                    && mouseEvent.getButton() == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrimaryMouseButtonUp(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (club.heiqi.uilib.ui.event.UiMouseEvent mouseEvent : frame.getMouseEvents()) {
            if (mouseEvent != null
                    && mouseEvent.getAction() == club.heiqi.uilib.ui.event.UiMouseEvent.Action.BUTTON_UP
                    && mouseEvent.getButton() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * HUD 文档内容构建器。
     */
    public interface UiHudDocumentContentBuilder {

        /**
         * 组装 HUD 文档内容。
         *
         * @param context 当前注册项的挂载上下文
         */
        void build(UiHudMountContext context);
    }

    /**
     * HUD 挂载上下文。
     */
    public static final class UiHudMountContext {

        private final UiDocument document;
        private final ElementNode mountRoot;
        private final UiHudLayerType layerType;
        private final String registrationId;

        private UiHudMountContext(UiDocument document, ElementNode mountRoot, UiHudLayerType layerType,
                String registrationId) {
            this.document = document;
            this.mountRoot = mountRoot;
            this.layerType = layerType;
            this.registrationId = registrationId;
        }

        public UiDocument getDocument() {
            return document;
        }

        public ElementNode getMountRoot() {
            return mountRoot;
        }

        public UiHudLayerType getLayerType() {
            return layerType;
        }

        public String getRegistrationId() {
            return registrationId;
        }
    }

    /**
     * HUD 后置离屏目标的尺寸同步契约。
     */
    interface DeferredPostMainRenderTarget {

        /**
         * 同步离屏目标尺寸。
         *
         * @param width 原生宽度
         * @param height 原生高度
         */
        void ensureSize(int width, int height);
    }

    static final class HudEntry {

        final String registrationId;
        final UiHudLayerType layerType;
        final ElementNode hostShell;
        final ElementNode mountRoot;
        private boolean hostVisible = true;

        private HudEntry(String registrationId, UiHudLayerType layerType, ElementNode hostShell,
                ElementNode mountRoot) {
            this.registrationId = registrationId;
            this.layerType = layerType;
            this.hostShell = hostShell;
            this.mountRoot = mountRoot;
        }

        boolean isVisibleIn(UiHudScreenCategory screenCategory) {
            if (screenCategory == UiHudScreenCategory.MENU) {
                return false;
            }
            if (layerType == UiHudLayerType.PASSIVE) {
                return screenCategory == UiHudScreenCategory.INGAME;
            }
            return screenCategory == UiHudScreenCategory.INGAME || screenCategory == UiHudScreenCategory.CONTAINER;
        }

        boolean contains(ElementNode element) {
            return isElementWithinSubtree(element, mountRoot);
        }

        void setHostVisible(boolean visible) {
            if (hostVisible == visible) {
                return;
            }
            hostVisible = visible;
            hostShell.style().setDisplay(visible ? UiDisplay.BLOCK : UiDisplay.NONE);
        }

        String getRuntimeName() {
            return layerType == UiHudLayerType.PASSIVE ? RUNTIME_NAME_PASSIVE : RUNTIME_NAME_INTERACTIVE;
        }

        String getDiagnosticName() {
            return getRuntimeName() + "#" + registrationId;
        }
    }

    private static final class HudInputContext {

        private final GuiScreen currentScreen;
        private final String screenClassName;
        private final UiHudScreenCategory screenCategory;
        private final boolean interactiveInputEnabled;
        private final List<HudEntry> entrySnapshot;
        private final boolean syncNativeViewportBounds;
        private final boolean nativeTextInputFocused;

        private HudInputContext(GuiScreen currentScreen, String screenClassName, UiHudScreenCategory screenCategory,
                boolean interactiveInputEnabled, List<HudEntry> entrySnapshot, boolean syncNativeViewportBounds,
                boolean nativeTextInputFocused) {
            this.currentScreen = currentScreen;
            this.screenClassName = screenClassName;
            this.screenCategory = screenCategory;
            this.interactiveInputEnabled = interactiveInputEnabled;
            this.entrySnapshot = entrySnapshot;
            this.syncNativeViewportBounds = syncNativeViewportBounds;
            this.nativeTextInputFocused = nativeTextInputFocused;
        }
    }

    private static final class HudMouseDecision {

        private static final HudMouseDecision CAPTURE = new HudMouseDecision(true, false, false, false);
        private static final HudMouseDecision RELEASE = new HudMouseDecision(false, false, false, false);
        private static final HudMouseDecision MISS_AND_CLEAR_FOCUS = new HudMouseDecision(false, true, false, false);

        private final boolean shouldCapture;
        private final boolean shouldClearFocus;
        private final boolean shouldBlurNativeTextInput;
        private final boolean shouldRestoreNativeTextInputFocus;

        private HudMouseDecision(boolean shouldCapture, boolean shouldClearFocus, boolean shouldBlurNativeTextInput,
                boolean shouldRestoreNativeTextInputFocus) {
            this.shouldCapture = shouldCapture;
            this.shouldClearFocus = shouldClearFocus;
            this.shouldBlurNativeTextInput = shouldBlurNativeTextInput;
            this.shouldRestoreNativeTextInputFocus = shouldRestoreNativeTextInputFocus;
        }

        private static HudMouseDecision capture() {
            return CAPTURE;
        }

        private static HudMouseDecision capture(boolean shouldBlurNativeTextInput) {
            return shouldBlurNativeTextInput ? new HudMouseDecision(true, false, true, false) : CAPTURE;
        }

        private static HudMouseDecision release() {
            return RELEASE;
        }

        private static HudMouseDecision missAndClearFocus(boolean shouldRestoreNativeTextInputFocus) {
            return shouldRestoreNativeTextInputFocus
                    ? new HudMouseDecision(false, true, false, true)
                    : MISS_AND_CLEAR_FOCUS;
        }
    }

    private static final class HudHostScreenSession {

        private final GuiScreen screen;
        private final String screenClassName;
        private final UiHudScreenCategory screenCategory;
        private final boolean nativeTextInputFocused;
        private final boolean screenHudFocusEstablished;

        private HudHostScreenSession(GuiScreen screen, String screenClassName, UiHudScreenCategory screenCategory,
                boolean nativeTextInputFocused, boolean screenHudFocusEstablished) {
            this.screen = screen;
            this.screenClassName = screenClassName;
            this.screenCategory = screenCategory;
            this.nativeTextInputFocused = nativeTextInputFocused;
            this.screenHudFocusEstablished = screenHudFocusEstablished;
        }

        private static HudHostScreenSession empty() {
            return new HudHostScreenSession(null, null, UiHudScreenCategory.INGAME, false, false);
        }

        private static HudHostScreenSession from(HudInputContext inputContext) {
            if (inputContext == null) {
                return empty();
            }
            return new HudHostScreenSession(inputContext.currentScreen, inputContext.screenClassName,
                    inputContext.screenCategory, inputContext.nativeTextInputFocused, false);
        }

        private HudHostScreenSession withHudFocusEstablished(boolean established) {
            return new HudHostScreenSession(screen, screenClassName, screenCategory, nativeTextInputFocused,
                    established);
        }

        private HudHostScreenSession withSnapshot(HudHostScreenSession snapshot) {
            return new HudHostScreenSession(screen, snapshot.screenClassName, snapshot.screenCategory,
                    snapshot.nativeTextInputFocused, screenHudFocusEstablished);
        }

        private boolean isSameScreen(HudHostScreenSession other) {
            return other != null && screen == other.screen;
        }

        private boolean shouldReleaseHudCaptureForNativeTextInput() {
            return nativeTextInputFocused && !screenHudFocusEstablished;
        }
    }

    public static final class HudInputDiagnosticsSnapshot {

        private final String screenClassName;
        private final boolean nativeTextInputFocused;
        private final boolean hudKeyboardCaptured;
        private final String activeHudName;
        private final String focusedHudElementTag;
        private final boolean screenHudFocusEstablished;

        private HudInputDiagnosticsSnapshot(String screenClassName, boolean nativeTextInputFocused,
                boolean hudKeyboardCaptured, String activeHudName, String focusedHudElementTag,
                boolean screenHudFocusEstablished) {
            this.screenClassName = screenClassName;
            this.nativeTextInputFocused = nativeTextInputFocused;
            this.hudKeyboardCaptured = hudKeyboardCaptured;
            this.activeHudName = activeHudName;
            this.focusedHudElementTag = focusedHudElementTag;
            this.screenHudFocusEstablished = screenHudFocusEstablished;
        }

        public String getScreenClassName() {
            return screenClassName;
        }

        public boolean isNativeTextInputFocused() {
            return nativeTextInputFocused;
        }

        public boolean isHudKeyboardCaptured() {
            return hudKeyboardCaptured;
        }

        public String getActiveHudName() {
            return activeHudName;
        }

        public String getFocusedHudElementTag() {
            return focusedHudElementTag;
        }

        public boolean isScreenHudFocusEstablished() {
            return screenHudFocusEstablished;
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
