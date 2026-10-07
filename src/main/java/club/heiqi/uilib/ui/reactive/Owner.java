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
    Owner(ReactiveDomain domain) { this.domain = domain; }
    public Owner child() { checkAlive(); Owner child = new Owner(domain); child.moveTo(this); return child; }
    public void own(AutoCloseable resource) { checkAlive(); resources.add(Objects.requireNonNull(resource, "resource")); }
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
    public boolean isDisposed() { domain.checkAccess(); return disposed; }
    private void checkAlive() { domain.checkAccess(); if (disposed) throw new IllegalStateException("作用域已释放"); }
    public void dispose() {
        domain.checkAccess();
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        for (Owner child : new ArrayList<>(children)) {
            try { child.dispose(); } catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        children.clear();
        for (int i = resources.size() - 1; i >= 0; i--) {
            try { resources.get(i).close(); }
            catch (Exception e) { if (failure == null) failure = new IllegalStateException("作用域释放失败", e); else failure.addSuppressed(e); }
        }
        resources.clear();
        if (parent != null) { parent.children.remove(this); parent = null; }
        if (failure != null) throw failure;
    }
    @Override public void close() { dispose(); }
}
