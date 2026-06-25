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
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSliderPrimitive 独立单元测试 —— 验证无样式连续值滑块行为核心契约。
 *
 * <p>primitive 不挂任何 chrome，只验证行为：
 * value 范围/步进（max<=min 静默退化、step<=0 连续模式）、
 * draggingValue 瞬态接管 + 松手回落、progress 派生、键盘步进、点击定位。</p>
 *
 * <p>注：primitive 不设置 root/track preferredWidth（属 wrapper chrome 职责），
 * 本测试在 mount 后显式设置 track preferredWidth=200 以提供确定的值↔像素映射分母，
 * 仅模拟 wrapper 提供的尺寸约束，测的是 primitive 的行为契约。</p>
 */
public class SceneSliderPrimitiveTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private Signal<Double> valueSignal;
    private Signal<Boolean> enabledSignal;

    private AtomicInteger changeCount;
    private AtomicInteger commitCount;
    private AtomicInteger previewCount;
    private double lastChangeValue;
    private boolean lastCommitting;

    private MountHandle handle;
    private SceneSliderPrimitive.Result result;
    private SceneNode sliderRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int TRACK_WIDTH = 200;

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
        sceneRoot = new SceneNode();

        valueSignal = Signal.create(0.0D);
        enabledSignal = Signal.create(Boolean.TRUE);
        changeCount = new AtomicInteger(0);
        commitCount = new AtomicInteger(0);
        previewCount = new AtomicInteger(0);
        lastChangeValue = Double.NaN;
        lastCommitting = false;

        SceneSliderPrimitive.Props props = new SceneSliderPrimitive.Props(
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
        final SceneSliderPrimitive.Result[] holder = new SceneSliderPrimitive.Result[1];
        handle = runtime.mount(sceneRoot, () -> {
            holder[0] = SceneSliderPrimitive.create(runtime, props);
            return holder[0].root();
        });
        result = holder[0];
        sliderRoot = result.root();

        // 模拟 wrapper 提供的尺寸约束（primitive 不设 chrome，测试提供确定尺寸以验证值↔像素映射 + 命中区）
        sliderRoot.setPreferredWidth(TRACK_WIDTH);
        result.track().setPreferredWidth(TRACK_WIDTH);
        // thumb 尺寸撑起 track 高度，让 root 有正高度可命中（布局尺寸非 chrome）
        result.thumb().setPreferredWidth(16);
        result.thumb().setPreferredHeight(16);

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

    private SceneNode trackNode() {
        return result.track();
    }

    private LayoutBox trackBox() {
        return (LayoutBox) trackNode().getCachedLayout();
    }

    private LayoutBox thumbBox() {
        return (LayoutBox) result.thumb().getCachedLayout();
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

    private int trackLeftX() {
        return sliderRootAbsX() + trackBox().getX();
    }

    /** 计算节点相对场景根的绝对 x（累加各级 LayoutBox.x） */
    private static int absXOf(SceneNode node) {
        int x = 0;
        SceneNode cur = node;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                x += ((LayoutBox) cached).getX();
            }
            cur = cur.__getParent();
        }
        return x;
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    // ==================== 契约 1：progress 派生 ====================

    /** progress = clamp((value-min)/(max-min), 0, 1)；越界 clamp；max<=min 时返回 0。 */
    @Test
    public void progressDerivesFromValue() {
        runtime.flush();
        Assert.assertEquals("value=0 → progress=0", 0.0D, result.progress().get(), EPS);

        valueSignal.set(50.0D);
        runtime.flush();
        Assert.assertEquals("value=50 → progress=0.5", 0.5D, result.progress().get(), EPS);

        valueSignal.set(100.0D);
        runtime.flush();
        Assert.assertEquals("value=100 → progress=1.0", 1.0D, result.progress().get(), EPS);

        // 越界 clamp
        valueSignal.set(-10.0D);
        runtime.flush();
        Assert.assertEquals("value=-10 → progress clamp 0", 0.0D, result.progress().get(), EPS);

        valueSignal.set(110.0D);
        runtime.flush();
        Assert.assertEquals("value=110 → progress clamp 1.0", 1.0D, result.progress().get(), EPS);

        // null value → 兜底 min → progress=0
        valueSignal.set(null);
        runtime.flush();
        Assert.assertEquals("null value → 兜底 min → progress=0", 0.0D, result.progress().get(), EPS);
    }

    // ==================== 契约 2：draggingValue 瞬态接管 + 松手回落 ====================

    /** DOWN 启动拖拽（预览 committing=false）；拖拽期 progress 跟 draggingValue（外部 value 不回写仍 0）；
     *  UP 提交（committing=true）后 draggingValue 清 null，progress 回落外部 value。 */
    @Test
    public void draggingValueIsTransientAndFallsBackOnRelease() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int thumbX0 = thumbBox().getX();

        // DOWN 命中 track 中点 → 预览 50（committing=false）
        int midX = left + TRACK_WIDTH / 2;
        routePointer(ScenePointerAction.BUTTON_DOWN, midX, cy);
        runtime.flush();
        Assert.assertEquals("DOWN 预览一次", 1, previewCount.get());
        Assert.assertFalse("DOWN committing=false", lastCommitting);
        Assert.assertEquals("DOWN 命中中点 value=50", 50.0D, lastChangeValue, EPS);

        // 拖拽期：外部 value 仍 0（未回写），progress 跟 draggingValue=50 → 0.5
        Assert.assertEquals("拖拽期外部 value 未回写仍 0", 0.0D, valueSignal.get(), EPS);
        Assert.assertEquals("拖拽期 progress 跟 draggingValue=0.5", 0.5D, result.progress().get(), EPS);

        // MOVE 到 3/4 → 预览 75
        int q3X = left + TRACK_WIDTH * 3 / 4;
        routePointer(ScenePointerAction.MOVE, q3X, cy);
        runtime.flush();
        Assert.assertEquals("MOVE 预览 75", 75.0D, lastChangeValue, EPS);
        Assert.assertFalse("MOVE committing=false", lastCommitting);

        // UP 提交 75（committing=true）
        routePointer(ScenePointerAction.BUTTON_UP, q3X, cy);
        runtime.flush();
        Assert.assertEquals("UP 提交一次", 1, commitCount.get());
        Assert.assertTrue("UP committing=true", lastCommitting);
        Assert.assertEquals("UP 提交 75", 75.0D, lastChangeValue, EPS);

        // 松手后 draggingValue 清 null，progress 回落外部 value=0
        Assert.assertEquals("松手后 progress 回落外部 value=0", 0.0D, result.progress().get(), EPS);
        doLayout();
        Assert.assertEquals("松手后 thumb 回到初始位置", thumbX0, thumbBox().getX());
    }

    // ==================== 契约 3：点击定位（值↔像素映射） ====================

    /** 点击 track 各位置 → value 按 ratio 映射 + step 量化。 */
    @Test
    public void pointerDownMapsToValueByRatio() {
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

        // track 内最右像素 localX=199 → ratio≈0.995 → raw 99.5 → 量化 clamp 100
        routePointer(ScenePointerAction.BUTTON_DOWN, left + TRACK_WIDTH - 1, cy);
        runtime.flush();
        Assert.assertEquals("track 内最右像素 → 量化 clamp 100", 100.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + TRACK_WIDTH - 1, cy);
        runtime.flush();

        // track 左缘 localX=0 → min=0
        routePointer(ScenePointerAction.BUTTON_DOWN, left, cy);
        runtime.flush();
        Assert.assertEquals("track 左缘 → min=0", 0.0D, lastChangeValue, EPS);
    }

    // ==================== 契约 4：step 量化 ====================

    /** step=5 时，raw=23 量化到 25；raw=22 量化到 20（min + round((v-min)/step)*step）。 */
    @Test
    public void stepQuantizationRoundsToNearestStep() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;

        // localX=46 → ratio=0.23 → raw=23 → round(23/5)*5 = 25
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 46, cy);
        runtime.flush();
        Assert.assertEquals("raw=23 量化到 25", 25.0D, lastChangeValue, EPS);
        routePointer(ScenePointerAction.BUTTON_UP, left + 46, cy);
        runtime.flush();

        // localX=44 → ratio=0.22 → raw=22 → round(22/5)*5 = 20
        routePointer(ScenePointerAction.BUTTON_DOWN, left + 44, cy);
        runtime.flush();
        Assert.assertEquals("raw=22 量化到 20", 20.0D, lastChangeValue, EPS);
    }

    // ==================== 契约 5：键盘步进 ====================

    /** 键盘步进：→/↑ 加 step；←/↓ 减 step；PageUp/PageDown 10×step；Home→min；End→max。
     *  全程 committing=true，且读外部 value.get() 算相邻值（受控）。 */
    @Test
    public void keyboardStepComputesCorrectValueWithCommitting() {
        doLayout();
        runtime.requestFocus(sliderRoot);
        valueSignal.set(50.0D);
        runtime.flush();

        // → 加 step=5 → 55
        routeKey(SceneKey.ARROW_RIGHT);
        runtime.flush();
        Assert.assertEquals("→ 加 step 到 55", 55.0D, lastChangeValue, EPS);
        Assert.assertTrue("键盘 committing=true", lastCommitting);

        // ↑ 同 → 加（外部 value 仍 50，未回写）→ 55
        routeKey(SceneKey.ARROW_UP);
        runtime.flush();
        Assert.assertEquals("↑ 同 → 加到 55", 55.0D, lastChangeValue, EPS);

        // ← 减 step → 45
        routeKey(SceneKey.ARROW_LEFT);
        runtime.flush();
        Assert.assertEquals("← 减 step 到 45", 45.0D, lastChangeValue, EPS);

        // ↓ 同 ← 减 → 45
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("↓ 同 ← 减到 45", 45.0D, lastChangeValue, EPS);

        // PageUp 加 10×step=50 → 100（clamp max）
        routeKey(SceneKey.PAGE_UP);
        runtime.flush();
        Assert.assertEquals("PageUp 加 50 → clamp 100", 100.0D, lastChangeValue, EPS);

        // PageDown 减 50 → 0（clamp min）
        routeKey(SceneKey.PAGE_DOWN);
        runtime.flush();
        Assert.assertEquals("PageDown 减 50 → clamp 0", 0.0D, lastChangeValue, EPS);

        // Home → min=0
        routeKey(SceneKey.HOME);
        runtime.flush();
        Assert.assertEquals("Home → min=0", 0.0D, lastChangeValue, EPS);

        // End → max=100
        routeKey(SceneKey.END);
        runtime.flush();
        Assert.assertEquals("End → max=100", 100.0D, lastChangeValue, EPS);

        // 全程键盘步进皆 committing=true（无预览）
        Assert.assertEquals("键盘步进无任何预览", 0, previewCount.get());
    }

    // ==================== 契约 6：step<=0 连续模式（不量化） ====================

    /** step<=0 时点击不量化，返回原始 ratio 映射值。 */
    @Test
    public void stepLeZeroUsesContinuousModeWithoutQuantization() {
        // 独立 primitive 实例：step=0
        ReactiveScheduler.get().reset();
        SceneRuntime rt2 = new SceneRuntime();
        SceneLayoutEngine le2 = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        SceneNode root2 = new SceneNode();
        Signal<Double> v2 = Signal.create(0.0D);
        Signal<Boolean> en2 = Signal.create(Boolean.TRUE);
        double[] lastVal = new double[]{Double.NaN};
        SceneSliderPrimitive.Props props = new SceneSliderPrimitive.Props(
                v2, en2, MIN, MAX, 0.0D,
                (value, committing) -> { lastVal[0] = value; });
        final SceneSliderPrimitive.Result[] holder = new SceneSliderPrimitive.Result[1];
        MountHandle h = rt2.mount(root2, () -> {
            holder[0] = SceneSliderPrimitive.create(rt2, props);
            return holder[0].root();
        });
        SceneSliderPrimitive.Result r = holder[0];
        r.root().setPreferredWidth(TRACK_WIDTH);
        r.track().setPreferredWidth(TRACK_WIDTH);
        r.thumb().setPreferredWidth(16);
        r.thumb().setPreferredHeight(16);
        rt2.flush();
        le2.layout(root2, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        int left = absXOf(r.track());
        int cy = ((LayoutBox) r.track().getCachedLayout()).getY()
                + ((LayoutBox) r.track().getCachedLayout()).getHeight() / 2;

        // localX=50 → ratio=0.25 → raw=25，step=0 不量化 → 25
        InputFrameBuilder fb = new InputFrameBuilder(left + 50, cy);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, left + 50, cy,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("step=0 连续模式 localX=50 → value=25（不量化）", 25.0D, lastVal[0], EPS);

        // localX=46 → ratio=0.23 → raw=23，不量化 → 23（非 25）
        fb = new InputFrameBuilder(left + 46, cy);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, left + 46, cy,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("step=0 连续模式 localX=46 → value=23（不量化）", 23.0D, lastVal[0], EPS);

        // 键盘步进：step<=0 时默认步长=(max-min)/100=1
        rt2.requestFocus(r.root());
        v2.set(50.0D);
        rt2.flush();
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("step=0 键盘默认步长 1 → 51", 51.0D, lastVal[0], EPS);

        h.dispose();
        rt2.dispose();
    }

    // ==================== 契约 7：max<=min 静默退化 ====================

    /** max<=min 时：progress 恒 0；点击/键盘不崩溃，值 clamp 到 min。 */
    @Test
    public void maxLeMinDegradesSilently() {
        ReactiveScheduler.get().reset();
        SceneRuntime rt2 = new SceneRuntime();
        SceneLayoutEngine le2 = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        SceneNode root2 = new SceneNode();
        Signal<Double> v2 = Signal.create(50.0D);
        Signal<Boolean> en2 = Signal.create(Boolean.TRUE);
        double[] lastVal = new double[]{Double.NaN};
        boolean[] lastCommit = new boolean[]{false};
        SceneSliderPrimitive.Props props = new SceneSliderPrimitive.Props(
                v2, en2, 100.0D, 50.0D, 5.0D,
                (value, committing) -> { lastVal[0] = value; lastCommit[0] = committing; });
        final SceneSliderPrimitive.Result[] holder = new SceneSliderPrimitive.Result[1];
        MountHandle h = rt2.mount(root2, () -> {
            holder[0] = SceneSliderPrimitive.create(rt2, props);
            return holder[0].root();
        });
        SceneSliderPrimitive.Result r = holder[0];
        r.root().setPreferredWidth(TRACK_WIDTH);
        r.track().setPreferredWidth(TRACK_WIDTH);
        r.thumb().setPreferredWidth(16);
        r.thumb().setPreferredHeight(16);
        rt2.flush();

        // progress 恒 0（range<=0）
        Assert.assertEquals("max<=min 时 progress 恒 0", 0.0D, r.progress().get(), EPS);

        le2.layout(root2, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int left = absXOf(r.track());
        int cy = ((LayoutBox) r.track().getCachedLayout()).getY()
                + ((LayoutBox) r.track().getCachedLayout()).getHeight() / 2;

        // 点击不崩溃，值 clamp 到 min=100（max<min 时 normalizeValue: max(100, min(raw,50))=100）
        InputFrameBuilder fb = new InputFrameBuilder(left + 100, cy);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, left + 100, cy,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("max<=min 点击 clamp 到 min=100", 100.0D, lastVal[0], EPS);

        // 键盘不崩溃，值 clamp 到 min=100
        rt2.requestFocus(r.root());
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("max<=min 键盘 clamp 到 min=100", 100.0D, lastVal[0], EPS);

        // Home → min=100
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.HOME, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("max<=min Home → min=100", 100.0D, lastVal[0], EPS);

        // End → max=50，但 clamp 逻辑 max(100, min(raw,50))=100，故 End 也 100
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.END, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("max<=min End → clamp 到 min=100", 100.0D, lastVal[0], EPS);

        h.dispose();
        rt2.dispose();
    }

    // ==================== 契约 8：disabled 阻断拖拽与键盘 ====================

    /** disabled 态：拖拽（DOWN）与键盘（→）均不触发 onChange。 */
    @Test
    public void disabledBlocksDragAndKeyboard() {
        doLayout();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();

        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int before = changeCount.get();

        routePointer(ScenePointerAction.BUTTON_DOWN, left + TRACK_WIDTH / 2, cy);
        runtime.flush();
        routePointer(ScenePointerAction.BUTTON_UP, left + TRACK_WIDTH / 2, cy);
        runtime.flush();
        Assert.assertEquals("disabled 拖拽不触发 onChange", before, changeCount.get());

        runtime.requestFocus(sliderRoot);
        routeKey(SceneKey.ARROW_RIGHT);
        runtime.flush();
        Assert.assertEquals("disabled 键盘不触发 onChange", before, changeCount.get());
    }

    // ==================== 契约 9：POINTER_CANCEL 取消不提交 ====================

    /** POINTER_CANCEL：拖拽中途取消，不产生提交（committing=true），draggingValue 清 null 回落外部 value。 */
    @Test
    public void cancelDoesNotCommitAndFallsBack() {
        doLayout();
        int left = trackLeftX();
        int cy = trackBox().getY() + trackBox().getHeight() / 2;
        int thumbX0 = thumbBox().getX();

        // DOWN 启动拖拽
        int rightX = left + TRACK_WIDTH - 1;
        routePointer(ScenePointerAction.BUTTON_DOWN, rightX, cy);
        runtime.flush();
        int commitsBefore = commitCount.get();
        Assert.assertTrue("DOWN 应已启动拖拽", changeCount.get() > 0);

        // CANCEL 取消
        routePointer(ScenePointerAction.CANCEL, rightX, cy);
        runtime.flush();
        doLayout();

        Assert.assertEquals("CANCEL 不产生新提交", commitsBefore, commitCount.get());
        Assert.assertEquals("CANCEL 后 thumb 回落外部 value=0 位置", thumbX0, thumbBox().getX());
        Assert.assertEquals("CANCEL 后 progress 回落 0", 0.0D, result.progress().get(), EPS);
    }

    // ==================== 契约 10：四节点结构 ====================

    /** primitive 创建 root/track/fillBox/thumb 四节点结构；track 是 root 子节点，fillBox/thumb 是 track 子节点。 */
    @Test
    public void primitiveBuildsFourNodeStructure() {
        Assert.assertNotNull("root 节点", result.root());
        Assert.assertNotNull("track 节点", result.track());
        Assert.assertNotNull("fillBox 节点", result.fillBox());
        Assert.assertNotNull("thumb 节点", result.thumb());

        Assert.assertEquals("root 第一个子节点是 track", result.track(), sliderRoot.__getChildren().get(0));
        Assert.assertEquals("track 第一个子节点是 fillBox", result.fillBox(), result.track().__getChildren().get(0));
        Assert.assertEquals("track 第二个子节点是 thumb", result.thumb(), result.track().__getChildren().get(1));

        // track/fillBox/thumb 均不可命中（hitTestable=false），只有 root 可命中
        Assert.assertTrue("root 可命中", sliderRoot.isHitTestable());
        Assert.assertFalse("track 不可命中", result.track().isHitTestable());
        Assert.assertFalse("fillBox 不可命中", result.fillBox().isHitTestable());
        Assert.assertFalse("thumb 不可命中", result.thumb().isHitTestable());
    }
}
