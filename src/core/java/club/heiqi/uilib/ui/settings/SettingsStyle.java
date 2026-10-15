package club.heiqi.uilib.ui.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
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
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.settings.SettingsLayout.Arrangement;
import club.heiqi.uilib.ui.settings.SettingsLayout.Result;

/** 设置页的视觉词汇；输入、选区、焦点与命中继续由标准控件和正式运行时维护。 */
final class SettingsStyle {
    private SettingsStyle() {}
    static final class Palette {
        final boolean dark;
        final int background,panel,text,muted,border,hover,accent,selected,primary,onPrimary,error;
        Palette(boolean dark) {
            this.dark=dark;
            background=dark?0xff020617:0xffffffff;
            panel=dark?0xff0f172a:0xfff8fafc;
            text=dark?0xfff8fafc:0xff0f172a;
            muted=dark?0xff94a3b8:0xff64748b;
            border=dark?0xff273044:0xffe2e8f0;
            hover=dark?0xff172033:0xfff1f5f9;
            accent=dark?0xff94a3b8:0xff64748b;
            selected=dark?0xff1e293b:0xfff1f5f9;
            primary=dark?0xffe2e8f0:0xff0f172a;
            onPrimary=dark?0xff0f172a:0xfff8fafc;
            error=dark?0xfff87171:0xffdc2626;
        }
        SceneTheme theme(int em,int color,int padding,boolean transparent) {
            return theme(SettingsTypography.style(em,color,400),padding,transparent);
        }
        SceneTheme theme(TextStyle style,int padding,boolean transparent) {
            return new SceneTheme(style,transparent?0:background,transparent?0:border,
                    transparent?0:selected,transparent?0:selected,transparent?0:accent,
                    transparent?0:panel,muted,primary,onPrimary,padding,8);
        }
    }
    static SceneNode column(int gap){return new SceneNode().size(Size.fill(1),Size.natural()).gap(gap);}
    static SceneNode row(int gap){return column(gap).axis(SceneNode.Axis.ROW);}
    static SceneNode label(ScenePageContext ctx,ReadableSignal<String> text,ReadableSignal<Palette> p,int em,boolean muted) {
        return SceneLabel.create(ctx,text,() -> p.get().theme(em,muted?p.get().muted:p.get().text,0,true));
    }
    static SceneNode heading(ScenePageContext ctx,ReadableSignal<String> text,ReadableSignal<Palette> p,int em) {
        return SceneLabel.create(ctx,text,() -> p.get().theme(SettingsTypography.style(em,p.get().text,600),0,true));
    }
    static final class Button {
        final SceneNode view,target;Button(SceneNode view,SceneNode target){this.view=view;this.target=target;}
    }
    static Button button(ScenePageContext ctx,ReadableSignal<String> text,ReadableSignal<Palette> p,
            Runnable action,boolean primary,boolean ghost) {
        SceneNode control=SceneButton.create(ctx,text,
                () -> p.get().theme(SettingsTypography.style(14,primary?p.get().onPrimary:p.get().text,500),8,true),
                action,SceneTextPolicy.WRAP).size(Size.natural(),Size.natural());
        return new Button(column(0).size(Size.natural(),Size.natural())
                .control(new Surface(p,primary,ghost,control,8,false)).add(control),control);
    }
    static SceneNode navigationButton(ScenePageContext ctx,ReadableSignal<String> text,ReadableSignal<Boolean> selected,
            ReadableSignal<Palette> p,Runnable action) {
        SceneNode button=SceneButton.create(ctx,text,() -> p.get().theme(
                SettingsTypography.style(14,p.get().text,selected.get()?600:400),9,true),action)
                .size(Size.fill(1),Size.natural());
        return column(0).control(new NavigationSurface(p,selected,button)).add(button);
    }
    static SceneNode toggle(ScenePageContext ctx,ReadableSignal<Boolean> checked,ReadableSignal<Palette> p,Runnable action) {
        SceneNode button=SceneButton.create(ctx,() -> checked.get()?"关闭接管原版文字绘制":"开启接管原版文字绘制",
                () -> p.get().theme(14,0,0,true),action).size(Size.fixed(42),Size.fixed(26));
        return column(0).size(Size.fixed(44),Size.fixed(28)).control(new SwitchSurface(p,checked,button)).add(button);
    }
    static SceneNode surface(SceneNode content,ReadableSignal<Palette> p,boolean muted) {
        return column(0).control(new Surface(p,false,false,null,8,muted)).add(content);
    }
    static SceneNode field(SceneNode editor,ReadableSignal<Palette> p) {
        return column(0).control(new Surface(p,false,false,editor,8,false)).add(editor);
    }
    static SceneNode card(SceneNode content,ReadableSignal<Palette> p){return surface(content.padding(24),p,false);}
    static SceneNode line(ReadableSignal<Palette> p){return column(0).size(Size.fill(1),Size.fixed(1)).bind(NodeBinding.value(p,(n,v) -> n.background(v.border)));}
    static SceneNode themeChoice(ScenePageContext ctx,String label,boolean dark,ReadableSignal<Boolean> selected,
            ReadableSignal<Palette> p,Runnable action) {
        SceneNode button=SceneButton.create(ctx,() -> label,() -> p.get().theme(14,p.get().text,12,true),action)
                .size(Size.fill(1),Size.natural());
        SceneNode preview=column(0).size(Size.fill(1),Size.fixed(112)).control(new ThemePreview(dark,p,selected));
        // 只扩展标准按钮的内容与装饰；整张选项仍只有一个动作与键盘焦点。
        return button.control(new NavigationSurface(p,selected,button))
                .add(column(8).add(preview).add(label(ctx,() -> label,p,14,false)));
    }
    private static class Surface extends Arrangement {
        final ReadableSignal<Palette> colors;final boolean primary,ghost,muted;
        final SceneNode button;final int radius;
        Surface(ReadableSignal<Palette> colors,boolean primary,boolean ghost,SceneNode button,int radius,boolean muted) {
            this.colors=colors;this.primary=primary;this.ghost=ghost;this.button=button;this.radius=radius;this.muted=muted;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Collections.singletonList(colors);}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            SceneNode child=m.flowChildren().get(0);int w=Math.max(0,c.availableWidth-2),h=Math.max(0,c.availableHeight-2);
            MeasuredSize s=m.measureChild(child,new Constraints(w,h));
            return new Result(s.width+2,s.height+2,Collections.singletonList(new ChildPlacements.Entry(child,new LayoutBox(1,1,
                    child.width().mode==Size.Mode.FILL?w:s.width,child.height().mode==Size.Mode.FILL?h:s.height))));
        }
        boolean enabled(){return button==null||button.runtime()==null||button.runtime().interactive(button);}
        boolean focused(){return button!=null&&button.runtime()!=null&&button.runtime().focusRouter().focused().get()==button;}
        boolean hovered(SceneGeometryView g){return button!=null&&button.runtime()!=null&&button.runtime().pointerRouter().hovered(g)==button;}
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint) {
            SceneGeometryView.Entry e=g.get(n);Palette p=colors.get();
            int fill=primary?p.primary:muted?p.panel:p.background;
            if(hovered(g)&&enabled())fill=primary?(p.dark?0xffcbd5e1:0xff263247):p.hover;
            if(!enabled())fill=p.panel;
            int edge=focused()?p.accent:primary?fill:ghost?fill:p.border;
            rounded(paint,e,edge,fill,radius);
        }
    }
    private static final class NavigationSurface extends Surface {
        final ReadableSignal<Boolean> selected;
        NavigationSurface(ReadableSignal<Palette> p,ReadableSignal<Boolean> selected,SceneNode button){super(p,false,true,button,8,false);this.selected=selected;}
        public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Arrays.asList(colors,selected);}
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint) {
            Palette p=colors.get();int fill=selected.get()?p.selected:hovered(g)?p.hover:p.background;
            rounded(paint,g.get(n),focused()?p.accent:fill,fill,8);
        }
    }
    private static void rounded(PaintPlanBuilder p,SceneGeometryView.Entry e,int edge,int fill,int radius) {
        ScenePaintShapes.rounded(p,e.box,e.clip,edge,radius);
        ScenePaintShapes.rounded(p,new LayoutBox(e.box.x+1,e.box.y+1,Math.max(0,e.box.width-2),Math.max(0,e.box.height-2)),e.clip,fill,Math.max(0,radius-1));
    }
    private static final class SwitchSurface extends Arrangement {
        final ReadableSignal<Palette> colors;final ReadableSignal<Boolean> checked;final SceneNode button;
        SwitchSurface(ReadableSignal<Palette> p,ReadableSignal<Boolean> checked,SceneNode button){colors=p;this.checked=checked;this.button=button;}
        public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Arrays.asList(colors,checked);}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m){m.measureChild(button,new Constraints(42,26));return new Result(44,28,Collections.singletonList(new ChildPlacements.Entry(button,new LayoutBox(1,1,42,26))));}
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder paint){
            SceneGeometryView.Entry e=g.get(n);Palette p=colors.get();
            boolean focus=button.runtime()!=null&&button.runtime().focusRouter().focused().get()==button;
            rounded(paint,e,focus?p.accent:checked.get()?p.primary:p.border,checked.get()?p.primary:p.hover,14);
            ScenePaintShapes.rounded(paint,new LayoutBox(e.box.x+(checked.get()?22:4),e.box.y+4,20,20),e.clip,checked.get()?p.onPrimary:p.muted,10);
        }
    }
    private static final class ThemePreview extends Arrangement {
        final boolean dark;final ReadableSignal<Palette> colors;final ReadableSignal<Boolean> selected;
        ThemePreview(boolean dark,ReadableSignal<Palette> p,ReadableSignal<Boolean> selected){this.dark=dark;colors=p;this.selected=selected;}
        public List<ReadableSignal<?>> paintDependencies(SceneNode n){return Arrays.asList(colors,selected);}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m){return new Result(c.availableWidth,112,Collections.emptyList());}
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder p){
            SceneGeometryView.Entry e=g.get(n);Palette c=new Palette(dark);LayoutBox b=e.box;
            ScenePaintShapes.rounded(p,b,e.clip,selected.get()?colors.get().text:colors.get().border,8);
            LayoutBox inside=new LayoutBox(b.x+3,b.y+3,Math.max(0,b.width-6),Math.max(0,b.height-6));
            ScenePaintShapes.rounded(p,inside,e.clip,c.background,6);
            ScenePaintShapes.rounded(p,new LayoutBox(b.x+12,b.y+14,36,84),e.clip,c.panel,4);
            int x=b.x+60,w=Math.max(0,b.width-76);
            ScenePaintShapes.rounded(p,new LayoutBox(x,b.y+18,w,12),e.clip,c.selected,3);
            ScenePaintShapes.rounded(p,new LayoutBox(x,b.y+40,w,22),e.clip,c.border,4);
            ScenePaintShapes.rounded(p,new LayoutBox(x,b.y+72,Math.max(0,w/2),12),e.clip,c.primary,3);
        }
    }
}
