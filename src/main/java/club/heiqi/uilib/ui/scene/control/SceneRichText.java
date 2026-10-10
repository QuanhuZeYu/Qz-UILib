package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 正式基础富文本入口；业务声明内容、主题及链接意图，不补链接节点或几何。 */
public final class SceneRichText {
    private SceneRichText() {}
    public static SceneNode create(ScenePageContext context, ReadableSignal<TextContent> content,
            ReadableSignal<SceneTheme> theme, Consumer<String> activate) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(theme, "theme");
        RichControl control = new RichControl(Objects.requireNonNull(activate, "activate"));
        SceneNode node = new SceneNode().control(control);
        node.bind(NodeBinding.value(content, (target, value) -> {
            control.declaration = Objects.requireNonNull(value, "content value"); target.text(value.source);
        }));
        node.bind(NodeBinding.value(theme, (target, value) -> target.style(value.textStyle)));
        return node;
    }
    private static final class RichControl implements SceneControl {
        private final Consumer<String> activate;
        private TextContent declaration, cachedDeclaration;
        private TextStyle cachedStyle;
        private TextContent.Resolved resolved;
        RichControl(Consumer<String> activate) { this.activate = activate; }
        private TextContent.Resolved resolve(SceneNode node) {
            if (cachedDeclaration != declaration || !node.style().equals(cachedStyle)) {
                resolved = declaration.resolve(node.style()); cachedDeclaration = declaration; cachedStyle = node.style();
            }
            return resolved;
        }
        @Override public TextLayout layoutText(SceneNode node, SceneTextMeasurer measurer, int width) {
            TextContent.Resolved content = resolve(node);
            TextLayout layout = measurer.layout(content.text, width, node.textPolicy());
            List<TextLayout.Diagnostic> diagnostics = new ArrayList<TextLayout.Diagnostic>(layout.diagnostics());
            for (TextLayout.Line line : layout.lines()) for (TextLayout.Cluster cluster : line.clusters()) {
                if (cluster.logicalStart == cluster.logicalEnd) continue;
                if (content.crossesLinkBoundary(cluster.logicalStart, cluster.logicalEnd))
                    diagnostics.add(new TextLayout.Diagnostic(TextLayout.Diagnostic.Kind.STYLE_COALESCED,
                        cluster.sourceStart, cluster.sourceEnd, "链接边界跨完整文字簇，按起始逻辑位置归属"));
            }
            if (diagnostics.size() == layout.diagnostics().size()) return layout;
            return new TextLayout(layout.width, layout.height, layout.advanceWidth, layout.advanceHeight, layout.style,
                layout.lines(), layout.logicalText, layout.originalText, layout.generation, diagnostics, layout.input);
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            TextLayout layout = layoutText(node, text, constraints.availableWidth);
            float width = 0, height = 0;
            for (TextLayout.Line line : layout.lines()) {
                width = Math.max(width, SceneTextPresentation.right(line)); height = Math.max(height, SceneTextPresentation.bottom(line));
            }
            return new MeasuredSize((int)Math.ceil(width), (int)Math.ceil(height));
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) { return new ChildPlacements(Collections.emptyList()); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node); if (entry == null) return;
            LayoutBox inner = new LayoutBox(entry.box.x + node.padding(), entry.box.y + node.padding(),
                Math.max(0, entry.box.width - node.padding() * 2), Math.max(0, entry.box.height - node.padding() * 2));
            target.add(PaintCommand.text(inner, entry.clip, entry.text));
        }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || cachedDeclaration != declaration || resolved == null
                    || entry.clip != null && !entry.clip.contains(x, y)) return null;
            float localX = x - entry.box.x - node.padding(), localY = y - entry.box.y - node.padding();
            for (TextLayout.Line line : entry.text.lines()) {
                if (localY < line.y || localY >= line.y + line.lineHeight) continue;
                for (TextLayout.Cluster cluster : line.clusters()) if (cluster.logicalEnd > cluster.logicalStart
                        && localX >= cluster.x && localX < cluster.x + cluster.advance) return resolved.linkAt(cluster.logicalStart);
            }
            return null;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object target) {
            if (cachedDeclaration != declaration || resolved == null) return false;
            return resolved.containsLink(target);
        }
        @Override public void activatePointer(SceneNode node, Object target) {
            if (pointerTargetValid(node, target)) activate.accept(((TextContent.Link)target).target);
        }
    }
}
