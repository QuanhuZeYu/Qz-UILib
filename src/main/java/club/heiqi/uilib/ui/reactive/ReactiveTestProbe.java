package club.heiqi.uilib.ui.reactive;

/**
 * 响应式系统测试探针 —— 暴露内部计数供测试断言 Owner/effect 回收是否泄漏。
 *
 * <p>本类仅用于单元测试与回归断言，<b>不用于业务代码</b>。业务不应依赖调度器内部计数；
 * 本探针只是把 {@link ReactiveScheduler} 的 package-private 计数方法转发给跨包测试使用。</p>
 *
 * <p>典型用法（断言控件卸载/行删除后 effect 回收）：</p>
 * <pre>{@code
 * int before = ReactiveTestProbe.registeredEffectCount();
 * // 触发删除行 / 折叠节点 / dispose
 * runtime.flush();
 * int after = ReactiveTestProbe.registeredEffectCount();
 * Assert.assertTrue("effect 应回收", after < before);
 * }</pre>
 */
public final class ReactiveTestProbe {

    private ReactiveTestProbe() {}

    /**
     * 当前已注册（未 dispose）的 effect 数量。
     *
     * @return 当前注册的 effect 数
     */
    public static int registeredEffectCount() {
        return ReactiveScheduler.get().registeredEffectCount();
    }
}
