package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.CharacterRuleFieldRenderer;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * {@link CharacterRuleFieldRenderer} 端到端 integration 测试（L3：runtime + signal + input）。
 *
 * <p>覆盖装配结构（三栏 + 添加按钮）、I5 keyed 复用（编辑第 2 行 selector 时第 1/3 行节点不重建）、
 * D2 reset 守卫（控件自写回投影相等跳过重建）、add/delete 行为、无效行错误文本透出。</p>
 *
 * <h3>结构探针</h3>
 * <pre>
 * card (FormFieldShell column)
 *   ├ header / helper
 *   ├ controlRoot (column) ← mount slot，本 renderer 产出
 *   │   ├ listViewport (column, scrollable=true)
 *   │   │   └ rowN (column) ← keyed diff 单元
 *   │   │       ├ line (row): [checkbox, selectorInput, fontNameInput, deleteButton]
 *   │   │       └ errorNode（条件渲染，parse 错误时显示）
 *   │   └ addButton
 *   └ errorNode (FormFieldShell 字段级错误)
 * </pre>
 */
public class CharacterRuleFieldRendererIntegrationTest {

    private static final int CANVAS_WIDTH = 600;
    private static final int CANVAS_HEIGHT = 400;

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
                    .simpleList("characterFontRules").label("字符字体规则").helper("每行一条").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.characterFontRules");
        renderer = new CharacterRuleFieldRenderer();
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

    /**
     * 用初值装配 field（YAML → Authority.load → DraftBuffer.from），render 后挂到 sceneRoot。
     */
    private void mountWithInitial(String yaml) throws Exception {
        File file = File.createTempFile("characterrule-renderer-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        card = renderer.render(runtime, spec, adapter);
        sceneRoot.appendChild(card);
        settle();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /**
     * 多次 flush 让响应式收敛到不动点。
     */
    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    // ==================== 装配结构 ====================

    /**
     * 初值 3 条规则 → viewport 渲染 3 行 + 添加按钮。
     */
    @Test
    public void initialDraftRendersRowsAndAddButton() throws Exception {
        mountWithInitial("fontSystem:\n  characterFontRules:\n    - a=FontA\n    - b-z=FontB\n    - U+0041=FontC\n");

        SceneNode viewport = findViewport(card);
        Assert.assertNotNull("card 内应找到滚动视口", viewport);
        Assert.assertEquals("初始应渲染 3 行", 3, viewport.__getChildren().size());
        Assert.assertNotNull("应渲染添加按钮", findAddButton(findControlRoot(card)));
    }

    // ==================== I5 keyed 复用 + D2 回环守卫 ====================

    /**
     * typeText 改第 2 行 selector → onFieldEdit 收到含新 selector 的列表；
     * 第 1/3 行节点未重建（I5 keyed）；第 2 行 selectorInput 节点也未重建（D2 回环守卫）。
     */
    @Test
    public void editSelectorWritesBackAndKeepsOtherRowsStable() throws Exception {
        mountWithInitial("fontSystem:\n  characterFontRules:\n    - a=FontA\n    - b=FontB\n    - c=FontC\n");
        SceneNode viewport = findViewport(card);

        SceneNode row0Before = rowAt(viewport, 0);
        SceneNode row2Before = rowAt(viewport, 2);
        SceneNode selectorInput1 = selectorInput(rowAt(viewport, 1));

        harness.click(selectorInput1);
        settle();
        harness.typeText("X");
        settle();

        // draft 写回 List<String>（D7 唯一翻译点）：第 2 行 selector 由 "b" → "bX"
        Object draftValue = adapter.draftSignal("fontSystem.characterFontRules").get();
        Assert.assertTrue("draft 值应为 List", draftValue instanceof List);
        Assert.assertEquals("编辑第 2 行 selector 写回",
                Arrays.asList("a=FontA", "bX=FontB", "c=FontC"), draftValue);

        // I5 keyed 复用：第 1/3 行节点未重建
        Assert.assertSame("第 1 行节点复用未重建", row0Before, rowAt(viewport, 0));
        Assert.assertSame("第 3 行节点复用未重建", row2Before, rowAt(viewport, 2));
        // D2 回环守卫：编辑后第 2 行 selector 输入节点也未重建
        Assert.assertSame("第 2 行 selector 输入节点复用未重建",
                selectorInput1, selectorInput(rowAt(viewport, 1)));
    }

    // ==================== add 行为 ====================

    /**
     * click add → draft 增空串行；其余行节点复用。
     */
    @Test
    public void addAppendsEmptyRowAndKeepsExistingRowsStable() throws Exception {
        mountWithInitial("fontSystem:\n  characterFontRules:\n    - a=FontA\n    - b=FontB\n");
        SceneNode viewport = findViewport(card);

        SceneNode row0Before = rowAt(viewport, 0);
        SceneNode row1Before = rowAt(viewport, 1);

        SceneNode controlRoot = findControlRoot(card);
        harness.click(findAddButton(controlRoot));
        settle();

        Assert.assertEquals("add 后 draft 增空串行",
                Arrays.asList("a=FontA", "b=FontB", ""),
                adapter.draftSignal("fontSystem.characterFontRules").get());
        Assert.assertEquals("viewport 渲染 3 行",
                3, viewport.__getChildren().size());
        Assert.assertSame("add 后原第 1 行节点复用", row0Before, rowAt(viewport, 0));
        Assert.assertSame("add 后原第 2 行节点复用", row1Before, rowAt(viewport, 1));
    }

    // ==================== delete 行为 ====================

    /**
     * click delete 第 2 行 → draft 删对应项；其余行节点复用（id 稳定）。
     */
    @Test
    public void deleteRemovesRowAndKeepsOthersStable() throws Exception {
        mountWithInitial("fontSystem:\n  characterFontRules:\n    - a=FontA\n    - b=FontB\n    - c=FontC\n");
        SceneNode viewport = findViewport(card);

        SceneNode row0Before = rowAt(viewport, 0);
        SceneNode row2Before = rowAt(viewport, 2);

        harness.click(deleteButton(rowAt(viewport, 1)));
        settle();

        Assert.assertEquals("删除第 2 行后 draft 收缩",
                Arrays.asList("a=FontA", "c=FontC"),
                adapter.draftSignal("fontSystem.characterFontRules").get());
        Assert.assertEquals("viewport 渲染 2 行",
                2, viewport.__getChildren().size());
        Assert.assertSame("删除后原第 1 行节点复用（id 稳定）", row0Before, rowAt(viewport, 0));
        Assert.assertSame("删除后原第 3 行节点复用（id 稳定）", row2Before, rowAt(viewport, 1));
    }

    // ==================== D2 reset 守卫 ====================

    /**
     * adapter.resetFieldToDefault → 列表视图跟随 draft（投影不等 → 重建）。
     */
    @Test
    public void resetFieldToDefaultRebuildsListFromDefault() throws Exception {
        mountWithInitial("fontSystem:\n  characterFontRules:\n    - a=FontA\n    - b=FontB\n");
        SceneNode viewport = findViewport(card);
        Assert.assertEquals("reset 前渲染 2 行", 2, viewport.__getChildren().size());

        adapter.resetFieldToDefault("fontSystem.characterFontRules");
        settle();

        Object draftValue = adapter.draftSignal("fontSystem.characterFontRules").get();
        Assert.assertTrue("reset 后 draft 值为 List", draftValue instanceof List);
        Assert.assertEquals("reset 后 draft 为空 list（schema 默认）", 0, ((List<?>) draftValue).size());
        Assert.assertEquals("reset 后列表视图跟随 draft（清空）",
                0, viewport.__getChildren().size());
    }

    // ==================== 结构探针 ====================

    /**
     * 找控件根（FormFieldShell mount 槽挂载的 controlFn 产出）：含滚动视口的列。
     */
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
        return viewport.__getParent();
    }

    private static SceneNode findViewport(SceneNode card) {
        return findScrollable(card);
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

    private static SceneNode findAddButton(SceneNode controlRoot) {
        for (SceneNode child : controlRoot.__getChildren()) {
            if (!child.__getChildren().isEmpty()
                    && "+ 添加规则".equals(child.__getChildren().get(0).getText())) {
                return child;
            }
        }
        throw new AssertionError("未找到添加按钮");
    }

    private static SceneNode rowAt(SceneNode viewport, int index) {
        return viewport.__getChildren().get(index);
    }

    /**
     * 取行内 line（row）：rowRoot 的首个子节点（行根是 column，第一子为 line row）。
     */
    private static SceneNode lineOf(SceneNode rowRoot) {
        return rowRoot.__getChildren().get(0);
    }

    /**
     * 取行内 selector 输入框：line.children[1]（[checkbox, selectorInput, fontNameInput, deleteButton]）。
     */
    private static SceneNode selectorInput(SceneNode rowRoot) {
        return lineOf(rowRoot).__getChildren().get(1);
    }

    /**
     * 取行内 delete 按钮：line.children[3]。
     */
    private static SceneNode deleteButton(SceneNode rowRoot) {
        return lineOf(rowRoot).__getChildren().get(3);
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
