package club.heiqi.uilib.ui.dom;

import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * HTML-like 元素键盘按键事件。
 */
public final class DocumentElementKeyEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final UiKeyEvent sourceEvent;

    /**
     * 创建元素键盘按键事件。
     *
     * @param target 当前聚焦元素
     * @param currentTarget 当前冒泡到的元素
     * @param sourceEvent UI 层原始按键事件
     */
    public DocumentElementKeyEvent(ElementNode target, ElementNode currentTarget, UiKeyEvent sourceEvent) {
        this.target = target;
        this.currentTarget = currentTarget;
        this.sourceEvent = sourceEvent;
    }

    /**
     * 返回当前聚焦元素。
     *
     * @return 事件目标元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前冒泡到的元素。
     *
     * @return 当前处理元素
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 返回 UI 层原始按键事件。
     *
     * @return 原始按键事件
     */
    public UiKeyEvent getSourceEvent() {
        return sourceEvent;
    }

    /**
     * 返回 LWJGL2 映射键码。
     *
     * @return 键码
     */
    public int getKeyCode() {
        return sourceEvent.getKeyCode();
    }

    /**
     * 返回按键动作。
     *
     * @return 按键动作
     */
    public UiKeyEvent.Action getAction() {
        return sourceEvent.getAction();
    }

    /**
     * 判断 Ctrl 是否按下。
     *
     * @return 是否按下 Ctrl
     */
    public boolean isControlPressed() {
        return sourceEvent.isControlPressed();
    }

    /**
     * 判断 Shift 是否按下。
     *
     * @return 是否按下 Shift
     */
    public boolean isShiftPressed() {
        return sourceEvent.isShiftPressed();
    }

    /**
     * 判断 Alt 是否按下。
     *
     * @return 是否按下 Alt
     */
    public boolean isAltPressed() {
        return sourceEvent.isAltPressed();
    }

    /**
     * 判断 Super 是否按下。
     *
     * @return 是否按下 Super
     */
    public boolean isSuperPressed() {
        return sourceEvent.isSuperPressed();
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return sourceEvent.getTimeNanos();
    }
}
