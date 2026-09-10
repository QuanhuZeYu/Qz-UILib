package club.heiqi.uilib.ui.scene.theme;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;

/**
 * 不可变 UI 主题：按材质角色提供表面配方，并提供语义色。
 *
 * <p>主题是纯值对象（值相等、可比较），不持有节点、Owner 或 runtime；运行时解析与作用域
 * 继承见 {@link SceneThemes}。默认档 {@link #liquidGlassDark()} 是本库的默认外观；
 * {@link #solidDark()} 保留旧实色观感供显式覆盖与对照；{@link #withoutBackdrop()}
 * 提供不支持滤镜时的可读替代。</p>
 *
 * <p><b>分层</b>：本类位于 {@code ui.scene.theme}，只依赖 render/node 值类型，不 import
 * control/form/internal/config，也不依赖 Minecraft/LWJGL。</p>
 */
public final class SceneTheme {

    /** 材质角色：消费方按用途取配方，不自行拼色值。 */
    public enum Role {
        /** 页面主面板：温和折射、受控染色、稳定文字对比。 */
        PANEL,
        /** 工具栏/导航底座：薄玻璃、清晰轮廓。 */
        TOOLBAR,
        /** 输入表面：弱光学效果、明确焦点、清晰文字与选区。 */
        INPUT,
        /** 列表/表单分组：低干扰内容底座。 */
        GROUP,
        /** 菜单/对话框/提示：独立浮层、稳定可读。 */
        OVERLAY,
        /** 滑块/开关/选中指示：小尺度高光与强调色。 */
        INDICATOR,
        /** 标准按钮。 */
        BUTTON_STANDARD,
        /** 主操作按钮（强调色）。 */
        BUTTON_PRIMARY,
        /** 危险操作按钮。 */
        BUTTON_DANGER
    }

    /** 无滤镜替代档的默认不透明底色（面板/工具栏/浮层/按钮）。 */
    public static final int FALLBACK_BG = 0xFF2B2930;
    /** 无滤镜替代档的输入表面底色。 */
    public static final int FALLBACK_BG_INPUT = 0xFF211F26;
    /** 无滤镜替代档的分组底色。 */
    public static final int FALLBACK_BG_GROUP = 0xFF1B1B1F;
    /** 无滤镜替代档的悬停底色。 */
    public static final int FALLBACK_BG_HOVER = 0xFF36333D;
    /** 无滤镜替代档的按下底色。 */
    public static final int FALLBACK_BG_PRESSED = 0xFF211F26;
    /** 无滤镜替代档的禁用底色。 */
    public static final int FALLBACK_BG_DISABLED = 0xFF1D1B20;

    /** 全圆角胶囊半径：小控件（开关轨道、滑块、勾选框、选中指示）的几何语义。 */
    public static final int PILL_RADIUS = 999;

    private final Map<Role, SceneSurfaceStyle> surfaces;
    private final int foreground;
    private final int mutedForeground;
    private final int disabledForeground;
    private final int onAccentForeground;
    private final int accent;
    private final int accentHover;
    private final int accentPressed;
    private final int selectionBackground;
    private final int selectionForeground;
    private final int borderDefault;
    private final int borderFocus;
    private final int borderDisabled;
    private final int danger;
    private final int errorText;
    private final int warningText;

    private SceneTheme(Builder builder) {
        EnumMap<Role, SceneSurfaceStyle> copy = new EnumMap<Role, SceneSurfaceStyle>(Role.class);
        for (Map.Entry<Role, SceneSurfaceStyle> entry : builder.surfaces.entrySet()) {
            copy.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "surface " + entry.getKey()));
        }
        for (Role role : Role.values()) {
            if (!copy.containsKey(role)) {
                throw new IllegalArgumentException("缺少角色配方: " + role);
            }
        }
        // 按钮前景默认取主题语义色（普通按钮=正文，强调按钮=强调底前景），
        // 配方作者显式设置的 foreground 优先保留。
        copy.put(Role.BUTTON_STANDARD, withDefaultForeground(copy.get(Role.BUTTON_STANDARD), builder.foreground));
        copy.put(Role.BUTTON_PRIMARY,
                withDefaultForeground(copy.get(Role.BUTTON_PRIMARY), builder.onAccentForeground));
        copy.put(Role.BUTTON_DANGER,
                withDefaultForeground(copy.get(Role.BUTTON_DANGER), builder.onAccentForeground));
        surfaces = Collections.unmodifiableMap(copy);
        foreground = builder.foreground;
        mutedForeground = builder.mutedForeground;
        disabledForeground = builder.disabledForeground;
        onAccentForeground = builder.onAccentForeground;
        accent = builder.accent;
        accentHover = builder.accentHover;
        accentPressed = builder.accentPressed;
        selectionBackground = builder.selectionBackground;
        selectionForeground = builder.selectionForeground;
        borderDefault = builder.borderDefault;
        borderFocus = builder.borderFocus;
        borderDisabled = builder.borderDisabled;
        danger = builder.danger;
        errorText = builder.errorText;
        warningText = builder.warningText;
    }

    /** @return 新 builder（默认取深色液态玻璃档） */
    public static Builder builder() { return new Builder(); }

    /**
     * 某角色的表面配方。
     *
     * @param role 材质角色，不可为 null
     * @return 配方（恒非 null）
     */
    public SceneSurfaceStyle surface(Role role) {
        return surfaces.get(Objects.requireNonNull(role, "role"));
    }

    /** @return 正文前景（ARGB） */
    public int foreground() { return foreground; }
    /** @return 次要/占位前景（ARGB） */
    public int mutedForeground() { return mutedForeground; }
    /** @return 禁用前景（ARGB） */
    public int disabledForeground() { return disabledForeground; }
    /** @return 强调底上的前景（ARGB） */
    public int onAccentForeground() { return onAccentForeground; }
    /** @return 强调色（ARGB） */
    public int accent() { return accent; }
    /** @return 强调色悬停档（ARGB） */
    public int accentHover() { return accentHover; }
    /** @return 强调色按下档（ARGB） */
    public int accentPressed() { return accentPressed; }
    /** @return 文本选区背景（ARGB） */
    public int selectionBackground() { return selectionBackground; }
    /** @return 文本选区前景（ARGB） */
    public int selectionForeground() { return selectionForeground; }
    /** @return 默认边框色（ARGB） */
    public int borderDefault() { return borderDefault; }
    /** @return 聚焦边框色（ARGB） */
    public int borderFocus() { return borderFocus; }
    /** @return 禁用边框色（ARGB） */
    public int borderDisabled() { return borderDisabled; }
    /** @return 危险动作色（ARGB） */
    public int danger() { return danger; }
    /** @return 错误文本色（ARGB） */
    public int errorText() { return errorText; }
    /** @return 警告文本色（ARGB） */
    public int warningText() { return warningText; }

    /**
     * 以本主题当前值为起点的构建器：全部语义色与九份角色配方按现值预置，可逐项覆盖。
     *
     * <p><b>与 {@link #builder()} 的区别</b>：{@code builder()} 恒以深色液态玻璃档为起点，
     * 本方法以「本实例的现值」为起点。因此从 {@link #liquidGlassLight()}、{@link #solidDark()}
     * 或 {@link #withoutBackdrop()} 的派生结果构造变体时，只需覆盖要改的字段，
     * 不必逐项抄写全部语义色与角色配方。</p>
     *
     * <p>返回的构建器与本实例无耦合：改动不回写本主题（值对象不可变）。</p>
     *
     * @return 预置了本主题全部字段的构建器
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.surfaces.clear();
        builder.surfaces.putAll(surfaces);
        builder.foreground = foreground;
        builder.mutedForeground = mutedForeground;
        builder.disabledForeground = disabledForeground;
        builder.onAccentForeground = onAccentForeground;
        builder.accent = accent;
        builder.accentHover = accentHover;
        builder.accentPressed = accentPressed;
        builder.selectionBackground = selectionBackground;
        builder.selectionForeground = selectionForeground;
        builder.borderDefault = borderDefault;
        builder.borderFocus = borderFocus;
        builder.borderDisabled = borderDisabled;
        builder.danger = danger;
        builder.errorText = errorText;
        builder.warningText = warningText;
        return builder;
    }

    /**
     * 全角色关闭滤镜的可读替代档：backdrop 全为 null，tint 换为不透明底色，
     * 边框、圆角、前景与状态色保持同一套。
     *
     * @return 无滤镜主题
     */
    public SceneTheme withoutBackdrop() {
        Builder builder = toBuilder();
        for (Map.Entry<Role, SceneSurfaceStyle> entry : surfaces.entrySet()) {
            builder.surfaces.put(entry.getKey(), opaque(entry.getKey(), entry.getValue()));
        }
        return builder.build();
    }

    private static SceneSurfaceStyle opaque(Role role, SceneSurfaceStyle style) {
        int idle = fallbackBackground(role);
        return style.toBuilder()
                .backdrop(null)
                .idle(new SceneSurfaceStyle.StateStyle(idle, style.getIdle().getEdge(), 0.0F, 0.0F))
                .hovered(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_HOVER, style.getHovered().getEdge(), 0.0F, 0.0F))
                .pressed(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_PRESSED, style.getPressed().getEdge(), 0.0F, 0.0F))
                .disabled(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_DISABLED, style.getDisabled().getEdge(), 0.0F, 0.0F))
                .build();
    }

    private static int fallbackBackground(Role role) {
        switch (role) {
            case INPUT: return FALLBACK_BG_INPUT;
            case GROUP: return FALLBACK_BG_GROUP;
            default: return FALLBACK_BG;
        }
    }

    private static SceneSurfaceStyle withDefaultForeground(SceneSurfaceStyle style, int foreground) {
        return style.getForeground() != null ? style : style.toBuilder().foreground(foreground).build();
    }

    /** 库默认：深色液态玻璃档（本目标默认外观）。 */
    public static SceneTheme liquidGlassDark() {
        return builder().build();
    }

    /** 浅色液态玻璃档（保留能力；本轮不做自动背景感知）。 */
    public static SceneTheme liquidGlassLight() {
        Builder builder = builder();
        builder.foreground = 0xFF1C1B1F;
        builder.mutedForeground = 0xFF49454F;
        builder.disabledForeground = 0xFF9E9AA7;
        builder.onAccentForeground = 0xFFFFFFFF;
        builder.accent = 0xFF6750A4;
        builder.accentHover = 0xFF7F67BE;
        builder.accentPressed = 0xFF4F378B;
        builder.selectionBackground = 0xFF6750A4;
        builder.selectionForeground = 0xFFFFFFFF;
        builder.borderDefault = 0xFF79747E;
        builder.borderFocus = 0xFF6750A4;
        builder.borderDisabled = 0xFFCAC4D0;
        builder.danger = 0xFFB3261E;
        builder.errorText = 0xFF8C1D18;
        builder.warningText = 0xFF8B5000;

        builder.surfaces.put(Role.PANEL, lightSurface(UiGlassMaterial.THIN, 10, 0.60F, 16,
                0x33FFFFFF, 0x66FFFFFF));
        builder.surfaces.put(Role.TOOLBAR, lightSurface(UiGlassMaterial.THIN, 6, 0.60F, 12,
                0x2EFFFFFF, 0x66FFFFFF));
        builder.surfaces.put(Role.INPUT, lightSurface(UiGlassMaterial.THIN, 4, 0.35F, 10,
                0x26FFFFFF, 0x66FFFFFF));
        builder.surfaces.put(Role.GROUP, lightSurface(UiGlassMaterial.ULTRA_THIN, 4, 0.25F, 12,
                0x1FFFFFFF, 0x4DFFFFFF));
        builder.surfaces.put(Role.OVERLAY, lightSurface(UiGlassMaterial.REGULAR, 8, 0.75F, 14,
                0x3DFFFFFF, 0x73FFFFFF));
        builder.surfaces.put(Role.INDICATOR, lightSurface(UiGlassMaterial.THIN, 4, 0.50F, PILL_RADIUS,
                0x33EADDFF, 0x99FFFFFF));
        builder.surfaces.put(Role.BUTTON_STANDARD, lightSurface(UiGlassMaterial.THIN, 6, 1.0F, 8,
                0x33FFFFFF, 0x66FFFFFF));
        builder.surfaces.put(Role.BUTTON_PRIMARY, lightSurface(UiGlassMaterial.THIN, 6, 1.0F, 8,
                0x4D6750A4, 0x80FFFFFF));
        builder.surfaces.put(Role.BUTTON_DANGER, lightSurface(UiGlassMaterial.THIN, 6, 1.0F, 8,
                0x4DB3261E, 0x80FFFFFF));
        return builder.build();
    }

    /** 旧实色观感档：全角色关闭滤镜，tint/边框取自 {@code SceneChromeTokens} 实色。 */
    public static SceneTheme solidDark() {
        Builder builder = builder();
        builder.foreground = 0xFFE6E1E5;
        builder.mutedForeground = 0xFFCAC4D0;
        builder.disabledForeground = 0xFF79747E;
        builder.onAccentForeground = 0xFFEADDFF;
        builder.accent = 0xFF4F378B;
        builder.accentHover = 0xFF6750A4;
        builder.accentPressed = 0xFF3F2E68;
        builder.selectionBackground = 0xFF4F378B;
        builder.selectionForeground = 0xFFEADDFF;
        builder.borderDefault = 0xFF938F99;
        builder.borderFocus = 0xFFD0BCFF;
        builder.borderDisabled = 0xFF49454F;
        builder.danger = 0xFF7F1D1D;
        builder.errorText = 0xFFFFB4AB;
        builder.warningText = 0xFFFBBF24;
        for (Role role : Role.values()) {
            builder.surfaces.put(role, solidSurface(role));
        }
        return builder.build();
    }

    private static SceneSurfaceStyle darkSurface(UiGlassMaterial material, int blur, float lens,
            int radius, int idleTint, int edge) {
        int tintAlpha = alphaOf(idleTint);
        int edgeAlpha = alphaOf(edge);
        return SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(material, blur, lens))
                .cornerRadius(radius)
                .borderWidth(1)
                .focusEdge(0xD0C7EEFF)
                .idle(new SceneSurfaceStyle.StateStyle(idleTint, edge, 0.5F, 0.85F))
                .hovered(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, tintAlpha + 6),
                        withAlpha(edge, edgeAlpha + 0x40), 1.0F, 1.0F))
                .pressed(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, Math.max(0, tintAlpha - 2)),
                        withAlpha(edge, Math.max(0, edgeAlpha - 0x10)), 0.0F, 0.60F))
                .disabled(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, 0x06),
                        withAlpha(edge, 0x12), 0.35F, 0.65F))
                .build();
    }

    private static SceneSurfaceStyle lightSurface(UiGlassMaterial material, int blur, float lens,
            int radius, int idleTint, int edge) {
        int tintAlpha = alphaOf(idleTint);
        int edgeAlpha = alphaOf(edge);
        return SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(material, blur, lens))
                .cornerRadius(radius)
                .borderWidth(1)
                .focusEdge(0xD06750A4)
                .idle(new SceneSurfaceStyle.StateStyle(idleTint, edge, 0.5F, 0.85F))
                .hovered(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, tintAlpha + 0x0A),
                        withAlpha(edge, edgeAlpha + 0x40), 1.0F, 1.0F))
                .pressed(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, Math.max(0, tintAlpha - 4)),
                        withAlpha(edge, Math.max(0, edgeAlpha - 0x10)), 0.0F, 0.60F))
                .disabled(new SceneSurfaceStyle.StateStyle(withAlpha(idleTint, 0x0A),
                        withAlpha(edge, 0x33), 0.35F, 0.65F))
                .build();
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int withAlpha(int argb, int alpha) {
        int clamped = Math.max(0, Math.min(0xFF, alpha));
        return (clamped << 24) | (argb & 0x00FFFFFF);
    }

    private static SceneSurfaceStyle solidSurface(Role role) {
        int idle = fallbackBackground(role);
        return SceneSurfaceStyle.builder()
                .backdrop(null)
                .cornerRadius(role == Role.PANEL || role == Role.OVERLAY ? 16 : 12)
                .borderWidth(1)
                .focusEdge(0xFFD0BCFF)
                .idle(new SceneSurfaceStyle.StateStyle(idle, 0xFF938F99, 0.0F, 0.0F))
                .hovered(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_HOVER, 0xFF938F99, 0.0F, 0.0F))
                .pressed(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_PRESSED, 0xFF938F99, 0.0F, 0.0F))
                .disabled(new SceneSurfaceStyle.StateStyle(FALLBACK_BG_DISABLED, 0xFF49454F, 0.0F, 0.0F))
                .build();
    }

    /** 主题构建器；默认取深色液态玻璃档，可逐字段覆盖。 */
    public static final class Builder {
        private final EnumMap<Role, SceneSurfaceStyle> surfaces = new EnumMap<Role, SceneSurfaceStyle>(Role.class);
        private int foreground = 0xFFE6E1E5;
        private int mutedForeground = 0xFFCAC4D0;
        private int disabledForeground = 0xFF79747E;
        private int onAccentForeground = 0xFFEADDFF;
        private int accent = 0xFF4F378B;
        private int accentHover = 0xFF6750A4;
        private int accentPressed = 0xFF3F2E68;
        private int selectionBackground = 0xFF4F378B;
        private int selectionForeground = 0xFFEADDFF;
        private int borderDefault = 0xFF938F99;
        private int borderFocus = 0xFFD0BCFF;
        private int borderDisabled = 0xFF49454F;
        private int danger = 0xFF7F1D1D;
        private int errorText = 0xFFFFB4AB;
        private int warningText = 0xFFFBBF24;

        private Builder() {
            // 默认深色液态玻璃角色配方：builder() 与各静态工厂共用同一套起点。
            surfaces.put(Role.PANEL, darkSurface(UiGlassMaterial.DARK_THIN, 10, 0.60F, 16, 0x14EAF7FF, 0x24FFFFFF));
            surfaces.put(Role.TOOLBAR, darkSurface(UiGlassMaterial.DARK_THIN, 6, 0.60F, 12, 0x10EAF7FF, 0x24FFFFFF));
            surfaces.put(Role.INPUT, darkSurface(UiGlassMaterial.DARK_THIN, 4, 0.35F, 10, 0x0A101418, 0x24FFFFFF));
            surfaces.put(Role.GROUP, darkSurface(UiGlassMaterial.DARK_ULTRA_THIN, 4, 0.25F, 12, 0x0C101418, 0x1FFFFFFF));
            surfaces.put(Role.OVERLAY, darkSurface(UiGlassMaterial.DARK_REGULAR, 8, 0.75F, 14, 0x1A101418, 0x2FFFFFFF));
            surfaces.put(Role.INDICATOR, darkSurface(UiGlassMaterial.DARK_THIN, 4, 0.50F, PILL_RADIUS, 0x0EEADDFF, 0x40FFFFFF));
            surfaces.put(Role.BUTTON_STANDARD, darkSurface(UiGlassMaterial.DARK_THIN, 6, 1.0F, 8, 0x0CEAF7FF, 0x24FFFFFF));
            surfaces.put(Role.BUTTON_PRIMARY, darkSurface(UiGlassMaterial.DARK_THIN, 6, 1.0F, 8, 0x2A0079BE, 0x40FFFFFF));
            surfaces.put(Role.BUTTON_DANGER, darkSurface(UiGlassMaterial.DARK_THIN, 6, 1.0F, 8, 0x2AFF8797, 0x40FFFFFF));
        }

        /** 覆盖某角色的表面配方。 */
        public Builder surface(Role role, SceneSurfaceStyle style) {
            surfaces.put(Objects.requireNonNull(role, "role"), Objects.requireNonNull(style, "style"));
            return this;
        }
        public Builder foreground(int value) { foreground = value; return this; }
        public Builder mutedForeground(int value) { mutedForeground = value; return this; }
        public Builder disabledForeground(int value) { disabledForeground = value; return this; }
        public Builder onAccentForeground(int value) { onAccentForeground = value; return this; }
        public Builder accent(int value) { accent = value; return this; }
        public Builder accentHover(int value) { accentHover = value; return this; }
        public Builder accentPressed(int value) { accentPressed = value; return this; }
        public Builder selectionBackground(int value) { selectionBackground = value; return this; }
        public Builder selectionForeground(int value) { selectionForeground = value; return this; }
        public Builder borderDefault(int value) { borderDefault = value; return this; }
        public Builder borderFocus(int value) { borderFocus = value; return this; }
        public Builder borderDisabled(int value) { borderDisabled = value; return this; }
        public Builder danger(int value) { danger = value; return this; }
        public Builder errorText(int value) { errorText = value; return this; }
        public Builder warningText(int value) { warningText = value; return this; }

        /** @return 不可变主题 */
        public SceneTheme build() { return new SceneTheme(this); }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SceneTheme)) return false;
        SceneTheme that = (SceneTheme) other;
        return foreground == that.foreground && mutedForeground == that.mutedForeground
                && disabledForeground == that.disabledForeground
                && onAccentForeground == that.onAccentForeground
                && accent == that.accent && accentHover == that.accentHover
                && accentPressed == that.accentPressed
                && selectionBackground == that.selectionBackground
                && selectionForeground == that.selectionForeground
                && borderDefault == that.borderDefault && borderFocus == that.borderFocus
                && borderDisabled == that.borderDisabled && danger == that.danger
                && errorText == that.errorText && warningText == that.warningText
                && surfaces.equals(that.surfaces);
    }
    @Override
    public int hashCode() {
        return Objects.hash(surfaces, foreground, mutedForeground, disabledForeground, onAccentForeground,
                accent, accentHover, accentPressed, selectionBackground, selectionForeground,
                borderDefault, borderFocus, borderDisabled, danger, errorText, warningText);
    }
    @Override
    public String toString() {
        return "SceneTheme{fg=" + Integer.toHexString(foreground) + ", accent=" + Integer.toHexString(accent)
                + ", panel=" + surface(Role.PANEL) + '}';
    }
}
