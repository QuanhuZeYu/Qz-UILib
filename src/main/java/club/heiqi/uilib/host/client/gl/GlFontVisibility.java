package club.heiqi.uilib.host.client.gl;

import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/** 实际墨迹四角先齐次六面裁剪再除w；没有把负w伪造成正值，也不拿caller范围当framebuffer。 */
final class GlFontVisibility {
    private GlFontVisibility(){}
    static LayoutBox viewport(FontRenderContext context){int[] v=context.projection.viewport();return new LayoutBox(v[0],Math.toIntExact((long)context.sourceFramebufferHeight-v[1]-v[3]),v[2],v[3]);}
    static boolean visible(PaintCommand command,TextLayout.Glyph glyph,FontRenderContext context){
        if(!glyph.inkBounds.known)return true;
        double x=(double)command.box.x+glyph.x,y=(double)command.box.y+glyph.y;
        double l=x+glyph.inkBounds.left,r=x+glyph.inkBounds.right,t=y+glyph.inkBounds.top,b=y+glyph.inkBounds.bottom;
        if(r<=l||b<=t)return false;
        List<double[]> polygon=new ArrayList<double[]>();polygon.add(context.projection.clipAt(l,t,0));polygon.add(context.projection.clipAt(r,t,0));polygon.add(context.projection.clipAt(r,b,0));polygon.add(context.projection.clipAt(l,b,0));
        int[] viewport=context.projection.viewport(),scissor=context.scissor();
        double[] screenLimits={2.0*(scissor[0]-viewport[0])/viewport[2]-1,2.0*((long)scissor[0]+scissor[2]-viewport[0])/viewport[2]-1,
            2.0*(scissor[1]-viewport[1])/viewport[3]-1,2.0*((long)scissor[1]+scissor[3]-viewport[1])/viewport[3]-1};
        // scissor亦以齐次平面裁多边形，避免旋转quad AABB与小scissor交但实际全在外。
        for(int plane=0;plane<(context.scissorEnabled?10:6)&&!polygon.isEmpty();plane++){
            List<double[]> out=new ArrayList<double[]>();double[] previous=polygon.get(polygon.size()-1);double pd=distance(previous,plane,screenLimits);
            for(double[] current:polygon){double cd=distance(current,plane,screenLimits);if((pd>=0)!=(cd>=0)){double f=pd/(pd-cd);double[] q=new double[4];for(int i=0;i<4;i++)q[i]=previous[i]+f*(current[i]-previous[i]);out.add(q);}if(cd>=0)out.add(current);previous=current;pd=cd;}polygon=out;
        }
        if(polygon.isEmpty())return false;
        int[] v=context.projection.viewport();double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(double[] q:polygon){if(q[3]<=0)return true;double sx=v[0]+(q[0]/q[3]+1)*v[2]/2.0,sy=v[1]+(q[1]/q[3]+1)*v[3]/2.0;
            minX=Math.min(minX,sx);maxX=Math.max(maxX,sx);minY=Math.min(minY,sy);maxY=Math.max(maxY,sy);}
        if(maxX<=minX||maxY<=minY)return false;
        if(context.scissorEnabled){int[] s=context.scissor();return minX<(long)s[0]+s[2]&&maxX>s[0]&&minY<(long)s[1]+s[3]&&maxY>s[1];}return true;
    }
    private static double distance(double[] p,int plane,double[] limits){
        if(plane<6){int axis=plane/2;return p[3]+(plane%2==0?p[axis]:-p[axis]);}
        int side=plane-6,axis=side/2;return side%2==0?p[axis]-limits[side]*p[3]:limits[side]*p[3]-p[axis];
    }
}
