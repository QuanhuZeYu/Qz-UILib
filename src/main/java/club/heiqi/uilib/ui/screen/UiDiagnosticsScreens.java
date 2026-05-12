package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.client.MinecraftInventoryOverviewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 内建诊断页与示例页入口。
 *
 * <p>该类只供库内开发工具调用，不构成对外稳定 API。</p>
 */
final class UiDiagnosticsScreens {

    private UiDiagnosticsScreens() {}

    /**
     * 创建诊断菜单。
     */
    static GuiScreen createUiTest() {
        return createUiTest(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
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
        return createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式环境创建布局诊断页。
     */
    static GuiScreen createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.UI_TEST_LAYOUT_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 HTML-like smoke 页。
     */
    static GuiScreen createHtmlLikeSmoke() {
        return createHtmlLikeSmoke(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
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
        return createHtmlLikeGlass(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
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
        return createInventoryOverview(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults(), model);
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
     * 判断界面是否为诊断菜单。
     */
    static boolean isUiTest(GuiScreen screen) {
        return isUiTest((Object) screen);
    }

    static boolean isUiTest(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.uiTestPageId());
    }

    static boolean isUiTestLayout(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.uiTestLayoutPageId());
    }

    static boolean isHtmlLikeSmoke(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.htmlLikeSmokePageId());
    }

    static boolean isHtmlLikeGlass(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.htmlLikeGlassPageId());
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
        };
    }
}
