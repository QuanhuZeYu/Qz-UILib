package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import com.ibm.icu.text.Bidi;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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

/** 正式单行字段；草稿、历史和提交归业务持有的独立状态，卸载只释放呈现绑定。
 * 编辑边界见 docs/superpowers/specs/2026-10-07-uilib-editing-spec.md。
 */
public final class SceneTextField {
    public enum SubmitPolicy { CONFIRM, INPUT, BLUR }

    private SceneTextField() {}

    public static SceneNode create(ScenePageContext context, SceneEditState state,
            ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit) {
        return create(context, state, theme, readOnly, onSubmit, SubmitPolicy.CONFIRM);
    }

    public static SceneNode create(ScenePageContext context, SceneEditState state,
            ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit, SubmitPolicy policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(state, "state");
        if (context.domain() != state.domain()) throw new IllegalArgumentException("字段与编辑状态必须同域");
        Control control = new Control(state, Objects.requireNonNull(theme, "theme"),
            Objects.requireNonNull(readOnly, "readOnly"), Objects.requireNonNull(onSubmit, "onSubmit"),
            Objects.requireNonNull(policy, "policy"));
        SceneNode node = new SceneNode().control(control).textPolicy(TextPolicy.SINGLE_LINE)
            .size(Size.fill(1), Size.natural()).focusable(true).clickFocus(true).onClick(() -> {});
        // snapshot 同时通知光标、组字与文本代际；绑定仅在正常挂载刷新中改节点。
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
        // 以下仅为呈现缓存，不是草稿、输入所有权或另一份编辑状态。
        private TextLayout source, presented;
        private Snapshot presentation;
        private Bidi direction;
        private int width = -1;
        private int offset;

        private Control(SceneEditState state, ReadableSignal<SceneTheme> theme,
                ReadableSignal<Boolean> readOnly, Consumer<Submission> onSubmit, SubmitPolicy policy) {
            this.state = state; this.theme = theme; this.readOnly = readOnly;
            this.onSubmit = onSubmit; this.policy = policy;
        }

        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Arrays.asList(state.snapshot(), theme, readOnly);
        }

        @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            TextLayout full = text.layout(node.text(), node.style(), constraints.availableWidth, TextPolicy.SINGLE_LINE);
            float height = full.height;
            for (TextLayout.Line line : full.lines()) height = Math.max(height, SceneTextPresentation.bottom(line));
            // 空文仍取真实字体的自然行高；M 只测度量，不进入字段的文字或命中计划。
            if (height <= 0) height = text.layout("M", node.style(), Integer.MAX_VALUE, TextPolicy.SINGLE_LINE).height;
            return new MeasuredSize(full.width, (int)Math.ceil(height));
        }

        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            return new ChildPlacements(Collections.emptyList());
        }

        @Override public TextLayout presentText(SceneNode node, TextLayout full, int innerWidth, int innerHeight,
                SceneTextMeasurer text) {
            Snapshot next = state.snapshot().get();
            boolean changed = source != full;
            if (changed) {
                // 与生产整形相同的 ICU 方向级别；位置始终取其 clusters，不重新 HB 排版。
                direction = new Bidi();
                direction.setPara(full.logicalText, Bidi.LEVEL_DEFAULT_LTR, null);
            }
            int maximum = Math.max(0, full.width - Math.max(0, innerWidth - 1));
            int nextOffset = Math.min(offset, maximum);
            // 光标/草稿/组字或真实排版区域变化各消费一次显露意图，不在稳定帧追滚动。
            if (changed || width != innerWidth || presentation == null || presentation.caret != next.caret || presentation.revision != next.revision
                    || presentation.composing != next.composing || !presentation.composition.equals(next.composition)
                    || next.composing && presentation.anchor != next.anchor) {
                float x = caretX(full, toDisplay(next, next.caret));
                if (x < nextOffset) nextOffset = (int)Math.floor(x);
                if (x > nextOffset + Math.max(0, innerWidth - 1))
                    nextOffset = (int)Math.ceil(x - Math.max(0, innerWidth - 1));
                nextOffset = Math.max(0, Math.min(nextOffset, maximum));
            }
            if (changed || offset != nextOffset || width != innerWidth) presented = translate(full, nextOffset);
            source = full; width = innerWidth; offset = nextOffset; presentation = next;
            return presented;
        }

        @Override public boolean capturesPointer() { return true; }
        @Override public boolean acceptsTextInput(SceneNode node) { return !readOnly.get(); }
        @Override public boolean edit(SceneNode node, SceneEditEvent event) {
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
            if(before.composing && !locked && event.command!=SceneEditEvent.Command.ESCAPE)return true;
            switch (event.command) {
                case LEFT: state.move(-1, event.shift); break;
                case RIGHT: state.move(1, event.shift); break;
                case HOME: state.home(event.shift); break;
                case END: state.end(event.shift); break;
                case SELECT_ALL: state.select(0, before.text.length()); break;
                case COPY: copy(before, event); break;
                case CUT:
                    if (!locked && event.clipboard != null) {
                        copy(before, event); state.replaceSelection("", false); inputChanged(before);
                    }
                    break;
                case PASTE:
                    if (!locked && event.clipboard != null) {
                        state.composition("", false);
                        state.replaceSelection(Objects.requireNonNull(event.clipboard.read(), "clipboard text"), false);
                        inputChanged(before);
                    }
                    break;
                case BACKSPACE: if (!locked) { state.deleteBackward(); inputChanged(before); } break;
                case DELETE: if (!locked) { state.deleteForward(); inputChanged(before); } break;
                case UNDO: if (!locked) { state.undo(); inputChanged(before); } break;
                case REDO: if (!locked) { state.redo(); inputChanged(before); } break;
                case SPACE:
                    if (!locked) {
                        if (before.composing) state.composition("", false);
                        state.replaceSelection(" ", !before.composing); inputChanged(before);
                    }
                    break;
                case CONFIRM:
                    if (!locked && !before.composing) {
                        state.breakHistoryGroup(); onSubmit.accept(state.request(true));
                    }
                    break;
                case ACTIVATE_ONLY: break;
                case TAB: return !locked && before.composing;
                case ESCAPE:
                    if (!before.composing) return false;
                    state.composition("", false);
                    break;
                default: return false;
            }
            return true;
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
            state.breakHistoryGroup(); state.composition("", false);
            // 真实用户离开才可完成提交；禁用、modal、卸载由父路由传 eligible=false。
            if (eligible && policy == SubmitPolicy.BLUR && before.active && !readOnly.get())
                onSubmit.accept(state.request(true));
        }

        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.kind != ScenePointerEvent.Kind.DOWN && event.kind != ScenePointerEvent.Kind.MOVE
                    && event.kind != ScenePointerEvent.Kind.UP) return;
            if (event.kind != ScenePointerEvent.Kind.MOVE && event.button != 0) return;
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.text != presented || presentation == null) return;
            LayoutBox inner = inner(entry.box, node.padding());
            int position = fromDisplay(presentation, nearest(entry.text, event.x - inner.x));
            Snapshot current = state.snapshot().get();
            position = Math.max(0, Math.min(current.text.length(), position));
            // DOWN 起选，MOVE/UP 只沿父 owner 延伸；松开不会重选或再次请求焦点。
            state.select(event.kind == ScenePointerEvent.Kind.DOWN ? position : current.anchor, position);
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
                range(target, entry.text, inner, clip, Math.min(value.anchor, value.caret),
                    Math.max(value.anchor, value.caret), colors.hover, false);
            TextLayout paint = enabled ? entry.text : entry.text.withStyle(entry.text.style.withColor(colors.disabledText));
            target.add(PaintCommand.text(inner, clip, paint));
            if (value != null && entry.text == presented && focused) {
                if (value.composing) {
                    int start = Math.min(value.anchor, value.caret);
                    range(target, entry.text, inner, clip, start, start + value.composition.length(), colors.focus, true);
                }
                TextLayout.Line line = entry.text.lines().isEmpty() ? null : entry.text.lines().get(0);
                int top = line == null ? 0 : line.y;
                int height = line == null ? inner.height : (int)Math.ceil(line.lineHeight);
                target.add(PaintCommand.rect(new LayoutBox(inner.x + (int)Math.floor(caretX(entry.text,
                    toDisplay(value, value.caret))), inner.y + top, 1, Math.max(0, height)), clip, colors.focus));
            }
        }

        private void range(PaintPlanBuilder target, TextLayout text, LayoutBox inner, LayoutBox clip,
                int start, int end, int color, boolean underline) {
            if (start == end) return;
            for (TextLayout.Line line : text.lines()) for (TextLayout.Cluster cluster : line.clusters()) {
                if (cluster.sourceEnd <= start || cluster.sourceStart >= end) continue;
                int left = (int)Math.floor(cluster.x), right = (int)Math.ceil(cluster.x + cluster.advance);
                int top = underline ? (int)Math.floor(line.baseline + line.descent) : line.y;
                int height = underline ? 1 : (int)Math.ceil(line.lineHeight);
                target.add(PaintCommand.rect(new LayoutBox(inner.x + left, inner.y + top,
                    Math.max(0, right - left), Math.max(0, height)), clip, color));
            }
        }

        private boolean rtl(TextLayout.Cluster cluster) {
            return direction != null && cluster.logicalStart < direction.getLength()
                && (direction.getLevelAt(cluster.logicalStart) & 1) != 0;
        }

        private float edge(TextLayout.Cluster cluster, boolean start) {
            return cluster.x + (start == rtl(cluster) ? cluster.advance : 0);
        }

        private float caretX(TextLayout text, int position) {
            float bestX = 0;
            int bestDistance = Integer.MAX_VALUE;
            // 双向边界可能有两个合法位置：固定选首个源起边界，不伪造视觉 affinity 支持。
            for (TextLayout.Line line : text.lines()) for (TextLayout.Cluster cluster : line.clusters()) {
                if (cluster.sourceStart == position) return edge(cluster, true);
                int distance = Math.abs(cluster.sourceStart - position);
                if (distance < bestDistance) { bestDistance = distance; bestX = edge(cluster, true); }
                distance = Math.abs(cluster.sourceEnd - position);
                if (distance < bestDistance) { bestDistance = distance; bestX = edge(cluster, false); }
            }
            return bestX;
        }

        private int nearest(TextLayout text, float x) {
            int position = 0;
            float distance = Float.POSITIVE_INFINITY;
            for (TextLayout.Line line : text.lines()) for (TextLayout.Cluster cluster : line.clusters()) {
                float delta = Math.abs(edge(cluster, true) - x);
                if (delta < distance) { distance = delta; position = cluster.sourceStart; }
                delta = Math.abs(edge(cluster, false) - x);
                if (delta < distance) { distance = delta; position = cluster.sourceEnd; }
            }
            return position;
        }
    }

    private static LayoutBox inner(LayoutBox box, int padding) {
        return new LayoutBox(box.x + padding, box.y + padding,
            Math.max(0, box.width - padding * 2), Math.max(0, box.height - padding * 2));
    }

    private static void border(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if (box.width == 0 || box.height == 0) return;
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y + box.height - 1, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, 1, box.height), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x + box.width - 1, box.y, 1, box.height), clip, color));
    }

    /** 纯位置投影，HB glyph 与 source 映射原样沿用，绘制和命中读取同一几何 text。 */
    private static TextLayout translate(TextLayout full, int offset) {
        if (offset == 0) return full;
        List<TextLayout.Line> lines = new ArrayList<>();
        for (TextLayout.Line line : full.lines()) {
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            for (TextLayout.Glyph g : line.glyphs()) glyphs.add(new TextLayout.Glyph(g.source, g.size,
                g.glyphId, g.logicalStart, g.logicalEnd, g.sourceStart, g.sourceEnd, g.x - offset, g.y,
                g.xAdvance, g.yAdvance, g.color, g.replacement, g.inkBounds, g.logicalEm, g.rasterToLayout,
                g.rasterStyle, g.resolvedStyle, g.clusterIndex));
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            for (TextLayout.Cluster c : line.clusters()) clusters.add(new TextLayout.Cluster(c.logicalStart,
                c.logicalEnd, c.sourceStart, c.sourceEnd, c.glyphIndexStart, c.glyphIndexEnd, c.x - offset, c.advance, c.style));
            List<TextLayout.Decoration> decorations = new ArrayList<>();
            for (TextLayout.Decoration d : line.decorations()) decorations.add(new TextLayout.Decoration(d.kind,
                d.x - offset, d.y, d.width, d.height, d.color, d.logicalStart, d.logicalEnd, d.sourceStart, d.sourceEnd));
            lines.add(new TextLayout.Line(line.text, line.x - offset, line.y, line.logicalStart, line.logicalEnd,
                line.sourceStart, line.sourceEnd, line.advance, line.baseline, line.ascent, line.descent,
                line.lineHeight, glyphs, clusters, decorations));
        }
        return new TextLayout(full.width, full.height, full.advanceWidth, full.advanceHeight, full.style,
            lines, full.logicalText, full.originalText, full.generation, full.diagnostics(), full.input);
    }
}
