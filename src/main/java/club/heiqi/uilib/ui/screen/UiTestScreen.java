package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.theme.UiDocumentTheme;

/**
 * 当前阶段的最小布局诊断页。
 */
public class UiTestScreen extends ControllerBackedDocumentScreen {

    private static final UiDocumentScreens.PageDescriptor PAGE_DESCRIPTOR = UiDocumentScreens.UI_TEST;

    public UiTestScreen(UiDocumentTheme documentTheme) {
        super(documentTheme, PAGE_DESCRIPTOR);
        bindController(new UiTestDocumentPageController(ui(), documentPageAuthoringSurface(), runtimeView(), pageId()));
    }
}
