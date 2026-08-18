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
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneToast 独立单元测试：show 挂载、多 toast 堆叠、帧时间驱动到期移除、
 * 非模态指针穿透（toast 不拦截主树命中）。
 *
 * <p>overlay 布局在测试内手动执行（无管线）；到期由 runtime.__tickFrame 驱动。</p>
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

    // ==================== 挂载与到期 ====================

    @Test
    public void showMountsOverlayUntilExpiry() {
        SceneToast.show(runtime, "保存成功", 2_000_000_000L);
        runtime.flush();
        doLayout();
        Assert.assertEquals("toast overlay 挂载", 1, overlaySize());

        tickAndFlush(1_000_000_000L); // 1s < 2s
        Assert.assertEquals("未到期保持", 1, overlaySize());

        tickAndFlush(2_000_000_000L); // 2s ≥ 2s
        Assert.assertEquals("到期移除", 0, overlaySize());
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
        SceneNode container = runtime.getOverlayHost().bottomFirst().get(0).getRoot();
        Assert.assertEquals("堆叠 2 toast 节点", 2, container.__getChildren().size());

        tickAndFlush(1_000_000_000L); // t=1s：先 1s-0=1 ≥ 1 → 移除；后 1-0.5=0.5 < 5 留
        Assert.assertEquals("第一条到期移除", 1, overlaySize());
        Assert.assertEquals("剩 1 条", 1, container.__getChildren().size());

        tickAndFlush(6_000_000_000L); // t=6s：后 6-0.5=5.5 ≥ 5 → 移除
        Assert.assertEquals("全部到期", 0, overlaySize());
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
        doLayout();
        // toast 在底部；点击顶部区域：overlay 整树不可命中 → 主树探针命中
        pressAt(10, 10);
        Assert.assertEquals("toast 非模态穿透", 1, mainTreeHitLog.size());
    }
}
