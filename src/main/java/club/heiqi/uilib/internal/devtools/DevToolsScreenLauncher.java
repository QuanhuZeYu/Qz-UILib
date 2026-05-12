package club.heiqi.uilib.internal.devtools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import net.minecraft.client.gui.GuiScreen;

/**
 * 通过反射调起库内诊断页面，避免把内部页面工厂重新暴露为公开 API。
 */
final class DevToolsScreenLauncher {

    private static final String DIAGNOSTIC_SCREENS_CLASS_NAME = "club.heiqi.uilib.ui.screen.UiDiagnosticsScreens";

    private DevToolsScreenLauncher() {}

    /**
     * 创建内部诊断菜单。
     *
     * @param environment 文档页面环境
     * @return 诊断菜单页面
     */
    static GuiScreen createDiagnosticsMenu(UiDocumentScreens.DocumentScreenEnvironment environment) {
        try {
            Class<?> diagnosticsClass = Class.forName(DIAGNOSTIC_SCREENS_CLASS_NAME);
            Method createMethod = diagnosticsClass.getDeclaredMethod("createUiTest",
                    UiDocumentScreens.DocumentScreenEnvironment.class);
            createMethod.setAccessible(true);
            return (GuiScreen) createMethod.invoke(null, environment);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("未找到内部诊断页面入口", exception);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("内部诊断菜单工厂缺失", exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("无法访问内部诊断菜单工厂", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("内部诊断菜单创建失败", exception.getCause());
        }
    }
}
