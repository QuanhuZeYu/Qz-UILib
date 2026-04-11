package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.widget.Widget;

/**
 * 可将后代节点滚动到可视区域内的滚动宿主。
 */
public interface UiScrollHost {

    void scrollDescendantIntoView(Widget target);
}
