package club.heiqi.uilib.config;

import club.heiqi.uilib.config.modern.ModernConfigEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Qz UILib 的游戏内配置页入口（ModGuiFactory.mainConfigGuiClass 反射指向的合法中转层）。
 *
 * <p>入口由本类中转到新栈 {@link ModernConfigEntry#createScreen}，
 * 旧栈 ForgeConfigTemplateScreen / Spec / createForgeSpec / createBaseSpec 段
 * 随本次切换一并清除。保存链路通过 C2 {@code ConfigSaveListener}（监听新栈 ConfigManager
 * eventBus BATCH_SAVE）触发 ConfigValueBridge 回灌 + FontService.reload——不再依赖
 * 旧 Spec 的 setSaveHandler 桥接。</p>
 *
 * <h3>不可动</h3>
 * <ul>
 *   <li>单参 {@code (GuiScreen)} 构造器必须保留——ModGuiFactory 反射调用单参构造器契约</li>
 *   <li>{@code ModGuiFactory.mainConfigGuiClass} 仍返回 {@code ModConfigGui.class}，反射入口不变</li>
 * </ul>
 *
 * <h3>C5/C6 暂留死代码窗口（commit 边界，待阶段 D/E 收敛）</h3>
 * <p>本切换后 {@code Config.java} 的 {@code init/saveAndReload/registerEvents/onConfigChangeEvent/
 * applyLoadedFontConfig/configuration} 仍被 {@code ConfigTemplateSyncManager:577-599} 与
 * {@code CommonProxy.preInit:34} 引用，保留到阶段 D（删旧栈 24 文件）+ 阶段 E
 * （删 ConfigTemplateSyncManager）时统一收敛。{@code onConfigChangeEvent} 在
 * mainConfigGuiClass 非 Forge GuiConfig 后已无触发路径，是死代码但保留无害。</p>
 */
public class ModConfigGui extends GuiScreen {

    private final GuiScreen parentScreen;

    /**
     * 创建配置界面入口（GuiScreen 单参构造器，ModGuiFactory 反射契约依赖）。
     *
     * @param parentScreen 父界面
     */
    public ModConfigGui(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    /**
     * 重写自 GuiScreen：界面初始化入口，将显示中转到新栈配置屏。
     *
     * <p>调用 {@link #createTargetScreen(parentScreen)} 构建 ModernConfigEntry 新栈
     * 配置屏，并通过 {@link Minecraft#displayGuiScreen} 替换当前屏。displayGuiScreen
     * 在此不构成递归：新屏替换 ModConfigGui 后本实例立即释放，不会再次触发本方法。</p>
     *
     * <p>{@code Minecraft == null} 为防御性检查：极早期启动或极端卸载场景下
     * {@link Minecraft#getMinecraft()} 可能返回 null，此时放弃中转交由上层默认空屏处理。</p>
     */
    @Override
    public void initGui() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            minecraft.displayGuiScreen(createTargetScreen(parentScreen));
        }
    }

    /**
     * 重写自 GuiScreen：配置页不暂停游戏。
     *
     * <p>返回 false 以与单机调试 / 服务器内调参场景兼容——玩家可在打开配置页时
     * 保持世界 tick 推进，便于实时观察配置项变更效果。</p>
     *
     * @return 固定 false，永不暂停游戏
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 中转到新栈配置页。
     *
     * <p>调用 {@link ModernConfigEntry#createScreen} 同步构建新栈 ConfigScreen +
     * ModernConfigScreen 包装层。bootstrap 失败时 createScreen 返回 parent，由调用方
     * 透传给 displayGuiScreen（回到来源屏，不进无界面状态）。</p>
     *
     * <p>保存回调链路：{@link ModernConfigEntry#createScreen:86-87} 已在 bootstrap 之后
     * 挂 {@code ConfigSaveListener}，监听 BATCH_SAVE 触发 ConfigValueBridge 回灌 +
     * FontService.reload（守 I1）。</p>
     *
     * @param parentScreen 父界面（同时作为 bootstrap 失败回退目标）
     * @return 新栈配置屏；本次切换理论上不返回 parent（除非 mcDataDir/qzuilib-modern.yaml 解析错）
     */
    static GuiScreen createTargetScreen(GuiScreen parentScreen) {
        return ModernConfigEntry.createScreen(parentScreen);
    }
}