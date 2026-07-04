package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.util.ArrayList;
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
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.SimpleListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * {@link SimpleListFieldRenderer} 端到端 integration 测试（L3：runtime + signal + input）。
 *
 * <p>覆盖装配结构、编辑写回 I5 keyed 复用（D2 回环守卫）、增删节点稳定、
 * 外部 reset 回流守卫（D2 reset 语义）。</p>
 *
 * <h3>测试搭台说明</h3>
 * <ul>
 *   <li>{@link club.heiqi.uilib.ui.scene.form.FormFieldShell} 按字段自带高度给控件根
 *       设 {@code preferredHeight}：单行字段传 inputHeight(30)，SIMPLE_LIST 多行字段传
 *       {@code theme.listHeight()=220}（"字段自带高度"五大框架共识）。
 *       本测试不再外部 hack 高度，而是断言 renderer 产出的控件根自身已具多行高度
 *       （preferredHeight == FormTheme.listHeight() == 220），验证真实生产路径。</li>
 *   <li>D2 外部 reset 守卫经 {@code rt.bind} effect 写 localItems；signal.set 入队后要下一轮
 *       flush 才应用。故 reset 后用 {@link #settle()} 多次 flush 让响应式收敛到不动点。</li>
 * </ul>
 *
 * <p>结构探针用递归查找定位 SimpleList 子树（与 SceneSimpleListTest 同款），不裸断坐标。</p>
 */
public class SimpleListFieldRendererTest {

    private static final int CANVAS_WIDTH = 420;
    private static final int CANVAS_HEIGHT = 320;
    /**
     * SIMPLE_LIST 多行字段表单壳应自带的高度：与 {@link FormTheme#listHeight()} 同源取值 220。
     * 本测试断言 renderer 产出的控件根 preferredHeight 等于此值，验证"字段自带高度"真实路径。
     */
    private static final int EXPECTED_LIST_HEIGHT = 220;

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
                .section("font")
                    .simpleList("sort").label("Sort").helper("排序").build()
                .endSection()
                .build();
        spec = schema.field("font.sort");
        renderer = new SimpleListFieldRenderer();
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
     * 用初值装配 field（写 YAML → Authority.load → DraftBuffer.from），
     * render 出 card 后挂到 sceneRoot，flush + mountRoot 让 box 就位。
     *
     * <p>同时断言 renderer 产出的控件根<b>自身</b>已具多行高度（preferredHeight == 220），
     * 验证 SimpleListFieldRenderer 走 FormFieldShell 新重载传 theme.listHeight() 的真实路径，
     * 不再依赖外部 setPreferredHeight hack。</p>
     */
    private void mountWithInitial(String yaml) throws Exception {
        File file = File.createTempFile("simplelist-renderer-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        card = renderer.render(runtime, spec, adapter);
        // 不再外部 hack 高度：断言控件根自身已具多行视口高度（"字段自带高度"真实路径）
        SceneNode controlRoot = findSimpleListRoot(card);
        Assert.assertNotNull("card 内应找到 SimpleList 控件根", controlRoot);
        Assert.assertEquals("SimpleList 控件根应自带 listHeight 高度（字段自带，非外部 hack）",
                EXPECTED_LIST_HEIGHT, controlRoot.getPreferredHeight());
        sceneRoot.appendChild(card);
        settle();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /**
     * 多次 flush 让响应式收敛到不动点。
     *
     * <p>reset 经 rt.bind effect 写 localItems，signal.set 入队后需下一轮 flush 应用；
     * 连续 flush 2 次覆盖「effect 入队 → 下轮 apply → reconcile」传导链。</p>
     */
    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    // ==================== B5. 装配结构 ====================

    /** 初值 3 行 → 渲染 3 行 + 添加按钮。 */
    @Test
    public void initialDraftRendersRowsAndAddButton() throws Exception {
        mountWithInitial("font:\n  sort:\n    - a\n    - b\n    - c\n");

        SceneNode simpleListRoot = findSimpleListRoot(card);
        Assert.assertNotNull("card 内应找到 SimpleList 控件根", simpleListRoot);
        Assert.assertEquals("初始应渲染 3 行", 3, listViewport(simpleListRoot).__getChildren().size());
        Assert.assertEquals("首行文本 a", "a", textInputValue(rowAt(simpleListRoot, 0)));
        Assert.assertEquals("末行文本 c", "c", textInputValue(rowAt(simpleListRoot, 2)));
        Assert.assertNotNull("应渲染添加按钮", addButton(simpleListRoot));
    }

    // ==================== B6. 编辑写回 + I5 keyed 复用（D2 回环守卫） ====================

    /** typeText 改第 2 行 → onFieldEdit 收到 ["a","bX","c"]，第 1/3 行节点复用未重建。 */
    @Test
    public void editRowWritesBackAndKeepsOtherRowsStable() throws Exception {
        mountWithInitial("font:\n  sort:\n    - a\n    - b\n    - c\n");
        SceneNode simpleListRoot = findSimpleListRoot(card);

        SceneNode row0Before = rowAt(simpleListRoot, 0);
        SceneNode row2Before = rowAt(simpleListRoot, 2);
        SceneNode input1 = textInput(rowAt(simpleListRoot, 1));

        harness.click(input1);
        settle();
        harness.typeText("X");
        settle();

        // draft 写回 List<String>（D7 唯一翻译点）
        Object draftValue = adapter.draftSignal("font.sort").get();
        Assert.assertTrue("draft 值应为 List", draftValue instanceof List);
        Assert.assertEquals("编辑第 2 行写回",
                Arrays.asList("a", "bX", "c"), draftValue);

        // I5 keyed 复用：第 1/3 行节点未重建
        Assert.assertSame("第 1 行节点复用未重建", row0Before, rowAt(simpleListRoot, 0));
        Assert.assertSame("第 3 行节点复用未重建", row2Before, rowAt(simpleListRoot, 2));
        // D2 回环守卫：编辑后第 2 行输入节点也未重建（投影相等 → 跳过重建）
        Assert.assertSame("第 2 行输入节点复用未重建", input1, textInput(rowAt(simpleListRoot, 1)));
    }

    // ==================== B7. 增删节点稳定（id 自治 → 节点复用） ====================

    /** click add → draft 增空行；其余行节点复用。 */
    @Test
    public void addAppendsEmptyRowAndKeepsExistingRowsStable() throws Exception {
        mountWithInitial("font:\n  sort:\n    - a\n    - b\n");
        SceneNode simpleListRoot = findSimpleListRoot(card);

        SceneNode row0Before = rowAt(simpleListRoot, 0);
        SceneNode row1Before = rowAt(simpleListRoot, 1);

        harness.click(addButton(simpleListRoot));
        settle();

        Assert.assertEquals("add 后 draft 增空行",
                Arrays.asList("a", "b", ""),
                adapter.draftSignal("font.sort").get());
        Assert.assertEquals("viewport 渲染 3 行",
                3, listViewport(simpleListRoot).__getChildren().size());
        Assert.assertSame("add 后原第 1 行节点复用", row0Before, rowAt(simpleListRoot, 0));
        Assert.assertSame("add 后原第 2 行节点复用", row1Before, rowAt(simpleListRoot, 1));
    }

    /** click delete 第 2 行 → draft 删对应项；其余行节点复用（id 稳定）。 */
    @Test
    public void deleteRemovesRowAndKeepsOthersStable() throws Exception {
        mountWithInitial("font:\n  sort:\n    - a\n    - b\n    - c\n");
        SceneNode simpleListRoot = findSimpleListRoot(card);

        SceneNode row0Before = rowAt(simpleListRoot, 0);
        SceneNode row2Before = rowAt(simpleListRoot, 2);

        harness.click(deleteButton(rowAt(simpleListRoot, 1)));
        settle();

        Assert.assertEquals("删除第 2 行后 draft 收缩",
                Arrays.asList("a", "c"),
                adapter.draftSignal("font.sort").get());
        Assert.assertEquals("viewport 渲染 2 行",
                2, listViewport(simpleListRoot).__getChildren().size());
        Assert.assertSame("删除后原第 1 行节点复用（id 稳定）", row0Before, rowAt(simpleListRoot, 0));
        Assert.assertSame("删除后原第 3 行节点复用（id 稳定）", row2Before, rowAt(simpleListRoot, 1));
    }

    // ==================== B8. reset 同步（D2 外部回流守卫） ====================

    /** adapter.resetFieldToDefault → 列表视图跟随 draft（投影不等 → 重建）。 */
    @Test
    public void resetFieldToDefaultRebuildsListFromDefault() throws Exception {
        // schema 默认空 list；current/draft 初值 2 行
        mountWithInitial("font:\n  sort:\n    - a\n    - b\n");
        SceneNode simpleListRoot = findSimpleListRoot(card);
        Assert.assertEquals("reset 前渲染 2 行",
                2, listViewport(simpleListRoot).__getChildren().size());

        adapter.resetFieldToDefault("font.sort");
        settle();

        // 默认值是空 list → 投影不等 → 重建为空列表
        Object draftValue = adapter.draftSignal("font.sort").get();
        Assert.assertTrue("reset 后 draft 值为 List", draftValue instanceof List);
        Assert.assertEquals("reset 后 draft 为空 list", 0, ((List<?>) draftValue).size());
        Assert.assertEquals("reset 后列表视图跟随 draft（清空）",
                0, listViewport(simpleListRoot).__getChildren().size());
    }

    /**
     * reset 到非空默认值也能正确重建（覆盖 reset 不是清空、而是换内容的语义）。
     */
    @Test
    public void resetFieldToNonEmptyDefaultRebuildsList() throws Exception {
        // 用带非空默认值的 schema
        ConfigSchema s = ConfigSchema.builder("t")
                .section("font")
                    .simpleList("sort").defaultValue(new ArrayList<String>(Arrays.asList("x", "y")))
                        .label("Sort").build()
                .endSection()
                .build();
        File file = File.createTempFile("simplelist-reset-nonempty-", ".yaml");
        write(file, "font:\n  sort:\n    - a\n    - b\n    - c\n");
        Authority authority = Authority.load(file, s);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        FieldSpec f = s.field("font.sort");
        card = renderer.render(runtime, f, adapter);
        // 不外部 hack：renderer 已通过 FormFieldShell 新重载传 theme.listHeight()，
        // 控件根自身具多行高度（真实生产路径）。
        sceneRoot.appendChild(card);
        settle();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);

        SceneNode simpleListRoot = findSimpleListRoot(card);
        Assert.assertEquals("reset 前渲染 3 行（current 初值）",
                3, listViewport(simpleListRoot).__getChildren().size());

        adapter.resetFieldToDefault("font.sort");
        settle();

        Object draftValue = adapter.draftSignal("font.sort").get();
        Assert.assertEquals("reset 后 draft 为默认 [x,y]", Arrays.asList("x", "y"), draftValue);
        Assert.assertEquals("reset 后列表视图重建为 2 行",
                2, listViewport(simpleListRoot).__getChildren().size());
        Assert.assertEquals("重建后首行 x", "x", textInputValue(rowAt(simpleListRoot, 0)));
    }

    // ==================== 结构探针（control/ 包局部工具镜像） ====================

    /**
     * 递归找 SimpleList 控件根：含可滚动视口子树的节点，向上回溯到含"添加"按钮的列根。
     */
    private static SceneNode findSimpleListRoot(SceneNode node) {
        SceneNode viewport = findScrollable(node);
        if (viewport == null) {
            for (SceneNode child : node.__getChildren()) {
                SceneNode found = findSimpleListRoot(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        // 向上回溯到含"添加"按钮的列根
        SceneNode cur = viewport;
        while (cur != null) {
            if (hasAddButton(cur)) {
                return cur;
            }
            cur = cur.__getParent();
        }
        return viewport.__getParent();
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

    private static boolean hasAddButton(SceneNode root) {
        for (SceneNode child : root.__getChildren()) {
            if (!child.__getChildren().isEmpty()
                    && "添加".equals(child.__getChildren().get(0).getText())) {
                return true;
            }
        }
        return false;
    }

    private static SceneNode listViewport(SceneNode simpleListRoot) {
        SceneNode found = findScrollable(simpleListRoot);
        if (found == null) {
            throw new AssertionError("未找到滚动列表视口");
        }
        return found;
    }

    private static SceneNode addButton(SceneNode simpleListRoot) {
        for (SceneNode child : simpleListRoot.__getChildren()) {
            if (!child.__getChildren().isEmpty()
                    && "添加".equals(child.__getChildren().get(0).getText())) {
                return child;
            }
        }
        throw new AssertionError("未找到添加按钮");
    }

    private static SceneNode rowAt(SceneNode simpleListRoot, int index) {
        return listViewport(simpleListRoot).__getChildren().get(index);
    }

    private static SceneNode textInput(SceneNode row) {
        return row.__getChildren().get(0);
    }

    private static SceneNode deleteButton(SceneNode row) {
        return row.__getChildren().get(1);
    }

    private static String textInputValue(SceneNode row) {
        SceneNode input = textInput(row);
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
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
