package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneToast;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneToast 独立单元测试：show 挂载、多 toast 堆叠、帧时间驱动「展示 → 退场 → 移除」、
 * 内容宽度收缩与水平居中、底部堆叠、出现/退场动画、类型化入口、非模态指针穿透；
 * 以及 G10 主题化：默认卡片 = OVERLAY 配方、每条消息的来源主题语义、来源主题更新、回收。
 *
 * <p>overlay 布局在测试内手动执行（无管线）；动画与到期均由 runtime.__tickFrame 驱动。</p>
 */
public class SceneToastTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneInteractionHarness harness;

    private List<String> mainTreeHitLog;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final long ENTER = SceneToast.ENTER_DURATION_NANOS;
    private static final long LEAVE = SceneToast.LEAVE_DURATION_NANOS;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        mainTreeHitLog = new ArrayList<>();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private int overlaySize() {
        return runtime.getOverlayHost().size();
    }

    /** toast 堆叠容器（overlay root）。 */
    private SceneNode toastContainer() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    /** 当前第 index 条 toast 节点（container 子节点按投递序）。 */
    private SceneNode toastAt(int index) {
        return toastContainer().__getChildren().get(index);
    }

    /** 当前第一条 toast 节点（container 子 0）。 */
    private SceneNode firstToast() {
        return toastAt(0);
    }

    /** 第 index 条 toast 的文本节点（row 子 1：色点 0、文本 1）。 */
    private SceneNode labelOf(SceneNode toast) {
        return toast.__getChildren().get(1);
    }

    /** 第 index 条 toast 的类型色点（row 子 0）。 */
    private SceneNode dotOf(SceneNode toast) {
        return toast.__getChildren().get(0);
    }

    private void tickAndFlush(long nanos) {
        runtime.__tickFrame(nanos);
        runtime.flush();
    }

    private void pressAt(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** 绘制浮层整棵子树，让每个节点的自身 PaintFragment 就位。 */
    private void paintOverlay() {
        paintEngine.paint(toastContainer());
    }

    /** 节点自身 PaintFragment 的命令流；未绘制出 fragment 时断言失败。 */
    private static List<PaintCommand> ownCommands(SceneNode node) {
        Object cached = node.getCachedPaint();
        Assert.assertTrue("节点应已绘制出自身 fragment", cached instanceof PaintFragment);
        return ((PaintFragment) cached).getCommands();
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数（不含后代）。 */
    private static int ownBackdropCount(SceneNode node) {
        int count = 0;
        for (PaintCommand command : ownCommands(node)) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfType(List<PaintCommand> commands, PaintCommandType type) {
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** 库默认主题的 OVERLAY 角色配方：通知卡片默认外观唯一来源。 */
    private static SceneSurfaceStyle overlaySurface() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    }

    // ==================== 挂载、堆叠与到期 ====================

    @Test
    public void showMountsOverlayAndExpiresAfterLeaveAnimation() {
        SceneToast.show(runtime, "保存成功", 2_000_000_000L);
        runtime.flush();
        doLayout();
        Assert.assertEquals("toast overlay 挂载", 1, overlaySize());

        tickAndFlush(1_000_000_000L); // 1s < 2s
        Assert.assertEquals("未到期保持", 1, overlaySize());

        tickAndFlush(2_000_000_000L); // 2s ≥ 2s → 进入退场淡出，尚未移除
        Assert.assertEquals("到期先退场", 1, overlaySize());

        tickAndFlush(2_000_000_000L + LEAVE); // 退场完成 → 移除
        Assert.assertEquals("退场后移除", 0, overlaySize());
    }

    @Test
    public void multipleToastsStackAndExpireIndependently() {
        // t=0：先 1s 后 5s 两条（帧时间差 500ms）
        SceneToast.show(runtime, "先", 1_000_000_000L);
        runtime.flush();
        tickAndFlush(500_000_000L);
        SceneToast.show(runtime, "后", 5_000_000_000L);
        runtime.flush();
        doLayout();
        Assert.assertEquals("两条堆叠", 1, overlaySize());
        SceneNode container = toastContainer();
        Assert.assertEquals("堆叠 2 toast 节点", 2, container.__getChildren().size());

        tickAndFlush(1_000_000_000L); // t=1s：先 1-0=1 ≥ 1 → 退场；后 1-0.5=0.5 < 5 留
        Assert.assertEquals("第一条进入退场", 1, overlaySize());
        Assert.assertEquals("退场中仍占位", 2, container.__getChildren().size());

        tickAndFlush(1_000_000_000L + LEAVE); // 先 退场完成 → 移除
        Assert.assertEquals("第一条移除", 1, overlaySize());
        Assert.assertEquals("剩 1 条", 1, container.__getChildren().size());

        tickAndFlush(6_000_000_000L); // t=6s：后 6-0.5=5.5 ≥ 5 → 退场
        Assert.assertEquals("第二条进入退场", 1, overlaySize());

        tickAndFlush(6_000_000_000L + LEAVE); // 退场完成 → 移除
        Assert.assertEquals("全部移除", 0, overlaySize());
    }

    @Test
    public void toastDoesNotBlockMainTreePointer() {
        // 主树命中探针
        SceneNode probe = new SceneNode();
        probe.setPreferredWidth(30);
        probe.setPreferredHeight(30);
        sceneRoot.appendChild(probe);
        runtime.on(probe, SceneEventType.POINTER_DOWN, (ev, ctx) -> mainTreeHitLog.add("hit"));

        SceneToast.show(runtime, "通知", 5_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        // toast 在底部；点击顶部区域：overlay 整树不可命中 → 主树探针命中
        pressAt(10, 10);
        Assert.assertEquals("toast 非模态穿透", 1, mainTreeHitLog.size());
    }

    // ==================== 布局：收缩、居中、底部 ====================

    @Test
    public void toastShrinksToContentAndCentersHorizontallyAtBottom() {
        SceneToast.show(runtime, "通知", 5_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER); // 完成出现动画
        doLayout();

        SceneNode toast = firstToast();
        LayoutBox box = (LayoutBox) toast.getCachedLayout();
        Assert.assertNotNull("toast 已布局", box);
        Assert.assertTrue("宽度收缩不再占满全宽", box.getWidth() < CANVAS_WIDTH);
        Assert.assertEquals("水平居中（左距=右距）",
                box.getX(), CANVAS_WIDTH - box.getX() - box.getWidth());
        Assert.assertEquals("底部堆叠（贴窗口下沿）",
                CANVAS_HEIGHT, box.getY() + box.getHeight());
    }

    // ==================== 动画 ====================

    @Test
    public void enterAnimationFadesInAndSlidesUp() {
        SceneToast.show(runtime, "动画", 5_000_000_000L);
        runtime.flush();
        doLayout();
        SceneNode toast = firstToast();
        Assert.assertEquals("挂载首帧透明", 0f, toast.getOpacity(), 0.001f);
        Assert.assertEquals("初始位移 8px", 8, toast.__getPresentationOffsetY());

        tickAndFlush(ENTER / 2);
        Assert.assertEquals("半程半透明", 0.5f, toast.getOpacity(), 0.01f);
        Assert.assertEquals("半程位移减半", 4, toast.__getPresentationOffsetY());

        tickAndFlush(ENTER);
        Assert.assertEquals("完成完全可见", 1f, toast.getOpacity(), 0.001f);
        Assert.assertEquals("完成位移归零", 0, toast.__getPresentationOffsetY());
    }

    @Test
    public void leaveAnimationFadesOutBeforeRemoval() {
        SceneToast.show(runtime, "退场", 1_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER); // 完成出现动画
        doLayout();
        SceneNode toast = firstToast();
        Assert.assertEquals("到期前完全可见", 1f, toast.getOpacity(), 0.001f);

        tickAndFlush(1_000_000_000L); // 到期 → 进入退场（leavingAt=1s）
        Assert.assertEquals("退场起点仍可见", 1f, toast.getOpacity(), 0.001f);
        Assert.assertEquals("退场中未移除", 1, overlaySize());

        tickAndFlush(1_000_000_000L + LEAVE / 2);
        Assert.assertEquals("半程半透明", 0.5f, toast.getOpacity(), 0.01f);
        Assert.assertEquals("半程仍在", 1, overlaySize());

        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成移除", 0, overlaySize());
    }

    // ==================== 类型化（语义色取来源主题） ====================

    @Test
    public void typedToastsRenderTypeDot() {
        // 两次投递之间 flush：Signal 为 pending-write 语义，未 flush 时读到旧列表会覆盖前一条
        SceneToast.showSuccess(runtime, "成功");
        runtime.flush();
        SceneToast.showError(runtime, "错误");
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        SceneNode container = toastContainer();
        Assert.assertEquals("两条堆叠", 2, container.__getChildren().size());
        SceneNode successDot = dotOf(toastAt(0));
        SceneNode errorDot = dotOf(toastAt(1));
        Assert.assertEquals("SUCCESS 色点 = 主题强调色",
                SceneThemes.DEFAULT.accent(), successDot.getBackgroundColor());
        Assert.assertEquals("ERROR 色点 = 主题错误文本色",
                SceneThemes.DEFAULT.errorText(), errorDot.getBackgroundColor());
    }

    @Test
    public void showDefaultsToInfoType() {
        SceneToast.show(runtime, "普通");
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        SceneNode dot = dotOf(firstToast());
        Assert.assertEquals("默认 INFO 色点 = 主题次要前景", SceneThemes.DEFAULT.mutedForeground(),
                dot.getBackgroundColor());
    }

    @Test
    public void warningAndErrorDotsTakeSemanticThemeColors() {
        SceneToast.showWarning(runtime, "警告");
        runtime.flush();
        SceneToast.showError(runtime, "错误");
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        Assert.assertEquals("WARNING 色点 = 主题警告文本色",
                SceneThemes.DEFAULT.warningText(), dotOf(toastAt(0)).getBackgroundColor());
        Assert.assertEquals("ERROR 色点 = 主题错误文本色",
                SceneThemes.DEFAULT.errorText(), dotOf(toastAt(1)).getBackgroundColor());
    }

    // ==================== 主题化：默认卡片 = OVERLAY 配方 ====================

    /**
     * 默认工厂路径：通知卡片 = OVERLAY 配方（染色/缘色/边框宽/圆角/实体高度/滤镜全来自配方），
     * 自身恰好一条 BACKDROP 且滤镜之上是半透明 tint（无不透明底盖）；文字取主题正文前景、
     * 色点取语义主题色；堆叠容器不装表面，内容节点不各自采样玻璃。
     */
    @Test
    public void defaultCardUsesOverlayRecipeWithSingleBackdrop() {
        SceneToast.show(runtime, "通知", 5_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();

        SceneNode container = toastContainer();
        SceneNode card = firstToast();
        SceneSurfaceStyle overlay = overlaySurface();

        Assert.assertEquals("卡片背景 = OVERLAY 配方 idle 染色",
                overlay.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡片缘色 = OVERLAY 配方 idle 缘色",
                overlay.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡片边框宽 = OVERLAY 配方", overlay.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("卡片圆角 = OVERLAY 配方", overlay.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡片实体高度 = OVERLAY 配方", overlay.getIdle().getElevation(),
                card.__getSurfaceElevation(), 0.0001f);
        Assert.assertNotNull("卡片应写入 OVERLAY 配方滤镜", card.getBackdrop());
        Assert.assertEquals("卡片材质 = OVERLAY 配方", overlay.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("卡片模糊半径 = OVERLAY 配方", overlay.getBackdrop().getBlurRadius(),
                card.getBackdrop().getBlurRadius());

        Assert.assertEquals("文字 = 主题正文前景", SceneThemes.DEFAULT.foreground(),
                labelOf(card).getTextColor());
        Assert.assertEquals("INFO 色点 = 主题次要前景", SceneThemes.DEFAULT.mutedForeground(),
                dotOf(card).getBackgroundColor());

        paintOverlay();
        Assert.assertEquals("卡片自身恰好一条 BACKDROP", 1, ownBackdropCount(card));
        Assert.assertEquals("堆叠容器自身零 BACKDROP（不给整层装玻璃）", 0, ownBackdropCount(container));
        Assert.assertEquals("色点零 BACKDROP（内容不各自采样玻璃）", 0, ownBackdropCount(dotOf(card)));
        Assert.assertEquals("文本零 BACKDROP（内容不各自采样玻璃）", 0, ownBackdropCount(labelOf(card)));
        List<PaintCommand> commands = ownCommands(card);
        int backdropIndex = indexOfType(commands, PaintCommandType.BACKDROP);
        int backgroundIndex = indexOfType(commands, PaintCommandType.BACKGROUND);
        Assert.assertTrue("BACKDROP 必须先于 BACKGROUND",
                backdropIndex >= 0 && backgroundIndex > backdropIndex);
        Assert.assertTrue("滤镜之上不得压不透明底盖",
                alpha(commands.get(backgroundIndex).getColor()) < 0xFF);
    }

    // ==================== 主题化：每条消息的来源主题 ====================

    /**
     * 来源主题在 show 入口捕获并随消息保存：在 {@code withTheme(A)} 作用域内 show 之后，
     * {@code install(rt, B)} 换掉 runtime 默认主题，再让卡片延后构建——该条消息仍用 A，
     * 新消息用 B。若在卡片构建期才解析，第一条会跟着 runtime 默认变成 B。
     */
    @Test
    public void messageKeepsSourceThemeCapturedAtShowTime() {
        SceneTheme sourceA = SceneTheme.liquidGlassDark();
        SceneTheme runtimeDefaultB = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：两档 OVERLAY 配方必须不同",
                sourceA.surface(SceneTheme.Role.OVERLAY), runtimeDefaultB.surface(SceneTheme.Role.OVERLAY));
        Assert.assertNotEquals("测试前提：两档正文前景必须不同",
                Integer.valueOf(sourceA.foreground()), Integer.valueOf(runtimeDefaultB.foreground()));

        Signal<SceneTheme> pageTheme = Signal.create(sourceA);
        runtime.mount(sceneRoot, () -> {
            // show 发生在 withTheme 作用域内：来源主题 = pageTheme 信号（不是 runtime 默认）
            SceneThemes.withTheme(pageTheme, () -> SceneToast.show(runtime, "A 消息", 5_000_000_000L));
            return new SceneNode();
        });
        // 故意先换 runtime 默认主题，再 flush 让浮层与卡片构建（延迟构建路径）
        SceneThemes.install(runtime, Signal.create(runtimeDefaultB));
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();

        SceneNode container = toastContainer();
        SceneNode first = toastAt(0);
        Assert.assertEquals("来源主题消息的卡片 = A 的 OVERLAY 染色",
                sourceA.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), first.getBackgroundColor());
        Assert.assertEquals("来源主题消息的缘色 = A 的 OVERLAY 缘色",
                sourceA.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), first.getBorderColor());
        Assert.assertEquals("来源主题消息的文字 = A 的正文前景",
                sourceA.foreground(), labelOf(first).getTextColor());
        Assert.assertEquals("来源主题消息的色点 = A 的次要前景",
                sourceA.mutedForeground(), dotOf(first).getBackgroundColor());

        // 作用域外投递的新消息：取 install 后的 runtime 默认 B
        SceneToast.show(runtime, "B 消息", 5_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        Assert.assertEquals("两条堆叠", 2, container.__getChildren().size());
        SceneNode second = toastAt(1);
        Assert.assertEquals("新消息卡片 = B 的 OVERLAY 染色",
                runtimeDefaultB.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), second.getBackgroundColor());
        Assert.assertEquals("新消息文字 = B 的正文前景",
                runtimeDefaultB.foreground(), labelOf(second).getTextColor());
        Assert.assertEquals("install 不改已投递消息：第一条仍用 A",
                sourceA.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), first.getBackgroundColor());
        Assert.assertEquals("install 不改已投递消息：第一条文字仍用 A",
                sourceA.foreground(), labelOf(first).getTextColor());
    }

    /**
     * 捕获的是主题信号而非颜色快照：来源主题信号更新时，已显示的该条消息跟着刷新，
     * 节点身份不变、订阅不增长（规划「打开后仍响应来源主题更新」）。
     */
    @Test
    public void sourceThemeSignalUpdateRefreshesMessageCard() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> SceneToast.show(runtime, "消息", 5_000_000_000L));
            return new SceneNode();
        });
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();

        SceneNode card = firstToast();
        Assert.assertEquals("初始卡片 = 深色 OVERLAY 染色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), card.getBackgroundColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();

        Assert.assertSame("切来源主题不重建卡片", card, firstToast());
        Assert.assertEquals("卡片随来源主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡片缘色随来源主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("文字随来源主题更新", light.foreground(), labelOf(card).getTextColor());
        Assert.assertEquals("色点随来源主题更新", light.mutedForeground(), dotOf(card).getBackgroundColor());
        Assert.assertEquals("切来源主题不新增订阅", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 混合来源主题的排队与过期语义不变：先到先到期、退场中占位、逐条独立移除；
     * 剩余消息始终保持自己的来源主题（不因前一条移除而串色）。
     */
    @Test
    public void mixedSourceThemeQueueKeepsOrderAndExpiresIndependently() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> SceneToast.show(runtime, "A", 1_000_000_000L));
            return new SceneNode();
        });
        runtime.flush();
        // 换掉 runtime 默认后再投递第二条（作用域外 → 取 B）
        SceneThemes.install(runtime, Signal.create(light));
        SceneToast.show(runtime, "B", 5_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        SceneNode container = toastContainer();
        Assert.assertEquals("两条堆叠", 2, container.__getChildren().size());

        tickAndFlush(1_000_000_000L); // A 到期 → 退场；B 未到期
        Assert.assertEquals("A 退场中仍占位", 2, container.__getChildren().size());

        tickAndFlush(1_000_000_000L + LEAVE); // A 退场完成 → 移除
        Assert.assertEquals("A 移除后剩 1 条", 1, container.__getChildren().size());
        Assert.assertEquals("剩余 B 保持自己的来源主题",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                container.__getChildren().get(0).getBackgroundColor());
        Assert.assertEquals("剩余 B 文字保持自己的来源主题",
                light.foreground(), labelOf(container.__getChildren().get(0)).getTextColor());

        tickAndFlush(6_000_000_000L); // B 到期 → 退场
        Assert.assertEquals("B 退场中仍占位", 1, overlaySize());

        tickAndFlush(6_000_000_000L + LEAVE);
        Assert.assertEquals("B 退场完成 → 全部移除", 0, overlaySize());
    }

    // ==================== 主题化：卸载回收 ====================

    /**
     * 卸载回收：单条消息移除后，该条消息的响应式绑定（表面/前景/语义色/来源主题作用域）全部回收；
     * 两轮之间只有 Host 级绑定（portal + 帧时间）留存，没有逐条泄漏；runtime 销毁回收 Host 级绑定。
     */
    @Test
    public void removalReclaimsPerMessageBindingsAndRuntimeDisposeReturnsToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        SceneToast.show(runtime, "第一轮", 1_000_000_000L);
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        Assert.assertEquals("首条已挂载", 1, overlaySize());
        int shown = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("展示期应注册响应式绑定", shown > baseline);

        tickAndFlush(1_000_000_000L); // 到期 → 进入退场
        Assert.assertEquals("到期先退场", 1, overlaySize());
        long firstGoneAt = 1_000_000_000L + LEAVE;
        tickAndFlush(firstGoneAt); // 退场完成 → 移除
        Assert.assertEquals("退场完成移除", 0, overlaySize());
        int hostOnly = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("移除后回收该条消息的绑定", hostOnly < shown);

        // 第二轮：Host 级绑定留存，新增的只有这一条消息的绑定（帧时间必须继续单调推进）
        SceneToast.show(runtime, "第二轮", 1_000_000_000L);
        runtime.flush();
        tickAndFlush(firstGoneAt + ENTER);
        doLayout();
        int secondShown = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("第二轮重新注册消息绑定", secondShown > hostOnly);
        long secondExpiryAt = firstGoneAt + 1_000_000_000L;
        tickAndFlush(secondExpiryAt); // 到期 → 进入退场
        Assert.assertEquals("第二轮到期先退场", 1, overlaySize());
        tickAndFlush(secondExpiryAt + LEAVE); // 退场完成 → 移除
        Assert.assertEquals("第二轮移除后回到 Host 基线（无逐条泄漏）", hostOnly,
                ReactiveTestProbe.registeredEffectCount());

        runtime.dispose();
        Assert.assertTrue("runtime 销毁回收 Host 级绑定",
                ReactiveTestProbe.registeredEffectCount() < hostOnly);
    }

    // ==================== 内聚化：不得再自带调色板 ====================

    /**
     * 源码守卫：通知卡片必须把表面、前景与每条消息的来源主题委托给主题权威。
     *
     * <p>本类历史上自带 {@code TOAST_BG}/{@code TEXT_PRIMARY} 与一份 {@code TYPE_COLOR_*} 色板，
     * 与主题形成两个外观写入者；来源主题也曾因 Host 挂 root owner 而在延迟构建时丢成 runtime 默认。
     * 结构用例只证明「现在能用」，证明不了「没退回手搓」——这条按签名粒度钉住委托关系与唯一写入者。</p>
     */
    @Test
    public void toastMustDelegateCardAppearanceToSurfaceBinderAndKeepPerMessageSourceTheme() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/SceneToast.java");
        String raw = new String(java.nio.file.Files.readAllBytes(path),
                java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            code.append(t).append('\n');
        }
        String src = code.toString();
        Assert.assertTrue("卡片外壳必须走表面绑定器（唯一外观写入者）",
                src.contains("SceneSurfaceBinder.bind("));
        Assert.assertTrue("卡片必须取 OVERLAY 角色配方", src.contains("SceneTheme.Role.OVERLAY"));
        Assert.assertTrue("文字必须取主题正文前景", src.contains("SceneThemes.foreground("));
        Assert.assertTrue("每条消息必须捕获来源主题（show 入口 resolve）",
                src.contains("SceneThemes.resolve("));
        Assert.assertTrue("卡片构建必须回到该条消息的来源主题作用域",
                src.contains("SceneThemes.withTheme("));
        Assert.assertFalse("旧静态底色写入者必须删除", src.contains("TOAST_BG"));
        Assert.assertFalse("本类不得再自带 0xFF 色值（调色板归主题）", src.contains("0xFF"));
        Assert.assertFalse("不得再直接取 SceneChromeTokens 颜色", src.contains("SceneChromeTokens"));
    }
}
