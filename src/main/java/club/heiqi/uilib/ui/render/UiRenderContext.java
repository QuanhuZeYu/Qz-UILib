package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.api.FontRendererAdapter;

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
    private final Deque<int[]> clipStack = new ArrayDeque<int[]>();
    private final List<DeferredPostMainPass> deferredPostMainPasses = new ArrayList<DeferredPostMainPass>();

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
        private final int[] clipRect;

        private DeferredPostMainPass(DeferredPostMainPassReplay replay, int[] clipRect) {
            this.replay = replay;
            this.clipRect = clipRect;
        }

        public void replay() {
            replay.replay();
        }

        public int[] getClipRect() {
            if (clipRect == null) {
                return null;
            }
            return new int[] { clipRect[0], clipRect[1], clipRect[2], clipRect[3] };
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
                copyCurrentClipRect()));
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

    public void pushClip(int left, int top, int right, int bottom) {
        int clipLeft = Math.max(0, Math.min(left, right));
        int clipTop = Math.max(0, Math.min(top, bottom));
        int clipRight = Math.min(screenWidth, Math.max(left, right));
        int clipBottom = Math.min(screenHeight, Math.max(top, bottom));

        if (!clipStack.isEmpty()) {
            int[] parent = clipStack.peek();
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

        clipStack.push(new int[] { clipLeft, clipTop, clipRight, clipBottom });
        applyCurrentClip();
    }

    public void popClip() {
        if (!clipStack.isEmpty()) {
            clipStack.pop();
        }
        applyCurrentClip();
    }

    private int[] copyCurrentClipRect() {
        if (clipStack.isEmpty()) {
            return null;
        }
        int[] clip = clipStack.peek();
        return new int[] { clip[0], clip[1], clip[2], clip[3] };
    }

    private void applyCurrentClip() {
        if (clipStack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        int[] clip = clipStack.peek();
        int width = Math.max(0, clip[2] - clip[0]);
        int height = Math.max(0, clip[3] - clip[1]);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clip[0], screenHeight - clip[3], width, height);
    }
}
