package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 场景节点交互状态容器 —— I3 交互状态机。
 *
 * <h3>核心职责</h3>
 * <p>作为 SceneNode 的交互状态外挂容器，持有 hover/focus/press 三个可选 signal。
 * 采用懒创建 + null 短路策略，确保未声明关心的节点零额外开销。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>懒创建</b>：signal 仅在首次调用 {@link #hovered()}/{@link #focused()}/{@link #pressed()} 时创建，
 *       未调用则保持 null，写入时短路跳过。</li>
 *   <li><b>只读暴露</b>：对外通过 {@link ReadableSignal} 返回，防止外部写入。</li>
 *   <li><b>包级写入</b>：{@code writeXxx} 仅 {@link SceneInputRouter} 调用，含 null 短路保护。</li>
 *   <li><b>外挂生命周期</b>：由 {@link SceneInputRouter#interactionStates} Map 持有，
 *       随 Owner onCleanup 自动移除，与 SceneNode 自身零耦合。</li>
 * </ul>
 */
public final class SceneInteractionState {

    /** hover 状态 signal，默认 null = 未声明关心 */
    private Signal<Boolean> hovered;

    /** focus 状态 signal，I4 才真正写，I3 占位暴露 */
    private Signal<Boolean> focused;

    /** press 状态 signal，默认 null = 未声明关心 */
    private Signal<Boolean> pressed;

    /** 包级构造，仅 SceneInputRouter 通过 interactionState() 创建 */
    SceneInteractionState() {
    }

    /**
     * 获取 hover 状态的只读 signal。
     *
     * <p>首次调用时懒创建初始值为 {@code Boolean.FALSE} 的 signal，
     * 后续调用返回同一实例（幂等）。</p>
     *
     * @return hover 状态只读 signal
     */
    public ReadableSignal<Boolean> hovered() {
        if (hovered == null) {
            hovered = Signal.create(Boolean.FALSE);
        }
        return hovered;
    }

    /**
     * 获取 focus 状态的只读 signal（I4 占位）。
     *
     * @return focus 状态只读 signal
     */
    public ReadableSignal<Boolean> focused() {
        if (focused == null) {
            focused = Signal.create(Boolean.FALSE);
        }
        return focused;
    }

    /**
     * 获取 press 状态的只读 signal。
     *
     * @return press 状态只读 signal
     */
    public ReadableSignal<Boolean> pressed() {
        if (pressed == null) {
            pressed = Signal.create(Boolean.FALSE);
        }
        return pressed;
    }

    // ==================== 包级写入（仅 Router 调） ====================

    /**
     * 写入 hover 状态。
     *
     * <p><b>★null 短路硬保证</b>：若 hovered signal 从未被创建（无人声明关心），
     * 则直接返回，零开销跳过。绝不因写入而创建 signal。</p>
     *
     * @param v 新值
     */
    void writeHovered(boolean v) {
        if (hovered != null) {
            hovered.set(v);
        }
    }

    /**
     * 写入 press 状态。
     *
     * <p><b>★null 短路硬保证</b>：同 writeHovered。</p>
     *
     * @param v 新值
     */
    void writePressed(boolean v) {
        if (pressed != null) {
            pressed.set(v);
        }
    }

    /**
     * 写入 focus 状态（I4 占位）。
     *
     * <p><b>★null 短路硬保证</b>：同 writeHovered。</p>
     *
     * @param v 新值
     */
    void writeFocused(boolean v) {
        if (focused != null) {
            focused.set(v);
        }
    }

    // ==================== 测试探针（包级可见性） ====================

    /** @return hovered signal 是否已创建 */
    boolean __hasHoveredSignal() {
        return hovered != null;
    }

    /** @return pressed signal 是否已创建 */
    boolean __hasPressedSignal() {
        return pressed != null;
    }

    /** @return focused signal 是否已创建 */
    boolean __hasFocusedSignal() {
        return focused != null;
    }
}
