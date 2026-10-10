package club.heiqi.uilib.ui.scene.localization;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 将宿主事实递交现有状态域；独立业务可用自己的Owner，页面关闭不会连带释放它。
 * 所有读取/文案创建/关闭在域内，通知可跨线程递交；关闭后的排队通知不触碰已释放派生。 */
public final class LocalizationBinding implements AutoCloseable {
    private final ReactiveDomain domain;
    private final Owner scope;
    private final Signal<LocalizationSnapshot> snapshot;
    private final Computed<String> language;
    private LocalizationSource.Subscription subscription;
    private volatile boolean closed;

    public LocalizationBinding(ReactiveDomain domain, Owner owner, LocalizationSource source) {
        this.domain = Objects.requireNonNull(domain, "domain"); domain.checkWrite();
        Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(source, "source");
        snapshot = domain.signal(source.current());
        scope = owner.child();
        scope.own(() -> { closed = true; if (subscription != null) subscription.close(); });
        language = domain.computed(scope, () -> snapshot.get().language());
        try {
            subscription = source.subscribe(next -> {
                Objects.requireNonNull(next, "snapshot");
                if (!closed) domain.execute(() -> { if (!closed) snapshot.set(next); });
            });
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public ReadableSignal<LocalizationSnapshot> snapshot() { checkAlive(); return snapshot; }
    public ReadableSignal<String> language() { checkAlive(); return language; }
    /** 固定键和固定参数在声明时复制参数数组，null参数仍遵循Formatter转换规则。 */
    public Computed<String> text(String key, Object... arguments) {
        checkAlive(); LocalizationSnapshot.requireKey(key);
        Object[] copy = Objects.requireNonNull(arguments, "arguments").clone();
        return domain.computed(scope, () -> snapshot.get().format(key, copy));
    }
    /** 响应键及响应参数按声明顺序读取；每次求值创建自己的参数数组。 */
    public Computed<String> text(ReadableSignal<String> key, ReadableSignal<?>... arguments) {
        checkAlive(); Objects.requireNonNull(key, "key");
        ReadableSignal<?>[] sources = Objects.requireNonNull(arguments, "arguments").clone();
        for (ReadableSignal<?> argument : sources) Objects.requireNonNull(argument, "argument source");
        return domain.computed(scope, () -> {
            String currentKey = LocalizationSnapshot.requireKey(key.get());
            Object[] values = new Object[sources.length];
            for (int i = 0; i < sources.length; i++) values[i] = sources[i].get();
            return snapshot.get().format(currentKey, values);
        });
    }
    private void checkAlive() { domain.checkAccess(); if (closed) throw new IllegalStateException("语言绑定已关闭"); }
    @Override public void close() { domain.checkWrite(); if (closed) return; closed = true; scope.dispose(); }
}
