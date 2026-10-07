package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import org.lwjgl.opengl.GL;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;

/** 客户端主线程的共享字体生命周期；不要求 scene，构造/度量不创建任何 GL 对象。
 * scene 与后续原版即时消费者共享字体/CPU/atlas，独立 consumer 可申请自己的quad renderer。
 * atlas 单 active Frame，所有消费即时结束；不承诺并发 scene/原版或 Angelica 延后提交兼容。
 */
public final class ClientFontServices implements SceneTextMeasurer,AutoCloseable {
    public static final class Settings {
        public final AsyncGlyphRasterizer.Budget cpu;
        public final GlGlyphAtlas.Mode mode;
        public final GlGlyphAtlas.Budget atlas;
        public final GlGlyphAtlas.UploadBudget upload;
        public final int quadCapacity,maxDemand;
        public Settings(AsyncGlyphRasterizer.Budget cpu,GlGlyphAtlas.Mode mode,GlGlyphAtlas.Budget atlas,
                GlGlyphAtlas.UploadBudget upload,int quadCapacity,int maxDemand) {
            if(quadCapacity<1||maxDemand<1)throw new IllegalArgumentException("无效客户端提交预算");
            this.cpu=Objects.requireNonNull(cpu,"cpu");this.mode=Objects.requireNonNull(mode,"mode");
            this.atlas=Objects.requireNonNull(atlas,"atlas");this.upload=Objects.requireNonNull(upload,"upload");
            this.quadCapacity=quadCapacity;this.maxDemand=maxDemand;
        }
        /** 预算为配置选择，不是字体库数量或窗口尺寸假设；未宣称array性能胜出。 */
        public static Settings defaults() {
            return new Settings(new AsyncGlyphRasterizer.Budget(512,2048,256,64L*1024*1024,16,256L*1024*1024,64,256),
                GlGlyphAtlas.Mode.TEXTURE_2D,new GlGlyphAtlas.Budget(512,2,16,8192,2048,32L*1024*1024),
                new GlGlyphAtlas.UploadBudget(256*1024,2000000,16),2048,2048);
        }
    }
    private final Thread thread=Thread.currentThread();
    private final Settings settings;
    private final FontTextService fonts;
    private final AsyncGlyphRasterizer rasterizer;
    private final List<GlPaintPlanRenderer> renderers=new ArrayList<GlPaintPlanRenderer>();
    private GlGlyphAtlas atlas;
    private GlPaintPlanRenderer sharedRenderer;
    private boolean closed;
    private long readyVersion;
    private boolean presenting;
    private final boolean presentationBoundaryRequired;
    private long epochId,autoEpoch,remainingBytes,remainingNanos,usedBytes,usedNanos,rejectedDemand;
    private int remainingCalls,usedCalls,preparedCount;
    private final LinkedHashSet<GlyphKey> presentationDemand=new LinkedHashSet<GlyphKey>();
    public static final class PresentationStats {
        public final long epochId,uploadedBytes,prepareNanos,rejectedDemand,remainingBytes,remainingNanos;
        public final int uploadCalls,demand,remainingCalls,activePrepared;
        public final boolean active;
        private PresentationStats(ClientFontServices owner){epochId=owner.epochId;uploadedBytes=owner.usedBytes;
            prepareNanos=owner.usedNanos;rejectedDemand=owner.rejectedDemand;remainingBytes=owner.remainingBytes;
            remainingNanos=owner.remainingNanos;uploadCalls=owner.usedCalls;demand=owner.presentationDemand.size();
            remainingCalls=owner.remainingCalls;activePrepared=owner.preparedCount;active=owner.presenting;}
    }
    public PresentationStats presentationStats(){check();return new PresentationStats(this);}
    /** main线程呈现范围，仅存key，不持GL Frame/UV。即使epochId相同也禁止重入；
     * 父宿主在异常路径必须finally endPresentation，未结束时新begin抛错而不重置预算。
     */
    public void beginPresentation(long epochId) {
        check();if(presenting)throw new IllegalStateException("上一个presentation尚未结束");
        this.epochId=epochId;presentationDemand.clear();presenting=true;
        remainingBytes=settings.upload.bytes;remainingCalls=settings.upload.calls;remainingNanos=settings.upload.nanos;
        usedBytes=0;usedCalls=0;usedNanos=0;rejectedDemand=0;
        // begin尚未知道本帧完整需求，不能撤销持续可见的queued请求；end统一清理过期。
        pumpReady();
    }
    public void endPresentation() {
        check();if(!presenting)throw new IllegalStateException("没有活动presentation");
        if(preparedCount!=0)throw new IllegalStateException("先关闭所有Prepared再结束presentation");
        try{rasterizer.retainPending(presentationDemand);if(atlas!=null)atlas.retainUploadDemand(presentationDemand);pumpReady();}finally{presenting=false;}
    }
    /** 所有共享consumer必须走此入口；无显式epoch时懒建立范围。默认构造模式在Prepared.close结束，
     * 有真实present边界的宿主模式保留到endPresentation。
     * nanos扣atlas prepare耗时（不含需求收集、布局、回放和consumer间空闲），比仅驱动耗时保守；
     * GL调用不可抢占，单调用可超过剩余时间，但后续消费不会重新获取预算。
     */
    public GlPaintPlanRenderer.Prepared prepare(GlPaintPlanRenderer renderer,PaintPlan plan,LayoutBox viewport) {
        check();if(!renderers.contains(renderer))throw new IllegalArgumentException("renderer不属于该owner");
        if(preparedCount!=0)throw new IllegalStateException("共享atlas只允许即时短Prepared");
        boolean automatic=!presenting&&!presentationBoundaryRequired;if(!presenting)beginPresentation(--autoEpoch);
        long start=System.nanoTime();
        try {
            for(GlyphKey key:renderer.collectVisibleDemand(plan,viewport)) {
                if(presentationDemand.size()<settings.maxDemand||presentationDemand.contains(key))presentationDemand.add(key);
                else rejectedDemand++;
            }
            GlGlyphAtlas sharedAtlas=atlas();GlGlyphAtlas.Stats before=sharedAtlas.stats();
            long availableNanos=Math.max(0,remainingNanos-(System.nanoTime()-start));
            try {
                GlPaintPlanRenderer.Prepared prepared=renderer.prepareDemand(plan,viewport,
                    new GlGlyphAtlas.UploadBudget(remainingBytes,availableNanos,remainingCalls),presentationDemand,false,
                    ()->{preparedCount--;if(automatic)endPresentation();});
                preparedCount++;return prepared;
            }finally {
                GlGlyphAtlas.Stats after=sharedAtlas.stats();long bytes=after.uploadedBytes-before.uploadedBytes;
                int calls=Math.toIntExact(after.uploadCalls-before.uploadCalls);long elapsed=System.nanoTime()-start;
                usedBytes+=bytes;usedCalls+=calls;usedNanos+=elapsed;
                remainingBytes=Math.max(0,remainingBytes-bytes);remainingCalls=Math.max(0,remainingCalls-calls);
                remainingNanos=Math.max(0,remainingNanos-elapsed);
            }
        }catch(RuntimeException|Error failure){if(automatic&&preparedCount==0)endPresentation();throw failure;}
    }
    public ClientFontServices(ClassLoader sources,long revision,Settings settings) throws IOException {
        this(sources,revision,settings,false);
    }
    /** 具有真实present通知的宿主选择保留整个呈现范围；首次消费懒启动，
     * 所有短Prepared仍须逐次关闭，宿主在交换窗口缓冲之前调用endPresentation。
     * 无present边界的独立consumer沿用默认自动单次范围。 */
    public ClientFontServices(ClassLoader sources,long revision,Settings settings,boolean presentationBoundaryRequired) throws IOException {
        this.presentationBoundaryRequired=presentationBoundaryRequired;
        this.settings=Objects.requireNonNull(settings,"settings");
        fonts=new FontTextService(FontTextService.defaultSources(sources,revision));
        rasterizer=new AsyncGlyphRasterizer(settings.cpu);
    }
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("客户端字体owner仅限创建线程");if(closed)throw new IllegalStateException("客户端字体owner已关闭");}
    public FontTextService fonts(){check();return fonts;}
    @Override public TextLayout layout(String text,TextStyle style,int availableWidth){check();return fonts.layout(text,style,availableWidth);}
    @Override public TextLayout layout(String text,TextStyle style,int availableWidth,TextPolicy policy){check();return fonts.layout(text,style,availableWidth,policy);}
    public AsyncGlyphRasterizer rasterizer(){check();return rasterizer;}
    public Settings settings(){return settings;}
    public boolean hasGlResources(){check();return atlas!=null;}
    public long readyVersion(){check();return readyVersion;}
    /** 宿主每帧准备阶段主动pump；ready只影响资源可画，不递增布局/字体度量代际。 */
    public int pumpReady() {
        check();int count=rasterizer.drainReady(settings.cpu.ready).size();readyVersion=rasterizer.stats().version;return count;
    }
    public void reload(ClassLoader loader,long revision) throws IOException {check();fonts.reload(FontTextService.defaultSources(loader,revision));}
    public GlGlyphAtlas atlas() {
        check();if(atlas==null) {
            try{GL.getCapabilities();}catch(IllegalStateException noCapabilities){GL.createCapabilities();}
            if(!GL.getCapabilities().OpenGL32)throw new IllegalStateException("共享字体backend要求GL3.2+");
            atlas=new GlGlyphAtlas(settings.mode,settings.atlas);
        }
        return atlas;
    }
    /** 返回screen/consumer拥有的renderer；release只释放这份quad，不影响共享缓存。 */
    public GlPaintPlanRenderer createPlanRenderer() {
        check();GlPaintPlanRenderer renderer=new GlPaintPlanRenderer(rasterizer,atlas(),settings.quadCapacity,settings.maxDemand);
        renderers.add(renderer);return renderer;
    }
    public void releasePlanRenderer(GlPaintPlanRenderer renderer) {
        if(Thread.currentThread()!=thread)throw new IllegalStateException("renderer必须在owner线程释放");
        if(closed)return; // owner退出已关闭全部renderer；稍后screen关闭无需再次访问共享服务。
        if(renderer==sharedRenderer)throw new IllegalArgumentException("共享即时renderer由owner关闭");
        if(!renderers.contains(renderer))throw new IllegalArgumentException("renderer不属于该owner");
        renderer.close();renderers.remove(renderer);
    }
    /** 后续vanilla即时adapter可复用这份quad backend，不通过SceneRuntime。 */
    public GlPaintPlanRenderer sharedPlanRenderer(){check();if(sharedRenderer==null)sharedRenderer=createPlanRenderer();return sharedRenderer;}
    /** 必须由客户端主线程且GLcontext仍有效时调用；不挂shutdownhook。
     * CPU worker收到异步关闭信号，native face/service在worker线程自行销毁。
     */
    @Override public void close() {
        if(closed)return;check();
        if(preparedCount!=0)throw new IllegalStateException("退出前先关闭活动Prepared");
        presenting=false;presentationDemand.clear();
        RuntimeException failure=null;
        try {
            for(GlPaintPlanRenderer renderer:new ArrayList<GlPaintPlanRenderer>(renderers)) {
                try{renderer.close();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            }
            renderers.clear();
            try{if(atlas!=null)atlas.close();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        }finally{closed=true;try{fonts.close();}finally{rasterizer.close();}}
        if(failure!=null)throw failure;
    }
    /** 仅供退出清理验收显式等待，不在每帧render调用。 */
    public boolean awaitRasterizerClose(long timeoutMillis) throws InterruptedException{return rasterizer.awaitClose(timeoutMillis);}
}
