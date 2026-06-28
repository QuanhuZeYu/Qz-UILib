package club.heiqi.config.ui;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.ui.field.FieldRendererRegistry;

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
     * <p>步骤：</p>
     * <ol>
     *   <li>{@code manager.openDraft()} → {@link DraftBuffer}</li>
     *   <li>{@code new DraftSignalAdapter(null, draft)}（runtime 由 ConfigScreen 内部创建）</li>
     *   <li>{@link FieldRendererRegistry#defaultRegistry()}</li>
     *   <li>{@code new ConfigScreen(null, manager, adapter, registry)}</li>
     * </ol>
     *
     * @param manager 配置管理器
     * @param input   平台输入源，可为 null（headless）
     * @return 配置页屏幕
     */
    public static ConfigScreen buildScreen(ConfigManager manager,
                                           club.heiqi.uilib.ui.scene.input.PlatformInputSource input) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        return new ConfigScreen(input, manager, adapter, registry);
    }
}
