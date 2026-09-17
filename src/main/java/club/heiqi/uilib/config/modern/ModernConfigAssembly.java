package club.heiqi.uilib.config.modern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.ui.ConfigScreen;
import club.heiqi.config.ui.ConfigUI;
import club.heiqi.config.ui.FieldRestorePolicy;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.FontSortFieldRenderer;
import club.heiqi.config.ui.field.FontSortOrderModel;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;

/**
 * uilib 配置页的**宿主无关装配**（零 {@code net.minecraft} 依赖）。
 *
 * <p>职责只有「Schema → 字段定制 → ConfigScreen」这一段：谁提供配置真源、谁把结果包成平台屏，
 * 都不是装配的事。因此同一份装配既服务游戏内配置页（{@link ModernConfigEntry}），
 * 也服务 headless 出图（{@code internal.devtools.headless}）——出图里的字段形态因此**就是**真机形态，
 * 而不是照着真机再写一遍的近似。</p>
 *
 * <p><b>为什么与 {@link ModernConfigEntry} 分成两个类</b>：不是行数问题，是**类加载**问题。
 * 入口类的方法签名含 {@code GuiScreen}，在无 MC 类路径下加载即失败
 * （实测 {@code NoClassDefFoundError: net/minecraft/client/gui/GuiScreen}）；装配若与它同类，
 * headless 出图就被迫带上整包 MC 及其静态初始化面。判据与「宿主窗口上提」同源：
 * 一个类要么是宿主，要么是装配，混在一起就两边都不可复用。</p>
 *
 * <p>字段定制是 uilib 接入层事实（{@code fontSystem.fontSort} 的专用 renderer、
 * {@code fontSystem.characterFontRules} 的三栏编辑器），硬编码留在本类——不进通用
 * {@code FieldRendererRegistry.defaultRegistry()}，也不在使用方各写一份。</p>
 */
public final class ModernConfigAssembly {

    /**
     * 新架构配置文件相对路径（相对 mcDataDir）。
     *
     * <p>包级可见：{@link ChatFrameConfig} 的运行时写入与配置页读写同一份文件，
     * 路径字面量只此一处（避免出现第二份会漂移的拷贝）。</p>
     */
    static final String CONFIG_RELATIVE_PATH = "config/qzuilib-modern.yaml";

    private ModernConfigAssembly() {
    }


    /**
     * 装配配置页屏幕，字体发现快照取当下值。
     *
     * <p>适合没有「listener 注册窗口」的调用方（headless 出图）：它不是「打开游戏内配置页」
     * 这个动作，因此不存在「快照必须先于订阅」的次序约束，那条约束由
     * {@link ModernConfigEntry#createScreen(net.minecraft.client.gui.GuiScreen)} 持有。</p>
     *
     * @param manager     配置真源
     * @param input       平台输入源，可为 null（headless）
     * @param environment 宿主环境端口，不可为 null
     * @return 配置页屏幕
     */
    public static ConfigScreen buildScreen(ConfigManager manager, PlatformInputSource input,
                                           UiEnvironment environment) {
        return buildScreen(manager, input, environment, captureFontSortSnapshot());
    }

    /**
     * 装配配置页屏幕，并显式传入本次打开时冻结的字体发现快照。
     *
     * @param manager             配置真源
     * @param input               平台输入源，可为 null（headless）
     * @param environment         宿主环境端口，不可为 null
     * @param discoveredSnapshot  screen-open 前冻结的发现顺序，见 {@link #captureFontSortSnapshot()}
     * @return 配置页屏幕
     */
    public static ConfigScreen buildScreen(ConfigManager manager, PlatformInputSource input,
                                           UiEnvironment environment, List<String> discoveredSnapshot) {
        return ConfigUI.buildScreen(manager, input, environment,
                registry -> configureFieldRenderers(registry, discoveredSnapshot),
                policy -> configureRestorePolicy(policy, discoveredSnapshot),
                editors -> { });
    }

    /**
     * 注册 uilib 自身配置页的专用字段 renderer。
     *
     * @param registry 字段 renderer 注册表
     */
    static void configureFieldRenderers(FieldRendererRegistry registry) {
        configureFieldRenderers(registry, captureFontSortSnapshot());
    }

    /**
     * 注册 renderer，并显式传入本 screen 的 frozen discovered snapshot。
     *
     * @param registry 字段 renderer 注册表
     * @param discoveredSnapshot 本次打开时冻结的发现顺序
     */
    static void configureFieldRenderers(FieldRendererRegistry registry, List<String> discoveredSnapshot) {
        registry.registerPath("fontSystem.fontSort",
                new FontSortFieldRenderer(discoveredSnapshot));
        registry.registerPath("fontSystem.characterFontRules", new CharacterRuleFieldRenderer());
    }

    /**
     * 注册 uilib 自身配置页的恢复默认策略。
     *
     * @param policy 恢复默认字段策略
     */
    static void configureRestorePolicy(FieldRestorePolicy policy) {
        configureRestorePolicy(policy, captureFontSortSnapshot());
    }

    /**
     * 注册恢复默认策略，并固定使用本 screen 的 discovered snapshot。
     *
     * @param policy 恢复默认字段策略
     * @param discoveredSnapshot 本次打开时冻结的发现顺序
     */
    static void configureRestorePolicy(FieldRestorePolicy policy, List<String> discoveredSnapshot) {
        policy.skip("fontSystem.characterFontRules");
        policy.custom("fontSystem.fontSort", adapter -> {
            adapter.onFieldEdit("fontSystem.fontSort",
                    FontSortOrderModel.merge(discoveredSnapshot, Collections.<String>emptyList()));
        });
    }

    /**
     * 在 ConfigSaveListener/coordinator initial apply 前冻结当前 FontConfig 发现顺序。
     *
     * @return 不可变 canonical discovered snapshot
     */
    static List<String> captureFontSortSnapshot() {
        return FontSortOrderModel.freezeDiscovered(
                Arrays.asList(FontConfig.getFontSortSnapshot()));
    }
}
