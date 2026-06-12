package club.heiqi.uilib.ui.input;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/**
 * 基于 LWJGLX 状态轮询的基础输入后端。
 */
final class LwjglxPollingInputBackend implements UiInputBackend {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/LwjglxPollingInputBackend");
    private static final AtomicBoolean TEXT_INPUT_DEGRADED_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean TOTAL_SCROLL_REFLECTION_LOGGED = new AtomicBoolean(false);
    private static final int KEYBOARD_SIZE = 256;
    private static final int MOUSE_BUTTON_COUNT = 8;

    private final UiInputService inputService;
    private volatile boolean collectKeyboardState;
    private final boolean[] previousKeyStates = new boolean[KEYBOARD_SIZE];
    private final boolean[] previousMouseButtonStates = new boolean[MOUSE_BUTTON_COUNT];
    private final MouseTotalScrollReader totalScrollReader = MouseTotalScrollReader.create();

    private int previousMouseX;
    private int previousMouseY;
    private double previousTotalScrollAmount;

    LwjglxPollingInputBackend(UiInputService inputService) {
        this(inputService, true);
    }

    LwjglxPollingInputBackend(UiInputService inputService, boolean collectKeyboardState) {
        this.inputService = inputService;
        this.collectKeyboardState = collectKeyboardState;
    }

    /**
     * 启用键盘状态差分收集，供增强输入后端注册失败时兜底。
     */
    void enableKeyboardStateCollection() {
        collectKeyboardState = true;
    }

    @Override
    public void initialize() {
        snapshotKeyboardState();
        previousTotalScrollAmount = totalScrollReader.readTotalScrollAmount();
    }

    @Override
    public void tick() {
        if (Mouse.isCreated()) {
            Mouse.poll();
            updateMouseState();
        }
        if (collectKeyboardState && Keyboard.isCreated()) {
            Keyboard.poll();
            updateKeyboardState();
        }
    }

    @Override
    public void beginTextInput() {
        logTextInputDegradedOnce();
    }

    @Override
    public void endTextInput() {
        logTextInputDegradedOnce();
    }

    @Override
    public UiInputFrame createImmediateKeyboardFrame() {
        if (!Keyboard.isCreated() || !Keyboard.getEventKeyState()) {
            return null;
        }
        UiKeyEvent keyEvent = createCurrentKeyboardEvent();
        if (keyEvent == null) {
            return null;
        }
        List<UiKeyEvent> keyEventList = new ArrayList<UiKeyEvent>(1);
        keyEventList.add(keyEvent);
        return new UiInputFrame(inputService.getMouseX(), inputService.getMouseY(),
                Collections.<UiMouseEvent>emptyList(), keyEventList, Collections.<UiTextInputEvent>emptyList());
    }

    @Override
    public UiInputFrame createImmediateMouseFrame() {
        if (!Mouse.isCreated()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            return null;
        }
        int mouseX = clamp(Mouse.getEventX(), 0, minecraft.displayWidth);
        int mouseY = clamp(minecraft.displayHeight - Mouse.getEventY() - 1, 0, minecraft.displayHeight);
        int button = Mouse.getEventButton();
        long now = Sys.getNanoTime();
        List<UiMouseEvent> mouseEventList = new ArrayList<UiMouseEvent>(1);
        if (Mouse.getEventButtonState()) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, mouseX, mouseY, button, 0, 0, 0,
                    now));
        } else if (button != -1) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, mouseX, mouseY, button, 0, 0, 0,
                    now));
        } else {
            int wheelDelta = Mouse.getEventDWheel();
            if (wheelDelta != 0) {
                mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.SCROLL, mouseX, mouseY, -1, wheelDelta, 0, 0,
                        now));
            } else {
                mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.MOVE, mouseX, mouseY, -1, 0,
                        mouseX - inputService.getMouseX(), mouseY - inputService.getMouseY(), now));
            }
        }
        return new UiInputFrame(mouseX, mouseY, mouseEventList, Collections.<UiKeyEvent>emptyList(),
                Collections.<UiTextInputEvent>emptyList());
    }

    private void updateKeyboardState() {
        long now = Sys.getNanoTime();
        for (int keyCode = 0; keyCode < previousKeyStates.length; keyCode++) {
            boolean isDown = Keyboard.isKeyDown(keyCode);
            if (isDown == previousKeyStates[keyCode]) {
                continue;
            }
            UiKeyEvent.Action action = isDown ? UiKeyEvent.Action.PRESSED : UiKeyEvent.Action.RELEASED;
            previousKeyStates[keyCode] = isDown;
            if (inputService.isSuppressedCollectedKeyEvent(keyCode, action)) {
                continue;
            }
            inputService.addKeyEvent(new UiKeyEvent(keyCode, 0, 0, action,
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
                    Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU), false, now));
        }
    }

    private void updateMouseState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        int currentMouseX = clamp(Mouse.getX(), 0, displayWidth);
        int currentMouseY = clamp(displayHeight - Mouse.getY() - 1, 0, displayHeight);
        int deltaX = currentMouseX - previousMouseX;
        int deltaY = currentMouseY - previousMouseY;
        long now = Sys.getNanoTime();

        if (deltaX != 0 || deltaY != 0) {
            inputService.addMouseEvent(new UiMouseEvent(UiMouseEvent.Action.MOVE, currentMouseX, currentMouseY, -1, 0,
                    deltaX, deltaY, now));
        }

        int wheelDelta = readWheelDelta();
        if (wheelDelta != 0) {
            inputService.addMouseEvent(new UiMouseEvent(UiMouseEvent.Action.SCROLL, currentMouseX, currentMouseY, -1,
                    wheelDelta, 0, 0, now));
        }

        for (int button = 0; button < previousMouseButtonStates.length; button++) {
            boolean isDown = Mouse.isButtonDown(button);
            if (isDown == previousMouseButtonStates[button]) {
                continue;
            }

            UiMouseEvent.Action action = isDown ? UiMouseEvent.Action.BUTTON_DOWN : UiMouseEvent.Action.BUTTON_UP;
            inputService.addMouseEvent(new UiMouseEvent(action, currentMouseX, currentMouseY, button, 0, 0, 0, now));
            previousMouseButtonStates[button] = isDown;
        }

        previousMouseX = currentMouseX;
        previousMouseY = currentMouseY;
        inputService.updateMousePosition(currentMouseX, currentMouseY);
    }

    private int readWheelDelta() {
        if (!totalScrollReader.isSupported()) {
            return Mouse.getDWheel();
        }
        double currentTotalScrollAmount = totalScrollReader.readTotalScrollAmount();
        double wheelDeltaRaw = currentTotalScrollAmount - previousTotalScrollAmount;
        previousTotalScrollAmount = currentTotalScrollAmount;
        if (Math.abs(wheelDeltaRaw) <= 0.0001D) {
            return 0;
        }
        int wheelDelta = (int) Math.round(wheelDeltaRaw * 120.0D);
        if (wheelDelta == 0) {
            return wheelDeltaRaw > 0.0D ? 1 : -1;
        }
        return wheelDelta;
    }

    private void snapshotKeyboardState() {
        if (!Keyboard.isCreated()) {
            return;
        }
        for (int keyCode = 0; keyCode < previousKeyStates.length; keyCode++) {
            previousKeyStates[keyCode] = Keyboard.isKeyDown(keyCode);
        }
    }

    private UiKeyEvent createCurrentKeyboardEvent() {
        long now = Sys.getNanoTime();
        return new UiKeyEvent(Keyboard.getEventKey(), 0, 0,
                Keyboard.isRepeatEvent() ? UiKeyEvent.Action.REPEATED : UiKeyEvent.Action.PRESSED,
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL),
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
                Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU), false, now);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void logTextInputDegradedOnce() {
        if (TEXT_INPUT_DEGRADED_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 当前使用 LWJGLX 轮询输入后端，文本输入与 IME 事件需要 lwjgl3ify InputEvents 支持");
        }
    }

    private static final class MouseTotalScrollReader {

        private final Field field;

        private MouseTotalScrollReader(Field field) {
            this.field = field;
        }

        private static MouseTotalScrollReader create() {
            try {
                return new MouseTotalScrollReader(Mouse.class.getField("totalScrollAmount"));
            } catch (NoSuchFieldException exception) {
                return new MouseTotalScrollReader(null);
            } catch (SecurityException exception) {
                logTotalScrollReflectionFailureOnce(exception);
                return new MouseTotalScrollReader(null);
            }
        }

        private boolean isSupported() {
            return field != null;
        }

        private double readTotalScrollAmount() {
            if (field == null) {
                return 0.0D;
            }
            try {
                return field.getDouble(null);
            } catch (IllegalAccessException exception) {
                logTotalScrollReflectionFailureOnce(exception);
                return 0.0D;
            } catch (IllegalArgumentException exception) {
                logTotalScrollReflectionFailureOnce(exception);
                return 0.0D;
            }
        }
    }

    private static void logTotalScrollReflectionFailureOnce(RuntimeException exception) {
        if (TOTAL_SCROLL_REFLECTION_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 鼠标滚轮 totalScrollAmount 反射读取失败，已降级为事件滚轮：", exception);
        }
    }

    private static void logTotalScrollReflectionFailureOnce(ReflectiveOperationException exception) {
        if (TOTAL_SCROLL_REFLECTION_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 鼠标滚轮 totalScrollAmount 反射读取失败，已降级为事件滚轮：", exception);
        }
    }
}
