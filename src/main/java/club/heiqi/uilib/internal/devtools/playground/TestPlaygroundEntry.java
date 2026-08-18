package club.heiqi.uilib.internal.devtools.playground;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.screen.UiScreenManager;

/**
 * 测试场地打开入口（/qzuilib test）。
 *
 * <p>打开链路与 {@code ModernConfigEntry} 同型：命令触发经 {@link UiScreenManager}
 * 入队延后切换 GuiScreen（命令可能在输入分发途中执行，延后可避免切屏冲突）；
 * 实际构建逻辑复用 {@link #createScreen(GuiScreen)}，单入口供命令与测试共用。</p>
 *
 * <p>头less 环境下（无 MC 客户端）方法安全降级：{@link #open()} 直接返回，
 * {@link #createScreen(GuiScreen)} 返回传入的 parent。</p>
 */
public final class TestPlaygroundEntry {

    private TestPlaygroundEntry() {
    }

    /**
     * 同步构建测试场地屏。
     *
     * @param parent 父屏（返回来源；可空，仅作为回退值透传）
     * @return 测试场地屏；MC 客户端不可用时返回 parent
     */
    public static GuiScreen createScreen(GuiScreen parent) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return parent;
        }
        final PlatformInputSource input = new LwjglInputSource(new LwjglStateReader());
        final TestPlaygroundHost host = new TestPlaygroundHost(input);
        return new TestPlaygroundScreen(parent, host);
    }

    /**
     * 在游戏内打开测试场地（命令入口）。
     *
     * <p>经 {@link UiScreenManager} 入队延后切换 GuiScreen；MC 客户端不可用时静默返回。</p>
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
