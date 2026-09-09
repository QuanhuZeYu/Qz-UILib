package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
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
 * SceneNavList 单元测试 —— 纵向受控单选导航列表的「行为不变 + 默认外观主题化」验收。
 *
 * <p>行为基线（必须继续通过）：{@code preferredHeight} prop 透传、选中指示条/标签位移的
 * standard Motion 半程与终态、受控单选闭环（点/键盘只上抛期望下标）、方向键导航、禁用拦截、
 * 交互与选中切换零重排。</p>
 *
 * <p>主题化验收（G09/NavList）：默认工厂路径底座取 {@code TOOLBAR} 配方、每个选项取
 * {@code selectableSurface(INDICATOR, selected)} 且<b>各自恰好采样一次背景</b>（保留配方自带的
 * 轻滤镜，契约 §4.1「导航族选项的滤镜口径（G09 裁决）」）、选中项 tint 为强调色系且强度 0x59、
 * 文字取主题正文/禁用前景色、{@code withTheme} 切换后底座/项/文字更新且选中项不丢、节点身份
 * 不变、effect 数不增长、卸载后绑定全部回收。</p>
 */
public class SceneNavListTest {

    private SceneRuntime runtime;
    /** 语义化交互注入 harness；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private static final List<String> OPTIONS = Arrays.asList("General", "Video", "Audio");
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 200;
    private static final float EPSILON = 0.0001F;

    /**
     * 库默认主题的底座/选项配方：默认外观唯一来源。
     * 断言取配方值而不是硬编码色号，主题集中调参时本类自动跟随。
     */
    private static final SceneSurfaceStyle BASE_SURFACE = SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    private static final SceneSurfaceStyle ITEM_SURFACE = SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    private static final int BASE_IDLE = BASE_SURFACE.getIdle().getTint();
    private static final int ITEM_UNSELECTED = ITEM_SURFACE.getIdle().getTint();
    /** 选中档：配方 tint 的 RGB 换成主题强调色、强度取主题统一选中强度 0x59（选中不只靠透明度）。 */
    private static final int ITEM_SELECTED = selectedTint(SceneThemes.DEFAULT.accent());
    private static final int ITEM_DISABLED = ITEM_SURFACE.getDisabled().getTint();
    /** 文字/指示条前景：启用取主题正文色，禁用取主题禁用前景色。 */
    private static final int TEXT_ENABLED = SceneThemes.DEFAULT.foreground();
    private static final int TEXT_DISABLED = SceneThemes.DEFAULT.disabledForeground();

    /*
     * 选中项文字为何取 foreground() 而非 onAccentForeground()（WCAG 相对亮度对比度实算）：
     * 选中项底色是 accent 以 0x59 叠在玻璃合成底上的中间调，不是不透明强调底。
     * 以主题自身不透明替代色作玻璃代理（深色 0xFF2B2930、浅色 0xFFF2F0F5）合成后：
     *   深色合成底 0xFF382E50：foreground 9.71:1，onAccentForeground 9.72:1
     *   浅色合成底 0xFFC1B8D9：foreground 9.08:1，onAccentForeground(白) 1.89:1
     * onAccentForeground 在浅色档远低于可读阈值（纯白底时 1.71:1），而主题正文色在深/浅两档
     * 均 ≥9:1，故选中/未选中统一取 foreground()；选中区分由 tint 染色与指示条承担。
     */

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
    }

    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /**
     * 选中配方语义：RGB 换成强调色、alpha 用主题统一选中强度
     * {@code SceneThemes.SELECTED_TINT_ALPHA}(0x59)——不沿用角色配方的低 alpha。
     */
    private static int selectedTint(int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    private static int backdropCount(ScenePaintEngine engine, SceneNode node) {
        PaintPlan plan = engine.paint(node).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    private LayoutResult doLayout(SceneNode root) {
        return layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** item[i]（root 第 i 个孩子） */
    private static SceneNode itemNode(SceneNode root, int i) {
        return root.__getChildren().get(i);
    }

    /** item[i] 的选中指示条（item 第一个孩子） */
    private static SceneNode indicatorNode(SceneNode root, int i) {
        return itemNode(root, i).__getChildren().get(0);
    }

    /** item[i] 的标签（item 第二个孩子） */
    private static SceneNode labelNode(SceneNode root, int i) {
        return itemNode(root, i).__getChildren().get(1);
    }

    private MountHandle mountNav(SceneNode parent, Signal<Integer> selected, Signal<Boolean> enabled,
            Integer preferredHeight) {
        SceneNavList.Props props = new SceneNavList.Props(selected, OPTIONS, enabled, idx -> { }, preferredHeight);
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        runtime.flush();
        return handle;
    }

    // ==================== 行为基线：preferredHeight 透传 ====================

    /** preferredHeight 非 null 时应透传到 root。 */
    @Test
    public void nonNullPreferredHeightShouldPassThroughToRoot() {
        SceneNode parent = new SceneNode();
        SceneNavList.Props props = new SceneNavList.Props(
                Signal.create(Integer.valueOf(0)),
                OPTIONS,
                Signal.create(Boolean.TRUE),
                idx -> { },
                Integer.valueOf(200));
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        runtime.flush();
        Assert.assertEquals("preferredHeight=200 应透传到 root", 200, handle.getRoot().getPreferredHeight());
    }

    /** preferredHeight 为 null 时 root 保持默认 0（不设，由布局链决定）。 */
    @Test
    public void nullPreferredHeightShouldLeaveRootDefault() {
        SceneNode parent = new SceneNode();
        SceneNavList.Props props = new SceneNavList.Props(
                Signal.create(Integer.valueOf(0)),
                OPTIONS,
                Signal.create(Boolean.TRUE),
                idx -> { },
                null);
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        runtime.flush();
        Assert.assertEquals("preferredHeight=null 时 root 保持默认 0", 0, handle.getRoot().getPreferredHeight());
    }

    // ==================== 行为基线：选中指示条/标签位移的 Motion 时序 ====================

    @Test
    public void selectionMotionShouldMoveIndicatorAndLabelAtStandardDuration() {
        runtime.__enableMotion();
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        SceneNode parent = new SceneNode();
        SceneNode root = mountNav(parent, selected, Signal.create(Boolean.TRUE), null).getRoot();

        Assert.assertEquals("选中项背景 = 强调色系选中档", ITEM_SELECTED, itemNode(root, 0).getBackgroundColor());
        Assert.assertEquals("未选中项背景 = 配方 idle 档", ITEM_UNSELECTED, itemNode(root, 1).getBackgroundColor());
        Assert.assertEquals(1.0f, indicatorNode(root, 0).getTransform().scaleY, EPSILON);
        Assert.assertEquals(0.0f, indicatorNode(root, 1).getTransform().scaleY, EPSILON);
        Assert.assertEquals(4.0f, labelNode(root, 0).getTransform().translateX, EPSILON);
        Assert.assertEquals(0.0f, labelNode(root, 1).getTransform().translateX, EPSILON);
        Assert.assertTrue("交互命中保持在完整 item", itemNode(root, 0).isHitTestable());
        Assert.assertFalse("indicator 不得截获 item 点击", indicatorNode(root, 0).isHitTestable());
        Assert.assertFalse("位移 label 不得改变 item 点击目标", labelNode(root, 0).isHitTestable());

        selected.set(Integer.valueOf(1));
        runtime.flush();
        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(81_000_000L);
        Assert.assertNotEquals("旧项背景半程离开选中档", ITEM_SELECTED, itemNode(root, 0).getBackgroundColor());
        Assert.assertNotEquals("新项背景半程进入选中档", ITEM_UNSELECTED, itemNode(root, 1).getBackgroundColor());
        Assert.assertEquals("旧 indicator 半程收起", 0.5f,
                indicatorNode(root, 0).getTransform().scaleY, EPSILON);
        Assert.assertEquals("新 indicator 半程展开", 0.5f,
                indicatorNode(root, 1).getTransform().scaleY, EPSILON);
        Assert.assertEquals("旧 label 半程归位", 2.0f,
                labelNode(root, 0).getTransform().translateX, EPSILON);
        Assert.assertEquals("新 label 半程移入", 2.0f,
                labelNode(root, 1).getTransform().translateX, EPSILON);

        runtime.__sampleMotion(161_000_000L);
        Assert.assertEquals("旧项回到未选中档", ITEM_UNSELECTED, itemNode(root, 0).getBackgroundColor());
        Assert.assertEquals("新项落到选中档", ITEM_SELECTED, itemNode(root, 1).getBackgroundColor());
        Assert.assertEquals(0.0f, indicatorNode(root, 0).getTransform().scaleY, EPSILON);
        Assert.assertEquals(1.0f, indicatorNode(root, 1).getTransform().scaleY, EPSILON);
        Assert.assertEquals(0.0f, labelNode(root, 0).getTransform().translateX, EPSILON);
        Assert.assertEquals(4.0f, labelNode(root, 1).getTransform().translateX, EPSILON);
    }

    // ==================== 默认工厂路径：底座 TOOLBAR + 选项 INDICATOR 选中配方 ====================

    /**
     * 默认工厂路径（不传任何样式参数）：底座外观等于 {@code SceneThemes.DEFAULT.surface(TOOLBAR)}、
     * 每个选项等于 {@code selectableSurface(INDICATOR, selected)}——选中项 tint 的 RGB 为强调色、
     * 强度 0x59（选中不只靠透明度）、未选中项保持配方极淡 tint；文字与指示条取主题正文色。
     */
    @Test
    public void defaultFactoryShouldConsumeToolbarBaseAndIndicatorItemRecipe() {
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        SceneNode root = mountNav(new SceneNode(), selected, Signal.create(Boolean.TRUE), null).getRoot();
        doLayout(root);

        // 底座：TOOLBAR 配方独占背景/边框/圆角/滤镜/实体高度
        Assert.assertEquals("底座背景 = TOOLBAR idle 染色", BASE_IDLE, root.getBackgroundColor());
        Assert.assertEquals("底座边框色 = TOOLBAR idle 缘色", BASE_SURFACE.getIdle().getEdge(), root.getBorderColor());
        Assert.assertEquals("底座边框宽 = 配方", BASE_SURFACE.getBorderWidth(), root.getBorderWidth());
        Assert.assertEquals("底座圆角 = 配方（不再静态设值）", BASE_SURFACE.getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals("底座实体高度 = 配方 idle 档", BASE_SURFACE.getIdle().getElevation(),
                root.__getSurfaceElevation(), EPSILON);
        UiBackdrop baseRecipeBackdrop = BASE_SURFACE.getBackdrop();
        Assert.assertNotNull("底座默认带液态玻璃滤镜", root.getBackdrop());
        Assert.assertEquals("底座模糊半径 = 配方", baseRecipeBackdrop.getBlurRadius(),
                root.getBackdrop().getBlurRadius());
        Assert.assertEquals("底座材质 = 配方", baseRecipeBackdrop.getEffect().getMaterial(),
                root.getBackdrop().getEffect().getMaterial());

        // 选项：INDICATOR 配方；选中换强调色系 tint、未选中保持配方 tint
        SceneNode selectedItem = itemNode(root, 0);
        SceneNode unselectedItem = itemNode(root, 1);
        Assert.assertEquals("选中项 tint = 强调色系选中档", ITEM_SELECTED, selectedItem.getBackgroundColor());
        Assert.assertEquals("选中项 tint RGB = 主题强调色", rgbOf(SceneThemes.DEFAULT.accent()),
                rgbOf(selectedItem.getBackgroundColor()));
        Assert.assertEquals("选中项强度 = 主题统一选中强度 0x59", 0x59, alphaOf(selectedItem.getBackgroundColor()));
        Assert.assertTrue("选中强度必须高于未选中档，否则读不出选中",
                alphaOf(selectedItem.getBackgroundColor()) > alphaOf(ITEM_UNSELECTED));
        Assert.assertNotEquals("选中与未选中必须可区分", ITEM_UNSELECTED, selectedItem.getBackgroundColor());
        Assert.assertEquals("未选中项 tint = 配方 idle 档", ITEM_UNSELECTED, unselectedItem.getBackgroundColor());
        Assert.assertEquals("选项圆角 = 配方（不再静态设值）", ITEM_SURFACE.getCornerRadius(),
                selectedItem.getCornerRadius());
        Assert.assertEquals("选项边框宽 = 配方", ITEM_SURFACE.getBorderWidth(), selectedItem.getBorderWidth());
        Assert.assertEquals("选项实体高度 = 配方 idle 档", ITEM_SURFACE.getIdle().getElevation(),
                selectedItem.__getSurfaceElevation(), EPSILON);
        UiBackdrop itemRecipeBackdrop = ITEM_SURFACE.getBackdrop();
        Assert.assertNotNull("选项保留配方自带的轻滤镜（G09 裁决，不置空 backdrop）", selectedItem.getBackdrop());
        Assert.assertEquals("选项模糊半径 = 配方", itemRecipeBackdrop.getBlurRadius(),
                selectedItem.getBackdrop().getBlurRadius());
        Assert.assertEquals("选项透镜强度 = 配方 lens × idle lensFactor",
                itemRecipeBackdrop.getEffect().getLensStrength() * ITEM_SURFACE.getIdle().getLensFactor(),
                selectedItem.getBackdrop().getEffect().getLensStrength(), EPSILON);

        // 文字与指示条：启用取主题正文色
        Assert.assertEquals("选中项文字 = 主题正文色", TEXT_ENABLED, labelNode(root, 0).getTextColor());
        Assert.assertEquals("未选中项文字 = 主题正文色", TEXT_ENABLED, labelNode(root, 1).getTextColor());
        Assert.assertEquals("选中指示条 = 主题正文色", TEXT_ENABLED, indicatorNode(root, 0).getBackgroundColor());

        // 旧静态外观写入者已删除：底座/选项不再取实色 token
        Assert.assertNotEquals("底座不再取旧实色 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT, root.getBackgroundColor());
        Assert.assertNotEquals("选项不再取旧实色 ACCENT", SceneChromeTokens.ACCENT, selectedItem.getBackgroundColor());
        Assert.assertNotEquals("选项不再取旧实色 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT,
                unselectedItem.getBackgroundColor());
    }

    // ==================== 滤镜口径：每个选项恰好 1 条 BACKDROP ====================

    /**
     * 契约 §4.1「导航族选项的滤镜口径（G09 裁决）」：每个选项消费 {@code selectableSurface} 并
     * <b>保留配方自带的轻滤镜</b>——整树 BACKDROP 命令数 = 底座 1 + 选项 N，且每个选项子树恰好 1 条
     * （不在配方之外叠第二层玻璃、不把配方 backdrop 置空）。
     */
    @Test
    public void eachItemShouldSampleBackdropExactlyOnce() {
        SceneNode root = mountNav(new SceneNode(), Signal.create(Integer.valueOf(1)),
                Signal.create(Boolean.TRUE), null).getRoot();
        doLayout(root);

        Assert.assertEquals("整树 BACKDROP = 底座 1 + 每个选项各 1",
                1 + OPTIONS.size(), backdropCount(paintEngine, root));
        int baseOnly = backdropCount(paintEngine, root);
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertEquals("选项[" + i + "] 恰好采样一次背景", 1,
                    backdropCount(paintEngine, itemNode(root, i)));
            baseOnly -= backdropCount(paintEngine, itemNode(root, i));
        }
        Assert.assertEquals("底座恰好采样一次背景（整树减去各选项子树）", 1, baseOnly);
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertNull("选项内文字节点不重复采样玻璃", labelNode(root, i).getBackdrop());
            Assert.assertNull("选项内指示条不重复采样玻璃", indicatorNode(root, i).getBackdrop());
            Assert.assertTrue("选项 tint 半透明叠在玻璃之上（非不透明底盖）",
                    alphaOf(itemNode(root, i).getBackgroundColor()) < 0xFF);
        }
    }

    // ==================== 主题切换：外观更新、选中项不丢、节点身份不变、effect 不增长 ====================

    /**
     * 页面主题信号更新 + flush 后：底座/选项/文字/滤镜随新主题更新，选中项不丢、
     * 节点身份不变、effect 数不增长；卸载后绑定全部回收。
     */
    @Test
    public void themeSwitchShouldUpdateBaseItemsAndTextWithoutRebuild() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 TOOLBAR 配方必须不同",
                dark.surface(SceneTheme.Role.TOOLBAR), light.surface(SceneTheme.Role.TOOLBAR));
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方必须不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));

        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNavList.Props props = new SceneNavList.Props(selected, OPTIONS, enabled, idx -> { }, null);

        SceneNode host = new SceneNode();
        MountHandle themed = runtime.mount(host, () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneNavList.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();
        doLayout(themed.getRoot());

        SceneNode root = themed.getRoot();
        SceneNode selectedItem = itemNode(root, 0);
        SceneNode unselectedItem = itemNode(root, 1);
        SceneNode indicator0 = indicatorNode(root, 0);
        SceneNode label0 = labelNode(root, 0);

        Assert.assertEquals("初始底座取深色 TOOLBAR 配方",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("初始未选中项取深色 INDICATOR 配方",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), unselectedItem.getBackgroundColor());
        Assert.assertEquals("初始选中项取深色强调色选中档",
                selectedTint(dark.accent()), selectedItem.getBackgroundColor());
        Assert.assertEquals("初始文字取深色正文色", dark.foreground(), label0.getTextColor());
        Assert.assertEquals("初始指示条取深色正文色", dark.foreground(), indicator0.getBackgroundColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        doLayout(root);

        Assert.assertSame("主题切换不重建底座", root, themed.getRoot());
        Assert.assertSame("主题切换不重建选项节点", selectedItem, itemNode(root, 0));
        Assert.assertSame("主题切换不重建文字节点", label0, labelNode(root, 0));
        Assert.assertSame("主题切换不重建指示条节点", indicator0, indicatorNode(root, 0));
        Assert.assertEquals("底座随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getBackdrop().getEffect().getMaterial(),
                root.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("未选中项随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), unselectedItem.getBackgroundColor());
        Assert.assertEquals("选中项随主题更新且不丢选中",
                selectedTint(light.accent()), selectedItem.getBackgroundColor());
        Assert.assertEquals("选中项 tint RGB 为新主题强调色", rgbOf(light.accent()),
                rgbOf(selectedItem.getBackgroundColor()));
        Assert.assertEquals("文字随主题更新", light.foreground(), label0.getTextColor());
        Assert.assertEquals("指示条随主题更新", light.foreground(), indicator0.getBackgroundColor());
        Assert.assertEquals("受控值不受主题切换影响", Integer.valueOf(0), selected.get());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        themed.dispose();
        Assert.assertEquals("卸载后回收该实例全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 禁用态：配方禁用档 + 禁用前景可读 ====================

    /**
     * 禁用态：底座/选项取角色配方禁用档，文字与指示条取主题禁用前景色（不透明、可读）；
     * 「选中 + 禁用」仍走禁用档；恢复启用后回到选中档；受控值不受影响。
     */
    @Test
    public void disabledShouldUseDisabledRecipeAndDisabledForeground() {
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        SceneNode root = mountNav(new SceneNode(), selected, enabled, null).getRoot();
        doLayout(root);

        enabled.set(Boolean.FALSE);
        runtime.flush();
        doLayout(root);
        Assert.assertEquals("禁用底座取 TOOLBAR 禁用档", BASE_SURFACE.getDisabled().getTint(),
                root.getBackgroundColor());
        Assert.assertEquals("禁用选中项取 INDICATOR 禁用档", ITEM_DISABLED, itemNode(root, 0).getBackgroundColor());
        Assert.assertEquals("禁用未选中项取 INDICATOR 禁用档", ITEM_DISABLED, itemNode(root, 1).getBackgroundColor());
        Assert.assertEquals("禁用文字取主题禁用前景色", TEXT_DISABLED, labelNode(root, 0).getTextColor());
        Assert.assertEquals("禁用指示条取主题禁用前景色", TEXT_DISABLED,
                indicatorNode(root, 0).getBackgroundColor());
        Assert.assertEquals("禁用前景色必须不透明（可读）", 0xFF, alphaOf(TEXT_DISABLED));
        Assert.assertNotEquals("禁用文字不得与禁用底同色", itemNode(root, 0).getBackgroundColor(),
                labelNode(root, 0).getTextColor());
        Assert.assertEquals("禁用不改变受控值", Integer.valueOf(0), selected.get());

        enabled.set(Boolean.TRUE);
        runtime.flush();
        doLayout(root);
        Assert.assertEquals("恢复启用回选中档", ITEM_SELECTED, itemNode(root, 0).getBackgroundColor());
        Assert.assertEquals("恢复启用回正文色", TEXT_ENABLED, labelNode(root, 0).getTextColor());
        Assert.assertEquals("恢复启用指示条回正文色", TEXT_ENABLED, indicatorNode(root, 0).getBackgroundColor());
    }

    // ==================== 行为保持：受控回调 / 键盘导航 / 禁用拦截 / 零重排 ====================

    /**
     * 受控单选闭环 + 键盘导航 + 禁用拦截：点/Enter/方向键只上抛期望下标，控件不改 selectedIndex；
     * 外部回写后选中态切换；禁用态点击与键盘均不触发。
     */
    @Test
    public void controlledClickAndKeyboardNavigationShouldStayUnchanged() {
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        AtomicInteger selectCount = new AtomicInteger(0);
        AtomicInteger lastSelectValue = new AtomicInteger(-1);
        SceneNavList.Props props = new SceneNavList.Props(selected, OPTIONS, enabled,
                idx -> {
                    selectCount.incrementAndGet();
                    lastSelectValue.set(idx.intValue());
                }, null);
        SceneNode parent = new SceneNode();
        SceneNode root = runtime.mount(parent, SceneNavList.create(runtime, props)).getRoot();
        runtime.flush();
        harness.mountRoot(parent, CANVAS_WIDTH, CANVAS_HEIGHT);

        // ① 点选项[1]：上抛期望下标 1，但受控值仍 0、视觉未自选中
        harness.click(itemNode(root, 1));
        Assert.assertEquals("CLICK 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("CLICK 上抛期望下标 1", 1, lastSelectValue.get());
        Assert.assertEquals("受控：外部未回写时 selectedIndex 仍 0", Integer.valueOf(0), selected.get());
        Assert.assertEquals("受控：选项[1] 视觉未自选中", ITEM_UNSELECTED, itemNode(root, 1).getBackgroundColor());

        // ② 外部回写 → 选项[1] 切选中、选项[0] 退选
        selected.set(Integer.valueOf(1));
        runtime.flush();
        doLayout(root);
        Assert.assertEquals("外部回写后选项[1] 选中", ITEM_SELECTED, itemNode(root, 1).getBackgroundColor());
        Assert.assertEquals("外部回写后选项[0] 退选", ITEM_UNSELECTED, itemNode(root, 0).getBackgroundColor());

        // ③ 方向键：↓ 从 1 上抛 2 并把焦点移到选项[2]
        runtime.requestFocus(itemNode(root, 1));
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 上抛相邻下标 2", 2, lastSelectValue.get());
        Assert.assertSame("↓ 焦点移到选项[2]", itemNode(root, 2), runtime.getFocusedNode());

        // ④ Enter 激活当前焦点项
        int before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 上抛当前项下标 2", 2, lastSelectValue.get());

        // ⑤ 禁用态：键盘与点击均不触发
        enabled.set(Boolean.FALSE);
        runtime.flush();
        doLayout(root);
        before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        harness.click(itemNode(root, 1));
        Assert.assertEquals("禁用态键盘与点击均不触发 onSelect", before, selectCount.get());
    }

    /**
     * 交互态/选中切换只打 PAINT/COMPOSITE 级标记：布局引擎的 relayoutCount 必须为 0
     * （选中指示条靠 scaleY、标签靠 translateX，均不触发布局）。
     */
    @Test
    public void selectionAndEnabledSwitchShouldNotRelayout() {
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        SceneNode root = mountNav(new SceneNode(), selected, enabled, null).getRoot();
        doLayout(root);

        selected.set(Integer.valueOf(2));
        runtime.flush();
        LayoutResult afterSelect = doLayout(root);
        Assert.assertEquals("选中切换零重排", 0, afterSelect.getRelayoutCount());
        Assert.assertEquals("选中切到选项[2]", ITEM_SELECTED, itemNode(root, 2).getBackgroundColor());

        enabled.set(Boolean.FALSE);
        runtime.flush();
        LayoutResult afterDisable = doLayout(root);
        Assert.assertEquals("禁用切换零重排", 0, afterDisable.getRelayoutCount());
        Assert.assertEquals("禁用选项[2] 取禁用档", ITEM_DISABLED, itemNode(root, 2).getBackgroundColor());
    }

    // ==================== 动态列表：项数变化不改变结构与外观归属 ====================

    /**
     * 动态列表：0/1/5 项挂载均不崩、结构正确（每项含指示条+标签）、底座仍取 TOOLBAR 配方、
     * 每个选项仍各自采样一次背景。
     */
    @Test
    public void dynamicOptionCountShouldKeepStructureAndSurfaceOwnership() {
        List<List<String>> cases = Arrays.asList(
                Collections.<String>emptyList(),
                Collections.singletonList("Only"),
                Arrays.asList("A", "B", "C", "D", "E"));
        for (List<String> options : cases) {
            SceneNode parent = new SceneNode();
            SceneNavList.Props props = new SceneNavList.Props(
                    Signal.create(Integer.valueOf(0)), options, Signal.create(Boolean.TRUE), idx -> { }, null);
            SceneNode root = runtime.mount(parent, SceneNavList.create(runtime, props)).getRoot();
            runtime.flush();
            doLayout(root);

            Assert.assertEquals("项数 = options.size()", options.size(), root.__getChildren().size());
            Assert.assertEquals("底座仍取 TOOLBAR 配方", BASE_IDLE, root.getBackgroundColor());
            for (int i = 0; i < options.size(); i++) {
                Assert.assertEquals("每项含指示条 + 标签", 2, itemNode(root, i).__getChildren().size());
                Assert.assertEquals("每项各自采样一次背景", 1, backdropCount(paintEngine, itemNode(root, i)));
            }
        }
    }

    // ==================== 卸载回收绑定（effect 探针） ====================

    /**
     * 挂载注册响应式绑定、卸载全部回收：{@code ReactiveTestProbe.registeredEffectCount()}
     * 回到挂载前基线（守「卸载即回收」纪律）。
     */
    @Test
    public void disposeShouldReclaimAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();

        SceneNavList.Props props = new SceneNavList.Props(
                Signal.create(Integer.valueOf(0)), OPTIONS, Signal.create(Boolean.TRUE), idx -> { }, null);
        MountHandle extra = runtime.mount(new SceneNode(), SceneNavList.create(runtime, props));
        runtime.flush();

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted, mounted > baseline);

        extra.dispose();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }
}
