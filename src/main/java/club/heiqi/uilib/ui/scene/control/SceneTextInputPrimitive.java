package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.ClipboardBackend;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTextInputPrimitive —— 无样式单行受控文本输入行为核心（B2：带选区）。
 *
 * <p>该 primitive 只负责结构、输入行为、caret/选区状态和文本布局绑定，不设置背景、边框、
 * 文本色、caret 色、cursor 或 padding 等 chrome。外观由上层 wrapper 自行组合。</p>
 *
 * <h3>B2 选区能力</h3>
 * <ul>
 *   <li>鼠标拖选（anchor 固定、focus 随 MOVE）；Shift+点击 / Shift+方向键/Home/End 扩展；</li>
 *   <li>双击选词、三击选整行（单行控件=全选）、Ctrl+A 全选；</li>
 *   <li>TEXT_INPUT / Backspace / Delete 有选区时替换/删除整段；</li>
 *   <li>readOnly 可选中/可移动/可 Shift 扩展，禁止编辑（与 B1 一致）。</li>
 * </ul>
 *
 * <h3>结构（B2 五节点）</h3>
 * <pre>
 * root (ROW, clipChildren=true, focusable, padding 由 wrapper 设置)
 *   ├─ prefixText     [0, selStart) 显示文本（caret 前/选区前）
 *   ├─ caret          主 caret 槽（selStart 侧）：focus==selStart 时 1px
 *   ├─ highlightText  选中段 [selStart, selEnd)（wrapper 绑背景/文本色）
 *   ├─ caretAfter     次 caret 槽（selEnd 侧）：选区激活且 focus==selEnd 时 1px
 *   └─ suffixText     [selEnd, end) 显示文本
 * </pre>
 * <p>两个 caret 槽宽度由 primitive 切换（focus 在选区哪一端，哪端 1px），颜色由 wrapper 供给；
 * 无选区时 focus==selStart==selEnd，主 caret 槽常亮。两个槽位均为空文本叶（setText("")），
 * 宽 0 时真正归零不撑满行（与 TextArea 非光标行 caret 兜底对齐）。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值仍由外部 {@code value} 唯一持有；控件不缓存 value、不自改 value。内部维护
 * {@code caretIndex} 与 {@link TextSelection} 两个本地 UI 态：caret≡selection.focus，
 * 无选区时 anchor==focus（折叠）。所有文本写入都只经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <p><b>消费者契约</b>：primitive 返回的 root/prefixText/highlightText/caret/caretAfter/suffixText
 * 默认无任何颜色，直接消费时必须自行挂 PAINT 绑定（尤其两个 caret 节点需按
 * {@code caretVisible} × 槽位激活上色，highlightText 需按选区激活上背景/文本色，
 * 否则选区与 caret 不可见）。可参考 {@link SceneTextInput} 的 chrome 挂载方式。</p>
 */
public final class SceneTextInputPrimitive {

    /** 闪烁周期（纳秒）：530ms 亮 + 430ms 暗。 */
    private static final long BLINK_PERIOD_NANOS = 960_000_000L;
    /** 闪烁亮相位时长（纳秒）。 */
    private static final long BLINK_ON_NANOS = 530_000_000L;
    /** root 行内间距：必须 0，否则 caret 与文本间多 1px gap。 */
    private static final int GAP = 0;
    /** caret 宽度（像素，1px 竖线）。 */
    private static final int CARET_WIDTH = 1;
    /** 密码掩码字符（圆点 U+2022）。 */
    private static final char MASK_CHAR = '\u2022';

    /** 纯静态工厂，禁止实例化。 */
    private SceneTextInputPrimitive() {
    }

    /**
     * TextInput primitive 输入契约 —— 只包含行为所需数据，不包含样式开关。
     *
     * @param value       当前文本（响应式只读，受控源）
     * @param enabled     是否启用
     * @param readOnly    是否只读
     * @param placeholder 占位文本
     * @param maxLength   最大长度（按码点数）
     * @param inputType   输入类型
     * @param onChange    文本变更回调
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            SceneInputType inputType,
            Consumer<String> onChange
    ) {
    }

    /**
     * TextInput primitive 创建结果，暴露无样式结构节点和派生行为状态。
     *
     * @param root          根节点
     * @param prefixText    caret/选区前文本节点
     * @param caret         主 caret 槽节点（selStart 侧，无选区时即唯一 caret）
     * @param highlightText 选中段文本节点（wrapper 绑背景/文本色）
     * @param caretAfter    次 caret 槽节点（selEnd 侧）
     * @param suffixText    caret/选区后文本节点
     * @param caretIndex    caret 码点索引 signal（=selection.focus 投影）
     * @param selection     选区状态 signal（本地 UI 态，anchor/focus 码点索引）
     * @param moveCaretToEndOf 将 caret 移到指定文本末尾的受限操作；用于 autocomplete commit 在外部
     *                         value signal flush 前同步对齐 caret，不暴露 caretIndex 写权限
     * @param caretVisible  caret 是否可见（enabled 且 focused）
     * @param isPlaceholder 当前是否处于空值且有 placeholder 的状态
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode prefixText,
            SceneNode caret,
            SceneNode highlightText,
            SceneNode caretAfter,
            SceneNode suffixText,
            ReadableSignal<Integer> caretIndex,
            ReadableSignal<TextSelection> selection,
            Consumer<String> moveCaretToEndOf,
            ReadableSignal<Boolean> caretVisible,
            ReadableSignal<Boolean> isPlaceholder
    ) {
    }

    /**
     * 创建无样式 TextInput primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        final String placeholder = props.placeholder();
        final int maxLength = props.maxLength();
        final SceneInputType inputType = props.inputType();
        final Signal<Integer> caretIndex = Signal.create(Integer.valueOf(0));
        // 输入 handler 读取同步真值；signal 只保留帧末响应式投影语义。
        final int[] caretAuthority = {0};
        final Signal<TextSelection> selection = Signal.create(TextSelection.collapsed(0));
        // 选区同步真值：handler 读此数组（Signal.set 只入 pending 写队列，flush 前 get() 返回旧值），
        // signal 只保留帧末响应式投影语义（与 caretAuthority 同款纪律）。
        final TextSelection[] selectionAuthority = {TextSelection.collapsed(0)};
        // 选区写入唯一汇点：四态（caretAuthority / caretIndex / selectionAuthority / selection）同步，caret≡focus。
        final BiConsumer<Integer, Integer> setSelection = (anchor, focus) -> {
            caretAuthority[0] = focus.intValue();
            caretIndex.set(focus);
            selectionAuthority[0] = TextSelection.of(anchor.intValue(), focus.intValue());
            selection.set(selectionAuthority[0]);
        };
        final Consumer<Integer> setCaretIndex = next -> setSelection.accept(next, next);
        // autocomplete commit 专用逃生舱：只允许按候选文本码点长度把 caret 同步移到末尾，
        // 不把 caretIndex 的可写 Signal 暴露给外部，避免通用 value 回写破坏中间编辑位置。
        final Consumer<String> moveCaretToEndOf = text -> setCaretIndex.accept(
                Integer.valueOf(SceneTextGeometry.codePointCount(SceneTextUtils.nullSafe(text))));
        // E1 编辑历史（undo/redo 栈，实例级本地 UI 态）
        final TextEditHistory editHistory = new TextEditHistory();
        // E4 默认右键菜单：复制/剪切/粘贴/全选/撤销/重做，按 readOnly/选区/历史启停；
        // 各项 onSelect 执行时重读当前 value/selection，避免捕获打开时刻快照。
        final Supplier<List<SceneContextMenu.MenuItem>> contextMenuItems = () -> {
            List<SceneContextMenu.MenuItem> items = new ArrayList<>();
            ClipboardBackend clipboard = rt.getClipboardBackend();
            boolean readOnly = Boolean.TRUE.equals(props.readOnly().get());
            items.add(SceneContextMenu.MenuItem.of("复制", () -> {
                if (clipboard == null) {
                    return;
                }
                String v = SceneTextUtils.nullSafe(props.value().get());
                TextSelection s = selectionAuthority[0];
                clipboard.setClipboardText(s.isActive()
                        ? SceneTextGeometry.substringByCodePoints(v, s.startCp(), s.endCp()) : v);
            }));
            items.add(SceneContextMenu.MenuItem.of("剪切", !readOnly, () -> {
                if (clipboard == null) {
                    return;
                }
                String v = SceneTextUtils.nullSafe(props.value().get());
                TextSelection s = selectionAuthority[0];
                if (s.isActive()) {
                    clipboard.setClipboardText(
                            SceneTextGeometry.substringByCodePoints(v, s.startCp(), s.endCp()));
                    String next = SceneTextGeometry.replaceRangeCp(v, s.startCp(), s.endCp(), "");
                    editHistory.record(v, next, s.focusCp(), s.startCp(), false, System.nanoTime());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(s.startCp()), Integer.valueOf(s.startCp()));
                }
            }));
            items.add(SceneContextMenu.MenuItem.of("粘贴", !readOnly, () -> {
                if (clipboard == null) {
                    return;
                }
                String text = clipboard.getClipboardText();
                if (text != null && !text.isEmpty()) {
                    String v = SceneTextUtils.nullSafe(props.value().get());
                    int caret = SceneTextGeometry.clampCaretIndex(v, Integer.valueOf(caretAuthority[0]));
                    applyTextInsert(v, caret, selectionAuthority[0], text, maxLength, inputType,
                            props.onChange(), setSelection, editHistory, false, System.nanoTime());
                }
            }));
            items.add(SceneContextMenu.MenuItem.of("全选", () -> {
                String v = SceneTextUtils.nullSafe(props.value().get());
                setSelection.accept(Integer.valueOf(0),
                        Integer.valueOf(SceneTextGeometry.codePointCount(v)));
            }));
            items.add(SceneContextMenu.MenuItem.divider());
            items.add(SceneContextMenu.MenuItem.of("撤销", !readOnly && editHistory.undoSize() > 0, () -> {
                String v = SceneTextUtils.nullSafe(props.value().get());
                TextEditHistory.Entry entry = editHistory.undo(v);
                if (entry != null) {
                    props.onChange().accept(entry.before());
                    setSelection.accept(Integer.valueOf(entry.caretBefore()),
                            Integer.valueOf(entry.caretBefore()));
                }
            }));
            items.add(SceneContextMenu.MenuItem.of("重做", !readOnly && editHistory.redoSize() > 0, () -> {
                String v = SceneTextUtils.nullSafe(props.value().get());
                TextEditHistory.Entry entry = editHistory.redo(v);
                if (entry != null) {
                    props.onChange().accept(entry.after());
                    setSelection.accept(Integer.valueOf(entry.caretAfter()),
                            Integer.valueOf(entry.caretAfter()));
                }
            }));
            return items;
        };
        // 拖选瞬态：>=0 表示拖选中且该值为 anchor；-1 表示未拖选。
        final int[] dragAnchor = {-1};
        final SceneTextGeometry.PrefixWidthCache prefixWidthCache = new SceneTextGeometry.PrefixWidthCache();

        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setMainAxisAlign(MainAxisAlign.START);
        root.setGap(GAP);
        root.setClipChildren(true);
        // 横向滚动地基：宽度钉死为视口宽、子内容宽解耦（内容超宽时裁剪 + scrollOffsetX 平移）
        root.setScrollableX(true);

        SceneNode prefixText = new SceneNode();
        prefixText.setHitTestable(false);
        root.appendChild(prefixText);

        SceneNode caret = new SceneNode();
        caret.setPreferredWidth(CARET_WIDTH);
        caret.setPreferredHeight(rt.lineHeight(caret.getFontSize()));
        caret.setHitTestable(false);
        // 标记为空文本叶：computeWidth 对 text==null 的无文本叶返回 outerWidth（填满父宽），
        // 会把同行 prefix/suffix 推出 row 裁剪区。setText("") 使其走 text.isEmpty() 分支返回 padH=0，
        // 确保 setPreferredWidth(0)（槽位不激活）时宽度真正归零，不撑满行。
        // 与 SceneTextAreaPrimitive 的 caret 兜底对齐，防止潜伏撑满 bug。
        caret.setText("");
        root.appendChild(caret);

        SceneNode highlightText = new SceneNode();
        highlightText.setHitTestable(false);
        root.appendChild(highlightText);

        SceneNode caretAfter = new SceneNode();
        caretAfter.setPreferredWidth(0);
        caretAfter.setPreferredHeight(rt.lineHeight(caretAfter.getFontSize()));
        caretAfter.setHitTestable(false);
        caretAfter.setText("");
        root.appendChild(caretAfter);

        SceneNode suffixText = new SceneNode();
        suffixText.setHitTestable(false);
        root.appendChild(suffixText);

        SceneInteractionState is = rt.interactionState(root);
        // focus 是 Router 权威状态的按需投影，必须在任何 requestFocus 写入前声明。
        // 显式缓存同一只读 signal，避免首次 portal 挂载时 Computed 尚未求值而漏掉 focused=true。
        ReadableSignal<Boolean> focused = is.focused();
        // caret 闪烁相位：起点由交互事件重置（MIN_VALUE=未初始化，常亮）；订阅帧时间每帧重算。
        final long[] blinkPhaseStart = {Long.MIN_VALUE};
        ReadableSignal<Boolean> blinkOn = Computed.create(() -> {
            long now = rt.__frameTimeNanos().get().longValue();
            if (blinkPhaseStart[0] == Long.MIN_VALUE || now < blinkPhaseStart[0]) {
                return Boolean.TRUE;
            }
            return Boolean.valueOf((now - blinkPhaseStart[0]) % BLINK_PERIOD_NANOS < BLINK_ON_NANOS);
        });
        ReadableSignal<Boolean> caretVisible = Computed.create(
                () -> Boolean.valueOf(Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(focused.get()) && Boolean.TRUE.equals(blinkOn.get())));
        ReadableSignal<Boolean> isPlaceholder = Computed.create(
                () -> Boolean.valueOf(SceneTextUtils.nullSafe(props.value().get()).isEmpty() && !SceneTextUtils.nullSafe(placeholder).isEmpty()));

        rt.bindComputed(() -> prefixDisplayText(
                        props.value().get(), focused.get(), placeholder, inputType, selection.get().startCp()),
                prefixText::setText);
        rt.bindComputed(() -> highlightDisplayText(
                        props.value().get(), focused.get(), inputType,
                        selection.get().startCp(), selection.get().endCp()),
                highlightText::setText);
        rt.bindComputed(() -> suffixDisplayText(
                        props.value().get(), focused.get(), inputType, selection.get().endCp()),
                suffixText::setText);

        // caret 双槽位宽度切换（颜色由 wrapper 供给）：focus 在选区哪一端，哪端 1px；无选区主槽常亮。
        rt.bind(selection, sel -> caret.setPreferredWidth(
                sel.focusCp() == sel.startCp() ? CARET_WIDTH : 0));
        rt.bind(selection, sel -> caretAfter.setPreferredWidth(
                sel.isActive() && sel.focusCp() == sel.endCp() ? CARET_WIDTH : 0));

        // 横向滚动 caret 跟随：caret X 超出可视内容区时最小滚动（GEOMETRY 级，不重排；
        // flush 后按已提交几何调整，视口宽稳定不随 caret 变化，读旧布局安全；无信号回环）
        rt.bind(caretIndex, idx -> {
            LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
            if (rootBox == null) {
                return;
            }
            String value = SceneTextUtils.nullSafe(props.value().get());
            String display = displayValue(value, inputType);
            int[] prefixWidths = prefixWidthCache.get(rt, display, root.getFontSize());
            int caretCp = Math.min(idx.intValue(), prefixWidths.length - 1);
            int viewStart = root.getPaddingLeft();
            int viewEnd = rootBox.getWidth() - root.getPaddingRight();
            int caretX = prefixWidths[caretCp] + viewStart;
            int scroll = root.getScrollOffsetX();
            int next = scroll;
            if (caretX > scroll + viewEnd) {
                next = caretX - viewEnd;
            } else if (caretX < scroll + viewStart) {
                next = caretX - viewStart;
            }
            // maxScroll 用测量宽闭式（不读子布局）：同 flush 内文本绑定 setText 会先清子
            // cachedLayout，读子布局在 effect 时序下恒为 none。显示全宽 + caret 1px + 水平 padding。
            int contentWidth = prefixWidths[prefixWidths.length - 1] + CARET_WIDTH
                    + root.getPaddingLeft() + root.getPaddingRight();
            int maxScroll = Math.max(0, contentWidth - rootBox.getWidth());
            next = Math.max(0, Math.min(maxScroll, next));
            if (next != scroll) {
                root.setScrollOffsetX(next);
            }
        });

        rt.focusable(root, props.enabled());

        rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            // 交互重置闪烁相位（点击后 caret 立即亮起）
            blinkPhaseStart[0] = ev.getTimeNanos();
            // E4 右键：打开上下文菜单（不移动 caret/选区；锚点=指针 host 坐标）
            if (ev.getButton() == SceneMouseButton.RIGHT) {
                SceneContextMenu.open(rt,
                        ctx.getRawPointerX() - ctx.getTreeRootAbsX(),
                        ctx.getRawPointerY() - ctx.getTreeRootAbsY(),
                        contextMenuItems.get());
                return;
            }
            LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
            if (rootBox == null) {
                return;
            }
            String value = SceneTextUtils.nullSafe(props.value().get());
            String display = displayValue(value, inputType);
            // 坐标系（I12 两层）：effectiveTarget=root，ctx.getLocalPointerX() = raw - absoluteBox(root,treeAbs)
            // = root 局部 X（框架每级重算，rootAbs≠0 不再错位）。再减 paddingLeft 得文本区局部。
            int localX = ctx.getLocalPointerX() - root.getPaddingLeft();
            int fontSizePx = root.getFontSize();
            int[] prefixWidths = prefixWidthCache.get(rt, display, fontSizePx);
            int pos = SceneTextGeometry.caretIndexFromX(prefixWidths, localX);
            int clickCount = ev.getClickCount();
            if (clickCount >= 3) {
                // 三击：选整行（单行控件=全选）
                TextSelection line = SceneTextGeometry.lineSelection(value, pos);
                setSelection.accept(Integer.valueOf(line.anchorCp()), Integer.valueOf(line.focusCp()));
                dragAnchor[0] = -1;
                return;
            }
            if (clickCount == 2) {
                // 双击：选词（caret 落分隔符时折叠，不误选）
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
                // Shift+点击：扩展选区（anchor 固定）
                TextSelection cur = selectionAuthority[0];
                int anchor = cur.isActive() ? cur.anchorCp()
                        : SceneTextGeometry.clampCaretIndex(value, Integer.valueOf(caretAuthority[0]));
                setSelection.accept(Integer.valueOf(anchor), Integer.valueOf(pos));
                dragAnchor[0] = -1;
                return;
            }
            // 单击：折叠选区并武装拖选
            setCaretIndex.accept(Integer.valueOf(pos));
            dragAnchor[0] = pos;
            ctx.requestPointerCapture();
        });

        rt.on(root, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
            // 仅拖选中响应（capture 保证 MOVE 强制投递本节点）；拖选 anchor 固定、focus 随指针
            if (dragAnchor[0] < 0 || !Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
            if (rootBox == null) {
                return;
            }
            String value = SceneTextUtils.nullSafe(props.value().get());
            String display = displayValue(value, inputType);
            int localX = ctx.getLocalPointerX() - root.getPaddingLeft();
            int[] prefixWidths = prefixWidthCache.get(rt, display, root.getFontSize());
            int pos = SceneTextGeometry.caretIndexFromX(prefixWidths, localX);
            setSelection.accept(Integer.valueOf(dragAnchor[0]), Integer.valueOf(pos));
        });

        rt.on(root, SceneEventType.POINTER_UP, (ev, ctx) -> {
            dragAnchor[0] = -1;
            blinkPhaseStart[0] = ev.getTimeNanos();
        });
        rt.on(root, SceneEventType.POINTER_CANCEL, (ev, ctx) -> dragAnchor[0] = -1);

        rt.on(root, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            // 交互重置闪烁相位（输入后 caret 立即亮起）
            blinkPhaseStart[0] = ev.getTimeNanos();
            String raw = ev.getText();
            if (raw == null || raw.isEmpty()) {
                return;
            }
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            applyTextInsert(cur, caretPos, selectionAuthority[0], raw, maxLength, inputType,
                    props.onChange(), setSelection, editHistory, true, ev.getTimeNanos());
        });

        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            // 交互重置闪烁相位（按键后 caret 立即亮起）
            blinkPhaseStart[0] = ev.getTimeNanos();
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            int count = SceneTextGeometry.codePointCount(cur);
            SceneKey key = ev.getKey();
            // === Ctrl 组合区（词跳转/文首尾/全选/剪贴板） ===
            if (ev.isControlDown()) {
                if (key == SceneKey.ARROW_LEFT) {
                    // Ctrl+← 词跳转（Shift 组合保留选区扩展）
                    moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                            SceneTextGeometry.previousWordCp(cur, caretPos), setCaretIndex, setSelection);
                    return;
                }
                if (key == SceneKey.ARROW_RIGHT) {
                    moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                            SceneTextGeometry.nextWordCp(cur, caretPos), setCaretIndex, setSelection);
                    return;
                }
                if (key == SceneKey.HOME) {
                    // Ctrl+Home 文首
                    moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], 0, setCaretIndex, setSelection);
                    return;
                }
                if (key == SceneKey.END) {
                    // Ctrl+End 文尾
                    moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], count, setCaretIndex, setSelection);
                    return;
                }
                if (key == SceneKey.KEY_A) {
                    // Ctrl+A 全选（readOnly 也允许，只改本地 UI 态）
                    setSelection.accept(Integer.valueOf(0), Integer.valueOf(count));
                    return;
                }
                // Undo/Redo：Ctrl+Z 撤销、Ctrl+Shift+Z 或 Ctrl+Y 重做（readOnly 不允许）
                if (key == SceneKey.KEY_Z && !Boolean.TRUE.equals(props.readOnly().get())) {
                    boolean redo = ev.isShiftDown();
                    TextEditHistory.Entry entry = redo ? editHistory.redo(cur) : editHistory.undo(cur);
                    if (entry != null) {
                        String target = redo ? entry.after() : entry.before();
                        int targetCaret = redo ? entry.caretAfter() : entry.caretBefore();
                        props.onChange().accept(target);
                        setSelection.accept(Integer.valueOf(targetCaret), Integer.valueOf(targetCaret));
                    }
                    return;
                }
                if (key == SceneKey.KEY_Y && !Boolean.TRUE.equals(props.readOnly().get())) {
                    TextEditHistory.Entry entry = editHistory.redo(cur);
                    if (entry != null) {
                        props.onChange().accept(entry.after());
                        setSelection.accept(Integer.valueOf(entry.caretAfter()), Integer.valueOf(entry.caretAfter()));
                    }
                    return;
                }
                // 剪贴板快捷键：Ctrl+C/X/V（无后端时静默降级；Ctrl+C 在 readOnly 也允许）
                ClipboardBackend clipboard = rt.getClipboardBackend();
                if (clipboard != null) {
                    if (key == SceneKey.KEY_C) {
                        TextSelection sel = selectionAuthority[0];
                        String copied = sel.isActive()
                                ? SceneTextGeometry.substringByCodePoints(cur, sel.startCp(), sel.endCp()) : cur;
                        clipboard.setClipboardText(copied);
                        return;
                    }
                    if (key == SceneKey.KEY_X && !Boolean.TRUE.equals(props.readOnly().get())) {
                        TextSelection sel = selectionAuthority[0];
                        if (sel.isActive()) {
                            clipboard.setClipboardText(
                                    SceneTextGeometry.substringByCodePoints(cur, sel.startCp(), sel.endCp()));
                            String next = SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), "");
                            editHistory.record(cur, next, caretPos, sel.startCp(), false, ev.getTimeNanos());
                            props.onChange().accept(next);
                            setSelection.accept(Integer.valueOf(sel.startCp()), Integer.valueOf(sel.startCp()));
                        } else {
                            clipboard.setClipboardText(cur);
                            editHistory.record(cur, "", caretPos, 0, false, ev.getTimeNanos());
                            props.onChange().accept("");
                            setSelection.accept(Integer.valueOf(0), Integer.valueOf(0));
                        }
                        return;
                    }
                    if (key == SceneKey.KEY_V && !Boolean.TRUE.equals(props.readOnly().get())) {
                        String text = clipboard.getClipboardText();
                        if (text != null && !text.isEmpty()) {
                            applyTextInsert(cur, caretPos, selectionAuthority[0], text, maxLength, inputType,
                                    props.onChange(), setSelection, editHistory, false, ev.getTimeNanos());
                        }
                        return;
                    }
                }
            }
            if (key == SceneKey.ARROW_LEFT) {
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], Math.max(0, caretPos - 1),
                        setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.ARROW_RIGHT) {
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], Math.min(count, caretPos + 1),
                        setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.HOME) {
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], 0, setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.END) {
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0], count, setCaretIndex, setSelection);
                return;
            }
            if (Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            TextSelection sel = selectionAuthority[0];
            if (key == SceneKey.BACKSPACE) {
                if (ev.isControlDown()) {
                    // Ctrl+Backspace 删前词
                    int ws = SceneTextGeometry.previousWordCp(cur, caretPos);
                    String next = SceneTextGeometry.replaceRangeCp(cur, ws, caretPos, "");
                    editHistory.record(cur, next, caretPos, ws, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(ws), Integer.valueOf(ws));
                } else if (sel.isActive()) {
                    String next = SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), "");
                    editHistory.record(cur, next, caretPos, sel.startCp(), false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(sel.startCp()), Integer.valueOf(sel.startCp()));
                } else if (caretPos > 0) {
                    String next = SceneTextGeometry.replaceRangeCp(cur, caretPos - 1, caretPos, "");
                    editHistory.record(cur, next, caretPos, caretPos - 1, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(caretPos - 1), Integer.valueOf(caretPos - 1));
                }
            } else if (key == SceneKey.DELETE) {
                if (ev.isControlDown()) {
                    // Ctrl+Delete 删后词
                    int we = SceneTextGeometry.nextWordCp(cur, caretPos);
                    String next = SceneTextGeometry.replaceRangeCp(cur, caretPos, we, "");
                    editHistory.record(cur, next, caretPos, caretPos, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(caretPos), Integer.valueOf(caretPos));
                } else if (sel.isActive()) {
                    String next = SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), "");
                    editHistory.record(cur, next, caretPos, sel.startCp(), false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(sel.startCp()), Integer.valueOf(sel.startCp()));
                } else if (caretPos < count) {
                    String next = SceneTextGeometry.replaceRangeCp(cur, caretPos, caretPos + 1, "");
                    editHistory.record(cur, next, caretPos, caretPos, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                }
            }
        });

        return new Result(root, prefixText, caret, highlightText, caretAfter, suffixText,
                caretIndex, selection, moveCaretToEndOf, caretVisible, isPlaceholder);
    }

    /**
     * caret 移动的 Shift 语义：Shift 时保留 anchor 扩展 focus（无选区 anchor=移动前 caret），
     * 否则折叠选区移动。
     *
     * @param shift         Shift 是否按下
     * @param current       当前选区（移动前）
     * @param target        目标 caret 码点索引
     * @param setCaretIndex 折叠移动写入口
     * @param setSelection  选区写入口（三态同步）
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
     * 在 caret/选区处插入文本（TEXT_INPUT 与 Ctrl+V 共用）：
     * 有选区替换整段，可用空间按"删除选区后剩余"计；过滤后无有效文本时无副作用。
     *
     * @param cur          当前文本
     * @param caretPos     当前 caret 码点索引
     * @param sel          当前选区
     * @param raw          原始输入文本
     * @param maxLength    最大长度（码点）
     * @param inputType    输入类型（过滤规则）
     * @param onChange     变更回调
     * @param setSelection 选区写入口（三态同步）
     */
    private static void applyTextInsert(String cur, int caretPos, TextSelection sel, String raw,
                                        int maxLength, SceneInputType inputType,
                                        Consumer<String> onChange,
                                        BiConsumer<Integer, Integer> setSelection,
                                        TextEditHistory editHistory, boolean mergeable, long timeNanos) {
        int selStart = sel.isActive() ? sel.startCp() : caretPos;
        int selEnd = sel.isActive() ? sel.endCp() : caretPos;
        int removed = selEnd - selStart;
        FilteredInsert filtered = filterForInsert(raw,
                Math.max(0, maxLength - (SceneTextGeometry.codePointCount(cur) - removed)), inputType);
        if (filtered.text.isEmpty()) {
            return;
        }
        String next = SceneTextGeometry.replaceRangeCp(cur, selStart, selEnd, filtered.text);
        int newCaret = selStart + filtered.codePointCount;
        // E1：先记历史（caretBefore=编辑发生位、caretAfter=新 caret），再上抛
        editHistory.record(cur, next, selStart, newCaret, mergeable, timeNanos);
        onChange.accept(next);
        setSelection.accept(Integer.valueOf(newCaret), Integer.valueOf(newCaret));
    }

    /**
     * 计算 caret/选区前显示文本。
     *
     * @param value       真实值
     * @param focused     是否聚焦
     * @param placeholder 占位文本
     * @param inputType   输入类型
     * @param selStart    选区起点码点索引
     * @return prefix 显示文本
     */
    private static String prefixDisplayText(String value, Boolean focused, String placeholder,
                                            SceneInputType inputType, int selStart) {
        String v = SceneTextUtils.nullSafe(value);
        if (v.isEmpty()) {
            return Boolean.TRUE.equals(focused) ? "" : SceneTextUtils.nullSafe(placeholder);
        }
        int start = SceneTextGeometry.clampCaretIndex(v, Integer.valueOf(selStart));
        if (inputType == SceneInputType.PASSWORD) {
            return mask(start);
        }
        return SceneTextGeometry.substringByCodePoints(v, 0, start);
    }

    /**
     * 计算选中段显示文本。
     *
     * @param value     真实值
     * @param focused   是否聚焦
     * @param inputType 输入类型
     * @param selStart  选区起点码点索引
     * @param selEnd    选区终点码点索引
     * @return highlight 显示文本
     */
    private static String highlightDisplayText(String value, Boolean focused,
                                               SceneInputType inputType, int selStart, int selEnd) {
        String v = SceneTextUtils.nullSafe(value);
        if (v.isEmpty()) {
            return "";
        }
        int start = SceneTextGeometry.clampCaretIndex(v, Integer.valueOf(selStart));
        int end = SceneTextGeometry.clampCaretIndex(v, Integer.valueOf(selEnd));
        if (inputType == SceneInputType.PASSWORD) {
            return mask(Math.max(0, end - start));
        }
        return SceneTextGeometry.substringByCodePoints(v, start, end);
    }

    /**
     * 计算 caret/选区后显示文本。
     *
     * @param value     真实值
     * @param focused   是否聚焦
     * @param inputType 输入类型
     * @param selEnd    选区终点码点索引
     * @return suffix 显示文本
     */
    private static String suffixDisplayText(String value, Boolean focused,
                                            SceneInputType inputType, int selEnd) {
        String v = SceneTextUtils.nullSafe(value);
        if (v.isEmpty()) {
            return "";
        }
        int max = SceneTextGeometry.codePointCount(v);
        int end = SceneTextGeometry.clampCaretIndex(v, Integer.valueOf(selEnd));
        if (inputType == SceneInputType.PASSWORD) {
            return mask(max - end);
        }
        return SceneTextGeometry.substringByCodePoints(v, end, max);
    }

    /**
     * 计算用于点击定位的完整显示文本。
     *
     * @param value     真实值
     * @param inputType 输入类型
     * @return display 文本
     */
    private static String displayValue(String value, SceneInputType inputType) {
        String v = SceneTextUtils.nullSafe(value);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(SceneTextGeometry.codePointCount(v));
        }
        return v;
    }

    /**
     * 生成指定码点数量的密码掩码。
     *
     * @param count 掩码数量
     * @return 掩码串
     */
    private static String mask(int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            sb.append(MASK_CHAR);
        }
        return sb.toString();
    }

    /**
     * 过滤输入并限制本次可插入码点数。
     *
     * @param input     原始输入
     * @param available 剩余可插入码点数
     * @param inputType 输入类型
     * @return 过滤结果
     */
    private static FilteredInsert filterForInsert(String input, int available, SceneInputType inputType) {
        StringBuilder sb = new StringBuilder();
        int accepted = 0;
        int i = 0;
        while (i < input.length() && accepted < available) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (!isAccepted(cp, inputType)) {
                continue;
            }
            sb.appendCodePoint(cp);
            accepted++;
        }
        return new FilteredInsert(sb.toString(), accepted);
    }

    /**
     * 单码点是否被接受。
     *
     * @param cp        码点
     * @param inputType 输入类型
     * @return true 表示放行
     */
    private static boolean isAccepted(int cp, SceneInputType inputType) {
        if (Character.isISOControl(cp) || cp == '\n' || cp == '\r' || cp == '\t') {
            return false;
        }
        if (inputType == SceneInputType.NUMBER) {
            if (cp >= '0' && cp <= '9') {
                return true;
            }
            return cp == '.' || cp == '-' || cp == '+' || cp == 'e' || cp == 'E';
        }
        return true;
    }

    /** 过滤后的插入文本与码点数。 */
    private static final class FilteredInsert {
        /** 过滤后的文本。 */
        private final String text;
        /** 过滤后码点数。 */
        private final int codePointCount;

        /**
         * 创建过滤结果。
         *
         * @param text           文本
         * @param codePointCount 码点数
         */
        private FilteredInsert(String text, int codePointCount) {
            this.text = text;
            this.codePointCount = codePointCount;
        }
    }
}
