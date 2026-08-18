package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
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
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneToast 独立单元测试：show 挂载、多 toast 堆叠、帧时间驱动「展示 → 退场 → 移除」、
 * 内容宽度收缩与水平居中、底部堆叠、出现/退场动画、类型化入口、非模态指针穿透。
 *
 * <p>overlay 布局在测试内手动执行（无管线）；动画与到期均由 runtime.__tickFrame 驱动。</p>
 */
public class SceneToastTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
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

    /** 当前第一条 toast 节点（container 子 0）。 */
    private SceneNode firstToast() {
        return toastContainer().__getChildren().get(0);
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

    // ==================== 类型化 ====================

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
        SceneNode successDot = container.__getChildren().get(0).__getChildren().get(0);
        SceneNode errorDot = container.__getChildren().get(1).__getChildren().get(0);
        Assert.assertEquals("SUCCESS 色点", 0xFF81C784, successDot.getBackgroundColor());
        Assert.assertEquals("ERROR 色点", 0xFFE57373, errorDot.getBackgroundColor());
    }

    @Test
    public void showDefaultsToInfoType() {
        SceneToast.show(runtime, "普通");
        runtime.flush();
        tickAndFlush(ENTER);
        doLayout();
        SceneNode dot = firstToast().__getChildren().get(0);
        Assert.assertEquals("默认 INFO 色点", 0xFFD0BCFF, dot.getBackgroundColor());
    }
}
