package club.heiqi.uilib.ui.input;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.Sys;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import me.eigenraven.lwjgl3ify.api.InputEvents;

/**
 * UI 原始输入服务。
 *
 * <p>键盘与文本输入直接订阅 lwjgl3ify 的 `InputEvents`，鼠标输入则从 `org.lwjglx.input.Mouse`
 * 事件队列中收集。这样后续 UI 层可以直接建立在 GLFW 化后的输入模型上，而不需要依赖
 * `GuiScreen.handleMouseInput` / `handleKeyboardInput` 这套旧入口。</p>
 */
public class UiInputService implements InputEvents.KeyboardListener {

    private static final UiInputService INSTANCE = new UiInputService();
    private static final Logger LOG = LogManager.getLogger("QzUiLib/UiInputService");
    private static final AtomicBoolean INPUT_FIELD_REFLECTION_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_EVENTS_REFLECTION_LOGGED = new AtomicBoolean(false);
    private static final short SDL_KMOD_LSHIFT = 0x0001;
    private static final short SDL_KMOD_RSHIFT = 0x0002;
    private static final short SDL_KMOD_LCTRL = 0x0040;
    private static final short SDL_KMOD_RCTRL = 0x0080;
    private static final short SDL_KMOD_LALT = 0x0100;
    private static final short SDL_KMOD_RALT = 0x0200;
    private static final short SDL_KMOD_LGUI = 0x0400;
    private static final short SDL_KMOD_RGUI = 0x0800;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Queue<UiMouseEvent> mouseEvents = new ConcurrentLinkedQueue<UiMouseEvent>();
    private final Queue<UiKeyEvent> keyEvents = new ConcurrentLinkedQueue<UiKeyEvent>();
    private final Queue<UiTextInputEvent> textEvents = new ConcurrentLinkedQueue<UiTextInputEvent>();
    private final Queue<SuppressedCollectedKeyWindow> suppressedCollectedKeys =
            new ConcurrentLinkedQueue<SuppressedCollectedKeyWindow>();
    private final Queue<String> suppressedCollectedTexts = new ConcurrentLinkedQueue<String>();
    private final boolean[] previousMouseButtonStates = new boolean[8];

    private volatile int mouseX;
    private volatile int mouseY;
    private volatile int previousMouseX;
    private volatile int previousMouseY;
    private volatile double previousTotalScrollAmount;

    private UiInputService() {}

    /**
     * 获取 UI 输入服务单例。
     *
     * @return 输入服务
     */
    public static UiInputService getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化输入服务并注册键盘监听。
     */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        InputEvents.addKeyboardListener(this);
    }

    /**
     * 刷新本帧输入状态并收集鼠标队列中的事件。
     */
    public void tick() {
        if (!initialized.get()) {
            return;
        }

        Mouse.poll();
        updateMouseState();
    }

    /**
     * 拉取并清空当前累计的输入事件。
     *
     * @return 输入快照
     */
    public UiInputFrame collectFrame() {
        UiInputFrame frame = new UiInputFrame(mouseX, mouseY, drainQueue(mouseEvents), drainQueue(keyEvents),
                drainQueue(textEvents));
        clearCollectedSuppressionWindow();
        return frame;
    }

    /**
     * 清空当前输入缓冲。
     */
    public void clear() {
        mouseEvents.clear();
        keyEvents.clear();
        textEvents.clear();
        clearCollectedSuppressionWindow();
    }

    /**
     * 开启底层文本输入模式。
     */
    public void beginTextInput() {
        invokeInputEventsMethod("beginTextInput");
    }

    /**
     * 结束底层文本输入模式。
     */
    public void endTextInput() {
        invokeInputEventsMethod("endTextInput");
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    /**
     * 基于当前原生键盘事件构造一份即时输入快照，供宿主在 `GuiScreen.handleKeyboardInput()` 内抢先分发。
     *
     * <p>即时快照只携带按键语义，不提前附带文本输入，避免 HUD 的即时路由与常规收集链重复写入同一字符。</p>
     *
     * @return 即时输入快照；当前事件无效时返回 null
     */
    public UiInputFrame createImmediateKeyboardFrame() {
        if (!Keyboard.getEventKeyState()) {
            return null;
        }
        UiKeyEvent keyEvent = createCurrentKeyboardEvent();
        if (keyEvent == null) {
            return null;
        }
        List<UiKeyEvent> keyEventList = new ArrayList<UiKeyEvent>(1);
        keyEventList.add(keyEvent);
        return new UiInputFrame(mouseX, mouseY, java.util.Collections.<UiMouseEvent>emptyList(), keyEventList,
                java.util.Collections.<UiTextInputEvent>emptyList());
    }

    /**
     * 基于当前原生鼠标事件构造一份即时输入快照，供宿主在 `GuiScreen.handleMouseInput()` 内抢先分发。
     *
     * @return 即时输入快照；当前事件无效时返回 null
     */
    public UiInputFrame createImmediateMouseFrame() {
        if (!Mouse.isCreated()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            return null;
        }
        int mouseX = Mouse.getEventX();
        int mouseY = minecraft.displayHeight - Mouse.getEventY() - 1;
        mouseY = Math.max(0, Math.min(minecraft.displayHeight, mouseY));
        mouseX = Math.max(0, Math.min(minecraft.displayWidth, mouseX));
        int button = Mouse.getEventButton();
        long now = Sys.getNanoTime();
        java.util.List<UiMouseEvent> mouseEventList = new java.util.ArrayList<UiMouseEvent>(1);
        if (Mouse.getEventButtonState()) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, mouseX, mouseY, button, 0, 0, 0, now));
        } else if (button != -1) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, mouseX, mouseY, button, 0, 0, 0, now));
        } else {
            int wheelDelta = Mouse.getEventDWheel();
            if (wheelDelta != 0) {
                mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.SCROLL, mouseX, mouseY, -1, wheelDelta, 0, 0, now));
            } else {
                mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.MOVE, mouseX, mouseY, -1, 0,
                        mouseX - this.mouseX, mouseY - this.mouseY, now));
            }
        }
        return new UiInputFrame(mouseX, mouseY, mouseEventList, java.util.Collections.<UiKeyEvent>emptyList(),
                java.util.Collections.<UiTextInputEvent>emptyList());
    }

    /**
     * 标记下一次从全局输入监听收集到的键盘事件已由宿主即时分发，避免 HUD 同帧收到重复键盘输入。
     *
     * @param text 当前原生事件对应的文本；无文本时传 null
     */
    public void suppressNextCollectedKeyboardEvent(int keyCode, UiKeyEvent.Action action, String text) {
        if (isCollectedKeyDownAction(action) && keyCode != 0) {
            suppressedCollectedKeys.add(new SuppressedCollectedKeyWindow(keyCode));
        }
        removeQueuedKeyEvents(keyCode, action);
        if (text != null) {
            suppressedCollectedTexts.add(text);
            removeQueuedTextEvents(text);
        }
    }

    @Override
    public void onKeyEvent(InputEvents.KeyEvent event) {
        int keyCode = readIntField(event, "lwjgl2KeyCode", 0);
        UiKeyEvent.Action action = mapAction(event.action);
        if (isSuppressedCollectedKeyEvent(keyCode, action)) {
            return;
        }
        int glfwKeyCode = readIntField(event, "glfwKeyCode", readIntField(event, "sdlKeyCode", 0));
        int glfwScanCode = readIntField(event, "glfwScanCode", readIntField(event, "sdlScanCode", 0));
        short modifierMask = readShortField(event, "sdlKeyModifiers", (short) 0);

        keyEvents.add(new UiKeyEvent(
                keyCode,
                glfwKeyCode,
                glfwScanCode,
                mapAction(event.action),
                readBooleanField(event, "controlPressed", hasAnyFlag(modifierMask, SDL_KMOD_LCTRL, SDL_KMOD_RCTRL)),
                readBooleanField(event, "shiftPressed", hasAnyFlag(modifierMask, SDL_KMOD_LSHIFT, SDL_KMOD_RSHIFT)),
                readBooleanField(event, "altPressed", hasAnyFlag(modifierMask, SDL_KMOD_LALT, SDL_KMOD_RALT)),
                readBooleanField(event, "superPressed", hasAnyFlag(modifierMask, SDL_KMOD_LGUI, SDL_KMOD_RGUI)),
                Sys.getNanoTime()));
    }

    @Override
    public void onTextEvent(InputEvents.TextEvent event) {
        if (event.text == null || event.text.isEmpty()) {
            return;
        }
        if (isSuppressedCollectedText(event.text)) {
            return;
        }
        textEvents.add(new UiTextInputEvent(event.text, Sys.getNanoTime()));
    }

    private UiKeyEvent createCurrentKeyboardEvent() {
        if (!Keyboard.isCreated()) {
            return null;
        }
        long now = Sys.getNanoTime();
        return new UiKeyEvent(
                Keyboard.getEventKey(),
                0,
                0,
                Keyboard.isRepeatEvent() ? UiKeyEvent.Action.REPEATED : UiKeyEvent.Action.PRESSED,
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
                Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU),
                false,
                now);
    }

    private UiKeyEvent.Action mapAction(InputEvents.KeyAction action) {
        if (action == InputEvents.KeyAction.RELEASED) {
            return UiKeyEvent.Action.RELEASED;
        }
        if (action == InputEvents.KeyAction.REPEATED) {
            return UiKeyEvent.Action.REPEATED;
        }
        return UiKeyEvent.Action.PRESSED;
    }

    private <T> List<T> drainQueue(Queue<T> queue) {
        List<T> result = new ArrayList<T>();
        while (!queue.isEmpty()) {
            T value = queue.poll();
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private void removeQueuedKeyEvents(int keyCode, UiKeyEvent.Action action) {
        if (keyCode == 0 || !isCollectedKeyDownAction(action) || keyEvents.isEmpty()) {
            return;
        }
        List<UiKeyEvent> retainedEvents = new ArrayList<UiKeyEvent>();
        while (!keyEvents.isEmpty()) {
            UiKeyEvent event = keyEvents.poll();
            if (event == null) {
                continue;
            }
            if (event.getKeyCode() == keyCode && isCollectedKeyDownAction(event.getAction())) {
                continue;
            }
            retainedEvents.add(event);
        }
        keyEvents.addAll(retainedEvents);
    }

    private void removeQueuedTextEvents(String text) {
        if (text == null || text.isEmpty() || textEvents.isEmpty()) {
            return;
        }
        List<UiTextInputEvent> retainedEvents = new ArrayList<UiTextInputEvent>();
        while (!textEvents.isEmpty()) {
            UiTextInputEvent event = textEvents.poll();
            if (event == null) {
                continue;
            }
            if (text.equals(event.getText())) {
                continue;
            }
            retainedEvents.add(event);
        }
        textEvents.addAll(retainedEvents);
    }

    private int readIntField(Object instance, String fieldName, int fallback) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.getInt(instance);
        } catch (ReflectiveOperationException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private short readShortField(Object instance, String fieldName, short fallback) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.getShort(instance);
        } catch (ReflectiveOperationException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private boolean readBooleanField(Object instance, String fieldName, boolean fallback) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.getBoolean(instance);
        } catch (ReflectiveOperationException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private static void logInputFieldReflectionFailureOnce(String fieldName, ReflectiveOperationException exception) {
        if (INPUT_FIELD_REFLECTION_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 输入字段反射读取失败，已降级为 fallback：fieldName={}", fieldName, exception);
        }
    }

    private boolean hasAnyFlag(short mask, short firstFlag, short secondFlag) {
        return (mask & firstFlag) != 0 || (mask & secondFlag) != 0;
    }

    private boolean isSuppressedCollectedKeyEvent(int keyCode, UiKeyEvent.Action action) {
        if (keyCode == 0 || !isCollectedKeyDownAction(action)) {
            return false;
        }
        for (SuppressedCollectedKeyWindow suppressedKey : suppressedCollectedKeys) {
            if (suppressedKey != null && suppressedKey.matches(keyCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuppressedCollectedText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String suppressedText : suppressedCollectedTexts) {
            if (text.equals(suppressedText)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCollectedKeyDownAction(UiKeyEvent.Action action) {
        return action == UiKeyEvent.Action.PRESSED || action == UiKeyEvent.Action.REPEATED;
    }

    private void clearCollectedSuppressionWindow() {
        suppressedCollectedKeys.clear();
        suppressedCollectedTexts.clear();
    }

    private void updateMouseState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        int currentMouseX = Mouse.getX();
        int currentMouseY = displayHeight - Mouse.getY() - 1;
        currentMouseY = Math.max(0, Math.min(displayHeight, currentMouseY));
        currentMouseX = Math.max(0, Math.min(displayWidth, currentMouseX));

        int deltaX = currentMouseX - previousMouseX;
        int deltaY = currentMouseY - previousMouseY;
        long now = Sys.getNanoTime();

        if (deltaX != 0 || deltaY != 0) {
            mouseEvents.add(new UiMouseEvent(UiMouseEvent.Action.MOVE, currentMouseX, currentMouseY, -1, 0, deltaX,
                    deltaY, now));
        }

        double currentTotalScrollAmount = Mouse.totalScrollAmount;
        double wheelDeltaRaw = currentTotalScrollAmount - previousTotalScrollAmount;
        if (Math.abs(wheelDeltaRaw) > 0.0001D) {
            int wheelDelta = (int) Math.round(wheelDeltaRaw * 120.0D);
            if (wheelDelta == 0) {
                wheelDelta = wheelDeltaRaw > 0.0D ? 1 : -1;
            }
            mouseEvents.add(new UiMouseEvent(UiMouseEvent.Action.SCROLL, currentMouseX, currentMouseY, -1, wheelDelta,
                    0, 0, now));
        }

        for (int button = 0; button < previousMouseButtonStates.length; button++) {
            boolean isDown = Mouse.isButtonDown(button);
            if (isDown == previousMouseButtonStates[button]) {
                continue;
            }

            UiMouseEvent.Action action = isDown ? UiMouseEvent.Action.BUTTON_DOWN : UiMouseEvent.Action.BUTTON_UP;
            mouseEvents.add(new UiMouseEvent(action, currentMouseX, currentMouseY, button, 0, 0, 0, now));
            previousMouseButtonStates[button] = isDown;
        }

        previousMouseX = currentMouseX;
        previousMouseY = currentMouseY;
        previousTotalScrollAmount = currentTotalScrollAmount;
        mouseX = currentMouseX;
        mouseY = currentMouseY;
    }

    private void invokeInputEventsMethod(String methodName) {
        try {
            Method method = InputEvents.class.getMethod(methodName);
            method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            if (INPUT_EVENTS_REFLECTION_LOGGED.compareAndSet(false, true)) {
                LOG.debug("UILib 输入事件方法反射调用失败，当前实现未提供该方法：methodName={}", methodName, exception);
            }
        }
    }

    private static final class SuppressedCollectedKeyWindow {

        private final int keyCode;

        private SuppressedCollectedKeyWindow(int keyCode) {
            this.keyCode = keyCode;
        }

        private boolean matches(int keyCode) {
            return this.keyCode == keyCode;
        }
    }
}
