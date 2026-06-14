package club.heiqi.uilib.ui.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * UI 原始输入服务。
 *
 * <p>该 facade 保持输入层对外入口稳定，具体原生输入来源由内部后端适配。优先使用可用的
 * lwjgl3ify 输入事件，缺失时降级到 LWJGLX 基础轮询。</p>
 */
public class UiInputService {

    private static final UiInputService INSTANCE = new UiInputService();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Queue<UiMouseEvent> mouseEvents = new ConcurrentLinkedQueue<UiMouseEvent>();
    private final Queue<UiKeyEvent> keyEvents = new ConcurrentLinkedQueue<UiKeyEvent>();
    private final Queue<UiTextInputEvent> textEvents = new ConcurrentLinkedQueue<UiTextInputEvent>();
    private final Queue<SuppressedCollectedKeyWindow> suppressedCollectedKeys =
            new ConcurrentLinkedQueue<SuppressedCollectedKeyWindow>();
    private final Queue<String> suppressedCollectedTexts = new ConcurrentLinkedQueue<String>();
    private final UiInputBackend backend;

    private volatile int mouseX;
    private volatile int mouseY;

    private UiInputService() {
        UiInputBackend inputBackend = Lwjgl3ifyInputBackend.create(this);
        backend = inputBackend == null ? new LwjglxPollingInputBackend(this) : inputBackend;
    }

    /**
     * 创建使用指定输入后端的服务实例，供包内测试替身使用。
     *
     * @param backend 输入后端
     */
    UiInputService(UiInputBackend backend) {
        this.backend = backend;
    }

    /**
     * 获取 UI 输入服务单例。
     *
     * @return 输入服务
     */
    public static UiInputService getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化输入服务并注册底层后端。
     */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        backend.initialize();
    }

    /**
     * 刷新本帧输入状态并收集底层队列中的事件。
     */
    public void tick() {
        if (!initialized.get()) {
            return;
        }
        backend.tick();
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
        backend.beginTextInput();
    }

    /**
     * 结束底层文本输入模式。
     */
    public void endTextInput() {
        backend.endTextInput();
    }

    /**
     * 提交宿主界面已翻译出的字符，供无增强输入事件的后端合成文本输入。
     *
     * @param typedChar 已翻译字符
     * @param keyCode 原始键码；当前实现暂未使用，保留用于后续区分小键盘、宿主键语义或 IME 等扩展场景
     */
    public void submitHostTypedCharacter(char typedChar, int keyCode) {
        if (!initialized.get()) {
            return;
        }
        backend.handleHostTypedCharacter(typedChar, keyCode);
    }

    /**
     * 设置宿主键盘重复事件开关。
     *
     * @param enabled true 表示开启重复事件
     */
    public void setHostKeyboardRepeatEnabled(boolean enabled) {
        if (!initialized.get()) {
            return;
        }
        backend.setHostKeyboardRepeatEnabled(enabled);
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
        return backend.createImmediateKeyboardFrame();
    }

    /**
     * 基于当前原生鼠标事件构造一份即时输入快照，供宿主在 `GuiScreen.handleMouseInput()` 内抢先分发。
     *
     * @return 即时输入快照；当前事件无效时返回 null
     */
    public UiInputFrame createImmediateMouseFrame() {
        return backend.createImmediateMouseFrame();
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

    void addMouseEvent(UiMouseEvent event) {
        if (event != null) {
            mouseEvents.add(event);
        }
    }

    void addKeyEvent(UiKeyEvent event) {
        if (event != null) {
            keyEvents.add(event);
        }
    }

    void addTextEvent(UiTextInputEvent event) {
        if (event != null) {
            textEvents.add(event);
        }
    }

    void updateMousePosition(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
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

    boolean isSuppressedCollectedKeyEvent(int keyCode, UiKeyEvent.Action action) {
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

    boolean isSuppressedCollectedText(String text) {
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
