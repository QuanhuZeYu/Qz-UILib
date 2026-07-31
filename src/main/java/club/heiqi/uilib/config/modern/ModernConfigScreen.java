package club.heiqi.uilib.config.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
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
 *   <li>只依赖 uilib 自身的宿主、surface、render/input 契约与 MC 客户端类型，
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
        super(parentScreen, new ViewportSurface(surface));
    }

    @Override
    public void drawDefaultBackground() {
        if (ModernConfigViewportPolicy.shouldRenderDefaultBackground(hasWorldContext())) {
            super.drawDefaultBackground();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean hasWorldContext() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.theWorld != null;
    }

    /** 只缩短配置页布局高度；宿主投影与 render context 始终保持完整 framebuffer。 */
    private static final class ViewportSurface implements UiSurface {

        private final UiSurface delegate;

        private ViewportSurface(UiSurface delegate) {
            this.delegate = delegate;
        }

        @Override
        public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
            int surfaceHeight = ModernConfigViewportPolicy.resolveSurfaceHeight(h, hasWorldContext());
            delegate.render(w, surfaceHeight, ctx, absX, absY);
        }

        @Override
        public void onKeyTyped(char typedChar, int keyCode) {
            delegate.onKeyTyped(typedChar, keyCode);
        }

        @Override
        public void pushText(String text) {
            delegate.pushText(text);
        }

        @Override
        public void setExternalTextMode(boolean external) {
            delegate.setExternalTextMode(external);
        }

        @Override
        public void onPointerButton(ScenePointerAction action, int callbackX, int callbackY,
                SceneMouseButton button, long timeNanos) {
            delegate.onPointerButton(action, callbackX, callbackY, button, timeNanos);
        }

        @Override
        public void setExternalPointerMode(boolean external) {
            delegate.setExternalPointerMode(external);
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }
    }
}
