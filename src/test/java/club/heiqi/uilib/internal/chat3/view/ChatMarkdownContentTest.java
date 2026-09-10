package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.render.software.MarkdownCountingMetrics;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

public class ChatMarkdownContentTest {
    private int budget;
    private static final String SOURCE = "Before\n\n| Name | Detail |\n| --- | --- |\n"
            + "| first | a long sequence of words to wrap over several lines |\n"
            + "| link | https://example.com/a/long/path and $x$ |\n\nAfter";

    @Before public void before() {
        budget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
        ReactiveScheduler.get().reset();
    }
    @After public void after() {
        FontConfig.widthCacheMissBudgetPerWindow = budget;
        ReactiveScheduler.get().reset();
    }

    @Test public void cacheSkipsParsingMappingMetricsAndCommandsAndInvalidatesInputs() {
        AtomicInteger parses = new AtomicInteger();
        AtomicInteger maps = new AtomicInteger();
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline(source -> {
            parses.incrementAndGet();
            return MarkdownDocument.parse(source);
        });
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        ChatMessageList.SegmentPostProcessor processor = (segments, font) -> {
            maps.incrementAndGet();
            return segments;
        };
        assertTrue(pipeline.hasTables(SOURCE));
        ChatMarkdownPipeline.RenderedContent first = pipeline.layoutContent(SOURCE, -1, 220, 14, processor, metrics, 1);
        int measured = metrics.calls;
        int mapped = maps.get();
        assertTrue(measured > 0);
        assertTrue(mapped > 0);
        assertSame(first, pipeline.layoutContent(SOURCE, -1, 220, 14, processor, metrics, 1));
        assertTrue(pipeline.hasTables(SOURCE));
        assertEquals(1, parses.get());
        assertEquals(mapped, maps.get());
        assertEquals(measured, metrics.calls);
        ChatMarkdownPipeline.RenderedContent narrow = pipeline.layoutContent(SOURCE, -1, 100, 14, processor, metrics, 1);
        assertNotSame(first, narrow);
        assertTrue(narrow.height > first.height);
        assertEquals(mapped, maps.get());
        measured = metrics.calls;
        assertSame(first, pipeline.layoutContent(SOURCE, -1, 220, 14, processor, metrics, 1));
        assertEquals("两个 occurrence 交替读宽不重度量", measured, metrics.calls);
        ChatMarkdownPipeline.RenderedContent font = pipeline.layoutContent(SOURCE, -1, 100, 18, processor, metrics, 1);
        assertNotSame(narrow, font);
        assertTrue(maps.get() > mapped);
        ChatMarkdownPipeline.RenderedContent color = pipeline.layoutContent(SOURCE, 0xFF123456, 100, 18, processor, metrics, 1);
        assertNotSame(font, color);
        ChatMarkdownPipeline.RenderedContent epoch = pipeline.layoutContent(SOURCE, 0xFF123456, 100, 18, processor, metrics, 2);
        assertNotSame(color, epoch);
        assertNotSame(epoch, pipeline.layoutContent(SOURCE, 0xFF123456, 100, 18, (s, f) -> s, metrics, 2));
        assertNotSame(epoch, pipeline.layoutContent(SOURCE, 0xFF123456, 100, 18, processor, new MarkdownCountingMetrics(), 2));
        assertEquals("布局失效不重复语法解析", 1, parses.get());
    }

    @Test public void bareUrlIsLinkifiedBeforeCellWrapAndTailContentIsRetained() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        ChatMarkdownPipeline.RenderedContent plan = pipeline.layoutContent(SOURCE, -1, 95, 14, null,
                new MarkdownCountingMetrics(), 1);
        int linked = 0;
        StringBuilder text = new StringBuilder();
        for (ChatMarkdownPipeline.PaintLeaf leaf : plan.leaves) {
            if (leaf.command.getType() != PaintCommandType.SEGMENTS) continue;
            for (TextSegment segment : leaf.command.getSegments()) {
                text.append(segment.getText());
                if ("https://example.com/a/long/path".equals(segment.getStyle().getLink())) linked++;
            }
        }
        assertTrue("窄 cell 换行片段均持完整 URL", linked > 1);
        assertTrue(text.toString().contains("After"));
        assertFalse(text.toString().contains("---"));
    }

    @Test public void occurrenceRetainsWholePlanClipsAndSkipsSteadySceneRebuild() {
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer());
        SceneNode root = SceneNode.column();
        ChatMarkdownContent.Result[] content = {null};
        int[] epoch = {1};
        rt.mount(root, () -> {
            content[0] = ChatMarkdownContent.create(rt, () -> 40, true, 14,
                    width -> pipeline.layoutContent(SOURCE, -1, width, 14, null, metrics, epoch[0]), (node, command) -> {});
            return content[0].root;
        });
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
        try {
            settle(rt, engine, root, 150);
            ChatMarkdownContent.Result view = content[0];
            assertEquals(40, ((LayoutBox) view.viewport.getCachedLayout()).getHeight());
            assertTrue(SceneGeometry.maxScrollY(view.viewport) > 0);
            List<SceneNode> first = new ArrayList<>(view.body.__getChildren());
            int calls = metrics.calls;
            rt.__tickFrame(1L);
            settle(rt, engine, root, 150);
            assertEquals(calls, metrics.calls);
            assertEquals(first, view.body.__getChildren());
            view.scrollY.set(SceneGeometry.maxScrollY(view.viewport));
            rt.flush();
            assertTrue(view.viewport.getScrollOffsetY() > 0);
            assertEquals("滚动只改几何，不重建内容", first, view.body.__getChildren());
            List<PaintCommand> painted = new ScenePaintEngine(new FixedTextMeasurer()).paint(root).getPlan().getCommands();
            assertTrue(painted.stream().anyMatch(command -> command.getType() == PaintCommandType.CLIP_PUSH));
            assertTrue(painted.stream().filter(command -> command.getType() == PaintCommandType.SEGMENTS)
                    .flatMap(command -> command.getSegments().stream()).anyMatch(segment -> segment.getText().contains("After")));
            epoch[0]++;
            rt.__tickFrame(2L);
            settle(rt, engine, root, 150);
            assertTrue(metrics.calls > calls);
            assertNotSame(first.get(0), view.body.__getChildren().get(0));
        } finally {
            rt.dispose();
        }
    }

    @Test public void hudOccurrenceKeepsOverflowWithoutInstallingInteractiveControls() {
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer());
        SceneNode root = SceneNode.column();
        ChatMarkdownContent.Result[] content = {null};
        rt.mount(root, () -> {
            content[0] = ChatMarkdownContent.create(rt, () -> 30, false, 14,
                    width -> pipeline.layoutContent(SOURCE, -1, width, 14, null, metrics, 1),
                    (node, command) -> fail("HUD 不装链接输入"));
            return content[0].root;
        });
        try {
            settle(rt, new SceneLayoutEngine(new FixedTextMeasurer()), root, 150);
            assertFalse(content[0].viewport.isHitTestable());
            assertTrue(SceneGeometry.maxScrollY(content[0].viewport) > 0);
            assertEquals(0, content[0].viewport.getScrollOffsetY());
            assertEquals(1, content[0].root.__getChildren().size());
        } finally { rt.dispose(); }
    }

    @Test public void resizeClampsBothAxesAndRemovesHorizontalControl() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer());
        SceneNode root = SceneNode.column();
        ChatMarkdownPipeline.RenderedContent wide = new ChatMarkdownPipeline.RenderedContent(
                java.util.Collections.emptyList(), 500, 400);
        ChatMarkdownPipeline.RenderedContent small = new ChatMarkdownPipeline.RenderedContent(
                java.util.Collections.emptyList(), 100, 20);
        ChatMarkdownContent.Result[] content = {null};
        rt.mount(root, () -> {
            content[0] = ChatMarkdownContent.create(rt, () -> 40, true, 14,
                    width -> width < 300 ? wide : small, (node, command) -> {});
            return content[0].root;
        });
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
        try {
            settle(rt, engine, root, 150);
            ChatMarkdownContent.Result view = content[0];
            assertTrue(SceneGeometry.maxScrollX(view.viewport) > 0);
            assertTrue(SceneGeometry.maxScrollY(view.viewport) > 0);
            view.scrollX.set(SceneGeometry.maxScrollX(view.viewport));
            view.scrollY.set(SceneGeometry.maxScrollY(view.viewport));
            rt.flush();
            assertTrue(view.viewport.getScrollOffsetX() > 0);
            assertTrue(view.viewport.getScrollOffsetY() > 0);
            settle(rt, engine, root, 600);
            assertEquals(0, SceneGeometry.maxScrollX(view.viewport));
            assertEquals(0, SceneGeometry.maxScrollY(view.viewport));
            assertEquals(Integer.valueOf(0), view.scrollX.get());
            assertEquals(Integer.valueOf(0), view.scrollY.get());
            assertEquals(0, view.viewport.getScrollOffsetX());
            assertEquals(0, view.viewport.getScrollOffsetY());
            assertNull("无横向溢出时控件应离树，不能留下透明命中带", horizontalTrack(view));
        } finally { rt.dispose(); }
    }

    @Test public void unmountAndDisposeStopCapturedDragAndFrameSubscriptions() {
        for (boolean disposeRuntime : new boolean[] {false, true}) {
            club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness input =
                    club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness.create();
            SceneRuntime rt = input.getRuntime();
            SceneNode root = SceneNode.column();
            ChatMarkdownPipeline.RenderedContent plan = new ChatMarkdownPipeline.RenderedContent(
                    java.util.Collections.emptyList(), 500, 400);
            AtomicInteger layouts = new AtomicInteger();
            ChatMarkdownContent.Result[] content = {null};
            club.heiqi.uilib.ui.scene.runtime.MountHandle mounted = rt.mount(root, () -> {
                content[0] = ChatMarkdownContent.create(rt, () -> 40, true, 14, width -> {
                    layouts.incrementAndGet();
                    return plan;
                }, (node, command) -> {});
                return content[0].root;
            });
            try {
                input.mountRoot(root, 150, 200);
                settle(rt, new SceneLayoutEngine(new FixedTextMeasurer()), root, 150);
                ChatMarkdownContent.Result view = content[0];
                SceneNode track = horizontalTrack(view);
                assertNotNull(track);
                club.heiqi.uilib.ui.scene.layout.AnchorRect box = SceneGeometry.absoluteBox(track, 0, 0);
                input.press(track);
                input.moveAt(box.getX() + box.getWidth(), box.getY() + box.getHeight() / 2);
                assertTrue("必须先证明真实拖动正在写scroll signal", view.scrollX.get() > 0);
                if (disposeRuntime) rt.dispose(); else mounted.dispose();
                int oldX = view.scrollX.get();
                int oldY = view.viewport.getScrollOffsetY();
                int oldLayouts = layouts.get();
                input.moveAt(box.getX(), box.getY());
                input.releaseAt(box.getX(), box.getY());
                assertEquals("卸载后捕获尾事件不能再写旧状态", Integer.valueOf(oldX), view.scrollX.get());
                view.scrollY.set(100);
                rt.__tickFrame(99L);
                rt.flush();
                assertEquals("滚动绑定已退订", oldY, view.viewport.getScrollOffsetY());
                assertEquals("帧刷新已退订", oldLayouts, layouts.get());
                assertNull(view.root.__getParent());
            } finally { input.dispose(); }
        }
    }

    @Test public void resizeDuringCaptureKeepsPrimitiveUntilReleaseThenUnmounts() throws Exception {
        club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness input =
                club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness.create();
        SceneRuntime rt = input.getRuntime();
        SceneNode root = SceneNode.column();
        ChatMarkdownPipeline.RenderedContent wide = new ChatMarkdownPipeline.RenderedContent(
                java.util.Collections.emptyList(), 500, 400);
        ChatMarkdownPipeline.RenderedContent small = new ChatMarkdownPipeline.RenderedContent(
                java.util.Collections.emptyList(), 100, 20);
        ChatMarkdownContent.Result[] content = {null};
        rt.mount(root, () -> {
            content[0] = ChatMarkdownContent.create(rt, () -> 40, true, 14,
                    width -> width < 300 ? wide : small, (node, command) -> {});
            return content[0].root;
        });
        try {
            input.mountRoot(root, 150, 200);
            SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
            settle(rt, engine, root, 150);
            ChatMarkdownContent.Result view = content[0];
            SceneNode track = horizontalTrack(view);
            input.press(track);
            assertTrue(rt.interactionState(track).pressed().get());
            // 白盒只读路由权威 capture；实际建立/结束均经 harness 的 DOWN/UP。
            java.lang.reflect.Method capture = rt.getInputRouter().getClass().getDeclaredMethod("__getCapturedNode");
            capture.setAccessible(true);
            assertSame(track, capture.invoke(rt.getInputRouter()));
            club.heiqi.uilib.ui.scene.layout.AnchorRect before = SceneGeometry.absoluteBox(track, 0, 0);
            input.moveAt(before.getX() + before.getWidth(), before.getY());
            assertTrue(view.scrollX.get() > 0);
            settle(rt, engine, root, 600);
            assertSame("resize不能卸载还在处理手势的primitive", track, horizontalTrack(view));
            assertFalse("溢出消失后不接收新的DOWN", track.isHitTestable());
            assertEquals(0, view.viewport.getScrollOffsetX());
            input.releaseAt(0, 0);
            rt.flush();
            assertNull("原生UP结束capture", capture.invoke(rt.getInputRouter()));
            assertFalse(rt.interactionState(track).pressed().get());
            assertNull("手势结束后才条件卸载", horizontalTrack(view));
            settle(rt, engine, root, 150);
            SceneNode restored = horizontalTrack(view);
            assertNotNull(restored);
            assertNotSame("重新溢出获得干净primitive", track, restored);
            assertEquals(Integer.valueOf(0), view.scrollX.get());
            SceneNode thumb = restored.__getChildren().get(restored.__getChildren().size() - 1);
            club.heiqi.uilib.ui.scene.layout.AnchorRect thumbBox = SceneGeometry.absoluteBox(thumb, 0, 0);
            club.heiqi.uilib.ui.scene.layout.AnchorRect trackBox = SceneGeometry.absoluteBox(restored, 0, 0);
            assertTrue("恢复后的thumb跟随归零x，不沿用旧draggingValue",
                    thumbBox.getX() - trackBox.getX() <= thumbBox.getWidth());
        } finally { input.dispose(); }
    }

    private static SceneNode horizontalTrack(ChatMarkdownContent.Result view) {
        for (SceneNode child : view.root.__getChildren()) {
            if (child.__getChildren().size() == 1) return child.__getChildren().get(0);
        }
        return null;
    }

    private static void settle(SceneRuntime rt, SceneLayoutEngine engine, SceneNode root, int width) {
        for (int i = 0; i < 6; i++) {
            rt.flush();
            engine.layout(root, new Constraints(width));
            rt.__setLayoutDoneEpoch(engine.layoutEpoch());
        }
        rt.flush();
    }
}
