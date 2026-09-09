package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
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
 * SceneDialog 独立单元测试：遮罩模态拦截、卡片窗口中心对齐与全屏遮罩、按钮点击/关闭语义、
 * ESC 关闭、打开焦点落首按钮、Tab 环限定在对话框内、Enter 激活焦点按钮、
 * 出现/退场动画（受控 visible 桥接延迟卸载）、alert/confirm 命令式 API。
 *
 * <p>overlay 布局在测试内手动执行（无管线，与 SceneContextMenuTest 同款假设）；
 * 动画由 runtime.__tickFrame 驱动。</p>
 */
public class SceneDialogTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneInteractionHarness harness;

    private Signal<Boolean> visible;
    private AtomicBoolean dismissed;
    private List<String> log;
    private ScenePortalHandle handle;

    private static final int CANVAS_WIDTH = 480;
    private static final int CANVAS_HEIGHT = 240;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final long ENTER = SceneDialog.ENTER_DURATION_NANOS;
    private static final long LEAVE = SceneDialog.LEAVE_DURATION_NANOS;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        visible = Signal.create(Boolean.TRUE);
        dismissed = new AtomicBoolean(false);
        log = new ArrayList<>();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 受控打开并等待出现动画完成（默认）。 */
    private void openDialog(List<SceneDialog.Button> buttons) {
        openDialog(buttons, true);
    }

    /** 受控打开；finishEnter=false 时停在挂载首帧（出现动画起点）。 */
    private void openDialog(List<SceneDialog.Button> buttons, boolean finishEnter) {
        SceneDialog.Props props = new SceneDialog.Props(visible, "确认操作", "确定继续吗？", buttons,
                () -> {
                    dismissed.set(true);
                    visible.set(Boolean.FALSE);
                });
        handle = SceneDialog.create(runtime, props);
        runtime.flush();
        if (finishEnter) {
            tickAndFlush(1_000_000_000L); // 1s ≥ 160ms 出现动画完成
            doLayout();
        }
    }

    private void tickAndFlush(long nanos) {
        runtime.__tickFrame(nanos);
        runtime.flush();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    /** 遮罩 → 卡片。 */
    private SceneNode cardNode() {
        return overlayRoot().__getChildren().get(0);
    }

    /** 卡片 → 第 index 个按钮（卡片子节点：标题 0、正文 1、按钮行 2）。 */
    private SceneNode buttonNode(int index) {
        SceneNode buttonRow = cardNode().__getChildren().get(2);
        return buttonRow.__getChildren().get(index);
    }

    private int overlaySize() {
        return runtime.getOverlayHost().size();
    }

    private int[] absCenter(SceneNode node) {
        LayoutBox b = (LayoutBox) node.getCachedLayout();
        int ax = b.getX();
        int ay = b.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox pb = (LayoutBox) parent.getCachedLayout();
            if (pb != null) {
                ax += pb.getX();
                ay += pb.getY();
            }
            parent = parent.__getParent();
        }
        return new int[] {ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    /** 单发指针事件（MOVE/DOWN/UP 分开路由，用于观察 hover 与按下中间态）。 */
    private void routePointer(ScenePointerAction action, int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(action, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private void pressAndReleaseAt(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1001L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private void routeKeyAndFlush(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** 构造对话框输入契约：onDismiss 记 dismissed 并把可见性写回调用方信号。 */
    private SceneDialog.Props dialogProps(Signal<Boolean> visibleSignal,
                                          List<SceneDialog.Button> buttons) {
        return new SceneDialog.Props(visibleSignal, "确认操作", "确定继续吗？", buttons, () -> {
            dismissed.set(true);
            visibleSignal.set(Boolean.FALSE);
        });
    }

    /** 双按钮（取消 + 主操作确定），回调写入 log。 */
    private List<SceneDialog.Button> twoButtons() {
        return Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm")));
    }

    /**
     * 在可切换的局部主题作用域内创建受控对话框（{@link SceneThemes#withTheme}）：
     * portal 内容构建期在来源作用域的子作用域内执行，因此继承该主题。
     *
     * @param theme 页面主题信号
     * @param props 对话框契约
     * @return 承载对话框的页面挂载句柄（dispose 时一并回收 portal 与绑定）
     */
    private MountHandle mountThemedDialog(Signal<SceneTheme> theme, SceneDialog.Props props) {
        MountHandle page = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(theme, () -> handle = SceneDialog.create(runtime, props));
            return new SceneNode();
        });
        runtime.flush();
        return page;
    }

    /** 绘制当前浮层整棵子树，让每个节点的自身 PaintFragment 就位。 */
    private void paintOverlay() {
        paintEngine.paint(overlayRoot());
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

    // ==================== 挂载与结构 ====================

    @Test
    public void visibleTrueMountsScrimCardAndButtons() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        Assert.assertEquals("对话框 overlay 挂载", 1, overlaySize());
        Assert.assertEquals("卡片 3 子节点（标题/正文/按钮行）", 3, cardNode().__getChildren().size());
        Assert.assertEquals("按钮行 2 按钮", 2, cardNode().__getChildren().get(2).__getChildren().size());
        Assert.assertEquals("打开聚焦首按钮", true, runtime.interactionState(buttonNode(0)).focused().get());
    }

    @Test
    public void visibleFalseUnmountsOverlayAfterLeaveAnimation() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        visible.set(Boolean.FALSE);
        runtime.flush();
        Assert.assertEquals("退场动画期间保持挂载", 1, overlaySize());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    // ==================== 布局：全屏遮罩与窗口中心 ====================

    @Test
    public void scrimFillsWholeWindow() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        LayoutBox scrimBox = (LayoutBox) overlayRoot().getCachedLayout();
        Assert.assertNotNull("遮罩已布局", scrimBox);
        Assert.assertEquals("遮罩宽=窗口宽", CANVAS_WIDTH, scrimBox.getWidth());
        Assert.assertEquals("遮罩高=窗口高", CANVAS_HEIGHT, scrimBox.getHeight());
    }

    @Test
    public void cardCentersInWindow() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        int[] center = absCenter(cardNode());
        Assert.assertEquals("卡片水平居中", CANVAS_WIDTH / 2, center[0]);
        Assert.assertEquals("卡片垂直居中", CANVAS_HEIGHT / 2, center[1]);
    }

    // ==================== 动画 ====================

    @Test
    public void enterAnimationFadesInAndSlidesUp() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)), false);
        SceneNode scrim = overlayRoot();
        SceneNode card = cardNode();
        Assert.assertEquals("挂载首帧遮罩透明", 0f, scrim.getOpacity(), 0.001f);
        Assert.assertEquals("挂载首帧卡片透明", 0f, card.getOpacity(), 0.001f);
        Assert.assertEquals("卡片初始位移 8px", 8, card.__getPresentationOffsetY());

        tickAndFlush(ENTER / 2);
        Assert.assertEquals("半程遮罩半透明", 0.5f, scrim.getOpacity(), 0.01f);
        Assert.assertEquals("半程卡片半透明", 0.5f, card.getOpacity(), 0.01f);
        Assert.assertEquals("半程位移减半", 4, card.__getPresentationOffsetY());

        tickAndFlush(ENTER);
        Assert.assertEquals("完成遮罩可见", 1f, scrim.getOpacity(), 0.001f);
        Assert.assertEquals("完成卡片可见", 1f, card.getOpacity(), 0.001f);
        Assert.assertEquals("完成位移归零", 0, card.__getPresentationOffsetY());
    }

    @Test
    public void leaveAnimationFadesOutThenUnmounts() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        Assert.assertEquals("退场前可见", 1f, overlayRoot().getOpacity(), 0.001f);

        visible.set(Boolean.FALSE); // t=1s 进入退场
        runtime.flush();
        Assert.assertEquals("退场中保持挂载", 1, overlaySize());

        tickAndFlush(1_000_000_000L + LEAVE / 2);
        Assert.assertEquals("半程遮罩半透明", 0.5f, overlayRoot().getOpacity(), 0.01f);
        Assert.assertEquals("半程仍挂载", 1, overlaySize());

        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    // ==================== 按钮与关闭语义 ====================

    @Test
    public void buttonClickRunsOnClickAndDismisses() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        int[] c = absCenter(buttonNode(1));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("onClick 执行", Arrays.asList("confirm"), log);
        Assert.assertTrue("点击后请求关闭", dismissed.get());
        Assert.assertEquals("退场动画期间保持挂载", 1, overlaySize());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    @Test
    public void nonClosingButtonRunsOnClickOnly() {
        openDialog(Arrays.asList(
                new SceneDialog.Button("帮助", SceneDialog.ButtonKind.NORMAL, false, () -> log.add("help")),
                SceneDialog.Button.of("关闭", () -> log.add("close"))));
        int[] c = absCenter(buttonNode(0));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("onClick 执行", Arrays.asList("help"), log);
        Assert.assertFalse("closesDialog=false 不关闭", dismissed.get());
        Assert.assertEquals("overlay 保持", 1, overlaySize());
    }

    @Test
    public void escapeRequestsDismiss() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        routeKeyAndFlush(SceneKey.ESCAPE);
        Assert.assertTrue("ESC 请求关闭", dismissed.get());
        Assert.assertEquals("退场动画期间保持挂载", 1, overlaySize());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    // ==================== 模态与焦点陷阱 ====================

    @Test
    public void scrimBlocksPointerToMainTree() {
        // 主树命中探针：覆盖 (10,10) 的 hitTestable 节点
        SceneNode probe = new SceneNode();
        probe.setPreferredWidth(30);
        probe.setPreferredHeight(30);
        sceneRoot.appendChild(probe);
        runtime.on(probe, SceneEventType.POINTER_DOWN, (ev, ctx) -> log.add("main-hit"));
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));

        // 点击遮罩区域（卡片外）：应被遮罩拦截，主树探针不命中
        pressAndReleaseAt(10, 10);
        Assert.assertEquals("遮罩拦截主树点击", 0, log.size());
        Assert.assertFalse("遮罩点击不触发关闭", dismissed.get());
    }

    @Test
    public void tabCycleStaysInsideDialog() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", null),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, null)));
        // Tab 从首按钮移到次按钮（Tab 环限定在 active overlay 内）
        routeKeyAndFlush(SceneKey.TAB);
        Assert.assertEquals("Tab 后焦点在次按钮", true, runtime.interactionState(buttonNode(1)).focused().get());
        // 再 Tab 循环回首按钮
        routeKeyAndFlush(SceneKey.TAB);
        Assert.assertEquals("Tab 循环回首按钮", true, runtime.interactionState(buttonNode(0)).focused().get());
    }

    @Test
    public void enterActivatesFocusedButton() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        // 焦点在首按钮（取消）；Enter 激活它
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活焦点按钮", Arrays.asList("cancel"), log);
        Assert.assertTrue("激活后关闭", dismissed.get());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    // ==================== alert / confirm 命令式 API ====================

    @Test
    public void alertOpensCentersAndClosesOnOk() {
        handle = SceneDialog.alert(runtime, "操作完成", "数据已保存", () -> log.add("ok"));
        runtime.flush();
        tickAndFlush(1_000_000_000L);
        doLayout();
        Assert.assertEquals("alert 挂载", 1, overlaySize());
        int[] center = absCenter(cardNode());
        Assert.assertEquals("alert 卡片水平居中", CANVAS_WIDTH / 2, center[0]);
        Assert.assertEquals("alert 卡片垂直居中", CANVAS_HEIGHT / 2, center[1]);
        Assert.assertEquals("单按钮", 1, cardNode().__getChildren().get(2).__getChildren().size());
        Assert.assertEquals("打开聚焦确定按钮", true, runtime.interactionState(buttonNode(0)).focused().get());

        int[] c = absCenter(buttonNode(0));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("确定回调执行", Arrays.asList("ok"), log);
        Assert.assertEquals("退场动画期间保持挂载", 1, overlaySize());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    @Test
    public void confirmOpensWithCancelAndOk() {
        handle = SceneDialog.confirm(runtime, "删除确认", "不可恢复", () -> log.add("ok"), () -> log.add("cancel"));
        runtime.flush();
        tickAndFlush(1_000_000_000L);
        doLayout();
        Assert.assertEquals("confirm 挂载", 1, overlaySize());
        Assert.assertEquals("双按钮", 2, cardNode().__getChildren().get(2).__getChildren().size());

        int[] c = absCenter(buttonNode(0)); // 取消
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("取消回调执行", Arrays.asList("cancel"), log);
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("取消后卸载", 0, overlaySize());
    }

    @Test
    public void confirmEscCloses() {
        handle = SceneDialog.confirm(runtime, "删除确认", "不可恢复", () -> log.add("ok"), () -> log.add("cancel"));
        runtime.flush();
        tickAndFlush(1_000_000_000L);
        doLayout();
        routeKeyAndFlush(SceneKey.ESCAPE);
        Assert.assertEquals("ESC 不触发按钮回调", 0, log.size());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("ESC 后卸载", 0, overlaySize());
    }

    // ==================== 内容换行：盒宽与换行宽同源 ====================

    /**
     * 锁：标题/正文的换行宽必须等于「卡片实测内容宽」。
     *
     * <p>真机缺陷：卡片固定 320px 且开了 clipChildren，文本节点却从未设换行宽，
     * 长 URL 以单行 intrinsic 宽度撑出盒子后被静默裁切（弹窗显示内容不全，且无任何报错）。
     * 内容宽从<b>布局盒与节点自身 padding/border</b>实测，不拿同一组常量重算 ——
     * 后者是恒等式，证不出东西；只有实测才能在盒宽与换行宽再次分叉时变红。</p>
     */
    @Test
    public void titleAndMessageWrapWidthTracksMeasuredCardContentWidth() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        SceneNode card = cardNode();
        LayoutBox cardBox = (LayoutBox) card.getCachedLayout();
        Assert.assertNotNull("卡片已布局", cardBox);
        int contentWidth = cardBox.getWidth() - card.getPaddingLeft() - card.getPaddingRight()
                - 2 * card.getBorderWidth();
        Assert.assertTrue("卡片内容宽为正数", contentWidth > 0);

        SceneNode title = card.__getChildren().get(0);
        SceneNode message = card.__getChildren().get(1);
        Assert.assertEquals("标题换行宽=实测内容宽", contentWidth, title.getMaxTextWidth());
        Assert.assertEquals("正文换行宽=实测内容宽", contentWidth, message.getMaxTextWidth());
    }

    /**
     * 锁：换行宽必须是「盒宽减去内边距与边框」的正数，不得回退成 0（0=不换行）。
     *
     * <p>{@code maxTextWidth<=0} 在布局层语义是「不折行」，正是缺陷 1 的成因；
     * 本用例把这条边界钉死，防止有人把换行宽重新乘回某个错误的除数。</p>
     */
    @Test
    public void messageWrapWidthMustBePositiveAndNarrowerThanCard() {
        int wrap = SceneDialog.messageWrapWidthPx();
        Assert.assertTrue("换行宽为正(0 等于关掉换行)", wrap > 0);
        Assert.assertTrue("换行宽必须窄于卡片，给内边距与边框留位置", wrap < 320);
    }

    // ==================== 按钮交互反馈（hover / pressed / cursor） ====================

    /**
     * 真指针驱动的 hover/pressed/cursor：主操作按钮必须有可感知反馈。
     *
     * <p>真机缺陷：对话框按钮只在创建时设过一次静态底色，既不响应悬停也不响应按下，
     * 光标也不切手型（用户反馈「窗口没有 hover 等响应」）。四态派生归共享控件后，
     * 这里用真实指针事件流（MOVE→DOWN→UP→移开）逐档验证外观确实变了。</p>
     */
    @Test
    public void primaryButtonRespondsToHoverPressAndRelease() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", null),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, false, null)));
        SceneNode ok = buttonNode(1);
        int[] c = absCenter(ok);
        Assert.assertEquals("静止态=主题主按钮配方",
                primarySurface().getIdle().getTint(), ok.getBackgroundColor());

        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("悬停切主按钮悬停档", primarySurface().getHovered().getTint(), ok.getBackgroundColor());
        Assert.assertEquals("悬停切手型光标", SceneCursor.POINTER, ok.getCursor());

        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        Assert.assertEquals("按下切主按钮按下档", primarySurface().getPressed().getTint(), ok.getBackgroundColor());

        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        Assert.assertEquals("抬起回到悬停档", primarySurface().getHovered().getTint(), ok.getBackgroundColor());

        routePointer(ScenePointerAction.MOVE, 4, 4); // 移到遮罩上（按钮外）
        Assert.assertEquals("移开复原静止态", primarySurface().getIdle().getTint(), ok.getBackgroundColor());
    }

    /** 主题按钮角色配方（对话框按钮复用 SceneButton，默认外观由主题提供）。 */
    private static SceneSurfaceStyle standardSurface() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);
    }

    private static SceneSurfaceStyle primarySurface() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_PRIMARY);
    }

    private static SceneSurfaceStyle dangerSurface() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_DANGER);
    }

    /** 普通按钮：静止落主题标准按钮配方、悬停提亮一档（旧实现把 hover 色当静态底色写死）。 */
    @Test
    public void normalButtonRestsOnStandardBackgroundAndLightensOnHover() {
        openDialog(Arrays.asList(SceneDialog.Button.of("取消", null)));
        SceneNode cancel = buttonNode(0);
        Assert.assertEquals("静止态=主题标准按钮配方",
                standardSurface().getIdle().getTint(), cancel.getBackgroundColor());
        int[] c = absCenter(cancel);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("悬停提亮一档", standardSurface().getHovered().getTint(), cancel.getBackgroundColor());
    }

    /** 危险按钮：底色来自主题危险按钮角色，且同样有悬停档。 */
    @Test
    public void dangerButtonUsesSharedDangerTokensAndHovers() {
        openDialog(Arrays.asList(
                new SceneDialog.Button("删除", SceneDialog.ButtonKind.DANGER, false, null)));
        SceneNode danger = buttonNode(0);
        Assert.assertEquals("危险底走主题危险角色", dangerSurface().getIdle().getTint(), danger.getBackgroundColor());
        int[] c = absCenter(danger);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("危险按钮也有悬停反馈",
                dangerSurface().getHovered().getTint(), danger.getBackgroundColor());
    }

    // ==================== 主题化：默认配方与遮罩口径 ====================

    /** 库默认主题的 OVERLAY 角色配方：对话框面板默认外观唯一来源。 */
    private static SceneSurfaceStyle overlaySurface() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    }

    /**
     * 默认工厂路径：面板 = OVERLAY 配方（染色/缘色/边框宽/圆角/实体高度/滤镜全来自配方），
     * 自身恰好一条 BACKDROP 且滤镜之上是半透明 tint（无不透明底盖）；遮罩只负责遮罩色与命中、
     * 不叠第二层玻璃；标题/正文各零 BACKDROP、前景取主题正文/次要前景。
     */
    @Test
    public void defaultPanelUsesOverlayRecipeWithSingleBackdropAndScrimStaysMaskOnly() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        SceneNode scrim = overlayRoot();
        SceneNode card = cardNode();
        SceneSurfaceStyle overlay = overlaySurface();

        Assert.assertEquals("面板背景 = OVERLAY 配方 idle 染色",
                overlay.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("面板缘色 = OVERLAY 配方 idle 缘色",
                overlay.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("面板边框宽 = OVERLAY 配方", overlay.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("面板圆角 = OVERLAY 配方", overlay.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("面板实体高度 = OVERLAY 配方", overlay.getIdle().getElevation(),
                card.__getSurfaceElevation(), 0.0001f);
        Assert.assertNotNull("面板应写入 OVERLAY 配方滤镜", card.getBackdrop());
        Assert.assertEquals("面板材质 = OVERLAY 配方", overlay.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("面板模糊半径 = OVERLAY 配方", overlay.getBackdrop().getBlurRadius(),
                card.getBackdrop().getBlurRadius());

        Assert.assertEquals("标题前景 = 主题正文前景", SceneThemes.DEFAULT.foreground(),
                card.__getChildren().get(0).getTextColor());
        Assert.assertEquals("正文前景 = 主题次要前景", SceneThemes.DEFAULT.mutedForeground(),
                card.__getChildren().get(1).getTextColor());

        // 遮罩只负责遮罩：保持既有遮罩色与全屏命中，不参与表面绑定
        Assert.assertNull("遮罩不得挂滤镜（不叠第二层玻璃）", scrim.getBackdrop());
        Assert.assertEquals("遮罩保持既有 80% 暗色", 0xCC, alpha(scrim.getBackgroundColor()));

        paintOverlay();
        Assert.assertEquals("面板自身恰好一条 BACKDROP", 1, ownBackdropCount(card));
        Assert.assertEquals("遮罩自身零 BACKDROP", 0, ownBackdropCount(scrim));
        List<PaintCommand> commands = ownCommands(card);
        int backdropIndex = indexOfType(commands, PaintCommandType.BACKDROP);
        int backgroundIndex = indexOfType(commands, PaintCommandType.BACKGROUND);
        Assert.assertTrue("BACKDROP 必须先于 BACKGROUND",
                backdropIndex >= 0 && backgroundIndex > backdropIndex);
        Assert.assertTrue("滤镜之上不得压不透明底盖",
                alpha(commands.get(backgroundIndex).getColor()) < 0xFF);
        Assert.assertEquals("标题零 BACKDROP（内容不各自采样玻璃）",
                0, ownBackdropCount(card.__getChildren().get(0)));
        Assert.assertEquals("正文零 BACKDROP（内容不各自采样玻璃）",
                0, ownBackdropCount(card.__getChildren().get(1)));
    }

    // ==================== 主题化：切换 / 延迟显示 / 离场中重开 / 回收 ====================

    /**
     * 打开中切主题：面板与标题/正文随主题更新，按钮/焦点/命中不丢，节点身份不变、effect 数不增长。
     */
    @Test
    public void themeSwitchWhileOpenUpdatesPanelAndKeepsButtonsFocusAndHit() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 OVERLAY 配方必须不同",
                dark.surface(SceneTheme.Role.OVERLAY), light.surface(SceneTheme.Role.OVERLAY));
        Assert.assertNotEquals("测试前提：深/浅正文前景必须不同",
                Integer.valueOf(dark.foreground()), Integer.valueOf(light.foreground()));
        Assert.assertNotEquals("测试前提：深/浅次要前景必须不同",
                Integer.valueOf(dark.mutedForeground()), Integer.valueOf(light.mutedForeground()));

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        mountThemedDialog(pageTheme, dialogProps(visible, twoButtons()));
        tickAndFlush(1_000_000_000L);
        doLayout();

        SceneNode card = cardNode();
        SceneNode cancel = buttonNode(0);
        Assert.assertEquals("深色面板 = 深色 OVERLAY idle 染色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("深色标题 = 深色正文前景", dark.foreground(),
                card.__getChildren().get(0).getTextColor());
        Assert.assertEquals("深色正文 = 深色次要前景", dark.mutedForeground(),
                card.__getChildren().get(1).getTextColor());
        Assert.assertSame("打开聚焦首按钮", cancel, runtime.getFocusedNode());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        doLayout();

        Assert.assertSame("切主题不重建面板根", card, cardNode());
        Assert.assertSame("切主题不重建按钮", cancel, buttonNode(0));
        Assert.assertEquals("面板随主题更新", light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                card.getBackgroundColor());
        Assert.assertEquals("面板缘色随主题更新", light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(),
                card.getBorderColor());
        Assert.assertEquals("面板材质随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("标题随主题更新", light.foreground(),
                card.__getChildren().get(0).getTextColor());
        Assert.assertEquals("正文随主题更新", light.mutedForeground(),
                card.__getChildren().get(1).getTextColor());
        Assert.assertSame("焦点不丢", cancel, runtime.getFocusedNode());
        Assert.assertEquals("切主题不新增订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());

        // 命中不丢：切主题后点次按钮仍触发回调并请求关闭
        int[] c = absCenter(buttonNode(1));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("切主题后按钮命中仍有效", Arrays.asList("confirm"), log);
        Assert.assertTrue("切主题后点击仍请求关闭", dismissed.get());
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
    }

    /**
     * 延迟显示仍取来源主题：构建期 visible=false（浮层未建）→ 切主题 → 置 true，
     * 面板/文字取构建期捕获的来源主题信号当前值，焦点与命中不丢。
     */
    @Test
    public void delayedDisplayUsesSourceThemeCapturedAtCreate() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        Signal<Boolean> pageVisible = Signal.create(Boolean.FALSE);
        mountThemedDialog(pageTheme, dialogProps(pageVisible, twoButtons()));
        Assert.assertEquals("初始不可见不构建浮层", 0, overlaySize());

        pageTheme.set(light);
        runtime.flush();

        pageVisible.set(Boolean.TRUE);
        runtime.flush();
        tickAndFlush(1_000_000_000L);
        doLayout();

        Assert.assertEquals("延迟显示后挂载浮层", 1, overlaySize());
        SceneNode card = cardNode();
        Assert.assertEquals("延迟显示面板取来源主题 OVERLAY 染色",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("延迟显示面板缘色取来源主题",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("延迟显示标题取来源主题正文前景", light.foreground(),
                card.__getChildren().get(0).getTextColor());
        Assert.assertEquals("延迟显示正文取来源主题次要前景", light.mutedForeground(),
                card.__getChildren().get(1).getTextColor());
        Assert.assertEquals("延迟显示聚焦首按钮", true,
                runtime.interactionState(buttonNode(0)).focused().get());

        // 显示后切主题：面板随主题更新、节点身份与焦点不丢、订阅不增长
        SceneNode cardAfterShow = cardNode();
        SceneNode firstButton = buttonNode(0);
        int effectsAfterShow = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(dark);
        runtime.flush();
        doLayout();
        Assert.assertSame("显示后切主题不重建面板", cardAfterShow, cardNode());
        Assert.assertSame("显示后切主题不重建按钮", firstButton, buttonNode(0));
        Assert.assertEquals("显示后面板随主题更新",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), cardNode().getBackgroundColor());
        Assert.assertEquals("显示后标题随主题更新", dark.foreground(),
                cardNode().__getChildren().get(0).getTextColor());
        Assert.assertEquals("显示后正文随主题更新", dark.mutedForeground(),
                cardNode().__getChildren().get(1).getTextColor());
        Assert.assertSame("显示后焦点不丢", firstButton, runtime.getFocusedNode());
        Assert.assertEquals("显示后切主题不新增订阅", effectsAfterShow,
                ReactiveTestProbe.registeredEffectCount());

        int[] c = absCenter(buttonNode(1));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("延迟显示后命中仍有效", Arrays.asList("confirm"), log);
        Assert.assertTrue("延迟显示后点击仍请求关闭", dismissed.get());
    }

    /**
     * 离场中重开：退场半程切主题并重新置 true（取消退场、重放淡入），面板仍是同一节点
     * 且取来源主题当前值，焦点/命中不丢。
     */
    @Test
    public void reopenDuringLeaveKeepsSourceThemeAndNodeIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        mountThemedDialog(pageTheme, dialogProps(visible, twoButtons()));
        tickAndFlush(1_000_000_000L);
        doLayout();
        SceneNode cardBefore = cardNode();
        Assert.assertEquals("初始面板 = 深色 OVERLAY 染色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), cardBefore.getBackgroundColor());

        visible.set(Boolean.FALSE);
        runtime.flush();
        tickAndFlush(1_000_000_000L + LEAVE / 2);
        Assert.assertEquals("离场半程仍挂载", 1, overlaySize());

        pageTheme.set(light);
        runtime.flush();
        doLayout();
        Assert.assertSame("离场中切主题不重建面板", cardBefore, cardNode());
        Assert.assertEquals("离场中面板随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), cardNode().getBackgroundColor());

        visible.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("离场中重开仍是一个浮层", 1, overlaySize());
        Assert.assertSame("重开不重建面板节点", cardBefore, cardNode());
        Assert.assertEquals("重开取来源主题 OVERLAY 染色",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), cardNode().getBackgroundColor());
        Assert.assertEquals("重开取来源主题缘色",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), cardNode().getBorderColor());
        Assert.assertEquals("重开正文取来源主题次要前景", light.mutedForeground(),
                cardNode().__getChildren().get(1).getTextColor());
        Assert.assertEquals("重放淡入：焦点仍在首按钮", true,
                runtime.interactionState(buttonNode(0)).focused().get());

        tickAndFlush(2_000_000_000L); // 重放淡入完成
        Assert.assertEquals("重放淡入完成后面板可见", 1f, cardNode().getOpacity(), 0.001f);
        doLayout();
        int[] c = absCenter(buttonNode(1));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("重开后命中仍有效", Arrays.asList("confirm"), log);
        Assert.assertTrue("重开后点击仍请求关闭", dismissed.get());
    }

    /**
     * 卸载回收：退场完成即回收浮层内全部绑定（面板/文字/动画），承载页面卸载后回到挂载前基线。
     */
    @Test
    public void closeReclaimsOverlayBindingsAndPageDisposeReturnsToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        MountHandle page = mountThemedDialog(Signal.create(SceneTheme.liquidGlassDark()),
                dialogProps(visible, twoButtons()));
        tickAndFlush(1_000_000_000L);
        doLayout();
        Assert.assertEquals("对话框已挂载", 1, overlaySize());
        int openEffects = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定", openEffects > baseline);

        visible.set(Boolean.FALSE);
        runtime.flush();
        tickAndFlush(1_000_000_000L + LEAVE);
        Assert.assertEquals("退场完成卸载", 0, overlaySize());
        int closedEffects = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("关闭回收浮层内绑定", closedEffects < openEffects);

        page.dispose();
        runtime.flush();
        Assert.assertEquals("承载页面卸载后回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 内聚化：不得再自带按钮/文本/调色板 ====================

    /**
     * 源码守卫：对话框必须把按钮行为、文本换行、面板外壳委托给既有权威。
     *
     * <p>本类历史上自带一份 {@code rt.on(CLICK/KEY_DOWN)}、一份静态配色、一份私藏 DANGER_BG，
     * 与 {@code SceneButtonPrimitive}/{@code SceneStateColors} 并行演化，结果四态反馈整个缺失；
     * 面板外壳也曾由 {@code SceneChromeTokens.applyPanelChrome} 静态写底色/边框/圆角，与主题配方
     * 形成两个外观写入者。结构用例只证明"现在能用"，证明不了"没退回手搓"——这条按签名粒度钉住
     * 委托关系与唯一写入者。</p>
     */
    @Test
    public void dialogMustDelegateButtonTextAndPanelShellToControlAuthorities() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/SceneDialog.java");
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
        Assert.assertFalse("按钮行为不得再自建（CLICK/KEY_DOWN 归 SceneButtonPrimitive）",
                src.contains("SceneEventType"));
        Assert.assertTrue("按钮必须委托 SceneButton", src.contains("SceneButton.create("));
        Assert.assertTrue("标题/正文必须委托 SceneLabel", src.contains("SceneLabel.create("));
        Assert.assertTrue("面板外壳必须走表面绑定器（唯一外观写入者）",
                src.contains("SceneSurfaceBinder.bind("));
        Assert.assertTrue("面板必须取 OVERLAY 角色配方",
                src.contains("SceneTheme.Role.OVERLAY"));
        Assert.assertFalse("旧静态面板外壳写入者必须删除",
                src.contains("applyPanelChrome("));
        Assert.assertFalse("本类不得再自带 0xFF 色值（调色板归 token）", src.contains("0xFF"));
    }
}

