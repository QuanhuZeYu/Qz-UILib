package club.heiqi.uilib.ui.scene.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平台侧输入缓冲与封板器（单线程无锁）。
 *
 * <p>平台适配层在主线程逐事件调用 {@link #push(RawInputEvent)} 推入原始事件，
 * 每帧末调用 {@link #drainFrame()} 排空并封板为 {@link SceneInputFrame}。</p>
 *
 * <h3>封板状态机</h3>
 * <ul>
 *   <li><b>事件列表</b>：{@code drainFrame()} 后清空（一次性增量，不跨帧）。</li>
 *   <li><b>指针位置 pointerX/Y</b>：随 POINTER 事件更新；{@code drainFrame()} 后保留（粘滞，不归零）。</li>
 *   <li><b>修饰键</b>：随带修饰键事件更新；{@code drainFrame()} 后保留（持续按下态）。</li>
 *   <li><b>帧时间戳 frameTimeNanos</b>：取本帧最后一个 push 事件的 timeNanos；
 *       无事件时取 0。</li>
 *   <li>无事件 drainFrame 返回 {@link SceneInputFrame#EMPTY} 单例，不分配新对象。</li>
 * </ul>
 */
public class InputFrameBuilder {

    /** 键盘事件缓冲 */
    private final List<SceneKeyEvent> keyBuffer;
    /** 指针事件缓冲 */
    private final List<ScenePointerEvent> pointerBuffer;
    /** 文本事件缓冲 */
    private final List<SceneTextEvent> textBuffer;

    /** 粘滞指针 X 坐标 */
    private int pointerX;
    /** 粘滞指针 Y 坐标 */
    private int pointerY;

    /** 粘滞 Ctrl 状态 */
    private boolean controlDown;
    /** 粘滞 Shift 状态 */
    private boolean shiftDown;
    /** 粘滞 Alt 状态 */
    private boolean altDown;
    /** 粘滞 Meta 状态 */
    private boolean metaDown;

    /** 本帧最后一个 push 事件的时间戳，无事件时为 0 */
    private long lastTimeNanos;

    /**
     * 构造封板器，指定初始指针位置。
     *
     * @param initialPointerX 初始指针逻辑 X 坐标
     * @param initialPointerY 初始指针逻辑 Y 坐标
     */
    public InputFrameBuilder(int initialPointerX, int initialPointerY) {
        this.keyBuffer = new ArrayList<SceneKeyEvent>();
        this.pointerBuffer = new ArrayList<ScenePointerEvent>();
        this.textBuffer = new ArrayList<SceneTextEvent>();
        this.pointerX = initialPointerX;
        this.pointerY = initialPointerY;
        this.controlDown = false;
        this.shiftDown = false;
        this.altDown = false;
        this.metaDown = false;
        this.lastTimeNanos = 0L;
    }

    /**
     * 推入一个原始输入事件。
     *
     * <p>根据事件的 kind 将其投影为对应的精简事件并追加到内部缓冲，
     * 同时更新帧级聚合态（指针位置随 POINTER 事件更新，修饰键随带修饰键事件更新）。</p>
     *
     * @param event 原始输入事件
     */
    public void push(RawInputEvent event) {
        // 更新帧时间戳
        lastTimeNanos = event.getTimeNanos();

        // 按类型投影并追加
        switch (event.getKind()) {
            case KEY:
                // KEY 事件携带可信修饰键状态，更新粘滞态
                updateModifiers(event);
                keyBuffer.add(projectKey(event));
                break;
            case POINTER:
                // POINTER 事件携带可信修饰键状态，更新粘滞态
                updateModifiers(event);
                pointerBuffer.add(projectPointer(event));
                // 指针位置随 POINTER 事件保持粘滞更新
                pointerX = event.getLogicalX();
                pointerY = event.getLogicalY();
                break;
            case TEXT:
                // TEXT 事件不携带可信修饰键，不更新粘滞态（防止 GLFW char callback 覆盖 key callback 的 mods）
                textBuffer.add(projectText(event));
                break;
            default:
                break;
        }
    }

    /**
     * 排空并封板为不可变输入帧。
     *
     * <p>将当前累积的事件冻结为不可变列表，与当前帧级聚合态一起构造
     * {@link SceneInputFrame}，然后清空事件缓冲。指针位置和修饰键状态保留到下帧。</p>
     *
     * <p>当事件缓冲为空且粘滞态与 {@link SceneInputFrame#EMPTY} 的默认态完全一致时，
     * 返回 EMPTY 单例以避免不必要分配；否则构造包含正确粘滞态的快照帧。</p>
     *
     * @return 当前帧输入快照
     */
    public SceneInputFrame drainFrame() {
        // 空缓冲 + 默认粘滞态 → 返回 EMPTY 单例
        if (isEmpty() && !hasNonDefaultStickyState()) {
            return SceneInputFrame.EMPTY;
        }

        // 防御性拷贝并包裹为不可变列表
        List<SceneKeyEvent> frozenKeys = Collections.unmodifiableList(
                new ArrayList<SceneKeyEvent>(keyBuffer));
        List<ScenePointerEvent> frozenPointers = Collections.unmodifiableList(
                new ArrayList<ScenePointerEvent>(pointerBuffer));
        List<SceneTextEvent> frozenTexts = freezeTextEvents();

        // 记录当前帧时间戳
        long frameTime = lastTimeNanos;

        // 清空事件缓冲（一次性增量，不跨帧）
        keyBuffer.clear();
        pointerBuffer.clear();
        textBuffer.clear();

        // 重置帧时间戳
        lastTimeNanos = 0L;

        // 构造帧快照（粘滞态保留不重置）
        return new SceneInputFrame(frozenKeys, frozenPointers, frozenTexts,
                pointerX, pointerY, controlDown, shiftDown, altDown, metaDown, frameTime);
    }

    /**
     * 判断当前粘滞态是否为非默认态（与 EMPTY 默认态不一致）。
     *
     * <p>当指针位于 (0,0) 且所有修饰键均为 false 时为默认态，
     * 方法返回 false；任何一个条件不满足则返回 true。</p>
     *
     * @return true 表示粘滞态为非默认态（指针非原点或有修饰键按下）
     */
    private boolean hasNonDefaultStickyState() {
        return pointerX != 0 || pointerY != 0
                || controlDown || shiftDown || altDown || metaDown;
    }

    /**
     * 判断当前是否有未排空的缓冲事件。
     *
     * @return true 表示三个事件缓冲均为空
     */
    public boolean isEmpty() {
        return keyBuffer.isEmpty() && pointerBuffer.isEmpty() && textBuffer.isEmpty();
    }

    /**
     * 冻结文本事件，并将同一输入帧内的多条 TEXT 按原顺序合并为一条。
     *
     * <p>文本输入组件是受控组件，运行时会在整帧 route 后统一 flush。
     * 因此同帧多条 TEXT 若逐条派发，会让 handler 多次读取同一个帧初 value。
     * 在封板层合并后，中央事务时序保持不变，handler 仍只需上抛一次完整文本。</p>
     *
     * @return 不可变文本事件列表
     */
    private List<SceneTextEvent> freezeTextEvents() {
        if (textBuffer.size() <= 1) {
            return Collections.unmodifiableList(new ArrayList<SceneTextEvent>(textBuffer));
        }

        StringBuilder mergedText = new StringBuilder();
        long mergedTimeNanos = 0L;
        for (SceneTextEvent textEvent : textBuffer) {
            mergedText.append(textEvent.getText());
            mergedTimeNanos = textEvent.getTimeNanos();
        }

        List<SceneTextEvent> mergedTexts = new ArrayList<SceneTextEvent>(1);
        mergedTexts.add(new SceneTextEvent(mergedText.toString(), mergedTimeNanos));
        return Collections.unmodifiableList(mergedTexts);
    }

    // ==================== 内部投影方法 ====================

    /**
     * 将 KEY 原始事件投影为帧内键盘事件。
     */
    private SceneKeyEvent projectKey(RawInputEvent raw) {
        return new SceneKeyEvent(
                raw.getKey(),
                raw.getKeyAction(),
                raw.isControlDown(),
                raw.isShiftDown(),
                raw.isAltDown(),
                raw.isMetaDown(),
                raw.getNativeKeyCode(),
                raw.getNativeScanCode(),
                raw.getTimeNanos());
    }

    /**
     * 将 POINTER 原始事件投影为帧内指针事件。
     * <p>结构上不含原生字段，杜绝逃生舱泄漏。</p>
     */
    private ScenePointerEvent projectPointer(RawInputEvent raw) {
        return new ScenePointerEvent(
                raw.getPointerAction(),
                raw.getLogicalX(),
                raw.getLogicalY(),
                raw.getButton(),
                raw.getWheelDelta(),
                raw.getDeltaX(),
                raw.getDeltaY(),
                raw.isControlDown(),
                raw.isShiftDown(),
                raw.isAltDown(),
                raw.isMetaDown(),
                raw.getTimeNanos());
    }

    /**
     * 将 TEXT 原始事件投影为帧内文本事件。
     */
    private SceneTextEvent projectText(RawInputEvent raw) {
        return new SceneTextEvent(raw.getText(), raw.getTimeNanos());
    }

    /**
     * 从事件中更新粘滞修饰键状态。
     * <p>每次 push 都会更新，确保修饰键按下/释放都能及时反映。</p>
     */
    private void updateModifiers(RawInputEvent event) {
        // 修饰键状态以事件携带值为准，实现持续态跟踪
        controlDown = event.isControlDown();
        shiftDown = event.isShiftDown();
        altDown = event.isAltDown();
        metaDown = event.isMetaDown();
    }
}
