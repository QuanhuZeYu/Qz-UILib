package club.heiqi.uilib.ui.scene.input;

/**
 * 平台原始输入事件（不可变联合体）。
 *
 * <p>平台适配层将 LWJGL/GLFW 等原生事件转换为此统一表示后推入
 * {@link InputFrameBuilder}。根据 {@link #kind} 区分事件类型：</p>
 * <ul>
 *   <li>{@link RawEventKind#KEY} — 使用 {@link #ofKey} 构造，访问 key/keyAction/native 等字段</li>
 *   <li>{@link RawEventKind#POINTER} — 使用 {@link #ofPointer} 构造，访问 pointerAction/坐标/按钮等字段</li>
 *   <li>{@link RawEventKind#TEXT} — 使用 {@link #ofText} 构造，访问 text 字段</li>
 * </ul>
 *
 * <p>非对应 kind 的字段取默认值：数值类型用 {@link #NATIVE_NONE}（-1），
 * 引用类型用 null。button 无按钮时取 {@link SceneMouseButton#NONE}，非 POINTER 事件取 null。</p>
 */
public class RawInputEvent {

    /** 无效原生值常量，无平台原生数据时使用 */
    public static final int NATIVE_NONE = -1;

    private final RawEventKind kind;
    private final long timeNanos;

    // === 键盘字段 ===
    private final SceneKey key;
    private final SceneKeyAction keyAction;
    private final int nativeKeyCode;
    private final int nativeScanCode;

    // === 修饰键字段 ===
    private final boolean controlDown;
    private final boolean shiftDown;
    private final boolean altDown;
    private final boolean metaDown;

    // === 指针字段 ===
    private final ScenePointerAction pointerAction;
    private final int logicalX;
    private final int logicalY;
    private final SceneMouseButton button;
    private final int wheelDelta;
    private final int deltaX;
    private final int deltaY;

    // === 文本字段 ===
    private final String text;

    /**
     * 私有全字段构造器，由静态工厂方法调用。
     */
    private RawInputEvent(RawEventKind kind, long timeNanos,
                          SceneKey key, SceneKeyAction keyAction,
                          int nativeKeyCode, int nativeScanCode,
                          boolean controlDown, boolean shiftDown, boolean altDown, boolean metaDown,
                           ScenePointerAction pointerAction,
                           int logicalX, int logicalY, SceneMouseButton button,
                           int wheelDelta, int deltaX, int deltaY,
                          String text) {
        this.kind = kind;
        this.timeNanos = timeNanos;
        this.key = key;
        this.keyAction = keyAction;
        this.nativeKeyCode = nativeKeyCode;
        this.nativeScanCode = nativeScanCode;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
        this.altDown = altDown;
        this.metaDown = metaDown;
        this.pointerAction = pointerAction;
        this.logicalX = logicalX;
        this.logicalY = logicalY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.text = text;
    }

    /**
     * 构造键盘原始事件。
     *
     * @param key 平台无关按键
     * @param action 按键动作
     * @param controlDown Ctrl 是否按下
     * @param shiftDown Shift 是否按下
     * @param altDown Alt 是否按下
     * @param metaDown Meta 是否按下
     * @param nativeKeyCode 平台原生键码（逃生舱）
     * @param nativeScanCode 平台原生扫描码（逃生舱）
     * @param timeNanos 事件时间戳（纳秒）
     * @return 键盘原始事件
     */
    public static RawInputEvent ofKey(SceneKey key, SceneKeyAction action,
                                       boolean controlDown, boolean shiftDown,
                                       boolean altDown, boolean metaDown,
                                       int nativeKeyCode, int nativeScanCode,
                                       long timeNanos) {
        return new RawInputEvent(RawEventKind.KEY, timeNanos,
                key, action, nativeKeyCode, nativeScanCode,
                controlDown, shiftDown, altDown, metaDown,
                null, NATIVE_NONE, NATIVE_NONE, null,
                NATIVE_NONE, NATIVE_NONE, NATIVE_NONE, null);
    }

    /**
     * 构造指针原始事件。
     *
     * @param action 指针动作类型
     * @param logicalX 逻辑坐标 X
     * @param logicalY 逻辑坐标 Y
     * @param button 鼠标按钮标识，无按钮时传 {@link SceneMouseButton#NONE}
     * @param wheelDelta 滚轮增量
     * @param deltaX X 方向移动增量
     * @param deltaY Y 方向移动增量
     * @param controlDown Ctrl 是否按下
     * @param shiftDown Shift 是否按下
     * @param altDown Alt 是否按下
     * @param metaDown Meta 是否按下
     * @param timeNanos 事件时间戳（纳秒）
     * @return 指针原始事件
     */
    public static RawInputEvent ofPointer(ScenePointerAction action,
                                           int logicalX, int logicalY,
                                           SceneMouseButton button, int wheelDelta,
                                           int deltaX, int deltaY,
                                           boolean controlDown, boolean shiftDown,
                                           boolean altDown, boolean metaDown,
                                           long timeNanos) {
        return new RawInputEvent(RawEventKind.POINTER, timeNanos,
                null, null, NATIVE_NONE, NATIVE_NONE,
                controlDown, shiftDown, altDown, metaDown,
                action, logicalX, logicalY, button,
                wheelDelta, deltaX, deltaY, null);
    }

    /**
     * 构造文本输入原始事件。
     *
     * @param text 输入的文本内容
     * @param timeNanos 事件时间戳（纳秒）
     * @return 文本输入原始事件
     */
    public static RawInputEvent ofText(String text, long timeNanos) {
        return new RawInputEvent(RawEventKind.TEXT, timeNanos,
                null, null, NATIVE_NONE, NATIVE_NONE,
                false, false, false, false,
                null, NATIVE_NONE, NATIVE_NONE, null,
                NATIVE_NONE, NATIVE_NONE, NATIVE_NONE, text);
    }

    // ==================== getter ====================

    /** @return 事件类型 */
    public RawEventKind getKind() { return kind; }

    /** @return 事件时间戳（纳秒） */
    public long getTimeNanos() { return timeNanos; }

    /** @return 按键标识，非 KEY 事件返回 null */
    public SceneKey getKey() { return key; }

    /** @return 按键动作，非 KEY 事件返回 null */
    public SceneKeyAction getKeyAction() { return keyAction; }

    /**
     * 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖。
     * @return 平台原生键码，无值时返回 {@link #NATIVE_NONE}
     */
    public int getNativeKeyCode() { return nativeKeyCode; }

    /**
     * 逃生舱：仅诊断 UNKNOWN 按键时使用，核心分支不得依赖。
     * @return 平台原生扫描码，无值时返回 {@link #NATIVE_NONE}
     */
    public int getNativeScanCode() { return nativeScanCode; }

    /** @return Ctrl 是否按下 */
    public boolean isControlDown() { return controlDown; }

    /** @return Shift 是否按下 */
    public boolean isShiftDown() { return shiftDown; }

    /** @return Alt 是否按下 */
    public boolean isAltDown() { return altDown; }

    /** @return Meta 是否按下 */
    public boolean isMetaDown() { return metaDown; }

    /** @return 指针动作类型，非 POINTER 事件返回 null */
    public ScenePointerAction getPointerAction() { return pointerAction; }

    /** @return 逻辑坐标 X，非 POINTER 事件返回 {@link #NATIVE_NONE} */
    public int getLogicalX() { return logicalX; }

    /** @return 逻辑坐标 Y，非 POINTER 事件返回 {@link #NATIVE_NONE} */
    public int getLogicalY() { return logicalY; }

    /**
     * @return 鼠标按钮标识，无按钮时返回 {@link SceneMouseButton#NONE}，非 POINTER 事件返回 null
     */
    public SceneMouseButton getButton() { return button; }

    /** @return 滚轮增量，非 SCROLL 事件返回 {@link #NATIVE_NONE} */
    public int getWheelDelta() { return wheelDelta; }

    /** @return X 方向移动增量，非 POINTER 事件返回 {@link #NATIVE_NONE} */
    public int getDeltaX() { return deltaX; }

    /** @return Y 方向移动增量，非 POINTER 事件返回 {@link #NATIVE_NONE} */
    public int getDeltaY() { return deltaY; }

    /** @return 文本内容，非 TEXT 事件返回 null */
    public String getText() { return text; }
}
