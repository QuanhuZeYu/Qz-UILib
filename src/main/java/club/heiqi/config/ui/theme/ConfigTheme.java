package club.heiqi.config.ui.theme;

import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * 配置页 UI 主题常量收口。
 *
 * <p>不新立主题引擎，仅委托 {@link SceneChromeTokens} 与本地配色常量，
 * 供 config.ui 包内 {@code ConfigScreen} 与各 {@code FieldRenderer} 共享视觉口径。</p>
 *
 * <p><b>访问说明</b>：概念上仅 config.ui 内部使用；因 Java 跨包访问限制
 * （{@code ConfigScreen} 在 {@code config.ui}，{@code FieldRenderer} 实现在
 * {@code config.ui.field}），常量需跨包可见，故类与常量设为 public。
 * 不属于对外公开 API，后续主题化时可在本类统一收口。</p>
 */
public final class ConfigTheme {

    /** 卡片圆角（大容器档） */
    public static final int CARD_RADIUS = SceneChromeTokens.RADIUS_LG;
    /** 字段卡片间距 */
    public static final int FIELD_GAP = SceneChromeTokens.GAP_MD;
    /** 卡片内边距（宽松档） */
    public static final int CARD_PAD = SceneChromeTokens.PAD_LG;
    /** 输入框行高 */
    public static final int INPUT_HEIGHT = SceneChromeTokens.INPUT_HEIGHT;
    /** 按钮高度 */
    public static final int BUTTON_HEIGHT = SceneChromeTokens.BUTTON_HEIGHT;
    /** 按钮宽度 */
    public static final int BUTTON_WIDTH = 110;

    /** 根背景色（深蓝黑） */
    public static final int ROOT_BG = 0xFF0B1424;
    /** 视口背景色（更深档） */
    public static final int VIEWPORT_BG = 0xFF081120;
    /** 卡片背景色 */
    public static final int CARD_BG = 0xFF0D1728;
    /** 卡片默认边框 */
    public static final int CARD_BORDER = 0xFF2F4D87;
    /** 卡片脏态边框（蓝提亮） */
    public static final int CARD_BORDER_DIRTY = 0xFF3B5BA5;
    /** 卡片错误边框（红） */
    public static final int CARD_BORDER_ERROR = 0xFFF87171;

    /** 标题文本色 */
    public static final int TITLE_COLOR = 0xFFC9D8F8;
    /** 正文文本色 */
    public static final int TEXT_COLOR = 0xFFEAF1FF;
    /** 次要文本色（helper/副标题） */
    public static final int MUTED_COLOR = 0xFF8AA0C8;
    /** 错误文本色 */
    public static final int ERROR_COLOR = 0xFFF87171;
    /** 正常态文本色（绿） */
    public static final int OK_COLOR = 0xFF34D399;
    /** 脏态文本色（蓝） */
    public static final int DIRTY_COLOR = 0xFF60A5FA;
    /** 徽标底色 */
    public static final int READOUT_BG = 0xFF1E293B;

    /** 标题条固定高度（压缩后） */
    public static final int TITLE_BAR_HEIGHT = 32;
    /** 状态摘要条固定高度（压缩后） */
    public static final int STATUS_HEIGHT = 24;
    /** 操作条固定高度（压缩后） */
    public static final int ACTION_BAR_HEIGHT = 36;
    /** 根容器内边距（压缩后，原 20） */
    public static final int ROOT_PADDING = 12;
    /** 根容器子节点间距（压缩后，原 12） */
    public static final int ROOT_GAP = 8;
    /** 滚动容器内 viewport 与 scrollbar 列间距（M3，原 0） */
    public static final int SCROLL_GAP = 3;

    // ===== 字号梯度 token（S1，UI 像素）=====
    /** 页标题字号（titleBar 主标题） */
    public static final int FONT_TITLE = 22;
    /** section 标题/导航字号 */
    public static final int FONT_SECTION = 18;
    /** 字段 label / 按钮文案字号 */
    public static final int FONT_LABEL = 16;
    /** helper text 字号 */
    public static final int FONT_HELPER = 13;
    /** error text 字号 */
    public static final int FONT_ERROR = 13;
    /** 按钮文案字号 */
    public static final int FONT_BUTTON = 16;
    /** 徽标字号 */
    public static final int FONT_BADGE = 12;
    /** titleBar 副标题（modId）字号 */
    public static final int FONT_SUBTITLE = 12;
    /** slider 读数字号 */
    public static final int FONT_READOUT = 14;

    /** 纯常量类，禁止实例化 */
    private ConfigTheme() {
    }
}
