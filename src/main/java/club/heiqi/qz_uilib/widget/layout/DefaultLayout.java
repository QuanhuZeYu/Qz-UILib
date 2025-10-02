package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.widget.Widget;
import org.joml.Vector2f;

/**
 * 默认布局策略
 * 绝对坐标直接布局
 */
public class DefaultLayout {
    public Widget curWidget;

    /**
     * 父级默认行为只调整子元素大小
     */
    public void applyLayout(Widget widget) {
        this.curWidget = widget;
        checkAndSetSize();
    }

    private void checkAndSetSize() {
        for (Widget child : curWidget.children) {
            Vector2f parentSize = child.getParentSize();
            // 检查最大大小
            if (child.width > parentSize.x - child.outMargins * 2) {
                child.width = parentSize.x - child.outMargins * 2;
            }
            if (child.height > parentSize.y - child.outMargins * 2) {
                child.height = parentSize.y - child.outMargins * 2;
            }
            // 检查最小大小
            if (child.width < child.miniumWidth + child.insideMargins * 2) {
                child.width = child.miniumWidth + child.insideMargins * 2;
            }
            if (child.height < child.miniumHeight + child.insideMargins * 2) {
                child.height = child.miniumHeight + child.insideMargins * 2;
            }
        }
    }
}
