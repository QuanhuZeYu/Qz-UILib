package club.heiqi.uilib.ui.paint;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * HTML-like 自定义绘制回调接口，供控件在元素背景/边框之后注入额外渲染。
 *
 * @apiNote 该接口是宿主级逃生口，适合 Minecraft 物品图标、诊断探针等标准
 *          HTML-like paint command 无法表达的绘制；普通业务页面不应直接依赖
 *          {@link UiRenderContext} 手绘背景、边框、文本或布局表面。LTS 不承诺该
 *          渲染后端接口长期稳定。
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
