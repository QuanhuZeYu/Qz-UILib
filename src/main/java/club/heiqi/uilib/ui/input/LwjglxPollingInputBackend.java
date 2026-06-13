package club.heiqi.uilib.ui.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * 基于当前可用 LWJGL 状态轮询的基础输入后端。
 */
final class LwjglxPollingInputBackend implements UiInputBackend {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/LwjglxPollingInputBackend");
    private static final AtomicBoolean TEXT_INPUT_DEGRADED_LOGGED = new AtomicBoolean(false);
    private static final int KEYBOARD_SIZE = 256;
    private static final int MOUSE_BUTTON_COUNT = 8;

    private final UiInputService inputService;
    private volatile boolean collectKeyboardState;
    private final boolean[] previousKeyStates = new boolean[KEYBOARD_SIZE];
    private final boolean[] previousMouseButtonStates = new boolean[MOUSE_BUTTON_COUNT];
    private final LwjglInputRuntime.KeyboardRuntime keyboardRuntime = LwjglInputRuntime.keyboard();
    private final LwjglInputRuntime.MouseRuntime mouseRuntime = LwjglInputRuntime.mouse();

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
        previousTotalScrollAmount = mouseRuntime.readTotalScrollAmount();
    }

    @Override
    public void tick() {
        if (mouseRuntime.isCreated()) {
            mouseRuntime.poll();
            updateMouseState();
        }
        if (collectKeyboardState && keyboardRuntime.isCreated()) {
            keyboardRuntime.poll();
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
        if (!keyboardRuntime.isCreated() || !keyboardRuntime.getEventKeyState()) {
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
        if (!mouseRuntime.isCreated()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            return null;
        }
        int mouseX = clamp(mouseRuntime.getEventX(), 0, minecraft.displayWidth);
        int mouseY = clamp(minecraft.displayHeight - mouseRuntime.getEventY() - 1, 0, minecraft.displayHeight);
        int button = mouseRuntime.getEventButton();
        long now = LwjglInputRuntime.getNanoTime();
        List<UiMouseEvent> mouseEventList = new ArrayList<UiMouseEvent>(1);
        if (mouseRuntime.getEventButtonState()) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, mouseX, mouseY, button, 0, 0, 0,
                    now));
        } else if (button != -1) {
            mouseEventList.add(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, mouseX, mouseY, button, 0, 0, 0,
                    now));
        } else {
            int wheelDelta = mouseRuntime.getEventDWheel();
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
        long now = LwjglInputRuntime.getNanoTime();
        for (int keyCode = 0; keyCode < previousKeyStates.length; keyCode++) {
            boolean isDown = keyboardRuntime.isKeyDown(keyCode);
            if (isDown == previousKeyStates[keyCode]) {
                continue;
            }
            UiKeyEvent.Action action = isDown ? UiKeyEvent.Action.PRESSED : UiKeyEvent.Action.RELEASED;
            previousKeyStates[keyCode] = isDown;
            if (inputService.isSuppressedCollectedKeyEvent(keyCode, action)) {
                continue;
            }
            inputService.addKeyEvent(new UiKeyEvent(keyCode, 0, 0, action,
                    isControlPressed(), isShiftPressed(), isAltPressed(), false, now));
        }
    }

    private void updateMouseState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        int currentMouseX = clamp(mouseRuntime.getX(), 0, displayWidth);
        int currentMouseY = clamp(displayHeight - mouseRuntime.getY() - 1, 0, displayHeight);
        int deltaX = currentMouseX - previousMouseX;
        int deltaY = currentMouseY - previousMouseY;
        long now = LwjglInputRuntime.getNanoTime();

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
            boolean isDown = mouseRuntime.isButtonDown(button);
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
        if (!mouseRuntime.hasTotalScrollAmount()) {
            return mouseRuntime.getDWheel();
        }
        double currentTotalScrollAmount = mouseRuntime.readTotalScrollAmount();
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
        if (!keyboardRuntime.isCreated()) {
            return;
        }
        for (int keyCode = 0; keyCode < previousKeyStates.length; keyCode++) {
            previousKeyStates[keyCode] = keyboardRuntime.isKeyDown(keyCode);
        }
    }

    private UiKeyEvent createCurrentKeyboardEvent() {
        long now = LwjglInputRuntime.getNanoTime();
        return new UiKeyEvent(keyboardRuntime.getEventKey(), 0, 0,
                keyboardRuntime.isRepeatEvent() ? UiKeyEvent.Action.REPEATED : UiKeyEvent.Action.PRESSED,
                isControlPressed(), isShiftPressed(), isAltPressed(), false, now);
    }

    private boolean isControlPressed() {
        return keyboardRuntime.isKeyDown(UiKeyCodes.KEY_LCONTROL)
                || keyboardRuntime.isKeyDown(UiKeyCodes.KEY_RCONTROL);
    }

    private boolean isShiftPressed() {
        return keyboardRuntime.isKeyDown(UiKeyCodes.KEY_LSHIFT) || keyboardRuntime.isKeyDown(UiKeyCodes.KEY_RSHIFT);
    }

    private boolean isAltPressed() {
        return keyboardRuntime.isKeyDown(UiKeyCodes.KEY_LMENU) || keyboardRuntime.isKeyDown(UiKeyCodes.KEY_RMENU);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void logTextInputDegradedOnce() {
        if (TEXT_INPUT_DEGRADED_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 当前使用 LWJGL 轮询输入后端，文本输入与 IME 事件需要 lwjgl3ify InputEvents 支持");
        }
    }

}
