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
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneKeyValueMap 端到端单元测试。
 *
 * <p>覆盖初始 keyed 渲染、增删行、key/value 文本编辑、type 分段切换与 key 校验反馈；
 * 并验证液态玻璃迁移口径（G12 通过条件「不只改外壳；实际进入编辑模式也统一」）：底座走主题
 * GROUP 配方、行只做 errorText 系轻量底色覆盖且不装滤镜、每颗表面只采样一次、编辑单元与按钮
 * 消费各自角色配方、文字取主题语义前景、主题切换不重建节点不丢草稿与校验状态、卸载回收绑定。</p>
 */
public class SceneKeyValueMapTest {

    /** 画布宽度。 */
    private static final int CANVAS_WIDTH = 1040;
    /** 画布高度。 */
    private static final int CANVAS_HEIGHT = 320;
    /** 校验失败行弱提示 alpha（与控件 {@code ROW_ERROR_ALPHA} 同一口径，沿用旧 DANGER_BG_SUBTLE 强度档）。 */
    private static final int ERROR_ROW_ALPHA = 0x22;

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 运行时。 */
    private SceneRuntime runtime;
    /** 语义化交互注入 harness（route 根 + click/typeText/pressKey 入口）；其 runtime 即上方 runtime 字段。 */
    private club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness harness;
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
    /** 绘制引擎（断言每颗表面只采样一次滤镜、行不装滤镜）。 */
    private ScenePaintEngine paintEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("name", "qz", ValueType.STRING),
                new KeyValueRow("count", "1", ValueType.NUMBER))));
        rowsChangedCount = new AtomicInteger(0);
        validationCount = new AtomicInteger(0);
        lastValidationError = null;
        paintEngine = new ScenePaintEngine(new FixedTextMeasurer(8, 16));

        handle = runtime.mount(sceneRoot, SceneKeyValueMap.create(runtime, defaultProps()));
        root = handle.getRoot();
        runtime.flush();
        doLayout();
    }

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
        Assert.assertEquals("showScrollbar 默认 false 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * showScrollbar 为 true 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void showScrollbarTrue_stackHostHasViewportAndScrollbarColumn() {
        remount(SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .showScrollbar(true)
                .build());
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 viewport 与 scrollbar column",
                2, stackHost().__getChildren().size());
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
        harness.click(addButton());
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
        harness.click(deleteButton(0));
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

        harness.click(deleteButton(0));
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
            harness.click(addButton());
            runtime.flush();
            doLayout();
            harness.click(deleteButton(rowsSignal.get().size() - 1));
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
        harness.typeText("myKey");
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
        harness.typeText("myValue");
        runtime.flush();

        Assert.assertEquals("value 输入写回 rows", "1myValue", rowsSignal.get().get(1).getValue());
        Assert.assertEquals("行变更回调触发", 1, rowsChangedCount.get());
    }

    /** 切换某行 type 后写回 rows。 */
    @Test
    public void switchTypeShouldUpdateRowsSignal() {
        harness.click(typeSegment(0, 2));
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
        harness.click(addButton());
        runtime.flush();

        Assert.assertEquals("空 key 反馈", ValidationErrorType.EMPTY_KEY, lastValidationError.getType());
        Assert.assertTrue("校验回调至少触发一次", validationCount.get() > 0);

        List<KeyValueRow> duplicateRows = new ArrayList<KeyValueRow>(rowsSignal.get());
        duplicateRows.set(0, duplicateRows.get(0).copyWith("dup", "a", ValueType.STRING));
        duplicateRows.set(1, duplicateRows.get(1).copyWith("dup", "b", ValueType.NUMBER));
        rowsSignal.set(Collections.unmodifiableList(duplicateRows));
        runtime.flush();
        harness.click(addButton());
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
        Assert.assertEquals("错误行标红 = 主题 errorText 弱提示底色（轻量覆盖，只写 backgroundColor）",
                Integer.valueOf(tint(SceneThemes.DEFAULT.errorText(), ERROR_ROW_ALPHA)),
                Integer.valueOf(row(0).getBackgroundColor()));
        Assert.assertEquals("未失败行保持透明露出底座", 0, row(1).getBackgroundColor());
        Assert.assertNull("错误行也不装滤镜", row(0).getBackdrop());
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
        harness.click(deleteButton(0));
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

        harness.click(addButton());
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
        harness.typeText("X");
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

    // ==================== 液态玻璃迁移：底座 GROUP + 行轻量覆盖 + 编辑单元/按钮角色配方 ====================

    /**
     * 默认路径：底座=GROUP、添加=BUTTON_STANDARD、删除=BUTTON_DANGER、行内 key/value
     * 输入=INPUT，各表面 background/border/borderWidth/cornerRadius/elevation/backdrop
     * 材质+模糊逐项等于所选 Role 配方；标题/表头/按钮文字取主题语义前景。
     */
    @Test
    public void defaultSurfacesShouldEqualThemeRoleRecipes() {
        doLayout();

        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle standard = SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);
        SceneSurfaceStyle danger = SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_DANGER);
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());
        Assert.assertNotNull("前置：DANGER 配方自带滤镜", danger.getBackdrop());
        Assert.assertNotNull("前置：INPUT 配方自带滤镜", input.getBackdrop());

        assertRecipe("底座", group, listViewport());
        assertRecipe("添加按钮", standard, addButton());
        assertRecipe("删除按钮", danger, deleteButton(0));
        assertRecipe("key 输入单元", input, keyInputRoot(0));
        assertRecipe("value 输入单元", input, valueInputRoot(1));

        Assert.assertEquals("标题 = 主题正文前景", SceneThemes.DEFAULT.foreground(), titleLabel().getTextColor());
        Assert.assertEquals("表头 = 主题次要前景", SceneThemes.DEFAULT.mutedForeground(),
                findText(root, "Key").getTextColor());
        Assert.assertEquals("添加按钮文字 = BUTTON_STANDARD 配方 foreground",
                standard.getForeground().intValue(), addButton().__getChildren().get(0).getTextColor());
        Assert.assertEquals("删除按钮文字 = BUTTON_DANGER 配方 foreground",
                danger.getForeground().intValue(), deleteButton(0).__getChildren().get(0).getTextColor());
    }

    /**
     * 每颗表面只采样一次滤镜：底座恰好一条 BACKDROP；行零颗（不装滤镜、不写圆角/边框宽，
     * 只做轻量底色覆盖）；输入单元与按钮各自恰好一条（复用已主题化控件，不重复装玻璃）。
     */
    @Test
    public void rowsCarryNoBackdropAndEachSurfaceSamplesOnce() {
        doLayout();
        paintEngine.paint(sceneRoot);

        SceneNode viewport = listViewport();
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(viewport));
        for (int i = 0; i < 2; i++) {
            SceneNode row = row(i);
            Assert.assertNull("行[" + i + "] 不装滤镜", row.getBackdrop());
            Assert.assertEquals("行[" + i + "] 自身零 BACKDROP", 0, backdropCount(row));
            Assert.assertEquals("行[" + i + "] 不写圆角（外观归轻量覆盖）", 0, row.getCornerRadius());
            Assert.assertEquals("行[" + i + "] 不写边框宽", 0, row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 默认透明露出底座玻璃", 0, row.getBackgroundColor());
            Assert.assertEquals("行[" + i + "] key 输入恰好一条 BACKDROP", 1, backdropCount(keyInputRoot(i)));
            Assert.assertEquals("行[" + i + "] value 输入恰好一条 BACKDROP", 1, backdropCount(valueInputRoot(i)));
            Assert.assertEquals("行[" + i + "] 删除按钮恰好一条 BACKDROP", 1, backdropCount(deleteButton(i)));
        }
        Assert.assertEquals("添加按钮恰好一条 BACKDROP", 1, backdropCount(addButton()));
    }

    /**
     * 真实进入编辑模式：点击聚焦并输入后，编辑单元外观仍是 INPUT 配方（染色/圆角/边框宽/
     * elevation/滤镜材质+模糊不变，缘色切到配方 focusEdge），草稿写回受控 rows signal。
     */
    @Test
    public void editModeShouldKeepThemedEditorAppearance() {
        doLayout();
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        SceneNode editor = keyInputRoot(0);

        focusInput(editor);
        harness.typeText("X");
        runtime.flush();
        Assert.assertEquals("编辑草稿写回受控 rows", "nameX", rowsSignal.get().get(0).getKey());

        // 指针移出画布清除 hover，验证非 hover 档仍是 INPUT 配方
        harness.moveAt(CANVAS_WIDTH + 20, CANVAS_HEIGHT + 20);
        runtime.flush();

        Assert.assertEquals("编辑中染色仍 = INPUT 配方 idle tint",
                input.getIdle().getTint(), editor.getBackgroundColor());
        Assert.assertEquals("编辑中圆角仍 = INPUT 配方", input.getCornerRadius(), editor.getCornerRadius());
        Assert.assertEquals("编辑中边框宽仍 = INPUT 配方", input.getBorderWidth(), editor.getBorderWidth());
        Assert.assertEquals("编辑中实体高度仍 = INPUT 配方 idle elevation",
                input.getIdle().getElevation(), editor.__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("聚焦缘色 = INPUT 配方 focusEdge", input.getFocusEdge(), editor.getBorderColor());
        Assert.assertNotNull("编辑中仍带 INPUT 滤镜", editor.getBackdrop());
        Assert.assertEquals("编辑中滤镜材质仍 = INPUT 配方",
                input.getBackdrop().getEffect().getMaterial(), editor.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("编辑中滤镜模糊仍 = INPUT 配方",
                input.getBackdrop().getBlurRadius(), editor.getBackdrop().getBlurRadius());
    }

    /**
     * 主题切换（withTheme，切换前后 GROUP/errorText 配方值确实不同）：底座、行错误底色、
     * 编辑单元、标题/表头/按钮文字全部随来源主题重派生；节点身份不变、编辑草稿与校验状态
     * 与行序保留、effect 数不增长、行仍不装滤镜。
     */
    @Test
    public void themeSwitchShouldUpdateSurfacesWithoutLosingDraftOrValidation() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());
        Assert.assertNotEquals("两档 errorText 必须不同", dark.errorText(), light.errorText());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        remountInTheme(pageTheme, defaultProps());

        // 校验失败行：点号 key 经受控 signal 写入（与既有校验用例同路径）
        List<KeyValueRow> dotRows = new ArrayList<KeyValueRow>(rowsSignal.get());
        dotRows.set(0, dotRows.get(0).copyWith("user.name", "qz", ValueType.STRING));
        rowsSignal.set(Collections.unmodifiableList(dotRows));
        runtime.flush();
        doLayout();

        // 编辑草稿：在被标记的失败行 key 输入单元继续输入
        SceneNode editor = keyInputRoot(0);
        focusInput(editor);
        harness.typeText("X");
        runtime.flush();
        Assert.assertEquals("前置：草稿已写入受控 signal", "user.nameX", rowsSignal.get().get(0).getKey());
        Assert.assertEquals("前置：校验回调反馈点号 key",
                ValidationErrorType.KEY_CONTAINS_DOT, lastValidationError.getType());
        // 指针移出画布清除 hover
        harness.moveAt(CANVAS_WIDTH + 20, CANVAS_HEIGHT + 20);
        runtime.flush();

        SceneNode viewport = listViewport();
        SceneNode firstRow = row(0);
        Assert.assertEquals("初始底座 = 深色 GROUP idle tint", darkGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("初始标题 = 深色正文前景", dark.foreground(), titleLabel().getTextColor());
        Assert.assertEquals("初始表头 = 深色次要前景", dark.mutedForeground(), findText(root, "Key").getTextColor());
        Assert.assertEquals("初始错误行 = 深色 errorText 弱提示",
                tint(dark.errorText(), ERROR_ROW_ALPHA), firstRow.getBackgroundColor());
        // G19/P-02 收编钉：底色 RGB 通道 = 公共 errorText 入口现值（控件仅遮 alpha）。
        Assert.assertEquals("错误行底色 RGB = SceneThemes.errorText 公共入口现值",
                SceneThemes.errorText(runtime).get().intValue() & 0x00FFFFFF,
                firstRow.getBackgroundColor() & 0x00FFFFFF);
        Assert.assertEquals("未失败行透明", 0, row(1).getBackgroundColor());
        Assert.assertEquals("初始编辑单元 = 深色 INPUT idle tint",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), editor.getBackgroundColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("底座染色随主题更新", lightGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座缘色随主题更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(), viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("标题文字随主题更新", light.foreground(), titleLabel().getTextColor());
        Assert.assertEquals("表头文字随主题更新", light.mutedForeground(), findText(root, "Key").getTextColor());
        Assert.assertEquals("编辑单元随主题更新",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(), editor.getBackgroundColor());
        Assert.assertEquals("添加按钮文字随主题更新",
                light.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground().intValue(),
                addButton().__getChildren().get(0).getTextColor());
        Assert.assertEquals("删除按钮文字随主题更新",
                light.surface(SceneTheme.Role.BUTTON_DANGER).getForeground().intValue(),
                deleteButton(0).__getChildren().get(0).getTextColor());
        Assert.assertEquals("校验状态保留：错误行底色随主题重派生",
                tint(light.errorText(), ERROR_ROW_ALPHA), firstRow.getBackgroundColor());

        Assert.assertSame("主题切换不重建 viewport", viewport, listViewport());
        Assert.assertSame("主题切换不重建行节点", firstRow, row(0));
        Assert.assertSame("主题切换不重建编辑单元", editor, keyInputRoot(0));
        Assert.assertEquals("主题切换不丢草稿", "user.nameX", rowsSignal.get().get(0).getKey());
        Assert.assertEquals("主题切换不丢草稿显示", "user.nameX", inputValue(keyInputRoot(0)));
        Assert.assertEquals("行序不丢：第二行仍是 count", "count", inputValue(keyInputRoot(1)));
        Assert.assertEquals("校验状态不丢：错误类型仍为点号 key",
                ValidationErrorType.KEY_CONTAINS_DOT, lastValidationError.getType());
        Assert.assertNull("切换后行仍不装滤镜", row(0).getBackdrop());
        Assert.assertEquals("主题切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 卸载后表面绑定 effect 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountShouldReleaseSurfaceBindings() {
        handle.dispose();
        runtime.flush();
        int baseline = ReactiveTestProbe.registeredEffectCount();

        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        remountInTheme(pageTheme, defaultProps());
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode viewport = listViewport();
        int colorBeforeDispose = viewport.getBackgroundColor();
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后外观绑定 effect 应回收", baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧 viewport",
                colorBeforeDispose, viewport.getBackgroundColor());
    }

    /** 默认外观用例共用输入契约（标题 + 占位 + 回调计数）。 */
    private SceneKeyValueMap.Props defaultProps() {
        return SceneKeyValueMap.Props.builder(rowsSignal)
                .label("属性")
                .keyPlaceholder("键")
                .valuePlaceholder("值")
                .onRowsChanged(rows -> rowsChangedCount.incrementAndGet())
                .onValidationError(error -> {
                    validationCount.incrementAndGet();
                    lastValidationError = error;
                })
                .build();
    }

    /**
     * 在可切换局部主题作用域内重新挂载被测控件（主题信号变化只重派生外观，不重建节点）。
     */
    private void remountInTheme(Signal<SceneTheme> pageTheme, SceneKeyValueMap.Props props) {
        handle.dispose();
        final SceneNode[] holder = new SceneNode[1];
        handle = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneKeyValueMap.create(runtime, props).get());
            return holder[0];
        });
        root = handle.getRoot();
        rowsChangedCount.set(0);
        validationCount.set(0);
        lastValidationError = null;
        runtime.flush();
        doLayout();
    }

    /** 控件标题文字节点。 */
    private SceneNode titleLabel() {
        SceneNode label = findText(root, "属性");
        if (label == null) {
            throw new AssertionError("未找到控件标题节点");
        }
        return label;
    }

    /**
     * 递归查找第一个文本等于 {@code text} 的节点。
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

    /** 保留色 RGB、替换 alpha 通道（行轻量覆盖口径）。 */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** 断言节点表面六项逐项等于角色配方（idle 档，未 hover/press/focus）。 */
    private static void assertRecipe(String name, SceneSurfaceStyle style, SceneNode node) {
        Assert.assertEquals(name + " 染色 = 配方 idle tint", style.getIdle().getTint(), node.getBackgroundColor());
        Assert.assertEquals(name + " 缘色 = 配方 idle edge", style.getIdle().getEdge(), node.getBorderColor());
        Assert.assertEquals(name + " 边框宽 = 配方", style.getBorderWidth(), node.getBorderWidth());
        Assert.assertEquals(name + " 圆角 = 配方", style.getCornerRadius(), node.getCornerRadius());
        Assert.assertEquals(name + " 实体高度 = 配方 idle elevation",
                style.getIdle().getElevation(), node.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull(name + " 默认带液态玻璃滤镜", node.getBackdrop());
        Assert.assertEquals(name + " 滤镜材质 = 配方",
                style.getBackdrop().getEffect().getMaterial(), node.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(name + " 滤镜模糊 = 配方",
                style.getBackdrop().getBlurRadius(), node.getBackdrop().getBlurRadius());
    }

    /** 跑一帧布局（经 harness.mountRoot 刷新路由根 + absoluteBox，供 harness.click 取中心）。 */
    private void doLayout() {
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
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
        SceneNode found = listViewport(root);
        if (found == null) {
            throw new AssertionError("未找到滚动视口");
        }
        return found;
    }

    /** 无标题时的列表滚动视口（与有标题共用同一递归定位）。 */
    private SceneNode listViewportWithoutLabel() {
        return listViewport(root);
    }

    /**
     * 递归查找子树中第一个 isScrollable 节点。
     *
     * <p>viewport 现嵌套在 stackHost(ROW) 内，不再是 root 直接子，需递归定位。</p>
     *
     * @param node 子树根
     * @return 第一个可滚动节点，未找到抛断言
     */
    private SceneNode listViewport(SceneNode node) {
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            if (child.isScrollable()) {
                return child;
            }
            SceneNode found = listViewport(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** @return 承载 viewport 与可选滚动条的 stackHost（viewport 的父节点）。 */
    private SceneNode stackHost() {
        return listViewport().__getParent();
    }

    /** 添加按钮。 */
    private SceneNode addButton() {
        return root.__getChildren().get(4);
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
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText()
                + input.__getChildren().get(4).getText();
    }

    /** 类型分段节点。 */
    private SceneNode typeSegment(int rowIndex, int typeIndex) {
        return row(rowIndex).__getChildren().get(2).__getChildren().get(0).__getChildren().get(typeIndex);
    }

    /** 删除按钮。 */
    private SceneNode deleteButton(int rowIndex) {
        return row(rowIndex).__getChildren().get(3);
    }

    /** 聚焦输入框并把 caret 移到末尾（click 聚焦 + END 跳末，分两步语义化注入）。 */
    private void focusInput(SceneNode input) {
        harness.click(input);
        harness.pressKey(SceneKey.END);
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
