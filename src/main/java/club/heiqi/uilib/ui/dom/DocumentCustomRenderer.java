package club.heiqi.uilib.ui.dom;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 元素自定义绘制回调。
 *
 * <p>在 paint engine 的 appendBoxCommands 中被包装为 CUSTOM 命令，
 * 在元素背景和边框绘制之后、clip/子树之前执行。</p>
 *
 * <p>CUSTOM 属于宿主级逃生口，普通业务表面应优先使用标准 DOM / 样式 /
 * paint command 表达，不应直接依赖渲染后端手绘。</p>
 */
public interface DocumentCustomRenderer {

    /**
     * 执行自定义绘制。
     *
     * @param context       渲染上下文
     * @param contentLeft   元素 content box 左边界
     * @param contentTop    元素 content box 上边界
     * @param contentRight  元素 content box 右边界
     * @param contentBottom 元素 content box 下边界
     */
    void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight, int contentBottom);
}
