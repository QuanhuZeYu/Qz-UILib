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
 * SceneBreadcrumb 端到端单元测试 —— Phase 4 批 2 纯展示 + 回调控件验收（G09/Breadcrumb 主题化后）。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 点击回调（点 segBtn 上抛该段 path）、命中穿透（点 label 装饰子节点穿透到所属 segBtn）、
 * 交互态切换零重排、键盘激活（Enter/Space）+ disabled 拦截；
 * 以及主题化外观——路径文字/分隔符/禁用文字取主题语义色、段只做 INDICATOR 配方的极淡状态覆盖
 * （默认透明、不装表面/滤镜）、withTheme 切换后文字与覆盖色更新且节点身份不变/订阅不增长、
 * 禁用态可读、卸载回收全部绑定。</p>
 *
 * <h3>root children 序列（separator 仅非首段）</h3>
 * <pre>
 *   [segBtn0, sep1, segBtn1, sep2, segBtn2]
 * </pre>
 */
public class SceneBreadcrumbTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/moveTo/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    private Signal<Boolean> enabledSignal;
    /** onSelect 触发计数器 */
    private AtomicInteger selectCount;
    /** onSelect 最近一次收到的 path */
    private String lastSelectPath;

    private MountHandle handle;
    /** breadcrumb 根节点 */
    private SceneNode crumbRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    /**
     * 库默认主题的 INDICATOR 角色配方：段状态覆盖色的唯一来源。
     * 只消费配方 tint 档，不装配方表面（面包屑段不装滤镜/边框，见实现类取舍说明）。
     */
    private static final SceneSurfaceStyle INDICATOR_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    /** 段背景期望：默认/禁用透明，hover/pressed 取配方同名档 tint，focus 取 idle 档（极淡覆盖）。 */
    private static final int SEGBTN_DEFAULT = 0;
    private static final int SEGBTN_HOVER = INDICATOR_SURFACE.getHovered().getTint();
    private static final int SEGBTN_PRESSED = INDICATOR_SURFACE.getPressed().getTint();
    private static final int SEGBTN_FOCUSED = INDICATOR_SURFACE.getIdle().getTint();
    /**
     * 路径文字：启用取主题正文色（当前段与路径段同色——accent 在深色档对宿主底代理色仅 1.54:1，
     * 正文色为 11.12:1，优先保证「路径文字保持可读」，与 G09/Segmented 同口径）。
     */
    private static final int LABEL_ENABLED = SceneThemes.DEFAULT.foreground();
    /** 禁用文字：主题禁用前景色（不透明，WCAG 豁免档仍要求可辨）。 */
    private static final int LABEL_DISABLED = SceneThemes.DEFAULT.disabledForeground();
    /** 分隔符：主题次要前景色。 */
    private static final int SEPARATOR_COLOR = SceneThemes.DEFAULT.mutedForeground();

    private static final List<SceneBreadcrumb.Segment> SEGMENTS = Arrays.asList(
            new SceneBreadcrumb.Segment("/", "Home"),
            new SceneBreadcrumb.Segment("/docs", "Docs"),
            new SceneBreadcrumb.Segment("/docs/api", "API"));

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectPath = null;

        SceneBreadcrumb.Props props = new SceneBreadcrumb.Props(
                SEGMENTS, enabledSignal,
                path -> {
                    selectCount.incrementAndGet();
                    lastSelectPath = path;
                });
        handle = runtime.mount(sceneRoot, SceneBreadcrumb.create(runtime, props));
        crumbRoot = handle.getRoot();

        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click/moveTo/pressKey 取中心 + route
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

    /**
     * segBtn[i] 节点。root children 序列为 [segBtn0, sep1, segBtn1, sep2, segBtn2]，
     * segBtn[0] 在索引 0，其余 segBtn[i] 在索引 2*i-1+1 = 2*i（首段无 separator）。
     *
     * @param i 段下标
     * @return segBtn[i] 节点
     */
    private SceneNode segBtnNode(int i) {
        // i=0 → 索引 0；i>=1 → 索引 2*i（前面有 i 个 segBtn + i 个 sep... 实际：每段贡献 1 segBtn，每非首段额外 1 sep）
        // 序列：idx0=segBtn0, idx1=sep1, idx2=segBtn1, idx3=sep2, idx4=segBtn2 → segBtn[i] 在 2*i
        return crumbRoot.__getChildren().get(2 * i);
    }

    /** separator[i] 节点（仅 i>=1 存在，索引 2*i-1） */
    private SceneNode separatorNode(int i) {
        return crumbRoot.__getChildren().get(2 * i - 1);
    }

    /** segBtn[i] 的 label 子节点（segBtn 第一个孩子） */
    private SceneNode labelNode(int i) {
        return segBtnNode(i).__getChildren().get(0);
    }

    private LayoutBox box(SceneNode n) {
        return (LayoutBox) n.getCachedLayout();
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /**
     * 节点自身 PaintFragment 内的 BACKDROP 命令数；无 fragment（无绘制内容）视为 0。
     * 用于验证「面包屑段不装表面/滤镜」——每颗节点都不应出现玻璃采样命令。
     */
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

    /** WCAG 相对对比度：(L亮 + 0.05) / (L暗 + 0.05)。 */
    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        double brighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);
        return (brighter + 0.05) / (darker + 0.05);
    }

    /** WCAG 相对亮度（sRGB 线性化后加权，忽略 alpha）。 */
    private static double relativeLuminance(int argb) {
        return 0.2126 * linearize((argb >>> 16) & 0xFF)
                + 0.7152 * linearize((argb >>> 8) & 0xFF)
                + 0.0722 * linearize(argb & 0xFF);
    }

    /** sRGB 通道线性化。 */
    private static double linearize(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    // ==================== 验收 1：点击回调（点 segBtn[2] 上抛对应 path） ====================

    /**
     * 点 segBtn[2] 几何中心 → onSelect 收到对应 path "/docs/api"。
     */
    @Test
    public void clickSegmentShouldRaiseOnSelectWithPath() {
        doLayout();
        harness.click(segBtnNode(2));
        Assert.assertEquals("点 segBtn[2] 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("onSelect 应收到对应 path", "/docs/api", lastSelectPath);

        // 再点 segBtn[0] → path "/"
        harness.click(segBtnNode(0));
        Assert.assertEquals("点 segBtn[0] 累计两次 onSelect", 2, selectCount.get());
        Assert.assertEquals("onSelect 应收到首段 path", "/", lastSelectPath);
    }

    // ==================== 验收 2：命中穿透（点 label 装饰子节点穿透到 segBtn） ====================

    /**
     * 命中穿透：点 label[1] 几何中心，穿透到 segBtn[1]，segBtn[1] 进 pressed 覆盖。
     */
    @Test
    public void hitTestShouldPassThroughDecorativeLabelToSegBtn() {
        doLayout();

        // 按下 label[1] 中心 → 穿透到 segBtn[1] → pressed 覆盖（配方 pressed 档）
        harness.press(labelNode(1));
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 segBtn[1] → pressed 覆盖",
                SEGBTN_PRESSED, segBtnNode(1).getBackgroundColor());

        // 释放 → 合成 CLICK → onSelect 收到 segBtn[1] path
        harness.release(labelNode(1));
        Assert.assertEquals("点 label[1] 释放应合成 CLICK 触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("期望 path /docs", "/docs", lastSelectPath);
    }

    // ==================== 验收 3：交互态切换零重排（终极反证 R-D） ====================

    /**
     * hover/pressed/focus 切换帧 {@code result.getRelayoutCount()==0}——交互态没被误做成布局级的终极证明。
     * 状态覆盖色全部取 INDICATOR 配方对应档 tint（极淡），不装表面/滤镜。
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segBtn[1] 透明背景", SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());

        // ① hover 进 → 配方 hovered 档极淡覆盖，零重排
        harness.moveTo(segBtnNode(1));
        result = doLayout();
        Assert.assertEquals("hover segBtn[1] 覆盖取配方 hovered 档",
                SEGBTN_HOVER, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: hover 进零重排", 0, result.getRelayoutCount());

        // ② pressed → 配方 pressed 档，零重排
        harness.press(segBtnNode(1));
        result = doLayout();
        Assert.assertEquals("pressed segBtn[1] 覆盖取配方 pressed 档",
                SEGBTN_PRESSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        // ③ 释放 pressed：回 hover 覆盖（指针仍在内），零重排
        harness.release(segBtnNode(1));
        result = doLayout();
        Assert.assertEquals("释放后回 hover 覆盖", SEGBTN_HOVER, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());

        // ④ hover 出 → 指针离开 segBtn[1]，但 DOWN 时已隐式 focus，focus 保持极淡 idle 档覆盖，零重排
        harness.moveAt(CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);
        result = doLayout();
        Assert.assertEquals("hover 出后 focus 覆盖取配方 idle 档",
                SEGBTN_FOCUSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("hover 出后 focus 文字保持主题正文色",
                LABEL_ENABLED, labelNode(1).getTextColor());
        Assert.assertEquals("R-D: hover 出零重排", 0, result.getRelayoutCount());
    }

    /**
     * Breadcrumb 段按钮宽度由真实文本测量驱动，不再使用字符数估算宽度。
     */
    @Test
    public void segmentWidthShouldUseMeasuredTextWidth() {
        doLayout();

        int horizontalPadding = 2 * SceneChromeTokens.PAD_MD;
        Assert.assertEquals("Home 段宽应等于测量文本宽加水平 padding",
                4 * STUB_CHAR_WIDTH + horizontalPadding, box(segBtnNode(0)).getWidth());
        Assert.assertEquals("Docs 段宽应等于测量文本宽加水平 padding",
                4 * STUB_CHAR_WIDTH + horizontalPadding, box(segBtnNode(1)).getWidth());
        Assert.assertEquals("API 段宽应等于测量文本宽加水平 padding",
                3 * STUB_CHAR_WIDTH + horizontalPadding, box(segBtnNode(2)).getWidth());
    }

    // ==================== 验收 3.5：focus 视觉态（极淡 idle 档覆盖，不靠文字变色） ====================

    /**
     * focus segBtn[1] → 背景取配方 idle 档极淡覆盖（不装边框/滤镜），文字保持主题正文色；
     * 失焦后回透明背景。零重排。
     */
    @Test
    public void focusStateShouldCoverSegmentWithFaintIndicatorTint() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segBtn[1] 透明背景", SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("初始 segBtn[1] 文字主题正文色", LABEL_ENABLED, labelNode(1).getTextColor());

        runtime.requestFocus(segBtnNode(1));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("focused segBtn[1] 覆盖取配方 idle 档（极淡）",
                SEGBTN_FOCUSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("focused segBtn[1] 文字保持主题正文色（不靠变色，保持可读）",
                LABEL_ENABLED, labelNode(1).getTextColor());
        Assert.assertNull("focused 段仍不装滤镜", segBtnNode(1).getBackdrop());
        Assert.assertEquals("R-D: focus 零重排", 0, result.getRelayoutCount());

        // 焦点切到 segBtn[0] → segBtn[1] 失焦回透明背景 + 正文色
        runtime.requestFocus(segBtnNode(0));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("失焦后 segBtn[1] 回透明背景",
                SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("失焦后 segBtn[1] 文字仍为正文色",
                LABEL_ENABLED, labelNode(1).getTextColor());
    }

    // ==================== 验收 4：键盘激活（Enter/Space），disabled 拦截 ====================
    /**
     * Enter/Space 键盘激活调 onSelect 上抛聚焦段 path；disabled 态键盘/点击均不触发。
     */
    @Test
    public void keyboardActivationRaisesOnSelectAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(segBtnNode(1));

        // ① Enter 激活 → 上抛 segBtn[1] path
        int before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 期望 path /docs", "/docs", lastSelectPath);

        // ② Space 激活
        before = selectCount.get();
        harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("Space 应触发一次 onSelect", before + 1, selectCount.get());

        // ③ disabled 态：Enter / CLICK 均不触发
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled 态 Enter 不触发", before, selectCount.get());

        doLayout();
        harness.click(segBtnNode(1));
        Assert.assertEquals("disabled 态 CLICK 不触发", before, selectCount.get());
    }

    // ==================== 验收 5：默认工厂路径消费主题语义色 ====================

    /**
     * 默认工厂路径（不传样式参数、无局部/运行时主题）：路径文字与分隔符取库默认主题语义色，
     * 段默认透明、静态几何保留；状态覆盖色取 INDICATOR 配方 tint 档且都是半透明极淡覆盖。
     */
    @Test
    public void defaultFactoryShouldUseThemeSemanticColors() {
        doLayout();

        for (int i = 0; i < SEGMENTS.size(); i++) {
            Assert.assertEquals("段[" + i + "] 文字 = 主题正文色",
                    LABEL_ENABLED, labelNode(i).getTextColor());
            Assert.assertEquals("段[" + i + "] 默认背景透明",
                    SEGBTN_DEFAULT, segBtnNode(i).getBackgroundColor());
            Assert.assertEquals("段[" + i + "] 静态圆角保留（几何不属主题）",
                    SceneChromeTokens.RADIUS_MD, segBtnNode(i).getCornerRadius());
            Assert.assertEquals("段[" + i + "] 静态内边距保留",
                    SceneChromeTokens.PAD_MD, segBtnNode(i).getPaddingTop());
        }
        for (int i = 1; i < SEGMENTS.size(); i++) {
            Assert.assertEquals("分隔符[" + i + "] = 主题次要前景色",
                    SEPARATOR_COLOR, separatorNode(i).getTextColor());
            Assert.assertEquals("分隔符[" + i + "] 文本不变", ">", separatorNode(i).getText());
        }
        Assert.assertEquals("root 背景透明", SEGBTN_DEFAULT, crumbRoot.getBackgroundColor());

        // 状态覆盖是「极淡」：三档都半透明，且 hover/pressed 与默认态可区分
        Assert.assertTrue("hover 覆盖必须半透明（极淡）", alphaOf(SEGBTN_HOVER) < 0xFF);
        Assert.assertTrue("pressed 覆盖必须半透明（极淡）", alphaOf(SEGBTN_PRESSED) < 0xFF);
        Assert.assertTrue("focus 覆盖必须半透明（极淡）", alphaOf(SEGBTN_FOCUSED) < 0xFF);
        Assert.assertNotEquals("hover 与 pressed 覆盖可区分", SEGBTN_HOVER, SEGBTN_PRESSED);
        Assert.assertNotEquals("hover 与 focus 覆盖可区分", SEGBTN_HOVER, SEGBTN_FOCUSED);
    }

    // ==================== 验收 6：段保持轻量（不装表面/滤镜） ====================

    /**
     * 面包屑是文字路径导航不是玻璃卡片：root 与每段都不装表面绑定器——
     * 无 backdrop / 无边框 / 无实体高度，PaintPlan 内 0 条 BACKDROP；
     * 段内文字也不重复采样背景。
     */
    @Test
    public void lightweightSegmentsShouldNotInstallSurfaceOrBackdrop() {
        doLayout();
        paintEngine.paint(sceneRoot);

        Assert.assertNull("root 不装滤镜", crumbRoot.getBackdrop());
        Assert.assertEquals("root 背景透明", SEGBTN_DEFAULT, crumbRoot.getBackgroundColor());
        Assert.assertEquals("root 无边框", 0, crumbRoot.getBorderWidth());
        Assert.assertEquals("root 无实体高度", -1.0F, crumbRoot.__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("root PaintPlan 无 BACKDROP", 0, backdropCount(crumbRoot));

        for (int i = 0; i < SEGMENTS.size(); i++) {
            SceneNode seg = segBtnNode(i);
            Assert.assertNull("段[" + i + "] 不装滤镜", seg.getBackdrop());
            Assert.assertEquals("段[" + i + "] 无边框", 0, seg.getBorderWidth());
            Assert.assertEquals("段[" + i + "] 无实体高度", -1.0F, seg.__getSurfaceElevation(), 0.0001F);
            Assert.assertEquals("段[" + i + "] PaintPlan 无 BACKDROP", 0, backdropCount(seg));
            Assert.assertEquals("段[" + i + "] 内文字不采样背景", 0, backdropCount(labelNode(i)));
        }
    }

    // ==================== 验收 7：禁用态可读 + 状态优先级 ====================

    /**
     * 禁用态：文字取主题禁用前景色、不透明、与宿主底代理色仍可辨；段背景保持透明；
     * 禁用优先于 hover（指针悬停也不铺覆盖）。恢复启用后文字回正文色。
     */
    @Test
    public void disabledShouldStayReadableWithDisabledForeground() {
        doLayout();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        for (int i = 0; i < SEGMENTS.size(); i++) {
            int color = labelNode(i).getTextColor();
            Assert.assertEquals("禁用段[" + i + "] 文字 = 主题禁用前景色", LABEL_DISABLED, color);
            Assert.assertEquals("禁用文字不透明（可读，不是半透明糊掉）", 0xFF, alphaOf(color));
            Assert.assertTrue("禁用文字与宿主底代理色仍可辨（WCAG 禁用档豁免，仍要求可辨）",
                    contrastRatio(color, SceneTheme.FALLBACK_BG) >= 3.0);
            Assert.assertEquals("禁用段[" + i + "] 背景保持透明",
                    SEGBTN_DEFAULT, segBtnNode(i).getBackgroundColor());
        }
        Assert.assertEquals("禁用分隔符仍取主题次要前景色",
                SEPARATOR_COLOR, separatorNode(1).getTextColor());

        // 禁用优先于 hover：指针悬停也不铺状态覆盖
        harness.moveTo(segBtnNode(1));
        doLayout();
        Assert.assertEquals("禁用态 hover 不铺覆盖",
                SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());

        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("恢复启用回主题正文色", LABEL_ENABLED, labelNode(1).getTextColor());
    }

    // ==================== 验收 8：withTheme 切换更新文字/覆盖色且不重建、不增订阅 ====================

    /**
     * 页面主题信号更新 + flush 后：路径文字、分隔符、状态覆盖色全部随新主题更新，
     * 节点身份不变、effect 数不增长；卸载后绑定全部回收。
     */
    @Test
    public void themeSwitchShouldUpdatePathTextWithoutRebuild() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅正文色必须不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("测试前提：深/浅次要前景必须不同",
                dark.mutedForeground(), light.mutedForeground());
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方必须不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));

        SceneInteractionHarness themed = SceneInteractionHarness.create();
        try {
            SceneRuntime themedRuntime = themed.getRuntime();
            int baseline = ReactiveTestProbe.registeredEffectCount();

            Signal<SceneTheme> pageTheme = Signal.create(dark);
            Signal<Boolean> themedEnabled = Signal.create(Boolean.TRUE);
            SceneBreadcrumb.Props props = new SceneBreadcrumb.Props(SEGMENTS, themedEnabled, path -> { });
            SceneNode host = new SceneNode();
            MountHandle themedHandle = themedRuntime.mount(host, () -> {
                final SceneNode[] holder = new SceneNode[1];
                SceneThemes.withTheme(pageTheme,
                        () -> holder[0] = SceneBreadcrumb.create(themedRuntime, props).get());
                return holder[0];
            });
            themedRuntime.flush();
            themed.mountRoot(host, CANVAS_WIDTH, CANVAS_HEIGHT);

            SceneNode root = themedHandle.getRoot();
            SceneNode sep1 = root.__getChildren().get(1);
            SceneNode seg1 = root.__getChildren().get(2);
            SceneNode label1 = seg1.__getChildren().get(0);

            Assert.assertEquals("初始路径文字取深色正文色", dark.foreground(), label1.getTextColor());
            Assert.assertEquals("初始分隔符取深色次要前景", dark.mutedForeground(), sep1.getTextColor());
            Assert.assertEquals("初始段背景透明", SEGBTN_DEFAULT, seg1.getBackgroundColor());

            int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
            pageTheme.set(light);
            themedRuntime.flush();

            Assert.assertSame("主题切换不重建段节点", seg1, root.__getChildren().get(2));
            Assert.assertSame("主题切换不重建文字节点", label1, seg1.__getChildren().get(0));
            Assert.assertEquals("路径文字随主题更新", light.foreground(), label1.getTextColor());
            Assert.assertEquals("分隔符随主题更新", light.mutedForeground(), sep1.getTextColor());
            Assert.assertEquals("主题切换不新增订阅",
                    effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

            // 状态覆盖色同样随主题更新（只换色，仍不装滤镜）
            SceneSurfaceStyle lightIndicator = light.surface(SceneTheme.Role.INDICATOR);
            themed.moveTo(seg1);
            Assert.assertEquals("hover 覆盖取浅色 INDICATOR hovered 档",
                    lightIndicator.getHovered().getTint(), seg1.getBackgroundColor());
            themed.press(seg1);
            Assert.assertEquals("pressed 覆盖取浅色 INDICATOR pressed 档",
                    lightIndicator.getPressed().getTint(), seg1.getBackgroundColor());
            Assert.assertNull("主题切换后段仍不装滤镜", seg1.getBackdrop());

            themedHandle.dispose();
            Assert.assertEquals("卸载后回收该实例全部绑定",
                    baseline, ReactiveTestProbe.registeredEffectCount());
        } finally {
            themed.dispose();
        }
    }

    // ==================== 验收 9：卸载回收绑定（effect 探针） ====================

    /**
     * 挂载注册响应式绑定、卸载全部回收：{@code ReactiveTestProbe.registeredEffectCount()}
     * 回到挂载前基线（守「卸载即回收」纪律）。
     */
    @Test
    public void disposeShouldReclaimAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();

        SceneBreadcrumb.Props props = new SceneBreadcrumb.Props(SEGMENTS, enabledSignal, path -> { });
        MountHandle extra = runtime.mount(sceneRoot, SceneBreadcrumb.create(runtime, props));
        runtime.flush();

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted,
                mounted > baseline);

        extra.dispose();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }
}
