package club.heiqi.uilib.ui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * UI 渲染裁剪栈。
 *
 * <p>负责维护矩形裁剪交集、圆角裁剪区域快照，并把快照回放到 OpenGL scissor/stencil 状态。</p>
 */
final class ClipStack {

    private final Deque<ClipState> entries = new ArrayDeque<ClipState>();

    void push(int left, int top, int right, int bottom, int screenWidth, int screenHeight,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        int clipLeft = Math.max(0, Math.min(left, right));
        int clipTop = Math.max(0, Math.min(top, bottom));
        int clipRight = Math.min(screenWidth, Math.max(left, right));
        int clipBottom = Math.min(screenHeight, Math.max(top, bottom));

        if (!entries.isEmpty()) {
            int[] parent = entries.peek().clipRect;
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

        entries.push(new ClipState(new int[] { clipLeft, clipTop, clipRight, clipBottom },
                UiBorderRadiusResolver.scaleToFit(cornerRadii, clipRight - clipLeft, clipBottom - clipTop)));
    }

    void pop() {
        if (!entries.isEmpty()) {
            entries.pop();
        }
    }

    ClipSnapshot copySnapshot() {
        if (entries.isEmpty()) {
            return null;
        }

        int[] clip = entries.peek().clipRect;
        List<RoundedClipRegion> roundedClipRegions = new ArrayList<RoundedClipRegion>();
        Iterator<ClipState> iterator = entries.descendingIterator();
        while (iterator.hasNext()) {
            ClipState clipState = iterator.next();
            if (!UiRoundedRectGeometry.hasAnyCornerRadius(clipState.cornerRadii)) {
                continue;
            }
            int[] clipRect = clipState.clipRect;
            roundedClipRegions.add(new RoundedClipRegion(clipRect[0], clipRect[1], clipRect[2], clipRect[3],
                    clipState.cornerRadii));
        }
        return new ClipSnapshot(new int[] { clip[0], clip[1], clip[2], clip[3] },
                Collections.unmodifiableList(roundedClipRegions));
    }

    static void applySnapshot(ClipSnapshot clipSnapshot, int screenHeight) {
        if (clipSnapshot == null || clipSnapshot.getClipRect() == null) {
            clearState();
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

    static void clearState() {
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
            UiRoundedRectGeometry.drawRoundedRectGeometry(clipRegion.getLeft(), clipRegion.getTop(),
                    clipRegion.getRight(), clipRegion.getBottom(), clipRegion.getCornerRadii(), true,
                    UiSurfaceStyle.CORNER_ALL);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilMask(0x00);
        GL11.glStencilFunc(GL11.GL_EQUAL, roundedClipRegions.size(), 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    /**
     * 单层裁剪状态快照。
     */
    private static final class ClipState {

        private final int[] clipRect;
        private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;

        private ClipState(int[] clipRect, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            this.clipRect = clipRect;
            this.cornerRadii = cornerRadii;
        }
    }
}
