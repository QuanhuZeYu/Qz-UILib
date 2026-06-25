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
        rt.forEach(content, rowIndices, idx -> idx, rowIdx -> buildRow(rt, props, caretIndex, caretColor, rowIdx));

        // placeholder：value 空且未聚焦时显示单行占位文本
        rt.show(content, isPlaceholder, () -> {
            SceneNode ph = new SceneNode();
            ph.setText(nullSafe(placeholder));
            ph.setHitTestable(false);
            return ph;
        });

        // 纵向滚动
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        rt.focusable(root);

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
            int scroll = scrollSignal.get().intValue();
            int fontSizePx = root.getFontSize();
            int lineH = rt.lineHeight(fontSizePx);
            // content 绝对 Y（布局位置，不含滚动偏移）
            int contentTop = absoluteY(content);
            int relY = ev.getPointerY() - contentTop + scroll;
            int row = Math.max(0, Math.min(countLines(value) - 1, relY / lineH));
            // 行内 X
            String[] lines = splitLines(value);
            String lineText = lines[row];
            int rowAbsX = absoluteX(content);
            // content 绝对 X 已含 root/viewport padding 布局偏移，不再额外扣除
            int localX = ev.getPointerX() - rowAbsX;
            int[] prefixWidths = buildPrefixWidths(rt, lineText, fontSizePx);
            int col = caretIndexFromX(prefixWidths, localX);
            caretIndex.set(Integer.valueOf(lineStartIndex(value, row) + col));
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
                caretIndex.set(Integer.valueOf(moveCaretVertical(cur, caretPos, -1)));
                return;
            }
            if (key == SceneKey.ARROW_DOWN) {
                caretIndex.set(Integer.valueOf(moveCaretVertical(cur, caretPos, 1)));
                return;
            }
            if (key == SceneKey.HOME) {
                caretIndex.set(Integer.valueOf(lineStartIndex(cur, caretRow(cur, caretPos))));
                return;
            }
            if (key == SceneKey.END) {
                int row = caretRow(cur, caretPos);
                caretIndex.set(Integer.valueOf(lineEndIndex(cur, row)));
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
                                       Signal<Integer> caretColor, Integer rowIdx) {
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
        row.appendChild(caret);

        SceneNode suffix = new SceneNode();
        suffix.setHitTestable(false);
        row.appendChild(suffix);

        // 行内 prefix 文本：caret 前部分
        rt.bind(Invalidation.LAYOUT,
                Computed.create(() -> rowPrefixText(props.value().get(), caretIndex.get(), rowIdx.intValue())),
                prefix::setText);
        // 行内 suffix 文本：caret 后部分
        rt.bind(Invalidation.LAYOUT,
                Computed.create(() -> rowSuffixText(props.value().get(), caretIndex.get(), rowIdx.intValue())),
                suffix::setText);

        // caret 是否在本行：抽单个 Computed 复用，避免重复求值
        Computed<Boolean> inRow = Computed.create(() ->
                Boolean.valueOf(isCaretInRow(props.value().get(), caretIndex.get(), rowIdx.intValue())));
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
     * 第 row 行起始全局码点索引。
     */
    private static int lineStartIndex(String text, int row) {
        String t = nullSafe(text);
        String[] lines = splitLines(t);
        int idx = 0;
        for (int i = 0; i < row && i < lines.length; i++) {
            idx += codePointCount(lines[i]) + 1; // +1 跳过 \n
        }
        return idx;
    }

    /**
     * 第 row 行结束全局码点索引（不含 \n，即行末 caret 位置）。
     */
    private static int lineEndIndex(String text, int row) {
        String[] lines = splitLines(nullSafe(text));
        if (row < 0 || row >= lines.length) {
            return 0;
        }
        return lineStartIndex(text, row) + codePointCount(lines[row]);
    }

    /**
     * caret 所在行号。
     */
    private static int caretRow(String text, int caret) {
        String t = nullSafe(text);
        String[] lines = splitLines(t);
        int idx = 0;
        for (int i = 0; i < lines.length; i++) {
            int end = idx + codePointCount(lines[i]);
            if (caret <= end) {
                return i;
            }
            idx = end + 1; // 跳过 \n
        }
        return lines.length - 1;
    }

    /**
     * caret 是否在第 row 行（lineStart <= caret <= lineEnd）。
     */
    private static boolean isCaretInRow(String text, int caret, int row) {
        int start = lineStartIndex(text, row);
        int end = lineEndIndex(text, row);
        return caret >= start && caret <= end;
    }

    /**
     * 第 row 行 caret 前文本。
     */
    private static String rowPrefixText(String value, int caret, int row) {
        String[] lines = splitLines(nullSafe(value));
        if (row < 0 || row >= lines.length) {
            return "";
        }
        int start = lineStartIndex(value, row);
        int end = lineEndIndex(value, row);
        int clamped = Math.max(start, Math.min(end, caret));
        int col = clamped - start;
        return substringByCodePoints(lines[row], 0, col);
    }

    /**
     * 第 row 行 caret 后文本。
     */
    private static String rowSuffixText(String value, int caret, int row) {
        String[] lines = splitLines(nullSafe(value));
        if (row < 0 || row >= lines.length) {
            return "";
        }
        int start = lineStartIndex(value, row);
        int end = lineEndIndex(value, row);
        int clamped = Math.max(start, Math.min(end, caret));
        int col = clamped - start;
        return substringByCodePoints(lines[row], col, codePointCount(lines[row]));
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
     * 垂直移动 caret（Up/Down），保持列位置，跨行 clamp 到行末。
     *
     * @param value  当前值
     * @param caret  当前全局码点索引
     * @param delta  -1 上移，+1 下移
     * @return 新 caret 索引
     */
    private static int moveCaretVertical(String value, int caret, int delta) {
        String t = nullSafe(value);
        String[] lines = splitLines(t);
        int row = caretRow(t, caret);
        int start = lineStartIndex(t, row);
        int col = clampCaretIndex(t, Integer.valueOf(caret)) - start;
        int newRow = row + delta;
        if (newRow < 0) {
            return 0;
        }
        if (newRow >= lines.length) {
            return codePointCount(t);
        }
        int newCol = Math.min(col, codePointCount(lines[newRow]));
        return lineStartIndex(t, newRow) + newCol;
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

    /**
     * 累加节点及祖先的 LayoutBox x，得到相对场景树根的绝对 x。
     */
    private static int absoluteX(SceneNode node) {
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

    /**
     * 累加节点及祖先的 LayoutBox y，得到相对场景树根的绝对 y。
     */
    private static int absoluteY(SceneNode node) {
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
}
