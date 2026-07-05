package club.heiqi.uilib.config.modern;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.config.ConfigException;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.ConfigScreen;
import club.heiqi.config.ui.ConfigUI;
import club.heiqi.config.ui.field.CharacterRuleFieldRenderer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.SimpleListFieldRenderer;
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
 * <h3>入口形态</h3>
 * <ul>
 *   <li>{@link #createScreen(GuiScreen)}：同步构建配置屏，供 guiFactory 中转层（{@code ModConfigGui}）
 *       与命令入口统一调用。Forge guiFactory 反射契约要求单参 {@code (GuiScreen)} 构造器，
 *       {@code ModConfigGui} 作为合法中转在内部调用本方法。</li>
 *   <li>{@link #open()}：命令异步触发入口，经 {@link UiScreenManager} 入队延后切换 GuiScreen
 *       （避免在输入分发途中切屏），内部复用 {@link #createScreen(GuiScreen)}。</li>
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
     * 同步构建新栈配置屏。供 guiFactory 中转层（{@code ModConfigGui}）与命令入口统一调用。
     *
     * <p>流程：bootstrap ConfigManager → {@link ConfigUI#buildScreen} 构建 ConfigScreen
     * → 包进 {@link ModernConfigScreen} 返回。</p>
     *
     * <p>bootstrap 失败时返回 parent（回到来源屏，不回无界面状态），调用方无需 null 检查。</p>
     *
     * @param parent 父屏（返回来源 / bootstrap 失败回退目标；可空，仅作为回退值透传）
     * @return 配置屏；bootstrap 失败或 mc 不可用时返回 parent
     */
    public static GuiScreen createScreen(GuiScreen parent) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return parent;
        }
        final File configFile = new File(minecraft.mcDataDir, CONFIG_RELATIVE_PATH);
        final ConfigSchema schema = QzUiLibModernSchema.create();

        final ConfigManager manager;
        try {
            manager = ConfigManager.bootstrap(configFile, schema);
        } catch (ConfigException e) {
            MyMod.LOG.error("新架构配置 bootstrap 失败，回退父屏: " + configFile.getAbsolutePath(), e);
            return parent;
        }

        // 阶段 C C2：挂保存回调 listener，监听 BATCH_SAVE 触发值回灌 + 字体 reload
        manager.eventBus().subscribe(new ConfigSaveListener(manager));

        final PlatformInputSource input = new LwjglInputSource(new LwjglStateReader());
        // P3：经 ConfigUI customizer hook 给 fontSystem.fontSort 挂 draggable=true 的
        // SimpleListFieldRenderer（path 覆盖优先于 type 注册）。
        // fontSort 字段语义为字体优先级排序，行序即配置值，故需拖拽排序支持。
        // P4：characterFontRules 挂 CharacterRuleFieldRenderer —— 字符字体规则字段，
        // YAML 仍是 simpleList，但渲染层拆成「启用/选择器/字体名」三栏编辑 + parse 错误透出。
        // 该 path 硬编码留在 uilib 接入层，不污染通用 FieldRendererRegistry.defaultRegistry()。
        // ConfigScreen extends AbstractSceneHostWidget implements UiSurface，
        // 天然可作 ModernConfigScreen 的 surface 参数。
        final ConfigScreen screen = ConfigUI.buildScreen(manager, input,
                (FieldRendererRegistry registry) -> {
                    registry.registerPath("fontSystem.fontSort", new SimpleListFieldRenderer(true));
                    registry.registerPath("fontSystem.characterFontRules", new CharacterRuleFieldRenderer());
                });
        return new ModernConfigScreen(parent, screen);
    }

    /**
     * 在游戏内打开新架构配置页（命令入口）。
     *
     * <p>经 {@link UiScreenManager} 入队延后切换 GuiScreen（命令触发时机可能在输入分发途中，
     * 需延后避免切屏冲突）；实际构建逻辑复用 {@link #createScreen(GuiScreen)}。</p>
     */
    public static void open() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;

        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                final GuiScreen screen = createScreen(parentScreen);
                // createScreen 失败回退 parentScreen，正常路径不返回 null；保留 null 检查作防御。
                if (screen != null) {
                    minecraft.displayGuiScreen(screen);
                }
            }
        });
    }
}