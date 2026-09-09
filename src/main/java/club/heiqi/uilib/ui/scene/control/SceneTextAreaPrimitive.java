package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.font.util.UnicodeTextClassifier;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.ClipboardBackend;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.text.layout.LogicalTextLine;
import club.heiqi.uilib.ui.text.layout.TextLayoutEngine;
import club.heiqi.uilib.ui.text.layout.TextMeasureFunction;
import club.heiqi.uilib.ui.text.layout.VisualLineLayout;

/**
 * SceneTextAreaPrimitive —— 无样式多行受控文本输入行为核心（D4：soft wrap 视觉行模型）。
 *
 * <p>只负责结构、输入行为、跨行 caret/选区状态和按视觉行文本布局绑定，不设置背景、边框、
 * 文本色、caret 色、cursor 或 padding 等 chrome。外观由上层 wrapper 自行组合。
 * 行内文本/caret/选区颜色由 Props 供给的 token 在 primitive 内上色（与 TextInput 不同：
 * TextArea 的 wrapper 把颜色 token 单向供给 primitive）。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值由外部 {@code value} 唯一持有（含 {@code \n} 换行符）；控件不缓存 value、不自改 value。
 * 内部维护 {@code caretIndex} 与 {@link TextSelection} 两个本地 UI 态（caret≡selection.focus，
 * 无选区时 anchor==focus）。所有写入都只经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <h3>结构（D4 视觉行五节点）</h3>
 * <pre>
 * root (COLUMN, clipChildren=true, focusable, padding)
 *   └─ viewport (COLUMN, scrollable=true, clipChildren=true, preferredHeight)
 *        ├─ content (COLUMN)  ← forEach 视觉行（独占，不与 show 共享）
 *        │    └─ row0 (ROW) → prefix + caretBefore + highlight + caretAfter + suffix
 *        │    └─ row1 (ROW) → ...
 *        └─ placeholderContainer (COLUMN)  ← show placeholder（独立容器）
 * </pre>
 * <p>每视觉行常驻五节点；caret 双槽位仅在「caret 所在视觉行 + focus 所在端」宽 1px 着色，
 * 其余行/槽位宽 0 透明。highlight 显示本视觉行选中段（跨行选区中间整行自然全段高亮，
 * 形成块状视觉）。</p>
 *
 * <h3>D4 soft wrap 视觉行模型</h3>
 * <ul>
 *   <li>逻辑行（以 {@code \n} 分隔）按 viewport 内容区可用宽经 {@link TextLayoutEngine}
 *       软换行为视觉行；渲染/命中/移动全部按视觉行；</li>
 *   <li>索引体系：布局引擎与视觉行用 char 索引，caret/selection 保持码点索引，
 *       转换集中在 {@link VisualLineModel}（码点→char 建逻辑行，char→码点做命中/移动几何）；</li>
 *   <li>可用宽经 layoutDoneSignal 桥接布局结果测得（两趟收敛：首帧宽未知按 0 不换行）；</li>
 *   <li>↑/↓ 按视觉行列保持、Home/End 视觉行首尾、caret 纵向跟随按视觉行号。</li>
 * </ul>
 *
 * <h3>B6 选区能力（D4 保持）</h3>
 * <ul>
 *   <li>鼠标跨视觉行拖选（anchor 固定、focus 随 MOVE 按视觉行+列解析）；Shift+点击/方向键/Home/End 扩展；</li>
 *   <li>双击选词（词不跨行）、三击选整逻辑行、Ctrl+A 全选；</li>
 *   <li>TEXT_INPUT/Backspace/Delete/Enter 有选区时替换/删除整段；</li>
 *   <li>readOnly 可选中/可移动/可 Shift 扩展，禁止编辑。</li>
 * </ul>
 */
public final class SceneTextAreaPrimitive {

    /** 闪烁周期（纳秒）：530ms 亮 + 430ms 暗（与 SceneTextInputPrimitive 对齐）。 */
    private static final long BLINK_PERIOD_NANOS = 960_000_000L;
    /** 闪烁亮相位时长（纳秒）。 */
    private static final long BLINK_ON_NANOS = 530_000_000L;
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
     * <p>颜色由 wrapper 供给，两种形态：{@code int} 常量（旧路径的裸值）与可空的
     * {@link ReadableSignal}&lt;Integer&gt; 语义色信号（主题路径）。信号非 null 时优先于同名
     * int 常量，且在 effect 派生内读取，因此主题切换自动重算、不重建节点；信号为 null 时
     * 完全回落同名 int 常量（旧 10 参构造器即此语义）。行文本由内部 forEach 创建、Result
     * 不暴露行节点，所以只能由 primitive 消费这些信号（契约 §2.8 授权修改本 Props 与颜色解析）。</p>
     *
     * @param value              当前文本（响应式只读，受控源，含 {@code \n} 换行符）
     * @param enabled            是否启用
     * @param readOnly           是否只读
     * @param placeholder        占位文本
     * @param maxLength          最大长度（按码点数）
     * @param caretVisibleColor  caret 可见时的颜色（focus 蓝，由 wrapper 传 token）
     * @param textNormalColor    普通文本色（enabled 且非 placeholder）
     * @param textPlaceholderColor placeholder 文本色（enabled 且空值）
     * @param textDisabledColor  禁用态文本色
     * @param onChange           文本变更回调
     * @param caretVisibleSignal caret 可见色语义信号；null = 回落 {@code caretVisibleColor}
     * @param textNormalSignal   正文前景语义信号；null = 回落 {@code textNormalColor}
     * @param textPlaceholderSignal 占位前景语义信号；null = 回落 {@code textPlaceholderColor}
     * @param textDisabledSignal 禁用前景语义信号；null = 回落 {@code textDisabledColor}
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
            Consumer<String> onChange,
            ReadableSignal<Integer> caretVisibleSignal,
            ReadableSignal<Integer> textNormalSignal,
            ReadableSignal<Integer> textPlaceholderSignal,
            ReadableSignal<Integer> textDisabledSignal
    ) {

        /**
         * 向后兼容 10 参构造：四个语义色信号为 null，颜色解析完全回落同名 int 常量
         * （外观与旧版逐像素一致）。
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     int caretVisibleColor,
                     int textNormalColor,
                     int textPlaceholderColor,
                     int textDisabledColor,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, caretVisibleColor,
                    textNormalColor, textPlaceholderColor, textDisabledColor, onChange,
                    null, null, null, null);
        }
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
                    applyTextInsert(v, caret, selectionAuthority[0], text, maxLength,
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
        // 行结构前缀和缓存（逻辑行构建源，码点索引体系；实例级，绝不能静态——多实例会跨实例串味）
        final LineStructureCache lineStructureCache = new LineStructureCache();
        // D4 视觉行模型：TextLayoutEngine 实例级接入 + char↔码点转换与视觉行几何查询
        final VisualLineModel visualModel = new VisualLineModel(rt, lineStructureCache);
        // 可用宽由布局桥接，视觉行及输入命中共享同一份已发布布局。
        final Signal<Integer> availableWidthSignal = Signal.create(Integer.valueOf(0));

        SceneNode root = SceneNode.column();
        root.setClipChildren(true);
        SceneControlTypography typography = SceneControlTypography.attach(rt, root);

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

        // D4 可用宽桥接：布局完成纪元 → 测 viewport 内容区宽（盒宽 - 左右 padding），变化才写入。
        // 两趟收敛：首帧可用宽未知按 0 不换行，桥接后重算视觉行再布局；真机帧管线自带 settle，
        // 测试需 doLayout + __setLayoutDoneEpoch 桥接（见 SceneTextAreaTest.doLayoutAndBridge）。
        rt.bind(rt.layoutDoneSignal(), epoch -> {
            LayoutBox vpBox = (LayoutBox) viewport.getCachedLayout();
            if (vpBox == null) {
                return;
            }
            int width = Math.max(0, vpBox.getWidth() - viewport.getPaddingLeft() - viewport.getPaddingRight());
            // applier 只写派生宽度；不订阅输出，以免形成多余反馈。
            availableWidthSignal.set(Integer.valueOf(width));
        });

        SceneInteractionState is = rt.interactionState(content);
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
                        && Boolean.TRUE.equals(is.focused().get())
                        && Boolean.TRUE.equals(blinkOn.get())));
        ReadableSignal<Boolean> isPlaceholder = Computed.create(
                () -> Boolean.valueOf(SceneTextUtils.nullSafe(props.value().get()).isEmpty()
                        && !SceneTextUtils.nullSafe(placeholder).isEmpty()
                        && !Boolean.TRUE.equals(is.focused().get())));

        // 字号、文本和宽度只在此处生成视觉布局。行 key、行内切片及命中均读此快照，
        // 避免根字号生成 key、默认行字号再次切片导致漏绘。Metrics 自带字体纪元失效。
        ReadableSignal<VisualLayoutSnapshot> visualLayout = Computed.create(
                visualModel.compute(props.value().get(), 0, typography.metrics().get()),
                () -> visualModel.compute(props.value().get(), availableWidthSignal.get().intValue(),
                        typography.metrics().get()));
        Computed<List<Integer>> visualKeys = Computed.create(() -> {
            List<VisualLineLayout> vlines = visualLayout.get().lines();
            List<Integer> keys = new ArrayList<>(vlines.size());
            for (VisualLineLayout vl : vlines) {
                keys.add(Integer.valueOf(vl.getVisualStartIndex()));
            }
            return keys;
        });

        // 按视觉行渲染（key=visualStartIndex；行内段/槽位按 key 现查视觉行号，视觉行重排自动跟随）
        rt.forEach(content, visualKeys, key -> key,
                key -> buildVisualRow(rt, props, selection, caretVisible, isPlaceholder,
                        typography, visualLayout, key));

        // placeholder：value 空且未聚焦时显示单行占位文本
        rt.show(placeholderContainer, isPlaceholder, () -> {
            SceneNode ph = new SceneNode();
            typography.bindText(ph);
            ph.setText(SceneTextUtils.nullSafe(placeholder));
            ph.setHitTestable(false);
            rt.bindComputed(() -> resolvePlaceholderColor(props, props.enabled().get()),
                    ph::setTextColor);
            return ph;
        });

        // 纵向滚动
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        // 布局/字号/视口改变也会移动光标；先派生并去重跟随目标，再读取滚动位置。
        // 布局缺失时发布 null，下一次布局通知重试；保留已成功目标，避免无关重布局后回拉。
        final CaretScrollTarget[] followed = {null};
        rt.bindComputed(() -> {
            rt.layoutDoneSignal().get();
            VisualLayoutSnapshot layout = visualLayout.get();
            int caret = caretIndex.get().intValue();
            LayoutBox box = (LayoutBox) viewport.getCachedLayout();
            return box == null ? null : new CaretScrollTarget(layout, caret, box.getHeight(),
                    viewport.getPaddingTop(), viewport.getPaddingBottom());
        }, target -> Effect.untrack(() -> {
            if (target == null || target.viewportHeight() <= 0 || target.equals(followed[0])) {
                return;
            }
            VisualLayoutSnapshot layout = target.layout();
            int row = VisualLineModel.visualRowOfCaret(layout.lines(), layout.value(), target.caretIndex());
            int lineH = layout.metrics().lineHeightPx();
            int caretTop = target.paddingTop() + row * lineH;
            int caretBottom = caretTop + lineH;
            int scroll = scrollSignal.get().intValue();
            int next = scroll;
            if (caretBottom > scroll + target.viewportHeight() - target.paddingBottom()) {
                next = caretBottom - target.viewportHeight() + target.paddingBottom();
            } else if (caretTop < scroll + target.paddingTop()) {
                next = caretTop - target.paddingTop();
            }
            int contentHeight = layout.lines().size() * lineH + target.paddingTop() + target.paddingBottom();
            next = Math.max(0, Math.min(Math.max(0, contentHeight - target.viewportHeight()), next));
            followed[0] = target;
            if (next != scroll) {
                scrollSignal.set(Integer.valueOf(next));
            }
        }));

        rt.focusable(content, props.enabled());

        // 点击/拖选定位：算全局码点索引（视觉行命中）
        rt.on(content, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            // 交互重置闪烁相位
            blinkPhaseStart[0] = ev.getTimeNanos();
            // E4 右键：打开上下文菜单（不移动 caret/选区；锚点=指针 host 坐标）
            if (ev.getButton() == SceneMouseButton.RIGHT) {
                SceneContextMenu.open(rt,
                        ctx.getRawPointerX() - ctx.getTreeRootAbsX(),
                        ctx.getRawPointerY() - ctx.getTreeRootAbsY(),
                        contextMenuItems.get());
                return;
            }
            VisualLayoutSnapshot layout = visualLayout.get();
            String value = layout.value();
            int pos = caretFromPointer(root, viewport, layout, ctx);
            if (pos < 0) {
                return;
            }
            int clickCount = ev.getClickCount();
            if (clickCount >= 3) {
                // 三击：选整逻辑行
                TextSelection line = SceneTextGeometry.lineSelection(value, pos);
                setSelection.accept(Integer.valueOf(line.anchorCp()), Integer.valueOf(line.focusCp()));
                dragAnchor[0] = -1;
                return;
            }
            if (clickCount == 2) {
                // 双击：选词（词不跨逻辑行，\n 天然分隔）
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
                TextSelection cur = selectionAuthority[0];
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
            VisualLayoutSnapshot layout = visualLayout.get();
            int pos = caretFromPointer(root, viewport, layout, ctx);
            if (pos < 0) {
                return;
            }
            setSelection.accept(Integer.valueOf(dragAnchor[0]), Integer.valueOf(pos));
        });

        rt.on(content, SceneEventType.POINTER_UP, (ev, ctx) -> {
            dragAnchor[0] = -1;
            blinkPhaseStart[0] = ev.getTimeNanos();
        });
        rt.on(content, SceneEventType.POINTER_CANCEL, (ev, ctx) -> dragAnchor[0] = -1);

        // 文本输入（接受 \n；有选区时替换整段）
        rt.on(content, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            // 交互重置闪烁相位
            blinkPhaseStart[0] = ev.getTimeNanos();
            String raw = ev.getText();
            if (raw == null || raw.isEmpty()) {
                return;
            }
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            applyTextInsert(cur, caretPos, selectionAuthority[0], raw, maxLength, props.onChange(), setSelection,
                    editHistory, true, ev.getTimeNanos());
        });

        // 键盘编辑键
        rt.on(content, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            // 交互重置闪烁相位
            blinkPhaseStart[0] = ev.getTimeNanos();
            String cur = SceneTextUtils.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, Integer.valueOf(caretAuthority[0]));
            int count = SceneTextGeometry.codePointCount(cur);
            VisualLayoutSnapshot layout = visualLayout.get();
            List<VisualLineLayout> vlines = layout.lines();
            // 几何导航使用绘制快照的文本与行边界；编辑操作仍读取受控 value。
            String visualValue = layout.value();
            int visualCaret = SceneTextGeometry.clampCaretIndex(visualValue, Integer.valueOf(caretAuthority[0]));
            SceneKey key = ev.getKey();
            // === Ctrl 组合区（词跳转/文首尾/全选/剪贴板） ===
            if (ev.isControlDown()) {
                if (key == SceneKey.ARROW_LEFT) {
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
                            applyTextInsert(cur, caretPos, selectionAuthority[0], text, maxLength,
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
            if (key == SceneKey.ARROW_UP) {
                // D4：视觉行 -1，视觉行内列保持
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                        VisualLineModel.moveVerticalCp(vlines, visualValue, visualCaret, -1), setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.ARROW_DOWN) {
                // D4：视觉行 +1，视觉行内列保持
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                        VisualLineModel.moveVerticalCp(vlines, visualValue, visualCaret, 1), setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.HOME) {
                // D4：视觉行首
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                        VisualLineModel.homeCp(vlines, visualValue, visualCaret), setCaretIndex, setSelection);
                return;
            }
            if (key == SceneKey.END) {
                // D4：视觉行末
                moveCaretWithShift(ev.isShiftDown(), selectionAuthority[0],
                        VisualLineModel.endCp(vlines, visualValue, visualCaret), setCaretIndex, setSelection);
                return;
            }
            if (Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            TextSelection sel = selectionAuthority[0];
            if (key == SceneKey.ENTER) {
                if (sel.isActive()) {
                    String next = SceneTextGeometry.replaceRangeCp(cur, sel.startCp(), sel.endCp(), "\n");
                    editHistory.record(cur, next, caretPos, sel.startCp() + 1, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(sel.startCp() + 1), Integer.valueOf(sel.startCp() + 1));
                } else {
                    int offset = SceneTextGeometry.charOffsetForCodePointIndex(cur, caretPos);
                    String next = cur.substring(0, offset) + "\n" + cur.substring(offset);
                    editHistory.record(cur, next, caretPos, caretPos + 1, false, ev.getTimeNanos());
                    props.onChange().accept(next);
                    setSelection.accept(Integer.valueOf(caretPos + 1), Integer.valueOf(caretPos + 1));
                }
            } else if (key == SceneKey.BACKSPACE) {
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

        return new Result(root, viewport, content, placeholderContainer, scrollSignal,
                caretIndex, selection, caretVisible, isPlaceholder);
    }

    /**
     * 构建单视觉行五节点（prefix + caretBefore + highlight + caretAfter + suffix）。
     *
     * <p>caret 双槽位上色/切宽：本视觉行且 caretVisible 且 focus 在本槽侧时 1px 着色。
     * highlight 显示本视觉行选中段（跨行选区中间整行自然全段高亮）。行内段/槽位按 key
     * （视觉行起始 char）在最新视觉行列表中现查行号——视觉行重排时自动跟随。</p>
     *
     * @param rt           场景运行时
     * @param props        输入契约（含颜色 token）
     * @param selection    选区 signal
     * @param caretVisible caret 是否可见（enabled 且 focused）
     * @param isPlaceholder 当前是否处于 placeholder 态
     * @param typography 控件内部字号与行高度量
     * @param visualLayout 文本、行列表与度量的共享快照
     * @param keyChar      本视觉行的 key（起始 char 索引，稳定）
     * @return 视觉行根节点
     */
    private static SceneNode buildVisualRow(SceneRuntime rt, Props props,
                                            ReadableSignal<TextSelection> selection,
                                            ReadableSignal<Boolean> caretVisible,
                                            ReadableSignal<Boolean> isPlaceholder,
                                            SceneControlTypography typography,
                                            ReadableSignal<VisualLayoutSnapshot> visualLayout,
                                            Integer keyChar) {
        SceneNode row = SceneNode.row();
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(ROW_GAP);
        row.setClipChildren(true);

        SceneNode prefix = new SceneNode();
        typography.bindText(prefix);
        prefix.setHitTestable(false);
        row.appendChild(prefix);

        SceneNode caretBefore = new SceneNode();
        caretBefore.setPreferredWidth(CARET_WIDTH);
        typography.bindCaret(caretBefore);
        caretBefore.setHitTestable(false);
        // 空文本叶兜底：宽 0 时真正归零不撑满行（与 TextInput 五节点同款防撑满 bug）
        caretBefore.setText("");
        row.appendChild(caretBefore);

        SceneNode highlight = new SceneNode();
        typography.bindText(highlight);
        highlight.setHitTestable(false);
        row.appendChild(highlight);

        SceneNode caretAfter = new SceneNode();
        caretAfter.setPreferredWidth(0);
        typography.bindCaret(caretAfter);
        caretAfter.setHitTestable(false);
        caretAfter.setText("");
        row.appendChild(caretAfter);

        SceneNode suffix = new SceneNode();
        typography.bindText(suffix);
        suffix.setHitTestable(false);
        row.appendChild(suffix);

        // 视觉行内 prefix：选区前段 [视觉行首, selStart)
        rt.bindComputed(() -> {
            VisualLayoutSnapshot layout = visualLayout.get();
            List<VisualLineLayout> vlines = layout.lines();
            int rowIdx = VisualLineModel.visualRowOfKey(vlines, keyChar.intValue());
            return VisualLineModel.segmentText(vlines, layout.value(), rowIdx,
                    Integer.MIN_VALUE, selection.get().startCp());
        }, prefix::setText);
        // 视觉行内 highlight：选中段 [selStart, selEnd) ∩ 本视觉行
        rt.bindComputed(() -> {
            VisualLayoutSnapshot layout = visualLayout.get();
            List<VisualLineLayout> vlines = layout.lines();
            int rowIdx = VisualLineModel.visualRowOfKey(vlines, keyChar.intValue());
            return VisualLineModel.segmentText(vlines, layout.value(), rowIdx,
                    selection.get().startCp(), selection.get().endCp());
        }, highlight::setText);
        // 视觉行内 suffix：选区后段 [selEnd, 视觉行末)
        rt.bindComputed(() -> {
            VisualLayoutSnapshot layout = visualLayout.get();
            List<VisualLineLayout> vlines = layout.lines();
            int rowIdx = VisualLineModel.visualRowOfKey(vlines, keyChar.intValue());
            return VisualLineModel.segmentText(vlines, layout.value(), rowIdx,
                    selection.get().endCp(), Integer.MAX_VALUE);
        }, suffix::setText);

        // 视觉行内文本色：按 isPlaceholder/enabled 解析三态色（normal/placeholder/disabled）
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

        // caret 是否在本视觉行：抽单个 Computed 复用（key 现查视觉行号 + caret 唯一归属）
        Computed<Boolean> inRow = Computed.create(() -> {
            VisualLayoutSnapshot layout = visualLayout.get();
            String value = layout.value();
            List<VisualLineLayout> vlines = layout.lines();
            int rowIdx = VisualLineModel.visualRowOfKey(vlines, keyChar.intValue());
            return Boolean.valueOf(rowIdx >= 0
                    && VisualLineModel.caretInVisualRow(vlines, value, selection.get().focusCp(), rowIdx));
        });
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
        // 槽位颜色：颜色解析优先读语义信号（非 null），再回落 int 常量
        rt.bindComputed(() -> resolveCaretColor(props, caretVisible.get(), caretAtSelStart.get()),
                caretBefore::setBackgroundColor);
        rt.bindComputed(() -> resolveCaretColor(props, caretVisible.get(), caretAtSelEnd.get()),
                caretAfter::setBackgroundColor);

        return row;
    }

    /**
     * 解析行内文本色：placeholder 态用 placeholder 色，否则按 enabled 选 normal/disabled。
     *
     * <p>三态色各自优先读同名语义信号（在 effect 派生内读取，建立主题依赖），信号为 null
     * 时回落同名 int 常量。</p>
     *
     * @param props        输入契约（含三态色 int 常量与可空语义信号）
     * @param isPlaceholder 是否处于 placeholder 态
     * @param enabled      是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Props props, Boolean isPlaceholder, Boolean enabled) {
        boolean en = Boolean.TRUE.equals(enabled);
        if (Boolean.TRUE.equals(isPlaceholder)) {
            return en ? placeholderColor(props) : disabledColor(props);
        }
        return en ? normalColor(props) : disabledColor(props);
    }

    /**
     * 解析 placeholder 容器文本色：enabled 用占位色，否则用禁用色；信号优先于 int 常量。
     *
     * @param props   输入契约
     * @param enabled 是否启用
     * @return 占位文本色 ARGB
     */
    private static int resolvePlaceholderColor(Props props, Boolean enabled) {
        return Boolean.TRUE.equals(enabled) ? placeholderColor(props) : disabledColor(props);
    }

    /**
     * 解析 caret 色：槽位激活且 caret 可见时用 caret 色，否则全透明。
     *
     * @param props        输入契约
     * @param caretVisible caret 是否可见（enabled 且 focused 且闪烁亮相位）
     * @param slotActive   本槽位是否激活（focus 在本槽侧）
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Props props, Boolean caretVisible, Boolean slotActive) {
        if (Boolean.TRUE.equals(caretVisible) && Boolean.TRUE.equals(slotActive)) {
            return caretColor(props);
        }
        return CARET_TRANSPARENT;
    }

    /** caret 色：信号非 null 优先，否则 int 常量。 */
    private static int caretColor(Props props) {
        ReadableSignal<Integer> signal = props.caretVisibleSignal();
        return signal != null ? signal.get().intValue() : props.caretVisibleColor();
    }

    /** 正文前景：信号非 null 优先，否则 int 常量。 */
    private static int normalColor(Props props) {
        ReadableSignal<Integer> signal = props.textNormalSignal();
        return signal != null ? signal.get().intValue() : props.textNormalColor();
    }

    /** 占位前景：信号非 null 优先，否则 int 常量。 */
    private static int placeholderColor(Props props) {
        ReadableSignal<Integer> signal = props.textPlaceholderSignal();
        return signal != null ? signal.get().intValue() : props.textPlaceholderColor();
    }

    /** 禁用前景：信号非 null 优先，否则 int 常量。 */
    private static int disabledColor(Props props) {
        ReadableSignal<Integer> signal = props.textDisabledSignal();
        return signal != null ? signal.get().intValue() : props.textDisabledColor();
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
     * 在 caret/选区处插入文本（TEXT_INPUT 与 Ctrl+V 共用）：
     * 有选区替换整段，可用空间按"删除选区后剩余"计；过滤后无有效文本时无副作用。
     * 与 {@link SceneTextInputPrimitive} 的 applyTextInsert 对齐（本版 filter 保留 \n）。
     *
     * @param cur          当前文本
     * @param caretPos     当前 caret 码点索引
     * @param sel          当前选区
     * @param raw          原始输入文本
     * @param maxLength    最大长度（码点）
     * @param onChange     变更回调
     * @param setSelection 选区写入口（三态同步）
     */
    private static void applyTextInsert(String cur, int caretPos, TextSelection sel, String raw,
                                        int maxLength, Consumer<String> onChange,
                                        BiConsumer<Integer, Integer> setSelection,
                                        TextEditHistory editHistory, boolean mergeable, long timeNanos) {
        int selStart = sel.isActive() ? sel.startCp() : caretPos;
        int selEnd = sel.isActive() ? sel.endCp() : caretPos;
        int removed = selEnd - selStart;
        String filtered = filterForInsert(raw,
                Math.max(0, maxLength - (SceneTextGeometry.codePointCount(cur) - removed)));
        if (filtered.isEmpty()) {
            return;
        }
        String next = SceneTextGeometry.replaceRangeCp(cur, selStart, selEnd, filtered);
        int newCaret = selStart + SceneTextGeometry.codePointCount(filtered);
        // E1：先记历史（caretBefore=编辑发生位、caretAfter=新 caret），再上抛
        editHistory.record(cur, next, selStart, newCaret, mergeable, timeNanos);
        onChange.accept(next);
        setSelection.accept(Integer.valueOf(newCaret), Integer.valueOf(newCaret));
    }

    /**
     * 由指针局部坐标解析全局 caret 码点索引（点击/拖选共用；D4 视觉行命中）。
     *
     * @return 全局码点索引；布局未建立时返回 -1（调用方忽略）
     */
    private static int caretFromPointer(SceneNode root, SceneNode viewport,
                                        VisualLayoutSnapshot layout, SceneEventContext ctx) {
        if (root.getCachedLayout() == null || viewport.getCachedLayout() == null) {
            return -1;
        }
        if (layout.value().isEmpty()) {
            return 0;
        }
        return VisualLineModel.caretCpFromPointer(layout.lines(), layout.value(),
                ctx.getLocalPointerX(), ctx.getLocalPointerY(), layout.metrics().lineHeightPx());
    }

    // ==================== 编辑操作 ====================

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
            // 换行类统一折叠为 \n 保留；过滤其他 ISO 控制字符（\t、剥离类等）
            if (UnicodeTextClassifier.isLineBreak(cp)) {
                sb.append('\n');
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

    // ==================== 视觉行模型（D4） ====================

    /** 同一次布局依据及其结果，供行结构、绘制和输入共同读取。 */
    @Desugar
    private record VisualLayoutSnapshot(String value, SceneControlTypography.Metrics metrics,
                                        List<VisualLineLayout> lines) {
    }

    /** 跟随条件按值去重；滚动本身不构成新的跟随请求。 */
    @Desugar
    private record CaretScrollTarget(VisualLayoutSnapshot layout, int caretIndex,
                                     int viewportHeight, int paddingTop, int paddingBottom) {
    }

    /**
     * 视觉行模型：TextLayoutEngine 实例级接入 + char↔码点索引转换 + 视觉行几何查询。
     *
     * <p>索引体系：TextLayoutEngine/LogicalTextLine/VisualLineLayout 全部使用 char 索引；
     * TextArea 的 caret/selection 保持码点索引（既有 API/测试兼容）。转换点集中在本类：
     * 逻辑行构建（码点→char 区间，{@link SceneTextGeometry#charOffsetForCodePointIndex}）
     * 与命中/移动几何（char→码点，{@code String#codePointCount} 往返）。</p>
     *
     * <p>compute 缓存：逻辑行列表按 value 复用；视觉行列表由 {@link TextLayoutEngine} 内部缓存
     * （内容指纹 + 可用宽 + 纪元 + 行高 + 软换行开关），稳态返回同一列表实例、零测量。</p>
     */
    private static final class VisualLineModel {
        private final SceneRuntime rt;
        private final TextLayoutEngine engine = new TextLayoutEngine();
        private final LineStructureCache lineCache;
        /** 当前测量字号（compute 时设定；引擎缓存命中时不调用 measure，可变字段安全）。 */
        private int measureFontSize;
        /** 文本测量适配：整测量走 rt.measureTextWidth，前缀向量覆盖为逐前缀整测量（与点击路径像素一致）。 */
        private final TextMeasureFunction measure = new TextMeasureFunction() {
            @Override
            public int widthOf(String text) {
                return rt.measureTextWidth(SceneTextUtils.nullSafe(text), measureFontSize);
            }

            @Override
            public int[] prefixWidths(String text) {
                return SceneTextGeometry.buildPrefixWidths(rt, SceneTextUtils.nullSafe(text), measureFontSize);
            }
        };
        /** 逻辑行缓存依据的 value（仅 equals 比对判失效，不是真值副本）。 */
        private String cachedValue;
        /** 与 cachedValue 匹配的逻辑行列表。 */
        private List<LogicalTextLine> cachedLogicalLines;

        private VisualLineModel(SceneRuntime rt, LineStructureCache lineCache) {
            this.rt = rt;
            this.lineCache = lineCache;
        }

        /**
         * 计算（或复用缓存的）视觉行布局。
         *
         * @param value          当前文本（可为 null，内部 nullSafe）
         * @param availableWidth 文本内容盒可用宽度；{@code <=0} 视为不限宽（不软换行）
         * @param metrics        字号、行高及字体纪元的同一快照
         * @return 共享视觉布局，内部列表复用既有引擎缓存
         */
        private VisualLayoutSnapshot compute(String value, int availableWidth,
                                              SceneControlTypography.Metrics metrics) {
            String safe = SceneTextUtils.nullSafe(value);
            if (cachedLogicalLines == null || !safe.equals(cachedValue)) {
                cachedLogicalLines = buildLogicalLines(safe);
                cachedValue = safe;
            }
            // 引擎按行高/字体纪元缓存，但相同行高并不代表相同字宽。
            // 字号改变时只失效本控件持有的布局缓存，防止复用旧换行边界。
            if (measureFontSize != metrics.fontSizePx()) {
                engine.invalidate();
                measureFontSize = metrics.fontSizePx();
            }
            List<VisualLineLayout> lines = engine.layout(cachedLogicalLines, availableWidth,
                    metrics.measureEpoch(), metrics.lineHeightPx(), true, measure);
            return new VisualLayoutSnapshot(safe, metrics, Collections.unmodifiableList(lines));
        }

        /**
         * 逻辑行构建：LineStructureCache 的码点行结构 → LogicalTextLine（char 区间 + 行文本）。
         */
        private List<LogicalTextLine> buildLogicalLines(String value) {
            LineStructureCache c = lineCache.get(value);
            List<LogicalTextLine> out = new ArrayList<>(c.lines.length);
            for (int row = 0; row < c.lines.length; row++) {
                int startCp = c.lineStartCp[row];
                int startChar = SceneTextGeometry.charOffsetForCodePointIndex(value, startCp);
                int endChar = SceneTextGeometry.charOffsetForCodePointIndex(value, startCp + c.lineLenCp[row]);
                out.add(new LogicalTextLine(startChar, endChar, c.lines[row]));
            }
            return out;
        }

        // ==================== 视觉行几何查询（static，接收视觉行列表） ====================

        /** 视觉行 char 索引 → 全局码点索引。 */
        private static int cpOf(String value, int charIndex) {
            return value.codePointCount(0, charIndex);
        }

        /**
         * 按 key（视觉行起始 char）查视觉行号；未命中返回 -1。
         */
        private static int visualRowOfKey(List<VisualLineLayout> vlines, int keyChar) {
            for (int i = 0; i < vlines.size(); i++) {
                if (vlines.get(i).getVisualStartIndex() == keyChar) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * caret 码点 → 视觉行号（唯一归属，纯二分）。
         *
         * <p>归属规则：最后一个「起始 char ≤ caret char」的视觉行。软换行断行点
         * （caret == 前一行末 == 后一行首）归<b>后一行行首</b>——caret 由点击行首、
         * ↑/↓ 到达该列时自然落在后一行，且 caret 显示位置与后一行行首一致；
         * 逻辑行边界（\n 占 char，两行区间不相邻）不受影响；空视觉行唯一归属自身。</p>
         */
        private static int visualRowOfCaret(List<VisualLineLayout> vlines, String value, int caretCp) {
            int n = vlines.size();
            if (n == 0) {
                return 0;
            }
            int caretChar = SceneTextGeometry.charOffsetForCodePointIndex(value, caretCp);
            int lo = 0;
            int hi = n - 1;
            int ans = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (vlines.get(mid).getVisualStartIndex() <= caretChar) {
                    ans = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return ans;
        }

        /** caret 是否落在指定视觉行内（按 visualRowOfCaret 唯一归属）。 */
        private static boolean caretInVisualRow(List<VisualLineLayout> vlines, String value, int caretCp,
                                                int visualRow) {
            if (visualRow < 0 || visualRow >= vlines.size()) {
                return false;
            }
            return visualRowOfCaret(vlines, value, caretCp) == visualRow;
        }

        /**
         * 垂直移动 caret（↑/↓）：视觉行 ±1，视觉行内码点列 clamp 保持列记忆。
         * 越界：上越界归 0，下越界归文尾。
         */
        private static int moveVerticalCp(List<VisualLineLayout> vlines, String value, int caretCp, int delta) {
            int n = vlines.size();
            if (n == 0) {
                return 0;
            }
            int row = visualRowOfCaret(vlines, value, caretCp);
            int startCp = cpOf(value, vlines.get(row).getVisualStartIndex());
            int col = caretCp - startCp;
            int newRow = row + delta;
            if (newRow < 0) {
                return 0;
            }
            if (newRow >= n) {
                return SceneTextGeometry.codePointCount(value);
            }
            VisualLineLayout target = vlines.get(newRow);
            int targetStartCp = cpOf(value, target.getVisualStartIndex());
            int targetLenCp = cpOf(value, target.getVisualEndIndex()) - targetStartCp;
            return targetStartCp + Math.min(col, targetLenCp);
        }

        /** Home：caret 所在视觉行首码点。 */
        private static int homeCp(List<VisualLineLayout> vlines, String value, int caretCp) {
            int n = vlines.size();
            if (n == 0) {
                return 0;
            }
            int row = visualRowOfCaret(vlines, value, caretCp);
            return cpOf(value, vlines.get(row).getVisualStartIndex());
        }

        /** End：caret 所在视觉行末码点。 */
        private static int endCp(List<VisualLineLayout> vlines, String value, int caretCp) {
            int n = vlines.size();
            if (n == 0) {
                return 0;
            }
            int row = visualRowOfCaret(vlines, value, caretCp);
            return cpOf(value, vlines.get(row).getVisualEndIndex());
        }

        /**
         * 指针命中：relY/行高 → 视觉行（clamp）→ 行内最近码点边界（char）→ 全局码点。
         */
        private static int caretCpFromPointer(List<VisualLineLayout> vlines, String value,
                                              int relX, int relY, int lineH) {
            int n = vlines.size();
            if (n == 0) {
                return 0;
            }
            int row = Math.max(0, Math.min(n - 1, relY / lineH));
            return cpOf(value, vlines.get(row).resolveClosestCaretIndex(relX));
        }

        /**
         * 视觉行内文本段：全局码点区间 {@code [segStartCp, segEndCp)} 与视觉行的交集。
         *
         * <p>跨视觉行选区时中间整行交集成全行，首/末行只取局部段，形成块状高亮视觉。</p>
         */
        private static String segmentText(List<VisualLineLayout> vlines, String value, int visualRow,
                                          int segStartCp, int segEndCp) {
            if (visualRow < 0 || visualRow >= vlines.size()) {
                return "";
            }
            VisualLineLayout vl = vlines.get(visualRow);
            int startCp = cpOf(value, vl.getVisualStartIndex());
            int endCp = cpOf(value, vl.getVisualEndIndex());
            int s = Math.max(startCp, Math.min(endCp, segStartCp));
            int e = Math.max(s, Math.min(endCp, segEndCp));
            return SceneTextGeometry.substringByCodePoints(value, s, e);
        }
    }

    // ==================== 行结构前缀和缓存（逻辑行构建源） ====================

    /**
     * 行结构前缀和缓存：单趟 O(L) 扫描 value 构建，命中时查表 O(1)。
     *
     * <p>实例级（create() 闭包内 final 持有），绝不能静态字段——多 TextArea 实例
     * 会跨实例串味。失效键仅 value.equals(cachedValue)，行结构只依赖 value 字符串
     * 本身（纯字符切分），不依赖 fontSize/epoch。D4 起仅作为逻辑行构建源
     * （{@link VisualLineModel#buildLogicalLines}），渲染/命中/移动几何均由视觉行模型承载。</p>
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
        /** 缓存 split 结果，供逻辑行构建取行文本。 */
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
         * 单趟扫描 value 重建行结构前缀和。语义与 split("\n", -1) 一致：保留空行、
         * 尾空行、连续 \n 产生的空行。
         *
         * @param value nullSafe 后的文本（非 null）
         */
        private void rebuild(String value) {
            if (value.isEmpty()) {
                // 空文本视作 1 行
                lineStartCp = new int[] {0, 0};
                lineLenCp = new int[] {0};
                lines = new String[] {""};
                cachedValue = value;
                return;
            }
            // 先数行数（换行类个数 + 1，\r\n 折叠为一个换行）
            int lineCount = 1;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (UnicodeTextClassifier.isLineBreak(ch)) {
                    if (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') {
                        i++;
                    }
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
                if (UnicodeTextClassifier.isLineBreak(ch)) {
                    String lineText = value.substring(lineStartChar, i);
                    int lineCp = SceneTextGeometry.codePointCount(lineText);
                    lineStartCp[row] = lineStartCpIdx;
                    lineLenCp[row] = lineCp;
                    lines[row] = lineText;
                    row++;
                    int skip = (ch == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') ? 2 : 1;
                    cpIdx += skip;             // \r\n 占 2 个码点，折叠为一个换行
                    lineStartCpIdx = cpIdx;    // 下一行起始全局码点索引
                    lineStartChar = i + skip;
                    i += skip;
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
