package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
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

    public static SceneNode create(ScenePageContext context, SceneEditState state,
            ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> readOnly, Consumer<Submission> onSubmit) {
        return create(context, state, theme, readOnly, onSubmit, SubmitPolicy.CONFIRM);
    }

    public static SceneNode create(ScenePageContext context, SceneEditState state,
            ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit, SubmitPolicy policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(state, "state");
        if (context.domain() != state.domain()) throw new IllegalArgumentException("字段与编辑状态必须同域");
        if (state.mode() != SceneEditState.Mode.MULTI_LINE) throw new IllegalArgumentException("多行字段要求 MULTI_LINE 状态");
        Control control = new Control(state, Objects.requireNonNull(theme, "theme"),
            Objects.requireNonNull(readOnly, "readOnly"), Objects.requireNonNull(onSubmit, "onSubmit"),
            Objects.requireNonNull(policy, "policy"));
        SceneNode node = new SceneNode().control(control).textPolicy(TextPolicy.NORMAL)
            .size(Size.fill(1), Size.natural()).focusable(true).clickFocus(true).onClick(() -> {});
        node.bind(NodeBinding.value(state.snapshot(), (target, value) -> target.text(display(value))));
        node.bind(NodeBinding.value(theme, (target, value) -> target.style(value.textStyle).padding(value.padding)));
        return node;
    }

    private static String display(Snapshot value) {
        if (!value.composing) return value.text;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        return value.text.substring(0, start) + value.composition + value.text.substring(end);
    }

    private static int toDisplay(Snapshot value, int offset) {
        if (!value.composing) return offset;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        return offset < start ? offset : offset <= end ? start + value.composition.length()
            : offset - (end - start) + value.composition.length();
    }

    private static int fromDisplay(Snapshot value, int offset) {
        if (!value.composing) return offset;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        int preeditEnd = start + value.composition.length();
        return offset <= start ? offset : offset < preeditEnd ? start : offset - value.composition.length() + end - start;
    }

    private static final class Control implements SceneControl {
        private final SceneEditState state;
        private final ReadableSignal<SceneTheme> theme;
        private final ReadableSignal<Boolean> readOnly;
        private final Consumer<Submission> onSubmit;
        private final SubmitPolicy policy;
        // 只通知呈现归属变化；不保存草稿、选区、会话或焦点权威。
        private final Signal<Boolean> viewChanged;
        private TextLayout source, presented;
        private SceneTextAreaGeometry editing;
        private TextPolicy layoutPolicy;
        private Snapshot presentation;
        private int width = -1, height = -1, offsetX, offsetY, paintedX = -1, paintedY = -1;
        private int affinity = -1, affinityPosition = -1;
        private float goalX = Float.NaN;
        private boolean reveal;

        Control(SceneEditState state, ReadableSignal<SceneTheme> theme,
                ReadableSignal<Boolean> readOnly, Consumer<Submission> onSubmit, SubmitPolicy policy) {
            this.state = state; this.theme = theme; this.readOnly = readOnly;
            this.onSubmit = onSubmit; this.policy = policy;
            viewChanged = state.domain().signal(false);
        }

        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Arrays.asList(state.snapshot(), theme, readOnly, viewChanged);
        }

        @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            TextLayout full = text.layout(node.text(), node.style(), constraints.availableWidth, node.textPolicy());
            return new MeasuredSize(full.width, contentHeight(full));
        }

        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            return new ChildPlacements(Collections.emptyList());
        }

        @Override public TextLayout presentText(SceneNode node, TextLayout full, int innerWidth, int innerHeight,
                SceneTextMeasurer text) {
            // 框架的 full 已按实际 innerWidth 排版；保留所有行，由 paint clip 截视口。
            Snapshot next = state.snapshot().get();
            boolean changed = source != full, resized = width != innerWidth || height != innerHeight;
            boolean layoutChanged = editing == null || !node.textPolicy().equals(layoutPolicy)
                || changed && !editing.sameGeometry(full);
            if (layoutChanged) { affinity = -1; affinityPosition = -1; goalX = Float.NaN; }
            if (changed || layoutChanged) editing = new SceneTextAreaGeometry(full, node.textPolicy());
            layoutPolicy = node.textPolicy();
            if (resized) goalX = Float.NaN;
            int position = toDisplay(next, next.caret);
            if (position != affinityPosition) { affinity = -1; goalX = Float.NaN; }
            int lineIndex = editing.lineAt(position, affinity);
            int maximumX = maximumX(full, innerWidth), maximumY = maximumY(full, innerHeight);
            offsetX = clamp(offsetX, maximumX); offsetY = clamp(offsetY, maximumY);
            if (reveal || layoutChanged || resized || presentation == null || presentation.caret != next.caret
                    || presentation.revision != next.revision || presentation.composing != next.composing
                    || !presentation.composition.equals(next.composition) || next.composing && presentation.anchor != next.anchor) {
                if (lineIndex >= 0) {
                    TextLayout.Line line = full.lines().get(lineIndex);
                    float x = editing.caretX(lineIndex, position);
                    if (x < offsetX) offsetX = (int)Math.floor(x);
                    if (x > offsetX + Math.max(0, innerWidth - 1)) offsetX = (int)Math.ceil(x - Math.max(0, innerWidth - 1));
                    if (line.y + line.lineHeight > offsetY + innerHeight)
                        offsetY = (int)Math.ceil(line.y + line.lineHeight - innerHeight);
                    // 视口小于本行时至少显示光标上端，而非把整条光标推到视口外。
                    if (line.y < offsetY) offsetY = line.y;
                    offsetX = clamp(offsetX, maximumX); offsetY = clamp(offsetY, maximumY);
                }
            }
            if (changed || presented == null || offsetX != paintedX || offsetY != paintedY)
                presented = SceneTextAreaGeometry.translate(full, offsetX, offsetY);
            source = full; width = innerWidth; height = innerHeight;
            paintedX = offsetX; paintedY = offsetY; presentation = next; reveal = false;
            affinity = lineIndex; affinityPosition = position;
            return presented;
        }

        @Override public boolean capturesPointer() { return true; }
        @Override public boolean scrollableContent() { return true; }
        @Override public boolean acceptsTextInput(SceneNode node) { return !readOnly.get(); }

        @Override public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (source != null) {
                int step = source.lines().isEmpty() ? 0 : (int)Math.ceil(source.lines().get(0).lineHeight);
                offsetY = (int)Math.max(0L, Math.min(maximumY(source, height), (long)offsetY - (long)event.delta * step));
            }
            // 端点也消费，避免查看字段时意外滚动外页；核心 dispatch 负责几何失效。
            return true;
        }

        @Override public boolean edit(SceneNode node, SceneEditEvent event) {
            Snapshot before = state.snapshot().get();
            boolean locked = readOnly.get();
            if (event.compositionEvent) {
                resetNavigation();
                if (!locked || !event.composing) state.composition(event.text, event.composing);
                return true;
            }
            if (event.text != null) {
                resetNavigation();
                if (!locked) {
                    if (before.composing) state.composition("", false);
                    state.replaceSelection(event.text, !event.atomic && !before.composing); inputChanged(before);
                }
                return true;
            }
            if (event.command == null) return false;
            if (before.composing && !locked && event.command != SceneEditEvent.Command.ESCAPE) return true;
            switch (event.command) {
                case UP: vertical(before, -1, event.shift); break;
                case DOWN: vertical(before, 1, event.shift); break;
                case HOME: visualEnd(before, false, event.shift); break;
                case END: visualEnd(before, true, event.shift); break;
                case DOCUMENT_HOME: resetNavigation(); state.home(event.shift); requestReveal(); break;
                case DOCUMENT_END: resetNavigation(); state.end(event.shift); requestReveal(); break;
                case LEFT: resetNavigation(); state.move(-1, event.shift); requestReveal(); break;
                case RIGHT: resetNavigation(); state.move(1, event.shift); requestReveal(); break;
                case SELECT_ALL: resetNavigation(); state.select(0, before.text.length()); requestReveal(); break;
                case COPY: copy(before, event); break;
                case CUT:
                    if (!locked && event.clipboard != null) {
                        resetNavigation(); copy(before, event); state.replaceSelection("", false); inputChanged(before);
                    }
                    break;
                case PASTE:
                    if (!locked && event.clipboard != null) {
                        resetNavigation(); state.composition("", false);
                        state.replaceSelection(Objects.requireNonNull(event.clipboard.read(), "clipboard text"), false); inputChanged(before);
                    }
                    break;
                case BACKSPACE: if (!locked) { resetNavigation(); state.deleteBackward(); inputChanged(before); } break;
                case DELETE: if (!locked) { resetNavigation(); state.deleteForward(); inputChanged(before); } break;
                case UNDO: if (!locked) { resetNavigation(); state.undo(); inputChanged(before); } break;
                case REDO: if (!locked) { resetNavigation(); state.redo(); inputChanged(before); } break;
                case SPACE:
                    if (!locked) { resetNavigation(); state.replaceSelection(" ", true); inputChanged(before); }
                    break;
                case CONFIRM:
                    if (!locked && !before.composing) { resetNavigation(); state.replaceSelection("\n", false); inputChanged(before); }
                    break;
                case SUBMIT:
                    if (!locked && !before.composing) { resetNavigation(); state.breakHistoryGroup(); onSubmit.accept(state.request(true)); }
                    break;
                case ACTIVATE_ONLY: break;
                case TAB: return !locked && before.composing;
                case ESCAPE:
                    if (!before.composing) return false;
                    resetNavigation(); state.composition("", false); break;
                default: return false;
            }
            return true;
        }

        private void vertical(Snapshot before, int direction, boolean extend) {
            if (editing == null) return;
            int position = toDisplay(before, before.caret);
            int line = editing.lineAt(position, affinityPosition == position ? affinity : -1);
            if (line < 0) return;
            if (Float.isNaN(goalX)) goalX = editing.caretX(line, position);
            int target = Math.max(0, Math.min(editing.text.lines().size() - 1, line + direction));
            selectVisual(before, target, editing.nearest(target, goalX), extend);
        }

        private void visualEnd(Snapshot before, boolean right, boolean extend) {
            goalX = Float.NaN;
            if (editing == null) return;
            int position = toDisplay(before, before.caret);
            int line = editing.lineAt(position, affinityPosition == position ? affinity : -1);
            if (line >= 0) selectVisual(before, line, editing.visualEnd(line, right), extend);
        }

        private void selectVisual(Snapshot before, int line, int displayPosition, boolean extend) {
            int position = fromDisplay(before, displayPosition);
            state.select(extend ? before.anchor : position, position);
            affinity = line; affinityPosition = toDisplay(state.snapshot().get(), state.snapshot().get().caret);
            requestReveal();
        }

        private void resetNavigation() { goalX = Float.NaN; affinity = -1; affinityPosition = -1; }
        private void requestReveal() {
            reveal = true;
            // 软边界可只改视觉行而不改 UTF-16 位置；同样经挂载依赖通知重呈现。
            viewChanged.set(!viewChanged.get());
        }
        private void inputChanged(Snapshot before) {
            if (policy == SubmitPolicy.INPUT && !before.text.equals(state.snapshot().get().text))
                onSubmit.accept(state.request(false));
        }
        private static void copy(Snapshot value, SceneEditEvent event) {
            if (event.clipboard != null) event.clipboard.write(value.text.substring(
                Math.min(value.anchor, value.caret), Math.max(value.anchor, value.caret)));
        }

        @Override public void focusChanged(SceneNode node, boolean focused, boolean eligible) {
            if (focused) return;
            Snapshot before = state.snapshot().get();
            resetNavigation(); state.breakHistoryGroup(); state.composition("", false);
            if (eligible && policy == SubmitPolicy.BLUR && before.active && !readOnly.get())
                onSubmit.accept(state.request(true));
        }

        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.kind != ScenePointerEvent.Kind.DOWN && event.kind != ScenePointerEvent.Kind.MOVE
                    && event.kind != ScenePointerEvent.Kind.UP) return;
            if (event.kind != ScenePointerEvent.Kind.MOVE && event.button != 0) return;
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text != presented || presentation == null || editing == null) return;
            LayoutBox inner = inner(entry.box, node.padding());
            int line = editing.lineAtY(event.y - inner.y + offsetY);
            if (line < 0) return;
            resetNavigation();
            int position = fromDisplay(presentation, editing.nearest(line, event.x - inner.x + offsetX));
            Snapshot current = state.snapshot().get();
            state.select(event.kind == ScenePointerEvent.Kind.DOWN ? position : current.anchor, position);
            affinity = line; affinityPosition = toDisplay(state.snapshot().get(), state.snapshot().get().caret);
            requestReveal();
        }

        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text == null) return;
            SceneTheme colors = theme.get();
            boolean enabled = node.runtime() == null || node.runtime().interactive(node);
            boolean focused = enabled && node.runtime() != null && node.runtime().focusRouter().focused().get() == node;
            target.add(PaintCommand.rect(entry.box, entry.clip, enabled ? colors.panel : colors.disabled));
            border(target, entry.box, entry.clip, focused ? colors.focus : colors.button);
            LayoutBox inner = inner(entry.box, node.padding());
            LayoutBox clip = entry.clip == null ? inner : entry.clip.intersect(inner);
            Snapshot value = presentation;
            if (value != null && entry.text == presented && focused && !value.composing)
                range(target, inner, clip, Math.min(value.anchor, value.caret), Math.max(value.anchor, value.caret), colors.hover, false);
            TextLayout paint = enabled ? entry.text : entry.text.withStyle(entry.text.style.withColor(colors.disabledText));
            target.add(PaintCommand.text(inner, clip, paint));
            if (value != null && entry.text == presented && focused && editing != null) {
                if (value.composing) {
                    int start = Math.min(value.anchor, value.caret);
                    range(target, inner, clip, start, start + value.composition.length(), colors.focus, true);
                }
                int position = toDisplay(value, value.caret), index = editing.lineAt(position, affinity);
                if (index >= 0) {
                    TextLayout.Line line = presented.lines().get(index);
                    target.add(PaintCommand.rect(new LayoutBox(inner.x + (int)Math.floor(editing.caretX(index, position) - offsetX),
                        inner.y + line.y, 1, Math.max(0, (int)Math.ceil(line.lineHeight))), clip, colors.focus));
                }
            }
        }

        private void range(PaintPlanBuilder target, LayoutBox inner, LayoutBox clip, int start, int end, int color, boolean underline) {
            if (start == end) return;
            for (int i = 0; i < presented.lines().size(); i++) {
                TextLayout.Line line = presented.lines().get(i);
                int top = underline ? (int)Math.floor(line.baseline + line.descent) : line.y;
                int height = underline ? 1 : (int)Math.ceil(line.lineHeight);
                for (TextLayout.Cluster cluster : line.clusters()) {
                    if (cluster.sourceEnd <= start || cluster.sourceStart >= end) continue;
                    int left = (int)Math.floor(cluster.x), right = (int)Math.ceil(cluster.x + cluster.advance);
                    target.add(PaintCommand.rect(new LayoutBox(inner.x + left, inner.y + top,
                        Math.max(0, right - left), Math.max(0, height)), clip, color));
                }
                // LF 没有 glyph/cluster；被选择时绘制前行到视口视觉边的换行区域。
                if (line.sourceEnd >= start && line.sourceEnd < end && line.sourceEnd < source.originalText.length()
                        && (source.originalText.charAt(line.sourceEnd) == '\n'
                            || source.originalText.charAt(line.sourceEnd) == 0x2028
                            || source.originalText.charAt(line.sourceEnd) == 0x2029)) {
                    int x = (int)Math.floor(editing.caretX(i, line.sourceEnd) - offsetX);
                    int left = editing.paragraphRtl(i) ? 0 : x;
                    int right = editing.paragraphRtl(i) ? x : Math.max(x + 1, inner.width);
                    target.add(PaintCommand.rect(new LayoutBox(inner.x + left, inner.y + top,
                        Math.max(1, right - left), Math.max(0, height)), clip, color));
                }
            }
        }
    }

    private static int contentHeight(TextLayout text) {
        float height = text.height;
        for (TextLayout.Line line : text.lines()) height = Math.max(height, SceneTextPresentation.bottom(line));
        return (int)Math.ceil(height);
    }
    private static int maximumX(TextLayout text, int width) { return Math.max(0, text.width - Math.max(0, width - 1)); }
    private static int maximumY(TextLayout text, int height) { return Math.max(0, contentHeight(text) - Math.max(1, height)); }
    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(value, maximum)); }
    private static LayoutBox inner(LayoutBox box, int padding) {
        return new LayoutBox(box.x + padding, box.y + padding, Math.max(0, box.width - padding * 2), Math.max(0, box.height - padding * 2));
    }
    private static void border(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if (box.width == 0 || box.height == 0) return;
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y + box.height - 1, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, 1, box.height), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x + box.width - 1, box.y, 1, box.height), clip, color));
    }
}
