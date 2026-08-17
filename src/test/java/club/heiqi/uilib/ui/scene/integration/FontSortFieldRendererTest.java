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
import club.heiqi.uilib.ui.reactive.Owner;
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
    private Owner renderOwner;

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
        if (renderOwner != null) {
            renderOwner.dispose();
        }
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
        renderOwner = new Owner();
        final SceneNode[] rendered = new SceneNode[1];
        renderOwner.run(() -> rendered[0] = renderer.render(runtime, spec, adapter));
        card = rendered[0];
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
        int targetY = pointerYForDraggedCenter(row0Before, handle0, centerY(rowAt(viewport, 2)) + 1);

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

    /** 同帧 MOVE→UP 必须提交最后坐标对应的完整顺序，不得回读旧 preview signal。 */
    @Test
    public void sameFrameMoveAndUpCommitsFinalOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int moveY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 1)) + 1);
        int upY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        harness.pressAt(x, centerY(handle));
        routeMoveAndTerminalSameFrame(ScenePointerAction.BUTTON_UP, x, moveY, upY);

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"), fontNames(viewport));
        Assert.assertTrue(adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 筛选后的首次 MOVE→UP 必须持续使用同步冻结的 visible 投影。 */
    @Test
    public void filteredSameFrameMoveAndUpCommitsVisibleOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Hidden A\n    - Visible A\n    - Hidden B\n    - Visible B\n",
                Arrays.asList("Hidden A", "Visible A", "Hidden B", "Visible B"));
        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);
        harness.typeText("visible");
        SceneNode viewport = findViewport(card);
        Assert.assertEquals(Arrays.asList("Visible A", "Visible B"), fontNames(viewport));
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 1)) + 1);

        harness.pressAt(x, centerY(handle));
        routeMoveAndTerminalSameFrame(ScenePointerAction.BUTTON_UP, x, targetY, targetY);

        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Visible B", "Visible A"), fontNames(viewport));
        Assert.assertTrue(adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 索引输入失焦与完整拖拽同帧时，未编辑的旧索引文本不得在 flush 后撤销拖拽。 */
    @Test
    public void sameFrameIndexBlurAndDragShouldKeepDragOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode indexInput = draggedRow.__getChildren().get(1);
        harness.click(indexInput);
        Assert.assertSame(indexInput, runtime.getFocusedNode());
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int startY = centerY(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        routeDownMoveAndUpSameFrame(x, startY, targetY);

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"), fontNames(viewport));
    }

    /** 编辑索引后同帧开始完整拖拽，手势必须从 FOCUS_LOST 产生的即时新顺序起步。 */
    @Test
    public void sameFrameEditedIndexBlurAndDragUsesImmediateOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n    - Font D\n",
                Arrays.asList("Font A", "Font B", "Font C", "Font D"));
        SceneNode viewport = findViewport(card);
        SceneNode secondIndex = rowAt(viewport, 1).__getChildren().get(1);
        harness.click(secondIndex);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("4");

        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);
        routeDownMoveAndUpSameFrame(x, centerY(handle), targetY);

        Assert.assertEquals("拖拽必须从索引 blur 后的 [A,C,D,B] 起步",
                Arrays.asList("Font C", "Font D", "Font A", "Font B"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** 被编辑行自身在 DOWN 失焦后换位时，旧几何不得把索引提交拖回原位。 */
    @Test
    public void sameFrameEditedDraggedRowBlurDoesNotUndoIndexMove() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n    - Font D\n",
                Arrays.asList("Font A", "Font B", "Font C", "Font D"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode indexInput = draggedRow.__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("4");
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        routeDownMoveAndUpSameFrame(x, centerY(handle), targetY);

        Assert.assertEquals("索引失焦提交不得被旧 keyed geometry 撤销",
                Arrays.asList("Font B", "Font C", "Font D", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font D", "Font A"), fontNames(viewport));
        Assert.assertSame("索引换位后仍复用原 row 节点", draggedRow, rowAt(viewport, 3));
        Assert.assertTrue(adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** reset 已同步改写 DraftBuffer 时，尚未 flush 的 signal 不得让旧索引 blur 覆盖默认值。 */
    @Test
    public void pendingResetBeforeIndexBlurWinsOverIndexEdit() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        adapter.resetFieldToDefault("fontSystem.fontSort");
        Assert.assertEquals("用例必须覆盖 DraftBuffer 领先于 draft signal 的窗口",
                Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);

        Assert.assertEquals("索引 blur 不得覆盖同步 reset 真值",
                new ArrayList<String>(), draft.getDraft("fontSystem.fontSort"));
        Assert.assertEquals(new ArrayList<String>(), adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals("空默认值仍按 discovered snapshot 展示",
                Arrays.asList("Font A", "Font B", "Font C"), fontNames(viewport));
    }

    /** reset 已同步写 DraftBuffer 但 signal 未 flush 时，Enter 也不得覆盖外部 authority。 */
    @Test
    public void pendingResetBeforeEnterWinsOverIndexEdit() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        adapter.resetFieldToDefault("fontSystem.fontSort");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);

        Assert.assertEquals("Enter 不得覆盖尚未 flush 的 reset",
                new ArrayList<String>(), draft.getDraft("fontSystem.fontSort"));
        Assert.assertEquals(new ArrayList<String>(), adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"), fontNames(viewport));
    }

    /** row Owner 卸载时必须在 handler/focusable cleanup 前提交仍聚焦的索引编辑。 */
    @Test
    public void ownerCleanupCommitsFocusedIndexBeforeHandlersDetach() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode indexInput = rowAt(findViewport(card), 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"),
                draft.getDraft("fontSystem.fontSort"));

        renderOwner.dispose();

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                draft.getDraft("fontSystem.fontSort"));
    }

    /** 同帧点击保存目标时，FOCUS_LOST 必须先提交索引，使保存包含最新编辑。 */
    @Test
    public void sameFrameIndexBlurBeforeSavePersistsLatestEdit() throws Exception {
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("fontSort").label("字体排序").helper("按优先级拖拽排序").build()
                    .string("other").defaultValue("original").label("其他字段").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.fontSort");
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        adapter.onFieldEdit("fontSystem.other", "changed");
        settle();
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        Assert.assertFalse("失焦前 fontSort 编辑仍只在输入框", draft.isDirty("fontSystem.fontSort"));
        Assert.assertTrue("其他字段使保存操作可执行", draft.isDirty("fontSystem.other"));
        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        final boolean[] saveSucceeded = {false};
        runtime.on(filterInput, club.heiqi.uilib.ui.scene.input.SceneEventType.CLICK, (event, context) -> {
            saveSucceeded[0] = manager.save(draft).isSuccess();
            if (saveSucceeded[0]) {
                adapter.afterSaveSync();
            }
        });
        harness.click(filterInput);

        Assert.assertTrue("测试保存必须成功", saveSucceeded[0]);
        Assert.assertEquals("保存必须包含同一 CLICK 前同步失焦提交的索引编辑",
                Arrays.asList("Font B", "Font C", "Font A"), draft.getCurrent("fontSystem.fontSort"));
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("保存后全局应保持 clean", draft.isDirtyAny());
    }

    /** 同帧点击恢复默认目标时，先提交的索引 blur 必须再被 restore 覆盖。 */
    @Test
    public void sameFrameRestoreAfterIndexBlurWinsOverIndexEdit() throws Exception {
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("fontSort")
                        .defaultValue(new ArrayList<String>(Arrays.asList("Font A", "Font B", "Font C")))
                        .label("字体排序").helper("按优先级拖拽排序").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.fontSort");
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        runtime.on(filterInput, club.heiqi.uilib.ui.scene.input.SceneEventType.CLICK,
                (event, context) -> adapter.resetFieldToDefault("fontSystem.fontSort"));
        harness.click(filterInput);

        Assert.assertEquals("restore 必须覆盖同一 CLICK 前同步提交的索引编辑",
                Arrays.asList("Font A", "Font B", "Font C"), draft.getDraft("fontSystem.fontSort"));
        Assert.assertFalse(draft.isDirty("fontSystem.fontSort"));
    }

    /** 外部 DraftBuffer 已同步更新但 draft signal 尚未 flush 时，UP 不得用旧拖拽快照覆盖它。 */
    @Test
    public void pendingExternalDraftBeforeUpWinsOverStaleDrag() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        harness.pressAt(x, centerY(handle));
        harness.moveAt(x, targetY);
        adapter.onFieldEdit("fontSystem.fontSort", Arrays.asList("Font C", "Font A", "Font B"));
        Assert.assertEquals("signal 尚未 flush，用例必须覆盖 DraftBuffer 领先于绑定 effect 的窗口",
                Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());

        harness.releaseAt(x, targetY);

        Assert.assertEquals("外部 draft 真值不得被旧拖拽结果覆盖",
                Arrays.asList("Font C", "Font A", "Font B"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font C", "Font A", "Font B"), fontNames(viewport));
    }

    /** 外部 DraftBuffer 在激活前领先于 signal 时，同帧 MOVE→UP 也必须采用外部真值。 */
    @Test
    public void pendingExternalDraftBeforeActivationWinsInSameFrameDrop() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        harness.pressAt(x, centerY(handle));
        adapter.onFieldEdit("fontSystem.fontSort", Arrays.asList("Font C", "Font A", "Font B"));
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        routeMoveAndTerminalSameFrame(ScenePointerAction.BUTTON_UP, x, targetY, targetY);

        Assert.assertEquals("激活前未 flush Draft 不得被旧 visible order 覆盖",
                Arrays.asList("Font C", "Font A", "Font B"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertEquals(Arrays.asList("Font C", "Font A", "Font B"), fontNames(viewport));
    }

    /** 同帧 MOVE→CANCEL 必须覆盖待 flush 预览并保持 draft 干净。 */
    @Test
    public void sameFrameMoveAndCancelRestoresStartOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode draggedRow = rowAt(viewport, 0);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);

        harness.pressAt(x, centerY(handle));
        routeMoveAndTerminalSameFrame(ScenePointerAction.CANCEL, x, targetY, targetY);

        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"), fontNames(viewport));
        Assert.assertEquals(Arrays.asList("Font A", "Font B", "Font C"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse(adapter.dirtySignal("fontSystem.fontSort").get());
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
        int awayY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 2)) + 1);
        harness.moveAt(x, awayY);
        Assert.assertEquals("换位后先形成预览", Arrays.asList("Font B", "Font C", "Font A"),
                fontNames(viewport));
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);

        int backY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(viewport, 0)) - 1);
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
        int targetY = pointerYForDraggedCenter(row0, handle, centerY(rowAt(viewport, 2)) + 1);

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

    /** 从一个已编辑索引直接切到另一个时，新焦点必须基于前一次 blur 后的即时顺序。 */
    @Test
    public void consecutiveIndexFocusUsesPreviousBlurOrder() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode firstIndex = rowAt(viewport, 0).__getChildren().get(1);
        SceneNode secondIndex = rowAt(viewport, 1).__getChildren().get(1);
        harness.click(firstIndex);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        harness.click(secondIndex);
        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"), fontNames(viewport));
        Assert.assertEquals("新焦点索引应立即按前一次 blur 后的全量顺序校准", "1", inputText(secondIndex));

        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("2");
        SceneNode filterInput = findControlRoot(card).__getChildren().get(0).__getChildren().get(0);
        harness.click(filterInput);

        Assert.assertEquals("第二次 blur 编辑不得因旧 focus 快照而丢失",
                Arrays.asList("Font C", "Font B", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** 点击聚焦与 TEXT_INPUT 同帧到达时，延迟焦点校准不得覆盖首个用户字符。 */
    @Test
    public void sameFrameFocusAndTextKeepsFirstEdit() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode indexInput = rowAt(findViewport(card), 0).__getChildren().get(1);

        routeClickAndTextSameFrame(indexInput, "3");

        Assert.assertTrue("同帧首个字符不得被 canonical index 写回覆盖", inputText(indexInput).contains("3"));
        Assert.assertSame(indexInput, runtime.getFocusedNode());
    }

    /** 同帧 TEXT_INPUT 先于 Enter 派发时，Enter 必须读取事件帧即时文本而非旧 signal。 */
    @Test
    public void sameFrameTextAndEnterCommitsLatestText() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode indexInput = rowAt(findViewport(card), 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);

        routeTextAndKeySameFrame("3", club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** Enter 提交后保持焦点继续编辑，后续 blur 必须使用刷新后的 authority 基线。 */
    @Test
    public void editAfterEnterCommitsAgainOnBlur() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode indexInput = rowAt(viewport, 0).__getChildren().get(1);
        harness.click(indexInput);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("2");
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER);
        Assert.assertEquals(Arrays.asList("Font B", "Font A", "Font C"), fontNames(viewport));

        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");
        harness.click(findControlRoot(card).__getChildren().get(0).__getChildren().get(0));

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
    }

    /** focus true→false 同帧合并时，同步 FOCUS_LOST 仍必须提交文本并清理本地编辑态。 */
    @Test
    public void sameFrameFocusTextAndTabCommitsOnSynchronousFocusLost() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode indexInput = rowAt(findViewport(card), 0).__getChildren().get(1);

        routeClickTextAndKeySameFrame(
                indexInput, "3", club.heiqi.uilib.ui.scene.input.SceneKey.TAB);

        Assert.assertEquals(Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertNotSame("Tab 应把焦点移出原索引", indexInput, runtime.getFocusedNode());
    }

    /** 前一 blur 改变目标行 canonical index 时，同帧文本编辑必须读取即时值而非旧 signal。 */
    @Test
    public void sameFrameChangedCanonicalFocusAndTextUsesImmediateValue() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n",
                Arrays.asList("Font A", "Font B", "Font C"));
        SceneNode viewport = findViewport(card);
        SceneNode firstIndex = rowAt(viewport, 0).__getChildren().get(1);
        SceneNode secondIndex = rowAt(viewport, 1).__getChildren().get(1);
        harness.click(firstIndex);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.END);
        harness.pressKey(club.heiqi.uilib.ui.scene.input.SceneKey.BACKSPACE);
        harness.typeText("3");

        routeClickAtLeftTextAndEnterSameFrame(secondIndex, "0");

        Assert.assertEquals("B 的即时 canonical 是 1，插入 0 后仍应解析为位置 1",
                Arrays.asList("Font B", "Font C", "Font A"),
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

    // 白盒回退（精确同帧事件序列）：覆盖 pointer 聚焦后同帧 TEXT_INPUT 的生产路由顺序
    private void routeClickAndTextSameFrame(SceneNode node, String text) {
        int x = centerX(node);
        int y = centerY(node);
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        fb.push(RawInputEvent.ofText(text, 1002L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // 白盒回退（精确同帧事件序列）：TEXT_INPUT 与 KEY_DOWN 必须在同一 route 内验证即时文本
    private void routeTextAndKeySameFrame(String text, club.heiqi.uilib.ui.scene.input.SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        fb.push(RawInputEvent.ofKey(key, club.heiqi.uilib.ui.scene.input.SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1001L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // 白盒回退（精确同帧事件序列）：覆盖 focused signal 净值不变时的同步 FOCUS_LOST
    private void routeClickTextAndKeySameFrame(SceneNode node, String text,
                                                club.heiqi.uilib.ui.scene.input.SceneKey key) {
        int x = centerX(node);
        int y = centerY(node);
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        fb.push(RawInputEvent.ofText(text, 1002L));
        fb.push(RawInputEvent.ofKey(key, club.heiqi.uilib.ui.scene.input.SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1003L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // 白盒回退（精确同帧事件序列）：左侧 caret + canonical 改写 + TEXT_INPUT→Enter
    private void routeClickAtLeftTextAndEnterSameFrame(SceneNode node, String text) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + 1;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        fb.push(RawInputEvent.ofText(text, 1002L));
        fb.push(RawInputEvent.ofKey(club.heiqi.uilib.ui.scene.input.SceneKey.ENTER,
                club.heiqi.uilib.ui.scene.input.SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1003L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // 白盒回退（精确输入帧时序）：单帧投递 MOVE→UP/CANCEL，覆盖 preview signal 未 flush 路径
    private void routeMoveAndTerminalSameFrame(ScenePointerAction terminal, int x, int moveY, int terminalY) {
        InputFrameBuilder fb = new InputFrameBuilder(x, moveY);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, moveY, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(terminal, x, terminalY, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // 白盒回退（精确输入帧时序）：DOWN 失焦与 MOVE→UP 均在一次 flush 前完成
    private void routeDownMoveAndUpSameFrame(int x, int downY, int targetY) {
        InputFrameBuilder fb = new InputFrameBuilder(x, downY);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, downY, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, targetY, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, targetY, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1002L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
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
