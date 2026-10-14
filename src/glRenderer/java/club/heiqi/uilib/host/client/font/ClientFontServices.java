package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.Collections;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;

/** 客户端主线程的共享字体生命周期；不要求 scene，构造/度量不创建任何 GL 对象。
 * scene 与原版字体适配共享字体/CPU/atlas，独立 consumer 可申请自己的 quad renderer。
 * atlas 的 active Frame 串行提交；即时 replay 与宿主延后提交分别持有所需资源引用，完成后释放。
 */
public final class ClientFontServices implements SceneTextMeasurer, AutoCloseable {
    public static final class Settings {
        public final AsyncGlyphRasterizer.Budget cpu;
        public final GlGlyphAtlas.Mode mode;
        public final GlGlyphAtlas.Budget atlas;
        public final GlGlyphAtlas.UploadBudget upload;
        public final int quadCapacity, maxDemand;

        public Settings(
                AsyncGlyphRasterizer.Budget cpu,
                GlGlyphAtlas.Mode mode,
                GlGlyphAtlas.Budget atlas,
                GlGlyphAtlas.UploadBudget upload,
                int quadCapacity,
                int maxDemand) {
            if (quadCapacity < 1 || maxDemand < 1) throw new IllegalArgumentException("无效客户端提交预算");
            this.cpu = Objects.requireNonNull(cpu, "cpu");
            this.mode = Objects.requireNonNull(mode, "mode");
            this.atlas = Objects.requireNonNull(atlas, "atlas");
            this.upload = Objects.requireNonNull(upload, "upload");
            this.quadCapacity = quadCapacity;
            this.maxDemand = maxDemand;
        }

        /** 预算为配置选择，不是字体库数量或窗口尺寸假设；未宣称array性能胜出。 */
        public static Settings defaults() {
            return new Settings(
                    new AsyncGlyphRasterizer.Budget(
                            512, 2048, 256, 64L * 1024 * 1024, 16, 256L * 1024 * 1024, 64, 256),
                    GlGlyphAtlas.Mode.TEXTURE_2D,
                    new GlGlyphAtlas.Budget(512, 2, 16, 8192, 2048, 32L * 1024 * 1024),
                    new GlGlyphAtlas.UploadBudget(256 * 1024, 2000000, 16),
                    2048,
                    2048);
        }
    }

    private final Thread thread = Thread.currentThread();
    private final Settings settings;
    private final GlApi gl;
    private final GlProgramProvider programs;
    private final FontTextService fonts;
    private final AsyncGlyphRasterizer rasterizer;
    private final List<GlPaintPlanRenderer> renderers = new ArrayList<GlPaintPlanRenderer>();
    private GlGlyphAtlas atlas;
    private GlPaintPlanRenderer sharedRenderer;
    private boolean closed;
    private long readyVersion;
    private boolean presenting;
    private final boolean presentationBoundaryRequired;
    private long epochId,
            autoEpoch,
            remainingBytes,
            remainingNanos,
            usedBytes,
            usedNanos,
            rejectedDemand;
    private int remainingCalls, usedCalls, preparedCount;
    private final LinkedHashSet<GlyphKey> presentationDemand = new LinkedHashSet<GlyphKey>();

    public static final class PresentationStats {
        public final long epochId,
                uploadedBytes,
                prepareNanos,
                rejectedDemand,
                remainingBytes,
                remainingNanos;
        public final int uploadCalls, demand, remainingCalls, activePrepared;
        public final boolean active;

        private PresentationStats(ClientFontServices owner) {
            epochId = owner.epochId;
            uploadedBytes = owner.usedBytes;
            prepareNanos = owner.usedNanos;
            rejectedDemand = owner.rejectedDemand;
            remainingBytes = owner.remainingBytes;
            remainingNanos = owner.remainingNanos;
            uploadCalls = owner.usedCalls;
            demand = owner.presentationDemand.size();
            remainingCalls = owner.remainingCalls;
            activePrepared = owner.preparedCount;
            active = owner.presenting;
        }
    }

    public PresentationStats presentationStats() {
        check();
        return new PresentationStats(this);
    }

    /** main线程呈现范围，仅存key，不持GL Frame/UV。即使epochId相同也禁止重入；
     * 父宿主在异常路径必须finally endPresentation，未结束时新begin抛错而不重置预算。
     */
    public void beginPresentation(long epochId) {
        check();
        if (presenting) throw new IllegalStateException("上一个presentation尚未结束");
        this.epochId = epochId;
        presentationDemand.clear();
        presenting = true;
        remainingBytes = settings.upload.bytes;
        remainingCalls = settings.upload.calls;
        remainingNanos = settings.upload.nanos;
        usedBytes = 0;
        usedCalls = 0;
        usedNanos = 0;
        rejectedDemand = 0;
        // begin尚未知道本帧完整需求，不能撤销持续可见的queued请求；end统一清理过期。
        pumpReady();
    }

    public void endPresentation() {
        check();
        if (!presenting) throw new IllegalStateException("没有活动presentation");
        if (preparedCount != 0) throw new IllegalStateException("先关闭所有Prepared再结束presentation");
        try {
            rasterizer.retainPending(presentationDemand);
            if (atlas != null) atlas.retainUploadDemand(presentationDemand);
            pumpReady();
        } finally {
            presenting = false;
        }
    }

    /** 所有共享consumer必须走此入口；无显式epoch时懒建立范围。默认构造模式在Prepared.close结束，
     * 有真实present边界的宿主模式保留到endPresentation。
     * nanos扣atlas prepare耗时（不含需求收集、布局、回放和consumer间空闲），比仅驱动耗时保守；
     * GL调用不可抢占，单调用可超过剩余时间，但后续消费不会重新获取预算。
     */
    /** 延期consumer只注册投影可见key并启动有界worker，不创建Frame、图集或冻结UV。
     * 与scene/immediate consumer共用完整呈现需求；真正回放时才取得短Prepared。 */
    public Set<GlyphKey> registerVisibleDemand(
            GlPaintPlanRenderer renderer, PaintPlan plan, FontRenderContext context) {
        checkRenderer(renderer);
        if (!presentationBoundaryRequired && !presenting)
            throw new IllegalStateException("延期consumer需要显式presentation或真实present边界");
        if (!presenting) beginPresentation(--autoEpoch);
        Set<GlyphKey> admitted = admitDemand(renderer.collectVisibleDemand(plan, context));
        for (GlyphKey key : admitted) rasterizer.request(key);
        return admitted;
    }

    /** 仅在调用方flush并退出quad范围之后借用；保持同一presentation预算和父Frame冻结资源。 */
    public void withDelegatedConsumer(Runnable delegated) {
        check();
        if (preparedCount != 1) throw new IllegalStateException("字体委托需要唯一父Prepared");
        preparedCount--;
        try {
            atlas().borrowFrozenFrame(delegated);
        } finally {
            preparedCount++;
        }
    }

    private void checkRenderer(GlPaintPlanRenderer renderer) {
        check();
        if (!renderers.contains(renderer)) throw new IllegalArgumentException("renderer不属于该owner");
    }

    private Set<GlyphKey> admitDemand(Collection<GlyphKey> incoming) {
        LinkedHashSet<GlyphKey> admitted = new LinkedHashSet<GlyphKey>();
        for (GlyphKey key : incoming) {
            if (presentationDemand.size() < settings.maxDemand
                    || presentationDemand.contains(key)) {
                presentationDemand.add(key);
                admitted.add(key);
            } else rejectedDemand++;
        }
        return Collections.unmodifiableSet(admitted);
    }

    public GlPaintPlanRenderer.Prepared prepare(
            GlPaintPlanRenderer renderer, PaintPlan plan, LayoutBox viewport) {
        return prepare(renderer, plan, viewport, null);
    }

    public GlPaintPlanRenderer.Prepared prepare(
            GlPaintPlanRenderer renderer, PaintPlan plan, FontRenderContext context) {
        Objects.requireNonNull(context, "context");
        return prepare(renderer, plan, null, context);
    }

    private GlPaintPlanRenderer.Prepared prepare(
            GlPaintPlanRenderer renderer,
            PaintPlan plan,
            LayoutBox viewport,
            FontRenderContext context) {
        checkRenderer(renderer);
        if (preparedCount != 0) throw new IllegalStateException("共享atlas只允许即时短Prepared");
        boolean automatic = !presenting && !presentationBoundaryRequired;
        if (!presenting) beginPresentation(--autoEpoch);
        long start = System.nanoTime();
        try {
            admitDemand(
                    context == null
                            ? renderer.collectVisibleDemand(plan, viewport)
                            : renderer.collectVisibleDemand(plan, context));
            GlGlyphAtlas sharedAtlas = atlas();
            GlGlyphAtlas.Stats before = sharedAtlas.stats();
            long availableNanos = Math.max(0, remainingNanos - (System.nanoTime() - start));
            try {
                GlGlyphAtlas.UploadBudget budget =
                        new GlGlyphAtlas.UploadBudget(
                                remainingBytes, availableNanos, remainingCalls);
                Runnable onClose =
                        () -> {
                            preparedCount--;
                            if (automatic) endPresentation();
                        };
                GlPaintPlanRenderer.Prepared prepared =
                        context == null
                                ? renderer.prepareDemand(
                                        plan, viewport, budget, presentationDemand, false, onClose)
                                : renderer.prepareDemandContext(
                                        plan, context, budget, presentationDemand, onClose);
                preparedCount++;
                return prepared;
            } finally {
                GlGlyphAtlas.Stats after = sharedAtlas.stats();
                long bytes = after.uploadedBytes - before.uploadedBytes;
                int calls = Math.toIntExact(after.uploadCalls - before.uploadCalls);
                long elapsed = System.nanoTime() - start;
                usedBytes += bytes;
                usedCalls += calls;
                usedNanos += elapsed;
                remainingBytes = Math.max(0, remainingBytes - bytes);
                remainingCalls = Math.max(0, remainingCalls - calls);
                remainingNanos = Math.max(0, remainingNanos - elapsed);
            }
        } catch (RuntimeException | Error failure) {
            if (automatic && preparedCount == 0) endPresentation();
            throw failure;
        }
    }

    public ClientFontServices(
            ClassLoader sources,
            long revision,
            Settings settings,
            FontEngine engine,
            GlApi gl,
            GlProgramProvider programs)
            throws IOException {
        this(sources, revision, settings, false, engine, gl, programs);
    }

    /** 具有真实present通知的宿主选择保留整个呈现范围；首次消费懒启动，
     * 所有短Prepared仍须逐次关闭，宿主在交换窗口缓冲之前调用endPresentation。
     * 无present边界的独立consumer沿用默认自动单次范围。 */
    public ClientFontServices(
            ClassLoader sources,
            long revision,
            Settings settings,
            boolean presentationBoundaryRequired,
            FontEngine engine,
            GlApi gl,
            GlProgramProvider programs)
            throws IOException {
        this.presentationBoundaryRequired = presentationBoundaryRequired;
        this.gl = Objects.requireNonNull(gl, "gl");
        this.programs = Objects.requireNonNull(programs, "programs");
        this.settings = Objects.requireNonNull(settings, "settings");
        fonts =
                new FontTextService(
                        FontTextService.defaultSources(sources, revision),
                        Objects.requireNonNull(engine, "engine"),
                        32,
                        256,
                        8192);
        rasterizer = new AsyncGlyphRasterizer(settings.cpu, engine);
    }

    private void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("客户端字体owner仅限创建线程");
        if (closed) throw new IllegalStateException("客户端字体owner已关闭");
    }

    public FontTextService fonts() {
        check();
        return fonts;
    }

    /** 共同文字/公式消费同一字体代际解析出的字号和基线，不能继承默认identity。 */
    @Override
    public TextStyle resolveStyle(TextStyle style) {
        check();
        return fonts.resolveStyle(style);
    }

    @Override
    public club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(TextStyle style) {
        check();
        return fonts.mathFont(style);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int availableWidth) {
        check();
        return fonts.layout(text, style, availableWidth);
    }

    @Override
    public TextLayout layout(
            club.heiqi.uilib.ui.scene.text.AttributedText text,
            float availableWidth,
            TextPolicy policy) {
        check();
        return fonts.layout(text, availableWidth, policy);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int availableWidth, TextPolicy policy) {
        check();
        return fonts.layout(text, style, availableWidth, policy);
    }

    public AsyncGlyphRasterizer rasterizer() {
        check();
        return rasterizer;
    }

    public Settings settings() {
        return settings;
    }

    public GlApi gl() {
        check();
        return gl;
    }

    public boolean hasGlResources() {
        check();
        return atlas != null;
    }

    public long readyVersion() {
        check();
        return readyVersion;
    }

    /** 宿主每帧准备阶段主动pump；ready只影响资源可画，不递增布局/字体度量代际。 */
    public int pumpReady() {
        check();
        int count = rasterizer.drainReady(settings.cpu.ready).size();
        readyVersion = rasterizer.stats().version;
        return count;
    }

    public void reload(ClassLoader loader, long revision) throws IOException {
        check();
        reload(FontTextService.defaultSources(loader, revision));
    }

    /** 宿主组合有序候选后经owner换代；复用字体失效通知，不新建第二套缓存。 */
    public void reload(List<club.heiqi.uilib.ui.scene.text.font.FontSource> sources) {
        check();
        fonts.reload(sources);
    }

    public GlGlyphAtlas atlas() {
        check();
        if (atlas == null) {
            if (!gl.capabilities().openGL32)
                throw new IllegalStateException("共享字体backend要求实际GL3.2+能力，不能以bindings版本推断");
            atlas = new GlGlyphAtlas(gl, settings.mode, settings.atlas);
        }
        return atlas;
    }

    /** 返回screen/consumer拥有的renderer；release只释放这份quad，不影响共享缓存。 */
    public GlPaintPlanRenderer createPlanRenderer() {
        return createPlanRenderer(null);
    }

    /** 图像资源由同级owner提供；此处只组合有序GPU消费者，不持网络服务。 */
    public GlPaintPlanRenderer createPlanRenderer(
            club.heiqi.uilib.host.client.gl.GlImageResources images) {
        check();
        GlPaintPlanRenderer renderer =
                new GlPaintPlanRenderer(
                        gl,
                        rasterizer,
                        atlas(),
                        settings.quadCapacity,
                        settings.maxDemand,
                        programs,
                        images);
        renderers.add(renderer);
        return renderer;
    }

    public void releasePlanRenderer(GlPaintPlanRenderer renderer) {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("renderer必须在owner线程释放");
        if (closed) return; // owner退出已关闭全部renderer；稍后screen关闭无需再次访问共享服务。
        if (renderer == sharedRenderer) throw new IllegalArgumentException("共享即时renderer由owner关闭");
        if (!renderers.contains(renderer)) throw new IllegalArgumentException("renderer不属于该owner");
        renderer.close();
        renderers.remove(renderer);
    }

    /** 后续vanilla即时adapter可复用这份quad backend，不通过SceneRuntime。 */
    public GlPaintPlanRenderer sharedPlanRenderer() {
        check();
        if (sharedRenderer == null) sharedRenderer = createPlanRenderer();
        return sharedRenderer;
    }

    /** 必须由客户端主线程且GLcontext仍有效时调用；不挂shutdownhook。
     * CPU worker收到异步关闭信号，native face/service在worker线程自行销毁。
     */
    @Override
    public void close() {
        if (closed) return;
        check();
        if (preparedCount != 0) throw new IllegalStateException("退出前先关闭活动Prepared");
        presenting = false;
        presentationDemand.clear();
        // 字体规格 5.2：单项关闭失败仍清理其余所属资源，首错保留，后续错误附入 suppressed。
        closed = true;
        Throwable failure = null;
        for (GlPaintPlanRenderer renderer : new ArrayList<GlPaintPlanRenderer>(renderers)) {
            try {
                renderer.close();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        renderers.clear();
        try {
            if (atlas != null) atlas.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        try {
            fonts.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        try {
            rasterizer.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
    }

    /** 仅供退出清理验收显式等待，不在每帧render调用。 */
    public boolean awaitRasterizerClose(long timeoutMillis) throws InterruptedException {
        return rasterizer.awaitClose(timeoutMillis);
    }
}
