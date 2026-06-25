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
     * 收集所有行节点（ROW 且有 3 个子节点 prefix/caret/suffix）。
     * content 还含 show 的 anchor（零子节点）与可能的 placeholder 文本节点（零子节点）。
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
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN,
                absX, absY, SceneMouseButton.LEFT, 0, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
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
        runtime.requestFocus(inputRoot);

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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.ARROW_RIGHT);
        routeKeyAndFlush(SceneKey.ARROW_UP);
        assertRowText(0, "", "ab");
    }

    @Test
    public void arrowDownPastLastLineGoesToEnd() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
        routeKeyAndFlush(SceneKey.END);
        assertRowText(0, "ab", "");
    }

    // ==================== 点击定位 ====================

    @Test
    public void clickPositionsCaretToClickedRowAndColumn() {
        mountTextArea("aaaa\nbbbb");
        doLayout();
        runtime.requestFocus(inputRoot);

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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
        // caret 在 0，Delete 删 'a' → "bcd"
        routeKeyAndFlush(SceneKey.DELETE);
        syncValue();
        Assert.assertEquals("Delete 删后一码点", "bcd", lastChangeValue);
    }

    @Test
    public void deleteAtNewlineMergesNextLine() {
        mountTextArea("ab\ncd");
        doLayout();
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
        runtime.requestFocus(inputRoot);
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
}
