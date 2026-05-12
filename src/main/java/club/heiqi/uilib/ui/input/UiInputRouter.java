package club.heiqi.uilib.ui.input;

import java.util.ArrayList;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
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

    /**
     * 清空当前悬停、按压与焦点状态。
     */
    public void clearInteractionState() {
        reset();
    }

    /**
     * 判断当前是否仍有有效焦点组件。
     *
     * @return 是否存在焦点组件
     */
    public boolean hasFocusedWidget() {
        return isFocusedWidgetInputActive(focusedWidget);
    }

    private void routeMouseEvent(Widget root, UiMouseEvent event) {
        Widget target = root.findWidgetAt(event.getMouseX(), event.getMouseY());
        updateHoveredWidget(target);

        switch (event.getAction()) {
            case MOVE:
                Widget moveTarget = pressedWidget != null ? pressedWidget : target;
                if (moveTarget != null) {
                    moveTarget.onMouseMove(event);
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
        if (event.getAction() == UiKeyEvent.Action.PRESSED && event.getKeyCode() == Keyboard.KEY_TAB) {
            Widget target = getActiveFocusedWidget(root);
            if (target != null && target.onFocusTraversal(event.isShiftPressed())) {
                return;
            }
            focusNextWidget(root, event.isShiftPressed());
            return;
        }

        Widget target = getActiveFocusedWidget(root);
        if (target == null) {
            target = root;
        }
        if (target != null) {
            target.onKeyEvent(event);
        }
    }

    private void routeTextEvent(Widget root, UiTextInputEvent event) {
        Widget target = getActiveFocusedWidget(root);
        if (target == null) {
            target = root;
        }
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
        setFocusedWidget(widget, false, false);
    }

    private void setFocusedWidgetFromTraversal(Widget widget, boolean reverse) {
        setFocusedWidget(widget, true, reverse);
    }

    private void setFocusedWidget(Widget widget, boolean fromTraversal, boolean reverse) {
        if (focusedWidget == widget) {
            if (fromTraversal && focusedWidget != null) {
                focusedWidget.onFocusTraversalEntered(reverse);
            }
            return;
        }
        if (focusedWidget != null) {
            focusedWidget.onFocusChanged(false);
        }
        focusedWidget = widget;
        if (focusedWidget != null) {
            focusedWidget.onFocusChanged(true);
            if (fromTraversal) {
                focusedWidget.onFocusTraversalEntered(reverse);
            }
        }
    }

    private void dispatchScrollEvent(Widget target, UiMouseEvent event) {
        Widget current = target;
        while (current != null) {
            if (current.onMouseScroll(event)) {
                return;
            }
            current = current.getParent();
        }
    }

    /**
     * 仅在焦点组件仍属于当前可交互树时才保留焦点，避免切页后继续把输入发送到隐藏控件。
     *
     * @param root 当前界面根组件
     * @return 有效焦点组件；失效时返回 null
     */
    private Widget getActiveFocusedWidget(Widget root) {
        if (!isWidgetActiveInTree(root, focusedWidget) || !isFocusedWidgetInputActive(focusedWidget)) {
            setFocusedWidget(null);
        }
        return focusedWidget;
    }

    private boolean isFocusedWidgetInputActive(Widget widget) {
        if (widget == null) {
            return false;
        }
        if (widget instanceof HtmlLikeDocumentWidget) {
            return ((HtmlLikeDocumentWidget) widget).getFocusedElement() != null;
        }
        return true;
    }

    private boolean isWidgetActiveInTree(Widget root, Widget widget) {
        if (root == null || widget == null) {
            return false;
        }

        Widget current = widget;
        while (current != null) {
            if (!current.isVisible() || !current.isEnabled()) {
                return false;
            }
            if (current == root) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void focusNextWidget(Widget root, boolean reverse) {
        List<Widget> focusableWidgets = new ArrayList<Widget>();
        collectFocusableWidgets(root, focusableWidgets);
        if (focusableWidgets.isEmpty()) {
            setFocusedWidget(null);
            return;
        }

        int currentIndex = focusedWidget == null ? -1 : focusableWidgets.indexOf(focusedWidget);
        int nextIndex;
        if (reverse) {
            nextIndex = currentIndex <= 0 ? focusableWidgets.size() - 1 : currentIndex - 1;
        } else {
            nextIndex = currentIndex < 0 || currentIndex >= focusableWidgets.size() - 1 ? 0 : currentIndex + 1;
        }
        setFocusedWidgetFromTraversal(focusableWidgets.get(nextIndex), reverse);
    }

    private void collectFocusableWidgets(Widget widget, List<Widget> focusableWidgets) {
        if (widget == null || !widget.isVisible() || !widget.isEnabled()) {
            return;
        }
        if (widget.isFocusable() && widget.getWidth() > 0 && widget.getHeight() > 0) {
            focusableWidgets.add(widget);
        }
        for (Widget child : widget.getChildren()) {
            collectFocusableWidgets(child, focusableWidgets);
        }
    }
}
