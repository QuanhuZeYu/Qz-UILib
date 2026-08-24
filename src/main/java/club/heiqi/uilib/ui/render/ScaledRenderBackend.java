package club.heiqi.uilib.ui.render;

import java.util.List;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/** 通用缩放装饰器：把 logical px 恰好一次映射为 framebuffer px（屏幕级宿主边界，如 HUD）。 */
final class ScaledRenderBackend implements UiRenderBackend {
    private final UiRenderBackend delegate;
    private final float scale;

    ScaledRenderBackend(UiRenderBackend delegate, float scale) { this.delegate = delegate; this.scale = scale; }
    private int p(int value) { return Math.round(value * scale); }
    public void publishTextDemand(List<String> texts){delegate.publishTextDemand(texts);}
    public void fillRect(int l,int t,int r,int b,int c){delegate.fillRect(p(l),p(t),p(r),p(b),c);}
    public void drawImage(SceneImageSource s,int l,int t,int r,int b){delegate.drawImage(s,p(l),p(t),p(r),p(b));}
    public void drawSurface(int l,int t,int r,int b,int f,int o,int radius){delegate.drawSurface(p(l),p(t),p(r),p(b),f,o,p(radius));}
    public void drawSurface(int l,int t,int r,int b,int f,int o,
            int tL,int tR,int bR,int bL){delegate.drawSurface(p(l),p(t),p(r),p(b),f,o,p(tL),p(tR),p(bR),p(bL));}
    public void drawBorder(int l,int t,int r,int b,int c){delegate.drawBorder(p(l),p(t),p(r),p(b),c);}
    public void pushClip(int l,int t,int r,int b,int radius){delegate.pushClip(p(l),p(t),p(r),p(b),p(radius));}
    public void popClip(){delegate.popClip();}
    public void drawText(String s,int x,int y,int c,boolean shadow){delegate.drawText(s,p(x),p(y),c,shadow);}
    public void drawText(String s,int x,int y,int c,boolean shadow,int font){delegate.drawText(s,p(x),p(y),c,shadow,p(font));}
    public void drawText(String s,int x,int y,int c,boolean shadow,int font,int mode){delegate.drawText(s,p(x),p(y),c,shadow,p(font),mode);}
    public void pushGroupOpacity(int l,int t,int r,int b,float opacity){delegate.pushGroupOpacity(p(l),p(t),p(r),p(b),opacity);}
    public void popGroupOpacity(){delegate.popGroupOpacity();}
    public void pushTransform(float x,float y,float d,float sx,float sy,float ox,float oy,int l,int t,int r,int b){delegate.pushTransform(x*scale,y*scale,d,sx,sy,ox,oy,p(l),p(t),p(r),p(b));}
    public void popTransform(){delegate.popTransform();}
    public void pushTransformLayer(float x,float y,float d,float sx,float sy,float ox,float oy,int l,int t,int r,int b){delegate.pushTransformLayer(x*scale,y*scale,d,sx,sy,ox,oy,p(l),p(t),p(r),p(b));}
    public void popTransformLayer(){delegate.popTransformLayer();}
}