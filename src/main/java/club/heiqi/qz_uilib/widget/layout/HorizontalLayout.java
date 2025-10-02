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
        float averageWidth = (curWidget.width - allPerfectWidth) / (childCount - perfectElementCount);

        float insideMargins = curWidget.insideMargins;
        float insideMargins2 = insideMargins * 2;

        // 如果父组件当前宽度足够放下所有完美宽度
        if (allPerfectWidth <= curWidget.width) {
            // 移动X指针
            float currentWidth = curWidget.x;
            for (Widget child : curWidget.children) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float offset = insideMargins + outMargins;
                float marginSize = insideMargins2 + outMargins2;

                child.x = currentWidth + offset;
                child.y = curWidget.y + offset;
                if (child.perfectWidth != -1) {
                    child.width = child.perfectWidth;
                    currentWidth += child.width + marginSize;
                } else {
                    child.width = averageWidth - marginSize;
                    currentWidth += child.width + marginSize;
                }
                child.height = curWidget.height - marginSize;
            }
        }
        // 无法容纳所有完美大小 退化到全部平均布局
        else {
            averageWidth = curWidget.width / childCount;

            float currentWidth = curWidget.x;
            for (Widget child : curWidget.children) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float offset = insideMargins + outMargins;
                float marginSize = insideMargins2 + outMargins2;

                child.x = currentWidth + offset;
                child.y = curWidget.y + offset;
                child.width = averageWidth - marginSize;
                child.height = curWidget.height - marginSize;
                currentWidth += child.width + marginSize;
            }
        }
    }

    private float getAllPerfectWidth() {
        float totalWidth = 0;
        float insideMargins = curWidget.insideMargins;
        float insideMargins2 = insideMargins * 2;

        for (Widget child : curWidget.children) {
            if (child.perfectWidth != -1) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float marginSize = insideMargins2 + outMargins2;
                totalWidth += child.perfectWidth + marginSize;
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
