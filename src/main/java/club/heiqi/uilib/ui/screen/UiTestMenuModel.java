package club.heiqi.uilib.ui.screen;

/**
 * 诊断菜单页的最小跳转模型。
 */
interface UiTestMenuModel {

    /**
     * 打开布局诊断子页。
     */
    void openLayoutDiagnostics();

    /**
     * 打开字体性能基线诊断子页。
     */
    void openFontPerformanceBaseline();

    /**
     * 打开 HTML-like smoke 子页。
     */
    void openHtmlLikeSmoke();

    /**
     * 打开大面积磨玻璃测试子页。
     */
    void openHtmlLikeGlass();

    /**
     * 打开背包概览示例页。
     */
    void openInventoryOverview();

    /**
     * 打开列表元素组件拖拽测试子页。
     */
    void openListElementDrag();
}
