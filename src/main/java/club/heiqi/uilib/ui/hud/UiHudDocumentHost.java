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
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.host.DocumentHostInteractionSession;
import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.host.DocumentHostWidgetFactory;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.input.UiHostInputCaptureParticipant;
import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import club.heiqi.uilib.ui.input.UiNativeTextInputInspector;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.props.UiOverflow;
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

    private final List<HudEntry> entries = new ArrayList<HudEntry>();
    private final UiHudRenderPipeline renderPipeline = new UiHudRenderPipeline();
    private boolean hudTextInputRequested;
    private HudEntry activeMouseEntry;
    private HudEntry activeKeyboardEntry;
    private HudEntry hoveredMouseEntry;

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
     * <p>HUD 层语义：</p>
     * <ul>
     *   <li>{@link UiHudLayerType#INTERACTIVE INTERACTIVE}：仅在 {@link GuiContainer} 类宿主界面下可交互，
     *       且只在主鼠标按键释放阶段路由输入；其他 {@link GuiScreen} 子类（如主菜单、聊天、设置）只渲染不可点。</li>
     *   <li>{@link UiHudLayerType#PASSIVE PASSIVE}：只在没有任何 {@link GuiScreen} 打开（纯游戏内 HUD 阶段）时可见，
     *       不接收输入；用于战斗/状态指示等仅显示用途。</li>
     * </ul>
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
     * <p>层语义同 {@link #register(UiHudLayerType, UiHudDocumentContentBuilder)}。</p>
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
        document.setDefaultTextContentMode(TextContentMode.UILIB_RAW);
        applyDefaultRootContract(document, resolvedLayerType);
        Objects.requireNonNull(contentBuilder, "contentBuilder").build(document);

        HtmlLikeDocumentWidget widget = DocumentHostWidgetFactory.createViewportDocumentWidget(document, 320, 180,
                Objects.requireNonNull(textMeasureService, "textMeasureService"), false);

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
     * @apiNote 仅供框架内部输入分发链路调用，业务代码不应直接触发。LTS 不承诺签名稳定。
     */
    public synchronized void handleInputFrame(UiInputFrame frame) {
        if (frame == null || entries.isEmpty()) {
            clearInteractiveStates();
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
        boolean keyboardCapturedBeforeRouting = UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured();
        routeMouseFrame(frame, screenCategory, new ArrayList<HudEntry>(entries));
        if (UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            UiInputFrame keyboardFrame = filterKeyboardInput(extractKeyboardFrame(frame),
                    UiNativeTextInputInspector.hasFocusedTextInput(currentScreen), keyboardCapturedBeforeRouting);
            routeKeyboardFrame(keyboardFrame, screenCategory, new ArrayList<HudEntry>(entries));
            updateHudKeyboardCaptureState();
            return;
        }
        updateHudKeyboardCaptureState();
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
        if (frame == null || entries.isEmpty()) {
            return false;
        }
        if (!isInteractiveInputEnabled(currentScreen)) {
            clearInteractiveStates();
            return false;
        }
        if (!UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            return false;
        }
        updateLatestPointer(frame);
        UiHudScreenCategory screenCategory = classifyScreen(currentScreen);
        boolean keyboardCapturedBeforeRouting = UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured();
        UiInputFrame routedFrame = filterKeyboardInput(frame,
                UiNativeTextInputInspector.hasFocusedTextInput(currentScreen),
                keyboardCapturedBeforeRouting);
        if (routedFrame.getKeyEvents().isEmpty() && routedFrame.getTextEvents().isEmpty()) {
            return false;
        }
        routeKeyboardFrame(routedFrame, screenCategory, new ArrayList<HudEntry>(entries));
        updateHudKeyboardCaptureState();
        return UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured();
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
        if (frame == null || frame.getMouseEvents().isEmpty() || entries.isEmpty()) {
            return false;
        }
        updateLatestPointer(frame);
        UiHudScreenCategory screenCategory = classifyScreen(currentScreen);
        if (!isInteractiveInputEnabled(currentScreen)) {
            clearInteractiveStates();
            return false;
        }
        boolean shouldCapture = shouldCaptureImmediateMouseInput(screenCategory, frame);
        if (!shouldCapture) {
            if (isPrimaryMouseButtonDown(frame)) {
                clearInteractiveStates();
            }
            return false;
        }
        UiNativeTextInputInspector.blurFocusedTextInputs(currentScreen);
        routeMouseFrame(frame, screenCategory, new ArrayList<HudEntry>(entries));
        updateHudKeyboardCaptureState();
        return true;
    }

    synchronized boolean handleImmediateMouseInputForTest(UiInputFrame frame, UiHudScreenCategory screenCategory) {
        if (frame == null || frame.getMouseEvents().isEmpty() || entries.isEmpty()) {
            return false;
        }
        updateLatestPointer(frame);
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            clearInteractiveStates();
            return false;
        }
        if (!shouldCaptureImmediateMouseInput(screenCategory, frame)) {
            return false;
        }
        routeMouseFrame(frame, screenCategory, new ArrayList<HudEntry>(entries));
        updateHudKeyboardCaptureState();
        return true;
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

    synchronized boolean handleImmediateKeyboardInputForTest(UiInputFrame frame, UiHudScreenCategory screenCategory) {
        if (frame == null || entries.isEmpty()) {
            return false;
        }
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            clearInteractiveStates();
            return false;
        }
        if (!UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            return false;
        }
        updateLatestPointer(frame);
        routeKeyboardFrame(frame, screenCategory, new ArrayList<HudEntry>(entries));
        updateHudKeyboardCaptureState();
        return UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured();
    }

    synchronized void handleInputFrameForTest(UiInputFrame frame, UiHudScreenCategory screenCategory, int width,
            int height) {
        if (frame == null || entries.isEmpty()) {
            return;
        }
        updateLatestPointer(frame);
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            clearInteractiveStates();
            return;
        }
        for (HudEntry entry : entries) {
            entry.widget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
        }
        routeMouseFrame(frame, screenCategory, new ArrayList<HudEntry>(entries));
        UiInputFrame keyboardFrame = extractKeyboardFrame(frame);
        if (keyboardFrame != null) {
            routeKeyboardFrame(keyboardFrame, screenCategory, new ArrayList<HudEntry>(entries));
        }
        updateHudKeyboardCaptureState();
    }

    /**
     * 返回首个交互 HUD 对应的 HTML-like 组件，供诊断读取滚动与输入状态。
     *
     * @return 首个交互 HUD 组件；不存在时返回 null
     */
    public synchronized HtmlLikeDocumentWidget getFirstInteractiveWidgetForDiagnostics() {
        for (HudEntry entry : entries) {
            if (entry.layerType == UiHudLayerType.INTERACTIVE) {
                return entry.widget;
            }
        }
        return null;
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

    private void updateLatestPointer(UiInputFrame frame) {
        for (HudEntry entry : entries) {
            entry.interactionSession.recordPointer(frame);
        }
    }

    private void routeMouseFrame(UiInputFrame frame, UiHudScreenCategory screenCategory, List<HudEntry> entrySnapshot) {
        if (frame == null || frame.getMouseEvents().isEmpty()) {
            return;
        }
        applyCurrentViewportBounds(entrySnapshot);
        boolean primaryDown = isPrimaryMouseButtonDown(frame);
        boolean primaryUp = isPrimaryMouseButtonUp(frame);
        if (primaryDown) {
            HudEntry targetEntry = resolveMouseTargetEntry(screenCategory, frame.getMouseX(), frame.getMouseY(),
                    entrySnapshot);
            if (targetEntry == null) {
                clearInteractiveStates();
                return;
            }
            clearInteractiveStatesExcept(targetEntry);
            hoveredMouseEntry = targetEntry;
            activeMouseEntry = targetEntry;
            UiInputFrame mouseFrame = new UiInputFrame(frame.getMouseX(), frame.getMouseY(), frame.getMouseEvents(),
                    Collections.<club.heiqi.uilib.ui.event.UiKeyEvent>emptyList(),
                    Collections.<club.heiqi.uilib.ui.event.UiTextInputEvent>emptyList());
            targetEntry.interactionSession.route(targetEntry.getRuntimeName(), targetEntry.widget, mouseFrame);
            if (targetEntry.interactionSession.hasFocusedWidget()) {
                activeKeyboardEntry = targetEntry;
            }
            updateHudKeyboardCaptureState();
            return;
        }
        HudEntry targetEntry = resolveMouseFrameTargetEntry(screenCategory, frame.getMouseX(), frame.getMouseY(),
                entrySnapshot);
        if (targetEntry == null) {
            updateHoveredMouseEntry(null);
            return;
        }
        updateHoveredMouseEntry(targetEntry);
        UiInputFrame mouseFrame = new UiInputFrame(frame.getMouseX(), frame.getMouseY(), frame.getMouseEvents(),
                Collections.<club.heiqi.uilib.ui.event.UiKeyEvent>emptyList(),
                Collections.<club.heiqi.uilib.ui.event.UiTextInputEvent>emptyList());
        targetEntry.interactionSession.route(targetEntry.getRuntimeName(), targetEntry.widget, mouseFrame);
        if (targetEntry.interactionSession.hasFocusedWidget()) {
            activeKeyboardEntry = targetEntry;
        }
        if (primaryUp) {
            activeMouseEntry = null;
        }
        updateHudKeyboardCaptureState();
    }

    private void routeKeyboardFrame(UiInputFrame frame, UiHudScreenCategory screenCategory, List<HudEntry> entrySnapshot) {
        if (frame == null || (frame.getKeyEvents().isEmpty() && frame.getTextEvents().isEmpty())) {
            return;
        }
        HudEntry targetEntry = resolveKeyboardTargetEntry(screenCategory, entrySnapshot);
        if (targetEntry == null) {
            return;
        }
        targetEntry.interactionSession.route(targetEntry.getRuntimeName(), targetEntry.widget, frame);
        if (targetEntry.interactionSession.hasFocusedWidget()) {
            activeKeyboardEntry = targetEntry;
            activeMouseEntry = targetEntry;
        } else if (activeKeyboardEntry == targetEntry) {
            activeKeyboardEntry = null;
        }
    }

    private UiInputFrame extractKeyboardFrame(UiInputFrame frame) {
        if (frame == null) {
            return null;
        }
        if (frame.getKeyEvents().isEmpty() && frame.getTextEvents().isEmpty()) {
            return null;
        }
        return new UiInputFrame(frame.getMouseX(), frame.getMouseY(), Collections.<club.heiqi.uilib.ui.event.UiMouseEvent>emptyList(),
                frame.getKeyEvents(), frame.getTextEvents());
    }

    private boolean shouldCaptureImmediateMouseInput(UiHudScreenCategory screenCategory, UiInputFrame frame) {
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            return false;
        }
        if (frame != null && isPrimaryMouseButtonDown(frame)) {
            return resolveMouseTargetEntry(screenCategory, frame.getMouseX(), frame.getMouseY(),
                    new ArrayList<HudEntry>(entries)) != null;
        }
        if (frame != null && isPrimaryMouseButtonUp(frame)) {
            return activeMouseEntry != null && isInteractiveEntryAvailable(activeMouseEntry, screenCategory);
        }
        if (activeMouseEntry != null && isInteractiveEntryAvailable(activeMouseEntry, screenCategory)) {
            return true;
        }
        return resolveMouseTargetEntry(screenCategory, frame == null ? 0 : frame.getMouseX(),
                frame == null ? 0 : frame.getMouseY(), new ArrayList<HudEntry>(entries)) != null;
    }

    private HudEntry resolveMouseFrameTargetEntry(UiHudScreenCategory screenCategory, int mouseX, int mouseY,
            List<HudEntry> entrySnapshot) {
        if (activeMouseEntry != null && isInteractiveEntryAvailable(activeMouseEntry, screenCategory)) {
            return activeMouseEntry;
        }
        return resolveMouseTargetEntry(screenCategory, mouseX, mouseY, entrySnapshot);
    }

    private HudEntry resolveMouseTargetEntry(UiHudScreenCategory screenCategory, int mouseX, int mouseY,
            List<HudEntry> entrySnapshot) {
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            return null;
        }
        applyCurrentViewportBounds(entrySnapshot);
        for (int index = entrySnapshot.size() - 1; index >= 0; index--) {
            HudEntry entry = entrySnapshot.get(index);
            if (!isInteractiveEntryAvailable(entry, screenCategory)) {
                continue;
            }
            ElementNode hitElement = entry.widget.findElementAt(mouseX, mouseY);
            if (shouldCaptureHit(entry, hitElement)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 在输入命中测试前同步 HUD widget 的真实原生视口尺寸。
     *
     * <p>HUD 注册时会先使用临时尺寸创建 widget；如果首次鼠标事件早于渲染阶段，
     * 拖拽辅助器会从过期布局边界初始化 fixed 坐标，造成首拖跳位。</p>
     *
     * @param entrySnapshot 当前输入帧的 HUD 条目快照
     */
    private void applyCurrentViewportBounds(List<HudEntry> entrySnapshot) {
        if (entrySnapshot == null || entrySnapshot.isEmpty()) {
            return;
        }
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
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        for (HudEntry entry : entrySnapshot) {
            if (entry != null) {
                entry.widget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
            }
        }
    }

    private HudEntry resolveKeyboardTargetEntry(UiHudScreenCategory screenCategory, List<HudEntry> entrySnapshot) {
        if (screenCategory != UiHudScreenCategory.CONTAINER) {
            return null;
        }
        if (isInteractiveEntryAvailable(activeKeyboardEntry, screenCategory)
                && activeKeyboardEntry.interactionSession.hasFocusedWidget()) {
            return activeKeyboardEntry;
        }
        activeKeyboardEntry = null;
        for (int index = entrySnapshot.size() - 1; index >= 0; index--) {
            HudEntry entry = entrySnapshot.get(index);
            if (!isInteractiveEntryAvailable(entry, screenCategory)) {
                continue;
            }
            if (entry.interactionSession.hasFocusedWidget()) {
                activeKeyboardEntry = entry;
                return entry;
            }
        }
        return null;
    }

    private boolean isInteractiveEntryAvailable(HudEntry entry, UiHudScreenCategory screenCategory) {
        return entry != null && entries.contains(entry) && entry.layerType == UiHudLayerType.INTERACTIVE
                && entry.isVisibleIn(screenCategory);
    }

    private boolean shouldCaptureHit(HudEntry entry, ElementNode hitElement) {
        if (hitElement == null) {
            return false;
        }
        if (entry.widget.isPassthroughHit(hitElement)) {
            return false;
        }
        if (entry.widget.isInteractiveHit(hitElement)) {
            return true;
        }
        return true;
    }

    /**
     * 在纯游戏 HUD 阶段绘制可见层。
     *
     * @param partialTicks 插值帧参数
     * @apiNote 仅供框架内部 forge {@code RenderGameOverlayEvent} 钩子调用，业务代码不应直接触发。
     */
    public void renderHud(float partialTicks) {
        renderVisibleLayers(UiHudScreenCategory.INGAME, partialTicks);
    }

    /**
     * 在普通 GuiScreen 上方绘制可见层。
     *
     * @param partialTicks 插值帧参数
     * @apiNote 仅供框架内部 forge {@code GuiScreenEvent.DrawScreenEvent.Post} 钩子调用，业务代码不应直接触发。
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
     * 供测试使用的可见层判断辅助入口。
     *
     * @param screen 当前屏幕实例
     * @param screenClassName 当前屏幕类名
     * @return 是否存在可见层
     */
    synchronized boolean hasVisibleLayerForTest(Object screen, String screenClassName) {
        UiHudScreenCategory screenCategory = classifyScreen(screen, screenClassName);
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

    /**
     * 判断当前页面是否属于 HUD 不显示黑名单。
     *
     * @param screen 当前屏幕实例
     * @param screenClassName 当前屏幕类名
     * @return 是否应归为菜单隐藏态
     */
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

    /**
     * 判断类名是否属于已知 Minecraft 游戏主页。
     *
     * @param screenClassName 当前屏幕类名
     * @return 是否为游戏主页类名
     */
    private static boolean isKnownMainMenuScreenClass(String screenClassName) {
        return "net.minecraft.client.gui.GuiMainMenu".equals(screenClassName)
                || "galaxyspace.core.gui.GSGuiMainMenu".equals(screenClassName)
                || "net.minecraft.client.gui.screen.TitleScreen".equals(screenClassName)
                || "net.minecraft.client.gui.screens.TitleScreen".equals(screenClassName);
    }

    private synchronized void renderVisibleLayers(UiHudScreenCategory screenCategory, float partialTicks) {
        renderPipeline.renderVisibleLayers(new ArrayList<HudEntry>(entries), screenCategory, partialTicks);
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
        if (entry == null) {
            return;
        }
        if (entries.remove(entry)) {
            if (hoveredMouseEntry == entry) {
                entry.widget.onMouseLeave();
                hoveredMouseEntry = null;
            }
            if (activeMouseEntry == entry) {
                activeMouseEntry = null;
            }
            if (activeKeyboardEntry == entry) {
                activeKeyboardEntry = null;
            }
            entry.interactionSession.clearInteractionState();
            UiLayoutInvalidationRegistry.unregisterRoot(entry.widget);
            updateHudKeyboardCaptureState();
        }
    }

    /**
     * 清空全部 HUD 注册并复位输入捕获状态。
     *
     * <p>用于客户端断开连接、退出到主菜单等生命周期切换：HUD 入口本身要求调用方手动 {@code unregister()}，
     * 但宿主切换世界时旧 HUD 的 widget 与会话已经失去意义，需要在显式钩子上一次性清理，
     * 避免世界切换后旧引用继续占用 {@link UiLayoutInvalidationRegistry} 与 HUD 键盘捕获状态。</p>
     */
    public synchronized void clearAllRegistrations() {
        if (entries.isEmpty()) {
            UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
            syncHudTextInputRequest(false);
            return;
        }
        List<HudEntry> snapshot = new ArrayList<HudEntry>(entries);
        for (HudEntry entry : snapshot) {
            try {
                if (hoveredMouseEntry == entry) {
                    entry.widget.onMouseLeave();
                }
                entry.interactionSession.clearInteractionState();
                UiLayoutInvalidationRegistry.unregisterRoot(entry.widget);
            } catch (RuntimeException exception) {
                // 清理路径不应抛出，最坏情况只是单个 widget 未释放，记录后继续推进。
            }
        }
        entries.clear();
        hoveredMouseEntry = null;
        activeMouseEntry = null;
        activeKeyboardEntry = null;
        UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
        syncHudTextInputRequest(false);
    }

    private synchronized void clearInteractiveStates() {
        updateHoveredMouseEntry(null);
        clearActiveInteractionEntries();
        for (HudEntry entry : entries) {
            if (entry.layerType == UiHudLayerType.INTERACTIVE) {
                entry.interactionSession.clearInteractionState();
            }
        }
        UiKeyboardCaptureState.getInstance().setHudKeyboardCaptured(false);
        syncHudTextInputRequest(false);
    }

    private synchronized void clearInteractiveStatesExcept(HudEntry preservedEntry) {
        if (hoveredMouseEntry != preservedEntry) {
            updateHoveredMouseEntry(null);
        }
        activeMouseEntry = null;
        if (activeKeyboardEntry != preservedEntry) {
            activeKeyboardEntry = null;
        }
        for (HudEntry entry : entries) {
            if (entry.layerType == UiHudLayerType.INTERACTIVE && entry != preservedEntry) {
                entry.interactionSession.clearInteractionState();
            }
        }
    }

    private synchronized void updateHudKeyboardCaptureState() {
        boolean captured = false;
        if (activeKeyboardEntry != null && entries.contains(activeKeyboardEntry)
                && activeKeyboardEntry.layerType == UiHudLayerType.INTERACTIVE
                && activeKeyboardEntry.interactionSession.hasFocusedWidget()) {
            captured = true;
        } else {
            activeKeyboardEntry = null;
            for (int index = entries.size() - 1; index >= 0; index--) {
                HudEntry entry = entries.get(index);
                if (entry.layerType == UiHudLayerType.INTERACTIVE && entry.interactionSession.hasFocusedWidget()) {
                    activeKeyboardEntry = entry;
                    captured = true;
                    break;
                }
            }
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
        if (hoveredMouseEntry != null && entries.contains(hoveredMouseEntry)) {
            hoveredMouseEntry.widget.onMouseLeave();
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

        final UiHudLayerType layerType;
        final HtmlLikeDocumentWidget widget;
        final DocumentHostInteractionSession interactionSession = new DocumentHostInteractionSession();
        final UiRuntimeAdapters runtimeAdapters;

        private HudEntry(UiHudLayerType layerType, HtmlLikeDocumentWidget widget, UiRuntimeAdapters runtimeAdapters) {
            this.layerType = layerType;
            this.widget = widget;
            this.runtimeAdapters = runtimeAdapters;
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

        String getRuntimeName() {
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
