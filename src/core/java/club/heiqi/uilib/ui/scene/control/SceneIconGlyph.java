package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 有限标准符号的 coverage 展开；只缓存形状/实际像素大小，颜色和节点不进入缓存。 */
final class SceneIconGlyph {
    private static final int SAMPLES=4;
    private static final Map<Long,int[]> MASKS=new LinkedHashMap<Long,int[]>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,int[]> entry) { return size()>64; }
    };
    private SceneIconGlyph() {}
    static void paint(SceneIconButton.Symbol symbol, LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color) {
        int side=Math.min(box.width,box.height);if(side<=0||(color>>>24)==0)return;
        if(clip!=null) {LayoutBox visible=box.intersect(clip);if(visible.width==0||visible.height==0)return;}
        long key=((long)symbol.ordinal()<<32)|(side&0xffffffffL);int[] spans;
        synchronized(MASKS) { spans=MASKS.get(key); }
        if(spans==null) { spans=raster(path(symbol),side);synchronized(MASKS){MASKS.put(key,spans);} }
        for(int i=0;i<spans.length;i+=5) {
            int alpha=((color>>>24)*spans[i+4]+SAMPLES*SAMPLES/2)/(SAMPLES*SAMPLES);
            if(alpha!=0)target.add(PaintCommand.rect(new LayoutBox(box.x+spans[i],box.y+spans[i+1],spans[i+2],spans[i+3]),clip,
                    (color&0xffffff)|(alpha<<24)));
        }
    }
    private static int[] raster(Path path,int side) {
        List<int[]> rectangles=new ArrayList<>();Map<Long,int[]> lastByKey=new LinkedHashMap<>();
        for(int y=0;y<side;y++) {
            int from=0,last=-1;
            for(int x=0;x<=side;x++) {
                int coverage=0;
                if(x<side)for(int sy=0;sy<SAMPLES;sy++)for(int sx=0;sx<SAMPLES;sx++)
                    if(path.contains((x+(sx+.5f)/SAMPLES)*24/side,(y+(sy+.5f)/SAMPLES)*24/side))coverage++;
                if(coverage!=last) {
                    if(last>0) append(rectangles,lastByKey,from,y,x-from,last);
                    from=x;last=coverage;
                }
            }
        }
        int[] result=new int[rectangles.size()*5];int index=0;
        for(int[] rectangle:rectangles)for(int value:rectangle)result[index++]=value;return result;
    }
    /** 同coverage的相邻条带是同一个矩形，合并不改变任意像素的alpha或绘制顺序。 */
    private static void append(List<int[]> rectangles,Map<Long,int[]> last,int x,int y,int width,int coverage) {
        long key=((long)x<<32)|((long)width<<8)|coverage;int[] previous=last.get(key);
        if(previous!=null&&previous[1]+previous[3]==y)previous[3]++;
        else {int[] rectangle={x,y,width,1,coverage};rectangles.add(rectangle);last.put(key,rectangle);}
    }
    private static Path path(SceneIconButton.Symbol symbol) {
        Path p=new Path();
        switch(symbol) {
            case STAR:case STAR_FILLED:
                p.polygon(symbol==SceneIconButton.Symbol.STAR_FILLED,12,2,15,8.5f,22,9.5f,17,14.5f,18.5f,22,12,18.2f,5.5f,22,7,14.5f,2,9.5f,9,8.5f);break;
            case ARCHIVE:
                p.polygon(false,4,8,20,8,20,21,4,21);p.polygon(false,3,4,21,4,21,8,3,8);
                p.line(12,11,12,17);p.line(9,14,12,17);p.line(12,17,15,14);break;
            case MAIL:
                p.polygon(false,3,5,21,5,21,19,3,19);p.line(3,6,12,13);p.line(12,13,21,6);break;
            case MAIL_OPEN:
                p.polygon(false,3,10,12,3,21,10,21,21,3,21);p.line(3,11,12,17);p.line(12,17,21,11);break;
            case SEARCH:
                p.circle(10,10,6.5f,1.8f);p.line(15,15,21,21);break;
            case CLOSE:p.line(6,6,18,18);p.line(18,6,6,18);break;
            case CHEVRON_LEFT:p.line(15,5,8,12);p.line(8,12,15,19);break;
            case CHEVRON_RIGHT:p.line(9,5,16,12);p.line(16,12,9,19);break;
            case INBOX:
                p.polygon(false,3,5,21,5,21,20,3,20);p.line(3,14,8,14);p.line(8,14,10,17);
                p.line(10,17,14,17);p.line(14,17,16,14);p.line(16,14,21,14);break;
            case ALL_MAIL:
                p.polygon(false,6,8,21,8,21,20,6,20);p.line(3,16,3,4);p.line(3,4,18,4);break;
            case THEME:p.circle(12,12,8,1.8f);p.circle(12,12,7,-1);break;
            case SORT:p.line(4,7,20,7);p.line(7,12,17,12);p.line(10,17,14,17);break;
            case CHECK:p.line(5,12,10,17);p.line(10,17,19,7);break;
            case MINUS:p.line(6,12,18,12);break;
            case MENU:p.line(4,6,20,6);p.line(4,12,20,12);p.line(4,18,20,18);break;
            case SLIDERS:
                p.line(4,6,20,6);p.line(4,12,20,12);p.line(4,18,20,18);
                p.circle(8,6,2,1.5f);p.circle(16,12,2,1.5f);p.circle(10,18,2,1.5f);break;
            case FILE:
                p.polygon(false,6,3,14,3,19,8,19,21,6,21);p.line(14,3,14,8);p.line(14,8,19,8);
                p.line(9,12,16,12);p.line(9,16,16,16);break;
            case FOLDER:
                p.polygon(false,3,5,9,5,12,8,21,8,21,20,3,20);break;
            case MONITOR:
                p.polygon(false,3,4,21,4,21,17,3,17);p.line(12,17,12,21);p.line(8,21,16,21);break;
            case CHECK_BOX:p.polygon(false,4,4,20,4,20,20,4,20);break;
            case TAG:p.polygon(false,3,3,11,3,21,13,13,21,3,11);p.circle(7,7,1,1.5f);break;
            case PEOPLE:p.circle(9,8,3,1.8f);p.circle(17,9,2.5f,1.8f);
                p.polygon(false,3,20,3,17,6,14,12,14,15,17,15,20);
                p.line(17,14,21,17);p.line(21,17,21,20);break;
            default:throw new AssertionError(symbol);
        }
        return p;
    }
    private static final class Path {
        final List<float[]> lines=new ArrayList<>(),polygons=new ArrayList<>(),circles=new ArrayList<>();
        void line(float x0,float y0,float x1,float y1) {lines.add(new float[]{x0,y0,x1,y1});}
        void polygon(boolean fill,float... xy) {
            if(fill)polygons.add(xy);
            else for(int i=0;i<xy.length;i+=2){int j=(i+2)%xy.length;line(xy[i],xy[i+1],xy[j],xy[j+1]);}
        }
        void circle(float x,float y,float radius,float stroke) {circles.add(new float[]{x,y,radius,stroke});}
        boolean contains(float x,float y) {
            for(float[] line:lines) {
                float dx=line[2]-line[0],dy=line[3]-line[1];
                float t=Math.max(0,Math.min(1,((x-line[0])*dx+(y-line[1])*dy)/(dx*dx+dy*dy)));
                float ex=x-line[0]-t*dx,ey=y-line[1]-t*dy;
                if(ex*ex+ey*ey<=.9f*.9f)return true;
            }
            for(float[] circle:circles) {
                float dx=x-circle[0],dy=y-circle[1],distance=dx*dx+dy*dy,r=circle[2],s=circle[3];
                if(s<0&&distance<=r*r&&x<=circle[0])return true;
                if(s>0&&distance<=(r+s/2)*(r+s/2)&&distance>=(r-s/2)*(r-s/2))return true;
            }
            for(float[] polygon:polygons) {
                boolean inside=false;
                for(int i=0,j=polygon.length-2;i<polygon.length;j=i,i+=2)
                    if((polygon[i+1]>y)!=(polygon[j+1]>y)
                            && x<(polygon[j]-polygon[i])*(y-polygon[i+1])/(polygon[j+1]-polygon[i+1])+polygon[i])inside=!inside;
                if(inside)return true;
            }
            return false;
        }
    }
}
