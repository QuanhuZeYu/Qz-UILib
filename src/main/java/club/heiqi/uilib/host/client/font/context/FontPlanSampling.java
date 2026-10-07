package club.heiqi.uilib.host.client.font.context;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace.InkBounds;

/** 已定位计划的有界质量采样，不重排、不查询native ink、不代替GLVisibility或预算。
 * 标准宿主计划从同一Settings捕获统一logicalEm；此入口明确拒绝混字号计划。
 * inkBounds已经logical：只加command box和glyph原点，绝不再乘rasterToLayout。
 * eye/near穿越和UNKNOWN透视取策略顶档，优先避免可见墨迹欠采样；不承诺最小纹理。
 * 完全behind的需求由既有GLVisibility剔除；此处不复制裁剪平面/不丢glyph。
 */
public final class FontPlanSampling {
    private FontPlanSampling(){}
    public static RasterSampling sampling(PaintCommand command,FontRenderContext context,RasterPolicy policy){
        Objects.requireNonNull(command,"command");Objects.requireNonNull(context,"context");Objects.requireNonNull(policy,"policy");
        if(command.kind!=PaintCommand.Kind.TEXT||command.text==null)throw new IllegalArgumentException("采样需要文本计划");
        double em=command.text.style.logicalEm;
        if(!Double.isFinite(em)||em<=0)throw new IllegalArgumentException("无效统一logicalEm");
        boolean hasGlyph=false;
        for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()){
            hasGlyph=true;
            if(glyph.logicalEm!=em)throw new IllegalArgumentException("FontPlanSampling不支持混logicalEm计划；须分为统一字号command");
        }
        if(!hasGlyph)return new RasterSampling(1,context.revision);
        int[] sizes=policy.sizes();int maximum=sizes[sizes.length-1];
        FontProjection projection=context.projection;float[] m=projection.mvp();
        // 平行投影在整个logical x/y平面有同一Jacobian，GUI不必逐glyph取四角。
        if(m[3]==0&&m[7]==0){
            double density=density(projection,command.box.x,command.box.y);
            return selected(policy,density,em,maximum,context.revision);
        }
        double best=0;
        for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()){
            InkBounds ink=glyph.inkBounds;
            if(!ink.known)return capped(maximum,em,context.revision);
            double left=(double)command.box.x+glyph.x+ink.left,right=(double)command.box.x+glyph.x+ink.right;
            double top=(double)command.box.y+glyph.y+ink.top,bottom=(double)command.box.y+glyph.y+ink.bottom;
            if(!Double.isFinite(left)||!Double.isFinite(right)||!Double.isFinite(top)||!Double.isFinite(bottom))return capped(maximum,em,context.revision);
            if(right<=left||bottom<=top)continue;
            double minW=Double.POSITIVE_INFINITY,maxW=Double.NEGATIVE_INFINITY,minNear=Double.POSITIVE_INFINITY,maxNear=Double.NEGATIVE_INFINITY;
            for(int corner=0;corner<4;corner++){
                double x=(corner&1)==0?left:right,y=(corner&2)==0?top:bottom;
                double[] q=projection.clipAt(x,y,0);double w=q[3],near=q[2]+w;
                if(!Double.isFinite(w)||!Double.isFinite(near))return capped(maximum,em,context.revision);
                minW=Math.min(minW,w);maxW=Math.max(maxW,w);minNear=Math.min(minNear,near);maxNear=Math.max(maxNear,near);
                if(w>1e-9){double d=density(projection,x,y);if(!Double.isFinite(d))return capped(maximum,em,context.revision);best=Math.max(best,d);}
            }
            // 线性clip平面在quad四角的异号意味着可能有可见部分，不按originbehind放弃质量。
            if((minW<=1e-9&&maxW>0)||(minNear<=0&&maxNear>0))return capped(maximum,em,context.revision);
        }
        return selected(policy,best,em,maximum,context.revision);
    }
    private static double density(FontProjection projection,double x,double y){
        try{return projection.pixelsPerUnitAt(x,y,0);}catch(IllegalArgumentException nonSampleable){
            // 完全behind使用逻辑档；数值退化/溢出保守封顶，不能因为诊断而丢可见墨迹。
            double w=projection.clipAt(x,y,0)[3];return Double.isFinite(w)&&w<=0?0:Double.POSITIVE_INFINITY;
        }
    }
    private static RasterSampling selected(RasterPolicy policy,double density,double em,int maximum,long revision){
        double desired=density>0?density*em:em;
        return !Double.isFinite(desired)?capped(maximum,em,revision):new RasterSampling((float)(policy.rasterEm(desired)/em),revision);
    }
    private static RasterSampling capped(int maximum,double em,long revision){return new RasterSampling((float)(maximum/em),revision);}
}
