package club.heiqi.uilib.host.media;

/** 仅媒体私有加载器定义；在实际owner成功创建/释放边界计数，不以包装器close推测native已释放。 */
public final class NativeMediaResourceTracker {
    public static final int FORMAT=0, CODEC=1, PACKET=2, FRAME=3, CONVERSION=4, BUFFER=5;
    public static final int AVIO=6, CALLBACK=7, DEVICE=8, CONTEXT=9, SOURCE=10, AUDIO_BUFFER=11;
    private static final long[] CURRENT=new long[12];
    private NativeMediaResourceTracker() {}
    public static synchronized void acquired(int kind) { CURRENT[kind]++; }
    public static synchronized void released(int kind) {
        if(CURRENT[kind]==0) throw new IllegalStateException("媒体native资源重复释放: "+kind);
        CURRENT[kind]--;
    }
    public static synchronized long[] snapshot() { return CURRENT.clone(); }
}
