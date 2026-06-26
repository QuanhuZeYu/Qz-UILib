package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.KeyValueRow;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationError;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationErrorType;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValueType;
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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneKeyValueMap 端到端单元测试。
 *
 * <p>覆盖初始 keyed 渲染、增删行、key/value 文本编辑、type 分段切换与 key 校验反馈。</p>
 */
public class SceneKeyValueMapTest {

    /** 画布宽度。 */
    private static final int CANVAS_WIDTH = 1040;
    /** 画布高度。 */
    private static final int CANVAS_HEIGHT = 320;
    /** 固定字符宽度。 */
    private static final int STUB_CHAR_WIDTH = 8;

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 运行时。 */
    private SceneRuntime runtime;
    /** 布局引擎。 */
    private SceneLayoutEngine layoutEngine;
    /** 受控行 signal。 */
    private Signal<List<KeyValueRow>> rowsSignal;
    /** 行变更次数。 */
    private AtomicInteger rowsChangedCount;
    /** 校验回调次数。 */
    private AtomicInteger validationCount;
    /** 最近一次校验错误。 */
    private ValidationError lastValidationError;
    /** mount 句柄。 */
    private MountHandle handle;
    /** 控件根节点。 */
    private SceneNode root;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("name", "qz", ValueType.STRING),
                new KeyValueRow("count", "1", ValueType.NUMBER))));
        rowsChangedCount = new AtomicInteger(0);
        validationCount = new AtomicInteger(0);
        lastValidationError = null;

        SceneKeyValueMap.Props props = SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .build();
        handle = runtime.mount(sceneRoot, SceneKeyValueMap.create(runtime, props));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 初始渲染 N 行。 */
    @Test
    public void initialRenderShouldCreateRows() {
        Assert.assertEquals("初始应渲染 2 行", 2, listViewport().__getChildren().size());
        Assert.assertEquals("首行 key 文本", "name", inputValue(keyInputRoot(0)));
        Assert.assertEquals("第二行 value 文本", "1", inputValue(valueInputRoot(1)));
    }

    /** 点击添加按钮后写回 rows。 */
    @Test
    public void addRowShouldUpdateRowsSignal() {
        clickCenter(addButton());
        runtime.flush();
        doLayout();

        Assert.assertEquals("添加后一共 3 行", 3, rowsSignal.get().size());
        Assert.assertEquals("新行 key 为空", "", rowsSignal.get().get(2).getKey());
        Assert.assertEquals("新行类型为 STRING", ValueType.STRING, rowsSignal.get().get(2).getType());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
        Assert.assertEquals("forEach 增量渲染第三行", 3, listViewport().__getChildren().size());
    }

    /** 删除按钮可点击并删除对应行。 */
    @Test
    public void deleteButtonShouldRemoveRow() {
        assertCenterInside(deleteButton(0), listViewport());
        clickCenter(deleteButton(0));
        runtime.flush();
        doLayout();

        Assert.assertEquals("删除后一共 1 行", 1, rowsSignal.get().size());
        Assert.assertEquals("剩余原第二行", "count", rowsSignal.get().get(0).getKey());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
    }

    /**
     * 删除行后，被删行内 mount 的子作用域 effect 应被回收（回归 df6e9299）。
     *
     * <p>df6e9299 修复前，buildRow 内 3 个 rt.mount 的子作用域挂到 rootOwner 而非当前 forEach
     * item Owner，删行后外层 dispose 不级联回收，effect 持续累积——本测试用全局 effect 计数
     * 探针断言"删除行后 effect 数下降"，守住该修复不被回归。</p>
     */
    @Test
    public void deleteRowShouldReclaimChildOwnerEffects() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("初始应已注册若干 effect", before > 0);

        clickCenter(deleteButton(0));
        runtime.flush();
        doLayout();

        int after = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("删除行后 effect 数应下降（回收子作用域），before=" + before + ", after=" + after,
                after < before);
        Assert.assertEquals("删除后一共 1 行", 1, rowsSignal.get().size());
    }

    /**
     * 反复增删行不应造成 effect 累积（回归 df6e9299 的泄漏场景）。
     *
     * <p>修复前每次"添加再删除"都会泄漏 buildRow 内 3 个 mount 的子作用域 effect；
     * 本测试循环 N 轮后断言 effect 数不单调增长。</p>
     */
    @Test
    public void repeatedAddDeleteShouldNotLeakEffects() {
        int initial = ReactiveTestProbe.registeredEffectCount();
        for (int i = 0; i < 5; i++) {
            clickCenter(addButton());
            runtime.flush();
            doLayout();
            clickCenter(deleteButton(rowsSignal.get().size() - 1));
            runtime.flush();
            doLayout();
        }
        int finalCount = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("反复增删后 effect 数不应显著高于初始（允许波动但不应泄漏累积），"
                + "initial=" + initial + ", final=" + finalCount,
                finalCount <= initial + 2);
    }

    /** key 输入框可编辑并写回 rows。 */
    @Test
    public void keyInputShouldUpdateRowsSignal() {
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("", "qz", ValueType.STRING),
                new KeyValueRow("count", "1", ValueType.NUMBER))));
        runtime.flush();
        doLayout();

        focusInput(keyInputRoot(0));
        routeText("myKey");
        runtime.flush();

        Assert.assertEquals("key 输入写回 rows", "myKey", rowsSignal.get().get(0).getKey());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
    }

    /** value 输入框可编辑并写回 rows。 */
    @Test
    public void valueInputShouldUpdateRowsSignal() {
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("name", "", ValueType.STRING),
                new KeyValueRow("count", "1", ValueType.NUMBER))));
        runtime.flush();
        doLayout();

        rowsChangedCount.set(0);
        focusInput(valueInputRoot(1));
        routeText("myValue");
        runtime.flush();

        Assert.assertEquals("value 输入写回 rows", "1myValue", rowsSignal.get().get(1).getValue());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
    }

    /** 切换某行 type 后写回 rows。 */
    @Test
    public void switchTypeShouldUpdateRowsSignal() {
        clickCenter(typeSegment(0, 2));
        runtime.flush();

        Assert.assertEquals("类型切到 BOOLEAN", ValueType.BOOLEAN, rowsSignal.get().get(0).getType());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
    }

    /** key 校验反馈：空 key 与重复 key。 */
    @Test
    public void validationShouldReportEmptyAndDuplicateKey() {
        List<KeyValueRow> emptyKeyRows = new ArrayList<KeyValueRow>(rowsSignal.get());
        emptyKeyRows.set(0, emptyKeyRows.get(0).copyWith("", "qz", ValueType.STRING));
        rowsSignal.set(Collections.unmodifiableList(emptyKeyRows));
        runtime.flush();
        doLayout();
        clickCenter(addButton());
        runtime.flush();

        Assert.assertEquals("空 key 反馈", ValidationErrorType.EMPTY_KEY, lastValidationError.getType());
        Assert.assertTrue("校验回调至少触发一次", validationCount.get() > 0);

        List<KeyValueRow> duplicateRows = new ArrayList<KeyValueRow>(rowsSignal.get());
        duplicateRows.set(0, duplicateRows.get(0).copyWith("dup", "a", ValueType.STRING));
        duplicateRows.set(1, duplicateRows.get(1).copyWith("dup", "b", ValueType.NUMBER));
        rowsSignal.set(Collections.unmodifiableList(duplicateRows));
        runtime.flush();
        clickCenter(addButton());
        runtime.flush();

        Assert.assertEquals("重复 key 反馈", ValidationErrorType.DUPLICATE_KEY, lastValidationError.getType());
    }

    /** key 含点号时标红并触发校验回调。 */
    @Test
    public void validationShouldReportKeyContainsDot() {
        List<KeyValueRow> dotRows = new ArrayList<KeyValueRow>(rowsSignal.get());
        dotRows.set(0, dotRows.get(0).copyWith("user.name", "qz", ValueType.STRING));
        rowsSignal.set(Collections.unmodifiableList(dotRows));
        runtime.flush();
        doLayout();

        Assert.assertEquals("点号 key 反馈", ValidationErrorType.KEY_CONTAINS_DOT, lastValidationError.getType());
        Assert.assertTrue("校验回调触发", validationCount.get() > 0);
        Assert.assertEquals("错误行标红", Integer.valueOf(SceneChromeTokens.DANGER_BG_SUBTLE), Integer.valueOf(row(0).getBackgroundColor()));
    }

    /** minRows 达边界时删除禁用。 */
    @Test
    public void minRowsBoundaryShouldDisableDelete() {
        remount(SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .minRows(2)
                .build());

        assertCenterInside(deleteButton(0), listViewport());
        clickCenter(deleteButton(0));
        runtime.flush();
        doLayout();

        Assert.assertEquals("minRows 边界不删除", 2, rowsSignal.get().size());
        Assert.assertEquals("不触发行变更回调", 0, rowsChangedCount.get());
    }

    /** maxRows 达边界时添加禁用。 */
    @Test
    public void maxRowsBoundaryShouldDisableAdd() {
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("name", "qz", ValueType.STRING),
                new KeyValueRow("count", "1", ValueType.NUMBER),
                new KeyValueRow("enabled", "true", ValueType.BOOLEAN))));
        remount(SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .maxRows(3)
                .build());

        clickCenter(addButton());
        runtime.flush();
        doLayout();

        Assert.assertEquals("maxRows 边界不添加", 3, rowsSignal.get().size());
        Assert.assertEquals("不触发行变更回调", 0, rowsChangedCount.get());
    }

    /** 控件级 enabled=FALSE 时，行内 key/value TextInput 编辑器应阻断文本输入。 */
    @Test
    public void disabledShouldBlockKeyEdit() {
        remount(SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .enabled(Signal.create(Boolean.FALSE))
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .build());

        runtime.requestFocus(keyInputRoot(0));
        runtime.flush();
        routeText("X");
        runtime.flush();

        Assert.assertEquals("disabled 时 key 编辑器应阻断输入，key 保持原值",
                "name", rowsSignal.get().get(0).getKey());
        Assert.assertEquals("disabled 时不触发行变更回调", 0, rowsChangedCount.get());
    }

    /** 空列表初始态只显示添加按钮。 */
    @Test
    public void emptyRowsShouldOnlyShowAddButton() {
        rowsSignal.set(Collections.<KeyValueRow>emptyList());
        remount(SceneKeyValueMap.Props.builder(rowsSignal)
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .build());

        Assert.assertEquals("空列表无行", 0, listViewportWithoutLabel().__getChildren().size());
        Assert.assertEquals("根节点保留添加按钮", "+ 添加", addButtonWithoutLabel().__getChildren().get(0).getText());
    }

    /** 跑一帧布局。 */
    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 重新挂载控件。 */
    private void remount(SceneKeyValueMap.Props props) {
        handle.dispose();
        handle = runtime.mount(sceneRoot, SceneKeyValueMap.create(runtime, props));
        root = handle.getRoot();
        rowsChangedCount.set(0);
        validationCount.set(0);
        lastValidationError = null;
        runtime.flush();
        doLayout();
    }

    /** 列表滚动视口。 */
    private SceneNode listViewport() {
        return root.__getChildren().get(3);
    }

    /** 添加按钮。 */
    private SceneNode addButton() {
        return root.__getChildren().get(4);
    }

    /** 无标题时的列表滚动视口。 */
    private SceneNode listViewportWithoutLabel() {
        return root.__getChildren().get(2);
    }

    /** 无标题时的添加按钮。 */
    private SceneNode addButtonWithoutLabel() {
        return root.__getChildren().get(3);
    }

    /** 指定行。 */
    private SceneNode row(int index) {
        return listViewport().__getChildren().get(index);
    }

    /** key 输入根。 */
    private SceneNode keyInputRoot(int rowIndex) {
        return row(rowIndex).__getChildren().get(0).__getChildren().get(0);
    }

    /** value 输入根。 */
    private SceneNode valueInputRoot(int rowIndex) {
        return row(rowIndex).__getChildren().get(1).__getChildren().get(0);
    }

    /** 输入框当前展示文本。 */
    private String inputValue(SceneNode input) {
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
    }

    /** 类型分段节点。 */
    private SceneNode typeSegment(int rowIndex, int typeIndex) {
        return row(rowIndex).__getChildren().get(2).__getChildren().get(0).__getChildren().get(typeIndex);
    }

    /** 删除按钮。 */
    private SceneNode deleteButton(int rowIndex) {
        return row(rowIndex).__getChildren().get(3);
    }

    /** 聚焦输入框并把 caret 移到末尾。 */
    private void focusInput(SceneNode input) {
        clickCenter(input);
        runtime.flush();
        routeKey(SceneKey.END);
        runtime.flush();
    }

    /** 发送文本输入。 */
    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 发送键盘输入。 */
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

    /** 发送指针事件。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 断言节点中心位于裁剪容器内。 */
    private void assertCenterInside(SceneNode node, SceneNode container) {
        doLayout();
        int[] center = absCenter(node);
        int[] containerCenter = absCenter(container);
        LayoutBox containerBox = (LayoutBox) container.getCachedLayout();
        int minX = containerCenter[0] - containerBox.getWidth() / 2;
        int maxX = minX + containerBox.getWidth();
        int minY = containerCenter[1] - containerBox.getHeight() / 2;
        int maxY = minY + containerBox.getHeight();
        Assert.assertTrue("节点中心应在裁剪容器水平范围内", center[0] >= minX && center[0] <= maxX);
        Assert.assertTrue("节点中心应在裁剪容器垂直范围内", center[1] >= minY && center[1] <= maxY);
    }

    /** 计算节点绝对中心。 */
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
}
