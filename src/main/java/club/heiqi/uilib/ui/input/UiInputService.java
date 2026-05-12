package club.heiqi.uilib.ui.input;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;

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
    private final boolean[] previousMouseButtonStates = new boolean[8];
    private volatile boolean suppressNextCollectedKeyEvent;
    private volatile int suppressNextCollectedKeyCode;
    private volatile UiKeyEvent.Action suppressNextCollectedKeyAction;
    private volatile String suppressNextCollectedText;

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
        return new UiInputFrame(mouseX, mouseY, drainQueue(mouseEvents), drainQueue(keyEvents), drainQueue(textEvents));
    }

    /**
     * 清空当前输入缓冲。
     */
    public void clear() {
        mouseEvents.clear();
        keyEvents.clear();
        textEvents.clear();
        suppressNextCollectedKeyEvent = false;
        suppressNextCollectedKeyCode = 0;
        suppressNextCollectedKeyAction = null;
        suppressNextCollectedText = null;
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
        List<UiTextInputEvent> textEventList = new ArrayList<UiTextInputEvent>(1);
        String eventText = resolveImmediateKeyboardText();
        if (eventText != null) {
            textEventList.add(new UiTextInputEvent(eventText, keyEvent.getTimeNanos()));
        }
        return new UiInputFrame(mouseX, mouseY, java.util.Collections.<UiMouseEvent>emptyList(), keyEventList,
                textEventList);
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
        suppressNextCollectedKeyEvent = true;
        suppressNextCollectedKeyCode = keyCode;
        suppressNextCollectedKeyAction = action;
        suppressNextCollectedText = text;
        removeQueuedKeyEvent(keyCode, action);
        if (text != null) {
            removeQueuedTextEvent(text);
        }
    }

    @Override
    public void onKeyEvent(InputEvents.KeyEvent event) {
        int keyCode = readIntField(event, "lwjgl2KeyCode", 0);
        UiKeyEvent.Action action = mapAction(event.action);
        if (suppressNextCollectedKeyEvent && suppressNextCollectedKeyCode == keyCode
                && suppressNextCollectedKeyAction == action) {
            suppressNextCollectedKeyEvent = false;
            suppressNextCollectedKeyCode = 0;
            suppressNextCollectedKeyAction = null;
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
        if (suppressNextCollectedText != null && suppressNextCollectedText.equals(event.text)) {
            suppressNextCollectedText = null;
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

    private String resolveImmediateKeyboardText() {
        char eventCharacter = Keyboard.getEventCharacter();
        if (eventCharacter == Keyboard.CHAR_NONE || Character.isISOControl(eventCharacter)) {
            return null;
        }
        return String.valueOf(eventCharacter);
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

    private void removeQueuedKeyEvent(int keyCode, UiKeyEvent.Action action) {
        if (action == null || keyEvents.isEmpty()) {
            return;
        }
        List<UiKeyEvent> retainedEvents = new ArrayList<UiKeyEvent>();
        boolean removed = false;
        while (!keyEvents.isEmpty()) {
            UiKeyEvent event = keyEvents.poll();
            if (event == null) {
                continue;
            }
            if (!removed && event.getKeyCode() == keyCode && event.getAction() == action) {
                removed = true;
                continue;
            }
            retainedEvents.add(event);
        }
        keyEvents.addAll(retainedEvents);
    }

    private void removeQueuedTextEvent(String text) {
        if (text == null || text.isEmpty() || textEvents.isEmpty()) {
            return;
        }
        List<UiTextInputEvent> retainedEvents = new ArrayList<UiTextInputEvent>();
        boolean removed = false;
        while (!textEvents.isEmpty()) {
            UiTextInputEvent event = textEvents.poll();
            if (event == null) {
                continue;
            }
            if (!removed && text.equals(event.getText())) {
                removed = true;
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
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private short readShortField(Object instance, String fieldName, short fallback) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.getShort(instance);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private boolean readBooleanField(Object instance, String fieldName, boolean fallback) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.getBoolean(instance);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private boolean hasAnyFlag(short mask, short firstFlag, short secondFlag) {
        return (mask & firstFlag) != 0 || (mask & secondFlag) != 0;
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
        } catch (ReflectiveOperationException ignored) {
            // 当前编译期或运行期实现未提供该方法时直接忽略。
        }
    }
}
