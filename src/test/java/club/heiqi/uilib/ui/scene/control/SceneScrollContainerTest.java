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
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

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

    // ==================== 默认底座主题化（G07/ScrollContainer） ====================

    /** 在可切换局部主题作用域内挂载默认滚动容器（无 scrollbar，外观走主题）。 */
    private MountHandle mountContainerInTheme(Signal<SceneTheme> theme,
            final SceneScrollContainer.Result[] holder, int padding, int gap,
            int backgroundColor, int cornerRadius) {
        return runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(theme, () -> {
                holder[0] = SceneScrollContainer.create(runtime, new SceneScrollContainer.Props(
                        padding, gap, backgroundColor, cornerRadius, null));
            });
            return holder[0].container();
        });
    }

    /** 往 content 里塞 count 个固定高行节点。 */
    private static void fillRows(SceneNode content, int count, int rowHeight) {
        for (int i = 0; i < count; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(rowHeight);
            content.appendChild(child);
        }
    }

    /**
     * 默认工厂路径（不传 backgroundColor/cornerRadius）：viewport 底座取主题 GROUP 配方
     * （背景/圆角/边框/实体高度/滤镜），container 与 content 不装表面。
     */
    @Test
    public void defaultFactoryPathUsesGroupRecipeSurface() {
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());
        final SceneScrollContainer.Result[] holder = new SceneScrollContainer.Result[1];
        MountHandle handle = mountContainerInTheme(
                Signal.create(SceneTheme.liquidGlassDark()), holder, 0, 0, 0, 0);
        runtime.flush();

        SceneNode viewport = holder[0].viewport();
        Assert.assertEquals("默认背景 = GROUP 配方 idle 染色",
                group.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("默认圆角 = GROUP 配方", group.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("默认边框色 = GROUP 配方 idle 缘色",
                group.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("默认边框宽 = GROUP 配方", group.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("默认实体高度 = GROUP 配方",
                group.getIdle().getElevation(), viewport.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("默认路径应给 viewport 装 GROUP 滤镜", viewport.getBackdrop());
        Assert.assertEquals("滤镜模糊半径 = 配方", group.getBackdrop().getBlurRadius(),
                viewport.getBackdrop().getBlurRadius());
        Assert.assertEquals("滤镜材质 = 配方", group.getBackdrop().getEffect().getMaterial(),
                viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertNull("container 根节点不装表面", holder[0].container().getBackdrop());
        Assert.assertNull("content 不装表面", holder[0].content().getBackdrop());
        handle.dispose();
    }

    /**
     * 显式 backgroundColor/cornerRadius 仍优先：走旧实色路径，不装表面绑定（无滤镜、无双写入者）。
     */
    @Test
    public void explicitChromeOverridesThemeAndSkipsSurfaceBinding() {
        final int bg = 0xFF224466;
        final int radius = 9;
        final SceneScrollContainer.Result[] holder = new SceneScrollContainer.Result[1];
        MountHandle handle = mountContainerInTheme(
                Signal.create(SceneTheme.liquidGlassDark()), holder, 0, 0, bg, radius);
        runtime.flush();

        SceneNode viewport = holder[0].viewport();
        Assert.assertEquals("显式 backgroundColor 覆盖主题", bg, viewport.getBackgroundColor());
        Assert.assertEquals("显式 cornerRadius 覆盖主题", radius, viewport.getCornerRadius());
        Assert.assertNull("显式实色路径不装表面绑定", viewport.getBackdrop());
        handle.dispose();
    }

    /**
     * 只显式传 backgroundColor 时保持旧语义：背景为显式色、圆角仍 0（不补主题圆角、不装绑定）。
     */
    @Test
    public void explicitBackgroundOnlyKeepsLegacyCornerRadius() {
        final SceneScrollContainer.Result[] holder = new SceneScrollContainer.Result[1];
        MountHandle handle = mountContainerInTheme(
                Signal.create(SceneTheme.liquidGlassDark()), holder, 0, 0, 0xFF101418, 0);
        runtime.flush();

        Assert.assertEquals("显式背景生效", 0xFF101418, holder[0].viewport().getBackgroundColor());
        Assert.assertEquals("未显式传圆角时保持 0", 0, holder[0].viewport().getCornerRadius());
        Assert.assertNull("不装表面绑定", holder[0].viewport().getBackdrop());
        handle.dispose();
    }

    /**
     * 主题切换：{@code withTheme} 来源主题信号变化 + flush 后底座更新，滚动偏移、子项布局与
     * 节点身份不变，effect 数不增长（外观重派生不重建节点、不重复订阅）。
     */
    @Test
    public void themeSwitchUpdatesSurfaceAndKeepsScrollOffsetAndRowLayout() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneScrollContainer.Result[] holder = new SceneScrollContainer.Result[1];
        MountHandle handle = mountContainerInTheme(pageTheme, holder, 0, 0, 0, 0);
        fillRows(holder[0].content(), 5, 100);
        doLayout();
        runtime.flush();

        SceneNode viewport = holder[0].viewport();
        SceneNode content = holder[0].content();
        SceneNode firstRow = content.__getChildren().get(0);
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("前置：内容溢出", maxScroll > 0);
        int offset = Math.min(120, maxScroll);
        holder[0].scrollSignal().set(Integer.valueOf(offset));
        runtime.flush();
        Assert.assertEquals("前置：滚动偏移已应用", offset, viewport.getScrollOffsetY());
        LayoutBox rowBox = (LayoutBox) firstRow.getCachedLayout();
        Assert.assertNotNull("前置：行已布局", rowBox);
        int rowYBefore = rowBox.getY();

        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两个主题的 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());
        Assert.assertEquals("深色档背景", darkGroup.getIdle().getTint(), viewport.getBackgroundColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切浅色档背景更新", lightGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("切浅色档圆角更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("切浅色档边框更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertSame("主题切换不重建 viewport",
                viewport, holder[0].container().__getChildren().get(0));
        Assert.assertSame("主题切换不重建 content", content, viewport.__getChildren().get(0));
        Assert.assertSame("主题切换不重建行节点", firstRow, content.__getChildren().get(0));
        Assert.assertEquals("主题切换保留滚动偏移", offset, viewport.getScrollOffsetY());
        LayoutBox rowBoxAfter = (LayoutBox) firstRow.getCachedLayout();
        Assert.assertNotNull(rowBoxAfter);
        Assert.assertEquals("主题切换不重排行节点", rowYBefore, rowBoxAfter.getY());
        Assert.assertEquals("主题切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /**
     * 卸载后表面绑定 effect 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountReleasesSurfaceBindings() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneScrollContainer.Result[] holder = new SceneScrollContainer.Result[1];
        MountHandle handle = mountContainerInTheme(pageTheme, holder, 0, 0, 0, 0);
        runtime.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode viewport = holder[0].viewport();
        int colorBeforeDispose = viewport.getBackgroundColor();
        handle.dispose();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                before, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧 viewport",
                colorBeforeDispose, viewport.getBackgroundColor());
    }

    /**
     * 长列表：底座只装在 viewport 一次，行节点不各装滤镜、不写背景。
     */
    @Test
    public void longListRowsDoNotInstallPerRowBackdrop() {
        List<Row> rows = new ArrayList<Row>();
        for (int i = 0; i < 20; i++) {
            rows.add(new Row("row" + i));
        }
        Signal<List<Row>> itemsSignal = Signal.create(rows);
        SceneNode container = SceneScrollContainer.scrollList(runtime, sceneRoot, itemsSignal,
                row -> {
                    SceneNode node = new SceneNode();
                    node.setPreferredHeight(60);
                    return node;
                });
        runtime.flush();
        doLayout();
        runtime.flush();

        SceneNode viewport = container.__getChildren().get(0);
        Assert.assertNotNull("底座只装在 viewport 上", viewport.getBackdrop());
        SceneNode content = viewport.__getChildren().get(0);
        List<SceneNode> rowNodes = content.__getChildren();
        Assert.assertEquals("长列表行数 == 数据量", 20, rowNodes.size());
        for (SceneNode row : rowNodes) {
            Assert.assertNull("行节点不得各装背景滤镜", row.getBackdrop());
            Assert.assertEquals("行节点背景保持透明", 0, row.getBackgroundColor());
        }
    }

    /**
     * 嵌套滚动容器：外层滚动只改外层 viewport 的 GEOMETRY 偏移，内层滚动偏移、内层子项布局
     * 与两层各自裁剪标志都不变（滚动几何与嵌套 scissor 完全不变）。
     */
    @Test
    public void nestedScrollKeepsInnerOffsetAndLayout() {
        final SceneScrollContainer.Result[] outer = new SceneScrollContainer.Result[1];
        final SceneScrollContainer.Result[] inner = new SceneScrollContainer.Result[1];
        MountHandle handle = runtime.mount(sceneRoot, () -> {
            outer[0] = SceneScrollContainer.create(runtime,
                    new SceneScrollContainer.Props(0, 0, 0, 0, null));
            // 第 0 行承载嵌套滚动容器（行高确定，内层 viewport 才能收到确定高约束）
            SceneNode nestedRow = SceneNode.column();
            nestedRow.setPreferredHeight(100);
            outer[0].content().appendChild(nestedRow);
            inner[0] = SceneScrollContainer.create(runtime,
                    new SceneScrollContainer.Props(0, 0, 0, 0, null));
            nestedRow.appendChild(inner[0].container());
            fillRows(inner[0].content(), 3, 50);
            // 其余 4 行普通内容
            fillRows(outer[0].content(), 4, 100);
            return outer[0].container();
        });
        doLayout();
        runtime.flush();

        Assert.assertTrue("前置：外层溢出", SceneGeometry.maxScrollY(outer[0].viewport()) > 0);
        Assert.assertTrue("前置：内层溢出", SceneGeometry.maxScrollY(inner[0].viewport()) > 0);
        inner[0].scrollSignal().set(Integer.valueOf(30));
        runtime.flush();
        Assert.assertEquals("前置：内层偏移已应用", 30, inner[0].viewport().getScrollOffsetY());
        SceneNode innerRow = inner[0].content().__getChildren().get(0);
        LayoutBox innerRowBox = (LayoutBox) innerRow.getCachedLayout();
        Assert.assertNotNull("前置：内层行已布局", innerRowBox);
        int innerRowYBefore = innerRowBox.getY();

        int outerMax = SceneGeometry.maxScrollY(outer[0].viewport());
        outer[0].scrollSignal().set(Integer.valueOf(Math.min(150, outerMax)));
        runtime.flush();
        doLayout();
        runtime.flush();

        Assert.assertTrue("外层已滚动", outer[0].viewport().getScrollOffsetY() > 0);
        Assert.assertEquals("外层滚动不改内层滚动偏移",
                30, inner[0].viewport().getScrollOffsetY());
        Assert.assertEquals("外层滚动不重排内层子项",
                innerRowYBefore, ((LayoutBox) innerRow.getCachedLayout()).getY());
        Assert.assertTrue("两层 viewport 各自保持裁剪",
                outer[0].viewport().isClipChildren() && inner[0].viewport().isClipChildren());
        handle.dispose();
    }
}
