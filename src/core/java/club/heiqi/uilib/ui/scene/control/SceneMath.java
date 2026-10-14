package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 独立公式控件；中性数学盒分配已有 SceneImage，绘制与输入仍归唯一 scene 管线。 */
public final class SceneMath implements SceneControl {
    /** 接受无焦点、无激活动作的图像子树；尺寸明确按数学 em 分配。 */
    public static final class Embed {
        public final SceneNode node;
        public final MathEmbedMetrics metrics;

        public Embed(SceneNode node, MathEmbedMetrics metrics) {
            this.node = Objects.requireNonNull(node, "node");
            this.metrics = Objects.requireNonNull(metrics, "metrics");
        }
    }

    private final Map<String, Embed> slots;
    private final Map<String, MathEmbedMetrics> metrics;
    private final MathStyle mathStyle;
    private final ReadableSignal<Integer> equationNumberDependency;
    private int initialEquationNumber = 1;
    private int cachedInitialEquationNumber;
    private final MathLayoutEngine engine = new MathLayoutEngine();
    private MathContent content, cachedContent;
    private TextStyle cachedStyle;
    // 弱引用只比较本批次身份，不把 BatchTextMeasurer 及其宿主资源带到后续批次。
    private WeakReference<SceneTextMeasurer> batch = new WeakReference<>(null);
    private MathLayout cachedLayout, finalLayout;
    private TextLayout cachedText;
    private LayoutBox presentationBounds;
    private final List<SceneNode> paintSegments = new ArrayList<>();
    private List<List<PaintPart>> paintChunks = Collections.emptyList();

    /** 每段仅投影同一 MathLayout；没有资源租约、输入目标或自己的几何算法。 */
    private static final class PaintPart {
        final MathLayout.Entry entry;
        final TextLayout text;

        PaintPart(MathLayout.Entry entry, TextLayout text) {
            this.entry = entry;
            this.text = text;
        }
    }

    private final class PassiveMathPaintSegment implements SceneControl {
        final SceneNode root;
        final int chunk;

        PassiveMathPaintSegment(SceneNode root, int chunk) {
            this.root = root;
            this.chunk = chunk;
        }

        public MeasuredSize measure(
                SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer childMeasurer) {
            return new ChildPlacements(Collections.emptyList());
        }

        public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
            return project(
                    cachedLayout, "", root.style(), Collections.emptyList(), presentationBounds);
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            paintChunk(root, geometry, target, chunk);
        }
    }

    private SceneMath(
            Map<String, Embed> slots,
            MathStyle mathStyle,
            ReadableSignal<Integer> equationNumberDependency) {
        this.equationNumberDependency = equationNumberDependency;
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        Map<String, MathEmbedMetrics> values = new LinkedHashMap<>();
        for (Map.Entry<String, Embed> slot : slots.entrySet())
            values.put(slot.getKey(), slot.getValue().metrics);
        metrics = Collections.unmodifiableMap(values);
        this.mathStyle = mathStyle;
    }

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<MathContent> source,
            ReadableSignal<SceneTheme> theme,
            MathStyle mathStyle,
            Map<String, Embed> embeds) {
        return create(context, source, theme, mathStyle, embeds, null);
    }

    /** 同文档公式编号由上层显式串联；重新测量没有隐藏递增副作用。 */
    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<MathContent> source,
            ReadableSignal<SceneTheme> theme,
            MathStyle mathStyle,
            Map<String, Embed> embeds,
            ReadableSignal<Integer> initialEquationNumber) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(mathStyle, "mathStyle");
        Objects.requireNonNull(embeds, "embeds");
        Map<String, Embed> copy = new LinkedHashMap<>();
        Set<SceneNode> identities =
                Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        // 在任何 add 前完整核验，失败不留下半挂载逻辑树。
        for (Map.Entry<String, Embed> entry : embeds.entrySet()) {
            String id = Objects.requireNonNull(entry.getKey(), "embed id");
            Embed value = Objects.requireNonNull(entry.getValue(), "embed");
            if (id.isEmpty() || copy.put(id, value) != null || !identities.add(value.node))
                throw new IllegalArgumentException("嵌入标识与节点身份必须唯一");
            if (!(value.node.control() instanceof SceneImage) || value.node.parent() != null)
                throw new IllegalArgumentException("嵌入必须是未归属的 SceneImage");
            passive(value.node);
        }
        SceneMath control = new SceneMath(copy, mathStyle, initialEquationNumber);
        SceneNode root = new SceneNode().control(control).textPolicy(TextPolicy.NO_WRAP);
        for (Embed slot : copy.values()) root.add(slot.node);
        // 至多 slots-1 个中间段，创建时固定声明；变化只更新投影，不拆树/重挂 Item。
        for (int i = 1; i < copy.size(); i++) {
            SceneNode segment =
                    new SceneNode().control(control.new PassiveMathPaintSegment(root, i));
            control.paintSegments.add(segment);
            root.add(segment);
        }
        root.bind(
                NodeBinding.value(
                        source,
                        (target, value) -> {
                            control.content = Objects.requireNonNull(value, "content");
                            target.text(value.source);
                        }));
        root.bind(NodeBinding.value(theme, (target, value) -> target.style(value.textStyle)));
        if (initialEquationNumber != null)
            root.bind(
                    NodeBinding.value(
                            initialEquationNumber,
                            (target, value) -> {
                                if (value == null || value < 1)
                                    throw new IllegalArgumentException("公式初始编号必须为正整数");
                                control.initialEquationNumber = value;
                            }));
        root.bind(
                (runtime, target, owner) ->
                        new BindingActivation() {
                            public void refresh() {}

                            public void close() {
                                control.clear();
                            }
                        });
        return root;
    }

    private static void passive(SceneNode node) {
        SceneControl control = node.control();
        if (node.runtime() != null
                || node.destroyed()
                || node.focusable()
                || node.clickFocus()
                || node.clickAction() != null
                || node.scrollAction() != null
                || node.escapeAction() != null
                || node.modal()
                || node.scrollable()
                || control != null
                        && (control.capturesPointer()
                                || control.scrollableContent()
                                || control.acceptsTextInput(node)))
            throw new IllegalArgumentException("数学嵌入只接受未挂载、非 focusable 的呈现型图像子树");
        for (SceneNode child : node.children()) passive(child);
    }

    /**
     * 当前最终不可变的公式逻辑坐标、尺寸与基线；首次呈现前及卸载后为 null。 独立控件容纳溢出墨迹时会平移外层呈现原点；实际场景定位读取同代 SceneGeometryView。
     * 不发布布局结果 Signal。
     */
    public static MathLayout layout(SceneNode node) {
        if (!(node.control() instanceof SceneMath)) throw new IllegalArgumentException("不是数学控件");
        return ((SceneMath) node.control()).finalLayout;
    }

    private void clear() {
        batch.clear();
        cachedContent = null;
        cachedStyle = null;
        cachedLayout = finalLayout = null;
        cachedText = null;
        presentationBounds = null;
        paintChunks = Collections.emptyList();
    }

    @Override
    public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return equationNumberDependency == null
                ? Collections.emptyList()
                : Collections.<ReadableSignal<?>>singletonList(equationNumberDependency);
    }

    /** 最近正式测量的外层呈现基线；同数学逻辑基线一起平移，供共同文档行内对齐。 */
    public static float presentationBaseline(SceneNode node) {
        if (!(node.control() instanceof SceneMath)) throw new IllegalArgumentException("不是数学控件");
        SceneMath math = (SceneMath) node.control();
        if (math.cachedLayout == null || math.presentationBounds == null)
            throw new IllegalStateException("公式尚未测量");
        return math.cachedLayout.baseline - math.presentationBounds.y;
    }

    private MathLayout resolve(SceneNode node, SceneTextMeasurer text) {
        MathContent current = Objects.requireNonNull(content, "数学内容尚未绑定");
        if (batch.get() != text
                || cachedContent != current
                || cachedStyle == null
                || !node.style().withColor(cachedStyle.color).equals(cachedStyle)
                || cachedInitialEquationNumber != initialEquationNumber) {
            cachedLayout =
                    engine.layout(
                            current, text.resolveStyle(node.style()).withBaselineShift(0).withLetterSpacing(0).withHighlight(null),
                            mathStyle, text, metrics, initialEquationNumber);
            cachedInitialEquationNumber = initialEquationNumber;
            cachedContent = current;
            cachedStyle = node.style();
            batch = new WeakReference<>(text);
            presentationBounds = presentationBounds(cachedLayout);
            cachedText =
                    project(
                            cachedLayout,
                            current.source,
                            node.style(),
                            cachedLayout.pieces,
                            presentationBounds);
            paintChunks = chunks(cachedLayout, current.source, node.style(), presentationBounds);
        } else if (node.style().color != cachedStyle.color) {
            // 颜色只刷新不可变载荷与投影；不进入数学盒、字体整形或伸缩构建。
            cachedLayout = cachedLayout.withColor(node.style().color);
            cachedStyle = node.style();
            cachedText = project(cachedLayout, current.source, cachedStyle,
                    cachedLayout.pieces, presentationBounds);
            paintChunks = chunks(cachedLayout, current.source, cachedStyle, presentationBounds);
        }
        return cachedLayout;
    }

    @Override
    public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
        resolve(node, text);
        return new MeasuredSize(presentationBounds.width, presentationBounds.height);
    }

    @Override
    public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
        resolve(node, text);
        return cachedText;
    }

    @Override
    public TextLayout presentText(
            SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer text) {
        // 不依赖 layoutText 的缓存回调顺序；最终排列总是显式确定本批事实。
        finalLayout = resolve(node, text);
        return cachedText;
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer childMeasurer) {
        club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine.rejectContentLayers(node, node, "math content");
        List<ChildPlacements.Entry> placements = new ArrayList<>();
        // 无 placement 的 slot 仍在逻辑树中，动态 binding 也不能给它输入资格。
        for (Embed slot : slots.values()) passiveAttached(slot.node);
        if (finalLayout != null) {
            int index = 0;
            for (MathLayout.Embed embed : finalLayout.embeds) {
                if (index > 0)
                    placements.add(
                            new ChildPlacements.Entry(
                                    paintSegments.get(index - 1), new LayoutBox(0, 0, 0, 0)));
                index++;
                SceneNode child = slots.get(embed.id).node;
                placements.add(
                        ChildPlacements.Entry.contentBox(
                                child,
                                bounds(
                                        node.padding() + embed.x - presentationBounds.x,
                                        node.padding() + embed.y - presentationBounds.y,
                                        embed.width,
                                        embed.height)));
            }
        }
        return new ChildPlacements(placements);
    }

    private static void passiveAttached(SceneNode node) {
        SceneControl control = node.control();
        if (node.focusable()
                || node.clickFocus()
                || node.clickAction() != null
                || node.scrollAction() != null
                || node.escapeAction() != null
                || node.modal()
                || node.scrollable()
                || control != null
                        && (control.capturesPointer()
                                || control.scrollableContent()
                                || control.acceptsTextInput(node)))
            throw new IllegalStateException("数学图像绑定不能获得主动交互资格");
        for (SceneNode child : node.children()) passiveAttached(child);
    }

    @Override
    public void preparePaint(
            SceneNode node,
            SceneGeometryView geometry,
            SceneTextMeasurer text,
            long metricsRevision) {
        // focusable 等资格变更只失效 interaction/paint，可能复用 geometry 而不再 arrange。
        // 此正式只读端口同样核验无 placement 的逻辑 slot，不依赖强制重排。
        for (Embed slot : slots.values()) passiveAttached(slot.node);
    }

    @Override
    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        paintChunk(node, geometry, target, 0);
    }

    @Override
    public void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        if (paintChunks.size() > 1) paintChunk(node, geometry, target, paintChunks.size() - 1);
    }

    private void paintChunk(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target, int index) {
        SceneGeometryView.Entry scene = geometry.get(node);
        if (scene == null || finalLayout == null || index >= paintChunks.size()) return;
        LayoutBox origin =
                new LayoutBox(
                        scene.box.x + node.padding(),
                        scene.box.y + node.padding(),
                        presentationBounds.width,
                        presentationBounds.height);
        for (PaintPart part : paintChunks.get(index)) {
            if (part.text != null) target.add(PaintCommand.text(origin, scene.clip, part.text));
            else if (part.entry.rule != null) {
                MathLayout.Rule r = part.entry.rule;
                target.add(
                        PaintCommand.rect(
                                bounds(
                                        origin.x + r.x - presentationBounds.x,
                                        origin.y + r.y - presentationBounds.y,
                                        r.width,
                                        r.height),
                                scene.clip,
                                r.color));
            } else {
                MathLayout.Stroke l = part.entry.stroke;
                target.add(
                        PaintCommand.line(
                                origin.x + l.x1 - presentationBounds.x,
                                origin.y + l.y1 - presentationBounds.y,
                                origin.x + l.x2 - presentationBounds.x,
                                origin.y + l.y2 - presentationBounds.y,
                                l.width,
                                scene.clip,
                                l.color));
            }
        }
        // IMAGE只由原有子树发出；此段严格位于两个对应Item子树之间。
    }

    private static List<List<PaintPart>> chunks(
            MathLayout value, String source, TextStyle style, LayoutBox extent) {
        List<List<PaintPart>> chunks = new ArrayList<>();
        List<PaintPart> chunk = new ArrayList<>();
        chunks.add(chunk);
        List<MathLayout.TextPiece> texts = new ArrayList<>();
        for (MathLayout.Entry entry : value.entries) {
            if (entry.kind == MathLayout.Entry.Kind.TEXT) {
                texts.add(entry.piece);
                continue;
            }
            flushText(chunk, texts, value, source, style, extent);
            if (entry.kind == MathLayout.Entry.Kind.EMBED) {
                chunk = new ArrayList<>();
                chunks.add(chunk);
            } else chunk.add(new PaintPart(entry, null));
        }
        flushText(chunk, texts, value, source, style, extent);
        return chunks;
    }

    private static void flushText(
            List<PaintPart> chunk,
            List<MathLayout.TextPiece> texts,
            MathLayout value,
            String source,
            TextStyle style,
            LayoutBox extent) {
        if (texts.isEmpty()) return;
        chunk.add(new PaintPart(null, project(value, source, style, texts, extent)));
        texts.clear();
    }

    /**
     * smash/lap 只压缩公式内部的逻辑占位，不删除墨迹。独立控件的自然尺寸必须容纳 同一布局中的可见事实，否则祖先滚动范围在分母/嵌入底部之前结束。这里只投影 外层呈现包络，保留
     * MathLayout 的基线、间距与逻辑度量；不改公式内部盒算法。
     */
    private static LayoutBox presentationBounds(MathLayout value) {
        float left = 0, top = 0, right = value.width, bottom = value.height;
        for (MathLayout.Entry entry : value.entries) {
            float x1, y1, x2, y2;
            if (entry.piece != null) {
                MathLayout.TextPiece piece = entry.piece;
                for (TextLayout.Line line : piece.text.lines()) {
                    for (TextLayout.Glyph glyph : line.glyphs()) {
                        if (glyph.inkBounds.right <= glyph.inkBounds.left
                                || glyph.inkBounds.bottom <= glyph.inkBounds.top) continue;
                        left = Math.min(left, piece.x + glyph.x + glyph.inkBounds.left);
                        top = Math.min(top, piece.y + glyph.y + glyph.inkBounds.top);
                        right = Math.max(right, piece.x + glyph.x + glyph.inkBounds.right);
                        bottom = Math.max(bottom, piece.y + glyph.y + glyph.inkBounds.bottom);
                    }
                    for (TextLayout.Decoration decoration : line.decorations()) {
                        left = Math.min(left, piece.x + decoration.x);
                        top = Math.min(top, piece.y + decoration.y);
                        right = Math.max(right, piece.x + decoration.x + decoration.width);
                        bottom = Math.max(bottom, piece.y + decoration.y + decoration.height);
                    }
                }
                continue;
            } else if (entry.rule != null) {
                MathLayout.Rule rule = entry.rule;
                x1 = rule.x;
                y1 = rule.y;
                x2 = x1 + rule.width;
                y2 = y1 + rule.height;
            } else if (entry.stroke != null) {
                MathLayout.Stroke stroke = entry.stroke;
                float half = stroke.width / 2;
                x1 = Math.min(stroke.x1, stroke.x2) - half;
                y1 = Math.min(stroke.y1, stroke.y2) - half;
                x2 = Math.max(stroke.x1, stroke.x2) + half;
                y2 = Math.max(stroke.y1, stroke.y2) + half;
            } else {
                MathLayout.Embed embed = entry.embed;
                x1 = embed.x;
                y1 = embed.y;
                x2 = x1 + embed.width;
                y2 = y1 + embed.height;
            }
            left = Math.min(left, x1);
            top = Math.min(top, y1);
            right = Math.max(right, x2);
            bottom = Math.max(bottom, y2);
        }
        return bounds(left, top, right - left, bottom - top);
    }

    private static LayoutBox bounds(float x, float y, float width, float height) {
        int left = (int) Math.floor(x), top = (int) Math.floor(y);
        return new LayoutBox(
                left, top, (int) Math.ceil(x + width) - left, (int) Math.ceil(y + height) - top);
    }

    private static TextLayout project(
            MathLayout value,
            String source,
            TextStyle style,
            List<MathLayout.TextPiece> pieces,
            LayoutBox extent) {
        List<TextLayout.Line> lines = new ArrayList<>();
        List<TextLayout.Diagnostic> diagnostics = new ArrayList<>();
        long generation = 0;
        for (MathLayout.TextPiece piece : pieces) {
            generation = piece.text.generation;
            for (TextLayout.Line line : piece.text.lines()) {
                List<TextLayout.Glyph> glyphs = new ArrayList<>();
                List<TextLayout.Cluster> clusters = new ArrayList<>();
                List<TextLayout.Decoration> decorations = new ArrayList<>();
                // MathFont.text 的 font baseline 可使 piece.y 为负。只平移原 glyph 坐标，
                // 不能 clamp 或按普通 font lineHeight 重裁剪；外层 extent 包含公式逻辑占位和墨迹。
                for (TextLayout.Glyph g : line.glyphs())
                    glyphs.add(
                            new TextLayout.Glyph(
                                    g.source,
                                    g.size,
                                    g.glyphId,
                                    g.logicalStart,
                                    g.logicalEnd,
                                    g.sourceStart,
                                    g.sourceEnd,
                                    g.x + piece.x - extent.x,
                                    g.y + piece.y - extent.y,
                                    g.xAdvance,
                                    g.yAdvance,
                                    g.color,
                                    g.replacement,
                                    g.inkBounds,
                                    g.logicalEm,
                                    g.rasterToLayout,
                                    g.rasterStyle,
                                    g.resolvedStyle,
                                    g.clusterIndex,
                                    g.paintClip,
                                    g.xExtension));
                for (TextLayout.Cluster c : line.clusters())
                    clusters.add(
                            new TextLayout.Cluster(
                                    c.logicalStart,
                                    c.logicalEnd,
                                    c.sourceStart,
                                    c.sourceEnd,
                                    c.glyphIndexStart,
                                    c.glyphIndexEnd,
                                    c.x + piece.x - extent.x,
                                    c.advance,
                                    c.style,
                                    c.rtl));
                for (TextLayout.Decoration d : line.decorations())
                    decorations.add(
                            new TextLayout.Decoration(
                                    d.kind,
                                    d.x + piece.x - extent.x,
                                    d.y + piece.y - extent.y,
                                    d.width,
                                    d.height,
                                    d.color,
                                    d.logicalStart,
                                    d.logicalEnd,
                                    d.sourceStart,
                                    d.sourceEnd));
                lines.add(
                        new TextLayout.Line(
                                line.text,
                                (int) Math.floor(line.x + piece.x - extent.x),
                                (int) Math.floor(line.y + piece.y - extent.y),
                                line.logicalStart,
                                line.logicalEnd,
                                line.sourceStart,
                                line.sourceEnd,
                                line.advance,
                                line.baseline + piece.y - extent.y,
                                line.ascent,
                                line.descent,
                                line.lineHeight,
                                glyphs,
                                clusters,
                                decorations,
                                line.paragraphRtl));
            }
        }
        for (MathLayout.Diagnostic diagnostic : value.diagnostics)
            diagnostics.add(
                    new TextLayout.Diagnostic(
                            TextLayout.Diagnostic.Kind.UNSUPPORTED,
                            diagnostic.start,
                            diagnostic.end,
                            diagnostic.stage + ": " + diagnostic.message));
        return new TextLayout(
                extent.width,
                extent.height,
                extent.width,
                extent.height,
                style,
                lines,
                source,
                source,
                generation,
                diagnostics,
                value.sourceFallback == null ? null : value.sourceFallback.input);
    }
}
