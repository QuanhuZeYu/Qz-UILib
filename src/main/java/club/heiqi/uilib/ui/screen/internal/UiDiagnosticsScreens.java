package club.heiqi.uilib.ui.screen.internal;

import java.util.Objects;

import club.heiqi.uilib.client.MinecraftInventoryOverviewModel;
import club.heiqi.uilib.internal.devtools.pages.UiTestMenuModel;
import club.heiqi.uilib.ui.inventory.InventoryOverviewModel;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 内建诊断页与示例页入口。
 *
 * <p>类放在 ui.screen.internal 子包内，名称仍以 Ui 开头但语义上仅供库内开发工具调用，
 * 不构成对业务作者的稳定 API。当前由 {@code DevToolsScreenLauncher} 通过反射调起。</p>
 *
 * @apiNote 内部类型，LTS 不承诺其稳定性。诊断页统一通过 {@code /qzuilib test} 命令打开，
 *          所有 {@code createXxx} 工厂方法均为包级私有，仅供同包工具与 {@code DevToolsScreenLauncher}
 *          反射调起。{@code isXxx} 判断方法对外保持公开，以便宿主与测试识别诊断界面。
 */
public final class UiDiagnosticsScreens {

    private UiDiagnosticsScreens() {}

    /**
     * 创建诊断菜单。
     */
    static GuiScreen createUiTest() {
        return createUiTest(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建诊断菜单。
     */
    static GuiScreen createUiTest(UiDocumentScreens.DocumentScreenEnvironment environment) {
        UiDocumentScreens.DocumentScreenEnvironment resolvedEnvironment = Objects.requireNonNull(environment,
                "environment");
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.UI_TEST_DEFINITION,
                resolvedEnvironment,
                createDefaultUiTestMenuModel(resolvedEnvironment));
    }

    /**
     * 创建布局诊断页。
     */
    static GuiScreen createUiTestLayout() {
        return createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建布局诊断页。
     */
    static GuiScreen createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.UI_TEST_LAYOUT_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建字体性能基线诊断页。
     */
    static GuiScreen createFontPerformanceBaseline() {
        return createFontPerformanceBaseline(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建字体性能基线诊断页。
     */
    static GuiScreen createFontPerformanceBaseline(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.FONT_PERFORMANCE_BASELINE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 HTML-like smoke 页。
     */
    static GuiScreen createHtmlLikeSmoke() {
        return createHtmlLikeSmoke(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建 HTML-like smoke 页。
     */
    static GuiScreen createHtmlLikeSmoke(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.HTML_LIKE_SMOKE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 Glass Lab 页。
     */
    static GuiScreen createHtmlLikeGlass() {
        return createHtmlLikeGlass(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建 Glass Lab 页。
     */
    static GuiScreen createHtmlLikeGlass(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.HTML_LIKE_GLASS_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建背包概览示例页。
     */
    static GuiScreen createInventoryOverview(InventoryOverviewModel model) {
        return createInventoryOverview(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults(), model);
    }

    /**
     * 基于显式环境创建背包概览示例页。
     */
    static GuiScreen createInventoryOverview(UiDocumentScreens.DocumentScreenEnvironment environment,
            InventoryOverviewModel model) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.INVENTORY_OVERVIEW_DEFINITION,
                Objects.requireNonNull(environment, "environment"), Objects.requireNonNull(model, "model"));
    }

    /**
     * 创建列表元素组件拖拽测试页。
     */
    static GuiScreen createListElementDrag() {
        return createListElementDrag(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建列表元素组件拖拽测试页。
     */
    static GuiScreen createListElementDrag(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.LIST_ELEMENT_DRAG_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建浏览器语义新功能展示页。
     */
    static GuiScreen createBrowserSemanticsShowcase() {
        return createBrowserSemanticsShowcase(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建浏览器语义新功能展示页。
     */
    static GuiScreen createBrowserSemanticsShowcase(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.BROWSER_SEMANTICS_SHOWCASE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建动画能力成功展示页。
     */
    static GuiScreen createAnimationCapabilityShowcase() {
        return createAnimationCapabilityShowcase(
                UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建动画能力成功展示页。
     */
    static GuiScreen createAnimationCapabilityShowcase(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.ANIMATION_CAPABILITY_SHOWCASE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 UI 框架结构审查展示页。
     */
    static GuiScreen createUiFrameworkStructureAudit() {
        return createUiFrameworkStructureAudit(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建 UI 框架结构审查展示页。
     */
    static GuiScreen createUiFrameworkStructureAudit(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.UI_FRAMEWORK_STRUCTURE_AUDIT_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建运行时自检页。
     */
    static GuiScreen createRuntimeSelfTest() {
        return createRuntimeSelfTest(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建运行时自检页。
     */
    static GuiScreen createRuntimeSelfTest(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.RUNTIME_SELF_TEST_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建网络层自检页。
     */
    static GuiScreen createNetSelfCheck() {
        return createNetSelfCheck(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建网络层自检页。
     */
    static GuiScreen createNetSelfCheck(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(
                InternalDiagnosticScreenRegistry.NET_SELF_CHECK_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 判断界面是否为诊断菜单。
     */
    public static boolean isUiTest(GuiScreen screen) {
        return isUiTest((Object) screen);
    }

    public static boolean isUiTest(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.uiTestPageId());
    }

    public static boolean isUiTestLayout(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.uiTestLayoutPageId());
    }

    public static boolean isFontPerformanceBaseline(Object screen) {
        return InternalScreenIdentity.hasPageId(screen,
                InternalDiagnosticScreenRegistry.fontPerformanceBaselinePageId());
    }

    public static boolean isHtmlLikeSmoke(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.htmlLikeSmokePageId());
    }

    public static boolean isHtmlLikeGlass(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.htmlLikeGlassPageId());
    }

    public static boolean isListElementDrag(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.listElementDragPageId());
    }

    public static boolean isAnimationCapabilityShowcase(Object screen) {
        return InternalScreenIdentity.hasPageId(screen,
                InternalDiagnosticScreenRegistry.animationCapabilityShowcasePageId());
    }

    public static boolean isUiFrameworkStructureAudit(Object screen) {
        return InternalScreenIdentity.hasPageId(screen,
                InternalDiagnosticScreenRegistry.uiFrameworkStructureAuditPageId());
    }

    public static boolean isRuntimeSelfTest(Object screen) {
        return InternalScreenIdentity.hasPageId(screen,
                InternalDiagnosticScreenRegistry.runtimeSelfTestPageId());
    }

    public static boolean isNetSelfCheck(Object screen) {
        return InternalScreenIdentity.hasPageId(screen,
                InternalDiagnosticScreenRegistry.netSelfCheckPageId());
    }

    /**
     * 创建默认诊断菜单跳转模型。
     */
    private static UiTestMenuModel createDefaultUiTestMenuModel(
            final UiDocumentScreens.DocumentScreenEnvironment environment) {
        return new UiTestMenuModel() {
            @Override
            public void openLayoutDiagnostics() {
                Minecraft.getMinecraft().displayGuiScreen(createUiTestLayout(environment));
            }

            @Override
            public void openFontPerformanceBaseline() {
                Minecraft.getMinecraft().displayGuiScreen(createFontPerformanceBaseline(environment));
            }

            @Override
            public void openHtmlLikeSmoke() {
                Minecraft.getMinecraft().displayGuiScreen(createHtmlLikeSmoke(environment));
            }

            @Override
            public void openHtmlLikeGlass() {
                Minecraft.getMinecraft().displayGuiScreen(createHtmlLikeGlass(environment));
            }

            @Override
            public void openInventoryOverview() {
                Minecraft.getMinecraft().displayGuiScreen(createInventoryOverview(environment,
                        new MinecraftInventoryOverviewModel()));
            }

            @Override
            public void openListElementDrag() {
                Minecraft.getMinecraft().displayGuiScreen(createListElementDrag(environment));
            }

            @Override
            public void openBrowserSemanticsShowcase() {
                Minecraft.getMinecraft().displayGuiScreen(createBrowserSemanticsShowcase(environment));
            }

            @Override
            public void openAnimationCapabilityShowcase() {
                Minecraft.getMinecraft().displayGuiScreen(createAnimationCapabilityShowcase(environment));
            }

            @Override
            public void openUiFrameworkStructureAudit() {
                Minecraft.getMinecraft().displayGuiScreen(createUiFrameworkStructureAudit(environment));
            }

            @Override
            public void openRuntimeSelfTest() {
                Minecraft.getMinecraft().displayGuiScreen(createRuntimeSelfTest(environment));
            }

            @Override
            public void openNetSelfCheck() {
                Minecraft.getMinecraft().displayGuiScreen(createNetSelfCheck(environment));
            }
        };
    }
}
