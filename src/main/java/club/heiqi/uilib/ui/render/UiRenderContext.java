package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.api.FontRendererAdapter;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * UI 渲染上下文。
 */
public class UiRenderContext {

    private static final float UI_TEXT_SCALE = 2.0F;
    private static final float[][] UI_BACKDROP_BLUR_SAMPLES = new float[][] {
            { -1.0F, 0.0F, 0.18F },
            { 1.0F, 0.0F, 0.18F },
            { 0.0F, -1.0F, 0.18F },
            { 0.0F, 1.0F, 0.18F },
            { -1.0F, -1.0F, 0.12F },
            { 1.0F, -1.0F, 0.12F },
            { -1.0F, 1.0F, 0.12F },
            { 1.0F, 1.0F, 0.12F }
    };
    private static final UiBackdropShaderProgram BACKDROP_SHADER_PROGRAM = new UiBackdropShaderProgram();
    private static volatile BackdropFilterRenderPath lastBackdropFilterRenderPath = BackdropFilterRenderPath.NONE;
    private static volatile String lastBackdropFilterDetail = "not-run";

    private final int screenWidth;
    private final int screenHeight;
    private final int mouseX;
    private final int mouseY;
    private final float partialTicks;
    private final FontRendererAdapter fontRenderer;
    private final PaintContextCompositor paintContextCompositor;
    private final UiMainLayerSnapshotService mainLayerSnapshotService;
    private final Deque<ClipState> clipStack = new ArrayDeque<ClipState>();
    private final List<DeferredPostMainPass> deferredPostMainPasses = new ArrayList<DeferredPostMainPass>();
    private String pendingBackdropFallbackDetail;
    private int mainLayerContentRevision;

    /**
     * 最近一次 backdrop-filter 实际渲染路径。
     */
    public enum BackdropFilterRenderPath {
        NONE("none"),
        SHADER("shader"),
        FIXED_PIPELINE("fixed-pipeline"),
        TINT_FALLBACK("tint-fallback");

        private final String label;

        BackdropFilterRenderPath(String label) {
            this.label = label;
        }

        /**
         * 返回适合显示的路径标签。
         *
         * @return 路径标签
         */
        public String getLabel() {
            return label;
        }
    }

    /**
     * 单层裁剪状态快照。
     */
    private static final class ClipState {

        private final int[] clipRect;
        private final int cornerRadius;

        private ClipState(int[] clipRect, int cornerRadius) {
            this.clipRect = clipRect;
            this.cornerRadius = cornerRadius;
        }
    }

    /**
     * HTML-like paint context 离屏合成器。
     *
     * <p>该对象由宿主会话跨帧复用，按需借出同尺寸 `UiRenderTarget`。文档作者只接触 CSS-like opacity，
     * 不直接感知 FBO、framebuffer 或 OpenGL 状态。</p>
     */
    public static final class PaintContextCompositor {

        private final Deque<PaintContextFrame> frameStack = new ArrayDeque<PaintContextFrame>();
        private final List<UiRenderTarget> layerPool = new ArrayList<UiRenderTarget>();
        private int borrowedLayerCount;
        private boolean disabledForFrame;

        /**
         * 开始新一帧 paint context 回放。
         */
        public void beginFrame() {
            finishFrame();
            borrowedLayerCount = 0;
            disabledForFrame = false;
        }

        /**
         * 结束当前帧，防御性清理仍未弹出的 paint context。
         */
        public void finishFrame() {
            while (!frameStack.isEmpty()) {
                popPaintContext();
            }
            borrowedLayerCount = 0;
        }

        /**
         * 释放已缓存的离屏层资源。
         */
        public void close() {
            finishFrame();
            for (UiRenderTarget renderTarget : layerPool) {
                renderTarget.close();
            }
            layerPool.clear();
        }

        private void pushPaintContext(int screenWidth, int screenHeight, int left, int top, int right, int bottom,
                float opacity, ClipSnapshot clipSnapshot) {
            int clampedLeft = clampInt(Math.min(left, right), 0, screenWidth);
            int clampedTop = clampInt(Math.min(top, bottom), 0, screenHeight);
            int clampedRight = clampInt(Math.max(left, right), 0, screenWidth);
            int clampedBottom = clampInt(Math.max(top, bottom), 0, screenHeight);
            float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
            if (disabledForFrame || clampedOpacity <= 0.0F || clampedOpacity >= 0.999F
                    || clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
                frameStack.push(PaintContextFrame.inactive());
                return;
            }

            int layerIndex = borrowedLayerCount;
            UiRenderTarget layer = null;
            boolean layerBegun = false;
            try {
                layer = borrowLayer(screenWidth, screenHeight);
                int parentFramebufferId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                layer.begin();
                layerBegun = true;
                applyClipSnapshot(clipSnapshot, screenHeight);
                frameStack.push(PaintContextFrame.active(layerIndex, layer, clampedLeft, clampedTop, clampedRight,
                        clampedBottom, clampedOpacity, parentFramebufferId));
            } catch (RuntimeException exception) {
                if (layerBegun && layer != null) {
                    layer.end();
                }
                disabledForFrame = true;
                borrowedLayerCount = layerIndex;
                frameStack.push(PaintContextFrame.inactive());
            } catch (LinkageError error) {
                if (layerBegun && layer != null) {
                    layer.end();
                }
                disabledForFrame = true;
                borrowedLayerCount = layerIndex;
                frameStack.push(PaintContextFrame.inactive());
            }
        }

        private boolean popPaintContext() {
            if (frameStack.isEmpty()) {
                return false;
            }
            PaintContextFrame frame = frameStack.pop();
            if (!frame.active) {
                return false;
            }
            frame.layer.end();
            frame.layer.compositeToCurrentFramebuffer(frame.left, frame.top, frame.right, frame.bottom,
                    frame.opacity);
            borrowedLayerCount = Math.min(borrowedLayerCount, frame.layerIndex);
            return true;
        }

        private boolean isCurrentLayerActive() {
            return !frameStack.isEmpty() && frameStack.peek().active;
        }

        private int getCurrentBackdropReadFramebufferId() {
            if (!isCurrentLayerActive()) {
                return -1;
            }
            return frameStack.peek().parentFramebufferId;
        }

        private UiRenderTarget borrowLayer(int screenWidth, int screenHeight) {
            UiRenderTarget layer;
            if (borrowedLayerCount < layerPool.size()) {
                layer = layerPool.get(borrowedLayerCount);
            } else {
                layer = new UiRenderTarget();
                layerPool.add(layer);
            }
            borrowedLayerCount++;
            layer.ensureSize(screenWidth, screenHeight);
            return layer;
        }
    }

    private static final class PaintContextFrame {

        private final int layerIndex;
        private final UiRenderTarget layer;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final float opacity;
        private final int parentFramebufferId;
        private final boolean active;

        private PaintContextFrame(int layerIndex, UiRenderTarget layer, int left, int top, int right, int bottom,
                float opacity, int parentFramebufferId, boolean active) {
            this.layerIndex = layerIndex;
            this.layer = layer;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.opacity = opacity;
            this.parentFramebufferId = parentFramebufferId;
            this.active = active;
        }

        private static PaintContextFrame inactive() {
            return new PaintContextFrame(-1, null, 0, 0, 0, 0, 1.0F, 0, false);
        }

        private static PaintContextFrame active(int layerIndex, UiRenderTarget layer, int left, int top, int right,
                int bottom, float opacity, int parentFramebufferId) {
            return new PaintContextFrame(layerIndex, layer, left, top, right, bottom, opacity, parentFramebufferId,
                    true);
        }
    }

    /**
     * 圆角裁剪区域快照。
     */
    public static final class RoundedClipRegion {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private RoundedClipRegion(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public int getCornerRadius() {
            return cornerRadius;
        }
    }

    /**
     * 当前有效裁剪状态快照，供延迟回放阶段复用。
     */
    public static final class ClipSnapshot {

        private final int[] clipRect;
        private final List<RoundedClipRegion> roundedClipRegions;

        private ClipSnapshot(int[] clipRect, List<RoundedClipRegion> roundedClipRegions) {
            this.clipRect = clipRect;
            this.roundedClipRegions = roundedClipRegions;
        }

        public int[] getClipRect() {
            if (clipRect == null) {
                return null;
            }
            return new int[] { clipRect[0], clipRect[1], clipRect[2], clipRect[3] };
        }

        public List<RoundedClipRegion> getRoundedClipRegions() {
            return roundedClipRegions;
        }
    }

    /**
     * 主 UI FBO 完成后的补充回放动作。
     */
    public interface DeferredPostMainPassReplay {

        /**
         * 在第二个 FBO 中回放当前动作。
         */
        void replay();
    }

    /**
     * 延迟到主渲染完成后再执行的回放记录。
     *
     * <p>这里保留 clip/scissor 快照，让宿主能够把同一批补充绘制
     * 回放到第二个 FBO，再按既有 alpha 合成契约贴回主 UI FBO。</p>
     */
    public static final class DeferredPostMainPass {

        private final DeferredPostMainPassReplay replay;
        private final ClipSnapshot clipSnapshot;

        private DeferredPostMainPass(DeferredPostMainPassReplay replay, ClipSnapshot clipSnapshot) {
            this.replay = replay;
            this.clipSnapshot = clipSnapshot;
        }

        public void replay() {
            replay.replay();
        }

        public ClipSnapshot getClipSnapshot() {
            return clipSnapshot;
        }
    }

    /**
     * 创建渲染上下文。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTicks 插值帧参数
     */
    public UiRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks) {
        this(screenWidth, screenHeight, mouseX, mouseY, partialTicks, new PaintContextCompositor(),
                new UiMainLayerSnapshotService());
    }

    /**
     * 创建渲染上下文。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTicks 插值帧参数
     * @param paintContextCompositor paint context 离屏合成器
     */
    public UiRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks,
            PaintContextCompositor paintContextCompositor) {
        this(screenWidth, screenHeight, mouseX, mouseY, partialTicks, paintContextCompositor,
                new UiMainLayerSnapshotService());
    }

    /**
     * 创建渲染上下文。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTicks 插值帧参数
     * @param paintContextCompositor paint context 离屏合成器
     * @param mainLayerSnapshotService UI 主层快照服务
     */
    public UiRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.fontRenderer = DefaultFontRendererAdapter.getInstance();
        this.paintContextCompositor = Objects.requireNonNull(paintContextCompositor, "paintContextCompositor");
        this.mainLayerSnapshotService = Objects.requireNonNull(mainLayerSnapshotService,
                "mainLayerSnapshotService");
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public FontRendererAdapter getFontRenderer() {
        return fontRenderer;
    }

    /**
     * 通知当前 UI 绘制目标已经发生内容写入。
     *
     * <p>内置的 surface、text、backdrop 与 paint context 合成路径会自动调用该方法；
     * 自定义渲染器如果绕过这些封装直接写入 OpenGL，也应在写入后调用，避免后续
     * `backdrop-filter` 继续复用写入前的旧快照。</p>
     */
    public void notifyMainLayerContentChanged() {
        if (mainLayerContentRevision == Integer.MAX_VALUE) {
            mainLayerContentRevision = 1;
            return;
        }
        mainLayerContentRevision++;
    }

    /**
     * 返回当前 UI 绘制目标内容版本，供测试和诊断使用。
     *
     * @return 内容版本
     */
    public int getMainLayerContentRevisionForDiagnostics() {
        return mainLayerContentRevision;
    }

    /**
     * 返回最近一次 backdrop-filter 实际渲染路径。
     *
     * @return 渲染路径
     */
    public static BackdropFilterRenderPath getLastBackdropFilterRenderPath() {
        return lastBackdropFilterRenderPath;
    }

    /**
     * 返回最近一次 backdrop-filter 诊断说明。
     *
     * @return 诊断说明
     */
    public static String getLastBackdropFilterDetail() {
        return lastBackdropFilterDetail;
    }

    /**
     * 绘制矩形。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param color ARGB 颜色
     */
    public void fillRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
        notifyMainLayerContentChanged();
    }

    /**
     * 绘制矩形边框。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param color ARGB 颜色
     */
    public void drawBorder(int left, int top, int right, int bottom, int color) {
        fillRect(left, top, right, top + 1, color);
        fillRect(left, bottom - 1, right, bottom, color);
        fillRect(left, top, left + 1, bottom, color);
        fillRect(right - 1, top, right, bottom, color);
    }

    /**
     * 按表面样式绘制容器表面。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param surfaceStyle 表面样式
     */
    public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
        if (surfaceStyle == null) {
            return;
        }

        int radius = clampCornerRadius(right - left, bottom - top, surfaceStyle.cornerRadius);
        if (surfaceStyle.fillColor != 0) {
            if (radius <= 0) {
                fillRect(left, top, right, bottom, surfaceStyle.fillColor);
            } else {
                fillRoundedRect(left, top, right, bottom, radius, surfaceStyle.fillColor);
            }
        }
        if (surfaceStyle.borderColor != 0) {
            if (radius <= 0) {
                drawBorder(left, top, right, bottom, surfaceStyle.borderColor);
            } else {
                drawRoundedBorder(left, top, right, bottom, radius, surfaceStyle.borderColor);
            }
        }
    }

    /**
     * 绘制元素背后内容滤镜。
     *
     * <p>该入口只采样当前 UI 主层已经绘制到当前 framebuffer 的内容，不主动读取游戏世界 framebuffer。
     * 如果页面壳提前绘制了一张已模糊底图，它会作为普通 UI 背景被采样；否则只处理 UI 自身内容。
     * 当前实现优先使用 GLSL 对同帧 UI 主层快照纹理做平滑采样，失败时回退为固定管线近似 blur，
     * 快照复制或绘制不可用时再回退为伪玻璃 tint。诊断中的 `region=atlas-*` 表示当前采样复用了同帧
     * 已捕获且完整覆盖当前区域的较大 block 快照，`tiles=...` 用于观察 tile 分桶命中情况。</p>
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率，1.0 表示不改变
     * @param cornerRadius 圆角半径
     */
    public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
            int cornerRadius) {
        pendingBackdropFallbackDetail = null;
        if (right <= left || bottom <= top || (blurRadius <= 0 && Float.compare(saturation, 1.0F) == 0)) {
            recordBackdropFilterPath(BackdropFilterRenderPath.NONE, "skipped");
            return;
        }
        if (drawCurrentUiBackdropFilter(left, top, right, bottom, blurRadius, saturation, cornerRadius)) {
            return;
        }
        drawBackdropFilterFallback(left, top, right, bottom, blurRadius, saturation, cornerRadius);
    }

    private boolean drawCurrentUiBackdropFilter(int left, int top, int right, int bottom, int blurRadius,
            float saturation, int cornerRadius) {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(
                screenWidth, screenHeight, left, top, right, bottom, blurRadius);
        if (sampleRegion == null) {
            return false;
        }

        int backdropReadFramebufferId = paintContextCompositor.getCurrentBackdropReadFramebufferId();
        UiMainLayerSnapshotService.Snapshot snapshot = mainLayerSnapshotService.acquireSnapshot(screenWidth,
                screenHeight, backdropReadFramebufferId, mainLayerContentRevision, sampleRegion, blurRadius);
        if (snapshot == null) {
            pendingBackdropFallbackDetail = "snapshot-unavailable: " + mainLayerSnapshotService.getLastFailureDetail();
            return false;
        }

        pushClip(left, top, right, bottom, cornerRadius);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean drewBackdrop = false;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.getTextureId());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_BLEND);

            if (drawBackdropTextureWithShader(left, top, right, bottom, snapshot.getSampleLeft(),
                    snapshot.getSampleTop(), snapshot.getWidth(), snapshot.getHeight(), snapshot.getTextureWidth(),
                    snapshot.getTextureHeight(), snapshot.getDownsampleFactor(), blurRadius, saturation, snapshot)) {
                drewBackdrop = true;
                return true;
            }
            if (blurRadius <= 0) {
                return false;
            }

            drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                    snapshot.getWidth(), snapshot.getHeight(), 0.0F, 0.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float sampleStep = (float) resolveBackdropSampleStep(blurRadius);
            for (float[] sample : UI_BACKDROP_BLUR_SAMPLES) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, sample[2]);
                drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                        snapshot.getWidth(), snapshot.getHeight(), sample[0] * sampleStep, sample[1] * sampleStep);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            recordBackdropFilterPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader-unavailable, snapshot=" + formatSnapshotState(snapshot));
            drewBackdrop = true;
            return true;
        } finally {
            GL20.glUseProgram(previousProgram);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glPopAttrib();
            popClip();
            mainLayerSnapshotService.releaseSnapshot(snapshot);
            if (drewBackdrop) {
                notifyMainLayerContentChanged();
            }
        }
    }

    private boolean drawBackdropTextureWithShader(int left, int top, int right, int bottom, int sampleLeft,
            int sampleTop, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight,
            int downsampleFactor, int blurRadius, float saturation, UiMainLayerSnapshotService.Snapshot snapshot) {
        if (!BACKDROP_SHADER_PROGRAM.ensureInitialized()) {
            recordBackdropFilterPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader unavailable: " + BACKDROP_SHADER_PROGRAM.getLastFailureMessage());
            return false;
        }
        BACKDROP_SHADER_PROGRAM.bind();
        BACKDROP_SHADER_PROGRAM.setUniformI("mainTex", 0);
        BACKDROP_SHADER_PROGRAM.setUniform2f("texelSize", 1.0F / (float) textureWidth, 1.0F / (float) textureHeight);
        BACKDROP_SHADER_PROGRAM.setUniformF("blurRadius", resolveBackdropShaderRadius(blurRadius,
                downsampleFactor));
        BACKDROP_SHADER_PROGRAM.setUniformF("saturation", Math.max(0.0F, saturation));
        BACKDROP_SHADER_PROGRAM.setUniformF("lodBias", resolveBackdropLodBias(blurRadius, downsampleFactor));
        drawBackdropTextureQuad(left, top, right, bottom, sampleLeft, sampleTop, sampleWidth, sampleHeight,
                0.0F, 0.0F);
        BACKDROP_SHADER_PROGRAM.unbind();
        recordBackdropFilterPath(BackdropFilterRenderPath.SHADER, "blur=" + blurRadius + ", saturation="
                + String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(Math.max(0.0F, saturation)))
                + ", snapshot=" + formatSnapshotState(snapshot));
        return true;
    }

    private void drawBackdropTextureQuad(int left, int top, int right, int bottom, int sampleLeft, int sampleTop,
            int sampleWidth, int sampleHeight, float sampleOffsetX, float sampleOffsetY) {
        float leftU = clampFloat(((float) left + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float rightU = clampFloat(((float) right + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float topV = clampFloat(1.0F - ((float) top + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        float bottomV = clampFloat(1.0F - ((float) bottom + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, bottom, 0.0D, leftU, bottomV);
        tessellator.addVertexWithUV(right, bottom, 0.0D, rightU, bottomV);
        tessellator.addVertexWithUV(right, top, 0.0D, rightU, topV);
        tessellator.addVertexWithUV(left, top, 0.0D, leftU, topV);
        tessellator.draw();
    }

    private void drawBackdropFilterFallback(int left, int top, int right, int bottom, int blurRadius, float saturation,
            int cornerRadius) {
        recordBackdropFilterPath(BackdropFilterRenderPath.TINT_FALLBACK,
                pendingBackdropFallbackDetail == null ? "texture-copy-unavailable" : pendingBackdropFallbackDetail);
        int tintAlpha = clampInt(18 + Math.max(0, blurRadius) * 2 + Math.round(Math.max(0.0F,
                saturation - 1.0F) * 16.0F), 18, 72);
        int highlightAlpha = clampInt(tintAlpha + 22, 32, 96);
        int tintColor = tintAlpha << 24 | 0x00FFFFFF;
        int highlightColor = highlightAlpha << 24 | 0x00FFFFFF;
        drawSurface(left, top, right, bottom, new UiSurfaceStyle(tintColor, highlightColor, cornerRadius));
    }

    /**
     * 绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     */
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, 0.0F);
        GL11.glScalef(UI_TEXT_SCALE, UI_TEXT_SCALE, 1.0F);
        fontRenderer.drawString(text, 0, 0, color, shadow);
        GL11.glPopMatrix();
        notifyMainLayerContentChanged();
    }

    /**
     * 绘制水平居中文本。
     *
     * @param text 文本
     * @param centerX 中心 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     */
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        int textWidth = fontRenderer.getStringWidth(text);
        drawText(text, centerX - Math.round(textWidth * UI_TEXT_SCALE / 2.0F), y, color, shadow);
    }

    public int measureTextWidth(String text) {
        return Math.round(fontRenderer.getStringWidth(text) * UI_TEXT_SCALE);
    }

    public int getTextLineHeight() {
        return Math.round(fontRenderer.getLineHeight() * UI_TEXT_SCALE);
    }

    /**
     * 延迟登记一批主渲染后的补充回放动作。
     *
     * @param replay 主渲染完成后要回放的动作
     */
    public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
        deferredPostMainPasses.add(new DeferredPostMainPass(Objects.requireNonNull(replay, "replay"),
                copyCurrentClipSnapshot()));
    }

    /**
     * 判断当前帧是否存在待回放的主后置补充绘制。
     *
     * @return 是否存在延迟回放
     */
    public boolean hasDeferredPostMainPasses() {
        return !deferredPostMainPasses.isEmpty();
    }

    /**
     * 取出并清空当前帧登记的主后置补充绘制。
     *
     * @return 当前帧延迟回放列表
     */
    public List<DeferredPostMainPass> drainDeferredPostMainPasses() {
        if (deferredPostMainPasses.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeferredPostMainPass> drainedPasses = new ArrayList<DeferredPostMainPass>(deferredPostMainPasses);
        deferredPostMainPasses.clear();
        return drainedPasses;
    }

    /**
     * 压入一个文档绘制上下文边界。
     *
     * <p>当前 opacity context 会通过离屏层做 group opacity 合成；FBO 不可用时调用方会降级为命令级 alpha。</p>
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param opacity 当前上下文的局部 opacity
     */
    public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {
        paintContextCompositor.pushPaintContext(screenWidth, screenHeight, left, top, right, bottom, opacity,
                copyCurrentClipSnapshot());
    }

    /**
     * 判断当前最近的 paint context 是否正在使用离屏层。
     *
     * @return 是否使用离屏层
     */
    public boolean isCurrentPaintContextLayerActive() {
        return paintContextCompositor.isCurrentLayerActive();
    }

    /**
     * 弹出最近压入的文档绘制上下文边界。
     */
    public void popPaintContext() {
        if (paintContextCompositor.popPaintContext()) {
            applyCurrentClip();
            notifyMainLayerContentChanged();
        }
    }

    public void pushClip(int left, int top, int right, int bottom) {
        pushClip(left, top, right, bottom, 0);
    }

    /**
     * 压入一个支持圆角的视觉裁剪区域。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param cornerRadius 圆角半径；为 0 时退化为普通矩形裁剪
     */
    public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
        int clipLeft = Math.max(0, Math.min(left, right));
        int clipTop = Math.max(0, Math.min(top, bottom));
        int clipRight = Math.min(screenWidth, Math.max(left, right));
        int clipBottom = Math.min(screenHeight, Math.max(top, bottom));

        if (!clipStack.isEmpty()) {
            int[] parent = clipStack.peek().clipRect;
            clipLeft = Math.max(clipLeft, parent[0]);
            clipTop = Math.max(clipTop, parent[1]);
            clipRight = Math.min(clipRight, parent[2]);
            clipBottom = Math.min(clipBottom, parent[3]);
        }

        if (clipRight < clipLeft) {
            clipRight = clipLeft;
        }
        if (clipBottom < clipTop) {
            clipBottom = clipTop;
        }

        clipStack.push(new ClipState(new int[] { clipLeft, clipTop, clipRight, clipBottom },
                clampCornerRadius(clipRight - clipLeft, clipBottom - clipTop, cornerRadius)));
        applyCurrentClip();
    }

    public void popClip() {
        if (!clipStack.isEmpty()) {
            clipStack.pop();
        }
        applyCurrentClip();
    }

    private ClipSnapshot copyCurrentClipSnapshot() {
        if (clipStack.isEmpty()) {
            return null;
        }

        int[] clip = clipStack.peek().clipRect;
        List<RoundedClipRegion> roundedClipRegions = new ArrayList<RoundedClipRegion>();
        Iterator<ClipState> iterator = clipStack.descendingIterator();
        while (iterator.hasNext()) {
            ClipState clipState = iterator.next();
            if (clipState.cornerRadius <= 0) {
                continue;
            }
            int[] clipRect = clipState.clipRect;
            roundedClipRegions.add(new RoundedClipRegion(clipRect[0], clipRect[1], clipRect[2], clipRect[3],
                    clipState.cornerRadius));
        }
        return new ClipSnapshot(new int[] { clip[0], clip[1], clip[2], clip[3] },
                Collections.unmodifiableList(roundedClipRegions));
    }

    private void applyCurrentClip() {
        applyClipSnapshot(copyCurrentClipSnapshot(), screenHeight);
    }

    /**
     * 将一份裁剪快照回放到当前 OpenGL 状态。
     *
     * <p>这里先继续沿用 scissor 处理矩形交集，只有真的出现圆角裁剪时才重建 stencil mask，
     * 这样半径为 0 的既有路径不会被额外改变。</p>
     *
     * @param clipSnapshot 裁剪快照；为空时清空当前裁剪状态
     * @param screenHeight 当前原生屏幕高度
     */
    public static void applyClipSnapshot(ClipSnapshot clipSnapshot, int screenHeight) {
        if (clipSnapshot == null || clipSnapshot.getClipRect() == null) {
            clearClipState();
            return;
        }

        int[] clipRect = clipSnapshot.getClipRect();
        int width = Math.max(0, clipRect[2] - clipRect[0]);
        int height = Math.max(0, clipRect[3] - clipRect[1]);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clipRect[0], screenHeight - clipRect[3], width, height);

        List<RoundedClipRegion> roundedClipRegions = clipSnapshot.getRoundedClipRegions();
        if (roundedClipRegions.isEmpty()) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            return;
        }

        rebuildRoundedClipMask(roundedClipRegions);
    }

    /**
     * 清空当前 OpenGL 裁剪状态。
     */
    public static void clearClipState() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
    }

    private static void rebuildRoundedClipMask(List<RoundedClipRegion> roundedClipRegions) {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        for (int index = 0; index < roundedClipRegions.size(); index++) {
            GL11.glStencilFunc(GL11.GL_EQUAL, index, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
            RoundedClipRegion clipRegion = roundedClipRegions.get(index);
            drawRoundedRectGeometry(clipRegion.getLeft(), clipRegion.getTop(), clipRegion.getRight(),
                    clipRegion.getBottom(), clipRegion.getCornerRadius(), true);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, roundedClipRegions.size(), 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    private void fillRoundedRect(int left, int top, int right, int bottom, int radius, int color) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawRoundedRectGeometry(left, top, right, bottom, radius, true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        notifyMainLayerContentChanged();
    }

    private void drawRoundedBorder(int left, int top, int right, int bottom, int radius, int color) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        // 线框边界若直接落在整数像素边缘，会让左/上侧描边有一半落在 clip 外，
        // 在真实运行时看起来像左侧边线被吞掉。这里统一把描边中心内缩到像素中心，
        // 让四条边都以同样的方式落在边框盒内部。
        drawRoundedRectGeometry(left + 0.5F, top + 0.5F, right - 0.5F, bottom - 0.5F, radius, false);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        notifyMainLayerContentChanged();
    }

    private static void drawRoundedRectGeometry(int left, int top, int right, int bottom, int radius, boolean filled) {
        drawRoundedRectGeometry((float) left, (float) top, (float) right, (float) bottom, (float) radius, filled);
    }

    private static void drawRoundedRectGeometry(float left, float top, float right, float bottom, float radius,
            boolean filled) {
        float resolvedRadius = clampCornerRadius(right - left, bottom - top, radius);
        if (resolvedRadius <= 0.0F) {
            if (filled) {
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(right, bottom);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(left, bottom);
                GL11.glEnd();
            } else {
                GL11.glVertex2f(left, bottom);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(right, bottom);
            }
            return;
        }

        if (filled) {
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f((left + right) / 2.0F, (top + bottom) / 2.0F);
        }
        emitRoundedRectVertices(left, top, right, bottom, resolvedRadius);
        if (filled) {
            GL11.glVertex2f(left, top + resolvedRadius);
            GL11.glEnd();
        }
    }

    private static void emitRoundedRectVertices(float left, float top, float right, float bottom, float radius) {
        emitArcVertices(left + radius, top + radius, radius, 180.0D, 270.0D, false);
        emitArcVertices(right - radius, top + radius, radius, 270.0D, 360.0D, true);
        emitArcVertices(right - radius, bottom - radius, radius, 0.0D, 90.0D, true);
        emitArcVertices(left + radius, bottom - radius, radius, 90.0D, 180.0D, true);
    }

    private static void emitArcVertices(float centerX, float centerY, float radius, double startAngle, double endAngle,
            boolean skipFirst) {
        int segments = resolveCornerSegments(radius);
        for (int index = skipFirst ? 1 : 0; index <= segments; index++) {
            double angle = Math.toRadians(startAngle + (endAngle - startAngle) * index / (double) segments);
            GL11.glVertex2f((float) (centerX + Math.cos(angle) * radius), (float) (centerY + Math.sin(angle) * radius));
        }
    }

    private static int resolveCornerSegments(float radius) {
        return Math.max(6, Math.min(18, Math.round(radius)));
    }

    private static int clampCornerRadius(int width, int height, int radius) {
        return Math.max(0, Math.min(radius, Math.min(Math.max(0, width), Math.max(0, height)) / 2));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int resolveBackdropSampleStep(int blurRadius) {
        return Math.max(1, Math.min(12, Math.round(Math.max(1, blurRadius) / 2.5F)));
    }

    private static String formatSnapshotState(UiMainLayerSnapshotService.Snapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        return (snapshot.isReused() ? "reused" : "captured") + " " + snapshot.getWidth() + "x"
                + snapshot.getHeight() + " @" + snapshot.getSampleLeft() + "," + snapshot.getSampleTop()
                + " fbo=" + snapshot.getReadFramebufferId() + " rev=" + snapshot.getContentRevision()
                + " region=" + snapshot.getRegionDetail() + " " + snapshot.getTileDetail()
                + " filter=" + snapshot.getFilterDetail();
    }

    private static float resolveBackdropShaderRadius(int blurRadius, int downsampleFactor) {
        if (blurRadius <= 0) {
            return 0.0F;
        }
        return Math.max(1.0F, Math.min(32.0F, (float) blurRadius * 0.75F
                / (float) Math.max(1, downsampleFactor)));
    }

    private static float resolveBackdropLodBias(int blurRadius, int downsampleFactor) {
        if (blurRadius <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(3.2F, (float) blurRadius
                / (14.0F * (float) Math.max(1, downsampleFactor))));
    }

    private static void recordBackdropFilterPath(BackdropFilterRenderPath renderPath, String detail) {
        lastBackdropFilterRenderPath = renderPath == null ? BackdropFilterRenderPath.NONE : renderPath;
        lastBackdropFilterDetail = detail == null ? "" : detail;
    }

    private static float clampCornerRadius(float width, float height, float radius) {
        return Math.max(0.0F, Math.min(radius, Math.min(Math.max(0.0F, width), Math.max(0.0F, height)) / 2.0F));
    }

    private void applyColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }
}
