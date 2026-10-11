package club.heiqi.uilib.ui.reactive;

/** 呈现订阅只登记失效；refresh 由呈现运行时调用，不参与业务 Effect 队列。 */
public final class Observation extends Tracker implements AutoCloseable {
    private final Runnable read;
    private final Runnable changed;
    private boolean dirty = true;

    Observation(ReactiveDomain domain, Runnable read, Runnable changed) {
        super(domain);
        this.read = read;
        this.changed = changed;
    }

    public void refresh() {
        checkAlive();
        if (!dirty) return;
        Tracker previous = domain.tracker;
        detachSources();
        domain.tracker = this;
        try {
            read.run();
            dirty = false;
        } finally {
            domain.tracker = previous;
        }
    }

    @Override
    void invalidate() {
        if (!disposed && !dirty) {
            dirty = true;
            domain.invalidate(this);
        }
    }

    void notifyChanged() {
        if (!disposed) changed.run();
    }

    @Override
    public void close() {
        domain.checkAccess();
        if (!disposed) {
            disposed = true;
            detachSources();
        }
    }
}
