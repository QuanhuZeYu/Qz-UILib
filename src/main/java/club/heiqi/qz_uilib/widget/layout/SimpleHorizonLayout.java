package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

/**
 * 简易水平布局器
 * 行为介绍:
 *      - 水平按顺序排布内部子元素
 *      - 不调整子元素的宽度值直接使用本身的宽度
 *      - 子元素高度值为本元素高度
 */
public class SimpleHorizonLayout extends DefaultLayout {


    @Override
    public void applyLayout(Widget widget) {
        this.curWidget = widget;

        float insideMargin = curWidget.insideMargins;
        float inside2 = insideMargin * 2;

        float currentX = curWidget.x;  // 横坐标起始值
        for (Widget child : curWidget.children) {
            // 计算出偏移后的绝对坐标
            child.x = currentX + insideMargin + child.localX;
            child.y = curWidget.y + insideMargin + child.localY;

            // 子元素高度值为本元素高度
            child.height = curWidget.height - inside2;

            // 递增高度值
            currentX += child.height + inside2;
        }
    }
}
