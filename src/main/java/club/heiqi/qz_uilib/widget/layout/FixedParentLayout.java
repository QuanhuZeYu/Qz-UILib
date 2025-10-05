package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;
import org.lwjgl.opengl.Display;

/**
 * 自适应子组件大小到自身大小内 并同时设置子元素的最佳大小
 */
public class FixedParentLayout extends DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        for (Widget child : curWidget.children) {
            child.width = curWidget.width;
            child.height = curWidget.height;
            child.setPerfectSize(child.width, child.height);

            child.x = child.localX + curWidget.x;
            child.y = child.localY + curWidget.y;
        }
    }
}
