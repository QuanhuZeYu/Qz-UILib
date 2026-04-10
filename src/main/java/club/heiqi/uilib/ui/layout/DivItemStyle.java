package club.heiqi.uilib.ui.layout;

/**
 * Div 容器对子项的主轴伸缩策略。
 */
public class DivItemStyle {

    private boolean grow = true;
    private boolean shrink = true;

    public static DivItemStyle flex() {
        return new DivItemStyle();
    }

    public static DivItemStyle fixed() {
        return new DivItemStyle().setGrow(false).setShrink(false);
    }

    public static DivItemStyle noGrow() {
        return new DivItemStyle().setGrow(false);
    }

    public static DivItemStyle noShrink() {
        return new DivItemStyle().setShrink(false);
    }

    public boolean isGrow() {
        return grow;
    }

    public DivItemStyle setGrow(boolean grow) {
        this.grow = grow;
        return this;
    }

    public boolean isShrink() {
        return shrink;
    }

    public DivItemStyle setShrink(boolean shrink) {
        this.shrink = shrink;
        return this;
    }
}
