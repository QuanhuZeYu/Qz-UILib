package club.heiqi.config.ui.theme;

import club.heiqi.uilib.ui.scene.form.FormTheme;
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

    /** 字段卡片间距 */
    public static final int FIELD_GAP = SceneChromeTokens.GAP_MD;
    /** 输入框行高 */
    public static final int INPUT_HEIGHT = SceneChromeTokens.INPUT_HEIGHT;
    /** 按钮高度 */
    public static final int BUTTON_HEIGHT = SceneChromeTokens.BUTTON_HEIGHT;
    /** 按钮宽度 */
    public static final int BUTTON_WIDTH = 108;

    /** Material dark surface。 */
    public static final int ROOT_BG = 0xFF111318;
    /** Material dark surface container。 */
    public static final int VIEWPORT_BG = 0xFF1B1B1F;
    /** 导航与底部操作区的 tonal surface。 */
    public static final int SURFACE_CONTAINER = 0xFF211F26;
    /** 字段与状态读数的高一级 tonal surface。 */
    public static final int SURFACE_CONTAINER_HIGH = 0xFF2B2930;

    /** 标题文本色 */
    public static final int TITLE_COLOR = 0xFFE6E1E5;
    /** 正文文本色 */
    public static final int TEXT_COLOR = 0xFFE6E1E5;
    /** 次要文本色（helper/副标题） */
    public static final int MUTED_COLOR = 0xFFCAC4D0;
    /** 错误文本色 */
    public static final int ERROR_COLOR = 0xFFFFB4AB;
    /** 正常态文本色 */
    public static final int OK_COLOR = 0xFFA8DAB5;
    /** 脏态文本色（primary） */
    public static final int DIRTY_COLOR = 0xFFD0BCFF;
    /** 徽标底色 */
    public static final int READOUT_BG = SURFACE_CONTAINER_HIGH;

    /** 页面内容最大宽度。 */
    public static final int PAGE_MAX_WIDTH = 1120;
    /** 单列内容最大宽度。 */
    public static final int CONTENT_MAX_WIDTH = 860;
    /** 固定左侧 section navigation 宽度。 */
    public static final int NAV_PANE_WIDTH = 196;

    /** 标题条固定高度（压缩后） */
    public static final int TITLE_BAR_HEIGHT = 44;
    /** 状态摘要条固定高度（压缩后） */
    public static final int STATUS_HEIGHT = 28;
    /** 操作条固定高度（压缩后） */
    public static final int ACTION_BAR_HEIGHT = 46;
    /** save 反馈独立行固定高度（与 STATUS_HEIGHT 同档，守 grow 求解器不早退） */
    public static final int SAVE_FEEDBACK_HEIGHT = 24;
    /** 横向 Tab 导航段内边距（与 {@code SceneSegmented.SEGMENT_PADDING} 对齐，PAD_LG=10） */
    public static final int NAV_TAB_PADDING = SceneChromeTokens.PAD_LG;
    /** 横向 Tab 段标签字号（与 {@code SceneSegmented.SEG_LABEL_FONT_SIZE} 对齐） */
    public static final int NAV_TAB_FONT_SIZE = 16;
    /** 根容器内边距（压缩后，原 20） */
    public static final int ROOT_PADDING = 16;
    /** 根容器子节点间距（压缩后，原 12） */
    public static final int ROOT_GAP = 10;
    /** 滚动容器内 viewport 与 scrollbar 列间距（M3，原 0） */
    public static final int SCROLL_GAP = 3;

    // ===== 字号梯度 token（S1，UI 像素）=====
    /** 页标题字号（titleBar 主标题） */
    public static final int FONT_TITLE = 24;
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

    /** 桥接 FormTheme 缓存实例：字段卡片相关通用 token 与 {@link FormTheme#defaultDark()} 对齐 */
    private static final FormTheme FORM_THEME = FormTheme.defaultDark();

    /**
     * 桥接获取 uilib.form 通用主题 token，供 4 个 FieldRenderer 调
     * {@link club.heiqi.uilib.ui.scene.form.FormFieldShell#build} 时传入。
     *
     * <p>config.ui 是 uilib.form 的适配层，主题 token 仍由本类收口，经此方法转为
     * uilib.form 的 {@link FormTheme} 形态下沉给字段外壳。</p>
     *
     * @return 深色档 FormTheme 实例
     */
    public static FormTheme asFormTheme() {
        return FORM_THEME;
    }

    /** 纯常量类，禁止实例化 */
    private ConfigTheme() {
    }
}
