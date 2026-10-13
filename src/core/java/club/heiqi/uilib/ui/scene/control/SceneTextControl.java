package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.inner;
import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.border;

import java.util.Collections;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 标签与按钮共用文本测量/呈现；缓存只持不可变排版，不持节点或旧挂载作用域。 */
final class SceneTextControl implements SceneControl {
    private final ReadableSignal<SceneTheme> theme;
    private final SceneTextPolicy policy;
    private final boolean button;
    private TextLayout source, presented, tooltipSource, tooltipLayout;
    private int presentWidth, presentHeight, tooltipWidth;

    private SceneTextControl(
            ReadableSignal<SceneTheme> theme, SceneTextPolicy policy, boolean button) {
        this.theme = theme;
        this.policy = policy;
        this.button = button;
    }

    static SceneNode create(
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy,
            boolean button) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(policy, "policy");
        SceneNode node =
                new SceneNode()
                        .control(new SceneTextControl(theme, policy, button))
                        .textPolicy(
                                policy == SceneTextPolicy.WRAP
                                        ? TextPolicy.NORMAL
                                        : TextPolicy.NO_WRAP);
        node.bind(NodeBinding.value(text, SceneNode::text));
        node.bind(
                NodeBinding.value(
                        theme,
                        (target, value) -> {
                            target.style(value.textStyle);
                            if (button) target.padding(value.padding);
                        }));
        return node;
    }

    @Override
    public MeasuredSize measure(
            SceneNode node, Constraints constraints, SceneTextMeasurer measurer) {
        TextLayout full =
                measurer.layout(
                        node.text(), node.style(), constraints.availableWidth, node.textPolicy());
        float width = 0, height = 0;
        for (TextLayout.Line line : full.lines()) {
            width = Math.max(width, SceneTextPresentation.right(line));
            height = Math.max(height, SceneTextPresentation.bottom(line));
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE) break;
        }
        return new MeasuredSize((int) Math.ceil(width), (int) Math.ceil(height));
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer childMeasurer) {
        return new ChildPlacements(Collections.emptyList());
    }

    @Override
    public TextLayout presentText(
            SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer measurer) {
        if (source != full || presentWidth != width || presentHeight != height) {
            source = full;
            presentWidth = width;
            presentHeight = height;
            presented = SceneTextPresentation.fit(full, width, height, policy, measurer);
        }
        return presented;
    }

    @Override
    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        SceneTheme style = theme.get();
        SceneRuntime runtime = node.runtime();
        boolean enabled = true;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
            enabled &= cursor.enabled();
        boolean focused =
                enabled && runtime != null && runtime.focusRouter().focused().get() == node;
        if (button) {
            boolean hovered =
                    enabled && runtime != null && runtime.pointerRouter().hovered(geometry) == node;
            boolean pressed = enabled && runtime != null && runtime.pointerRouter().pressed(node);
            int color =
                    !enabled
                            ? style.disabled
                            : pressed ? style.pressed : hovered ? style.hover : style.button;
            target.add(PaintCommand.rect(entry.box, entry.clip, color));
            if (focused) border(target, entry.box, entry.clip, style.focus);
        }
        LayoutBox inner = inner(entry.box, node.padding());
        LayoutBox clip =
                policy == SceneTextPolicy.OVERFLOW
                        ? entry.clip
                        : entry.clip == null ? inner : entry.clip.intersect(inner);
        TextLayout paint = entry.text;
        if (!enabled) paint = paint.withStyle(paint.style.withColor(style.disabledText));
        target.add(PaintCommand.text(inner, clip, paint));
    }

    @Override
    public void paintOverlay(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            SceneTextMeasurer measurer) {
        // 即时被动提示：只在正文省略时显示，不注册输入、不改变焦点或正文布局。
        SceneRuntime runtime = node.runtime();
        SceneGeometryView.Entry entry = geometry.get(node);
        if (runtime == null
                || entry == null
                || !runtime.interactive(node)
                || source == null
                || presented == source
                || entry.text != presented) return;
        if (runtime.pointerRouter().hovered(geometry) != node
                && runtime.focusRouter().focused().get() != node) return;
        LayoutBox viewport = geometry.viewport();
        if (viewport.width == 0 || viewport.height == 0) return;
        LayoutBox visible = entry.box.intersect(viewport);
        if (entry.clip != null) visible = visible.intersect(entry.clip);
        if (visible.width == 0 || visible.height == 0) return;
        SceneTheme style = theme.get();
        int padding = Math.min(style.padding, Math.min(viewport.width, viewport.height) / 2);
        int width = Math.max(0, viewport.width - padding * 2);
        if (tooltipSource != source || tooltipWidth != width) {
            tooltipSource = source;
            tooltipWidth = width;
            String fullText =
                    source.originalText == null ? source.logicalText : source.originalText;
            tooltipLayout =
                    measurer.layout(
                            fullText,
                            source.style,
                            width,
                            new TextPolicy(
                                    TextPolicy.Direction.AUTO,
                                    TextPolicy.Wrap.EMERGENCY,
                                    "und",
                                    0,
                                    0));
        }
        float textWidth = 0, textHeight = 0;
        for (TextLayout.Line line : tooltipLayout.lines()) {
            textWidth = Math.max(textWidth, SceneTextPresentation.right(line));
            textHeight = Math.max(textHeight, SceneTextPresentation.bottom(line));
        }
        int w = (int) Math.min(viewport.width, Math.ceil(textWidth) + padding * 2L);
        int h = (int) Math.min(viewport.height, Math.ceil(textHeight) + padding * 2L);
        int x = clamp(entry.box.x, viewport.x, viewport.x + viewport.width - w);
        long below = (long) entry.box.y + entry.box.height + style.gap;
        long desired =
                below + h <= (long) viewport.y + viewport.height
                        ? below
                        : (long) entry.box.y - style.gap - h;
        int y =
                (int)
                        Math.max(
                                viewport.y,
                                Math.min((long) viewport.y + viewport.height - h, desired));
        LayoutBox box = new LayoutBox(x, y, w, h);
        target.add(PaintCommand.rect(box, viewport, style.tooltip));
        LayoutBox inner = inner(box, padding);
        // 完整源文保留，极长内容只裁剪到viewport；不声称任意文案都能放入单屏。
        target.add(
                PaintCommand.text(
                        inner,
                        inner.intersect(viewport),
                        tooltipLayout.withStyle(tooltipLayout.style.withColor(style.tooltipText))));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }


}
