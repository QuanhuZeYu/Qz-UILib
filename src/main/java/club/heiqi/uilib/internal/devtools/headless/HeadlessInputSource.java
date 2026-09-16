package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.KeyboardTextInputSource;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;

/**
 * headless 平台输入源：{@link HeadlessInputDevice} 与生产封板器之间的适配层。
 *
 * <p>职责边界：设备模型只表达用户动作与时间轴；本类负责把「一帧」这个边界告诉设备
 * （{@link #drainFrame()} 时先推进设备、再封板），因此帧划分与生产帧管线天然对齐，
 * 不需要会话或调用方手工对齐帧号。</p>
 *
 * <p>契约遵守：无事件时封板器返回 {@link SceneInputFrame#EMPTY} 单例；指针位置与修饰键为粘滞态；
 * 逻辑视口尺寸与请求尺寸一致。文本输入同时支持两条路径——{@link #pushKeyTyped}（逐字符）
 * 与 {@link #pushText}（整串，外部文本接管 / IME 语义），两者都经生产 TEXT 事件进入帧。</p>
 */
public final class HeadlessInputSource implements PlatformInputSource, KeyboardTextInputSource {

    private final int width;
    private final int height;
    private final InputFrameBuilder builder;
    private final HeadlessInputDevice device;
    private boolean externalTextMode;

    /**
     * 创建自带设备的输入源。
     *
     * @param width  逻辑视口宽
     * @param height 逻辑视口高
     */
    public HeadlessInputSource(int width, int height) {
        this(width, height, new HeadlessInputDevice());
    }

    /**
     * 创建输入源。
     *
     * @param width  逻辑视口宽
     * @param height 逻辑视口高
     * @param device 设备模型，不可为 null
     */
    public HeadlessInputSource(int width, int height, HeadlessInputDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("device 不可为空");
        }
        this.width = width;
        this.height = height;
        this.device = device;
        this.builder = new InputFrameBuilder(0, 0);
    }

    /** @return 设备模型（装载脚本、追加动作的入口） */
    public HeadlessInputDevice device() {
        return device;
    }

    @Override
    public SceneInputFrame drainFrame() {
        device.pumpFrame(builder);
        return builder.drainFrame();
    }

    @Override
    public int logicalWidth() {
        return width;
    }

    @Override
    public int logicalHeight() {
        return height;
    }

    @Override
    public void pushKeyTyped(char typedChar, int nativeKeyCode, long timeNanos) {
        builder.push(RawInputEvent.ofText(String.valueOf(typedChar), timeNanos));
    }

    @Override
    public void pushText(String text, long timeNanos) {
        if (text != null && !text.isEmpty()) {
            builder.push(RawInputEvent.ofText(text, timeNanos));
        }
    }

    @Override
    public void setExternalTextMode(boolean external) {
        this.externalTextMode = external;
    }

    /** @return 当前是否处于外部文本接管态 */
    public boolean isExternalTextMode() {
        return externalTextMode;
    }
}
