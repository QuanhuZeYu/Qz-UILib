package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.InlineBox;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 共同文档：字体排版负责行/簇，数学与图像仍是唯一scene树中的正式子项。 */
public final class SceneDocument {
    private SceneDocument() {}

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<DocumentContent> content,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(activate, "activate");
        SceneNode root = new SceneNode();
        root.bind(NodeBinding.value(theme, (node, value) -> node.gap(value.gap)));
        root.bind(
                NodeBinding.repeat(
                        () -> content.get().blocks(),
                        block -> block,
                        signal -> {
                            DocumentContent.Block block = signal.get();
                            return block.paragraph != null
                                    ? paragraph(
                                                    context,
                                                    () -> signal.get().paragraph,
                                                    theme,
                                                    resources,
                                                    activate)
                                            .size(Size.fill(1), Size.natural())
                                    : SceneMath.create(
                                            context,
                                            () -> signal.get().math,
                                            theme,
                                            block.style,
                                            Collections.emptyMap());
                        }));
        return root;
    }

    public static SceneNode paragraph(
            ScenePageContext context,
            ReadableSignal<TextContent> content,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(activate, "activate");
        ResolvedContent shared = new ResolvedContent(content, theme);
        Paragraph control = new Paragraph(activate, resources, shared);
        SceneNode root = new SceneNode().control(control);
        root.bind(
                NodeBinding.value(
                        content,
                        (node, value) -> {
                            if (control.declaration != value) {
                                control.declaration = value;
                                control.invalidate();
                                control.items.keySet().retainAll(value.inlineContents());
                                node.text(value.source);
                                // 相同源码的新声明仍会改变链接/内联身份，必须通知测量和手势资格。
                                if (node.runtime() != null) node.runtime().invalidate();
                            }
                        }));
        root.bind(NodeBinding.value(theme, (node, value) -> node.style(value.textStyle)));
        root.bind(
                NodeBinding.repeat(
                        () -> content.get().inlineContents(),
                        item -> item,
                        signal -> {
                            InlineContent item = signal.get();
                            ReadableSignal<SceneTheme> scoped =
                                    () ->
                                            theme.get()
                                                    .withTextStyle(
                                                            shared.get().text.styleAt(item.offset));
                            SceneNode child;
                            if (item.kind == InlineContent.Kind.MATH) {
                                child =
                                        SceneMath.create(
                                                context,
                                                () -> item.math,
                                                scoped,
                                                item.mathStyle,
                                                Collections.emptyMap());
                            } else {
                                if (resources == null)
                                    throw new IllegalArgumentException(
                                            "img声明需要DocumentResources图像服务");
                                DocumentResources.ImageReference reference =
                                        resources.reference(item.source);
                                ReadableSignal<ImageSource> source =
                                        reference == null
                                                ? () -> resources.source(item.source)
                                                : reference.source;
                                ReadableSignal<ImageOptions> options =
                                        () -> {
                                            ImageOptions original =
                                                    reference == null
                                                            ? ImageOptions.defaults()
                                                            : reference.options.get();
                                            return original.document()
                                                    .size(
                                                            item.width == null
                                                                    ? Size.natural()
                                                                    : Size.fixed(item.width),
                                                            item.height == null
                                                                    ? Size.natural()
                                                                    : Size.fixed(item.height))
                                                    .fit(item.fit)
                                                    .alt(item.alt);
                                        };
                                child =
                                        SceneImage.themed(
                                                resources.images, source, options, scoped);
                            }
                            control.items.put(item, child);
                            return child;
                        }));
        root.bind(
                (runtime, node, owner) ->
                        new BindingActivation() {
                            public void refresh() {}

                            public void close() {
                                control.invalidate();
                            }
                        });
        return root;
    }

    /** 同一声明与基础样式的纯内容派生只归段落一处，父测量和子主题共享；不持节点或租约。 */
    private static final class ResolvedContent {
        final ReadableSignal<TextContent> source;
        final ReadableSignal<SceneTheme> theme;
        TextContent declaration;
        TextStyle style;
        TextContent.Resolved value;

        ResolvedContent(ReadableSignal<TextContent> source, ReadableSignal<SceneTheme> theme) {
            this.source = source;
            this.theme = theme;
        }

        TextContent.Resolved get() {
            TextContent current = source.get();
            TextStyle base = theme.get().textStyle;
            if (current != declaration || !base.equals(style)) {
                value = current.resolve(base);
                declaration = current;
                style = base;
            }
            return value;
        }

        void clear() {
            declaration = null;
            style = null;
            value = null;
        }
    }

    private static final class Paragraph implements SceneControl {
        final Consumer<String> activate;
        final DocumentResources resources;
        final Map<InlineContent, SceneNode> items = new IdentityHashMap<>();
        TextContent declaration, cachedDeclaration;
        final ResolvedContent shared;
        TextContent.Resolved resolved;
        AttributedText measuredText;
        List<InlineBox> boxes = Collections.emptyList();
        List<ChildPlacements.Entry> placements = Collections.emptyList();

        Paragraph(Consumer<String> activate, DocumentResources resources, ResolvedContent shared) {
            this.activate = activate;
            this.resources = resources;
            this.shared = shared;
        }

        void invalidate() {
            cachedDeclaration = null;
            shared.clear();
            resolved = null;
            measuredText = null;
            boxes = Collections.emptyList();
            placements = Collections.emptyList();
        }

        @Override
        public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
            return layoutText(node, text, width, null);
        }

        private void resolveContent() {
            if (declaration == null) throw new IllegalStateException("文档尚未激活");
            TextContent.Resolved current = shared.get();
            if (current != resolved) {
                resolved = current;
                cachedDeclaration = declaration;
                measuredText = null;
            }
        }

        private List<InlineBox> measureBoxes(SceneChildMeasurer children, int width) {
            List<InlineBox> result = new ArrayList<>();
            for (InlineContent item : resolved.inlineContents()) {
                if (children == null) throw new IllegalArgumentException("行内子内容需要scene子测量能力");
                SceneNode child = items.get(item);
                if (child == null) throw new IllegalStateException("内联声明没有同树子项");
                MeasuredSize size =
                        children.measureChild(child, new Constraints(width, Integer.MAX_VALUE));
                float baseline =
                        item.kind == InlineContent.Kind.MATH
                                ? SceneMath.presentationBaseline(child)
                                : size.height;
                result.add(new InlineBox(item.offset, size.width, size.height, baseline));
            }
            return result;
        }

        @Override
        public TextLayout layoutText(
                SceneNode node, SceneTextMeasurer text, int width, SceneChildMeasurer children) {
            resolveContent();
            List<InlineBox> next = measureBoxes(children, width);
            boolean same = next.size() == boxes.size();
            for (int i = 0; same && i < next.size(); i++) {
                InlineBox a = next.get(i), b = boxes.get(i);
                same =
                        a.offset == b.offset
                                && a.width == b.width
                                && a.height == b.height
                                && a.baseline == b.baseline;
            }
            if (measuredText == null || !same) {
                boxes = next;
                measuredText = resolved.text.withInlineBoxes(boxes);
            }
            TextLayout layout = text.layout(measuredText, width, node.textPolicy());
            placements = place(layout, boxes);
            List<TextLayout.Diagnostic> diagnostics = new ArrayList<>(layout.diagnostics());
            for (TextLayout.Line line : layout.lines())
                for (TextLayout.Cluster cluster : line.clusters())
                    if (cluster.logicalEnd > cluster.logicalStart
                            && resolved.crossesLinkBoundary(
                                    cluster.logicalStart, cluster.logicalEnd))
                        diagnostics.add(
                                new TextLayout.Diagnostic(
                                        TextLayout.Diagnostic.Kind.STYLE_COALESCED,
                                        cluster.sourceStart,
                                        cluster.sourceEnd,
                                        "链接边界跨完整文字簇，按起始逻辑位置归属"));
            return diagnostics.size() == layout.diagnostics().size()
                    ? layout
                    : new TextLayout(
                            layout.width,
                            layout.height,
                            layout.advanceWidth,
                            layout.advanceHeight,
                            layout.style,
                            layout.lines(),
                            layout.logicalText,
                            layout.originalText,
                            layout.generation,
                            diagnostics,
                            layout.input);
        }

        private List<ChildPlacements.Entry> place(TextLayout layout, List<InlineBox> layoutBoxes) {
            List<ChildPlacements.Entry> arranged = new ArrayList<>();
            for (int i = 0; i < layoutBoxes.size(); i++) {
                InlineBox box = layoutBoxes.get(i);
                InlineContent item = resolved.inlineContents().get(i);
                for (TextLayout.Line line : layout.lines())
                    for (TextLayout.Cluster cluster : line.clusters()) {
                        if (cluster.logicalStart == box.offset
                                && cluster.logicalEnd == box.offset + 1) {
                            float x = cluster.x, y = line.baseline - box.baseline;
                            int left = (int) Math.floor(x), top = (int) Math.floor(y);
                            arranged.add(
                                    new ChildPlacements.Entry(
                                            items.get(item),
                                            new LayoutBox(
                                                    left,
                                                    top,
                                                    (int) Math.ceil(x + box.width) - left,
                                                    (int) Math.ceil(y + box.height) - top)));
                        }
                    }
            }
            return arranged;
        }

        @Override
        public TextLayout presentText(
                SceneNode node,
                TextLayout full,
                int width,
                int height,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            resolveContent();
            // 每次呈现以本次full定位，即使引擎命中另一宽度的文字缓存，也不消费最后一次排列。
            placements = place(full, measureBoxes(children, width));
            return full;
        }

        @Override
        public MeasuredSize measure(
                SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            return measure(node, constraints, text, null);
        }

        @Override
        public MeasuredSize measure(
                SceneNode node,
                Constraints constraints,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            TextLayout layout = layoutText(node, text, constraints.availableWidth, children);
            float right = layout.width, bottom = layout.height;
            for (TextLayout.Line line : layout.lines()) {
                right = Math.max(right, SceneTextPresentation.right(line));
                bottom = Math.max(bottom, SceneTextPresentation.bottom(line));
            }
            for (ChildPlacements.Entry placed : placements) {
                right = Math.max(right, placed.relativeBox.x + placed.relativeBox.width);
                bottom = Math.max(bottom, placed.relativeBox.y + placed.relativeBox.height);
            }
            return new MeasuredSize((int) Math.ceil(right), (int) Math.ceil(bottom));
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox box) {
            if (node.padding() == 0) return new ChildPlacements(placements);
            List<ChildPlacements.Entry> padded = new ArrayList<>();
            for (ChildPlacements.Entry placement : placements) {
                LayoutBox relative = placement.relativeBox;
                padded.add(
                        new ChildPlacements.Entry(
                                placement.child,
                                new LayoutBox(
                                        relative.x + node.padding(),
                                        relative.y + node.padding(),
                                        relative.width,
                                        relative.height)));
            }
            return new ChildPlacements(padded);
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry != null)
                target.add(
                        PaintCommand.text(
                                new LayoutBox(
                                        entry.box.x + node.padding(),
                                        entry.box.y + node.padding(),
                                        Math.max(0, entry.box.width - node.padding() * 2),
                                        Math.max(0, entry.box.height - node.padding() * 2)),
                                entry.clip,
                                entry.text));
        }

        @Override
        public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null
                    || cachedDeclaration != declaration
                    || resolved == null
                    || entry.clip != null && !entry.clip.contains(x, y)) return null;
            // 显式行高允许对象超出行带：对象链接消费正式子几何，不能只用文字行高判断资格。
            List<InlineContent> inline = resolved.inlineContents();
            for (int i = inline.size() - 1; i >= 0; i--) {
                InlineContent item = inline.get(i);
                SceneGeometryView.Entry child = geometry.get(items.get(item));
                if (child != null
                        && child.box.contains(x, y)
                        && (child.clip == null || child.clip.contains(x, y)))
                    return resolved.linkAt(item.offset);
            }
            float localX = x - entry.box.x - node.padding(),
                    localY = y - entry.box.y - node.padding();
            for (TextLayout.Line line : entry.text.lines()) {
                if (localY < line.y || localY >= line.y + line.lineHeight) continue;
                for (TextLayout.Cluster cluster : line.clusters())
                    if (cluster.logicalEnd > cluster.logicalStart
                            && localX >= cluster.x
                            && localX < cluster.x + cluster.advance)
                        return resolved.linkAt(cluster.logicalStart);
            }
            return null;
        }

        @Override
        public boolean pointerTargetValid(SceneNode node, Object target) {
            return cachedDeclaration == declaration
                    && resolved != null
                    && resolved.containsLink(target);
        }

        @Override
        public void activatePointer(SceneNode node, Object target) {
            if (pointerTargetValid(node, target)) {
                String value = ((TextContent.Link) target).target;
                activate.accept(resources == null ? value : resources.target(value));
            }
        }
    }
}
