package club.heiqi.uilib.testing.gallery.mail;

/** 有限的绘制缓存；只保存颜色与单调时间，反向过渡从当前值继续，不持节点/主题/宿主。 */
final class MailMotion {
    private boolean initialized;
    private int from,to,current;
    private long started,duration,next=Long.MAX_VALUE;
    private int reversingStart;
    private double shortening=1,eased=1;
    enum Curve { STANDARD, EASE_IN, LINEAR }
    private Curve curve=Curve.STANDARD;
    int color(int target,long durationNanos) {return color(target,durationNanos,Curve.STANDARD);}
    int color(int target,long durationNanos,Curve requestedCurve) {
        long now=System.nanoTime();
        if(!initialized) {initialized=true;from=to=current=reversingStart=target;return current;}
        if(durationNanos<=0) {from=to=current=reversingStart=target;shortening=eased=1;duration=0;next=Long.MAX_VALUE;return current;}
        sample(now);
        if(target!=to) {
            if(target==reversingStart) {shortening=Math.abs(eased*shortening+1-shortening);reversingStart=to;}
            else {shortening=1;reversingStart=current;}
            from=current;to=target;started=now;duration=Math.round(durationNanos*shortening);curve=requestedCurve;
        }
        sample(now);return current;
    }
    int opacity(boolean visible,long durationNanos) {return opacity(visible,durationNanos,Curve.STANDARD);}
    int opacity(boolean visible,long durationNanos,Curve curve) {return color(visible?0xff000000:0,durationNanos,curve)>>>24;}
    static double standard(double progress) {return ease(progress,Curve.STANDARD);}
    private static double ease(double progress,Curve curve) {
        if(progress<=0)return 0;if(progress>=1)return 1;
        if(curve==Curve.LINEAR)return progress;
        double x1=curve==Curve.EASE_IN?.42:.4,x2=curve==Curve.EASE_IN?1:.2;
        double low=0,high=1,t=0;
        for(int i=0;i<20;i++) {t=(low+high)/2;double x=3*(1-t)*(1-t)*t*x1+3*(1-t)*t*t*x2+t*t*t;if(x<progress)low=t;else high=t;}
        return 3*(1-t)*t*t+t*t*t;
    }
    static int alpha(int color,float opacity) {
        return (Math.round((color>>>24)*Math.max(0,Math.min(1,opacity)))<<24)|(color&0xffffff);
    }
    long nextPaintNanos() {return next;}
    private void sample(long now) {
        if(current==to||duration<=0||now-started>=duration) {current=to;eased=1;next=Long.MAX_VALUE;return;}
        double progress=Math.max(0,Math.min(1,(double)(now-started)/duration));
        // 同一插值器显式选择现场参考的Material、ease-in或linear，不能混用体感。
        eased=ease(progress,curve);
        int fromAlpha=from>>>24,toAlpha=to>>>24;
        double alpha=fromAlpha+(toAlpha-fromAlpha)*eased;int value=((int)Math.round(alpha))<<24;
        // 透明颜色须按预乘alpha插值；直插透明黑的RGB会在浅色hover中途产生暗闪。
        for(int shift=0;shift<24;shift+=8) {
            double a=((from>>>shift)&255)*fromAlpha,b=((to>>>shift)&255)*toAlpha;
            value|=(alpha==0?0:(int)Math.round((a+(b-a)*eased)/alpha))<<shift;
        }
        current=value;next=now+8000000L;
    }
}
