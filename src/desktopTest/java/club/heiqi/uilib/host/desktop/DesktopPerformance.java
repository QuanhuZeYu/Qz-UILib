package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import club.heiqi.uilib.testing.gallery.PerformanceGallery;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/** 同一压测声明运行在完整正式Desktop帧上；自动输入借用已安装的GLFW回调。 */
public final class DesktopPerformance {
    public static void main(String[] args) throws Exception {
        boolean automatic = args.length > 0 && "--benchmark".equals(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : Paths.get("performance-report");
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 200;
        int warmup = args.length > 3 ? Integer.parseInt(args[3]) : 30;
        int frames = args.length > 4 ? Integer.parseInt(args[4]) : 120;
        Files.createDirectories(output);
        final int[] sequence = {0};
        PerformanceGallery gallery = new PerformanceGallery("desktop-glfw", "actions+window.frame; poll,clear,presentation,swap; vsync=0 for benchmark,1 for manual",
                System.getProperty("uilib.version", "unknown"), json -> {
                    try { Files.write(output.resolve(String.format("desktop-run-%03d.json", sequence[0]++)), json.getBytes(StandardCharsets.UTF_8)); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
        try (DesktopWindow window = DesktopWindow.openWithImages(output.resolve("performance.yaml"), images -> gallery)) {
            if (automatic) glfwSwapInterval(0);
            window.frame();
            Files.write(output.resolve("graphics.txt"), ("renderer=" + glGetString(GL_RENDERER) + "\nversion=" + glGetString(GL_VERSION) + "\n").getBytes(StandardCharsets.UTF_8));
            DesktopTestInput input = new DesktopTestInput(window);
            PerformanceGallery.Driver driver = new PerformanceGallery.Driver() {
                public void initialize(SceneNode target) {
                    club.heiqi.uilib.ui.scene.layout.LayoutBox box = DesktopTestInput.hit(DesktopTestInput.geometry(window, target));
                    double x = box.x + (target.padding() > 0 ? 2 : Math.max(1, box.width / 2));
                    double y = box.y + Math.max(1, Math.min(20, box.height / 2));
                    if (target.scrollable()) input.point(x, y); else input.enqueueAt(x, y);
                }
                public void scroll(int delta) { input.scroll.invoke(window.handle(), 0, delta); }
                public void edit(String text, SceneEditEvent.Command command) {
                    if (text != null) text.codePoints().forEach(c -> input.character.invoke(window.handle(), c));
                    else { input.key.invoke(window.handle(), GLFW_KEY_BACKSPACE, 0, GLFW_PRESS, 0); input.key.invoke(window.handle(), GLFW_KEY_BACKSPACE, 0, GLFW_RELEASE, 0); }
                }
            };
            if (!automatic) {
                while (!window.shouldClose()) gallery.frame(window::frame, null, window.width(), window.height());
                gallery.stop();
                return;
            }
            for (PerformanceGallery.Load load : PerformanceGallery.Load.values()) {
                gallery.start(load, scale, warmup, frames);
                while (gallery.running()) {
                    if (window.shouldClose()) { gallery.stop(); throw new IllegalStateException("压测窗口提前关闭"); }
                    gallery.frame(window::frame, driver, window.width(), window.height());
                }
                if (!gallery.lastReport().contains("\"pathObserved\":true")) throw new AssertionError("压测未命中负载：" + load);
                System.out.println("PERFORMANCE host=desktop load=" + load + " scale=" + scale + " frames=" + frames);
            }
            window.frame(); window.frame();
            // 采样已结束；沿既有广场截图方式结算文字资源，再读取实际GL_BACK。
            glClear(GL_COLOR_BUFFER_BIT);
            window.fonts.beginPresentation(1000000);
            try { window.replay(window.lastPlan); } finally { window.fonts.endPresentation(); }
            javax.imageio.ImageIO.write(DesktopTestReadback.readBack(window), "png", output.resolve("performance-desktop.png").toFile());
            // 同窗中断和重跑，不能把不完整采样记为完成。
            gallery.start(PerformanceGallery.Load.IDLE, 50, 1, 3);
            gallery.frame(window::frame, driver, window.width(), window.height()); gallery.stop();
            if (!gallery.lastReport().contains("\"complete\":false")) throw new AssertionError("停止未保留中断身份");
        }
    }
}
