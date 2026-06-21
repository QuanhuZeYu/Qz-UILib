package club.heiqi.uilib.ui.scene;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 平台无关的 scene UI 渲染面，实现类负责持有 scene pipeline 所有内部状态。
 */
public interface UiSurface {

    /**
     * 由 McScreenBridge.drawScreen 调用，驱动完整 scene pipeline。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染上下文
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    void render(int w, int h, UiRenderContext ctx, int absX, int absY);

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
     * 随屏幕关闭销毁，回收 reactive 资源。
     */
    void dispose();
}
