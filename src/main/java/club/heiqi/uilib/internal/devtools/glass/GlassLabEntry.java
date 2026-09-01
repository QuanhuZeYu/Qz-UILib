package club.heiqi.uilib.internal.devtools.glass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.screen.UiScreenManager;

/**
 * 磨玻璃实验室打开入口（/qzuilib glass）。
 *
 * <p>链路与 {@code TestPlaygroundEntry} 同型：命令触发经 {@link UiScreenManager}
 * 入队延后切换 GuiScreen；headless 环境安全降级（open 静默返回、createScreen 回传 parent）。</p>
 */
public final class GlassLabEntry {

    private GlassLabEntry() {
    }

    /**
     * 同步构建磨玻璃实验室屏。
     *
     * @param parent 父屏（关闭后返回；MC 不可用时原样回传）
     * @return 实验室屏；MC 客户端不可用时返回 parent
     */
    public static GuiScreen createScreen(GuiScreen parent) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return parent;
        }
        final PlatformInputSource input = new LwjglInputSource(new LwjglStateReader());
        final GlassLabHost host = new GlassLabHost(input);
        return new GlassLabScreen(parent, host);
    }

    /**
     * 在游戏内打开磨玻璃实验室（命令入口）。
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
                if (screen != null) {
                    minecraft.displayGuiScreen(screen);
                }
            }
        });
    }
}
