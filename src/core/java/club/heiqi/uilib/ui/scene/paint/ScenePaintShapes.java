package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 纯装饰沿普通 RECT 展开；角点的像素 coverage 不改变布局或裁剪事实。 */
public final class ScenePaintShapes {
    private static final int SAMPLES=4;
    private ScenePaintShapes() {}
    public static void rounded(PaintPlanBuilder paint, LayoutBox box, LayoutBox clip, int color, int requested) {
        if(box.width<=0||box.height<=0||(color>>>24)==0)return;
        int radius=Math.max(0,Math.min(requested,Math.min(box.width,box.height)/2));
        paint.add(PaintCommand.rect(new LayoutBox(box.x,box.y+radius,box.width,box.height-radius*2),clip,color));
        for(int y=0;y<radius;y++) {
            double far=radius-y,near=radius-y-1;
            int full=(int)Math.ceil(radius-Math.sqrt(Math.max(0,(double)radius*radius-far*far)));
            int first=(int)Math.floor(radius-Math.sqrt(Math.max(0,(double)radius*radius-near*near)));
            if(box.width>full*2) {
                paint.add(PaintCommand.rect(new LayoutBox(box.x+full,box.y+y,box.width-full*2,1),clip,color));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+full,box.y+box.height-y-1,box.width-full*2,1),clip,color));
            }
            for(int x=first;x<full;x++) {
                int coverage=0;
                for(int sy=0;sy<SAMPLES;sy++)for(int sx=0;sx<SAMPLES;sx++) {
                    double dx=radius-x-(sx+.5)/SAMPLES,dy=radius-y-(sy+.5)/SAMPLES;
                    if(dx*dx+dy*dy<=(double)radius*radius)coverage++;
                }
                int alpha=((color>>>24)*coverage+SAMPLES*SAMPLES/2)/(SAMPLES*SAMPLES);
                if(alpha==0)continue;int edge=(color&0xffffff)|(alpha<<24);
                paint.add(PaintCommand.rect(new LayoutBox(box.x+x,box.y+y,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+box.width-x-1,box.y+y,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+x,box.y+box.height-y-1,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+box.width-x-1,box.y+box.height-y-1,1,1),clip,edge));
            }
        }
    }
}
