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

    private static final Path HOST_RENDER_SUPPORT = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/host/UiHostRenderSupport.java");

    @Test
    public void modernConfigKeepsFullSurfaceAndOnlySuppressesWorldBackground() throws Exception {
        String bridge = source(BRIDGE_SOURCE);
        String modernConfig = source(MODERN_CONFIG_SOURCE);

        // 投影与 viewport 已上提到共享帧入口（MC 宿主与 headless 宿主共用同一帧前置语义），
        // 故断言改钉「桥把完整 native 盒交给该入口」，并由入口源码自身钉住 ortho 用的是完整高度。
        Assert.assertTrue("桥必须经共享帧入口把完整 native 盒交给投影与 viewport",
                bridge.contains("UiHostRenderSupport.beginMainUiFrame(nativeWidth, nativeHeight)"));
        Assert.assertTrue("共享帧入口必须继续使用完整 framebuffer 高度做正交投影",
                source(HOST_RENDER_SUPPORT).contains("GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D"));
        Assert.assertTrue("渲染上下文工厂必须继续收到完整 framebuffer 高度与指针坐标",
                bridge.contains("UiHostRenderSupport.createRenderContext(nativeWidth, nativeHeight,")
                        && bridge.contains("pointerX, pointerY, partialTicks"));
        // P5 §1.1.1：宿主边界把 nativeBox + GUI Scale 合成为 logicalBox，渲染面按<B>逻辑盒</B>驱动。
        // 断言不降级：policy A（默认）下逻辑盒恒等于原生盒，等价于旧的「完整 framebuffer 高度」；
        // 同时必须存在唯一的合成点调用（GUI Scale 只允许在这里被接触）。
        Assert.assertTrue("宿主边界必须经唯一合成点把 nativeBox + GUI Scale 折成逻辑盒",
                bridge.contains("HostViewportScale.compose(nativeWidth, nativeHeight, scaleFactor)"));
        Assert.assertTrue("通用 host 必须把完整逻辑盒交给 surface",
                bridge.contains("surface.render(logicalWidth, logicalHeight, context, 0, 0)"));
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
