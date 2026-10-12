package club.heiqi.uilib.ui.reactive;

import java.util.function.Supplier;

/** 纯派生按需求当前值；首次读取不返回占位，也不推进 Effect。 */
public final class Computed<T> extends Tracker implements ReadableSignal<T>, AutoCloseable {
    private final Supplier<T> derivation;
    private boolean dirty = true;
    private boolean computing;
    private T value;

    Computed(ReactiveDomain domain, Supplier<T> derivation) {
        super(domain);
        this.derivation = derivation;
    }

    @Override
    public T get() {
        checkAlive();
        if (computing) throw new IllegalStateException("派生依赖循环");
        if (dirty) {
            Tracker previous = domain.tracker;
            detachSources();
            domain.tracker = this;
            computing = true;
            domain.computationDepth++;
            try {
                value = derivation.get();
                dirty = false;
            } finally {
                domain.computationDepth--;
                computing = false;
                domain.tracker = previous;
            }
        }
        track();
        return value;
    }

    @Override
    void invalidate() {
        if (!dirty && !disposed) {
            dirty = true;
            invalidateDependents();
        }
    }

    public void dispose() {
        domain.checkWrite();
        if (disposed) return;
        disposed = true;
        detachSources();
        value = null;
        invalidateDependents();
    }

    @Override
    public void close() {
        dispose();
    }
}
