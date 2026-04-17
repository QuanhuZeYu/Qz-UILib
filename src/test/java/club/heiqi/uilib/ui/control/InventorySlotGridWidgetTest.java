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

        Assert.assertEquals(1, renderContext.deferredItemPasses.size());
        UiRenderContext.DeferredInventoryItemPass deferredPass = renderContext.deferredItemPasses.get(0);
        Assert.assertSame(itemRenderer, deferredPass.getItemRenderer());
        Assert.assertNotNull(deferredPass.getSlotSnapshots());
        Assert.assertEquals(3, deferredPass.getSlotSnapshots().length);
        Assert.assertFalse(deferredPass.getSlotSnapshots()[0].isOccupied());
        Assert.assertFalse(deferredPass.getSlotSnapshots()[1].isOccupied());
        Assert.assertTrue(deferredPass.getSlotSnapshots()[2].isOccupied());
    }

    /**
     * 记录绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<Integer> fillColors = new ArrayList<Integer>();
        private final List<DeferredInventoryItemPass> deferredItemPasses = new ArrayList<DeferredInventoryItemPass>();

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
        public void enqueueInventoryItemPass(InventorySlotGridItemRenderer itemRenderer, InventorySlotGridLayout layout,
                int absoluteX, int absoluteY, InventorySlotSnapshot[] slotSnapshots) {
            super.enqueueInventoryItemPass(itemRenderer, layout, absoluteX, absoluteY, slotSnapshots);
            deferredItemPasses.clear();
            deferredItemPasses.addAll(drainDeferredInventoryItemPasses());
        }
    }

    /**
     * 记录 renderer 入参的测试桩。
     */
    private static final class RecordingInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

        @Override
        public void renderItems(InventorySlotGridLayout layout, int absoluteX, int absoluteY,
                InventorySlotSnapshot[] slotSnapshots) {}
    }
}
