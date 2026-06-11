package club.heiqi.uilib.ui.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.api.FontRendererAdapter;
import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;

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
public class UiRenderContext {

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
    private final ClipStack clipStack = new ClipStack();
    private final List<DeferredPostMainPass> deferredPostMainPasses = new ArrayList<DeferredPostMainPass>();
    private final List<DeferredPostMainPass> deferredPostMainOverlayPasses = new ArrayList<DeferredPostMainPass>();
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
        applyColor(color);
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
                fillRoundedRect(left, top, right, bottom, cornerRadii, cornerMask, surfaceStyle.fillColor);
            }
        }
        if (surfaceStyle.borderColor != 0) {
            if (!UiRoundedRectGeometry.hasAnyCornerRadius(cornerRadii) || cornerMask == 0) {
                drawBorder(left, top, right, bottom, surfaceStyle.borderColor);
            } else {
                drawRoundedBorder(left, top, right, bottom, cornerRadii, cornerMask, surfaceStyle.borderColor);
            }
        }
    }

    /**
     * 绘制带分角圆角的表面。
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
     * 使用宿主图片渲染能力在指定区域绘制一张隔离贴图。
     *
     * <p>该入口会把宿主绘制放进独立 FBO，再立即按预乘 alpha 回贴到当前主层，避免宿主错误状态、
     * depth 写入或半透明叠层污染当前文档内容。</p>
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
        HostImageRenderer hostImageRenderer = runtimeAdapters.getHostImageRenderer();
        if (hostImageRenderer == null) {
            return;
        }

        UiRenderTarget layer = paintContextCompositor.borrowIsolatedLayer(screenWidth, screenHeight);
        ClipSnapshot clipSnapshot = copyCurrentClipSnapshot();
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        layer.begin();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                GL11.glOrtho(0.0D, screenWidth, screenHeight, 0.0D, -1000.0D, 1000.0D);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    clearClipState();
                    applyClipSnapshot(clipSnapshot, screenHeight);
                    hostImageRenderer.render(source, left, top, right, bottom);
                    clearClipState();
                } finally {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(previousMatrixMode);
            }
        } finally {
            layer.end();
        }

        applyClipSnapshot(clipSnapshot, screenHeight);
        layer.compositeToCurrentFramebuffer(left, top, right, bottom, 1.0F);
        notifyMainLayerContentChanged();
        paintContextCompositor.releaseIsolatedLayer(layer);
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
     * 延迟登记一批主渲染后的顶层 overlay 回放动作。
     *
     * <p>该路径用于 tooltip、鼠标携带物品这类应覆盖在页面内容之上、且不应被槽位卡片局部裁剪的运行时叠层。</p>
     *
     * @param replay 主渲染完成后要回放的顶层动作
     */
    public void enqueueDeferredPostMainOverlayPass(DeferredPostMainPassReplay replay) {
        deferredPostMainOverlayPasses.add(new DeferredPostMainPass(Objects.requireNonNull(replay, "replay"), null));
    }

    /**
     * 判断当前帧是否存在待回放的主后置补充绘制。
     *
     * @return 是否存在延迟回放
     */
    public boolean hasDeferredPostMainPasses() {
        return !deferredPostMainPasses.isEmpty() || !deferredPostMainOverlayPasses.isEmpty();
    }

    /**
     * 取出并清空当前帧登记的主后置补充绘制。
     *
     * @return 当前帧延迟回放列表
     */
    public List<DeferredPostMainPass> drainDeferredPostMainPasses() {
        if (deferredPostMainPasses.isEmpty() && deferredPostMainOverlayPasses.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeferredPostMainPass> drainedPasses = new ArrayList<DeferredPostMainPass>(deferredPostMainPasses);
        drainedPasses.addAll(deferredPostMainOverlayPasses);
        deferredPostMainPasses.clear();
        deferredPostMainOverlayPasses.clear();
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
     * 弹出最近压入的文档元素 transform 矩阵。
     */
    public void popTransform() {
        GL11.glPopMatrix();
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
        clipStack.push(left, top, right, bottom, screenWidth, screenHeight, cornerRadii);
        applyCurrentClip();
    }

    public void popClip() {
        clipStack.pop();
        applyCurrentClip();
    }

    private ClipSnapshot copyCurrentClipSnapshot() {
        return clipStack.copySnapshot();
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
        ClipStack.applySnapshot(clipSnapshot, screenHeight);
    }

    /**
     * 清空当前 OpenGL 裁剪状态。
     */
    public static void clearClipState() {
        ClipStack.clearState();
    }

    private void fillRoundedRect(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        UiRoundedRectGeometry.drawRoundedRectGeometry(left, top, right, bottom, cornerRadii, true, cornerMask);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        notifyMainLayerContentChanged();
    }

    private void drawRoundedBorder(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        // 线框边界若直接落在整数像素边缘，会让左/上侧描边有一半落在 clip 外，
        // 在真实运行时看起来像左侧边线被吞掉。这里统一把描边中心内缩到像素中心，
        // 让四条边都以同样的方式落在边框盒内部。
        UiRoundedRectGeometry.drawRoundedRectGeometry(left + 0.5F, top + 0.5F, right - 0.5F, bottom - 0.5F,
                cornerRadii, false, cornerMask);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        notifyMainLayerContentChanged();
    }

    private void applyColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }
}
