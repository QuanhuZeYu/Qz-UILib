package club.heiqi.uilib.ui.scene.paint;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderFrameAbortException;
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

    /** 状态不可恢复信号必须中止当前帧，不能被图片隔离分支吞掉。 */
    @Test(expected = UiRenderFrameAbortException.class)
    public void unrecoveredImageFailureAbortsFrame() {
        PaintPlan plan = new PaintPlan().addCommand(PaintCommand.image(new TestSource(), 0, 0, 8, 8));
        new ScenePaintReplayer().replay(plan, new FailingImageBackend() {
            @Override public void drawImage(SceneImageSource source, int l, int t, int r, int b) {
                throw new UiRenderFrameAbortException("unrecovered");
            }
        });
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
}
