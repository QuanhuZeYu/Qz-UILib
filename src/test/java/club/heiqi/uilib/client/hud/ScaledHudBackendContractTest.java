package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** 独立 HUD scale 在 backend 边界只执行一次的完整出口契约测试。 */
public class ScaledHudBackendContractTest {
    private static final float[] SCALES = {1F, 1.25F, 1.5F, 1.75F, 2F};

    /** 验证全部后端出口的调用顺序、参数缩放、舍入和非几何参数透传。 */
    @Test
    public void everyBackendExitScalesGeometryExactlyOnceAndPreservesOtherParameters() {
        ImageFixture image = new ImageFixture("test:textures/hud.png", 0.125F, 0.25F, 0.75F, 0.875F);
        for (float scale : SCALES) {
            RecordingRenderBackend recording = new RecordingRenderBackend();
            invokeEveryExit(new ScaledHudBackend(recording, scale), image);
            assertCompleteCalls(recording.getCalls(), expectedCalls(scale, image), scale);
        }
    }

    private static void invokeEveryExit(UiRenderBackend backend, SceneImageSource image) {
        backend.pushClip(1, 2, 257, 129, 3);
        backend.fillRect(2, 3, 251, 127, 0xFF010203);
        backend.drawBorder(3, 4, 250, 126, 0xFF040506);
        backend.drawText("scaled text", 5, 7, 0xFF070809, true);
        backend.drawText("sized text", 6, 8, 0xFF0A0B0C, false, 11);
        backend.drawImage(image, 7, 9, 249, 125);
        backend.drawSurface(8, 10, 248, 124, 0xFF0D0E0F, 0xFF101112, 5);
        backend.pushGroupOpacity(9, 11, 247, 123, 0.625F);
        backend.popGroupOpacity();
        backend.pushTransform(1.5F, 2.5F, 17F, 1.1F, 0.9F, 0.25F, 0.75F,
                10, 12, 246, 122);
        backend.popTransform();
        backend.pushTransformLayer(3.5F, 4.5F, 23F, 1.2F, 0.8F, 0.375F, 0.625F,
                11, 13, 245, 121);
        backend.popTransformLayer();
        backend.popClip();
    }

    private static List<ExpectedCall> expectedCalls(float scale, ImageFixture image) {
        return Arrays.asList(
                call("pushClip", p(1, scale), p(2, scale), p(257, scale), p(129, scale), p(3, scale)),
                call("fillRect", p(2, scale), p(3, scale), p(251, scale), p(127, scale), 0xFF010203),
                call("drawBorder", p(3, scale), p(4, scale), p(250, scale), p(126, scale), 0xFF040506),
                call("drawText", "scaled text", p(5, scale), p(7, scale), 0xFF070809, true),
                call("drawText", "sized text", p(6, scale), p(8, scale), 0xFF0A0B0C, false, p(11, scale)),
                call("drawImage", image, p(7, scale), p(9, scale), p(249, scale), p(125, scale)),
                call("drawSurface", p(8, scale), p(10, scale), p(248, scale), p(124, scale),
                        0xFF0D0E0F, 0xFF101112, p(5, scale)),
                call("pushGroupOpacity", p(9, scale), p(11, scale), p(247, scale), p(123, scale), 0.625F),
                call("popGroupOpacity"),
                call("pushTransform", 1.5F * scale, 2.5F * scale, 17F, 1.1F, 0.9F, 0.25F, 0.75F,
                        p(10, scale), p(12, scale), p(246, scale), p(122, scale)),
                call("popTransform"),
                call("pushTransformLayer", 3.5F * scale, 4.5F * scale, 23F, 1.2F, 0.8F, 0.375F, 0.625F,
                        p(11, scale), p(13, scale), p(245, scale), p(121, scale)),
                call("popTransformLayer"),
                call("popClip"));
    }

    private static void assertCompleteCalls(List<RecordingRenderBackend.RenderCall> actual,
                                            List<ExpectedCall> expected, float scale) {
        assertEquals("scale=" + scale + " 调用总数", expected.size(), actual.size());
        List<String> actualNames = new ArrayList<String>(actual.size());
        List<String> expectedNames = new ArrayList<String>(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            RecordingRenderBackend.RenderCall actualCall = actual.get(i);
            ExpectedCall expectedCall = expected.get(i);
            actualNames.add(actualCall.methodName());
            expectedNames.add(expectedCall.methodName);
            assertArguments(expectedCall, actualCall, scale, i);
        }
        assertEquals("scale=" + scale + " 严格调用顺序", expectedNames, actualNames);
    }

    private static void assertArguments(ExpectedCall expected, RecordingRenderBackend.RenderCall actual,
                                        float scale, int callIndex) {
        Object[] actualArgs = actual.args();
        assertEquals(message(scale, callIndex, "参数总数"), expected.args.length, actualArgs.length);
        for (int argumentIndex = 0; argumentIndex < expected.args.length; argumentIndex++) {
            Object expectedArg = expected.args[argumentIndex];
            Object actualArg = actualArgs[argumentIndex];
            String message = message(scale, callIndex, "参数 " + argumentIndex);
            if (expectedArg instanceof ImageFixture) {
                assertSame(message + " 资源及 UV 元数据对象", expectedArg, actualArg);
            } else {
                assertEquals(message, expectedArg, actualArg);
            }
        }
    }

    private static String message(float scale, int callIndex, String detail) {
        return "scale=" + scale + " call=" + callIndex + " " + detail;
    }

    private static int p(int value, float scale) {
        return Math.round(value * scale);
    }

    private static ExpectedCall call(String methodName, Object... args) {
        return new ExpectedCall(methodName, args);
    }

    /** 单条后端调用的完整结构化期望。 */
    private static final class ExpectedCall {
        private final String methodName;
        private final Object[] args;

        private ExpectedCall(String methodName, Object[] args) {
            this.methodName = methodName;
            this.args = args;
        }
    }

    /** 携带资源标识和 UV 的图片夹具，用对象身份守卫全部非几何图片参数。 */
    private static final class ImageFixture implements SceneImageSource {
        private final String resource;
        private final float u0;
        private final float v0;
        private final float u1;
        private final float v1;

        private ImageFixture(String resource, float u0, float v0, float u1, float v1) {
            this.resource = resource;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
}
