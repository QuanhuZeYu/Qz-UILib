package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
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
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * SceneTextArea 基础版端到端单元测试。
 *
 * <p>覆盖受控多行文本、按行结构、Enter 换行、Backspace 跨行删除、方向键跨行移动、
 * Home/End 行首行尾、点击定位、placeholder、readOnly/disabled、maxLength。</p>
 */
public class SceneTextAreaTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private Signal<String> valueSignal;
    private Signal<Boolean> enabledSignal;
    private Signal<Boolean> readOnlySignal;

    private AtomicInteger changeCount;
    private String lastChangeValue;

    private MountHandle handle;
    private SceneNode inputRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int LINE_HEIGHT = 16;
    private static final int VIEWPORT_HEIGHT = 80;

    private static final String PLACEHOLDER = "输入多行...";

    /** caret 可见色（聚焦态，对标 SceneTextInputTest.CARET_COLOR）。 */
    private static final int CARET_COLOR = SceneChromeTokens.BORDER_FOCUS;
    /** caret 透明色（不可见态）。 */
    private static final int CARET_TRANSPARENT = 0x00000000;
    /** 正常文本色（对标 SceneButtonTest.TEXT_ENABLED）。 */
    private static final int TEXT_PRIMARY = SceneChromeTokens.TEXT_PRIMARY;
    /** placeholder 文本色。 */
    private static final int TEXT_SECONDARY = SceneChromeTokens.TEXT_SECONDARY;
    /** 禁用态文本色。 */
    private static final int TEXT_DISABLED = SceneChromeTokens.TEXT_DISABLED;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    private void mountTextArea(String initialValue) {
        mountTextArea(initialValue, 64);
    }

    private void mountTextArea(String initialValue, int maxLength) {
        valueSignal = Signal.create(initialValue);
        enabledSignal = Signal.create(Boolean.TRUE);
        readOnlySignal = Signal.create(Boolean.FALSE);
        changeCount = new AtomicInteger(0);
        lastChangeValue = null;

        SceneTextArea.Props props = new SceneTextArea.Props(
                valueSignal, enabledSignal, readOnlySignal,
                PLACEHOLDER, maxLength, VIEWPORT_HEIGHT,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneTextArea.create(runtime, props));
        inputRoot = handle.getRoot();
        runtime.flush();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private SceneNode viewportNode() {
        return inputRoot.__getChildren().get(0);
    }

    private SceneNode contentNode() {
        return viewportNode().__getChildren().get(0);
    }

    /**
     * placeholder 独立容器：viewport 的第 1 个子节点（content 之后的兄弟节点）。
     * show 的 anchor 与 placeholder 文本节点挂在此容器，不与 forEach 的 content 共享。
     */
    private SceneNode placeholderContainerNode() {
        return viewportNode().__getChildren().get(1);
    }

    /**
     * 收集所有行节点（ROW 且有 3 个子节点 prefix/caret/suffix）。
     * content 现为 forEach 独占容器，只含行节点；anchor 与 placeholder 文本节点
     * 位于独立 placeholderContainer，不再混入 content。
     */
    private List<SceneNode> rowNodes() {
        List<SceneNode> rows = new ArrayList<>();
        for (SceneNode child : contentNode().__getChildren()) {
            if (child.__getChildren().size() == 3) {
                rows.add(child);
            }
        }
        return rows;
    }

    private SceneNode rowNode(int rowIdx) {
        return rowNodes().get(rowIdx);
    }

    private SceneNode rowPrefix(int rowIdx) {
        return rowNode(rowIdx).__getChildren().get(0);
    }

    private SceneNode rowSuffix(int rowIdx) {
        return rowNode(rowIdx).__getChildren().get(2);
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

    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    private void routeKeyAndFlush(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
        runtime.flush();
    }

    private void clickAt(int absX, int absY) {
        clickAt(absX, absY, 0, 0);
    }

    /**
     * 点击指定绝对坐标，可指定 rootAbs（验证 I12 三层坐标）。
     * rootAbs≠0 时，传入的 absX/absY 应已含 rootAbs（即屏幕绝对坐标），hitTester 内部 nodeAbs 也含 rootAbs，命中正确。
     */
    private void clickAt(int absX, int absY, int rootAbsX, int rootAbsY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN,
                absX, absY, SceneMouseButton.LEFT, 0, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, rootAbsX, rootAbsY);
        runtime.flush();
    }

    /** 把外部 value 回写到 lastChangeValue 并 flush + layout，模拟受控回路闭合。 */
    private void syncValue() {
        valueSignal.set(lastChangeValue);
        runtime.flush();
        doLayout();
    }

    private void assertRowText(int rowIdx, String prefix, String suffix) {
        runtime.flush();
        doLayout();
        Assert.assertEquals("行" + rowIdx + " prefix", prefix, rowPrefix(rowIdx).getText());
        Assert.assertEquals("行" + rowIdx + " suffix", suffix, rowSuffix(rowIdx).getText());
    }

    // ==================== 受控契约 ====================

    @Test
    public void controlledInputRaisesOnChangeWithoutSelfMutate() {
        mountTextArea("");
        doLayout();
        runtime.requestFocus(contentNode());

        routeText("a");
        runtime.flush();
        Assert.assertEquals("输入 a 触发 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 上抛 a", "a", lastChangeValue);
        Assert.assertEquals("外部未回写时 value 仍空", "", valueSignal.get());

        valueSignal.set("a");
        runtime.flush();
        routeText("b");
        runtime.flush();
        Assert.assertEquals("基于回写后的 value 插入 b", "ab", lastChangeValue);
    }

    // ==================== 多行结构 ====================

    @Test
    public void multiLineValueProducesRowPerLine() {
        mountTextArea("ab\ncd\nef");
        doLayout();
        Assert.assertEquals("3 行值 → 3 行节点", 3, rowNodes().size());
        // caret 初始 0，各行 prefix 空、suffix 为整行
        assertRowText(0, "", "ab");
        assertRowText(1, "", "cd");
        assertRowText(2, "", "ef");
    }

    @Test
    public void emptyValueProducesSingleEmptyRow() {
        mountTextArea("");
        doLayout();
        Assert.assertEquals("空值仍建 1 行", 1, rowNodes().size());
    }

    // ==================== Enter 换行 ====================

    @Test
    public void enterAtEndAppendsNewline() {
        mountTextArea("ab");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 初始 0，移到末尾
        routeKeyAndFlush(SceneKey.END);
        routeKeyAndFlush(SceneKey.ENTER);
        syncValue();

        Assert.assertEquals("末尾 Enter 追加 \\n", "ab\n", lastChangeValue);
        Assert.assertEquals("追加后 2 行", 2, rowNodes().size());
    }

    @Test
    public void enterInMiddleSplitsLine() {
        mountTextArea("abcd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 移到 index 2（ab|cd）：END 到末(4)，LEFT×2 到 2
        routeKeyAndFlush(SceneKey.END);
        routeKeyAndFlush(SceneKey.ARROW_LEFT);
        routeKeyAndFlush(SceneKey.ARROW_LEFT);
        routeKeyAndFlush(SceneKey.ENTER);
        syncValue();

        Assert.assertEquals("中间 Enter 拆行", "ab\ncd", lastChangeValue);
        Assert.assertEquals("拆行后 2 行", 2, rowNodes().size());
        // caret 在插入后 = 3（行1行首），行0 全在 caret 前
        assertRowText(0, "ab", "");
        assertRowText(1, "", "cd");
    }

    // ==================== Backspace 跨行删除 ====================

    @Test
    public void backspaceAtLineStartMergesWithPreviousLine() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 移到行1行首：DOWN 到行1列0（index 3）
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        routeKeyAndFlush(SceneKey.BACKSPACE);
        syncValue();

        Assert.assertEquals("Backspace 合并行", "abcd", lastChangeValue);
        Assert.assertEquals("合并后 1 行", 1, rowNodes().size());
    }

    // ==================== 方向键跨行移动 ====================

    @Test
    public void arrowDownThenUpReturnsToSamePosition() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 0 → DOWN 到行1列0（index3）→ prefix 行1 空，suffix cd
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        assertRowText(1, "", "cd");
        // UP 回行0列0
        routeKeyAndFlush(SceneKey.ARROW_UP);
        assertRowText(0, "", "ab");
    }

    @Test
    public void arrowUpFromFirstLineGoesToStart() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_UP);
        assertRowText(0, "", "ab");
    }

    @Test
    public void arrowDownPastLastLineGoesToEnd() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        // 超出末行 → 全局末（index5），行1 suffix 空
        assertRowText(1, "cd", "");
    }

    // ==================== Home/End 行首行尾 ====================

    @Test
    public void homeMovesToLineStart() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 到行1末：DOWN + END
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        routeKeyAndFlush(SceneKey.END);
        // Home → 行1行首
        routeKeyAndFlush(SceneKey.HOME);
        assertRowText(1, "", "cd");
    }

    @Test
    public void endMovesToLineEnd() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        routeKeyAndFlush(SceneKey.END);
        assertRowText(0, "ab", "");
    }

    // ==================== 点击定位 ====================

    @Test
    public void clickPositionsCaretToClickedRowAndColumn() {
        mountTextArea("aaaa\nbbbb");
        doLayout();
        runtime.requestFocus(contentNode());

        // 点击行1的第2个字符后
        int contentAbsY = absoluteY(contentNode());
        int contentAbsX = absoluteX(contentNode());
        int targetY = contentAbsY + LINE_HEIGHT + 1;
        int targetX = contentAbsX + STUB_CHAR_WIDTH * 2 + 1;
        clickAt(targetX, targetY);
        doLayout();

        assertRowText(1, "bb", "bb");
    }

    // ==================== placeholder ====================

    @Test
    public void placeholderShownWhenValueEmpty() {
        mountTextArea("");
        doLayout();
        Assert.assertEquals("空值 1 行 + placeholder 显示", 1, rowNodes().size());
    }

    /**
     * 回归锚点：placeholder 节点必须真正插入树（修复 forEach/show 共享 content 时
     * anchor 被 applyChildReconcile 误删导致 placeholder 无法插入树的 bug）。
     *
     * <p>修复后 show 挂在独立 placeholderContainer 上，空值未聚焦时 placeholder
     * 文本节点应出现在 placeholderContainer 的 children 中（anchor 之外多一个文本节点）。</p>
     */
    @Test
    public void placeholderNodeInsertedWhenValueEmpty() {
        mountTextArea("");
        doLayout();
        runtime.flush();
        SceneNode phc = placeholderContainerNode();
        // placeholderContainer 至少含 show 的 anchor；isPlaceholder=true 时还应含 placeholder 文本节点
        Assert.assertTrue("placeholderContainer 应含 anchor + placeholder 文本节点",
                phc.__getChildren().size() >= 2);
        // 找出 placeholder 文本节点（非 anchor，文本等于 PLACEHOLDER）
        SceneNode phNode = null;
        for (SceneNode child : phc.__getChildren()) {
            if (PLACEHOLDER.equals(child.getText())) {
                phNode = child;
                break;
            }
        }
        Assert.assertNotNull("placeholder 文本节点应插入树", phNode);
        Assert.assertEquals("placeholder 文本内容", PLACEHOLDER, phNode.getText());
    }

    /**
     * 回归锚点：聚焦时 isPlaceholder 变 false，show 卸载 placeholder 文本节点，
     * placeholderContainer 只剩 anchor（零尺寸占位）。
     */
    @Test
    public void placeholderNodeRemovedWhenFocused() {
        mountTextArea("");
        doLayout();
        runtime.flush();
        // 聚焦 → isPlaceholder=false → show 卸载 placeholder 文本节点
        runtime.requestFocus(contentNode());
        runtime.flush();
        doLayout();
        SceneNode phc = placeholderContainerNode();
        // 只剩 anchor 一个节点
        Assert.assertEquals("聚焦后 placeholderContainer 只剩 anchor", 1, phc.__getChildren().size());
        for (SceneNode child : phc.__getChildren()) {
            Assert.assertNull("聚焦后不应有 placeholder 文本节点", child.getText());
        }
    }

    @Test
    public void placeholderHiddenWhenValueNonEmpty() {
        mountTextArea("ab");
        doLayout();
        Assert.assertEquals("非空值 1 行", 1, rowNodes().size());
    }

    // ==================== readOnly / disabled ====================

    @Test
    public void readOnlyBlocksTextInsert() {
        mountTextArea("ab");
        doLayout();
        runtime.requestFocus(contentNode());
        readOnlySignal.set(Boolean.TRUE);
        runtime.flush();

        int before = changeCount.get();
        routeText("c");
        runtime.flush();
        Assert.assertEquals("readOnly 阻断文本插入", before, changeCount.get());
    }

    @Test
    public void disabledBlocksTextInsert() {
        mountTextArea("ab");
        doLayout();
        runtime.requestFocus(contentNode());
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();

        int before = changeCount.get();
        routeText("c");
        runtime.flush();
        Assert.assertEquals("disabled 阻断文本插入", before, changeCount.get());
    }

    @Test
    public void readOnlyBlocksEnterButAllowsCaretMove() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        readOnlySignal.set(Boolean.TRUE);
        runtime.flush();

        int beforeChange = changeCount.get();
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("readOnly 阻断 Enter", beforeChange, changeCount.get());

        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        assertRowText(1, "", "cd");
    }

    // ==================== maxLength ====================

    @Test
    public void maxLengthRejectsInsertWhenFull() {
        mountTextArea("ab", 4);
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 移到末尾
        routeKeyAndFlush(SceneKey.END);
        routeText("cd");
        runtime.flush();
        syncValue();
        Assert.assertEquals("未满可插", "abcd", lastChangeValue);

        routeText("e");
        runtime.flush();
        Assert.assertEquals("满后拒绝新增", "abcd", lastChangeValue);
    }

    // ==================== Delete 键 ====================

    @Test
    public void deleteAtCaretRemovesFollowingCodepoint() {
        mountTextArea("abcd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 在 0，Delete 删 'a' → "bcd"
        routeKeyAndFlush(SceneKey.DELETE);
        syncValue();
        Assert.assertEquals("Delete 删后一码点", "bcd", lastChangeValue);
    }

    @Test
    public void deleteAtNewlineMergesNextLine() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 在行0末尾（index 2，\n 前），Delete 删 \n → "abcd"
        routeKeyAndFlush(SceneKey.END);
        routeKeyAndFlush(SceneKey.DELETE);
        syncValue();
        Assert.assertEquals("Delete 删 \\n 合并行", "abcd", lastChangeValue);
        Assert.assertEquals("合并后 1 行", 1, rowNodes().size());
    }

    @Test
    public void backspaceAtFirstLineStartIsNoop() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        int before = changeCount.get();
        // caret 在 0（首行行首），Backspace 应 no-op
        routeKeyAndFlush(SceneKey.BACKSPACE);
        Assert.assertEquals("首行行首 Backspace 无效", before, changeCount.get());
    }

    // ==================== 码点安全 ====================

    @Test
    public void caretMovesByCodepointForSupplementaryCharacters() {
        // 𝄞（U+1D11E）占 2 char 1 码点
        mountTextArea("a𝄞b");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 0 → RIGHT 应到 index 2（跳过 𝄞 整个码点）
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        // 现在 caret 在 index 2（b 前），prefix 应 "a𝄞"
        assertRowText(0, "a𝄞", "b");
    }

    @Test
    public void backspaceDeletesSupplementaryCodepoint() {
        mountTextArea("a𝄞b");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 移到 index 2（𝄞 后），Backspace 删 𝄞（整码点）→ "ab"
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.BACKSPACE);
        syncValue();
        Assert.assertEquals("Backspace 删补充码点", "ab", lastChangeValue);
    }

    // ==================== 非光标行 caret 不撑满回归 ====================

    /**
     * 回归锚点：非光标行的 caret 节点宽度必须归零（≤ CARET_WIDTH=1），
     * 不能因 setPreferredWidth(0) 语义"取消首选宽度"而回退填满父宽。
     *
     * <p>根因：caret 无文本时 computeWidth 返回 outerWidth（填满父宽），
     * 把同行文本节点推出 row 裁剪区，表现为光标行之后的行不显示。
     * 修复：caret.setText("") 使其走空文本叶分支返回 padH=0。</p>
     */
    @Test
    public void nonCaretRowCaretWidthIsZeroNotFill() {
        mountTextArea("L0\nL1\nL2");
        doLayout();
        runtime.requestFocus(contentNode());
        runtime.flush();
        doLayout();
        // caret 默认在第 0 行：本行 caret 宽=1，非本行宽=0
        Assert.assertEquals("光标行 caret 宽", 1, caretWidth(0));
        Assert.assertEquals("非光标行(1) caret 宽应 0", 0, caretWidth(1));
        Assert.assertEquals("非光标行(2) caret 宽应 0", 0, caretWidth(2));
        // 非光标行文本节点绝对 X 必须在 viewport 可视区内（不被 caret 推出）
        int viewportRight = absoluteX(viewportNode()) + ((LayoutBox) viewportNode().getCachedLayout()).getWidth();
        Assert.assertTrue("行1 suffix 绝对X 应在可视区内",
                absoluteX(rowSuffix(1)) < viewportRight);
        Assert.assertTrue("行2 suffix 绝对X 应在可视区内",
                absoluteX(rowSuffix(2)) < viewportRight);

        // caret 移到第 1 行
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        doLayout();
        Assert.assertEquals("光标移到行1 后行0 caret 宽应 0", 0, caretWidth(0));
        Assert.assertEquals("光标行(1) caret 宽", 1, caretWidth(1));
        Assert.assertEquals("行2 caret 宽应 0", 0, caretWidth(2));
        Assert.assertTrue("行2 suffix 绝对X 应在可视区内",
                absoluteX(rowSuffix(2)) < viewportRight);

        // caret 移到第 2 行（最后一行）：全部行可见
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        doLayout();
        Assert.assertEquals("行0 caret 宽应 0", 0, caretWidth(0));
        Assert.assertEquals("行1 caret 宽应 0", 0, caretWidth(1));
        Assert.assertEquals("光标行(2) caret 宽", 1, caretWidth(2));
        // 行0/行1 文本在 prefix（caret 在后面行），绝对X 从 0 开始，必在可视区
        Assert.assertTrue("行0 prefix 绝对X 应在可视区内",
                absoluteX(rowPrefix(0)) < viewportRight);
        Assert.assertTrue("行1 prefix 绝对X 应在可视区内",
                absoluteX(rowPrefix(1)) < viewportRight);
    }

    private int caretWidth(int rowIdx) {
        SceneNode caret = rowNode(rowIdx).__getChildren().get(1);
        Object cached = caret.getCachedLayout();
        if (cached instanceof LayoutBox) {
            return ((LayoutBox) cached).getWidth();
        }
        return -1;
    }

    /**
     * 取行内 caret 节点（row 的第 1 个子节点：prefix/caret/suffix 中的 caret）。
     *
     * @param rowIdx 行号
     * @return caret 节点
     */
    private SceneNode rowCaret(int rowIdx) {
        return rowNode(rowIdx).__getChildren().get(1);
    }

    // ==================== 空行 / 尾空行 / 连续 \n 专项（B2 Step1 回归） ====================

    /**
     * 尾空行：value 以 \n 结尾时，split("\n",-1) 保留尾空串，前缀和构建必须同语义。
     * 验证行数=2（含尾空行），caret 落在尾空行时 prefix/suffix 均空。
     */
    @Test
    public void trailingNewlineProducesTrailingEmptyRow() {
        mountTextArea("ab\n");
        doLayout();
        Assert.assertEquals("尾 \\n → 2 行（含尾空行）", 2, rowNodes().size());
        // 行0=ab，行1=空（尾空行）
        assertRowText(0, "", "ab");
        assertRowText(1, "", "");
    }

    /**
     * 连续 \n 产生中间空行：caretRow 边界（caret ≤ end 归当前行）必须逐位等价。
     * value="a\n\nb"：行0="a"(end=1)，行1=""(end=2)，行2="b"(end=3)。
     */
    @Test
    public void consecutiveNewlinesProduceEmptyMiddleRow() {
        mountTextArea("a\n\nb");
        doLayout();
        Assert.assertEquals("连续 \\n → 3 行", 3, rowNodes().size());
        assertRowText(0, "", "a");
        assertRowText(1, "", "");
        assertRowText(2, "", "b");
    }

    /**
     * caretRow 边界：caret 恰好等于行末码点索引时，必须归当前行（≤ end 语义）。
     * value="ab\ncd"：行0 end=2，行1 end=5。
     * - caret=2（行0末）→ 行0
     * - caret=3（行1首）→ 行1
     * 用 Home/End + 方向键驱动 caret 到边界，验证行归属。
     */
    @Test
    public void caretRowBoundaryEndBelongsToCurrentLine() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 到行0末（index 2）：END
        routeKeyAndFlush(SceneKey.END);
        // 此时 caret=2，应属行0；DOWN 应到行1列2（clamp 到行1末=2，index 3+2=5）
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        // caret=5（行1末），行1 prefix=cd suffix=""
        assertRowText(1, "cd", "");
        // 再 DOWN 超出末行 → 全局末（5），不变
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        assertRowText(1, "cd", "");
    }

    /**
     * 连续 \n 中间空行的 Up/Down 列 clamp：caret 在空行上下移动时列 clamp 到 0，
     * 且经空行后列记忆丢失（原实现每次从 caret 重算 col，不持久化列）。
     * value="abcd\n\nefgh"：行0="abcd"(len4)，行1=""(len0)，行2="efgh"(len4)。
     * caret 在行0列2（index2）→ DOWN 到行1（空行，clamp col=0，index5）→ DOWN 到行2列0（index6）。
     */
    @Test
    public void verticalMoveAcrossEmptyLineClampsColumnToZero() {
        mountTextArea("abcd\n\nefgh");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 0 → RIGHT×2 到 index 2（行0列2）
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        // DOWN 到行1（空行）：col=min(2,0)=0，index=5（行1首）
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        assertRowText(1, "", "");
        // 行0 此时 caret 已离开（caret=5 > 行0 end=4），clamp 到行末 → prefix=整行
        assertRowText(0, "abcd", "");
        // 再 DOWN 到行2：col=min(0,4)=0，index=6（行2首）
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        assertRowText(2, "", "efgh");
        // UP 回行1（空行）：col=min(0,0)=0，index=5
        routeKeyAndFlush(SceneKey.ARROW_UP);
        assertRowText(1, "", "");
        // UP 回行0：经空行后列记忆丢失，col=min(0,4)=0，index=0
        routeKeyAndFlush(SceneKey.ARROW_UP);
        assertRowText(0, "", "abcd");
    }

    /**
     * 尾空行 + Home/End：caret 在尾空行时 Home/End 都到 index=总码点数。
     * value="ab\n"：总码点数=3（a,b,\n），行1=""(start=3,end=3)。
     */
    @Test
    public void homeEndOnTrailingEmptyRowStaysAtEnd() {
        mountTextArea("ab\n");
        doLayout();
        runtime.requestFocus(contentNode());
        // caret 到末尾：END（行0末 index2）→ DOWN（行1空 index3）
        routeKeyAndFlush(SceneKey.END);
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        // 此时 caret=3（尾空行），Home/End 都应保持在 3
        routeKeyAndFlush(SceneKey.HOME);
        assertRowText(1, "", "");
        routeKeyAndFlush(SceneKey.END);
        assertRowText(1, "", "");
    }

    /**
     * 缓存命中稳定性：同帧多次读 value 不变时，行结构应稳定不重建。
     * 通过多次方向键往返验证 caret 定位不漂移（间接验证缓存命中后查表一致）。
     */
    @Test
    public void repeatedReadsProduceStableRowStructure() {
        mountTextArea("L0\nL1\nL2\nL3");
        doLayout();
        runtime.requestFocus(contentNode());
        // 反复 DOWN/UP 往返，验证 caret 行归属稳定
        for (int i = 0; i < 5; i++) {
            routeKeyAndFlush(SceneKey.ARROW_DOWN);
            routeKeyAndFlush(SceneKey.ARROW_DOWN);
            routeKeyAndFlush(SceneKey.ARROW_DOWN);
            // 此时 caret 在行3首
            assertRowText(3, "", "L3");
            routeKeyAndFlush(SceneKey.ARROW_UP);
            routeKeyAndFlush(SceneKey.ARROW_UP);
            routeKeyAndFlush(SceneKey.ARROW_UP);
            // 回到行0首
            assertRowText(0, "", "L0");
        }
    }

    // ==================== caret 颜色 + 文本三态色回归（P1-B 上色重构） ====================

    /**
     * 回归锚点：caret 背景色必须按「所在行 + 聚焦」上色，其余情形透明。
     *
     * <p>对标 SceneTextInputTest.placeholderAndCaretVisibilityFollowFocus 的 caret 颜色断言，
     * 适配 TextArea 多行：caret 只在所在行着 BORDER_FOCUS，非所在行恒透明；失焦时所有行透明。</p>
     *
     * <p>覆盖：</p>
     * <ol>
     *   <li>失焦：所有行 caret 背景透明</li>
     *   <li>聚焦 + caret 在行0：行0 = BORDER_FOCUS，行1/行2 透明</li>
     *   <li>caret 移到行1：行1 = BORDER_FOCUS，行0/行2 透明</li>
     *   <li>失焦后：所有行 caret 重新透明</li>
     * </ol>
     */
    @Test
    public void caretColorFollowsFocusAndCaretRow() {
        mountTextArea("L0\nL1\nL2");
        doLayout();
        runtime.flush();
        // 1) 失焦：所有行 caret 透明
        Assert.assertEquals("失焦 行0 caret 透明", CARET_TRANSPARENT, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("失焦 行1 caret 透明", CARET_TRANSPARENT, rowCaret(1).getBackgroundColor());
        Assert.assertEquals("失焦 行2 caret 透明", CARET_TRANSPARENT, rowCaret(2).getBackgroundColor());

        // 2) 聚焦：caret 默认在行0，仅行0 着色
        runtime.requestFocus(contentNode());
        runtime.flush();
        doLayout();
        Assert.assertEquals("聚焦 行0 caret 可见", CARET_COLOR, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("聚焦 行1 caret 透明", CARET_TRANSPARENT, rowCaret(1).getBackgroundColor());
        Assert.assertEquals("聚焦 行2 caret 透明", CARET_TRANSPARENT, rowCaret(2).getBackgroundColor());

        // 3) caret 移到行1：仅行1 着色
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        doLayout();
        Assert.assertEquals("caret 行1 后 行0 caret 透明", CARET_TRANSPARENT, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("caret 行1 后 行1 caret 可见", CARET_COLOR, rowCaret(1).getBackgroundColor());
        Assert.assertEquals("caret 行1 后 行2 caret 透明", CARET_TRANSPARENT, rowCaret(2).getBackgroundColor());

        // 4) 焦点转走到 sceneRoot（非 focusable）：inputRoot 失焦，所有行 caret 重新透明
        runtime.requestFocus(sceneRoot);
        runtime.flush();
        doLayout();
        Assert.assertEquals("失焦后 行0 caret 透明", CARET_TRANSPARENT, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("失焦后 行1 caret 透明", CARET_TRANSPARENT, rowCaret(1).getBackgroundColor());
        Assert.assertEquals("失焦后 行2 caret 透明", CARET_TRANSPARENT, rowCaret(2).getBackgroundColor());
    }

    /**
     * 回归锚点：行内文本色（prefix/suffix）与 placeholder 文本色必须按三态着色。
     *
     * <p>对标 SceneButtonTest.disabledTextColorShouldBeNonWhite 的文本色断言，适配 TextArea：
     * normal=TEXT_PRIMARY、placeholder=TEXT_SECONDARY、disabled=TEXT_DISABLED。</p>
     *
     * <p>覆盖：</p>
     * <ol>
     *   <li>normal 态（非空值 + enabled）：prefix/suffix = TEXT_PRIMARY</li>
     *   <li>placeholder 态（空值 + enabled + 未聚焦）：placeholder 节点 = TEXT_SECONDARY</li>
     *   <li>disabled 态（非空值 + disabled）：prefix/suffix = TEXT_DISABLED</li>
     *   <li>disabled placeholder 态（空值 + disabled）：placeholder 节点 = TEXT_DISABLED</li>
     * </ol>
     */
    /**
     * 回归锚点：行内文本色（prefix/suffix）必须按三态着色。
     *
     * <p>对标 SceneButtonTest.disabledTextColorShouldBeNonWhite 的文本色断言，适配 TextArea：
     * normal=TEXT_PRIMARY、placeholder=TEXT_SECONDARY、disabled=TEXT_DISABLED。
     * 行内 prefix/suffix 文本色由 {@code resolveTextColor(isPlaceholder, enabled)} 三态分支驱动，
     * 与 placeholder 占位节点共享同一套色 token。</p>
     *
     * <p>注：placeholder 占位节点挂在独立 placeholderContainer（viewport 子节点），
     * 与 forEach 的 content 分离，避免 applyChildReconcile 的 children.clear() 误删
     * show 的 anchor。此处通过行内 prefix/suffix 的 textColor 验证三态色逻辑，
     * 覆盖 P1-B {@code resolveTextColor} 分支回归；placeholder 节点本身的插入树
     * 回归由 {@link #placeholderNodeInsertedWhenValueEmpty} 单独覆盖。</p>
     *
     * <p>覆盖：</p>
     * <ol>
     *   <li>placeholder 态（空值 + enabled + 未聚焦）：prefix/suffix = TEXT_SECONDARY</li>
     *   <li>normal 态（非空值 + enabled）：prefix/suffix = TEXT_PRIMARY</li>
     *   <li>disabled 态（非空值 + disabled）：prefix/suffix = TEXT_DISABLED</li>
     *   <li>disabled placeholder 态（空值 + disabled）：prefix/suffix = TEXT_DISABLED</li>
     * </ol>
     */
    @Test
    public void textColorFollowsNormalPlaceholderDisabledStates() {
        // 单次 mount，通过 signal 切换状态，避免同 sceneRoot 重复 mount 累积子树
        mountTextArea("");
        doLayout();
        runtime.flush();

        // 1) placeholder 态：空值 + enabled + 未聚焦 → 行内文本色 = TEXT_SECONDARY
        Assert.assertEquals("placeholder 态 prefix 文本色", TEXT_SECONDARY, rowPrefix(0).getTextColor());
        Assert.assertEquals("placeholder 态 suffix 文本色", TEXT_SECONDARY, rowSuffix(0).getTextColor());

        // 2) normal 态：切非空值 → prefix/suffix = TEXT_PRIMARY
        valueSignal.set("ab");
        runtime.flush();
        doLayout();
        Assert.assertEquals("normal prefix 文本色", TEXT_PRIMARY, rowPrefix(0).getTextColor());
        Assert.assertEquals("normal suffix 文本色", TEXT_PRIMARY, rowSuffix(0).getTextColor());

        // 3) disabled 态：非空值 + disabled → prefix/suffix = TEXT_DISABLED
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("disabled prefix 文本色", TEXT_DISABLED, rowPrefix(0).getTextColor());
        Assert.assertEquals("disabled suffix 文本色", TEXT_DISABLED, rowSuffix(0).getTextColor());

        // 4) disabled placeholder 态：切空值 + disabled → 行内文本色 = TEXT_DISABLED
        valueSignal.set("");
        runtime.flush();
        doLayout();
        Assert.assertEquals("disabled placeholder 态 prefix 文本色", TEXT_DISABLED, rowPrefix(0).getTextColor());
        Assert.assertEquals("disabled placeholder 态 suffix 文本色", TEXT_DISABLED, rowSuffix(0).getTextColor());
    }

    /**
     * 回归锚点：disabled 态文本变灰且 caret 透明（综合验证 disabled 上色不漏项）。
     *
     * <p>对标 SceneButtonTest 试金石 5「disabled 文本色非白」，并叠加 caret 透明断言，
     * 确保 TextArea disabled 态同时满足：文本色 = TEXT_DISABLED（非 TEXT_PRIMARY）、
     * caret 背景透明（不可见）、且文本色绝不等于正常态文本色。</p>
     */
    @Test
    public void disabledTextTurnsGrayAndCaretTransparent() {
        mountTextArea("L0\nL1");
        doLayout();
        runtime.requestFocus(contentNode());
        runtime.flush();
        doLayout();
        // 聚焦 enabled 基线：行0 caret 可见、文本色 = TEXT_PRIMARY
        Assert.assertEquals("enabled 聚焦 行0 caret 可见", CARET_COLOR, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("enabled 文本色", TEXT_PRIMARY, rowPrefix(0).getTextColor());

        // 切 disabled
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        // disabled 态：所有行 caret 透明
        Assert.assertEquals("disabled 行0 caret 透明", CARET_TRANSPARENT, rowCaret(0).getBackgroundColor());
        Assert.assertEquals("disabled 行1 caret 透明", CARET_TRANSPARENT, rowCaret(1).getBackgroundColor());
        // disabled 态：文本色变灰 = TEXT_DISABLED，且不等于正常态 TEXT_PRIMARY
        Assert.assertEquals("disabled 行0 prefix 文本色变灰", TEXT_DISABLED, rowPrefix(0).getTextColor());
        Assert.assertEquals("disabled 行0 suffix 文本色变灰", TEXT_DISABLED, rowSuffix(0).getTextColor());
        Assert.assertNotEquals("disabled 文本色绝不等于正常态",
                TEXT_PRIMARY, rowPrefix(0).getTextColor());
        Assert.assertNotEquals("disabled 文本色绝不等于正常态",
                TEXT_PRIMARY, rowSuffix(0).getTextColor());
    }

    // ==================== 点击前缀宽数组缓存（缓存②）复用/失效 ====================

    /**
     * 用 CountingTextMeasurer 重建 runtime，供缓存②测试计数 measureTextWidth 调用。
     */
    private CountingTextMeasurer rebuildWithCountingMeasurer() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
        CountingTextMeasurer measurer = new CountingTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        return measurer;
    }

    /**
     * 在指定行列点击（基于 content 绝对坐标 + 行高 + 字符宽推算 absX/absY）。
     * 仅路由点击事件并 flush，不触发 doLayout——避免布局期文本测量污染 measureCount。
     *
     * @param row    目标行号（0-based）
     * @param col    目标列（码点数，决定 X 偏移；落在字符中点之后归下一格）
     */
    private void clickRowCol(int row, int col) {
        int contentAbsY = absoluteY(contentNode());
        int contentAbsX = absoluteX(contentNode());
        int targetY = contentAbsY + LINE_HEIGHT * row + 1;
        int targetX = contentAbsX + STUB_CHAR_WIDTH * col + 1;
        clickAt(targetX, targetY);
    }

    /**
     * 缓存②复用与失效边界：参考 SceneTextInputTest.clickPositionReusesPrefixWidthCacheUntilDisplayOrEpochChanges，
     * 适配 TextArea 多行点击路径。
     *
     * <p>覆盖：</p>
     * <ol>
     *   <li>同行同字号同 epoch 第二次点击 measureCount 不增长（缓存命中）</li>
     *   <li>字号变化失效重建（measureCount 增长）</li>
     *   <li>textMeasureEpoch 变化失效重建（measureCount 增长）</li>
     *   <li>不同行点击失效重建（缓存只存最近点击行，measureCount 增长）</li>
     * </ol>
     */
    @Test
    public void clickPositionReusesClickPrefixWidthCacheUntilDisplayFontSizeEpochOrRowChanges() {
        CountingTextMeasurer measurer = rebuildWithCountingMeasurer();
        // 两行各 4 字符：行0="aaaa"，行1="bbbb"
        mountTextArea("aaaa\nbbbb");
        doLayout();

        // 1) 首次点击行1列2：为行1 "bbbb" 构建前缀宽，4 个码点 → 4 次 measureTextWidth
        measurer.resetMeasureCount();
        clickRowCol(1, 2);
        Assert.assertEquals("首次点击行1 应为 4 个码点构建前缀宽", 4, measurer.getMeasureCount());

        // 2) 同行同字号同 epoch 第二次点击：缓存命中，measureCount 不增长
        clickRowCol(1, 1);
        Assert.assertEquals("同行同字号同 epoch 第二次点击应复用缓存", 4, measurer.getMeasureCount());

        // 3) 字号变化失效重建：改 root fontSize 后点击同行
        inputRoot.setFontSize(inputRoot.getFontSize() + 4);
        doLayout();
        measurer.resetMeasureCount();
        clickRowCol(1, 2);
        Assert.assertTrue("字号变化后应重建前缀宽（measureCount > 0）",
                measurer.getMeasureCount() > 0);
        Assert.assertEquals("重建仍为 4 个码点构建", 4, measurer.getMeasureCount());

        // 4) textMeasureEpoch 变化失效重建
        measurer.resetMeasureCount();
        // 先点一次填缓存（同字号同 epoch）
        clickRowCol(1, 2);
        Assert.assertEquals("epoch 未变应复用缓存", 0, measurer.getMeasureCount());
        // 改 epoch
        measurer.setEpoch(measurer.getEpoch() + 1);
        clickRowCol(1, 2);
        Assert.assertEquals("epoch 变化后应重建前缀宽", 4, measurer.getMeasureCount());

        // 5) 不同行点击失效重建：缓存只存最近点击行，切到行0 应重建
        measurer.resetMeasureCount();
        clickRowCol(0, 2);
        Assert.assertEquals("切到不同行应重建前缀宽（行0 aaaa 4 码点）", 4, measurer.getMeasureCount());
        // 同行再点应命中
        clickRowCol(0, 1);
        Assert.assertEquals("同行再点应复用缓存", 4, measurer.getMeasureCount());
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
        SceneTextArea.Props fromBuilder = SceneTextArea.Props.builder(value)
                .enabled(enabled).readOnly(readOnly).placeholder("p").maxLength(64)
                .viewportHeight(VIEWPORT_HEIGHT).onChange(onChange)
                .build();
        SceneTextArea.Props fromCanonical = new SceneTextArea.Props(
                value, enabled, readOnly, "p", 64, VIEWPORT_HEIGHT, onChange);

        Assert.assertSame("value 引用一致", value, fromBuilder.value());
        Assert.assertSame("enabled 引用一致", enabled, fromBuilder.enabled());
        Assert.assertSame("readOnly 引用一致", readOnly, fromBuilder.readOnly());
        Assert.assertEquals("placeholder 一致", fromCanonical.placeholder(), fromBuilder.placeholder());
        Assert.assertEquals("maxLength 一致", fromCanonical.maxLength(), fromBuilder.maxLength());
        Assert.assertEquals("viewportHeight 一致", fromCanonical.viewportHeight(), fromBuilder.viewportHeight());
        Assert.assertSame("onChange 引用一致", onChange, fromBuilder.onChange());
        Assert.assertEquals("Builder 与 canonical Props 应 record equals 等价", fromCanonical, fromBuilder);
    }

    /**
     * 计数文本度量器，用于验证点击前缀宽数组缓存②的失效边界。
     * 与 SceneTextInputTest.CountingTextMeasurer 同构。
     */
    private static final class CountingTextMeasurer implements SceneTextMeasurer {
        /** 单字符宽度。 */
        private final int charWidth;
        /** 行高。 */
        private final int lineHeight;
        /** 当前度量纪元。 */
        private int epoch;
        /** measureWidth 调用次数。 */
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

        /** 重置测量调用次数。 */
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

        /**
         * 获取当前度量纪元。
         *
         * @return 当前度量纪元
         */
        private int getEpoch() {
            return epoch;
        }
    }

    // ==================== I12：rootAbs≠0 时点击 caret 定位不偏移 ====================

    /**
     * I12 坐标系对齐：rootAbsX/Y≠0 时，点击 TextArea 行1第2个字符后，
     * caret 仍定位到 row=1 col=2（prefix="bb"），与 rootAbs=0 时一致。
     *
     * <p>修复前 SceneTextAreaPrimitive 用 ev.getPointerX/Y()（raw，含 rootAbs）与
     * absoluteBox(content,0,0)（host 局部）混比，rootAbs≠0 时 relY/localX 多算一个 rootAbs，
     * 行号与列号错位。修复后用 ctx.getLocalPointerX/Y()（两层坐标，= raw - absoluteBox(content,treeAbs)
     * = content 真局部），rootAbs≠0 不再错位。</p>
     */
    @Test
    public void clickCaretPositionCorrectWithNonZeroRootAbs() {
        mountTextArea("aaaa\nbbbb");
        doLayout();
        runtime.requestFocus(contentNode());

        int contentAbsY = absoluteY(contentNode());
        int contentAbsX = absoluteX(contentNode());
        int rootAbsX = 60;
        int rootAbsY = 50;
        // 屏幕绝对坐标 = content 绝对 + 偏移 + rootAbs
        int targetY = contentAbsY + LINE_HEIGHT + 1 + rootAbsY;
        int targetX = contentAbsX + STUB_CHAR_WIDTH * 2 + 1 + rootAbsX;
        clickAt(targetX, targetY, rootAbsX, rootAbsY);
        doLayout();

        assertRowText(1, "bb", "bb");
    }
}
