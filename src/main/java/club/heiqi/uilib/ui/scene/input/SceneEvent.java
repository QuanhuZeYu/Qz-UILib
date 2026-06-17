package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景事件（不可变数据对象）。
 *
 * <p>由 {@link SceneInputRouter} 在 route 过程中根据指针事件 + hit-test 结果构造。
 * 对象不可变，全 final 字段 + getter 无 setter。包级构造器仅供 router 使用。</p>
 *
 * <h3>坐标语义</h3>
 * <p>{@code pointerX/pointerY} 存储画布逻辑坐标（即 {@link ScenePointerEvent#getLogicalX()} 的原始值），
 * 不叠加 {@code rootAbsX/Y} 宿主偏移。整树平移由 {@link SceneHitTester} 内部通过
 * {@code rootAbsX/Y} 参数完成，命中判定自动抵消，无需调用方预先变换指针坐标。</p>
 */
public class SceneEvent {

    /** 事件类型 */
    private final SceneEventType type;
    /** 最深命中目标节点 */
    private final SceneNode target;
    /** 指针画布逻辑 X 坐标（不叠加 rootAbsX） */
    private final int pointerX;
    /** 指针画布逻辑 Y 坐标（不叠加 rootAbsY） */
    private final int pointerY;
    /** 鼠标按钮，非按钮事件为 {@link SceneMouseButton#NONE} */
    private final SceneMouseButton button;
    /** 滚轮增量，非 SCROLL 事件为 0 */
    private final int wheelDelta;
    /** Ctrl 是否按下 */
    private final boolean controlDown;
    /** Shift 是否按下 */
    private final boolean shiftDown;
    /** Alt 是否按下 */
    private final boolean altDown;
    /** Meta 是否按下 */
    private final boolean metaDown;
    /** 事件时间戳（纳秒） */
    private final long timeNanos;

    /**
     * 包级构造器，仅供 {@link SceneInputRouter} 使用。
     */
    SceneEvent(SceneEventType type, SceneNode target,
               int pointerX, int pointerY,
               SceneMouseButton button, int wheelDelta,
               boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
               long timeNanos) {
        this.type = type;
        this.target = target;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.timeNanos = timeNanos;
    }

    /** @return 事件类型 */
    public SceneEventType getType() { return type; }

    /** @return 最深命中目标节点 */
    public SceneNode getTarget() { return target; }

    /**
     * @return 指针画布逻辑 X 坐标（不叠加 rootAbsX，与 {@link ScenePointerEvent#getLogicalX()} 一致）
     */
    public int getPointerX() { return pointerX; }

    /**
     * @return 指针画布逻辑 Y 坐标（不叠加 rootAbsY，与 {@link ScenePointerEvent#getLogicalY()} 一致）
     */
    public int getPointerY() { return pointerY; }

    /** @return 鼠标按钮，非按钮事件为 {@link SceneMouseButton#NONE} */
    public SceneMouseButton getButton() { return button; }

    /** @return 滚轮增量，非 SCROLL 事件为 0 */
    public int getWheelDelta() { return wheelDelta; }

    /** @return Ctrl 是否按下 */
    public boolean isControlDown() { return controlDown; }

    /** @return Shift 是否按下 */
    public boolean isShiftDown() { return shiftDown; }

    /** @return Alt 是否按下 */
    public boolean isAltDown() { return altDown; }

    /** @return Meta 是否按下 */
    public boolean isMetaDown() { return metaDown; }

    /** @return 事件时间戳（纳秒） */
    public long getTimeNanos() { return timeNanos; }
}
