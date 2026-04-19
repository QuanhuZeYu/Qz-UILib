package club.heiqi.uilib.ui.screen;

import net.minecraft.client.Minecraft;

/**
 * 当前阶段的诊断菜单页宿主。
 */
public class UiTestScreen extends ControllerBackedDocumentScreen {

    private static final UiDocumentScreens.PageDescriptor PAGE_DESCRIPTOR = UiDocumentScreens.UI_TEST;

    public UiTestScreen(UiDocumentScreens.DocumentScreenEnvironment environment) {
        super(environment, PAGE_DESCRIPTOR);
        final UiDocumentScreens.DocumentScreenEnvironment resolvedEnvironment = environment;
        bindController(new UiTestDocumentPageController(ui(), documentPageAuthoringSurface(), new UiTestMenuModel() {
            @Override
            public void openLayoutDiagnostics() {
                Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createUiTestLayout(resolvedEnvironment));
            }
        }));
    }
}
