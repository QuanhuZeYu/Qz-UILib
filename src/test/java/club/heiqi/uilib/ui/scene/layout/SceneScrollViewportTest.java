package club.heiqi.uilib.ui.scene.layout;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * 纵向滚动视口单元测试 —— Phase 4 批 4 步骤 B「滚动/视口基础设施地基」验收。
 *
 * <p>验证核心约束：scrollable 钉死视口高、scrollOffsetY 只标 geometry 不标 layout/paint、
 * 滚动帧 layout 零重排（I7 命门反证）、后代 fragment 复用（信条七反证）、CLIP 裁剪固定不随滚动跑、
 * 滚轮 handler clamp、几何偏移生效。</p>
 */
public class SceneScrollViewportTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        paintEngine = new ScenePaintEngine();
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private PaintPlan doPaint() {
        return paintEngine.paint(sceneRoot);
    }

    // ==================== 验收 1：视口高钉死（scrollable 节点不被内容撑大） ====================

    /**
     * scrollable viewport 设 preferredHeight=200，子内容总高 600，
     * 断言 viewport LayoutBox.height==200（不被内容撑到 600）。
     */
    @Test
    public void scrollableViewportShouldPinHeightIgnoringContentHeight() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        // 子内容：3 个各高 200 的面板，总高 600
        for (int i = 0; i < 3; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(200);
            child.setBackgroundColor(0xFF000000 + (i + 1) * 0x111111);
            viewport.appendChild(child);
        }

        doLayout();

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertNotNull("viewport 应已布局", viewportBox);
        Assert.assertEquals("scrollable viewport 高度应钉死为 preferredHeight=200（不被内容 600 撑大）",
                200, viewportBox.getHeight());

        // 验证子内容确实总高 600（通过末子 y + height）
        List<SceneNode> children = viewport.__getChildren();
        Assert.assertEquals("应有 3 个子节点", 3, children.size());
        LayoutBox lastChildBox = (LayoutBox) children.get(2).getCachedLayout();
        int contentTotalHeight = lastChildBox.getY() + lastChildBox.getHeight();
        Assert.assertEquals("子内容总高应为 600", 600, contentTotalHeight);
    }

    // ==================== 验收 2：滚动帧 layout 零重排（I7 命门反证） ====================

    /**
     * 滚动后改 scrollOffsetY，断言 layoutEngine.__getRelayoutCount()==0
     * ——这是 I7 不破的核心反证：滚动只标 geometry 不标 layout，绝不触发重排。
     */
    @Test
    public void scrollShouldNotTriggerRelayout() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);

        // 首帧布局
        doLayout();
        int firstLayoutCount = layoutEngine.__getRelayoutCount();
        Assert.assertTrue("首帧应有布局发生", firstLayoutCount > 0);

        // 滚动：改 scrollOffsetY
        viewport.setScrollOffsetY(100);

        // 滚动帧布局：断言零重排
        doLayout();
        int scrollLayoutCount = layoutEngine.__getRelayoutCount();
        Assert.assertEquals("滚动帧 layout 应零重排（I7 命门反证）",
                0, scrollLayoutCount);
    }

    // ==================== 验收 3：后代 fragment 复用（信条七反证） ====================

    /**
     * 滚动帧断言 paint 的 regeneratedFragmentCount 不增——
     * 证明滚动只重定位不重绘，后代 fragment 复用。
     */
    @Test
    public void scrollShouldReuseFragmentsWithoutRegeneration() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        content.setBackgroundColor(0xFFFF0000);
        viewport.appendChild(content);

        doLayout();

        // 首帧 paint：记录生成的 fragment 数
        PaintPlan plan1 = doPaint();
        int firstRegenCount = paintEngine.__getRegeneratedFragmentCount();
        Assert.assertTrue("首帧应生成 fragment", firstRegenCount > 0);

        // 滚动
        viewport.setScrollOffsetY(100);

        // 滚动帧 paint：断言 regeneratedFragmentCount==0（后代 fragment 复用）
        PaintPlan plan2 = doPaint();
        int scrollRegenCount = paintEngine.__getRegeneratedFragmentCount();
        Assert.assertEquals("滚动帧应零 fragment 重生成（信条七反证：滚动只重定位不重绘）",
                0, scrollRegenCount);
    }

    // ==================== 验收 4：CLIP 裁剪固定不随滚动跑 ====================

    /**
     * 断言 scrollable 节点产出的 CLIP_PUSH 命令坐标 == viewport 自己绝对坐标（不含 scrollOffset），
     * 滚动后 CLIP 坐标不变、只内容偏移变。
     */
    @Test
    public void clipShouldStayFixedWhileContentScrolls() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        content.setBackgroundColor(0xFFFF0000);
        viewport.appendChild(content);

        doLayout();

        // 首帧 paint：找到 CLIP_PUSH 命令（scrollable 自动触发 clipChildren 行为）
        PaintPlan plan1 = doPaint();
        PaintCommand clip1 = findFirstClipPush(plan1);
        Assert.assertNotNull("scrollable 节点应产出 CLIP_PUSH", clip1);

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        int expectedClipLeft = 0;  // viewport 是 root 直接子节点，absX=0
        int expectedClipTop = 0;   // viewport 是首个子节点，absY=0
        int expectedClipRight = viewportBox.getWidth();
        int expectedClipBottom = viewportBox.getHeight();

        Assert.assertEquals("CLIP left 应为 viewport 绝对 X", expectedClipLeft, clip1.getLeft());
        Assert.assertEquals("CLIP top 应为 viewport 绝对 Y（不含 scrollOffset）", expectedClipTop, clip1.getTop());
        Assert.assertEquals("CLIP right 应为 viewport 右边界", expectedClipRight, clip1.getRight());
        Assert.assertEquals("CLIP bottom 应为 viewport 下边界", expectedClipBottom, clip1.getBottom());

        // 滚动后 paint：CLIP 坐标应完全不变
        viewport.setScrollOffsetY(100);
        PaintPlan plan2 = doPaint();
        PaintCommand clip2 = findFirstClipPush(plan2);
        Assert.assertNotNull("滚动后仍应有 CLIP_PUSH", clip2);

        Assert.assertEquals("滚动后 CLIP left 不变", clip1.getLeft(), clip2.getLeft());
        Assert.assertEquals("滚动后 CLIP top 不变（视口窗口固定）", clip1.getTop(), clip2.getTop());
        Assert.assertEquals("滚动后 CLIP right 不变", clip1.getRight(), clip2.getRight());
        Assert.assertEquals("滚动后 CLIP bottom 不变", clip1.getBottom(), clip2.getBottom());
    }

    private PaintCommand findFirstClipPush(PaintPlan plan) {
        for (PaintCommand cmd : plan.getCommands()) {
            if (cmd.getType() == PaintCommandType.CLIP_PUSH) {
                return cmd;
            }
        }
        return null;
    }

    // ==================== 验收 5：滚动 clamp ====================

    /**
     * scrollOffsetY 被 clamp 到 [0, maxScroll]，超界不溢出。
     * 滚轮 handler 实现 clamp 逻辑，测试模拟其行为。
     */
    @Test
    public void scrollOffsetShouldBeClampedToValidRange() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);

        doLayout();

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
        int viewportHeight = viewportBox.getHeight();
        int contentHeight = contentBox.getHeight();
        int maxScroll = Math.max(0, contentHeight - viewportHeight);

        Assert.assertEquals("maxScroll 应为 600-200=400", 400, maxScroll);

        // 模拟 handler clamp 逻辑（handler 在写 scrollOffsetY 前 clamp）
        int attemptNegative = -50;
        int clampedNegative = Math.max(0, Math.min(maxScroll, attemptNegative));
        viewport.setScrollOffsetY(clampedNegative);
        Assert.assertEquals("负值应被 clamp 到 0", 0, viewport.getScrollOffsetY());

        int attemptOverMax = 500;
        int clampedOverMax = Math.max(0, Math.min(maxScroll, attemptOverMax));
        viewport.setScrollOffsetY(clampedOverMax);
        Assert.assertEquals("超 maxScroll 应被 clamp 到 400", 400, viewport.getScrollOffsetY());

        int attemptValid = 200;
        int clampedValid = Math.max(0, Math.min(maxScroll, attemptValid));
        viewport.setScrollOffsetY(clampedValid);
        Assert.assertEquals("范围内值不变", 200, viewport.getScrollOffsetY());
    }

    // ==================== 验收 6：几何偏移生效 ====================

    /**
     * 滚动后某个子内容节点的绝对 Y 坐标 == 原始 Y - scrollOffsetY。
     * 通过 paint 产出的 fragment offset 验证（BACKGROUND 命令 top）。
     */
    @Test
    public void scrollOffsetShouldTranslateChildAbsoluteY() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        content.setBackgroundColor(0xFFFF0000);  // 产出 BACKGROUND 命令
        viewport.appendChild(content);

        doLayout();

        // 首帧 paint：content 的 BACKGROUND 命令 top 应为 0（相对 viewport padTop=0）
        PaintPlan plan1 = doPaint();
        PaintCommand bg1 = findFirstBackground(plan1);
        Assert.assertNotNull("content 应产出 BACKGROUND 命令", bg1);
        Assert.assertEquals("首帧 content 绝对 top=0", 0, bg1.getTop());

        // 滚动 100 像素
        viewport.setScrollOffsetY(100);
        PaintPlan plan2 = doPaint();
        PaintCommand bg2 = findFirstBackground(plan2);
        Assert.assertNotNull("滚动后仍应有 BACKGROUND 命令", bg2);

        // 滚动后 content 绝对 top 应为 0 - 100 = -100（内容上移 100 像素）
        // 方向语义：scrollOffsetY 向下为正（值越大越往下滚），content 往上移（绝对 Y 减小）
        Assert.assertEquals("滚动后 content 绝对 top = 原始 top - scrollOffsetY",
                -100, bg2.getTop());
    }

    /**
     * B5：absoluteBox 回溯式注入嵌套 scrollable 祖先的 scrollOffsetY。
     */
    @Test
    public void absoluteBoxShouldSubtractNestedScrollableAncestors() {
        SceneNode outerViewport = new SceneNode();
        SceneNode innerViewport = new SceneNode();
        SceneNode trigger = new SceneNode();
        sceneRoot.appendChild(outerViewport);
        outerViewport.appendChild(innerViewport);
        innerViewport.appendChild(trigger);

        sceneRoot.setCachedLayout(new LayoutBox(0, 0, 400, 300));
        outerViewport.setCachedLayout(new LayoutBox(10, 20, 200, 160));
        innerViewport.setCachedLayout(new LayoutBox(5, 70, 150, 100));
        trigger.setCachedLayout(new LayoutBox(3, 40, 80, 24));
        outerViewport.setScrollable(true);
        innerViewport.setScrollable(true);
        outerViewport.setScrollOffsetY(30);
        innerViewport.setScrollOffsetY(15);

        club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver.AnchorRect box =
                SceneGeometry.absoluteBox(trigger, 0, 0);

        Assert.assertEquals("X 不受纵向滚动影响", 18, box.getX());
        Assert.assertEquals("Y 应沿链减两个 scrollOffsetY", 85, box.getY());
        Assert.assertEquals("宽度保持 trigger 自身布局", 80, box.getWidth());
        Assert.assertEquals("高度保持 trigger 自身布局", 24, box.getHeight());
    }

    /**
     * absoluteBox 作为 SceneSelect anchorProvider 源时，滚动后 anchorY 跟随 trigger 上移。
     */
    @Test
    public void absoluteBoxAnchorShouldFollowScrollableTrigger() {
        SceneNode viewport = new SceneNode();
        SceneNode trigger = new SceneNode();
        sceneRoot.appendChild(viewport);
        viewport.appendChild(trigger);

        sceneRoot.setCachedLayout(new LayoutBox(0, 0, 400, 300));
        viewport.setCachedLayout(new LayoutBox(0, 20, 200, 100));
        trigger.setCachedLayout(new LayoutBox(0, 60, 120, 24));
        viewport.setScrollable(true);

        club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver.AnchorRect before =
                SceneGeometry.absoluteBox(trigger, 0, 0);
        viewport.setScrollOffsetY(50);
        club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver.AnchorRect after =
                SceneGeometry.absoluteBox(trigger, 0, 0);

        Assert.assertEquals("滚动前 trigger y", 80, before.getY());
        Assert.assertEquals("滚动后 trigger y 应减少 scrollOffsetY", 30, after.getY());
        Assert.assertEquals("anchor 底边同步上移", before.getBottom() - 50, after.getBottom());
    }

    private PaintCommand findFirstBackground(PaintPlan plan) {
        for (PaintCommand cmd : plan.getCommands()) {
            if (cmd.getType() == PaintCommandType.BACKGROUND) {
                return cmd;
            }
        }
        return null;
    }

    // ==================== 验收 B4：滚轮 handler + signal bind ====================

    /**
     * 滚轮 handler：on(viewport, SCROLL) → 读 wheelDelta → 算新 scrollOffsetY →
     * clamp → 写 signal → flush → setScrollOffsetY 触发 markGeometryDirty。
     *
     * <p>方向语义测试：wheelDelta=-120（向下滚）→ scrollOffsetY 增大（内容上移），
     * wheelDelta=+120（向上滚）→ scrollOffsetY 减小（内容下移）。
     * 注：测试选择「wheelDelta 原始值 = 平台差分×120 未取反」，即向下滚 wheelDelta<0。</p>
     */
    @Test
    public void wheelHandlerShouldUpdateScrollOffsetViaSignal() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);

        doLayout();

        // 创建 scrollOffsetY signal
        Signal<Integer> scrollOffsetSignal = Signal.create(Integer.valueOf(0));

        // bind signal → setScrollOffsetY（geometry 级不需要显式 Invalidation.GEOMETRY，
        // 因为 setScrollOffsetY 内部自己 markGeometryDirty，bind 只负责推值）
        AtomicInteger bindCallCount = new AtomicInteger(0);
        runtime.bind(club.heiqi.uilib.ui.scene.node.Invalidation.COMPOSITE, scrollOffsetSignal, val -> {
            viewport.setScrollOffsetY(val.intValue());
            bindCallCount.incrementAndGet();
        });
        runtime.flush();
        Assert.assertEquals("bind 首次执行应调用一次", 1, bindCallCount.get());

        // 注册滚轮 handler（clamp + 写 signal，方向语义：wheelDelta=-120 向下滚 → offset 增大）
        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
        int maxScroll = Math.max(0, contentBox.getHeight() - viewportBox.getHeight());

        runtime.on(viewport, SceneEventType.SCROLL, (evt, ctx) -> {
            int wheelDelta = evt.getWheelDelta();
            // 方向约定：wheelDelta<0 向下滚 → offset 增大（内容上移）；
            // 每次滚动步长取 wheelDelta 绝对值除以滚动速率因子（此处简化为 1，即 delta 值直接作为偏移增量）
            int currentOffset = scrollOffsetSignal.get().intValue();
            int step = -wheelDelta;  // wheelDelta=-120 → step=120（向下滚增加 offset）
            int newOffset = currentOffset + step;
            int clamped = Math.max(0, Math.min(maxScroll, newOffset));
            scrollOffsetSignal.set(Integer.valueOf(clamped));
        });

        // 模拟向下滚动（wheelDelta=-120）
        routeScroll(viewport, -120);
        runtime.flush();

        Assert.assertEquals("向下滚后 scrollOffsetY 应增大到 120（内容上移）",
                120, viewport.getScrollOffsetY());
        Assert.assertEquals("bind 应被再次调用", 2, bindCallCount.get());

        // 模拟向上滚动（wheelDelta=+120）
        routeScroll(viewport, 120);
        runtime.flush();

        Assert.assertEquals("向上滚后 scrollOffsetY 应减小回 0（内容下移）",
                0, viewport.getScrollOffsetY());
        Assert.assertEquals("bind 应被第三次调用", 3, bindCallCount.get());

        // 模拟超界向下滚：当前 0，maxScroll=400，滚 500 步应 clamp 到 400
        scrollOffsetSignal.set(Integer.valueOf(0));
        runtime.flush();
        routeScroll(viewport, -500);  // wheelDelta=-500 → step=500
        runtime.flush();

        Assert.assertEquals("超界向下滚应 clamp 到 maxScroll=400",
                400, viewport.getScrollOffsetY());
    }

    private void routeScroll(SceneNode target, int wheelDelta) {
        LayoutBox box = (LayoutBox) target.getCachedLayout();
        int centerX = box.getX() + box.getWidth() / 2;
        int centerY = box.getY() + box.getHeight() / 2;

        InputFrameBuilder fb = new InputFrameBuilder(centerX, centerY);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, centerX, centerY,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
