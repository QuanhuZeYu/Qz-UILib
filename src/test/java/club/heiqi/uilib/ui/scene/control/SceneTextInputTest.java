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
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneTextInput 端到端单元测试 —— Phase 4 批 3 受控文本输入控件（档位 A）验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控输入闭环（onChange 上抛追加末尾新 String + 外部不回写时控件不自改，R9 命门）、
 * 字符过滤、maxLength、退格（含代理对码点）、密码掩码、placeholder、readOnly/disabled 阻断、
 * caret 可见性随聚焦态、caret 位置靠 ROW 自然排到文本末尾。</p>
 *
 * <h3>测试沙箱 pipeline（对照 SceneSliderTest）</h3>
 * <pre>
 *   signal.set / route(文本/键盘事件) → runtime.flush() → layout → 断言
 * </pre>
 */
public class SceneTextInputTest {

    /** 场景根：input 作为子节点 mount 到此（route/layout 入口） */
    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    /** input 的 value 受控源（可写，测试驱动） */
    private Signal<String> valueSignal;
    /** input 的 enabled signal */
    private Signal<Boolean> enabledSignal;
    /** input 的 readOnly signal */
    private Signal<Boolean> readOnlySignal;

    /** onChange 触发计数器 */
    private AtomicInteger changeCount;
    /** onChange 最近一次收到的「期望新值」 */
    private String lastChangeValue;

    private MountHandle handle;
    /** input 根节点 */
    private SceneNode inputRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    // SceneTextInput 内部常量镜像（与私有常量保持一致）
    private static final int CARET_COLOR = 0xFFE2E8F0;
    private static final int CARET_TRANSPARENT = 0x00000000;
    private static final char MASK_CHAR = '\u2022';

    private static final int MAX_LENGTH = 8;
    private static final String PLACEHOLDER = "输入...";

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
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    // ==================== 构造辅助：按 inputType 挂载一个 input ====================

    /**
     * 挂载一个 TextInput，初始 value/enabled/readOnly 由参数指定。
     *
     * @param initialValue 初始受控值
     * @param inputType    输入类型
     * @param maxLength    最大码点数
     * @param placeholder  占位文本
     */
    private void mountInput(String initialValue, SceneInputType inputType,
                            int maxLength, String placeholder) {
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
        runtime.flush();
    }

    /** 默认 TEXT 类型 input，空初值，maxLength=8，带 placeholder */
    private void mountTextInput() {
        mountInput("", SceneInputType.TEXT, MAX_LENGTH, PLACEHOLDER);
    }

    // ==================== 节点/布局辅助 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** textNode 子节点（root 第一个孩子） */
    private SceneNode textNode() {
        return inputRoot.__getChildren().get(0);
    }

    /** caret 子节点（root 第二个孩子） */
    private SceneNode caretNode() {
        return inputRoot.__getChildren().get(1);
    }

    private LayoutBox textBox() {
        return (LayoutBox) textNode().getCachedLayout();
    }

    private LayoutBox caretBox() {
        return (LayoutBox) caretNode().getCachedLayout();
    }

    /** 构造单文本事件帧并 route 到 sceneRoot */
    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    /** 构造单键盘事件帧并 route 到 sceneRoot */
    private void routeKey(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    // ==================== 验收 1：受控输入闭环（R9 命门） ====================

    /**
     * 受控核心：聚焦后输入字符 → onChange 收到「追加到末尾的新 String」；
     * 但外部 value <b>不回写</b>时控件视觉不自改（仍显示旧 value）——证明控件零内部受控状态，
     * 不自缓存/自改 value（R9 命门，类比 Slider 的 R7 命门）。外部 set 回后才更新。
     */
    @Test
    public void controlledInputRaisesOnChangeWithoutSelfMutate() {
        mountTextInput();
        doLayout();
        runtime.requestFocus(inputRoot);

        // 输入 "a"
        routeText("a");
        runtime.flush();
        Assert.assertEquals("输入 a 触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 上抛追加末尾的新值 a", "a", lastChangeValue);

        // 受控：外部未 set 回 → value 仍空 → textNode 仍显示 placeholder（控件不自改）
        Assert.assertEquals("受控：外部未回写时 value 仍空", "", valueSignal.get());

        // 外部 set value=a → flush → 再输入 b → onChange 收到 ab（追加在末尾）
        valueSignal.set("a");
        runtime.flush();
        routeText("b");
        runtime.flush();
        Assert.assertEquals("基于回写后的 value=a 追加 b → ab", "ab", lastChangeValue);
    }

    // ==================== 验收 2：字符过滤 ====================

    /**
     * 字符过滤：TEXT 类型下控制字符 / \n / \t 被拒（onChange 不含它们，且纯控制串不触发 onChange）；
     * NUMBER 类型字母被拒、数字放行。
     */
    @Test
    public void characterFilteringRejectsControlAndNonNumeric() {
        // TEXT：输入含正常字符 + 换行 + 制表 → 只保留正常字符
        mountTextInput();
        doLayout();
        runtime.requestFocus(inputRoot);

        routeText("a\nb\tc");
        runtime.flush();
        Assert.assertEquals("TEXT 过滤 \\n \\t 后保留 abc", "abc", lastChangeValue);

        // 纯控制串不触发 onChange（全被过滤 → next==cur）
        int before = changeCount.get();
        routeText("\n\t");
        runtime.flush();
        Assert.assertEquals("纯控制串全被过滤，不触发 onChange", before, changeCount.get());

        // NUMBER：字母被拒，数字与符号放行
        runtime.dispose();
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        sceneRoot = new SceneNode();
        mountInput("", SceneInputType.NUMBER, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        routeText("1a2b.3");
        runtime.flush();
        Assert.assertEquals("NUMBER 过滤字母 a/b，保留数字与小数点 12.3", "12.3", lastChangeValue);
    }

    // ==================== 验收 3：maxLength 填满拒绝新增 ====================

    /**
     * maxLength（码点数）：填满 8 后再输入被拒（onChange 不再增长，已有不被截断）。
     */
    @Test
    public void maxLengthRejectsAppendWhenFull() {
        mountInput("12345678", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        Assert.assertEquals("初值已填满 8 码点", 8, valueSignal.get().length());

        int before = changeCount.get();
        routeText("9");
        runtime.flush();
        Assert.assertEquals("已填满，再输入被拒，不触发 onChange", before, changeCount.get());

        // 未满时可继续（验证填满判定是 < maxLength）
        valueSignal.set("1234567"); // 7 码点
        runtime.flush();
        routeText("8");
        runtime.flush();
        Assert.assertEquals("未满时追加成功 → 12345678", "12345678", lastChangeValue);
    }

    // ==================== 验收 4：退格 ====================

    /**
     * 退格：删末尾一个码点；空串退格无 onChange；代理对（emoji）退格删整个码点不是半个 char。
     */
    @Test
    public void backspaceDeletesOneCodePoint() {
        // 普通：abc 退格 → ab
        mountInput("abc", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        routeKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("退格删末尾一码点 abc → ab", "ab", lastChangeValue);

        // 空串退格无操作
        valueSignal.set("");
        runtime.flush();
        int before = changeCount.get();
        routeKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("空串退格不触发 onChange", before, changeCount.get());

        // 代理对：emoji（U+1F600，2 个 char = 1 码点）退格删整码点 → 空串
        String emoji = new String(Character.toChars(0x1F600));
        Assert.assertEquals("emoji 占 2 个 char", 2, emoji.length());
        valueSignal.set("x" + emoji); // 3 char = 2 码点
        runtime.flush();
        routeKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("退格删整个 emoji 码点（不留半个代理） → x", "x", lastChangeValue);
    }

    // ==================== 验收 5：密码掩码 ====================

    /**
     * 密码掩码：displayText 是等量 •（按码点数），但 onChange/value 是真实值。
     */
    @Test
    public void passwordMasksDisplayButKeepsRealValueInCallback() {
        mountInput("", SceneInputType.PASSWORD, MAX_LENGTH, "");
        doLayout();
        runtime.requestFocus(inputRoot);

        // 输入 abc → onChange 真实值 abc
        routeText("abc");
        runtime.flush();
        Assert.assertEquals("PASSWORD onChange 上抛真实值 abc（不掩码）", "abc", lastChangeValue);

        // 外部回写真实值 → flush → textNode 显示等量掩码 •••
        valueSignal.set("abc");
        runtime.flush();
        doLayout();
        Assert.assertEquals("displayText 为 3 个掩码圆点",
                String.valueOf(new char[]{MASK_CHAR, MASK_CHAR, MASK_CHAR}), textNode().getText());
        Assert.assertEquals("受控真实值仍为 abc（掩码只影响显示）", "abc", valueSignal.get());
    }

    // ==================== 验收 6：placeholder ====================

    /**
     * placeholder：value 空串时 textNode 显示 placeholder；非空时显示真实值。
     */
    @Test
    public void placeholderShownWhenValueEmpty() {
        mountTextInput();
        doLayout();
        Assert.assertEquals("value 空串时 textNode 显示 placeholder", PLACEHOLDER, textNode().getText());

        // 回写非空值 → 显示真实值（非 placeholder）
        valueSignal.set("hello");
        runtime.flush();
        doLayout();
        Assert.assertEquals("value 非空时 textNode 显示真实值", "hello", textNode().getText());
    }

    // ==================== 验收 7：readOnly 阻断写入但可聚焦 ====================

    /**
     * readOnly：可聚焦（caret 可见）但字符输入/退格被阻断（onChange 不触发）。
     */
    @Test
    public void readOnlyBlocksWriteButStaysFocusable() {
        mountInput("seed", SceneInputType.TEXT, MAX_LENGTH, "");
        readOnlySignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        runtime.requestFocus(inputRoot);

        int before = changeCount.get();
        // 字符输入被阻断
        routeText("x");
        runtime.flush();
        Assert.assertEquals("readOnly 字符输入不触发 onChange", before, changeCount.get());

        // 退格被阻断
        routeKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("readOnly 退格不触发 onChange", before, changeCount.get());

        // 仍可聚焦：caret 可见（focused 态生效）
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("readOnly 仍可聚焦 → caret 可见", CARET_COLOR, caretNode().getBackgroundColor());
    }

    // ==================== 验收 8：disabled 阻断所有 ====================

    /**
     * disabled：字符输入与退格均不触发 onChange。
     */
    @Test
    public void disabledBlocksAllInput() {
        mountInput("seed", SceneInputType.TEXT, MAX_LENGTH, "");
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        // 即便强制聚焦，disabled 也兜底早退
        runtime.requestFocus(inputRoot);

        int before = changeCount.get();
        routeText("x");
        runtime.flush();
        routeKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 字符与退格均不触发 onChange", before, changeCount.get());
    }

    // ==================== 验收 9：caret 可见性随聚焦态 ====================

    /**
     * caret 可见性：focused 时 backgroundColor 非透明（CARET_COLOR），失焦时透明。
     */
    @Test
    public void caretVisibilityFollowsFocus() {
        mountTextInput();
        doLayout();

        // 未聚焦：caret 透明
        Assert.assertEquals("未聚焦 caret 透明", CARET_TRANSPARENT, caretNode().getBackgroundColor());

        // 聚焦：caret 可见
        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertEquals("聚焦后 caret 可见 CARET_COLOR", CARET_COLOR, caretNode().getBackgroundColor());

        // 失焦（聚焦到别的可聚焦节点）：caret 回透明
        SceneNode other = new SceneNode();
        sceneRoot.appendChild(other);
        runtime.focusable(other);
        runtime.requestFocus(other);
        runtime.flush();
        Assert.assertEquals("失焦后 caret 回透明", CARET_TRANSPARENT, caretNode().getBackgroundColor());
    }

    // ==================== 验收 10：caret 位置靠 ROW 自然排到文本末尾 ====================

    /**
     * caret 位置：布局后 caret 在 textNode 之后（caret.x >= textNode.x + textNode.width），
     * 证明 ROW 逐子定位自然把 caret 排到文本末尾右侧；value 变长后 caret x 右移。
     */
    @Test
    public void caretSitsAfterTextAndMovesRightAsTextGrows() {
        mountInput("ab", SceneInputType.TEXT, MAX_LENGTH, "");
        doLayout();

        LayoutBox text = textBox();
        int caretX0 = caretBox().getX();
        Assert.assertTrue("caret 排在 textNode 之后（x >= text.x + text.width）",
                caretX0 >= text.getX() + text.getWidth());

        // value 变长 → 文本宽增加 → caret x 右移（FixedTextMeasurer: 每字符 8px）
        valueSignal.set("abcdef");
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int caretX1 = caretBox().getX();
        Assert.assertTrue("value 变长后 caret x 右移（caretX1 > caretX0）", caretX1 > caretX0);
    }
}
