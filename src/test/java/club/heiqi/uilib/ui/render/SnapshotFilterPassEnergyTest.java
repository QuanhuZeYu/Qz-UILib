package club.heiqi.uilib.ui.render;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/** 无 GL context 的源码契约：归一核只有在颜色调制、加法方程和完整清屏下才守恒。 */
public class SnapshotFilterPassEnergyTest {
    @Test
    public void shouldPreserveConstantInputWithTheActualKernel() throws Exception {
        String source = source("SnapshotFilterPassRenderer");
        String kernel = source.substring(source.indexOf("FILTER_BLUR_SAMPLES ="),
                source.indexOf("private SnapshotFilterPassRenderer"));
        Matcher matcher = Pattern.compile("\\{[-0-9.]+F,\\s*([0-9.]+)F\\}").matcher(kernel);
        double energy = 0.0;
        int taps = 0;
        while (matcher.find()) {
            energy += Double.parseDouble(matcher.group(1));
            taps++;
        }
        Assert.assertTrue(taps > 1);
        Assert.assertEquals(1.0, energy, 1.0e-6);
        Assert.assertEquals("two passes must preserve a constant background", 0.25,
                0.25 * energy * energy, 1.0e-6);
    }

    @Test
    public void shouldEstablishWeightedAdditiveStateBeforeDrawingOrClearing() throws Exception {
        String source = source("SnapshotFilterPassRenderer");
        String pass = source.substring(source.indexOf("static void renderFilterPass("),
                source.indexOf("static void ensureDownsampleTarget("));
        int firstDraw = pass.indexOf("drawFilterTextureQuad(");
        assertBefore(pass, "GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)", firstDraw);
        assertBefore(pass, "GL14.glBlendEquation(GL14.GL_FUNC_ADD)", firstDraw);
        assertBefore(pass, "GL11.glColorMask(true, true, true, true)", pass.indexOf("GL11.glClear("));
        assertBefore(pass, "GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE)", pass.indexOf("for (float[] sample"));
        Assert.assertTrue(pass.contains("GL11.glColor4f(weight, weight, weight, weight)"));
        // 生产调用位于 captureSnapshot 的 attrib 保护内，设置权重状态不能污染宿主。
        String capture = source("UiMainLayerSnapshotService");
        assertBefore(capture, "GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)",
                capture.indexOf("SnapshotFilterPassRenderer.downsampleSnapshot("));
        Assert.assertTrue(capture.contains("GL11.glPopAttrib()"));
    }

    private static void assertBefore(String source, String statement, int boundary) {
        int index = source.indexOf(statement);
        Assert.assertTrue(statement + " must precede its first consumer", index >= 0 && index < boundary);
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/club/heiqi/uilib/ui/render/" + name + ".java")),
                StandardCharsets.UTF_8);
    }
}
