package club.heiqi.uilib.config.modern;

import java.io.File;

import net.minecraft.client.Minecraft;

import club.heiqi.config.ConfigException;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * 聊天框形态配置项（{@code general.chatFrame}）在 UILib 侧的<b>唯一接线点</b>。
 *
 * <p><b>真源 = 配置文件</b>：{@code custom}（默认）= 改动后的自定义聊天框；
 * {@code vanilla} = 原版聊天框。运行态权威仍是 internal.chat3 的接管开关
 * （{@link ChatMarkdownSettings#isEnabled()}——安装器每渲染帧读它决定接管还是回退原版），
 * 本类只负责把配置值搬进该开关，不持有第二份形态状态。</p>
 *
 * <h3>两条写入通道（同一真源）</h3>
 * <ul>
 *   <li><b>配置页 / 手改 yaml</b>：保存或磁盘重载后经
 *       {@link ConfigValueBridge#applyFromAuthority} 调 {@link #applyConfigured(String)}
 *       回灌运行态，下一渲染帧安装器接管或回退。</li>
 *   <li><b>工具栏「切换聊天框形态」按钮</b>：动作在 internal.chat3 侧
 *       （{@code ChatFrameIntent}），写盘入口是本类 {@link #persistVanilla()}。写盘与回灌
 *       <b>刻意分成两步</b>：按钮侧要把自定义聊天输入屏按既有 CLOSING 动画先收回去，
 *       关屏完成后才调 {@link #applyVanilla()}——提前回灌会让渲染帧安装器在动画期间就注销
 *       聊天 HUD。写盘失败时不回灌也不关屏（按钮侧给聊天栏提示），不让「看到的形态」和
 *       「配置里的形态」分叉。</li>
 * </ul>
 *
 * <h3>为什么按钮要另起一个 ConfigManager</h3>
 * <p>配置页的 manager 随屏生命周期存在，而按钮在聊天屏里、配置页并不在场。这里沿用
 * {@code ModernConfigBootstrap} 的既有用法（bootstrap → 用完即 GC）：读入当前文件 →
 * 开草稿 → 只改本键 → {@link ConfigManager#save} 三阶段事务落盘，用户其它配置值原样保留；
 * 磁盘写前检测（cas）保证不静默覆盖外部并发改动，冲突时返回 false 而不改运行态。</p>
 */
public final class ChatFrameConfig {

    /** 配置项全路径（schema 声明处，唯一真值来源）。 */
    public static final String CONFIG_PATH = "general.chatFrame";

    /** 改动后的自定义聊天框（默认形态）。 */
    public static final String MODE_CUSTOM = "custom";

    /** 原版聊天框。 */
    public static final String MODE_VANILLA = "vanilla";

    private ChatFrameConfig() {
    }

    /**
     * 配置值 → 形态名（大小写不敏感、忽略首尾空白）；{@code null}/空白/未知值回落
     * {@link #MODE_CUSTOM} 并留 WARN。
     *
     * <p>纯函数、可单独复用。非法值走 WARN 失效通道而不是抛异常：配置坏值不得让
     * 客户端起不来，也不得静默吞掉（手改 yaml 写错时现场可定位）。</p>
     *
     * @param configured 配置里的形态名（可为 null）
     * @return {@link #MODE_CUSTOM} 或 {@link #MODE_VANILLA}，恒非 null
     */
    public static String resolve(String configured) {
        if (configured != null) {
            String trimmed = configured.trim();
            if (MODE_VANILLA.equalsIgnoreCase(trimmed)) {
                return MODE_VANILLA;
            }
            if (MODE_CUSTOM.equalsIgnoreCase(trimmed) || trimmed.isEmpty()) {
                return MODE_CUSTOM;
            }
            MyMod.LOG.warn("配置项 {} 的值 \"{}\" 不是合法形态（custom/vanilla），已回落 custom（自定义聊天框）",
                    CONFIG_PATH, configured);
        }
        return MODE_CUSTOM;
    }

    /**
     * 回灌入口：把配置里的形态写进运行态（{@link ConfigValueBridge#applyFromAuthority} 唯一调用点）。
     *
     * @param configured 配置里的形态名（可为 null = 未配置 → custom）
     */
    public static void applyConfigured(String configured) {
        ChatMarkdownSettings.setEnabled(!MODE_VANILLA.equals(resolve(configured)));
    }

    /** 回灌入口：把运行态切到原版聊天框（按钮路径在自定义输入屏关闭完成后调用）。 */
    public static void applyVanilla() {
        ChatMarkdownSettings.setEnabled(false);
    }

    /**
     * 工具栏按钮入口·第一步：把 {@code vanilla} 持久化进配置文件（<b>不改运行态</b>）。
     *
     * @return true = 已落盘；false = 未写盘，调用方不得回灌运行态
     */
    public static boolean persistVanilla() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            MyMod.LOG.warn("聊天框形态持久化失败：客户端不可用");
            return false;
        }
        return persist(new File(minecraft.mcDataDir, ModernConfigAssembly.CONFIG_RELATIVE_PATH), MODE_VANILLA);
    }

    /**
     * 写盘（包级入口，不触碰 Minecraft，便于 headless 验证落盘）。
     *
     * <p><b>只写配置、不回灌运行态</b>：回灌由 {@link #applyConfigured(String)}（配置页 /
     * 启动加载通道）与 {@link #applyVanilla()}（按钮通道）承担，两条通道各自决定时机。</p>
     *
     * @param configFile 新架构配置文件（{@code <mcDir>/config/qzuilib-modern.yaml}）
     * @param mode       目标形态名（经 {@link #resolve(String)} 规范化）
     * @return true = 已写盘；false = 加载/写入未成功，配置文件与运行态都未改变
     */
    static boolean persist(File configFile, String mode) {
        if (configFile == null) {
            MyMod.LOG.warn("聊天框形态持久化失败：配置文件路径为空");
            return false;
        }
        final String normalized = resolve(mode);
        final ConfigManager manager;
        try {
            manager = ConfigManager.bootstrap(configFile, QzUiLibModernSchema.create());
        } catch (ConfigException failure) {
            MyMod.LOG.warn("聊天框形态持久化失败：配置加载异常 {}", configFile.getAbsolutePath(), failure);
            return false;
        }
        DraftBuffer draft = manager.openDraft();
        draft.setDraft(CONFIG_PATH, normalized);
        SaveOutcome outcome = manager.save(draft);
        if (!outcome.isSuccess()) {
            MyMod.LOG.warn("聊天框形态持久化失败：{}（冲突类型 {}）",
                    outcome.errorMessage(), outcome.conflictType());
            return false;
        }
        MyMod.LOG.info("聊天框形态已持久化为 {}（{}）", normalized, configFile.getAbsolutePath());
        return true;
    }
}
