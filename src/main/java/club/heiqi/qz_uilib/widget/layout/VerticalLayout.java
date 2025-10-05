package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.ListWidget;
import club.heiqi.qz_uilib.widget.Widget;

/**
 * 附带最佳高度计算的垂直排布器
 * 行为介绍:
 *      - 垂直按顺序排布内部子元素
 *      - 子元素高度通过最佳高度来排布
 *      - 子元素宽度为本元素的宽度
 */
public class VerticalLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        float allPerfectHeight = getAllPerfectHeight();
        int childCount = curWidget.children.size();
        int perfectElementCount = getPerfectElementCount();
        // 剩余可拉伸元素的平均高度  如果总高-完美高度 > inside2 计算平均高度，否则为零
        float inside = curWidget.insideMargins;
        float inside2 = inside * 2;
        float averageHeight = (curWidget.height - allPerfectHeight) > inside2
                ? (curWidget.height - allPerfectHeight) / (childCount - perfectElementCount)
                : 0;

        // 移动Y指针
        float currentY = curWidget.y;
        for (Widget child : curWidget.children) {

            child.x = curWidget.x + child.localX + inside;
            child.y = currentY + child.localY + inside;

            // 有完美高度时用完美高度
            if (child.perfectHeight != -1) {
                child.height = child.perfectHeight;
                currentY += child.height + inside2;
            } else {
                child.height = Math.max(0, averageHeight - inside2);
                currentY += averageHeight;
            }
            // 设置子组件宽度
            child.width = curWidget.width - inside2;
        }
    }

    private float getAllPerfectHeight() {
        float totalHeight = 0;
        float inside = curWidget.insideMargins;
        float inside2 = inside * 2;

        for (Widget child : curWidget.children) {
            if (child.perfectHeight != -1) {
                totalHeight += child.perfectHeight + inside2;
            }
        }
        return totalHeight;
    }

    private int getPerfectElementCount() {
        int count = 0;
        for (Widget child : curWidget.children) {
            if (child.perfectHeight != -1) {
                count++;
            }
        }
        return count;
    }
}
