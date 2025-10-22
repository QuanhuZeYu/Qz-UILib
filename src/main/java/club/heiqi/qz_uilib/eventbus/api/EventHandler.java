package club.heiqi.qz_uilib.eventbus.api;

@FunctionalInterface
public interface EventHandler {
    void handle(Event event);
}
