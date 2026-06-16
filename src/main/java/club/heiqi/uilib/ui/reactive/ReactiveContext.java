package club.heiqi.uilib.ui.reactive;

/**
 * 追踪当前响应式执行上下文：
 * <ul>
 *   <li>当前正在执行的 {@link Effect}——实现 {@link Signal#get()} 的自动依赖注册；</li>
 *   <li>当前激活的 {@link Owner} 作用域——实现新建 effect 的自动归属（信条三：组件挂载/卸载）。</li>
 * </ul>
 */
final class ReactiveContext {
    private static final ThreadLocal<Effect> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Owner> CURRENT_OWNER = new ThreadLocal<>();

    static Effect getCurrent() { return CURRENT.get(); }

    /** 设置当前 effect，返回上一个（用于嵌套恢复）。 */
    static Effect setCurrent(Effect e) {
        Effect prev = CURRENT.get();
        if (e == null) CURRENT.remove(); else CURRENT.set(e);
        return prev;
    }

    static Owner getCurrentOwner() { return CURRENT_OWNER.get(); }

    /** 设置当前 owner 作用域，返回上一个（用于嵌套恢复）。 */
    static Owner setCurrentOwner(Owner owner) {
        Owner prev = CURRENT_OWNER.get();
        if (owner == null) CURRENT_OWNER.remove(); else CURRENT_OWNER.set(owner);
        return prev;
    }

    private ReactiveContext() {}
}
