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

    /**
     * 执行自定义绘制（携带构建期固化边界的回放期表面入口）。
     *
     * <p>绘制命令回放期统一经该入口调用：表面除了携带 5 参版本的屏幕坐标内容盒，还携带构建期固化的
     * 文档坐标边界/滚动态快照（见 {@link DocumentCustomRenderSurface}）。需要在回放期读取视口/内容/图层
     * 边界或滚动偏移的渲染器（文本控件选区/光标/行号层）应覆写本方法、改读 {@code surface}，从而避免每帧
     * 经 {@code element.getDocumentBounds()} / {@code element.getScrollLeft()} 全树推进动画时间线。</p>
     *
     * <p>默认实现委派到 5 参 {@link #render(UiRenderContext, int, int, int, int)}，使仅用入参坐标的简单渲染器
     * （宿主图标、颜色预览、单行光标等）无需改动即可继续工作。</p>
     *
     * @param surface 回放期表面
     */
    default void render(DocumentCustomRenderSurface surface) {
        render(surface.getContext(), surface.getContentLeft(), surface.getContentTop(), surface.getContentRight(),
                surface.getContentBottom());
    }
}
