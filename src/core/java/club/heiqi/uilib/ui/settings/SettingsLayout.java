package club.heiqi.uilib.ui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 设置工作区的正式布局扩展；页面只提供具名区域，不依赖内部子槽次序。 */
final class SettingsLayout {
    static final int COMPACT_WIDTH=760;
    private SettingsLayout() {}
    static SceneNode workspace(ReadableSignal<Boolean> compact,SceneNode toolbar,SceneNode heading,
            SceneNode navigation,SceneNode content,SceneNode footer) {
        return SettingsStyle.column(0).size(Size.fill(1),Size.fill(1))
                .control(new Workspace(compact,toolbar,heading,navigation,content,footer))
                .add(toolbar).add(heading).add(navigation).add(content).add(footer);
    }
    static SceneNode form(SceneNode content) {
        return SettingsStyle.column(0).control(new Form(content)).add(content);
    }
    static final class Result {
        final MeasuredSize size;final ChildPlacements children;
        Result(int width,int height,List<ChildPlacements.Entry> entries) {
            size=new MeasuredSize(Math.max(0,width),Math.max(0,height));children=new ChildPlacements(entries);
        }
    }
    abstract static class Arrangement implements SceneControl {
        abstract Result layout(SceneNode node,Constraints limits,SceneChildMeasurer children);
        public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer t){return new MeasuredSize(0,0);}
        public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer t,SceneChildMeasurer m){return layout(n,c,m).size;}
        public ChildPlacements arrange(SceneNode n,LayoutBox b,SceneChildMeasurer m) {
            int pad=n.padding();Result result=layout(n,new Constraints(Math.max(0,b.width-pad*2),Math.max(0,b.height-pad*2)),m);
            if(pad==0)return result.children;
            List<ChildPlacements.Entry> placed=new ArrayList<>();
            for(ChildPlacements.Entry e:result.children.entries()) {
                LayoutBox box=e.relativeBox;place(placed,e.child,pad+box.x,pad+box.y,box.width,box.height);
            }
            return new ChildPlacements(placed);
        }
        public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder p) {}
        static void place(List<ChildPlacements.Entry> entries,SceneNode n,int x,int y,int w,int h) {
            entries.add(new ChildPlacements.Entry(n,new LayoutBox(x,y,Math.max(0,w),Math.max(0,h))));
        }
    }
    private static final class Workspace extends Arrangement {
        final ReadableSignal<Boolean> compact;
        final SceneNode toolbar,heading,navigation,content,footer;
        Workspace(ReadableSignal<Boolean> compact,SceneNode toolbar,SceneNode heading,SceneNode navigation,SceneNode content,SceneNode footer) {
            this.compact=compact;this.toolbar=toolbar;this.heading=heading;this.navigation=navigation;this.content=content;this.footer=footer;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode n){return Collections.singletonList(compact);}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            boolean small=compact.get();int inset=small?16:32,width=Math.min(1200,Math.max(0,c.availableWidth-inset*2));
            int x=(c.availableWidth-width)/2;
            int th=m.measureChild(toolbar,new Constraints(c.availableWidth,c.availableHeight)).height;
            int hh=m.measureChild(heading,new Constraints(width,c.availableHeight)).height;
            int fh=m.measureChild(footer,new Constraints(width,c.availableHeight)).height;
            int y=th+(small?16:24),start=y+hh+24,bottom=Math.max(start,c.availableHeight-fh-16);
            List<ChildPlacements.Entry> e=new ArrayList<>();
            place(e,toolbar,0,0,c.availableWidth,th);place(e,heading,x,y,width,hh);
            if(small) {
                int nh=m.measureChild(navigation,new Constraints(width,Math.max(0,bottom-start))).height;
                place(e,navigation,x,start,width,nh);start+=nh+20;
                m.measureChild(content,new Constraints(width,Math.max(0,bottom-start)));
                place(e,content,x,start,width,Math.max(0,bottom-start));
            } else {
                int navWidth=184,gap=48,body=Math.max(0,width-navWidth-gap),h=Math.max(0,bottom-start);
                m.measureChild(navigation,new Constraints(navWidth,h));m.measureChild(content,new Constraints(body,h));
                place(e,navigation,x,start,navWidth,h);place(e,content,x+navWidth+gap,start,body,h);
            }
            place(e,footer,x,Math.max(bottom,c.availableHeight-fh-16),width,fh);
            return new Result(c.availableWidth,c.availableHeight,e);
        }
    }
    private static final class Form extends Arrangement {
        final SceneNode content;Form(SceneNode content){this.content=content;}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            int width=Math.min(720,c.availableWidth);MeasuredSize s=m.measureChild(content,new Constraints(width,c.availableHeight));
            return new Result(c.availableWidth,s.height,Collections.singletonList(new ChildPlacements.Entry(content,new LayoutBox(0,0,width,s.height))));
        }
    }
    static final class WrapRow extends Arrangement {
        final int gap;WrapRow(int gap){this.gap=gap;}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();int x=0,y=0,line=0,width=0;
            for(SceneNode child:m.flowChildren())if(child.visible()) {
                MeasuredSize s=m.measureChild(child,new Constraints(c.availableWidth,c.availableHeight));
                if(x>0&&x+s.width>c.availableWidth){x=0;y+=line+gap;line=0;}
                place(e,child,x,y,s.width,s.height);x+=s.width+gap;width=Math.max(width,x-gap);line=Math.max(line,s.height);
            }
            return new Result(width,y+line,e);
        }
    }
    static final class Footer extends Arrangement {
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            SceneNode status=m.flowChildren().get(0),buttons=m.flowChildren().get(1);
            MeasuredSize bs=m.measureChild(buttons,c);List<ChildPlacements.Entry> e=new ArrayList<>();
            int remaining=Math.max(0,c.availableWidth-bs.width-32);
            MeasuredSize ss=m.measureChild(status,new Constraints(c.availableWidth>=780?remaining:c.availableWidth,c.availableHeight));
            if(c.availableWidth>=780){place(e,status,0,0,remaining,ss.height);place(e,buttons,c.availableWidth-bs.width,0,bs.width,bs.height);return new Result(c.availableWidth,Math.max(ss.height,bs.height),e);}
            place(e,status,0,0,c.availableWidth,ss.height);place(e,buttons,0,ss.height+12,bs.width,bs.height);
            return new Result(c.availableWidth,ss.height+12+bs.height,e);
        }
    }
    static final class SourceRow extends Arrangement {
        final SceneNode info,actions;SourceRow(SceneNode info,SceneNode actions){this.info=info;this.actions=actions;}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();MeasuredSize buttons=m.measureChild(actions,c);
            boolean stacked=c.availableWidth<480;
            int width=stacked?c.availableWidth:Math.max(0,c.availableWidth-buttons.width-16);
            MeasuredSize label=m.measureChild(info,new Constraints(width,c.availableHeight));
            place(e,info,0,0,width,label.height);
            place(e,actions,stacked?0:c.availableWidth-buttons.width,stacked?label.height+8:Math.max(0,(label.height-buttons.height)/2),buttons.width,buttons.height);
            return new Result(c.availableWidth,stacked?label.height+8+buttons.height:Math.max(label.height,buttons.height),e);
        }
    }
    static final class Layers extends Arrangement {
        final boolean centered;Layers(boolean centered){this.centered=centered;}
        Result layout(SceneNode n,Constraints c,SceneChildMeasurer m) {
            List<ChildPlacements.Entry> e=new ArrayList<>();
            for(SceneNode child:m.flowChildren())if(child.visible()) {
                int maxWidth=centered?Math.max(0,Math.min(480,c.availableWidth-32)):c.availableWidth;
                MeasuredSize s=m.measureChild(child,new Constraints(maxWidth,c.availableHeight));
                int w=child.width().mode==Size.Mode.FILL?maxWidth:Math.min(maxWidth,s.width);
                int h=child.height().mode==Size.Mode.FILL?c.availableHeight:Math.min(c.availableHeight,s.height);
                place(e,child,centered?(c.availableWidth-w)/2:0,centered?(c.availableHeight-h)/2:0,w,h);
            }
            return new Result(c.availableWidth,c.availableHeight,e);
        }
    }
}
