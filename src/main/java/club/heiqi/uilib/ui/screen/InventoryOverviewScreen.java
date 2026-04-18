package club.heiqi.uilib.ui.screen;

import java.util.Objects;

/**
 * 当前阶段的最小背包诊断页。
 */
public class InventoryOverviewScreen extends ControllerBackedDocumentScreen {

    private static final UiDocumentScreens.PageDescriptor PAGE_DESCRIPTOR = UiDocumentScreens.INVENTORY_OVERVIEW;

    public InventoryOverviewScreen(UiDocumentScreens.DocumentScreenEnvironment environment, InventoryOverviewModel model) {
        super(environment, PAGE_DESCRIPTOR);
        bindController(new InventoryOverviewDocumentPageController(ui(), documentPageAuthoringSurface(), runtimeView(),
                Objects.requireNonNull(model, "model")));
    }

}
