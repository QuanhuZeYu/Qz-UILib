package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.testing.gallery.PerformanceGallery;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** MC只观测页面绘制回调；滚动和编辑由操作者走正式原生输入。 */
public final class PerformanceScreen extends MinecraftSceneScreen {
    private final PerformanceGallery gallery;
    public PerformanceScreen() {
        this(new PerformanceGallery("minecraft-manual", "actions+drawScreen; excludes world tick and swap; scroll/edit=manual",
                club.heiqi.uilib.Tags.VERSION, json -> {
                    Path output = Minecraft.getMinecraft().mcDataDir.toPath().resolve("qz-uilib-performance");
                    try {
                        Files.createDirectories(output);
                        Path report = Files.createTempFile(output, "run-", ".json");
                        Files.write(report, json.getBytes(StandardCharsets.UTF_8));
                        System.out.println("UILIB_PERFORMANCE_REPORT " + report);
                    } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                }));
    }
    private PerformanceScreen(PerformanceGallery gallery) { super(gallery); this.gallery = gallery; }
    @Override public void drawScreen(int x, int y, float partialTicks) {
        gallery.frame(() -> super.drawScreen(x, y, partialTicks), null, Display.getWidth(), Display.getHeight());
    }
    @Override public void onGuiClosed() { try { gallery.stop(); } finally { super.onGuiClosed(); } }
}
