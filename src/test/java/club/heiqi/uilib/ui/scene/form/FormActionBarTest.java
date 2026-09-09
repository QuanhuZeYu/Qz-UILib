package club.heiqi.uilib.ui.scene.form;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FormActionBar} 单元测试。
 *
 * <p><b>默认路径</b>（不传 {@link FormTheme}）：操作条 root 表面逐项等于来源主题
 * {@link SceneTheme.Role#TOOLBAR} 配方（染色/边框/边框宽/圆角/浮雕/滤镜），绑定器为唯一写入者；
 * 条上文字（按钮文案）只读复用已主题化的 {@code SceneButton}，标准按钮取主题
 * {@code foreground}、primary 取 {@code onAccentForeground}（BUTTON_* 角色配方前景）；
 * 整树每颗语义表面恰好一条 {@code BACKDROP}（条 1 + 按钮 3，不叠第二层玻璃）；
 * 来源主题切换只重派生（root/按钮节点身份与订阅数不变）；卸载回收全部外观绑定。</p>
 *
 * <p><b>显式路径</b>（旧 {@link FormTheme} 重载）：root 零外观写入、不装滤镜、不订阅主题，
 * 语义与迁移前一致（caller 挂回后的静态设色继续独占生效）；旧签名全部保留可编译。</p>
 *
 * <p><b>行为保持</b>：两条路径的结构、子序（恢复/spacer/取消/保存）、高度/间距/按钮宽高，
 * enabled 信号直通按钮（禁用档背景/内容透明度），点击回调按按钮归位触发，均不随迁移变化。</p>
 *
 * <p>分层：build 内部经 {@link SceneRuntime} mount SceneButton 与注册表面绑定，属 L3 集成范畴，
 * 照 {@code FormPageShellTest} 范式直接 new SceneRuntime，放 form 同包（白盒测试留对应包）。</p>
 */
public class FormActionBarTest {

    private static final float EPSILON = 0.0001F;
    private static final String RESTORE_LABEL = "恢复默认";
    private static final String CANCEL_LABEL = "取消更改";
    private static final String SAVE_LABEL = "保存";

    /** 场景运行时（build 消费方），每用例独立 new，避免跨用例污染。 */
    private SceneRuntime runtime;
    /** 布局与绘制引擎（PaintPlan 断言用）。 */
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（click 入口），默认复用上方 runtime。 */
    private SceneInteractionHarness harness;

    /** 默认主题的操作条角色配方：默认路径外观唯一来源，断言引用配方值而非硬编码色号。 */
    private static final SceneSurfaceStyle TOOLBAR_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    private static final SceneSurfaceStyle BUTTON_STANDARD =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);
    private static final SceneSurfaceStyle BUTTON_PRIMARY =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_PRIMARY);

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 装配工具 ====================

    /** 恒真/恒假只读信号（便捷构造）。 */
    private static ReadableSignal<Boolean> constSignal(boolean value) {
        return () -> Boolean.valueOf(value);
    }

    /** 默认路径便捷构建（不传 FormTheme），全部 enabled 恒真、回调记名。 */
    private static SceneNode buildDefault(SceneRuntime rt, List<String> log) {
        return FormActionBar.build(rt,
                () -> log.add("restore"),
                constSignal(true), () -> log.add("cancel"),
                constSignal(true), () -> log.add("save"));
    }

    /** 在可切换局部主题作用域内构建默认路径操作条并返回挂载句柄。 */
    private MountHandle mountDefault(Signal<SceneTheme> pageTheme, SceneNode sceneRoot,
                                     final SceneNode[] holder, List<String> log) {
        return runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = buildDefault(runtime, log));
            return holder[0];
        });
    }

    /** 递归查找指定文本的节点；不存在返回 null。 */
    private static SceneNode findByText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findByText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 统计整棵子树 PaintPlan 中的 BACKDROP 命令数。 */
    private int backdropCount(SceneNode root) {
        PaintPlan plan = paintEngine.paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数（须先对含该节点的树执行过 paint）。 */
    private static int ownBackdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        Assert.assertTrue("节点应已绘制出自身 fragment", cached instanceof PaintFragment);
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 默认路径：root 表面 = TOOLBAR 配方 ====================

    /**
     * 默认路径（无局部主题，走库默认）：root 六项表面属性逐项等于 TOOLBAR 配方，
     * 不再保持旧「零外观写入」的裸行外观。
     */
    @Test
    public void defaultBuildBindsToolbarRecipeOnRoot() {
        SceneNode sceneRoot = SceneNode.column();
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(sceneRoot,
                () -> holder[0] = buildDefault(runtime, new ArrayList<String>()));
        runtime.flush();

        SceneNode row = holder[0];
        Assert.assertNotNull("前置：TOOLBAR 配方自带滤镜", TOOLBAR_SURFACE.getBackdrop());
        Assert.assertEquals("root 背景 = TOOLBAR idle 染色",
                TOOLBAR_SURFACE.getIdle().getTint(), row.getBackgroundColor());
        Assert.assertEquals("root 边框色 = TOOLBAR idle 缘色",
                TOOLBAR_SURFACE.getIdle().getEdge(), row.getBorderColor());
        Assert.assertEquals("root 边框宽 = TOOLBAR 配方",
                TOOLBAR_SURFACE.getBorderWidth(), row.getBorderWidth());
        Assert.assertEquals("root 圆角 = TOOLBAR 配方",
                TOOLBAR_SURFACE.getCornerRadius(), row.getCornerRadius());
        Assert.assertEquals("root 浮雕高度 = TOOLBAR idle 实体高度",
                TOOLBAR_SURFACE.getIdle().getElevation(), row.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("root 装 TOOLBAR 滤镜", row.getBackdrop());
        Assert.assertEquals("root 滤镜模糊半径 = 配方",
                TOOLBAR_SURFACE.getBackdrop().getBlurRadius(), row.getBackdrop().getBlurRadius());
        Assert.assertEquals("root 滤镜材质 = 配方",
                TOOLBAR_SURFACE.getBackdrop().getEffect().getMaterial(),
                row.getBackdrop().getEffect().getMaterial());
        handle.dispose();
    }

    /** 默认路径：在 withTheme 局部作用域内构建同样消费来源主题 TOOLBAR 配方。 */
    @Test
    public void defaultBuildInsideWithThemeConsumesSourceToolbarRecipe() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        SceneSurfaceStyle lightToolbar = pageTheme.get().surface(SceneTheme.Role.TOOLBAR);
        Assert.assertNotEquals("前置：浅色档 TOOLBAR 配方与库默认不同",
                TOOLBAR_SURFACE.getIdle(), lightToolbar.getIdle());

        SceneNode sceneRoot = SceneNode.column();
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(pageTheme, sceneRoot, holder, new ArrayList<String>());
        runtime.flush();

        SceneNode row = holder[0];
        Assert.assertEquals("root 背景 = 来源主题 TOOLBAR idle 染色",
                lightToolbar.getIdle().getTint(), row.getBackgroundColor());
        Assert.assertEquals("root 边框色 = 来源主题 TOOLBAR idle 缘色",
                lightToolbar.getIdle().getEdge(), row.getBorderColor());
        Assert.assertEquals("root 圆角 = 来源主题 TOOLBAR 配方",
                lightToolbar.getCornerRadius(), row.getCornerRadius());
        handle.dispose();
    }

    /** 默认路径：条上文字（标准按钮文案）取来源主题前景，primary 取强调底前景（复用 Button 角色配方）。 */
    @Test
    public void defaultBuildButtonLabelsTakeThemeForeground() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Assert.assertNotNull("前置：BUTTON_STANDARD 配方带主题前景",
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground());
        Assert.assertEquals("前置：标准按钮前景 = 主题正文色",
                dark.foreground(),
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground().intValue());
        Assert.assertNotEquals("前置：primary 前景与正文色可区分",
                dark.foreground(), dark.onAccentForeground());

        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(dark), SceneNode.column(), holder,
                new ArrayList<String>());
        runtime.flush();

        SceneNode row = holder[0];
        Assert.assertEquals("恢复默认（standard）文字 = 主题 foreground",
                dark.foreground(), findByText(row, RESTORE_LABEL).getTextColor());
        Assert.assertEquals("取消更改（standard）文字 = 主题 foreground",
                dark.foreground(), findByText(row, CANCEL_LABEL).getTextColor());
        Assert.assertEquals("保存（primary）文字 = BUTTON_PRIMARY 配方前景（强调底前景）",
                dark.onAccentForeground(), findByText(row, SAVE_LABEL).getTextColor());
        handle.dispose();
    }

    // ==================== PaintPlan：每颗表面只采样一次 ====================

    /**
     * 默认路径：操作条 root 自身恰好一条 {@code BACKDROP}；整树 = 条 1 + 三颗按钮各 1
     * （按钮玻璃来自只读复用的 SceneButton，操作条不叠第二层玻璃、不重复采样）。
     */
    @Test
    public void defaultPaintPlanEmitsOneBackdropPerSemanticSurface() {
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = mountDefault(Signal.create(SceneTheme.liquidGlassDark()),
                sceneRoot, holder, new ArrayList<String>());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(420, 120));
        // 先整树 paint（填充各节点 fragment 缓存），再断言 root 自身 fragment
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();

        Assert.assertEquals("操作条 root 自身恰好一条 BACKDROP（不重复采样）",
                1, ownBackdropCount(holder[0]));
        int treeCount = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                treeCount++;
            }
        }
        Assert.assertEquals("整树 BACKDROP = 条 1 + 恢复 1 + 取消 1 + 保存 1（每颗表面只采样一次）",
                4, treeCount);
        handle.dispose();
    }

    /** 关闭滤镜档（withoutBackdrop）：替代底色不透明可读，整树零 BACKDROP。 */
    @Test
    public void withoutBackdropThemeEmitsNoBackdropAndKeepsReadableTint() {
        SceneTheme noBackdrop = SceneTheme.liquidGlassDark().withoutBackdrop();
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = mountDefault(Signal.create(noBackdrop), sceneRoot, holder,
                new ArrayList<String>());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(420, 120));

        SceneNode row = holder[0];
        Assert.assertNull("关闭滤镜时不装滤镜", row.getBackdrop());
        Assert.assertEquals("替代底色不透明可读", 0xFF, (row.getBackgroundColor() >>> 24) & 0xFF);
        Assert.assertEquals("关闭滤镜时不发 BACKDROP 命令", 0, backdropCount(sceneRoot));
        Assert.assertEquals("圆角与边框仍由同一配方给出",
                noBackdrop.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), row.getCornerRadius());
        handle.dispose();
    }

    // ==================== 主题切换：只重派生，不重建 ====================

    /** 主题切换（dark→light，TOOLBAR 配方确值不同）：root 更新、节点身份与订阅数不变。 */
    @Test
    public void themeSwitchRebindsToolbarSurfaceWithoutRebuildingNodes() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkToolbar = dark.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle lightToolbar = light.surface(SceneTheme.Role.TOOLBAR);
        Assert.assertNotEquals("前置：两档 TOOLBAR 配方必须不同，否则切换不传播",
                darkToolbar.getIdle(), lightToolbar.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(pageTheme, SceneNode.column(), holder,
                new ArrayList<String>());
        runtime.flush();

        SceneNode row = holder[0];
        List<SceneNode> childrenBefore = new ArrayList<SceneNode>(row.__getChildren());
        SceneNode restoreRoot = childrenBefore.get(0);
        Assert.assertEquals("前置：深色档 root 背景",
                darkToolbar.getIdle().getTint(), row.getBackgroundColor());
        Assert.assertEquals("前置：标准按钮文字 = 深色主题正文色",
                dark.foreground(), findByText(row, RESTORE_LABEL).getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后 root 背景 = 浅色 TOOLBAR idle 染色",
                lightToolbar.getIdle().getTint(), row.getBackgroundColor());
        Assert.assertEquals("切换后 root 边框色 = 浅色 TOOLBAR idle 缘色",
                lightToolbar.getIdle().getEdge(), row.getBorderColor());
        Assert.assertEquals("切换后标准按钮文字 = 浅色主题正文色",
                light.foreground(), findByText(row, RESTORE_LABEL).getTextColor());
        Assert.assertSame("主题切换不重建操作条 root", row, holder[0]);
        Assert.assertEquals("主题切换不增删子节点", childrenBefore.size(), row.__getChildren().size());
        for (int i = 0; i < childrenBefore.size(); i++) {
            Assert.assertSame("子节点[" + i + "]身份不变", childrenBefore.get(i), row.__getChildren().get(i));
        }
        Assert.assertSame("按钮根身份不变", restoreRoot, row.__getChildren().get(0));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    // ==================== 显式路径：旧 FormTheme 语义不变 ====================

    /**
     * 显式 {@link FormTheme} 路径：root 零外观写入（背景/边框/圆角/滤镜/浮雕全为默认值）、
     * 不订阅主题——来源主题切换后一切不变（caller 挂回后静态设色继续独占，与迁移前一致）。
     */
    @Test
    public void explicitThemePathStaysStaticAndUnsubscribed() {
        FormTheme explicit = FormTheme.defaultDark();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        final SceneNode[] holder = new SceneNode[1];

        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = FormActionBar.build(
                    runtime,
                    constSignal(true), () -> { },
                    constSignal(true), () -> { },
                    constSignal(true), () -> { },
                    explicit, 36, 10, 110, SceneChromeTokens.BUTTON_HEIGHT));
            return holder[0];
        });
        runtime.flush();

        SceneNode row = holder[0];
        Assert.assertEquals("显式路径 root 背景保持默认（零外观写入）", 0, row.getBackgroundColor());
        Assert.assertEquals("显式路径 root 边框宽保持默认", 0, row.getBorderWidth());
        Assert.assertEquals("显式路径 root 圆角保持默认", 0, row.getCornerRadius());
        Assert.assertNull("显式路径不装滤镜", row.getBackdrop());
        Assert.assertEquals("显式路径不绑浮雕", -1.0F, row.__getSurfaceElevation(), EPSILON);

        pageTheme.set(SceneTheme.liquidGlassDark());
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        runtime.flush();
        Assert.assertEquals("主题切换不改显式 root 背景", 0, row.getBackgroundColor());
        Assert.assertNull("主题切换不给显式 root 加出滤镜", row.getBackdrop());
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 旧签名可编译性回归：7 参与 12 参 {@link FormTheme} 重载都保持原调用形态。 */
    @Test
    public void legacySignaturesRemainCallable() {
        FormTheme explicit = FormTheme.defaultDark();
        final SceneNode[] holder = new SceneNode[3];
        MountHandle[] handles = new MountHandle[3];
        handles[0] = runtime.mount(new SceneNode(), () -> holder[0] = FormActionBar.build(
                runtime, () -> { }, constSignal(true), () -> { }, constSignal(true), () -> { }, explicit));
        handles[1] = runtime.mount(new SceneNode(), () -> holder[1] = FormActionBar.build(
                runtime, constSignal(true), () -> { }, constSignal(true), () -> { },
                constSignal(true), () -> { }, explicit, 40, 8, 96, 24));
        handles[2] = runtime.mount(new SceneNode(), () -> holder[2] = FormActionBar.build(
                runtime, () -> { }, constSignal(true), () -> { }, constSignal(true), () -> { }));
        runtime.flush();

        for (SceneNode row : holder) {
            Assert.assertNotNull("旧/新签名均返回非 null 操作条", row);
            Assert.assertEquals("子序不变：恢复/spacer/取消/保存", 4, row.__getChildren().size());
            Assert.assertEquals("高度契约不变", row == holder[1] ? 40 : 36, row.getPreferredHeight());
        }
        Assert.assertEquals("显式 7 参重载仍为裸外观", 0, holder[0].getBackgroundColor());
        Assert.assertNull("显式 12 参重载不装滤镜", holder[1].getBackdrop());
        Assert.assertNotNull("默认 6 参重载装 TOOLBAR 滤镜", holder[2].getBackdrop());
        for (MountHandle handle : handles) {
            handle.dispose();
        }
    }

    // ==================== 行为保持：布局/enabled/回调 ====================

    /** 两条路径的布局与结构完全一致：子序、spacer、按钮宽高、条高/间距（默认 6 参 vs 旧 7 参）。 */
    @Test
    public void bothPathsKeepLayoutStructureAndSizing() {
        final SceneNode[] holder = new SceneNode[2];
        MountHandle defaultHandle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> holder[0] = buildDefault(runtime, new ArrayList<String>()));
            return holder[0];
        });
        MountHandle explicitHandle = runtime.mount(new SceneNode(), () -> holder[1] =
                FormActionBar.build(runtime, () -> { }, constSignal(true), () -> { },
                        constSignal(true), () -> { }, FormTheme.defaultDark()));
        runtime.flush();

        SceneNode themedRow = holder[0];
        SceneNode legacyRow = holder[1];
        Assert.assertEquals("条高不变", 36, themedRow.getPreferredHeight());
        Assert.assertEquals("条高两路径一致", legacyRow.getPreferredHeight(), themedRow.getPreferredHeight());
        Assert.assertEquals("间距不变（GAP_MD）",
                SceneChromeTokens.GAP_MD, themedRow.getGap());
        Assert.assertEquals("间距两路径一致", legacyRow.getGap(), themedRow.getGap());

        for (SceneNode row : new SceneNode[] { themedRow, legacyRow }) {
            List<SceneNode> children = row.__getChildren();
            Assert.assertEquals("四子结构一致", 4, children.size());
            SceneNode spacer = children.get(1);
            Assert.assertEquals("spacer flexGrow 不变", 1.0F, spacer.getFlexGrow(), EPSILON);
            Assert.assertFalse("spacer 不可命中", spacer.isHitTestable());
            for (int i = 0; i < children.size(); i++) {
                if (i == 1) {
                    continue;
                }
                SceneNode button = children.get(i);
                Assert.assertEquals("按钮宽不变", 110, button.getPreferredWidth());
                Assert.assertEquals("按钮高不变", SceneChromeTokens.BUTTON_HEIGHT,
                        button.getPreferredHeight());
            }
            Assert.assertEquals("取消按钮为 standard 档", BUTTON_STANDARD.getIdle().getTint(),
                    children.get(2).getBackgroundColor());
            Assert.assertEquals("保存按钮为 primary 档", BUTTON_PRIMARY.getIdle().getTint(),
                    children.get(3).getBackgroundColor());
        }
        defaultHandle.dispose();
        explicitHandle.dispose();
    }

    /** 默认路径完整参数重载：自定义条高/间距/按钮宽高照常生效（主题不接管布局）。 */
    @Test
    public void defaultFullArgsOverloadKeepsCustomSizes() {
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> holder[0] = FormActionBar.build(runtime,
                            constSignal(true), () -> { }, constSignal(true), () -> { },
                            constSignal(true), () -> { }, 44, 7, 90, 26));
            return holder[0];
        });
        runtime.flush();

        SceneNode row = holder[0];
        Assert.assertEquals("自定义条高生效", 44, row.getPreferredHeight());
        Assert.assertEquals("自定义间距生效", 7, row.getGap());
        Assert.assertEquals("自定义按钮宽生效", 90, row.__getChildren().get(0).getPreferredWidth());
        Assert.assertEquals("自定义按钮高生效", 26, row.__getChildren().get(0).getPreferredHeight());
        Assert.assertNotNull("自定义尺寸下仍装 TOOLBAR 滤镜", row.getBackdrop());
        handle.dispose();
    }

    /** enabled 语义直通按钮：canSave=false 时保存按钮切禁用档（背景=配方 disabled、内容降透明度）。 */
    @Test
    public void enabledSignalsDriveButtonDisabledState() {
        Signal<Boolean> canSave = Signal.create(Boolean.FALSE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> holder[0] = FormActionBar.build(runtime,
                            () -> { }, constSignal(true), () -> { }, canSave, () -> { }));
            return holder[0];
        });
        runtime.flush();

        SceneNode saveRoot = holder[0].__getChildren().get(3);
        SceneNode saveLabel = findByText(saveRoot, SAVE_LABEL);
        Assert.assertEquals("禁用保存按钮背景 = BUTTON_PRIMARY disabled 档",
                BUTTON_PRIMARY.getDisabled().getTint(), saveRoot.getBackgroundColor());
        Assert.assertEquals("禁用由内容透明度表达",
                BUTTON_PRIMARY.getDisabledOpacity(), saveLabel.getOpacity(), EPSILON);

        canSave.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("恢复启用后背景回 idle 档",
                BUTTON_PRIMARY.getIdle().getTint(), saveRoot.getBackgroundColor());
        Assert.assertEquals("恢复启用后内容不透明", 1.0F, saveLabel.getOpacity(), EPSILON);
        handle.dispose();
    }

    /** 点击回调按按钮归位触发；禁用按钮不触发（与迁移前语义一致）。 */
    @Test
    public void clicksFirePerButtonAndDisabledSwallows() {
        List<String> log = new ArrayList<String>();
        Signal<Boolean> restoreEnabled = Signal.create(Boolean.TRUE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> holder[0] = FormActionBar.build(runtime,
                            restoreEnabled, () -> log.add("restore"),
                            constSignal(true), () -> log.add("cancel"),
                            constSignal(true), () -> log.add("save"),
                            36, 10, 110, SceneChromeTokens.BUTTON_HEIGHT));
            return holder[0];
        });
        runtime.flush();

        SceneNode row = holder[0];
        SceneNode sceneRoot = row.__getParent();
        harness.mountRoot(sceneRoot, 420, 120);
        layoutEngine.layout(sceneRoot, new Constraints(420, 120));

        harness.click(row.__getChildren().get(0));
        harness.click(row.__getChildren().get(2));
        harness.click(row.__getChildren().get(3));
        Assert.assertEquals("三按钮回调各触发一次且归位", "restore,cancel,save", String.join(",", log));

        log.clear();
        restoreEnabled.set(Boolean.FALSE);
        runtime.flush();
        harness.click(row.__getChildren().get(0));
        Assert.assertEquals("disabled 按钮不触发回调", "", String.join(",", log));
        handle.dispose();
    }

    // ==================== 卸载回收 ====================

    /** 卸载回收：默认路径的外观绑定 effect 随 mount 卸载全部退订，主题更新不再写入旧节点。 */
    @Test
    public void unmountReleasesThemeSurfaceBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(pageTheme, SceneNode.column(), holder,
                new ArrayList<String>());
        runtime.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode row = holder[0];
        int colorBeforeDispose = row.getBackgroundColor();
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧操作条",
                colorBeforeDispose, row.getBackgroundColor());
    }
}
