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
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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
    /** 固定字符宽度。 */
    private static final int STUB_CHAR_WIDTH = 8;

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 布局引擎。 */
    private SceneLayoutEngine layoutEngine;
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
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        changeCount = new AtomicInteger(0);
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
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
        routeText("-ui");
        runtime.flush();

        Assert.assertEquals("标量编辑写回根 signal", "qz-ui", valueSignal.get().get("name"));
    }

    /** 点击嵌套对象按钮应更新外部展开路径。 */
    @Test
    public void toggleNestedObjectShouldUpdateExpandedPaths() {
        mountObject(sampleValue(), Collections.<String>emptySet(), 5);
        clickCenter(databaseToggle());
        runtime.flush();

        Assert.assertTrue("点击后应展开 database", expandedPaths.get().contains("database"));

        clickCenter(databaseToggle());
        runtime.flush();

        Assert.assertFalse("再次点击后应折叠 database", expandedPaths.get().contains("database"));
    }

    /** 编辑嵌套子字段应只重建命中路径。 */
    @Test
    public void nestedScalarInputShouldRebuildHitPathOnly() {
        mountObject(sampleValue(), setOf("database"), 5);
        Map<String, Object> originalRoot = valueSignal.get();
        Object originalEnabled = originalRoot.get("enabled");

        focusInput(scalarInput(databaseContent().__getChildren().get(0)));
        routeKey(SceneKey.END);
        routeText("_new");
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
        routeText("X");
        runtime.flush();

        Assert.assertEquals("编辑应触发一次回调", 1, changeCount.get());
        Assert.assertSame("回调收到当前 signal 值", valueSignal.get(), lastChangedValue);
    }

    /**
     * 挂载待测控件。
     *
     * @param value         初始对象
     * @param expanded      初始展开路径
     * @param maxDepth      最大深度
     */
    private void mountObject(Map<String, Object> value, Set<String> expanded, int maxDepth) {
        valueSignal = Signal.create(value);
        expandedPaths = Signal.create(expanded);
        lastChangedValue = null;
        SceneObjectField.Props props = SceneObjectField.Props.builder(valueSignal)
                .label("对象")
                .expandedPaths(expandedPaths)
                .maxDepth(maxDepth)
                .onValueChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedValue = next;
                })
                .build();
        handle = runtime.mount(sceneRoot, SceneObjectField.create(runtime, props));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    /** 跑一帧布局。 */
    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** @return 滚动视口 */
    private SceneNode viewport() {
        for (SceneNode child : root.__getChildren()) {
            if (child.isScrollable()) {
                return child;
            }
        }
        throw new AssertionError("未找到滚动视口");
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

    /** 聚焦输入框并移动 caret 到末尾。 */
    private void focusInput(SceneNode input) {
        clickCenter(input);
        runtime.flush();
        routeKey(SceneKey.END);
        runtime.flush();
    }

    /** 返回输入框展示文本。 */
    private String inputValue(SceneNode input) {
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
    }

    /** 路由文本输入。 */
    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 路由按键。 */
    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 点击节点中心。 */
    private void clickCenter(SceneNode node) {
        doLayout();
        int[] center = absCenter(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
    }

    /** 路由指针事件。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 计算绝对中心。 */
    private int[] absCenter(SceneNode node) {
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        int x = box.getX();
        int y = box.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
            if (parentBox != null) {
                x += parentBox.getX();
                y += parentBox.getY();
            }
            parent = parent.__getParent();
        }
        return new int[] {x + box.getWidth() / 2, y + box.getHeight() / 2};
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
