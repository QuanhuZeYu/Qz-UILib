package club.heiqi.uilib.ui.host.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;

/** GTNH lwjgl3ify 的可选事实桥；焦点与编辑语义仍归场景唯一输入序列。 */
public final class SceneNativeTextInput implements AutoCloseable {
    private static final AtomicReference<SceneNativeTextInput> OWNER = new AtomicReference<>();
    private static volatile boolean installed;
    private final Bindings bindings;
    private final Object queueLock = new Object();
    private final ConcurrentLinkedQueue<NativeEvent> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private volatile boolean receivingText;
    private boolean windowAvailable = true;
    private boolean textRequested;
    private boolean compositionSession, compositionActive;

    /** 仅实际执行过注入后的 pump 才可选择增强路径；页面内不得中途切换两路输入。 */
    public static boolean supported() { return installed; }
    public static boolean available() { return installed; }

    /** 注入的 HEAD 每帧运行，无事件时也能证明 hook 已安装；不触发 SDL 类加载。 */
    public static void markHookAvailable() { installed = true; }

    public SceneNativeTextInput() {
        if (!supported()) throw new IllegalStateException("Native text input hook has not run");
        bindings = new Bindings();
        if (!OWNER.compareAndSet(null, this))
            throw new IllegalStateException("Another native text input bridge is registered");
    }

    /** active 表示该页仍拥有桥；窗口不可用时保留按键释放尾部供宿主门控。 */
    public boolean active() { return !closed && OWNER.get() == this; }

    /** 宿主根据场景焦点通知，重复调用不增加 lwjgl3ify 的 textInputDepth。 */
    public synchronized void textInput(boolean enabled) {
        if (closed) return;
        textRequested = enabled;
        updateTextInput();
    }

    /** 失去窗口可用性时停止组字并丢弃文本意图，按键事实仍保留。 */
    public synchronized void availability(boolean available) {
        if (closed) return;
        windowAvailable = available;
        updateTextInput();
    }

    private boolean textStarted;
    private void updateTextInput() {
        boolean enabled = textRequested && windowAvailable && !closed;
        if (!enabled) {
            synchronized (queueLock) {
                receivingText = false;compositionSession=false;compositionActive=false;
                pending.removeIf(event -> event.edit != null);
            }
        }
        if (enabled == textStarted) return;
        // 调用可能调度到 SDL 主线程；不得持队列锁，capture 不访问核心可变状态。
        invoke(enabled ? bindings.begin : bindings.end, null);
        textStarted = enabled;
        synchronized (queueLock) { receivingText = enabled; }
    }

    /** 保留原生消费顺序；不拿 SDL timestamp 与 Keyboard 的 Sys.nanoTime 字符流对齐。 */
    public List<NativeEvent> drain() {
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
            receivingText = false;compositionSession=false;compositionActive=false;
            OWNER.compareAndSet(this, null);
            pending.clear();
        }
        // 先使旧代际失效，再停止其所属文本输入；释放失败仍保持旧页不可接收。
        updateTextInput();
    }

    /** 在唯一 SDL consumer 的 memCopy 后调用；无注册页时不反射读取 SDL。 */
    public static void capture() {
        SceneNativeTextInput owner = OWNER.get();
        if (owner == null || owner.closed) return;
        synchronized (owner.queueLock) {
            if (OWNER.get() != owner || owner.closed) return;
            // 快照与入队同一临界区，失焦/关闭不会让正在复制的旧文本越过清理边界。
            NativeEvent event = owner.bindings.snapshot(owner.receivingText);
            if(event!=null && event.edit!=null){
                if(event.edit.compositionEvent){
                    // 空preedit常在771前出现，保留真实组字会话事实到提交或下一次新按压。
                    owner.compositionActive=event.edit.composing;
                    if(event.edit.composing)owner.compositionSession=true;
                } else {
                    if(!owner.compositionSession)event=new NativeEvent(0,false,false,SceneEditEvent.text(event.edit.text));
                    owner.compositionSession=false;owner.compositionActive=false;
                }
            } else if(event!=null && event.down && !event.repeat && !owner.compositionActive){
                // 组字结束且尚无提交时，下一次真实新按压开始新的输入段（取消旧组字）。
                owner.compositionSession=false;
            }
            if (event != null) owner.pending.add(event);
        }
    }

    /** 不可变原生事实；edit 非空表示真实组字/提交，key 仅按键事实有效。 */
    public static final class NativeEvent {
        public final int key;
        public final boolean down, repeat;
        public final SceneEditEvent edit;
        private NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit) {
            this.key = key;
            this.down = down;
            this.repeat = repeat;
            this.edit = edit;
        }
    }

    /** 只在 hook 已知且页面申请增强路径后解析；LWJGL2 不存在强制链接。 */
    private static final class Bindings {
        final Field event, windowId;
        final Method type, key, edit, text, keyWindow, editWindow, textWindow;
        final Method scancode, down, repeat, editString, textString, map, begin, end;
        Bindings() {
            try {
                ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
                Class<?> loop = Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, loader);
                Class<?> union = Class.forName("org.lwjgl.sdl.SDL_Event", false, loader);
                Class<?> keyboard = Class.forName("org.lwjgl.sdl.SDL_KeyboardEvent", false, loader);
                Class<?> editing = Class.forName("org.lwjgl.sdl.SDL_TextEditingEvent", false, loader);
                Class<?> input = Class.forName("org.lwjgl.sdl.SDL_TextInputEvent", false, loader);
                event = loop.getField("event");
                windowId = Class.forName("org.lwjglx.opengl.Display", false, loader).getField("sdlWindowId");
                type = union.getMethod("type");
                key = union.getMethod("key");
                edit = union.getMethod("edit");
                text = union.getMethod("text");
                keyWindow = keyboard.getMethod("windowID");
                editWindow = editing.getMethod("windowID");
                textWindow = input.getMethod("windowID");
                scancode = keyboard.getMethod("scancode");
                down = keyboard.getMethod("down");
                repeat = keyboard.getMethod("repeat");
                editString = editing.getMethod("textString");
                textString = input.getMethod("textString");
                map = Class.forName("org.lwjglx.input.KeyCodes", false, loader)
                    .getMethod("sdlScancodeToLwjgl", int.class);
                Class<?> events = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents", false, loader);
                begin = events.getMethod("beginTextInput");
                end = events.getMethod("endTextInput");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind installed lwjgl3ify text input hook", failure);
            }
        }

        NativeEvent snapshot(boolean textEnabled) {
            try {
                Object union = event.get(null);
                int kind = (Integer) invoke(type, union);
                if (kind != 768 && kind != 769 && (!textEnabled || (kind != 770 && kind != 771))) return null;
                Object value = invoke(kind == 768 || kind == 769 ? key : kind == 770 ? edit : text, union);
                Method id = kind == 768 || kind == 769 ? keyWindow : kind == 770 ? editWindow : textWindow;
                if ((Integer) invoke(id, value) != windowId.getInt(null)) return null;
                if (kind == 768 || kind == 769)
                    return new NativeEvent((Integer) invoke(map, null, invoke(scancode, value)),
                        (Boolean) invoke(down, value), (Boolean) invoke(repeat, value), null);
                // SDL 文本指针属于当前 event，必须在下一次 memCopy 前复制为 Java String。
                String content = (String) invoke(kind == 770 ? editString : textString, value);
                if (content == null) throw new IllegalStateException("SDL text event has a null text pointer");
                return new NativeEvent(0, false, false, kind == 770
                    ? SceneEditEvent.composition(content, !content.isEmpty()) : SceneEditEvent.committed(content));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read installed lwjgl3ify event", failure);
            }
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable original = failure.getCause();
            if (original instanceof RuntimeException) throw (RuntimeException) original;
            if (original instanceof Error) throw (Error) original;
            throw new IllegalStateException("Native text input call failed: " + method, original);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot invoke native text input: " + method, failure);
        }
    }
}
