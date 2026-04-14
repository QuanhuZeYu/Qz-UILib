package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.theme.UiDocumentTheme;

/**
 * 当前阶段的最小背包诊断页。
 */
public class InventoryOverviewScreen extends ControllerBackedDocumentScreen {

    private static final UiDocumentScreens.PageDescriptor PAGE_DESCRIPTOR = UiDocumentScreens.INVENTORY_OVERVIEW;

    public InventoryOverviewScreen(UiDocumentTheme documentTheme, InventoryOverviewModel model) {
        super(documentTheme, PAGE_DESCRIPTOR);
        bindController(new InventoryOverviewDocumentPageController(ui(), documentPageAuthoringSurface(), runtimeView(),
                Objects.requireNonNull(model, "model")));
    }

}
