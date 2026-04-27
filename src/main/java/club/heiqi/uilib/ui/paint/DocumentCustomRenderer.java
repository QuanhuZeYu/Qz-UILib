package club.heiqi.uilib.ui.paint;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * HTML-like 自定义绘制回调接口，供控件在元素背景/边框之后注入额外渲染。
 */
public interface DocumentCustomRenderer {

    /**
     * 执行自定义绘制。
     *
     * @param context 渲染上下文
     * @param contentLeft 元素内容区左边界（绝对坐标）
     * @param contentTop 元素内容区上边界（绝对坐标）
     * @param contentRight 元素内容区右边界（绝对坐标）
     * @param contentBottom 元素内容区下边界（绝对坐标）
     */
    void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight, int contentBottom);
}
