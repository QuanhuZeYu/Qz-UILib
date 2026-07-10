package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量配置事件总线，复用现有 {@link ConfigChangeEvent} / {@link ConfigChangeListener}。
 *
 * <p>发布订阅模型，监听器列表用 {@link CopyOnWriteArrayList} 保证遍历安全。
 * 单个监听器抛 {@link RuntimeException} 或非致命 {@link AssertionError} 不会中断后续通知；
 * {@link VirtualMachineError}、{@link ThreadDeath} 与 {@link LinkageError} 不会被吞掉。</p>
 *
 * <p>{@link #publish(ConfigChangeEvent)} 为包级私有，仅 {@link ConfigManager} 在保存事务
 * 完成后发布 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 事件。</p>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class ConfigEventBus {

    private final CopyOnWriteArrayList<ConfigChangeListener> listeners = new CopyOnWriteArrayList<ConfigChangeListener>();

    /**
     * 订阅配置变更事件。
     *
     * @param listener 监听器，null 被忽略
     */
    public void subscribe(ConfigChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 取消订阅。
     *
     * @param listener 监听器
     */
    public void unsubscribe(ConfigChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 发布事件，包级私有。仅 {@link ConfigManager} 调用。
     *
     * @param event 变更事件
     */
    void publish(ConfigChangeEvent event) {
        if (event == null) {
            return;
        }
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(event);
            } catch (RuntimeException e) {
                // 业务监听器运行时异常隔离，不影响其他监听器
                e.printStackTrace();
            } catch (AssertionError e) {
                // 测试/断言类非致命错误同样隔离；其他 Error 必须继续传播
                e.printStackTrace();
            }
        }
    }
}
