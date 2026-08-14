package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `UiRenderContext` 文本绘制入口的回归测试。
 */
public class UiRenderContextTest {

    /** 完全位于 clip 外的宿主图片在排队前剔除。 */
    @Test
    public void shouldRejectRectangleCompletelyOutsideClip() {
        ClipSnapshot clip = new ClipSnapshot(new int[] {10, 10, 20, 20},
                java.util.Collections.<RoundedClipRegion>emptyList());
        Assert.assertFalse(UiRenderContext.isVisibleInClip(clip, 21, 10, 30, 20));
        Assert.assertTrue(UiRenderContext.isVisibleInClip(clip, 19, 19, 30, 30));
    }

    /**
     * 验证普通字体样式不会在 `drawText` 重载之间递归。
     */
    @Test
    public void shouldDrawNormalFontStyleWithoutRecursiveOverload() {
        RecordingUiRenderContext context = new RecordingUiRenderContext();

        context.drawText("Normal", 3, 5, 0xFFE2E8F0, false, TextContentMode.UILIB_RAW,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL);

        Assert.assertEquals(1, context.resolvedTextCount);
        Assert.assertEquals("Normal", context.lastText);
        Assert.assertEquals(UiFontWeight.NORMAL, context.lastFontWeight);
        Assert.assertEquals(UiFontStyle.NORMAL, context.lastFontStyle);
    }

    @Test
    public void shouldCenterSquareItemDestinationAndCapOnlyRasterSide() {
        assertItemGeometry(10, 20, 28, 38, 10, 20, 28, 38, 18);
        assertItemGeometry(10, 20, 74, 36, 34, 20, 50, 36, 16);
        assertItemGeometry(10, 20, 26, 84, 10, 44, 26, 60, 16);
        assertItemGeometry(10, 20, 74, 84, 10, 20, 74, 84, 32);
        assertItemGeometry(10, 20, 74, 148, 10, 52, 74, 116, 32);
    }

    private static void assertItemGeometry(int left, int top, int right, int bottom,
            int expectedLeft, int expectedTop, int expectedRight, int expectedBottom, int expectedRasterSide) {
        UiRenderContext.ItemIconGeometry geometry = UiRenderContext.resolveItemIconGeometry(
                left, top, right, bottom);
        Assert.assertEquals(expectedLeft, geometry.getDestinationLeft());
        Assert.assertEquals(expectedTop, geometry.getDestinationTop());
        Assert.assertEquals(expectedRight, geometry.getDestinationRight());
        Assert.assertEquals(expectedBottom, geometry.getDestinationBottom());
        Assert.assertEquals(expectedRasterSide, geometry.getRasterSide());
    }

    /** ITEM_ICON 当帧直绘：渲染器直接收到居中正方形与 snapshot 副本，无会话/栅格/占位参与。 */
    @Test
    public void itemIconDrawsDirectlyWithCenteredSquareAndSnapshotCopy() {
        final net.minecraft.item.ItemStack[] receivedStack = new net.minecraft.item.ItemStack[1];
        final int[] geometry = new int[4];
        club.heiqi.uilib.ui.runtime.UiRuntimeAdapters adapters =
                club.heiqi.uilib.ui.runtime.UiRuntimeAdapters.empty()
                        .withItemIconRenderer((stack, left, top, side) -> {
                            receivedStack[0] = stack;
                            geometry[0] = left;
                            geometry[1] = top;
                            geometry[2] = side;
                        });
        RecordingUiRenderContext context = new RecordingUiRenderContext(adapters);

        club.heiqi.uilib.ui.image.HostImageSource source =
                club.heiqi.uilib.ui.image.HostImageSource.itemIcon(
                        new net.minecraft.item.ItemStack(new net.minecraft.item.Item(), 1, 3));

        context.drawHostImage(source, 10, 20, 42, 44);

        Assert.assertNotNull("item icon 必须直接交给渲染器", receivedStack[0]);
        Assert.assertEquals(3, receivedStack[0].getItemDamage());
        Assert.assertEquals("目标矩形内居中正方形", 14, geometry[0]);
        Assert.assertEquals(20, geometry[1]);
        Assert.assertEquals(24, geometry[2]);
        Assert.assertEquals(1, context.mainLayerContentChangedCount);
    }

    /** 空适配器路径下 ITEM_ICON 跳过绘制（无渲染器、无占位、无异常）。 */
    @Test
    public void itemIconWithoutRendererSkipsQuietly() {
        RecordingUiRenderContext context = new RecordingUiRenderContext(
                club.heiqi.uilib.ui.runtime.UiRuntimeAdapters.empty());

        context.drawHostImage(club.heiqi.uilib.ui.image.HostImageSource.itemIcon(
                new net.minecraft.item.ItemStack(new net.minecraft.item.Item())), 0, 0, 16, 16);

        Assert.assertEquals(0, context.mainLayerContentChangedCount);
    }

    /**
     * 记录最终文本绘制入口的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private int resolvedTextCount;
        private int mainLayerContentChangedCount;
        private String lastText;
        private UiFontWeight lastFontWeight;
        private UiFontStyle lastFontStyle;

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        private RecordingUiRenderContext(club.heiqi.uilib.ui.runtime.UiRuntimeAdapters adapters) {
            super(320, 240, 0, 0, 0.0F, new PaintContextCompositor(),
                    new UiMainLayerSnapshotService(), adapters);
        }

        @Override
        protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
                TextContentMode textContentMode, UiFontWeight resolvedFontWeight, UiFontStyle resolvedFontStyle) {
            resolvedTextCount++;
            lastText = text;
            lastFontWeight = resolvedFontWeight;
            lastFontStyle = resolvedFontStyle;
        }

        @Override
        public void notifyMainLayerContentChanged() {
            mainLayerContentChangedCount++;
        }
    }
}
