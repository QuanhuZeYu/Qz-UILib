package club.heiqi.uilib.ui.control;

import java.util.Objects;
import java.util.function.Consumer;

import club.heiqi.uilib.ui.component.UiComponentRuntime;

/**
 * 把控件事件桥接到响应式 {@link UiComponentRuntime#on(Runnable, Runnable) runtime.on}（输入半环：控件事件 → signal）。
 *
 * <p><b>分层定位</b>：本类居于控件层（{@code ui.control}），<b>依赖组件层</b>（{@code ui.component}）。
 * 这是一条<b>新引入的单向依赖（control → component）</b>，无环——因为 {@link UiComponentRuntime}（组件层）
 * 受 I6 约束<b>绝不能 import {@code ui.control}</b>，故控件便利糖不能放在 runtime 内，而落在控件层的本 bridge 类。
 * 控件层依赖组件层与「控件是 DOM 适配器、单向依赖 {@code ui.dom}」并不冲突：bridge 只是把控件的命令式
 * setXxxHandler 包装进 runtime 的 {@code on}，使其退订随 Owner 作用域自动发生。</p>
 *
 * <p><b>纪律（信条一/I1）</b>：传入的 {@code action}/{@code consumer} 应改 signal 驱动界面变化，不得命令式操作节点。</p>
 */
public final class ReactiveControlBindings {

    private ReactiveControlBindings() {
    }

    /**
     * 把按钮的 action 事件桥接到 {@code action}（输入半环）。建立时通过 {@code runtime.on} 注册
     * {@link DocumentButtonControl#setActionHandler(DocumentButtonActionHandler)}，作用域卸载（或
     * {@link UiComponentRuntime.Binding#dispose()}）时自动 {@code setActionHandler(null)} 退订。
     *
     * <p>若在 {@code forEach}/{@code show} 的项/内容作用域内调用，绑定归属该作用域：项被移除时
     * 该行 owner dispose 会自动清空按钮的 actionHandler，根除潜在悬挂监听器。</p>
     *
     * @param runtime 组件运行时（提供 {@code on}）
     * @param button  目标按钮控件
     * @param action  按钮触发动作时执行的回调（应改 signal）
     * @return 绑定句柄，可单独 {@link UiComponentRuntime.Binding#dispose()} 提前退订
     */
    public static UiComponentRuntime.Binding onAction(
            UiComponentRuntime runtime, DocumentButtonControl button, Runnable action) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(action, "action");
        return runtime.on(
                () -> button.setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        action.run();
                    }
                }),
                () -> button.setActionHandler(null));
    }

    /**
     * 把开关的变更事件桥接到 {@code consumer}（输入半环）：变更时把<b>新开关态</b>
     * （{@link DocumentToggleSwitchControl#isToggled()}）喂给 {@code consumer}（其内应 {@code signal.set(toggled)}）。
     * 建立时通过 {@code runtime.on} 注册
     * {@link DocumentToggleSwitchControl#setChangeHandler(DocumentToggleChangeHandler)}，作用域卸载（或
     * {@link UiComponentRuntime.Binding#dispose()}）时自动 {@code setChangeHandler(null)} 退订。
     *
     * @param runtime  组件运行时（提供 {@code on}）
     * @param toggle   目标开关控件
     * @param consumer 接收新开关态的回调（应 {@code signal.set(toggled)}）
     * @return 绑定句柄，可单独 {@link UiComponentRuntime.Binding#dispose()} 提前退订
     */
    public static UiComponentRuntime.Binding onToggle(
            UiComponentRuntime runtime, DocumentToggleSwitchControl toggle,
            Consumer<Boolean> consumer) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(toggle, "toggle");
        Objects.requireNonNull(consumer, "consumer");
        return runtime.on(
                () -> toggle.setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        consumer.accept(Boolean.valueOf(toggle.isToggled()));
                    }
                }),
                () -> toggle.setChangeHandler(null));
    }
}
