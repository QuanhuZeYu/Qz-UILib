package club.heiqi.uilib.ui.hud.api;

/**
 * HUD 外接工具栏的挂载边（HUD 主体外侧四边）。
 *
 * <p>语义：工具栏挂在 HUD 内容盒（"主体"）<b>外侧</b>的指定边上，与内容盒不重叠；
 * 该边决定内容与工具栏在主轴上的先后，以及 {@link HudToolbarSpec#getThickness()} 占用的轴。</p>
 *
 * <ul>
 *   <li>{@link #TOP} / {@link #BOTTOM}：工具栏是水平条，厚度占用<b>高</b>（y 轴），
 *       工具栏在内容上方 / 下方；</li>
 *   <li>{@link #LEFT} / {@link #RIGHT}：工具栏是竖直条，厚度占用<b>宽</b>（x 轴），
 *       工具栏在内容左侧 / 右侧。</li>
 * </ul>
 */
public enum HudToolbarSide {
    /** 内容上方（水平条，厚度占高）。 */
    TOP,
    /** 内容下方（水平条，厚度占高）。 */
    BOTTOM,
    /** 内容左侧（竖直条，厚度占宽）。 */
    LEFT,
    /** 内容右侧（竖直条，厚度占宽）。 */
    RIGHT;

    /**
     * 默认挂载边：{@link #BOTTOM}。
     *
     * <p>理由：HUD 常驻窗口以顶/底边锚定为主，底部条是"贴近屏幕边、内容向上生长"的
     * 最自然选择；对左下锚定的聊天框，底部工具栏紧贴屏幕下缘，聊天内容整体上移，
     * 不遮挡消息区与输入条。</p>
     */
    public static final HudToolbarSide DEFAULT = BOTTOM;

    /** @return 是否为水平边（TOP/BOTTOM；厚度占用高度） */
    public boolean isHorizontalEdge() {
        return this == TOP || this == BOTTOM;
    }

    /** @return 工具栏是否排在内容之后（BOTTOM/RIGHT；false = 内容之后才轮到工具栏） */
    public boolean isTrailing() {
        return this == BOTTOM || this == RIGHT;
    }
}
