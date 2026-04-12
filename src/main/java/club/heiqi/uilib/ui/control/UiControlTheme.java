package club.heiqi.uilib.ui.control;

/**
 * 基础表单控件默认主题。
 *
 * <p>控件语义与交互保留在具体控件类中，默认皮肤与尺寸常量集中收口在本类。</p>
 */
public final class UiControlTheme {

    private static final BoxState BUTTON_NORMAL_STATE = new BoxState(0xDD243041, 0xFF44556E, 0x335D86C5);
    private static final BoxState BUTTON_HOVERED_STATE = new BoxState(0xDD2C3B51, 0xFF5B7293, 0x336891D0);
    private static final BoxState BUTTON_FOCUSED_STATE = new BoxState(0xDD2A4161, 0xFF9CC3FF, 0x447EB1FF);
    private static final BoxState BUTTON_PRESSED_STATE = new BoxState(0xDD1D2938, 0xFF3B4B62, 0x22486EA7);
    private static final ButtonStyle DEFAULT_BUTTON_STYLE = new ButtonStyle(
            BUTTON_NORMAL_STATE,
            BUTTON_HOVERED_STATE,
            BUTTON_FOCUSED_STATE,
            BUTTON_PRESSED_STATE,
            0xFFF7FAFF,
            148,
            92,
            36,
            24,
            38,
            1,
            2
    );

    private static final BoxState TOGGLE_NORMAL_STATE = new BoxState(0xD910151D, 0xFF35465D, 0);
    private static final BoxState TOGGLE_HOVERED_STATE = new BoxState(0xD910151D, 0xFF607697, 0);
    private static final BoxState TOGGLE_FOCUSED_STATE = new BoxState(0xDD131B26, 0xFF89B4FF, 0);
    private static final BoxState TOGGLE_TRACK_OFF_STATE = new BoxState(0xFF2A3442, 0xFF53657D, 0);
    private static final BoxState TOGGLE_TRACK_ON_STATE = new BoxState(0xFF2F78E6, 0xFF8EC0FF, 0);
    private static final ToggleSwitchStyle DEFAULT_TOGGLE_SWITCH_STYLE = new ToggleSwitchStyle(
            TOGGLE_NORMAL_STATE,
            TOGGLE_HOVERED_STATE,
            TOGGLE_FOCUSED_STATE,
            TOGGLE_TRACK_OFF_STATE,
            TOGGLE_TRACK_ON_STATE,
            0x1A607697,
            0x223A84E5,
            0xFFB8C5D8,
            0xFFF3F7FF,
            0xFFF7FAFF,
            184,
            124,
            84,
            74,
            38,
            10,
            10,
            14,
            46,
            22,
            18,
            1,
            2
    );

    private static final BoxState SEGMENTED_NORMAL_STATE = new BoxState(0xD910151D, 0xFF35465D, 0x1A607697);
    private static final BoxState SEGMENTED_FOCUSED_STATE = new BoxState(0xD910151D, 0xFF89B4FF, 0x337EB1FF);
    private static final SegmentedSelectorStyle DEFAULT_SEGMENTED_SELECTOR_STYLE = new SegmentedSelectorStyle(
            SEGMENTED_NORMAL_STATE,
            SEGMENTED_FOCUSED_STATE,
            0xFF2E3A4A,
            0xFF2B4F7C,
            0xFF315E94,
            0x332F435D,
            0xFFF7FAFF,
            0xFFE4ECFF,
            0xFFB7C3D6,
            220,
            140,
            28,
            16,
            38,
            6,
            2,
            12
    );

    private static final BoxState TEXT_INPUT_NORMAL_STATE = new BoxState(0xD910151D, 0xFF35465D, 0x1A607697);
    private static final BoxState TEXT_INPUT_HOVERED_STATE = new BoxState(0xD910151D, 0xFF607697, 0x1A607697);
    private static final BoxState TEXT_INPUT_FOCUSED_STATE = new BoxState(0xE6131A24, 0xFF89B4FF, 0x337EB1FF);
    private static final TextInputStyle DEFAULT_TEXT_INPUT_STYLE = new TextInputStyle(
            TEXT_INPUT_NORMAL_STATE,
            TEXT_INPUT_HOVERED_STATE,
            TEXT_INPUT_FOCUSED_STATE,
            0xFFF3F7FF,
            0xFF7E8A9D,
            0xFFD6E5FF,
            280,
            140,
            36,
            38,
            12,
            2,
            8,
            8
    );
    private static final ScrollbarStyle DEFAULT_SCROLLBAR_STYLE = new ScrollbarStyle(
            0x552B3647,
            0x66344155,
            0xFF8FB3FF,
            0xFFB8D0FF,
            0xFFD1E2FF
    );
    private static final LabelStyle DEFAULT_LABEL_STYLE = new LabelStyle(0xFFFFFFFF, true);

    private static final InventorySlotGridStyle DEFAULT_INVENTORY_SLOT_GRID_STYLE = new InventorySlotGridStyle(
            0xAA171C24,
            0xFF465468,
            0xCC202A38,
            0xFF9AB8F2
    );

    private UiControlTheme() {}

    /**
     * 获取默认按钮样式。
     *
     * @return 默认按钮样式
     */
    public static ButtonStyle defaultButtonStyle() {
        return DEFAULT_BUTTON_STYLE;
    }

    /**
     * 获取默认开关样式。
     *
     * @return 默认开关样式
     */
    public static ToggleSwitchStyle defaultToggleSwitchStyle() {
        return DEFAULT_TOGGLE_SWITCH_STYLE;
    }

    /**
     * 获取默认分段选择器样式。
     *
     * @return 默认分段选择器样式
     */
    public static SegmentedSelectorStyle defaultSegmentedSelectorStyle() {
        return DEFAULT_SEGMENTED_SELECTOR_STYLE;
    }

    /**
     * 获取默认文本输入框样式。
     *
     * @return 默认文本输入框样式
     */
    public static TextInputStyle defaultTextInputStyle() {
        return DEFAULT_TEXT_INPUT_STYLE;
    }

    /**
     * 获取默认滚动条样式。
     *
     * @return 默认滚动条样式
     */
    public static ScrollbarStyle defaultScrollbarStyle() {
        return DEFAULT_SCROLLBAR_STYLE;
    }

    /**
     * 获取默认标签样式。
     *
     * @return 默认标签样式
     */
    public static LabelStyle defaultLabelStyle() {
        return DEFAULT_LABEL_STYLE;
    }

    /**
     * 获取默认背包格子网格样式。
     *
     * @return 默认网格样式
     */
    public static InventorySlotGridStyle defaultInventorySlotGridStyle() {
        return DEFAULT_INVENTORY_SLOT_GRID_STYLE;
    }

    /**
     * 盒模型状态样式。
     */
    public static final class BoxState {

        public final int fillColor;
        public final int borderColor;
        public final int accentColor;

        public BoxState(int fillColor, int borderColor, int accentColor) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.accentColor = accentColor;
        }
    }

    /**
     * 按钮样式。
     */
    public static final class ButtonStyle {

        public final BoxState normalState;
        public final BoxState hoveredState;
        public final BoxState focusedState;
        public final BoxState pressedState;
        public final int textColor;
        public final int preferredMinWidth;
        public final int minContentWidthFloor;
        public final int preferredExtraWidth;
        public final int minExtraWidth;
        public final int height;
        public final int accentInsetTop;
        public final int accentInsetHeight;

        public ButtonStyle(BoxState normalState, BoxState hoveredState, BoxState focusedState, BoxState pressedState,
                int textColor, int preferredMinWidth, int minContentWidthFloor, int preferredExtraWidth,
                int minExtraWidth, int height, int accentInsetTop, int accentInsetHeight) {
            this.normalState = normalState;
            this.hoveredState = hoveredState;
            this.focusedState = focusedState;
            this.pressedState = pressedState;
            this.textColor = textColor;
            this.preferredMinWidth = preferredMinWidth;
            this.minContentWidthFloor = minContentWidthFloor;
            this.preferredExtraWidth = preferredExtraWidth;
            this.minExtraWidth = minExtraWidth;
            this.height = height;
            this.accentInsetTop = accentInsetTop;
            this.accentInsetHeight = accentInsetHeight;
        }
    }

    /**
     * 开关样式。
     */
    public static final class ToggleSwitchStyle {

        public final BoxState normalState;
        public final BoxState hoveredState;
        public final BoxState focusedState;
        public final BoxState uncheckedTrackState;
        public final BoxState checkedTrackState;
        public final int uncheckedAccentColor;
        public final int checkedAccentColor;
        public final int uncheckedTextColor;
        public final int checkedTextColor;
        public final int thumbColor;
        public final int preferredMinWidth;
        public final int minContentWidthFloor;
        public final int preferredExtraWidth;
        public final int minExtraWidth;
        public final int height;
        public final int contentPaddingLeft;
        public final int contentPaddingRight;
        public final int textTrackGap;
        public final int trackWidth;
        public final int trackHeight;
        public final int thumbWidth;
        public final int accentInsetTop;
        public final int accentInsetHeight;

        public ToggleSwitchStyle(BoxState normalState, BoxState hoveredState, BoxState focusedState,
                BoxState uncheckedTrackState, BoxState checkedTrackState, int uncheckedAccentColor,
                int checkedAccentColor, int uncheckedTextColor, int checkedTextColor, int thumbColor,
                int preferredMinWidth, int minContentWidthFloor, int preferredExtraWidth, int minExtraWidth,
                int height, int contentPaddingLeft, int contentPaddingRight, int textTrackGap, int trackWidth,
                int trackHeight, int thumbWidth, int accentInsetTop, int accentInsetHeight) {
            this.normalState = normalState;
            this.hoveredState = hoveredState;
            this.focusedState = focusedState;
            this.uncheckedTrackState = uncheckedTrackState;
            this.checkedTrackState = checkedTrackState;
            this.uncheckedAccentColor = uncheckedAccentColor;
            this.checkedAccentColor = checkedAccentColor;
            this.uncheckedTextColor = uncheckedTextColor;
            this.checkedTextColor = checkedTextColor;
            this.thumbColor = thumbColor;
            this.preferredMinWidth = preferredMinWidth;
            this.minContentWidthFloor = minContentWidthFloor;
            this.preferredExtraWidth = preferredExtraWidth;
            this.minExtraWidth = minExtraWidth;
            this.height = height;
            this.contentPaddingLeft = contentPaddingLeft;
            this.contentPaddingRight = contentPaddingRight;
            this.textTrackGap = textTrackGap;
            this.trackWidth = trackWidth;
            this.trackHeight = trackHeight;
            this.thumbWidth = thumbWidth;
            this.accentInsetTop = accentInsetTop;
            this.accentInsetHeight = accentInsetHeight;
        }
    }

    /**
     * 分段选择器样式。
     */
    public static final class SegmentedSelectorStyle {

        public final BoxState normalState;
        public final BoxState focusedState;
        public final int dividerColor;
        public final int selectedFillColor;
        public final int focusedSelectedFillColor;
        public final int hoveredSegmentFillColor;
        public final int selectedTextColor;
        public final int hoveredTextColor;
        public final int textColor;
        public final int preferredMinWidth;
        public final int minContentWidthFloor;
        public final int preferredOptionExtraWidth;
        public final int minOptionExtraWidth;
        public final int height;
        public final int dividerInset;
        public final int segmentInset;
        public final int segmentTextPadding;

        public SegmentedSelectorStyle(BoxState normalState, BoxState focusedState, int dividerColor,
                int selectedFillColor, int focusedSelectedFillColor, int hoveredSegmentFillColor,
                int selectedTextColor, int hoveredTextColor, int textColor, int preferredMinWidth,
                int minContentWidthFloor, int preferredOptionExtraWidth, int minOptionExtraWidth, int height,
                int dividerInset, int segmentInset, int segmentTextPadding) {
            this.normalState = normalState;
            this.focusedState = focusedState;
            this.dividerColor = dividerColor;
            this.selectedFillColor = selectedFillColor;
            this.focusedSelectedFillColor = focusedSelectedFillColor;
            this.hoveredSegmentFillColor = hoveredSegmentFillColor;
            this.selectedTextColor = selectedTextColor;
            this.hoveredTextColor = hoveredTextColor;
            this.textColor = textColor;
            this.preferredMinWidth = preferredMinWidth;
            this.minContentWidthFloor = minContentWidthFloor;
            this.preferredOptionExtraWidth = preferredOptionExtraWidth;
            this.minOptionExtraWidth = minOptionExtraWidth;
            this.height = height;
            this.dividerInset = dividerInset;
            this.segmentInset = segmentInset;
            this.segmentTextPadding = segmentTextPadding;
        }
    }

    /**
     * 文本输入框样式。
     */
    public static final class TextInputStyle {

        public final BoxState normalState;
        public final BoxState hoveredState;
        public final BoxState focusedState;
        public final int textColor;
        public final int placeholderColor;
        public final int caretColor;
        public final int preferredMinWidth;
        public final int minContentWidthFloor;
        public final int preferredExtraWidth;
        public final int height;
        public final int textHorizontalPadding;
        public final int caretWidth;
        public final int caretRightInset;
        public final int caretVerticalInset;

        public TextInputStyle(BoxState normalState, BoxState hoveredState, BoxState focusedState, int textColor,
                int placeholderColor, int caretColor, int preferredMinWidth, int minContentWidthFloor,
                int preferredExtraWidth, int height, int textHorizontalPadding, int caretWidth,
                int caretRightInset, int caretVerticalInset) {
            this.normalState = normalState;
            this.hoveredState = hoveredState;
            this.focusedState = focusedState;
            this.textColor = textColor;
            this.placeholderColor = placeholderColor;
            this.caretColor = caretColor;
            this.preferredMinWidth = preferredMinWidth;
            this.minContentWidthFloor = minContentWidthFloor;
            this.preferredExtraWidth = preferredExtraWidth;
            this.height = height;
            this.textHorizontalPadding = textHorizontalPadding;
            this.caretWidth = caretWidth;
            this.caretRightInset = caretRightInset;
            this.caretVerticalInset = caretVerticalInset;
        }
    }

    /**
     * 滚动条样式。
     */
    public static final class ScrollbarStyle {

        public final int trackColor;
        public final int hoveredTrackColor;
        public final int thumbColor;
        public final int hoveredThumbColor;
        public final int draggingThumbColor;

        public ScrollbarStyle(int trackColor, int hoveredTrackColor, int thumbColor, int hoveredThumbColor,
                int draggingThumbColor) {
            this.trackColor = trackColor;
            this.hoveredTrackColor = hoveredTrackColor;
            this.thumbColor = thumbColor;
            this.hoveredThumbColor = hoveredThumbColor;
            this.draggingThumbColor = draggingThumbColor;
        }
    }

    /**
     * 标签样式。
     */
    public static final class LabelStyle {

        public final int textColor;
        public final boolean shadow;

        public LabelStyle(int textColor, boolean shadow) {
            this.textColor = textColor;
            this.shadow = shadow;
        }
    }

    /**
     * 背包格子网格样式。
     */
    public static final class InventorySlotGridStyle {

        public final int emptySlotFillColor;
        public final int emptySlotBorderColor;
        public final int occupiedSlotFillColor;
        public final int occupiedSlotBorderColor;

        public InventorySlotGridStyle(int emptySlotFillColor, int emptySlotBorderColor, int occupiedSlotFillColor,
                int occupiedSlotBorderColor) {
            this.emptySlotFillColor = emptySlotFillColor;
            this.emptySlotBorderColor = emptySlotBorderColor;
            this.occupiedSlotFillColor = occupiedSlotFillColor;
            this.occupiedSlotBorderColor = occupiedSlotBorderColor;
        }
    }
}
