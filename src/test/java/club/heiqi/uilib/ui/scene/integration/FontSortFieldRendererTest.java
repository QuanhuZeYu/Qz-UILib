package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FontSortFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * {@link FontSortFieldRenderer} 端到端测试。
 */
public class FontSortFieldRendererTest {

    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 360;
    private static final int EXPECTED_LIST_HEIGHT = 220;
    private static final int ROW_CARD_HEIGHT = 36;
    private static final int ROW_CARD_BG_IDLE = 0xFF152238;
    private static final int ROW_CARD_BG_HOVER = 0xFF1E2E4A;
    private static final int ROW_CARD_BORDER = 0xFF2F4D87;

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private ConfigSchema schema;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FieldRenderer renderer;
    private SceneNode sceneRoot;
    private SceneNode card;
    private FieldSpec spec;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("fontSort").label("字体排序").helper("按优先级拖拽排序").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.fontSort");
        renderer = new FontSortFieldRenderer(() -> new ArrayList<String>(Arrays.asList("Font A", "Font B")));
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() throws Exception {
        if (adapter != null) {
            adapter.dispose();
        }
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    private void mountWithInitial(String yaml) throws Exception {
        File file = File.createTempFile("fontsort-renderer-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        card = renderer.render(runtime, spec, adapter);
        SceneNode controlRoot = findControlRoot(card);
        Assert.assertNotNull("card 内应找到 fontSort 控件根", controlRoot);
        Assert.assertEquals("fontSort 控件根应自带 listHeight 高度",
                EXPECTED_LIST_HEIGHT, controlRoot.getPreferredHeight());
        sceneRoot.appendChild(card);
        settle();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    /** 空 draft 应预填发现字体，且只渲染文本行，不渲染输入框或添加按钮。 */
    @Test
    public void prefillRendersReadOnlyFontRowsWithoutInputOrAddButton() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort: []\n");

        SceneNode viewport = findViewport(card);
        Assert.assertEquals("预填充应渲染 2 行", 2, viewport.__getChildren().size());
        Assert.assertEquals("首行字体名", "Font A", fontName(rowAt(viewport, 0)));
        Assert.assertEquals("末行字体名", "Font B", fontName(rowAt(viewport, 1)));
        Assert.assertEquals("fontSort 行结构应为 [handle, text]", 2,
                rowAt(viewport, 0).__getChildren().size());
        Assert.assertFalse("fontSort 不应渲染添加按钮", containsText(card, "添加"));

        Assert.assertEquals("draft 镜像 = prefill", Arrays.asList("Font A", "Font B"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertFalse("预填充后 dirty==false", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    /** 字体行应是卡片式只读行，hover 只切换行背景。 */
    @Test
    public void fontRowsUseCardChromeAndHoverBackground() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n");
        SceneNode viewport = findViewport(card);
        SceneNode row = rowAt(viewport, 0);

        Assert.assertEquals("fontSort 行结构应保持 [handle, text]", 2, row.__getChildren().size());
        Assert.assertEquals("fontSort 行卡片 idle 背景", ROW_CARD_BG_IDLE, row.getBackgroundColor());
        Assert.assertEquals("fontSort 行卡片边框宽度", 1, row.getBorderWidth());
        Assert.assertEquals("fontSort 行卡片边框色", ROW_CARD_BORDER, row.getBorderColor());
        Assert.assertEquals("fontSort 行卡片圆角", SceneChromeTokens.RADIUS_MD, row.getCornerRadius());
        Assert.assertEquals("fontSort 行卡片高度", ROW_CARD_HEIGHT, row.getPreferredHeight());
        Assert.assertEquals("fontSort 行卡片左内边距", SceneChromeTokens.PAD_MD, row.getPaddingLeft());
        Assert.assertEquals("fontSort 行卡片右内边距", SceneChromeTokens.PAD_MD, row.getPaddingRight());

        harness.moveAt(centerX(row), centerY(row));
        Assert.assertEquals("hover 进入行后切换卡片背景", ROW_CARD_BG_HOVER, row.getBackgroundColor());

        harness.moveAt(CANVAS_WIDTH - 2, CANVAS_HEIGHT - 2);
        Assert.assertEquals("hover 移出行后恢复 idle 背景", ROW_CARD_BG_IDLE, row.getBackgroundColor());
    }

    /** hover 命中拖拽把手时，行卡片也应随把手交互态高亮。 */
    @Test
    public void hoverDragHandleHighlightsRowCardAndRestoresOnExit() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n");
        SceneNode viewport = findViewport(card);
        SceneNode row = rowAt(viewport, 0);
        SceneNode handle = dragHandle(row);

        Assert.assertEquals("把手 hover 前行卡片为 idle 背景", ROW_CARD_BG_IDLE, row.getBackgroundColor());

        harness.moveAt(centerX(handle), centerY(handle));
        Assert.assertEquals("把手自身 hover 背景仍生效", SceneChromeTokens.BG_HOVER, handle.getBackgroundColor());
        Assert.assertEquals("hover 命中把手时行卡片同步高亮", ROW_CARD_BG_HOVER, row.getBackgroundColor());

        harness.moveAt(CANVAS_WIDTH - 2, CANVAS_HEIGHT - 2);
        Assert.assertEquals("hover 移出把手后行卡片恢复 idle 背景", ROW_CARD_BG_IDLE, row.getBackgroundColor());
    }

    /** 拖拽排序应写回 draft，并保持只读行节点按 id 复用。 */
    @Test
    public void dragReordersFontNamesAndWritesDraft() throws Exception {
        mountWithInitial("fontSystem:\n  fontSort:\n    - Font A\n    - Font B\n    - Font C\n");
        SceneNode viewport = findViewport(card);

        SceneNode row0Before = rowAt(viewport, 0);
        SceneNode handle0 = dragHandle(row0Before);
        int hx = centerX(handle0);
        int targetY = pointerYForDraggedCenter(row0Before, handle0, bottomY(rowAt(viewport, 2)) + 1);

        harness.pressAt(hx, centerY(handle0));
        harness.moveAt(hx, targetY);
        harness.releaseAt(hx, targetY);

        Assert.assertEquals("拖拽 row0→row2 后写回 draft",
                Arrays.asList("Font B", "Font C", "Font A"),
                adapter.draftSignal("fontSystem.fontSort").get());
        Assert.assertSame("被拖行节点应移动复用", row0Before, rowAt(viewport, 2));
        Assert.assertTrue("拖拽后 dirty==true", adapter.dirtySignal("fontSystem.fontSort").get());
    }

    private static SceneNode findControlRoot(SceneNode node) {
        SceneNode viewport = findScrollable(node);
        if (viewport == null) {
            for (SceneNode child : node.__getChildren()) {
                SceneNode found = findControlRoot(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        return viewport.__getParent().__getParent();
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

    private static SceneNode rowAt(SceneNode viewport, int index) {
        return viewport.__getChildren().get(index);
    }

    private static SceneNode dragHandle(SceneNode row) {
        return row.__getChildren().get(0);
    }

    private static String fontName(SceneNode row) {
        return row.__getChildren().get(1).getText();
    }

    private static int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    private static int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
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

    private static void write(File file, String content) throws Exception {
        java.io.FileWriter w = new java.io.FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
