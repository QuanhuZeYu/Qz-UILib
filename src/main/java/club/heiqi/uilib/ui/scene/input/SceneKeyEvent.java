package club.heiqi.uilib.ui.scene.input;

/**
 * 帧内不可变键盘事件。
 *
 * <p>由 {@link InputFrameBuilder} 在封板时从 {@link RawInputEvent} 投影生成，
 * 对象不可变，仅通过 getter 读取字段。</p>
 *
 * <p>nativeKeyCode/nativeScanCode 字段仅在 UNKNOWN 按键诊断时使用，
 * 核心分支不得依赖。</p>
 */
public class SceneKeyEvent {

    private final SceneKey key;
    private final SceneKeyAction action;
    private final boolean controlDown;
    private final boolean shiftDown;
    private final boolean altDown;
    private final boolean metaDown;
    /** 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖 */
    private final int nativeKeyCode;
    /** 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖 */
    private final int nativeScanCode;
    private final long timeNanos;

    /**
     * 包级构造器，仅供 {@link InputFrameBuilder} 封板使用。
     */
    SceneKeyEvent(SceneKey key, SceneKeyAction action,
                  boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                  int nativeKeyCode, int nativeScanCode, long timeNanos) {
        this.key = key;
        this.action = action;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.nativeKeyCode = nativeKeyCode;
        this.nativeScanCode = nativeScanCode;
        this.timeNanos = timeNanos;
    }

    /** @return 按键标识 */
    public SceneKey getKey() { return key; }

    /** @return 按键动作（按下/重复/释放） */
    public SceneKeyAction getAction() { return action; }

    /** @return Ctrl 是否按下 */
    public boolean isControlDown() { return controlDown; }

    /** @return Shift 是否按下 */
    public boolean isShiftDown() { return shiftDown; }

    /** @return Alt 是否按下 */
    public boolean isAltDown() { return altDown; }

    /** @return Meta（Win/Cmd）是否按下 */
    public boolean isMetaDown() { return metaDown; }

    /**
     * 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖。
     * @return 平台原生键码，无值时返回 {@link RawInputEvent#NATIVE_NONE}
     */
    public int getNativeKeyCode() { return nativeKeyCode; }

    /**
     * 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖。
     * @return 平台原生扫描码，无值时返回 {@link RawInputEvent#NATIVE_NONE}
     */
    public int getNativeScanCode() { return nativeScanCode; }

    /** @return 事件时间戳（纳秒） */
    public long getTimeNanos() { return timeNanos; }
}
