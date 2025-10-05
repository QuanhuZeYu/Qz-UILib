package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

/**相对坐标布局器*/
public class RelativeCoordinateLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        // 根据自身的绝对坐标 和子组件的相对坐标 调整子组件的绝对坐标
        for (Widget child : curWidget.children) {
            // 子组件绝对坐标 = 父组件绝对坐标 + 父组件内边距 + 子组件相对坐标
            child.x = curWidget.x + curWidget.insideMargins + child.localX;
            child.y = curWidget.y + curWidget.insideMargins + child.localY;
        }
    }
}
