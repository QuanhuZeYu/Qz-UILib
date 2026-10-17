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
    private final ReadableSignal<SceneIconButton.Symbol> leadingIcon;
    private SceneAppearance.Surface modernSurface;
    private final SceneControlMotion motion = new SceneControlMotion();
    private TextLayout source, presented;
    private int presentWidth, presentHeight;

    private SceneTextControl(
            ReadableSignal<SceneTheme> theme, SceneTextPolicy policy, boolean button,
            ReadableSignal<SceneIconButton.Symbol> leadingIcon) {
        this.theme = theme;
        this.policy = policy;
        this.button = button;
        this.leadingIcon = leadingIcon;
    }

    static SceneNode create(
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy,
            boolean button) {
        return create(text,theme,policy,button,null);
    }
    static SceneNode create(ReadableSignal<String> text, ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy, boolean button, ReadableSignal<SceneIconButton.Symbol> leadingIcon) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(policy, "policy");
        SceneNode node =
                new SceneNode()
                        .control(new SceneTextControl(theme, policy, button, leadingIcon))
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
                            if (button && target.padding()!=value.padding) target.padding(value.padding);
                        }));
        if (button) {
            SceneTextControl control = (SceneTextControl) node.control();
            control.motion.install(node, () -> theme.get().feedbackMotion,
                    self -> control.modernSurface == null ? SceneControlMotion.target(self, theme.get())
                            : control.modernSurface.fill(self));
        }
        return node;
    }

    static SceneNode createModern(ReadableSignal<String> text,ReadableSignal<SceneAppearance.Palette> colors,
            SceneAppearance.Role role,ReadableSignal<SceneIconButton.Symbol> icon){
        SceneNode node=create(text,()->colors.get().theme(1,
                role==SceneAppearance.Role.PRIMARY?colors.get().onPrimary:colors.get().text,10),
                SceneTextPolicy.ELLIPSIS_SINGLE_LINE,true,icon);
        ((SceneTextControl)node.control()).modernSurface=new SceneAppearance.Surface(colors,role,node,()->false);
        return node;
    }
    private int iconSize(SceneNode node) { return leadingIcon==null?0:Math.max(1,Math.round(node.style().logicalEm*1.2f)); }
    private int iconSpace(SceneNode node) { return leadingIcon==null?0:iconSize(node)+theme.get().gap; }
    @Override public java.util.List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return leadingIcon==null?Collections.emptyList():Collections.singletonList(theme);
    }
    @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode node) {
        return leadingIcon==null?Collections.singletonList(theme):java.util.Arrays.asList(theme,leadingIcon);
    }
    @Override public TextLayout layoutText(SceneNode node,SceneTextMeasurer text,int width) {
        return text.layout(node.text(),node.style(),Math.max(0,width-iconSpace(node)),node.textPolicy());
    }
    @Override
    public MeasuredSize measure(
            SceneNode node, Constraints constraints, SceneTextMeasurer measurer) {
        TextLayout full =
                layoutText(node,measurer,constraints.availableWidth);
        float width = 0, height = 0;
        for (TextLayout.Line line : full.lines()) {
            width = Math.max(width, SceneTextPresentation.right(line));
            height = Math.max(height, SceneTextPresentation.bottom(line));
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE) break;
        }
        return new MeasuredSize((int) Math.ceil(width)+iconSpace(node),Math.max(iconSize(node),(int) Math.ceil(height)));
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer childMeasurer) {
        return new ChildPlacements(Collections.emptyList());
    }

    @Override
    public TextLayout presentText(
            SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer measurer) {
        width=Math.max(0,width-iconSpace(node));
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
                enabled && runtime != null && runtime.focusRouter().focusVisible(node);
        if(modernSurface!=null)modernSurface.paint(node,geometry,target,motion.color(modernSurface.fill(node)));
        else if (button) {
            boolean hovered =
                    enabled && runtime != null && runtime.pointerRouter().hovered(geometry) == node;
            boolean pressed = enabled && runtime != null && runtime.pointerRouter().pressed(node);
            int color =
                    !enabled
                            ? style.disabled
                            : pressed ? style.pressed : hovered ? style.hover : style.button;
            target.add(PaintCommand.rect(entry.box, entry.clip, motion.color(color)));
            if (focused) border(target, entry.box, entry.clip, style.focus);
        }
        LayoutBox inner = inner(entry.box, node.padding());
        if(leadingIcon!=null){
            int side=Math.min(iconSize(node),Math.min(inner.width,inner.height));
            SceneIconGlyph.paint(Objects.requireNonNull(leadingIcon.get(),"按钮图标"),
                    new LayoutBox(inner.x,inner.y+Math.max(0,(inner.height-side)/2),side,side),target,entry.clip,
                    enabled?node.style().color:style.disabledText);
            int inset=iconSpace(node);
            inner=new LayoutBox(inner.x+inset,inner.y,Math.max(0,inner.width-inset),inner.height);
        }
        LayoutBox clip =
                policy == SceneTextPolicy.OVERFLOW
                        ? entry.clip
                        : entry.clip == null ? inner : entry.clip.intersect(inner);
        TextLayout paint = entry.text;
        if (!enabled) paint = paint.withStyle(paint.style.withColor(style.disabledText));
        target.add(PaintCommand.text(inner, clip, paint));
    }

    @Override public SceneTooltip.Hint tooltip(SceneNode node,SceneGeometryView geometry){
        SceneGeometryView.Entry entry=geometry.get(node);
        if(source==null||presented==source||entry==null||entry.text!=presented)return null;
        String full=source.originalText==null?source.logicalText:source.originalText;
        return new SceneTooltip.Hint(SceneTooltip.Content.text(full),new SceneTooltip.Options().width(Float.MAX_VALUE),theme.get());
    }

}
