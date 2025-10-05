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

    /**无任何行为*/
    public void applyLayout(Widget widget) {
        this.curWidget = widget;
    }
}
