package club.heiqi.uilib.ui.scene.host;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * 框架级回归测试：滚动/悬浮/浮层锚定与窗口尺寸同步。
 *
 * <p>覆盖三个真实机症状对应的框架机制：</p>
 * <ul>
 *   <li>虚拟化列表滚动卸载行后，锚点子树离树但直接 parent 非 null，
 *       overlay 关闭判定必须按「是否仍挂任一已挂载根」处理；</li>
 *   <li>非滚轮滚动源（程序写 scrollSignal）必须触发 hover 重算，否则 tooltip 残留；</li>
 *   <li>窗口尺寸变化（root 约束变化）必须驱动主树与 overlay 重新布局。</li>
 * </ul>
 */
public class SceneHostFrameworkRegressionTest {

    private SceneTestHost host;
    private SceneRuntime runtime;
    private UiRenderBackend backend;

    @Before
    public void setUp() {
        host = new SceneTestHost();
        runtime = host.__getRuntime();
        backend = new RecordingRenderBackend();
    }

    @After
    public void tearDown() {
        host.dispose();
    }

    /** 行级卸载（子树离树，直接 parent 仍非 null）后，锚点应视为不可见并请求关闭 overlay。 */
    @Test
    public void anchoredOverlayShouldDismissWhenAnchorSubtreeUnmounted() {
        SceneNode rowsContainer = new SceneNode();
        host.__getRoot().appendChild(rowsContainer);
        SceneNode row = new SceneNode();
        rowsContainer.appendChild(row);
        SceneNode cell = new SceneNode();
        row.appendChild(cell);
        cell.setPreferredWidth(80).setPreferredHeight(20);

        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        AtomicInteger dismissCount = new AtomicInteger(0);
        runtime.portalAnchored(visible, () -> overlayNode(), OverlayDismissPolicy.DEFAULT,
                () -> {
                    dismissCount.incrementAndGet();
                    visible.set(Boolean.FALSE);
                }, AnchorProvider.forNode(cell));

        host.render(200, 120, backend, 0, 0);
        runtime.flush();
        Assert.assertEquals("挂载中不应请求关闭", 0, dismissCount.get());

        // 虚拟化滚动卸载整行：cell 的 parent（row）仍非 null，但整条链已不挂任何已挂载根。
        rowsContainer.removeChild(row);
        host.render(200, 120, backend, 0, 0);
        runtime.flush();
        Assert.assertEquals("行卸载后应请求关闭一次", 1, dismissCount.get());
        Assert.assertFalse("dismissRequest 应写 false", visible.get());
    }

    /** 程序化滚动（scrollSignal.set，非滚轮事件）后，hover 应按新几何重算。 */
    @Test
    public void programmaticScrollShouldReconcileHover() {
        SceneNode viewport = new SceneNode();
        host.__getRoot().appendChild(viewport);
        viewport.setScrollable(true);
        viewport.setPreferredHeight(40);
        SceneNode rowA = new SceneNode();
        SceneNode rowB = new SceneNode();
        rowA.setPreferredHeight(20);
        rowB.setPreferredHeight(20);
        viewport.appendChild(rowA);
        viewport.appendChild(rowB);

        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        // 懒创建时序契约：hovered signal 必须在 route 前声明关心，路由写入才能落到该 signal。
        ReadableSignal<Boolean> hoveredA = runtime.interactionState(rowA).hovered();

        host.render(200, 120, backend, 0, 0);
        // 指针移到 rowA 中心（10,10）
        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.MOVE, 10, 10), 0, 0);
        runtime.flush();
        Assert.assertTrue("rowA 应 hover", Boolean.TRUE.equals(hoveredA.get()));

        // 程序化滚动 20px：rowA 完全滚出视口；绑定应请求 hover 重算，host 帧末重算后 rowA 失去 hover。
        scrollSignal.set(Integer.valueOf(20));
        host.render(200, 120, backend, 0, 0);
        runtime.flush();
        Assert.assertFalse("程序化滚动后 rowA hover 应清除", Boolean.TRUE.equals(hoveredA.get()));
    }

    /** 窗口尺寸变化应驱动主树按新约束重新布局（百分尺寸按新宽高重算）。 */
    @Test
    public void rootConstraintChangeShouldRelayoutPercentSizedSubtree() {
        SceneNode panel = new SceneNode();
        panel.setPercentWidth(70).setPercentHeight(70);
        host.__getRoot().appendChild(panel);

        host.render(200, 120, backend, 0, 0);
        LayoutBox first = (LayoutBox) panel.getCachedLayout();
        Assert.assertEquals("70% 宽", 140, first.getWidth());
        Assert.assertEquals("70% 高", 84, first.getHeight());

        // 模拟窗口缩放：宿主尺寸变化，同一条 render 循环。
        host.render(300, 200, backend, 0, 0);
        LayoutBox second = (LayoutBox) panel.getCachedLayout();
        Assert.assertEquals("缩放后 70% 宽", 210, second.getWidth());
        Assert.assertEquals("缩放后 70% 高", 140, second.getHeight());
    }

    // ==================== 夹具 ====================

    private static SceneNode overlayNode() {
        SceneNode node = new SceneNode();
        node.setPreferredHeight(30);
        node.setBackgroundColor(0xFF00AAFF);
        return node;
    }

    private static SceneInputFrame pointerFrame(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        return fb.drainFrame();
    }
}
