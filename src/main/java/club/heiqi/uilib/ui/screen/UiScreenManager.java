package club.heiqi.uilib.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;

/**
 * UI 界面全局协调器。
 */
public class UiScreenManager {

    private static final UiScreenManager INSTANCE = new UiScreenManager();

    private UiScreenManager() {}

    /**
     * 获取界面协调器单例。
     *
     * @return 协调器实例
     */
    public static UiScreenManager getInstance() {
        return INSTANCE;
    }

    /**
     * 刷新一帧 UI 输入与界面路由。
     */
    public void tick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        UiInputFrame frame = UiInputService.getInstance().collectFrame();

        GuiScreen currentScreen = minecraft.currentScreen;
        if (currentScreen instanceof BaseScreen) {
            ((BaseScreen) currentScreen).handleInputFrame(frame);
        }
    }
}
