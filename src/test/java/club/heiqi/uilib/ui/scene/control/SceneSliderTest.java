package club.heiqi.uilib.ui.scene.control;

import java.util.concurrent.atomic.AtomicInteger;

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
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneSlider 端到端单元测试 —— Phase 4 批 3 受控连续滑块控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控连续闭环（拖拽 committing=false 预览、释放 committing=true 提交）、
 * draggingValue 瞬态接管 + 松手回落外部 value（R7 受控命门）、值↔像素映射、
 * step 量化、键盘步进（←/→/Home/End/PageUp/PageDown）、disabled 阻断、
 * 命中穿透（点 fill/thumb 装饰子节点穿透到 root）。</p>
 *
 * <h3>测试沙箱 pipeline（对照 SceneToggleTest）</h3>
 * <pre>
 *   signal.set / route → runtime.flush() → layout → paint → 断言
 * </pre>
 *
 * <p><b>时序说明</b>：真机一帧 route 一次 flush；本测试每次 routePointer 后单独 flush，
 * 模拟「跨帧拖拽」场景；同帧场景另见 {@code sameFramePressAndReleaseStillCommits}
 * 与 {@code sameFrameDownMoveUpCommitsLastPosition}（在 SceneSliderPrimitiveTest 中）。</p>
 */
public class SceneSliderTest {

    /** 场景根：slider 作为子节点 mount 到此（route/layout/paint 入口） */
    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    /** slider 的 value 受控源（可写，测试驱动） */
    private Signal<Double> valueSignal;
    /** slider 的 enabled signal（驱动禁用） */
    private Signal<Boolean> enabledSignal;

    /** onChange 触发计数器 */
    private AtomicInteger changeCount;
    /** onChange 最近一次收到的「期望新值」 */
    private double lastChangeValue;
    /** onChange 最近一次收到的 committing 标志 */
    private boolean lastCommitting;
    /** 提交（committing=true）次数计数 */
    private AtomicInteger commitCount;
    /** 预览（committing=false）次数计数 */
    private AtomicInteger previewCount;

    private MountHandle handle;
    /** slider 根节点 */
    private SceneNode sliderRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    // SceneSlider 内部常量镜像（与私有常量保持一致）
    private static final int TRACK_WIDTH = 200;
    private static final int THUMB_SIZE = 16;

    private static final double MIN = 0.0D;
    private static final double MAX = 100.0D;
    private static final double STEP = 5.0D;
    private static final double EPS = 1e-9D;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        valueSignal = Signal.create(0.0D);
        enabledSignal = Signal.create(Boolean.TRUE);
        changeCount = new AtomicInteger(0);
        commitCount = new AtomicInteger(0);
        previewCount = new AtomicInteger(0);
        lastChangeValue = Double.NaN;
        lastCommitting = false;

        SceneSlider.Props props = new SceneSlider.Props(
                valueSignal, enabledSignal, MIN, MAX, STEP,
                (value, committing) -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = value;
                    lastCommitting = committing;
                    if (committing) {
                        commitCount.incrementAndGet();
                    } else {
                        previewCount.incrementAndGet();
                    }
                });
        handle = runtime.mount(sceneRoot, SceneSlider.create(runtime, props));
        sliderRoot = handle.getRoot();

        // 首帧 flush：让所有 bind 的 effect 首次执行
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

    /** track 子节点（root 第一个孩子） */
    private SceneNode trackNode() {
        return sliderRoot.__getChildren().get(0);
    }

    /** fillBox 子节点（track 第一个孩子） */
    private SceneNode fillNode() {
        return trackNode().__getChildren().get(0);
    }

    /** thumb 子节点（track 第二个孩子） */
    private SceneNode thumbNode() {
        return trackNode().__getChildren().get(1);
    }

    private LayoutBox trackBox() {
        return (LayoutBox) trackNode().getCachedLayout();
    }

    private LayoutBox fillBox() {
        return (LayoutBox) fillNode().getCachedLayout();
    }

    private LayoutBox thumbBox() {
        return (LayoutBox) thumbNode().getCachedLayout();
    }

    /** 构造单指针事件帧并 route 到 sceneRoot（rootAbs=0,0） */
    private void routePointer(ScenePointerAction action, int x, int y) {
        routePointer(action, x, y, 0, 0);
    }

    /** 构造单指针事件帧并 route 到 sceneRoot（可指定 rootAbs，验证 I12 三层坐标） */
    private void routePointer(ScenePointerAction action, int x, int y, int rootAbsX, int rootAbsY) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, rootAbsX, rootAbsY);
    }

    /** 构造单键盘事件帧并 route 到 sceneRoot */
    private void routeKey(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    /** track 绝对左缘 x（rootAbs=0 时即 trackBox.x，因 root 局部 x 通常 0） */
    private int trackLeftX() {
        // root 在 sceneRoot 内的局部 x + track 在 root 内的局部 x（累加到场景根）
        return sliderRootAbsX() + trackBox().getX();
    }

    /** slider root 相对场景根的绝对 x（累加各级 LayoutBox.x） */
    private int sliderRootAbsX() {
        int x = 0;
        SceneNode cur = sliderRoot;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                x += ((LayoutBox) cached).getX();
            }
            cur = cur.__getParent();
        }
        return x;
    }

    // ==================== 验收 1：受控连续闭环（拖拽预览 committing=false、释放提交 committing=true） ====================

    /**
     * 受控连续核心：DOWN 命中 track 中点 → onChange 预览（committing=false）收到约 50；
     * MOVE 到 track 3/4 处 → 预览收到约 75；UP → 提交（committing=true）收到末值约 75。
     * 全程 onChange 收到期望值，且预览/提交 committing 语义正确。
     */
    @Test
    public void controlledDragShouldPreviewThenCommit() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // DOWN 命中 track 中点（ratio=0.5 → value≈50，step=5 量化后正好 50）
        int midX = left + TRACK_WIDTH / 2;
        routePointer(ScenePointerAction.BUTTON_DOWN, midX, cy);
        runtime.flush();
        Assert.assertEquals("DOWN 应触发一次预览", 1, previewCount.get());
        Assert.assertFalse("DOWN 是预览 committing=false", lastCommitting);
        Assert.assertEquals("DOWN 命中中点 value≈50", 50.0D, lastChangeValue, EPS);

        // MOVE 到 3/4 处（ratio=0.75 → value=75）
        int q3X = left + TRACK_WIDTH * 3 / 4;
        routePointer(ScenePointerAction.MOVE, q3X, cy);
        runtime.flush();
        Assert.assertEquals("MOVE 再触发一次预览", 2, previewCount.get());
        Assert.assertFalse("MOVE 是预览 committing=false", lastCommitting);
        Assert.assertEquals("MOVE 到 3/4 value=75", 75.0D, lastChangeValue, EPS);

        // UP 提交
        routePointer(ScenePointerAction.BUTTON_UP, q3X, cy);
        runtime.flush();
        Assert.assertEquals("UP 应触发一次提交", 1, commitCount.get());
        Assert.assertTrue("UP 是提交 committing=true", lastCommitting);
        Assert.assertEquals("UP 提交末值 75", 75.0D, lastChangeValue, EPS);
    }

    // ==================== 验收 2：draggingValue 瞬态 + 松手回落外部 value（R7 受控命门） ====================

    /**
     * R7 受控命门：拖拽期 effectiveValue 跟 draggingValue（thumb 位置随拖拽移动，
     * <b>即使外部 value 始终为 0 不回写</b>）；松手清 null 后 effectiveValue 回落外部 value=0
     * （thumb 回到最左）。这证明控件零内部受控状态——拖拽态是瞬态接管，提交后归还外部唯一源。
     */
    @Test
    public void draggingValueIsTransientAndFallsBackToExternalValueOnRelease() {
        doLayout();
        // 外部 value 固定为 0：测试全程绝不 set 回 valueSignal，模拟「外部拒绝回写」
        Assert.assertEquals("初始外部 value=0", 0.0D, valueSignal.get(), EPS);
        int thumbX0 = thumbBox().getX();

        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // DOWN 命中 track 内最右点（left+W-1，左闭右开命中区 [left,left+W) 的最后一个可命中像素，
        // ratio≈0.995 → raw 99.5 → step=5 量化到 100）→ draggingValue 接管
        int rightX = left + TRACK_WIDTH - 1;
        routePointer(ScenePointerAction.BUTTON_DOWN, rightX, cy);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int thumbXDragging = thumbBox().getX();

        // 拖拽期：thumb 跟 draggingValue 显著右移（即使外部 value 仍为 0）
        Assert.assertEquals("拖拽期外部 value 未被回写仍为 0", 0.0D, valueSignal.get(), EPS);
        Assert.assertTrue("拖拽期 thumb 跟 draggingValue 右移（effectiveValue=draggingValue）",
                thumbXDragging > thumbX0);

        // UP 释放：提交后 draggingValue 清 null（经 flush），effectiveValue 回落外部 value=0
        routePointer(ScenePointerAction.BUTTON_UP, rightX, cy);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int thumbXReleased = thumbBox().getX();

        Assert.assertTrue("UP 提交 committing=true", lastCommitting);
        Assert.assertEquals("松手后 thumb 回落到外部 value=0 的位置（瞬态接管已归还外部）",
                thumbX0, thumbXReleased);
    }

    // ==================== 验收 3：值↔像素映射正确 ====================

    /**
     * 值↔像素映射：给定 track 宽 200、min=0/max=100、step=5，
     * 命中 localX=50（ratio=0.25）→ value=25；localX=100（ratio=0.5）→ value=50；
     * 命中 track 内最右可命中像素（localX=199，ratio≈0.995）→ 量化 clamp 到 max=100；
     * 命中 track 左缘（localX=0，ratio=0）→ min=0。
     *
     * <p><b>边界说明</b>：root 宽 = track preferredWidth = 200（ROW shrink-to-fit），命中区为
     * 左闭右开 {@code [left, left+200)}，故 track 外坐标（负 localX / 超右）端到端命中不到 root，
     * 无法经真实命中触发。值映射函数内的 ratio clamp 对越界 localX 仍正确（纯函数防御），
     * 此处用 track 内两端（localX=0 与 199）验证上下界量化结果可达且正确。</p>
     */
    @Test
    public void valueFromPointerXMapsCorrectly() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // localX=50 → ratio=0.25 → 25
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 50, cy);
        runtime.flush();
        Assert.assertEquals("localX=50 → value=25", 25.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + 50, cy);
        runtime.flush();

        // localX=100 → ratio=0.5 → 50
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 100, cy);
        runtime.flush();
        Assert.assertEquals("localX=100 → value=50", 50.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + 100, cy);
        runtime.flush();

        // track 内最右可命中像素 localX=199 → ratio≈0.995 → raw 99.5 → 量化 clamp max=100
        routePointer(ScenePointerAction.BUTTON_DOWN, left + TRACK_WIDTH - 1, cy);
        runtime.flush();
        Assert.assertEquals("track 内最右像素 → 量化 clamp max=100", 100.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + TRACK_WIDTH - 1, cy);
        runtime.flush();

        // track 左缘 localX=0 → ratio=0 → min=0
        routePointer(ScenePointerAction.BUTTON_DOWN, left, cy);
        runtime.flush();
        Assert.assertEquals("track 左缘 localX=0 → min=0", 0.0D, lastChangeValue, EPS);
    }

    // ==================== 验收 4：step 量化正确 ====================

    /**
     * step 量化：step=5 时，命中 localX=46（ratio=0.23 → raw=23）量化到最近的 5 的倍数 25；
     * localX=44（ratio=0.22 → raw=22）量化到 20。证明 min + round((v-min)/step)*step。
     */
    @Test
    public void stepQuantizationRoundsToNearestStep() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // localX=46 → ratio=0.23 → raw=23 → round(23/5)*5 = round(4.6)*5 = 25
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 46, cy);
        runtime.flush();
        Assert.assertEquals("raw=23 量化到 25", 25.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + 46, cy);
        runtime.flush();

        // localX=44 → ratio=0.22 → raw=22 → round(22/5)*5 = round(4.4)*5 = 20
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 44, cy);
        runtime.flush();
        Assert.assertEquals("raw=22 量化到 20", 20.0D, lastChangeValue, EPS);
    }

    // ==================== 验收 5：键盘步进各方向算出正确 newV 且 committing=true ====================

    /**
     * 键盘步进：从外部 value=50 起，→/↑ 加 step=5 到 55；←/↓ 减到 45；
     * PageUp 加 10×step=50 到 100（clamp）；PageDown 减 50 到 0（clamp）；Home→min=0；End→max=100。
     * 每次均 committing=true（键盘离散提交），且读外部 value.get() 算相邻值（受控）。
     */
    @Test
    public void keyboardStepComputesCorrectValueWithCommitting() {
        doLayout();
        // B2：focusable 挂 track（primitive 已改），requestFocus 传 track = sliderRoot 第一个子
        runtime.requestFocus(sliderRoot.__getChildren().get(0));
        valueSignal.set(50.0D);
        runtime.flush();

        // → 加 step=5 → 55
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("→ 加 step 到 55", 55.0D, lastChangeValue, EPS);
        Assert.assertTrue("键盘步进 committing=true", lastCommitting);

        // ↑ 同 → 加（外部 value 仍 50，未回写）→ 55
        routeKey(SceneKey.ARROW_UP, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("↑ 同 → 加到 55", 55.0D, lastChangeValue, EPS);

        // ← 减 step → 45
        routeKey(SceneKey.ARROW_LEFT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("← 减 step 到 45", 45.0D, lastChangeValue, EPS);

        // ↓ 同 ← 减 → 45
        routeKey(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("↓ 同 ← 减到 45", 45.0D, lastChangeValue, EPS);

        // PageUp 加 10×step=50 → 100（clamp max）
        routeKey(SceneKey.PAGE_UP, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("PageUp 加 50 → clamp 100", 100.0D, lastChangeValue, EPS);

        // PageDown 减 50 → 0（clamp min）
        routeKey(SceneKey.PAGE_DOWN, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("PageDown 减 50 → clamp 0", 0.0D, lastChangeValue, EPS);

        // Home → min=0
        routeKey(SceneKey.HOME, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Home → min=0", 0.0D, lastChangeValue, EPS);

        // End → max=100
        routeKey(SceneKey.END, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("End → max=100", 100.0D, lastChangeValue, EPS);

        // 全程键盘步进皆 committing=true
        Assert.assertEquals("键盘步进无任何预览（全提交）", 0, previewCount.get());
    }

    // ==================== 验收 6：disabled 阻断拖拽和键盘 ====================

    /**
     * disabled 态：拖拽（DOWN）与键盘（→）均不触发 onChange。
     */
    @Test
    public void disabledBlocksDragAndKeyboard() {
        doLayout();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();

        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int before = changeCount.get();

        // disabled 拖拽不触发
        routePointer(ScenePointerAction.BUTTON_DOWN, left + TRACK_WIDTH / 2, cy);
        runtime.flush();
        routePointer(ScenePointerAction.BUTTON_UP, left + TRACK_WIDTH / 2, cy);
        runtime.flush();
        Assert.assertEquals("disabled 态拖拽不触发 onChange", before, changeCount.get());

        // disabled 键盘不触发
        // B2：focusable 挂 track（primitive 已改），requestFocus 传 track = sliderRoot 第一个子
        runtime.requestFocus(sliderRoot.__getChildren().get(0));
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 态键盘不触发 onChange", before, changeCount.get());
    }

    // ==================== 验收 7：命中穿透（点 fill/thumb 装饰子节点穿透到 root） ====================

    /**
     * 命中穿透：点 thumb 装饰子节点几何中心，最深命中穿透到 root（交互单元），
     * DOWN 触发拖拽预览 onChange（证明命中落到了 root 的 POINTER_DOWN handler）。
     */
    @Test
    public void hitTestPassesThroughDecorativeThumbToRoot() {
        // 先把外部 value 设到中段，使 thumb 不在最左、命中点稳定落在 track 内
        valueSignal.set(50.0D);
        runtime.flush();
        doLayout();

        LayoutBox thumb = thumbBox();
        int rootAbsX = sliderRootAbsX();
        // thumb 在 track 内，track 在 root 内：thumb 绝对 x = rootAbsX + trackBox.x + thumbBox.x
        int thumbAbsX = rootAbsX + trackBox().getX() + thumb.getX();
        int cx = thumbAbsX + thumb.getWidth() / 2;
        int cy = sliderRootAbsY() + trackBox().getY() + thumb.getY() + thumb.getHeight() / 2;

        int before = changeCount.get();
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        Assert.assertTrue("点 thumb 穿透到 root → 触发 root 的 POINTER_DOWN 拖拽预览",
                changeCount.get() > before);
        Assert.assertFalse("DOWN 是预览 committing=false", lastCommitting);
    }

    /**
     * 命中穿透补充：点 fill 装饰子节点同样穿透到 root。
     */
    @Test
    public void hitTestPassesThroughDecorativeFillToRoot() {
        valueSignal.set(50.0D);
        runtime.flush();
        doLayout();

        LayoutBox fill = fillBox();
        // fill 可能宽度为 0（value 小）时不可命中；value=50 时 fill 宽 = round(200*0.5)-8 = 92 > 0
        Assert.assertTrue("value=50 时 fill 应有正宽度可命中", fill.getWidth() > 0);
        int rootAbsX = sliderRootAbsX();
        int fillAbsX = rootAbsX + trackBox().getX() + fill.getX();
        int cx = fillAbsX + fill.getWidth() / 2;
        int cy = sliderRootAbsY() + trackBox().getY() + fill.getY() + fill.getHeight() / 2;

        int before = changeCount.get();
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        Assert.assertTrue("点 fill 穿透到 root → 触发 root 的 POINTER_DOWN",
                changeCount.get() > before);
    }

    /** slider root 相对场景根的绝对 y（累加各级 LayoutBox.y） */
    private int sliderRootAbsY() {
        int y = 0;
        SceneNode cur = sliderRoot;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                y += ((LayoutBox) cached).getY();
            }
            cur = cur.__getParent();
        }
        return y;
    }

    // ==================== 验收 8：POINTER_CANCEL 取消不提交 ====================

    /**
     * POINTER_CANCEL：拖拽中途取消，不产生提交（committing=true），draggingValue 清 null 回落外部 value。
     */
    @Test
    public void cancelDoesNotCommitAndFallsBack() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int thumbX0 = thumbBox().getX();

        // DOWN 接管拖拽（外部 value 不回写）；用 track 内最右可命中像素 left+W-1
        // （修复 A 后 root 宽=TRACK_WIDTH，命中区左闭右开 [left,left+W)，left+W 已越界命中落空）
        int rightX = left + TRACK_WIDTH - 1;
        routePointer(ScenePointerAction.BUTTON_DOWN, rightX, cy);
        runtime.flush();
        int commitsBefore = commitCount.get();
        Assert.assertTrue("DOWN 应已启动拖拽（changeCount 增长），否则后续断言为假绿",
                changeCount.get() > 0);

        // CANCEL 取消
        routePointer(ScenePointerAction.CANCEL, rightX, cy);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        Assert.assertEquals("CANCEL 不产生新的提交", commitsBefore, commitCount.get());
        Assert.assertEquals("CANCEL 后 thumb 回落外部 value=0 位置（瞬态清空）",
                thumbX0, thumbBox().getX());
    }

    // ==================== 验收 9：缺陷 A 回归——root 命中区=视觉区（收窄到 track 宽） ====================

    /**
     * 缺陷 A 回归①：点 track 右边界之外的「右侧空白」不应启动拖拽。
     *
     * <p>修复前 root 宽被容器 fill 语义拉满（测试沙箱可用宽 400），track 仅 200 宽 START 左对齐，
     * root 右侧 [200,400) 空白仍是命中区——点 trackLeftX+300（远超 track 右界 200）会命中 root、
     * 触发 POINTER_DOWN，valueFromPointerX 算 localX=300>200 → ratio clamp 1 → 值跳 max，
     * 即「点滑块右边空白滑块直接跳满」。
     * 修复后 root 设 preferredWidth=TRACK_WIDTH 收窄到 200，命中区左闭右开 [left,left+200)，
     * trackLeftX+300 已不在 root 命中区内 → 命中落空 → onChange 不触发（changeCount 不变）。</p>
     *
     * <p>该坐标 left+300 是「修复前必跳 max、修复后必不触发」的判别点，真实证伪缺陷 A。</p>
     */
    @Test
    public void pointerDownRightOfTrackDoesNotJumpToMax() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // 前置自检：沙箱可用宽 400 > track 200，确保「右侧空白坐标」在修复前确属 root 拉满区
        Assert.assertTrue("沙箱可用宽应大于 track 宽，才能暴露右侧空白命中带",
                CANVAS_WIDTH > TRACK_WIDTH);

        int before = changeCount.get();
        // 远超 track 右界（left+200）的右侧空白坐标：修复前命中 root 跳 max，修复后命中落空
        int rightBlankX = left + TRACK_WIDTH + 100;
        routePointer(ScenePointerAction.BUTTON_DOWN, rightBlankX, cy);
        runtime.flush();

        Assert.assertEquals("点 track 右侧空白不应启动拖拽（命中区=视觉区，onChange 不触发）",
                before, changeCount.get());
    }

    /**
     * 缺陷 A 回归②：slider root 的命中宽 == TRACK_WIDTH(200)，锚住「命中单元宽=视觉宽」不变量。
     *
     * <p>本测试在 COLUMN + STRETCH 父容器下挂载 slider（模拟 demo 的 host 环境：cross 轴=宽，
     * STRETCH 会把未设 cross 向 preferred 的子节点拉满父宽）。断言 root 仍稳定保持 200，
     * 真实证伪「root 被 STRETCH 拉宽」——即验证 root 的 preferredWidth 在 STRETCH 分支被豁免、
     * 收窄生效。若后续 agent 删掉 root.setPreferredWidth，本断言会因 root 被拉满父宽而失败。</p>
     */
    @Test
    public void rootHitWidthEqualsTrackWidth() {
        // 用独立的 STRETCH 父容器模拟 demo host：COLUMN + 交叉轴 STRETCH（默认即 STRETCH，显式声明以表意图）
        SceneNode stretchHost = new SceneNode();
        stretchHost.setFlexDirection(FlexDirection.COLUMN);
        stretchHost.setCrossAxisAlign(CrossAxisAlign.STRETCH);

        Signal<Double> v = Signal.create(40.0D);
        Signal<Boolean> en = Signal.create(Boolean.TRUE);
        SceneSlider.Props p = new SceneSlider.Props(v, en, MIN, MAX, STEP,
                (value, committing) -> { /* 本测试只看布局，不关心回调 */ });
        MountHandle h = runtime.mount(stretchHost, SceneSlider.create(runtime, p));
        SceneNode stretchRoot = h.getRoot();
        runtime.flush();

        // 父容器给 400 宽（远大于 track 200），STRETCH 下若未豁免 root 会被拉到 400
        layoutEngine.layout(stretchHost, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        LayoutBox rootBox = (LayoutBox) stretchRoot.getCachedLayout();
        Assert.assertNotNull("slider root 应有布局缓存", rootBox);
        Assert.assertEquals("命中单元 root 宽必须=track 视觉宽 200（STRETCH 下 preferredWidth 豁免生效，未被拉宽）",
                TRACK_WIDTH, rootBox.getWidth());

        h.dispose();
    }

    // ==================== 验收 10：Builder 构建等价 canonical 构造器 ====================

    /**
     * Builder.build() 构建的 Props 与 canonical 构造器构建的 Props 各字段等价。
     *
     * <p>显式设置全部字段后，Builder 与 canonical 传入相同引用/值，
     * 逐 getter 断言一致，并验证 record equals 成立。</p>
     */
    @Test
    public void builderShouldMatchCanonicalProps() {
        Signal<Double> value = Signal.create(50.0D);
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        SceneSliderPrimitive.SliderChange onChange = (v, c) -> { };
        SceneSlider.Props fromBuilder = SceneSlider.Props.builder(value)
                .enabled(enabled).min(0.0D).max(100.0D).step(5.0D).onChange(onChange)
                .build();
        SceneSlider.Props fromCanonical = new SceneSlider.Props(
                value, enabled, 0.0D, 100.0D, 5.0D, onChange);

        Assert.assertSame("value 引用一致", value, fromBuilder.value());
        Assert.assertSame("enabled 引用一致", enabled, fromBuilder.enabled());
        Assert.assertEquals("min 一致", fromCanonical.min(), fromBuilder.min(), EPS);
        Assert.assertEquals("max 一致", fromCanonical.max(), fromBuilder.max(), EPS);
        Assert.assertEquals("step 一致", fromCanonical.step(), fromBuilder.step(), EPS);
        Assert.assertSame("onChange 引用一致", onChange, fromBuilder.onChange());
        Assert.assertEquals("Builder 与 canonical Props 应 record equals 等价", fromCanonical, fromBuilder);
    }

    // ==================== 验收 11：NaN/Infinity 防御——fillWidth 有限 ====================

    /**
     * NaN/Infinity 防御端到端：value=NaN 或 Infinity 时 progress=0，fillBox 宽度有限（FILL_MIN_WIDTH=1），
     * 布局不崩溃、不溢出。
     */
    @Test
    public void nonFiniteValueFallsBackToMinFillWidthFinite() {
        doLayout();

        // NaN → progress=0 → fill 宽 = FILL_MIN_WIDTH=1
        valueSignal.set(Double.NaN);
        runtime.flush();
        doLayout();
        LayoutBox fillBoxNaNDirect = ((LayoutBox) sliderRoot.__getChildren().get(0).__getChildren().get(0).getCachedLayout());
        Assert.assertTrue("NaN value → fill 宽有限且 >0",
                fillBoxNaNDirect.getWidth() > 0 && fillBoxNaNDirect.getWidth() <= TRACK_WIDTH);

        // +Infinity → progress=0 → fill 宽有限
        valueSignal.set(Double.POSITIVE_INFINITY);
        runtime.flush();
        doLayout();
        LayoutBox fillBox = fillBox();
        Assert.assertTrue("+Infinity 时 fill 宽有限且 >0", fillBox.getWidth() > 0 && fillBox.getWidth() <= TRACK_WIDTH);

        // -Infinity → progress=0 → fill 宽有限
        valueSignal.set(Double.NEGATIVE_INFINITY);
        runtime.flush();
        doLayout();
        fillBox = fillBox();
        Assert.assertTrue("-Infinity 时 fill 宽有限且 >0", fillBox.getWidth() > 0 && fillBox.getWidth() <= TRACK_WIDTH);

        // 恢复正常值后 fill 宽恢复
        valueSignal.set(50.0D);
        runtime.flush();
        doLayout();
        fillBox = fillBox();
        Assert.assertTrue("恢复 value=50 后 fill 宽 >0", fillBox.getWidth() > 0);
    }

    // ==================== 验收 12：I12 rootAbs≠0 时拖拽定位不偏移 ====================

    /**
     * I12 坐标系对齐：rootAbsX/Y≠0 时，slider 拖拽定位 value 仍正确（不因 raw 含 rootAbs 而错位）。
     *
     * <p>修复前 slider 用 ev.getPointerX()（raw，含 rootAbs）与 absoluteBox(track,0,0)（host 局部，不含 rootAbs）
     * 混比，rootAbs≠0 时 localX 多算一个 rootAbs，value 偏移。修复后用 ctx.getLocalPointerX()（两层坐标，
     * = raw - absoluteBox(track,treeAbs) = track 真局部），rootAbs≠0 不再错位。</p>
     *
     * <p>本测试在 rootAbs=(80,60) 下点击 track 中点，断言 value≈50（与 rootAbs=0 时一致），
     * 真实证伪 raw↔host 混比缺陷。</p>
     */
    @Test
    public void dragPositionCorrectWithNonZeroRootAbs() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int rootAbsX = 80;
        int rootAbsY = 60;

        // 点击 track 中点：屏幕坐标 = left + TRACK_WIDTH/2（left 已含 slider 在 sceneRoot 内的累加偏移）
        // rootAbs≠0 时，指针屏幕绝对坐标需再加 rootAbs 才能命中（hitTester 内部 nodeAbs 含 rootAbs）
        int midX = left + TRACK_WIDTH / 2 + rootAbsX;
        int midY = cy + rootAbsY;

        routePointer(ScenePointerAction.BUTTON_DOWN, midX, midY, rootAbsX, rootAbsY);
        runtime.flush();
        Assert.assertEquals("rootAbs≠0 时点击 track 中点 value 仍为 50（host 同系，不错位）",
                50.0D, lastChangeValue, EPS);
        Assert.assertFalse("DOWN 是预览 committing=false", lastCommitting);

        // MOVE 到 3/4 处
        int q3X = left + TRACK_WIDTH * 3 / 4 + rootAbsX;
        routePointer(ScenePointerAction.MOVE, q3X, midY, rootAbsX, rootAbsY);
        runtime.flush();
        Assert.assertEquals("rootAbs≠0 时 MOVE 到 3/4 value 仍为 75",
                75.0D, lastChangeValue, EPS);

        // UP 提交
        routePointer(ScenePointerAction.BUTTON_UP, q3X, midY, rootAbsX, rootAbsY);
        runtime.flush();
        Assert.assertEquals("rootAbs≠0 时 UP 提交末值 75", 75.0D, lastChangeValue, EPS);
        Assert.assertTrue("UP 是提交 committing=true", lastCommitting);
    }
}
