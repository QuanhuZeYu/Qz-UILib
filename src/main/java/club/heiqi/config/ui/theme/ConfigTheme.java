package club.heiqi.config.ui.theme;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.form.FormThemes;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 配置页 UI 主题的「配置包内消费入口」：双路径，与 {@code FormTheme}/{@code FormThemes}
 * 的 G11 双路径同构。
 *
 * <p><b>默认路径</b>（全库液态玻璃外观）：{@link #asFormTheme(SceneRuntime)} 经
 * {@link FormThemes} 既有桥把当前来源主题（页面 {@code SceneThemes.withTheme} 局部主题 &gt;
 * runtime 默认主题 &gt; 库默认液态玻璃档）派生为 {@link FormTheme}，主题切换自动重派生；
 * 页壳语义色同样直接经 {@link club.heiqi.uilib.ui.scene.theme.SceneThemes} 的冻结便捷入口消费。
 * 本类<b>不是</b>颜色权威：不复制、不改写任何色板，只做配置包内的消费收口。</p>
 *
 * <p><b>显式路径</b>（旧语义，完整保留）：本类静态常量与无参 {@link #asFormTheme()} 保持
 * 「配置包内共享的显式旧主题」语义——固定值、不感知主题切换，供尚未迁移的调用点继续使用
 * （契约 §4.2：{@code FormTheme}/{@code ConfigTheme} 保留旧显式主题语义；新增默认解析走主题）。
 * {@code ConfigScreen} 与字段渲染器按 G15 序列在后续实例逐个转入默认路径。</p>
 *
 * <p><b>方向纪律</b>：配置包只消费 UILib 主题/模板，不反向成为主题中心——本类不得被
 * {@code ui.scene.theme}/{@code ui.scene.form} 引用，桥接逻辑一律复用既有
 * {@link FormThemes}，不新建第二份解析。</p>
 *
 * <p><b>访问说明</b>：概念上仅 config.ui 内部使用；因 Java 跨包访问限制
 * （{@code ConfigScreen} 在 {@code config.ui}，{@code FieldRenderer} 实现在
 * {@code config.ui.field}），常量需跨包可见，故类与常量设为 public。
 * 不属于对外公开 API。</p>
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

    /** 全屏 Material 暗色遮罩（80% 不透明），世界画面可在其后连续透出。 */
    public static final int ROOT_BG = 0xCC111318;
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

    /** Material fast Motion。 */
    public static final int MOTION_FAST_MS = SceneChromeTokens.MOTION_FAST_MS;
    /** Material standard Motion。 */
    public static final int MOTION_STANDARD_MS = SceneChromeTokens.MOTION_STANDARD_MS;
    /** Material emphasized Motion。 */
    public static final int MOTION_EMPHASIZED_MS = SceneChromeTokens.MOTION_EMPHASIZED_MS;

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
     * <p><b>显式旧主题</b>：返回缓存的 {@link FormTheme#defaultDark()} 固定值，
     * <b>不感知主题切换</b>（语义自 G11 前保持原样，未随默认路径迁移而改写）；
     * 需要跟随主题的默认外观请用 {@link #asFormTheme(SceneRuntime)}。</p>
     *
     * @return 深色档 FormTheme 实例（缓存，恒同一对象）
     */
    public static FormTheme asFormTheme() {
        return FORM_THEME;
    }

    /**
     * 默认路径：把当前来源 {@code SceneTheme} 经 {@link FormThemes} 既有桥派生为
     * 配置页消费的 {@link FormTheme} 信号（G15/Theme 新增，全库默认液态玻璃外观）。
     *
     * <p>解析优先级与语义色/表面映射口径全部由 {@link FormThemes#resolve(SceneRuntime)}
     * 单点定义（页面局部主题 &gt; runtime 默认 &gt; 库默认液态玻璃档）；本方法零色板、
     * 零第二套解析，只是配置包内的消费入口收口。主题切换后信号自动重派生，不重建节点。</p>
     *
     * <p><b>构造期调用</b>：与 {@link FormThemes#resolve(SceneRuntime)} 同一纪律——必须在构建期
     * （{@code mount/show/forEach/portal} 的 builder 内）调用，此时 {@code Owner.current()}
     * 是来源作用域，局部/页面主题才能被捕获；无 Owner 时经 runtime 根作用域解析并安全回落库默认。
     * 与无参 {@link #asFormTheme()} 的显式旧路径并存、互不感知。</p>
     *
     * @param rt 目标 runtime，不可为 null（null 抛 {@link NullPointerException}，不静默降级）
     * @return 表单主题只读信号（恒非 null，其值恒非 null）
     */
    public static ReadableSignal<FormTheme> asFormTheme(SceneRuntime rt) {
        return FormThemes.resolve(rt);
    }

    /** 纯常量类，禁止实例化 */
    private ConfigTheme() {
    }
}
