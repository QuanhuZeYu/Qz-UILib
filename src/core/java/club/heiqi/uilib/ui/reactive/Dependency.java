package club.heiqi.uilib.ui.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/** 内部依赖边；先完整传播失效，观察通知由状态域在失效边界结束后统一同步调用。 */
abstract class Dependency {
    final ReactiveDomain domain;
    private final Set<Tracker> dependents = new LinkedHashSet<>();

    Dependency(ReactiveDomain domain) {
        this.domain = domain;
    }

    final void track() {
        domain.checkAccess();
        if (domain.tracker != null) {
            dependents.add(domain.tracker);
            domain.tracker.sources.add(this);
        }
    }

    final void remove(Tracker tracker) {
        dependents.remove(tracker);
    }

    final void invalidateDependents() {
        domain.invalidate(() -> {
            for (Tracker tracker : new LinkedHashSet<>(dependents)) tracker.invalidate();
        });
    }
}

abstract class Tracker extends Dependency {
    final Set<Dependency> sources = new LinkedHashSet<>();
    boolean disposed;

    Tracker(ReactiveDomain domain) {
        super(domain);
    }

    final void detachSources() {
        for (Dependency source : sources) source.remove(this);
        sources.clear();
    }

    final void checkAlive() {
        domain.checkAccess();
        if (disposed) throw new IllegalStateException("响应对象已释放");
    }

    abstract void invalidate();
}
