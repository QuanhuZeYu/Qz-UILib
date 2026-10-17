package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 标准控件的不可变主题值；字号派生用withTextStyle保留其余样式。 */
public final class SceneTheme {
    private static final club.heiqi.uilib.ui.scene.motion.MotionSpec DEFAULT_FEEDBACK =
            club.heiqi.uilib.ui.scene.motion.MotionSpec.tween(140_000_000L, club.heiqi.uilib.ui.scene.motion.MotionSpec.SMOOTH);
    private static final club.heiqi.uilib.ui.scene.motion.MotionSpec DEFAULT_SPATIAL =
            club.heiqi.uilib.ui.scene.motion.MotionSpec.spring(.28, .82);
    public final TextStyle textStyle;
    public final club.heiqi.uilib.ui.scene.motion.MotionSpec feedbackMotion, spatialMotion;
    public final int panel, button, hover, pressed, focus, disabled, disabledText;
    public final int tooltip, tooltipText, padding, gap;

    public SceneTheme(
            TextStyle textStyle,
            int panel,
            int button,
            int hover,
            int pressed,
            int focus,
            int disabled,
            int disabledText,
            int tooltip,
            int tooltipText,
            int padding,
            int gap) {
        this(textStyle, panel, button, hover, pressed, focus, disabled, disabledText, tooltip,
                tooltipText, padding, gap,
                DEFAULT_FEEDBACK, DEFAULT_SPATIAL);
    }

    private SceneTheme(TextStyle textStyle, int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText, int padding, int gap,
            club.heiqi.uilib.ui.scene.motion.MotionSpec feedback, club.heiqi.uilib.ui.scene.motion.MotionSpec spatial) {
        this.feedbackMotion = Objects.requireNonNull(feedback, "主题反馈动效");
        this.spatialMotion = Objects.requireNonNull(spatial, "主题空间动效");
        this.textStyle = Objects.requireNonNull(textStyle, "textStyle");
        if (padding < 0 || gap < 0) throw new IllegalArgumentException("主题间距不能为负");
        this.panel = panel;
        this.button = button;
        this.hover = hover;
        this.pressed = pressed;
        this.focus = focus;
        this.disabled = disabled;
        this.disabledText = disabledText;
        this.tooltip = tooltip;
        this.tooltipText = tooltipText;
        this.padding = padding;
        this.gap = gap;
    }

    public static SceneTheme light() {
        return new SceneTheme(
                new TextStyle("default", 18, 0xFF20252B).withDefaultHighlightColor(0x88FFD54F),
                0xFFF4F6F8,
                0xFFE2E8EF,
                0xFFD1E1F2,
                0xFFB9D1EB,
                0xFF246DB5,
                0xFFE4E6E9,
                0xFF777D85,
                0xFF20252B,
                0xFFFFFFFF,
                8,
                8);
    }

    public static SceneTheme dark() {
        return new SceneTheme(
                new TextStyle("default", 18, 0xFFF1F4F8).withDefaultHighlightColor(0x887A5A16),
                0xFF20252B,
                0xFF37414D,
                0xFF465C74,
                0xFF294866,
                0xFF80BFFF,
                0xFF30353D,
                0xFF8C949F,
                0xFFF1F4F8,
                0xFF20252B,
                8,
                8);
    }

    public SceneTheme withMotion(club.heiqi.uilib.ui.scene.motion.MotionSpec feedback,
            club.heiqi.uilib.ui.scene.motion.MotionSpec spatial) {
        return new SceneTheme(textStyle, panel, button, hover, pressed, focus, disabled, disabledText,
                tooltip, tooltipText, padding, gap, feedback, spatial);
    }

    public SceneTheme withTextStyle(TextStyle style) {
        return new SceneTheme(
                style,
                panel,
                button,
                hover,
                pressed,
                focus,
                disabled,
                disabledText,
                tooltip,
                tooltipText,
                padding,
                gap, feedbackMotion, spatialMotion);
    }
}
