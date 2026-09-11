package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.io.FileWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.Values;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.StructuredListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 折叠态零成本守卫（ADR §4.1 R-11 / §7 A-15）。
 *
 * <p>契约：对象组行的成员编辑器构建必须在 {@code rt.show(root, expanded, supplier)} 的
 * <b>supplier 内部</b>发生 —— supplier 只在展开时被调用，且其构建期登记的 signal/effect 归属
 * show 的挂载子 Owner，折叠即随 owner dispose 回收。</p>
 *
 * <p>反例（P4 前形态）：先循环 {@code buildMember(...)} 建好节点、再把节点列表交给 supplier，
 * 惰性通道形同虚设 —— 折叠行同样构建全部成员控件（含 picker 面板与成员订阅），折叠只隐藏节点、
 * 不释放订阅。本类的 effect 计数断言即该形态的变异检查点。</p>
 */
public class StructuredListFieldRendererLazyMemberTest {

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private DraftSignalAdapter adapter;
    private SceneNode sceneRoot;
    private MountHandle mountHandle;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        if (adapter != null) adapter.dispose();
        if (mountHandle != null) mountHandle.dispose();
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 初始：row0 默认展开、row1 折叠。折叠 row0 必须释放其成员订阅（计数下降），
     * 展开 row1 后计数回到「恰一行展开」的水平；反复开合不得累积。
     */
    @Test
    public void collapsedRowBuildsNoMemberEditorsAndFoldReleasesSubscriptions() throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build().endSection().build();
        File file = File.createTempFile("structured-list-lazy-member-", ".yaml");
        write(file, "general:\n  rules:\n    - id: first\n      members:\n        - alpha\n"
                + "    - id: second\n      members:\n        - beta\n");
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        mountHandle = runtime.mount(sceneRoot,
                () -> new StructuredListFieldRenderer().render(runtime, schema.field("general.rules"), adapter));
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);

        SceneNode card = mountHandle.getRoot();
        SceneNode firstRow = rowAt(card, 0);
        SceneNode secondRow = rowAt(card, 1);
        Assert.assertTrue("前置：row0 默认展开，成员表单已挂载", containsText(firstRow, "members"));
        Assert.assertFalse("前置：row1 默认折叠，成员表单不得挂载", containsText(secondRow, "members"));
        final int oneRowExpanded = ReactiveTestProbe.registeredEffectCount();

        clickAfterLayout(findButton(firstRow, "展开"));
        Assert.assertFalse("折叠后成员表单必须整体消失", containsText(firstRow, "members"));
        int allCollapsed = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("折叠必须释放成员编辑器订阅（R-11：成员在 supplier 内构建）："
                        + allCollapsed + " !< " + oneRowExpanded,
                allCollapsed < oneRowExpanded);

        clickAfterLayout(findButton(secondRow, "展开"));
        Assert.assertTrue("展开 row1 后成员表单在场", containsText(secondRow, "members"));
        int secondRowExpanded = ReactiveTestProbe.registeredEffectCount();
        Assert.assertEquals("折叠行不得预留订阅：单行展开的订阅数必须与初始一致",
                oneRowExpanded, secondRowExpanded);

        // 反复开合：每次折叠回到全折叠基线、每次展开回到单行展开基线（无累积）。
        for (int cycle = 0; cycle < 3; cycle++) {
            clickAfterLayout(findButton(secondRow, "展开"));
            Assert.assertEquals("第 " + cycle + " 次折叠：订阅回到全折叠基线",
                    allCollapsed, ReactiveTestProbe.registeredEffectCount());
            clickAfterLayout(findButton(secondRow, "展开"));
            Assert.assertEquals("第 " + cycle + " 次展开：订阅回到单行展开基线",
                    secondRowExpanded, ReactiveTestProbe.registeredEffectCount());
        }
    }

    // ==================== 助手 ====================

    /** 宿主逐帧语义：先重新布局让命中盒刷新，再注入点击（否则布局变化后坐标会指向旧位置）。 */
    private void clickAfterLayout(SceneNode node) {
        harness.mountRoot(sceneRoot, 640, 420);
        harness.click(node);
    }

    private static SceneNode rowAt(SceneNode card, int index) {
        return findScrollable(card).__getChildren().get(index);
    }

    /** 行子树是否挂载了成员表单（成员 label 文本只在 supplier 内构建，故其存在 = 已构建）。 */
    private static boolean containsText(SceneNode node, String text) {
        if (text.equals(node.getText())) return true;
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, text)) return true;
        }
        return false;
    }

    private static SceneNode findButton(SceneNode node, String text) {
        if (!node.__getChildren().isEmpty() && text.equals(node.__getChildren().get(0).getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findButton(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static SceneNode findScrollable(SceneNode node) {
        SceneNode found = findScrollableOrNull(node);
        if (found == null) throw new AssertionError("未找到结构化列表滚动视口");
        return found;
    }

    private static SceneNode findScrollableOrNull(SceneNode node) {
        if (node.isScrollable()) return node;
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollableOrNull(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void write(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
