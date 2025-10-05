package club.heiqi.qz_uilib.widget.layout;


import club.heiqi.qz_uilib.widget.Widget;
import org.lwjgl.opengl.Display;

/**
 * 简易垂直布局器
 * 行为介绍:
 *      - 垂直按顺序排布内部子元素
 *      - 不调整子元素的高度值直接使用本身的高度值
 *      - 子元素宽度为本元素的宽度
 */
public class SimpleVerticalLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        this.curWidget = widget;

        float insideMargin = curWidget.insideMargins;
        float inside2 = insideMargin * 2;

        float currentY = curWidget.y;  // 高度坐标起始值
        for (Widget child : curWidget.children) {
            // 计算出偏移后的绝对坐标
            child.x = curWidget.x + insideMargin + child.localX;
            child.y = currentY + insideMargin + child.localY;

            // 子元素宽度为本元素的宽度
            child.width = curWidget.width - inside2;

            // 递增高度值
            currentY += child.height + inside2;
        }
    }
}
