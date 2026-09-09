package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

import club.heiqi.uilib.ui.render.UiBackdrop;

/**
 * 不可变玻璃按钮配方。默认值保持既有液态玻璃按钮观感；backdrop 为 null 时关闭过滤。
 * 四态按 disabled > pressed > hovered > idle 选择，focus 只覆盖非禁用态缘色。
 * foreground 是供内容绑定消费的可选 ARGB 令牌，不隐式遍历或改写业务内容。
 */
public final class SceneGlassButtonStyle {
    private final SceneButtonVariant variant;
    private final UiBackdrop backdrop;
    private final int cornerRadius;
    private final int borderWidth;
    private final StateStyle idle;
    private final StateStyle hovered;
    private final StateStyle pressed;
    private final StateStyle disabled;
    private final int focusEdge;
    private final float contentLift;
    private final float disabledOpacity;
    private final int transitionMillis;
    private final Integer foreground;

    private SceneGlassButtonStyle(Builder builder) {
        variant = builder.variant == null ? SceneButtonVariant.STANDARD : builder.variant;
        backdrop = builder.backdrop;
        cornerRadius = nonNegative(builder.cornerRadius, "cornerRadius");
        borderWidth = nonNegative(builder.borderWidth, "borderWidth");
        idle = builder.idle;
        hovered = builder.hovered;
        pressed = builder.pressed;
        disabled = builder.disabled;
        focusEdge = builder.focusEdge;
        contentLift = nonNegative(builder.contentLift, "contentLift");
        disabledOpacity = unit(builder.disabledOpacity, "disabledOpacity");
        transitionMillis = nonNegative(builder.transitionMillis, "transitionMillis");
        foreground = builder.foreground;
    }

    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { return new Builder(this); }
    public SceneButtonVariant getVariant() { return variant; }
    public UiBackdrop getBackdrop() { return backdrop; }
    public int getCornerRadius() { return cornerRadius; }
    public int getBorderWidth() { return borderWidth; }
    public int getFocusEdge() { return focusEdge; }
    public float getContentLift() { return contentLift; }
    public float getDisabledOpacity() { return disabledOpacity; }
    public int getTransitionMillis() { return transitionMillis; }
    public Integer getForeground() { return foreground; }

    public StateStyle getIdle() {
        return idle != null ? idle : new StateStyle(tint(0x0C, 0x20), 0x24FFFFFF, 0.5F, 0.85F);
    }
    public StateStyle getHovered() {
        return hovered != null ? hovered : new StateStyle(tint(0x12, 0x2A), 0x90FFFFFF, 1.0F, 1.0F);
    }
    public StateStyle getPressed() {
        return pressed != null ? pressed : new StateStyle(tint(0x0A, 0x24), 0x50FFFFFF, 0.0F, 0.60F);
    }
    public StateStyle getDisabled() {
        return disabled != null ? disabled : new StateStyle(tint(0x06, 0x06), 0x12FFFFFF, 0.35F, 0.65F);
    }

    private int tint(int neutralAlpha, int accentAlpha) {
        int rgb = variant == SceneButtonVariant.PRIMARY ? 0x0079BEFF
                : variant == SceneButtonVariant.DANGER ? 0x00FF8797 : 0x00EAF7FF;
        return ((variant == SceneButtonVariant.STANDARD ? neutralAlpha : accentAlpha) << 24) | rgb;
    }

    /** 单个交互态的完整表面值；tint/edge 为 ARGB，高度为 [0,1]，透镜系数有限且非负。 */
    public static final class StateStyle {
        private final int tint;
        private final int edge;
        private final float elevation;
        private final float lensFactor;

        public StateStyle(int tint, int edge, float elevation, float lensFactor) {
            this.tint = tint;
            this.edge = edge;
            this.elevation = unit(elevation, "elevation");
            this.lensFactor = nonNegative(lensFactor, "lensFactor");
        }
        public int getTint() { return tint; }
        public int getEdge() { return edge; }
        public float getElevation() { return elevation; }
        public float getLensFactor() { return lensFactor; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StateStyle)) return false;
            StateStyle that = (StateStyle) other;
            return tint == that.tint && edge == that.edge
                    && Float.compare(elevation, that.elevation) == 0
                    && Float.compare(lensFactor, that.lensFactor) == 0;
        }
        @Override
        public int hashCode() { return Objects.hash(tint, edge, elevation, lensFactor); }
    }

    /** null 状态覆盖表示重新采用当前 variant 的默认值；重复 build 不共享可变状态。 */
    public static final class Builder {
        private SceneButtonVariant variant = SceneButtonVariant.STANDARD;
        private UiBackdrop backdrop;
        private int cornerRadius = 8;
        private int borderWidth = 1;
        private StateStyle idle;
        private StateStyle hovered;
        private StateStyle pressed;
        private StateStyle disabled;
        private int focusEdge = 0xD0C7EEFF;
        private float contentLift = 2.0F;
        private float disabledOpacity = 0.35F;
        private int transitionMillis = 160;
        private Integer foreground;

        private Builder() {}
        private Builder(SceneGlassButtonStyle style) {
            variant = style.variant;
            backdrop = style.backdrop;
            cornerRadius = style.cornerRadius;
            borderWidth = style.borderWidth;
            idle = style.idle;
            hovered = style.hovered;
            pressed = style.pressed;
            disabled = style.disabled;
            focusEdge = style.focusEdge;
            contentLift = style.contentLift;
            disabledOpacity = style.disabledOpacity;
            transitionMillis = style.transitionMillis;
            foreground = style.foreground;
        }
        public Builder variant(SceneButtonVariant value) { variant = value; return this; }
        public Builder backdrop(UiBackdrop value) { backdrop = value; return this; }
        public Builder cornerRadius(int value) { cornerRadius = value; return this; }
        public Builder borderWidth(int value) { borderWidth = value; return this; }
        public Builder idle(StateStyle value) { idle = value; return this; }
        public Builder hovered(StateStyle value) { hovered = value; return this; }
        public Builder pressed(StateStyle value) { pressed = value; return this; }
        public Builder disabled(StateStyle value) { disabled = value; return this; }
        public Builder focusEdge(int value) { focusEdge = value; return this; }
        public Builder contentLift(float value) { contentLift = value; return this; }
        public Builder disabledOpacity(float value) { disabledOpacity = value; return this; }
        public Builder transitionMillis(int value) { transitionMillis = value; return this; }
        public Builder foreground(Integer value) { foreground = value; return this; }
        public SceneGlassButtonStyle build() { return new SceneGlassButtonStyle(this); }
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return value;
    }
    private static float nonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value == 0.0F ? 0.0F : value;
    }
    private static float unit(float value, String name) {
        nonNegative(value, name);
        if (value > 1.0F) throw new IllegalArgumentException(name + " must be within [0,1]");
        return value == 0.0F ? 0.0F : value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SceneGlassButtonStyle)) return false;
        SceneGlassButtonStyle that = (SceneGlassButtonStyle) other;
        return variant == that.variant && Objects.equals(backdrop, that.backdrop)
                && cornerRadius == that.cornerRadius && borderWidth == that.borderWidth
                && Objects.equals(idle, that.idle) && Objects.equals(hovered, that.hovered)
                && Objects.equals(pressed, that.pressed) && Objects.equals(disabled, that.disabled)
                && focusEdge == that.focusEdge && Float.compare(contentLift, that.contentLift) == 0
                && Float.compare(disabledOpacity, that.disabledOpacity) == 0
                && transitionMillis == that.transitionMillis && Objects.equals(foreground, that.foreground);
    }
    @Override
    public int hashCode() {
        return Objects.hash(variant, backdrop, cornerRadius, borderWidth, idle, hovered, pressed, disabled,
                focusEdge, contentLift, disabledOpacity, transitionMillis, foreground);
    }
}
