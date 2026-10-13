package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
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

import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.inner;
import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.border;
import static club.heiqi.uilib.ui.scene.control.SceneEditCommands.display;
import static club.heiqi.uilib.ui.scene.control.SceneEditCommands.toDisplay;
import static club.heiqi.uilib.ui.scene.control.SceneEditCommands.fromDisplay;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 正式单行字段；草稿、历史和提交归业务持有的独立状态，卸载只释放呈现绑定。 编辑边界见
 * specs/2026-10-07-uilib-editing-spec.md。
 */
public final class SceneTextField {
    public enum SubmitPolicy {
        CONFIRM,
        INPUT,
        BLUR
    }

    private SceneTextField() {}

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
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(state, "state");
        if (state.mode() != SceneEditState.Mode.SINGLE_LINE)
            throw new IllegalArgumentException("单行字段需要单行编辑状态");
        if (context.domain() != state.domain()) throw new IllegalArgumentException("字段与编辑状态必须同域");
        Control control =
                new Control(
                        state,
                        Objects.requireNonNull(theme, "theme"),
                        Objects.requireNonNull(readOnly, "readOnly"),
                        Objects.requireNonNull(onSubmit, "onSubmit"),
                        Objects.requireNonNull(policy, "policy"));
        SceneNode node =
                new SceneNode()
                        .control(control)
                        .textPolicy(TextPolicy.SINGLE_LINE)
                        .size(Size.fill(1), Size.natural())
                        .focusable(true)
                        .clickFocus(true)
                        .onClick(() -> {});
        // snapshot 同时通知光标、组字与文本代际；绑定仅在正常挂载刷新中改节点。
        node.bind(
                NodeBinding.value(
                        state.snapshot(), (target, value) -> target.text(display(value))));
        node.bind(
                NodeBinding.value(
                        theme,
                        (target, value) -> target.style(value.textStyle).padding(value.padding)));
        node.bind(
                NodeBinding.value(
                        readOnly,
                        (target, locked) -> {
                            if (locked) target.runtime().invalidateComposition(target);
                        }));
        return node;
    }

    private static final class Control implements SceneControl, SceneCompositionTarget {
        private final SceneEditState state;
        private final ReadableSignal<SceneTheme> theme;
        private final ReadableSignal<Boolean> readOnly;
        private final SceneEditCommands commands;
        // 以下仅为呈现缓存，不是草稿、输入所有权或另一份编辑状态。
        private TextLayout source, presented;
        private Snapshot presentation;
        private int width = -1;
        private int offset;
        private boolean reveal;
        private SceneCaretBoundary boundary;
        private final Signal<Boolean> viewChanged;

        private Control(
                SceneEditState state,
                ReadableSignal<SceneTheme> theme,
                ReadableSignal<Boolean> readOnly,
                Consumer<Submission> onSubmit,
                SubmitPolicy policy) {
            this.state = state;
            this.theme = theme;
            this.readOnly = readOnly;
            this.commands = new SceneEditCommands(state, readOnly, onSubmit, policy);
            viewChanged = state.domain().signal(false);
        }

        @Override
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Collections.singletonList(theme);
        }

        @Override
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
            return java.util.Arrays.asList(state.snapshot(), viewChanged);
        }

        @Override
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) {
            return Collections.singletonList(readOnly);
        }

        private static TextPolicy bodyPolicy(SceneNode node) {
            TextPolicy p = node.textPolicy();
            return p.wrap == TextPolicy.Wrap.NONE && p.hardBreak == TextPolicy.HardBreak.MARKER
                    ? p
                    : new TextPolicy(
                            p.direction,
                            TextPolicy.Wrap.NONE,
                            p.language,
                            p.tabSize,
                            p.lineHeight,
                            TextPolicy.HardBreak.MARKER);
        }

        @Override
        public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
            return text.layout(node.text(), node.style(), width, bodyPolicy(node));
        }

        @Override
        public MeasuredSize measure(
                SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            TextLayout full =
                    text.layout(
                            node.text(),
                            node.style(),
                            constraints.availableWidth,
                            bodyPolicy(node));
            float height = full.height;
            for (TextLayout.Line line : full.lines())
                height = Math.max(height, SceneTextPresentation.bottom(line));
            // 空文仍取真实字体的自然行高；M 只测度量，不进入字段的文字或命中计划。
            if (height <= 0)
                height = text.layout("M", node.style(), Integer.MAX_VALUE, bodyPolicy(node)).height;
            return new MeasuredSize(full.width, (int) Math.ceil(height));
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            return new ChildPlacements(Collections.emptyList());
        }

        @Override
        public TextLayout presentText(
                SceneNode node,
                TextLayout full,
                int innerWidth,
                int innerHeight,
                SceneTextMeasurer text) {
            Snapshot next = state.snapshot().get();
            boolean changed = source != full;
            int position = toDisplay(next, next.caret);
            if (boundary != null && (boundary.position != position
                    || !SceneCaretBoundary.sameProjection(presentation, next)
                    || boundary.lineAt(full) < 0)) boundary = null;
            int maximum = Math.max(0, full.width - Math.max(0, innerWidth - 1));
            int nextOffset = Math.min(offset, maximum);
            // 光标/草稿/组字或真实排版区域变化各消费一次显露意图，不在稳定帧追滚动。
            if (reveal
                    || changed
                    || width != innerWidth
                    || presentation == null
                    || presentation.caret != next.caret
                    || presentation.revision != next.revision
                    || presentation.composing != next.composing
                    || !presentation.composition.equals(next.composition)
                    || next.composing && presentation.anchor != next.anchor) {
                float x = caretX(full, toDisplay(next, next.caret));
                if (x < nextOffset) nextOffset = (int) Math.floor(x);
                if (x > nextOffset + Math.max(0, innerWidth - 1))
                    nextOffset = (int) Math.ceil(x - Math.max(0, innerWidth - 1));
                nextOffset = Math.max(0, Math.min(nextOffset, maximum));
            }
            if (changed || offset != nextOffset || width != innerWidth)
                presented = translate(full, nextOffset);
            source = full;
            width = innerWidth;
            offset = nextOffset;
            presentation = next;
            reveal = false;
            return presented;
        }

        @Override
        public boolean capturesPointer() {
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
        public boolean edit(SceneNode node, SceneEditEvent event) {
            Snapshot before = state.snapshot().get();
            boolean locked = readOnly.get();
            if (event.compositionEvent) {
                if (!locked || !event.composing) state.composition(event.text, event.composing);
                return true;
            }
            if (event.text != null) {
                if (!locked) {
                    if (before.composing) state.composition("", false);
                    state.replaceSelection(event.text, !event.atomic && !before.composing);
                    inputChanged(before);
                }
                return true;
            }
            if (event.command == null) return false;
            // 真实组字期间方向/删除/空格等候选键归IME；不同时修改已确认草稿或提交字段。
            if (before.composing && !locked && event.command != SceneEditEvent.Command.ESCAPE)
                return true;
            if (commands.command(before, event, this::resetBoundary, node.textPolicy().language)) return true;
            switch (event.command) {
                case LEFT:
                    resetBoundary();
                    state.move(-1, event.shift);
                    break;
                case RIGHT:
                    resetBoundary();
                    state.move(1, event.shift);
                    break;
                case DOCUMENT_HOME:
                case HOME:
                    resetBoundary();
                    state.home(event.shift);
                    break;
                case DOCUMENT_END:
                case END:
                    resetBoundary();
                    state.end(event.shift);
                    break;
                case SELECT_ALL:
                    resetBoundary();
                    state.select(0, before.text.length());
                    break;
                case SPACE:
                    if (!locked) {
                        if (before.composing) state.composition("", false);
                        state.replaceSelection(" ", !before.composing);
                        inputChanged(before);
                    }
                    break;
                case SUBMIT:
                case CONFIRM:
                    if (!locked && !before.composing) {
                        commands.submit();
                    }
                    break;
                case ACTIVATE_ONLY:
                    break;
                case TAB:
                    return !locked && before.composing;
                case ESCAPE:
                    if (!before.composing) return false;
                    state.composition("", false);
                    break;
                default:
                    return false;
            }
            return true;
        }

        private void inputChanged(Snapshot before) {
            commands.inputChanged(before);
        }

        @Override
        public void focusChanged(SceneNode node, boolean focused, boolean eligible) {
            if (focused) return;
            commands.blur(eligible);
        }

        @Override
        public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.kind != ScenePointerEvent.Kind.DOWN
                    && event.kind != ScenePointerEvent.Kind.MOVE
                    && event.kind != ScenePointerEvent.Kind.UP) return;
            if (event.kind != ScenePointerEvent.Kind.MOVE && event.button != 0) return;
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text != presented || presentation == null) return;
            LayoutBox inner = inner(entry.box, node.padding());
            SceneCaretBoundary hit = entry.text.lines().isEmpty() ? null
                    : SceneCaretBoundary.nearest(entry.text.lines().get(0), event.x - inner.x);
            if (hit == null) return;
            int position = fromDisplay(presentation, hit.position);
            Snapshot current = state.snapshot().get();
            position = Math.max(0, Math.min(current.text.length(), position));
            // DOWN 起选，MOVE/UP 只沿父 owner 延伸；松开不会重选或再次请求焦点。
            state.select(
                    event.kind == ScenePointerEvent.Kind.DOWN ? position : current.anchor,
                    position);
            boundary = hit;
            requestReveal();
        }

        private void resetBoundary() {
            boundary = null;
            requestReveal();
        }

        private void requestReveal() {
            reveal = true;
            // 同UTF-16位置换视觉边也需要刷新，不依赖select是否发布新值。
            viewChanged.set(!viewChanged.get());
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
            target.add(
                    PaintCommand.rect(
                            entry.box, entry.clip, enabled ? colors.panel : colors.disabled));
            border(target, entry.box, entry.clip, focused ? colors.focus : colors.button);
            LayoutBox inner = inner(entry.box, node.padding());
            LayoutBox clip = entry.clip == null ? inner : entry.clip.intersect(inner);
            Snapshot value = presentation;
            if (value != null && entry.text == presented && focused && !value.composing)
                range(
                        target,
                        entry.text,
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
            if (value != null && entry.text == presented && focused) {
                if (value.composing) {
                    int start = Math.min(value.anchor, value.caret);
                    range(
                            target,
                            entry.text,
                            inner,
                            clip,
                            start,
                            start + value.composition.length(),
                            colors.focus,
                            true);
                }
                target.add(PaintCommand.rect(caretBox(entry), clip, colors.focus));
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

        /** 绘制与导航只消费同一个当前呈现事实，保留组字位置和内部水平偏移。 */
        private LayoutBox caretBox(SceneGeometryView.Entry entry) {
            if (entry == null || presentation == null || entry.text != presented) return null;
            LayoutBox inner = inner(entry.box, entry.node.padding());
            TextLayout.Line line = entry.text.lines().isEmpty() ? null : entry.text.lines().get(0);
            return new LayoutBox(
                    inner.x + (int) Math.floor(caretX(entry.text, toDisplay(presentation, presentation.caret))),
                    inner.y + (line == null ? 0 : line.y),
                    1,
                    Math.max(0, line == null ? inner.height : (int) Math.ceil(line.lineHeight)));
        }

        private void range(
                PaintPlanBuilder target,
                TextLayout text,
                LayoutBox inner,
                LayoutBox clip,
                int start,
                int end,
                int color,
                boolean underline) {
            if (start == end) return;
            for (TextLayout.Line line : text.lines())
                for (TextLayout.Cluster cluster : line.clusters()) {
                    if (cluster.sourceEnd <= start || cluster.sourceStart >= end) continue;
                    int left = (int) Math.floor(cluster.x),
                            right = (int) Math.ceil(cluster.x + cluster.advance);
                    int top = underline ? (int) Math.floor(line.baseline + line.descent) : line.y;
                    int height = underline ? 1 : (int) Math.ceil(line.lineHeight);
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
        }

        private float caretX(TextLayout text, int position) {
            return text.lines().isEmpty() ? 0
                    : SceneCaretBoundary.caretX(text.lines().get(0), position, boundary);
        }

    }





    /** 纯位置投影，HB glyph 与 source 映射原样沿用，绘制和命中读取同一几何 text。 */
    private static TextLayout translate(TextLayout full, int offset) {
        return SceneTextPresentation.translate(full, -offset, 0);
    }
}
