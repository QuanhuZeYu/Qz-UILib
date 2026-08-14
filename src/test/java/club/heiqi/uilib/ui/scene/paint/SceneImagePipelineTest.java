package club.heiqi.uilib.ui.scene.paint;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.image.SceneImageRect;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** scene 平台图片 Display List 管线测试。 */
public class SceneImagePipelineTest {

    private final ScenePaintEngine engine = new ScenePaintEngine(new ZeroMeasurer());

    /** 命令固化 source 身份、矩形与 clip 内顺序，并可延迟回放一次。 */
    @Test
    public void imageCommand_isSelfContainedOrderedAndDeferred() {
        SceneImageSource source = new TestSource();
        SceneNode node = new SceneNode().setBackgroundColor(0xFF010203).setClipChildren(true)
                .setImageSource(source).setImageRect(new SceneImageRect(2, 3, 12, 13));
        node.setCachedLayout(new LayoutBox(10, 20, 30, 40));

        PaintPlan plan = engine.paint(node).getPlan();
        Assert.assertEquals(Arrays.asList(PaintCommandType.CLIP_PUSH, PaintCommandType.BACKGROUND,
                PaintCommandType.IMAGE, PaintCommandType.CLIP_POP), types(plan));
        PaintCommand image = plan.getCommands().get(2);
        Assert.assertSame(source, image.getImageSource());
        Assert.assertArrayEquals(new int[] {12, 23, 22, 33}, geometry(image));

        node.setImageSource(new TestSource());
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(plan, backend, 5, 7);
        Assert.assertEquals(1, count(backend, "drawImage"));
        RecordingRenderBackend.RenderCall call = find(backend, "drawImage");
        Assert.assertSame(source, call.args()[0]);
        Assert.assertArrayEquals(new int[] {17, 30, 27, 40},
                new int[] {call.getInt(1), call.getInt(2), call.getInt(3), call.getInt(4)});
    }

    /** 单张图片 backend 失败被隔离，后续旧绘制命令仍回放。 */
    @Test
    public void imageFailure_doesNotStopLegacyPaint() {
        PaintPlan plan = new PaintPlan().addCommand(PaintCommand.image(new TestSource(), 0, 0, 8, 8))
                .addCommand(PaintCommand.background(1, 2, 3, 4, 0xFFFFFFFF));
        FailingImageBackend backend = new FailingImageBackend();
        new ScenePaintReplayer().replay(plan, backend);
        Assert.assertEquals(1, backend.imageCalls);
        Assert.assertEquals(1, backend.fillCalls);
    }

    /** 普通绘制命令异常前已进入的四类作用域必须按真实 LIFO 全部关闭，异常继续传播。 */
    @Test
    public void commandFailureUnwindsAllOpenScopesInReverseOrder() {
        ScopeRecordingBackend backend = new ScopeRecordingBackend(false);
        try {
            new ScenePaintReplayer().replay(openFourScopesThenFailingBackground(), backend);
            Assert.fail("普通绘制命令失败应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("boom", expected.getMessage());
            Assert.assertEquals(java.util.Arrays.asList("popTransformLayer", "popTransform", "popOpacity", "popClip"),
                    backend.cleanupCalls);
        }
    }

    /** 任一作用域清理失败须继续清理其余作用域，并转换为普通异常。 */
    @Test
    public void cleanupFailureBecomesOrdinaryExceptionAndKeepsOriginalFailure() {
        ScopeRecordingBackend backend = new ScopeRecordingBackend(true);
        try {
            new ScenePaintReplayer().replay(openFourScopesThenFailingBackground(), backend);
            Assert.fail("清理失败应转换为普通异常");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getCause() instanceof IllegalStateException);
            Assert.assertEquals(1, expected.getSuppressed().length);
            Assert.assertEquals(java.util.Arrays.asList("popTransformLayer", "popTransform", "popOpacity", "popClip"),
                    backend.cleanupCalls);
        }
    }

    private static PaintPlan openFourScopesThenFailingBackground() {
        return new PaintPlan()
                .addCommand(PaintCommand.clipPush(0, 0, 10, 10, 0))
                .addCommand(PaintCommand.pushOpacity(0, 0, 10, 10, 0.5F))
                .addCommand(PaintCommand.pushTransform(0, 0, 10, 10,
                        0, 0, 0, 1, 1, 0.5F, 0.5F))
                .addCommand(PaintCommand.pushTransformLayer(0, 0, 10, 10,
                        0, 0, 0, 1, 1, 0.5F, 0.5F))
                .addCommand(PaintCommand.background(0, 0, 8, 8, 0xFFFFFFFF));
    }

    private static List<PaintCommandType> types(PaintPlan plan) {
        java.util.ArrayList<PaintCommandType> result = new java.util.ArrayList<PaintCommandType>();
        for (PaintCommand command : plan.getCommands()) result.add(command.getType());
        return result;
    }

    private static int[] geometry(PaintCommand command) {
        return new int[] {command.getLeft(), command.getTop(), command.getRight(), command.getBottom()};
    }

    private static int count(RecordingRenderBackend backend, String name) {
        int count = 0;
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) if (name.equals(call.methodName())) count++;
        return count;
    }

    private static RecordingRenderBackend.RenderCall find(RecordingRenderBackend backend, String name) {
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) if (name.equals(call.methodName())) return call;
        throw new AssertionError("missing call: " + name);
    }

    private static final class TestSource implements SceneImageSource { }

    private static final class ZeroMeasurer implements SceneTextMeasurer {
        @Override public int measureWidth(String text, int fontSizePx) { return 0; }
        @Override public int lineHeight(int fontSizePx) { return fontSizePx; }
        @Override public int epoch() { return 0; }
    }

    private static class FailingImageBackend implements UiRenderBackend {
        private int imageCalls;
        private int fillCalls;
        @Override public void drawImage(SceneImageSource source, int l, int t, int r, int b) {
            imageCalls++;
            throw new IllegalStateException("expected");
        }
        @Override public void fillRect(int l, int t, int r, int b, int c) { fillCalls++; }
        @Override public void drawSurface(int l, int t, int r, int b, int f, int c, int radius) { }
        @Override public void drawBorder(int l, int t, int r, int b, int c) { }
        @Override public void pushClip(int l, int t, int r, int b, int radius) { }
        @Override public void popClip() { }
        @Override public void drawText(String text, int x, int y, int c, boolean shadow) { }
        @Override public void drawText(String text, int x, int y, int c, boolean shadow, int size) { }
        @Override public void pushGroupOpacity(int l, int t, int r, int b, float opacity) { }
        @Override public void popGroupOpacity() { }
        @Override public void pushTransform(float tx, float ty, float d, float sx, float sy, float ox, float oy,
                int l, int t, int r, int b) { }
        @Override public void popTransform() { }
        @Override public void pushTransformLayer(float tx, float ty, float d, float sx, float sy, float ox, float oy,
                int l, int t, int r, int b) { }
        @Override public void popTransformLayer() { }
    }

    /** 记录异常回滚顺序的纯 JVM backend；BACKGROUND 绘制命令抛普通异常触发回滚。 */
    private static final class ScopeRecordingBackend extends FailingImageBackend {
        private final java.util.ArrayList<String> cleanupCalls = new java.util.ArrayList<String>();
        private final boolean failTransformCleanup;

        private ScopeRecordingBackend(boolean failTransformCleanup) {
            this.failTransformCleanup = failTransformCleanup;
        }

        @Override public void fillRect(int l, int t, int r, int b, int c) {
            throw new IllegalStateException("boom");
        }

        @Override public void popTransformLayer() { cleanupCalls.add("popTransformLayer"); }
        @Override public void popTransform() {
            cleanupCalls.add("popTransform");
            if (failTransformCleanup) throw new IllegalArgumentException("cleanup failed");
        }
        @Override public void popGroupOpacity() { cleanupCalls.add("popOpacity"); }
        @Override public void popClip() { cleanupCalls.add("popClip"); }
    }
}
