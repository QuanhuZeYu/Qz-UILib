package club.heiqi.uilib.ui.screen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/** 无需 Minecraft/GL 运行态的宿主源码契约测试。 */
public class McScreenBridgeTest {

    private static final Path BRIDGE_SOURCE = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/screen/McScreenBridge.java");

    private static final Path MODERN_CONFIG_SOURCE = Paths.get(
            "src/main/java/club/heiqi/uilib/config/modern/ModernConfigScreen.java");

    @Test
    public void modernConfigKeepsFullSurfaceAndOnlySuppressesWorldBackground() throws Exception {
        String bridge = source(BRIDGE_SOURCE);
        String modernConfig = source(MODERN_CONFIG_SOURCE);

        Assert.assertTrue("投影必须继续使用完整 framebuffer 高度",
                bridge.contains("GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D"));
        Assert.assertTrue("UiRenderContext 必须继续使用完整 framebuffer 高度",
                bridge.contains("new UiRenderContext(nativeWidth, nativeHeight, mouseX, mouseY"));
        Assert.assertTrue("通用 host 必须把完整 framebuffer 高度交给 surface",
                bridge.contains("surface.render(nativeWidth, nativeHeight, context, 0, 0)"));
        Assert.assertTrue("ModernConfigScreen 必须直接使用原 surface，不得裁掉底部世界",
                modernConfig.contains("super(parentScreen, surface)")
                        && !modernConfig.contains("ViewportSurface")
                        && !modernConfig.contains("resolveSurfaceHeight"));
        Assert.assertTrue("世界内背景抑制必须收窄在 ModernConfigScreen",
                modernConfig.contains("public void drawDefaultBackground()")
                        && modernConfig.contains("if (!hasWorldContext())")
                        && modernConfig.contains("super.drawDefaultBackground()"));
    }

    private static String source(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
