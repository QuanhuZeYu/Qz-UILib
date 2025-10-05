package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

/**
 * 使自身大小自适应内容的布局器
 *      行为介绍:
 *          - 遍历所有子元素获取最大子元素的尺寸
 *          - 将自身的最佳大小和大小设置为最大尺寸
 */
public class FixedContentLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        float maxWidth = 0, maxHeight = 0;
        for (Widget child : widget.children) {
            maxWidth = Math.max(maxWidth, child.perfectWidth);
            maxHeight = Math.max(maxHeight, child.perfectHeight);
        }
        widget.setPerfectSize(maxWidth, maxHeight);
    }
}
