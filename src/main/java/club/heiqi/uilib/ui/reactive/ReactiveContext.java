package club.heiqi.uilib.ui.reactive;

/**
 * 追踪当前正在执行的 Effect，实现 Signal.get() 的自动依赖注册。
 */
final class ReactiveContext {
    private static final ThreadLocal<Effect> CURRENT = new ThreadLocal<>();

    static Effect getCurrent() { return CURRENT.get(); }

    /** 设置当前 effect，返回上一个（用于嵌套恢复）。 */
    static Effect setCurrent(Effect e) {
        Effect prev = CURRENT.get();
        if (e == null) CURRENT.remove(); else CURRENT.set(e);
        return prev;
    }

    private ReactiveContext() {}
}
