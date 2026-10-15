package club.heiqi.uilib.testing.gallery.mail;

/** 有限的绘制缓存；只保存颜色与单调时间，反向过渡从当前值继续，不持节点/主题/宿主。 */
final class MailMotion {
    private boolean initialized;
    private int from,to,current;
    private long started,duration,next=Long.MAX_VALUE;
    int color(int target,long durationNanos) {
        long now=System.nanoTime();
        if(!initialized) {initialized=true;from=to=current=target;return current;}
        if(durationNanos<=0) {from=to=current=target;duration=0;next=Long.MAX_VALUE;return current;}
        sample(now);
        if(target!=to) {from=current;to=target;started=now;duration=durationNanos;}
        sample(now);return current;
    }
    int opacity(boolean visible,long durationNanos) {return color(visible?0xff000000:0,durationNanos)>>>24;}
    long nextPaintNanos() {return next;}
    private void sample(long now) {
        if(current==to||duration<=0||now-started>=duration) {current=to;next=Long.MAX_VALUE;return;}
        double progress=Math.max(0,Math.min(1,(double)(now-started)/duration));
        // 对齐Gmail的cubic-bezier(.4,0,.2,1)，先逆解x再求y。
        double low=0,high=1,t=0;
        for(int i=0;i<16;i++) {t=(low+high)/2;double x=3*(1-t)*(1-t)*t*.4+3*(1-t)*t*t*.2+t*t*t;if(x<progress)low=t;else high=t;}
        double eased=3*(1-t)*t*t+t*t*t;
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
