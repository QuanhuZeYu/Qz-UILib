package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FontSortFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** {@link FontSortFieldRenderer} headless scene 集成测试。 */
public class FontSortFieldRendererTest {

    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 360;
    private static final int EXPECTED_LIST_HEIGHT = 220;
    private static final int ROW_HEIGHT = 30;

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private ConfigSchema schema;
    private ConfigManager manager;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FieldRenderer renderer;
    private SceneNode sceneRoot;
    private SceneNode card;
    private FieldSpec spec;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("fontSort").label("字体排序").helper("按优先级拖拽排序").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.fontSort");
        renderer = new FontSortFieldRenderer(Arrays.asList("Font A", "Font B"));
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        if (adapter != null) {
            adapter.dispose();
        }
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    private void mountWithInitial(String yaml) throws Exception {
        mountWithInitial(yaml, Arrays.asList("Font A", "Font B"));
    }

    private void mountWithInitial(String yaml, List<String> discovered) throws Exception {
        renderer = new FontSortFieldRenderer(discovered);
        File file = File.createTempFile("fontsort-renderer-", ".yaml");
        write(file, yaml);
        manager = ConfigManager.bootstrap(file, schema);
        // 关键事务用例必须使用 manager-owned draft，禁止 unowned DraftBuffer.from。
        draft = manager.openDraft();
        adapter = new DraftSignalAdapter(runtime, draft);
        card = renderer.render(runtime, spec, adapter);
        SceneNode controlRoot = findControlRoot(card);
        Assert.assertNotNull("card 内应找到 fontSort 控件根", controlRoot);
        Assert.assertEquals("fontSort 控件根应自带稳定 listHeight 高度",
                EXPECTED_LIST_HEIGHT, controlRoot.getPreferredHeight());
        sceneRoot.appendChild(card);
        settle();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    /** 空 draft 只预填 presentation，draft 与 dirty 保持不变。 */
    @Test
    public void emptyDraftShowsFrozenFontsWithoutWritingDraft() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort: []\n");

        SceneNode viewport = findViewport(card);
        Assert.assertEquals("预填充应渲染 2 行", 2, rows(viewport).size());
        Assert.assertEquals("首行字体名", "Font A", fontName(rowAt(viewport, 0)));
        Assert.assertEquals("末行字体名", "Font B", fontName(rowAt(viewport, 1)));
        Assert.assertEquals("行结构应为 [handle, index input, text]", 3,
                rowAt(viewport, 0).__getChildren().size());
        Assert.assertFalse("fontSort 不应渲染添加按钮", containsText(card, "添加"));
        Assert.assertEquals("draft 镜像仍空", 0,
                ((List<?>) adapter.draftSignal("fontSystem.fontSort").get()).size());
        Assert.assertFalse("打开不应 dirty", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 行高、索引宽度和字体名槽位固定，长文本不能挤压索引或滚动条。 */
    @Test
    public void rowsHaveStableIndexAndLabelSlots() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - A very long discovered font name\n    - Font B\n",
                Arrays.asList("A very long discovered font name", "Font B"));
        SceneNode viewport = findViewport(card);
        SceneNode row = rowAt(viewport, 0);
        Assert.assertEquals(ROW_HEIGHT, row.getPreferredHeight());
        Assert.assertEquals(3, row.__getChildren().size());
        Assert.assertEquals("索引输入固定宽度", 56, row.__getChildren().get(1).getPreferredWidth());
        Assert.assertEquals("首行全局索引", "1", row.__getChildren().get(1).__getChildren().get(2).getText());
        Assert.assertNotNull("长字体名仍存在于 label 槽", row.__getChildren().get(2).getText());
    }

    /** 窄宽结构快照：固定把手/索引槽不被字体名或滚动条挤压。 */
    @Test
    public void narrowWidthKeepsIndexAndLabelNonOverlapping() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - An exceptionally long font name for narrow width\n    - Font B\n",
                Arrays.asList("An exceptionally long font name for narrow width", "Font B"));
        harness.mountRoot(sceneRoot, 240, CANVAS_HEIGHT);
        SceneNode row = rowAt(findViewport(card), 0);
        AnchorRect handleBox = SceneGeometry.absoluteBox(row.__getChildren().get(0), 0, 0);
        AnchorRect indexBox = SceneGeometry.absoluteBox(row.__getChildren().get(1), 0, 0);
        AnchorRect labelBox = SceneGeometry.absoluteBox(row.__getChildren().get(2), 0, 0);
        Assert.assertTrue("索引槽不得挤到把手左侧", indexBox.getX() >= handleBox.getX() + handleBox.getWidth());
        Assert.assertTrue("字体名槽不得覆盖索引槽", labelBox.getX() >= indexBox.getX() + indexBox.getWidth());
    }

    /** 真实拖拽 MOVE 只预览，UP 才一次性提交完整 order，并复用 keyed 行节点。 */
    @Test
    public void dragReordersFontNamesAndWritesDraftOnUpOnly() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode row0Before = rowAt(viewport, 0);
        SceneNode handle0 = dragHandle(row0Before);
        int hx = centerX(handle0);
        int targetY = pointerYForDraggedCenter(row0Before, handle0, bottomY(rowAt(viewport, 2)) + 1);

        harness.pressAt(hx, centerY(handle0));
        harness.moveAt(hx, targetY);
        Assert.assertEquals("MOVE 期 draft 不提交",
                Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("MOVE 期 dirty 不闪动", adapter.dirtySignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"), fontNames(viewport));
        harness.releaseAt(hx, targetY);

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertSame("被拖行按 keyed identity 复用", row0Before, rowAt(viewport, 2));
        Assert.assertTrue("UP 后 dirty=true", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 已越过阈值但原位释放：UP 不写 draft，拖拽门闩清理后筛选仍可用。 */
    @Test
    public void noOpDragUpDoesNotWriteDraftAndAllowsFilteringAfterwards() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode handle = dragHandle(rowAt(viewport, 0));
        int x = centerX(handle);
        int y = centerY(handle);

        harness.pressAt(x, y);
        harness.moveAt(x, y + 6);
        harness.releaseAt(x, y + 6);

        Assert.assertEquals("原位 UP 不应写 draft", Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("原位 UP 不应 dirty", adapter.dirtySignal("fontSystem.fontSort").get());

        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);
        harness.typeText("b");
        Assert.assertEquals("原位 UP 后筛选仍可修改", Arrays.asList("Font B"), fontNames(viewport));
    }

    /** 同一手势换位后拖回原位：完整快照比较阻止提交，且 UP 后仍可筛选。 */
    @Test
    public void dragAwayAndBackBeforeUpIsNoOpAndClearsDragState() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int startY = centerY(handle);

        harness.pressAt(x, startY);
        int awayY = pointerYForDraggedCenter(draggedRow, handle, bottomY(rowAt(viewport, 2)) + 1);
        harness.moveAt(x, awayY);
        Assert.assertEquals("换位后先形成预览", Arrays.asList("Font B", "Font C", "Font A"),
                fontNames(viewport));
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);

        int backY = pointerYForDraggedCenter(draggedRow, handle, topY(rowAt(viewport, 0)) - 1);
        harness.moveAt(x, backY);
        Assert.assertEquals("拖回原位后完整预览恢复", Arrays.asList("Font A", "Font B", "Font C"),
                fontNames(viewport));
        harness.releaseAt(x, backY);

        Assert.assertEquals("换位后拖回原位不应提交 draft", Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("换位后拖回原位不应 dirty", adapter.dirtySignal("fontSystem.fontSort").get());

        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);
        harness.typeText("c");
        Assert.assertEquals("拖回原位后筛选仍可修改", Arrays.asList("Font C"), fontNames(viewport));
    }

    /** 阈值前 DOWN+CANCEL：full presentation 与 draft 均保持不变。 */
    @Test
    public void cancelBeforeDragActivationKeepsFullOrderAndDraft() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode handle = dragHandle(rowAt(viewport, 0));
        int x = centerX(handle);
        int y = centerY(handle);

        harness.pressAt(x, y);
        routePointer(ScenePointerAction.CANCEL, x, y);

        Assert.assertEquals("阈值前 CANCEL 不应清空 full 列表",
                Arrays.asList("Font A", "Font B", "Font C"), fontNames(viewport));
        Assert.assertEquals("阈值前 CANCEL 不应改 draft",
                Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("阈值前 CANCEL 后不应 dirty", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** CANCEL 只回滚 presentation snapshot，不写 draft。 */
    @Test
    public void dragCancelRollsBackWithoutWritingDraft() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode row0 = rowAt(viewport, 0);
        SceneNode handle = dragHandle(row0);
        int hx = centerX(handle);
        int targetY = pointerYForDraggedCenter(row0, handle, bottomY(rowAt(viewport, 2)) + 1);

        harness.pressAt(hx, centerY(handle));
        harness.moveAt(hx, targetY);
        routePointer(ScenePointerAction.CANCEL, hx, targetY);

        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"), fontNames(viewport));
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("CANCEL 后 dirty=false", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 筛选只改变可见投影，清空恢复全量，行内索引仍显示全局位置。 */
    @Test
    public void filterAndClearDoNotWriteDraftAndKeepGlobalIndex() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode controlRoot = viewport.__getParent().__getParent();
        SceneNode filterInput = controlRoot.__getChildren().get(0).__getChildren().get(0);
        SceneNode clearButton = controlRoot.__getChildren().get(0).__getChildren().get(1);

        harness.click(filterInput);
        harness.typeText("b");
        Assert.assertEquals(1, rows(viewport).size());
        Assert.assertEquals("Font B", fontName(rowAt(viewport, 0)));
        Assert.assertEquals("筛选行保留全局索引 2", "2",
                rowAt(viewport, 0).__getChildren().get(1).__getChildren().get(2).getText());
        Assert.assertFalse(adapter.dirtySignal("fontSystem.fontSort").get());

        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("z");
        Assert.assertEquals("空筛选结果不建字体行", 0, rows(viewport).size());
        Assert.assertTrue("空筛选结果显示紧凑提示", containsText(viewport, "无匹配字体"));

        harness.click(clearButton);
        Assert.assertEquals(3, rows(viewport).size());
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** Enter 提交一次合法全局索引；非法索引恢复当前值且不写 draft。 */
    @Test
    public void indexEnterCommitsOnceAndInvalidInputDoesNotCommit() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());

        SceneNode invalidInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(invalidInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("1.0");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);
        Assert.assertEquals("非法小数不应提交", Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** Enter 后 blur 不重复提交，Escape 恢复编辑值且不提交。 */
    @Test
    public void indexEnterThenBlurIsIdempotentAndEscapeCancels() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);
        @SuppressWarnings("unchecked")
        List<String> afterEnter = new ArrayList<String>(
                (List<String>) adapter.draftSignal("fontSystem.fontSort").get());
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);

        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);
        Assert.assertEquals("Enter 后 blur 不应二次改变顺序", afterEnter,
                adapter.draftSignal("fontSystem.fontSort").get());

        SceneNode movedRowIndex = rowAt(viewport, 2).__getChildren().get(1);
        harness.click(movedRowIndex);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("1");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ESCAPE);
        Assert.assertEquals("Escape 应恢复当前全量索引", "3", inputText(movedRowIndex));
        harness.click(filterInput);
        Assert.assertEquals("Escape 后 blur 不应提交", afterEnter,
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** 筛选态索引仍按完整列表位置提交。 */
    @Test
    public void filteredIndexUsesFullOrderPosition() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);
        harness.typeText("b");
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        Assert.assertEquals("筛选态仍显示全局索引", "2", indexInput.__getChildren().get(2).getText());

        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);

        Assert.assertEquals("筛选态索引移动应作用于 full order",
                Arrays.asList("Font A", "Font C", "Font B"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    private static SceneNode findControlRoot(SceneNode node) {
        SceneNode viewport = findScrollable(node);
        return viewport == null ? null : viewport.__getParent().__getParent();
    }

    private static SceneNode findViewport(SceneNode node) {
        SceneNode found = findScrollable(node);
        if (found == null) {
            throw new AssertionError("未找到滚动列表视口");
        }
        return found;
    }

    private static SceneNode findScrollable(SceneNode node) {
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<SceneNode> rows(SceneNode viewport) {
        Assert.assertFalse("viewport 应有 rowsContainer", viewport.__getChildren().isEmpty());
        return viewport.__getChildren().get(0).__getChildren();
    }

    private static SceneNode rowAt(SceneNode viewport, int index) {
        return rows(viewport).get(index);
    }

    private static SceneNode dragHandle(SceneNode row) {
        return row.__getChildren().get(0);
    }

    private static String fontName(SceneNode row) {
        return row.__getChildren().get(2).getText();
    }

    private static String inputText(SceneNode input) {
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
    }

    private static List<String> fontNames(SceneNode viewport) {
        List<String> result = new ArrayList<String>();
        for (SceneNode row : rows(viewport)) {
            result.add(fontName(row));
        }
        return result;
    }

    private static int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    private static int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    private static int topY(SceneNode node) {
        return SceneGeometry.absoluteBox(node, 0, 0).getY();
    }

    private static int bottomY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight();
    }

    private static int pointerYForDraggedCenter(SceneNode draggedRow, SceneNode handle, int draggedCenterY) {
        return draggedCenterY - (centerY(draggedRow) - centerY(handle));
    }

    private static boolean containsText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return true;
        }
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, text)) {
                return true;
            }
        }
        return false;
    }

    // 白盒回退（精确 localX/坐标）：harness 无 CANCEL 投递入口，需裸建 InputFrameBuilder 直投
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
        runtime.flush();
    }

    private static void write(File file, String content) throws Exception {
        java.io.FileWriter writer = new java.io.FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }
}
