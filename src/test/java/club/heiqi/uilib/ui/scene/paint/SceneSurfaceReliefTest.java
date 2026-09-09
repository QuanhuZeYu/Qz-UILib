package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/** 立体装饰沿用原绘制计划、失效与局部坐标，不扩大命中/合成边界。 */
public class SceneSurfaceReliefTest {

    private static final int TINT = 0x12345678;
    private final ScenePaintEngine paint = new ScenePaintEngine(new FixedTextMeasurer());

    @Test
    public void ordinarySurfaceKeepsOriginalCommandsAfterReliefIsRemoved() {
        SceneNode node = surface(24, 24);
        assertOrdinary(paint.paint(node).getPlan().getCommands());
        node.__setSurfaceElevation(0.5F);
        Assert.assertTrue(paint.paint(node).getPlan().getCommands().size() > 3);
        node.__setSurfaceElevation(-1.0F);
        assertOrdinary(paint.paint(node).getPlan().getCommands());
    }

    @Test
    public void elevationInvalidatesOnlyItsPaintAndCompositeKeepsFragment() {
        SceneNode root = SceneNode.row();
        SceneNode glass = surface(24, 24).__setSurfaceElevation(0.5F);
        SceneNode sibling = surface(24, 24);
        root.appendChild(glass);
        root.appendChild(sibling);
        SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
        Constraints constraints = new Constraints(200, 100);
        layout.layout(root, constraints);
        paint.paint(root);
        Object layoutBefore = glass.getCachedLayout();
        Object siblingBefore = sibling.getCachedPaint();
        Object paintBefore = glass.getCachedPaint();
        glass.__setSurfaceElevation(0.5F);
        Assert.assertEquals(0, paint.paint(root).getRegeneratedFragmentCount());
        Assert.assertSame(paintBefore, glass.getCachedPaint());
        glass.__setSurfaceElevation(1.0F);
        Assert.assertTrue(glass.__isSelfPaintDirty());
        Assert.assertFalse(glass.__isSelfLayoutDirty());
        Assert.assertFalse(sibling.__isSelfPaintDirty());
        layout.layout(root, constraints);
        Assert.assertSame(layoutBefore, glass.getCachedLayout());
        Assert.assertEquals(1, paint.paint(root).getRegeneratedFragmentCount());
        Assert.assertSame(siblingBefore, sibling.getCachedPaint());
        Object elevated = glass.getCachedPaint();
        glass.setOpacity(0.6F).setTransform(Transform.translate(2.5F, -1.0F));
        Assert.assertEquals(0, paint.paint(root).getRegeneratedFragmentCount());
        Assert.assertSame(elevated, glass.getCachedPaint());
    }

    @Test
    public void reliefCommandsTranslateTogetherWhenCachedSurfaceMoves() {
        SceneNode node = surface(24, 24).__setSurfaceElevation(0.5F);
        List<PaintCommand> before = paint.paint(node).getPlan().getCommands();
        Object fragment = node.getCachedPaint();
        node.setCachedLayout(new LayoutBox(31, 47, 24, 24));
        node.markGeometryDirty();
        PaintResult moved = paint.paint(node);
        Assert.assertEquals(0, moved.getRegeneratedFragmentCount());
        Assert.assertSame(fragment, node.getCachedPaint());
        List<PaintCommand> after = moved.getPlan().getCommands();
        Assert.assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            PaintCommand a = before.get(i);
            PaintCommand b = after.get(i);
            Assert.assertEquals(a.getType(), b.getType());
            Assert.assertEquals(a.getLeft() + 31, b.getLeft());
            Assert.assertEquals(a.getRight() + 31, b.getRight());
            Assert.assertEquals(a.getTop() + 47, b.getTop());
            Assert.assertEquals(a.getBottom() + 47, b.getBottom());
        }
    }

    @Test
    public void tinyAndLargeAsymmetricSurfacesKeepBoundedLocalGeometry() {
        int[] widths = {1, 4, 5, 7, 24, 80, 1200};
        int[] heights = {1, 6, 7, 24, 80, 900};
        float[] elevations = {0.0F, 0.35F, 0.5F, 1.0F};
        for (int width : widths) {
            for (int height : heights) {
                for (float elevation : elevations) {
                    SceneNode node = surface(width, height).setCornerRadius(40, 0, 13, 5)
                            .__setSurfaceElevation(elevation);
                    List<PaintCommand> commands = paint.paint(node).getPlan().getCommands();
                    Assert.assertTrue("装饰命令不能随表面积增长", commands.size() <= 160);
                    int backdropCount = 0;
                    int tintCount = 0;
                    for (PaintCommand command : commands) {
                        Assert.assertTrue(command.getType() == PaintCommandType.BACKGROUND
                                || command.getType() == PaintCommandType.BACKDROP
                                || command.getType() == PaintCommandType.BORDER
                                || command.getType() == PaintCommandType.ROUNDED_BAND);
                        Assert.assertTrue(command.getLeft() >= 0 && command.getTop() >= 0);
                        Assert.assertTrue(command.getRight() <= width && command.getBottom() <= height);
                        Assert.assertTrue(command.getRight() > command.getLeft());
                        Assert.assertTrue(command.getBottom() > command.getTop());
                        if (command.getType() == PaintCommandType.BACKDROP) backdropCount++;
                        if (command.getType() == PaintCommandType.BACKGROUND && command.getColor() == TINT) {
                            tintCount++;
                        }
                    }
                    Assert.assertEquals("每个实体只有一个滤镜面", 1, backdropCount);
                    Assert.assertEquals("染色只叠一次", 1, tintCount);
                }
            }
        }
    }

    @Test
    public void replayKeepsTransparentCenterAndDirectionalEdgesAtEveryScale() {
        for (float scale : new float[] {1.0F, 1.5F, 2.0F, 3.0F}) {
            for (float elevation : new float[] {0.0F, 0.5F, 1.0F}) {
                SceneNode node = surface(24, 24).__setSurfaceElevation(elevation);
                PaintPlan decorations = new PaintPlan();
                for (PaintCommand command : paint.paint(node).getPlan().getCommands()) {
                    if (command.getType() == PaintCommandType.ROUNDED_BAND) decorations.addCommand(command);
                }
                RecordingRenderBackend backend = new RecordingRenderBackend();
                new ScenePaintReplayer().replay(decorations, backend.scaled(scale));
                int extent = Math.round(24 * scale);
                int center = extent / 2;
                for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
                    Assert.assertTrue(call.getInt(0) >= 0 && call.getInt(1) >= 0);
                    Assert.assertTrue(call.getInt(2) <= extent && call.getInt(3) <= extent);
                    Assert.assertFalse("中心必须保持玻璃透射，不得用底盖填孔",
                            call.getInt(0) <= center && call.getInt(2) > center
                                    && call.getInt(1) <= center && call.getInt(3) > center);
                }
                System.out.println("RELIEF_CALLS scale=" + scale + " elevation=" + elevation
                        + " calls=" + backend.getCallCount());
                Assert.assertTrue("最多按物理边沿增长，不能逐像素输出整个面积",
                        backend.getCallCount() < 300 * scale);
            }
        }
    }

    @Test
    public void descriptorIsDefensiveAndParticipatesInValueIdentityAndTranslation() {
        int[] outer = {0, 0, 24, 24, 8, 8, 8, 8};
        int[] colors = {0x80FFFFFF, 0x40FFFFFF, 0x20FFFFFF, 0x20071320};
        RoundedBand value = new RoundedBand(outer, null, null, colors);
        RoundedBand equal = new RoundedBand(outer, null, null, colors);
        PaintCommand command = PaintCommand.roundedBand(0, 0, 24, 24, value);
        PaintCommand equivalent = PaintCommand.roundedBand(0, 0, 24, 24, equal);
        Assert.assertEquals(command, equivalent);
        Assert.assertEquals(command.hashCode(), equivalent.hashCode());
        outer[4] = 0;
        colors[0] = 0;
        value.outer()[4] = 0;
        value.colors()[0] = 0;
        Assert.assertEquals(equal, value);
        Assert.assertNotEquals(command,
                PaintCommand.roundedBand(0, 0, 24, 24, new RoundedBand(outer, null, null, colors)));
        Assert.assertSame(value, command.translatedBy(31, 47).getRoundedBand());
        RecordingRenderBackend shifted = new RecordingRenderBackend();
        RecordingRenderBackend offset = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(new PaintPlan().addCommand(command.translatedBy(31, 47)),
                shifted.scaled(2.0F));
        new ScenePaintReplayer().replay(new PaintPlan().addCommand(command), offset.scaled(2.0F), 31, 47);
        Assert.assertEquals(shifted.getCallCount(), offset.getCallCount());
        for (int i = 0; i < shifted.getCallCount(); i++) {
            Assert.assertArrayEquals(shifted.getCall(i).args(), offset.getCall(i).args());
        }
    }

    private static SceneNode surface(int width, int height) {
        SceneNode node = new SceneNode().setPreferredWidth(width).setPreferredHeight(height)
                .setBackgroundColor(TINT).setBorderWidth(1).setBorderColor(0x60FFFFFF)
                .setCornerRadius(8).setBackdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN, 6, 0.8F));
        node.setCachedLayout(new LayoutBox(0, 0, width, height));
        return node;
    }

    private static void assertOrdinary(List<PaintCommand> commands) {
        Assert.assertEquals(3, commands.size());
        Assert.assertEquals(PaintCommandType.BACKDROP, commands.get(0).getType());
        Assert.assertEquals(PaintCommandType.BACKGROUND, commands.get(1).getType());
        Assert.assertEquals(PaintCommandType.BORDER, commands.get(2).getType());
        for (PaintCommand command : commands) {
            Assert.assertEquals(0, command.getLeft());
            Assert.assertEquals(0, command.getTop());
            Assert.assertEquals(24, command.getRight());
            Assert.assertEquals(24, command.getBottom());
        }
        Assert.assertEquals(TINT, commands.get(1).getColor());
    }
}
