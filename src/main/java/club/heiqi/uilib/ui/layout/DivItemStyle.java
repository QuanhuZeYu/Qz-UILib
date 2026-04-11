package club.heiqi.uilib.ui.layout;

/**
 * Div 容器对子项的主轴伸缩策略。
 */
public class DivItemStyle {

    private boolean grow = true;
    private boolean shrink = true;
    private float growFactor = 1.0F;
    private float shrinkFactor = 1.0F;

    public static DivItemStyle flex() {
        return new DivItemStyle();
    }

    /**
     * 创建可增长的柔性子项，并指定增长权重。
     *
     * @param growFactor 主轴增长权重
     * @return 子项样式
     */
    public static DivItemStyle flex(float growFactor) {
        return new DivItemStyle().setGrowFactor(growFactor);
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
        if (!grow) {
            this.growFactor = 0.0F;
        } else if (growFactor <= 0.0F) {
            this.growFactor = 1.0F;
        }
        return this;
    }

    public boolean isShrink() {
        return shrink;
    }

    public DivItemStyle setShrink(boolean shrink) {
        this.shrink = shrink;
        if (!shrink) {
            this.shrinkFactor = 0.0F;
        } else if (shrinkFactor <= 0.0F) {
            this.shrinkFactor = 1.0F;
        }
        return this;
    }

    /**
     * 获取主轴增长权重。
     *
     * @return 增长权重
     */
    public float getGrowFactor() {
        return growFactor;
    }

    /**
     * 设置主轴增长权重。
     *
     * @param growFactor 增长权重
     * @return 当前样式
     */
    public DivItemStyle setGrowFactor(float growFactor) {
        this.growFactor = Math.max(0.0F, growFactor);
        this.grow = this.growFactor > 0.0F;
        return this;
    }

    /**
     * 获取主轴收缩权重。
     *
     * @return 收缩权重
     */
    public float getShrinkFactor() {
        return shrinkFactor;
    }

    /**
     * 设置主轴收缩权重。
     *
     * @param shrinkFactor 收缩权重
     * @return 当前样式
     */
    public DivItemStyle setShrinkFactor(float shrinkFactor) {
        this.shrinkFactor = Math.max(0.0F, shrinkFactor);
        this.shrink = this.shrinkFactor > 0.0F;
        return this;
    }
}
