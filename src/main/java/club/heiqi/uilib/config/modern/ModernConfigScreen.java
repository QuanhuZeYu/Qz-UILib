package club.heiqi.uilib.config.modern;

import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.screen.McScreenBridge;

/**
 * 新架构配置页的 MC GuiScreen 宿主。
 *
 * <p>把反射拿到的 {@code ConfigScreen}（经 {@link ConfigUiBridge} 转成 {@link UiSurface}）
 * 包进 {@link McScreenBridge}，接入 MC GuiScreen 生命周期。</p>
 *
 * <h3>合规边界</h3>
 * <ul>
 *   <li>本类位于 {@code uilib.config.modern}（mod 配置接入包），非 {@code uilib.ui.*} 通用组件包。</li>
 *   <li>只 import uilib 自身的 {@link McScreenBridge} / {@link UiSurface} 与 MC {@link GuiScreen}，
 *       零 {@code config.ui.*} 依赖。</li>
 *   <li>{@link McScreenBridge} 构造器为 {@code protected}，子类跨包可访问（通过继承）。</li>
 * </ul>
 */
public class ModernConfigScreen extends McScreenBridge {

    /**
     * 创建新架构配置页宿主。
     *
     * @param parentScreen 关闭后返回的父界面，可为 null
     * @param surface      配置页渲染面（由 {@link ConfigUiBridge} 反射构建）
     */
    public ModernConfigScreen(GuiScreen parentScreen, UiSurface surface) {
        super(parentScreen, surface);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
