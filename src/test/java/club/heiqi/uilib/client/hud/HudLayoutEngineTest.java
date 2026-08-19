package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** 四角锚点、margin、安全区、稳定 stack 与 clamp 测试。 */
public class HudLayoutEngineTest {
    @Test public void placesAllFourCornersWithInsetsAndMargin() {
        List<HudLayoutEngine.MeasuredHud> input = Arrays.asList(
                measured("tl", HudAnchor.TOP_LEFT, 0, 20, 10, 0),
                measured("tr", HudAnchor.TOP_RIGHT, 0, 20, 10, 1),
                measured("bl", HudAnchor.BOTTOM_LEFT, 0, 20, 10, 2),
                measured("br", HudAnchor.BOTTOM_RIGHT, 0, 20, 10, 3));
        List<HudLayoutEngine.PlacedHud> result = new HudLayoutEngine().layout(input, 100, 80,
                new HudInsets(3, 5, 7, 9));
        assertBox(result.get(0), 11, 13, 20, 10);
        assertBox(result.get(1), 65, 13, 20, 10);
        assertBox(result.get(2), 11, 53, 20, 10);
        assertBox(result.get(3), 65, 53, 20, 10);
    }

    @Test public void sortsByStackThenRegistrationAndClampsViewport() {
        List<HudLayoutEngine.MeasuredHud> input = Arrays.asList(
                measured("late", HudAnchor.TOP_LEFT, 5, 200, 100, 2),
                measured("first", HudAnchor.TOP_LEFT, -1, 10, 10, 1),
                measured("stable", HudAnchor.TOP_LEFT, 5, 10, 10, 0));
        List<HudLayoutEngine.PlacedHud> result = new HudLayoutEngine().layout(input, 40, 30, HudInsets.NONE);
        assertEquals("first", result.get(0).entry.spec.getId());
        assertEquals("stable", result.get(1).entry.spec.getId());
        assertEquals("late", result.get(2).entry.spec.getId());
        assertTrue(result.get(2).width <= 32);
        assertTrue(result.get(2).x >= 0 && result.get(2).y >= 0);
    }

    @Test public void normalTokensStayWithinEmphasisCeiling() {
        assertEquals(14, HudTokens.NORMAL.fontSize);
        assertEquals(18, HudTokens.MAX_EMPHASIS_FONT_SIZE);
        assertTrue(HudTokens.NORMAL.fontSize <= HudTokens.NORMAL.lineBox);
        assertTrue(HudTokens.NORMAL.lineBox < HudTokens.NORMAL.lineHeight);
        assertEquals(HudTokens.NORMAL.lineHeight, HudLayoutEngine.lineHeight(HudSpec.builder("n").build()));
    }

    @Test public void widthChangesKeepLeftAndRightAnchorMarginsStable() {
        HudLayoutEngine engine = new HudLayoutEngine();
        HudLayoutEngine.PlacedHud leftShort = engine.layout(Arrays.asList(
                measured("left", HudAnchor.TOP_LEFT, 0, 30, 20, 0)), 200, 100, HudInsets.NONE).get(0);
        HudLayoutEngine.PlacedHud leftLong = engine.layout(Arrays.asList(
                measured("left", HudAnchor.TOP_LEFT, 0, 90, 20, 0)), 200, 100, HudInsets.NONE).get(0);
        HudLayoutEngine.PlacedHud rightShort = engine.layout(Arrays.asList(
                measured("right", HudAnchor.TOP_RIGHT, 0, 30, 20, 0)), 200, 100, HudInsets.NONE).get(0);
        HudLayoutEngine.PlacedHud rightLong = engine.layout(Arrays.asList(
                measured("right", HudAnchor.TOP_RIGHT, 0, 90, 20, 0)), 200, 100, HudInsets.NONE).get(0);
        assertEquals(leftShort.x, leftLong.x);
        assertEquals(200 - rightShort.x - rightShort.width, 200 - rightLong.x - rightLong.width);
    }

    @Test public void hudSpecValidatesGenericWidthBounds() {
        HudSpec spec = HudSpec.builder("widths").minWidth(20).maxWidth(80).build();
        assertEquals(20, spec.getMinWidth());
        assertEquals(80, spec.getMaxWidth());
        assertThrows(IllegalArgumentException.class, () -> HudSpec.builder("negative").minWidth(-1).build());
        assertThrows(IllegalArgumentException.class, () -> HudSpec.builder("zero-max").maxWidth(0).build());
        assertThrows(IllegalArgumentException.class, () -> HudSpec.builder("inverted").minWidth(81).maxWidth(80).build());
    }

    private static HudLayoutEngine.MeasuredHud measured(String id, HudAnchor anchor, int order,
            int width, int height, long registration) {
        HudSpec spec = HudSpec.builder(id).anchor(anchor).stackOrder(order).build();
        HudRegistry.Entry entry = new HudRegistry.Entry(spec, rt -> SceneNode.row(), registration);
        return new HudLayoutEngine.MeasuredHud(entry, width, height);
    }
    private static void assertBox(HudLayoutEngine.PlacedHud actual, int x, int y, int w, int h) {
        assertEquals(x, actual.x); assertEquals(y, actual.y);
        assertEquals(w, actual.width); assertEquals(h, actual.height);
    }
}
