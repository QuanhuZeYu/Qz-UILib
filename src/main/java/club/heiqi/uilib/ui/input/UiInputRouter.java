package club.heiqi.uilib.ui.input;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 将原始输入快照分发到组件树。
 */
public class UiInputRouter {

    private Widget hoveredWidget;
    private Widget pressedWidget;
    private Widget focusedWidget;

    /**
     * 将一帧输入分发到组件树。
     *
     * @param root 根组件
     * @param frame 输入快照
     */
    public void route(Widget root, UiInputFrame frame) {
        if (root == null || frame == null) {
            return;
        }

        for (UiMouseEvent mouseEvent : frame.getMouseEvents()) {
            routeMouseEvent(root, mouseEvent);
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            routeKeyEvent(root, keyEvent);
        }
        for (UiTextInputEvent textEvent : frame.getTextEvents()) {
            routeTextEvent(root, textEvent);
        }
    }

    /**
     * 清空当前交互状态。
     */
    public void reset() {
        if (hoveredWidget != null) {
            hoveredWidget.onMouseLeave();
        }
        if (focusedWidget != null) {
            focusedWidget.onFocusChanged(false);
        }
        hoveredWidget = null;
        pressedWidget = null;
        focusedWidget = null;
    }

    private void routeMouseEvent(Widget root, UiMouseEvent event) {
        Widget target = root.findWidgetAt(event.getMouseX(), event.getMouseY());
        updateHoveredWidget(target);

        switch (event.getAction()) {
            case MOVE:
                if (target != null) {
                    target.onMouseMove(event);
                }
                break;
            case BUTTON_DOWN:
                pressedWidget = target;
                if (target != null) {
                    if (target.isFocusable()) {
                        setFocusedWidget(target);
                    }
                    target.onMouseDown(event);
                } else {
                    setFocusedWidget(null);
                }
                break;
            case BUTTON_UP:
                Widget releaseTarget = pressedWidget != null ? pressedWidget : target;
                if (releaseTarget != null) {
                    releaseTarget.onMouseUp(event);
                }
                pressedWidget = null;
                break;
            case SCROLL:
                dispatchScrollEvent(target, event);
                break;
            default:
                break;
        }
    }

    private void routeKeyEvent(Widget root, UiKeyEvent event) {
        Widget target = focusedWidget != null ? focusedWidget : root;
        if (target != null) {
            target.onKeyEvent(event);
        }
    }

    private void routeTextEvent(Widget root, UiTextInputEvent event) {
        Widget target = focusedWidget != null ? focusedWidget : root;
        if (target != null) {
            target.onTextInput(event);
        }
    }

    private void updateHoveredWidget(Widget target) {
        if (target == hoveredWidget) {
            return;
        }
        if (hoveredWidget != null) {
            hoveredWidget.onMouseLeave();
        }
        hoveredWidget = target;
        if (hoveredWidget != null) {
            hoveredWidget.onMouseEnter();
        }
    }

    private void setFocusedWidget(Widget widget) {
        if (focusedWidget == widget) {
            return;
        }
        if (focusedWidget != null) {
            focusedWidget.onFocusChanged(false);
        }
        focusedWidget = widget;
        if (focusedWidget != null) {
            focusedWidget.onFocusChanged(true);
        }
    }

    private void dispatchScrollEvent(Widget target, UiMouseEvent event) {
        Widget current = target;
        while (current != null) {
            current.onMouseScroll(event);
            current = current.getParent();
        }
    }
}
