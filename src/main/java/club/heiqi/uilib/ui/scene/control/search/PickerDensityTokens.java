package club.heiqi.uilib.ui.scene.control.search;

/**
 * PickerDensityTokens —— 选择器尺寸/密度派生的<b>唯一比例常量表</b>（P5 §4.2 A）。
 *
 * <h3>为什么是"比例常量"而不是"尺寸表"</h3>
 * <p>本类只登记<b>比例、除数、夹取边界</b>；任何"某分辨率下的确定像素值"都不在这里 ——
 * 那些值必须由 {@link PickerMetrics} 从「逻辑盒 + 字号 + 密度档」派生。
 * 把 {@code 48 / 248 / 168 / 64} 这类确定值写进令牌表，等于把静态假定换个地方藏起来
 * （用户工程偏好「不假定静态」红线）。</p>
 *
 * <h3>唯一允许的确定像素值</h3>
 * <ul>
 *   <li><b>夹取边界</b>（clamp 上下界）：它们本身是"可读性下限 / 空间上限"的语义，
 *       且参与派生，不是布局快照；</li>
 *   <li><b>{@code LEGACY_*} 现状基线常量</b>：只服务 {@link PickerMetrics#legacyBaselineItems}
 *       的<b>支配性对照</b>（红线 R1/R2「不得低于现状」的参照物），<b>不参与任何布局计算</b>。
 *       它们一旦被用于布局即属违约。</li>
 * </ul>
 *
 * <h3>失效通道（P5 §4.3 铁律 5：无失效通道的令牌禁止新增）</h3>
 * <p>本类全部常量是 {@code static final} <b>编译期比例</b>，不是可变量，因此不需要失效通道：
 * 它们不随任何运行期状态变化。真正需要失效通道的是<i>由它们派生的结果</i>，
 * 该职责由 {@link PickerMetrics} 的输入信号（逻辑盒 / 字号倍率 / 密度偏好）承担。</p>
 */
public final class PickerDensityTokens {

    private PickerDensityTokens() {
    }

    // ==================== 面板盒（P5 §1.2） ====================

    /** 面板距逻辑盒边缘的最小留白（逻辑 px）。 */
    public static final int PANEL_MARGIN = 8;
    /** 面板占逻辑盒比例阶梯（%）：从小到大，先满足软目标、再满足硬目标。 */
    public static final int[] PANEL_RATIO_STEPS = {70, 78, 84, 92};
    /** 小盒降级模式的面板比例（%）：满屏留 {@link #PANEL_MARGIN}。 */
    public static final int PANEL_RATIO_SMALL = 100;
    /** 小盒降级模式的门槛：逻辑盒宽/高任一低于该值即进入降级。 */
    public static final int SMALL_BOX_MIN_WIDTH = 1280;
    /** 小盒降级模式的门槛：逻辑盒高低于该值即进入降级。 */
    public static final int SMALL_BOX_MIN_HEIGHT = 720;
    /** 面板内边距（逻辑 px，面板根 padding 与 gap 同值）。 */
    public static final int PANEL_PADDING = 8;
    /** 顶栏高比例（× 字号）。 */
    public static final double HEADER_RATIO = 3.67;
    /** 顶栏高下限（逻辑 px）。 */
    public static final int HEADER_MIN = 36;
    /** 顶栏高上限（逻辑 px）。 */
    public static final int HEADER_MAX = 64;
    /** 左导航宽比例（× 逻辑盒宽）。 */
    public static final double NAV_RATIO = 0.13;
    /** 左导航宽下限（逻辑 px）。 */
    public static final int NAV_MIN = 96;
    /** 左导航宽上限（逻辑 px）。 */
    public static final int NAV_MAX = 188;
    /** 小盒降级时导航宽比例（× 逻辑盒宽）。 */
    public static final double NAV_RATIO_SMALL = 0.18;
    /** 小盒降级时导航宽下限（逻辑 px）。 */
    public static final int NAV_MIN_SMALL = 72;
    /** 成员卡最多显示行数（超出滚动）。 */
    public static final int MEMBER_ROWS_MAX = 2;
    /** 成员卡高比例（× 字号）。 */
    public static final double MEMBER_CARD_RATIO = 8.0;
    /** 成员卡高下限（逻辑 px）。 */
    public static final int MEMBER_CARD_MIN = 72;
    /** 成员卡高上限（逻辑 px）。 */
    public static final int MEMBER_CARD_MAX = 120;
    /** 成员卡间距比例（× 字号）。 */
    public static final double MEMBER_GAP_RATIO = 0.66;
    /** 成员卡间距下限（逻辑 px）。 */
    public static final int MEMBER_GAP_MIN = 4;
    /** 成员卡间距上限（逻辑 px）。 */
    public static final int MEMBER_GAP_MAX = 10;
    /** 成员带折叠态（无成员）提示行高比例（× 字号）。 */
    public static final double MEMBERS_EMPTY_RATIO = 2.67;

    // ==================== 网格几何（P5 §2.1） ====================

    /** 字号下限（逻辑 px）。 */
    public static final int FONT_FLOOR = 11;
    /** 字号上限（逻辑 px）。 */
    public static final int FONT_CEIL = 24;
    /** 图标最小边长（逻辑 px；{@code k} 缩放的兜底下限）。 */
    public static final int ICON_MIN = 16;
    /** 单元内边距比例（fs / 3）。 */
    public static final double CELL_PAD_DIVISOR = 3.0;
    /** 单元内边距下限（逻辑 px）。 */
    public static final int CELL_PAD_MIN = 2;
    /** 单元内边距上限（逻辑 px）。 */
    public static final int CELL_PAD_MAX = 6;
    /** 图标与标签间距除数（fs / 6）。 */
    public static final double LABEL_GAP_DIVISOR = 6.0;
    /** 列/行间距比例（× 字号）。 */
    public static final double CELL_GAP_RATIO = 0.5;
    /** 列/行间距下限（逻辑 px）。 */
    public static final int CELL_GAP_MIN = 3;
    /** 列/行间距上限（逻辑 px）。 */
    public static final int CELL_GAP_MAX = 10;
    /** cellW 宽度下限的实测字符样本（4 字符实测宽；度量口径同源，见 P5 I-5）。 */
    public static final String CELL_WIDTH_SAMPLE = "MMMM";

    // ==================== 面板内其它随字号缩放的元素（P5 §2.5） ====================

    /** 搜索输入框宽度占顶栏比例（%）：挂载前预算的一部分，不随字号变。 */
    public static final int SEARCH_INPUT_WIDTH_PERCENT = 35;
    /** 成员卡宽比例（× 字号）。 */
    public static final double MEMBER_CARD_WIDTH_RATIO = 16.0;
    /** 成员卡宽下限（逻辑 px）。 */
    public static final int MEMBER_CARD_WIDTH_MIN = 176;
    /** 成员卡宽上限（逻辑 px）。 */
    public static final int MEMBER_CARD_WIDTH_MAX = 232;
    /** 成员卡图标比例（× 字号）。 */
    public static final double MEMBER_ICON_RATIO = 2.0;
    /** 变体行高比例（× 字号）。 */
    public static final double VARIANT_ROW_RATIO = 2.67;
    /** 变体行高下限（逻辑 px）。 */
    public static final int VARIANT_ROW_MIN = 26;
    /** 变体行高上限（逻辑 px）。 */
    public static final int VARIANT_ROW_MAX = 40;
    /** 变体图标比例（× 字号）。 */
    public static final double VARIANT_ICON_RATIO = 1.5;
    /** 变体列表视口高比例（× 字号）。 */
    public static final double VARIANT_LIST_RATIO = 12.0;
    /** 变体列表视口高下限（逻辑 px）。 */
    public static final int VARIANT_LIST_MIN = 180;
    /** 变体列表视口高上限（逻辑 px）。 */
    public static final int VARIANT_LIST_MAX = 320;
    /** 变体卡宽比例（× 面板宽；上限见 {@link #VARIANT_CARD_MAX}，防小盒溢出）。 */
    public static final double VARIANT_CARD_RATIO = 0.42;
    /** 变体卡宽上限（逻辑 px）。 */
    public static final int VARIANT_CARD_MAX = 520;
    /** 分类导航行高比例（× 字号）。 */
    public static final double NAV_ROW_RATIO = 2.67;
    /** 分类导航行高下限（逻辑 px）。 */
    public static final int NAV_ROW_MIN = 24;
    /** 分类导航行高上限（逻辑 px）。 */
    public static final int NAV_ROW_MAX = 36;
    /** 徽章内边距除数（fs / 3）。 */
    public static final double BADGE_PAD_DIVISOR = 3.0;
    /** 触发器图标比例（× 字号）。 */
    public static final double TRIGGER_ICON_RATIO = 1.5;
    /** 滚动条宽比例（× 字号）。 */
    public static final double SCROLLBAR_WIDTH_RATIO = 0.5;
    /** 滚动条宽下限（逻辑 px）。 */
    public static final int SCROLLBAR_WIDTH_MIN = 6;
    /** 滚动条宽上限（逻辑 px）。 */
    public static final int SCROLLBAR_WIDTH_MAX = 10;

    // ==================== auto 求解（P5 §1.4） ====================

    /** 图标等比缩放阶梯 {@code k}（只缩图标与间距派生量，不缩字号）。 */
    public static final double[] ICON_SCALE_STEPS = {1.00, 0.95, 0.90, 0.85, 0.80};
    /** 安全裕度（%）：先按 {@code hard * 1.05} 求解，拒绝贴线通过。 */
    public static final int SAFETY_MARGIN_PCT = 5;
    /** 硬目标下限：空/极小盒也要保证的最少可见项（防止退化到 0 项）。 */
    public static final int HARD_TARGET_FLOOR = 12;

    // ==================== 信息条与动效（P5 §3.3 / §4.2 D） ====================

    /** 信息条高比例（× 字号）。 */
    public static final double INFO_BAR_RATIO = 2.0;
    /** 面板入场动效时长（ms），与 SceneDialog 同族。 */
    public static final int PANEL_ENTER_MS = 160;
    /** 面板退场动效时长（ms），与入场对称。 */
    public static final int PANEL_LEAVE_MS = 160;
    /** 面板入场位移（逻辑 px）。 */
    public static final int PANEL_ENTER_OFFSET_Y = 8;
    /** 删除撤销窗口（ms）：P5 §5.5 E1/E3「≤5s、至多 1 条 tombstone」的唯一时间常量。 */
    public static final int REMOVE_UNDO_WINDOW_MS = 5000;

    // ==================== 现状基线（仅支配性对照，禁止参与布局） ====================

    /** 现状面板比例（%）。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_PANEL_RATIO = 70;
    /** 现状面板内边距。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_PANEL_PADDING = 8;
    /** 现状分区间距。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_GAP = 8;
    /** 现状顶栏高。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_TOP_BAR_HEIGHT = 48;
    /** 现状成员带高。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_MEMBERS_HEIGHT = 248;
    /** 现状左导航宽。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_NAV_WIDTH = 168;
    /** 现状中栏内边距。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_CENTER_PADDING = 4;
    /** 现状信息条高。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_INFO_BAR_HEIGHT = 24;
    /** 现状单元高。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_CELL_HEIGHT = 64;
    /** 现状单元间距。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_CELL_GAP = 8;
    /** 现状滚动条宽。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_SCROLLBAR_WIDTH = 8;
    /** 现状标签行高（fs=12 时 lineHeight=15）。<b>仅用于支配性对照。</b> */
    public static final int LEGACY_LINE_HEIGHT = 15;
}
