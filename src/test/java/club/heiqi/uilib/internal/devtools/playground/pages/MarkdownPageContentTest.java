package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import club.heiqi.uilib.font.config.FontConfig;

import club.heiqi.uilib.font.render.software.MarkdownCountingMetrics;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

public class MarkdownPageContentTest {
    private int savedWidthMissBudget;
    @Before public void stableMetrics() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void restoreMetrics() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }
    private static final String SOURCE = "Before\n\n| Name | Detail | Count |\n| :--- | :---: | ---: |\n"
            + "| **Bold** | a long sequence of words to wrap over several lines | 128 |\n"
            + "| [Guide](https://example.com) | `code` and ~~old~~ | 2 |\n"
            + "| Formula | $\\frac{1}{\\frac{2}{3}}$ tail [next](https://example.com/next) | 3 |\n\nAfter\n\n"
            + "- | Nested table | Value |\n  | --- | ---: |\n  | Cell | 4 |";

    private static MarkdownPageContent cache(String source) {
        return new MarkdownPageContent(source, MarkdownStyleTable.defaults(), new TextStyle());
    }

    @Test
    public void cacheSkipsMetricsAndInvalidatesEveryPixelInput() {
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        MarkdownPageContent cache = cache(SOURCE);
        MarkdownPainter.ContentLayout first = cache.layout(metrics, 240, 14, 1);
        int calls = metrics.calls;
        Assert.assertTrue("冷布局必须度量，避免计数代理空转", calls > 0);
        Assert.assertSame(first, cache.layout(metrics, 240, 14, 1));
        Assert.assertEquals("命中零度量", calls, metrics.calls);
        MarkdownPainter.ContentLayout epoch = cache.layout(metrics, 240, 14, 2);
        Assert.assertNotSame(first, epoch);
        Assert.assertTrue(metrics.calls > calls);
        MarkdownPainter.ContentLayout narrow = cache.layout(metrics, 120, 14, 2);
        Assert.assertNotSame(epoch, narrow);
        Assert.assertTrue("窄列使多词样本增加行数", narrow.getHeightPx() > epoch.getHeightPx());
        MarkdownPainter.ContentLayout font = cache.layout(metrics, 120, 18, 2);
        Assert.assertNotSame(narrow, font);
        Assert.assertNotSame(font, cache.layout(new MarkdownCountingMetrics(), 120, 18, 2));
        Assert.assertNotEquals(first.getCommands(), cache("Different source").layout(metrics, 240, 14, 1).getCommands());
    }

    @Test
    public void scenePaintPreservesPlanOrderGeometryAndLinkSegments() {
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        MarkdownPainter.ContentLayout plan = cache(SOURCE).layout(metrics, 180, 14, 1);
        SceneNode root = SceneNode.column(0).setPreferredWidth(plan.getWidthPx())
                .setPreferredHeight(plan.getHeightPx());
        for (SceneNode node : MarkdownPageContent.nodes(plan, metrics)) root.appendChild(node);
        FixedTextMeasurer sceneMetrics = new FixedTextMeasurer();
        new SceneLayoutEngine(sceneMetrics).layout(root, new Constraints(plan.getWidthPx(), plan.getHeightPx()));
        List<PaintCommand> actual = new ScenePaintEngine(sceneMetrics).paint(root).getPlan().getCommands();
        List<PaintCommand> visible = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() != PaintCommandType.LINK_REGION) visible.add(command);
        }
        assertCommands(visible, actual);
        Assert.assertEquals(plan.getHeightPx(), ((LayoutBox) root.getCachedLayout()).getHeight());
        boolean link = false;
        for (PaintCommand command : actual) {
            if (command.getType() == PaintCommandType.SEGMENTS) {
                for (TextSegment segment : command.getSegments()) link |= segment.getStyle().getLink() != null;
            }
        }
        Assert.assertTrue("链接段必须真实进入最终scene计划", link);

        // 视口切过第一行中部：段流不得因原点在裁剪外而丢弃；裁剪归scene成对命令。
        SceneNode viewport = SceneNode.column(0).setPreferredWidth(plan.getWidthPx())
                .setPreferredHeight(14).setScrollable(true).setClipChildren(true);
        viewport.appendChild(root);
        new SceneLayoutEngine(sceneMetrics).layout(viewport, new Constraints(plan.getWidthPx(), 14));
        viewport.setScrollOffsetY(7);
        List<PaintCommand> clipped = new ScenePaintEngine(sceneMetrics).paint(viewport).getPlan().getCommands();
        List<PaintCommand> scrolled = new ArrayList<PaintCommand>();
        for (PaintCommand command : clipped) {
            if (command.getType() == PaintCommandType.BACKGROUND || command.getType() == PaintCommandType.SEGMENTS) {
                scrolled.add(command);
            }
        }
        assertCommands(visible, scrolled, -7);
        Assert.assertTrue("保留scene裁剪边界", clipped.size() > scrolled.size());
    }

    @Test
    public void widthAndEpochSignalsRebuildOnlyWhenNecessary() {
        ReactiveScheduler.get().reset();
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        SceneRuntime runtime = new SceneRuntime(new FixedTextMeasurer());
        SceneNode root = SceneNode.column();
        int[] epoch = {1};
        SceneNode[] body = {null};
        runtime.mount(root, () -> {
            body[0] = MarkdownPageContent.create(runtime, SOURCE, MarkdownStyleTable.defaults(),
                    new TextStyle(), 14, () -> metrics, () -> epoch[0]);
            return body[0];
        });
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
        try {
            settle(runtime, engine, root, 240);
            List<SceneNode> first = new ArrayList<SceneNode>(body[0].__getChildren());
            Assert.assertFalse(first.isEmpty());
            int calls = metrics.calls;
            runtime.__tickFrame(1L);
            settle(runtime, engine, root, 240);
            Assert.assertEquals("稳态不度量", calls, metrics.calls);
            Assert.assertEquals("稳态不重建节点", first, body[0].__getChildren());
            epoch[0]++;
            runtime.__tickFrame(2L);
            settle(runtime, engine, root, 240);
            Assert.assertTrue(metrics.calls > calls);
            Assert.assertNotSame(first.get(0), body[0].__getChildren().get(0));
            List<SceneNode> second = new ArrayList<SceneNode>(body[0].__getChildren());
            settle(runtime, engine, root, 120);
            Assert.assertNotSame(second.get(0), body[0].__getChildren().get(0));
            Assert.assertEquals(120, ((LayoutBox) body[0].getCachedLayout()).getWidth());
        } finally {
            runtime.dispose();
            ReactiveScheduler.get().reset();
        }
    }

    private static void settle(SceneRuntime rt, SceneLayoutEngine engine, SceneNode root, int width) {
        for (int i = 0; i < 4; i++) {
            rt.flush();
            engine.layout(root, new Constraints(width));
            rt.__setLayoutDoneEpoch(engine.layoutEpoch());
        }
        rt.flush();
    }

    private static void assertCommands(List<PaintCommand> expected, List<PaintCommand> actual) {
        assertCommands(expected, actual, 0);
    }

    private static void assertCommands(List<PaintCommand> expected, List<PaintCommand> actual, int offsetY) {
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            PaintCommand a = expected.get(i), b = actual.get(i);
            String where = "command " + i;
            Assert.assertEquals(where, a.getType(), b.getType());
            Assert.assertEquals(where, a.getLeft(), b.getLeft());
            Assert.assertEquals(where, a.getTop() + offsetY, b.getTop());
            Assert.assertEquals(where, a.getRight(), b.getRight());
            Assert.assertEquals(where, a.getBottom() + offsetY, b.getBottom());
            Assert.assertEquals(where, a.getColor(), b.getColor());
            Assert.assertEquals(where, a.getCornerRadius(), b.getCornerRadius());
            Assert.assertEquals(where, a.getSegments(), b.getSegments());
            if (a.getType() == PaintCommandType.SEGMENTS) {
                Assert.assertEquals(where, a.getTextStyle().getFontSize(), b.getTextStyle().getFontSize());
            }
        }
    }

    /**
     * G16/MarkdownPage（含本 L3）：BACKGROUND 节点底色/圆角恒等于 {@code PaintCommand} 数据色——
     * 契约 §7.3「markdown 样本」保留显式的搬运合同钉（表头/边框/围栏衬底色由 L2 样式表登记项解析，
     * 本类与主题色板零关联；配合 {@code MarkdownPageTest} 的反向钉住证明不被主题接管）。
     */
    @Test
    public void backgroundNodePropsAreTransportedFromCommandsNotPalette() {
        MarkdownCountingMetrics metrics = new MarkdownCountingMetrics();
        MarkdownPainter.ContentLayout plan = cache(SOURCE).layout(metrics, 180, 14, 1);
        List<SceneNode> nodes = MarkdownPageContent.nodes(plan, metrics);
        List<PaintCommand> visible = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() != PaintCommandType.LINK_REGION) visible.add(command);
        }
        Assert.assertEquals(visible.size(), nodes.size());
        int backgrounds = 0;
        for (int i = 0; i < visible.size(); i++) {
            PaintCommand command = visible.get(i);
            if (command.getType() != PaintCommandType.BACKGROUND) continue;
            backgrounds++;
            Assert.assertEquals("BACKGROUND 节点底色恒为命令数据色（L107 搬运合同）",
                    command.getColor(), nodes.get(i).getBackgroundColor());
            Assert.assertEquals("BACKGROUND 节点圆角恒为命令几何",
                    command.getCornerRadius(), nodes.get(i).getCornerRadius());
        }
        Assert.assertTrue("含表格样本必须真实产生数据驱动底色（断言不空转）", backgrounds > 0);
    }

    @org.junit.AfterClass
    public static void release() { LatexSoftwareRenderKit.resetShared(); }
}
