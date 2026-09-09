package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import club.heiqi.uilib.ui.scene.layout.LayoutAssertions;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneTab 端到端单元测试 —— Phase 4 批 4 标签页控件（R8 受控头 + R10 内容区 show 切换）验收。
 *
 * <p>端到端验证：tabBar 受控闭环（点 tab 只上抛期望页下标、控件零状态不自改）、
 * 内容区 N 选 1（仅活动页内容挂载、切页卸旧挂新）、show I7 不重建（活动页保持不变重复 flush 不重建）、
 * R6 段穿透（点 tab 内 label 文字穿透到段）、键盘导航（←/→/Home/End/Enter/Space）、
 * disabled 拦截、tabBar 四态切换零重排。</p>
 *
 * <p>外观断言按主题配方语义：导航条底座 = {@code Role.TOOLBAR} 配方，每个 tab 项 =
 * {@code SceneThemes.selectableSurface(Role.INDICATOR, selected)}（选中 tint 换强调色、保留 0x59
 * alpha，故选中不只靠透明度），文字 = 主题正文/禁用前景（选中在强调底前景与正文色间按对比度择一），
 * 内容区面板保持透明不装表面。断言取 {@link SceneThemes#DEFAULT} 的配方值而非硬编码色号，
 * 主题集中调参时本类自动跟随。</p>
 */
public class SceneTabTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    private Signal<Integer> activeSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger activateCount;
    private Integer lastActivateValue;

    /** 各页内容 builder 的构建计数（探测 show 重建语义） */
    private List<AtomicInteger> buildCounts;

    private MountHandle handle;
    private SceneNode tabRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 200;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final float EPSILON = 0.0001F;

    // ==================== 主题配方镜像（默认档唯一外观来源，不硬编码色号） ====================

    /** 导航条底座配方：默认工厂路径的 TOOLBAR 角色。 */
    private static final SceneSurfaceStyle BAR_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    /** tab 项配方：默认工厂路径的 INDICATOR 角色（选中档在此基础上换 tint RGB）。 */
    private static final SceneSurfaceStyle TAB_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);

    private static final int TAB_INACTIVE_ENABLED = TAB_SURFACE.getIdle().getTint();
    private static final int TAB_INACTIVE_HOVER = TAB_SURFACE.getHovered().getTint();
    private static final int TAB_INACTIVE_PRESSED = TAB_SURFACE.getPressed().getTint();
    private static final int TAB_DISABLED = TAB_SURFACE.getDisabled().getTint();
    /** 选中档：tint RGB 换主题强调色、alpha 用统一选中强度 0x59（SceneThemes 内定值）。 */
    private static final int TAB_ACTIVE_ENABLED = selectedTint(TAB_INACTIVE_ENABLED, SceneThemes.DEFAULT.accent());
    private static final int TAB_ACTIVE_PRESSED =
            selectedTint(TAB_INACTIVE_PRESSED, SceneThemes.DEFAULT.accentPressed());

    /** label 前景：未选中 = 主题正文色；禁用 = 主题禁用前景色；选中 = 两候选按对比度择一。 */
    private static final int LABEL_ENABLED = SceneThemes.DEFAULT.foreground();
    private static final int LABEL_DISABLED = SceneThemes.DEFAULT.disabledForeground();

    // 各页内容 panel 的标识背景色（用于断言挂载的是哪一页）
    private static final int PANEL_BG_0 = 0xFF111111;
    private static final int PANEL_BG_1 = 0xFF222222;
    private static final int PANEL_BG_2 = 0xFF333333;
    private static final int[] PANEL_BG = { PANEL_BG_0, PANEL_BG_1, PANEL_BG_2 };

    private static final List<String> LABELS = Arrays.asList("常规", "外观", "高级");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        activeSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        activateCount = new AtomicInteger(0);
        lastActivateValue = null;

        // 各页 builder：建一个带标识背景色的 panel，并对自己的构建计数 +1（探测重建）
        buildCounts = new ArrayList<>();
        List<Supplier<SceneNode>> panels = new ArrayList<>();
        for (int idx = 0; idx < LABELS.size(); idx++) {
            final int i = idx;
            final AtomicInteger cnt = new AtomicInteger(0);
            buildCounts.add(cnt);
            panels.add(() -> {
                cnt.incrementAndGet();
                SceneNode panel = new SceneNode();
                panel.setBackgroundColor(PANEL_BG[i]);
                panel.setPreferredHeight(40);
                return panel;
            });
        }

        SceneTab.Props props = new SceneTab.Props(
                activeSignal, LABELS, panels, enabledSignal,
                next -> {
                    activateCount.incrementAndGet();
                    lastActivateValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneTab.create(runtime, props));
        tabRoot = handle.getRoot();

        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click/pressKey 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** root 第 0 个孩子是 tabBar */
    private SceneNode tabBar() {
        return tabRoot.__getChildren().get(0);
    }

    /** root 第 1 个孩子是 contentPanel */
    private SceneNode contentPanel() {
        return tabRoot.__getChildren().get(1);
    }

    /** tabSeg[i] 节点（tabBar 第 i 个孩子） */
    private SceneNode tabSeg(int i) {
        return tabBar().__getChildren().get(i);
    }

    /** tabSeg[i] 的 label 节点（tabSeg 第一个孩子） */
    private SceneNode labelNode(int i) {
        return tabSeg(i).__getChildren().get(0);
    }

    private int tabBackground(int i) {
        return tabSeg(i).getBackgroundColor();
    }

    private PaintPlan doPaint(SceneNode root) {
        return paintEngine.paint(root).getPlan();
    }

    private static int countType(List<PaintCommand> cmds, PaintCommandType type) {
        int count = 0;
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    /** 选中档镜像：tint RGB 换强调色、alpha 用 SceneThemes 统一选中强度 0x59。 */
    private static int selectedTint(int baseTint, int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }

    /**
     * 选中项文字期望值镜像（独立复算 {@link SceneTab} 的对比度择色规则）：
     * 以「选中 tint 叠在 TOOLBAR idle tint 上」的近似合成色为底，取两候选里 WCAG 对比度更高者。
     */
    private static int expectedSelectedLabel(SceneSurfaceStyle tabSurface, SceneSurfaceStyle barSurface,
            int accent, int onAccentForeground, int foreground) {
        int background = over(selectedTint(tabSurface.getIdle().getTint(), accent),
                barSurface.getIdle().getTint());
        return contrastRatio(background, onAccentForeground) >= contrastRatio(background, foreground)
                ? onAccentForeground : foreground;
    }

    /** alpha「over」合成镜像（结果按不透明处理，只用于对比度判定）。 */
    private static int over(int source, int base) {
        int sourceAlpha = alpha(source);
        int baseAlpha = alpha(base);
        int restAlpha = (0xFF - sourceAlpha) * baseAlpha / 0xFF;
        int outAlpha = sourceAlpha + restAlpha;
        if (outAlpha == 0) {
            return 0;
        }
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int value = (((source >>> shift) & 0xFF) * sourceAlpha
                    + ((base >>> shift) & 0xFF) * restAlpha) / outAlpha;
            out |= Math.max(0, Math.min(0xFF, value)) << shift;
        }
        return out;
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        double brighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);
        return (brighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int argb) {
        return 0.2126 * linearize((argb >>> 16) & 0xFF)
                + 0.7152 * linearize((argb >>> 8) & 0xFF)
                + 0.0722 * linearize(argb & 0xFF);
    }

    private static double linearize(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /**
     * 收集 contentPanel 下当前已挂载的内容 panel（按背景色匹配 PANEL_BG）。
     *
     * <p>contentPanel 子节点含 N 个 show 的零尺寸 anchor（无背景色，默认 0）+ 至多一个活动内容 panel
     * （带标识背景色）。本方法返回匹配到的内容页下标列表。</p>
     *
     * @return 当前挂载的内容页下标列表（顺序按 contentPanel 子节点顺序）
     */
    private List<Integer> mountedPanelIndices() {
        return mountedPanelIndices(contentPanel());
    }

    /** 指定内容区容器下的已挂载内容页下标列表（主题切换测试用独立挂载树）。 */
    private List<Integer> mountedPanelIndices(SceneNode contentPanel) {
        List<Integer> result = new ArrayList<>();
        for (SceneNode child : contentPanel.__getChildren()) {
            int bg = child.getBackgroundColor();
            for (int i = 0; i < PANEL_BG.length; i++) {
                if (bg == PANEL_BG[i]) {
                    result.add(Integer.valueOf(i));
                }
            }
        }
        return result;
    }

    // ==================== 验收 1：tabBar 受控闭环（点 tab 不自改） ====================

    /**
     * 受控核心：初始 activeIndex=0，点 tabSeg[1] → onActivate 收到期望下标 1，
     * 但 activeIndex 仍 0（控件零状态不自改），视觉未自切；外部 set 1 → flush → tabSeg[1] 切活动背景。
     */
    @Test
    public void controlledTabClickShouldRaiseOnActivateWithoutSelfMutate() {
        doLayout();
        Assert.assertEquals("初始 tabSeg[0] 活动背景", TAB_ACTIVE_ENABLED, tabBackground(0));
        Assert.assertEquals("初始 tabSeg[1] 非活动背景", TAB_INACTIVE_ENABLED, tabBackground(1));

        harness.click(tabSeg(1));

        Assert.assertEquals("CLICK 应触发一次 onActivate", 1, activateCount.get());
        Assert.assertEquals("onActivate 应收到期望下标 1", Integer.valueOf(1), lastActivateValue);

        Assert.assertEquals("受控：外部未回写时 activeIndex 仍 0",
                Integer.valueOf(0), activeSignal.get());
        doLayout();
        Assert.assertEquals("受控：tabSeg[1] 视觉未自活动", TAB_INACTIVE_ENABLED, tabBackground(1));

        activeSignal.set(Integer.valueOf(1));
        runtime.flush();
        doLayout();
        Assert.assertEquals("外部回写后 tabSeg[1] 活动背景", TAB_ACTIVE_ENABLED, tabBackground(1));
        Assert.assertEquals("外部回写后 tabSeg[0] 退活动背景", TAB_INACTIVE_ENABLED, tabBackground(0));
    }

    // ==================== 验收 2：内容区 N 选 1（仅活动页挂载，切页卸旧挂新） ====================

    /**
     * 内容区铁律：activeIndex=0 时只有 page0 内容挂载、page1/page2 未挂载；
     * 外部 set 1 后 page0 卸载、page1 挂载（show 的 condition computed 驱动）。
     */
    @Test
    public void contentPanelShouldMountOnlyActivePageAndSwapOnChange() {
        doLayout();
        List<Integer> mounted0 = mountedPanelIndices();
        Assert.assertEquals("activeIndex=0：仅一页内容挂载", 1, mounted0.size());
        Assert.assertEquals("activeIndex=0：挂载的是 page0", Integer.valueOf(0), mounted0.get(0));
        Assert.assertEquals("page0 builder 应被调用一次", 1, buildCounts.get(0).get());
        Assert.assertEquals("page1 builder 不应被调用", 0, buildCounts.get(1).get());
        Assert.assertEquals("page2 builder 不应被调用", 0, buildCounts.get(2).get());

        // 切到 page1
        activeSignal.set(Integer.valueOf(1));
        runtime.flush();
        doLayout();
        List<Integer> mounted1 = mountedPanelIndices();
        Assert.assertEquals("activeIndex=1：仍仅一页内容挂载", 1, mounted1.size());
        Assert.assertEquals("activeIndex=1：挂载的是 page1", Integer.valueOf(1), mounted1.get(0));
        Assert.assertEquals("切到 1 后 page1 builder 被调用一次", 1, buildCounts.get(1).get());

        // 切到 page2
        activeSignal.set(Integer.valueOf(2));
        runtime.flush();
        doLayout();
        List<Integer> mounted2 = mountedPanelIndices();
        Assert.assertEquals("activeIndex=2：仍仅一页内容挂载", 1, mounted2.size());
        Assert.assertEquals("activeIndex=2：挂载的是 page2", Integer.valueOf(2), mounted2.get(0));
    }

    // ==================== 验收 3：show I7 不重建（活动页保持不变重复 flush 不重建） ====================

    /**
     * show I7 稳定：activeIndex 保持 0 不变，重复 flush（含其它无关 signal 变化触发的 flush）
     * 不重建当前活动页（page0 builder 仍只调用一次）。
     */
    @Test
    public void stableActivePageShouldNotRebuildAcrossFlushes() {
        doLayout();
        Assert.assertEquals("初始 page0 builder 调用一次", 1, buildCounts.get(0).get());

        // 多次无关 flush + 无关 signal 变化（enabled 切换触发 effect 重跑，但 show condition 未变）
        runtime.flush();
        runtime.flush();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();

        Assert.assertEquals("activeIndex 不变：page0 不重建（仍 1 次）", 1, buildCounts.get(0).get());

        // 冗余设同值（memoized 不通知）也不重建
        activeSignal.set(Integer.valueOf(0));
        runtime.flush();
        Assert.assertEquals("activeIndex 设同值：page0 不重建（仍 1 次）", 1, buildCounts.get(0).get());
    }

    // ==================== 验收 4：R6 段穿透（点 tab 内 label 穿透到段） ====================

    /**
     * R6 权威落地：点 tabSeg[1] 内 label 文字几何中心，命中穿透到 tabSeg[1]，
     * tabSeg[1] 进 pressed → 切 pressed 背景；释放合成 CLICK 上抛 1。
     */
    @Test
    public void hitTestShouldPassThroughTabLabelToSegment() {
        doLayout();

        harness.press(labelNode(1));
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 tabSeg[1] → pressed 背景",
                TAB_INACTIVE_PRESSED, tabBackground(1));

        harness.release(labelNode(1));
        Assert.assertEquals("点 label[1] 释放应合成 CLICK 触发 onActivate", 1, activateCount.get());
        Assert.assertEquals("期望下标 1", Integer.valueOf(1), lastActivateValue);
    }

    // ==================== 验收 5：键盘导航（←/→/Home/End/Enter/Space） ====================

    /**
     * 键盘导航：→ 算 cur+1、← 算 cur-1（裁剪边界）、Home 到首页 0、End 到末页 count-1，
     * 各自 onActivate 上抛目标下标 + 焦点移动；Enter/Space 激活当前段。
     */
    @Test
    public void keyboardNavigationRaisesTargetIndexAndMovesFocus() {
        doLayout();
        runtime.requestFocus(tabSeg(0));

        // → cur=0 → 1
        harness.pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals("→ 上抛相邻下标 1", Integer.valueOf(1), lastActivateValue);
        Assert.assertSame("→ 焦点移到 tabSeg[1]", tabSeg(1), runtime.getFocusedNode());

        // End → 末页 2（从 1，回写后）
        activeSignal.set(Integer.valueOf(1));
        runtime.flush();
        harness.pressKey(SceneKey.END);
        Assert.assertEquals("End 上抛末页 2", Integer.valueOf(2), lastActivateValue);
        Assert.assertSame("End 焦点移到 tabSeg[2]", tabSeg(2), runtime.getFocusedNode());

        // Home → 首页 0（从 2，回写后）
        activeSignal.set(Integer.valueOf(2));
        runtime.flush();
        harness.pressKey(SceneKey.HOME);
        Assert.assertEquals("Home 上抛首页 0", Integer.valueOf(0), lastActivateValue);
        Assert.assertSame("Home 焦点移到 tabSeg[0]", tabSeg(0), runtime.getFocusedNode());

        // ← 边界裁剪：cur=0 再 ← 仍 0
        activeSignal.set(Integer.valueOf(0));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_LEFT);
        Assert.assertEquals("← 首段边界裁剪仍 0", Integer.valueOf(0), lastActivateValue);

        // Enter 激活当前段（焦点在 tabSeg[0]）
        runtime.requestFocus(tabSeg(0));
        int before = activateCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onActivate", before + 1, activateCount.get());
        Assert.assertEquals("Enter 激活当前段 0", Integer.valueOf(0), lastActivateValue);

        // Space 激活当前段
        before = activateCount.get();
        harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("Space 应触发一次 onActivate", before + 1, activateCount.get());
    }

    // ==================== 验收 6：disabled 拦截 ====================

    /**
     * disabled 态：CLICK 与键盘 Enter 均不触发 onActivate，tabBar 切灰背景。
     */
    @Test
    public void disabledShouldBlockClickAndKeyboard() {
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("disabled tabSeg[0] 灰背景", TAB_DISABLED, tabBackground(0));
        Assert.assertEquals("disabled tabSeg[1] 灰背景", TAB_DISABLED, tabBackground(1));

        int before = activateCount.get();
        harness.click(tabSeg(1));
        Assert.assertEquals("disabled 态 CLICK 不触发", before, activateCount.get());

        runtime.requestFocus(tabSeg(1));
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled 态 Enter 不触发", before, activateCount.get());
    }

    // ==================== 验收 7：tabBar 四态切换零重排（PAINT 级） ====================

    /**
     * tabBar 选中态四态切换应是纯 PAINT 级零重排（照 SceneSegmented 断言）：
     * enabled↔disabled、pressed、外部 set 活动切换都不触发重排。
     */
    @Test
    public void tabBarStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 tabSeg[1] 非活动背景", TAB_INACTIVE_ENABLED, tabBackground(1));

        // ① enabled → disabled
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("disabled tabSeg[1] 背景", TAB_DISABLED, tabBackground(1));
        Assert.assertEquals("enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("回 enabled tabSeg[1] 背景", TAB_INACTIVE_ENABLED, tabBackground(1));
        Assert.assertEquals("disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：harness.press 命中 tabSeg[1] 几何中心
        result = doLayout();
        harness.press(tabSeg(1));
        result = doLayout();
        Assert.assertEquals("pressed tabSeg[1] 背景", TAB_INACTIVE_PRESSED, tabBackground(1));
        Assert.assertEquals("pressed 零重排", 0, result.getRelayoutCount());

        harness.release(tabSeg(1));
        result = doLayout();
        Assert.assertEquals("释放后回默认背景", TAB_INACTIVE_ENABLED, tabBackground(1));
        Assert.assertEquals("释放 pressed 零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 8：默认工厂路径 = TOOLBAR 底座 + INDICATOR 项配方 ====================

    /**
     * 默认工厂路径（不传任何样式参数）：导航条底座等于 {@code SceneThemes.DEFAULT.surface(Role.TOOLBAR)}
     * 的对应值（染色/缘色/边框宽/圆角/滤镜/实体高度），每个 tab 项等于 {@code Role.INDICATOR} 配方；
     * 选中项 tint 的 RGB 换成主题强调色且 alpha 用统一选中强度 0x59（选中不只靠透明度区分）；
     * 内容区面板保持透明、不装表面（避免第二层玻璃挡死底座）。
     */
    @Test
    public void defaultFactoryShouldUseToolbarBarAndIndicatorItemRecipe() {
        doLayout();

        // 导航条底座 = TOOLBAR 配方（全属性归表面绑定器）
        Assert.assertEquals("底座背景 = TOOLBAR idle tint", BAR_SURFACE.getIdle().getTint(),
                tabBar().getBackgroundColor());
        Assert.assertEquals("底座边框色 = TOOLBAR idle 缘色", BAR_SURFACE.getIdle().getEdge(),
                tabBar().getBorderColor());
        Assert.assertEquals("底座边框宽 = TOOLBAR 配方", BAR_SURFACE.getBorderWidth(), tabBar().getBorderWidth());
        Assert.assertEquals("底座圆角 = TOOLBAR 配方（不再是控件静态常量）", BAR_SURFACE.getCornerRadius(),
                tabBar().getCornerRadius());
        Assert.assertEquals("底座实体高度 = TOOLBAR 配方 idle 档", BAR_SURFACE.getIdle().getElevation(),
                tabBar().__getSurfaceElevation(), EPSILON);
        UiBackdrop barRecipeBackdrop = BAR_SURFACE.getBackdrop();
        Assert.assertNotNull("TOOLBAR 配方自带滤镜", barRecipeBackdrop);
        Assert.assertNotNull("底座应写入配方滤镜", tabBar().getBackdrop());
        Assert.assertEquals("底座滤镜模糊半径 = 配方", barRecipeBackdrop.getBlurRadius(),
                tabBar().getBackdrop().getBlurRadius());
        Assert.assertEquals("底座滤镜材质 = 配方", barRecipeBackdrop.getEffect().getMaterial(),
                tabBar().getBackdrop().getEffect().getMaterial());

        // 每个 tab 项 = INDICATOR 配方（圆角/边框宽/边框色/滤镜均来自配方）
        Assert.assertEquals("tab 项圆角 = INDICATOR 配方", TAB_SURFACE.getCornerRadius(),
                tabSeg(1).getCornerRadius());
        Assert.assertEquals("tab 项边框宽 = INDICATOR 配方", TAB_SURFACE.getBorderWidth(),
                tabSeg(1).getBorderWidth());
        Assert.assertEquals("tab 项边框色 = INDICATOR idle 缘色", TAB_SURFACE.getIdle().getEdge(),
                tabSeg(1).getBorderColor());
        Assert.assertEquals("tab 项实体高度 = INDICATOR 配方 idle 档", TAB_SURFACE.getIdle().getElevation(),
                tabSeg(1).__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("INDICATOR 配方自带滤镜", TAB_SURFACE.getBackdrop());
        Assert.assertNotNull("tab 项应写入配方滤镜（每项一次绑定）", tabSeg(1).getBackdrop());
        Assert.assertEquals("tab 项滤镜模糊半径 = 配方", TAB_SURFACE.getBackdrop().getBlurRadius(),
                tabSeg(1).getBackdrop().getBlurRadius());

        // 选中项 = 强调色系 tint：RGB 换强调色、alpha 用统一选中强度 0x59
        Assert.assertEquals("选中项 tint = 配方 tint 换强调色", TAB_ACTIVE_ENABLED, tabBackground(0));
        Assert.assertEquals("选中 tint RGB 为强调色", rgbOf(SceneThemes.DEFAULT.accent()), rgbOf(tabBackground(0)));
        Assert.assertEquals("选中 tint 用主题统一选中强度 0x59", 0x59, alpha(tabBackground(0)));
        Assert.assertTrue("选中强度必须高于未选中档，否则读不出选中",
                alpha(tabBackground(0)) > alpha(TAB_INACTIVE_ENABLED));
        Assert.assertNotEquals("选中与未选中必须可区分（不能只靠透明度）",
                TAB_INACTIVE_ENABLED, tabBackground(0));
        Assert.assertEquals("未选中项 tint = INDICATOR idle tint", TAB_INACTIVE_ENABLED, tabBackground(1));

        // 选中项按下档：仍保留 0x59 强度、RGB 取强调按下色（选中语义不因交互态丢失）
        harness.press(tabSeg(0));
        doLayout();
        Assert.assertEquals("选中项 pressed tint = 强调按下档", TAB_ACTIVE_PRESSED, tabBackground(0));
        Assert.assertEquals("选中项 pressed RGB 为强调按下色", rgbOf(SceneThemes.DEFAULT.accentPressed()),
                rgbOf(tabBackground(0)));
        harness.release(tabSeg(0));

        // 内容区面板：透明 + 零圆角 + 不装滤镜（无表面绑定）
        Assert.assertEquals("contentPanel 保持透明", 0, contentPanel().getBackgroundColor());
        Assert.assertEquals("contentPanel 不设圆角", 0, contentPanel().getCornerRadius());
        Assert.assertNull("contentPanel 不装滤镜（避免第二层玻璃挡死底座）", contentPanel().getBackdrop());
        Assert.assertTrue("contentPanel 未绑定表面（surfaceElevation 保持未绑定）",
                contentPanel().__getSurfaceElevation() < 0.0F);
    }

    // ==================== 验收 9：每项一颗表面只采样一次，内容区零滤镜 ====================

    /**
     * 滤镜采样预算：每个 tab 项恰好 1 条 BACKDROP（配方自带，每项一次绑定、项内不嵌套第二层玻璃），
     * 内容区面板 0 条；整树 = 底座 1 条 + 每项 1 条。
     */
    @Test
    public void paintPlanShouldSampleOneBackdropPerTabAndNoneForContentPanel() {
        doLayout();

        for (int i = 0; i < LABELS.size(); i++) {
            List<PaintCommand> commands = doPaint(tabSeg(i)).getCommands();
            Assert.assertEquals("tab 项 " + i + " 恰好一条 BACKDROP（配方自带，不重复安装）", 1,
                    countType(commands, PaintCommandType.BACKDROP));
            Assert.assertTrue("tab 项 " + i + " 应有 BACKGROUND（半透明 tint 叠玻璃之上）",
                    countType(commands, PaintCommandType.BACKGROUND) >= 1);
        }

        Assert.assertEquals("contentPanel 不得产出 BACKDROP", 0,
                countType(doPaint(contentPanel()).getCommands(), PaintCommandType.BACKDROP));
        Assert.assertEquals("整树 = 底座 1 条 + 每项 1 条 BACKDROP", 1 + LABELS.size(),
                countType(doPaint(sceneRoot).getCommands(), PaintCommandType.BACKDROP));
    }

    // ==================== 验收 10：hover/focus 遵守 INDICATOR 配方 ====================

    /**
     * hover 取配方 hovered 档、hover 出回 idle；focus 只覆盖缘色（不改染色），取配方 focusEdge。
     */
    @Test
    public void hoverAndFocusShouldFollowIndicatorRecipe() {
        doLayout();

        harness.moveTo(tabSeg(1));
        doLayout();
        Assert.assertEquals("hover 取 INDICATOR 配方 hovered 档", TAB_INACTIVE_HOVER, tabBackground(1));

        runtime.requestFocus(tabSeg(1));
        runtime.flush();
        Assert.assertEquals("focus 只覆盖缘色，不改染色", TAB_INACTIVE_HOVER, tabBackground(1));
        Assert.assertEquals("focus 缘色取配方 focusEdge", TAB_SURFACE.getFocusEdge(), tabSeg(1).getBorderColor());

        harness.moveAt(CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);
        doLayout();
        Assert.assertEquals("hover 出回 INDICATOR idle 档", TAB_INACTIVE_ENABLED, tabBackground(1));
    }

    // ==================== 验收 11：文字前景 = 主题正文/禁用前景 + 选中对比度择色 ====================

    /**
     * 文字遵守统一配方：未选中取主题正文色；选中在「强调底前景」与「正文色」之间按对比度择一；
     * 禁用取主题禁用前景色（不透明可读）。
     */
    @Test
    public void labelForegroundShouldFollowThemeAndSelection() {
        doLayout();

        Assert.assertEquals("未选中 label = 主题正文色", LABEL_ENABLED, labelNode(1).getTextColor());
        Assert.assertEquals("未选中 label[2] = 主题正文色", LABEL_ENABLED, labelNode(2).getTextColor());
        Assert.assertEquals("选中 label = 对比度更优候选",
                expectedSelectedLabel(TAB_SURFACE, BAR_SURFACE, SceneThemes.DEFAULT.accent(),
                        SceneThemes.DEFAULT.onAccentForeground(), SceneThemes.DEFAULT.foreground()),
                labelNode(0).getTextColor());
        Assert.assertTrue("选中文字必须是主题的两个候选之一",
                labelNode(0).getTextColor() == SceneThemes.DEFAULT.onAccentForeground()
                        || labelNode(0).getTextColor() == SceneThemes.DEFAULT.foreground());
        Assert.assertEquals("深色默认档：强调底前景对比度更优", SceneThemes.DEFAULT.onAccentForeground(),
                labelNode(0).getTextColor());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        for (int i = 0; i < LABELS.size(); i++) {
            Assert.assertEquals("禁用 label[" + i + "] = 主题禁用前景色", LABEL_DISABLED,
                    labelNode(i).getTextColor());
        }
        Assert.assertEquals("禁用前景不透明可读", 0xFF, alpha(LABEL_DISABLED));

        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("恢复启用 label 回正文色", LABEL_ENABLED, labelNode(1).getTextColor());
    }

    // ==================== 验收 12：主题切换更新外观且不丢当前页/不重建/不增订阅 ====================

    /**
     * {@code SceneThemes.withTheme} 页面主题信号更新 + flush 后：底座/项/文字全部随新主题更新，
     * 当前页仍挂载且不重建，节点身份不变、effect 数不增长；卸载后绑定全部回收。
     */
    @Test
    public void themeSwitchShouldUpdateBarItemsAndTextKeepingPageAndBindings() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 TOOLBAR 配方必须不同",
                dark.surface(SceneTheme.Role.TOOLBAR), light.surface(SceneTheme.Role.TOOLBAR));
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方必须不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));

        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        List<AtomicInteger> themedBuilds = new ArrayList<>();
        List<Supplier<SceneNode>> panels = new ArrayList<>();
        for (int idx = 0; idx < LABELS.size(); idx++) {
            final int i = idx;
            AtomicInteger count = new AtomicInteger(0);
            themedBuilds.add(count);
            panels.add(() -> {
                count.incrementAndGet();
                SceneNode panel = new SceneNode();
                panel.setBackgroundColor(PANEL_BG[i]);
                panel.setPreferredHeight(40);
                return panel;
            });
        }
        SceneTab.Props props = new SceneTab.Props(activeSignal, LABELS, panels, enabledSignal, next -> { });

        SceneNode host = new SceneNode();
        MountHandle themed = runtime.mount(host, () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneTab.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();

        SceneNode themedRoot = themed.getRoot();
        SceneNode themedBar = themedRoot.__getChildren().get(0);
        SceneNode themedContent = themedRoot.__getChildren().get(1);
        SceneNode themedItem0 = themedBar.__getChildren().get(0);
        SceneNode themedItem1 = themedBar.__getChildren().get(1);
        SceneNode themedLabel0 = themedItem0.__getChildren().get(0);
        SceneNode themedLabel1 = themedItem1.__getChildren().get(0);

        Assert.assertEquals("初始底座 = 深色 TOOLBAR idle tint",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), themedBar.getBackgroundColor());
        Assert.assertEquals("初始未选中项 = 深色 INDICATOR idle tint",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), themedItem1.getBackgroundColor());
        Assert.assertEquals("初始选中项 = 深色强调档",
                selectedTint(dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), dark.accent()),
                themedItem0.getBackgroundColor());
        Assert.assertEquals("初始未选中文字 = 深色正文色", dark.foreground(), themedLabel1.getTextColor());
        Assert.assertEquals("初始选中文字 = 深色对比度择优",
                expectedSelectedLabel(dark.surface(SceneTheme.Role.INDICATOR),
                        dark.surface(SceneTheme.Role.TOOLBAR), dark.accent(),
                        dark.onAccentForeground(), dark.foreground()),
                themedLabel0.getTextColor());
        Assert.assertEquals("深色档选中文字取强调底前景", dark.onAccentForeground(), themedLabel0.getTextColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertSame("主题切换不重建 tabBar", themedBar, themedRoot.__getChildren().get(0));
        Assert.assertSame("主题切换不重建 tab 项", themedItem1, themedBar.__getChildren().get(1));
        Assert.assertSame("主题切换不重建 label", themedLabel0, themedItem0.__getChildren().get(0));
        Assert.assertSame("主题切换不重建内容区", themedContent, themedRoot.__getChildren().get(1));

        Assert.assertEquals("底座染色随主题更新", light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(),
                themedBar.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", light.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(),
                themedBar.getCornerRadius());
        Assert.assertEquals("底座滤镜随主题更新", light.surface(SceneTheme.Role.TOOLBAR).getBackdrop().getBlurRadius(),
                themedBar.getBackdrop().getBlurRadius());
        Assert.assertEquals("未选中项随主题更新", light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                themedItem1.getBackgroundColor());
        Assert.assertEquals("选中项随主题更新",
                selectedTint(light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), light.accent()),
                themedItem0.getBackgroundColor());
        Assert.assertEquals("未选中文字随主题更新", light.foreground(), themedLabel1.getTextColor());
        Assert.assertEquals("选中文字随主题更新",
                expectedSelectedLabel(light.surface(SceneTheme.Role.INDICATOR),
                        light.surface(SceneTheme.Role.TOOLBAR), light.accent(),
                        light.onAccentForeground(), light.foreground()),
                themedLabel0.getTextColor());
        Assert.assertEquals("浅色档选中文字改取正文色（0x59 强调 tint 叠浅玻璃上白字不可读）",
                light.foreground(), themedLabel0.getTextColor());

        Assert.assertEquals("主题切换后当前页仍挂载", Arrays.asList(Integer.valueOf(0)),
                mountedPanelIndices(themedContent));
        Assert.assertEquals("主题切换不重建当前页内容", 1, themedBuilds.get(0).get());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        themed.dispose();
        Assert.assertEquals("卸载后回收该实例全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 验收 13：卸载回收绑定（effect 探针） ====================

    /**
     * 挂载注册响应式绑定、卸载全部回收：{@code ReactiveTestProbe.registeredEffectCount()}
     * 回到挂载前基线（守「卸载即回收」纪律）。
     */
    @Test
    public void disposeShouldReclaimAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        List<Supplier<SceneNode>> panels = Arrays.<Supplier<SceneNode>>asList(
                () -> new SceneNode(), () -> new SceneNode(), () -> new SceneNode());
        SceneTab.Props props = new SceneTab.Props(activeSignal, LABELS, panels, enabledSignal, next -> { });

        MountHandle extra = runtime.mount(sceneRoot, SceneTab.create(runtime, props));
        runtime.flush();

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted,
                mounted > baseline);

        extra.dispose();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 验收 14：关闭滤镜档（withoutBackdrop）仍可读 ====================

    /**
     * 无滤镜替代档：{@code SceneTheme.withoutBackdrop()} 下底座/项 backdrop 全为 null、PaintPlan 零
     * BACKDROP，tint 换为不透明替代底色（可读），选中项仍是强调色系、文字仍按对比度择色。
     */
    @Test
    public void withoutBackdropThemeShouldDropBackdropsAndKeepReadableTints() {
        SceneTheme noFilter = SceneTheme.liquidGlassDark().withoutBackdrop();
        Assert.assertNull("前提：无滤镜档 TOOLBAR 无 backdrop",
                noFilter.surface(SceneTheme.Role.TOOLBAR).getBackdrop());
        Assert.assertNull("前提：无滤镜档 INDICATOR 无 backdrop",
                noFilter.surface(SceneTheme.Role.INDICATOR).getBackdrop());

        Signal<SceneTheme> pageTheme = Signal.create(noFilter);
        List<Supplier<SceneNode>> panels = Arrays.<Supplier<SceneNode>>asList(
                () -> new SceneNode(), () -> new SceneNode(), () -> new SceneNode());
        SceneTab.Props props = new SceneTab.Props(activeSignal, LABELS, panels, enabledSignal, next -> { });
        MountHandle mounted = runtime.mount(new SceneNode(), () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneTab.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();

        SceneNode root = mounted.getRoot();
        SceneNode bar = root.__getChildren().get(0);
        SceneNode item0 = bar.__getChildren().get(0);
        SceneNode item1 = bar.__getChildren().get(1);
        SceneNode label0 = item0.__getChildren().get(0);

        Assert.assertNull("关滤镜：底座不装 backdrop", bar.getBackdrop());
        Assert.assertNull("关滤镜：tab 项不装 backdrop", item1.getBackdrop());
        Assert.assertEquals("关滤镜：底座 tint = 不透明替代底色",
                noFilter.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), bar.getBackgroundColor());
        Assert.assertEquals("关滤镜：未选中项 tint = 不透明替代底色",
                noFilter.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), item1.getBackgroundColor());
        Assert.assertEquals("关滤镜：选中项仍是强调色系（0x59 叠不透明底）",
                selectedTint(noFilter.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), noFilter.accent()),
                item0.getBackgroundColor());
        Assert.assertEquals("关滤镜：选中文字仍按对比度择色",
                expectedSelectedLabel(noFilter.surface(SceneTheme.Role.INDICATOR),
                        noFilter.surface(SceneTheme.Role.TOOLBAR), noFilter.accent(),
                        noFilter.onAccentForeground(), noFilter.foreground()),
                label0.getTextColor());

        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("关滤镜：整树零 BACKDROP", 0,
                countType(doPaint(root).getCommands(), PaintCommandType.BACKDROP));
    }

    // ==================== 验收 15：Props 同长同序契约运行期校验（P1-D） ====================

    /**
     * P1-D 修复验收：tabLabels 与 tabPanels 长度不匹配时，Props 构造期 fail-fast
     * 抛 IllegalArgumentException，而不是延迟到建树循环 items.get(idx) 越界 IndexOutOfBoundsException。
     *
     * <p>覆盖三种违例：panels 多于 labels（原崩溃路径）、labels 多于 panels、null 入参。</p>
     */
    @Test
    public void propsShouldRejectMismatchedLabelsAndPanelsSizes() {
        Signal<Integer> active = Signal.create(Integer.valueOf(0));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        Consumer<Integer> onActivate = next -> { };

        // panels 多于 labels：原 IndexOutOfBoundsException 崩溃路径，应前移为 IllegalArgumentException
        List<Supplier<SceneNode>> morePanels = Arrays.asList(
                () -> new SceneNode(),
                () -> new SceneNode(),
                () -> new SceneNode());
        try {
            new SceneTab.Props(active, Arrays.asList("仅一个"), morePanels, enabled, onActivate);
            Assert.fail("panels 多于 labels 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息应含同长度提示",
                    expected.getMessage().contains("同长度同序"));
        }

        // labels 多于 panels：对称违例
        List<Supplier<SceneNode>> fewerPanels = Arrays.asList(() -> new SceneNode());
        try {
            new SceneTab.Props(active, Arrays.asList("常规", "外观", "高级"), fewerPanels, enabled, onActivate);
            Assert.fail("labels 多于 panels 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息应含同长度提示",
                    expected.getMessage().contains("同长度同序"));
        }

        // tabLabels 为 null
        try {
            new SceneTab.Props(active, null, morePanels, enabled, onActivate);
            Assert.fail("tabLabels 为 null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息应含 null 提示",
                    expected.getMessage().contains("null"));
        }

        // tabPanels 为 null
        try {
            new SceneTab.Props(active, Arrays.asList("常规"), null, enabled, onActivate);
            Assert.fail("tabPanels 为 null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息应含 null 提示",
                    expected.getMessage().contains("null"));
        }

        // 等长（含空列表）应通过校验，不抛
        new SceneTab.Props(active, Arrays.asList("常规"),
                Arrays.asList((Supplier<SceneNode>) () -> new SceneNode()), enabled, onActivate);
        new SceneTab.Props(active, Arrays.asList(), Arrays.asList(), enabled, onActivate);
    }

    // ==================== 验收 16：fillContentPanel=true 父高传导（打通 4 处断裂点） ====================

    /**
     * fill 传导核心：fillContentPanel=true 时，root/tabBar/contentPanel 三处静态配置打通，
     * 活动页内容 panel（自行 setFillParentHeight）吃到父分配高，contentPanel 填满 holder。
     *
     * <p>结构：独立 layout 根(COLUMN) → holder(fill) → SceneTab(fillContentPanel=true)。
     * 活动页 panel preferredHeight=40（自然高）+ setFillParentHeight(true)。
     * 断言：tabBar 高==preferredHeight(36，验证 borderWidth=1 不撑高，风险点)、
     * contentPanel 高==分配剩余(200-36-8=156)、活动页 panel 高==156（远大于自然高 40）。</p>
     */
    @Test
    public void fillContentPanelShouldPropagateParentHeightToActivePage() {
        FillSetup s = mountFillTab(true);
        int expectedContentH = FILL_CANVAS_HEIGHT - FILL_TAB_BAR_PREFERRED_H - FILL_ROOT_GAP; // 156
        // 断裂点③：tabBar preferredHeight 生效（36），borderWidth=1 不撑高（风险点验证）
        LayoutAssertions.assertHeight(s.tabBar(), FILL_TAB_BAR_PREFERRED_H);
        // 断裂点①②：contentPanel fill 吃满父分配剩余高
        LayoutAssertions.assertHeight(s.contentPanel(), expectedContentH);
        // 断裂点④：活动页 panel fill 传导成功（>> 自然高 40）
        LayoutAssertions.assertHeight(s.panelRefs[0], expectedContentH);
    }

    // ==================== 验收 17：fillContentPanel=false 向后兼容（shrink） ====================

    /**
     * 向后兼容：fillContentPanel=false（5 参重载默认值）时，contentPanel 按内容自然高 shrink，
     * 不传导父高。活动页 panel 即便 setFillParentHeight(true)，因 contentPanel 无确定高约束下传，
     * fill 门槛（SizingCalculator：需确定高约束才生效）不满足而回退 shrink，高==preferredHeight(40)。
     */
    @Test
    public void fillContentPanelFalseShouldShrinkToContentNaturalHeight() {
        FillSetup s = mountFillTab(false);
        // contentPanel shrink 到活动页 panel 自然高 40（与 fill 模式 156 形成对比，证明不传导）
        LayoutAssertions.assertHeight(s.contentPanel(), FILL_PANEL_NATURAL_H);
        LayoutAssertions.assertHeight(s.panelRefs[0], FILL_PANEL_NATURAL_H);
    }

    // ==================== 验收 18：fill 模式切页 contentPanel 高零重排 ====================

    /**
     * fill 模式切页稳定：contentPanel 高由父分配决定，与活动页内容自然高无关。
     * activeIndex 0→1（两页 panel 同 preferredHeight=40+fill），contentPanel 高切页前后不变。
     */
    @Test
    public void fillModePageSwitchShouldKeepContentPanelHeight() {
        FillSetup s = mountFillTab(true);
        int expected = FILL_CANVAS_HEIGHT - FILL_TAB_BAR_PREFERRED_H - FILL_ROOT_GAP; // 156
        LayoutAssertions.assertHeight(s.contentPanel(), expected);

        // 切到 page1（两页 panel 同 preferredHeight+fill，contentPanel 高由父分配决定）
        s.active.set(Integer.valueOf(1));
        runtime.flush();
        layoutEngine.layout(s.holder, new Constraints(CANVAS_WIDTH, FILL_CANVAS_HEIGHT));
        LayoutAssertions.assertHeight(s.contentPanel(), expected); // 切页后仍 156
        LayoutAssertions.assertHeight(s.panelRefs[1], expected);   // page1 也填满
    }

    // ==================== fill 测试辅助 ====================

    /** fill 测试 layout 根高度（CANVAS_HEIGHT 同值，语义独立常量） */
    private static final int FILL_CANVAS_HEIGHT = 200;
    /** fill 模式 tabBar preferredHeight = stub lineHeight(16) + 2*PAD_LG(10) = 36，照 SceneTab fill 公式镜像 */
    private static final int FILL_TAB_BAR_PREFERRED_H = 16 + 2 * SceneChromeTokens.PAD_LG;
    /** SceneTab.ROOT_GAP 镜像（tabBar 与 contentPanel 纵向间距，private 不可直访） */
    private static final int FILL_ROOT_GAP = 8;
    /** 活动页 panel 自然高（preferredHeight，fill 不生效时的回退高） */
    private static final int FILL_PANEL_NATURAL_H = 40;

    /**
     * fill 传导测试搭建：以 fill 父 holder 作 layout 根 → 挂 SceneTab(fillContentPanel)。
     * 各页 panel 均 setFillParentHeight(true)+preferredHeight(40)，用 PANEL_BG 标识。
     *
     * <p><b>为何 holder 直接作 layout 根</b>：若外加一层非 fill 的 COLUMN 根，根会 shrink-to-fit
     * 不下传确定高，holder 的 fill 收到 UNCONSTRAINED 回退 shrink，整条 fill 链失效。
     * holder 作根 + setFillParentHeight(true) + Constraints(W,H)，由 computeHeight 的 fill 分支
     * （max(contentHeight, 约束高)）吃满 H 并下传确定高给 SceneTab root（复现 StressTest 真实场景）。</p>
     *
     * <p>用独立 layout 根，不复用 setUp 的 sceneRoot（避免与默认非 fill tab 挂载冲突），
     * 仅复用 runtime/layoutEngine/harness。</p>
     *
     * @param fill fillContentPanel 选项
     * @return 搭建产物（含 holder/active/各页 panel 引用）
     */
    private FillSetup mountFillTab(boolean fill) {
        Signal<Integer> active = Signal.create(Integer.valueOf(0));
        SceneNode[] panelRefs = new SceneNode[LABELS.size()];
        List<Supplier<SceneNode>> panels = new ArrayList<>();
        for (int idx = 0; idx < LABELS.size(); idx++) {
            final int i = idx;
            panels.add(() -> {
                SceneNode panel = new SceneNode();
                panel.setBackgroundColor(PANEL_BG[i]);
                panel.setPreferredHeight(FILL_PANEL_NATURAL_H);
                panel.setFillParentHeight(true);
                panelRefs[i] = panel;
                return panel;
            });
        }
        SceneTab.Props props = new SceneTab.Props(active, LABELS, panels,
                Signal.create(Boolean.TRUE), next -> { }, fill);
        // holder 作 layout 根 + fill：吃满 Constraints 高并下传确定高给 SceneTab root
        SceneNode holder = SceneNode.column();
        holder.setFillParentHeight(true);
        runtime.mount(holder, SceneTab.create(runtime, props));
        runtime.flush();
        layoutEngine.layout(holder, new Constraints(CANVAS_WIDTH, FILL_CANVAS_HEIGHT));
        return new FillSetup(holder, active, panelRefs);
    }

    /** fill 测试搭建产物：持有 layout 根(holder) 与活动页受控源，便捷访问各层节点。 */
    private static final class FillSetup {
        /** layout 根，即 fill 父 holder */
        final SceneNode holder;
        final Signal<Integer> active;
        final SceneNode[] panelRefs;
        FillSetup(SceneNode holder, Signal<Integer> active, SceneNode[] panelRefs) {
            this.holder = holder;
            this.active = active;
            this.panelRefs = panelRefs;
        }
        /** holder 第 0 子为 SceneTab root（mount 挂载点） */
        SceneNode tabRoot() { return holder.__getChildren().get(0); }
        /** tabRoot 第 0 子为 tabBar */
        SceneNode tabBar() { return tabRoot().__getChildren().get(0); }
        /** tabRoot 第 1 子为 contentPanel */
        SceneNode contentPanel() { return tabRoot().__getChildren().get(1); }
    }
}
