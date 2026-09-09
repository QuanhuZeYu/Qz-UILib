package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneObjectField 端到端单元测试。
 *
 * <p>覆盖对象初始渲染、标量编辑、嵌套对象折叠展开、嵌套写回、深度限制、空对象和回调触发；
 * 并验证液态玻璃迁移口径：编辑视口底座走主题 GROUP 配方、字段行不装滤镜只做透明布局、
 * 展开按钮与编辑单元（复用 {@link SceneTextInput}）消费各自角色配方、标题/字段名/提示文字
 * 随主题语义色重派生、主题切换不重建节点不丢草稿·展开·滚动状态、卸载回收绑定 effect。</p>
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
    /** 绘制引擎（断言滤镜采样预算与行不装滤镜）。 */
    private ScenePaintEngine paintEngine;

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
        changeCount = new AtomicInteger(0);
        paintEngine = new ScenePaintEngine(new FixedTextMeasurer(8, 16));
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * showScrollbar 默认 false 时，stackHost 只含 viewport（结构向后兼容）。
     */
    @Test
    public void showScrollbarFalseByDefault_stackHostHasOnlyViewport() {
        mountObject(sampleValue(), Collections.<String>emptySet(), 5);
        Assert.assertEquals("showScrollbar 默认 false 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * showScrollbar 为 true 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void showScrollbarTrue_stackHostHasViewportAndScrollbarColumn() {
        valueSignal = Signal.create(sampleValue());
        expandedPaths = Signal.create(Collections.<String>emptySet());
        SceneObjectField.Props props = SceneObjectField.Props.builder(valueSignal)
                .label("对象")
                .expandedPaths(expandedPaths)
                .maxDepth(5)
                .showScrollbar(true)
                .build();
        handle = runtime.mount(sceneRoot, SceneObjectField.create(runtime, props));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 viewport 与 scrollbar column",
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
                             ReadableSignal<Boolean> enabled,
                             ReadableSignal<Boolean> readOnly) {
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
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText()
                + input.__getChildren().get(4).getText();
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

    // ==================== 液态玻璃迁移口径（G12/ObjectField） ====================

    /**
     * ① 默认路径：编辑视口底座逐项等于 GROUP 角色配方，展开按钮逐项等于 BUTTON_STANDARD 配方，
     * 标题/字段名取主题正文、类型提示取次要前景；字段行与各级容器只是透明布局节点，
     * 不装滤镜、不写边框与圆角。
     */
    @Test
    public void defaultShellShouldFollowGroupAndButtonRecipes() {
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle button = SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());
        Assert.assertNotNull("前置：BUTTON_STANDARD 配方自带滤镜", button.getBackdrop());
        mountObject(sampleValue(), setOf("database"), 5);
        doLayout();

        SceneNode viewport = viewport();
        Assert.assertEquals("底座染色 = GROUP 配方 idle tint",
                group.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座缘色 = GROUP 配方 idle edge",
                group.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座边框宽 = GROUP 配方", group.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("底座圆角 = GROUP 配方", group.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座实体高度 = GROUP 配方 idle elevation",
                group.getIdle().getElevation(), viewport.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", viewport.getBackdrop());
        Assert.assertEquals("底座滤镜材质 = 配方",
                group.getBackdrop().getEffect().getMaterial(), viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("底座滤镜模糊半径 = 配方",
                group.getBackdrop().getBlurRadius(), viewport.getBackdrop().getBlurRadius());

        SceneNode toggle = databaseToggle();
        Assert.assertEquals("按钮染色 = BUTTON_STANDARD 配方 idle tint（静态 BG_DEFAULT 已销账）",
                button.getIdle().getTint(), toggle.getBackgroundColor());
        Assert.assertEquals("按钮缘色 = BUTTON_STANDARD 配方 idle edge",
                button.getIdle().getEdge(), toggle.getBorderColor());
        Assert.assertEquals("按钮边框宽 = 配方", button.getBorderWidth(), toggle.getBorderWidth());
        Assert.assertEquals("按钮圆角 = 配方（静态 RADIUS_MD 已销账）",
                button.getCornerRadius(), toggle.getCornerRadius());
        Assert.assertEquals("按钮实体高度 = 配方",
                button.getIdle().getElevation(), toggle.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("按钮默认带滤镜", toggle.getBackdrop());
        Assert.assertEquals("按钮文字 = 配方 foreground（静态 TEXT_ON_ACCENT 已销账）",
                button.getForeground().intValue(), toggle.__getChildren().get(0).getTextColor());

        Assert.assertEquals("标题 = 主题正文前景",
                SceneThemes.DEFAULT.foreground(), root.__getChildren().get(0).getTextColor());
        Assert.assertEquals("字段名 = 主题正文前景",
                SceneThemes.DEFAULT.foreground(), findText(root, "name").getTextColor());
        Assert.assertEquals("类型提示 = 主题次要前景",
                SceneThemes.DEFAULT.mutedForeground(),
                databaseHeader().__getChildren().get(2).getTextColor());

        assertNoSurface("根编辑器容器", objectEditor());
        for (int i = 0; i < 4; i++) {
            assertNoSurface("根层行[" + i + "]", rootRow(i));
        }
        assertNoSurface("嵌套 header", databaseHeader());
        assertNoSurface("嵌套内容容器", databaseContent());
    }

    /**
     * ①′ 控件自产提示文本取主题语义色：深度超限/列表未实现提示取 {@code warningText}
     * （旧 {@code WARNING_TEXT} 静态常量销账），空对象占位取 {@code mutedForeground}。
     */
    @Test
    public void noticeTextsShouldConsumeThemeSemanticColors() {
        Map<String, Object> listValue = new LinkedHashMap<String, Object>();
        listValue.put("tags", Arrays.asList("a", "b"));
        mountObject(listValue, Collections.<String>emptySet(), 5);
        Assert.assertEquals("列表未实现提示 = 主题 warningText",
                SceneThemes.DEFAULT.warningText(), findText(root, "列表编辑暂未实现").getTextColor());

        handle.dispose();
        mountObject(deepValue(6), setOf("a", "a.b", "a.b.c", "a.b.c.d", "a.b.c.d.e"), 5);
        Assert.assertEquals("深度超限提示 = 主题 warningText",
                SceneThemes.DEFAULT.warningText(),
                findText(root, "嵌套层级超出显示深度，请通过配置文件编辑此字段").getTextColor());

        handle.dispose();
        mountObject(Collections.<String, Object>emptyMap(), Collections.<String>emptySet(), 5);
        Assert.assertEquals("空对象占位 = 主题 mutedForeground",
                SceneThemes.DEFAULT.mutedForeground(), findText(root, "空对象").getTextColor());
    }

    /**
     * ② 滤镜采样预算：每颗表面只采样一次滤镜——底座、按钮、每个编辑单元各自恰好一条
     * {@code BACKDROP}；行/容器零 {@code BACKDROP}，整树命令数等于表面颗数。
     */
    @Test
    public void backdropSamplingBudgetShouldBeOnePerSurface() {
        Map<String, Object> database = new LinkedHashMap<String, Object>();
        database.put("host", "localhost");
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("name", "qz");
        value.put("database", database);
        mountObject(Collections.unmodifiableMap(value), setOf("database"), 5);
        doLayout();
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();

        // 排序后 keys = [database(0), name(1)]
        SceneNode databaseRow = rootRow(0);
        SceneNode header = databaseRow.__getChildren().get(0);
        SceneNode toggle = header.__getChildren().get(0);
        SceneNode content = databaseRow.__getChildren().get(1);
        SceneNode hostRow = content.__getChildren().get(0);

        Assert.assertEquals("底座恰好一条 BACKDROP", 1, backdropCount(viewport()));
        Assert.assertEquals("展开按钮恰好一条 BACKDROP", 1, backdropCount(toggle));
        Assert.assertEquals("标量编辑单元恰好一条 BACKDROP",
                1, backdropCount(scalarInput(rootRow(1))));
        Assert.assertEquals("嵌套编辑单元恰好一条 BACKDROP",
                1, backdropCount(scalarInput(hostRow)));

        assertNoSurface("database 行", databaseRow);
        assertNoSurface("嵌套 header", header);
        assertNoSurface("host 行", hostRow);
        assertNoSurface("根编辑器容器", objectEditor());
        assertNoSurface("嵌套内容容器", content);
        Assert.assertEquals("整树 BACKDROP = 底座 1 + 按钮 1 + 编辑单元 2（每颗表面只采样一次）",
                4, countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * ③ 真实进入编辑模式：编辑单元（只读复用的 {@link SceneTextInput}）外观逐项等于 INPUT
     * 角色配方；聚焦后 focus 只覆盖缘色不改染色；草稿写回受控 signal；行仍不装滤镜。
     */
    @Test
    public void editModeShouldConsumeInputRecipeAndKeepDraft() {
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        Assert.assertNotNull("前置：INPUT 配方自带滤镜", input.getBackdrop());
        mountObject(sampleValue(), setOf("database"), 5);
        SceneNode nameInput = scalarInput(rootRow(3));

        Assert.assertEquals("未聚焦编辑单元染色 = INPUT 配方 idle tint",
                input.getIdle().getTint(), nameInput.getBackgroundColor());
        Assert.assertEquals("未聚焦编辑单元缘色 = INPUT 配方 idle edge",
                input.getIdle().getEdge(), nameInput.getBorderColor());
        Assert.assertEquals("编辑单元边框宽 = 配方", input.getBorderWidth(), nameInput.getBorderWidth());
        Assert.assertEquals("编辑单元圆角 = 配方", input.getCornerRadius(), nameInput.getCornerRadius());
        Assert.assertEquals("编辑单元实体高度 = 配方",
                input.getIdle().getElevation(), nameInput.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("编辑单元滤镜来自复用控件", nameInput.getBackdrop());
        Assert.assertEquals("编辑单元滤镜材质 = INPUT 配方",
                input.getBackdrop().getEffect().getMaterial(), nameInput.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("编辑单元滤镜模糊 = INPUT 配方",
                input.getBackdrop().getBlurRadius(), nameInput.getBackdrop().getBlurRadius());

        focusInput(nameInput);
        harness.typeText("-ui");
        runtime.flush();

        Assert.assertEquals("编辑草稿写回受控 signal", "qz-ui", valueSignal.get().get("name"));
        Assert.assertEquals("编辑单元展示草稿", "qz-ui", inputValue(nameInput));
        Assert.assertEquals("聚焦后染色仍为 INPUT 配方 idle tint",
                input.getIdle().getTint(), nameInput.getBackgroundColor());
        Assert.assertEquals("聚焦缘色 = 配方 focusEdge", input.getFocusEdge(), nameInput.getBorderColor());
        Assert.assertNull("编辑态下行仍不装滤镜", rootRow(3).getBackdrop());
        Assert.assertEquals("编辑态下行背景保持透明", 0, rootRow(3).getBackgroundColor());
    }

    /**
     * ④ 主题切换：底座/按钮/编辑单元/各类文字随来源主题重派生；节点身份不变；
     * 编辑草稿、嵌套展开与滚动偏移保留；effect 数不增长；底座滤镜仍恰好一条。
     */
    @Test
    public void themeSwitchShouldRestyleWithoutLosingState() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP idle 必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        sceneRoot.setFillParentHeight(true);
        mountObjectInTheme(pageTheme, wideValue(), setOf("database"), 5);
        doLayout();

        SceneNode viewport = viewport();
        SceneNode nameRow = rootRow(9);
        SceneNode nameInput = scalarInput(nameRow);
        SceneNode toggle = databaseToggle();
        SceneNode titleLabel = root.__getChildren().get(0);
        SceneNode nestedContent = databaseContent();
        SceneSurfaceStyle darkButton = dark.surface(SceneTheme.Role.BUTTON_STANDARD);
        Assert.assertEquals("初始底座 = 深色 GROUP idle tint",
                darkGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("初始按钮 = 深色 BUTTON_STANDARD idle tint",
                darkButton.getIdle().getTint(), toggle.getBackgroundColor());
        Assert.assertEquals("初始标题 = 深色正文前景", dark.foreground(), titleLabel.getTextColor());
        Assert.assertEquals("初始字段名 = 深色正文前景",
                dark.foreground(), findText(root, "name").getTextColor());
        Assert.assertEquals("初始类型提示 = 深色次要前景",
                dark.mutedForeground(), findText(root, "对象").getTextColor());
        Assert.assertEquals("初始列表提示 = 深色 warningText",
                dark.warningText(), findText(root, "列表编辑暂未实现").getTextColor());

        // 草稿：编辑视口内可见的嵌套 host 输入（主题切换不得丢）
        SceneNode hostRow = nestedContent.__getChildren().get(0);
        SceneNode hostInput = scalarInput(hostRow);
        focusInput(hostInput);
        harness.typeText("X");
        runtime.flush();
        Assert.assertEquals("前置：草稿已写回受控 signal", "localhostX",
                childMap(valueSignal.get(), "database").get("host"));

        // 滚动偏移：多字段列表向下滚
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("前置：多字段可滚动，maxScroll=" + maxScroll, maxScroll > 0);
        int offset = Math.min(60, maxScroll);
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        scrollAt(viewportBox.getX() + viewportBox.getWidth() - 2,
                viewportBox.getY() + viewportBox.getHeight() / 2, -offset);
        Assert.assertEquals("前置：滚动偏移已应用", offset, viewport.getScrollOffsetY());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        SceneSurfaceStyle lightButton = light.surface(SceneTheme.Role.BUTTON_STANDARD);
        Assert.assertEquals("底座染色随主题更新", lightGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座缘色随主题更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("按钮染色随主题更新",
                lightButton.getIdle().getTint(), toggle.getBackgroundColor());
        Assert.assertEquals("按钮文字随主题更新",
                lightButton.getForeground().intValue(), toggle.__getChildren().get(0).getTextColor());
        Assert.assertEquals("编辑单元染色随主题更新",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(), nameInput.getBackgroundColor());
        Assert.assertEquals("标题文字随主题更新", light.foreground(), titleLabel.getTextColor());
        Assert.assertEquals("字段名随主题更新", light.foreground(), findText(root, "name").getTextColor());
        Assert.assertEquals("类型提示随主题更新", light.mutedForeground(), findText(root, "对象").getTextColor());
        Assert.assertEquals("列表提示随主题更新", light.warningText(),
                findText(root, "列表编辑暂未实现").getTextColor());

        Assert.assertSame("主题切换不重建 viewport", viewport, viewport());
        Assert.assertSame("主题切换不重建 name 行", nameRow, rootRow(9));
        Assert.assertSame("主题切换不重建编辑单元", nameInput, scalarInput(rootRow(9)));
        Assert.assertSame("主题切换不重建展开按钮", toggle, databaseToggle());
        Assert.assertSame("主题切换不重建标题", titleLabel, root.__getChildren().get(0));
        Assert.assertSame("主题切换不重建嵌套内容", nestedContent, databaseContent());
        Assert.assertSame("主题切换不重建被编辑的嵌套行", hostRow, databaseContent().__getChildren().get(0));
        Assert.assertSame("主题切换不重建被编辑的输入", hostInput,
                scalarInput(databaseContent().__getChildren().get(0)));

        Assert.assertEquals("主题切换不丢草稿", "localhostX",
                childMap(valueSignal.get(), "database").get("host"));
        Assert.assertEquals("主题切换不丢编辑单元草稿文本", "localhostX",
                inputValue(scalarInput(databaseContent().__getChildren().get(0))));
        Assert.assertTrue("主题切换不丢展开状态", expandedPaths.get().contains("database"));
        Assert.assertEquals("主题切换后嵌套子字段数量不变", 2, databaseContent().__getChildren().size());
        Assert.assertEquals("主题切换保留滚动偏移", offset, viewport.getScrollOffsetY());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        paintEngine.paint(sceneRoot);
        Assert.assertEquals("主题切换后底座仍恰好一条 BACKDROP", 1, backdropCount(viewport));
        for (int i = 0; i < 11; i++) {
            Assert.assertNull("主题切换后行[" + i + "] 仍不装滤镜", rootRow(i).getBackdrop());
        }
    }

    /**
     * ⑤ 卸载后表面绑定 effect 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountShouldReleaseSurfaceBindings() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountObjectInTheme(pageTheme, sampleValue(), setOf("database"), 5);
        doLayout();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode viewport = viewport();
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
     * 在 {@code withTheme} 局部主题作用域下挂载控件（主题切换/卸载用例入口）。
     *
     * @param pageTheme 页面主题信号
     * @param value     初始对象
     * @param expanded  初始展开路径
     * @param maxDepth  最大深度
     */
    private void mountObjectInTheme(Signal<SceneTheme> pageTheme, Map<String, Object> value,
                                    Set<String> expanded, int maxDepth) {
        valueSignal = Signal.create(value);
        expandedPaths = Signal.create(expanded);
        lastChangedValue = null;
        changeCount.set(0);
        SceneObjectField.Props props = SceneObjectField.Props.builder(valueSignal)
                .label("对象配置")
                .expandedPaths(expandedPaths)
                .maxDepth(maxDepth)
                .onValueChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedValue = next;
                })
                .build();
        final SceneNode[] holder = new SceneNode[1];
        handle = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme,
                    () -> holder[0] = SceneObjectField.create(runtime, props).get());
            return holder[0];
        });
        root = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    /** @return database 嵌套行的 header 节点 */
    private SceneNode databaseHeader() {
        return databaseRow().__getChildren().get(0);
    }

    /** 断言纯布局节点：不装滤镜、背景透明、不写边框宽与圆角。 */
    private void assertNoSurface(String what, SceneNode node) {
        Assert.assertNull(what + " 不得装滤镜", node.getBackdrop());
        Assert.assertEquals(what + " 背景应保持透明", 0, node.getBackgroundColor());
        Assert.assertEquals(what + " 不应写边框宽", 0, node.getBorderWidth());
        Assert.assertEquals(what + " 不应写圆角", 0, node.getCornerRadius());
    }

    /**
     * 递归查找第一个文本等于 {@code text} 的节点。
     *
     * @param node 子树根
     * @param text 目标文本
     * @return 命中节点，未找到返回 null
     */
    private SceneNode findText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数；无 fragment（无绘制内容）视为 0。 */
    private static int backdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return 0;
        }
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** PaintPlan 中指定类型的命令数。 */
    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /**
     * 在给定坐标投递 SCROLL 事件（{@code wheelDelta < 0} 向下滚），坐标取行内空白区
     * 以避开输入框自身的交互语义。
     */
    private void scrollAt(int x, int y, int wheelDelta) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y, SceneMouseButton.NONE,
                wheelDelta, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
        runtime.flush();
    }

    /**
     * 多字段样例对象：10 个标量 + 1 个可嵌套对象 + 1 个列表占位，纵向撑出可滚动高度。
     *
     * <p>排序后行序：count(0) database(1) enabled(2) k01(3)..k06(8) name(9) tags(10)。</p>
     */
    private Map<String, Object> wideValue() {
        Map<String, Object> database = new LinkedHashMap<String, Object>();
        database.put("host", "localhost");
        database.put("port", Integer.valueOf(3306));
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("name", "qz");
        value.put("count", Integer.valueOf(3));
        value.put("enabled", Boolean.TRUE);
        for (int i = 1; i <= 6; i++) {
            value.put("k0" + i, "v" + i);
        }
        value.put("database", database);
        value.put("tags", Arrays.asList("a", "b"));
        return Collections.unmodifiableMap(value);
    }
}
