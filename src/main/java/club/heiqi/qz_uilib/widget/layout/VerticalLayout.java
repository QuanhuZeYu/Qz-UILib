package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.ListWidget;
import club.heiqi.qz_uilib.widget.Widget;

public class VerticalLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        // 列表组件特殊处理
        if (widget instanceof ListWidget listWidget) {
            // 当是列表组件时只调整子元素的宽度
            curWidget = widget;
            float insideMargin = curWidget.insideMargins;
            float insideMargin2 = insideMargin * 2;

            float currentHeight = curWidget.y;
            for (Widget child : curWidget.children) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float marginSize = insideMargin2 + outMargins2;
                float offset = outMargins + insideMargin;

                child.x = curWidget.x + offset;
                child.y = currentHeight + offset + listWidget.offsetY;

                currentHeight += child.height + marginSize;
                child.width = curWidget.width - marginSize;
            }

            return;
        }

        super.applyLayout(widget);

        float allPerfectHeight = getAllPerfectHeight();
        int childCount = curWidget.children.size();
        int perfectElementCount = getPerfectElementCount();
        // 剩余可拉伸元素的平均高度
        float averageHeight = (curWidget.height - allPerfectHeight) / (childCount - perfectElementCount);
        float insideMargin = curWidget.insideMargins;
        float insideMargin2 = insideMargin * 2;

        if (allPerfectHeight <= curWidget.height) {
            // 移动Y指针
            float currentHeight = curWidget.y;
            for (Widget child : curWidget.children) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float marginSize = insideMargin2 + outMargins2;
                float offset = outMargins + insideMargin;

                child.x = curWidget.x + offset;
                child.y = currentHeight + offset;
                if (child.perfectHeight != -1) {
                    child.height = child.perfectHeight;
                    currentHeight += child.height + marginSize;
                } else {
                    child.height = averageHeight - marginSize;
                    currentHeight += child.height + marginSize;
                }
                child.width = curWidget.width - marginSize;
            }
        }
        // 无法容纳所有完美大小 退化到全部平均布局
        else {
            averageHeight = curWidget.height / childCount;
            float currentHeight = curWidget.y;
            for (Widget child : curWidget.children) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float marginSize = insideMargin2 + outMargins2;
                float offset = outMargins + insideMargin;

                child.x = curWidget.x + offset;
                child.y = currentHeight + offset;
                child.height = averageHeight - marginSize;
                child.width = curWidget.width - marginSize;
                currentHeight += child.height + marginSize;
            }
        }
    }

    private float getAllPerfectHeight() {
        float totalHeight = 0;
        float insideMargin = curWidget.insideMargins;
        float insideMargin2 = insideMargin * 2;

        for (Widget child : curWidget.children) {
            if (child.perfectHeight != -1) {
                float outMargins = child.outMargins;
                float outMargins2 = outMargins * 2;
                float marginSize = insideMargin2 + outMargins2;

                totalHeight += child.perfectHeight + marginSize;
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
