package club.heiqi.uilib.ui.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 配置工作区的局部视觉词汇；仅测量、排列与PaintPlan装饰，输入仍由标准控件处理。 */
final class SettingsStyle {
    private SettingsStyle() {}
    static final class Palette {
        final boolean dark;
        final int background, panel, text, muted, border, hover, accent, selected, primary, onPrimary, error;
        Palette(boolean dark) {
            this.dark = dark;
            background = dark ? 0xff17181b : 0xffffffff;
            panel = dark ? 0xff1d1f23 : 0xfffafafa;
            text = dark ? 0xffe5e7eb : 0xff202124;
            muted = dark ? 0xff969ba6 : 0xff767980;
            border = dark ? 0xff32353c : 0xffe6e7eb;
            hover = dark ? 0xff272a30 : 0xfff2f3f5;
            accent = dark ? 0xff84acff : 0xff2563eb;
            selected = dark ? 0xff253249 : 0xffeff5ff;
            primary = dark ? 0xffe8eaf0 : 0xff24262a;
            onPrimary = dark ? 0xff17181b : 0xffffffff;
            error = dark ? 0xffff8b8b : 0xffc43535;
        }
        SceneTheme theme(int em, int color, int padding, boolean transparent) {
            return new SceneTheme(new TextStyle("default", em, color), background,
                    transparent ? 0 : panel, transparent ? 0 : hover, transparent ? 0 : selected,
                    transparent ? 0 : accent, transparent ? 0 : panel, muted,
                    primary, onPrimary, padding, 8);
        }
    }
    static SceneNode column(int gap) { return new SceneNode().size(Size.fill(1), Size.natural()).gap(gap); }
    static SceneNode row(int gap) { return new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(gap); }
    static SceneNode label(ScenePageContext ctx, ReadableSignal<String> text, ReadableSignal<Palette> p, int em, boolean muted) {
        return SceneLabel.create(ctx, text, () -> p.get().theme(em, muted ? p.get().muted : p.get().text, 0, false));
    }
    static final class Button {
        final SceneNode view, target;
        Button(SceneNode view, SceneNode target) { this.view=view; this.target=target; }
    }
    static Button button(ScenePageContext ctx, ReadableSignal<String> text, ReadableSignal<Palette> p,
                            Runnable action, boolean primary, boolean ghost) {
        SceneNode control = SceneButton.create(ctx, text,
                () -> p.get().theme(13, primary ? p.get().onPrimary : p.get().text, 9, true), action,
                SceneTextPolicy.WRAP).size(Size.natural(), Size.natural());
        SceneNode view = new SceneNode().size(Size.natural(), Size.natural()).control(new Surface(p, primary, ghost, control, 7)).add(control);
        return new Button(view, control);
    }
    static SceneNode navigationButton(ScenePageContext ctx,ReadableSignal<String> text,ReadableSignal<Boolean> selected,ReadableSignal<Palette> p,Runnable action) {
        SceneNode control=SceneButton.create(ctx,text,() -> p.get().theme(14,selected.get()?p.get().accent:p.get().text,10,true),action)
                .size(Size.natural(),Size.natural());
        return new SceneNode().size(Size.fill(1),Size.natural()).control(new NavigationSurface(p,selected,control)).add(control);
    }
    static SceneNode toggle(ScenePageContext ctx, ReadableSignal<Boolean> checked, ReadableSignal<Palette> p,Runnable action) {
        SceneNode control=SceneButton.create(ctx,() -> checked.get()?"   ●":"●   ",
                () -> p.get().theme(14,checked.get()?p.get().onPrimary:p.get().text,5,true),action)
                .size(Size.fixed(42),Size.fixed(26));
        return new SceneNode().size(Size.fixed(44),Size.fixed(28))
                .control(new SwitchSurface(p,checked,control)).add(control);
    }
    static SceneNode surface(SceneNode content, ReadableSignal<Palette> p, boolean muted) {
        return new SceneNode().size(Size.fill(1), Size.natural()).control(new Surface(p, false, false, null, 12, muted)).add(content);
    }
    static SceneNode card(SceneNode content, ReadableSignal<Palette> p) { return surface(content.padding(20), p, false); }
    static SceneNode line(ReadableSignal<Palette> p) {
        return new SceneNode().size(Size.fill(1), Size.fixed(1))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(p, (n,v) -> n.background(v.border)));
    }
    private static final class Prepared {
        final MeasuredSize size;
        final ChildPlacements children;
        Prepared(int width, int height, List<ChildPlacements.Entry> children) {
            size = new MeasuredSize(Math.max(0, width), Math.max(0, height));
            this.children = new ChildPlacements(children);
        }
    }
    /** 最终宽度下重新消费正式子测量；不读取宿主尺寸，也不布局后回写节点声明。 */
    private abstract static class Arrangement implements SceneControl {
        private ChildPlacements prepared;
        abstract Prepared layout(SceneNode node, Constraints limits, SceneChildMeasurer children);
        @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer t) { return new MeasuredSize(0,0); }
        @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer t, SceneChildMeasurer m) { return layout(n,c,m).size; }
        @Override public TextLayout presentText(SceneNode n, TextLayout full, int w, int h, SceneTextMeasurer t, SceneChildMeasurer m) {
            prepared = layout(n,new Constraints(w,h),m).children;
            return full;
        }
        @Override public ChildPlacements arrange(SceneNode n, LayoutBox b) { ChildPlacements result = prepared; prepared = null; return result; }
        @Override public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder p) {}
        static void place(List<ChildPlacements.Entry> e, SceneNode n, int x, int y, int w, int h) {
            e.add(new ChildPlacements.Entry(n,new LayoutBox(x,y,Math.max(0,w),Math.max(0,h))));
        }
    }
    private static class Surface extends Arrangement {
        final ReadableSignal<Palette> colors;
        final boolean primary, ghost, muted;
        final SceneNode button;
        final int radius;
        Surface(ReadableSignal<Palette> p, boolean primary, boolean ghost, SceneNode button, int radius) { this(p,primary,ghost,button,radius,false); }
        Surface(ReadableSignal<Palette> p, boolean primary, boolean ghost, SceneNode button, int radius, boolean muted) {
            colors=p;this.primary=primary;this.ghost=ghost;this.button=button;this.radius=radius;this.muted=muted;
        }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode n) { return Collections.singletonList(colors); }
        @Override Prepared layout(SceneNode n, Constraints c, SceneChildMeasurer m) {
            SceneNode child=n.children().get(0); int w=Math.max(0,c.availableWidth-2), h=Math.max(0,c.availableHeight-2);
            MeasuredSize size=m.measureChild(child,new Constraints(w,h));
            int cw=child.width().mode==Size.Mode.FILL?w:size.width;
            int ch=child.height().mode==Size.Mode.FILL?h:size.height;
            return new Prepared(size.width+2,size.height+2,Collections.singletonList(new ChildPlacements.Entry(child,new LayoutBox(1,1,cw,ch))));
        }
        @Override public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry=g.get(n); Palette p=colors.get();
            boolean enabled=true,hovered=false,pressed=false,focus=false;
            if(button!=null) {
                for(SceneNode cursor=button;cursor!=null;cursor=cursor.parent()) enabled &= cursor.enabled();
                hovered=enabled && button.runtime()!=null && button.runtime().pointerRouter().hovered(g)==button;
                pressed=enabled && button.runtime()!=null && button.runtime().pointerRouter().pressed(button);
                focus=enabled && button.runtime()!=null && button.runtime().focusRouter().focused().get()==button;
            }
            int fill=primary?p.primary:(muted?p.panel:p.background);
            if(button!=null && (hovered||pressed)) fill=primary?p.primary:p.hover;
            if(button!=null && !enabled) fill=p.panel;
            if(ghost && button!=null && !hovered && !pressed) fill=p.background;
            int edge=focus?p.accent:primary?fill:ghost?fill:p.border;
            ScenePaintShapes.rounded(paint,entry.box,entry.clip,edge,radius);
            ScenePaintShapes.rounded(paint,new LayoutBox(entry.box.x+1,entry.box.y+1,Math.max(0,entry.box.width-2),Math.max(0,entry.box.height-2)),entry.clip,fill,Math.max(0,radius-1));
        }
    }
    private static final class NavigationSurface extends Surface {
        final ReadableSignal<Boolean> selected;
        NavigationSurface(ReadableSignal<Palette> colors,ReadableSignal<Boolean> selected,SceneNode button){super(colors,false,true,button,7);this.selected=selected;}
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Arrays.asList(colors,selected);}
        @Override public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint){
            SceneGeometryView.Entry e=g.get(n);Palette p=colors.get();
            boolean focus=button.runtime()!=null&&button.runtime().focusRouter().focused().get()==button;
            boolean hover=button.runtime()!=null&&button.runtime().pointerRouter().hovered(g)==button;
            int fill=selected.get()?p.selected:hover?p.hover:p.panel;
            ScenePaintShapes.rounded(paint,e.box,e.clip,focus?p.accent:fill,7);
            ScenePaintShapes.rounded(paint,new LayoutBox(e.box.x+1,e.box.y+1,Math.max(0,e.box.width-2),Math.max(0,e.box.height-2)),e.clip,fill,6);
        }
    }
    private static final class SwitchSurface extends Arrangement {
        final ReadableSignal<Palette> colors;final ReadableSignal<Boolean> checked;final SceneNode button;
        SwitchSurface(ReadableSignal<Palette> colors,ReadableSignal<Boolean> checked,SceneNode button){this.colors=colors;this.checked=checked;this.button=button;}
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Arrays.asList(colors,checked);}
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m){m.measureChild(button,new Constraints(42,26));return new Prepared(44,28,Collections.singletonList(new ChildPlacements.Entry(button,new LayoutBox(1,1,42,26))));}
        @Override public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint){
            SceneGeometryView.Entry e=g.get(n);Palette p=colors.get();
            boolean focus=button.runtime()!=null&&button.runtime().focusRouter().focused().get()==button;
            ScenePaintShapes.rounded(paint,e.box,e.clip,focus?p.accent:checked.get()?p.primary:p.border,14);
            ScenePaintShapes.rounded(paint,new LayoutBox(e.box.x+1,e.box.y+1,Math.max(0,e.box.width-2),Math.max(0,e.box.height-2)),e.clip,checked.get()?p.primary:p.hover,13);
        }
    }
    static final class Columns extends Arrangement {
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            int gap=20, width=c.availableWidth, height=0;
            List<ChildPlacements.Entry> e=new ArrayList<>();
            if(n.children().size()==2 && width>=780) {
                int right=Math.min(340,Math.max(280,width/3)); int left=Math.max(0,width-gap-right);
                MeasuredSize a=m.measureChild(n.children().get(0),new Constraints(left,c.availableHeight));
                MeasuredSize b=m.measureChild(n.children().get(1),new Constraints(right,c.availableHeight));
                place(e,n.children().get(0),0,0,left,a.height);place(e,n.children().get(1),left+gap,0,right,b.height);height=Math.max(a.height,b.height);
            } else for(SceneNode child:n.children()) if(child.visible()) {
                MeasuredSize s=m.measureChild(child,new Constraints(width,c.availableHeight));place(e,child,0,height,width,s.height);height+=s.height+gap;
            }
            if(e.size()>0 && !(n.children().size()==2 && width>=780))height-=gap;
            return new Prepared(width,height,e);
        }
    }
    /** 本次布局的局部模式；由Workspace选择，子布局明确消费，不以子宽猜测。
     * 仅保存布局准备事实，不写业务Signal；视口变化沿正式测量失效路径重算。 */
    static final class NavigationLayout {
        static final int BREAKPOINT=760, SIDEBAR_WIDTH=210, HORIZONTAL_PADDING=12;
        boolean sidebar;
    }
    static final class Workspace extends Arrangement {
        final NavigationLayout navigation;
        Workspace(NavigationLayout navigation) { this.navigation=navigation; }
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();SceneNode nav=n.children().get(0),body=n.children().get(1);
            navigation.sidebar=c.availableWidth>=NavigationLayout.BREAKPOINT;
            if(navigation.sidebar) {
                int sidebar=NavigationLayout.SIDEBAR_WIDTH,bodyWidth=Math.max(0,c.availableWidth-sidebar);
                m.measureChild(nav,new Constraints(sidebar,c.availableHeight));m.measureChild(body,new Constraints(bodyWidth,c.availableHeight));
                place(e,nav,0,0,sidebar,c.availableHeight);place(e,body,sidebar,0,bodyWidth,c.availableHeight);
            } else {
                MeasuredSize ns=m.measureChild(nav,new Constraints(c.availableWidth,c.availableHeight));int nh=Math.min(ns.height,c.availableHeight);
                m.measureChild(body,new Constraints(c.availableWidth,Math.max(0,c.availableHeight-nh)));
                place(e,nav,0,0,c.availableWidth,nh);place(e,body,0,nh,c.availableWidth,Math.max(0,c.availableHeight-nh));
            }
            return new Prepared(c.availableWidth,c.availableHeight,e);
        }
    }
    static final class Navigation extends Arrangement {
        final NavigationLayout navigation;
        Navigation(NavigationLayout navigation) { this.navigation=navigation; }
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();int width=c.availableWidth;
            SceneNode brand=n.children().get(0),buttons=n.children().get(1),info=n.children().get(2);
            if(navigation.sidebar) {
                MeasuredSize bs=m.measureChild(brand,new Constraints(Math.max(0,width-32),c.availableHeight));
                MeasuredSize ns=m.measureChild(buttons,new Constraints(Math.max(0,width-NavigationLayout.HORIZONTAL_PADDING*2),c.availableHeight));
                MeasuredSize is=m.measureChild(info,new Constraints(Math.max(0,width-32),c.availableHeight));
                place(e,brand,16,16,width-32,bs.height);place(e,buttons,NavigationLayout.HORIZONTAL_PADDING,bs.height+40,width-NavigationLayout.HORIZONTAL_PADDING*2,ns.height);
                place(e,info,16,Math.max(bs.height+ns.height+64,c.availableHeight-is.height-18),width-32,is.height);
                return new Prepared(width,c.availableHeight,e);
            }
            // 窄工作区只展示分类导航；同一分类按钮仍由原逻辑树持有。
            m.measureChild(brand,new Constraints(0,0));m.measureChild(info,new Constraints(0,0));
            MeasuredSize ns=m.measureChild(buttons,new Constraints(Math.max(0,width-NavigationLayout.HORIZONTAL_PADDING*2),c.availableHeight));
            place(e,brand,0,0,0,0);place(e,buttons,NavigationLayout.HORIZONTAL_PADDING,10,width-NavigationLayout.HORIZONTAL_PADDING*2,ns.height);place(e,info,0,0,0,0);
            return new Prepared(width,ns.height+20,e);
        }
    }
    static final class NavigationButtons extends Arrangement {
        final NavigationLayout navigation;
        NavigationButtons(NavigationLayout navigation) { this.navigation=navigation; }
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            if(!navigation.sidebar)return new WrapRow(8).layout(n,c,m);
            List<ChildPlacements.Entry> e=new ArrayList<>();int y=0;
            for(SceneNode child:n.children())if(child.visible()){
                MeasuredSize s=m.measureChild(child,c);place(e,child,0,y,c.availableWidth,s.height);y+=s.height+8;
            }
            return new Prepared(c.availableWidth,Math.max(0,y-8),e);
        }
    }
    static final class WrapRow extends Arrangement {
        final int gap;
        WrapRow(int gap) { this.gap=gap; }
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();int x=0,y=0,line=0,maxWidth=0;
            for(SceneNode child:n.children()) if(child.visible()) {
                MeasuredSize s=m.measureChild(child,new Constraints(c.availableWidth,c.availableHeight));int w=Math.min(c.availableWidth,s.width);
                if(x>0 && x+w>c.availableWidth){x=0;y+=line+gap;line=0;}
                place(e,child,x,y,w,s.height);x+=w+gap;maxWidth=Math.max(maxWidth,x-gap);line=Math.max(line,s.height);
            }
            return new Prepared(maxWidth,y+line,e);
        }
    }
    static final class Footer extends Arrangement {
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();SceneNode status=n.children().get(0),buttons=n.children().get(1);
            MeasuredSize bs=m.measureChild(buttons,new Constraints(c.availableWidth,c.availableHeight));
            if(c.availableWidth>=740) {
                int left=Math.max(0,c.availableWidth-bs.width-24);MeasuredSize ss=m.measureChild(status,new Constraints(left,c.availableHeight));
                place(e,status,0,0,left,ss.height);place(e,buttons,c.availableWidth-bs.width,0,bs.width,bs.height);
                return new Prepared(c.availableWidth,Math.max(ss.height,bs.height),e);
            }
            MeasuredSize ss=m.measureChild(status,c);place(e,status,0,0,c.availableWidth,ss.height);
            place(e,buttons,Math.max(0,c.availableWidth-bs.width),ss.height+10,bs.width,bs.height);
            return new Prepared(c.availableWidth,ss.height+10+bs.height,e);
        }
    }
    static final class Layers extends Arrangement {
        final boolean centered;
        Layers(boolean centered) { this.centered=centered; }
        @Override Prepared layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();
            for(SceneNode child:n.children()) if(child.visible()) {
                int maxWidth=centered?Math.max(0,Math.min(480,c.availableWidth-32)):c.availableWidth;
                MeasuredSize s=m.measureChild(child,new Constraints(maxWidth,c.availableHeight));
                int w=child.width().mode==Size.Mode.FILL?maxWidth:Math.min(maxWidth,s.width);
                int h=child.height().mode==Size.Mode.FILL?c.availableHeight:Math.min(c.availableHeight,s.height);
                place(e,child,centered?(c.availableWidth-w)/2:0,centered?(c.availableHeight-h)/2:0,w,h);
            }
            return new Prepared(c.availableWidth,c.availableHeight,e);
        }
    }
}
