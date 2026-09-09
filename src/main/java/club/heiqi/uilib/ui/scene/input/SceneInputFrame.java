package club.heiqi.uilib.ui.scene.input;

import java.util.Collections;
import java.util.List;

/**
 * 一帧输入快照（不可变）。
 *
 * <p>由 {@link InputFrameBuilder#drainFrame()} 生成，包含本帧内收集的所有输入事件
 * 以及帧级的聚合状态（指针位置、修饰键、帧时间戳）。对象不可变，所有列表为只读。</p>
 *
 * <p>通过 {@link #EMPTY} 单例表示空帧，避免无事件时不必要的对象分配。
 * EMPTY 单例仅在粘滞态（指针位置/修饰键）为默认值时命中，指针离开原点后
 * 每个无事件帧会构造新帧，属预期行为（帧携带粘滞态快照的必然代价）。</p>
 */
public class SceneInputFrame {

    /** 空帧单例：三列表空 + 指针位置默认 0 / 修饰键 false / 时间 0 */
    public static final SceneInputFrame EMPTY = new SceneInputFrame(
            Collections.<SceneKeyEvent>emptyList(),
            Collections.<ScenePointerEvent>emptyList(),
            Collections.<SceneTextEvent>emptyList(),
            0, 0, false, false, false, false, 0L);

    private final List<SceneKeyEvent> keyEvents;
    private final List<ScenePointerEvent> pointerEvents;
    private final List<SceneTextEvent> textEvents;
    private final int pointerX;
    private final int pointerY;
    private final boolean controlDown;
    private final boolean shiftDown;
    private final boolean altDown;
    private final boolean metaDown;
    private final long frameTimeNanos;

    /**
     * 包级构造器。
     *
     * <p>调用方负责传入已防御性拷贝且不可修改的列表。
     * {@link InputFrameBuilder} 在封板时完成拷贝与不可变包裹。</p>
     *
     * @param keyEvents 键盘事件列表（不可变）
     * @param pointerEvents 指针事件列表（不可变）
     * @param textEvents 文本事件列表（不可变）
     * @param pointerX 帧末指针逻辑 X 坐标
     * @param pointerY 帧末指针逻辑 Y 坐标
     * @param controlDown 帧末 Ctrl 状态
     * @param shiftDown 帧末 Shift 状态
     * @param altDown 帧末 Alt 状态
     * @param metaDown 帧末 Meta 状态
     * @param frameTimeNanos 帧时间戳（取本帧最后一个事件的时间；无事件时为 0）
     */
    SceneInputFrame(List<SceneKeyEvent> keyEvents,
                    List<ScenePointerEvent> pointerEvents,
                    List<SceneTextEvent> textEvents,
                    int pointerX, int pointerY,
                    boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                    long frameTimeNanos) {
        this.keyEvents = keyEvents;
        this.pointerEvents = pointerEvents;
        this.textEvents = textEvents;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.frameTimeNanos = frameTimeNanos;
    }

    /**
     * 判断此帧是否为空（三个事件列表均为空）。
     *
     * @return true 表示本帧无任何输入事件
     */
    public boolean isEmpty() {
        return keyEvents.isEmpty() && pointerEvents.isEmpty() && textEvents.isEmpty();
    }

    /**
     * 宿主边界的指针坐标缩放；保留按钮点击计数、键盘、文本、修饰键和事件时间。
     * 同时转换已排队事件与帧末指针，避免倍率切换时两者分属不同坐标域。
     *
     * @param factor 从输入坐标到宿主 logical px 的倍率（正有限数）
     * @return 不可变坐标副本；倍率为 1 时返回本帧
     */
    public SceneInputFrame scalePointerCoordinates(float factor) {
        if (!(factor > 0F) || Float.isInfinite(factor)) {
            throw new IllegalArgumentException("pointer scale must be positive and finite");
        }
        if (factor == 1F) return this;
        java.util.ArrayList<ScenePointerEvent> pointers = new java.util.ArrayList<ScenePointerEvent>(pointerEvents.size());
        for (ScenePointerEvent event : pointerEvents) {
            pointers.add(new ScenePointerEvent(event.getAction(),
                    (int) Math.floor(event.getLogicalX() * factor),
                    (int) Math.floor(event.getLogicalY() * factor), event.getButton(), event.getWheelDelta(),
                    Math.round(event.getDeltaX() * factor), Math.round(event.getDeltaY() * factor),
                    event.isControlDown(), event.isShiftDown(), event.isAltDown(), event.isMetaDown(),
                    event.getClickCount(), event.getTimeNanos()));
        }
        return new SceneInputFrame(keyEvents, Collections.unmodifiableList(pointers), textEvents,
                (int) Math.floor(pointerX * factor), (int) Math.floor(pointerY * factor),
                controlDown, shiftDown, altDown, metaDown, frameTimeNanos);
    }

    // ==================== getter ====================

    /** @return 本帧键盘事件列表（不可变） */
    public List<SceneKeyEvent> getKeyEvents() { return keyEvents; }

    /** @return 本帧指针事件列表（不可变） */
    public List<ScenePointerEvent> getPointerEvents() { return pointerEvents; }

    /** @return 本帧文本事件列表（不可变） */
    public List<SceneTextEvent> getTextEvents() { return textEvents; }

    /** @return 帧末指针逻辑 X 坐标 */
    public int getPointerX() { return pointerX; }

    /** @return 帧末指针逻辑 Y 坐标 */
    public int getPointerY() { return pointerY; }

    /** @return 帧末 Ctrl 是否按下 */
    public boolean isControlDown() { return controlDown; }

    /** @return 帧末 Shift 是否按下 */
    public boolean isShiftDown() { return shiftDown; }

    /** @return 帧末 Alt 是否按下 */
    public boolean isAltDown() { return altDown; }

    /** @return 帧末 Meta 是否按下 */
    public boolean isMetaDown() { return metaDown; }

    /**
     * 帧时间戳。
     *
     * <p>取本帧最后一个 push 事件的 timeNanos；若本帧无任何事件则为 0。</p>
     *
     * @return 帧时间戳（纳秒）
     */
    public long getFrameTimeNanos() { return frameTimeNanos; }
}
