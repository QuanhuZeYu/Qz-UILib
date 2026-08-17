package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneTextAreaPrimitive —— 无样式多行受控文本输入行为核心（B6：带跨行选区）。
 *
 * <p>只负责结构、输入行为、跨行 caret/选区状态和按行文本布局绑定，不设置背景、边框、
 * 文本色、caret 色、cursor 或 padding 等 chrome。外观由上层 wrapper 自行组合。
 * 行内文本/caret/选区颜色由 Props 供给的 token 在 primitive 内上色（与 TextInput 不同：
 * TextArea 的 wrapper 把颜色 token 单向供给 primitive）。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值由外部 {@code value} 唯一持有（含 {@code 
} 换行符）；控件不缓存 value、不自改 value。
 * 内部维护 {@code caretIndex} 与 {@link TextSelection} 两个本地 UI 态（caret≡selection.focus，
 * 无选区时 anchor==focus）。所有写入都只经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <h3>结构（B6 五节点行）</h3>
 * <pre>
 * root (COLUMN, clipChildren=true, focusable, padding)
 *   └─ viewport (COLUMN, scrollable=true, clipChildren=true, preferredHeight)
 *        ├─ content (COLUMN)  ← forEach 行（独占，不与 show 共享）
 *        │    └─ row0 (ROW) → prefix + caretBefore + highlight + caretAfter + suffix
 *        │    └─ row1 (ROW) → ...
 *        └─ placeholderContainer (COLUMN)  ← show placeholder（独立容器）
 * </pre>
 * <p>每行常驻五节点；caret 双槽位仅在「caret 所在行 + focus 所在端」宽 1px 着色，
 * 其余行/槽位宽 0 透明。highlight 显示本行选中段（跨行选区中间整行自然全段高亮，
 * 形成块状视觉）。</p>
 *
 * <h3>B6 选区能力</h3>
 * <ul>
 *   <li>鼠标跨行拖选（anchor 固定、focus 随 MOVE 按行+列解析）；Shift+点击/方向键/Home/End 扩展；</li>
 *   <li>双击选词（词不跨行）、三击选整行、Ctrl+A 全选；</li>
 *   <li>TEXT_INPUT/Backspace/Delete/Enter 有选区时替换/删除整段；</li>
 *   <li>readOnly 可选中/可移动/可 Shift 扩展，禁止编辑。</li>
 * </ul>
 *
 * <h3>基础版范围（B6 之外仍不支持）</h3>
 * <p>剪贴板、IME 组合态、caret 闪烁、自动换行（soft wrap）、横向滚动、
 * caret 滚动跟随视口、Shift+Enter。</p>
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
     * <p>颜色 token 由 wrapper 供给（裸值），primitive 内部据此给动态行 caret 与文本节点上色。
     * 样式颜色不在 primitive 内硬编码，数据流 wrapper→primitive 单向供给。</p>
     *
     * @param value              当前文本（响应式只读，受控源，含 {@code 
} 换行符）
     * @param enabled            是否启用
     * @param readOnly           是否只读
     * @param placeholder        占位文本
     * @param maxLength          最大长度（按码点数）
     * @param caretVisibleColor  caret 可见时的颜色（focus 蓝，由 wrapper 传 token）
     * @param textNormalColor    普通文本色（enabled 且非 placeholder）
     * @param textPlaceholderColor placeholder 文本色（enabled 且空值）
     * @param textDisabledColor  禁用态文本色
     * @param onChange           文本变更回调
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            int caretVisibleColor,
            int textNormalColor,
            int textPlaceholderColor,
            int textDisabledColor,
            Consumer<String> onChange
    ) {
    }

    /**
     * TextArea primitive 创建结果，暴露无样式结构节点和派生行为状态。
     *
     * <p>交互/派生状态一律只读（ReadableSignal）；样式颜色由 Props 供给，primitive 代为上色，
     * 不再暴露可写 Signal&lt;color&gt; 供 wrapper 反向注入。</p>
     *
     * @param root          根节点
     * @param viewport      滚动视口节点
     * @param content       行内容容器节点（forEach 独占）
     * @param placeholderContainer placeholder 容器节点（show 独占，与 content 分离）
     * @param scrollSignal  纵向滚动位置 signal（可观察/编程式滚动）
     * @param caretIndex    caret 全局码点索引 signal（=selection.focus 投影）
     * @param selection     选区状态 signal（本地 UI 态，anchor/focus 全局码点索引）
     * @param caretVisible  caret 是否可见（enabled 且 focused）
     * @param isPlaceholder 当前是否处于空值且有 placeholder 的状态
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode viewport,
            SceneNode content,
            SceneNode placeholderContainer,
            Signal<Integer> scrollSignal,
            ReadableSignal<Integer> caretIndex,
            ReadableSignal<TextSelection> selection,
            ReadableSignal<Boolean> caretVisible,
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
        // 输入 handler 读取同步真值；signal 只保留帧末响应式投影语义。
        final int[] caretAuthority = {0};
        final Signal<TextSelection> selection = Signal.create(TextSelection.collapsed(0));
        // 选区写入唯一汇点：三态（caretAuthority / caretIndex / selection）同步，caret≡focus。
        final BiConsumer<Integer, Integer> setSelection = (anchor, focus) -> {
            caretAuthority[0] = focus.intValue();
            caretIndex.set(focus);
            selection.set(TextSelection.of(anchor.intValue(), focus.intValue()));
        };
        final Consumer<Integer> setCaretIndex = next -> setSelection.accept(next, next);
        // 拖选瞬态：>=0 表示拖选中且该值为 anchor；-1 表示未拖选。
        final int[] dragAnchor = {-1};
        // 行结构前缀和缓存（实例级，绝不能静态——多 TextArea 实例会跨实例串味）
        final LineStructureCache lineStructureCache = new LineStructureCache();
        // 点击前缀宽数组缓存（实例级，只缓存"最近点击行"单行一份；失效键含 textMeasureEpoch，字体重载后必失效）
        final SceneTextGeometry.PrefixWidthCache clickPrefixWidthCache = new SceneTextGeometry.PrefixWidthCache();

        SceneNode root = SceneNode.column();
        root.setClipChildren(true);

        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        root.appendChild(viewport);

        SceneNode content = SceneNode.column();
        content.setHitTestable(true); // B2：content 为交互单元，命中 content → handler 触发 + focused 写 content
        viewport.appendChild(content);

        // placeholder 独立容器：与 content 分离，避免 forEach 的 applyChildReconcile
        // 在 children.clear() 时误删 show 同步 append 到 content 的 anchor（已知 bug）。
        SceneNode placeholderContainer = SceneNode.column();
        viewport.appendChild(placeholderContainer);

        SceneInteractionState is = rt.interactionState(content);
        ReadableSignal<Boolean> caretVisible = Computed.create(
                () -> Boolean.valueOf(Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(is.focused().get())));
        ReadableSignal<Boolean> isPlaceholder = Computed.create(
                () -> Boolean.valueOf(SceneTextUtils.nullSafe(props.value().get()).isEmpty()
                        && !SceneTextUtils.nullSafe(placeholder).isEmpty()
                        && !Boolean.TRUE.equals(is.focused().get())));

        // 行号列表 signal：value 变化时重算行数，驱动 forEach 增删行节点
        Computed<List<Integer>> rowIndices = Computed.create(() -> {
            int lines = countLines(SceneTextUtils.nullSafe(props.value().get()));
            List<Integer> list = new ArrayList<>(lines);
            for (int i = 0; i < lines; i++) {
                list.add(Integer.valueOf(i));
            }
            return list;
        });

        // 按行渲染（key=行号；itemComponent 每 key 只调一次，行内文本靠 Computed 响应 value）
        rt.forEach(content, rowIndices, idx -> idx,
                rowIdx -> buildRow(rt, props, caretIndex, selection, caretVisible, isPlaceholder,
                        lineStructureCache, rowIdx));

        // placeholder：value 空且未聚焦时显示单行占位文本
        rt.show(placeholderContainer, isPlaceholder, () -> {
            SceneNode ph = new SceneNode();
            ph.setText(SceneTextUtils.nullSafe(placeholder));
            ph.setHitTestable(false);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? props.textPlaceholderColor() : props.textDisabledColor(),
                    ph::setTextColor);
            return ph;
        });

        // 纵向滚动
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        rt.focusable(content, props.enabled());

        // 点击/拖选定位：算全局码点索引
        rt.on(content, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            String value = SceneTextUtils.nullSafe(props.value().get());
            int pos = caretFromPointer(rt, root, viewport, value, clickPrefixWidthCache,
                    lineStructureCache, ctx);
            if (pos < 0) {
                return;
            }
            int clickCount = ev.getClickCount();
            if (clickCount >= 3) {
                // 三击：选整行
                TextSelection line = SceneTextGeometry.lineSelection(value, pos);
                setSelection.accept(Integer.valueOf(line.anchorCp()), Integer.valueOf(line.focusCp()));
                dragAnchor[0] = -1;
                return;
            }
            if (clickCount == 2) {
                // 双击：选词（词不跨行，\n 天然分隔）
                int ws = SceneTextGeometry.wordStartCp(value, pos);
                int we = SceneTextGeometry.wordEndCp(value, pos);
                if (ws == we) {
                    setCaretIndex.accept(Integer.valueOf(pos));
                } else {
                    setSelection.accept(Integer.valueOf(ws), Integer.valueOf(we));
                }
                dragAnchor[0] = -1;
                return;
            }
            if (ev.isShiftDown()) {
                TextSelection cur = selection.get();
                int anchor = cur.isActive() ? cur.anchorCp()
                        : SceneTextGeometry.clampCaretIndex(value, Integer.valueOf(caretAuthority[0]));
                setSelection.accept(Integer.valueOf(anchor), Integer.valueOf(pos));
                dragAnchor[0] = -1;
                return;
            }
            setCaretIndex.accept(Integer.valueOf(pos));
            dragAnchor[0] = pos;
            ctx.requestPointerCapture();
        });

        rt.on(content, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
            if (dragAnchor[0] < 0 || !Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            String value = SceneTextUtils.nullSafe(props.value().get());
            int pos = caretFromPointer(rt, root, viewport, value, clickPrefixWidthCache,
                    lineStructureCache, ctx);
            if (pos < 0) {
                return;
            }
            setSelection.accept(Integer.valueOf(dragAnchor[0]), Integer.valueOf(pos));
        });

        rt.on(content, SceneEventType.POINTER_UP, (ev, ctx) -> dragAnchor[0] = -1);
        rt.on(content, SceneEventType.POINTER_CANCEL, (ev, ctx) -> dragAnchor[0] = -1);

        // 文本输入（接受 \n；有选区时替换整段）
        rt.on(content, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            String raw = ev.getText();
            if (raw == null || raw.isEmpty()) {
                return;
            }
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            TextSelection sel = selection.get();
            int selStart = sel.isActive() ? sel.startCp() : caretPos;
            int selEnd = sel.isActive() ? sel.endCp() : caretPos;
            int removed = selEnd - selStart;
            String filtered = filterForInsert(raw,
                    Math.max(0, maxLength - (SceneTextGeometry.codePointCount(cur) - removed)));
            if (filtered.isEmpty()) {
                return;
            }
            String next = SceneTextGeometry.replaceRangeCp(cur, selStart, selEnd, filtered);
            props.onChange().accept(next);
            int newCaret = selStart + SceneTextGeometry.codePointCount(filtered);
            setSelection.accept(Integer.valueOf(newCaret), Integer.valueOf(newCaret));
        });

        // 键盘编辑键
        rt.on(content, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            int count = SceneTextGeometry.codePointCount(cur);
            SceneKey key = ev.getKey();
            if (ev.isControlDown() && key == SceneKey.KEY_A) {
                setSelection.accept(Integer.valueOf(0), Integer.valueOf(count));
                return;
            }
            if (key == SceneKey.ARROW_LEFT) {
                moveCaretWithShift(ev.isShiftDown(), selection.get(), Math.max(0, caretPos - 1),
                        setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.ARROW_RIGHT) {
                moveCaretWithShift(ev.isShiftDown(), selection.get(), Math.min(count, caretPos + 1),
                        setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.ARROW_UP) {
                moveCaretWithShift(ev.isShiftDown(), selection.get(),
                        moveCaretVertical(lineStructureCache, cur, caretPos, -1), setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.ARROW_DOWN) {
                moveCaretWithShift(ev.isShiftDown(), selection.get(),
                        moveCaretVertical(lineStructureCache, cur, caretPos, 1), setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.HOME) {
                moveCaretWithShift(ev.isShiftDown(), selection.get(),
                        lineStartIndex(lineStructureCache, cur, caretRow(lineStructureCache, cur, caretPos)),
                        setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.END) {
                int row = caretRow(lineStructureCache, cur, caretPos);
                moveCaretWithShift(ev.isShiftDown(), selection.get(),
                        lineEndIndex(lineStructureCache, cur, row), setCaretIndex, setSelection);
                return;
            }
            if (Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            TextSelection sel = selection.get();
            if (key == SceneKey.ENTER) {
                if (sel.isActive()) {
                    props.onChange().accept(SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), "\n"));
                    setSelection.accept(Integer.valueOf(sel.startCp() + 1), Integer.valueOf(sel.startCp() + 1));
                } else {
                    insertAtCaret(cur, caretPos, "\n", props.onChange(), caretIndex);
                    setSelection.accept(Integer.valueOf(caretPos + 1), Integer.valueOf(caretPos + 1));
                }
            } else if (key == SceneKey.BACKSPACE) {
                if (sel.isActive()) {
                    props.onChange().accept(SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), ""));
                    setSelection.accept(Integer.valueOf(sel.startCp()), Integer.valueOf(sel.startCp()));
                } else {
                    SceneTextGeometry.deleteBeforeCaret(cur, caretPos, props.onChange(), caretIndex);
                    if (caretPos > 0) {
                        setSelection.accept(Integer.valueOf(caretPos - 1), Integer.valueOf(caretPos - 1));
                    }
                }
            } else if (key == SceneKey.DELETE) {
                if (sel.isActive()) {
                    props.onChange().accept(SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), ""));
                    setSelection.accept(Integer.valueOf(sel.startCp()), Integer.valueOf(sel.startCp()));
                } else {
                    SceneTextGeometry.deleteAfterCaret(cur, caretPos, props.onChange());
                }
            }
        });

        return new Result(root, viewport, content, placeholderContainer, scrollSignal,
                caretIndex, selection, caretVisible, isPlaceholder);
    }

    /**
     * 构建单行五节点（prefix + caretBefore + highlight + caretAfter + suffix）。
     *
     * <p>caret 双槽位上色/切宽：本行且 caretVisible 且 focus 在本槽侧时 1px 着色。
     * highlight 显示本行选中段（跨行选区中间整行自然全段高亮）。</p>
     *
     * @param rt           场景运行时
     * @param props        输入契约（含颜色 token）
     * @param caretIndex   caret 全局码点索引 signal（selection.focus 投影，兼容读面）
     * @param selection    选区 signal
     * @param caretVisible caret 是否可见（enabled 且 focused）
     * @param isPlaceholder 当前是否处于 placeholder 态
     * @param lineStructureCache 行结构前缀和缓存
     * @param rowIdx       当前行号（key，稳定）
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, Signal<Integer> caretIndex,
                                        ReadableSignal<TextSelection> selection,
                                        ReadableSignal<Boolean> caretVisible,
                                        ReadableSignal<Boolean> isPlaceholder,
                                        LineStructureCache lineStructureCache, Integer rowIdx) {
        SceneNode row = SceneNode.row();
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(ROW_GAP);
        row.setClipChildren(true);

        SceneNode prefix = new SceneNode();
        prefix.setHitTestable(false);
        row.appendChild(prefix);

        SceneNode caretBefore = new SceneNode();
        caretBefore.setPreferredWidth(CARET_WIDTH);
        caretBefore.setPreferredHeight(rt.lineHeight(caretBefore.getFontSize()));
        caretBefore.setHitTestable(false);
        // 空文本叶兜底：宽 0 时真正归零不撑满行（与 TextInput 五节点同款防撑满 bug）
        caretBefore.setText("");
        row.appendChild(caretBefore);

        SceneNode highlight = new SceneNode();
        highlight.setHitTestable(false);
        row.appendChild(highlight);

        SceneNode caretAfter = new SceneNode();
        caretAfter.setPreferredWidth(0);
        caretAfter.setPreferredHeight(rt.lineHeight(caretAfter.getFontSize()));
        caretAfter.setHitTestable(false);
        caretAfter.setText("");
        row.appendChild(caretAfter);

        SceneNode suffix = new SceneNode();
        suffix.setHitTestable(false);
        row.appendChild(suffix);

        // 行内 prefix：选区前段 [lineStart, selStart)
        rt.bindComputed(() -> rowSegmentText(lineStructureCache, props.value().get(), rowIdx.intValue(),
                        Integer.MIN_VALUE, selection.get().startCp()),
                prefix::setText);
        // 行内 highlight：选中段 [selStart, selEnd) ∩ 本行
        rt.bindComputed(() -> rowSegmentText(lineStructureCache, props.value().get(), rowIdx.intValue(),
                        selection.get().startCp(), selection.get().endCp()),
                highlight::setText);
        // 行内 suffix：选区后段 [selEnd, lineEnd)
        rt.bindComputed(() -> rowSegmentText(lineStructureCache, props.value().get(), rowIdx.intValue(),
                        selection.get().endCp(), Integer.MAX_VALUE),
                suffix::setText);

        // 行内文本色：按 isPlaceholder/enabled 解析三态色（normal/placeholder/disabled）
        rt.bindComputed(() -> resolveTextColor(props, isPlaceholder.get(), props.enabled().get()),
                prefix::setTextColor);
        rt.bindComputed(() -> resolveTextColor(props, isPlaceholder.get(), props.enabled().get()),
                suffix::setTextColor);
        // 选区高亮：激活时反白文本 + 统一高亮背景（失焦保留选区可见）
        rt.bindComputed(() -> Boolean.TRUE.equals(selection.get().isActive())
                        ? SceneChromeTokens.SELECTION_TEXT
                        : resolveTextColor(props, isPlaceholder.get(), props.enabled().get()),
                highlight::setTextColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(selection.get().isActive())
                        ? SceneChromeTokens.SELECTION_BG : CARET_TRANSPARENT,
                highlight::setBackgroundColor);

        // caret 是否在本行：抽单个 Computed 复用
        Computed<Boolean> inRow = Computed.create(() ->
                Boolean.valueOf(isCaretInRow(lineStructureCache, props.value().get(),
                        selection.get().focusCp(), rowIdx.intValue())));
        // caret 槽位激活：focus 在选区哪一端（无选区 focus==start==end → 主槽）
        Computed<Boolean> caretAtSelStart = Computed.create(() ->
                Boolean.valueOf(Boolean.TRUE.equals(inRow.get())
                        && selection.get().focusCp() == selection.get().startCp()));
        Computed<Boolean> caretAtSelEnd = Computed.create(() ->
                Boolean.valueOf(Boolean.TRUE.equals(inRow.get())
                        && Boolean.TRUE.equals(selection.get().isActive())
                        && selection.get().focusCp() == selection.get().endCp()));
        // 槽位宽度切换（LAYOUT 级，仅 caret/选区移动时触发）
        rt.bind(caretAtSelStart, v -> caretBefore.setPreferredWidth(
                Boolean.TRUE.equals(v) ? CARET_WIDTH : 0));
        rt.bind(caretAtSelEnd, v -> caretAfter.setPreferredWidth(
                Boolean.TRUE.equals(v) ? CARET_WIDTH : 0));
        // 槽位颜色
        rt.bindComputed(() -> Boolean.TRUE.equals(caretVisible.get()) && Boolean.TRUE.equals(caretAtSelStart.get())
                        ? props.caretVisibleColor() : CARET_TRANSPARENT,
                caretBefore::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(caretVisible.get()) && Boolean.TRUE.equals(caretAtSelEnd.get())
                        ? props.caretVisibleColor() : CARET_TRANSPARENT,
                caretAfter::setBackgroundColor);

        return row;
    }

    /**
     * 解析行内文本色：placeholder 态用 placeholder 色，否则按 enabled 选 normal/disabled。
     *
     * @param props        输入契约（含三态色 token）
     * @param isPlaceholder 是否处于 placeholder 态
     * @param enabled      是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Props props, Boolean isPlaceholder, Boolean enabled) {
        boolean en = Boolean.TRUE.equals(enabled);
        if (Boolean.TRUE.equals(isPlaceholder)) {
            return en ? props.textPlaceholderColor() : props.textDisabledColor();
        }
        return en ? props.textNormalColor() : props.textDisabledColor();
    }

    /**
     * caret 移动的 Shift 语义：Shift 时保留 anchor 扩展 focus（无选区 anchor=移动前 caret），
     * 否则折叠选区移动。与 {@link SceneTextInputPrimitive} 的 moveCaretWithShift 对齐。
     */
    private static void moveCaretWithShift(boolean shift, TextSelection current, int target,
                                           Consumer<Integer> setCaretIndex,
                                           BiConsumer<Integer, Integer> setSelection) {
        if (shift) {
            int anchor = current.isActive() ? current.anchorCp() : current.focusCp();
            setSelection.accept(Integer.valueOf(anchor), Integer.valueOf(target));
        } else {
            setCaretIndex.accept(Integer.valueOf(target));
        }
    }

    /**
     * 由指针局部坐标解析全局 caret 码点索引（点击/拖选共用）。
     *
     * @return 全局码点索引；布局未建立时返回 -1（调用方忽略）
     */
    private static int caretFromPointer(SceneRuntime rt, SceneNode root, SceneNode viewport,
                                        String value, SceneTextGeometry.PrefixWidthCache cache,
                                        LineStructureCache lineStructureCache, SceneEventContext ctx) {
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        if (rootBox == null || viewportBox == null) {
            return -1;
        }
        if (value.isEmpty()) {
            return 0;
        }
        int fontSizePx = root.getFontSize();
        int lineH = rt.lineHeight(fontSizePx);
        // 坐标系（I12 两层）：ctx.getLocalPointerY() = content 局部 Y（框架每级重算，rootAbs≠0 不再错位）。
        int relY = ctx.getLocalPointerY();
        int row = Math.max(0, Math.min(countLines(value) - 1, relY / lineH));
        String[] lines = splitLines(value);
        String lineText = lines[row];
        int localX = ctx.getLocalPointerX();
        int[] prefixWidths = cache.get(rt, lineText, fontSizePx);
        int col = SceneTextGeometry.caretIndexFromX(prefixWidths, localX);
        return lineStartIndex(lineStructureCache, value, row) + col;
    }

    // ==================== 文本几何工具 ====================

    /**
     * 统计逻辑行数（按 {@code 
} 切分，空文本视作 1 行）。
     */
    private static int countLines(String text) {
        String t = SceneTextUtils.nullSafe(text);
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
     * 按 {@code 
} 切分行（保留空行，尾空行保留）。
     */
    private static String[] splitLines(String text) {
        String t = SceneTextUtils.nullSafe(text);
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
     * 第 row 行结束全局码点索引（不含 
，即行末 caret 位置；查表 O(1)）。
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
     * 行内文本段：全局区间 {@code [segStart, segEnd)} 与第 row 行的交集（行内相对索引截取）。
     *
     * <p>跨行选区时中间整行交集成全行（segStart≤lineStart 且 segEnd≥lineEnd），
     * 首/末行只取局部段，形成块状高亮视觉。</p>
     *
     * @param cache    行结构缓存
     * @param value    当前值
     * @param row      行号
     * @param segStart 全局段起点（可为 MIN_VALUE 表示行首）
     * @param segEnd   全局段终点（可为 MAX_VALUE 表示行末，半开）
     * @return 行内文本
     */
    private static String rowSegmentText(LineStructureCache cache, String value, int row,
                                         int segStart, int segEnd) {
        LineStructureCache c = cache.get(value);
        if (row < 0 || row >= c.lines.length) {
            return "";
        }
        int start = c.lineStartCp[row];
        int end = start + c.lineLenCp[row];
        int s = Math.max(start, Math.min(end, segStart));
        int e = Math.max(s, Math.min(end, segEnd));
        return SceneTextGeometry.substringByCodePoints(c.lines[row], s - start, e - start);
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
        int col = SceneTextGeometry.clampCaretIndex(value, Integer.valueOf(caret)) - start;
        int newRow = row + delta;
        if (newRow < 0) {
            return 0;
        }
        if (newRow >= lineCount) {
            return SceneTextGeometry.codePointCount(value);
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
        int offset = SceneTextGeometry.charOffsetForCodePointIndex(cur, caret);
        String next = cur.substring(0, offset) + text + cur.substring(offset);
        onChange.accept(next);
        caretIndex.set(Integer.valueOf(caret + SceneTextGeometry.codePointCount(text)));
    }

    /**
     * 过滤输入并限制本次可插入码点数（保留 
，过滤其他控制字符）。
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
        /** 长度=行数；第 r 行码点数（不含 
）。 */
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
            String safe = SceneTextUtils.nullSafe(value);
            if (cachedValue != null && safe.equals(cachedValue) && lines != null) {
                return this;
            }
            rebuild(safe);
            return this;
        }

        /**
         * 单趟扫描 value 重建行结构前缀和。语义与 split("
", -1) 一致：保留空行、
         * 尾空行、连续 
 产生的空行。
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
                    int lineCp = SceneTextGeometry.codePointCount(lineText);
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
            int lastCp = SceneTextGeometry.codePointCount(lastLine);
            lineStartCp[row] = lineStartCpIdx;
            lineLenCp[row] = lastCp;
            lines[row] = lastLine;
            // 末尾哨兵 = 总码点数，供 caretRow 二分边界
            lineStartCp[lineCount] = lineStartCpIdx + lastCp;
            cachedValue = value;
        }
    }

}
