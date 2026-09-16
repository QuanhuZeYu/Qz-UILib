package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;

/**
 * headless 平台输入源：向生产链路提供一个「当前无事件」的合规输入源。
 *
 * <p>契约遵守：{@link #drainFrame()} 无事件时返回 {@link SceneInputFrame#EMPTY} 单例（不分配新对象），
 * 逻辑视口尺寸与请求尺寸一致（供 UI 做坐标归一化与布局）。</p>
 *
 * <p>演进方向（M3 输入设备模型）：本类将持有 {@code HeadlessInputDevice} 的逐帧事件累积，
 * 把鼠标（移动/按键/滚轮/拖拽）、键盘（按下/释放/重复/修饰键）、文本（char/整串/IME 接管）
 * 与焦点/失焦规范化为 {@link SceneInputFrame}，仍然只走生产链路
 * {@code PlatformInputSource → SceneInputFrame → SceneRuntime.route}，不旁路 router。</p>
 */
public final class HeadlessInputSource implements PlatformInputSource {

    private final int width;
    private final int height;

    /**
     * @param width  逻辑视口宽
     * @param height 逻辑视口高
     */
    public HeadlessInputSource(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public SceneInputFrame drainFrame() {
        return SceneInputFrame.EMPTY;
    }

    @Override
    public int logicalWidth() {
        return width;
    }

    @Override
    public int logicalHeight() {
        return height;
    }
}
