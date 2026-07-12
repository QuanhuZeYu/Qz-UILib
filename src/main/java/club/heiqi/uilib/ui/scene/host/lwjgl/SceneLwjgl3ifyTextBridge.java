package club.heiqi.uilib.ui.scene.host.lwjgl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * scene 输入层 lwjgl3ify 完整文本桥。
 *
 * <p>本类是适配层中唯一允许反射 lwjgl3ify 的文本入口。注册与 SDL 文本输入启停组成一个
 * 可回滚事务；任何失败均保留尚未完成的清理步骤，供后续注销重试。</p>
 */
public final class SceneLwjgl3ifyTextBridge {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/SceneLwjgl3ifyTextBridge");
    private static final String INPUT_EVENTS_CLASS_NAME = "me.eigenraven.lwjgl3ify.api.InputEvents";
    private static final String KEYBOARD_LISTENER_CLASS_NAME =
            "me.eigenraven.lwjgl3ify.api.InputEvents$KeyboardListener";
    private static final ClassLoader ANCHOR_LOADER = SceneLwjgl3ifyTextBridge.class.getClassLoader();

    private final Consumer<String> textSink;
    private final ReflectionAdapter reflection;
    private RegistrationPlan plan;
    private State state = State.IDLE;
    private boolean endPending;
    private boolean removePending;

    /** 创建使用真实反射的文本桥。 */
    public SceneLwjgl3ifyTextBridge(Consumer<String> textSink) {
        this(textSink, new ReflectionAdapter());
    }

    /** 创建可注入反射行为的文本桥，供包内故障测试使用。 */
    SceneLwjgl3ifyTextBridge(Consumer<String> textSink, ReflectionAdapter reflection) {
        this.textSink = textSink;
        this.reflection = reflection;
    }

    /** 探测 lwjgl3ify 输入入口是否可链接，不触发类初始化。 */
    public static boolean isAvailable() {
        return isAvailable(new ReflectionAdapter());
    }

    /** 使用指定反射适配器探测运行时。 */
    static boolean isAvailable(ReflectionAdapter adapter) {
        try {
            adapter.loadClass(INPUT_EVENTS_CLASS_NAME, false, ANCHOR_LOADER);
            return true;
        } catch (ClassNotFoundException | SecurityException | LinkageError e) {
            return false;
        }
    }

    /**
     * 注册监听器并启动 SDL 文本输入。
     *
     * @return 注册事务完整提交时返回 true，否则回滚并返回 false
     */
    public boolean register() {
        if (state == State.ACTIVE) {
            return true;
        }
        if (state == State.CLEANUP_PENDING) {
            rollback();
            if (state != State.IDLE) {
                return false;
            }
        }

        try {
            RegistrationPlan prepared = preparePlan();
            if (prepared == null) {
                return false;
            }
            plan = prepared;
            removePending = true;
            state = State.ADD_ATTEMPTED;
            reflection.invokeStatic(prepared.addMethod, prepared.listener);

            endPending = true;
            state = State.BEGIN_ATTEMPTED;
            reflection.invokeStatic(prepared.beginMethod);
            state = State.ACTIVE;
            return true;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
            LOG.debug("UILib scene 文本桥注册事务失败，开始回滚", e);
            rollback();
            return false;
        }
    }

    /** 注销监听器并停止 SDL 文本输入；失败步骤保留到下次调用重试。 */
    public void unregister() {
        if (state == State.IDLE) {
            return;
        }
        state = State.CLEANUP_PENDING;
        rollback();
    }

    /** 预解析所有必要方法，保证外部副作用发生前计划完整且不可变。 */
    private RegistrationPlan preparePlan() throws ReflectiveOperationException {
        Class<?> inputEvents = reflection.loadClass(INPUT_EVENTS_CLASS_NAME, false, ANCHOR_LOADER);
        Class<?> listenerClass = reflection.loadClass(KEYBOARD_LISTENER_CLASS_NAME, false, ANCHOR_LOADER);
        Object listener = reflection.proxy(listenerClass, new TextListenerInvocationHandler(textSink), ANCHOR_LOADER);
        Method begin = findMethod(inputEvents, "beginTextInput");
        Method end = findMethod(inputEvents, "endTextInput");
        if (begin == null || end == null) {
            return null;
        }

        Method add = findMethod(inputEvents, "addWeakKeyboardListener", listenerClass);
        Method remove = findMethod(inputEvents, "removeWeakKeyboardListener", listenerClass);
        if (add == null || remove == null) {
            add = findMethod(inputEvents, "addKeyboardListener", listenerClass);
            remove = findMethod(inputEvents, "removeKeyboardListener", listenerClass);
        }
        return add == null || remove == null ? null
                : new RegistrationPlan(inputEvents, listener, add, remove, begin, end);
    }

    /** 独立尝试每个待清理步骤，成功后立即清除对应 pending。 */
    private void rollback() {
        state = State.CLEANUP_PENDING;
        if (endPending) {
            try {
                reflection.invokeStatic(plan.endMethod);
                endPending = false;
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
                LOG.debug("UILib scene 文本桥 endTextInput 清理失败，保留重试", e);
            }
        }
        if (removePending) {
            try {
                reflection.invokeStatic(plan.removeMethod, plan.listener);
                removePending = false;
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
                LOG.debug("UILib scene 文本桥 listener 清理失败，保留重试", e);
            }
        }
        if (!endPending && !removePending) {
            plan = null;
            state = State.IDLE;
        }
    }

    /** 查找公开方法；缺失或安全策略拒绝时返回 null。 */
    private Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return reflection.getMethod(owner, name, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    /** 注册事务状态。 */
    private enum State {
        IDLE,
        ADD_ATTEMPTED,
        BEGIN_ATTEMPTED,
        ACTIVE,
        CLEANUP_PENDING
    }

    /** 外部副作用执行前冻结的完整注册计划。 */
    private static final class RegistrationPlan {
        private final Class<?> inputEventsClass;
        private final Object listener;
        private final Method addMethod;
        private final Method removeMethod;
        private final Method beginMethod;
        private final Method endMethod;

        private RegistrationPlan(Class<?> inputEventsClass, Object listener, Method addMethod, Method removeMethod,
                Method beginMethod, Method endMethod) {
            this.inputEventsClass = inputEventsClass;
            this.listener = listener;
            this.addMethod = addMethod;
            this.removeMethod = removeMethod;
            this.beginMethod = beginMethod;
            this.endMethod = endMethod;
        }
    }

    /** 将所有可故障反射操作集中到可替换边界。 */
    static class ReflectionAdapter {
        Class<?> loadClass(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
            return Class.forName(name, initialize, loader);
        }

        Method getMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            return owner.getMethod(name, parameterTypes);
        }

        Object proxy(Class<?> listenerClass, InvocationHandler handler, ClassLoader fallbackLoader) {
            ClassLoader loader = listenerClass.getClassLoader();
            return Proxy.newProxyInstance(loader == null ? fallbackLoader : loader,
                    new Class<?>[] {listenerClass}, handler);
        }

        Object invokeStatic(Method method, Object... arguments) throws ReflectiveOperationException {
            return method.invoke(null, arguments);
        }
    }

    /** KeyboardListener 代理处理器，仅消费完整文本事件。 */
    private static final class TextListenerInvocationHandler implements InvocationHandler {
        private final Consumer<String> textSink;

        private TextListenerInvocationHandler(Consumer<String> textSink) {
            this.textSink = textSink;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if ("onTextEvent".equals(method.getName()) && args != null && args.length == 1 && args[0] != null) {
                String text = readTextField(args[0]);
                if (text != null && !text.isEmpty()) {
                    textSink.accept(text);
                }
                return null;
            }
            return defaultReturn(method.getReturnType());
        }

        private static String readTextField(Object event) {
            try {
                Field field = event.getClass().getField("text");
                Object value = field.get(event);
                return value instanceof String ? (String) value : null;
            } catch (NoSuchFieldException | IllegalAccessException | SecurityException e) {
                return null;
            }
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "QzUiLib SceneLwjgl3ifyTextBridge listener";
            }
            if ("hashCode".equals(method.getName())) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(method.getName())) {
                return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
            }
            return null;
        }

        private static Object defaultReturn(Class<?> returnType) {
            if (returnType == boolean.class) return Boolean.FALSE;
            if (returnType == byte.class) return Byte.valueOf((byte) 0);
            if (returnType == short.class) return Short.valueOf((short) 0);
            if (returnType == int.class) return Integer.valueOf(0);
            if (returnType == long.class) return Long.valueOf(0L);
            if (returnType == float.class) return Float.valueOf(0f);
            if (returnType == double.class) return Double.valueOf(0d);
            if (returnType == char.class) return Character.valueOf('\0');
            return null;
        }
    }
}
