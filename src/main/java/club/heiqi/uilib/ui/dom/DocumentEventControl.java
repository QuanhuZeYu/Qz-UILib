package club.heiqi.uilib.ui.dom;

/**
 * DOM 事件传播控制器。
 *
 * <p>提供 {@code stopPropagation()}、{@code stopImmediatePropagation()} 和 {@code preventDefault()} 的共享实现。
 * 各事件类通过组合方式持有该控制器，避免重复代码。</p>
 *
 * <p>该类为框架内部使用，不直接暴露给页面作者。</p>
 */
public final class DocumentEventControl {

    private DocumentEventPhase phase = DocumentEventPhase.NONE;
    private boolean propagationStopped;
    private boolean immediatePropagationStopped;
    private boolean defaultPrevented;

    /**
     * 返回当前事件传播阶段。
     *
     * @return 事件阶段
     */
    public DocumentEventPhase getEventPhase() {
        return phase;
    }

    /**
     * 设置当前事件传播阶段（框架内部调用）。
     *
     * @param phase 事件阶段
     */
    public void setEventPhase(DocumentEventPhase phase) {
        this.phase = phase == null ? DocumentEventPhase.NONE : phase;
    }

    /**
     * 阻止事件继续向后续元素传播。
     *
     * <p>当前元素上已注册的其他 handler 仍会执行；
     * 若需要同时阻止当前元素上的后续 handler，使用 {@link #stopImmediatePropagation()}。</p>
     */
    public void stopPropagation() {
        propagationStopped = true;
    }

    /**
     * 阻止事件继续传播，并阻止当前元素上的后续 handler 执行。
     */
    public void stopImmediatePropagation() {
        propagationStopped = true;
        immediatePropagationStopped = true;
    }

    /**
     * 阻止事件的默认行为。
     *
     * <p>例如：阻止 button 的默认 click 行为、阻止 input 的默认文本输入等。</p>
     */
    public void preventDefault() {
        defaultPrevented = true;
    }

    /**
     * 判断传播是否已被阻止。
     *
     * @return 是否已阻止传播
     */
    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    /**
     * 判断立即传播是否已被阻止。
     *
     * @return 是否已阻止立即传播
     */
    public boolean isImmediatePropagationStopped() {
        return immediatePropagationStopped;
    }

    /**
     * 判断默认行为是否已被阻止。
     *
     * @return 是否已阻止默认行为
     */
    public boolean isDefaultPrevented() {
        return defaultPrevented;
    }

    /**
     * 重置所有状态（框架内部复用时调用）。
     */
    public void reset() {
        phase = DocumentEventPhase.NONE;
        propagationStopped = false;
        immediatePropagationStopped = false;
        defaultPrevented = false;
    }
}
