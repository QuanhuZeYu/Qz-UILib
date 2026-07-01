package club.heiqi.uilib.internal.devtools.pages;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrollHostWidget 滚动 demo 组装单元测试 —— Phase 4 批 4 步骤 B 真机接入验收。
 *
 * <h3>验收范围（demo 组装层，引擎核心已由 SceneScrollViewportTest 单独验收）</h3>
 * <ul>
 *   <li>① viewport 钉死视口高 + 内容总高溢出（demo 实际配置 20×32=640 > 240）。</li>
 *   <li>② 滚轮 handler 经 signal 更新 offset + clamp（signal-first 路径，向下/向上/超界）。</li>
 *   <li>③ <b>内容条目深命中 SCROLL 冒泡到 viewport handler</b>（填 SceneScrollViewportTest 验收7
 *       「viewport 直命中」未覆盖的 bubble 盲区）。</li>
 * </ul>
 *
 * <h3>注入范式</h3>
 * <p>null 输入源 → 退化模式构造 SceneScrollHostWidget（不触发 LWJGL 反射，沙箱安全）。
 * 通过 {@code __get*} 探针取内部 runtime/layoutEngine/root/viewport/content/scrollSignal，
 * 手动跑 layout → 造帧 route → flush 模拟 drawSelf 的 pipeline。</p>
 */
public class SceneScrollDemoTest {

    private SceneScrollHostWidget host;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneNode root;
    private SceneNode viewport;
    private SceneNode content;

    /** 模拟 host 画布尺寸（足够宽高） */
    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 400;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        // null 输入源 → 退化模式，不注入 LWJGL cursor 后端（沙箱安全）
        host = new SceneScrollHostWidget(null);
        runtime = host.__getRuntime();
        layoutEngine = host.__getLayoutEngine();
        root = host.__getRoot();
        viewport = host.__getViewport();
        content = host.__getContent();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 跑一次布局（模拟 drawSelf 的 layout 阶段） */
    private void doLayout() {
        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 模拟 drawSelf pipeline：layout① → route(SCROLL) → flush → layout②。
     *
     * <p>滚轮事件命中 (x, y) 处最深节点，沿父链冒泡到 viewport handler。</p>
     *
     * @param x          滚轮命中绝对 X
     * @param y          滚轮命中绝对 Y
     * @param wheelDelta 滚轮增量（向下滚 &lt; 0）
     */
    private void routeScrollAt(int x, int y, int wheelDelta) {
        doLayout();
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
        doLayout();
    }

    /** 计算节点绝对坐标（沿父链累加 LayoutBox.x/y） */
    private int[] absOrigin(SceneNode n) {
        int ax = 0;
        int ay = 0;
        SceneNode cur = n;
        while (cur != null) {
            LayoutBox b = (LayoutBox) cur.getCachedLayout();
            if (b != null) {
                ax += b.getX();
                ay += b.getY();
            }
            cur = cur.__getParent();
        }
        return new int[] { ax, ay };
    }

    // ==================== 验收①：viewport 钉死视口高 + 内容溢出 ====================

    /**
     * demo 实际配置：viewport scrollable + preferredHeight=240，内容 20 条 × 32px = 640。
     * 断言 viewport.height 钉死 240（不被内容 640 撑大），content.height==640 溢出。
     */
    @Test
    public void viewportShouldPinHeightAndContentShouldOverflow() {
        doLayout();

        LayoutBox vb = (LayoutBox) viewport.getCachedLayout();
        LayoutBox cb = (LayoutBox) content.getCachedLayout();
        Assert.assertNotNull("viewport 应已布局", vb);
        Assert.assertNotNull("content 应已布局", cb);

        Assert.assertEquals("viewport 高度应钉死为视口高 240（不被内容撑大）",
                SceneScrollHostWidget.__getViewportHeight(), vb.getHeight());
        Assert.assertEquals("content 总高应为 20×32=640", 640, cb.getHeight());
        Assert.assertTrue("内容总高应溢出视口（滚动前提）", cb.getHeight() > vb.getHeight());
    }

    // ==================== 验收②：滚轮 handler 经 signal 更新 offset + clamp ====================

    /**
     * signal-first 路径：滚轮命中 viewport → handler 写 scrollSignal → flush → bind 推给
     * setScrollOffsetY。验证向下滚 offset 增大、向上滚回 0、超界 clamp 到 maxScroll。
     */
    @Test
    public void wheelShouldUpdateScrollOffsetViaSignalWithClamp() {
        doLayout();
        LayoutBox vb = (LayoutBox) viewport.getCachedLayout();
        LayoutBox cb = (LayoutBox) content.getCachedLayout();
        int maxScroll = Math.max(0, cb.getHeight() - vb.getHeight());
        Assert.assertEquals("maxScroll 应为 640-240=400", 400, maxScroll);

        int[] vOrigin = absOrigin(viewport);
        int viewportCenterX = vOrigin[0] + vb.getWidth() / 2;
        int viewportCenterY = vOrigin[1] + vb.getHeight() / 2;

        // 向下滚 wheelDelta=-120 → step=120 → offset 增大到 120
        routeScrollAt(viewportCenterX, viewportCenterY, -120);
        Assert.assertEquals("向下滚后 scrollOffsetY 应为 120", 120, viewport.getScrollOffsetY());
        Assert.assertEquals("signal 终值一致", 120, host.__getScrollSignal().get().intValue());

        // 向上滚 wheelDelta=+120 → step=-120 → offset 减回 0
        routeScrollAt(viewportCenterX, viewportCenterY, 120);
        Assert.assertEquals("向上滚后 scrollOffsetY 应减回 0", 0, viewport.getScrollOffsetY());

        // 超界向上滚（已在 0，再 +120 滚）→ clamp 到 0 不变负
        routeScrollAt(viewportCenterX, viewportCenterY, 120);
        Assert.assertEquals("已在顶部继续向上滚应 clamp 到 0（不变负）", 0, viewport.getScrollOffsetY());

        // 超界向下滚：wheelDelta=-9999 → step=9999 → clamp 到 maxScroll=400
        routeScrollAt(viewportCenterX, viewportCenterY, -9999);
        Assert.assertEquals("超界向下滚应 clamp 到 maxScroll=400", 400, viewport.getScrollOffsetY());

        // 再向下滚仍 clamp 在 400（不超）
        routeScrollAt(viewportCenterX, viewportCenterY, -120);
        Assert.assertEquals("已到底继续向下滚应 clamp 在 400（不超）", 400, viewport.getScrollOffsetY());
    }

    // ==================== 验收③：内容条目深命中 SCROLL 冒泡到 viewport（填验收7 盲区） ====================

    /**
     * <b>bubble 盲区补测</b>：SceneScrollViewportTest 验收7 是 viewport 自己当 target 直命中，
     * 本测试改为滚轮命中<b>某个内容条目几何中心（非 viewport 自身中心）</b>，断言：
     * <ol>
     *   <li>hit-test 最深命中目标是某个 item（而非 viewport 直命中）——证明确实走深命中 + bubble；</li>
     *   <li>viewport handler 仍收到 SCROLL 并更新 scrollOffsetY——证明沿父链冒泡到 viewport 成功。</li>
     * </ol>
     *
     * <p>条目的文本 label 设了 hitTestable=false（pointer-events:none），命中穿透到 item；
     * item 保持默认 hitTestable=true 作为最深命中目标，再 bubble 到挂 handler 的 viewport。</p>
     */
    @Test
    public void scrollOnContentItemShouldBubbleToViewportHandler() {
        doLayout();

        // 取第一个内容条目（content 的首子）
        java.util.List<SceneNode> items = content.__getChildren();
        Assert.assertFalse("content 应有条目", items.isEmpty());
        SceneNode firstItem = items.get(0);

        // 计算第一个条目的几何中心绝对坐标
        LayoutBox itemBox = (LayoutBox) firstItem.getCachedLayout();
        Assert.assertNotNull("条目应已布局", itemBox);
        int[] itemOrigin = absOrigin(firstItem);
        int itemCenterX = itemOrigin[0] + itemBox.getWidth() / 2;
        int itemCenterY = itemOrigin[1] + itemBox.getHeight() / 2;

        // 先验证命中点确实落在条目内、且不等于 viewport 自身中心（确保是深命中而非 viewport 直命中）
        int[] vOrigin = absOrigin(viewport);
        LayoutBox vb = (LayoutBox) viewport.getCachedLayout();
        int viewportCenterY = vOrigin[1] + vb.getHeight() / 2;
        Assert.assertNotEquals("条目中心 Y 不应等于 viewport 中心 Y（确保非 viewport 直命中）",
                viewportCenterY, itemCenterY);

        // 用 hit-tester 确认最深命中目标是条目（而非 viewport）：经 router 派发前先断言命中链
        // 直接断言：命中点处最深 hitTestable 节点是 firstItem（label 穿透、item 命中）
        SceneNode deepest = hitTestDeepest(itemCenterX, itemCenterY);
        Assert.assertSame("最深命中目标应是内容条目 item（label 穿透到 item），而非 viewport",
                firstItem, deepest);

        // 滚轮命中条目中心 → 应 bubble 到 viewport handler 更新 offset
        int beforeOffset = viewport.getScrollOffsetY();
        routeScrollAt(itemCenterX, itemCenterY, -120);
        int afterOffset = viewport.getScrollOffsetY();

        Assert.assertEquals("命中条目滚动前 offset 为 0", 0, beforeOffset);
        Assert.assertEquals("命中条目的 SCROLL 应冒泡到 viewport handler，offset 更新为 120",
                120, afterOffset);
    }

    /**
     * 用 router 内部 hit-tester 求命中点最深 hitTestable 目标（验证深命中而非 viewport 直命中）。
     *
     * <p>经 runtime 暴露的 inputRouter 做一次只读 hit-test：route 一个 SCROLL 帧前，
     * 借 router 的命中链推断最深目标。此处用简化方式：直接复用 route 的 hit-test 结果，
     * 通过在条目上临时挂 handler 捕获 target 来确认。</p>
     *
     * @param x 命中绝对 X
     * @param y 命中绝对 Y
     * @return 最深命中目标节点
     */
    private SceneNode hitTestDeepest(int x, int y) {
        // 在 content 全部条目上临时挂 SCROLL handler 捕获 ev.getTarget()，确认深命中目标。
        // handler target 是 hit-test 最深命中节点（label 穿透后为 item），与冒泡无关。
        final SceneNode[] captured = new SceneNode[1];
        java.util.List<club.heiqi.uilib.ui.scene.input.InputBinding> bindings =
                new java.util.ArrayList<>();
        for (SceneNode item : content.__getChildren()) {
            bindings.add(runtime.on(item,
                    club.heiqi.uilib.ui.scene.input.SceneEventType.SCROLL,
                    (ev, ctx) -> captured[0] = ev.getTarget()));
        }
        doLayout();
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y,
                SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 999L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
        // 退订临时 handler，避免污染后续断言（wheelDelta=0 不改 offset）
        for (club.heiqi.uilib.ui.scene.input.InputBinding b : bindings) {
            b.dispose();
        }
        return captured[0];
    }
}
