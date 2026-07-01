package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneObjectField 端到端单元测试。
 *
 * <p>覆盖对象初始渲染、标量编辑、嵌套对象折叠展开、嵌套写回、深度限制、空对象和回调触发。</p>
 */
public class SceneObjectFieldTest {

    /** 画布宽度。 */
    private static final int CANVAS_WIDTH = 720;
    /** 画布高度。 */
    private static final int CANVAS_HEIGHT = 420;

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 语义化交互注入 harness（route 根 + click/typeText/pressKey 入口）；其 runtime 即上方 runtime 字段。 */
    private SceneInteractionHarness harness;
    /** 对象值 signal。 */
    private Signal<Map<String, Object>> valueSignal;
    /** 展开路径 signal。 */
    private Signal<Set<String>> expandedPaths;
    /** 变更回调次数。 */
    private AtomicInteger changeCount;
    /** 最近一次回调值。 */
    private Map<String, Object> lastChangedValue;
    /** 挂载句柄。 */
    private MountHandle handle;
    /** 控件根节点。 */
    private SceneNode root;

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
        changeCount = new AtomicInteger(0);
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * scrollbarContentSignal 默认 null 时，stackHost 只含 viewport（结构向后兼容）。
     */
    @Test
    public void scrollbarContentSignalNullByDefault_stackHostHasOnlyViewport() {
        mountObject(sampleValue(), Collections.<String>emptySet(), 5);
        Assert.assertEquals("scrollbarContentSignal 默认 null 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * scrollbarContentSignal 非 null 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void scrollbarContentSignalSet_stackHostHasViewportAndScrollbarColumn() {
        Signal<Integer> contentSignal = Signal.create(Integer.valueOf(0));
        valueSignal = Signal.create(sampleValue());
        expandedPaths = Signal.create(Collections.<String>emptySet());
        SceneObjectField.Props props = SceneObjectField.Props.builder(valueSignal)
                .label("对象")
                .expandedPaths(expandedPaths)
                .maxDepth(5)
                .scrollbarContentSignal(contentSignal)
                .build();
        handle = runtime.mount(sceneRoot, SceneObjectField.create(runtime, props));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
        Assert.assertEquals("scrollbarContentSignal 非 null 时 stackHost 应含 viewport 与 scrollbar column",
                2, stackHost().__getChildren().size());
    }

    /** 初始渲染标量字段和已展开嵌套对象。 */
    @Test
    public void initialRenderShouldCreateScalarRowsAndNestedObject() {
        mountObject(sampleValue(), setOf("database"), 5);

        Assert.assertEquals("根层应渲染 4 个字段", 4, objectEditor().__getChildren().size());
        Assert.assertEquals("嵌套对象应渲染 header、anchor 与内容", 3, databaseRow().__getChildren().size());
        Assert.assertEquals("嵌套对象应渲染 2 个子字段", 2, databaseContent().__getChildren().size());
        Assert.assertEquals("name 字段展示文本", "qz", inputValue(scalarInput(rootRow(3))));
    }

    /** 编辑标量字段应写回根 signal。 */
    @Test
    public void scalarInputShouldUpdateValueSignal() {
        mountObject(sampleValue(), setOf("database"), 5);
        focusInput(scalarInput(rootRow(3)));
        harness.typeText("-ui");
        runtime.flush();

        Assert.assertEquals("标量编辑写回根 signal", "qz-ui", valueSignal.get().get("name"));
    }

    /** 点击嵌套对象按钮应更新外部展开路径。 */
    @Test
    public void toggleNestedObjectShouldUpdateExpandedPaths() {
        mountObject(sampleValue(), Collections.<String>emptySet(), 5);
        harness.click(databaseToggle());
        runtime.flush();

        Assert.assertTrue("点击后应展开 database", expandedPaths.get().contains("database"));

        // 展开后结构变化，重新 layout 让 databaseToggle 的 absoluteBox 就位再点击
        doLayout();
        harness.click(databaseToggle());
        runtime.flush();

        Assert.assertFalse("再次点击后应折叠 database", expandedPaths.get().contains("database"));
    }

    /**
     * 折叠嵌套对象后，展开内容子作用域的 effect 应被回收（回归 df6e9299）。
     *
     * <p>ObjectField 用 {@code rt.show(row, isExpanded, ...)} 控制嵌套内容挂卸；
     * df6e9299 修复前 show 的 condOwner 归属 rootOwner 而非当前作用域，
     * 折叠时 dispose 不级联到内容子 Owner，effect 泄漏。本测试用全局 effect 计数探针
     * 断言"折叠后 effect 数下降"，守住该修复不被回归。</p>
     */
    @Test
    public void collapseNestedObjectShouldReclaimEffects() {
        mountObject(sampleValue(), setOf("database"), 5);
        int expanded = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("展开态应已注册若干 effect", expanded > 0);

        harness.click(databaseToggle());
        runtime.flush();
        doLayout();

        Assert.assertFalse("应已折叠 database", expandedPaths.get().contains("database"));
        int collapsed = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("折叠后 effect 数应下降（回收内容子作用域），expanded=" + expanded
                + ", collapsed=" + collapsed, collapsed < expanded);
    }

    /** 编辑嵌套子字段应只重建命中路径。 */
    @Test
    public void nestedScalarInputShouldRebuildHitPathOnly() {
        mountObject(sampleValue(), setOf("database"), 5);
        Map<String, Object> originalRoot = valueSignal.get();
        Object originalEnabled = originalRoot.get("enabled");

        focusInput(scalarInput(databaseContent().__getChildren().get(0)));
        harness.pressKey(SceneKey.END);
        harness.typeText("_new");
        runtime.flush();

        Map<String, Object> nextRoot = valueSignal.get();
        Map<String, Object> nextDatabase = childMap(nextRoot, "database");
        Assert.assertNotSame("根 Map 应重建", originalRoot, nextRoot);
        Assert.assertEquals("嵌套字段写回", "localhost_new", nextDatabase.get("host"));
        Assert.assertSame("未命中兄弟值保持引用", originalEnabled, nextRoot.get("enabled"));
    }

    /** 深度超过限制时渲染占位提示。 */
    @Test
    public void depthLimitShouldRenderNotice() {
        mountObject(deepValue(6), setOf("a", "a.b", "a.b.c", "a.b.c.d", "a.b.c.d.e"), 5);

        Assert.assertTrue("超过深度应显示占位提示", containsText(objectEditor(), "嵌套层级超出显示深度，请通过配置文件编辑此字段"));
    }

    /** 空对象初始态显示空对象占位。 */
    @Test
    public void emptyObjectShouldRenderEmptyNotice() {
        mountObject(Collections.<String, Object>emptyMap(), Collections.<String>emptySet(), 5);

        Assert.assertEquals("空对象只渲染一个占位", 1, objectEditor().__getChildren().size());
        Assert.assertTrue("应显示空对象", containsText(objectEditor(), "空对象"));
    }

    /** 每次编辑后触发 onValueChanged。 */
    @Test
    public void onValueChangedShouldFireAfterEdit() {
        mountObject(sampleValue(), setOf("database"), 5);
        focusInput(scalarInput(rootRow(3)));
        harness.typeText("X");
        runtime.flush();

        Assert.assertEquals("编辑应触发一次回调", 1, changeCount.get());
        Assert.assertSame("回调收到当前 signal 值", valueSignal.get(), lastChangedValue);
    }

    /** 控件级 enabled=FALSE 时，标量行 TextInput 编辑器应阻断文本输入。 */
    @Test
    public void disabledShouldBlockScalarEdit() {
        mountObject(sampleValue(), setOf("database"), 5, Signal.create(Boolean.FALSE), null);

        runtime.requestFocus(scalarInput(rootRow(3)));
        runtime.flush();
        harness.typeText("X");
        runtime.flush();

        Assert.assertEquals("disabled 时标量编辑器应阻断输入，name 保持原值",
                "qz", valueSignal.get().get("name"));
        Assert.assertEquals("disabled 时不触发变更回调", 0, changeCount.get());
    }

    /**
     * 挂载待测控件。
     *
     * @param value         初始对象
     * @param expanded      初始展开路径
     * @param maxDepth      最大深度
     */
    private void mountObject(Map<String, Object> value, Set<String> expanded, int maxDepth) {
        mountObject(value, expanded, maxDepth, null, null);
    }

    /**
     * 挂载待测控件并注入控件级 enabled/readOnly 信号。
     *
     * @param value    初始对象
     * @param expanded 初始展开路径
     * @param maxDepth 最大深度
     * @param enabled  启用信号，null 时默认恒 true
     * @param readOnly 只读信号，null 时默认恒 false
     */
    private void mountObject(Map<String, Object> value, Set<String> expanded, int maxDepth,
                             club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> enabled,
                             club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> readOnly) {
        valueSignal = Signal.create(value);
        expandedPaths = Signal.create(expanded);
        lastChangedValue = null;
        SceneObjectField.Props.Builder builder = SceneObjectField.Props.builder(valueSignal)
                .label("对象")
                .expandedPaths(expandedPaths)
                .maxDepth(maxDepth)
                .onValueChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedValue = next;
                });
        if (enabled != null) {
            builder.enabled(enabled);
        }
        if (readOnly != null) {
            builder.readOnly(readOnly);
        }
        SceneObjectField.Props props = builder.build();
        handle = runtime.mount(sceneRoot, SceneObjectField.create(runtime, props));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    /** 跑一帧布局（经 harness.mountRoot 刷新路由根 + absoluteBox，供 harness.click 取中心）。 */
    private void doLayout() {
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** @return 滚动视口 */
    private SceneNode viewport() {
        SceneNode found = findScrollable(root);
        if (found == null) {
            throw new AssertionError("未找到滚动视口");
        }
        return found;
    }

    /**
     * 递归查找子树中第一个 isScrollable 节点。
     *
     * <p>viewport 现嵌套在 stackHost(ROW) 内，不再是 root 直接子，需递归定位。</p>
     *
     * @param node 子树根
     * @return 第一个可滚动节点，未找到返回 null
     */
    private SceneNode findScrollable(SceneNode node) {
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

    /** @return 承载 viewport 与可选滚动条的 stackHost（viewport 的父节点） */
    private SceneNode stackHost() {
        return viewport().__getParent();
    }

    /** @return 对象编辑器根 */
    private SceneNode objectEditor() {
        return viewport().__getChildren().get(0);
    }

    /** 返回根层行。 */
    private SceneNode rootRow(int index) {
        return objectEditor().__getChildren().get(index);
    }

    /** @return database 行 */
    private SceneNode databaseRow() {
        return rootRow(1);
    }

    /** @return database 展开按钮 */
    private SceneNode databaseToggle() {
        return databaseRow().__getChildren().get(0).__getChildren().get(0);
    }

    /** @return database 内容节点 */
    private SceneNode databaseContent() {
        return databaseRow().__getChildren().get(1);
    }

    /** 返回标量输入节点。 */
    private SceneNode scalarInput(SceneNode row) {
        return row.__getChildren().get(1);
    }

    /** 聚焦输入框并移动 caret 到末尾（click 聚焦 + END 跳末，分两步语义化注入）。 */
    private void focusInput(SceneNode input) {
        harness.click(input);
        harness.pressKey(SceneKey.END);
    }

    /** 返回输入框展示文本。 */
    private String inputValue(SceneNode input) {
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
    }

    /** 递归查找文本。 */
    private boolean containsText(SceneNode node, String text) {
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

    /** 创建样例对象。 */
    private Map<String, Object> sampleValue() {
        Map<String, Object> database = new LinkedHashMap<String, Object>();
        database.put("host", "localhost");
        database.put("port", Integer.valueOf(3306));

        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("name", "qz");
        value.put("count", Integer.valueOf(3));
        value.put("enabled", Boolean.TRUE);
        value.put("database", database);
        return Collections.unmodifiableMap(value);
    }

    /** 创建深层对象。 */
    private Map<String, Object> deepValue(int depth) {
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        Map<String, Object> rootMap = current;
        String[] keys = new String[] {"a", "b", "c", "d", "e", "f"};
        for (int i = 0; i < depth; i++) {
            Map<String, Object> child = new LinkedHashMap<String, Object>();
            current.put(keys[i], child);
            current = child;
        }
        current.put("leaf", "value");
        return rootMap;
    }

    /** 创建路径集合。 */
    private Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }

    /** 读取子 Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }
}
