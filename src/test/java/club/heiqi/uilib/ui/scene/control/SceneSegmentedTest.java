package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
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
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneSegmented 端到端单元测试 —— Phase 4 批 2 分段单选受控控件（R8）验收。
 *
 * <p>端到端验证：受控闭环（点段只上抛期望下标、控件零状态不自改）、
 * R6 段穿透权威验证（点段内 label 文字穿透到所属段进 pressed）、四态切换零重排、
 * 键盘激活（Enter/Space）+ disabled 拦截、方向键导航（←/→ + 焦点移动），
 * 以及主题化外观（底座走 TOOLBAR 配方、段走 INDICATOR 选中配方并保留配方自带轻滤镜
 * （每段恰好一条 BACKDROP）、选中 tint 为强调色系且强度 0x59、文字走主题正文/禁用前景色、
 * hover/focus 走配方状态档、主题切换不重建节点且选中项不丢、卸载回收绑定）。</p>
 */
public class SceneSegmentedTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/press/release/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger selectCount;
    private Integer lastSelectValue;

    private MountHandle handle;
    private SceneNode segRoot;

    private static final int CANVAS_WIDTH = 300;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    /**
     * 库默认主题的 TOOLBAR 角色配方：导航底座默认外观唯一来源。
     * 断言取配方值而不是硬编码色号，主题集中调参时本类自动跟随。
     */
    private static final SceneSurfaceStyle BASE_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    /**
     * 库默认主题的 INDICATOR 角色配方：段选中指示默认外观唯一来源。
     */
    private static final SceneSurfaceStyle SEG_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);

    private static final int BASE_IDLE = BASE_SURFACE.getIdle().getTint();
    private static final int BASE_HOVERED = BASE_SURFACE.getHovered().getTint();
    private static final int BASE_DISABLED = BASE_SURFACE.getDisabled().getTint();

    private static final int SEG_UNSEL_ENABLED = SEG_SURFACE.getIdle().getTint();
    private static final int SEG_UNSEL_HOVERED = SEG_SURFACE.getHovered().getTint();
    private static final int SEG_UNSEL_PRESSED = SEG_SURFACE.getPressed().getTint();
    /**
     * 选中档：{@code SceneThemes.selectableSurface} 把配方 tint 的 RGB 换成主题强调色、
     * 保留原 alpha（故选中是色彩语义，不是仅透明度）。
     */
    private static final int SEG_SEL_ENABLED = selectedTint(SEG_UNSEL_ENABLED, SceneThemes.DEFAULT.accent());
    private static final int SEG_DISABLED = SEG_SURFACE.getDisabled().getTint();

    /** 段文字：启用取主题正文色，禁用取主题禁用前景色（选中/未选中同色，见实现类对比度说明）。 */
    private static final int LABEL_ENABLED = SceneThemes.DEFAULT.foreground();
    private static final int LABEL_DISABLED = SceneThemes.DEFAULT.disabledForeground();

    private static final List<String> OPTIONS = Arrays.asList("Day", "Week", "Month");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        // 控件构建期需 measureTextWidth，用 create(measurer) 注入真实 measurer
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        selectedSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectValue = null;

        SceneSegmented.Props props = new SceneSegmented.Props(
                selectedSignal, OPTIONS, enabledSignal,
                next -> {
                    selectCount.incrementAndGet();
                    lastSelectValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneSegmented.create(runtime, props));
        segRoot = handle.getRoot();

        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click/press/pressKey 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /**
     * 选中配方语义：RGB 换成强调色，alpha 用主题统一选中强度
     * {@code SceneThemes.SELECTED_TINT_ALPHA}(0x59)——不再沿用角色配方的低 alpha。
     */
    private static int selectedTint(int baseTint, int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** segment[i] 节点（root 第 i 个孩子） */
    private SceneNode segmentNode(int i) {
        return segRoot.__getChildren().get(i);
    }

    /** segment[i] 的 label 节点（segment 第一个孩子） */
    private SceneNode labelNode(int i) {
        return segmentNode(i).__getChildren().get(0);
    }

    private int segBackground(int i) {
        return segmentNode(i).getBackgroundColor();
    }

    /**
     * 底座 hover 探测点 X：落在底座盒内、且在所有段右缘之外（段只做轻量覆盖，
     * 底座空白区才是底座自身的命中区）。
     */
    private int baseHoverProbeX() {
        LayoutBox rootBox = (LayoutBox) segRoot.getCachedLayout();
        int rightMostSegment = 0;
        for (SceneNode segment : segRoot.__getChildren()) {
            LayoutBox box = (LayoutBox) segment.getCachedLayout();
            rightMostSegment = Math.max(rightMostSegment, box.getX() + box.getWidth());
        }
        int probe = rootBox.getX() + rootBox.getWidth() - 2;
        Assert.assertTrue("底座右缘应留出段外空白区供 hover 探测，probe=" + probe
                + ", rightMostSegment=" + rightMostSegment, probe >= rightMostSegment);
        return probe;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数；无 fragment 返回 -1。 */
    private static int backdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return -1;
        }
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 验收 1：受控闭环 ====================

    /**
     * 受控核心：初始 selectedIndex=0，点 segment[1] → onSelect 收到期望下标 1，
     * 但 selectedIndex 仍 0（控件零状态不自改）；外部 set 1 → flush → segment[1] 切选中背景。
     */
    @Test
    public void controlledClickShouldRaiseOnSelectWithoutSelfMutate() {
        doLayout();
        Assert.assertEquals("初始 segment[0] 选中背景", SEG_SEL_ENABLED, segBackground(0));
        Assert.assertEquals("初始 segment[1] 未选中背景", SEG_UNSEL_ENABLED, segBackground(1));

        harness.click(segmentNode(1));

        Assert.assertEquals("CLICK 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("onSelect 应收到期望下标 1", Integer.valueOf(1), lastSelectValue);

        Assert.assertEquals("受控：外部未回写时 selectedIndex 仍 0",
                Integer.valueOf(0), selectedSignal.get());
        doLayout();
        Assert.assertEquals("受控：segment[1] 视觉未自选中", SEG_UNSEL_ENABLED, segBackground(1));

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        doLayout();
        Assert.assertEquals("外部回写后 segment[1] 选中背景", SEG_SEL_ENABLED, segBackground(1));
        Assert.assertEquals("外部回写后 segment[0] 退选背景", SEG_UNSEL_ENABLED, segBackground(0));
    }

    // ==================== 验收 2：R6 段穿透权威验证（点段内 label 文字穿透到段） ====================

    /**
     * R6 权威落地：点 segment[1] 内 label 文字几何中心，命中穿透到 segment[1]，
     * segment[1] 进 pressed → 切 pressed 背景；释放合成 CLICK 上抛 1。
     */
    @Test
    public void hitTestShouldPassThroughSegmentLabelToSegment() {
        doLayout();

        harness.press(labelNode(1));
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 segment[1] → pressed 背景",
                SEG_UNSEL_PRESSED, segBackground(1));

        harness.release(labelNode(1));
        Assert.assertEquals("点 label[1] 释放应合成 CLICK 触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("期望下标 1", Integer.valueOf(1), lastSelectValue);
    }

    // ==================== 验收 3：四态切换零重排（终极反证 R-D） ====================

    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segment[1] 默认背景", SEG_UNSEL_ENABLED, segBackground(1));

        // ① enabled → disabled：切灰，零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("disabled segment[1] 背景", SEG_DISABLED, segBackground(1));
        Assert.assertEquals("R-D: enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled：回默认，零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("回 enabled segment[1] 背景", SEG_UNSEL_ENABLED, segBackground(1));
        Assert.assertEquals("R-D: disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：route 真实 POINTER_DOWN 命中 segment[1] 几何中心
        result = doLayout();
        harness.press(segmentNode(1));
        result = doLayout();
        Assert.assertEquals("pressed segment[1] 背景", SEG_UNSEL_PRESSED, segBackground(1));
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        harness.release(segmentNode(1));
        result = doLayout();
        Assert.assertEquals("释放后回默认背景", SEG_UNSEL_ENABLED, segBackground(1));
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());

        // ④ 外部 set 选中切换：纯 PAINT 级零重排
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("选中切到 2：segment[2] 选中背景", SEG_SEL_ENABLED, segBackground(2));
        Assert.assertEquals("R-D: 选中切换零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 4：键盘激活 + disabled 拦截 ====================

    @Test
    public void keyboardActivationRaisesOnSelectAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(segmentNode(1));

        int before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 期望下标 1", Integer.valueOf(1), lastSelectValue);

        before = selectCount.get();
        harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("Space 应触发一次 onSelect", before + 1, selectCount.get());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled 态 Enter 不触发", before, selectCount.get());

        doLayout();
        harness.click(segmentNode(1));
        Assert.assertEquals("disabled 态 CLICK 不触发", before, selectCount.get());
    }

    // ==================== 验收 6：段宽按标题文本自适应 ====================

    /**
     * 段宽应按其标题文本宽度自适应：短标题段窄、长标题段宽，
     * 段宽 = 文本宽（每字符 STUB_CHAR_WIDTH）+ 2*SEGMENT_PADDING（PAD_LG=10）。
     *
     * <p>构建期一次性测量固化进 preferredWidth，不引入每段脏标记瀑布（守 I7）。</p>
     */
    @Test
    public void segmentWidthShouldAdaptToTitleText() {
        // OPTIONS = ["Day"(3), "Week"(4), "Month"(5)]，charWidth=8，PAD_LG=10
        int pad = SceneChromeTokens.PAD_LG;
        Assert.assertEquals("段[0] 'Day' 宽 = 3*8 + 2*10 = 44",
                3 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(0).getPreferredWidth());
        Assert.assertEquals("段[1] 'Week' 宽 = 4*8 + 2*10 = 52",
                4 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(1).getPreferredWidth());
        Assert.assertEquals("段[2] 'Month' 宽 = 5*8 + 2*10 = 60",
                5 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(2).getPreferredWidth());
        // 短标题段窄于长标题段，不留白
        Assert.assertTrue("短标题段窄于长标题段",
                segmentNode(0).getPreferredWidth() < segmentNode(2).getPreferredWidth());
    }

    // ==================== 验收 7：内置默认高（preferredHeight） ====================

    /**
     * SceneSegmented 应内置默认高：root.preferredHeight = lineHeight(16) + 2*PAD_LG。
     *
     * <p>容器型固定子须显式设 preferredHeight，否则 ConstraintResolver.computeColumnGrowHeights
     * 命中 priorKnownChildHeight 容器分支返回 UNCONSTRAINED 早退，grow 兄弟收不到分配高。
     * 内置后调用方无需再手动设高。FixedTextMeasurer lineHeight=16，PAD_LG=10 → 期望 36。</p>
     */
    @Test
    public void rootShouldHaveBuiltinPreferredHeight() {
        int pad = SceneChromeTokens.PAD_LG;
        int expected = 16 + 2 * pad; // lineHeight(16)=16 + 2*PAD_LG=20 = 36
        Assert.assertEquals("segRoot 内置 preferredHeight = lineHeight(16) + 2*PAD_LG",
                expected, segRoot.getPreferredHeight());
        Assert.assertTrue("segRoot preferredHeight > 0（回归保护）",
                segRoot.getPreferredHeight() > 0);
    }

    // ==================== 验收 5：方向键导航（←/→ + 焦点移动） ====================

    @Test
    public void arrowKeyNavigationRaisesAdjacentIndexAndMovesFocus() {
        doLayout();
        runtime.requestFocus(segmentNode(0));

        // ① →：cur=0 → next=1
        harness.pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals("→ 上抛相邻下标 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("→ 焦点移到 segment[1]", segmentNode(1), runtime.getFocusedNode());

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals("→ 从 1 上抛 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertSame("→ 焦点移到 segment[2]", segmentNode(2), runtime.getFocusedNode());

        // ② 边界裁剪：cur=2 再 → 仍 2
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals("→ 末段边界裁剪仍 2", Integer.valueOf(2), lastSelectValue);

        // ③ ←：cur=2 → next=1
        harness.pressKey(SceneKey.ARROW_LEFT);
        Assert.assertEquals("← 从 2 上抛 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("← 焦点移到 segment[1]", segmentNode(1), runtime.getFocusedNode());

        // ④ ← 边界裁剪：cur=0 再 ← 仍 0
        selectedSignal.set(Integer.valueOf(0));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_LEFT);
        Assert.assertEquals("← 首段边界裁剪仍 0", Integer.valueOf(0), lastSelectValue);
    }

    // ==================== 验收 8：默认工厂路径消费主题（底座 TOOLBAR + 段 INDICATOR） ====================

    /**
     * 默认工厂路径（不传任何样式参数）：底座 background/border/borderWidth/cornerRadius/
     * backdrop/surfaceElevation 全部等于 {@code SceneThemes.DEFAULT.surface(Role.TOOLBAR)}
     * 的对应值；每段走 INDICATOR 配方（选中 tint 的 RGB 换成强调色、强度 0x59），
     * 且段不重复安装滤镜（backdrop 为 null，背景由底座统一采样一次）。
     */
    @Test
    public void defaultFactoryShouldConsumeToolbarBaseAndIndicatorSelection() {
        doLayout();

        // 底座 = TOOLBAR 配方（导航栏唯一滤镜安装点）
        Assert.assertEquals("底座染色 = TOOLBAR 配方 idle tint", BASE_IDLE, segRoot.getBackgroundColor());
        Assert.assertEquals("底座圆角 = TOOLBAR 配方圆角",
                BASE_SURFACE.getCornerRadius(), segRoot.getCornerRadius());
        Assert.assertEquals("底座边框宽 = TOOLBAR 配方",
                BASE_SURFACE.getBorderWidth(), segRoot.getBorderWidth());
        Assert.assertEquals("底座缘色 = TOOLBAR 配方 idle edge",
                BASE_SURFACE.getIdle().getEdge(), segRoot.getBorderColor());
        Assert.assertEquals("底座实体高度 = TOOLBAR 配方 idle elevation",
                BASE_SURFACE.getIdle().getElevation(), segRoot.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", segRoot.getBackdrop());
        Assert.assertEquals("底座滤镜模糊半径来自 TOOLBAR 配方",
                BASE_SURFACE.getBackdrop().getBlurRadius(), segRoot.getBackdrop().getBlurRadius());

        // 段 = INDICATOR 配方，保留配方自带的轻滤镜（G09 裁决：与 G05 选中族同口径）
        UiBackdrop recipeBackdrop = SEG_SURFACE.getBackdrop();
        Assert.assertNotNull("测试前提：INDICATOR 配方自带轻滤镜", recipeBackdrop);
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertEquals("段[" + i + "] 圆角来自 INDICATOR 配方",
                    SEG_SURFACE.getCornerRadius(), segmentNode(i).getCornerRadius());
            Assert.assertEquals("段[" + i + "] 边框宽来自 INDICATOR 配方",
                    SEG_SURFACE.getBorderWidth(), segmentNode(i).getBorderWidth());
            UiBackdrop segmentBackdrop = segmentNode(i).getBackdrop();
            Assert.assertNotNull("段[" + i + "] 保留配方自带轻滤镜", segmentBackdrop);
            Assert.assertEquals("段[" + i + "] 滤镜模糊半径 = INDICATOR 配方",
                    recipeBackdrop.getBlurRadius(), segmentBackdrop.getBlurRadius());
            Assert.assertEquals("段[" + i + "] 滤镜材质 = INDICATOR 配方",
                    recipeBackdrop.getEffect().getMaterial(), segmentBackdrop.getEffect().getMaterial());
        }

        // 选中段 = 强调色系 + 主题统一选中强度 0x59（选中不能只靠透明度区分）
        int selected = segBackground(0);
        Assert.assertEquals("选中段染色 = 配方 tint 换强调色", SEG_SEL_ENABLED, selected);
        Assert.assertEquals("选中段 tint 为强调色系", rgbOf(SceneThemes.DEFAULT.accent()), rgbOf(selected));
        Assert.assertEquals("选中用主题统一选中强度 0x59", 0x59, alphaOf(selected));
        Assert.assertTrue("选中强度必须高于未选中档，否则读不出选中",
                alphaOf(selected) > alphaOf(SEG_UNSEL_ENABLED));
        Assert.assertNotEquals("选中与未选中必须可区分", SEG_UNSEL_ENABLED, selected);
        Assert.assertEquals("未选中段染色 = INDICATOR 配方 idle tint",
                SEG_UNSEL_ENABLED, segBackground(1));

        // 段文字 = 主题正文色（选中/未选中同色，选中区分由染色承担）
        Assert.assertEquals("段[0] 文字 = 主题正文色", LABEL_ENABLED, labelNode(0).getTextColor());
        Assert.assertEquals("段[1] 文字 = 主题正文色", LABEL_ENABLED, labelNode(1).getTextColor());
    }

    // ==================== 验收 9：hover / focus 走配方状态档且零重排 ====================

    /**
     * hover 染色取角色配方 hovered 档、聚焦缘色取配方 focusEdge、底座 hover 取 TOOLBAR
     * hovered 档，全部纯 PAINT 级零重排。
     */
    @Test
    public void hoverAndFocusShouldFollowRecipeWithoutRelayout() {
        LayoutResult result = doLayout();

        // ① 段 hover：染色取 INDICATOR 配方 hovered 档，零重排
        harness.moveTo(segmentNode(1));
        result = doLayout();
        Assert.assertEquals("段[1] hover 染色取配方 hovered 档", SEG_UNSEL_HOVERED, segBackground(1));
        Assert.assertEquals("R-D: 段 hover 零重排", 0, result.getRelayoutCount());

        // ② 聚焦：缘色取配方 focusEdge（非禁用态），零重排
        runtime.requestFocus(segmentNode(1));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("段[1] 聚焦缘色 = 配方 focusEdge",
                SEG_SURFACE.getFocusEdge(), segmentNode(1).getBorderColor());
        Assert.assertEquals("R-D: 聚焦缘色零重排", 0, result.getRelayoutCount());

        // ③ 底座 hover：指针落在段外空白区 → 底座取 TOOLBAR 配方 hovered 档，零重排
        LayoutBox rootBox = (LayoutBox) segRoot.getCachedLayout();
        harness.moveAt(baseHoverProbeX(), rootBox.getY() + rootBox.getHeight() / 2);
        result = doLayout();
        Assert.assertEquals("底座 hover 染色取 TOOLBAR 配方 hovered 档",
                BASE_HOVERED, segRoot.getBackgroundColor());
        Assert.assertEquals("R-D: 底座 hover 零重排", 0, result.getRelayoutCount());

        // ④ 移出整条导航栏：底座回 idle 档，零重排
        harness.moveAt(0, rootBox.getY() + rootBox.getHeight() + 1);
        result = doLayout();
        Assert.assertEquals("移出后底座回 TOOLBAR idle 档", BASE_IDLE, segRoot.getBackgroundColor());
        Assert.assertEquals("R-D: 移出零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 10：禁用态取禁用档与禁用前景 ====================

    /**
     * 禁用态：底座取 TOOLBAR 禁用档、段取 INDICATOR 禁用档（选中段同样退到禁用档）、
     * 文字取主题禁用前景色且不透明可读；「选中 + 禁用」不覆盖 disabled 分支，受控值不受影响。
     */
    @Test
    public void disabledShouldUseDisabledRecipeAndDisabledForeground() {
        doLayout();

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("禁用底座取 TOOLBAR 配方禁用档", BASE_DISABLED, segRoot.getBackgroundColor());
        Assert.assertEquals("禁用段[0]（原选中）取 INDICATOR 禁用档", SEG_DISABLED, segBackground(0));
        Assert.assertEquals("禁用段[1] 取 INDICATOR 禁用档", SEG_DISABLED, segBackground(1));
        Assert.assertEquals("禁用文字取主题禁用前景色", LABEL_DISABLED, labelNode(0).getTextColor());
        Assert.assertEquals("禁用文字不透明可读", 0xFF, alphaOf(LABEL_DISABLED));
        Assert.assertTrue("禁用段仍有可见染色（可读，不是全透明消失）", alphaOf(SEG_DISABLED) > 0);
        Assert.assertNotEquals("禁用文字色与禁用染色必须可区分", SEG_DISABLED, LABEL_DISABLED);
        Assert.assertEquals("禁用不改变受控值", Integer.valueOf(0), selectedSignal.get());

        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("恢复启用底座回 TOOLBAR idle 档", BASE_IDLE, segRoot.getBackgroundColor());
        Assert.assertEquals("恢复启用回选中档", SEG_SEL_ENABLED, segBackground(0));
        Assert.assertEquals("恢复启用文字回正文色", LABEL_ENABLED, labelNode(0).getTextColor());
    }

    // ==================== 验收 11：主题切换更新外观且不重建节点 / 不增订阅 ====================

    /**
     * 页面主题信号更新 + flush 后：底座/段/文字外观随新主题更新，选中项不丢，
     * 节点身份不变、effect 数不增长；卸载后绑定全部回收。
     */
    @Test
    public void themeSwitchShouldUpdateAppearanceWithoutRebuild() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 TOOLBAR 配方必须不同",
                dark.surface(SceneTheme.Role.TOOLBAR), light.surface(SceneTheme.Role.TOOLBAR));
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方必须不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));
        Assert.assertNotEquals("测试前提：深/浅正文色必须不同", dark.foreground(), light.foreground());

        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        Signal<Integer> themedSelected = Signal.create(Integer.valueOf(1));
        SceneSegmented.Props props = new SceneSegmented.Props(
                themedSelected, OPTIONS, enabledSignal, next -> lastSelectValue = next);

        SceneNode host = new SceneNode();
        MountHandle themed = runtime.mount(host, () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneSegmented.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();

        SceneNode themedRoot = themed.getRoot();
        SceneNode themedSeg0 = themedRoot.__getChildren().get(0);
        SceneNode themedSeg1 = themedRoot.__getChildren().get(1);
        SceneNode themedLabel1 = themedSeg1.__getChildren().get(0);

        Assert.assertEquals("初始底座取深色 TOOLBAR 配方",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), themedRoot.getBackgroundColor());
        Assert.assertEquals("初始段[0] 未选中档",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), themedSeg0.getBackgroundColor());
        Assert.assertEquals("初始段[1] 选中档（selectedIndex=1）",
                selectedTint(dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), dark.accent()),
                themedSeg1.getBackgroundColor());
        Assert.assertEquals("初始文字取深色正文色", dark.foreground(), themedLabel1.getTextColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertSame("主题切换不重建底座节点", themedRoot, themed.getRoot());
        Assert.assertSame("主题切换不重建段节点", themedSeg1, themedRoot.__getChildren().get(1));
        Assert.assertSame("主题切换不重建文字节点", themedLabel1, themedSeg1.__getChildren().get(0));
        Assert.assertEquals("底座随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), themedRoot.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), themedRoot.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getBackdrop().getEffect().getMaterial(),
                themedRoot.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("段[0] 随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), themedSeg0.getBackgroundColor());
        Assert.assertEquals("段滤镜材质随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getBackdrop().getEffect().getMaterial(),
                themedSeg0.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("选中项仍是段[1] 且随主题更新",
                selectedTint(light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), light.accent()),
                themedSeg1.getBackgroundColor());
        Assert.assertEquals("文字随主题更新", light.foreground(), themedLabel1.getTextColor());
        Assert.assertEquals("切主题不丢选中项", Integer.valueOf(1), themedSelected.get());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        themed.dispose();
        Assert.assertEquals("卸载后回收该实例全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 验收 12：卸载回收绑定（effect 探针） ====================

    /**
     * 挂载注册响应式绑定、卸载全部回收：{@code ReactiveTestProbe.registeredEffectCount()}
     * 回到挂载前基线（守「卸载即回收」纪律）。
     */
    @Test
    public void disposeShouldReclaimAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();

        SceneSegmented.Props props = new SceneSegmented.Props(
                selectedSignal, OPTIONS, enabledSignal, next -> lastSelectValue = next);
        MountHandle extra = runtime.mount(sceneRoot, SceneSegmented.create(runtime, props));
        runtime.flush();

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted,
                mounted > baseline);

        extra.dispose();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 验收 13：每颗表面恰好采样一次背景（不重复安装滤镜） ====================

    /**
     * 滤镜口径（G09 裁决，契约 §4.1）：导航族选项保留配方自带的轻滤镜，与 G05 选中族
     * （RadioGroup circle / Checkbox box / Toggle track）一致；「不给每个子项重复安装滤镜」
     * 的语义是不得在配方之外再叠第二层玻璃、也不得让段内文字各自采样背景。
     * 故底座与每段各自恰好一条 BACKDROP，段内 label 的 PaintFragment 内 0 条。
     */
    @Test
    public void eachSurfaceShouldSampleBackdropExactlyOnce() {
        doLayout();
        paintEngine.paint(sceneRoot);

        Assert.assertEquals("底座恰好一条 BACKDROP", 1, backdropCount(segRoot));
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertEquals("段[" + i + "] 恰好一条 BACKDROP（配方自带轻滤镜，不叠第二层玻璃）",
                    1, backdropCount(segmentNode(i)));
            Assert.assertEquals("段[" + i + "] 内文字不重复采样玻璃", 0, backdropCount(labelNode(i)));
        }
    }
}
