package club.heiqi.uilib.config.modern;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.config.ConfigException;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.ConfigScreen;
import club.heiqi.config.ui.ConfigUI;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.screen.UiScreenManager;

/**
 * uilib 自身配置接入新架构的入口（实验性）。
 *
 * <p>把 uilib 这个 mod 作为新架构配置页的第一个真实使用方，端到端验证：
 * 声明 Schema → {@link ConfigManager#bootstrap} → {@link ConfigUI#buildScreen}
 * → 包进 {@link ModernConfigScreen} → {@code displayGuiScreen}。</p>
 *
 * <h3>合规边界守护</h3>
 * <ul>
 *   <li>本类位于 {@code uilib.config.modern}（mod 配置接入专门包），非 {@code uilib.ui.*} 通用组件包。</li>
 *   <li>依据决策 {@code ee1e181d}，uilib 作为 mod 自身使用方可直接 import
 *       {@code club.heiqi.config.schema.*} / {@code club.heiqi.config.runtime.*}
 *       / {@code club.heiqi.config.ui.*}（含 {@link ConfigUI}），合法使用新架构全部 API。</li>
 *   <li>仍严守：uilib 通用 UI 组件包（{@code uilib.ui.*}）严禁 import {@code config.ui.*}
 *       ——本类不在通用组件包内，不触发该红线。</li>
 *   <li>{@link LwjglInputSource} / {@link LwjglStateReader} 暂复用 devtools 输入适配器
 *      （uilib 内部实现，非 {@code uilib.ui.*} 通用组件包）；后续输入适配器提到通用位置后可平滑替换。</li>
 * </ul>
 *
 * <h3>配置文件</h3>
 * <p>新架构配置独立于 Forge cfg，使用 YAML 格式存于 {@code config/qzuilib-modern.yaml}，
 * 避免与 Forge 配置互相覆盖。本实验为并行接入，不影响现有 Forge 配置链路。</p>
 */
public final class ModernConfigEntry {

    /** 新架构配置文件相对路径（相对 mcDataDir）。 */
    private static final String CONFIG_RELATIVE_PATH = "config/qzuilib-modern.yaml";

    private ModernConfigEntry() {
    }

    /**
     * 在游戏内打开新架构配置页。
     *
     * <p>流程：bootstrap ConfigManager → {@link ConfigUI#buildScreen} 构建 ConfigScreen
     * → 包进 {@link ModernConfigScreen} → 经 {@link UiScreenManager} 入队切换 GuiScreen。
     * bootstrap 失败时记录日志并提示。</p>
     */
    public static void open() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final File configFile = new File(minecraft.mcDataDir, CONFIG_RELATIVE_PATH);
        final ConfigSchema schema = QzUiLibModernSchema.create();

        final ConfigManager manager;
        try {
            manager = ConfigManager.bootstrap(configFile, schema);
        } catch (ConfigException e) {
            MyMod.LOG.error("新架构配置 bootstrap 失败: " + configFile.getAbsolutePath(), e);
            return;
        }

        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                PlatformInputSource input = new LwjglInputSource(new LwjglStateReader());
                // ConfigScreen extends AbstractSceneHostWidget implements UiSurface，
                // 天然可作 ModernConfigScreen 的 surface 参数。
                ConfigScreen screen = ConfigUI.buildScreen(manager, input);
                ModernConfigScreen mcScreen = new ModernConfigScreen(parentScreen, screen);
                minecraft.displayGuiScreen(mcScreen);
            }
        });
    }
}