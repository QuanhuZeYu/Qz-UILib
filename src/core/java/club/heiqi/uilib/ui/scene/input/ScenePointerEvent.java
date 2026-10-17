package club.heiqi.uilib.ui.scene.input;

/** 无平台、不可变的单指针输入；坐标已由宿主转换到scene统一像素。
 * 宿主必须递交所有按钮的DOWN/UP。按钮0是标准激活键，其余按钮仅占位并吞尾。
 * 每个事件按采集顺序递交；focus、IME、键盘与拖放不属于本接口。 */
public final class ScenePointerEvent {
    public enum Kind {
        DOWN,
        MOVE,
        UP,
        CANCEL,
        ALL_BUTTONS_RELEASED,
        SCROLL,
        SYNTHETIC_ACTIVATION
    }

    public final Kind kind;

    /** DOWN/UP为非负物理按钮号；其他事件为-1。 */
    public final int button;

    public final int x, y, delta;
    /** 采集时刻；速度估计使用事件间隔，不以同帧结算间隔代替。 */
    public final long timeNanos;
    public enum ScrollPhase { DISCRETE, UPDATE, END, MOMENTUM }
    public final ScrollPhase scrollPhase;

    private ScenePointerEvent(Kind kind, int button, int x, int y, int delta) {
        this(kind, button, x, y, delta, System.nanoTime(), ScrollPhase.DISCRETE);
    }
    private ScenePointerEvent(Kind kind, int button, int x, int y, int delta, long timeNanos, ScrollPhase phase) {
        this.timeNanos = timeNanos; this.scrollPhase = java.util.Objects.requireNonNull(phase, "滚动输入阶段");
        this.kind = kind;
        this.button = button;
        this.x = x;
        this.y = y;
        this.delta = delta;
    }

    public ScenePointerEvent at(long timeNanos) { return new ScenePointerEvent(kind, button, x, y, delta, timeNanos, scrollPhase); }
    /** 宿主明确提供直接滚动序列；MOMENTUM 表示已有动量，不能重复合成。 */
    public static ScenePointerEvent scrollGesture(int x, int y, int delta, ScrollPhase phase, long timeNanos) {
        return new ScenePointerEvent(Kind.SCROLL, -1, x, y, delta, timeNanos, phase);
    }

    /** 按下只建立手势所有权，不发布click。 */
    public static ScenePointerEvent down(int button, int x, int y) {
        return button(Kind.DOWN, button, x, y);
    }

    /** 释放按当前统一几何检查原目标，未知或重复UP不激活。 */
    public static ScenePointerEvent up(int button, int x, int y) {
        return button(Kind.UP, button, x, y);
    }

    private static ScenePointerEvent button(Kind kind, int button, int x, int y) {
        if (button < 0) throw new IllegalArgumentException("物理按钮号不能为负");
        return new ScenePointerEvent(kind, button, x, y, 0);
    }

    /** 移动维护当前位置，不重选起手目标。 */
    public static ScenePointerEvent move(int x, int y) {
        return new ScenePointerEvent(Kind.MOVE, -1, x, y, 0);
    }

    /** 窗口失焦/关闭时取消owner；仍按下的按钮继续吞尾至UP或确认全部释放。 */
    public static ScenePointerEvent cancel() {
        return new ScenePointerEvent(Kind.CANCEL, -1, 0, 0, 0);
    }

    /** 宿主确认所有物理按钮已释放时同步结束占位，用于失焦丢UP；不会恢复旧owner。
     * 不能在仍有按钮按下时递交此事件；仅恢复焦点并不能证明物理结束。 */
    public static ScenePointerEvent allButtonsReleased() {
        return new ScenePointerEvent(Kind.ALL_BUTTONS_RELEASED, -1, 0, 0, 0);
    }

    /** 滚轮沿当前有效范围路由，不建立pointer owner。 */
    public static ScenePointerEvent scroll(int x, int y, int delta) {
        return new ScenePointerEvent(Kind.SCROLL, -1, x, y, delta);
    }

    /** 完整synthetic activation，空闲状态按坐标完整点击，执行clickFocus和业务激活；不制造物理DOWN或拖选。
     * 已有物理手势（包括取消尾部、空白和第二键）时整项吞掉，不能绕过owner。 */
    public static ScenePointerEvent syntheticActivation(int x, int y) {
        return new ScenePointerEvent(Kind.SYNTHETIC_ACTIVATION, -1, x, y, 0);
    }
}
