package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `InventorySlotGridWidget` 的聚焦测试。
 */
public class InventorySlotGridWidgetTest {

    /**
     * 验证占用判定只依赖槽位快照，并且 renderer 只接收 snapshot 边界入参。
     */
    @Test
    public void shouldUseSnapshotOccupationAndPassSnapshotsToRenderer() {
        RecordingInventorySlotGridItemRenderer itemRenderer = new RecordingInventorySlotGridItemRenderer();
        InventorySlotGridWidget widget = new InventorySlotGridWidget(3, 3,
                UiControlTheme.defaultInventorySlotGridStyle(), new InventorySlotGridWidget.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        if (localIndex == 0) {
                            return InventorySlotSnapshot.empty();
                        }
                        if (localIndex == 1) {
                            return InventorySlotSnapshot.fromRuntimeStack(null);
                        }
                        return InventorySlotSnapshot.occupied();
                    }
                }, itemRenderer);
        widget.applyLayoutBounds(10, 20, 120, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        UiControlTheme.InventorySlotGridStyle style = UiControlTheme.defaultInventorySlotGridStyle();
        Assert.assertEquals(3, renderContext.fillColors.size());
        Assert.assertEquals(style.emptySlotFillColor, renderContext.fillColors.get(0).intValue());
        Assert.assertEquals(style.emptySlotFillColor, renderContext.fillColors.get(1).intValue());
        Assert.assertEquals(style.occupiedSlotFillColor, renderContext.fillColors.get(2).intValue());

        Assert.assertEquals(1, renderContext.deferredPostMainPasses.size());
        RecordedDeferredPostMainPass deferredPass = renderContext.deferredPostMainPasses.get(0);
        deferredPass.replay();

        Assert.assertNotNull(itemRenderer.slotSnapshots);
        Assert.assertEquals(3, itemRenderer.slotSnapshots.length);
        Assert.assertFalse(itemRenderer.slotSnapshots[0].isOccupied());
        Assert.assertFalse(itemRenderer.slotSnapshots[1].isOccupied());
        Assert.assertTrue(itemRenderer.slotSnapshots[2].isOccupied());
        Assert.assertNotNull(itemRenderer.geometry);
        Assert.assertEquals(3, itemRenderer.geometry.getSlotCount());
    }

    /**
     * 验证延迟物品回放会携带显式结构裁剪快照。
     */
    @Test
    public void shouldCaptureExplicitStructuralClipForDeferredItems() {
        InventorySlotGridWidget widget = new InventorySlotGridWidget(1, 1,
                UiControlTheme.defaultInventorySlotGridStyle(), new InventorySlotGridWidget.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return InventorySlotSnapshot.occupied();
                    }
                }, new RecordingInventorySlotGridItemRenderer());
        widget.applyLayoutBounds(12, 16, 48, 48);

        ClipAwareWidget clipAwareWidget = new ClipAwareWidget();
        clipAwareWidget.applyLayoutBounds(40, 60, 180, 120);
        clipAwareWidget.addChild(widget);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        clipAwareWidget.render(renderContext);

        Assert.assertEquals(1, renderContext.deferredPostMainPasses.size());
        RecordedDeferredPostMainPass deferredPass = renderContext.deferredPostMainPasses.get(0);
        Assert.assertNotNull(deferredPass.clipRect);
        Assert.assertArrayEquals(new int[] { 48, 72, 204, 164 }, deferredPass.clipRect);
        Assert.assertTrue(deferredPass.roundedClipRegions.isEmpty());
    }

    /**
     * 记录绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<Integer> fillColors = new ArrayList<Integer>();
        private final List<RecordedDeferredPostMainPass> deferredPostMainPasses = new ArrayList<RecordedDeferredPostMainPass>();
        private final Deque<RecordedClipState> clipStates = new ArrayDeque<RecordedClipState>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            fillColors.add(Integer.valueOf(color));
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom) {
            pushClip(left, top, right, bottom, 0);
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            clipStates.push(new RecordedClipState(new int[] { left, top, right, bottom }, Math.max(0, cornerRadius)));
        }

        @Override
        public void popClip() {
            if (!clipStates.isEmpty()) {
                clipStates.pop();
            }
        }

        @Override
        public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
            int[] clipRect = clipStates.isEmpty() ? null : clipStates.peek().clipRect.clone();
            List<RecordedRoundedClipRegion> roundedClipRegions = new ArrayList<RecordedRoundedClipRegion>();
            for (RecordedClipState clipState : clipStates) {
                if (clipState.cornerRadius <= 0) {
                    continue;
                }
                int[] clip = clipState.clipRect;
                roundedClipRegions.add(new RecordedRoundedClipRegion(clip[0], clip[1], clip[2], clip[3],
                        clipState.cornerRadius));
            }
            Collections.reverse(roundedClipRegions);
            deferredPostMainPasses.add(new RecordedDeferredPostMainPass(replay, clipRect, roundedClipRegions));
        }
    }

    private static final class RecordedClipState {

        private final int[] clipRect;
        private final int cornerRadius;

        private RecordedClipState(int[] clipRect, int cornerRadius) {
            this.clipRect = clipRect;
            this.cornerRadius = cornerRadius;
        }
    }

    private static final class RecordedDeferredPostMainPass {

        private final UiRenderContext.DeferredPostMainPassReplay replay;
        private final int[] clipRect;
        private final List<RecordedRoundedClipRegion> roundedClipRegions;

        private RecordedDeferredPostMainPass(UiRenderContext.DeferredPostMainPassReplay replay, int[] clipRect,
                List<RecordedRoundedClipRegion> roundedClipRegions) {
            this.replay = replay;
            this.clipRect = clipRect;
            this.roundedClipRegions = roundedClipRegions;
        }

        private void replay() {
            replay.replay();
        }
    }

    private static final class RecordedRoundedClipRegion {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private RecordedRoundedClipRegion(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }

        private int getLeft() {
            return left;
        }

        private int getTop() {
            return top;
        }

        private int getRight() {
            return right;
        }

        private int getBottom() {
            return bottom;
        }

        private int getCornerRadius() {
            return cornerRadius;
        }
    }

    /**
     * 记录 renderer 入参的测试桩。
     */
    private static final class RecordingInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

        private InventorySlotGridItemGeometry geometry;
        private InventorySlotSnapshot[] slotSnapshots;

        @Override
        public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
            this.geometry = geometry;
            this.slotSnapshots = slotSnapshots;
        }
    }

    /**
     * 供测试使用的最小结构裁剪容器。
     */
    private static final class ClipAwareWidget extends Widget {

        private ClipAwareWidget() {
            applyChildClipEnabled(true);
        }

        @Override
        protected int[] getChildClipRect() {
            return new int[] { getAbsoluteX() + 8, getAbsoluteY() + 12, getAbsoluteX() + getWidth() - 16,
                    getAbsoluteY() + getHeight() - 16 };
        }
    }
}
