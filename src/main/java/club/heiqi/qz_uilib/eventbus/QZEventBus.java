package club.heiqi.qz_uilib.eventbus;


import club.heiqi.qz_uilib.eventbus.api.Event;
import club.heiqi.qz_uilib.eventbus.api.EventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自定义事件总线
 */
public class QZEventBus {
    public static Logger LOG = LogManager.getLogger("QZEventBus");
    public static QZEventBus INSTANCE;
    public static QZEventBus getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new QZEventBus();
        }
        return INSTANCE;
    }

    public ConcurrentHashMap<Class<? extends Event>, List<HandlerWrapper>> eventHandlerMap = new ConcurrentHashMap<>();

    /**
     * 注册事件
     * @param event     需要订阅的事件类型
     * @param handler   事件处理器
     */
    public void register(Class<? extends Event> event, EventHandler handler) {
        HandlerWrapper wrapper = new HandlerWrapper(handler);

        List<HandlerWrapper> handlerWrappers = eventHandlerMap.computeIfAbsent(
                event,
                // CopyOnWriteArrayList 保证了线程安全，特别是对于读取操作。
                k -> new CopyOnWriteArrayList<>()
        );

        int insertIndex = handlerWrappers.size(); // 默认插入到末尾

        // 假设优先级数字越小，越优先（希望高优先级的Handler排在前面）
        for (int i = 0; i < handlerWrappers.size(); i++) {
            HandlerWrapper handlerWrapper = handlerWrappers.get(i);

            // 找到第一个优先级比新处理器低的现有处理器
            if (handlerWrapper.priority > wrapper.priority) {
                insertIndex = i;
                break; // 找到位置后立即退出循环
            }
        }

        // 由于 CopyOnWriteArrayList 的 add(index, element) 操作是线程安全的
        // 且会创建新的底层数组，此操作是安全的。
        handlerWrappers.add(insertIndex, wrapper);
    }

    public void register(Class<? extends Event> event, HandlerWrapper wrapper) {
        // 使用 computeIfAbsent 确保线程安全地获取或创建列表
        List<HandlerWrapper> handlerWrappers = eventHandlerMap.computeIfAbsent(
                event,
                k -> new CopyOnWriteArrayList<>() // 保证列表本身的线程安全
        );

        // 目标：优先级数字越小，越靠前执行。
        int insertIndex = handlerWrappers.size(); // 默认插入到末尾

        // 遍历找到第一个优先级比新处理器低的现有处理器
        for (int i = 0; i < handlerWrappers.size(); i++) {
            HandlerWrapper handlerWrapper = handlerWrappers.get(i);

            // 如果找到一个优先级比新 wrapper 低的，则应该插在这个 handlerWrapper 之前
            if (handlerWrapper.priority > wrapper.priority) {
                insertIndex = i;
                break; // 找到位置后立即退出循环
            }
        }

        // 插入到正确的位置
        handlerWrappers.add(insertIndex, wrapper);
    }

    /**
     * 发布事件，通知订阅了该事件的处理器
     * @param event 发布的事件
     */
    public void post(Event event) {
        List<HandlerWrapper> eventHandlers = eventHandlerMap.get(event.getClass());
        if (eventHandlers != null) {
            for (HandlerWrapper eventHandler : eventHandlers) {
                if (!event.isCancelled) {
                    try {
                        eventHandler.handle(event);
                    } catch (Exception e) {
                        LOG.error("事件处理器 {} 执行异常: {}", eventHandler.getClass().getName(), e);
                    }
                }
            }
        }
    }

    /**
     * 取消注册事件
     * @param event     需要取消订阅的事件类型
     * @param handler   事件处理器
     */
    public void unregister(Class<? extends Event> event, EventHandler handler) {
        List<HandlerWrapper> eventHandlers = eventHandlerMap.get(event);
        HandlerWrapper toRemove = null;

        if (eventHandlers != null) {
            // 遍历查找对应的 HandlerWrapper
            for (HandlerWrapper wrapper : eventHandlers) {
                if (wrapper.handler == handler) {
                    toRemove = wrapper;
                    break;
                }
            }

            if (toRemove != null) {
                // CopyOnWriteArrayList 的 remove(Object) 是线程安全的
                eventHandlers.remove(toRemove);

                // 优化：如果列表为空，则从 HashMap 中移除该 Key，释放内存
                if (eventHandlers.isEmpty()) {
                    // 只有当 event 关联的值仍是这个 eventHandlers 列表时才移除
                    eventHandlerMap.remove(event, eventHandlers);
                }
            }
        }
    }

    public void unregister(Class<? extends Event> event, HandlerWrapper wrapper) {
        List<HandlerWrapper> eventHandlers = eventHandlerMap.get(event);

        if (eventHandlers != null) {
            // CopyOnWriteArrayList 的 remove(Object) 方法是线程安全的
            boolean removed = eventHandlers.remove(wrapper);
            if (removed) {
                LOG.info("成功取消注册事件: {} 的处理器: {}", event.getName(), wrapper.handler.getClass().getName());
            }

            // 考虑如果列表为空，是否要从 ConcurrentHashMap 中移除
            if (eventHandlers.isEmpty()) {
                // 使用 remove 检查确保只有当前列表为空时才移除，防止竞态条件。
                eventHandlerMap.remove(event, eventHandlers);
            }
        }
    }
}
