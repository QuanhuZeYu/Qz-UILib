package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTextAreaPrimitive —— 无样式多行受控文本输入行为核心。
 *
 * <p>只负责结构、输入行为、跨行 caret 状态和按行文本布局绑定，不设置背景、边框、
 * 文本色、caret 色、cursor 或 padding 等 chrome。外观由上层 wrapper 自行组合。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值由外部 {@code value} 唯一持有（含 {@code \n} 换行符）；控件不缓存 value、不自改 value。
 * 内部仅维护 {@code caretIndex} 本地 UI 状态，语义为真实文本的全局码点索引（0..codePointCount）。
 * 所有写入都只经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, clipChildren=true, focusable, padding)
 *   └─ viewport (COLUMN, scrollable=true, clipChildren=true, preferredHeight)
 *        └─ content (COLUMN)  ← forEach 行
 *             ├─ row0 (ROW) → prefix + caret + suffix
 *             ├─ row1 (ROW) → prefix + caret + suffix
 *             └─ ...
 * </pre>
 * 每行常驻 prefix/caret/suffix 三节点；caret 仅在「caret 所在行」宽 1px 且着色，
 * 其余行 caret 节点宽 0 且透明。caret 跨行移动只切宽度/颜色 + 行内文本，不重建节点。
 *
 * <h3>渲染说明</h3>
 * <p>scene 渲染层 {@code drawBaselineAlignedString} 不按 {@code \n} 自动分行，故 TextArea
 * 必须按行拆成独立文本节点。行数据从 value 派生，用 forEach+keyed（key=行号）渲染。</p>
 *
 * <h3>基础版范围</h3>
 * <ul>
 *   <li>支持：Enter 插入换行、Backspace/Delete 跨行删除、Left/Right/Up/Down 跨行移动、
 *       Home/End 行首行尾、点击定位、纵向滚动、placeholder。</li>
 *   <li>不支持：选区、剪贴板、IME 组合态、caret 闪烁、自动换行（soft wrap）、横向滚动、
 *       caret 滚动跟随视口、Shift+Enter。</li>
 * </ul>
 */
public final class SceneTextAreaPrimitive {

    /** caret 宽度（像素，1px 竖线）。 */
    private static final int CARET_WIDTH = 1;
    /** 行内节点间距：必须 0，否则 caret 与文本间多 1px gap。 */
    private static final int ROW_GAP = 0;
    /** caret 透明色（不可见时）。 */
    private static final int CARET_TRANSPARENT = 0x00000000;

    /** 纯静态工厂，禁止实例化。 */
    private SceneTextAreaPrimitive() {
    }

    /**
     * TextArea primitive 输入契约 —— 只包含行为所需数据，不包含样式开关。
     *
     * @param value       当前文本（响应式只读，受控源，含 {@code \n} 换行符）
     * @param enabled     是否启用
     * @param readOnly    是否只读
     * @param placeholder 占位文本
     * @param maxLength   最大长度（按码点数）
     * @param onChange    文本变更回调
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            Consumer<String> onChange
    ) {
    }

    /**
     * TextArea primitive 创建结果，暴露无样式结构节点和派生行为状态。
     *
     * @param root          根节点
     * @param viewport      滚动视口节点
     * @param content       行内容容器节点
     * @param scrollSignal  纵向滚动位置 signal（可观察/编程式滚动）
     * @param caretIndex    caret 全局码点索引 signal
     * @param caretVisible  caret 是否可见（enabled 且 focused）
     * @param caretColor    caret 颜色 signal（wrapper 注入，primitive 内每行 caret 绑定）
     * @param isPlaceholder 当前是否处于空值且有 placeholder 的状态
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode viewport,
            SceneNode content,
            Signal<Integer> scrollSignal,
            ReadableSignal<Integer> caretIndex,
            ReadableSignal<Boolean> caretVisible,
            Signal<Integer> caretColor,
            ReadableSignal<Boolean> isPlaceholder
    ) {
    }

    /**
     * 创建无样式 TextArea primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        final String placeholder = props.placeholder();
        final int maxLength = props.maxLength();

        Signal<Integer> caretIndex = Signal.create(Integer.valueOf(0));
        Signal<Integer> caretColor = Signal.create(Integer.valueOf(CARET_TRANSPARENT));
        // 行结构前缀和缓存（实例级，绝不能静态——多 TextArea 实例会跨实例串味）
        final LineStructureCache lineStructureCache = new LineStructureCache();
        // 点击前缀宽数组缓存（实例级，只缓存"最近点击行"单行一份；失效键含 textMeasureEpoch，字体重载后必失效）
        final ClickPrefixWidthCache clickPrefixWidthCache = new ClickPrefixWidthCache();

        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setClipChildren(true);

        SceneNode viewport = new SceneNode();
        viewport.setFlexDirection(FlexDirection.COLUMN);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        root.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setFlexDirection(FlexDirection.COLUMN);
        viewport.appendChild(content);

        SceneInteractionState is = rt.interactionState(root);
        ReadableSignal<Boolean> caretVisible = Computed.create(
                () -> Boolean.valueOf(Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(is.focused().get())));
        ReadableSignal<Boolean> isPlaceholder = Computed.create(
                () -> Boolean.valueOf(nullSafe(props.value().get()).isEmpty()
                        && !nullSafe(placeholder).isEmpty()
                        && !Boolean.TRUE.equals(is.focused().get())));

        // 行号列表 signal：value 变化时重算行数，驱动 forEach 增删行节点
        Computed<List<Integer>> rowIndices = Computed.create(() -> {
            int lines = countLines(nullSafe(props.value().get()));
            List<Integer> list = new ArrayList<>(lines);
            for (int i = 0; i < lines; i++) {
                list.add(Integer.valueOf(i));
            }
            return list;
        });

        // 按行渲染（key=行号；itemComponent 每 key 只调一次，行内文本靠 Computed 响应 value）
        rt.forEach(content, rowIndices, idx -> idx, rowIdx -> buildRow(rt, props, caretIndex, caretColor, lineStructureCache, rowIdx));

        // placeholder：value 空且未聚焦时显示单行占位文本
        rt.show(content, isPlaceholder, () -> {
            SceneNode ph = new SceneNode();
            ph.setText(nullSafe(placeholder));
            ph.setHitTestable(false);
            return ph;
        });

        // 纵向滚动
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        rt.focusable(root, props.enabled());

        // 点击定位：算行号 + 行内码点
        rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
            LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
            if (rootBox == null || viewportBox == null) {
                return;
            }
            String value = nullSafe(props.value().get());
            if (value.isEmpty()) {
                caretIndex.set(Integer.valueOf(0));
                return;
            }
            int fontSizePx = root.getFontSize();
            int lineH = rt.lineHeight(fontSizePx);
            // content 绝对坐标由 SceneGeometry.absoluteBox 统一注入祖先 scrollable 的 scrollOffsetY
            int contentTop = SceneGeometry.absoluteBox(content, 0, 0).getY();
            int relY = ev.getPointerY() - contentTop;
            int row = Math.max(0, Math.min(countLines(value) - 1, relY / lineH));
            // 行内 X
            String[] lines = splitLines(value);
            String lineText = lines[row];
            int rowAbsX = SceneGeometry.absoluteBox(content, 0, 0).getX();
            // content 绝对 X 已含 root/viewport padding 布局偏移，不再额外扣除
            int localX = ev.getPointerX() - rowAbsX;
            // 跨帧缓存：同行同字号同度量纪元时跳过重复构建；单次构建仍逐边界 measureTextWidth(整前缀)
            int[] prefixWidths = clickPrefixWidthCache.get(rt, lineText, fontSizePx);
            int col = caretIndexFromX(prefixWidths, localX);
            caretIndex.set(Integer.valueOf(lineStartIndex(lineStructureCache, value, row) + col));
        });

        // 文本输入（接受 \n，与单行 primitive 区别）
        rt.on(root, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            String raw = ev.getText();
            if (raw == null || raw.isEmpty()) {
                return;
            }
            String cur = nullSafe(props.value().get());
            int caretPos = clampCaretIndex(cur, caretIndex.get());
            // 过滤控制字符但保留 \n（TextArea 接受换行）
            String filtered = filterForInsert(raw, Math.max(0, maxLength - codePointCount(cur)));
            if (filtered.isEmpty()) {
                return;
            }
            int offset = charOffsetForCodePointIndex(cur, caretPos);
            String next = cur.substring(0, offset) + filtered + cur.substring(offset);
            props.onChange().accept(next);
            caretIndex.set(Integer.valueOf(caretPos + codePointCount(filtered)));
        });

        // 键盘编辑键
        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            String cur = nullSafe(props.value().get());
            int caretPos = clampCaretIndex(cur, caretIndex.get());
            SceneKey key = ev.getKey();
            if (key == SceneKey.ARROW_LEFT) {
                caretIndex.set(Integer.valueOf(Math.max(0, caretPos - 1)));
                return;
            }
            if (key == SceneKey.ARROW_RIGHT) {
                caretIndex.set(Integer.valueOf(Math.min(codePointCount(cur), caretPos + 1)));
                return;
            }
            if (key == SceneKey.ARROW_UP) {
                caretIndex.set(Integer.valueOf(moveCaretVertical(lineStructureCache, cur, caretPos, -1)));
                return;
            }
            if (key == SceneKey.ARROW_DOWN) {
                caretIndex.set(Integer.valueOf(moveCaretVertical(lineStructureCache, cur, caretPos, 1)));
                return;
            }
            if (key == SceneKey.HOME) {
                caretIndex.set(Integer.valueOf(lineStartIndex(lineStructureCache, cur, caretRow(lineStructureCache, cur, caretPos))));
                return;
            }
            if (key == SceneKey.END) {
                int row = caretRow(lineStructureCache, cur, caretPos);
                caretIndex.set(Integer.valueOf(lineEndIndex(lineStructureCache, cur, row)));
                return;
            }
            if (Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            if (key == SceneKey.ENTER) {
                insertAtCaret(cur, caretPos, "\n", props.onChange(), caretIndex);
            } else if (key == SceneKey.BACKSPACE) {
                deleteBeforeCaret(cur, caretPos, props.onChange(), caretIndex);
            } else if (key == SceneKey.DELETE) {
                deleteAfterCaret(cur, caretPos, props.onChange());
            }
        });

        return new Result(root, viewport, content, scrollSignal, caretIndex, caretVisible, caretColor, isPlaceholder);
    }

    /**
     * 构建单行节点（prefix + caret + suffix），行内文本靠 Computed 响应 value 与 caretIndex。
     *
     * @param rt         场景运行时
     * @param props      输入契约
     * @param caretIndex caret 全局码点索引 signal
     * @param caretColor caret 颜色 signal（wrapper 注入）
     * @param rowIdx     当前行号（key，稳定）
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, Signal<Integer> caretIndex,
                                        Signal<Integer> caretColor, LineStructureCache lineStructureCache, Integer rowIdx) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(ROW_GAP);
        row.setClipChildren(true);

        SceneNode prefix = new SceneNode();
        prefix.setHitTestable(false);
        row.appendChild(prefix);

        SceneNode caret = new SceneNode();
        caret.setPreferredWidth(CARET_WIDTH);
        caret.setPreferredHeight(rt.lineHeight(caret.getFontSize()));
        caret.setHitTestable(false);
        // 标记为空文本叶：computeWidth 对 text==null 的无文本叶返回 outerWidth（填满父宽），
        // 会把同行文本节点推出 row 裁剪区。setText("") 使其走 text.isEmpty() 分支返回 padH=0，
        // 确保 setPreferredWidth(0)（非本行）时宽度真正归零，不撑满行。
        caret.setText("");
        row.appendChild(caret);

        SceneNode suffix = new SceneNode();
        suffix.setHitTestable(false);
        row.appendChild(suffix);

        // 行内 prefix 文本：caret 前部分
        rt.bind(Invalidation.LAYOUT,
                Computed.create(() -> rowPrefixText(lineStructureCache, props.value().get(), caretIndex.get(), rowIdx.intValue())),
                prefix::setText);
        // 行内 suffix 文本：caret 后部分
        rt.bind(Invalidation.LAYOUT,
                Computed.create(() -> rowSuffixText(lineStructureCache, props.value().get(), caretIndex.get(), rowIdx.intValue())),
                suffix::setText);

        // caret 是否在本行：抽单个 Computed 复用，避免重复求值
        Computed<Boolean> inRow = Computed.create(() ->
                Boolean.valueOf(isCaretInRow(lineStructureCache, props.value().get(), caretIndex.get(), rowIdx.intValue())));
        // 切换宽度（LAYOUT 级，仅 caret 移动时触发，可接受）
        rt.bind(Invalidation.LAYOUT, inRow,
                v -> caret.setPreferredWidth(Boolean.TRUE.equals(v) ? CARET_WIDTH : 0));
        // caret 颜色：本行用注入色，非本行透明
        rt.bind(Invalidation.PAINT,
                Computed.create(() -> Boolean.TRUE.equals(inRow.get())
                        ? caretColor.get().intValue() : CARET_TRANSPARENT),
                caret::setBackgroundColor);

        return row;
    }

    // ==================== 文本几何工具 ====================

    /**
     * null 安全：null → 空串。
     */
    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 计算字符串码点数。
     */
    private static int codePointCount(String s) {
        String text = nullSafe(s);
        return text.codePointCount(0, text.length());
    }

    /**
     * 统计逻辑行数（按 {@code \n} 切分，空文本视作 1 行）。
     */
    private static int countLines(String text) {
        String t = nullSafe(text);
        if (t.isEmpty()) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * 按 {@code \n} 切分行（保留空行，尾空行保留）。
     */
    private static String[] splitLines(String text) {
        String t = nullSafe(text);
        if (t.isEmpty()) {
            return new String[] {""};
        }
        // split 限制参数 -1 保留尾空串
        return t.split("\n", -1);
    }

    /**
     * 第 row 行起始全局码点索引（查表 O(1)，命中缓存时不重建）。
     */
    private static int lineStartIndex(LineStructureCache cache, String text, int row) {
        LineStructureCache c = cache.get(text);
        if (row < 0 || row >= c.lineLenCp.length) {
            return 0;
        }
        return c.lineStartCp[row];
    }

    /**
     * 第 row 行结束全局码点索引（不含 \n，即行末 caret 位置；查表 O(1)）。
     */
    private static int lineEndIndex(LineStructureCache cache, String text, int row) {
        LineStructureCache c = cache.get(text);
        if (row < 0 || row >= c.lineLenCp.length) {
            return 0;
        }
        return c.lineStartCp[row] + c.lineLenCp[row];
    }

    /**
     * caret 所在行号（在 lineStartCp 上二分查找，复刻「caret ≤ lineEnd 归当前行」边界）。
     */
    private static int caretRow(LineStructureCache cache, String text, int caret) {
        LineStructureCache c = cache.get(text);
        int lineCount = c.lineLenCp.length;
        if (lineCount == 0) {
            return 0;
        }
        int[] starts = c.lineStartCp;
        // 在 [0, lineCount] 中找最后一个 row 使得 starts[row] <= caret
        // starts[lineCount] 是哨兵=总码点数，确保 caret=总码点数 时落到末行
        int lo = 0;
        int hi = lineCount;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= caret) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo >= lineCount) {
            lo = lineCount - 1;
        }
        return lo;
    }

    /**
     * caret 是否在第 row 行（lineStart <= caret <= lineEnd；查表 O(1)）。
     */
    private static boolean isCaretInRow(LineStructureCache cache, String text, int caret, int row) {
        LineStructureCache c = cache.get(text);
        if (row < 0 || row >= c.lineLenCp.length) {
            return false;
        }
        int start = c.lineStartCp[row];
        int end = start + c.lineLenCp[row];
        return caret >= start && caret <= end;
    }

    /**
     * 第 row 行 caret 前文本（在缓存行文本上按列截取）。
     */
    private static String rowPrefixText(LineStructureCache cache, String value, int caret, int row) {
        LineStructureCache c = cache.get(value);
        if (row < 0 || row >= c.lines.length) {
            return "";
        }
        int start = c.lineStartCp[row];
        int end = start + c.lineLenCp[row];
        int clamped = Math.max(start, Math.min(end, caret));
        int col = clamped - start;
        return substringByCodePoints(c.lines[row], 0, col);
    }

    /**
     * 第 row 行 caret 后文本（在缓存行文本上按列截取）。
     */
    private static String rowSuffixText(LineStructureCache cache, String value, int caret, int row) {
        LineStructureCache c = cache.get(value);
        if (row < 0 || row >= c.lines.length) {
            return "";
        }
        int start = c.lineStartCp[row];
        int end = start + c.lineLenCp[row];
        int clamped = Math.max(start, Math.min(end, caret));
        int col = clamped - start;
        return substringByCodePoints(c.lines[row], col, c.lineLenCp[row]);
    }

    /**
     * 将 caret 码点索引钳制到当前 value 合法范围。
     */
    private static int clampCaretIndex(String value, Integer caretIndex) {
        int max = codePointCount(value);
        int index = caretIndex == null ? 0 : caretIndex.intValue();
        return Math.max(0, Math.min(max, index));
    }

    /**
     * 把码点索引转换为 Java char offset。
     */
    private static int charOffsetForCodePointIndex(String value, int index) {
        String text = nullSafe(value);
        int clamped = Math.max(0, Math.min(codePointCount(text), index));
        return text.offsetByCodePoints(0, clamped);
    }

    /**
     * 按码点范围截取字符串。
     */
    private static String substringByCodePoints(String value, int startCp, int endCp) {
        String text = nullSafe(value);
        int max = codePointCount(text);
        int start = Math.max(0, Math.min(max, startCp));
        int end = Math.max(start, Math.min(max, endCp));
        int startOffset = text.offsetByCodePoints(0, start);
        int endOffset = text.offsetByCodePoints(0, end);
        return text.substring(startOffset, endOffset);
    }

    /**
     * 垂直移动 caret（Up/Down），保持列位置，跨行 clamp 到行末（查表 O(1)）。
     *
     * @param cache 行结构前缀和缓存
     * @param value 当前值
     * @param caret 当前全局码点索引
     * @param delta -1 上移，+1 下移
     * @return 新 caret 索引
     */
    private static int moveCaretVertical(LineStructureCache cache, String value, int caret, int delta) {
        LineStructureCache c = cache.get(value);
        int lineCount = c.lineLenCp.length;
        if (lineCount == 0) {
            return 0;
        }
        int row = caretRow(cache, value, caret);
        int start = c.lineStartCp[row];
        int col = clampCaretIndex(value, Integer.valueOf(caret)) - start;
        int newRow = row + delta;
        if (newRow < 0) {
            return 0;
        }
        if (newRow >= lineCount) {
            return codePointCount(value);
        }
        // 复刻 min(col, 目标行码点数) clamp 语义保持列记忆
        int newCol = Math.min(col, c.lineLenCp[newRow]);
        return c.lineStartCp[newRow] + newCol;
    }

    // ==================== 编辑操作 ====================

    /**
     * 在 caret 位置插入文本。
     */
    private static void insertAtCaret(String cur, int caret, String text, Consumer<String> onChange,
                                       Signal<Integer> caretIndex) {
        int offset = charOffsetForCodePointIndex(cur, caret);
        String next = cur.substring(0, offset) + text + cur.substring(offset);
        onChange.accept(next);
        caretIndex.set(Integer.valueOf(caret + codePointCount(text)));
    }

    /**
     * 删除 caret 前一码点（若为 \n 则合并行）。
     */
    private static void deleteBeforeCaret(String cur, int caret, Consumer<String> onChange,
                                          Signal<Integer> caretIndex) {
        if (caret <= 0) {
            return;
        }
        int start = charOffsetForCodePointIndex(cur, caret - 1);
        int end = charOffsetForCodePointIndex(cur, caret);
        onChange.accept(cur.substring(0, start) + cur.substring(end));
        caretIndex.set(Integer.valueOf(caret - 1));
    }

    /**
     * 删除 caret 后一码点。
     */
    private static void deleteAfterCaret(String cur, int caret, Consumer<String> onChange) {
        if (caret >= codePointCount(cur)) {
            return;
        }
        int start = charOffsetForCodePointIndex(cur, caret);
        int end = charOffsetForCodePointIndex(cur, caret + 1);
        onChange.accept(cur.substring(0, start) + cur.substring(end));
    }

    /**
     * 过滤输入并限制本次可插入码点数（保留 \n，过滤其他控制字符）。
     */
    private static String filterForInsert(String input, int available) {
        StringBuilder sb = new StringBuilder();
        int accepted = 0;
        int i = 0;
        while (i < input.length() && accepted < available) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            // 保留 \n；过滤其他控制字符（\r \t 等）
            if (cp == '\n') {
                sb.appendCodePoint(cp);
                accepted++;
                continue;
            }
            if (Character.isISOControl(cp)) {
                continue;
            }
            sb.appendCodePoint(cp);
            accepted++;
        }
        return sb.toString();
    }

    // ==================== 点击定位 ====================

    /**
     * 构建用于点击定位的码点前缀宽度数组。
     */
    private static int[] buildPrefixWidths(SceneRuntime rt, String display, int fontSizePx) {
        String text = nullSafe(display);
        int count = codePointCount(text);
        int[] prefixWidths = new int[count + 1];
        prefixWidths[0] = 0;
        for (int i = 1; i <= count; i++) {
            String prefix = substringByCodePoints(text, 0, i);
            prefixWidths[i] = rt.measureTextWidth(prefix, fontSizePx);
        }
        return prefixWidths;
    }

    /**
     * 根据点击 X 和前缀宽度数组计算最近 caret 码点边界。
     */
    private static int caretIndexFromX(int[] prefixWidths, int localX) {
        int[] widths = prefixWidths == null || prefixWidths.length == 0 ? new int[] {0} : prefixWidths;
        int count = widths.length - 1;
        if (localX <= 0) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            int leftWidth = widths[i];
            int rightWidth = widths[i + 1];
            int midpoint = leftWidth + (rightWidth - leftWidth) / 2;
            if (localX < midpoint) {
                return i;
            }
        }
        return count;
    }

    // ==================== 行结构前缀和缓存（缓存①） ====================

    /**
     * 行结构前缀和缓存：单趟 O(L) 扫描 value 构建，命中时查表 O(1)。
     *
     * <p>实例级（create() 闭包内 final 持有），绝不能静态字段——多 TextArea 实例
     * 会跨实例串味。失效键仅 value.equals(cachedValue)，行结构只依赖 value 字符串
     * 本身（纯字符切分），不依赖 fontSize/epoch。</p>
     *
     * <p>受控不变量：cachedValue 只是脏检测用的不可变 String 引用，不是真值副本，
     * 不得作为 props.value() 的替代读源；缓存对象不暴露 setter，不得被 onChange
     * 之外的路径写。</p>
     */
    private static final class LineStructureCache {
        /** 上次构建依据的 value（仅作 equals 比对判失效，不是真值副本）。 */
        private String cachedValue;
        /** 长度=行数+1；lineStartCp[r] = 第 r 行起始全局码点索引；末元素为总码点数哨兵。 */
        private int[] lineStartCp;
        /** 长度=行数；第 r 行码点数（不含 \n）。 */
        private int[] lineLenCp;
        /** 缓存 split 结果，供 rowPrefix/rowSuffix 取行文本。 */
        private String[] lines;

        /**
         * 获取与 value 匹配的行结构缓存（命中直接返回 this，未命中单趟重建）。
         *
         * @param value 当前文本（可为 null，内部 nullSafe）
         * @return this（已更新到与 value 一致）
         */
        private LineStructureCache get(String value) {
            String safe = nullSafe(value);
            if (cachedValue != null && safe.equals(cachedValue) && lines != null) {
                return this;
            }
            rebuild(safe);
            return this;
        }

        /**
         * 单趟扫描 value 重建行结构前缀和。语义与 split("\n", -1) 一致：保留空行、
         * 尾空行、连续 \n 产生的空行。
         *
         * @param value nullSafe 后的文本（非 null）
         */
        private void rebuild(String value) {
            if (value.isEmpty()) {
                // 空文本视作 1 行（与 countLines/splitLines 一致）
                lineStartCp = new int[] {0, 0};
                lineLenCp = new int[] {0};
                lines = new String[] {""};
                cachedValue = value;
                return;
            }
            // 先数行数（\n 个数 + 1）
            int lineCount = 1;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '\n') {
                    lineCount++;
                }
            }
            lineStartCp = new int[lineCount + 1];
            lineLenCp = new int[lineCount];
            lines = new String[lineCount];
            // 单趟扫描：遇 \n 收尾当前行，否则累加码点
            int row = 0;
            int lineStartChar = 0;   // 当前行起始 char offset
            int lineStartCpIdx = 0;  // 当前行起始全局码点索引
            int cpIdx = 0;           // 当前扫描到的全局码点索引
            int i = 0;
            while (i < value.length()) {
                char ch = value.charAt(i);
                if (ch == '\n') {
                    String lineText = value.substring(lineStartChar, i);
                    int lineCp = codePointCount(lineText);
                    lineStartCp[row] = lineStartCpIdx;
                    lineLenCp[row] = lineCp;
                    lines[row] = lineText;
                    row++;
                    lineStartCpIdx = cpIdx + 1; // 跳过 \n（\n 占 1 个码点）
                    cpIdx++;
                    lineStartChar = i + 1;
                    i++;
                } else {
                    int cp = value.codePointAt(i);
                    i += Character.charCount(cp);
                    cpIdx++;
                }
            }
            // 最后一行（含尾空行：若 value 以 \n 结尾，此处为空串）
            String lastLine = value.substring(lineStartChar);
            int lastCp = codePointCount(lastLine);
            lineStartCp[row] = lineStartCpIdx;
            lineLenCp[row] = lastCp;
            lines[row] = lastLine;
            // 末尾哨兵 = 总码点数，供 caretRow 二分边界
            lineStartCp[lineCount] = lineStartCpIdx + lastCp;
            cachedValue = value;
        }
    }

    // ==================== 点击前缀宽数组缓存（缓存②） ====================

    /**
     * 点击定位前缀宽数组缓存：跨帧复用最近点击行的整前缀宽数组。
     *
     * <p>实例级（create() 闭包内 final 持有），只缓存"最近点击行"单行一份——
     * 点击是离散事件，不必为每行常驻宽数组。绝不能静态字段，多 TextArea 实例会跨实例串味。</p>
     *
     * <p>失效键三元组：行文本串 + fontSize + textMeasureEpoch()。
     * ⚠️ 必须含 textMeasureEpoch()——字体重载后旧宽不可复用。
     * 这与 {@link LineStructureCache} 失效键只有 value.equals 不同：
     * 行结构是纯字符切分不涉测量，而本缓存涉测量，必须随度量纪元失效。</p>
     *
     * <p>像素一致保证：单次构建仍走 {@link #buildPrefixWidths} 逐边界
     * {@code rt.measureTextWidth(整前缀, fontSizePx)} 整测量，缓存只跳过重复构建，
     * 不改变测量方式。scene measureWidth 含 ceil+round 双取整，"逐码点 UI 宽相加"
     * 会漂移，故绝不能用逐码点相加替代整前缀测量。</p>
     */
    private static final class ClickPrefixWidthCache {
        /** 上次测量的行文本。 */
        private String display;
        /** 上次测量的字号像素。 */
        private int fontSizePx;
        /** 上次测量的文本度量纪元（字体重载后递增，使缓存失效）。 */
        private int epoch;
        /** 缓存的整前缀宽数组（逐边界 measureTextWidth(整前缀) 产出）。 */
        private int[] widths;

        /**
         * 获取与当前行文本、字号、度量纪元匹配的前缀宽数组。
         * 命中三元组时直接返回缓存，未命中时调用 buildPrefixWidths 重建并刷新缓存。
         *
         * @param rt         场景运行时
         * @param lineText   当前行文本
         * @param fontSizePx 字号像素
         * @return 前缀宽数组
         */
        private int[] get(SceneRuntime rt, String lineText, int fontSizePx) {
            String safe = nullSafe(lineText);
            int currentEpoch = rt.textMeasureEpoch();
            if (widths != null && safe.equals(this.display)
                    && fontSizePx == this.fontSizePx && currentEpoch == this.epoch) {
                return widths;
            }
            this.display = safe;
            this.fontSizePx = fontSizePx;
            this.epoch = currentEpoch;
            this.widths = buildPrefixWidths(rt, safe, fontSizePx);
            return widths;
        }
    }
}
