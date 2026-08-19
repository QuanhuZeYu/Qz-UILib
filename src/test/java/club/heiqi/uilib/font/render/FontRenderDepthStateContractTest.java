package club.heiqi.uilib.font.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/** 字体层与 UI host 的深度状态所有权源码契约测试。 */
public class FontRenderDepthStateContractTest {

    private static final Path FONT_STATE_SUPPORT = Paths.get(
            "src/main/java/club/heiqi/uilib/font/render/FontRenderStateSupport.java");
    private static final Path UI_HOST_SUPPORT = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/host/UiHostRenderSupport.java");
    private static final Path HUD_ENTRY = Paths.get(
            "src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java");
    private static final Path MC_SCREEN_ENTRY = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/screen/McScreenBridge.java");

    /** 字体共享准备只设置字体自有状态，不得接管调用阶段的任何深度状态。 */
    @Test
    public void sharedFontPreparationInheritsDepthState() throws IOException {
        String method = methodBody(source(FONT_STATE_SUPPORT),
                "public static void prepareTextRenderState()");

        assertFalse("字体共享准备不得启用或禁用 depth test", method.contains("GL_DEPTH_TEST"));
        assertFalse("字体共享准备不得改写 depth mask", method.contains("glDepthMask("));
        assertFalse("字体共享准备不得改写 depth func", method.contains("glDepthFunc("));
    }

    /** UI host 显式建立二维深度状态，且三个主 UI 入口均在回放前调用。 */
    @Test
    public void uiHostOwnsDepthDisableForEveryMainUiEntry() throws IOException {
        String hostMethod = methodBody(source(UI_HOST_SUPPORT),
                "public static void prepareMainUiRenderState()");
        assertTrue("UI host 主阶段必须显式关闭 depth test",
                hostMethod.contains("GL11.glDisable(GL11.GL_DEPTH_TEST);"));

        assertPreparesBeforeReplay(HUD_ENTRY, "private void renderHudFrame(", "host.render(context,");
        assertPreparesBeforeReplay(MC_SCREEN_ENTRY, "public void drawScreen(",
                "surface.render(nativeWidth, nativeHeight, context, 0, 0)");
    }

    /** 断言指定 UI 入口在主内容回放前建立 host 状态。 */
    private static void assertPreparesBeforeReplay(Path path, String methodMarker, String replayMarker)
            throws IOException {
        String method = methodBody(source(path), methodMarker);
        int prepare = method.indexOf("UiHostRenderSupport.prepareMainUiRenderState()");
        int replay = method.indexOf(replayMarker);

        assertTrue(path + " 缺少主 UI 状态准备", prepare >= 0);
        assertTrue(path + " 必须先准备主 UI 状态再回放内容", replay > prepare);
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 按方法标记提取配平花括号后的方法体。 */
    private static String methodBody(String source, String methodMarker) {
        int method = source.indexOf(methodMarker);
        Assert.assertTrue("缺少方法标记：" + methodMarker, method >= 0);
        int openingBrace = source.indexOf('{', method);
        Assert.assertTrue("方法缺少起始花括号：" + methodMarker, openingBrace >= 0);

        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, index);
                }
            }
        }
        Assert.fail("方法花括号未配平：" + methodMarker);
        return "";
    }
}
