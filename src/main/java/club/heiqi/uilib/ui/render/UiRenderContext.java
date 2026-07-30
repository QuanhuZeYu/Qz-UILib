package club.heiqi.uilib.ui.render;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.api.FontRendererAdapter;
import club.heiqi.uilib.internal.image.HostImageCacheCompositeGuard;
import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageGlStateGuard;
import club.heiqi.uilib.ui.image.HostImageRenderOutcome;
import club.heiqi.uilib.ui.image.HostImageRenderSession;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.image.ItemIconRenderer;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.base.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.base.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * UI 渲染上下文。
 *
 * <p>重构后协调以下协作者：</p>
 * <ul>
 *   <li>{@link PaintContextCompositor} 负责离屏 paint context group opacity 合成；</li>
 *   <li>{@link UiBackdropFilterRenderer} 负责 backdrop-filter 整条渲染链路；</li>
 *   <li>{@link UiRoundedRectGeometry} 负责圆角矩形几何（填充与描边路径）；</li>
 *   <li>剪切栈（{@code clipStack}）以及 scissor + stencil mask 应用在本类。</li>
 * </ul>
 */
public class UiRenderContext implements UiRenderBackend {

    private static final Logger HOST_IMAGE_LOG = LogManager.getLogger("QzUiLib/HostImage");
    private static final int MAX_ITEM_RASTER_SIZE = 32;

    /** 将 scene 图片源适配到既有 Minecraft 宿主图片渲染器。 */
    @Override
    public void drawImage(SceneImageSource source, int left, int top, int right, int bottom) {
        if (source instanceof HostImageSource) {
            drawHostImage((HostImageSource) source, left, top, right, bottom);
        }
    }

    private static final float UI_TEXT_SCALE = 2.0F;

    private final int screenWidth;
    private final int screenHeight;
    private final int mouseX;
    private final int mouseY;
    private final float partialTicks;
    private final FontRendererAdapter fontRenderer;
    private final PaintContextCompositor paintContextCompositor;
    private final UiMainLayerSnapshotService mainLayerSnapshotService;
    private final UiRuntimeAdapters runtimeAdapters;
    private final BackdropBlurPolicy backdropBlurPolicy;
    private final HostImageGlStateGuard itemIconStateGuard = new HostImageGlStateGuard();
    private final HostImageCacheCompositeGuard cacheCompositeStateGuard = new HostImageCacheCompositeGuard();
    /** 包内可见，便于同包测试关闭 GL 副作用 / 安装测试基线。 */
    final ClipStack clipStack = new ClipStack();
    private final DeferredPostMainPassQueue deferredPostMainPassQueue = new DeferredPostMainPassQueue();
    private int mainLayerContentRevision;

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
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty());
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
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty());
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
        this(screenWidth, screenHeight, mouseX, mouseY, partialTicks, paintContextCompositor,
                mainLayerSnapshotService, UiRuntimeAdapters.empty());
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
     * @param runtimeAdapters 运行时适配器集合
     */
    public UiRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService,
            UiRuntimeAdapters runtimeAdapters) {
        this(screenWidth, screenHeight, mouseX, mouseY, partialTicks, paintContextCompositor,
                mainLayerSnapshotService, runtimeAdapters, BackdropBlurPolicy.inheritGlobal());
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
     * @param runtimeAdapters 运行时适配器集合
     * @param backdropBlurPolicy 页面级背景模糊策略
     */
    public UiRenderContext(int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService,
            UiRuntimeAdapters runtimeAdapters, BackdropBlurPolicy backdropBlurPolicy) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.fontRenderer = DefaultFontRendererAdapter.getInstance();
        this.paintContextCompositor = Objects.requireNonNull(paintContextCompositor, "paintContextCompositor");
        this.mainLayerSnapshotService = Objects.requireNonNull(mainLayerSnapshotService,
                "mainLayerSnapshotService");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        this.backdropBlurPolicy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                : backdropBlurPolicy;
        // 在构造时捕获主 FB 当前 scissor/stencil，避免首次 pushClip 落在 FBO 内时抓到清空态
        clipStack.installHostBaseline(ClipStack.captureCurrentHostBaseline());
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
     * 判断当前上下文是否支持延迟文本批处理。
     *
     * <p>运行时默认支持；测试上下文可覆盖为 {@code false}，从而让文本按顺序回放而不进入真实字体批处理边界。</p>
     *
     * @return 是否支持延迟文本批处理
     */
    public boolean supportsDeferredTextBatching() {
        return true;
    }

    /**
     * 开始延迟文本批处理边界。
     *
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     */
    public void beginDeferredTextBatch(int targetWidth, int targetHeight) {
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            ((DefaultFontRendererAdapter) fontRenderer).beginDeferredFlushScope(targetWidth, targetHeight);
        }
    }

    /**
     * 刷新当前延迟文本批次，但不结束批处理边界。
     */
    public void flushDeferredTextBatch() {
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            ((DefaultFontRendererAdapter) fontRenderer).flushDeferredFlushScope();
        }
    }

    /**
     * 结束延迟文本批处理边界。
     */
    public void endDeferredTextBatch() {
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            ((DefaultFontRendererAdapter) fontRenderer).endDeferredFlushScope();
        }
    }

    /**
     * 返回当前运行时适配器集合。
     *
     * @return 运行时适配器集合
     */
    public UiRuntimeAdapters getRuntimeAdapters() {
        return runtimeAdapters;
    }

    /**
     * 返回当前渲染帧使用的页面级背景模糊策略。
     *
     * @return 背景模糊策略
     */
    public BackdropBlurPolicy getBackdropBlurPolicy() {
        return backdropBlurPolicy;
    }

    /**
     * 通知当前 UI 绘制目标已经发生内容写入。
     *
     * <p>内置的 surface、text、backdrop 与 paint context 合成路径会自动调用该方法；
     * 自定义渲染器如果绕过这些封装直接写入 OpenGL，也应在写入后调用，避免后续
     * {@code backdrop-filter} 继续复用写入前的旧快照。</p>
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
     * 返回当前主层快照服务，供 backdrop-filter 协作者读取。
     */
    UiMainLayerSnapshotService getMainLayerSnapshotService() {
        return mainLayerSnapshotService;
    }

    /**
     * 返回当前 paint context 合成器中可用的 backdrop 读取 framebuffer id。
     */
    int getCurrentBackdropReadFramebufferId() {
        return paintContextCompositor.getCurrentBackdropReadFramebufferId();
    }

    /**
     * 返回最近一次 backdrop-filter 实际渲染路径。
     *
     * @return 渲染路径
     */
    public static BackdropFilterRenderPath getLastBackdropFilterRenderPath() {
        return UiBackdropFilterRenderer.getLastRenderPath();
    }

    /**
     * 返回最近一次 backdrop-filter 诊断说明。
     *
     * @return 诊断说明
     */
    public static String getLastBackdropFilterDetail() {
        return UiBackdropFilterRenderer.getLastDetail();
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
        UiContextGlHelpers.applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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

        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = UiRoundedRectGeometry.resolveCornerRadii(surfaceStyle,
                right - left, bottom - top);
        int cornerMask = surfaceStyle.cornerMask;
        if (surfaceStyle.fillColor != 0) {
            if (!UiRoundedRectGeometry.hasAnyCornerRadius(cornerRadii) || cornerMask == 0) {
                fillRect(left, top, right, bottom, surfaceStyle.fillColor);
            } else {
                UiContextGlHelpers.fillRoundedRect(left, top, right, bottom, cornerRadii, cornerMask,
                    surfaceStyle.fillColor, this::notifyMainLayerContentChanged);
            }
        }
        if (surfaceStyle.borderColor != 0) {
            if (!UiRoundedRectGeometry.hasAnyCornerRadius(cornerRadii) || cornerMask == 0) {
                drawBorder(left, top, right, bottom, surfaceStyle.borderColor);
            } else {
                UiContextGlHelpers.drawRoundedBorder(left, top, right, bottom, cornerRadii, cornerMask,
                    surfaceStyle.borderColor, this::notifyMainLayerContentChanged);
            }
        }
    }

    /**
     * 绘制带圆角的表面（UiRenderBackend 接口实现，uniform 单值圆角）。
     *
     * <p>第 7 参为 {@code int cornerRadius}，避免 scene 回放器反向依赖 {@code ui.style}
     * 包的 {@code ResolvedCornerRadii} 类型（守不变量 I6）。render 层内部仍可自由使用
     * {@code ui.style}，这里把 uniform 单值转成 {@link UiSurfaceStyle} 所需的分角圆角结构。</p>
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param fillColor 填充颜色
     * @param borderColor 边框颜色
     * @param cornerRadius 圆角半径（uniform 单值）
     */
    public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            int cornerRadius) {
        drawSurface(left, top, right, bottom, new UiSurfaceStyle(fillColor, borderColor,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius)));
    }

    /**
     * 绘制带分角圆角的表面（render 层内部重载，供 backdrop-filter 等需要分角圆角的调用方使用）。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param fillColor 填充颜色
     * @param borderColor 边框颜色
     * @param cornerRadii 四角圆角
     */
    public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        drawSurface(left, top, right, bottom, new UiSurfaceStyle(fillColor, borderColor, cornerRadii));
    }

    /**
     * 绘制元素背后内容滤镜。
     *
     * <p>该入口只采样当前 UI 主层已经绘制到当前 framebuffer 的内容，不主动读取游戏世界 framebuffer。
     * 如果页面壳提前绘制了一张已模糊底图，它会作为普通 UI 背景被采样；否则只处理 UI 自身内容。
     * 当前实现优先使用 GLSL 对同帧 UI 主层快照纹理做平滑采样，失败时回退为固定管线近似 blur，
     * 快照复制或绘制不可用时再回退为伪玻璃 tint。诊断中的 {@code region=atlas-*} 表示当前采样复用了同帧
     * 已捕获且完整覆盖当前区域的较大 block 快照，{@code region=tile-atlas-*} 表示当前采样由多个 tile 组装，
     * {@code tiles=...} 用于观察 tile 覆盖计划和实际复用/复制数量。</p>
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
        UiBackdropFilterRenderer.render(this, left, top, right, bottom, blurRadius, saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius));
    }

    /**
     * 绘制带分角圆角的背后滤镜。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径
     * @param saturation 饱和度
     * @param cornerRadii 四角圆角
     */
    public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        UiBackdropFilterRenderer.render(this, left, top, right, bottom, blurRadius, saturation, cornerRadii);
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
        drawText(text, x, y, color, shadow, TextContentMode.UILIB_RAW);
    }

    /**
     * 按指定 UI 像素字号绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param fontSizePx UI 像素字号
     */
    public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
        drawText(text, x, y, color, shadow, TextMeasureStyle.fontSizePx(fontSizePx));
    }

    /**
     * 使用指定文本模式绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param textContentMode 文本内容解析模式
     */
    public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
        drawTextResolved(text, x, y, color, shadow, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
    }

    /**
     * 使用指定文本模式和基础字体样式绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     */
    public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle) {
        UiFontWeight resolvedFontWeight = fontWeight == null ? UiFontWeight.NORMAL : fontWeight;
        UiFontStyle resolvedFontStyle = fontStyle == null ? UiFontStyle.NORMAL : fontStyle;
        if (resolvedFontWeight == UiFontWeight.NORMAL && resolvedFontStyle == UiFontStyle.NORMAL) {
            drawText(text, x, y, color, shadow, textContentMode);
            return;
        }
        drawTextResolved(text, x, y, color, shadow, textContentMode, resolvedFontWeight, resolvedFontStyle);
    }

    /**
     * 使用语义化文本样式绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param textStyle 文本样式快照
     */
    public void drawText(String text, int x, int y, int color, boolean shadow, TextMeasureStyle textStyle) {
        TextMeasureStyle resolvedStyle = textStyle == null ? TextMeasureStyle.DEFAULT : textStyle;
        drawTextResolved(text, x, y, color, shadow, resolvedStyle);
    }

    /**
     * 使用已归一化的字体样式执行实际文本绘制。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param textContentMode 文本内容解析模式
     * @param resolvedFontWeight 已归一化字体粗细
     * @param resolvedFontStyle 已归一化字体样式
     */
    protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
            TextContentMode textContentMode, UiFontWeight resolvedFontWeight, UiFontStyle resolvedFontStyle) {
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            DefaultFontRendererAdapter defaultFontRenderer = (DefaultFontRendererAdapter) fontRenderer;
            if (defaultFontRenderer.isDeferredFlushScopeActive()) {
                defaultFontRenderer.drawBaselineAlignedStringScaled(text, x, y, color, shadow, textContentMode,
                        resolvedFontWeight, resolvedFontStyle, UI_TEXT_SCALE);
                notifyMainLayerContentChanged();
                return;
            }
        }

        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, 0.0F);
        GL11.glScalef(UI_TEXT_SCALE, UI_TEXT_SCALE, 1.0F);
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            ((DefaultFontRendererAdapter) fontRenderer).drawBaselineAlignedString(text, 0, 0, color, shadow,
                    textContentMode, resolvedFontWeight, resolvedFontStyle);
        } else {
            fontRenderer.drawBaselineAlignedString(text, 0, 0, color, shadow);
        }
        GL11.glPopMatrix();
        notifyMainLayerContentChanged();
    }

    /**
     * 使用已归一化的语义化文本样式执行实际文本绘制。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param resolvedStyle 文本样式快照
     */
    protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
            TextMeasureStyle resolvedStyle) {
        TextMeasureStyle safeStyle = resolvedStyle == null ? TextMeasureStyle.DEFAULT : resolvedStyle;
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            DefaultFontRendererAdapter defaultFontRenderer = (DefaultFontRendererAdapter) fontRenderer;
            defaultFontRenderer.drawBaselineAlignedStringPx(text, x, y, color, shadow, safeStyle);
            notifyMainLayerContentChanged();
            return;
        }

        float renderScale = safeStyle.getFontSizePx() / (float) Math.max(1, fontRenderer.getLineHeight());
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, 0.0F);
        GL11.glScalef(renderScale, renderScale, 1.0F);
        fontRenderer.drawBaselineAlignedString(text, 0, 0, color, shadow);
        GL11.glPopMatrix();
        notifyMainLayerContentChanged();
    }

    /**
     * 使用宿主图片渲染能力在指定区域绘制一张隔离贴图。
     *
     * <p>普通 texture/bitmap 在独立 FBO 中走轻量路径；ItemStack icon 则进入受预算保护的正方形
     * raster cache，只有 publishable 结果会按预乘 alpha 回贴到当前主层。</p>
     *
     * @param source 图片源
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    public void drawHostImage(HostImageSource source, int left, int top, int right, int bottom) {
        if (source == null || right <= left || bottom <= top) {
            return;
        }
        ClipSnapshot clipSnapshot = copyCurrentClipSnapshot();
        if (source.getKind() == HostImageSource.Kind.ITEM_ICON) {
            ItemIconGeometry geometry = resolveItemIconGeometry(left, top, right, bottom);
            if (!isVisibleInClip(clipSnapshot, geometry.destinationLeft, geometry.destinationTop,
                    geometry.destinationRight, geometry.destinationBottom)) {
                return;
            }
            abortIfLayerCleanupPending();
            drawCachedItemHostImage(source, left, top, right, bottom, clipSnapshot,
                    runtimeAdapters.getItemIconRenderer());
            return;
        }
        if (!isVisibleInClip(clipSnapshot, left, top, right, bottom)) {
            return;
        }
        abortIfLayerCleanupPending();
        HostImageRenderer hostImageRenderer = runtimeAdapters.getHostImageRenderer();
        if (hostImageRenderer == null) {
            return;
        }
        drawUncachedHostImage(source, left, top, right, bottom, clipSnapshot, hostImageRenderer);
    }

    private void drawCachedItemHostImage(HostImageSource source, int left, int top, int right, int bottom,
            ClipSnapshot clipSnapshot, ItemIconRenderer renderer) {
        ItemIconGeometry geometry = resolveItemIconGeometry(left, top, right, bottom);
        if (renderer == null) {
            drawHostImagePlaceholder(geometry.destinationLeft, geometry.destinationTop,
                    geometry.destinationRight, geometry.destinationBottom);
            return;
        }
        HostImageRenderSession.RequestResult result = paintContextCompositor.getHostImageRenderSession().request(
                source, geometry.rasterSide,
                (itemSource, rasterSide) -> rasterizeItem(itemSource, rasterSide, renderer));
        if (result.getStatus() == HostImageRenderSession.RequestResult.Status.ABORT_FRAME) {
            HostImageRenderOutcome outcome = result.getOutcome();
            logHostImageFailure(source, outcome);
            throw new UiRenderFrameAbortException("HostImage GL state recovery failed at "
                    + (outcome == null ? "unknown" : outcome.getStage()), outcome == null ? null : outcome.getFailure());
        }
        if (result.getStatus() == HostImageRenderSession.RequestResult.Status.UNAVAILABLE) {
            if (shouldLogHostImageFailure(result.getStatus(), result.getOutcome())) {
                logHostImageFailure(source, result.getOutcome());
            }
        }
        applyClipSnapshot(clipSnapshot, screenHeight);
        if (result.getRaster() instanceof UiRenderTarget) {
            UiRenderTarget raster = (UiRenderTarget) result.getRaster();
            HostImageRenderOutcome compositeOutcome = cacheCompositeStateGuard.run(() ->
                    raster.compositeCachedTextureGuarded(
                            geometry.destinationLeft, geometry.destinationTop,
                            geometry.destinationRight, geometry.destinationBottom));
            if (compositeOutcome.isHostStateLost()) {
                logHostImageFailure(source, compositeOutcome);
                throw new UiRenderFrameAbortException("Cached HostImage GL state recovery failed at "
                        + compositeOutcome.getStage(), compositeOutcome.getFailure());
            }
            if (compositeOutcome.isPublishable()) {
                notifyMainLayerContentChanged();
            } else {
                drawHostImagePlaceholder(geometry.destinationLeft, geometry.destinationTop,
                        geometry.destinationRight, geometry.destinationBottom);
            }
        } else {
            drawHostImagePlaceholder(geometry.destinationLeft, geometry.destinationTop,
                    geometry.destinationRight, geometry.destinationBottom);
        }
    }

    private HostImageRenderSession.RasterizeResult rasterizeItem(HostImageSource source, int rasterSide,
            ItemIconRenderer renderer) {
        UiRenderTarget target = new UiRenderTarget();
        try {
            final HostImageRenderOutcome[] transactionOutcome = new HostImageRenderOutcome[1];
            HostImageRenderOutcome guardOutcome = itemIconStateGuard.run(() ->
                    transactionOutcome[0] = renderItemTransaction(target, source, rasterSide, renderer));
            HostImageRenderOutcome outcome = guardOutcome.isPublishable()
                    ? transactionOutcome[0]
                    : guardOutcome;
            return new HostImageRenderSession.RasterizeResult(target, outcome == null
                    ? HostImageRenderOutcome.unavailable("render", null, "missing-outcome") : outcome);
        } catch (RuntimeException failure) {
            return new HostImageRenderSession.RasterizeResult(target,
                    HostImageRenderOutcome.hostStateLost(
                            "rasterize", failure, "rasterize-exception"));
        } catch (LinkageError failure) {
            return new HostImageRenderSession.RasterizeResult(target,
                    HostImageRenderOutcome.hostStateLost(
                            "rasterize", failure, "rasterize-linkage"));
        } catch (Error failure) {
            try {
                paintContextCompositor.discardIsolatedLayer(target);
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            } catch (Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** 完整 FBO 事务体；外层 guard 在本方法返回后统一恢复、验错并决定是否可发布。 */
    private HostImageRenderOutcome renderItemTransaction(UiRenderTarget target, HostImageSource source,
            int rasterSide, ItemIconRenderer renderer) {
        boolean begun = false;
        boolean matrixModeCaptured = false;
        int previousMatrixMode = GL11.GL_MODELVIEW;
        boolean projectionPushed = false;
        boolean modelviewPushed = false;
        HostImageRenderOutcome delegateOutcome = null;
        Throwable transactionFailure = null;
        Throwable cleanupFailure = null;
        Error fatalFailure = null;
        try {
            target.ensureSize(rasterSide, rasterSide);
            previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            matrixModeCaptured = true;
            target.beginWithoutAttrib();
            begun = true;
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, rasterSide, rasterSide, 0.0D, -1000.0D, 1000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelviewPushed = true;
            GL11.glLoadIdentity();
            clearClipState();
            try {
                delegateOutcome = renderer.render(source.getItemIconStack(), 0, 0, rasterSide);
            } catch (RuntimeException failure) {
                delegateOutcome = HostImageRenderOutcome.unavailable(
                        "render", failure, "renderer-failed");
            } catch (LinkageError failure) {
                delegateOutcome = HostImageRenderOutcome.unavailable(
                        "render", failure, "renderer-linkage");
            } catch (Error failure) {
                fatalFailure = failure;
            }
        } catch (RuntimeException exception) {
            transactionFailure = exception;
        } catch (LinkageError error) {
            transactionFailure = error;
        } catch (Error error) {
            if (fatalFailure == null) fatalFailure = error;
            else if (fatalFailure != error) fatalFailure.addSuppressed(error);
        } finally {
            if (modelviewPushed) {
                try {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                } catch (RuntimeException failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (LinkageError failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (Error failure) {
                    if (fatalFailure == null) fatalFailure = failure;
                    else if (fatalFailure != failure) fatalFailure.addSuppressed(failure);
                }
            }
            if (projectionPushed) {
                try {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                } catch (RuntimeException failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (LinkageError failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (Error failure) {
                    if (fatalFailure == null) fatalFailure = failure;
                    else if (fatalFailure != failure) fatalFailure.addSuppressed(failure);
                }
            }
            if (matrixModeCaptured) {
                try {
                    GL11.glMatrixMode(previousMatrixMode);
                } catch (RuntimeException failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (LinkageError failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (Error failure) {
                    if (fatalFailure == null) fatalFailure = failure;
                    else if (fatalFailure != failure) fatalFailure.addSuppressed(failure);
                }
            }
            if (begun) {
                try {
                    target.end();
                } catch (RuntimeException failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (LinkageError failure) {
                    cleanupFailure = preferCleanupFailure(cleanupFailure, failure);
                } catch (Error failure) {
                    if (fatalFailure == null) fatalFailure = failure;
                    else if (fatalFailure != failure) fatalFailure.addSuppressed(failure);
                }
            }
        }
        if (fatalFailure != null) {
            if (cleanupFailure != null && cleanupFailure != fatalFailure) {
                fatalFailure.addSuppressed(cleanupFailure);
            }
            if (transactionFailure != null && transactionFailure != fatalFailure) {
                fatalFailure.addSuppressed(transactionFailure);
            }
            throw fatalFailure;
        }
        if (cleanupFailure != null) {
            if (transactionFailure != null && transactionFailure != cleanupFailure) {
                cleanupFailure.addSuppressed(transactionFailure);
            }
            return HostImageRenderOutcome.hostStateLost(
                    "fbo-restore", cleanupFailure, "transaction-restore");
        }
        if (transactionFailure != null) {
            return HostImageRenderOutcome.hostStateLost(
                    "fbo-render", transactionFailure, "transaction-failed");
        }
        return delegateOutcome == null
                ? HostImageRenderOutcome.unavailable("render", null, "missing-delegate-outcome")
                : delegateOutcome;
    }

    private void drawUncachedHostImage(HostImageSource source, int left, int top, int right, int bottom,
            ClipSnapshot clipSnapshot, HostImageRenderer hostImageRenderer) {
        int entryGlError = consumeFirstGlError();
        if (entryGlError != GL11.GL_NO_ERROR) {
            throw new UiRenderFrameAbortException(
                    "Plain HostImage entered with GL error " + entryGlError, null);
        }
        UiRenderTarget layer = null;
        boolean begun = false;
        boolean projectionPushed = false;
        boolean modelviewPushed = false;
        int previousMatrixMode = GL11.GL_MODELVIEW;
        Throwable delegateFailure = null;
        Throwable transactionFailure = null;
        Error fatalFailure = null;
        try {
            layer = paintContextCompositor.borrowIsolatedLayer(screenWidth, screenHeight);
            previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            layer.begin();
            begun = true;
            try {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                projectionPushed = true;
                GL11.glLoadIdentity();
                GL11.glOrtho(0.0D, screenWidth, screenHeight, 0.0D, -1000.0D, 1000.0D);
                try {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPushMatrix();
                    modelviewPushed = true;
                    GL11.glLoadIdentity();
                    clearClipState();
                    applyClipSnapshot(clipSnapshot, screenHeight);
                    try {
                        hostImageRenderer.render(source, left, top, right, bottom);
                    } catch (RuntimeException failure) {
                        delegateFailure = failure;
                    } catch (LinkageError failure) {
                        delegateFailure = failure;
                    } catch (Error failure) {
                        fatalFailure = failure;
                    }
                    clearClipState();
                } finally {
                    boolean modelviewModeReady = false;
                    try {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        modelviewModeReady = true;
                    } catch (RuntimeException cleanupFailure) {
                        transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                    } catch (LinkageError cleanupFailure) {
                        transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                    } catch (Error cleanupFailure) {
                        if (fatalFailure == null) fatalFailure = cleanupFailure;
                        else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                    }
                    if (modelviewPushed && modelviewModeReady) {
                        try {
                            GL11.glPopMatrix();
                            modelviewPushed = false;
                        } catch (RuntimeException cleanupFailure) {
                            transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                        } catch (LinkageError cleanupFailure) {
                            transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                        } catch (Error cleanupFailure) {
                            if (fatalFailure == null) fatalFailure = cleanupFailure;
                            else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                        }
                    }
                }
            } finally {
                boolean projectionModeReady = false;
                try {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    projectionModeReady = true;
                } catch (RuntimeException cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (LinkageError cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (Error cleanupFailure) {
                    if (fatalFailure == null) fatalFailure = cleanupFailure;
                    else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                }
                if (projectionPushed && projectionModeReady) {
                    try {
                        GL11.glPopMatrix();
                        projectionPushed = false;
                    } catch (RuntimeException cleanupFailure) {
                        transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                    } catch (LinkageError cleanupFailure) {
                        transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                    } catch (Error cleanupFailure) {
                        if (fatalFailure == null) fatalFailure = cleanupFailure;
                        else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                    }
                }
                try {
                    GL11.glMatrixMode(previousMatrixMode);
                } catch (RuntimeException cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (LinkageError cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (Error cleanupFailure) {
                    if (fatalFailure == null) fatalFailure = cleanupFailure;
                    else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                }
            }
            if (fatalFailure != null) throw fatalFailure;
            rethrowDelegateFailure(transactionFailure);
            layer.end();
            begun = false;
            applyClipSnapshot(clipSnapshot, screenHeight);
            int renderGlError = consumeFirstGlError();
            if (renderGlError != GL11.GL_NO_ERROR) {
                transactionFailure = new IllegalStateException(
                        "Plain HostImage render GL error=" + renderGlError);
            }
            if (delegateFailure == null && transactionFailure == null && fatalFailure == null) {
                layer.compositeToCurrentFramebuffer(left, top, right, bottom, 1.0F);
                notifyMainLayerContentChanged();
            }
        } catch (RuntimeException failure) {
            transactionFailure = failure;
        } catch (LinkageError failure) {
            transactionFailure = failure;
        } catch (Error failure) {
            if (fatalFailure == null) {
                fatalFailure = failure;
            } else if (fatalFailure != failure) {
                fatalFailure.addSuppressed(failure);
            }
        } finally {
            try {
                if (layer != null && begun) {
                    layer.end();
                }
            } catch (RuntimeException cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (LinkageError cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (Error cleanupFailure) {
                if (fatalFailure == null) fatalFailure = cleanupFailure;
                else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
            }
            try {
                applyClipSnapshot(clipSnapshot, screenHeight);
            } catch (RuntimeException cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (LinkageError cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (Error cleanupFailure) {
                if (fatalFailure == null) fatalFailure = cleanupFailure;
                else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
            }
            try {
                int cleanupGlError = consumeFirstGlError();
                if (cleanupGlError != GL11.GL_NO_ERROR) {
                    transactionFailure = preferCleanupFailure(transactionFailure,
                            new IllegalStateException("Plain HostImage cleanup GL error=" + cleanupGlError));
                }
            } catch (RuntimeException cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (LinkageError cleanupFailure) {
                transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
            } catch (Error cleanupFailure) {
                if (fatalFailure == null) fatalFailure = cleanupFailure;
                else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
            }
            if (layer != null) {
                try {
                    if (transactionFailure == null && fatalFailure == null) {
                        paintContextCompositor.releaseIsolatedLayer(layer);
                    } else {
                        paintContextCompositor.discardIsolatedLayer(layer);
                    }
                } catch (RuntimeException cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (LinkageError cleanupFailure) {
                    transactionFailure = preferCleanupFailure(transactionFailure, cleanupFailure);
                } catch (Error cleanupFailure) {
                    if (fatalFailure == null) fatalFailure = cleanupFailure;
                    else if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (fatalFailure != null) {
            if (transactionFailure != null && transactionFailure != fatalFailure) {
                fatalFailure.addSuppressed(transactionFailure);
            }
            throw fatalFailure;
        }
        if (transactionFailure != null) {
            if (delegateFailure != null && delegateFailure != transactionFailure) {
                transactionFailure.addSuppressed(delegateFailure);
            }
            throw new UiRenderFrameAbortException(
                    "Plain HostImage transaction could not restore host state", transactionFailure);
        }
        rethrowDelegateFailure(delegateFailure);
    }

    private static Throwable preferCleanupFailure(Throwable previousFailure, Throwable cleanupFailure) {
        if (previousFailure != null && previousFailure != cleanupFailure) {
            cleanupFailure.addSuppressed(previousFailure);
        }
        return cleanupFailure;
    }

    private void abortIfLayerCleanupPending() {
        Throwable cleanupFailure = paintContextCompositor.getPendingLayerCleanupFailure();
        if (cleanupFailure != null) {
            throw new UiRenderFrameAbortException(
                    "HostImage render target cleanup retry failed", cleanupFailure);
        }
    }

    private static void rethrowDelegateFailure(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof LinkageError) throw (LinkageError) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new IllegalStateException("plain HostImage renderer failed", failure);
    }

    /** 排空当前 GL error queue 并返回首错，避免不可信 plain renderer 污染宿主后续绘制。 */
    private static int consumeFirstGlError() {
        int first = GL11.GL_NO_ERROR;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            if (first == GL11.GL_NO_ERROR) {
                first = error;
            }
        }
        return first;
    }

    static boolean isVisibleInClip(ClipSnapshot snapshot, int left, int top, int right, int bottom) {
        if (snapshot == null || snapshot.getClipRect() == null) return true;
        int[] clip = snapshot.getClipRect();
        return right > clip[0] && left < clip[2] && bottom > clip[1] && top < clip[3];
    }

    /** 将任意目标矩形解析为居中的 item destination square 与受 cap 限制的 square raster。 */
    static ItemIconGeometry resolveItemIconGeometry(int left, int top, int right, int bottom) {
        int targetWidth = Math.max(0, right - left);
        int targetHeight = Math.max(0, bottom - top);
        int destinationSide = Math.min(targetWidth, targetHeight);
        int destinationLeft = left + (targetWidth - destinationSide) / 2;
        int destinationTop = top + (targetHeight - destinationSide) / 2;
        return new ItemIconGeometry(destinationLeft, destinationTop, destinationSide,
                Math.min(destinationSide, MAX_ITEM_RASTER_SIZE));
    }

    /** 纯数值 item icon 几何，供渲染路径与纯 JVM 测试共享。 */
    static final class ItemIconGeometry {
        private final int destinationLeft;
        private final int destinationTop;
        private final int destinationRight;
        private final int destinationBottom;
        private final int rasterSide;

        private ItemIconGeometry(int destinationLeft, int destinationTop, int destinationSide, int rasterSide) {
            this.destinationLeft = destinationLeft;
            this.destinationTop = destinationTop;
            this.destinationRight = destinationLeft + destinationSide;
            this.destinationBottom = destinationTop + destinationSide;
            this.rasterSide = rasterSide;
        }

        int getDestinationLeft() { return destinationLeft; }
        int getDestinationTop() { return destinationTop; }
        int getDestinationRight() { return destinationRight; }
        int getDestinationBottom() { return destinationBottom; }
        int getRasterSide() { return rasterSide; }
    }

    private void drawHostImagePlaceholder(int left, int top, int right, int bottom) {
        fillRect(left, top, right, bottom, 0x55383838);
        int size = Math.min(right - left, bottom - top);
        if (size >= 4) {
            fillRect(left, top, right, top + 1, 0x889A9A9A);
            fillRect(left, bottom - 1, right, bottom, 0x889A9A9A);
        }
    }

    private static void logHostImageFailure(HostImageSource source, HostImageRenderOutcome outcome) {
        net.minecraft.item.ItemStack stack = source.getItemIconStack();
        Object registry = stack == null || stack.getItem() == null ? "unknown"
                : net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        int meta = stack == null ? -1 : stack.getItemDamage();
        String stage = outcome == null ? "unknown" : outcome.getStage();
        String detail = outcome == null ? "missing-outcome" : outcome.getDetail();
        Object status = outcome == null ? "unknown" : outcome.getStatus();
        HOST_IMAGE_LOG.warn("HostImage failure kind={} registry={} meta={} stage={} error/drift={} status={}",
                source.getKind(), registry, meta, stage, detail, status);
    }

    /**
     * 只有真实栅格尝试产生 outcome 时才记录 unavailable；typed cooldown 不重复打印。
     *
     * @param status 会话请求状态
     * @param outcome 栅格尝试结果
     * @return 是否应记录详细 warning
     */
    static boolean shouldLogHostImageFailure(HostImageRenderSession.RequestResult.Status status,
            HostImageRenderOutcome outcome) {
        return status == HostImageRenderSession.RequestResult.Status.UNAVAILABLE
                && outcome != null
                && !"cooldown".equals(outcome.getStage());
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
        drawCenteredText(text, centerX, y, color, shadow, TextContentMode.UILIB_RAW);
    }

    /**
     * 使用指定文本模式绘制水平居中文本。
     *
     * @param text 文本
     * @param centerX 中心 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param textContentMode 文本内容解析模式
     */
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow,
            TextContentMode textContentMode) {
        int textWidth = measureTextWidth(text, textContentMode);
        drawText(text, centerX - Math.round(textWidth / 2.0F), y, color, shadow, textContentMode);
    }

    public int measureTextWidth(String text) {
        return measureTextWidth(text, TextContentMode.UILIB_RAW);
    }

    /**
     * 使用指定文本模式测量文本宽度。
     *
     * @param text 文本
     * @param textContentMode 文本内容解析模式
     * @return UI 坐标系下的文本宽度
     */
    public int measureTextWidth(String text, TextContentMode textContentMode) {
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            return Math.round(((DefaultFontRendererAdapter) fontRenderer).getStringWidth(text, textContentMode)
                    * UI_TEXT_SCALE);
        }
        return Math.round(fontRenderer.getStringWidth(text) * UI_TEXT_SCALE);
    }

    /**
     * 计算文本按码点边界切分的 UI 像素前缀宽度向量。
     *
     * <p>返回数组长度为该文本码点数 + 1，元素 {@code i} 等于该文本前 {@code i} 个码点子串在
     * {@code UILIB_RAW} 语义下的 UI 像素宽度，与逐次 {@link #measureTextWidth(String)} 数值一致。该方法供
     * 文本控件的视觉行布局复用，避免每帧对每个码点 {@code measureTextWidth(substring)} 造成的 O(N²) 测量。</p>
     *
     * <p>纯生产上下文（未被子类覆盖测量行为）走底层 {@code DefaultFontRendererAdapter} 的单趟 O(N) 累加；
     * 任何子类（含测试替身）一旦覆盖测量语义，则回退到基于虚 {@link #measureTextWidth(String, TextContentMode)}
     * 的逐前缀实现，保证前缀向量始终与该上下文的 {@code measureTextWidth} 自洽。</p>
     *
     * @param text 文本；为 {@code null} 或空串时返回 {@code {0}}
     * @return UI 像素坐标系下的前缀宽度向量
     */
    public int[] measurePrefixWidths(String text) {
        if (text == null || text.isEmpty()) {
            return new int[] {0};
        }
        if (getClass() == UiRenderContext.class && fontRenderer instanceof DefaultFontRendererAdapter) {
            int[] rawWidths = ((DefaultFontRendererAdapter) fontRenderer).prefixWidthsRaw(text, UiFontWeight.NORMAL,
                    UiFontStyle.NORMAL);
            int[] uiWidths = new int[rawWidths.length];
            for (int index = 0; index < rawWidths.length; index++) {
                uiWidths[index] = Math.round(rawWidths[index] * UI_TEXT_SCALE);
            }
            return uiWidths;
        }
        int codePointCount = text.codePointCount(0, text.length());
        int[] widths = new int[codePointCount + 1];
        widths[0] = 0;
        int currentOffset = 0;
        for (int index = 1; index <= codePointCount; index++) {
            currentOffset = text.offsetByCodePoints(currentOffset, 1);
            widths[index] = measureTextWidth(text.substring(0, currentOffset), TextContentMode.UILIB_RAW);
        }
        return widths;
    }

    /**
     * 使用语义化文本样式测量文本宽度。
     *
     * @param text 文本
     * @param textStyle 文本样式快照
     * @return UI 坐标系下的文本宽度
     */
    public int measureTextWidth(String text, TextMeasureStyle textStyle) {
        TextMeasureStyle resolvedStyle = textStyle == null ? TextMeasureStyle.DEFAULT : textStyle;
        if (fontRenderer instanceof DefaultFontRendererAdapter) {
            DefaultFontRendererAdapter defaultFontRenderer = (DefaultFontRendererAdapter) fontRenderer;
            return defaultFontRenderer.getStringWidth(text, resolvedStyle);
        }
        int rawWidth = fontRenderer.getStringWidth(text);
        return Math.round(rawWidth * resolvedStyle.getFontSizePx() / (float) Math.max(1, fontRenderer.getLineHeight()));
    }

    public int getTextLineHeight() {
        return Math.round(fontRenderer.getLineHeight() * UI_TEXT_SCALE);
    }

    /**
     * 获取文本测量缓存失效纪元。
     *
     * <p>透传底层字体适配器的测量纪元，供文本控件的视觉行布局缓存判断字体运行时是否变化。</p>
     *
     * @return 文本测量纪元
     */
    public int getTextMeasureEpoch() {
        return fontRenderer.getTextMeasureEpoch();
    }

    /**
     * 延迟登记一批主渲染后的补充回放动作。
     *
     * @param replay 主渲染完成后要回放的动作
     */
    public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
        deferredPostMainPassQueue.enqueue(replay, copyCurrentClipSnapshot());
    }

    /**
     * 延迟登记一批主渲染后的顶层 overlay 回放动作。
     *
     * <p>该路径用于 tooltip、鼠标携带物品这类应覆盖在页面内容之上、且不应被槽位卡片局部裁剪的运行时叠层。</p>
     *
     * @param replay 主渲染完成后要回放的顶层动作
     */
    public void enqueueDeferredPostMainOverlayPass(DeferredPostMainPassReplay replay) {
        deferredPostMainPassQueue.enqueueOverlay(replay);
    }

    /**
     * 判断当前帧是否存在待回放的主后置补充绘制。
     *
     * @return 是否存在延迟回放
     */
    public boolean hasDeferredPostMainPasses() {
        return deferredPostMainPassQueue.hasPasses();
    }

    /**
     * 取出并清空当前帧登记的主后置补充绘制。
     *
     * @return 当前帧延迟回放列表
     */
    public List<DeferredPostMainPass> drainDeferredPostMainPasses() {
        return deferredPostMainPassQueue.drain();
    }

    /**
     * 进入 group opacity 合成作用域。
     *
     * <p>当前 opacity context 会通过离屏层做 group opacity 合成；FBO 不可用时调用方会降级为命令级 alpha。</p>
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param opacity 当前上下文的局部 opacity
     */
    public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
        paintContextCompositor.pushGroupOpacity(screenWidth, screenHeight, left, top, right, bottom, opacity,
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
     * 退出 group opacity 合成作用域，与 {@link #pushGroupOpacity} 严格配对。
     */
    public void popGroupOpacity() {
        if (paintContextCompositor.popGroupOpacity()) {
            applyCurrentClip();
            notifyMainLayerContentChanged();
        }
    }

    /**
     * 压入文档元素 transform 矩阵。
     *
     * @param transform transform 值
     * @param left 元素 border box 左边界
     * @param top 元素 border box 上边界
     * @param right 元素 border box 右边界
     * @param bottom 元素 border box 下边界
     */
    public void pushTransform(UiTransform transform, int left, int top, int right, int bottom) {
        UiTransform resolvedTransform = transform == null ? UiTransform.identity() : transform;
        GL11.glPushMatrix();
        float originX = left + resolvedTransform.resolveOriginX(Math.max(0, right - left));
        float originY = top + resolvedTransform.resolveOriginY(Math.max(0, bottom - top));
        GL11.glTranslatef(originX + resolvedTransform.getTranslateX(),
                originY + resolvedTransform.getTranslateY(), 0.0F);
        GL11.glRotatef(resolvedTransform.getRotateDegrees(), 0.0F, 0.0F, 1.0F);
        GL11.glScalef(resolvedTransform.getScaleX(), resolvedTransform.getScaleY(), 1.0F);
        GL11.glTranslatef(-originX, -originY, 0.0F);
    }

    /**
     * 纯数值 pushTransform 重载（I6 让步，全 primitive，零 scene/DOM 概念）。
     *
     * <p>与 opacity 的 {@link #pushGroupOpacity} 同构，供 ScenePaintReplayer 调用，
     * 不暴露 UiTransform/Transform 类型。origin 三明治：先移到 origin+translate，
     * 再 rotate/scale，再反移——translate 是在 origin 坐标系内的偏移，与直觉语义一致。</p>
     *
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     * @param left          绝对左边界（像素）
     * @param top           绝对上边界（像素）
     * @param right         绝对右边界（像素）
     * @param bottom        绝对下边界（像素）
     */
    public void pushTransform(float translateX, float translateY, float rotateDegrees,
                              float scaleX, float scaleY, float originXRatio, float originYRatio,
                              int left, int top, int right, int bottom) {
        GL11.glPushMatrix();
        float originX = left + originXRatio * (right - left);
        float originY = top + originYRatio * (bottom - top);
        GL11.glTranslatef(originX + translateX, originY + translateY, 0.0f);
        GL11.glRotatef(rotateDegrees, 0.0f, 0.0f, 1.0f);
        GL11.glScalef(scaleX, scaleY, 1.0f);
        GL11.glTranslatef(-originX, -originY, 0.0f);
    }

    /**
     * 弹出最近压入的文档元素 transform 矩阵。
     */
    public void popTransform() {
        GL11.glPopMatrix();
    }

    /**
     * 进入 transform 离屏图层作用域（B6 FBO 方案，transform+clip 叠加正确处理）。
     *
     * <p>内部借 FBO 离屏层 + MODELVIEW 归 I + 重建父 clip，使段内 scissor 在未变换坐标系下
     * 轴对齐正确裁剪。FBO 不可用时降级为「保留 clip 放弃 transform」。</p>
     *
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     * @param left          绝对左边界（像素）
     * @param top           绝对上边界（像素）
     * @param right         绝对右边界（像素）
     * @param bottom        绝对下边界（像素）
     */
    public void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
                                   float scaleX, float scaleY, float originXRatio, float originYRatio,
                                   int left, int top, int right, int bottom) {
        paintContextCompositor.pushTransformLayer(screenWidth, screenHeight, left, top, right, bottom,
                translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio,
                copyCurrentClipSnapshot());
    }

    /**
     * 退出 transform 离屏图层作用域，与 {@link #pushTransformLayer} 严格配对。
     */
    public void popTransformLayer() {
        if (paintContextCompositor.popTransformLayer()) {
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
        pushClip(left, top, right, bottom, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius));
    }

    /**
     * 压入一个支持分角圆角的视觉裁剪区域。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param cornerRadii 四角圆角
     */
    public void pushClip(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        // clip 变更前先 flush，避免 deferred text batch 跨 scissor 边界提交到错误状态
        flushDeferredTextBatch();
        clipStack.push(left, top, right, bottom, screenWidth, screenHeight, cornerRadii);
        applyCurrentClip();
    }

    public void popClip() {
        flushDeferredTextBatch();
        clipStack.pop();
        applyCurrentClip();
    }

    private ClipSnapshot copyCurrentClipSnapshot() {
        return clipStack.copySnapshot();
    }

    /**
     * 将当前 clip 栈应用到 GL：栈非空用栈顶；栈空则恢复宿主 scissor/stencil 基线。
     */
    private void applyCurrentClip() {
        clipStack.applyCurrent(screenHeight);
    }

    /**
     * 将一份裁剪快照回放到当前 OpenGL 状态。
     *
     * <p>这里先继续沿用 scissor 处理矩形交集，只有真的出现圆角裁剪时才重建 stencil mask，
     * 这样半径为 0 的既有路径不会被额外改变。</p>
     *
     * <p>{@code clipSnapshot == null} 时语义是<strong>强制清空</strong>当前裁切（供 FBO/deferred
     * 回放），不会恢复进入 uilib 前的宿主 scissor 基线。宿主基线恢复仅由实例路径
     * （{@link #popClip} / {@code popGroupOpacity} / {@code popTransformLayer} 等）
     * 在 clip 栈空时经 {@link ClipStack#applyCurrent(int)} 幂等完成。</p>
     *
     * @param clipSnapshot 裁剪快照；为空时清空当前裁剪状态
     * @param screenHeight 当前原生屏幕高度
     */
    public static void applyClipSnapshot(ClipSnapshot clipSnapshot, int screenHeight) {
        ClipStack.applySnapshot(clipSnapshot, screenHeight);
    }

    /**
     * 强制清空当前 OpenGL 裁剪状态（关闭 scissor/stencil）。
     *
     * <p>与 {@link #applyClipSnapshot}(null, …) 同语义，用于 FBO/deferred 回放前的干净起点；
     * 不是恢复宿主进入 uilib 前的 scissor 基线。</p>
     */
    public static void clearClipState() {
        ClipStack.clearState();
    }

}
