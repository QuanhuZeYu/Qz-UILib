package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/** 不可变 HUD 注册规格；布局数值单位均为 UILib logical px。 */
public final class HudSpec {
    private final String id;
    private final HudAnchor anchor;
    private final HudVisibility visibility;
    private final int margin;
    private final int stackOrder;
    private final int minWidth;
    private final int maxWidth;

    private HudSpec(Builder builder) {
        if (builder.id == null || builder.id.trim().isEmpty()) throw new IllegalArgumentException("id must not be blank");
        this.id = builder.id;
        this.anchor = Objects.requireNonNull(builder.anchor, "anchor");
        this.visibility = Objects.requireNonNull(builder.visibility, "visibility");
        if (builder.margin < 0) throw new IllegalArgumentException("margin must be >= 0");
        this.margin = builder.margin;
        this.stackOrder = builder.stackOrder;
        if (builder.minWidth < 0) throw new IllegalArgumentException("minWidth must be >= 0");
        if (builder.maxWidth <= 0) throw new IllegalArgumentException("maxWidth must be > 0");
        if (builder.minWidth > builder.maxWidth) throw new IllegalArgumentException("minWidth must be <= maxWidth");
        this.minWidth = builder.minWidth;
        this.maxWidth = builder.maxWidth;
    }

    /** 以稳定且全局唯一的 id 创建 builder。 */
    public static Builder builder(String id) { return new Builder(id); }
    public String getId() { return id; }
    public HudAnchor getAnchor() { return anchor; }
    public HudVisibility getVisibility() { return visibility; }
    public int getMargin() { return margin; }
    public int getStackOrder() { return stackOrder; }
    /** 返回调用方要求的最小外宽；0 表示使用不超过 {@link #getMaxWidth()} 的 UILib 默认值。 */
    public int getMinWidth() { return minWidth; }
    /** 返回调用方允许的最大外宽；默认不额外限制视口 clamp。 */
    public int getMaxWidth() { return maxWidth; }

    /** HUD 规格 builder。 */
    public static final class Builder {
        private final String id;
        private HudAnchor anchor = HudAnchor.TOP_LEFT;
        private HudVisibility visibility = HudVisibility.GAMEPLAY_ONLY;
        private int margin = 8;
        private int stackOrder;
        private int minWidth;
        private int maxWidth = Integer.MAX_VALUE;
        private Builder(String id) { this.id = id; }
        public Builder anchor(HudAnchor value) { this.anchor = value; return this; }
        public Builder visibility(HudVisibility value) { this.visibility = value; return this; }
        public Builder margin(int value) { this.margin = value; return this; }
        public Builder stackOrder(int value) { this.stackOrder = value; return this; }
        /** 设置 HUD 外框最小宽度（logical px）；显式值不得大于 {@link #maxWidth(int)}。 */
        public Builder minWidth(int value) { this.minWidth = value; return this; }
        /** 设置 HUD 外框硬最大宽度（logical px）；默认最小宽度与其冲突时向下收敛。 */
        public Builder maxWidth(int value) { this.maxWidth = value; return this; }
        public HudSpec build() { return new HudSpec(this); }
    }
}
