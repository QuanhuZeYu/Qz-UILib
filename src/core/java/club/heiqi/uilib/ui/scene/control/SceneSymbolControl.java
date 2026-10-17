package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.Collections;
import java.util.Objects;

/** 名称、提示、命中与向量标记共用正式几何；不建立内部输入所有权。 */
abstract class SceneSymbolControl implements SceneControl {
    final ReadableSignal<SceneTheme> theme;
    private final boolean showLabel;
    SceneSymbolControl(ReadableSignal<SceneTheme> theme, boolean showLabel) {
        this.theme = Objects.requireNonNull(theme); this.showLabel = showLabel;
    }
    static SceneNode node(ScenePageContext page, ReadableSignal<String> name, SceneSymbolControl control, Runnable action) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(name); Objects.requireNonNull(action);
        SceneNode node = new SceneNode().control(control).size(Size.natural(),Size.natural())
                .focusable(true).clickFocus(true).onClick(action);
        node.bind(NodeBinding.value(() -> Objects.requireNonNull(name.get(), "控件名称"), SceneNode::text));
        node.bind(NodeBinding.value(control.theme, (n,t) -> {
            n.style(t.textStyle);
            int padding=Math.max(4,t.padding/2);
            if(n.padding()!=padding)n.padding(padding);
            // 只读名称的间距也是尺寸依赖；颜色变化不能借重复padding写入失效度量。
            int gap=control.showLabel?t.gap:0;
            if(n.gap()!=gap)n.gap(gap);
        }));
        return node;
    }
    static SceneNode graphic(ScenePageContext page, SceneSymbolControl control) {
        Objects.requireNonNull(page).domain().checkWrite();
        return new SceneNode().control(control).size(Size.natural(),Size.natural())
                .bind(NodeBinding.value(control.theme,(n,t) -> n.style(t.textStyle)));
    }
    private int symbolSize(SceneNode node) { return Math.max(1,(int)Math.ceil(node.style().logicalEm * 1.15f)); }
    private int labelOffset(SceneNode node) { return symbolSize(node) + node.gap(); }
    @Override public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
        return text.layout(showLabel ? node.text() : "",node.style(),Math.max(0,width - (showLabel ? labelOffset(node) : 0)),node.textPolicy());
    }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        TextLayout label = layoutText(node,text,limits.availableWidth);
        return new MeasuredSize(showLabel ? labelOffset(node) + label.width : symbolSize(node),Math.max(symbolSize(node),label.height));
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return new ChildPlacements(Collections.emptyList());
    }
    @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(theme); }
    abstract void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color);
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry e = geometry.get(node); if (e == null) return;
        SceneTheme style = theme.get(); boolean enabled = true;
        for (SceneNode n = node; n != null; n = n.parent()) enabled &= n.enabled();
        boolean hover = enabled && node.runtime().pointerRouter().hovered(geometry) == node;
        boolean pressed = enabled && node.runtime().pointerRouter().pressed(node);
        if(node.clickAction()!=null) club.heiqi.uilib.ui.scene.paint.ScenePaintShapes.rounded(target,e.box,e.clip,
                !enabled?style.disabled:pressed?style.pressed:hover?style.hover:style.button,Math.min(e.box.width,e.box.height)/2);
        if (enabled && node.runtime().focusRouter().focusVisible(node)) SceneControlPaint.border(target,e.box,e.clip,style.focus);
        LayoutBox inner = SceneControlPaint.inner(e.box,node.padding());
        int side = Math.min(symbolSize(node),Math.min(inner.width,inner.height));
        LayoutBox mark = new LayoutBox(inner.x+(showLabel?0:(inner.width-side)/2),inner.y + (inner.height-side)/2,side,side);
        symbol(mark,target,e.clip,enabled ? style.textStyle.color : style.disabledText);
        if (showLabel) {
            LayoutBox label = new LayoutBox(inner.x+labelOffset(node),inner.y,Math.max(0,inner.width-labelOffset(node)),inner.height);
            target.add(PaintCommand.text(label,e.clip,e.text.withStyle(e.text.style.withColor(enabled ? node.style().color : style.disabledText))));
        }
    }
    @Override public SceneTooltip.Hint tooltip(SceneNode node,SceneGeometryView geometry){
        return showLabel||node.clickAction()==null||node.text().isEmpty()?null:new SceneTooltip.Hint(SceneTooltip.Content.text(node.text()),new SceneTooltip.Options(),theme.get());
    }
}
