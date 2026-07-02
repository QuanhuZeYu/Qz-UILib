package club.heiqi.uilib.internal.devtools.pages;

/**
 * demo 页面壳色板收口：跨页高频色常量统一来源。
 *
 * <p>仅收录在多个 demo 页面重复出现的壳色常量；单页独有色
 * （演示色板、状态语义色、性能页专用底色等）仍留各页私有定义。</p>
 */
public final class SceneDemoTokens {
    private SceneDemoTokens() {
    }

    /** 根背景色 */
    public static final int ROOT_BG = 0xFF0B1424;
    /** 卡片背景色 */
    public static final int CARD_BG = 0xFF0D1728;
    /** 卡片边框色 */
    public static final int CARD_BORDER = 0xFF2F4D87;
    /** 视口背景色 */
    public static final int VIEWPORT_BG = 0xFF081120;
    /** 读数面板背景色 */
    public static final int READOUT_BG = 0xFF1E293B;
    /** 标题色 */
    public static final int TITLE_COLOR = 0xFFC9D8F8;
    /** 正文色 */
    public static final int TEXT_COLOR = 0xFFEAF1FF;
    /** 次要文本色 */
    public static final int MUTED_COLOR = 0xFF8AA0C8;
    /** 错误色 */
    public static final int ERROR_COLOR = 0xFFF87171;
    /** 成功色 */
    public static final int OK_COLOR = 0xFF34D399;
    /** 脏标记色 */
    public static final int DIRTY_COLOR = 0xFF60A5FA;
}
