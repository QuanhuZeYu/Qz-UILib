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
     * listbox item 背景（Select 下拉选项专用四态查表）。
     *
     * <p>与 {@link #standardBackground} / {@link #selectedBackground} 的差异：
     * item 多一个键盘高亮态（highlighted），且默认态透明以露出 listbox 凹陷底
     * （{@link #inputBackground}）。优先级：disabled > selected+hovered > selected+highlighted >
     * selected > highlighted > hovered > transparent。selected 走 ACCENT 通道；选中项 hover/highlight
     * 继续走 ACCENT 变体以保留可见交互反馈；未选中时 highlighted/hovered 走 Slate 提亮通道。</p>
     *
     * @param enabled     是否启用
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮（未选中时由方向键移动产生）
     * @param hovered     是否指针悬停
     * @return 对应 item 背景色 token；默认态返回全透明（0x00000000，无填充语义）
     */
    public static int listItemBackground(boolean enabled, boolean selected, boolean highlighted, boolean hovered) {
        if (!enabled) {
            return SceneChromeTokens.BG_DISABLED;
        }
        if (selected) {
            if (hovered) {
                return SceneChromeTokens.ACCENT_HOVER;
            }
            if (highlighted) {
                return SceneChromeTokens.ACCENT_PRESSED;
            }
            return SceneChromeTokens.ACCENT;
        }
        if (highlighted) {
            return SceneChromeTokens.BG_DEFAULT;
        }
        if (hovered) {
            return SceneChromeTokens.BG_HOVER;
        }
        return 0x00000000;
    }

    /**
     * 错误行背景色查表。
     *
     * @param invalid 是否为校验失败行
     * @return 失败行返回危险弱提示底色（半透明红），正常行返回透明
     */
    public static int errorRowBackground(boolean invalid) {
        return invalid ? SceneChromeTokens.DANGER_BG_SUBTLE : 0x00000000;
    }

    /**
     * Link 变体背景（Breadcrumb 段按钮等导航链接）。
     * 默认透明融入容器，hover/pressed 走标准灰档，focused 不加背景
     * （focus 指示靠 {@link #linkText} 文本色提亮到 ACCENT_HOVER，避免背景与文本同色导致文本消失），
     * disabled 保持透明。
     * 优先级：disabled > pressed > hovered > default(透明)。
     *
     * @param enabled 是否启用
     * @param hovered 是否悬停
     * @param pressed 是否按下
     * @param focused 是否聚焦（保留参数兼容性，不影响背景）
     * @return 对应背景色 token（默认/禁用/focused 态返回 0 透明）
     */
    public static int linkBackground(boolean enabled, boolean hovered, boolean pressed, boolean focused) {
        if (!enabled) {
            return 0;
        }
        if (pressed) {
            return SceneChromeTokens.BG_PRESSED;
        }
        if (hovered) {
            return SceneChromeTokens.BG_HOVER;
        }
        return 0;
    }

    /**
     * Link 变体文本（Breadcrumb 段文本等导航链接）。
     * enabled 用 ACCENT 蓝（暗示可点），focused 提亮到 ACCENT_HOVER，disabled 走 TEXT_DISABLED。
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return 对应文本色 token
     */
    public static int linkText(boolean enabled, boolean focused) {
        if (!enabled) {
            return SceneChromeTokens.TEXT_DISABLED;
        }
        if (focused) {
            return SceneChromeTokens.ACCENT_HOVER;
        }
        return SceneChromeTokens.ACCENT;
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
