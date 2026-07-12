package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudTone;
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
        assertBox(result.get(0), 7, 9, 20, 10);
        assertBox(result.get(1), 69, 9, 20, 10);
        assertBox(result.get(2), 7, 57, 20, 10);
        assertBox(result.get(3), 69, 57, 20, 10);
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

    @Test public void compactPresetUsesSmallerTokensAndToneProgressArePreserved() {
        HudSpec normal = HudSpec.builder("normal").build();
        HudSpec compact = HudSpec.builder("compact").compact(true).build();
        assertTrue(HudLayoutEngine.lineHeight(compact) < HudLayoutEngine.lineHeight(normal));
        assertTrue(HudLayoutEngine.padding(compact) < HudLayoutEngine.padding(normal));
        HudLine line = HudLine.progress("p", "Load", HudTone.WARNING, 0.5F);
        assertEquals(HudTone.WARNING, line.getTone());
        assertEquals(0.5F, line.getProgress(), 0F);
    }

    private static HudLayoutEngine.MeasuredHud measured(String id, HudAnchor anchor, int order,
            int width, int height, long registration) {
        HudSpec spec = HudSpec.builder(id).anchor(anchor).stackOrder(order).build();
        HudRegistry.FrameEntry entry = new HudRegistry.FrameEntry(spec,
                HudSnapshot.of(HudLine.text("line", id)), registration);
        return new HudLayoutEngine.MeasuredHud(entry, width, height);
    }
    private static void assertBox(HudLayoutEngine.PlacedHud actual, int x, int y, int w, int h) {
        assertEquals(x, actual.x); assertEquals(y, actual.y);
        assertEquals(w, actual.width); assertEquals(h, actual.height);
    }
}
