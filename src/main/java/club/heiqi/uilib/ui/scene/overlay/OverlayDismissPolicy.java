package club.heiqi.uilib.ui.scene.overlay;

/**
 * 浮层关闭策略描述。
 *
 * <p>本类只描述哪些输入意图可以请求关闭浮层，不直接摘除节点、不修改 signal。
 * 实际关闭必须由调用方把请求转换为状态写入，再由 portal 派生出挂载或卸载。</p>
 */
public final class OverlayDismissPolicy {

    /** 默认策略：允许 ESC、外部点击和选中请求关闭。 */
    public static final OverlayDismissPolicy DEFAULT = new OverlayDismissPolicy(true, true, true);

    /** 完全不自动请求关闭的策略。 */
    public static final OverlayDismissPolicy NONE = new OverlayDismissPolicy(false, false, false);

    private final boolean dismissOnEscape;
    private final boolean dismissOnOutsidePointerDown;
    private final boolean dismissOnSelect;

    /**
     * 创建浮层关闭策略。
     *
     * @param dismissOnEscape ESC 是否请求关闭
     * @param dismissOnOutsidePointerDown 外部指针按下是否请求关闭
     * @param dismissOnSelect 选中动作是否请求关闭
     */
    public OverlayDismissPolicy(boolean dismissOnEscape,
                                boolean dismissOnOutsidePointerDown,
                                boolean dismissOnSelect) {
        this.dismissOnEscape = dismissOnEscape;
        this.dismissOnOutsidePointerDown = dismissOnOutsidePointerDown;
        this.dismissOnSelect = dismissOnSelect;
    }

    /** @return ESC 是否请求关闭 */
    public boolean isDismissOnEscape() {
        return dismissOnEscape;
    }

    /** @return 外部指针按下是否请求关闭 */
    public boolean isDismissOnOutsidePointerDown() {
        return dismissOnOutsidePointerDown;
    }

    /** @return 选中动作是否请求关闭 */
    public boolean isDismissOnSelect() {
        return dismissOnSelect;
    }
}
