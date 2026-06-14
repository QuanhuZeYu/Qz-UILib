package club.heiqi.uilib.internal.devtools.pages;

/**
 * 现代配置模板 demo 的可选模块检测与跳转入口。
 *
 * <p>本类不直接引用 {@code club.heiqi.config} 与现代配置屏幕类型，确保未来
 * config 模块缺失时仍能安全执行 Class.forName 检测并渲染降级提示。</p>
 */
final class UiTestModernConfigDemoLauncher {

    private static final String CONFIG_CLASS_NAME = "club.heiqi.config.Config";
    private static final String MUTABLE_CONFIG_CLASS_NAME = "club.heiqi.config.MutableConfig";

    private UiTestModernConfigDemoLauncher() {}

    /**
     * 检测现代 config 模块能力是否可用。
     *
     * <p>与 {@code ModConfigGui.isModernConfigModuleAvailable} 采用相同的 Class.forName
     * 字符串检测模式，不触发目标类初始化，同时捕获 {@code ClassNotFoundException}
     * 与 {@code LinkageError}。</p>
     *
     * @return 模块可用时返回 true
     */
    static boolean isModernConfigModuleAvailable() {
        return isClassAvailable(CONFIG_CLASS_NAME) && isClassAvailable(MUTABLE_CONFIG_CLASS_NAME);
    }

    /**
     * 打开现代配置模板 demo 屏幕。
     *
     * <p>模块不可用时直接返回，由调用方的文档状态牌承担降级提示。</p>
     */
    static void openDemo() {
        if (!isModernConfigModuleAvailable()) {
            return;
        }
        UiTestModernConfigDemoBridge.openDemo();
    }

    /**
     * 检测指定类名是否可加载。
     *
     * @param className 类全限定名
     * @return 可加载时返回 true
     */
    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, UiTestModernConfigDemoLauncher.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        } catch (LinkageError error) {
            return false;
        }
    }
}
