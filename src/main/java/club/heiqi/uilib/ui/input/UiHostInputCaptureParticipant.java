package club.heiqi.uilib.ui.input;

import net.minecraft.client.gui.GuiScreen;

/**
 * 宿主输入抢占参与者。
 *
 * <p>input 包只消费该抽象；具体的 screen / HUD 宿主负责在初始化阶段注册实现，
 * 避免输入基础设施反向依赖某个宿主子系统。</p>
 */
public interface UiHostInputCaptureParticipant {

    /**
     * 判断当前屏幕是否允许参与者抢占原生输入。
     *
     * @param currentScreen 当前宿主界面
     * @param screenClassName 当前宿主界面类名
     * @param mouseGrabbed 鼠标是否被游戏捕获
     * @return 是否允许抢占
     */
    boolean isHostInputCaptureEnabled(GuiScreen currentScreen, String screenClassName, boolean mouseGrabbed);

    /**
     * 在宿主原生键盘处理阶段即时处理当前输入帧。
     *
     * @param currentScreen 当前宿主界面
     * @param frame 即时键盘输入帧
     * @return 是否应阻断宿主继续处理该键盘事件
     */
    boolean handleImmediateKeyboardInput(GuiScreen currentScreen, UiInputFrame frame);

    /**
     * 在宿主原生鼠标处理阶段即时处理当前输入帧。
     *
     * @param currentScreen 当前宿主界面
     * @param frame 即时鼠标输入帧
     * @return 是否应阻断宿主继续处理该鼠标事件
     */
    boolean handleImmediateMouseInput(GuiScreen currentScreen, UiInputFrame frame);
}
