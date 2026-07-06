package club.heiqi.uilib.ui.scene;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;

/**
 * 平台无关的 scene UI 渲染面，实现类负责持有 scene pipeline 所有内部状态。
 *
 * <p>渲染出口只认抽象契约 {@link UiRenderBackend}（守宪章信条六 / I6）：本接口位于
 * scene 核心顶层包，绝不 import 任何具体渲染后端类（如 MC 平台的
 * {@code UiRenderContext}）。宿主壳构造具体后端实现后向上转型传入，scene 渲染面
 * 零平台认知，换渲染后端无需触碰本接口。</p>
 */
public interface UiSurface {

    /**
     * 由宿主壳（如 McScreenBridge.drawScreen）调用，驱动完整 scene pipeline。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口（平台无关抽象后端，由宿主壳传入具体实现）
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    void render(int w, int h, UiRenderBackend ctx, int absX, int absY);

    /**
     * 透传 MC keyTyped 事件。
     *
     * @param typedChar 输入字符
     * @param keyCode 原生键码
     */
    void onKeyTyped(char typedChar, int keyCode);

    /**
     * 推入 lwjgl3ify 文本旁路事件。
     *
     * @param text 文本内容
     */
    void pushText(String text);

    /**
     * 设置 lwjgl3ify 外部文本模式。
     *
     * @param external true 表示外部文本事件接管输入
     */
    void setExternalTextMode(boolean external);

    /**
     * 透传 MC mouseClicked/mouseMovedOrUp 回调的指针按钮事件（Bug3 修复）。
     *
     * <p>坐标必须是<b>物理像素</b>（与 {@code LwjglStateReader.mouseX/Y} 同量纲），
     * 调用方（如 {@code McScreenBridge}）负责 scaled→physical 换算。</p>
     *
     * @param action    BUTTON_DOWN 或 BUTTON_UP
     * @param physicalX 物理像素 X
     * @param physicalY 物理像素 Y
     * @param button    鼠标按钮
     * @param timeNanos 事件时间戳（纳秒）
     */
    void onPointerButton(ScenePointerAction action, int physicalX, int physicalY,
                         SceneMouseButton button, long timeNanos);

    /**
     * 设置外部指针模式（按钮事件由宿主回调接管）。
     *
     * @param external true 表示按钮事件走宿主回调旁路
     */
    void setExternalPointerMode(boolean external);

    /**
     * 随屏幕关闭销毁，回收 reactive 资源。
     */
    void dispose();
}
