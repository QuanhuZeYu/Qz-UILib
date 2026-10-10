package club.heiqi.uilib.ui.host.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;

/** GTNH lwjgl3ify 的可选事实桥；焦点与编辑语义仍归场景唯一输入序列。 */
public final class SceneNativeTextInput implements AutoCloseable {
    private static final AtomicReference<SceneNativeTextInput> OWNER = new AtomicReference<>();
    private final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    private Watch watch;
    private final Bindings bindings;
    private final Object queueLock = new Object();
    private final ConcurrentLinkedQueue<NativeEvent> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private volatile boolean receivingText;
    private boolean windowAvailable = true;
    private boolean textRequested;
    private boolean compositionSession, compositionActive;

    /** 仅检查可选资源，不初始化 SDL 或在普通 LWJGL2 上加载其 native。 */
    public static boolean supported() {
        SceneNativeTextInput owner = OWNER.get();
        if (owner != null) owner.checkFailure();
        ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
        return loader.getResource("org/lwjglx/opengl/Display.class") != null
                && loader.getResource("me/eigenraven/lwjgl3ify/api/InputEvents.class") != null
                && loader.getResource("org/lwjgl/sdl/SDLEvents.class") != null
                && loader.getResource("org/lwjgl/sdl/SDL_EventFilterI.class") != null;
    }

    public static boolean available() {
        return supported();
    }

    public SceneNativeTextInput() {
        if (!supported())
            throw new IllegalStateException("Native text input resources are unavailable");
        bindings = new Bindings();
        if (!OWNER.compareAndSet(null, this))
            throw new IllegalStateException("Another native text input bridge is registered");
        try {
            watch = new Watch(this::observe);
            checkFailure();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (failure != cleanup) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void checkFailure() {
        if (watch != null) watch.checkFailure();
        Throwable failure = callbackFailure.get();
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    /** active 表示该页仍拥有桥；窗口不可用时保留按键释放尾部供宿主门控。 */
    public boolean active() {
        checkFailure();
        return !closed && OWNER.get() == this;
    }

    /** 宿主根据场景焦点通知，重复调用不增加 lwjgl3ify 的 textInputDepth。 */
    public synchronized void textInput(boolean enabled) {
        checkFailure();
        if (closed) return;
        textRequested = enabled;
        updateTextInput();
    }

    /** 失去窗口可用性时停止组字并丢弃文本意图，按键事实仍保留。 */
    public synchronized void availability(boolean available) {
        checkFailure();
        if (closed) return;
        windowAvailable = available;
        updateTextInput();
    }

    private boolean textStarted;

    private void updateTextInput() {
        boolean enabled = textRequested && windowAvailable && !closed;
        if (!enabled) {
            synchronized (queueLock) {
                receivingText = false;
                compositionSession = false;
                compositionActive = false;
                pending.removeIf(event -> event.edit != null);
            }
        }
        if (enabled == textStarted) return;
        // begin 可能同步产生 watch 回调，必须在 SDL_StartTextInput 前允许复制文本。
        synchronized (queueLock) {
            receivingText = enabled;
        }
        // InputEvents 保留 MainThreadExec 和 textInputDepth 配对；调用时不持队列锁。
        invoke(enabled ? bindings.begin : bindings.end, null);
        textStarted = enabled;
    }

    /** 保留 watch 观察顺序；不承诺与 MC mouse 或 SDL consumer 的绝对历史同序。 */
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

    @Override
    public void close() {
        Watch oldWatch;
        synchronized (this) {
            if (closed) return;
            synchronized (queueLock) {
                closed = true;
                receivingText = false;
                compositionSession = false;
                compositionActive = false;
                OWNER.compareAndSet(this, null);
                pending.clear();
            }
            oldWatch = watch;
            watch = null;
        }
        Throwable failure = callbackFailure.get();
        try {
            updateTextInput();
        } catch (RuntimeException | Error cleanup) {
            if (failure == null) failure = cleanup;
            else if (failure != cleanup) failure.addSuppressed(cleanup);
        }
        if (oldWatch != null) {
            try {
                oldWatch.close();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    /** SDL watch 可在任意线程调用；仅复制事实，任何异常不得越过 JNI。 */
    private void observe(long eventAddress) {
        try {
            synchronized (queueLock) {
                if (OWNER.get() != this || closed) return;
                // 复制和入队同一短锁，失焦/关闭边界之后不会重放旧文本。
                NativeEvent event = bindings.snapshot(eventAddress, receivingText);
                if (event != null && event.edit != null) {
                    if (event.edit.compositionEvent) {
                        // 空 preedit 可能先于 771，保留真实组字会话直到提交或下一次新按压。
                        compositionActive = event.edit.composing;
                        if (event.edit.composing) compositionSession = true;
                    } else {
                        if (!compositionSession)
                            event =
                                    new NativeEvent(
                                            0, false, false, SceneEditEvent.text(event.edit.text));
                        compositionSession = false;
                        compositionActive = false;
                    }
                } else if (event != null && event.down && !event.repeat && !compositionActive) {
                    compositionSession = false;
                }
                if (event != null) pending.add(event);
            }
        } catch (RuntimeException | Error failure) {
            callbackFailure.compareAndSet(null, failure);
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

    /** SDL 观察资源；独立于 MC 窗口与 core，只在构造时加载可选平台。 */
    private static final class Watch implements AutoCloseable {
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Method remove, free;
        private Object callback;

        private Watch(LongConsumer observer) {
            try {
                ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
                Class<?> filterInterface =
                        Class.forName("org.lwjgl.sdl.SDL_EventFilterI", false, loader);
                Class<?> filter = Class.forName("org.lwjgl.sdl.SDL_EventFilter", false, loader);
                Class<?> events = Class.forName("org.lwjgl.sdl.SDLEvents", false, loader);
                Method create = filter.getMethod("create", filterInterface);
                Method add = events.getMethod("SDL_AddEventWatch", filterInterface, long.class);
                remove = events.getMethod("SDL_RemoveEventWatch", filterInterface, long.class);
                free = filter.getMethod("free");
                // 精确 LWJGL jar 使用 getDescriptor/callback(long,long)/address 默认方法。
                // 交给接口本身执行，不复制内部 upcall 协议；GTNH Java25 上反射调用。
                Method defaultMethod =
                        InvocationHandler.class.getMethod(
                                "invokeDefault", Object.class, Method.class, Object[].class);
                Object proxy =
                        Proxy.newProxyInstance(
                                loader,
                                new Class<?>[] {filterInterface},
                                (receiver, method, arguments) -> {
                                    if (method.getName().equals("invoke")) {
                                        try {
                                            observer.accept((Long) arguments[1]);
                                        } catch (RuntimeException | Error problem) {
                                            failure.compareAndSet(null, problem);
                                        }
                                        // SDL 忽略 watch 返回值；始终 true，不消费事件。
                                        return true;
                                    }
                                    if (method.isDefault())
                                        return invoke(
                                                defaultMethod, null, receiver, method, arguments);
                                    if (method.getName().equals("hashCode"))
                                        return System.identityHashCode(receiver);
                                    if (method.getName().equals("equals"))
                                        return receiver == arguments[0];
                                    if (method.getName().equals("toString"))
                                        return "SceneNativeTextInput SDL event watch";
                                    throw new IllegalStateException(
                                            "Unexpected SDL callback method: " + method);
                                });
                callback = invoke(create, null, proxy);
                try {
                    if (!(Boolean) invoke(add, null, callback, 0L))
                        throw new IllegalStateException("SDL_AddEventWatch failed");
                    checkFailure();
                } catch (RuntimeException | Error problem) {
                    try {
                        close();
                    } catch (RuntimeException | Error cleanup) {
                        if (problem != cleanup) problem.addSuppressed(cleanup);
                    }
                    throw problem;
                }
            } catch (ReflectiveOperationException problem) {
                throw new IllegalStateException("Cannot bind SDL event watch", problem);
            }
        }

        private void checkFailure() {
            Throwable problem = failure.get();
            if (problem instanceof RuntimeException) throw (RuntimeException) problem;
            if (problem instanceof Error) throw (Error) problem;
        }

        @Override
        public void close() {
            Object oldCallback = callback;
            if (oldCallback == null) return;
            callback = null;
            // SDL watch list 锁等待在途 callback；调用处不持 owner/queue 锁，返回后才能 free。
            invoke(remove, null, oldCallback, 0L);
            invoke(free, oldCallback);
            checkFailure();
        }
    }

    /** 仅页面选择增强路径时解析；Java8 字节码不硬链接 SDL3 或新版 Java API。 */
    private static final class Bindings {
        final Field windowId;
        final Method createEvent;
        final Method type, key, edit, text, keyWindow, editWindow, textWindow;
        final Method scancode, down, repeat, editString, textString, map, begin, end;

        Bindings() {
            try {
                ClassLoader loader = SceneNativeTextInput.class.getClassLoader();
                Class<?> union = Class.forName("org.lwjgl.sdl.SDL_Event", false, loader);
                Class<?> keyboard = Class.forName("org.lwjgl.sdl.SDL_KeyboardEvent", false, loader);
                Class<?> editing =
                        Class.forName("org.lwjgl.sdl.SDL_TextEditingEvent", false, loader);
                Class<?> input = Class.forName("org.lwjgl.sdl.SDL_TextInputEvent", false, loader);
                createEvent = union.getMethod("create", long.class);
                windowId =
                        Class.forName("org.lwjglx.opengl.Display", false, loader)
                                .getField("sdlWindowId");
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
                map =
                        Class.forName("org.lwjglx.input.KeyCodes", false, loader)
                                .getMethod("sdlScancodeToLwjgl", int.class);
                Class<?> events =
                        Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents", false, loader);
                begin = events.getMethod("beginTextInput");
                end = events.getMethod("endTextInput");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind lwjgl3ify SDL event watch", failure);
            }
        }

        NativeEvent snapshot(long eventAddress, boolean textEnabled) {
            try {
                Object union = invoke(createEvent, null, eventAddress);
                int kind = (Integer) invoke(type, union);
                if (kind != 768 && kind != 769 && (!textEnabled || (kind != 770 && kind != 771)))
                    return null;
                Object value =
                        invoke(kind == 768 || kind == 769 ? key : kind == 770 ? edit : text, union);
                Method id =
                        kind == 768 || kind == 769
                                ? keyWindow
                                : kind == 770 ? editWindow : textWindow;
                if ((Integer) invoke(id, value) != windowId.getInt(null)) return null;
                if (kind == 768 || kind == 769)
                    return new NativeEvent(
                            (Integer) invoke(map, null, invoke(scancode, value)),
                            (Boolean) invoke(down, value),
                            (Boolean) invoke(repeat, value),
                            null);
                // 指针仅在本次 callback 有效，立即复制 UTF-8 为 String，队列不保留 native pointer。
                String content = (String) invoke(kind == 770 ? editString : textString, value);
                if (content == null)
                    throw new IllegalStateException("SDL text event has a null text pointer");
                return new NativeEvent(
                        0,
                        false,
                        false,
                        kind == 770
                                ? SceneEditEvent.composition(content, !content.isEmpty())
                                : SceneEditEvent.committed(content));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read SDL event watch fact", failure);
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
