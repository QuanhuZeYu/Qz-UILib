package club.heiqi.uilib.internal.chat3.view;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend.RenderCall;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;

/**
 * T3a 真消费者验收：只替换平台 sink 和字体度量环境，不替换 markdown wrap/layout。
 * printMarkdown -> history -> composer -> ChatMessageList -> scene layout/paint/replay。
 * 玩家字面反锁仍由 ChatMessageListTest 和两份冻结 snapshot 负责。
 */
public class ChatMarkdownTableConsumerTest {
    private static final long NOW = 1_700_000_000_000L;
    private int savedBudget;
    private boolean savedGlass;

    @Before public void stableMetrics() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
        savedGlass = ChatMarkdownSettings.isGlassEnabled();
        ChatMarkdownSettings.setGlassEnabled(false);
    }

    @After public void restoreSettings() {
        FontConfig.widthCacheMissBudgetPerWindow = savedBudget;
        ChatMarkdownSettings.setGlassEnabled(savedGlass);
    }

    private static String tableSource() {
        StringBuilder source = new StringBuilder("before-table\n\n| First | Second |\n| --- | --- |\n");
        for (int row = 0; row < 16; row++) {
            source.append("| **row").append(row).append("** | words that wrap inside the second column |\n");
        }
        return source.append("\nafter-table").toString();
    }

    @Test public void sourceHasRealTableIdentity() {
        Assert.assertEquals(1, MarkdownDocument.parse(tableSource()).toTableModels(new TextStyle()).size());
        Assert.assertEquals(16, MarkdownDocument.parse(tableSource()).toTableModels(new TextStyle())
                .get(0).getRows().size());
    }

    @Test public void hudKeepsFullTwoDimensionalContentBehindRealClip() throws Exception {
        try (Fixture f = new Fixture(tableSource(), true, 800, 600, 0)) {
            SceneNode viewport = f.tableViewport();
            Assert.assertTrue("完整内容必须超出 HUD 视口", SceneGeometry.maxScrollY(viewport) > 0);
            Capture painted = f.capture();
            Assert.assertTrue("完整尾文仍递交给 replay，不能被八行裁掉", painted.allText().contains("after-table"));
            Assert.assertFalse("初始 HUD 尾文被 clip", painted.visibleText().contains("after-table"));
            assertTwoColumns(painted);
            Assert.assertFalse("语法分隔行不再字面", painted.allText().contains("---"));
            Assert.assertFalse("表格管道符不再字面", painted.allText().contains("|"));
            Assert.assertTrue("头部应实际可见", painted.visibleText().contains("First"));
        }
    }

    @Test public void expandedContainerScrollReachesTailWithoutDeletingHead() throws Exception {
        try (Fixture f = new Fixture(tableSource(), false, 800, 600, 0)) {
            SceneNode viewport = f.tableViewport();
            Capture before = f.capture();
            Assert.assertFalse(before.visibleText().contains("after-table"));
            f.scrollToBottom(viewport);
            Capture after = f.capture();
            Assert.assertTrue("鼠标滚轮经 scene 路由到达完整尾文", after.visibleText().contains("after-table"));
            Assert.assertTrue("滚动只移动完整内容，表头并未删除", after.allText().contains("First"));
            Assert.assertFalse("滚到底部表头在裁剪范围之外", after.visibleText().contains("First"));
            Assert.assertEquals(SceneGeometry.maxScrollY(viewport), viewport.getScrollOffsetY());
        }
    }

    @Test public void narrowerRealChatColumnWrapsCellsWithoutLosingRows() throws Exception {
        try (Fixture narrow = new Fixture(tableSource(), false, 400, 600, 0);
                Fixture wide = new Fixture(tableSource(), false, 1920, 600, 0)) {
            SceneNode n = narrow.tableViewport(), w = wide.tableViewport();
            Assert.assertTrue("必须是可区分的宿主可用宽", box(n).getWidth() < box(w).getWidth());
            Assert.assertTrue("窄列必须真实增加 cell 软折高度", box(n.__getChildren().get(0)).getHeight()
                    > box(w.__getChildren().get(0)).getHeight());
            for (Fixture f : new Fixture[] {narrow, wide}) {
                Capture capture = f.capture();
                assertTwoColumns(capture);
                Assert.assertTrue(capture.allText().contains("row15"));
                Assert.assertTrue(capture.allText().contains("after-table"));
            }
        }
    }

    @Test public void minimumColumnWidthsOverflowHorizontallyAndMouseSliderReachesLastColumn() throws Exception {
        String source = "| A | B | C | D | E | F | G | H | I | J | K | Z |\n"
                + "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n"
                + "| one | two | three | four | five | six | seven | eight | nine | ten | eleven | tail |";
        try (Fixture f = new Fixture(source, false, 400, 600, 0)) {
            SceneNode viewport = f.tableViewport();
            Assert.assertTrue("多列最小宽应超出窄聊天列", SceneGeometry.maxScrollX(viewport) > 0);
            Capture before = f.capture();
            Assert.assertFalse("最后一列初始在横向裁剪范围外", before.visibleText().contains("Z"));
            // 真实 helper 树中横向 slider 与 viewport row 同级；只观察节点，不调用其写入回调。
            SceneNode contentRoot = viewport.__getParent().__getParent();
            SceneNode track = horizontalTrack(contentRoot, viewport.__getParent());
            AnchorRect trackBox = SceneGeometry.absoluteBox(track, 0, 0);
            f.input.press(track);
            f.input.moveAt(trackBox.getX() + trackBox.getWidth(), trackBox.getY() + trackBox.getHeight() / 2);
            f.input.releaseAt(trackBox.getX() + trackBox.getWidth(), trackBox.getY() + trackBox.getHeight() / 2);
            f.settle();
            Assert.assertEquals("鼠标 capture 拖到横滚末端", SceneGeometry.maxScrollX(viewport), viewport.getScrollOffsetX());
            Assert.assertEquals("横滚不应偷改纵轴", 0, viewport.getScrollOffsetY());
            Assert.assertTrue("最后一列真实进入 clip", f.capture().visibleText().contains("Z"));
        }
    }

    @Test public void innerScrollConsumesMovementAndHandsBoundaryToOuterHistory() throws Exception {
        // 平台壳替身不允许漂移为不存在的生产接线；输入行为仍在下面走真实 scene 路由验证。
        String surface = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatInputSurface.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertTrue(surface.contains("runtime.on(root, SceneEventType.SCROLL,"));
        Assert.assertTrue(surface.contains("wheelScrollLines(event.getWheelDelta(), event.isShiftDown())"));
        Assert.assertTrue(surface.contains("controller.smoothScroll().releaseDrag();"));
        Assert.assertTrue(surface.contains("controller.history().scrollBy(wheel);"));
        Assert.assertTrue(surface.contains("controller.notifyDataChanged();"));
        try (Fixture f = new Fixture(tableSource(), false, 800, 600, 20)) {
            SceneNode viewport = f.tableViewport();
            SceneNode outer = f.container.root().__getChildren().get(0).__getChildren().get(0);
            Assert.assertTrue("真实历史视口也必须溢出", SceneGeometry.maxScrollY(outer) > 0);
            int historyBefore = f.controller.history().getScroll();
            f.input.scroll(viewport, -120);
            f.settle();
            Assert.assertTrue("内层先滚动", viewport.getScrollOffsetY() > 0);
            Assert.assertEquals("内层移动不冒泡到历史", historyBefore, f.controller.history().getScroll());
            f.input.scroll(viewport, Integer.MAX_VALUE);
            f.settle();
            Assert.assertEquals("先回到内层上沿", 0, viewport.getScrollOffsetY());
            Assert.assertEquals(historyBefore, f.controller.history().getScroll());
            int outerBefore = outer.getScrollOffsetY();
            f.input.scroll(viewport, 120);
            f.controller.tick(NOW + 2000L);
            f.settle();
            Assert.assertTrue("内层上沿继续向上会交给外层历史", f.controller.history().getScroll() > historyBefore);
            Assert.assertTrue("历史 Signal 投影真实移动外层视口", outer.getScrollOffsetY() < outerBefore);
            Assert.assertEquals("历史滚动不反写内层位置", 0, viewport.getScrollOffsetY());
        }
    }

    @Test public void explicitNonTableMarkdownKeepsExistingLinePath() throws Exception {
        StringBuilder source = new StringBuilder("**plain-markdown**\n\n");
        for (int i = 0; i < 16; i++) source.append("paragraph ").append(i).append("\n\n");
        source.append("plain-tail");
        for (boolean hud : new boolean[] {true, false}) {
            try (Fixture f = new Fixture(source.toString(), hud, 800, 600, 0)) {
                List<SceneNode> tables = new ArrayList<SceneNode>();
                collectTableViewports(f.root, tables);
                Assert.assertTrue("非表格不可被表格视口接管", tables.isEmpty());
                Capture capture = f.capture();
                Assert.assertTrue(capture.allText().contains("plain-markdown"));
                Assert.assertEquals("非表格 HUD 仍沿现有八行截断，展开保留尾文", !hud,
                        capture.allText().contains("plain-tail"));
            }
        }
    }

    @Test public void movingBetweenDifferentLinksKeepsLatestHoverAfterFlush() throws Exception {
        String a = "https://example.com/a", b = "https://example.com/b";
        String source = "[alpha](" + a + ") [beta](" + b + ")\n\n| H | V |\n| --- | --- |\n| row | data |";
        try (Fixture f = new Fixture(source, false, 800, 600, 0)) {
            SceneNode viewport = f.tableViewport();
            MarkdownPainter.ContentLayout layout = MarkdownPainter.layoutContent(MarkdownDocument.parse(source)
                    .toLayoutContent(ChatMarkdownPipeline.chatStyleTable(), new TextStyle()),
                    FontService.getInstance().getTextLayoutService(), box(viewport).getWidth(),
                    ChatMarkdownSettings.getSystemFontSizePx());
            PaintCommand ar = region(layout.getCommands(), a), br = region(layout.getCommands(), b);
            AnchorRect body = SceneGeometry.absoluteBox(viewport.__getChildren().get(0), 0, 0);
            int ax = body.getX() + (ar.getLeft() + ar.getRight()) / 2;
            int ay = body.getY() + (ar.getTop() + ar.getBottom()) / 2;
            int bx = body.getX() + (br.getLeft() + br.getRight()) / 2;
            int by = body.getY() + (br.getTop() + br.getBottom()) / 2;
            f.input.moveAt(ax, ay);
            Assert.assertEquals("先命中 A", a, currentHover(f));
            f.input.moveAt(bx, by);
            Assert.assertEquals("A 的离开不能覆盖刚进入的 B", b, currentHover(f));
            f.input.moveAt(bx, by);
            Assert.assertEquals("同点再次 MOVE 不能卡在已被清空的 hover", b, currentHover(f));
        }
    }

    @Test public void legacyExplicitLineAndTableRegionShareOneCurrentHover() throws Exception {
        String oldUrl = "https://example.com/old", tableUrl = "https://example.com/table";
        String source = "| H | V |\n| --- | --- |\n| [table-link](" + tableUrl + ") | data |";
        try (Fixture f = new Fixture(source, false, 1920, 800, 0)) {
            printMarkdownInto(f.controller, "[old-link](" + oldUrl + ")");
            f.controller.notifyDataChanged();
            f.settle();
            Capture capture = f.capture();
            Draw old = capture.find("old-link"), table = capture.find("table-link");
            Assert.assertNotNull(old); Assert.assertNotNull(table);
            Assert.assertTrue("旧行消息和表格链接都应真实可见", old.visible() && table.visible());
            f.input.moveAt(old.x + 1, old.y + 1);
            Assert.assertEquals(oldUrl, currentHover(f));
            f.input.moveAt(table.x + 1, table.y + 1);
            Assert.assertEquals("旧行 driver 离开不能清掉表格当前链接", tableUrl, currentHover(f));
            f.input.moveAt(table.x + 1, table.y + 1);
            Assert.assertEquals(tableUrl, currentHover(f));
            f.input.moveAt(old.x + 1, old.y + 1);
            Assert.assertEquals("反向切换也必须保持旧行当前链接", oldUrl, currentHover(f));
        }
    }

    private static String currentHover(Fixture f) throws Exception {
        // 只读现有 tooltip URL Signal；不为测试扩生产 API，也不读取 owner/active 等实现标志。
        Field field = ChatMessageList.class.getDeclaredField("hoverLink");
        field.setAccessible(true);
        return ((club.heiqi.uilib.ui.reactive.Signal<?>) field.get(f.controller.messageList())).get().toString();
    }

    @Test public void tallFormulaLinkTopAndScrolledTailUseL2RegionsInsideClip() throws Exception {
        String formula = "$\\frac{1}{\\frac{a}{b}}$";
        String firstUrl = "https://example.com/top", lastUrl = "https://example.com/tail";
        String source = "x [" + formula + "](" + firstUrl + ")\n\n" + tableSource()
                + "\n\nx [" + formula + "](" + lastUrl + ")";
        try (Fixture f = new Fixture(source, false, 800, 600, 0)) {
            SceneNode viewport = f.tableViewport(), body = viewport.__getChildren().get(0);
            List<ChatLinkClick> clicks = new ArrayList<ChatLinkClick>();
            f.controller.setMessageLinkClickHandler(clicks::add);
            TextLayoutService metrics = FontService.getInstance().getTextLayoutService();
            int font = ChatMarkdownSettings.getSystemFontSizePx();
            // 独立 L2 原始命令是几何 oracle；不读取 helper 自报的命中区域作为期望。
            MarkdownDocument.LayoutContent document = MarkdownDocument.parse(source)
                    .toLayoutContent(ChatMarkdownPipeline.chatStyleTable(), new TextStyle())
                    .mapSegments(segments -> metrics.applyLatexLineHeightConstraint(segments, font,
                            ChatMarkdownSettings.getChatLineHeightPx(), ChatMarkdownSettings.getLatexMaxLineHeightFactor(),
                            ChatMarkdownSettings.getLatexShrinkFactor()));
            List<PaintCommand> commands = MarkdownPainter.layoutContent(document, metrics, box(viewport).getWidth(), font).getCommands();
            PaintCommand first = region(commands, firstUrl), last = region(commands, lastUrl);
            PaintCommand text = formulaText(commands, firstUrl);
            Assert.assertTrue("高公式代理必须向普通段原点上方伸出", first.getTop() < text.getTop());
            AnchorRect origin = SceneGeometry.absoluteBox(body, 0, 0);
            int x = origin.getX() + (first.getLeft() + first.getRight()) / 2;
            int top = origin.getY() + first.getTop() + 1;
            f.input.clickAt(x, top);
            Assert.assertEquals("可见公式顶端必须真正投递点击", 1, clicks.size());
            Assert.assertEquals(firstUrl, clicks.get(0).url());
            Assert.assertNotNull("保留原始消息组件", clicks.get(0).component());

            f.input.scroll(viewport, -(first.getTop() + (first.getBottom() - first.getTop()) / 2));
            f.settle();
            AnchorRect vp = SceneGeometry.absoluteBox(viewport, 0, 0);
            origin = SceneGeometry.absoluteBox(body, 0, 0);
            Assert.assertTrue("公式跨越 clip 上沿，反空跑", origin.getY() + first.getTop() < vp.getY()
                    && origin.getY() + first.getBottom() > vp.getY());
            f.input.clickAt(x, vp.getY() - 1);
            Assert.assertEquals("被裁掉的公式部分不得点击", 1, clicks.size());
            f.input.clickAt(x, vp.getY() + 1);
            Assert.assertEquals("滚后仍可点击可见公式上沿", 2, clicks.size());
            Assert.assertEquals(firstUrl, clicks.get(1).url());

            f.scrollToBottom(viewport);
            origin = SceneGeometry.absoluteBox(body, 0, 0);
            Assert.assertTrue("含表文档普通尾行公式完整进入视口", origin.getY() + last.getTop() >= vp.getY()
                    && origin.getY() + last.getBottom() <= vp.getY() + vp.getHeight());
            f.input.clickAt(origin.getX() + (last.getLeft() + last.getRight()) / 2,
                    origin.getY() + last.getTop() + 1);
            Assert.assertEquals("滚后尾公式顶端可点击", 3, clicks.size());
            Assert.assertEquals(lastUrl, clicks.get(2).url());
        }
    }

    private static PaintCommand region(List<PaintCommand> commands, String url) {
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.LINK_REGION && url.equals(command.getLinkUrl())) return command;
        }
        throw new AssertionError("缺少 L2 公式链接区域: " + url);
    }

    private static PaintCommand formulaText(List<PaintCommand> commands, String url) {
        for (PaintCommand command : commands) {
            if (command.getType() != PaintCommandType.SEGMENTS) continue;
            for (TextSegment segment : command.getSegments()) {
                if (segment.isLatex() && url.equals(segment.getStyle().getLink())) return command;
            }
        }
        throw new AssertionError("缺少公式段: " + url);
    }

    @Test public void writesRealSceneClipAndScrollComparison() throws Exception {
        try (Fixture hud = new Fixture(tableSource(), true, 800, 600, 0);
                Fixture expanded = new Fixture(tableSource(), false, 800, 600, 0)) {
            SceneNode hv = hud.tableViewport(), ev = expanded.tableViewport();
            Capture h = hud.capture(), start = expanded.capture();
            expanded.scrollToBottom(ev);
            Capture end = expanded.capture();
            Assert.assertEquals("独立 HUD occurrence 不受展开滚动影响", 0, hv.getScrollOffsetY());
            Assert.assertEquals("HUD 横轴同样隔离", 0, hv.getScrollOffsetX());
            Assert.assertTrue(h.allText().contains("after-table"));
            Assert.assertFalse(h.visibleText().contains("after-table"));
            Assert.assertFalse(start.visibleText().contains("after-table"));
            Assert.assertTrue(end.visibleText().contains("after-table"));
            AnchorRect hb = SceneGeometry.absoluteBox(hv, 0, 0), eb = SceneGeometry.absoluteBox(ev, 0, 0);
            int bodyHeight = box(hv.__getChildren().get(0)).getHeight();
            java.awt.image.BufferedImage full = h.rasterViewport(hb, box(hv.__getChildren().get(0)).getWidth(), bodyHeight, false);
            java.awt.image.BufferedImage clipped = h.rasterViewport(hb, hb.getWidth(), hb.getHeight(), true);
            java.awt.image.BufferedImage scrolled = end.rasterViewport(eb, eb.getWidth(), eb.getHeight(), true);
            int panel = Math.max(full.getWidth(), Math.max(clipped.getWidth(), scrolled.getWidth())) + 32;
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(panel * 3, bodyHeight + 88,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            try {
                g.setColor(new java.awt.Color(0xff17151b, true)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
                g.setColor(java.awt.Color.WHITE); g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12));
                g.drawString("Full scene commands (clip off)", 12, 20);
                g.drawString("HUD: clip on, tail retained", panel + 12, 20);
                g.drawString("Expanded: scrolled to tail", panel * 2 + 12, 20);
                g.drawString("Java2D glyph stand-in; actual scene positions, clip and scroll", 12, 42);
                g.drawImage(full, 12, 64, null); g.drawImage(clipped, panel + 12, 64, null);
                g.drawImage(scrolled, panel * 2 + 12, 64, null);
            } finally { g.dispose(); }
            java.io.File directory = new java.io.File(System.getProperty("qz.chat.table.output",
                    "build/reports/chat-markdown-table-consumer"));
            Assert.assertTrue(directory.isDirectory() || directory.mkdirs());
            Assert.assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(directory, "scene-clip-scroll.png")));
        }
    }

    private static SceneNode horizontalTrack(SceneNode contentRoot, SceneNode viewportRow) {
        // rt.show 可包含零尺寸 anchor；只认真实挂载且有可命中 track 的 slider。
        for (SceneNode child : contentRoot.__getChildren()) {
            if (child == viewportRow || child.__getChildren().isEmpty()) continue;
            SceneNode track = child.__getChildren().get(0);
            if (track.isHitTestable() && box(track) != null && box(track).getWidth() > 0 && box(track).getHeight() > 0) return track;
        }
        throw new AssertionError("横向溢出时必须提供真实可命中的 slider track");
    }

    private static LayoutBox box(SceneNode node) { return (LayoutBox) node.getCachedLayout(); }

    private static void assertTwoColumns(Capture capture) {
        Draw first = capture.find("First"), second = capture.find("Second");
        Assert.assertNotNull("表头第一格必须存在", first);
        Assert.assertNotNull("表头第二格必须存在", second);
        Assert.assertEquals("两格共享真实行位置", first.y, second.y);
        Assert.assertTrue("两格有不同横向位置，不能仅去掉 pipe", second.x > first.x);
        Assert.assertTrue("数据行独立于表头", capture.find("row0").y > first.y);
    }

    private static void printMarkdownInto(ChatSceneController controller, String source) throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        // 平台注入接缝：临时替换 singleton sink，finally 恢复，避免影响其他消费者测试。
        Field sink = ChatAccess.class.getDeclaredField("markdownSink");
        sink.setAccessible(true);
        Object previous = sink.get(access);
        try {
            access.setMarkdownSink(component -> controller.history().append(new ChatLineRecord(component, 0, NOW)));
            access.printMarkdown(source);
        } finally {
            sink.set(access, previous);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SceneInteractionHarness input = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        final SceneRuntime rt = input.getRuntime();
        final SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        final ChatSceneController controller;
        final Map<SceneNode, ChatLineRecord> registry = new IdentityHashMap<SceneNode, ChatLineRecord>();
        final SceneNode root;
        final ChatContainer.Result container;
        final int width, height;

        Fixture(String source, boolean hud, int width, int height, int olderMessages) throws Exception {
            this.width = width;
            this.height = height;
            controller = new ChatSceneController(new ChatLineLayouter.Measure() {
                @Override public float advance(String text, int fontSizePx) { return text.length() * 4.0F; }
                @Override public int epoch() { return 0; }
            }, () -> "Alex", (text, color) -> {
                TextStyle style = new TextStyle();
                style.setColor(color);
                return Collections.singletonList(new TextSegment(text, style));
            }, ChatSceneController.uiLibSegmentMeasurer());
            controller.setHostViewport(width, height);
            controller.tick(NOW);
            controller.setChatOpen(!hud);
            for (int i = 0; i < olderMessages; i++) {
                controller.history().append(new ChatLineRecord(new ChatComponentText("older-" + i), i + 1, NOW));
            }
            printMarkdownInto(controller, source);
            controller.notifyDataChanged();
            if (hud) {
                container = null;
                root = controller.buildContent(rt);
            } else {
                container = ChatContainer.mount(rt, controller, registry, "");
                container.setViewport(width, height);
                root = SceneNode.column().setHitTestable(true);
                root.appendChild(container.root());
                // 平台壳边界替身：ChatInputSurface 的 root SCROLL 语义，生产容器与历史投影仍真实。
                // 不构造 LwjglInputSource/原版屏幕，也不直接调用 inner handler。
                rt.on(root, SceneEventType.SCROLL, (event, ctx) -> {
                    int delta = event.getWheelDelta();
                    if (delta == 0) return;
                    controller.smoothScroll().releaseDrag();
                    controller.history().scrollBy(delta > 0 ? 7 : -7);
                    controller.notifyDataChanged();
                });
            }
            settle();
            controller.tick(NOW + 1000L);
            settle();
        }

        void settle() {
            rt.flush();
            input.mountRoot(root, width, height);
            // 无宿主帧循环；与 SceneFramePipeline 一样在 layout 后发布完成 epoch。
            // 内容宽来自第一次真实 layout，后续 settle 让完整 plan/滚动上界/滚动条得到同一几何。
            for (int pass = 0; pass < 4; pass++) {
                layout.layout(root, new Constraints(width, height));
                rt.__setLayoutDoneEpoch(layout.layoutEpoch());
                rt.flush();
            }
        }

        SceneNode tableViewport() {
            List<SceneNode> found = new ArrayList<SceneNode>();
            collectTableViewports(root, found);
            Assert.assertEquals("真实显式消费者必须装配一个二维滚动视口", 1, found.size());
            return found.get(0);
        }

        void scrollToBottom(SceneNode viewport) {
            int attempts = 0;
            while (viewport.getScrollOffsetY() < SceneGeometry.maxScrollY(viewport) && attempts++ < 200) {
                int before = viewport.getScrollOffsetY();
                input.scroll(viewport, -120);
                settle();
                Assert.assertTrue("每次滚轮都应推进内层内容", viewport.getScrollOffsetY() > before);
            }
            Assert.assertTrue("必须真实跨越视口", viewport.getScrollOffsetY() > 0);
        }

        Capture capture() { return new Capture(ScenePaintCapture.paintAndCapture(root, width, height)); }

        @Override public void close() {
            if (container != null) container.dispose();
            input.dispose();
        }
    }

    private static void collectTableViewports(SceneNode node, List<SceneNode> found) {
        if (node.isScrollable() && node.isScrollableX()) found.add(node);
        for (SceneNode child : node.__getChildren()) collectTableViewports(child, found);
    }

    /** replay 记录独立解释 clip 栈；不拿生产 helper 的可见行集合当期望。 */
    private static final class Capture {
        final List<Draw> draws = new ArrayList<Draw>();
        final List<RenderCall> calls;
        Capture(RecordingRenderBackend backend) {
            calls = backend.getCalls();
            List<int[]> clips = new ArrayList<int[]>();
            for (RenderCall call : backend.getCalls()) {
                if ("pushClip".equals(call.methodName())) {
                    int[] box = {call.getInt(0), call.getInt(1), call.getInt(2), call.getInt(3)};
                    if (!clips.isEmpty()) {
                        int[] parent = clips.get(clips.size() - 1);
                        box[0] = Math.max(box[0], parent[0]); box[1] = Math.max(box[1], parent[1]);
                        box[2] = Math.min(box[2], parent[2]); box[3] = Math.min(box[3], parent[3]);
                    }
                    clips.add(box);
                } else if ("popClip".equals(call.methodName())) {
                    Assert.assertFalse("clip 栈不可下溢", clips.isEmpty());
                    clips.remove(clips.size() - 1);
                } else if ("drawSegments".equals(call.methodName())) {
                    @SuppressWarnings("unchecked")
                    List<TextSegment> segments = (List<TextSegment>) call.args()[0];
                    StringBuilder text = new StringBuilder();
                    for (TextSegment segment : segments) text.append(segment.getText());
                    draws.add(new Draw(text.toString(), call.getInt(1), call.getInt(2), call.getInt(3),
                            clips.isEmpty() ? null : clips.get(clips.size() - 1).clone()));
                }
            }
            Assert.assertTrue("clip 栈必须平衡", clips.isEmpty());
        }
        /** 仅回放真实目标 viewport 作用域；字形用 JDK 字体替身，几何/clip 来自 scene replay。 */
        java.awt.image.BufferedImage rasterViewport(AnchorRect viewport, int width, int height, boolean clip) {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Math.max(1, width), Math.max(1, height),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            try {
                g.setColor(new java.awt.Color(0xff22212a, true)); g.fillRect(0, 0, width, height);
                g.translate(-viewport.getX(), -viewport.getY());
                int depth = 0, target = -1;
                for (RenderCall call : calls) {
                    String name = call.methodName();
                    if ("pushClip".equals(name)) {
                        depth++;
                        if (target < 0 && call.getInt(0) == viewport.getX() && call.getInt(1) == viewport.getY()
                                && call.getInt(2) == viewport.getX() + viewport.getWidth()
                                && call.getInt(3) == viewport.getY() + viewport.getHeight()) {
                            target = depth;
                            if (clip) g.clipRect(viewport.getX(), viewport.getY(), viewport.getWidth(), viewport.getHeight());
                        }
                    } else if ("popClip".equals(name)) {
                        if (depth == target) break;
                        depth--;
                    } else if (target >= 0 && ("fillRect".equals(name) || "drawSurface".equals(name))) {
                        g.setColor(new java.awt.Color(call.getInt(4), true));
                        g.fillRect(call.getInt(0), call.getInt(1), call.getInt(2) - call.getInt(0), call.getInt(3) - call.getInt(1));
                    } else if (target >= 0 && "drawSegments".equals(name)) {
                        @SuppressWarnings("unchecked") List<TextSegment> segments = (List<TextSegment>) call.args()[0];
                        StringBuilder text = new StringBuilder();
                        for (TextSegment segment : segments) text.append(segment.getText());
                        int size = Math.max(1, call.getInt(3));
                        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, size));
                        g.setColor(new java.awt.Color(0xffe9e7ef, true));
                        g.drawString(text.toString(), call.getInt(1), call.getInt(2) + g.getFontMetrics().getAscent());
                    }
                }
                Assert.assertTrue("出图必须找到真实 viewport clip 命令", target >= 0);
            } finally { g.dispose(); }
            return image;
        }
        String allText() { StringBuilder out = new StringBuilder(); for (Draw d : draws) out.append(d.text); return out.toString(); }
        String visibleText() { StringBuilder out = new StringBuilder(); for (Draw d : draws) if (d.visible()) out.append(d.text); return out.toString(); }
        Draw find(String text) { for (Draw d : draws) if (d.text.contains(text)) return d; return null; }
    }

    private static final class Draw {
        final String text;
        final int x, y, fontSize;
        final int[] clip;
        Draw(String text, int x, int y, int fontSize, int[] clip) {
            this.text = text; this.x = x; this.y = y; this.fontSize = fontSize; this.clip = clip;
        }
        boolean visible() {
            return clip == null || (y >= clip[1] && y + fontSize <= clip[3] && x >= clip[0] && x < clip[2]);
        }
    }
}
