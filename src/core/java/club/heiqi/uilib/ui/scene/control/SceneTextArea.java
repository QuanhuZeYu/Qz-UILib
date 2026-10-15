package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.inner;
import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.border;
import static club.heiqi.uilib.ui.scene.control.SceneEditCommands.toDisplay;
import static club.heiqi.uilib.ui.scene.control.SceneEditCommands.fromDisplay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy;
import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/**
 * 正式多行字段，草稿、历史及提交全部由业务持有的 MULTI_LINE 状态管理。
 * 默认宽填充、高自然；正文视口应由父页面明确声明 height 为 fill 或 fixed，边界仍用 Size 的 min/max。
 * 字段自身滚动正文，不需要也不应为正文声明 node.scrollable。
 * 行为合同见 UILib editing spec 1.1.2；焦点、capture、MountScope 和 callback guard 沿核心管线。
 */
public final class SceneTextArea {
    private SceneTextArea() {}

    /** 多行 Tab 的编辑语义；制表位几何仍由节点 TextPolicy 决定。 */
    public enum TabMode {
        NAVIGATE,
        INSERT_TAB
    }

    public static SceneNode create(
            ScenePageContext context,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit) {
        return create(context, state, theme, readOnly, onSubmit, SubmitPolicy.CONFIRM);
    }

    public static SceneNode create(
            ScenePageContext context,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit,
            SubmitPolicy policy) {
        return create(context, state, theme, readOnly, onSubmit, policy, () -> TabMode.NAVIGATE);
    }

    /** 动态选择 Tab 行为；Shift+Tab 始终交回范围导航，IME 仍优先消费。 */
    public static SceneNode create(
            ScenePageContext context,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit,
            SubmitPolicy policy,
            ReadableSignal<TabMode> tabMode) {
        return create(context,state,theme,readOnly,onSubmit,policy,tabMode,true);
    }

    /** 外层表面负责装饰时关闭本体边框；编辑、选择、组字与滚动语义不变。 */
    public static SceneNode create(ScenePageContext context,SceneEditState state,
            ReadableSignal<SceneTheme> theme,ReadableSignal<Boolean> readOnly,Consumer<Submission> onSubmit,
            SubmitPolicy policy,ReadableSignal<TabMode> tabMode,boolean framed) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(state, "state");
        if (context.domain() != state.domain()) throw new IllegalArgumentException("字段与编辑状态必须同域");
        if (state.mode() != SceneEditState.Mode.MULTI_LINE)
            throw new IllegalArgumentException("多行字段要求 MULTI_LINE 状态");
        Control control =
                new Control(
                        state,
                        Objects.requireNonNull(theme, "theme"),
                        Objects.requireNonNull(readOnly, "readOnly"),
                        Objects.requireNonNull(onSubmit, "onSubmit"),
                        Objects.requireNonNull(policy, "policy"),
                        Objects.requireNonNull(tabMode, "tabMode"),framed);
        return SceneEditBindings.create(control, state, theme, readOnly, TextPolicy.NORMAL);
    }

    private static final class Control implements SceneControl, SceneCompositionTarget {
        private final SceneEditState state;
        private final ReadableSignal<SceneTheme> theme;
        private final ReadableSignal<Boolean> readOnly;
        private final ReadableSignal<TabMode> tabMode;
        private final boolean framed;
        private final SceneEditCommands commands;
        // 只通知呈现归属变化；不保存草稿、选区、会话或焦点权威。
        private final Signal<Boolean> viewChanged;
        private TextLayout source, presented;
        private SceneTextAreaGeometry editing;
        private TextPolicy layoutPolicy;
        private Snapshot presentation;
        private int width = -1, height = -1, offsetX, offsetY, paintedX = -1, paintedY = -1;
        private int affinity = -1, affinityPosition = -1;
        private SceneCaretBoundary boundary;
        private float goalX = Float.NaN;
        private boolean reveal;

        Control(
                SceneEditState state,
                ReadableSignal<SceneTheme> theme,
                ReadableSignal<Boolean> readOnly,
                Consumer<Submission> onSubmit,
                SubmitPolicy policy,
                ReadableSignal<TabMode> tabMode,boolean framed) {
            this.tabMode = tabMode;this.framed=framed;
            this.state = state;
            this.theme = theme;
            this.readOnly = readOnly;
            this.commands = new SceneEditCommands(state, readOnly, onSubmit, policy);
            viewChanged = state.domain().signal(false);
        }

        @Override
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
            return Arrays.asList(state.snapshot(), viewChanged);
        }

        @Override
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) {
            return Arrays.asList(theme,readOnly);
        }

        @Override
        public MeasuredSize measure(
                SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            TextLayout full =
                    text.layout(
                            node.text(),
                            node.style(),
                            constraints.availableWidth,
                            node.textPolicy());
            return new MeasuredSize(full.width, contentHeight(full));
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer childMeasurer) {
            return new ChildPlacements(Collections.emptyList());
        }

        @Override
        public TextLayout presentText(
                SceneNode node,
                TextLayout full,
                int innerWidth,
                int innerHeight,
                SceneTextMeasurer text) {
            // 框架的 full 已按实际 innerWidth 排版；保留所有行，由 paint clip 截视口。
            Snapshot next = state.snapshot().get();
            boolean changed = source != full,
                    resized = width != innerWidth || height != innerHeight;
            boolean layoutChanged =
                    editing == null
                            || !node.textPolicy().equals(layoutPolicy)
                            || changed && !editing.sameGeometry(full);
            if (layoutChanged) {
                affinity = -1;
                affinityPosition = -1;
                goalX = Float.NaN;
            }
            if (changed || layoutChanged)
                editing = new SceneTextAreaGeometry(full, node.textPolicy());
            layoutPolicy = node.textPolicy();
            if (resized) goalX = Float.NaN;
            int position = toDisplay(next, next.caret);
            if (position != affinityPosition) {
                affinity = -1;
                goalX = Float.NaN;
            }
            if (boundary != null && (boundary.position != position
                    || !SceneCaretBoundary.sameProjection(presentation, next))) boundary = null;
            if (boundary != null) {
                int selectedLine = boundary.lineAt(full);
                if (selectedLine >= 0) affinity = selectedLine;
                else boundary = null;
            }
            int lineIndex = editing.lineAt(position, affinity);
            int maximumX = maximumX(full, innerWidth), maximumY = maximumY(full, innerHeight);
            offsetX = clamp(offsetX, maximumX);
            offsetY = clamp(offsetY, maximumY);
            if (reveal
                    || layoutChanged
                    || resized
                    || presentation == null
                    || presentation.caret != next.caret
                    || presentation.revision != next.revision
                    || presentation.composing != next.composing
                    || !presentation.composition.equals(next.composition)
                    || next.composing && presentation.anchor != next.anchor) {
                if (lineIndex >= 0) {
                    TextLayout.Line line = full.lines().get(lineIndex);
                    float x = editing.caretX(lineIndex, position, boundary);
                    if (x < offsetX) offsetX = (int) Math.floor(x);
                    if (x > offsetX + Math.max(0, innerWidth - 1))
                        offsetX = (int) Math.ceil(x - Math.max(0, innerWidth - 1));
                    if (line.y + line.lineHeight > offsetY + innerHeight)
                        offsetY = (int) Math.ceil(line.y + line.lineHeight - innerHeight);
                    // 视口小于本行时至少显示光标上端，而非把整条光标推到视口外。
                    if (line.y < offsetY) offsetY = line.y;
                    offsetX = clamp(offsetX, maximumX);
                    offsetY = clamp(offsetY, maximumY);
                }
            }
            if (changed || presented == null || offsetX != paintedX || offsetY != paintedY)
                presented = SceneTextAreaGeometry.translate(full, offsetX, offsetY);
            source = full;
            width = innerWidth;
            height = innerHeight;
            paintedX = offsetX;
            paintedY = offsetY;
            presentation = next;
            reveal = false;
            affinity = lineIndex;
            affinityPosition = position;
            return presented;
        }

        @Override
        public boolean capturesPointer() {
            return true;
        }

        @Override
        public boolean scrollableContent() {
            return true;
        }

        @Override
        public SceneEditState compositionState() {
            return state;
        }

        @Override
        public boolean acceptsTextInput(SceneNode node) {
            return !readOnly.get();
        }

        @Override
        public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (source != null) {
                int step =
                        source.lines().isEmpty()
                                ? 0
                                : (int) Math.ceil(source.lines().get(0).lineHeight);
                offsetY =
                        (int)
                                Math.max(
                                        0L,
                                        Math.min(
                                                maximumY(source, height),
                                                (long) offsetY - (long) event.delta * step));
            }
            // 端点也消费，避免查看字段时意外滚动外页；核心 dispatch 负责几何失效。
            return true;
        }

        @Override
        public boolean edit(SceneNode node, SceneEditEvent event) {
            Snapshot before = state.snapshot().get();
            if (commands.input(before, event, this::resetNavigation)) return true;
            if (event.command == null) return false;
            boolean locked = readOnly.get();
            if (commands.command(before, event, this::resetNavigation, node.textPolicy().language)) return true;
            switch (event.command) {
                case UP:
                    vertical(before, -1, event.shift);
                    break;
                case DOWN:
                    vertical(before, 1, event.shift);
                    break;
                case HOME:
                    visualEnd(before, false, event.shift);
                    break;
                case END:
                    visualEnd(before, true, event.shift);
                    break;
                case DOCUMENT_HOME:
                    resetNavigation();
                    state.home(event.shift);
                    requestReveal();
                    break;
                case DOCUMENT_END:
                    resetNavigation();
                    state.end(event.shift);
                    requestReveal();
                    break;
                case LEFT:
                    resetNavigation();
                    state.move(-1, event.shift);
                    requestReveal();
                    break;
                case RIGHT:
                    resetNavigation();
                    state.move(1, event.shift);
                    requestReveal();
                    break;
                case SELECT_ALL:
                    resetNavigation();
                    state.select(0, before.text.length());
                    requestReveal();
                    break;
                case SPACE:
                    if (!locked) {
                        resetNavigation();
                        state.replaceSelection(" ", true);
                        inputChanged(before);
                    }
                    break;
                case CONFIRM:
                    if (!locked && !before.composing) {
                        resetNavigation();
                        state.replaceSelection("\n", false);
                        inputChanged(before);
                    }
                    break;
                case SUBMIT:
                    if (!locked && !before.composing) {
                        resetNavigation();
                        commands.submit();
                    }
                    break;
                case ACTIVATE_ONLY:
                    break;
                case TAB:
                    if (locked || event.shift || tabMode.get() != TabMode.INSERT_TAB) return false;
                    resetNavigation();
                    // 原子编辑在前后结束输入组，制表符及随后普通输入各保留自己的撤销步骤。
                    state.replaceSelection("\t", false);
                    inputChanged(before);
                    break;
                case ESCAPE:
                    if (!before.composing) return false;
                    resetNavigation();
                    state.composition("", false);
                    break;
                default:
                    return false;
            }
            return true;
        }

        private void vertical(Snapshot before, int direction, boolean extend) {
            if (editing == null) return;
            int position = toDisplay(before, before.caret);
            int line = editing.lineAt(position, affinityPosition == position ? affinity : -1);
            if (line < 0) return;
            if (Float.isNaN(goalX)) goalX = editing.caretX(line, position, boundary);
            int target = Math.max(0, Math.min(editing.text.lines().size() - 1, line + direction));
            selectVisual(before, target, editing.nearest(target, goalX), extend);
        }

        private void visualEnd(Snapshot before, boolean right, boolean extend) {
            goalX = Float.NaN;
            if (editing == null) return;
            int position = toDisplay(before, before.caret);
            int line = editing.lineAt(position, affinityPosition == position ? affinity : -1);
            if (line >= 0) selectVisual(before, line,
                    editing.visualEnd(line, right), extend);
        }

        private void selectVisual(Snapshot before, int line, SceneCaretBoundary hit, boolean extend) {
            int position = fromDisplay(before, hit.position);
            state.select(extend ? before.anchor : position, position);
            affinity = line;
            boundary = hit;
            affinityPosition = toDisplay(state.snapshot().get(), state.snapshot().get().caret);
            requestReveal();
        }

        private void resetNavigation() {
            goalX = Float.NaN;
            affinity = -1;
            affinityPosition = -1;
            boundary = null;
        }

        private void requestReveal() {
            reveal = true;
            // 软边界可只改视觉行而不改 UTF-16 位置；同样经挂载依赖通知重呈现。
            viewChanged.set(!viewChanged.get());
        }

        private void inputChanged(Snapshot before) {
            commands.inputChanged(before);
        }

        @Override
        public void focusChanged(SceneNode node, boolean focused, boolean eligible) {
            if (focused) return;
            goalX = Float.NaN;
            commands.blur(eligible);
        }

        @Override
        public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.kind != ScenePointerEvent.Kind.DOWN
                    && event.kind != ScenePointerEvent.Kind.MOVE
                    && event.kind != ScenePointerEvent.Kind.UP) return;
            if (event.kind != ScenePointerEvent.Kind.MOVE && event.button != 0) return;
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text != presented || presentation == null || editing == null)
                return;
            LayoutBox inner = inner(entry.box, node.padding());
            int line = editing.lineAtY(event.y - inner.y + offsetY);
            if (line < 0) return;
            resetNavigation();
            SceneCaretBoundary hit = editing.nearest(line, event.x - inner.x + offsetX);
            int position = fromDisplay(presentation, hit.position);
            Snapshot current = state.snapshot().get();
            state.select(
                    event.kind == ScenePointerEvent.Kind.DOWN ? position : current.anchor,
                    position);
            affinity = line;
            boundary = hit;
            affinityPosition = toDisplay(state.snapshot().get(), state.snapshot().get().caret);
            requestReveal();
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text == null) return;
            SceneTheme colors = theme.get();
            boolean enabled = node.runtime() == null || node.runtime().interactive(node);
            boolean focused =
                    enabled
                            && node.runtime() != null
                            && node.runtime().focusRouter().focused().get() == node;
            if(framed) {
                target.add(PaintCommand.rect(entry.box,entry.clip,enabled?colors.panel:colors.disabled));
                border(target,entry.box,entry.clip,focused?colors.focus:colors.button);
            }
            LayoutBox inner = inner(entry.box, node.padding());
            LayoutBox clip = entry.clip == null ? inner : entry.clip.intersect(inner);
            Snapshot value = presentation;
            if (value != null && entry.text == presented && focused && !value.composing)
                range(
                        target,
                        inner,
                        clip,
                        Math.min(value.anchor, value.caret),
                        Math.max(value.anchor, value.caret),
                        colors.hover,
                        false);
            TextLayout paint =
                    enabled
                            ? entry.text
                            : entry.text.withStyle(entry.text.style.withColor(colors.disabledText));
            target.add(PaintCommand.text(inner, clip, paint));
            if (value != null && entry.text == presented && focused && editing != null) {
                if (value.composing) {
                    int start = Math.min(value.anchor, value.caret);
                    range(
                            target,
                            inner,
                            clip,
                            start,
                            start + value.composition.length(),
                            colors.focus,
                            true);
                }
                LayoutBox caret = caretBox(entry);
                if (caret != null) target.add(PaintCommand.rect(caret, clip, colors.focus));
            }
        }

        @Override
        public boolean prepareNavigation(SceneNode node, SceneGeometryView geometry) {
            SceneGeometryView.Entry entry = geometry.get(node);
            LayoutBox caret = caretBox(entry);
            if (caret == null) return false;
            LayoutBox visible = caret.intersect(inner(entry.box, node.padding()));
            if (visible.width == caret.width && visible.height == caret.height) return false;
            reveal = true;
            return true;
        }

        @Override
        public LayoutBox navigationRegion(SceneNode node, SceneGeometryView geometry) {
            SceneGeometryView.Entry entry = geometry.get(node);
            LayoutBox caret = caretBox(entry);
            return caret == null ? null : caret.intersect(inner(entry.box, node.padding()));
        }

        /** 同一行、视觉亲和性、组字和内部滚动事实供绘制与Tab显露共同消费。 */
        private LayoutBox caretBox(SceneGeometryView.Entry entry) {
            if (entry == null || presentation == null || entry.text != presented || editing == null)
                return null;
            int position = toDisplay(presentation, presentation.caret);
            int index = editing.lineAt(position, affinity);
            if (index < 0) return null;
            LayoutBox inner = inner(entry.box, entry.node.padding());
            TextLayout.Line line = presented.lines().get(index);
            return new LayoutBox(
                    inner.x + (int) Math.floor(editing.caretX(index, position, boundary) - offsetX),
                    inner.y + line.y,
                    1,
                    Math.max(0, (int) Math.ceil(line.lineHeight)));
        }

        private void range(
                PaintPlanBuilder target,
                LayoutBox inner,
                LayoutBox clip,
                int start,
                int end,
                int color,
                boolean underline) {
            if (start == end) return;
            for (int i = 0; i < presented.lines().size(); i++) {
                TextLayout.Line line = presented.lines().get(i);
                int top = underline ? (int) Math.floor(line.baseline + line.descent) : line.y;
                int height = underline ? 1 : (int) Math.ceil(line.lineHeight);
                for (TextLayout.Cluster cluster : line.clusters()) {
                    if (cluster.sourceEnd <= start || cluster.sourceStart >= end) continue;
                    int left = (int) Math.floor(cluster.x),
                            right = (int) Math.ceil(cluster.x + cluster.advance);
                    target.add(
                            PaintCommand.rect(
                                    new LayoutBox(
                                            inner.x + left,
                                            inner.y + top,
                                            Math.max(0, right - left),
                                            Math.max(0, height)),
                                    clip,
                                    color));
                }
                // LF 没有 glyph/cluster；被选择时绘制前行到视口视觉边的换行区域。
                if (line.sourceEnd >= start
                        && line.sourceEnd < end
                        && line.sourceEnd < source.originalText.length()
                        && (source.originalText.charAt(line.sourceEnd) == '\n'
                                || source.originalText.charAt(line.sourceEnd) == 0x2028
                                || source.originalText.charAt(line.sourceEnd) == 0x2029)) {
                    int x = (int) Math.floor(editing.caretX(i, line.sourceEnd) - offsetX);
                    int left = editing.paragraphRtl(i) ? 0 : x;
                    int right = editing.paragraphRtl(i) ? x : Math.max(x + 1, inner.width);
                    target.add(
                            PaintCommand.rect(
                                    new LayoutBox(
                                            inner.x + left,
                                            inner.y + top,
                                            Math.max(1, right - left),
                                            Math.max(0, height)),
                                    clip,
                                    color));
                }
            }
        }
    }

    private static int contentHeight(TextLayout text) {
        float height = text.height;
        for (TextLayout.Line line : text.lines())
            height = Math.max(height, SceneTextPresentation.bottom(line));
        return (int) Math.ceil(height);
    }

    private static int maximumX(TextLayout text, int width) {
        return Math.max(0, text.width - Math.max(0, width - 1));
    }

    private static int maximumY(TextLayout text, int height) {
        return Math.max(0, contentHeight(text) - Math.max(1, height));
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(value, maximum));
    }




}
