package club.heiqi.config.ui;

import java.util.function.Consumer;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;

/**
 * 配置 UI 门面入口。
 *
 * <p>封装从 {@link ConfigManager} 打开草稿、建适配器、建注册表、建 {@link ConfigScreen}
 * 的完整流程。MC GuiScreen 宿主桥接由 {@link HostBridge} 抽象，config.ui 包零 MC 依赖，
 * 由调用方提供桥接实现。</p>
 *
 * <h3>可测性</h3>
 * <p>{@link #buildScreen} 抽出核心逻辑（建 adapter + registry + screen），不涉及 MC GuiScreen，
 * 可在纯 JVM 测试中调用。{@link #open} 系列方法额外经 {@link HostBridge} 桥接宿主。</p>
 */
public final class ConfigUI {

    /**
     * 宿主桥接：把 {@link ConfigScreen} 接入平台 GuiScreen 体系。
     *
     * <p>config.ui 包不依赖 MC，由调用方实现此接口完成 GuiScreen 切换。</p>
     */
    public interface HostBridge {

        /**
         * 打开配置页屏幕。
         *
         * @param screen 配置页 UI 骨架
         */
        void openScreen(ConfigScreen screen);
    }

    private ConfigUI() {
    }

    /**
     * 打开配置页（无宿主桥接，仅构建屏幕，不切换 GuiScreen）。
     *
     * @param manager 配置管理器
     * @return 配置页屏幕
     */
    public static ConfigScreen open(ConfigManager manager) {
        return open(manager, null);
    }

    /**
     * 打开配置页，经宿主桥接切换 GuiScreen。
     *
     * @param manager 配置管理器
     * @param bridge  宿主桥接，可为 null（仅构建屏幕不切换）
     * @return 配置页屏幕
     */
    public static ConfigScreen open(ConfigManager manager, HostBridge bridge) {
        ConfigScreen screen = buildScreen(manager, null);
        if (bridge != null) {
            bridge.openScreen(screen);
        }
        return screen;
    }

    /**
     * 构建配置页屏幕（可测核心逻辑，不涉及 MC GuiScreen）。
     *
     * <p>等价于 {@link #buildScreen(ConfigManager, PlatformInputSource, Consumer)} 传入
     * 空定制器（不覆盖任何注册），向后兼容。</p>
     *
     * @param manager 配置管理器
     * @param input   平台输入源，可为 null（headless）
     * @return 配置页屏幕
     */
    public static ConfigScreen buildScreen(ConfigManager manager,
                                           PlatformInputSource input) {
        return buildScreen(manager, input, registry -> { });
    }

    /**
     * 构建配置页屏幕，允许使用方在 {@link ConfigScreen} 装配前定制
     * {@link FieldRendererRegistry}（P3 customizer hook）。
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>{@code manager.openDraft()} → {@link DraftBuffer}</li>
     *   <li>{@code new DraftSignalAdapter(null, draft)}（runtime 由 ConfigScreen 内部创建）</li>
     *   <li>{@link FieldRendererRegistry#defaultRegistry()}</li>
     *   <li>调用 {@code registryCustomizer}，使用方可在此
     *       {@link FieldRendererRegistry#registerPath registerPath} 为特定字段挂覆盖 renderer
     *       （如 fontSort 走带拖拽的 SimpleListFieldRenderer），或
     *       {@link FieldRendererRegistry#register register} 替换默认 type 实现</li>
     *   <li>{@code new ConfigScreen(null, manager, adapter, registry)}</li>
     * </ol>
     *
     * <p>框架层不在此处硬编码任何使用方专属 path（如 {@code "fontSystem.fontSort"}），
     * path 覆盖由使用方在 customizer lambda 内注入，保持通用框架与具体使用方解耦。</p>
     *
     * @param manager            配置管理器
     * @param input              平台输入源，可为 null（headless）
     * @param registryCustomizer registry 定制回调，在 defaultRegistry 之后、ConfigScreen 装配前调用；
     *                           不可为 null（无定制需求请用 2 参重载或传 {@code reg -> {}}）
     * @return 配置页屏幕
     * @throws IllegalArgumentException manager 或 registryCustomizer 为 null
     */
    public static ConfigScreen buildScreen(ConfigManager manager,
                                           PlatformInputSource input,
                                           Consumer<FieldRendererRegistry> registryCustomizer) {
        return buildScreen(manager, input, registryCustomizer, policy -> { });
    }

    /**
     * 构建配置页屏幕，允许使用方同时定制字段 renderer 与恢复默认策略。
     *
     * <p>框架层不在此处硬编码任何使用方专属 path（如 {@code "fontSystem.fontSort"}）。
     * renderer 覆盖与恢复默认策略均由使用方在 customizer lambda 内注入，保持通用框架与
     * 具体使用方解耦。</p>
     *
     * @param manager                 配置管理器
     * @param input                   平台输入源，可为 null（headless）
     * @param registryCustomizer      renderer 注册表定制回调，不可为 null
     * @param restorePolicyCustomizer 恢复默认策略定制回调，不可为 null
     * @return 配置页屏幕
     * @throws IllegalArgumentException manager、registryCustomizer 或 restorePolicyCustomizer 为 null
     */
    public static ConfigScreen buildScreen(ConfigManager manager,
                                           PlatformInputSource input,
                                           Consumer<FieldRendererRegistry> registryCustomizer,
                                           Consumer<FieldRestorePolicy> restorePolicyCustomizer) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        if (registryCustomizer == null) {
            throw new IllegalArgumentException("registryCustomizer must not be null");
        }
        if (restorePolicyCustomizer == null) {
            throw new IllegalArgumentException("restorePolicyCustomizer must not be null");
        }
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        registryCustomizer.accept(registry);
        FieldRestorePolicy policy = new FieldRestorePolicy();
        restorePolicyCustomizer.accept(policy);
        return new ConfigScreen(input, manager, adapter, registry, policy);
    }
}
