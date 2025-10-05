package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

/**水平布局器*/
public class HorizontalLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        float allPerfectWidth = getAllPerfectWidth();
        int childCount = curWidget.children.size();
        int perfectElementCount = getPerfectElementCount();
        // 剩余可拉伸元素的平均宽度
        float inside = curWidget.insideMargins;
        float inside2 = inside * 2;
        float averageWidth = (curWidget.width - allPerfectWidth) > 0
                ? (curWidget.width - allPerfectWidth) / (childCount - perfectElementCount)
                : 0;


        // 移动X指针
        float currentX = curWidget.x;
        for (Widget child : curWidget.children) {

            child.x = currentX + inside + child.localX;
            child.y = curWidget.y + inside + child.localY;
            if (child.perfectWidth != -1) {
                child.width = child.perfectWidth;
                currentX += child.width + inside2;
            } else {
                child.width = Math.max(0, averageWidth - inside2);
                currentX += averageWidth;
            }
            child.height = curWidget.perfectHeight != -1
                    ? curWidget.perfectHeight - inside2
                    : curWidget.height - inside2;
        }
    }

    private float getAllPerfectWidth() {
        float totalWidth = 0;
        float insideMargins = curWidget.insideMargins;
        float insideMargins2 = insideMargins * 2;

        for (Widget child : curWidget.children) {
            if (child.perfectWidth != -1) {
                totalWidth += child.perfectWidth + insideMargins2;
            }
        }
        return totalWidth;
    }

    private int getPerfectElementCount() {
        int count = 0;
        for (Widget child : curWidget.children) {
            if (child.perfectWidth != -1) {
                count++;
            }
        }
        return count;
    }
}
