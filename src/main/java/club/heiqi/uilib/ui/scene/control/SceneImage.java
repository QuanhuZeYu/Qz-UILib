package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import java.util.Collections;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 正式图像控件；来源/结果订阅只在NodeBinding挂载期获取，measure/paint纯读不可变内容。 */
public final class SceneImage implements SceneControl {
    private final ImageService service;
    private final ReadableSignal<ImageSource> source;
    private ImageOptions options = ImageOptions.defaults();
    private ImageState state;
    private final Signal<ImageState> current;
    private final SceneNode placeholder;
    private boolean documentPolicy;
    private SceneTheme theme = SceneTheme.dark();
    private club.heiqi.uilib.ui.scene.text.TextLayout quantityText, tooltipText;
    private club.heiqi.uilib.ui.scene.image.ItemImageFacts tooltipFacts;
    private club.heiqi.uilib.ui.scene.text.TextStyle tooltipStyle;
    private int tooltipWidth = -1;
    private long tooltipMetrics = -1;

    /** 可选主题绑定与其他正式控件一致，字号/提示背景变化自动失效。 */
    public static SceneNode themed(
            ImageService service,
            ReadableSignal<ImageSource> source,
            ReadableSignal<ImageOptions> options,
            ReadableSignal<SceneTheme> theme) {
        SceneNode node = create(service, source, options);
        SceneImage control = (SceneImage) node.control();
        node.bind(
                NodeBinding.value(
                        theme,
                        (target, value) -> {
                            control.theme = value;
                            target.style(value.textStyle);
                        }));
        return node;
    }

    private SceneImage(
            ImageService service, ReadableSignal<ImageSource> source, SceneNode placeholder) {
        this.service = Objects.requireNonNull(service, "service");
        this.source = Objects.requireNonNull(source, "source");
        this.placeholder = placeholder;
        current = ReactiveDomain.current().signal(ImageState.loading(source.get(), 0));
    }

    /** 只读呈现状态；其他正规控件可绑定来源/stage诊断，无业务缓存轮询。 */
    public static ReadableSignal<ImageState> state(SceneNode node) {
        if (!(node.control() instanceof SceneImage)) throw new IllegalArgumentException("不是图像控件");
        return ((SceneImage) node.control()).current;
    }

    public static SceneNode create(
            ImageService service,
            ReadableSignal<ImageSource> source,
            ReadableSignal<ImageOptions> options) {
        return create(service, source, options, null);
    }

    /** 自定义占位是正规逻辑子树；尺寸仍由图像声明/占位意图分配，不要求业务回写布局。 */
    public static SceneNode create(
            ImageService service,
            ReadableSignal<ImageSource> source,
            ReadableSignal<ImageOptions> options,
            SceneNode placeholder) {
        SceneImage control = new SceneImage(service, source, placeholder);
        SceneNode node = new SceneNode().control(control);
        if (placeholder != null) node.add(placeholder);
        node.bind(
                NodeBinding.value(
                        options,
                        (target, value) -> {
                            control.options =
                                    control.documentPolicy
                                            ? Objects.requireNonNull(value, "options").document()
                                            : Objects.requireNonNull(value, "options");
                            target.size(value.width, value.height);
                            control.describe(target);
                        }));
        node.bind(
                (runtime, target, owner) -> {
                    final ImageService.Lease[] lease = {null};
                    final ImageSource[] current = {null};
                    Observation observation =
                            runtime.domain()
                                    .observe(
                                            owner,
                                            () -> {
                                                ImageSource next = source.get();
                                                if (!Objects.equals(next, current[0])) {
                                                    if (lease[0] != null) lease[0].close();
                                                    lease[0] =
                                                            service.acquire(next, runtime.domain());
                                                    current[0] = next;
                                                }
                                                control.state = lease[0].state().get();
                                                control.current.set(control.state);
                                                control.describe(target);
                                                runtime.invalidate();
                                            },
                                            () -> runtime.invalidateBinding(target));
                    return new BindingActivation() {
                        public void refresh() {
                            observation.refresh();
                        }

                        public void close() {
                            observation.close();
                            if (lease[0] != null) {
                                lease[0].close();
                                lease[0] = null;
                            }
                            control.state = null;
                            control.tooltipFacts = null;
                            control.tooltipStyle = null;
                            control.tooltipText = null;
                            control.quantityText = null;
                        }
                    };
                });
        return node;
    }

    /** 后续Markdown解析器使用此入口；当前只实现图像消费者，不解析Markdown全文。 */
    public static SceneNode documentImage(
            ImageService service,
            ReadableSignal<ImageSource> source,
            ReadableSignal<ImageOptions> options) {
        SceneNode node = create(service, source, options);
        ((SceneImage) node.control()).documentPolicy = true;
        return node;
    }

    private void describe(SceneNode node) {
        boolean ready = state != null && state.status == ImageState.Status.READY;
        node.text(options.alt);
        if (placeholder != null) placeholder.visible(!ready);
    }

    @Override
    public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
        boolean ready = state != null && state.content != null;
        int width = ready ? state.content.width : options.placeholderWidth,
                height = ready ? state.content.height : options.placeholderHeight;
        Size w = node.width(), h = node.height();
        // 先求明确单边及比例，随后分别约束边界；未知内容从不借占位比例推导。
        if (ready) {
            if (w.mode != Size.Mode.NATURAL && h.mode == Size.Mode.NATURAL) {
                int specified = w.mode == Size.Mode.FIXED ? w.value : constraints.availableWidth;
                width = specified;
                height = round((double) specified * state.content.height / state.content.width);
            } else if (w.mode == Size.Mode.NATURAL
                    && (h.mode == Size.Mode.FIXED
                            || h.mode == Size.Mode.FILL
                                    && constraints.availableHeight != Integer.MAX_VALUE)) {
                height =
                        h.mode == Size.Mode.FIXED
                                ? h.value
                                : h.constrain(constraints.availableHeight);
                width = round((double) height * state.content.width / state.content.height);
            } else if (options.document
                    && w.mode == Size.Mode.NATURAL
                    && h.mode == Size.Mode.NATURAL) {
                int limit = Math.min(width, constraints.availableWidth);
                height = round((double) height * limit / width);
                width = limit;
            }
        }
        return new MeasuredSize(w.constrain(width), h.constrain(height));
    }

    private static int round(double value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(value)));
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox box) {
        return placeholder == null || !placeholder.visible()
                ? new ChildPlacements(Collections.emptyList())
                : new ChildPlacements(
                        Collections.singletonList(
                                new ChildPlacements.Entry(
                                        placeholder, new LayoutBox(0, 0, box.width, box.height))));
    }

    @Override
    public boolean passiveHover() {
        return options.tooltip;
    }

    @Override
    public club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text) {
        club.heiqi.uilib.ui.scene.image.ItemImageFacts facts =
                state == null || state.content == null ? null : state.content.itemFacts();
        quantityText =
                facts != null && options.quantity && !facts.quantity.isEmpty()
                        ? text.layout(
                                facts.quantity,
                                node.style(),
                                width,
                                club.heiqi.uilib.ui.scene.text.TextPolicy.SINGLE_LINE)
                        : null;
        return full;
    }

    /** 只缓存最近事实/当前样式/空间/度量代际；hover与其他paint变化不重新resolve或整形。 */
    @Override
    public void preparePaint(
            SceneNode node,
            SceneGeometryView geometry,
            SceneTextMeasurer text,
            long metricsRevision) {
        club.heiqi.uilib.ui.scene.image.ItemImageFacts facts =
                state == null || state.content == null ? null : state.content.itemFacts();
        if (facts == null || !options.tooltip || facts.tooltip.source.isEmpty()) {
            tooltipFacts = null;
            tooltipStyle = null;
            tooltipText = null;
            return;
        }
        LayoutBox viewport = geometry.viewport();
        int padding = Math.min(theme.padding, Math.min(viewport.width, viewport.height) / 2);
        int width = Math.max(0, viewport.width - padding * 2);
        club.heiqi.uilib.ui.scene.text.TextStyle style = node.style().withColor(theme.tooltipText);
        if (tooltipText == null
                || tooltipFacts != facts
                || !style.equals(tooltipStyle)
                || tooltipWidth != width
                || tooltipMetrics != metricsRevision) {
            club.heiqi.uilib.ui.scene.text.TextPolicy policy =
                    new club.heiqi.uilib.ui.scene.text.TextPolicy(
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.AUTO,
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.EMERGENCY,
                            "und",
                            0,
                            0);
            tooltipText = text.layout(facts.tooltip.resolve(style).text, width, policy);
            tooltipFacts = facts;
            tooltipStyle = style;
            tooltipWidth = width;
            tooltipMetrics = metricsRevision;
        }
    }

    @Override
    public void paintOverlay(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            SceneTextMeasurer text) {
        club.heiqi.uilib.ui.scene.image.ItemImageFacts facts =
                state == null || state.content == null ? null : state.content.itemFacts();
        if (facts == null
                || tooltipText == null
                || !options.tooltip
                || node.runtime() == null
                || node.runtime().pointerRouter().passiveHovered(geometry) != node) return;
        SceneGeometryView.Entry entry = geometry.get(node);
        LayoutBox viewport = geometry.viewport();
        if (entry == null || viewport.width == 0 || viewport.height == 0) return;
        int padding = Math.min(theme.padding, Math.min(viewport.width, viewport.height) / 2);
        club.heiqi.uilib.ui.scene.text.TextLayout tip = tooltipText;
        int w = Math.min(viewport.width, tip.width + padding * 2),
                h = Math.min(viewport.height, tip.height + padding * 2);
        int x = Math.max(viewport.x, Math.min(viewport.x + viewport.width - w, entry.box.x));
        long below = (long) entry.box.y + entry.box.height + theme.gap;
        long desired =
                below + h <= (long) viewport.y + viewport.height
                        ? below
                        : (long) entry.box.y - theme.gap - h;
        int y =
                (int)
                        Math.max(
                                viewport.y,
                                Math.min((long) viewport.y + viewport.height - h, desired));
        LayoutBox box = new LayoutBox(x, y, w, h);
        target.add(PaintCommand.rect(box, viewport, theme.tooltip));
        LayoutBox inner =
                new LayoutBox(
                        x + padding,
                        y + padding,
                        Math.max(0, w - padding * 2),
                        Math.max(0, h - padding * 2));
        target.add(PaintCommand.text(inner, inner.intersect(viewport), tip));
    }

    @Override
    public SceneNode paintFallbackChild(SceneNode node) {
        return placeholder;
    }

    @Override
    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        paint(node, geometry, target, null);
    }

    @Override
    public void paint(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            club.heiqi.uilib.ui.scene.paint.PaintPlan fallbackPaint) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        club.heiqi.uilib.ui.scene.paint.PaintPlan pending =
                placeholder == null ? defaultFallback(node, entry, false) : fallbackPaint;
        club.heiqi.uilib.ui.scene.paint.PaintPlan failed =
                placeholder == null ? defaultFallback(node, entry, true) : fallbackPaint;
        if (state == null || state.content == null) {
            club.heiqi.uilib.ui.scene.paint.PaintPlan selected =
                    state == null || state.status == ImageState.Status.LOADING ? pending : failed;
            if (selected != null)
                for (PaintCommand command : selected.commands()) target.add(command);
            return;
        }
        LayoutBox clip = entry.clip;
        if (options.fit == ImageOptions.Fit.COVER)
            clip = clip == null ? entry.box : clip.intersect(entry.box);
        ImagePlacement placement = ImagePlacement.fit(state.content, entry.box, options);
        PaintPlanBuilder overlay = new PaintPlanBuilder();
        club.heiqi.uilib.ui.scene.image.ItemImageFacts facts = state.content.itemFacts();
        if (facts != null) {
            LayoutBox overlayClip = clip == null ? entry.box : clip.intersect(entry.box);
            int right = (int) Math.floor(placement.x + placement.width),
                    bottom = (int) Math.floor(placement.y + placement.height);
            if (quantityText != null)
                overlay.add(
                        PaintCommand.text(
                                new LayoutBox(
                                        right - quantityText.width,
                                        bottom - quantityText.height,
                                        quantityText.width,
                                        quantityText.height),
                                overlayClip,
                                quantityText));
            if (options.durability && facts.durabilityVisible) {
                int barWidth =
                        Math.max(0, (int) Math.floor(placement.width * 13 / state.content.width));
                int barHeight =
                        Math.max(1, (int) Math.ceil(placement.height * 2 / state.content.height));
                int x = (int) Math.floor(placement.x + placement.width / state.content.width),
                        y = bottom - barHeight;
                overlay.add(
                        PaintCommand.rect(
                                new LayoutBox(x, y, barWidth, barHeight), overlayClip, 0xFF000000));
                overlay.add(
                        PaintCommand.rect(
                                new LayoutBox(
                                        x,
                                        y,
                                        (int) Math.round(barWidth * facts.durabilityRemaining),
                                        Math.max(1, barHeight / 2)),
                                overlayClip,
                                facts.durabilityColor));
            }
        }
        target.add(
                PaintCommand.image(entry.box, clip, placement, pending, failed, overlay.build()));
    }

    private club.heiqi.uilib.ui.scene.paint.PaintPlan defaultFallback(
            SceneNode node, SceneGeometryView.Entry entry, boolean failed) {
        LayoutBox clip = entry.clip == null ? entry.box : entry.clip.intersect(entry.box);
        PaintPlanBuilder target = new PaintPlanBuilder();
        target.add(PaintCommand.rect(entry.box, clip, failed ? 0xFF642D35 : 0xFF39434F));
        if (!node.text().isEmpty()) target.add(PaintCommand.text(entry.box, clip, entry.text));
        return target.build();
    }
}
