package club.heiqi.uilib.ui.scene.input.mock;

import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;

/**
 * 平台输入源 Mock 实现，用于单元测试。
 *
 * <p>内部委托真实 {@link InputFrameBuilder}，通过便捷的 enqueue 方法模拟
 * 平台推送各类原始事件，验证封板状态机和事件投影的正确性。</p>
 */
public class MockPlatformInputSource implements PlatformInputSource {

    private final InputFrameBuilder builder;
    private int logicalWidth;
    private int logicalHeight;

    /**
     * 构造 Mock 输入源。
     *
     * @param logicalWidth 逻辑视口宽度
     * @param logicalHeight 逻辑视口高度
     */
    public MockPlatformInputSource(int logicalWidth, int logicalHeight) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.builder = new InputFrameBuilder(0, 0);
    }

    /**
     * 入队一个键盘事件（不含原生码）。
     *
     * @param key 平台无关按键
     * @param action 按键动作
     * @param ctrl Ctrl 是否按下
     * @param shift Shift 是否按下
     * @param alt Alt 是否按下
     * @param meta Meta 是否按下
     * @param timeNanos 时间戳（纳秒）
     */
    public void enqueueKey(SceneKey key, SceneKeyAction action,
                           boolean ctrl, boolean shift, boolean alt, boolean meta,
                           long timeNanos) {
        RawInputEvent event = RawInputEvent.ofKey(key, action,
                ctrl, shift, alt, meta,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, timeNanos);
        builder.push(event);
    }

    /**
     * 入队一个带原生码的键盘事件（用于测试 UNKNOWN 逃生舱透传）。
     *
     * @param key 按键标识（通常为 UNKNOWN）
     * @param action 按键动作
     * @param nativeKeyCode 平台原生键码
     * @param nativeScanCode 平台原生扫描码
     * @param timeNanos 时间戳（纳秒）
     */
    public void enqueueKeyWithNative(SceneKey key, SceneKeyAction action,
                                     int nativeKeyCode, int nativeScanCode,
                                     long timeNanos) {
        RawInputEvent event = RawInputEvent.ofKey(key, action,
                false, false, false, false,
                nativeKeyCode, nativeScanCode, timeNanos);
        builder.push(event);
    }

    /**
     * 入队一个指针事件。
     *
     * @param action 指针动作类型
     * @param logicalX 逻辑坐标 X
     * @param logicalY 逻辑坐标 Y
     * @param button 鼠标按钮标识，无按钮时传 {@link SceneMouseButton#NONE}
     * @param wheelDelta 滚轮增量
     * @param deltaX X 移动增量
     * @param deltaY Y 移动增量
     * @param ctrl Ctrl 是否按下
     * @param shift Shift 是否按下
     * @param alt Alt 是否按下
     * @param meta Meta 是否按下
     * @param timeNanos 时间戳（纳秒）
     */
    public void enqueuePointer(ScenePointerAction action,
                               int logicalX, int logicalY,
                               SceneMouseButton button, int wheelDelta,
                               int deltaX, int deltaY,
                               boolean ctrl, boolean shift, boolean alt, boolean meta,
                               long timeNanos) {
        RawInputEvent event = RawInputEvent.ofPointer(action, logicalX, logicalY,
                button, wheelDelta, deltaX, deltaY,
                ctrl, shift, alt, meta, timeNanos);
        builder.push(event);
    }

    /**
     * 入队一个文本输入事件。
     *
     * @param text 输入的文本内容
     * @param timeNanos 时间戳（纳秒）
     */
    public void enqueueText(String text, long timeNanos) {
        RawInputEvent event = RawInputEvent.ofText(text, timeNanos);
        builder.push(event);
    }

    /**
     * 直接入队一个原始事件。
     *
     * @param event 原始输入事件
     */
    public void enqueueRaw(RawInputEvent event) {
        builder.push(event);
    }

    /**
     * 设置逻辑视口尺寸。
     *
     * @param w 逻辑宽度
     * @param h 逻辑高度
     */
    public void setLogicalSize(int w, int h) {
        this.logicalWidth = w;
        this.logicalHeight = h;
    }

    @Override
    public SceneInputFrame drainFrame() {
        return builder.drainFrame();
    }

    @Override
    public int logicalWidth() {
        return logicalWidth;
    }

    @Override
    public int logicalHeight() {
        return logicalHeight;
    }
}
