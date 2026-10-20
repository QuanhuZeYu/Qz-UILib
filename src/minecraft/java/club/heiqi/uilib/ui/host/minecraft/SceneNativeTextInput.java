package club.heiqi.uilib.ui.host.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;

/** GTNH lwjgl3ify 的可选事实桥；按宿主唯一 SDL 消费序复制，Core 持有焦点及编辑目标权威。 */
public final class SceneNativeTextInput implements AutoCloseable {
    private static final AtomicReference<SceneNativeTextInput> OWNER = new AtomicReference<>();
    private final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    private final Bindings bindings;
    private final Object queueLock = new Object();
    private final ConcurrentLinkedQueue<NativeEvent> pending = new ConcurrentLinkedQueue<>();
    private SceneCompositionSource composition;
    private volatile boolean closed;
    private boolean receivingText, windowAvailable = true, textRequested, textStarted;

    /** 只查可选资源；普通 LWJGL2 的类加载不初始化 SDL。 */
    public static boolean supported() {
        SceneNativeTextInput owner = OWNER.get();
        if (owner != null) owner.checkFailure();
        ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
        return SceneNativeInputSupport.resourcesPresent(loader)
                && SceneNativeInputSupport.consumerFailure(loader) == null;
    }

    public static boolean available() {
        ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
        if (!SceneNativeInputSupport.resourcesPresent(loader)) return false;
        String failure = SceneNativeInputSupport.consumerFailure(loader);
        if (failure != null) throw new IllegalStateException("UILib 增强输入消费绑定不兼容: " + failure);
        try {
            Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, loader).getMethod("uilib$nativeInputCaptureReady");
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException("UILib 增强输入缺少RFB消费接线，org.lwjglx原排除保持不变", missing);
        }
        SceneNativeTextInput owner = OWNER.get();
        if (owner != null) owner.checkFailure();
        return true;
    }

    public SceneNativeTextInput() {
        if (!available()) throw new IllegalStateException("Native text input resources are unavailable");
        bindings = new Bindings();
        if (!OWNER.compareAndSet(null, this))
            throw new IllegalStateException("Another native text input bridge is registered");
    }

    /** 可选RFB接线在每项SDL消费前调用；无活动页面时不读取原生事件。 */
    public static void captureCurrentEvent() {
        SceneNativeTextInput owner = OWNER.get();
        if (owner == null) return;
        try { owner.observe(owner.bindings.currentAddress()); }
        catch (RuntimeException | Error failure) { owner.callbackFailure.compareAndSet(null, failure); }
    }

    private void checkFailure() {
        Throwable failure = callbackFailure.get();
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    public boolean active() { checkFailure(); return !closed && OWNER.get() == this; }

    /** 重复调用不增加 lwjgl3ify 的 textInputDepth。 */
    public synchronized void textInput(boolean enabled) {
        checkFailure();
        if (closed) return;
        textRequested = enabled;
        updateTextInput();
    }

    public synchronized void availability(boolean available) {
        checkFailure();
        if (closed) return;
        windowAvailable = available;
        updateTextInput();
    }

    private void updateTextInput() {
        boolean enabled = textRequested && windowAvailable && !closed;
        if (enabled == textStarted) return;
        synchronized (queueLock) {
            receivingText = false;
            if (!enabled) {
                pending.removeIf(event -> event.edit != null);
                if (composition != null) pending.add(NativeEvent.edit(SceneEditEvent.compositionCancel(composition)));
            }
        }
        // SDL_StopTextInput取消组字但不承诺最终提交，也不清空已排队文本。
        // 在原SDL线程用原消费者耗尽旧事实后退休来源；整个边界内文本不进入新capture。
        invoke(bindings.mainThread, null, (Runnable) () -> {
            if (!enabled) invoke(bindings.end, null);
            invoke(bindings.pump, null);
            if (enabled) invoke(bindings.begin, null);
            synchronized (queueLock) {
                composition = null;
                receivingText = enabled;
            }
        });
        textStarted = enabled;
    }

    /** 顺序来自 Lwjgl3ifyEventLoop 的同一次逐项消费，不按 Java 递交时刻排序。 */
    public List<NativeEvent> drain() {
        checkFailure();
        synchronized (queueLock) {
            if (closed) return Collections.emptyList();
            List<NativeEvent> result = new ArrayList<>();
            NativeEvent event;
            while ((event = pending.poll()) != null) result.add(event);
            return Collections.unmodifiableList(result);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        synchronized (queueLock) {
            closed = true;
            receivingText = false;
            composition = null;
            OWNER.compareAndSet(this, null);
            pending.clear();
        }
        Throwable failure = callbackFailure.get();
        try { updateTextInput(); }
        catch (RuntimeException | Error cleanup) {
            if (failure == null) failure = cleanup; else if (failure != cleanup) failure.addSuppressed(cleanup);
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    private void observe(long eventAddress) {
        synchronized (queueLock) {
            if (OWNER.get() != this || closed) return;
            NativeEvent event = bindings.snapshot(eventAddress);
            if (event == null) return;
            if (event.edit != null) {
                String text = event.edit.text;
                if (event.preedit) {
                    if (!receivingText) return;
                    if (composition == null) {
                        if (text.isEmpty()) return;
                        composition = new SceneCompositionSource();
                        event = NativeEvent.edit(SceneEditEvent.compositionBegin(composition, text));
                    } else event = NativeEvent.edit(SceneEditEvent.compositionUpdate(composition, text, !text.isEmpty()));
                } else {
                    SceneCompositionSource source = composition;
                    composition = null;
                    if (!receivingText) return;
                    event = NativeEvent.edit(source == null ? SceneEditEvent.text(text)
                            : SceneEditEvent.compositionCommit(source, text));
                }
            }
            pending.add(event);
        }
    }

    /** 自包含不可变事实；组字在采集时关联来源，drain不读取焦点或改写其所属联系。
     * pointer/focused/edit各自非空时表示对应事实，其余为键事实。 */
    public static final class NativeEvent {
        public final int key;
        public final boolean down, repeat;
        public final SceneEditEvent edit;
        public final ScenePointerEvent pointer;
        public final Boolean focused;
        private final boolean preedit;

        private NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit) {
            this(key, down, repeat, edit, null, null);
        }
        private NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit,
                ScenePointerEvent pointer, Boolean focused) {
            this(key, down, repeat, edit, pointer, focused, false);
        }
        private NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit,
                ScenePointerEvent pointer, Boolean focused, boolean preedit) {
            this.key = key; this.down = down; this.repeat = repeat;
            this.edit = edit; this.pointer = pointer; this.focused = focused; this.preedit = preedit;
        }
        private static NativeEvent edit(SceneEditEvent edit) { return new NativeEvent(0, false, false, edit); }
    }

    /** 只在增强路径构造时解析；Java8 字节码不硬链接 SDL 或 org.lwjglx。 */
    private static final class Bindings {
        final Field windowId, currentEvent;
        final Method address, createEvent, type, key, edit, text, motion, button, wheel, window, common;
        final Method keyWindow, editWindow, textWindow, motionWindow, buttonWindow, wheelWindow, focusWindow;
        final Method scancode, down, repeat, editString, textString, map, begin, end, mainThread, pump, scale, timestamp;
        final Method motionX, motionY, buttonX, buttonY, buttonNumber, wheelX, wheelY, wheelDelta;
        final int focusGained, focusLost;

        Bindings() {
            try {
                ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
                Class<?> union = Class.forName("org.lwjgl.sdl.SDL_Event", false, loader);
                Class<?> keyboard = Class.forName("org.lwjgl.sdl.SDL_KeyboardEvent", false, loader);
                Class<?> editing = Class.forName("org.lwjgl.sdl.SDL_TextEditingEvent", false, loader);
                Class<?> input = Class.forName("org.lwjgl.sdl.SDL_TextInputEvent", false, loader);
                Class<?> moving = Class.forName("org.lwjgl.sdl.SDL_MouseMotionEvent", false, loader);
                Class<?> buttons = Class.forName("org.lwjgl.sdl.SDL_MouseButtonEvent", false, loader);
                Class<?> scrolling = Class.forName("org.lwjgl.sdl.SDL_MouseWheelEvent", false, loader);
                Class<?> windows = Class.forName("org.lwjgl.sdl.SDL_WindowEvent", false, loader);
                Class<?> display = Class.forName("org.lwjglx.opengl.Display", false, loader);
                Class<?> events = Class.forName("org.lwjgl.sdl.SDLEvents", false, loader);
                currentEvent = Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, loader).getField("event");
                address = union.getMethod("address");
                createEvent = union.getMethod("create", long.class);
                windowId = display.getField("sdlWindowId");
                scale = display.getMethod("getPixelScaleFactor");
                type = union.getMethod("type"); key = union.getMethod("key");
                edit = union.getMethod("edit"); text = union.getMethod("text");
                motion = union.getMethod("motion"); button = union.getMethod("button");
                wheel = union.getMethod("wheel"); window = union.getMethod("window"); common = union.getMethod("common");
                timestamp = Class.forName("org.lwjgl.sdl.SDL_CommonEvent", false, loader).getMethod("timestamp");
                keyWindow = keyboard.getMethod("windowID"); editWindow = editing.getMethod("windowID");
                textWindow = input.getMethod("windowID"); motionWindow = moving.getMethod("windowID");
                buttonWindow = buttons.getMethod("windowID"); wheelWindow = scrolling.getMethod("windowID");
                focusWindow = windows.getMethod("windowID");
                scancode = keyboard.getMethod("scancode"); down = keyboard.getMethod("down");
                repeat = keyboard.getMethod("repeat"); editString = editing.getMethod("textString");
                textString = input.getMethod("textString");
                motionX = moving.getMethod("x"); motionY = moving.getMethod("y");
                buttonX = buttons.getMethod("x"); buttonY = buttons.getMethod("y"); buttonNumber = buttons.getMethod("button");
                wheelX = scrolling.getMethod("mouse_x"); wheelY = scrolling.getMethod("mouse_y");
                wheelDelta = scrolling.getMethod("y");
                focusGained = events.getField("SDL_EVENT_WINDOW_FOCUS_GAINED").getInt(null);
                focusLost = events.getField("SDL_EVENT_WINDOW_FOCUS_LOST").getInt(null);
                map = Class.forName("org.lwjglx.input.KeyCodes", false, loader).getMethod("sdlScancodeToLwjgl", int.class);
                Class<?> inputEvents = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents", false, loader);
                begin = inputEvents.getMethod("beginTextInput"); end = inputEvents.getMethod("endTextInput");
                mainThread = Class.forName("me.eigenraven.lwjgl3ify.client.MainThreadExec", false, loader)
                        .getMethod("runOnMainThread", Runnable.class);
                pump = Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, loader).getMethod("pumpEvents");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind lwjgl3ify SDL consumer", failure);
            }
        }

        long currentAddress() {
            try { return (Long) invoke(address, currentEvent.get(null)); }
            catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read current SDL event", failure); }
        }

        NativeEvent snapshot(long eventAddress) {
            try {
                Object union = invoke(createEvent, null, eventAddress);
                int kind = (Integer) invoke(type, union);
                Object value;
                Method id;
                if (kind == 768 || kind == 769) { value = invoke(key, union); id = keyWindow; }
                else if (kind == 770) { value = invoke(edit, union); id = editWindow; }
                else if (kind == 771) { value = invoke(text, union); id = textWindow; }
                else if (kind == 1024) { value = invoke(motion, union); id = motionWindow; }
                else if (kind == 1025 || kind == 1026) { value = invoke(button, union); id = buttonWindow; }
                else if (kind == 1027) { value = invoke(wheel, union); id = wheelWindow; }
                else if (kind == focusGained || kind == focusLost) { value = invoke(window, union); id = focusWindow; }
                else return null;
                if ((Integer) invoke(id, value) != windowId.getInt(null)) return null;
                if (kind == 768 || kind == 769)
                    return new NativeEvent((Integer) invoke(map, null, invoke(scancode, value)),
                            (Boolean) invoke(down, value), (Boolean) invoke(repeat, value), null);
                if (kind == 770 || kind == 771) {
                    String content = (String) invoke(kind == 770 ? editString : textString, value);
                    if (content == null) throw new IllegalStateException("SDL text event has a null text pointer");
                    return new NativeEvent(0, false, false, SceneEditEvent.text(content), null, null, kind == 770);
                }
                if (kind == focusGained || kind == focusLost)
                    return new NativeEvent(0, false, false, null, null, kind == focusGained);
                float ratio = ((Number) invoke(scale, null)).floatValue();
                Method x = kind == 1024 ? motionX : kind == 1027 ? wheelX : buttonX;
                Method y = kind == 1024 ? motionY : kind == 1027 ? wheelY : buttonY;
                int px = (int) Math.floor(((Number) invoke(x, value)).doubleValue() * ratio);
                int py = (int) Math.floor(((Number) invoke(y, value)).doubleValue() * ratio);
                ScenePointerEvent pointer;
                if (kind == 1024) pointer = ScenePointerEvent.move(px, py);
                else if (kind == 1027) pointer = ScenePointerEvent.scroll(px, py,
                        (int) Math.signum(((Number) invoke(wheelDelta, value)).doubleValue()));
                else {
                    int nativeButton = ((Number) invoke(buttonNumber, value)).intValue();
                    int mapped = nativeButton == 1 ? 0 : nativeButton == 3 ? 1 : nativeButton == 2 ? 2 : nativeButton - 1;
                    pointer = kind == 1025 ? ScenePointerEvent.down(mapped, px, py) : ScenePointerEvent.up(mapped, px, py);
                }
                pointer = pointer.at((Long) invoke(timestamp, invoke(common, union)));
                return new NativeEvent(0, false, false, null, pointer, null);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot copy SDL consumer fact", failure);
            }
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            Throwable original = failure.getCause();
            if (original instanceof RuntimeException) throw (RuntimeException) original;
            if (original instanceof Error) throw (Error) original;
            throw new IllegalStateException("Native input call failed: " + method, original);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot invoke native input: " + method, failure); }
    }
}
