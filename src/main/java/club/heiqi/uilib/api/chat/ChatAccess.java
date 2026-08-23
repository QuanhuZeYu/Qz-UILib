package club.heiqi.uilib.api.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.util.IChatComponent;

/**
 * 聊天渲染公共接入点(api.chat 兼容承诺核心):装饰器链 + 接管状态 + 发送桥。
 *
 * <p>其他 mod 通过本单例接入聊天渲染管线,无需触碰内部接管实现;本包为公共兼容承诺。</p>
 *
 * <p>线程语义:装饰器注册可在任意线程;应用发生在消息到达路径,链用 CopyOnWrite 保证
 * 注册/迭代并发安全;装饰器异常隔离(单例失败不影响消息与链路)。</p>
 */
public final class ChatAccess {

    private static final ChatAccess INSTANCE = new ChatAccess();

    /** 装饰器链(注册序应用)。 */
    private final List<ChatMessageDecorator> decorators =
            new CopyOnWriteArrayList<ChatMessageDecorator>();

    private volatile boolean takeoverActive = false;

    private ChatAccess() {
    }

    /** @return 全局接入单例 */
    public static ChatAccess getInstance() {
        return INSTANCE;
    }

    // ==================== 接管状态 ====================

    /** @return 聊天框接管当前是否生效(安装器内部回写) */
    public boolean isTakeoverActive() {
        return takeoverActive;
    }

    /** 安装器状态回写(internal.chat3 调用,非公共契约)。 */
    public void setTakeoverActive(boolean active) {
        takeoverActive = active;
    }

    // ==================== 消息装饰器链 ====================

    /**
     * 注册消息装饰器(显示前变换组件链)。
     *
     * @param decorator 装饰器(不可为 null)
     * @return 注销句柄(调用 close 即注销;重复 close 幂等)
     */
    public AutoCloseable registerDecorator(final ChatMessageDecorator decorator) {
        if (decorator == null) {
            throw new IllegalArgumentException("decorator 不能为 null");
        }
        decorators.add(decorator);
        return new AutoCloseable() {
            private boolean closed = false;

            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    decorators.remove(decorator);
                }
            }
        };
    }

    /** @return 当前装饰器数量(诊断) */
    public int decoratorCount() {
        return decorators.size();
    }

    /**
     * 应用装饰器链(接管层调用):按注册序变换,任一装饰器抛异常则隔离并继续。
     *
     * @param component 原始消息组件(不可为 null)
     * @return 变换后组件;链中某装饰器返回 null 表示丢弃(null)
     */
    public IChatComponent decorate(IChatComponent component) {
        IChatComponent current = component;
        for (ChatMessageDecorator decorator : decorators) {
            try {
                IChatComponent result = decorator.decorate(current);
                if (result == null) {
                    return null; // 丢弃语义
                }
                current = result;
            } catch (RuntimeException failure) {
                // 异常隔离:该装饰器失效,消息继续走链
                decorators.remove(decorator);
            }
        }
        return current;
    }

    // ==================== 发送桥 ====================

    /**
     * 发送聊天消息(经 {@link ChatBridge} 映射原版发送链)。
     *
     * @param message 消息文本
     */
    public void send(String message) {
        ChatBridge.send(message);
    }
}
