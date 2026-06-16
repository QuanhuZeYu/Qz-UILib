package club.heiqi.uilib.ui.reactive;

/**
 * 可读响应式源：在响应式上下文中 {@link #get()} 会自动建立订阅（依赖追踪）。
 *
 * <p>{@link Signal}（可写原子）与 {@link Computed}（只读派生）都实现本接口，
 * 使两者都能作为响应式数据源喂给依赖追踪的消费方（如 effect 绑定）。</p>
 *
 * @param <T> 值类型
 */
public interface ReadableSignal<T> {

    /**
     * 读取当前值；若处于响应式上下文中则自动建立订阅。
     *
     * @return 当前值
     */
    T get();
}
