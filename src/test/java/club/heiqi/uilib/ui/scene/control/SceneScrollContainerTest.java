package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrollContainer 高阶工厂单元测试。
 *
 * <p>验证：建容器→挂内容超视口→layout→maxScrollY > 0；
 * scrollSignal set 后 scrollOffsetY 同步；无 scrollbarSpec 时不建 scrollbar。</p>
 */
public class SceneScrollContainerTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        // sceneRoot 设 fillParentHeight 使其从 Constraints 收到确定高并下传给 container（grow 子），
        // 否则 priorKnownInnerHeight(sceneRoot) 返回 UNCONSTRAINED → container 收不到确定高 → viewport 被内容撑大。
        sceneRoot.setFillParentHeight(true);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 无 scrollbarSpec 时：container 只有 1 个子（viewport），content 溢出时 maxScrollY > 0。
     */
    @Test
    public void noScrollbarSpecShouldBuildViewportAndContentOnly() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        // 挂超视口内容：5 个固定高 100 的子，总高 500 > 300
        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        Assert.assertTrue("container 只有 viewport 一个子（无 scrollbar）",
                r.container().__getChildren().size() == 1);
        Assert.assertSame("container 第一个子是 viewport",
                r.viewport(), r.container().__getChildren().get(0));

        LayoutBox vpBox = (LayoutBox) r.viewport().getCachedLayout();
        Assert.assertNotNull("viewport 已布局", vpBox);
        Assert.assertTrue("viewport 高度受约束（未被内容撑大）",
                vpBox.getHeight() > 0 && vpBox.getHeight() <= CANVAS_HEIGHT);

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("content 溢出时 maxScroll > 0", maxScroll > 0);
    }

    /**
     * scrollSignal set 后，经 bind 同步到 viewport.scrollOffsetY。
     */
    @Test
    public void scrollSignalSetShouldSyncToViewportOffset() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("前置：maxScroll > 0", maxScroll > 0);

        int target = Math.min(120, maxScroll);
        r.scrollSignal().set(Integer.valueOf(target));
        runtime.flush();

        Assert.assertEquals("scrollSignal set 后 viewport.scrollOffsetY 同步",
                target, r.viewport().getScrollOffsetY());
    }

    /**
     * 有 scrollbarSpec 时：container 有 2 个子（viewport + scrollbar column）。
     */
    @Test
    public void scrollbarSpecShouldBuildScrollbarColumn() {
        SceneScrollContainer.ScrollbarSpec sbSpec = new SceneScrollContainer.ScrollbarSpec(
                0x33FFFFFF, 0xFFFFFFFF, 8, 20);
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, sbSpec);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        Assert.assertEquals("有 scrollbarSpec 时 container 有 2 个子（viewport + scrollbar）",
                2, r.container().__getChildren().size());
        Assert.assertSame("container 第一个子是 viewport",
                r.viewport(), r.container().__getChildren().get(0));
        Assert.assertNotSame("container 第二个子是 scrollbar column（非 viewport）",
                r.viewport(), r.container().__getChildren().get(1));

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("有 scrollbar 时 content 仍溢出 maxScroll > 0", maxScroll > 0);
    }

    /**
     * viewport 应已设 scrollable=true（attach 防呆不抛异常即证明）。
     */
    @Test
    public void viewportShouldBeScrollable() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        Assert.assertTrue("viewport 应已设 scrollable=true", r.viewport().isScrollable());
    }

    // ==================== attach 门面测试 ====================

    /**
     * attach 一行门面：parent 应含 container 子，container 有 2 子（viewport + scrollbar column），
     * container flexGrow=1，content 已被装填回调填入条目。
     */
    @Test
    public void attachShouldCreateContainerWithViewportAndScrollbar() {
        final int itemCount = 5;
        Consumer<SceneNode> contentBuilder = content -> {
            for (int i = 0; i < itemCount; i++) {
                SceneNode child = new SceneNode();
                child.setPreferredHeight(100);
                content.appendChild(child);
            }
        };

        SceneNode container = SceneScrollContainer.attach(
                runtime, sceneRoot, contentBuilder);

        // parent 应含 container 一个子
        Assert.assertEquals("parent 含 container 一个子",
                1, sceneRoot.__getChildren().size());
        Assert.assertSame("parent 的子就是 attach 返回的 container",
                container, sceneRoot.__getChildren().get(0));

        // container 有 2 子（viewport + scrollbar column）
        Assert.assertEquals("container 有 2 子（viewport + scrollbar column）",
                2, container.__getChildren().size());
        SceneNode viewport = container.__getChildren().get(0);
        Assert.assertTrue("viewport 应 scrollable=true", viewport.isScrollable());

        // container 应 flexGrow=1（attach 门面在 COLUMN 父中撑满剩余高的契约）
        Assert.assertEquals("container flexGrow=1",
                1, container.getFlexGrow());

        // content 应已装填 itemCount 个条目
        // viewport 唯一子是 content，content 含 itemCount 个 item
        Assert.assertEquals("viewport 含 content 一个子",
                1, viewport.__getChildren().size());
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("content 已装填 5 个条目",
                itemCount, content.__getChildren().size());

        // layout 后 maxScroll > 0（content 溢出）
        doLayout();
        runtime.flush();
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("attach 后 content 溢出 maxScroll > 0", maxScroll > 0);
    }

    /**
     * attachNoBar 变体：container 只有 1 子（viewport），不建 scrollbar column。
     * content 仍可正常装填。
     */
    @Test
    public void attachNoBarShouldCreateContainerWithoutScrollbar() {
        final int itemCount = 3;
        Consumer<SceneNode> contentBuilder = content -> {
            for (int i = 0; i < itemCount; i++) {
                SceneNode child = new SceneNode();
                child.setPreferredHeight(80);
                content.appendChild(child);
            }
        };

        SceneNode container = SceneScrollContainer.attachNoBar(
                runtime, sceneRoot, contentBuilder);

        // parent 含 container
        Assert.assertEquals("parent 含 container 一个子",
                1, sceneRoot.__getChildren().size());
        Assert.assertSame("parent 的子就是 attachNoBar 返回的 container",
                container, sceneRoot.__getChildren().get(0));

        // container 只有 1 子（viewport，无 scrollbar column）
        Assert.assertEquals("attachNoBar 时 container 只有 1 子（viewport，无 scrollbar）",
                1, container.__getChildren().size());

        // content 仍正常装填
        SceneNode viewport = container.__getChildren().get(0);
        Assert.assertTrue("viewport 应 scrollable=true", viewport.isScrollable());
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("content 已装填 3 个条目",
                itemCount, content.__getChildren().size());
    }

    // ==================== scrollList 门面测试 ====================

    /** 简单可标识对象，引用做 key。 */
    private static final class Row {
        final String label;
        Row(String label) { this.label = label; }
    }

    /**
     * scrollList 一行建出 container+viewport+scrollbar+forEach：
     * container 挂 parent、viewport 可滚动、scrollbar 存在、forEach 行数 == 数据量。
     */
    @Test
    public void scrollListShouldBuildContainerViewportScrollbarAndForEach() {
        Row r1 = new Row("a");
        Row r2 = new Row("b");
        Row r3 = new Row("c");
        Signal<List<Row>> itemsSignal = Signal.create(new ArrayList<Row>(Arrays.asList(r1, r2, r3)));

        SceneNode container = SceneScrollContainer.scrollList(
                runtime, sceneRoot, itemsSignal,
                row -> {
                    SceneNode node = new SceneNode();
                    node.setPreferredHeight(100);
                    node.setText(row.label);
                    return node;
                });
        runtime.flush();

        // container 挂 parent
        Assert.assertEquals("parent 含 container 一个子",
                1, sceneRoot.__getChildren().size());
        Assert.assertSame("parent 的子就是 scrollList 返回的 container",
                container, sceneRoot.__getChildren().get(0));
        // container flexGrow=1
        Assert.assertEquals("container flexGrow=1", 1, container.getFlexGrow());

        // container 有 2 子（viewport + scrollbar column）
        Assert.assertEquals("container 有 2 子（viewport + scrollbar）",
                2, container.__getChildren().size());
        SceneNode viewport = container.__getChildren().get(0);
        Assert.assertTrue("viewport 应 scrollable=true", viewport.isScrollable());

        // viewport 唯一子 content 含 3 个 item
        Assert.assertEquals("viewport 含 content 一个子",
                1, viewport.__getChildren().size());
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("forEach 行数 == 数据量", 3, content.__getChildren().size());
    }

    /**
     * scrollList 内容超出 viewport 时 maxScrollY > 0，scrollbar 可见。
     */
    @Test
    public void scrollListShouldShowScrollbarOnOverflow() {
        // 5 个高 100 的 item，总高 500 > CANVAS_HEIGHT 300
        List<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < 5; i++) rows.add(new Row("r" + i));
        Signal<List<Row>> itemsSignal = Signal.create(rows);

        SceneNode container = SceneScrollContainer.scrollList(
                runtime, sceneRoot, itemsSignal,
                row -> {
                    SceneNode node = new SceneNode();
                    node.setPreferredHeight(100);
                    return node;
                });
        runtime.flush();
        doLayout();
        runtime.flush();

        SceneNode viewport = container.__getChildren().get(0);
        LayoutBox vpBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertNotNull("viewport 已布局", vpBox);
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("content 溢出时 maxScroll > 0", maxScroll > 0);

        // scrollbar column 存在（container 2 子）
        Assert.assertEquals("scrollbar 可见（container 2 子）",
                2, container.__getChildren().size());
    }

    /**
     * scrollList 带 keyFn 重载：结构同无 keyFn 版，key 正确驱动复用。
     */
    @Test
    public void scrollListWithKeyFn() {
        Signal<List<String>> itemsSignal = Signal.create(new ArrayList<String>(
                Arrays.asList("x", "y", "z")));

        SceneNode container = SceneScrollContainer.scrollList(
                runtime, sceneRoot, itemsSignal,
                Function.identity(),
                key -> {
                    SceneNode node = new SceneNode();
                    node.setPreferredHeight(80);
                    node.setText(key);
                    return node;
                });
        runtime.flush();

        Assert.assertEquals("parent 含 container 一个子",
                1, sceneRoot.__getChildren().size());
        Assert.assertEquals("container 有 2 子（viewport + scrollbar）",
                2, container.__getChildren().size());
        SceneNode viewport = container.__getChildren().get(0);
        Assert.assertTrue("viewport 应 scrollable=true", viewport.isScrollable());
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("带 keyFn forEach 行数 == 数据量",
                3, content.__getChildren().size());

        // 重排验证 key 复用：z, x, y
        itemsSignal.set(new ArrayList<String>(Arrays.asList("z", "x", "y")));
        runtime.flush();
        List<SceneNode> children = content.__getChildren();
        Assert.assertEquals("重排后仍 3 行", 3, children.size());
        Assert.assertEquals("第 0 行应为 z", "z", children.get(0).getText());
        Assert.assertEquals("第 1 行应为 x", "x", children.get(1).getText());
        Assert.assertEquals("第 2 行应为 y", "y", children.get(2).getText());
    }
}
