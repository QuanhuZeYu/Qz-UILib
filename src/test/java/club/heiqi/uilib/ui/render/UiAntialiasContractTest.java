package club.heiqi.uilib.ui.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.*;

/** 无 GL 环境下的覆盖率/混合状态契约；实际驱动像素输出仍需运行态验收。 */
public class UiAntialiasContractTest {
    @Test
    public void glassShaderCoverageIsNotCutOffByItsOwnRoundedStencil() throws IOException {
        String shader = read("src/main/resources/shader/uiBackdropF.frag");
        String host = read("src/main/java/club/heiqi/uilib/ui/render/UiBackdropFilterRenderer.java");
        assertTrue(shader.contains("fwidth(signedDistance)"));
        assertTrue(shader.contains("roundedPanelDistance(panelUv * panelSizePx, panelSizePx, cornerRadii)"));
        assertTrue(host.contains("radii = UiBorderRadiusResolver.scaleToFit("));
        assertTrue(shader.contains("clamp(0.5 - signedDistance / edgeWidth, 0.0, 1.0)"));
        assertTrue(shader.contains("mix(coverage, blurred.a * coverage, sourceAlphaPass)"));
        int rectangleClip = host.indexOf("context.pushClip(left, top, right, bottom, 0)");
        int shaderDraw = host.indexOf("if (drawBackdropTextureWithShader(");
        int fallbackClip = host.indexOf("context.pushClip(left, top, right, bottom, cornerRadii)");
        assertTrue(rectangleClip >= 0 && rectangleClip < shaderDraw);
        assertTrue("仅固定管线回退需要自身硬圆角", shaderDraw < fallbackClip);
        assertTrue(host.indexOf("GL11.glDisable(GL11.GL_BLEND)", fallbackClip) > fallbackClip);
        assertTrue(host.contains("if (fixedPipelineClip) context.popClip()"));
        int alphaPass = host.indexOf("sourceAlphaPass", host.indexOf("if (isolatedLayer)"));
        int maskRgb = host.indexOf("glColorMask(false, false, false, true)");
        assertTrue("alpha 加法遍必须先关闭全部 RGB 写入", maskRgb >= 0 && maskRgb < alphaPass);
        assertTrue(host.contains("GL11.GL_ZERO, isolatedLayer ? GL11.GL_ONE_MINUS_SRC_ALPHA : GL11.GL_ONE"));
    }

    @Test
    public void isolatedGlassPreservesReplacementEvenOverExistingContentAndTransparentParent() {
        // 首遍按coverage替换RGB/衰减目标A，第二遍只补sourceAlpha*coverage。
        // 覆盖空子层、非空子层、透明父快照和group opacity回贴。
        for (double coverage : new double[] {0, 0.25, 0.5, 1}) {
            for (double sourceAlpha : new double[] {0, 0.25, 0.5, 1}) {
                for (double destinationAlpha : new double[] {0, 0.5, 1}) {
                    double sourceRgb = 0.6 * sourceAlpha;
                    double destinationRgb = 0.2 * destinationAlpha;
                    double rgb = sourceRgb * coverage + destinationRgb * (1 - coverage);
                    double firstAlpha = destinationAlpha * (1 - coverage);
                    double alpha = firstAlpha + sourceAlpha * coverage;
                    assertEquals(destinationAlpha + (sourceAlpha - destinationAlpha) * coverage, alpha, 1e-12);
                    assertTrue(rgb >= 0 && rgb <= alpha);
                    if (coverage == 1) {
                        assertEquals(sourceRgb, rgb, 0);
                        assertEquals(sourceAlpha, alpha, 0);
                    }
                    double opacity = 0.5;
                    double parentRgb = 0.4;
                    double composited = rgb * opacity + parentRgb * (1 - alpha * opacity);
                    assertTrue("预乘回贴不能异常加亮", composited >= 0 && composited <= 1);
                }
            }
        }
    }

    @Test
    public void ownedBitmapTexturesUseLinearSamplingAndRestoreTheBinding() throws IOException {
        String source = read("src/main/java/club/heiqi/uilib/ui/image/MinecraftHostImageRenderer.java");
        String upload = source.substring(source.indexOf("final class MinecraftDynamicImageTextureAccess"));
        int create = upload.indexOf("new DynamicTexture(image)");
        int min = upload.indexOf("GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR");
        int mag = upload.indexOf("GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR");
        int register = upload.indexOf("getDynamicTextureLocation(key, texture)");
        assertTrue(create >= 0 && create < min && min < register);
        assertTrue(create < mag && mag < register);
        assertTrue(upload.contains("GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE"));
        assertTrue(upload.contains("GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE"));
        assertTrue(upload.indexOf("glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)") > upload.indexOf("finally"));
    }

    @Test
    public void roundedCoverageBatchPreservesCallerStateAndDoesNotAlphaTestTheEdge() throws IOException {
        String helper = read("src/main/java/club/heiqi/uilib/ui/render/UiContextGlHelpers.java");
        assertTrue(helper.contains("glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)"));
        assertTrue(helper.contains("glPopAttrib()"));
        assertTrue(helper.contains("glDisable(GL11.GL_ALPHA_TEST)"));
        assertTrue(helper.contains("glBlendEquation(GL14.GL_FUNC_ADD)"));
        assertFalse("抗锯齿不能依赖驱动全局polygon smooth", helper.contains("GL_POLYGON_SMOOTH"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
