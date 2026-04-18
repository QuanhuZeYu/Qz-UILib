package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderContext;

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
        UiRenderContext.DeferredPostMainPass deferredPass = renderContext.deferredPostMainPasses.get(0);
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
     * 记录绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<Integer> fillColors = new ArrayList<Integer>();
        private final List<DeferredPostMainPass> deferredPostMainPasses = new ArrayList<DeferredPostMainPass>();

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
        public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
            super.enqueueDeferredPostMainPass(replay);
            deferredPostMainPasses.clear();
            deferredPostMainPasses.addAll(drainDeferredPostMainPasses());
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
}
