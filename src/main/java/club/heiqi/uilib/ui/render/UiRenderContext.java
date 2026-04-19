package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.api.FontRendererAdapter;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * UI 渲染上下文。
 */
public class UiRenderContext {

    private static final float UI_TEXT_SCALE = 2.0F;

    private final int screenWidth;
    private final int screenHeight;
    private final int mouseX;
    private final int mouseY;
    private final float partialTicks;
    private final FontRendererAdapter fontRenderer;
    private final Deque<ClipState> clipStack = new ArrayDeque<ClipState>();
    private final List<DeferredPostMainPass> deferredPostMainPasses = new ArrayList<DeferredPostMainPass>();
    private final List<BackdropEffectRequest> backdropEffectRequests = new ArrayList<BackdropEffectRequest>();

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
     * 一条待由宿主执行的 backdrop effect 请求。
     */
    public static final class BackdropEffectRequest {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiBackdropEffectSpec effectSpec;
        private final ClipSnapshot clipSnapshot;

        private BackdropEffectRequest(int left, int top, int right, int bottom, UiBackdropEffectSpec effectSpec,
                ClipSnapshot clipSnapshot) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.effectSpec = effectSpec;
            this.clipSnapshot = clipSnapshot;
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

        public UiBackdropEffectSpec getEffectSpec() {
            return effectSpec;
        }

        public ClipSnapshot getClipSnapshot() {
            return clipSnapshot;
        }

        public int[] getClipRect() {
            return clipSnapshot == null ? null : clipSnapshot.getClipRect();
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
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.fontRenderer = DefaultFontRendererAdapter.getInstance();
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
     * 登记一条待由宿主 effect runtime 执行的 backdrop effect 请求。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param effectSpec effect 配置
     */
    public void enqueueBackdropEffect(int left, int top, int right, int bottom, UiBackdropEffectSpec effectSpec) {
        UiBackdropEffectSpec resolvedSpec = effectSpec == null ? UiBackdropEffectSpec.none() : effectSpec;
        if (!resolvedSpec.enabled) {
            return;
        }

        int normalizedLeft = Math.max(0, Math.min(left, right));
        int normalizedTop = Math.max(0, Math.min(top, bottom));
        int normalizedRight = Math.min(screenWidth, Math.max(left, right));
        int normalizedBottom = Math.min(screenHeight, Math.max(top, bottom));
        if (normalizedRight <= normalizedLeft || normalizedBottom <= normalizedTop) {
            return;
        }

        backdropEffectRequests.add(new BackdropEffectRequest(normalizedLeft, normalizedTop, normalizedRight,
                normalizedBottom, resolvedSpec, copyCurrentClipSnapshot()));
    }

    /**
     * 判断当前帧是否存在待执行的 backdrop effect 请求。
     *
     * @return 是否存在 effect 请求
     */
    public boolean hasBackdropEffectRequests() {
        return !backdropEffectRequests.isEmpty();
    }

    /**
     * 取出并清空当前帧登记的 backdrop effect 请求。
     *
     * @return 当前帧 effect 请求列表
     */
    public List<BackdropEffectRequest> drainBackdropEffectRequests() {
        if (backdropEffectRequests.isEmpty()) {
            return Collections.emptyList();
        }
        List<BackdropEffectRequest> drainedRequests = new ArrayList<BackdropEffectRequest>(backdropEffectRequests);
        backdropEffectRequests.clear();
        return drainedRequests;
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
