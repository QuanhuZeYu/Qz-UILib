package club.heiqi.uilib.ui.screen.page;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;

/**
 * 文档页面控制器可见的最小宿主运行时视图。
 *
 * <p>该视图只暴露页面控制器在刷新阶段确实需要的只读宿主信息，
 * 避免控制器直接耦合具体 Screen 实现。</p>
 */
public interface DocumentPageRuntimeView {

    /**
     * 获取当前宿主宽度。
     *
     * @return 宿主宽度
     */
    int getHostWidth();

    /**
     * 获取当前宿主高度。
     *
     * @return 宿主高度
     */
    int getHostHeight();

    /**
     * 获取最近一次输入路由记录的鼠标 X。
     *
     * @return 鼠标 X
     */
    int getMouseX();

    /**
     * 获取最近一次输入路由记录的鼠标 Y。
     *
     * @return 鼠标 Y
     */
    int getMouseY();

    /**
     * 获取最近一次完成帧的 UI 运行时统计。
     *
     * @return 运行时统计快照
     */
    UiRuntimeStats getUiRuntimeStats();
}
