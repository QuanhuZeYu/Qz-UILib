package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景事件（不可变数据对象）。
 *
 * <p>由 {@link SceneInputRouter} 在 route 过程中根据指针事件 + hit-test 结果构造。
 * 对象不可变，全 final 字段 + getter 无 setter。包级构造器仅供 router 使用。</p>
 *
 * <h3>坐标语义（三层坐标，I12）</h3>
 * <p>指针事件携带三层坐标，由 {@link SceneInputRouter} 在 route 阶段统一注入，handler 按需消费：</p>
 * <ul>
 *   <li><b>raw（屏幕绝对）</b>：{@code pointerX/pointerY}，存储屏幕绝对坐标（含 {@code rootAbsX/Y}，
 *       = {@link ScenePointerEvent#getLogicalX()}）。仅供 hit-test 内部与跨窗口/跨树辅助，
 *       <b>禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 的结果混比</b>（rootAbs≠0 时错位）。</li>
 *   <li><b>host（host 局部）</b>：{@code hostPointerX/hostPointerY} = {@code pointerX/Y - rootAbsX/Y}，
 *       不含 rootAbs，与 {@link SceneGeometry#absoluteBox} 传 0,0 返回的 host 局部盒同系，
 *       供 handler 做几何比对时使用。</li>
 *   <li><b>local（命中节点局部）</b>：{@code localPointerX/localPointerY} = {@code hostPointer - absoluteBox(effectiveTarget,0,0)}，
 *       命中节点 {@code effectiveTarget} 局部坐标，框架自动注入，handler 默认消费此层。</li>
 * </ul>
 * <p>rootAbsX/Y=0 时三层退化为同值（raw==host==local 当 root layout 在原点），向后兼容。</p>
 */
public class SceneEvent {

    /** 事件类型 */
    private final SceneEventType type;
    /** 最深命中目标节点 */
    private final SceneNode target;
    /** 指针屏幕绝对 X 坐标（含 rootAbsX，= ScenePointerEvent.getLogicalX()；raw 层） */
    private final int pointerX;
    /** 指针屏幕绝对 Y 坐标（含 rootAbsY，= ScenePointerEvent.getLogicalY()；raw 层） */
    private final int pointerY;
    /** 指针 host 局部 X 坐标（= pointerX - rootAbsX，与 absoluteBox(node,0,0) 同系；host 层） */
    private final int hostPointerX;
    /** 指针 host 局部 Y 坐标（= pointerY - rootAbsY，与 absoluteBox(node,0,0) 同系；host 层） */
    private final int hostPointerY;
    /** 指针命中节点 effectiveTarget 局部 X 坐标（= hostPointerX - absoluteBox(effectiveTarget,0,0).getX()；local 层，框架自动注入） */
    private final int localPointerX;
    /** 指针命中节点 effectiveTarget 局部 Y 坐标（= hostPointerY - absoluteBox(effectiveTarget,0,0).getY()；local 层，框架自动注入） */
    private final int localPointerY;
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

    // === I4a 键盘/文本字段（指针事件为 null/默认） ===
    /** 按键标识，非键盘事件为 null */
    private final SceneKey key;
    /** 按键动作，非键盘事件为 null */
    private final SceneKeyAction keyAction;
    /** 是否为按键重复事件，非键盘事件为 false */
    private final boolean repeat;
    /** 文本内容，非 TEXT_INPUT 事件为 null */
    private final String text;

    /**
     * 包级构造器（指针事件），仅供 {@link SceneInputRouter} 使用。
     *
     * @param localPointerX 命中节点 effectiveTarget 局部 X（local 层，框架注入）
     * @param localPointerY 命中节点 effectiveTarget 局部 Y（local 层，框架注入）
     */
    SceneEvent(SceneEventType type, SceneNode target,
               int pointerX, int pointerY,
               int hostPointerX, int hostPointerY,
               int localPointerX, int localPointerY,
               SceneMouseButton button, int wheelDelta,
               boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
               long timeNanos) {
        this.type = type;
        this.target = target;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        this.hostPointerX = hostPointerX;
        this.hostPointerY = hostPointerY;
        this.localPointerX = localPointerX;
        this.localPointerY = localPointerY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.timeNanos = timeNanos;
        // 键盘/文本字段默认值
        this.key = null;
        this.keyAction = null;
        this.repeat = false;
        this.text = null;
    }

    /**
     * 私有全字段构造器，由静态工厂方法使用。
     */
    private SceneEvent(SceneEventType type, SceneNode target,
                       int pointerX, int pointerY,
                       SceneMouseButton button, int wheelDelta,
                       boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                       long timeNanos,
                       SceneKey key, SceneKeyAction keyAction, boolean repeat, String text) {
        this.type = type;
        this.target = target;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        // 键盘/文本事件无指针坐标，hostPointer/localPointer 退化为 0（与 pointerX/Y=0 同系）
        this.hostPointerX = 0;
        this.hostPointerY = 0;
        this.localPointerX = 0;
        this.localPointerY = 0;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.timeNanos = timeNanos;
        this.key = key;
        this.keyAction = keyAction;
        this.repeat = repeat;
        this.text = text;
    }

    /**
     * 构造键盘事件的静态工厂。
     *
     * @param type      事件类型（KEY_DOWN 或 KEY_UP）
     * @param target    焦点目标节点
     * @param key       按键标识
     * @param keyAction 按键动作
     * @param repeat    是否为重复事件
     * @param controlDown Ctrl 是否按下
     * @param shiftDown   Shift 是否按下
     * @param altDown     Alt 是否按下
     * @param metaDown    Meta 是否按下
     * @param timeNanos  事件时间戳（纳秒）
     * @return 键盘场景事件
     */
    public static SceneEvent ofKey(SceneEventType type, SceneNode target,
                                   SceneKey key, SceneKeyAction keyAction, boolean repeat,
                                   boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                                   long timeNanos) {
        return new SceneEvent(type, target,
                0, 0, SceneMouseButton.NONE, 0,
                controlDown, shiftDown, altDown, metaDown, timeNanos,
                key, keyAction, repeat, null);
    }

    /**
     * 构造文本输入事件的静态工厂。
     *
     * @param type   事件类型（TEXT_INPUT）
     * @param target 焦点目标节点
     * @param text   输入的文本内容
     * @param timeNanos 事件时间戳（纳秒）
     * @return 文本输入场景事件
     */
    public static SceneEvent ofText(SceneEventType type, SceneNode target,
                                    String text, long timeNanos) {
        return new SceneEvent(type, target,
                0, 0, SceneMouseButton.NONE, 0,
                false, false, false, false, timeNanos,
                null, null, false, text);
    }

    /** @return 事件类型 */
    public SceneEventType getType() { return type; }

    /** @return 最深命中目标节点 */
    public SceneNode getTarget() { return target; }

    /**
     * @return 指针屏幕绝对 X 坐标（含 rootAbsX，= {@link ScenePointerEvent#getLogicalX()}；raw 层）。
     *         仅供 hit-test 内部与跨窗口/跨树辅助，禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 混比。
     */
    public int getPointerX() { return pointerX; }

    /**
     * @return 指针屏幕绝对 Y 坐标（含 rootAbsY，= {@link ScenePointerEvent#getLogicalY()}；raw 层）。
     *         仅供 hit-test 内部与跨窗口/跨树辅助，禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 混比。
     */
    public int getPointerY() { return pointerY; }

    /**
     * @return 指针 host 局部 X 坐标（= pointerX - rootAbsX，与 {@link SceneGeometry#absoluteBox} 传 0,0 同系；host 层）。
     *         与 absoluteBox(node,0,0) 比对时用此值。rootAbsX=0 时等于 {@link #getPointerX()}，向后兼容。
     */
    public int getHostPointerX() { return hostPointerX; }

    /**
     * @return 指针 host 局部 Y 坐标（= pointerY - rootAbsY，与 {@link SceneGeometry#absoluteBox} 传 0,0 同系；host 层）。
     *         与 absoluteBox(node,0,0) 比对时用此值。rootAbsY=0 时等于 {@link #getPointerY()}，向后兼容。
     */
    public int getHostPointerY() { return hostPointerY; }

    /**
     * @return 命中节点 effectiveTarget 局部 X 坐标（local 层）。
     *         框架自动注入 = hostPointerX - {@link SceneGeometry#absoluteBox}(effectiveTarget,0,0).getX()。
     *         handler 默认消费此值，无需自行做 raw↔host 转换。
     */
    public int getLocalPointerX() { return localPointerX; }

    /**
     * @return 命中节点 effectiveTarget 局部 Y 坐标（local 层）。
     *         框架自动注入 = hostPointerY - {@link SceneGeometry#absoluteBox}(effectiveTarget,0,0).getY()。
     *         handler 默认消费此值，无需自行做 raw↔host 转换。
     */
    public int getLocalPointerY() { return localPointerY; }

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

    /** @return 按键标识，非键盘事件返回 null */
    public SceneKey getKey() { return key; }

    /** @return 按键动作，非键盘事件返回 null */
    public SceneKeyAction getKeyAction() { return keyAction; }

    /** @return 是否为按键重复事件 */
    public boolean isRepeat() { return repeat; }

    /** @return 文本内容，非 TEXT_INPUT 事件返回 null */
    public String getText() { return text; }
}
