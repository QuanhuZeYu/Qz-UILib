package club.heiqi.uilib.internal.devtools.pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * 新栈 scene 输入层 lwjgl3ify 文本桥（Bug2）—— 适配层中唯一允许 import/反射 lwjgl3ify 的类。
 *
 * <h3>职责</h3>
 * <p>对接 lwjgl3ify {@code me.eigenraven.lwjgl3ify.api.InputEvents#KeyboardListener#onTextEvent}：
 * 该回调传入的 {@code TextEvent.text} 是完整 String（含 IME 合成结果与补充平面 emoji，codepoint &gt; 0xFFFF），
 * 直接喂给 {@code textSink}（接 {@code SceneHostWidget::pushText}）。避免 MC/lwjgl3ify 把 emoji 拆成两次
 * surrogate {@code keyTyped} 回调导致碎字符的 Bug2。</p>
 *
 * <h3>双环境守护（Issue #62）</h3>
 * <p>全程反射探测，lwjgl3ify 不可用（{@link #isAvailable()} 为 false 或注册抛异常）时
 * {@link #register()} 返回 false，宿主据此回到降级 char 累积路径，不抛异常不污染核心包。</p>
 *
 * <h3>I10 守护</h3>
 * <p>核心包 {@code ui.scene.input} 零平台 import；lwjgl3ify 类全部经字符串反射加载，不出现编译期 import。</p>
 */
public final class SceneLwjgl3ifyTextBridge {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/SceneLwjgl3ifyTextBridge");

    /** lwjgl3ify 输入事件入口类全限定名 */
    private static final String INPUT_EVENTS_CLASS_NAME = "me.eigenraven.lwjgl3ify.api.InputEvents";
    /** lwjgl3ify 键盘监听接口全限定名（内部接口） */
    private static final String KEYBOARD_LISTENER_CLASS_NAME =
            "me.eigenraven.lwjgl3ify.api.InputEvents$KeyboardListener";

    /** 完整文本下沉口（接 hostWidget::pushText） */
    private final Consumer<String> textSink;

    /** 已注册的监听器代理实例（unregister 时用于移除），未注册为 null */
    private Object keyboardListener;
    /** 反射定位到的 InputEvents 类，注册失败为 null */
    private Class<?> inputEventsClass;
    /** 实际用于移除监听器的方法（addWeakKeyboardListener / addKeyboardListener 对应的 remove），可能为 null */
    private Method removeKeyboardListenerMethod;
    /** 是否已成功注册（幂等控制） */
    private boolean registered;

    /**
     * 创建文本桥。
     *
     * @param textSink 完整文本下沉口，onTextEvent 回调内 {@code textSink.accept(text)}（不可为 null）
     */
    public SceneLwjgl3ifyTextBridge(Consumer<String> textSink) {
        this.textSink = textSink;
    }

    /**
     * 探测当前运行时是否存在 lwjgl3ify {@code InputEvents}。
     *
     * <p>仅做类存在性探测，不触发任何注册副作用。catch {@link ClassNotFoundException} 与
     * {@link LinkageError}（类存在但链接失败，如依赖缺失），均视为不可用返回 false。</p>
     *
     * @return true=lwjgl3ify InputEvents 可用；false=不可用（应走降级路径）
     */
    public static boolean isAvailable() {
        try {
            Class.forName(INPUT_EVENTS_CLASS_NAME);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            return false;
        }
    }

    /**
     * 注册 lwjgl3ify 键盘监听并启动 SDL 文本输入。
     *
     * <h3>流程</h3>
     * <ol>
     *   <li>反射加载 InputEvents 与 KeyboardListener 接口</li>
     *   <li>用 {@link Proxy} 实现 KeyboardListener：onTextEvent 反射读 {@code event.text}（String）→ {@code textSink.accept}，
     *       onKeyEvent 等其余方法空实现/返回默认值</li>
     *   <li>优先 {@code addWeakKeyboardListener}（弱引用，避免监听器泄漏），降级 {@code addKeyboardListener}</li>
     *   <li>调 {@code beginTextInput()} 启动 SDL 文本输入</li>
     * </ol>
     *
     * <p>任何反射异常（类/方法缺失、调用失败、链接错误）均吞掉并返回 false，由宿主回退降级路径。</p>
     *
     * @return true=注册成功（external 模式可用）；false=失败（应走降级路径）
     */
    public boolean register() {
        if (registered) {
            return true;
        }
        try {
            Class<?> ieClass = Class.forName(INPUT_EVENTS_CLASS_NAME);
            Class<?> listenerClass = Class.forName(KEYBOARD_LISTENER_CLASS_NAME);

            // 用 Proxy 实现 KeyboardListener 接口（onTextEvent 接文本，其余方法默认空实现）
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[] {listenerClass},
                    new TextListenerInvocationHandler(textSink));

            // 优先 addWeakKeyboardListener（弱引用注册，规避监听器泄漏），降级 addKeyboardListener
            Method addMethod = findMethod(ieClass, "addWeakKeyboardListener", listenerClass);
            boolean weak = addMethod != null;
            if (addMethod == null) {
                addMethod = findMethod(ieClass, "addKeyboardListener", listenerClass);
            }
            if (addMethod == null) {
                LOG.debug("UILib scene 文本桥：未找到 add(Weak)KeyboardListener 方法，降级");
                return false;
            }
            addMethod.invoke(null, listener);

            // 解析对应的 remove 方法（弱注册→removeWeakKeyboardListener 优先，再降级 removeKeyboardListener）
            Method removeMethod = null;
            if (weak) {
                removeMethod = findMethod(ieClass, "removeWeakKeyboardListener", listenerClass);
            }
            if (removeMethod == null) {
                removeMethod = findMethod(ieClass, "removeKeyboardListener", listenerClass);
            }

            // 启动 SDL 文本输入
            invokeStaticNoArg(ieClass, "beginTextInput");

            this.inputEventsClass = ieClass;
            this.keyboardListener = listener;
            this.removeKeyboardListenerMethod = removeMethod;
            this.registered = true;
            LOG.debug("UILib scene 文本桥注册成功：weak={} remove={}", weak, removeMethod != null);
            return true;
        } catch (ClassNotFoundException e) {
            LOG.debug("UILib scene 文本桥注册失败（类缺失），降级", e);
            return false;
        } catch (LinkageError e) {
            LOG.debug("UILib scene 文本桥注册失败（链接错误），降级", e);
            return false;
        } catch (Exception e) {
            LOG.debug("UILib scene 文本桥注册失败（反射异常），降级", e);
            return false;
        }
    }

    /**
     * 注销监听并停止 SDL 文本输入（幂等）。
     *
     * <p>先 {@code endTextInput()}，再移除监听器（若解析到 remove 方法）。任何异常静默吞掉。
     * 未注册时直接返回。</p>
     */
    public void unregister() {
        if (!registered) {
            return;
        }
        try {
            invokeStaticNoArg(inputEventsClass, "endTextInput");
            if (removeKeyboardListenerMethod != null && keyboardListener != null) {
                removeKeyboardListenerMethod.invoke(null, keyboardListener);
            }
        } catch (Exception e) {
            LOG.debug("UILib scene 文本桥注销异常（已忽略）", e);
        } catch (LinkageError e) {
            LOG.debug("UILib scene 文本桥注销链接错误（已忽略）", e);
        } finally {
            registered = false;
            keyboardListener = null;
            inputEventsClass = null;
            removeKeyboardListenerMethod = null;
        }
    }

    /**
     * 查找静态方法（带单参重载），不存在返回 null。
     *
     * @param owner     目标类
     * @param name      方法名
     * @param paramType 参数类型
     * @return Method 或 null
     */
    private static Method findMethod(Class<?> owner, String name, Class<?> paramType) {
        try {
            return owner.getMethod(name, paramType);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * 反射调用无参静态方法，方法不存在或调用失败静默忽略。
     *
     * @param owner 目标类
     * @param name  方法名
     */
    private static void invokeStaticNoArg(Class<?> owner, String name) {
        if (owner == null) {
            return;
        }
        try {
            Method m = owner.getMethod(name);
            m.invoke(null);
        } catch (Exception e) {
            LOG.debug("UILib scene 文本桥：无参静态方法调用失败 name={}", name, e);
        }
    }

    /**
     * KeyboardListener 代理的调用处理器 —— 只关心 onTextEvent，其余方法返回默认值。
     */
    private static final class TextListenerInvocationHandler implements InvocationHandler {

        private final Consumer<String> textSink;

        private TextListenerInvocationHandler(Consumer<String> textSink) {
            this.textSink = textSink;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // Object 自带方法（toString/hashCode/equals）走默认实现，避免 Proxy 调用抛异常
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            // onTextEvent(TextEvent)：反射读 event.text（完整 String）→ 下沉
            if ("onTextEvent".equals(method.getName()) && args != null && args.length == 1 && args[0] != null) {
                String text = readTextField(args[0]);
                if (text != null && !text.isEmpty()) {
                    textSink.accept(text);
                }
                return null;
            }
            // onKeyEvent 及其它接口方法：scene 层键盘走 MC keyTyped 旁路，此处不处理，返回默认值
            return defaultReturn(method.getReturnType());
        }

        /**
         * 反射读取 TextEvent.text 字段（参照 Lwjgl3ifyInputBackend 实际读法：Object→String）。
         *
         * @param event TextEvent 实例
         * @return text 字符串，读不到或非 String 返回 null
         */
        private static String readTextField(Object event) {
            try {
                Field field = event.getClass().getField("text");
                Object value = field.get(event);
                return value instanceof String ? (String) value : null;
            } catch (NoSuchFieldException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            } catch (SecurityException e) {
                return null;
            }
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "QzUiLib SceneLwjgl3ifyTextBridge listener";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
            }
            return null;
        }

        /**
         * 为非 void 原始类型返回类型给出默认值，避免 Proxy 拆箱 NPE。
         *
         * @param returnType 方法返回类型
         * @return 默认值（boolean→false，数值→0，其余→null）
         */
        private static Object defaultReturn(Class<?> returnType) {
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == byte.class) {
                return Byte.valueOf((byte) 0);
            }
            if (returnType == short.class) {
                return Short.valueOf((short) 0);
            }
            if (returnType == int.class) {
                return Integer.valueOf(0);
            }
            if (returnType == long.class) {
                return Long.valueOf(0L);
            }
            if (returnType == float.class) {
                return Float.valueOf(0f);
            }
            if (returnType == double.class) {
                return Double.valueOf(0d);
            }
            if (returnType == char.class) {
                return Character.valueOf('\0');
            }
            return null;
        }
    }
}
