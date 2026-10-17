package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.UiFontSources;

/** 当前提示的有界纯值呈现；在帧准备中度量/定位，paint只写普通PaintCommand。 */
final class SceneTooltipFrame {
    final LayoutBox anchor,box,viewport;
    final SceneTooltip.Side side;
    final TextLayout body,shortcut;
    final int color,padding,gap,arrow;
    SceneTooltipFrame(LayoutBox anchor,LayoutBox box,LayoutBox viewport,SceneTooltip.Side side,TextLayout body,TextLayout shortcut,int color,int padding,int gap,int arrow){
        this.anchor=anchor;this.box=box;this.viewport=viewport;this.side=side;this.body=body;this.shortcut=shortcut;this.color=color;this.padding=padding;this.gap=gap;this.arrow=arrow;
    }
    boolean same(SceneTooltipFrame other){return other!=null&&same(box,other.box)&&same(anchor,other.anchor)&&same(viewport,other.viewport)&&side==other.side&&body==other.body&&shortcut==other.shortcut&&color==other.color&&padding==other.padding&&gap==other.gap&&arrow==other.arrow;}
    private static boolean same(LayoutBox a,LayoutBox b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    void paint(PaintPlanBuilder target){
        int radius=Math.min(club.heiqi.uilib.ui.scene.control.SceneAppearance.CONTROL_RADIUS,Math.min(box.width,box.height)/2);
        ScenePaintShapes.rounded(target,box,viewport,color,radius);
        paintArrow(target);
        int bodyHeight=body.height;
        LayoutBox inner=new LayoutBox(box.x+padding,box.y+padding,Math.max(0,box.width-padding*2),Math.max(0,box.height-padding*2));
        target.add(PaintCommand.text(inner,inner.intersect(viewport),body));
        if(shortcut!=null){
            LayoutBox key=new LayoutBox(inner.x,inner.y+bodyHeight+gap,inner.width,Math.max(0,inner.height-bodyHeight-gap));
            target.add(PaintCommand.text(key,key.intersect(viewport),shortcut));
        }
    }
    private void paintArrow(PaintPlanBuilder target){
        if(arrow==0)return;
        boolean vertical=side==SceneTooltip.Side.TOP||side==SceneTooltip.Side.BOTTOM;
        int center=vertical?clamp(anchor.x+anchor.width/2,box.x+arrow,box.x+box.width-arrow)
                :clamp(anchor.y+anchor.height/2,box.y+arrow,box.y+box.height-arrow);
        for(int row=0;row<arrow;row++){
            int half=arrow-row;
            LayoutBox strip;
            if(side==SceneTooltip.Side.TOP)strip=new LayoutBox(center-half,box.y+box.height+row,half*2,1);
            else if(side==SceneTooltip.Side.BOTTOM)strip=new LayoutBox(center-half,box.y-row-1,half*2,1);
            else if(side==SceneTooltip.Side.LEFT)strip=new LayoutBox(box.x+box.width+row,center-half,1,half*2);
            else strip=new LayoutBox(box.x-row-1,center-half,1,half*2);
            target.add(PaintCommand.rect(strip,viewport,color));
        }
    }
    static int clamp(long value,int min,int max){return (int)Math.max(min,Math.min((long)Math.max(min,max),value));}
    static final class Layout {
        private SceneTooltip.Content content;
        private TextStyle style;
        private TextPolicy.Direction direction;
        private int width=-1;
        private long metrics=-1;
        private TextLayout body,shortcut;
        SceneTooltipFrame prepare(LayoutBox anchor,LayoutBox viewport,SceneTooltip.Hint hint,SceneTextMeasurer text,long revision){
            SceneTooltip.Options o=hint.options;SceneTheme theme=hint.theme;
            TextStyle next=UiFontSources.style(theme.textStyle).withColor(theme.tooltipText);
            int padding=Math.min(Math.max(4,Math.round(next.logicalEm*.4f)),Math.min(viewport.width,viewport.height)/2);
            int available=Math.max(0,Math.min(viewport.width-padding*2,(int)Math.ceil(o.maxWidthEm*next.logicalEm)));
            if(body==null||!hint.content.same(content)||!next.equals(style)||available!=width||metrics!=revision||direction!=o.direction){
                content=hint.content;style=next;width=available;metrics=revision;direction=o.direction;
                TextPolicy policy=new TextPolicy(direction,TextPolicy.Wrap.EMERGENCY,"und",0,0);
                body=content.plainText==null?text.layout(content.text.resolve(style).text,available,policy):text.layout(content.plainText,style,available,policy);
                shortcut=content.shortcut.isEmpty()?null:text.layout(content.shortcut,style.withEm(style.logicalEm*.9f),available,policy);
            }
            int gap=shortcut==null?0:Math.max(2,padding/2);
            int w=Math.min(viewport.width,Math.max(body.width,shortcut==null?0:shortcut.width)+padding*2);
            int h=Math.min(viewport.height,body.height+(shortcut==null?0:shortcut.height+gap)+padding*2);
            int arrow=o.arrow?Math.max(2,Math.min(12,Math.round(next.logicalEm*.25f))):0;
            SceneTooltip.Side actual=o.side;
            long[] point=place(anchor,w,h,actual,o,body);
            if(!fits(point,w,h,viewport)){
                SceneTooltip.Side opposite=opposite(actual);long[] alternate=place(anchor,w,h,opposite,o,body);
                if(fits(alternate,w,h,viewport)||room(anchor,viewport,opposite)>room(anchor,viewport,actual)){actual=opposite;point=alternate;}
            }
            LayoutBox box=new LayoutBox(clamp(point[0],viewport.x,viewport.x+viewport.width-w),clamp(point[1],viewport.y,viewport.y+viewport.height-h),w,h);
            return new SceneTooltipFrame(anchor,box,viewport,actual,body,shortcut,theme.tooltip,padding,gap,arrow);
        }
        private static boolean fits(long[] p,int w,int h,LayoutBox v){return p[0]>=v.x&&p[1]>=v.y&&p[0]+w<=(long)v.x+v.width&&p[1]+h<=(long)v.y+v.height;}
        private static long room(LayoutBox a,LayoutBox v,SceneTooltip.Side side){switch(side){case TOP:return (long)a.y-v.y;case BOTTOM:return (long)v.y+v.height-a.y-a.height;case LEFT:return (long)a.x-v.x;default:return (long)v.x+v.width-a.x-a.width;}}
        private static SceneTooltip.Side opposite(SceneTooltip.Side side){switch(side){case TOP:return SceneTooltip.Side.BOTTOM;case BOTTOM:return SceneTooltip.Side.TOP;case LEFT:return SceneTooltip.Side.RIGHT;default:return SceneTooltip.Side.LEFT;}}
        private static long[] place(LayoutBox a,int w,int h,SceneTooltip.Side side,SceneTooltip.Options o,TextLayout body){
            boolean vertical=side==SceneTooltip.Side.TOP||side==SceneTooltip.Side.BOTTOM;
            boolean rtl=o.direction==TextPolicy.Direction.RTL||o.direction==TextPolicy.Direction.AUTO&&!body.lines().isEmpty()&&body.lines().get(0).paragraphRtl;
            SceneTooltip.Align align=o.align;
            if(vertical&&rtl&&align!=SceneTooltip.Align.CENTER)align=align==SceneTooltip.Align.START?SceneTooltip.Align.END:SceneTooltip.Align.START;
            long cross=vertical?(long)a.x:(long)a.y;int span=vertical?a.width:a.height,size=vertical?w:h;
            cross+=align==SceneTooltip.Align.CENTER?(span-size)/2L:align==SceneTooltip.Align.END?(long)span-size:0;
            cross+=o.alignOffset;
            switch(side){case TOP:return new long[]{cross,(long)a.y-h-o.sideOffset};case BOTTOM:return new long[]{cross,(long)a.y+a.height+o.sideOffset};case LEFT:return new long[]{(long)a.x-w-o.sideOffset,cross};default:return new long[]{(long)a.x+a.width+o.sideOffset,cross};}
        }
    }
}
