package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 页面共用的现代视觉词汇；颜色与文字单一派生，输入/编辑/布局仍归正式控件。 */
public class SceneAppearance {
    public static final int LIGHT_CANVAS=0xfffafafa,DARK_CANVAS=0xff09090b;
    public static final int CONTROL_RADIUS=8,CARD_RADIUS=12,CONTROL_PADDING=10,GAP=12,PAGE_PADDING=28,MODAL_SCRIM=0x80000000;
    public enum Role { PANEL, CARD, OUTLINE, PRIMARY, GHOST, ENTRY, FIELD }
    public static final class Palette {
        public final SceneTheme source;
        public final boolean dark;
        public final int canvas,selected,accent,error;
        public final club.heiqi.uilib.ui.scene.text.TextStyle textStyle;
        public final int background,muted,surface,border,text,primary,onPrimary,hover;
        public Palette(boolean dark){this(dark?SceneTheme.dark():SceneTheme.light());}
        public Palette(SceneTheme source) {
            this.source=source;
            textStyle=club.heiqi.uilib.ui.scene.text.font.UiFontSources.style(source.textStyle);
            int color=source.panel;
            dark=((color>>>16&255)*299+(color>>>8&255)*587+(color&255)*114)<128000;
            canvas=dark?DARK_CANVAS:LIGHT_CANVAS;
            selected=dark?0xff27272a:0xffe4e4e7;
            accent=dark?0xffd4d4d8:0xff52525b;
            error=dark?0xfff87171:0xffdc2626;
            background=dark?0xff09090b:0xffffffff;
            surface=dark?0xff18181b:0xfffafafa;
            border=dark?0xff27272a:0xffe4e4e7;
            text=dark?0xfffafafa:0xff18181b;
            muted=dark?0xffa1a1aa:0xff71717a;
            primary=text;onPrimary=background;
            hover=dark?0xff27272a:0xfff4f4f5;
        }
        public SceneTheme editorTheme() {
            return new SceneTheme(textStyle.withColor(text),0,0,0x409494a3,0,text,0,muted,primary,onPrimary,11,8);
        }
        public SceneTheme theme(float scale,int color,int padding) {
            return new SceneTheme(textStyle.withEm(textStyle.logicalEm*scale).withColor(color),
                    0,0,0,0,0,0,muted,primary,onPrimary,padding,8);
        }
    }
    protected final ScenePageContext page;
    public final club.heiqi.uilib.ui.reactive.Owner scope;
    public final ReadableSignal<Palette> colors;
    public SceneAppearance(ScenePageContext page,ReadableSignal<SceneTheme> theme) {
        this.page=page;
        scope=page.scope().child();
        colors=page.domain().computed(scope,()->new Palette(theme.get()));
    }
    public static SceneNode column(int gap){return new SceneNode().size(Size.fill(1),Size.natural()).gap(gap);}
    public static SceneNode row(int gap){return column(gap).axis(SceneNode.Axis.ROW).control(new Flow(true));}
    public SceneNode label(ReadableSignal<String> text,float scale,boolean muted) {
        return SceneLabel.create(page,text,()->colors.get().theme(scale,muted?colors.get().muted:colors.get().text,0));
    }
    public SceneNode error(ReadableSignal<String> message){
        return SceneLabel.create(page,()->message.get()==null?"":message.get(),()->colors.get().theme(.9f,colors.get().error,0))
                .bind(NodeBinding.value(()->message.get()!=null&&!message.get().isEmpty(),SceneNode::visible));
    }
    public SceneNode ellipsis(ReadableSignal<String> text,float scale,boolean muted) {
        return SceneLabel.create(page,text,()->colors.get().theme(scale,muted?colors.get().muted:colors.get().text,0),SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
    }
    public SceneNode title(String text) {
        return SceneLabel.create(page,()->text,()->colors.get().theme(1.4f,colors.get().text,0)
                .withTextStyle(colors.get().textStyle.withEm(colors.get().textStyle.logicalEm*1.4f)
                        .withColor(colors.get().text).withFontStyle(new FontStyle(600,FontStyle.Posture.NORMAL))));
    }
    public SceneNode icon(SceneIconButton.Symbol symbol) {
        return SceneIconButton.graphic(page,()->symbol,()->colors.get().theme(1,colors.get().muted,0))
                .bind(NodeBinding.value(()->Math.round(colors.get().source.textStyle.logicalEm*1.25f),
                        (n,s)->n.size(Size.fixed(s),Size.fixed(s))));
    }
    public SceneNode action(String text,SceneIconButton.Symbol icon,Runnable action,Role role){return action(()->text,icon,action,role);}
    public SceneNode action(ReadableSignal<String> text,SceneIconButton.Symbol icon,Runnable action,Role role){
        return SceneButton.createModern(page,text,colors,action,role,icon==null?null:()->icon);
    }
    public static SceneTheme theme(boolean dark){
        Palette p=new Palette(dark);
        return new SceneTheme(p.textStyle.withEm(15).withColor(p.text),p.background,p.surface,p.hover,p.selected,p.accent,
                p.surface,p.muted,p.primary,p.onPrimary,CONTROL_PADDING,GAP);
    }
    public static SceneNode button(ScenePageContext page,ReadableSignal<String> text,ReadableSignal<SceneTheme> theme,Runnable action,Role role){
        club.heiqi.uilib.ui.reactive.Owner owner=page.scope().child();
        ReadableSignal<Palette> colors=page.domain().computed(owner,()->new Palette(theme.get()));
        return SceneButton.createModern(page,text,colors,action,role,null).own(owner);
    }
    public SceneNode card(SceneNode content){return column(0).control(new Surface(colors,Role.CARD,null,()->false)).add(content);}
    public SceneNode field(SceneNode editor) {
        SceneNode scrollbar=SceneScrollbar.create(page,SceneScrollViewport.of(page,editor,SceneNode.Axis.ROW),
                ()->new SceneScrollbar.Options().dimensions(4,24,1).colors(0,colors.get().border,colors.get().muted),
                ()->colors.get().editorTheme()).size(Size.fill(1),Size.natural());
        // 显式复用同一编辑视口，替代自动条；条位于字段底部，不覆盖路径文字。
        return column(0).size(Size.fill(1),Size.natural()).control(new Surface(colors,Role.FIELD,editor,()->false))
                .add(editor).add(scrollbar);

    }
    public SceneNode line() {return column(0).size(Size.fill(1),Size.fixed(1)).bind(NodeBinding.value(colors,(n,p)->n.background(p.border)));}
    static class Flow implements SceneControl {
        final boolean centered;
        public Flow(){this(false);} public Flow(boolean centered){this.centered=centered;}
        public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer text){return new MeasuredSize(0,0);}
        public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer text,SceneChildMeasurer children){return SceneFlowLayout.measure(n,c,children);}
        public ChildPlacements arrange(SceneNode n,LayoutBox box,SceneChildMeasurer children){
            ChildPlacements flow=SceneFlowLayout.arrange(n,box,children);
            if(!centered)return flow;
            List<ChildPlacements.Entry> result=new ArrayList<>();
            for(ChildPlacements.Entry item:flow.entries()){
                LayoutBox b=item.relativeBox;
                result.add(new ChildPlacements.Entry(item.child,new LayoutBox(b.x,n.padding()+Math.max(0,(box.height-n.padding()*2-b.height)/2),b.width,b.height)));
            }
            return new ChildPlacements(result);
        }
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint){}
    }
    static class Surface extends Flow {
        private final ReadableSignal<Palette> colors;
        private final ReadableSignal<Boolean> selected;
        private final SceneNode target;
        private final Role role;
        public Surface(ReadableSignal<Palette> colors,Role role,SceneNode target,ReadableSignal<Boolean> selected){
            this.colors=colors;this.role=role;this.target=target;this.selected=selected;
        }
        public List<ReadableSignal<?>> paintDependencies(SceneNode n){return Arrays.asList(colors,selected);}
        private boolean enabled(){for(SceneNode at=target;at!=null;at=at.parent())if(!at.enabled())return false;return true;}
        private boolean hovered(SceneGeometryView geometry){
            if(target==null||target.runtime()==null)return false;
            for(SceneNode at=target.runtime().pointerRouter().hovered(geometry);at!=null;at=at.parent())if(at==target)return true;
            return false;
        }
        public void paint(SceneNode n,SceneGeometryView geometry,PaintPlanBuilder paint){
            SceneGeometryView.Entry e=geometry.get(n);if(e==null)return;
            Palette p=colors.get();boolean active=enabled();
            boolean focused=target!=null&&target.runtime()!=null&&target.runtime().focusRouter().focusVisible(target);
            boolean pressed=target!=null&&target.runtime()!=null&&target.runtime().pointerRouter().pressed(target);
            int fill=role==Role.PRIMARY?p.primary:role==Role.GHOST||role==Role.ENTRY?0:p.background;
            if(role==Role.ENTRY&&selected.get())fill=p.hover;
            if(hovered(geometry)&&active||pressed&&active)fill=role==Role.PRIMARY?p.muted:p.hover;
            if(!active)fill=p.surface;
            int edge=focused?p.muted:role==Role.OUTLINE||role==Role.FIELD||role==Role.PANEL||role==Role.CARD?p.border:fill;
            if(role==Role.PANEL){
                for(int spread=6;spread>=2;spread-=2)ScenePaintShapes.rounded(paint,
                        new LayoutBox(e.box.x-spread,e.box.y-spread/2,e.box.width+spread*2,e.box.height+spread*2),e.clip,0x08000000,12+spread);
            }
            int radius=role==Role.PANEL||role==Role.CARD?CARD_RADIUS:CONTROL_RADIUS;
            ScenePaintShapes.rounded(paint,e.box,e.clip,edge,radius);
            ScenePaintShapes.rounded(paint,new LayoutBox(e.box.x+1,e.box.y+1,Math.max(0,e.box.width-2),Math.max(0,e.box.height-2)),e.clip,fill,radius-1);
        }
    }
}
