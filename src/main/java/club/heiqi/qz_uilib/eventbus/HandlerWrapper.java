package club.heiqi.qz_uilib.eventbus;

import club.heiqi.qz_uilib.eventbus.api.Event;
import club.heiqi.qz_uilib.eventbus.api.EventHandler;

public class HandlerWrapper implements Comparable<HandlerWrapper> {
    /**事件优先级，值越小优先级越高，默认为 3000*/
    public int priority;
    public EventHandler handler;
    public HandlerWrapper(EventHandler handler, int priority) {
        this.priority = priority;
        this.handler = handler;
    }
    public HandlerWrapper(EventHandler handler) {
        this.handler = handler;
        this.priority = 3000;
    }

    public void handle(Event event) {
        handler.handle(event);
    }

    @Override
    public int compareTo(HandlerWrapper other) {
        return Integer.compare(this.priority, other.priority);
    }
}
