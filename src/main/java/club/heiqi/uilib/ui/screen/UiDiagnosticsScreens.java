package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.client.MinecraftInventoryOverviewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 内建诊断页与示例页入口。
 */
public final class UiDiagnosticsScreens {

    public static final UiDocumentScreens.PageDescriptor UI_TEST = new UiDocumentScreens.PageDescriptor("ui_test");
    public static final UiDocumentScreens.PageDescriptor UI_TEST_LAYOUT = new UiDocumentScreens.PageDescriptor(
            "ui_test_layout");
    public static final UiDocumentScreens.PageDescriptor HTML_LIKE_SMOKE = new UiDocumentScreens.PageDescriptor(
            "html_like_smoke");
    public static final UiDocumentScreens.PageDescriptor HTML_LIKE_GLASS = new UiDocumentScreens.PageDescriptor(
            "html_like_glass");
    public static final UiDocumentScreens.PageDescriptor INVENTORY_OVERVIEW = new UiDocumentScreens.PageDescriptor(
            "inventory_overview");

    static final UiDocumentScreens.DocumentScreenDefinition<UiTestMenuModel> UI_TEST_DEFINITION = new UiDocumentScreens.DocumentScreenDefinition<UiTestMenuModel>(
            UI_TEST, DocumentScreenChrome::resolve, new UiDocumentScreens.DocumentPageControllerFactory<UiTestMenuModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, UiTestMenuModel provision) {
                    return new UiTestDocumentPageController(documentUi, documentPage, provision);
                }
            });
    static final UiDocumentScreens.DocumentScreenDefinition<Void> UI_TEST_LAYOUT_DEFINITION = new UiDocumentScreens.DocumentScreenDefinition<Void>(
            UI_TEST_LAYOUT, DocumentScreenChrome::resolve, new UiDocumentScreens.DocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, Void provision) {
                    return new UiLayoutDiagnosticsDocumentPageController(documentUi, documentPage, runtimeView, pageId);
                }
            });
    static final UiDocumentScreens.DocumentScreenDefinition<Void> HTML_LIKE_SMOKE_DEFINITION = new UiDocumentScreens.DocumentScreenDefinition<Void>(
            HTML_LIKE_SMOKE, DocumentScreenChrome::resolve, new UiDocumentScreens.DocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, Void provision) {
                    return new HtmlLikeSmokeDocumentPageController(documentUi, documentPage);
                }
            });
    static final UiDocumentScreens.DocumentScreenDefinition<Void> HTML_LIKE_GLASS_DEFINITION = new UiDocumentScreens.DocumentScreenDefinition<Void>(
            HTML_LIKE_GLASS, DocumentScreenChrome::resolve, new UiDocumentScreens.DocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, Void provision) {
                    return new HtmlLikeGlassDocumentPageController(documentUi, documentPage);
                }
            });
    static final UiDocumentScreens.DocumentScreenDefinition<InventoryOverviewModel> INVENTORY_OVERVIEW_DEFINITION = new UiDocumentScreens.DocumentScreenDefinition<InventoryOverviewModel>(
            INVENTORY_OVERVIEW, DocumentScreenChrome::resolve,
            new UiDocumentScreens.DocumentPageControllerFactory<InventoryOverviewModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, InventoryOverviewModel provision) {
                    return new HtmlLikeInventoryOverviewDocumentPageController(documentUi, documentPage, runtimeView,
                            provision);
                }
            });

    private UiDiagnosticsScreens() {}

    /**
     * 创建诊断菜单。
     */
    public static GuiScreen createUiTest() {
        return createUiTest(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式环境创建诊断菜单。
     */
    public static GuiScreen createUiTest(UiDocumentScreens.DocumentScreenEnvironment environment) {
        UiDocumentScreens.DocumentScreenEnvironment resolvedEnvironment = Objects.requireNonNull(environment,
                "environment");
        return UiDocumentScreens.createDefinitionBackedScreen(UI_TEST_DEFINITION, resolvedEnvironment,
                createDefaultUiTestMenuModel(resolvedEnvironment));
    }

    /**
     * 创建布局诊断页。
     */
    public static GuiScreen createUiTestLayout() {
        return createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式环境创建布局诊断页。
     */
    public static GuiScreen createUiTestLayout(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return UiDocumentScreens.createDefinitionBackedScreen(UI_TEST_LAYOUT_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 HTML-like smoke 页。
     */
    public static GuiScreen createHtmlLikeSmoke() {
        return createHtmlLikeSmoke(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式环境创建 HTML-like smoke 页。
     */
    public static GuiScreen createHtmlLikeSmoke(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return UiDocumentScreens.createDefinitionBackedScreen(HTML_LIKE_SMOKE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建 Glass Lab 页。
     */
    public static GuiScreen createHtmlLikeGlass() {
        return createHtmlLikeGlass(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式环境创建 Glass Lab 页。
     */
    public static GuiScreen createHtmlLikeGlass(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return UiDocumentScreens.createDefinitionBackedScreen(HTML_LIKE_GLASS_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 创建背包概览示例页。
     */
    public static GuiScreen createInventoryOverview(InventoryOverviewModel model) {
        return createInventoryOverview(UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults(), model);
    }

    /**
     * 基于显式环境创建背包概览示例页。
     */
    public static GuiScreen createInventoryOverview(UiDocumentScreens.DocumentScreenEnvironment environment,
            InventoryOverviewModel model) {
        return UiDocumentScreens.createDefinitionBackedScreen(INVENTORY_OVERVIEW_DEFINITION,
                Objects.requireNonNull(environment, "environment"), Objects.requireNonNull(model, "model"));
    }

    /**
     * 判断界面是否为诊断菜单。
     */
    public static boolean isUiTest(GuiScreen screen) {
        return isUiTest((Object) screen);
    }

    static boolean isUiTest(Object screen) {
        return UiDocumentScreens.hasPageId(screen, UI_TEST.getPageId());
    }

    static boolean isUiTestLayout(Object screen) {
        return UiDocumentScreens.hasPageId(screen, UI_TEST_LAYOUT.getPageId());
    }

    static boolean isHtmlLikeSmoke(Object screen) {
        return UiDocumentScreens.hasPageId(screen, HTML_LIKE_SMOKE.getPageId());
    }

    static boolean isHtmlLikeGlass(Object screen) {
        return UiDocumentScreens.hasPageId(screen, HTML_LIKE_GLASS.getPageId());
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
