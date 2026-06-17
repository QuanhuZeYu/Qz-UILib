package club.heiqi.uilib.ui.scene.input;

/**
 * 帧内不可变指针事件。
 *
 * <p>由 {@link InputFrameBuilder} 在封板时从 {@link RawInputEvent} 投影生成，
 * 对象不可变，仅通过 getter 读取字段。</p>
 *
 * <p>结构上不含任何原生平台字段，杜绝逃生舱向核心泄漏。</p>
 */
public class ScenePointerEvent {

    private final ScenePointerAction action;
    private final int logicalX;
    private final int logicalY;
    /** 鼠标按钮标识，MOVE/SCROLL 时为 {@link SceneMouseButton#NONE} */
    private final SceneMouseButton button;
    private final int wheelDelta;
    private final int deltaX;
    private final int deltaY;
    private final boolean controlDown;
    private final boolean shiftDown;
    private final boolean altDown;
    private final boolean metaDown;
    private final long timeNanos;

    /**
     * 包级构造器，仅供 {@link InputFrameBuilder} 封板使用。
     */
    ScenePointerEvent(ScenePointerAction action, int logicalX, int logicalY,
                      SceneMouseButton button, int wheelDelta, int deltaX, int deltaY,
                      boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                      long timeNanos) {
        this.action = action;
        this.logicalX = logicalX;
        this.logicalY = logicalY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.timeNanos = timeNanos;
    }

    /** @return 指针动作类型 */
    public ScenePointerAction getAction() { return action; }

    /** @return 逻辑坐标 X */
    public int getLogicalX() { return logicalX; }

    /** @return 逻辑坐标 Y */
    public int getLogicalY() { return logicalY; }

    /**
     * @return 鼠标按钮标识，MOVE/SCROLL 时为 {@link SceneMouseButton#NONE}
     */
    public SceneMouseButton getButton() { return button; }

    /** @return 滚轮增量 */
    public int getWheelDelta() { return wheelDelta; }

    /** @return X 方向移动增量 */
    public int getDeltaX() { return deltaX; }

    /** @return Y 方向移动增量 */
    public int getDeltaY() { return deltaY; }

    /** @return Ctrl 是否按下 */
    public boolean isControlDown() { return controlDown; }

    /** @return Shift 是否按下 */
    public boolean isShiftDown() { return shiftDown; }

    /** @return Alt 是否按下 */
    public boolean isAltDown() { return altDown; }

    /** @return Meta（Win/Cmd）是否按下 */
    public boolean isMetaDown() { return metaDown; }

    /** @return 事件时间戳（纳秒） */
    public long getTimeNanos() { return timeNanos; }
}
