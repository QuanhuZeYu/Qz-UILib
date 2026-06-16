package club.heiqi.uilib.ui.reactive;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 只读派生 signal：值由一个函数从其它 {@link ReadableSignal} 算出，上游变化时自动重算（信条二）。
 *
 * <p>实现为「effect 驱动 + 输出 cell」：内部 recompute effect 在追踪上下文中执行派生函数，
 * 自动订阅其读取的所有上游源；重算结果同步推入内部输出 {@link Signal}（cell）。
 * {@link #get()} 委托给 cell，使下游消费方（effect / 另一个 computed）能订阅本派生值，
 * 形成 {@code signal → computed → effect} 的链式传播。</p>
 *
 * <p><b>记忆化</b>：只有当派生函数的输出值发生变化（{@link Objects#equals} 判定）时才向下游传播，
 * 上游变化但输出不变时下游不重跑。</p>
 *
 * <p><b>生效时机</b>：与 {@link Effect} 一致，派生值在首次 {@link ReactiveScheduler#flush()} 时
 * 物化；flush 前 {@link #get()} 返回初始占位（{@code null}）。需在 flush 后读取。</p>
 *
 * @param <T> 派生值类型
 */
public final class Computed<T> implements ReadableSignal<T> {

    /** 输出 cell：承载派生结果，复用 Signal 的订阅追踪能力。 */
    private final Signal<T> cell = Signal.create(null);
    /** 重算单元：追踪上游依赖，上游变化时重跑并同步刷新 cell。 */
    private final Effect recompute;

    private Computed(Supplier<T> derivation) {
        Objects.requireNonNull(derivation, "derivation");
        // recompute 在 Computed 构造时注册，先于下游消费方，保证单次 flush 内先重算再被消费。
        this.recompute = Effect.create(() -> {
            T next = derivation.get();               // 追踪上游
            if (!Objects.equals(next, cell.peek())) { // 记忆化：仅输出变化时传播
                cell.applyAndNotify(next);            // 同步推入 cell，标记下游变脏
            }
        });
    }

    /**
     * 创建一个派生值。
     *
     * @param derivation 派生函数，在追踪上下文中执行，读取的上游源自动成为依赖
     * @param <T>        派生值类型
     * @return 只读派生 signal
     */
    public static <T> Computed<T> create(Supplier<T> derivation) {
        return new Computed<>(derivation);
    }

    /**
     * 读取当前派生值；若处于响应式上下文中则自动订阅本派生值。
     *
     * @return 当前派生值（首次 flush 前为 {@code null}）
     */
    @Override
    public T get() {
        return cell.get();
    }

    /**
     * 释放：注销内部 recompute effect，后续不再重算与传播。
     */
    public void dispose() {
        recompute.dispose();
    }
}
