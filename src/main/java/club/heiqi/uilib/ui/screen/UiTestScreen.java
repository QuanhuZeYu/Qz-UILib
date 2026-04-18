package club.heiqi.uilib.ui.screen;

/**
 * 当前阶段的最小布局诊断页。
 */
public class UiTestScreen extends ControllerBackedDocumentScreen {

    private static final UiDocumentScreens.PageDescriptor PAGE_DESCRIPTOR = UiDocumentScreens.UI_TEST;

    public UiTestScreen(UiDocumentScreens.DocumentScreenEnvironment environment) {
        super(environment, PAGE_DESCRIPTOR);
        bindController(new UiTestDocumentPageController(ui(), documentPageAuthoringSurface(), runtimeView(), pageId()));
    }
}
