package club.heiqi.qz_uilib.eventbus.api;

public abstract class Event {
    public boolean isCancelled = false;
    public void cancel() {
        isCancelled = true;
    }
}
