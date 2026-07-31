package club.heiqi.uilib.ui.scene.control;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
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
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * SceneTextInput B1 端到端单元测试。
 *
 * <p>覆盖受控文本真值、三节点 prefix/caret/suffix 结构、字符级 caret 移动、点击定位、
 * 中间插入/删除、码点安全、readOnly/disabled 与 PASSWORD 掩码定位。</p>
 */
public class SceneTextInputTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** 语义化交互注入 harness（typeText 入口）；其 runtime 即上方 runtime 字段。
     *  仅用于单帧文本注入；多帧同批次 routeTextFrame、精确 caret 定位 clickLocalX 走白盒回退
     *  （多帧批次 + 精确 localX，判据见 §7.1）。 */
    private SceneInteractionHarness harness;

    private Signal<String> valueSignal;
    private Signal<Boolean> enabledSignal;
    private Signal<Boolean> readOnlySignal;

    private AtomicInteger changeCount;
    private String lastChangeValue;

    private MountHandle handle;
    private SceneNode inputRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int LINE_HEIGHT = 16;
    private static final int PADDING = SceneChromeTokens.PAD_MD;

    private static final int CARET_COLOR = SceneChromeTokens.BORDER_FOCUS;
    private static final int CARET_TRANSPARENT = 0x00000000;
    private static final int BG_ENABLED = SceneChromeTokens.BG_PRESSED;
    private static final int BG_DISABLED = SceneChromeTokens.BG_DISABLED;
    private static final int BORDER_ENABLED = SceneChromeTokens.BORDER_DEFAULT;
    private static final char MASK_CHAR = '\u2022';

    private static final int MAX_LENGTH = 8;
    private static final String PLACEHOLDER = "输入...";

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        // 记住路由根（mountRoot 内 layout 此时空树无害；typeText 只用 root route，不依赖 centerOf）
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    private void mountInput(String initialValue, SceneInputType inputType,
                            int maxLength, String placeholder) {
        mountInput(initialValue, inputType, maxLength, placeholder, true);
    }

    /** 挂载输入框，并可保留首次响应式 flush 供焦点声明时序测试控制。 */
    private void mountInput(String initialValue, SceneInputType inputType,
                            int maxLength, String placeholder, boolean flush) {
        valueSignal = Signal.create(initialValue);
        enabledSignal = Signal.create(Boolean.TRUE);
        readOnlySignal = Signal.create(Boolean.FALSE);
        changeCount = new AtomicInteger(0);
        lastChangeValue = null;

        SceneTextInput.Props props = new SceneTextInput.Props(
                valueSignal, enabledSignal, readOnlySignal,
                placeholder, maxLength, inputType,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneTextInput.create(runtime, props));
        inputRoot = handle.getRoot();
        if (flush) runtime.flush();
    }

    private void mountTextInput() {
        mountInput("", SceneInputType.TEXT, MAX_LENGTH, PLACEHOLDER);
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private SceneNode prefixNode() {
        return inputRoot.__getChildren().get(0);
    }

    private SceneNode caretNode() {
        return inputRoot.__getChildren().get(1);
    }

    private SceneNode suffixNode() {
        return inputRoot.__getChildren().get(2);
    }

    private LayoutBox rootBox() {
        return (LayoutBox) inputRoot.getCachedLayout();
    }

    private LayoutBox caretBox() {
        return (LayoutBox) caretNode().getCachedLayout();
    }

    /** 单帧文本注入（不 flush）。
     *  <p>白盒回退（多帧批次）：批次用例（routeText 后不 flush 直接 routeKey，最后统一 flush）依赖
     *  「同批次多帧 route 后一次 flush」语义，harness.typeText 内部会 flush 破坏批次，
     *  故批次用例仍用本方法；单帧用例已迁 harness.typeText。判据见 §7.1。</p> */
    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 多 ofText 同帧注入。白盒回退（多帧批次）：harness 不覆盖多帧文本同帧注入。判据见 §7.1。 */
    private void routeTextFrame(String first, String second, String third) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(first, 1000L));
        fb.push(RawInputEvent.ofText(second, 1001L));
        fb.push(RawInputEvent.ofText(third, 1002L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    private void routeKeyAndFlush(SceneKey key) {
        routeKey(key);
        runtime.flush();
    }

    private void clickLocalX(int localX) {
        clickLocalX(localX, 0, 0);
    }

    /**
     * 点击 input 内 localX 偏移（文本区局部），可指定 rootAbs（验证 I12 三层坐标）。
     * 屏幕坐标 = absoluteX(inputRoot) + PADDING + localX + rootAbsX（hitTester 内部 nodeAbs 含 rootAbs）。
     *
     * <p>白盒回退（精确 localX / 自定义坐标，§7.1判据2 + 判据4）：精确 caret 定位需按文本区局部偏移 + rootAbs 三层坐标计算，
     * harness.click 取节点中心无法表达「文本区内某像素列」语义，故全留自建。</p>
     */
    private void clickLocalX(int localX, int rootAbsX, int rootAbsY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN,
                absoluteX(inputRoot) + PADDING + localX + rootAbsX,
                absoluteY(inputRoot) + PADDING + 1 + rootAbsY,
                SceneMouseButton.LEFT, 0, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, rootAbsX, rootAbsY);
    }

    private int absoluteX(SceneNode node) {
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

    private int absoluteY(SceneNode node) {
        int y = 0;
        SceneNode cur = node;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                y += ((LayoutBox) cached).getY();
            }
            cur = cur.__getParent();
        }
        return y;
    }

    private void assertParts(String prefix, String suffix) {
        runtime.flush();
        doLayout();
        Assert.assertEquals("prefix 文本", prefix, prefixNode().getText());
        Assert.assertEquals("suffix 文本", suffix, suffixNode().getText());
    }

    @Test
    public void configMotionInterpolatesFocusBorder() {
        mountTextInput();
        runtime.__enableMotion();

        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertEquals("retarget 帧保持默认边框起点", BORDER_ENABLED, inputRoot.getBorderColor());

        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(46_000_000L);
        int midpoint = inputRoot.getBorderColor();
        Assert.assertNotEquals("fast Motion 半程不得停在起点", BORDER_ENABLED, midpoint);
        Assert.assertNotEquals("fast Motion 半程不得提前到终点", SceneChromeTokens.BORDER_FOCUS, midpoint);

        runtime.__sampleMotion(91_000_000L);
        Assert.assertEquals("fast 90ms 到达 focus border", SceneChromeTokens.BORDER_FOCUS,
                inputRoot.getBorderColor());
    }

    @Test
    public void controlledInputRaisesOnChangeWithoutSelfMutate() {
        mountTextInput();
        doLayout();
        runtime.requestFocus(inputRoot);

        harness.typeText("a");
        Assert.assertEquals("输入 a 触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 上抛新值 a", "a", lastChangeValue);
        Assert.assertEquals("外部未回写时 value 仍空", "", valueSignal.get());

        valueSignal.set("a");
        runtime.flush();
        harness.typeText("b");
        Assert.assertEquals("基于外部回写后的 value 插入 b", "ab", lastChangeValue);
    }

    @Test
    public void defaultAppearanceShouldStayNonFlat() {
        mountTextInput();

        Assert.assertEquals("默认 TextInput padding 保持原值", PADDING, inputRoot.getPaddingLeft());
        Assert.assertEquals("默认 TextInput borderWidth 保持原值", 1, inputRoot.getBorderWidth());
        Assert.assertEquals("默认 TextInput cornerRadius 使用统一 token",
                SceneChromeTokens.RADIUS_MD, inputRoot.getCornerRadius());
        Assert.assertEquals("默认 TextInput 背景保持原值", BG_ENABLED, inputRoot.getBackgroundColor());
        Assert.assertEquals("默认 TextInput 边框保持原值", BORDER_ENABLED, inputRoot.getBorderColor());
    }

    @Test
    public void inputBackgroundStaysStableAcrossHoverAndFocus() {
        mountTextInput();
        doLayout();
        runtime.__enableMotion();

        harness.moveTo(inputRoot);
        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(91_000_000L);
        Assert.assertEquals("hover 不应把内凹输入背景提亮", BG_ENABLED, inputRoot.getBackgroundColor());

        harness.click(inputRoot);
        runtime.__sampleMotion(92_000_000L);
        runtime.__sampleMotion(137_000_000L);
        Assert.assertEquals("hover 到 focus 不应触发背景反向变暗", BG_ENABLED,
                inputRoot.getBackgroundColor());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        runtime.__sampleMotion(138_000_000L);
        runtime.__sampleMotion(228_000_000L);
        Assert.assertEquals("disabled 仍使用禁用背景", BG_DISABLED, inputRoot.getBackgroundColor());
    }

    @Test
    public void multipleTextEventsInSameFrameRaiseSingleMergedOnChange() {
        mountTextInput();
        doLayout();
        runtime.requestFocus(inputRoot);

        routeTextFrame("修", "好", "了");
        runtime.flush();

        Assert.assertEquals("同帧多条 TEXT 只触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 上抛完整合并文本", "修好了", lastChangeValue);
    }

    @Test
    public void characterFilteringRejectsControlAndNonNumeric() {
        mountTextInput();
        doLayout();
        runtime.requestFocus(inputRoot);

        harness.typeText("a\nb\tc");
        Assert.assertEquals("TEXT 过滤控制字符", "abc", lastChangeValue);

        int before = changeCount.get();
        harness.typeText("\n\t");
        Assert.assertEquals("纯控制串不触发 onChange", before, changeCount.get());

        runtime.dispose();
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        mountInput("", SceneInputType.NUMBER, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        // 白盒回退（多 runtime，§7.1判据5）：本用例中途重建 runtime/sceneRoot，harness 仍持有旧实例，故回退裸建 routeText
        routeText("1a2b.3");
        runtime.flush();
        Assert.assertEquals("NUMBER 过滤字母，保留数字与符号", "12.3", lastChangeValue);
    }

    @Test
    public void maxLengthRejectsInsertWhenFull() {
        mountInput("12345678", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        int before = changeCount.get();
        harness.typeText("9");
        Assert.assertEquals("填满后拒绝新增", before, changeCount.get());

        valueSignal.set("1234567");
        runtime.flush();
        routeKeyAndFlush(SceneKey.END);
        harness.typeText("8");
        Assert.assertEquals("未满时允许插入", "12345678", lastChangeValue);
    }

    @Test
    public void arrowHomeEndMoveCaretWithoutOnChange() {
        mountInput("abc", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        routeKeyAndFlush(SceneKey.END);
        assertParts("abc", "");
        int before = changeCount.get();

        routeKeyAndFlush(SceneKey.ARROW_LEFT);
        assertParts("ab", "c");
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("abc", "");
        routeKeyAndFlush(SceneKey.HOME);
        assertParts("", "abc");

        Assert.assertEquals("移动 caret 不触发 onChange", before, changeCount.get());
    }

    @Test
    public void textInputInsertsAtMiddle() {
        mountInput("ac", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("a", "c");

        harness.typeText("b");
        Assert.assertEquals("中间插入得到 abc", "abc", lastChangeValue);

        valueSignal.set(lastChangeValue);
        assertParts("ab", "c");
    }

    @Test
    public void backspaceAndDeleteWorkAtMiddle() {
        mountInput("abcd", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("ab", "cd");

        routeKey(SceneKey.BACKSPACE);
        runtime.flush();
        Assert.assertEquals("Backspace 删除 caret 前码点", "acd", lastChangeValue);

        valueSignal.set(lastChangeValue);
        runtime.flush();
        routeKey(SceneKey.DELETE);
        runtime.flush();
        Assert.assertEquals("Delete 删除 caret 后码点", "ad", lastChangeValue);
    }

    @Test
    public void emojiEditingDoesNotSplitSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        mountInput("a" + emoji + "b", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("a" + emoji, "b");

        routeKey(SceneKey.BACKSPACE);
        runtime.flush();
        Assert.assertEquals("Backspace 删除完整 emoji", "ab", lastChangeValue);

        valueSignal.set("a" + emoji + "b");
        runtime.flush();
        routeKeyAndFlush(SceneKey.HOME);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKey(SceneKey.DELETE);
        runtime.flush();
        Assert.assertEquals("Delete 删除完整 emoji", "ab", lastChangeValue);
    }

    @Test
    public void clickPositionsCaretByMeasuredPrefixWidth() {
        mountInput("abc", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        clickLocalX(13);
        assertParts("ab", "c");
    }

    @Test
    public void clickPositionReusesPrefixWidthCacheUntilDisplayOrEpochChanges() {
        if (runtime != null) {
            runtime.dispose();
        }
        CountingTextMeasurer measurer = new CountingTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();

        mountInput("abc", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();

        measurer.resetMeasureCount();
        clickLocalX(13);
        Assert.assertEquals("首次点击为三个码点构建前缀宽度", 3, measurer.getMeasureCount());

        clickLocalX(21);
        Assert.assertEquals("同 display/fontSize/epoch 第二次点击复用缓存", 3, measurer.getMeasureCount());

        valueSignal.set("abcd");
        runtime.flush();
        doLayout();
        measurer.resetMeasureCount();
        clickLocalX(21);
        Assert.assertEquals("display 变更后重建前缀宽度", 4, measurer.getMeasureCount());

        measurer.setEpoch(1);
        clickLocalX(21);
        Assert.assertEquals("epoch 变更后重建前缀宽度", 8, measurer.getMeasureCount());
    }

    @Test
    public void clickPositionUsesAbsoluteAncestorOffset() {
        sceneRoot.setPadding(30, 0, 0, 0);
        SceneNode wrapper = new SceneNode();
        wrapper.setPadding(20, 0, 0, 0);
        sceneRoot.appendChild(wrapper);

        valueSignal = Signal.create("abc");
        enabledSignal = Signal.create(Boolean.TRUE);
        readOnlySignal = Signal.create(Boolean.FALSE);
        changeCount = new AtomicInteger(0);
        lastChangeValue = null;
        SceneTextInput.Props props = new SceneTextInput.Props(
                valueSignal, enabledSignal, readOnlySignal,
                "", MAX_LENGTH, SceneInputType.TEXT,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = next;
                });
        handle = runtime.mount(wrapper, SceneTextInput.create(runtime, props));
        inputRoot = handle.getRoot();
        runtime.flush();
        doLayout();

        clickLocalX(13);
        assertParts("ab", "c");
    }

    @Test
    public void readOnlyAllowsCaretMoveAndClickButBlocksEditing() {
        mountInput("abcd", SceneInputType.TEXT, MAX_LENGTH, "");
        readOnlySignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        runtime.requestFocus(inputRoot);

        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("a", "bcd");
        clickLocalX(21);
        assertParts("abc", "d");

        int before = changeCount.get();
        // 白盒回退（多帧批次）：routeText + routeKey 同批次最后统一 flush，harness.typeText 会中途 flush 破坏批次。判据见 §7.1
        routeText("x");
        routeKey(SceneKey.BACKSPACE);
        routeKey(SceneKey.DELETE);
        runtime.flush();
        Assert.assertEquals("readOnly 阻断插入与删除", before, changeCount.get());
        Assert.assertEquals("readOnly 仍可聚焦显示 caret", CARET_COLOR, caretNode().getBackgroundColor());
    }

    @Test
    public void disabledBlocksInputAndPointerFocus() {
        mountInput("seed", SceneInputType.TEXT, MAX_LENGTH, "");
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        clickLocalX(10);
        runtime.flush();
        Assert.assertNotSame("disabled 点击不聚焦输入框", inputRoot, runtime.getFocusedNode());

        int before = changeCount.get();
        runtime.requestFocus(inputRoot);
        // 白盒回退（多帧批次）：同上，harness.typeText 会中途 flush 破坏批次。判据见 §7.1
        routeText("x");
        routeKey(SceneKey.BACKSPACE);
        routeKey(SceneKey.DELETE);
        runtime.flush();
        Assert.assertEquals("disabled handler 兜底阻断所有写入", before, changeCount.get());
    }

    @Test
    public void placeholderAndCaretVisibilityFollowFocus() {
        mountTextInput();
        doLayout();
        Assert.assertEquals("失焦空值显示 placeholder", PLACEHOLDER, prefixNode().getText());
        Assert.assertEquals("失焦 caret 透明", CARET_TRANSPARENT, caretNode().getBackgroundColor());

        runtime.requestFocus(inputRoot);
        runtime.flush();
        doLayout();
        Assert.assertEquals("聚焦空值 prefix 清空", "", prefixNode().getText());
        Assert.assertEquals("聚焦空值 prefix 宽度为 0", 0,
                ((LayoutBox) prefixNode().getCachedLayout()).getWidth());
        Assert.assertEquals("聚焦空值 caret 位于左 padding", PADDING, caretBox().getX());
        Assert.assertEquals("聚焦 caret 可见", CARET_COLOR, caretNode().getBackgroundColor());
    }

    /** 首次 effect flush 前请求焦点时，权威焦点、投影 signal、边框与 caret 必须同步。 */
    @Test
    public void focusRequestedBeforeFirstFlushProjectsFocusedChromeAndCaret() {
        mountInput("", SceneInputType.TEXT, MAX_LENGTH, PLACEHOLDER, false);

        Assert.assertTrue("首次 flush 前应可请求权威焦点", runtime.requestFocus(inputRoot));
        runtime.flush();

        Assert.assertSame("权威焦点应指向输入框", inputRoot, runtime.getFocusedNode());
        Assert.assertEquals("interaction focused signal 应同步为 true", Boolean.TRUE,
                runtime.interactionState(inputRoot).focused().get());
        Assert.assertEquals("首次聚焦应显示 focus border", SceneChromeTokens.BORDER_FOCUS,
                inputRoot.getBorderColor());
        Assert.assertEquals("首次聚焦应显示常亮 caret", CARET_COLOR,
                caretNode().getBackgroundColor());
    }

    @Test
    public void externalValueShrinkClampsComputedCaret() {
        mountInput("abcdef", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.END);
        assertParts("abcdef", "");

        valueSignal.set("ab");
        assertParts("ab", "");

        valueSignal.set("");
        assertParts("", "");
    }

    @Test
    public void passwordSplitsByMaskButOnChangeUsesRealValue() {
        mountInput("abcd", SceneInputType.PASSWORD, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        clickLocalX(13);
        runtime.flush();
        String twoMasks = String.valueOf(new char[]{MASK_CHAR, MASK_CHAR});
        Assert.assertEquals("PASSWORD prefix 基于掩码宽度定位", twoMasks, prefixNode().getText());
        Assert.assertEquals("PASSWORD suffix 基于掩码宽度拆分", twoMasks, suffixNode().getText());

        harness.typeText("X");
        Assert.assertEquals("PASSWORD onChange 仍上抛真实值", "abXcd", lastChangeValue);
    }

    @Test
    public void caretNodeSitsBetweenPrefixAndSuffix() {
        mountInput("ab", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        assertParts("a", "b");

        int expectedX = PADDING + STUB_CHAR_WIDTH;
        Assert.assertEquals("caret 在 prefix 与 suffix 中间", expectedX, caretBox().getX());
    }

    // ==================== caret 空文本叶兜底回归 ====================

    /**
     * 回归锚点：caret 节点即使被取消首选宽度（setPreferredWidth(0)），
     * 也不应回退填满父宽，必须真正归零。
     *
     * <p>根因：caret 无文本时 computeWidth 返回 outerWidth（填满父宽），
     * 会把同行 prefix/suffix 推出 row 裁剪区。修复：caret.setText("") 使其走
     * 空文本叶分支返回 padH=0。与 SceneTextAreaPrimitive 的非光标行 caret
     * 兜底对齐，防止未来把 caret 宽度改成条件性 setPreferredWidth(0) 时
     * 复现撑满 bug。</p>
     *
     * <p>本测试模拟"未来潜在改动"：手动把 caret 首选宽度置 0，验证布局后
     * 宽度归零而非填满 canvas。</p>
     */
    @Test
    public void caretWidthDoesNotFillWhenPreferredWidthZero() {
        mountTextInput();
        doLayout();
        // 基线：默认 caret 宽 = CARET_WIDTH
        Assert.assertEquals("默认 caret 宽", 1, caretBox().getWidth());

        // 模拟未来潜在改动：把 caret 首选宽度置 0（取消首选宽度）
        caretNode().setPreferredWidth(0);
        doLayout();
        // 走空文本叶分支应返回 0，不撑满父宽
        Assert.assertEquals("caret preferredWidth=0 时应归零不撑满", 0, caretBox().getWidth());
        Assert.assertTrue("caret 宽度不得填满 canvas",
                caretBox().getWidth() < CANVAS_WIDTH);
    }

    /**
     * Builder.build() 构建的 Props 与 canonical 构造器构建的 Props 各字段等价。
     *
     * <p>显式设置全部字段后，Builder 与 canonical 传入相同引用/值，
     * 逐字段断言一致，并验证 record equals 成立。</p>
     */
    @Test
    public void builderShouldMatchCanonicalProps() {
        Signal<String> value = Signal.create("abc");
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        Signal<Boolean> readOnly = Signal.create(Boolean.FALSE);
        java.util.function.Consumer<String> onChange = v -> { };
        SceneTextInput.Props fromBuilder = SceneTextInput.Props.builder(value)
                .enabled(enabled).readOnly(readOnly).placeholder("p").maxLength(8)
                .inputType(SceneInputType.TEXT).onChange(onChange)
                .build();
        SceneTextInput.Props fromCanonical = new SceneTextInput.Props(
                value, enabled, readOnly, "p", 8, SceneInputType.TEXT, onChange);

        Assert.assertSame("value 引用一致", value, fromBuilder.value());
        Assert.assertSame("enabled 引用一致", enabled, fromBuilder.enabled());
        Assert.assertSame("readOnly 引用一致", readOnly, fromBuilder.readOnly());
        Assert.assertEquals("placeholder 一致", fromCanonical.placeholder(), fromBuilder.placeholder());
        Assert.assertEquals("maxLength 一致", fromCanonical.maxLength(), fromBuilder.maxLength());
        Assert.assertEquals("inputType 一致", fromCanonical.inputType(), fromBuilder.inputType());
        Assert.assertSame("onChange 引用一致", onChange, fromBuilder.onChange());
        Assert.assertEquals("Builder 与 canonical Props 应 record equals 等价", fromCanonical, fromBuilder);
    }

    /**
     * 计数文本度量器，用于验证点击定位缓存失效边界。
     */
    private static final class CountingTextMeasurer implements SceneTextMeasurer {
        /**
         * 单字符宽度。
         */
        private final int charWidth;
        /**
         * 行高。
         */
        private final int lineHeight;
        /**
         * 当前度量纪元。
         */
        private int epoch;
        /**
         * measureWidth 调用次数。
         */
        private int measureCount;

        /**
         * 创建计数文本度量器。
         *
         * @param charWidth  单字符宽度
         * @param lineHeight 行高
         */
        private CountingTextMeasurer(int charWidth, int lineHeight) {
            this.charWidth = charWidth;
            this.lineHeight = lineHeight;
        }

        @Override
        public int measureWidth(String text, int fontSizePx) {
            measureCount++;
            return (text == null ? 0 : text.codePointCount(0, text.length())) * charWidth;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return lineHeight;
        }

        @Override
        public int ascent(int fontSizePx) {
            return 12;
        }

        @Override
        public int descent(int fontSizePx) {
            return 4;
        }

        @Override
        public int lineGap(int fontSizePx) {
            return 0;
        }

        @Override
        public int epoch() {
            return epoch;
        }

        /**
         * 重置测量调用次数。
         */
        private void resetMeasureCount() {
            measureCount = 0;
        }

        /**
         * 获取测量调用次数。
         *
         * @return measureWidth 调用次数
         */
        private int getMeasureCount() {
            return measureCount;
        }

        /**
         * 设置当前度量纪元。
         *
         * @param epoch 当前度量纪元
         */
        private void setEpoch(int epoch) {
            this.epoch = epoch;
        }
    }

    // ==================== I12：rootAbs≠0 时点击 caret 定位不偏移 ====================

    /**
     * I12 坐标系对齐：rootAbsX/Y≠0 时，点击 input 内 localX=13（落在 "ab" 与 "c" 之间），
     * caret 仍定位到 index=2（prefix="ab"），与 rootAbs=0 时一致。
     *
     * <p>修复前 SceneTextInputPrimitive 用 ev.getPointerX()（raw，含 rootAbs）-
     * absoluteBox(root,0,0).getX()（host 局部）- paddingLeft，rootAbs≠0 时 localX 多减一个 rootAbs，
     * caret 定位偏移。修复后用 ctx.getLocalPointerX()（两层坐标，= raw - absoluteBox(root,treeAbs)
     * = root 真局部）- paddingLeft，rootAbs≠0 不再错位。</p>
     */
    @Test
    public void clickCaretPositionCorrectWithNonZeroRootAbs() {
        mountInput("abc", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        int rootAbsX = 70;
        int rootAbsY = 40;

        // localX=13 落在 "ab"（2 字符 × 8px = 16px）之前，应定位到 index=1 或 2 附近；
        // 与 rootAbs=0 的 clickPositionsCaretByMeasuredPrefixWidth 同点对照，断言 prefix="ab"
        clickLocalX(13, rootAbsX, rootAbsY);
        assertParts("ab", "c");
    }
}
