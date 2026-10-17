package club.heiqi.uilib.host.neoforge263;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;

/** 消费宿主已发布的窗口事实；OS 的下一次尺寸不属于当前输入批次或正在回放的帧。 */
record NeoForgeSceneCoordinates(int width, int height, int windowWidth, int windowHeight, int guiWidth, int guiHeight) {
    static NeoForgeSceneCoordinates input(Window window) {
        return new NeoForgeSceneCoordinates(window.getWidth(), window.getHeight(), window.getScreenWidth(),
                window.getScreenHeight(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }
    static NeoForgeSceneCoordinates frame(RenderTarget target, Window window) {
        return new NeoForgeSceneCoordinates(target.width, target.height, window.getScreenWidth(),
                window.getScreenHeight(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }
    int nativeX(double x) { return (int) Math.floor(x * width / windowWidth); }
    int nativeY(double y) { return (int) Math.floor(y * height / windowHeight); }
    int guiX(double x) { return (int) Math.floor(x * width / guiWidth); }
    int guiY(double y) { return (int) Math.floor(y * height / guiHeight); }
    double windowX(double x) { return x * windowWidth / width; }
    double windowY(double y) { return y * windowHeight / height; }
}
