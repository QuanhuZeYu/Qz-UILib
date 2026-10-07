package club.heiqi.uilib.ui.reactive;
import java.util.Objects;
/** 写入立即生效；不保留帧末待提交值。 */
public final class Signal<T> extends Dependency implements ReadableSignal<T> {
    private T value;
    Signal(ReactiveDomain domain, T initial) { super(domain); value = initial; }
    @Override public T get() { track(); return value; }
    public void set(T next) {
        domain.checkWrite();
        if (!Objects.equals(value, next)) { value = next; invalidateDependents(); }
    }
    /** 外部可变内容原地更新后的正式通知。 */
    public void changed() { domain.checkWrite(); invalidateDependents(); }
}
