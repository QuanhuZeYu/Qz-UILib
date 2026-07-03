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
    public static final int BUTTON_WIDTH = 110;

    /** 根背景色（深蓝黑） */
    public static final int ROOT_BG = 0xFF0B1424;
    /** 视口背景色（更深档） */
    public static final int VIEWPORT_BG = 0xFF081120;

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
    /** save 反馈独立行固定高度（与 STATUS_HEIGHT 同档，守 grow 求解器不早退） */
    public static final int SAVE_FEEDBACK_HEIGHT = 24;
    /** 横向 Tab 导航段内边距（与 {@code SceneSegmented.SEGMENT_PADDING} 对齐，PAD_LG=10） */
    public static final int NAV_TAB_PADDING = SceneChromeTokens.PAD_LG;
    /** 横向 Tab 段标签字号（与 {@code SceneSegmented.SEG_LABEL_FONT_SIZE} 对齐） */
    public static final int NAV_TAB_FONT_SIZE = 16;
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
