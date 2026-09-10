package club.heiqi.uilib.ui.scene.theme;

import java.util.Objects;

import club.heiqi.uilib.ui.render.UiBackdrop;

/**
 * 通用表面配方：一块玻璃/实色表面的完整不可变描述。
 *
 * <p>由主题按 {@link SceneTheme.Role} 提供，也可由调用方显式构造以覆盖主题。字段语义与
 * {@code SceneGlassButtonStyle} 保持同源（后者是按钮专用外观，本类是通用角色表面），
 * 因此从按钮配方迁移过来的观感不变。</p>
 *
 * <p><b>backdrop 语义</b>：{@code null} 表示<b>显式关闭滤镜</b>，不是"未指定"。此时
 * tint 必须自身可读（不透明或足够覆盖），无滤镜替代档由
 * {@link SceneTheme#withoutBackdrop()} 提供。任何"跟随主题"的表达由消费方传非 null
 * 的配方信号完成，禁止用同一个 null 同时表达继承与关闭。</p>
 *
 * <p>四态按 disabled &gt; pressed &gt; hovered &gt; idle 选择；focus 只覆盖非禁用态的缘色。
 * {@code foreground} 是可选前景令牌（null = 本配方不管理前景，由调用方决定）。</p>
 *
 * <p><b>浮雕豁免（P-05）</b>：{@code reliefDisabled} 为 true 时绑定器不消费
 * {@link StateStyle#getElevation()}，恒把节点 {@code surfaceElevation} 写为 -1（普通绘制路径）。
 * 这是给「大面板不得进浮雕通道」这类已知例外提供的<b>显式声明位</b>，不是后门：它只关浮雕通道，
 * 不放弃写入权——backgroundColor / border / cornerRadius / backdrop 仍归绑定器独占。
 * 默认 false 保持既有行为。</p>
 */
public final class SceneSurfaceStyle {

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
    private final boolean reliefDisabled;

    private SceneSurfaceStyle(Builder builder) {
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
        reliefDisabled = builder.reliefDisabled;
    }

    /** @return 新 builder（默认值见 {@link Builder} 字段初始化） */
    public static Builder builder() { return new Builder(); }

    /** @return 以本配方为起点的 builder（不共享可变状态） */
    public Builder toBuilder() { return new Builder(this); }

    /** @return 背后滤镜声明；null 表示显式关闭滤镜 */
    public UiBackdrop getBackdrop() { return backdrop; }
    /** @return 圆角（像素，&gt;= 0） */
    public int getCornerRadius() { return cornerRadius; }
    /** @return 边框宽度（像素，&gt;= 0；0 表示无边框，但不代表关闭全部材质高光） */
    public int getBorderWidth() { return borderWidth; }
    /** @return 非禁用态聚焦缘色（ARGB） */
    public int getFocusEdge() { return focusEdge; }
    /** @return 可选前景令牌（ARGB）；null 表示本配方不管理前景 */
    public Integer getForeground() { return foreground; }
    /** @return 是否禁用浮雕通道（true = 绑定器恒写 surfaceElevation -1，走普通绘制；见类文档「浮雕豁免」） */
    public boolean isReliefDisabled() { return reliefDisabled; }
    /** @return 按压时内容上移量（&gt;= 0） */
    public float getContentLift() { return contentLift; }
    /** @return 禁用态内容不透明度 [0,1] */
    public float getDisabledOpacity() { return disabledOpacity; }
    /** @return 状态过渡时长（毫秒，&gt;= 0） */
    public int getTransitionMillis() { return transitionMillis; }

    /** @return 默认态表面值 */
    public StateStyle getIdle() {
        return idle != null ? idle : new StateStyle(0x0CEAF7FF, 0x24FFFFFF, 0.5F, 0.85F);
    }
    /** @return 悬停态表面值 */
    public StateStyle getHovered() {
        return hovered != null ? hovered : new StateStyle(0x12EAF7FF, 0x90FFFFFF, 1.0F, 1.0F);
    }
    /** @return 按下态表面值 */
    public StateStyle getPressed() {
        return pressed != null ? pressed : new StateStyle(0x0AEAF7FF, 0x50FFFFFF, 0.0F, 0.60F);
    }
    /** @return 禁用态表面值 */
    public StateStyle getDisabled() {
        return disabled != null ? disabled : new StateStyle(0x06EAF7FF, 0x12FFFFFF, 0.35F, 0.65F);
    }

    /** 单个交互态的完整表面值；tint/edge 为 ARGB，elevation 为 [0,1]，lensFactor 有限且非负。 */
    public static final class StateStyle {
        private final int tint;
        private final int edge;
        private final float elevation;
        private final float lensFactor;

        /**
         * @param tint       叠在玻璃之上的染色（ARGB）
         * @param edge       边框色（ARGB）
         * @param elevation  表面浮雕高度 [0,1]
         * @param lensFactor 透镜系数乘子（&gt;= 0，有限）
         */
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
        @Override
        public String toString() {
            return "StateStyle{tint=" + Integer.toHexString(tint) + ", edge=" + Integer.toHexString(edge)
                    + ", elevation=" + elevation + ", lens=" + lensFactor + '}';
        }
    }

    /** 配方构建器；未显式设置的字段采用与 {@code SceneGlassButtonStyle} 同源的默认值。 */
    public static final class Builder {
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
        private boolean reliefDisabled;

        private Builder() {}
        private Builder(SceneSurfaceStyle style) {
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
            reliefDisabled = style.reliefDisabled;
        }

        /** @param value 滤镜声明；null 表示显式关闭滤镜 */
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
        /** @param value true = 禁用浮雕通道（绑定器不写 surfaceElevation）；默认 false 保持既有行为 */
        public Builder reliefDisabled(boolean value) { reliefDisabled = value; return this; }

        /** @return 不可变配方 */
        public SceneSurfaceStyle build() { return new SceneSurfaceStyle(this); }
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
        if (!(other instanceof SceneSurfaceStyle)) return false;
        SceneSurfaceStyle that = (SceneSurfaceStyle) other;
        return cornerRadius == that.cornerRadius && borderWidth == that.borderWidth
                && Objects.equals(backdrop, that.backdrop)
                && Objects.equals(idle, that.idle) && Objects.equals(hovered, that.hovered)
                && Objects.equals(pressed, that.pressed) && Objects.equals(disabled, that.disabled)
                && focusEdge == that.focusEdge && Float.compare(contentLift, that.contentLift) == 0
                && Float.compare(disabledOpacity, that.disabledOpacity) == 0
                && transitionMillis == that.transitionMillis && Objects.equals(foreground, that.foreground)
                && reliefDisabled == that.reliefDisabled;
    }
    @Override
    public int hashCode() {
        return Objects.hash(backdrop, cornerRadius, borderWidth, idle, hovered, pressed, disabled,
                focusEdge, contentLift, disabledOpacity, transitionMillis, foreground, reliefDisabled);
    }
    @Override
    public String toString() {
        return "SceneSurfaceStyle{backdrop=" + backdrop + ", radius=" + cornerRadius
                + ", border=" + borderWidth + ", foreground="
                + (foreground == null ? "null" : Integer.toHexString(foreground)) + '}';
    }
}
