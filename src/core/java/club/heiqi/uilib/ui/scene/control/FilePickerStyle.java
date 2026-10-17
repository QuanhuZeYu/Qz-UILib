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

/** 选择器局部的中性视觉词汇；装饰不创建输入目标、编辑状态或另一套布局事实。 */
final class FilePickerStyle {
    enum Role { PANEL, OUTLINE, PRIMARY, GHOST, ENTRY, FIELD }
    static final class Palette {
        final SceneTheme source;
        final club.heiqi.uilib.ui.scene.text.TextStyle textStyle;
        final int background,muted,surface,border,text,primary,onPrimary,hover;
        Palette(SceneTheme source) {
            this.source=source;
            club.heiqi.uilib.ui.scene.text.TextStyle declared=source.textStyle;
            // 默认界面文字使用随包无衬线；显式字族/候选、字号、字重及其动态变化仍由调用方决定。
            textStyle=declared.candidates()!=null||!"default".equals(declared.font)?declared:
                    new club.heiqi.uilib.ui.scene.text.TextStyle("uilib-ui",declared.logicalEm,declared.color,
                            club.heiqi.uilib.ui.scene.text.font.UiFontSources.sans(),declared.effects,declared.decoration,
                            declared.fontStyle,declared.baselineShift,declared.letterSpacing,declared.highlightColor,
                            declared.defaultHighlightColor,declared.script);
            int color=source.panel;
            boolean dark=((color>>>16&255)*299+(color>>>8&255)*587+(color&255)*114)<128000;
            background=dark?0xff09090b:0xffffffff;
            surface=dark?0xff18181b:0xfffafafa;
            border=dark?0xff27272a:0xffe4e4e7;
            text=dark?0xfffafafa:0xff18181b;
            muted=dark?0xffa1a1aa:0xff71717a;
            primary=text;onPrimary=background;
            hover=dark?0xff27272a:0xfff4f4f5;
        }
        SceneTheme editorTheme() {
            return new SceneTheme(textStyle.withColor(text),0,0,0x409494a3,0,text,0,muted,primary,onPrimary,11,8);
        }
        SceneTheme theme(float scale,int color,int padding) {
            return new SceneTheme(textStyle.withEm(textStyle.logicalEm*scale).withColor(color),
                    0,0,0,0,0,0,muted,primary,onPrimary,padding,8);
        }
    }
    private final ScenePageContext page;
    final club.heiqi.uilib.ui.reactive.Owner scope;
    final ReadableSignal<Palette> colors;
    FilePickerStyle(ScenePageContext page,ReadableSignal<SceneTheme> theme) {
        this.page=page;
        scope=page.scope().child();
        colors=page.domain().computed(scope,()->new Palette(theme.get()));
    }
    static SceneNode column(int gap){return new SceneNode().size(Size.fill(1),Size.natural()).gap(gap);}
    static SceneNode row(int gap){return column(gap).axis(SceneNode.Axis.ROW).control(new Flow(true));}
    SceneNode label(ReadableSignal<String> text,float scale,boolean muted) {
        return SceneLabel.create(page,text,()->colors.get().theme(scale,muted?colors.get().muted:colors.get().text,0));
    }
    SceneNode ellipsis(ReadableSignal<String> text,float scale,boolean muted) {
        return SceneLabel.create(page,text,()->colors.get().theme(scale,muted?colors.get().muted:colors.get().text,0),SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
    }
    SceneNode title(String text) {
        return SceneLabel.create(page,()->text,()->colors.get().theme(1.4f,colors.get().text,0)
                .withTextStyle(colors.get().textStyle.withEm(colors.get().textStyle.logicalEm*1.4f)
                        .withColor(colors.get().text).withFontStyle(new FontStyle(600,FontStyle.Posture.NORMAL))));
    }
    SceneNode icon(SceneIconButton.Symbol symbol) {
        return SceneIconButton.graphic(page,()->symbol,()->colors.get().theme(1,colors.get().muted,0))
                .bind(NodeBinding.value(()->Math.round(colors.get().source.textStyle.logicalEm*1.25f),
                        (n,s)->n.size(Size.fixed(s),Size.fixed(s))));
    }
    SceneNode action(String text,SceneIconButton.Symbol symbol,Runnable action,Role role) {
        SceneNode button=SceneButton.createWithIcon(page,()->text,()->colors.get().theme(1,
                role==Role.PRIMARY?colors.get().onPrimary:colors.get().text,9),action,SceneTextPolicy.ELLIPSIS_SINGLE_LINE,
                symbol==null?null:()->symbol).size(Size.natural(),Size.natural());
        return column(0).size(Size.natural(),Size.natural()).control(new Surface(colors,role,button,()->false)).add(button);
    }
    SceneNode entry(ReadableSignal<FilePickerDirectory.Entry> entry,ReadableSignal<Boolean> selected,Runnable activate) {
        SceneNode button=SceneButton.createWithIcon(page,()->entry.get().name(),()->colors.get().theme(1,colors.get().text,10),
                activate,SceneTextPolicy.ELLIPSIS_SINGLE_LINE,()->entry.get().directory?SceneIconButton.Symbol.FOLDER:SceneIconButton.Symbol.FILE)
                .size(Size.fill(1),Size.natural());
        return column(0).control(new Surface(colors,Role.ENTRY,button,selected)).add(button);
    }
    SceneNode field(SceneNode editor) {
        SceneNode scrollbar=SceneScrollbar.create(page,SceneScrollViewport.of(page,editor,SceneNode.Axis.ROW),
                ()->new SceneScrollbar.Options().dimensions(4,24,1).colors(0,colors.get().border,colors.get().muted),
                ()->colors.get().editorTheme()).size(Size.fill(1),Size.natural());
        // 显式复用同一编辑视口，替代自动条；条位于字段底部，不覆盖路径文字。
        return column(0).size(Size.fill(1),Size.natural()).control(new Surface(colors,Role.FIELD,editor,()->false))
                .add(editor).add(scrollbar);

    }
    SceneNode line() {return column(0).size(Size.fill(1),Size.fixed(1)).bind(NodeBinding.value(colors,(n,p)->n.background(p.border)));}
    SceneNode centered(SceneNode panel) {
        panel.bind(NodeBinding.value(colors,(n,p)->n.size(Size.fill(1).bounds(0,Math.round(p.source.textStyle.logicalEm*58)),
                Size.fill(1).bounds(0,Math.round(p.source.textStyle.logicalEm*46)))));
        return column(0).size(Size.fill(1),Size.fill(1)).control(new Center()).add(panel);
    }
    static class Flow implements SceneControl {
        final boolean centered;
        Flow(){this(false);} Flow(boolean centered){this.centered=centered;}
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
    private static final class Center extends Flow {
        public ChildPlacements arrange(SceneNode n,LayoutBox box,SceneChildMeasurer children){
            SceneNode child=children.flowChildren().get(0);
            int w=child.width().constrain(Math.max(0,box.width-n.padding()*2));
            int h=child.height().constrain(Math.max(0,box.height-n.padding()*2));
            children.measureChild(child,new Constraints(w,h));
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(child,
                    new LayoutBox((box.width-w)/2,(box.height-h)/2,w,h))));
        }
    }
    static class Surface extends Flow {
        private final ReadableSignal<Palette> colors;
        private final ReadableSignal<Boolean> selected;
        private final SceneNode target;
        private final Role role;
        Surface(ReadableSignal<Palette> colors,Role role,SceneNode target,ReadableSignal<Boolean> selected){
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
            int edge=focused?p.muted:role==Role.OUTLINE||role==Role.FIELD||role==Role.PANEL?p.border:fill;
            if(role==Role.PANEL){
                for(int spread=6;spread>=2;spread-=2)ScenePaintShapes.rounded(paint,
                        new LayoutBox(e.box.x-spread,e.box.y-spread/2,e.box.width+spread*2,e.box.height+spread*2),e.clip,0x08000000,12+spread);
            }
            int radius=role==Role.PANEL?12:7;
            ScenePaintShapes.rounded(paint,e.box,e.clip,edge,radius);
            ScenePaintShapes.rounded(paint,new LayoutBox(e.box.x+1,e.box.y+1,Math.max(0,e.box.width-2),Math.max(0,e.box.height-2)),e.clip,fill,radius-1);
        }
    }
}
