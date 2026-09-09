package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
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
    public void defaultBevelKeepsLegacyGeometryAndColors() {
        List<PaintCommand> bevels = bevels(paint.paint(surface(24, 24).__setSurfaceElevation(0.5F))
                .getPlan().getCommands());
        Assert.assertEquals(2, bevels.size());
        // 独立 Python 验算的原默认宽度 1 输出，锁住几何与原非零 alpha 配方。
        Assert.assertEquals(PaintCommand.roundedBand(1, 1, 23, 21, new RoundedBand(
                new int[] {0, 0, 22, 20, 7, 7, 7, 7},
                new int[] {1, 1, 21, 19, 6, 6, 6, 6}, null,
                new int[] {0x54FFFFFF, 0x2AFFFFFF, 0x15CEE8FF, 0x26071320})), bevels.get(0));
        Assert.assertEquals(PaintCommand.roundedBand(2, 2, 22, 20, new RoundedBand(
                new int[] {0, 0, 20, 18, 6, 6, 6, 6},
                new int[] {1, 1, 19, 17, 5, 5, 5, 5}, null,
                new int[] {0x18071320, 0, 0, 0x18071320})), bevels.get(1));
    }

    @Test
    public void zeroWidthOrTransparentBorderRemovesOnlyBevelCommands() {
        SceneNode node = surface(24, 24).__setSurfaceElevation(0.5F);
        List<PaintCommand> original = paint.paint(node).getPlan().getCommands();
        List<PaintCommand> withoutBevel = new ArrayList<PaintCommand>(original);
        Assert.assertEquals(2, bevels(original).size());
        withoutBevel.removeAll(bevels(original));
        Assert.assertFalse("厚底、滤镜面、染色及表面渐变必须保留", withoutBevel.isEmpty());
        node.setBorderWidth(0);
        Assert.assertEquals(withoutBevel, paint.paint(node).getPlan().getCommands());
        node.setBorderWidth(3).setBorderColor(0x00ABCDEF);
        Assert.assertEquals("透明边色不能被最低强度重新点亮",
                withoutBevel, paint.paint(node).getPlan().getCommands());
    }

    @Test
    public void borderWidthChangesActualBandAndReplayCoverageAtEveryScale() {
        SceneNode node = surface(24, 24).__setSurfaceElevation(0.5F);
        PaintCommand narrow = bevels(paint.paint(node).getPlan().getCommands()).get(0);
        node.setBorderWidth(3);
        PaintCommand wide = bevels(paint.paint(node).getPlan().getCommands()).get(0);
        Assert.assertArrayEquals(narrow.getRoundedBand().outer(), wide.getRoundedBand().outer());
        Assert.assertArrayEquals(new int[] {3, 3, 19, 17, 4, 4, 4, 4}, wide.getRoundedBand().inner());
        for (float scale : new float[] {1.0F, 1.5F, 2.0F, 3.0F}) {
            RecordingRenderBackend thinReplay = replayBand(narrow, scale);
            RecordingRenderBackend wideReplay = replayBand(wide, scale);
            int x = (int) Math.floor(12.5F * scale);
            int outerY = (int) Math.floor(1.5F * scale);
            int insetY = (int) Math.floor(3.5F * scale);
            int centerY = (int) Math.floor(11.5F * scale);
            Assert.assertTrue(covers(thinReplay, x, outerY));
            Assert.assertTrue(covers(wideReplay, x, outerY));
            Assert.assertFalse("1px 倒角不覆盖更深一行", covers(thinReplay, x, insetY));
            Assert.assertTrue("3px 倒角必须真实覆盖更深一行", covers(wideReplay, x, insetY));
            Assert.assertFalse("加宽不能填满玻璃中心", covers(wideReplay, x, centerY));
        }
    }

    @Test
    public void enormousBorderWidthIsClampedWithoutOverflowOrFillingTheCenter() {
        SceneNode node = surface(24, 24).__setSurfaceElevation(0.5F).setBorderWidth(9);
        List<PaintCommand> clamped = paint.paint(node).getPlan().getCommands();
        node.setBorderWidth(Integer.MAX_VALUE);
        Assert.assertEquals(clamped, paint.paint(node).getPlan().getCommands());
        PaintCommand bevel = bevels(clamped).get(0);
        Assert.assertArrayEquals(new int[] {9, 9, 13, 11, 0, 0, 0, 0}, bevel.getRoundedBand().inner());
        Assert.assertFalse(covers(replayBand(bevel, 1.0F), 12, 11));
        for (int width : new int[] {1, 4, 5, 7, 24}) {
            for (int height : new int[] {1, 6, 7, 24}) {
                node = surface(width, height).__setSurfaceElevation(0.5F).setBorderWidth(Integer.MAX_VALUE);
                for (PaintCommand command : paint.paint(node).getPlan().getCommands()) {
                    Assert.assertTrue(command.getLeft() >= 0 && command.getTop() >= 0);
                    Assert.assertTrue(command.getRight() <= width && command.getBottom() <= height);
                    Assert.assertTrue(command.getLeft() < command.getRight());
                    Assert.assertTrue(command.getTop() < command.getBottom());
                    if (command.getRoundedBand() != null && command.getRoundedBand().inner() != null) {
                        int[] inner = command.getRoundedBand().inner();
                        Assert.assertTrue(inner[0] < inner[2] && inner[1] < inner[3]);
                    }
                }
            }
        }
    }

    private static List<PaintCommand> bevels(List<PaintCommand> commands) {
        List<PaintCommand> result = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            RoundedBand band = command.getRoundedBand();
            // 厚底带有盒内 bounds，渐变不挖孔；只选面内倒角及其阴影。
            if (band != null && band.inner() != null && band.bounds() == null) result.add(command);
        }
        return result;
    }

    private static RecordingRenderBackend replayBand(PaintCommand command, float scale) {
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(new PaintPlan().addCommand(command), backend.scaled(scale));
        return backend;
    }

    private static boolean covers(RecordingRenderBackend backend, int x, int y) {
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if (call.methodName().equals("fillRect") && call.getInt(0) <= x && x < call.getInt(2)
                    && call.getInt(1) <= y && y < call.getInt(3) && (call.getInt(4) >>> 24) != 0) return true;
        }
        return false;
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
