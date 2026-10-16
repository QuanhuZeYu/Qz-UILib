package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * 正式 PaintPlan 回放：位置只消费不可变 Glyph，不重新布局、读取 scene 或等待字形。 调用方先 prepare（资源上传/失效通知边界），再
 * replay（固定MVP/depth/pass），最后关闭 Prepared。 plan.box/clip、glyph定位及 prepare.viewport 均为原生 framebuffer
 * 像素（左上原点）。 旧UI重载MVP将framebuffer XY映到clip，不再次GUI scale。FontRenderContext重载直接接受caller XY，
 * 保留P/MV/viewport/FBO与齐次w；只使用context outer scissor，不把callerclip矩形近似成screenrect。
 * CPU与atlas由宿主持有，可跨renderer复用；本类仅拥有自己的quad backend。
 */
public final class GlPaintPlanRenderer implements AutoCloseable {
    private final GlProgramProvider programs;
    private final AsyncGlyphRasterizer cpu;
    private final GlGlyphAtlas atlas;
    private final GlQuadRenderer quads;
    private final int capacity, maxDemand;
    private final GlImageResources images;
    private final GlApi gl;
    private final GlVideoResources videos;
    private Prepared replaying;
    private long resourceEpoch;
    private long clippedGlyphs, demandOverflow, blankGlyphs, drawnGlyphs;

    public GlPaintPlanRenderer(
            GlApi gl,
            AsyncGlyphRasterizer cpu,
            GlGlyphAtlas atlas,
            int quadCapacity,
            int maxDemand,
            GlProgramProvider programs) {
        this(gl, cpu, atlas, quadCapacity, maxDemand, programs, null);
    }

    public GlPaintPlanRenderer(
            GlApi gl,
            AsyncGlyphRasterizer cpu,
            GlGlyphAtlas atlas,
            int quadCapacity,
            int maxDemand,
            GlProgramProvider programs,
            GlImageResources images) {
        this(gl, cpu, atlas, quadCapacity, maxDemand, programs, images, null);
    }

    public GlPaintPlanRenderer(GlApi gl, AsyncGlyphRasterizer cpu, GlGlyphAtlas atlas, int quadCapacity,
            int maxDemand, GlProgramProvider programs, GlImageResources images, GlVideoResources videos) {
        this.images = images;
        this.videos = videos;
        this.gl = Objects.requireNonNull(gl, "gl");
        this.programs = Objects.requireNonNull(programs, "programs");
        if (maxDemand < 1) throw new IllegalArgumentException("无效需求预算");
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        capacity = quadCapacity;
        this.maxDemand = maxDemand;
        quads =
                new GlQuadRenderer(
                        gl,
                        quadCapacity,
                        (resource, generation) -> {
                            if (resource >= 0) return atlas.resolve(resource, generation);
                            if (replaying == null) throw new IllegalStateException("纹理引用没有所属回放");
                            return replaying.resolveTexture(resource, generation);
                        },
                        programs);
    }

    public long clippedGlyphs() {
        return clippedGlyphs;
    }

    public long demandOverflow() {
        return demandOverflow;
    }

    public long blankGlyphs() {
        return blankGlyphs;
    }

    public long drawnGlyphs() {
        return drawnGlyphs;
    }

    public GlQuadRenderer.Counters counters() {
        return quads.counters();
    }

    /** 可见需求与回放共用实际墨迹判定，供共享owner累积同帧有界union。 */
    public java.util.Set<GlyphKey> collectVisibleDemand(PaintPlan plan, LayoutBox viewport) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(viewport, "viewport");
        LinkedHashSet<GlyphKey> demand = new LinkedHashSet<GlyphKey>();
        for (PaintCommand command : plan.resourceCommands())
            if (command.kind == PaintCommand.Kind.TEXT) {
                LayoutBox clip = clip(command.clip, viewport);
                if (clip.width == 0 || clip.height == 0) continue;
                for (TextLayout.Line line : command.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) {
                        if (!visible(command, glyph, clip)) {
                            clippedGlyphs++;
                            continue;
                        }
                        GlyphKey key = GlyphKey.of(glyph);
                        if (demand.size() < maxDemand || demand.contains(key)) demand.add(key);
                        else demandOverflow++;
                    }
            }
        return java.util.Collections.unmodifiableSet(demand);
    }

    /** caller空间的world需求：真实墨迹通过齐次clip与实际viewport/scissor裁决，不重布局。 */
    public java.util.Set<GlyphKey> collectVisibleDemand(PaintPlan plan, FontRenderContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        LinkedHashSet<GlyphKey> demand = new LinkedHashSet<GlyphKey>();
        for (PaintCommand command : plan.resourceCommands())
            if (command.kind == PaintCommand.Kind.TEXT)
                for (TextLayout.Line line : command.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) {
                        if (!GlFontVisibility.visible(command, glyph, context)) {
                            clippedGlyphs++;
                            continue;
                        }
                        GlyphKey key = GlyphKey.of(glyph);
                        if (demand.size() < maxDemand || demand.contains(key)) demand.add(key);
                        else demandOverflow++;
                    }
        return java.util.Collections.unmodifiableSet(demand);
    }

    /** owner传同epoch裁决资源key与剩余预算；此入口不重新扩张需求或取消其他consumer。 */
    public Prepared prepareDemandContext(
            PaintPlan plan,
            FontRenderContext context,
            GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> admitted,
            Runnable onClose) {
        Objects.requireNonNull(context, "context");
        return prepareDemandView(
                plan,
                GlFontVisibility.viewport(context),
                budget,
                admitted,
                false,
                onClose,
                resolveSamplingView(context),
                context);
    }

    public GlGlyphAtlas.SamplingView resolveSamplingView(FontRenderContext context) {
        return !programs.preview(context)
                ? GlGlyphAtlas.SamplingView.PREMULT_RGBA
                : GlGlyphAtlas.SamplingView.STRAIGHT_RGBA;
    }

    public Prepared prepare(PaintPlan plan, LayoutBox viewport, GlGlyphAtlas.UploadBudget budget) {
        return prepareDemand(
                plan, viewport, budget, collectVisibleDemand(plan, viewport), true, null);
    }

    /** 显式资源准备入口；共享owner传入已裁决的epoch需求与剩余预算，不能重新扩张需求。 onClose仅归还owner生命周期，不控制渲染或上传。 */
    public Prepared prepareDemand(
            PaintPlan plan,
            LayoutBox viewport,
            GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> demand,
            boolean retainPending,
            Runnable onClose) {
        return prepareDemandView(
                plan,
                viewport,
                budget,
                demand,
                retainPending,
                onClose,
                GlGlyphAtlas.SamplingView.PREMULT_RGBA,
                null);
    }

    private Prepared prepareDemandView(
            PaintPlan plan,
            LayoutBox viewport,
            GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> demand,
            boolean retainPending,
            Runnable onClose,
            GlGlyphAtlas.SamplingView view,
            FontRenderContext visibilityContext) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(viewport, "viewport");
        boolean needsSolid = false;
        for (PaintCommand command : plan.resourceCommands())
            if (command.kind == PaintCommand.Kind.RECT || command.kind == PaintCommand.Kind.LINE)
                needsSolid = true;
            else if (command.kind == PaintCommand.Kind.IMAGE) needsSolid = true;
            else if (command.text != null)
                for (TextLayout.Line line : command.text.lines())
                    if (!line.decorations().isEmpty()) needsSolid = true;
        GlGlyphAtlas.Frame frame =
                atlas.beginFrameLayers(
                        glyphLayers(plan, viewport, demand, visibilityContext),
                        !retainPending,
                        view,
                        needsSolid);
        GlImageResources.Frame imageFrame = null;
        GlVideoResources.Frame videoFrame = null;
        try {
            frame.prepare(cpu, budget, retainPending);
            frame.freeze();
            if (images != null) imageFrame = images.prepare(plan);
            else
                for (PaintCommand command : plan.resourceCommands())
                    if (command.kind == PaintCommand.Kind.IMAGE)
                        throw new IllegalStateException("缺少图像GL宿主服务");
            if (videos != null) videoFrame = videos.prepare(plan, viewport);
            else for (PaintCommand command : plan.resourceCommands())
                if (command.kind == PaintCommand.Kind.VIDEO) throw new IllegalStateException("缺少视频GL宿主服务");
            return new Prepared(plan, viewport, frame, imageFrame, videoFrame, onClose);
        } catch (RuntimeException | Error failure) {
            AutoCloseable[] acquired = {videoFrame, imageFrame, frame};
            for (AutoCloseable resource : acquired) if (resource != null) try { resource.close(); }
                catch (Throwable next) { if (next != failure) failure.addSuppressed(next); }
            throw failure;
        }
    }

    /** 只对owner已admit的key分层，不扩张maxDemand或另开Frame。图像与字形共享PaintPlan资源层权威。 */
    private java.util.List<java.util.Collection<GlyphKey>> glyphLayers(
            PaintPlan plan,
            LayoutBox viewport,
            java.util.Collection<GlyphKey> admitted,
            FontRenderContext context) {
        java.util.LinkedHashSet<GlyphKey> remaining = new java.util.LinkedHashSet<>(admitted);
        java.util.List<java.util.Collection<GlyphKey>> layers = new java.util.ArrayList<>();
        for (java.util.List<PaintCommand> commands : plan.resourceLayers()) {
            java.util.List<GlyphKey> layer = new java.util.ArrayList<>();
            for (PaintCommand command : commands)
                if (command.kind == PaintCommand.Kind.TEXT) {
                    LayoutBox clip = context == null ? clip(command.clip, viewport) : null;
                    if (clip != null && (clip.width == 0 || clip.height == 0)) continue;
                    for (TextLayout.Line line : command.text.lines())
                        for (TextLayout.Glyph glyph : line.glyphs()) {
                            // 与需求收集使用同一GUI墨迹/clip或world齐次投影判定，隐藏同key不能冒主层。
                            if (context == null
                                    ? !visible(command, glyph, clip)
                                    : !GlFontVisibility.visible(command, glyph, context)) continue;
                            GlyphKey key = GlyphKey.of(glyph);
                            if (remaining.remove(key)) layer.add(key);
                        }
                }
            if (!layer.isEmpty()) layers.add(layer);
        }
        // 显式prepareDemand允许已admit的额外需求；保留原有集合，不增加或遗漏。
        if (!remaining.isEmpty()) layers.add(remaining);
        return layers;
    }

    private static boolean visible(PaintCommand command, TextLayout.Glyph glyph, LayoutBox clip) {
        InkBounds bounds = glyph.inkBounds;
        if (!bounds.known) return true;
        double x = (double) command.box.x + glyph.x, y = (double) command.box.y + glyph.y;
        return x + bounds.left < (long) clip.x + clip.width
                && x + bounds.right > clip.x
                && y + bounds.top < (long) clip.y + clip.height
                && y + bounds.bottom > clip.y;
    }

    private static LayoutBox clip(LayoutBox requested, LayoutBox viewport) {
        if (requested == null) return viewport;
        long left = Math.max((long) requested.x, viewport.x),
                top = Math.max((long) requested.y, viewport.y);
        long right =
                Math.min((long) requested.x + requested.width, (long) viewport.x + viewport.width);
        long bottom =
                Math.min(
                        (long) requested.y + requested.height, (long) viewport.y + viewport.height);
        return new LayoutBox(
                (int) left,
                (int) top,
                (int) Math.max(0, right - left),
                (int) Math.max(0, bottom - top));
    }

    public final class Prepared implements AutoCloseable {
        public final PaintPlan plan;
        public final LayoutBox viewport;
        private final GlGlyphAtlas.Frame frame;
        private boolean closed;
        private Replay active;
        private final Runnable onClose;
        private final GlImageResources.Frame imageFrame;
        private final GlVideoResources.Frame videoFrame;
        private final long textureEpoch = ++resourceEpoch;
        private final java.util.Map<Long, TextureRef> textureRefs = new java.util.HashMap<>();
        private final java.util.Map<Long, Long> imageRefs = new java.util.HashMap<>();
        private final java.util.Map<club.heiqi.uilib.ui.media.MediaFrameKey, Long> videoRefs = new java.util.HashMap<>();

        private Prepared(
                PaintPlan plan,
                LayoutBox viewport,
                GlGlyphAtlas.Frame frame,
                GlImageResources.Frame imageFrame,
                GlVideoResources.Frame videoFrame,
                Runnable onClose) {
            this.plan = plan;
            this.viewport = viewport;
            this.frame = frame;
            this.imageFrame = imageFrame;
            this.videoFrame = videoFrame;
            this.onClose = onClose;
            if (textureEpoch <= 0) throw new IllegalStateException("绘制资源代际耗尽");
            // 本 Prepared 为 image/video 分配独立 ref；不会把负 sessionId 混入图像 identity。
            for (PaintCommand command : plan.resourceCommands()) {
                if (command.kind == PaintCommand.Kind.IMAGE && imageFrame != null && imageFrame.ready(command.image.content.identity)
                        && command.image.content.kind != club.heiqi.uilib.ui.scene.image.ImageContent.Kind.HOST_ITEM
                        && !imageRefs.containsKey(command.image.content.identity)) {
                    long ref = -(textureRefs.size() + 1L);
                    imageRefs.put(command.image.content.identity, ref);
                    textureRefs.put(ref, new TextureRef(command.image.content.identity, null));
                } else if (command.kind == PaintCommand.Kind.VIDEO && videoFrame != null
                        && videoFrame.ready(command.video.frame.key) && !videoRefs.containsKey(command.video.frame.key)) {
                    long ref = -(textureRefs.size() + 1L);
                    videoRefs.put(command.video.frame.key, ref);
                    textureRefs.put(ref, new TextureRef(0, command.video.frame.key));
                }
            }
        }

        private GlQuadRenderer.Texture resolveTexture(long resource, long generation) {
            check();
            TextureRef ref = textureRefs.get(resource);
            if (ref == null || generation != textureEpoch) throw new IllegalArgumentException("失效呈现纹理引用");
            return ref.video == null ? images.resolve(-ref.image, ref.image) : videoFrame.resolve(ref.video);
        }

        /** 宿主在成功实际 flush 后取得有界票据；其租约保留到交换或明确提交边界。 */
        public java.util.List<GlVideoResources.Submission> takeVideoSubmissions() {
            check();
            if (active != null) throw new IllegalStateException("先结束回放再交接提交票据");
            return videoFrame == null ? java.util.Collections.emptyList() : videoFrame.takeSubmissions();
        }

        public GlGlyphAtlas.Resident resident(TextLayout.Glyph glyph) {
            check();
            return frame.lookup(GlyphKey.of(glyph));
        }

        public int demandCount() {
            check();
            return frame.demand().size();
        }

        public int readyCount() {
            check();
            int n = 0;
            for (GlyphKey key : frame.demand()) if (frame.lookup(key) != null) n++;
            return n;
        }

        private void check() {
            if (closed) throw new IllegalStateException("prepared 已关闭");
        }

        public Replay begin(float[] mvp, int framebufferHeight, boolean depthEnabled) {
            check();
            if (active != null) throw new IllegalStateException("回放范围重入");
            Replay replay = new Replay(this, mvp.clone(), framebufferHeight, depthEnabled);
            active = replay;
            return replay;
        }

        public Replay begin(FontRenderContext context) {
            check();
            if (active != null) throw new IllegalStateException("回放范围重入");
            Replay replay = new Replay(this, Objects.requireNonNull(context, "context"));
            active = replay;
            return replay;
        }

        public void render(FontRenderContext context) {
            try (Replay replay = begin(context)) {
                for (PaintCommand command : plan.commands()) replay.draw(command);
            }
        }

        public void render(float[] mvp, int framebufferHeight, boolean depthEnabled) {
            try (Replay replay = begin(mvp, framebufferHeight, depthEnabled)) {
                for (PaintCommand command : plan.commands()) replay.draw(command);
            }
        }

        @Override
        public void close() {
            if (closed) return;
            if (active != null) throw new IllegalStateException("先结束回放");
            closed = true;
            Throwable failure = null;
            AutoCloseable[] acquired = {videoFrame, imageFrame, frame};
            for (AutoCloseable resource : acquired) if (resource != null) try { resource.close(); }
                catch (Throwable next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
            textureRefs.clear(); imageRefs.clear(); videoRefs.clear();
            try { if (onClose != null) onClose.run(); }
                catch (Throwable next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure != null) throw new IllegalStateException("绘制准备资源释放失败", failure);
        }
    }

    private static final class TextureRef {
        final long image;
        final club.heiqi.uilib.ui.media.MediaFrameKey video;
        TextureRef(long image, club.heiqi.uilib.ui.media.MediaFrameKey video) { this.image = image; this.video = video; }
    }

    private enum ImageChoice {
        READY,
        PENDING,
        FAILED
    }

    public final class Replay implements AutoCloseable {
        private final Prepared prepared;
        private final float[] mvp;
        private final int height;
        private final boolean depth;
        private FontRenderContext context;
        private final java.util.Map<PaintCommand, ImageChoice> imageChoices =
                new java.util.IdentityHashMap<>();
        private GlQuadRenderer.Scope scope;
        private OrderedQuadCollector collector;
        private club.heiqi.uilib.ui.media.MediaFrameKey videoSubmitting;
        private boolean closed;

        private Replay(Prepared prepared, float[] mvp, int height, boolean depth) {
            this.prepared = prepared;
            this.mvp = mvp;
            this.height = height;
            this.depth = depth;
            open();
        }

        private Replay(Prepared prepared, FontRenderContext context) {
            this.prepared = prepared;
            this.context = context;
            this.mvp = context.projection.mvp();
            this.height = context.sourceFramebufferHeight;
            this.depth = context.depthTest;
            open();
        }

        private void open() {
            if (context != null && prepared.frame.view != resolveSamplingView(context))
                throw new IllegalStateException("prepare/replay atlas采样view与activepass不一致");
            scope = context == null ? quads.begin(mvp, height, depth) : quads.begin(context);
            replaying = prepared;
            collector = new OrderedQuadCollector(capacity, this::submitBatch);
        }

        public void draw(PaintCommand command) {
            if (closed) throw new IllegalStateException("回放已结束");
            LayoutBox clip = context == null ? clip(command.clip, prepared.viewport) : null;
            // world不能把framebuffer scissor交给会按callerXY裁决的collector；scope单独处理outerclip。
            if (clip != null && (clip.width == 0 || clip.height == 0)) return;
            switch (command.kind) {
                case RECT:
                    drawRect(command, clip);
                    break;
                case LINE:
                    drawLine(command, clip);
                    break;
                case VIDEO:
                    drawVideo(command, clip);
                    break;
                case IMAGE:
                    drawImage(command, clip);
                    break;
                case IMAGE_BRANCH_OVERLAY:
                    drawImageBranchOverlay(command);
                    break;
                case TEXT:
                    drawText(command, clip);
                    break;
            }
        }

        private void submitBatch(club.heiqi.uilib.ui.render.batch.OrderedQuadBatch batch) {
            if (videoSubmitting != null && (prepared.videoFrame == null || !prepared.videoFrame.active(videoSubmitting))) return;
            scope.consumeBatch(batch);
            if (videoSubmitting != null) prepared.videoFrame.submitted(videoSubmitting, System.nanoTime());
        }

        private void drawVideo(PaintCommand command, LayoutBox clip) {
            if (context != null) throw new IllegalStateException("视频只支持framebuffer scene回放");
            club.heiqi.uilib.ui.scene.video.VideoPlacement video = command.video;
            collector.flush();
            if (prepared.videoFrame == null || !prepared.videoFrame.active(video.frame.key)) return;
            Long resource = prepared.videoRefs.get(video.frame.key);
            if (resource == null) return;
            collector.add(new State(GlQuadRenderer.COLOR_PREMULT, resource, prepared.textureEpoch, clip),
                    new Quad(video.x, video.y, video.width, video.height, 0, 0, 1, 1, 0, command.color));
            // VIDEO 单独形成同一 collector 的提交段；容量或委托提交不会把未提交 key 补报成功。
            videoSubmitting = video.frame.key;
            try { collector.flush(); } finally { videoSubmitting = null; }
        }

        private void drawImageBranchOverlay(PaintCommand command) {
            ImageChoice choice = imageChoices.get(command.branchSource);
            if (choice == ImageChoice.PENDING) drawPlan(command.pendingPaint);
            else if (choice == ImageChoice.FAILED) drawPlan(command.failedPaint);
        }

        private void drawRect(PaintCommand command, LayoutBox clip) {
            solid(
                    command.box.x,
                    command.box.y,
                    command.box.width,
                    command.box.height,
                    command.color,
                    clip);
        }

        private void drawLine(PaintCommand command, LayoutBox clip) {
            if (command.line.empty()) return;
            GlGlyphAtlas.Resident white =
                    prepared.frame.view == GlGlyphAtlas.SamplingView.STRAIGHT_RGBA
                            ? prepared.frame.solid()
                            : null;
            if (prepared.frame.view == GlGlyphAtlas.SamplingView.STRAIGHT_RGBA && white == null)
                return;
            PaintCommand.Line line = command.line;
            collector.add(
                    white == null
                            ? new State(GlQuadRenderer.SOLID, 0, 0, clip)
                            : new State(
                                    GlQuadRenderer.MONOCHROME_COVERAGE,
                                    white.resource,
                                    white.generation,
                                    clip),
                    Quad.line(
                            line.x0,
                            line.y0,
                            line.x1,
                            line.y1,
                            line.thickness,
                            white == null ? 0 : white.u0,
                            white == null ? 0 : white.v0,
                            white == null ? 0 : white.u1,
                            white == null ? 0 : white.v1,
                            0,
                            command.color));
        }

        private void drawImage(PaintCommand command, LayoutBox clip) {
            club.heiqi.uilib.ui.scene.image.ImagePlacement image = command.image;
            if (context != null) throw new IllegalStateException("图像只支持framebuffer scene回放");
            if (prepared.imageFrame == null
                    || !prepared.imageFrame.ready(image.content.identity)) {
                boolean failed =
                        prepared.imageFrame != null
                                && prepared.imageFrame.failed(image.content.identity);
                imageChoices.put(command, failed ? ImageChoice.FAILED : ImageChoice.PENDING);
                drawPlan(failed ? command.failedPaint : command.pendingPaint);
                return;
            }
            if (image.content.kind
                    == club.heiqi.uilib.ui.scene.image.ImageContent.Kind.HOST_ITEM) {
                boolean[] drawn = {false};
                delegate(
                        () -> {
                            int[] viewport = new int[4];
                            gl.glGetIntegerv(GL_VIEWPORT, viewport);
                            drawn[0] = images.renderHost(image, clip, viewport[2], height);
                        });
                // 原图只执行一次；失败仅选择控件已冻结的同代片段，不重prepare或读live scene。
                imageChoices.put(command, drawn[0] ? ImageChoice.READY : ImageChoice.FAILED);
                drawPlan(drawn[0] ? command.readyOverlay : command.failedPaint);
                return;
            }
            imageChoices.put(command, ImageChoice.READY);
            collector.add(
                    new State(
                            GlQuadRenderer.COLOR_PREMULT,
                            prepared.imageRefs.get(image.content.identity),
                            prepared.textureEpoch,
                            clip),
                    new Quad(
                            image.x,
                            image.y,
                            image.width,
                            image.height,
                            0,
                            0,
                            1,
                            1,
                            0,
                            0xFFFFFFFF));
            drawPlan(command.readyOverlay);
        }

        private void drawText(PaintCommand command, LayoutBox clip) {
            if (command.text == null) return;
            // 显式小行高允许墨迹溢出：所有行的背景先输出，后行高亮不覆盖前行字形。
            for (TextLayout.Line line : command.text.lines())
                for (TextLayout.Decoration decoration : line.decorations())
                    if (decoration.kind == TextLayout.Decoration.Kind.HIGHLIGHT)
                        solid(command.box.x + decoration.x, command.box.y + decoration.y,
                                decoration.width, decoration.height, decoration.color, clip);
            for (TextLayout.Line line : command.text.lines()) {
                for (TextLayout.Glyph glyph : line.glyphs()) drawGlyph(command, glyph, clip);
                for (TextLayout.Decoration decoration : line.decorations()) {
                    if (decoration.kind == TextLayout.Decoration.Kind.HIGHLIGHT) continue;
                    solid(
                            command.box.x + decoration.x,
                            command.box.y + decoration.y,
                            decoration.width,
                            decoration.height,
                            decoration.color,
                            clip);
                }
            }
        }

        private void drawGlyph(PaintCommand command, TextLayout.Glyph glyph, LayoutBox clip) {
            if (context == null
                    ? !visible(command, glyph, clip)
                    : !GlFontVisibility.visible(command, glyph, context)) return;
            GlGlyphAtlas.Resident r = prepared.frame.lookup(GlyphKey.of(glyph));
            if (r == null) {
                blankGlyphs++;
                return;
            }
            if (r.width == 0 || r.height == 0) return;
            // resident bearing/尺寸属于采样face；布局位置与墨迹界已是logical单位，只映回一次。
            float ratio = glyph.rasterToLayout;
            float x = command.box.x + glyph.x + r.bearingX * ratio,
                    y = command.box.y + glyph.y - r.bearingY * ratio;
            float width = r.width * r.pixelScale * ratio;
            float height = r.height * r.pixelScale * ratio;
            float u0 = r.u0, v0 = r.v0, u1 = r.u1, v1 = r.v1;
            if (glyph.paintClip.known) {
                float left =
                        Math.max(x, command.box.x + glyph.x + glyph.paintClip.left);
                float top = Math.max(y, command.box.y + glyph.y + glyph.paintClip.top);
                float right =
                        Math.min(
                                x + width,
                                command.box.x + glyph.x + glyph.paintClip.right);
                float bottom =
                        Math.min(
                                y + height,
                                command.box.y + glyph.y + glyph.paintClip.bottom);
                if (right <= left || bottom <= top) return;
                u0 = r.u0 + (r.u1 - r.u0) * (left - x) / width;
                u1 = r.u0 + (r.u1 - r.u0) * (right - x) / width;
                v0 = r.v0 + (r.v1 - r.v0) * (top - y) / height;
                v1 = r.v0 + (r.v1 - r.v0) * (bottom - y) / height;
                x = left;
                y = top;
                width = right - left;
                height = bottom - top;
            }
            int color =
                    glyph.resolvedStyle == null
                            ? command.color
                            : glyph.resolvedStyle.color;
            State state =
                    new State(
                            r.color
                                    ? GlQuadRenderer.COLOR_PREMULT
                                    : GlQuadRenderer.MONOCHROME_COVERAGE,
                            r.resource,
                            r.generation,
                            clip);
            if (glyph.xExtension == null) {
                collector.add(
                        state,
                        new Quad(x, y, width, height, u0, v0, u1, v1, r.layer, color));
            } else {
                // 三片目标域互斥，共享原atlas资源及Y采样；中片固定原覆盖度列，hook/stem不横向变形。
                float cut = command.box.x + glyph.x + glyph.xExtension.splitX;
                float extra = glyph.xExtension.extraWidth;
                float splitU = u0 + (u1 - u0) * (cut - x) / width;
                if (cut > x)
                    collector.add(
                            state,
                            new Quad(
                                    x, y, cut - x, height, u0, v0, splitU, v1, r.layer,
                                    color));
                collector.add(
                        state,
                        new Quad(
                                cut, y, extra, height, splitU, v0, splitU, v1, r.layer,
                                color));
                if (x + width > cut)
                    collector.add(
                            state,
                            new Quad(
                                    cut + extra,
                                    y,
                                    x + width - cut,
                                    height,
                                    splitU,
                                    v0,
                                    u1,
                                    v1,
                                    r.layer,
                                    color));
            }
            drawnGlyphs++;
        }

        private void drawPlan(PaintPlan plan) {
            if (plan != null) for (PaintCommand command : plan.commands()) draw(command);
        }

        private void solid(float x, float y, float width, float height, int color, LayoutBox clip) {
            if (prepared.frame.view == GlGlyphAtlas.SamplingView.STRAIGHT_RGBA) {
                GlGlyphAtlas.Resident white = prepared.frame.solid();
                if (white == null) return;
                collector.add(
                        new State(
                                GlQuadRenderer.MONOCHROME_COVERAGE,
                                white.resource,
                                white.generation,
                                clip),
                        new Quad(
                                x, y, width, height, white.u0, white.v0, white.u1, white.v1, 0,
                                color));
            } else
                collector.add(
                        new State(GlQuadRenderer.SOLID, 0, 0, clip),
                        new Quad(x, y, width, height, 0, 0, 0, 0, 0, color));
        }

        /** 外部绘制前提交并退出quad状态范围，委托后重新观测宿主状态，不复用状态猜测。 */
        public void delegate(Runnable delegated) {
            if (closed) throw new IllegalStateException("回放已结束");
            try {
                collector.flush();
            } finally {
                scope.close();
                scope = null;
            }
            try {
                Objects.requireNonNull(delegated, "delegate").run();
            } finally {
                try {
                    open();
                } catch (RuntimeException | Error failure) {
                    closed = true;
                    prepared.active = null;
                    replaying = null;
                    throw failure;
                }
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                collector.flush();
            } finally {
                try { if (scope != null) scope.close(); }
                finally { prepared.active = null; replaying = null; }
            }
        }
    }

    @Override
    public void close() {
        quads.close();
    }
}
