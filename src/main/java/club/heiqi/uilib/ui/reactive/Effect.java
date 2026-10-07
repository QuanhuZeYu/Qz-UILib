package club.heiqi.uilib.ui.reactive;
/** 显式副作用在域操作结束后执行，回调内写入不递归重入。 */
public final class Effect extends Tracker implements AutoCloseable {
    private final Runnable action;
    boolean queued;
    Effect(ReactiveDomain domain, Runnable action) { super(domain); this.action = action; }
    void run() {
        checkAlive();
        Tracker previous = domain.tracker;
        detachSources(); domain.tracker = this;
        try { action.run(); } finally { domain.tracker = previous; }
    }
    @Override void invalidate() { if (!disposed) domain.enqueue(this); }
    public void dispose() { domain.checkAccess(); if (!disposed) { disposed = true; detachSources(); } }
    @Override public void close() { dispose(); }
}
