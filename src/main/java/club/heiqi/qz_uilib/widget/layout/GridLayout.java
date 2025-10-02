package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

public class GridLayout extends DefaultLayout {
    public int rows, cols;

    public GridLayout(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        // 在网格模式下不使用最佳大小
        float averageWidth = curWidget.width / cols;
        float averageHeight = curWidget.height / rows;
        float currentX = curWidget.x;
        float currentY = curWidget.y;
        int curRow = 0, curCol = 0;
        float insideMargins = curWidget.insideMargins;
        float insideMargins2 = insideMargins * 2;

        for (Widget child : curWidget.children) {
            // 子组件的最佳外边距
            float outMargins = child.outMargins;
            float outMargins2 = outMargins * 2;
            float offset = insideMargins + outMargins;
            float marginsSize = insideMargins2 + outMargins2;
            child.x = currentX + offset;
            child.y = currentY + offset;
            child.width = averageWidth - marginsSize;
            child.height = averageHeight - marginsSize;
            currentX += child.width + marginsSize;

            // 计算完成递增计算下一个
            curCol++;  // 计算完一个递增列
            if (curCol >= cols) {  // 如果当前列不超过每行列数跳过 否则进行换行计算
                curCol = 0;  // 换行时列归零
                curRow++;  // 换行递增行数
                currentX = curWidget.x;  // 计算换行后的当前X绝对坐标
                currentY += averageHeight * curRow;  // 计算换行后的当前Y坐标
            }
        }
    }
}
