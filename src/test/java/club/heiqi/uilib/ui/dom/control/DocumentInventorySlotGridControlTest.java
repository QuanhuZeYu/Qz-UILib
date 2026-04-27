package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.InventorySlotGridItemGeometry;
import club.heiqi.uilib.ui.control.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.control.InventorySlotSnapshot;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `DocumentInventorySlotGridControl` 的基础行为契约测试。
 */
public class DocumentInventorySlotGridControlTest {

    /**
     * 验证空槽网格渲染时不触发物品渲染器。
     */
    @Test
    public void shouldRenderEmptySlotGridWithoutCallingItemRenderer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return InventorySlotSnapshot.empty();
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertTrue(renderContext.deferredReplays.isEmpty());
    }

    /**
     * 验证占用槽触发物品渲染器。
     */
    @Test
    public void shouldCallItemRendererWhenSlotsAreOccupied() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        final List<InventorySlotSnapshot[]> receivedSnapshots = new ArrayList<InventorySlotSnapshot[]>();
        final List<InventorySlotGridItemGeometry> receivedGeometries = new ArrayList<InventorySlotGridItemGeometry>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return localIndex == 0 ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
                    }
                })
                .setItemRenderer(new InventorySlotGridItemRenderer() {
                    @Override
                    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
                        receivedGeometries.add(geometry);
                        receivedSnapshots.add(slotSnapshots);
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.deferredReplays.size());
        renderContext.deferredReplays.get(0).replay();
        Assert.assertEquals(1, receivedSnapshots.size());
        Assert.assertEquals(9, receivedSnapshots.get(0).length);
        Assert.assertTrue(receivedSnapshots.get(0)[0].isOccupied());
        Assert.assertFalse(receivedSnapshots.get(0)[1].isOccupied());
        Assert.assertEquals(9, receivedGeometries.get(0).getSlotCount());
    }

    /**
     * 验证无 contentProvider 时全部识别为空槽。
     */
    @Test
    public void shouldTreatAllSlotsAsEmptyWhenNoContentProvider() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        final boolean[] rendererCalled = new boolean[] { false };
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setItemRenderer(new InventorySlotGridItemRenderer() {
                    @Override
                    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
                        rendererCalled[0] = true;
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertTrue(renderContext.deferredReplays.isEmpty());
        Assert.assertFalse(rendererCalled[0]);
    }

    /**
     * 验证网格布局尺寸按期望列数计算。
     */
    @Test
    public void shouldComputePreferredLayoutSize() {
        UiDocument document = UiDocument.create();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .commitLayout();

        int expectedWidth = 9 * 32 + 8 * 4;
        int expectedHeight = 1 * 32;
        Assert.assertEquals(expectedWidth, (int) gridControl.getElement().style().getWidth().getValue());
        Assert.assertEquals(expectedHeight, (int) gridControl.getElement().style().getHeight().getValue());
    }

    /**
     * 记录延迟回放与 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DeferredPostMainPassReplay> deferredReplays = new ArrayList<DeferredPostMainPassReplay>();

        private RecordingUiRenderContext() {
            super(400, 200, 0, 0, 1.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            // 记录但不验证具体绘制
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void popClip() {}

        @Override
        public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
            deferredReplays.add(replay);
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
