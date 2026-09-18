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
 *
 * <h3>真机诊断（零行为变更）</h3>
 * <p>external 文本通道（lwjgl3ify {@code InputEvents.onTextEvent}）在本仓的自动化测试里被
 * {@code addon.late.gradle} 从 run 任务 classpath 移除，<b>不在测试覆盖内</b>；真机"所有输入框
 * 打不进字"这类故障只能靠一次真机复现的日志定案。故本类把注册事务结果与反射面缺失提到
 * {@code info}/{@code warn} 级各打一条（正常路径每条消息只打一次，不刷屏）：</p>
 * <ul>
 *   <li>注册成功 → info 一条（证明 external 模式确实接管）；</li>
 *   <li>宿主为 2.x 世代（{@code InputEvents} 无 begin/endTextInput）→ info 一条：该世代 jar 内
 *       无任何类调用 {@code injectTextEvent}，字符只经 MC {@code keyTyped} 到达，回退 char 路径
 *       是它的正确形态而非降级；</li>
 *   <li>反射面不匹配（已有 begin/endTextInput 却缺监听器注册入口）→ warn 一条
 *       （版本漂移第一现场，提示比对 api jar）；</li>
 *   <li>注册事务异常 → warn 一条带异常（证明回落 char 降级路径）。</li>
 * </ul>
 *
 * <p>级别口径与 {@code McScreenBridge} 的文本通道四态一致：世代差异是正常态，只有「契约存在但
 * 面不匹配」才是异常态。</p>
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
    /** 注册成功日志是否已上报（每实例一次，正常路径不刷屏）。 */
    private boolean successReported;
    /** 反射面不匹配日志是否已上报（每实例一次）。 */
    private boolean faceMismatchReported;
    /** 2.x 世代无文本接管契约说明是否已上报（每实例一次）。 */
    private boolean generationReported;

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
     * 探测宿主是否提供文本接管契约：{@code InputEvents} 上同时存在 {@code beginTextInput()} 与
     * {@code endTextInput()}。
     *
     * <p>两个 lwjgl3ify 世代的正确文本路径不同，本探测就是这条分界：2.x 世代（GLFW 后端，
     * GTNH 2.8.0/2.8.4 的 2.1.15/2.1.16）不声明这两个方法，其 jar 内也无任何类调用
     * {@code injectTextEvent}，字符只经 MC {@code keyTyped} 到达，因此「回退 MC char 路径」是该
     * 世代的正确形态而非降级；3.x 世代（SDL 文本输入，GTNH 2.9.0 的 3.0.x）改为显式启停，
     * 字符随之改由 {@code onTextEvent} 投递，{@code pushKeyTyped} 的 char 才按契约不产 TEXT。</p>
     *
     * <p>与 {@link #register()} 的必要条件同源：本探测为 true 只说明宿主具备接管能力，
     * 是否真的生效仍由注册事务结果决定。</p>
     */
    public static boolean textTakeoverSupported() {
        return textTakeoverSupported(new ReflectionAdapter());
    }

    /** 使用指定反射适配器探测宿主文本接管能力。 */
    static boolean textTakeoverSupported(ReflectionAdapter adapter) {
        try {
            Class<?> inputEvents = adapter.loadClass(INPUT_EVENTS_CLASS_NAME, false, ANCHOR_LOADER);
            try {
                adapter.getMethod(inputEvents, "beginTextInput");
                adapter.getMethod(inputEvents, "endTextInput");
                return true;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
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
                reportUnavailableOnce();
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
            logRegisteredOnce(prepared);
            return true;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
            // 真机"文本输入打不进字"的第一现场：注册失败 ⇒ external 模式不会启用，
            // 宿主回落 MC char 降级路径。原为 debug 级（真机默认日志级别看不到），提到 warn。
            LOG.warn("[文本通道] lwjgl3ify 文本桥注册事务失败，已回滚 ⇒ 本次界面回退 MC char 降级路径；"
                            + "失败类型={}，原因={}",
                    e.getClass().getName(), String.valueOf(e.getMessage()), e);
            rollback();
            return false;
        }
    }

    /** 注册成功一次性上报：真机据此确认 external 文本模式确实接管。 */
    private void logRegisteredOnce(RegistrationPlan registered) {
        if (successReported) {
            return;
        }
        successReported = true;
        LOG.info("[文本通道] lwjgl3ify 文本桥注册成功: InputEvents={}, 监听器={}, beginTextInput(启动 SDL 文本输入) 已调用"
                        + " ⇒ external 文本模式接管：此后 pushKeyTyped 的字符不再产 TEXT，文本只由 onTextEvent 投递",
                INPUT_EVENTS_CLASS_NAME, registered.addMethod.getName());
    }

    /**
     * 无法注册时按宿主世代分级上报。
     *
     * <p>「缺 begin/endTextInput」与「有 begin/endTextInput 却缺监听器注册入口」是两件事：
     * 前者是 lwjgl3ify 2.x 世代的契约形态（GTNH 2.8.0/2.8.4 的 2.1.15/2.1.16），后者才是本类
     * 反射面与宿主不匹配。把前者报成 warn 会让真机日志每次打开界面都出现假告警。</p>
     */
    private void reportUnavailableOnce() {
        if (!textTakeoverSupported(reflection)) {
            logGenerationWithoutTakeoverOnce();
            return;
        }
        logFaceMismatchOnce();
    }

    /** 2.x 世代一次性说明：该世代没有外部文本接管契约，char 路径就是正确路径。 */
    private void logGenerationWithoutTakeoverOnce() {
        if (generationReported) {
            return;
        }
        generationReported = true;
        LOG.info("[文本通道] 宿主 lwjgl3ify 无文本接管契约（2.x GLFW 世代）: {} 上无 beginTextInput/"
                        + "endTextInput ⇒ 本类不注册监听器，字符按该世代正确路径由 MC keyTyped 合成，非降级",
                INPUT_EVENTS_CLASS_NAME);
    }

    /** 反射面不匹配一次性告警：lwjgl3ify 版本漂移（改名/移除）的第一现场。 */
    private void logFaceMismatchOnce() {
        if (faceMismatchReported) {
            return;
        }
        faceMismatchReported = true;
        LOG.warn("[文本通道] lwjgl3ify 输入 API 反射面不匹配: {} 具备 beginTextInput/endTextInput，"
                        + "却缺少 add+remove[Weak]KeyboardListener ⇒ 不注册，回退 MC char 路径；"
                        + "本类按 lwjgl3ify 3.0.x InputEvents 契约反射，版本变更时请比对 api jar",
                INPUT_EVENTS_CLASS_NAME);
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
                LOG.warn("[文本通道] lwjgl3ify endTextInput 清理失败（保留待下次重试）", e);
            }
        }
        if (removePending) {
            try {
                reflection.invokeStatic(plan.removeMethod, plan.listener);
                removePending = false;
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError e) {
                LOG.warn("[文本通道] lwjgl3ify 监听器清理失败（保留待下次重试）——未摘除的监听器会让下一次注册事务失败", e);
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
