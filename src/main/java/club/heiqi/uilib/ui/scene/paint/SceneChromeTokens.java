package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneChromeTokens 集中维护 scene 交互控件 chrome（背景/边框/文本/强调/圆角/间距）统一配色 token。
 * 纯静态常量收口，非主题引擎。色值一律 0xFF ARGB，直接传 SceneNode setter。
 */
public final class SceneChromeTokens {

    /**
     * 实底圆角面板外壳：BG_DEFAULT + 1px BORDER_DEFAULT 边框 + 圆角 + 裁剪。
     *
     * <p>把「bg + border + radius + clip」四件套收敛为单点应用，消除各处手写外壳样式的复发；
     * 调用方仍可在此之后覆盖个别属性。</p>
     *
     * @param node         目标节点
     * @param cornerRadius 圆角（像素）
     */
    public static void applyPanelChrome(SceneNode node, int cornerRadius) {
        node.setBackgroundColor(BG_DEFAULT);
        applyOuterShell(node, cornerRadius);
    }

    /**
     * 无底色外层边框壳：1px BORDER_DEFAULT 边框 + 圆角 + 裁剪（供透明底横带等场景）。
     *
     * @param node         目标节点
     * @param cornerRadius 圆角（像素）
     */
    public static void applyOuterShell(SceneNode node, int cornerRadius) {
        node.setBorderWidth(1);
        node.setBorderColor(BORDER_DEFAULT);
        node.setCornerRadius(cornerRadius);
        node.setClipChildren(true);
    }

    /**
     * 默认态背景（Slate-700）。
     */
    public static final int BG_DEFAULT = 0xFF2B2930;
    /**
     * 悬停态背景（Slate-600 提亮）。
     */
    public static final int BG_HOVER = 0xFF36333D;
    /**
     * 按下态背景（Slate-800 压暗）。
     */
    public static final int BG_PRESSED = 0xFF211F26;
    /**
     * 禁用态背景（冷灰沉底）。
     */
    public static final int BG_DISABLED = 0xFF1D1B20;

    /**
     * 选中/聚焦/激活主色（Blue-500）。
     */
    public static final int ACCENT = 0xFF4F378B;
    /**
     * 选中态悬停（Blue-400）。
     */
    public static final int ACCENT_HOVER = 0xFF6750A4;
    /**
     * 选中态按下（Blue-600）。
     */
    public static final int ACCENT_PRESSED = 0xFF3F2E68;
    /**
     * Slider 进度填充（Sky-400，区分进度量与选中态）。
     */
    public static final int ACCENT_PROGRESS = 0xFFD0BCFF;
    /**
     * 标准选中背色（与 Select 控件 selected 状态视觉一致，Blue-400）。
     *
     * <p>与 {@link #ACCENT_HOVER} 同值，旨在语义层面分离「标准选中」与「高亮 hover」，
     * 避免后续主题化时出现耦合错改。</p>
     */
    public static final int STANDARD_SELECTED = 0xFF4F378B;

    /**
     * 默认边框（Slate-600）。
     */
    public static final int BORDER_DEFAULT = 0xFF938F99;
    /**
     * 聚焦/激活边框（Blue-400）。
     */
    public static final int BORDER_FOCUS = 0xFFD0BCFF;
    /**
     * 禁用边框（Slate-700）。
     */
    public static final int BORDER_DISABLED = 0xFF49454F;

    /**
     * 正常文本（Slate-200）。
     */
    public static final int TEXT_PRIMARY = 0xFFE6E1E5;
    /**
     * 次要文本/placeholder（Slate-400）。
     */
    public static final int TEXT_SECONDARY = 0xFFCAC4D0;
    /**
     * 禁用文本（Slate-500）。
     */
    public static final int TEXT_DISABLED = 0xFF79747E;
    /**
     * 强调底上的文本（纯白）。
     */
    public static final int TEXT_ON_ACCENT = 0xFFEADDFF;
    /**
     * 文本选区高亮背景（与 {@link #ACCENT} 同值，独立语义：主题化时避免与「选中态」耦合）。
     */
    public static final int SELECTION_BG = 0xFF4F378B;
    /**
     * 文本选区高亮上的文本色（与 {@link #TEXT_ON_ACCENT} 同值，独立语义）。
     */
    public static final int SELECTION_TEXT = 0xFFEADDFF;

    /**
     * Slider/Toggle thumb 默认色（Sky-100）。
     */
    public static final int THUMB_DEFAULT = 0xFFEADDFF;
    /**
     * thumb 悬停纯白。
     */
    public static final int THUMB_HOVER = 0xFFFFFFFF;
    /**
     * thumb 按下（Sky-200）。
     */
    public static final int THUMB_PRESSED = 0xFFD0BCFF;

    /**
     * 小控件圆角。
     */
    public static final int RADIUS_SM = 8;
    /**
     * 标准控件圆角。
     */
    public static final int RADIUS_MD = 12;
    /**
     * 大容器圆角。
     */
    public static final int RADIUS_LG = 20;
    /**
     * 全圆角胶囊。
     */
    public static final int RADIUS_PILL = 999;

    /**
     * 紧凑内边距。
     */
    public static final int PAD_SM = 4;
    /**
     * 标准内边距。
     */
    public static final int PAD_MD = 8;
    /**
     * 宽松内边距。
     */
    public static final int PAD_LG = 12;
    /**
     * 小间距。
     */
    public static final int GAP_SM = 4;
    /**
     * 标准间距。
     */
    public static final int GAP_MD = 8;

    /** 快速交互态 Motion 时长。 */
    public static final int MOTION_FAST_MS = 90;
    /** 标准控件 Motion 时长。 */
    public static final int MOTION_STANDARD_MS = 160;
    /** 强调内容切换 Motion 时长。 */
    public static final int MOTION_EMPHASIZED_MS = 240;

    /**
     * 表格行高（只读紧凑行，DataTable 默认行高）。
     */
    public static final int ROW_HEIGHT_TABLE = 28;
    /**
     * 输入框/按钮行高（KeyValueMap、ObjectField、HostWidget 文本输入框显式高度）。
     */
    public static final int INPUT_HEIGHT = 34;
    /**
     * 标准按钮高度（像素，用于添加按钮等独立操作按钮）。
     */
    public static final int BUTTON_HEIGHT = 32;
    /**
     * 标准滚动视口默认高度（像素）。
     */
    public static final int VIEWPORT_HEIGHT_DEFAULT = 160;

    /**
     * 危险动作背景色（Red-900，用于删除按钮等危险动作底色）。
     */
    public static final int DANGER_BG = 0xFF7F1D1D;
    /**
     * 危险动作悬停背景色（Red-800，比 {@link #DANGER_BG} 提亮一档）。
     */
    public static final int DANGER_BG_HOVER = 0xFF991B1B;
    /**
     * 危险动作按下背景色（Red-950，比 {@link #DANGER_BG} 压暗一档）。
     */
    public static final int DANGER_BG_PRESSED = 0xFF5C1414;
    /**
     * 危险动作禁用背景色（暗红，用于删除按钮禁用态）。
     */
    public static final int DANGER_BG_DISABLED = 0xFF3F2A2A;
    /**
     * 危险弱提示背景色（Red-500 @ alpha=0x22，约 13% 不透明度，用于错误行叠加底色）。
     */
    public static final int DANGER_BG_SUBTLE = 0x22EF4444;

    /**
     * 警告文本色（Amber-400，用于占位提示/警告文本）。
     */
    public static final int WARNING_TEXT = 0xFFFBBF24;

    /**
     * 滚动条滑块默认态色（Slate-400 @ 60% 不透明度，中性灰，idle 可发现性）。
     */
    public static final int SCROLLBAR_THUMB_IDLE = 0x99938F99;
    /**
     * 滚动条滑块悬停态色（Slate-400 全不透明，提亮反馈）。
     */
    public static final int SCROLLBAR_THUMB_HOVER = 0xFFCAC4D0;
    /**
     * 滚动条滑块拖动态色（Slate-300 更亮，强反馈）。
     */
    public static final int SCROLLBAR_THUMB_DRAG = 0xFFE6E1E5;

    /**
     * 透明色（用于隐藏 caret、默认透明项背景等 chrome 槽位）。
     */
    public static final int TRANSPARENT = 0x00000000;

    /**
     * DataTable 视口背景色（Slate-900，深色嵌入槽底）。
     */
    public static final int DATA_TABLE_VIEWPORT_BG = 0xFF1B1B1F;
    /**
     * DataTable 深色槽主文本色。
     */
    public static final int DATA_TABLE_TEXT = 0xFFE6E1E5;
    /**
     * DataTable 编辑输入槽默认底色。
     */
    public static final int DATA_TABLE_EDIT_SLOT_BG = 0xFF211F26;
    /**
     * DataTable 编辑输入槽 hover/聚焦底色。
     */
    public static final int DATA_TABLE_EDIT_SLOT_BG_HOVER = 0xFF2B2930;
    /**
     * DataTable 编辑输入槽默认边框色。
     */
    public static final int DATA_TABLE_EDIT_BORDER = 0xFF938F99;
    /**
     * DataTable 编辑输入槽 hover 边框色。
     */
    public static final int DATA_TABLE_EDIT_BORDER_HOVER = 0xFFD0BCFF;
    /**
     * DataTable Select 箭头默认色。
     */
    public static final int DATA_TABLE_EDIT_ARROW = 0xFFCAC4D0;
    /**
     * DataTable 下拉键盘高亮项背景色。
     */
    public static final int DATA_TABLE_ITEM_BG_HIGHLIGHTED = 0xFF4F378B;

    // ==================== HUD 虚拟窗口（屏幕级宿主外壳） ====================

    /**
     * HUD 窗口外壳背景（半透明黑，弱化对游戏画面的遮挡）。
     */
    public static final int HUD_SHELL_BG = 0xA0000000;
    /**
     * HUD 弱强调文本（旧 HudTone.MUTED 语义：次要信息灰）。
     */
    public static final int HUD_TEXT_MUTED = 0xFFAAAAAA;
    /**
     * HUD 信息文本（旧 HudTone.INFO 语义：青）。
     */
    public static final int HUD_TEXT_INFO = 0xFF55FFFF;
    /**
     * HUD 成功文本（旧 HudTone.SUCCESS 语义：绿）。
     */
    public static final int HUD_TEXT_SUCCESS = 0xFF55FF55;
    /**
     * HUD 警告文本（旧 HudTone.WARNING 语义：黄）。
     */
    public static final int HUD_TEXT_WARNING = 0xFFFFFF55;
    /**
     * HUD 危险文本（旧 HudTone.DANGER 语义：红）。
     */
    public static final int HUD_TEXT_DANGER = 0xFFFF5555;

    /**
     * 纯静态 token 类，禁止实例化。
     */
    private SceneChromeTokens() {
    }
}
