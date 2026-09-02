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
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

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
        Assert.assertEquals("静止态=ACCENT 主色", SceneChromeTokens.ACCENT, ok.getBackgroundColor());

        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("悬停切 ACCENT_HOVER", SceneChromeTokens.ACCENT_HOVER, ok.getBackgroundColor());
        Assert.assertEquals("悬停切手型光标", SceneCursor.POINTER, ok.getCursor());

        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        Assert.assertEquals("按下切 ACCENT_PRESSED", SceneChromeTokens.ACCENT_PRESSED, ok.getBackgroundColor());

        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        Assert.assertEquals("抬起回到悬停档", SceneChromeTokens.ACCENT_HOVER, ok.getBackgroundColor());

        routePointer(ScenePointerAction.MOVE, 4, 4); // 移到遮罩上（按钮外）
        Assert.assertEquals("移开复原静止态", SceneChromeTokens.ACCENT, ok.getBackgroundColor());
    }

    /** 普通按钮：静止落面板底色、悬停提亮一档（旧实现把 hover 色当静态底色写死）。 */
    @Test
    public void normalButtonRestsOnStandardBackgroundAndLightensOnHover() {
        openDialog(Arrays.asList(SceneDialog.Button.of("取消", null)));
        SceneNode cancel = buttonNode(0);
        Assert.assertEquals("静止态=面板底色", SceneChromeTokens.BG_DEFAULT, cancel.getBackgroundColor());
        int[] c = absCenter(cancel);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("悬停提亮一档", SceneChromeTokens.BG_HOVER, cancel.getBackgroundColor());
    }

    /** 危险按钮：底色收口到共享 token（旧实现私藏一个同名不同值的 DANGER_BG），且同样有悬停档。 */
    @Test
    public void dangerButtonUsesSharedDangerTokensAndHovers() {
        openDialog(Arrays.asList(
                new SceneDialog.Button("删除", SceneDialog.ButtonKind.DANGER, false, null)));
        SceneNode danger = buttonNode(0);
        Assert.assertEquals("危险底走 token", SceneChromeTokens.DANGER_BG, danger.getBackgroundColor());
        int[] c = absCenter(danger);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        Assert.assertEquals("危险按钮也有悬停反馈",
                SceneChromeTokens.DANGER_BG_HOVER, danger.getBackgroundColor());
    }

    // ==================== 内聚化：不得再自带按钮/文本/调色板 ====================

    /**
     * 源码守卫：对话框必须把按钮行为、文本换行、面板外壳委托给既有权威。
     *
     * <p>本类历史上自带一份 {@code rt.on(CLICK/KEY_DOWN)}、一份静态配色、一份私藏 DANGER_BG，
     * 与 {@code SceneButtonPrimitive}/{@code SceneStateColors} 并行演化，结果四态反馈整个缺失。
     * 结构用例只证明"现在能用"，证明不了"没退回手搓"——这条按签名粒度钉住委托关系。</p>
     */
    @Test
    public void dialogMustDelegateButtonAndTextToControlAuthorities() throws Exception {
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
        Assert.assertTrue("卡片外壳必须走 applyPanelChrome", src.contains("applyPanelChrome("));
        Assert.assertFalse("本类不得再自带 0xFF 色值（调色板归 token）", src.contains("0xFF"));
    }
}

