package club.heiqi.uilib.ui.scene.paint;

/**
 * SceneStateColors 统一解析交互控件视觉态到 chrome token 色值。
 * 纯静态方法查表，无状态，非主题引擎。
 * 优先级：disabled > pressed > hovered > selected > default。
 */
public final class SceneStateColors {

    /**
     * 纯静态状态解析器，禁止实例化。
     */
    private SceneStateColors() {
    }

    /**
     * 标准交互背景四态（未选中控件）。
     * 优先级：disabled > pressed > hover > default。
     *
     * @param enabled 是否启用
     * @param hovered 是否悬停
     * @param pressed 是否按下
     * @return 对应背景色 token
     */
    public static int standardBackground(boolean enabled, boolean hovered, boolean pressed) {
        if (!enabled) {
            return SceneChromeTokens.BG_DISABLED;
        }
        if (pressed) {
            return SceneChromeTokens.BG_PRESSED;
        }
        if (hovered) {
            return SceneChromeTokens.BG_HOVER;
        }
        return SceneChromeTokens.BG_DEFAULT;
    }

    /**
     * 选中态背景四态（checkbox/radio/segment/tab 选中）。
     * 选中态走 ACCENT 蓝色通道，未选中走 Slate 灰色通道。
     * 优先级：disabled > pressed > hover > default。
     *
     * @param enabled 是否启用
     * @param hovered 是否悬停
     * @param pressed 是否按下
     * @return 对应选中背景色 token
     */
    public static int selectedBackground(boolean enabled, boolean hovered, boolean pressed) {
        if (!enabled) {
            return SceneChromeTokens.BG_DISABLED;
        }
        if (pressed) {
            return SceneChromeTokens.ACCENT_PRESSED;
        }
        if (hovered) {
            return SceneChromeTokens.ACCENT_HOVER;
        }
        return SceneChromeTokens.ACCENT;
    }

    /**
     * 边框三态。focus 走 border ring（borderColor 切蓝），borderWidth 常驻不动。
     * 优先级：disabled > focused > default。
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return 对应边框色 token
     */
    public static int standardBorder(boolean enabled, boolean focused) {
        if (!enabled) {
            return SceneChromeTokens.BORDER_DISABLED;
        }
        if (focused) {
            return SceneChromeTokens.BORDER_FOCUS;
        }
        return SceneChromeTokens.BORDER_DEFAULT;
    }

    /**
     * 标准文本。选中态用 TEXT_ON_ACCENT（白），否则 TEXT_PRIMARY。
     *
     * @param enabled  是否启用
     * @param selected 是否选中
     * @return 对应文本色 token
     */
    public static int standardText(boolean enabled, boolean selected) {
        if (!enabled) {
            return SceneChromeTokens.TEXT_DISABLED;
        }
        if (selected) {
            return SceneChromeTokens.TEXT_ON_ACCENT;
        }
        return SceneChromeTokens.TEXT_PRIMARY;
    }

    /**
     * 次要文本（未选中 tab/segment、placeholder）。
     *
     * @param enabled 是否启用
     * @return 对应次要文本色 token
     */
    public static int secondaryText(boolean enabled) {
        if (!enabled) {
            return SceneChromeTokens.TEXT_DISABLED;
        }
        return SceneChromeTokens.TEXT_SECONDARY;
    }

    /**
     * 输入区背景（TextInput/Select listbox/Tab 内容区）。
     * 输入区用更深档 BG_PRESSED 做“凹陷”视觉。
     *
     * @param enabled 是否启用
     * @return 对应输入区背景色 token
     */
    public static int inputBackground(boolean enabled) {
        if (!enabled) {
            return SceneChromeTokens.BG_DISABLED;
        }
        return SceneChromeTokens.BG_PRESSED;
    }

    /**
     * Slider thumb 三态（Sky 系，独立于标准背景四态）。
     *
     * @param enabled 是否启用
     * @param hovered 是否悬停
     * @param pressed 是否按下
     * @return 对应 thumb 背景色 token
     */
    public static int thumbBackground(boolean enabled, boolean hovered, boolean pressed) {
        if (!enabled) {
            return SceneChromeTokens.TEXT_DISABLED;
        }
        if (pressed) {
            return SceneChromeTokens.THUMB_PRESSED;
        }
        if (hovered) {
            return SceneChromeTokens.THUMB_HOVER;
        }
        return SceneChromeTokens.THUMB_DEFAULT;
    }
}
