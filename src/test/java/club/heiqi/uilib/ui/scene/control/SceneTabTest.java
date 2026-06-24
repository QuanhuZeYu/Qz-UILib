package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneTab 端到端单元测试 —— Phase 4 批 4 标签页控件（R8 受控头 + R10 内容区 show 切换）验收。
 *
 * <p>端到端验证：tabBar 受控闭环（点 tab 只上抛期望页下标、控件零状态不自改）、
 * 内容区 N 选 1（仅活动页内容挂载、切页卸旧挂新）、show I7 不重建（活动页保持不变重复 flush 不重建）、
 * R6 段穿透（点 tab 内 label 文字穿透到段）、键盘导航（←/→/Home/End/Enter/Space）、
 * disabled 拦截、tabBar 四态切换零重排。</p>
 */
public class SceneTabTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

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

    // SceneTab chrome token 镜像
    private static final int TAB_INACTIVE_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int TAB_INACTIVE_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int TAB_ACTIVE_ENABLED = SceneChromeTokens.ACCENT;
    private static final int TAB_DISABLED = SceneChromeTokens.BG_DISABLED;

    // 各页内容 panel 的标识背景色（用于断言挂载的是哪一页）
    private static final int PANEL_BG_0 = 0xFF111111;
    private static final int PANEL_BG_1 = 0xFF222222;
    private static final int PANEL_BG_2 = 0xFF333333;
    private static final int[] PANEL_BG = { PANEL_BG_0, PANEL_BG_1, PANEL_BG_2 };

    private static final List<String> LABELS = Arrays.asList("常规", "外观", "高级");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        paintEngine = new ScenePaintEngine();
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
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
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

    /**
     * 收集 contentPanel 下当前已挂载的内容 panel（按背景色匹配 PANEL_BG）。
     *
     * <p>contentPanel 子节点含 N 个 show 的零尺寸 anchor（无背景色，默认 0）+ 至多一个活动内容 panel
     * （带标识背景色）。本方法返回匹配到的内容页下标列表。</p>
     *
     * @return 当前挂载的内容页下标列表（顺序按 contentPanel 子节点顺序）
     */
    private List<Integer> mountedPanelIndices() {
        List<Integer> result = new ArrayList<>();
        for (SceneNode child : contentPanel().__getChildren()) {
            int bg = child.getBackgroundColor();
            for (int i = 0; i < PANEL_BG.length; i++) {
                if (bg == PANEL_BG[i]) {
                    result.add(Integer.valueOf(i));
                }
            }
        }
        return result;
    }

    private int[] absCenter(SceneNode n) {
        LayoutBox b = (LayoutBox) n.getCachedLayout();
        int ax = b.getX();
        int ay = b.getY();
        SceneNode p = n.__getParent();
        while (p != null) {
            LayoutBox pb = (LayoutBox) p.getCachedLayout();
            if (pb != null) {
                ax += pb.getX();
                ay += pb.getY();
            }
            p = p.__getParent();
        }
        return new int[] { ax + b.getWidth() / 2, ay + b.getHeight() / 2 };
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    private void routeKey(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    private void clickCenter(SceneNode n) {
        int[] c = absCenter(n);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
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

        clickCenter(tabSeg(1));
        runtime.flush();

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
        int[] c = absCenter(labelNode(1));
        int cx = c[0];
        int cy = c[1];

        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 tabSeg[1] → pressed 背景",
                TAB_INACTIVE_PRESSED, tabBackground(1));

        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
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
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("→ 上抛相邻下标 1", Integer.valueOf(1), lastActivateValue);
        Assert.assertSame("→ 焦点移到 tabSeg[1]", tabSeg(1), runtime.getFocusedNode());

        // End → 末页 2（从 1，回写后）
        activeSignal.set(Integer.valueOf(1));
        runtime.flush();
        routeKey(SceneKey.END, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("End 上抛末页 2", Integer.valueOf(2), lastActivateValue);
        Assert.assertSame("End 焦点移到 tabSeg[2]", tabSeg(2), runtime.getFocusedNode());

        // Home → 首页 0（从 2，回写后）
        activeSignal.set(Integer.valueOf(2));
        runtime.flush();
        routeKey(SceneKey.HOME, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Home 上抛首页 0", Integer.valueOf(0), lastActivateValue);
        Assert.assertSame("Home 焦点移到 tabSeg[0]", tabSeg(0), runtime.getFocusedNode());

        // ← 边界裁剪：cur=0 再 ← 仍 0
        activeSignal.set(Integer.valueOf(0));
        runtime.flush();
        routeKey(SceneKey.ARROW_LEFT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("← 首段边界裁剪仍 0", Integer.valueOf(0), lastActivateValue);

        // Enter 激活当前段（焦点在 tabSeg[0]）
        runtime.requestFocus(tabSeg(0));
        int before = activateCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Enter 应触发一次 onActivate", before + 1, activateCount.get());
        Assert.assertEquals("Enter 激活当前段 0", Integer.valueOf(0), lastActivateValue);

        // Space 激活当前段
        before = activateCount.get();
        routeKey(SceneKey.SPACE, SceneKeyAction.PRESSED);
        runtime.flush();
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
        clickCenter(tabSeg(1));
        runtime.flush();
        Assert.assertEquals("disabled 态 CLICK 不触发", before, activateCount.get());

        runtime.requestFocus(tabSeg(1));
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 态 Enter 不触发", before, activateCount.get());
    }

    // ==================== 验收 7：tabBar 四态切换零重排（PAINT 级） ====================

    /**
     * tabBar 选中态四态切换应是纯 PAINT 级零重排（照 SceneSegmented 断言）：
     * enabled↔disabled、pressed、外部 set 活动切换都不触发重排。
     */
    @Test
    public void tabBarStateSwitchShouldOnlyPaintNotLayout() {
        doLayout();
        Assert.assertEquals("初始 tabSeg[1] 非活动背景", TAB_INACTIVE_ENABLED, tabBackground(1));

        // ① enabled → disabled
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("disabled tabSeg[1] 背景", TAB_DISABLED, tabBackground(1));
        Assert.assertEquals("enabled→disabled 零重排", 0, layoutEngine.__getRelayoutCount());

        // ② disabled → enabled
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("回 enabled tabSeg[1] 背景", TAB_INACTIVE_ENABLED, tabBackground(1));
        Assert.assertEquals("disabled→enabled 零重排", 0, layoutEngine.__getRelayoutCount());

        // ③ pressed：route 真实 POINTER_DOWN 命中 tabSeg[1] 几何中心
        doLayout();
        int[] sc = absCenter(tabSeg(1));
        int cx = sc[0];
        int cy = sc[1];
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        doLayout();
        Assert.assertEquals("pressed tabSeg[1] 背景", TAB_INACTIVE_PRESSED, tabBackground(1));
        Assert.assertEquals("pressed 零重排", 0, layoutEngine.__getRelayoutCount());

        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        doLayout();
        Assert.assertEquals("释放后回默认背景", TAB_INACTIVE_ENABLED, tabBackground(1));
        Assert.assertEquals("释放 pressed 零重排", 0, layoutEngine.__getRelayoutCount());
    }
}
