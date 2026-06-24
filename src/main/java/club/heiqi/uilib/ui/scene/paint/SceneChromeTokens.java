package club.heiqi.uilib.ui.scene.paint;

/**
 * SceneChromeTokens 集中维护 scene 交互控件 chrome（背景/边框/文本/强调/圆角/间距）统一配色 token。
 * 纯静态常量收口，非主题引擎。色值一律 0xFF ARGB，直接传 SceneNode setter。
 */
public final class SceneChromeTokens {

    /**
     * 默认态背景（Slate-700）。
     */
    public static final int BG_DEFAULT = 0xFF334155;
    /**
     * 悬停态背景（Slate-600 提亮）。
     */
    public static final int BG_HOVER = 0xFF475569;
    /**
     * 按下态背景（Slate-800 压暗）。
     */
    public static final int BG_PRESSED = 0xFF1E293B;
    /**
     * 禁用态背景（冷灰沉底）。
     */
    public static final int BG_DISABLED = 0xFF1F2937;

    /**
     * 选中/聚焦/激活主色（Blue-500）。
     */
    public static final int ACCENT = 0xFF3B82F6;
    /**
     * 选中态悬停（Blue-400）。
     */
    public static final int ACCENT_HOVER = 0xFF60A5FA;
    /**
     * 选中态按下（Blue-600）。
     */
    public static final int ACCENT_PRESSED = 0xFF2563EB;
    /**
     * Slider 进度填充（Sky-400，区分进度量与选中态）。
     */
    public static final int ACCENT_PROGRESS = 0xFF38BDF8;

    /**
     * 默认边框（Slate-600）。
     */
    public static final int BORDER_DEFAULT = 0xFF475569;
    /**
     * 聚焦/激活边框（Blue-400）。
     */
    public static final int BORDER_FOCUS = 0xFF60A5FA;
    /**
     * 禁用边框（Slate-700）。
     */
    public static final int BORDER_DISABLED = 0xFF334155;

    /**
     * 正常文本（Slate-200）。
     */
    public static final int TEXT_PRIMARY = 0xFFE2E8F0;
    /**
     * 次要文本/placeholder（Slate-400）。
     */
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    /**
     * 禁用文本（Slate-500）。
     */
    public static final int TEXT_DISABLED = 0xFF64748B;
    /**
     * 强调底上的文本（纯白）。
     */
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    /**
     * Slider/Toggle thumb 默认色（Sky-100）。
     */
    public static final int THUMB_DEFAULT = 0xFFE0F2FE;
    /**
     * thumb 悬停纯白。
     */
    public static final int THUMB_HOVER = 0xFFFFFFFF;
    /**
     * thumb 按下（Sky-200）。
     */
    public static final int THUMB_PRESSED = 0xFFBAE6FD;

    /**
     * 小控件圆角。
     */
    public static final int RADIUS_SM = 3;
    /**
     * 标准控件圆角。
     */
    public static final int RADIUS_MD = 4;
    /**
     * 大容器圆角。
     */
    public static final int RADIUS_LG = 6;
    /**
     * 全圆角胶囊。
     */
    public static final int RADIUS_PILL = 999;

    /**
     * 紧凑内边距。
     */
    public static final int PAD_SM = 2;
    /**
     * 标准内边距。
     */
    public static final int PAD_MD = 6;
    /**
     * 宽松内边距。
     */
    public static final int PAD_LG = 10;
    /**
     * 小间距。
     */
    public static final int GAP_SM = 4;
    /**
     * 标准间距。
     */
    public static final int GAP_MD = 8;

    /**
     * 纯静态 token 类，禁止实例化。
     */
    private SceneChromeTokens() {
    }
}
