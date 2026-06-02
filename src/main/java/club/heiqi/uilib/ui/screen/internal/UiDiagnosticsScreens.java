package club.heiqi.uilib.ui.screen.internal;

import java.util.Objects;

import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import net.minecraft.client.gui.GuiScreen;

/**
 * 内建 test 规划页入口。
 *
 * <p>类放在 ui.screen.internal 子包内，语义上仅供库内开发工具调用，不构成对业务作者的稳定 API。</p>
 *
 * @apiNote 内部类型，LTS 不承诺其稳定性。当前由 {@code DevToolsScreenLauncher} 通过反射调起。
 */
public final class UiDiagnosticsScreens {

    private UiDiagnosticsScreens() {}

    /**
     * 创建 test 规划页。
     */
    static GuiScreen createUiTest() {
        return createUiTest(UiDocumentScreens.DocumentScreenEnvironment.minecraftFormattedDefaults());
    }

    /**
     * 基于显式环境创建 test 规划页。
     */
    static GuiScreen createUiTest(UiDocumentScreens.DocumentScreenEnvironment environment) {
        return InternalHostedScreenFactory.createScreen(InternalDiagnosticScreenRegistry.UI_TEST_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 判断界面是否为 test 规划页。
     */
    public static boolean isUiTest(GuiScreen screen) {
        return isUiTest((Object) screen);
    }

    /**
     * 判断对象是否为 test 规划页。
     */
    public static boolean isUiTest(Object screen) {
        return InternalScreenIdentity.hasPageId(screen, InternalDiagnosticScreenRegistry.uiTestPageId());
    }
}
