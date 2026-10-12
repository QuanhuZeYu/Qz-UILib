package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 生命周期只管理自己的资源；挂载作用域可随逻辑归属转移。 */
public final class Owner implements AutoCloseable {
    private final ReactiveDomain domain;
    private final List<Owner> children = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();
    private Owner parent;
    private boolean disposed;

    Owner(ReactiveDomain domain) {
        this.domain = domain;
    }

    public Owner child() {
        checkAlive();
        Owner child = new Owner(domain);
        child.moveTo(this);
        return child;
    }

    public void own(AutoCloseable resource) {
        checkAlive();
        resources.add(Objects.requireNonNull(resource, "resource"));
    }

    public void moveTo(Owner next) {
        checkAlive();
        if (next != null) {
            next.checkAlive();
            if (next.domain != domain) throw new IllegalArgumentException("作用域不可跨域转移");
            for (Owner cursor = next; cursor != null; cursor = cursor.parent)
                if (cursor == this) throw new IllegalArgumentException("作用域不能包含自身");
        }
        if (parent != null) parent.children.remove(this);
        parent = next;
        if (next != null) next.children.add(this);
    }

    public boolean isDisposed() {
        domain.checkAccess();
        return disposed;
    }

    private void checkAlive() {
        domain.checkAccess();
        if (disposed) throw new IllegalStateException("作用域已释放");
    }

    public void dispose() {
        domain.checkAccess();
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        for (Owner child : new ArrayList<>(children)) {
            try {
                child.dispose();
            } catch (Throwable e) {
                if (failure == null) failure = e;
                else if (e != failure) failure.addSuppressed(e);
            }
        }
        children.clear();
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Throwable e) {
                if (failure == null) failure = e;
                else if (e != failure) failure.addSuppressed(e);
            }
        }
        resources.clear();
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("作用域释放失败", failure);
    }

    @Override
    public void close() {
        dispose();
    }
}
