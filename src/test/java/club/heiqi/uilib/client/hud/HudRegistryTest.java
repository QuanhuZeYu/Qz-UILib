package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudSpan;
import club.heiqi.uilib.ui.hud.api.HudTone;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** HUD 注册、快照与故障隔离契约测试。 */
public class HudRegistryTest {
    @Test public void rejectsDuplicateIdsAndCloseIsIdempotent() {
        HudRegistry registry = new HudRegistry();
        HudRegistration first = registry.register(spec("same", HudAnchor.TOP_LEFT, 0), this::oneLine);
        try {
            registry.register(spec("same", HudAnchor.TOP_LEFT, 0), this::oneLine);
            fail("duplicate id must fail");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("same")); }
        first.close(); first.close();
        assertTrue(first.isClosed());
        assertNotNull(registry.register(spec("same", HudAnchor.TOP_LEFT, 0), this::oneLine));
    }

    @Test public void snapshotPreventsCmeAndProviderFailureIsIsolated() {
        HudRegistry registry = new HudRegistry();
        AtomicInteger failures = new AtomicInteger();
        final HudRegistration[] self = new HudRegistration[1];
        self[0] = registry.register(spec("self", HudAnchor.TOP_LEFT, 0), () -> {
            self[0].close(); return oneLine();
        });
        registry.register(spec("broken", HudAnchor.TOP_LEFT, 0), () -> { throw new IllegalStateException("boom"); });
        registry.register(spec("healthy", HudAnchor.TOP_LEFT, 0), this::oneLine);
        List<HudRegistry.FrameEntry> frame = registry.snapshot(error -> failures.incrementAndGet());
        assertEquals(2, frame.size());
        assertEquals(1, failures.get());
        assertEquals(1, registry.snapshot(null).size());
    }

    @Test public void emptySnapshotKeepsRegisteredEntryForRetainedLifecycle() {
        HudRegistry registry = new HudRegistry();
        registry.register(spec("empty", HudAnchor.TOP_LEFT, 0), () -> HudSnapshot.EMPTY);
        assertEquals(1, registry.snapshot(null).size());
        assertTrue(registry.snapshot(null).get(0).snapshot.isEmpty());
    }

    @Test public void snapshotAndLinesAreImmutable() {
        List<HudLine> source = new ArrayList<HudLine>();
        source.add(HudLine.text("a", "A"));
        HudSnapshot snapshot = HudSnapshot.of(source);
        source.clear();
        assertEquals(1, snapshot.getLines().size());
        try { snapshot.getLines().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { /* expected */ }
        try { HudSnapshot.of(HudLine.text("a", "A"), HudLine.text("a", "B")); fail("duplicate line id"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("a")); }
    }

    @Test public void richLineValidatesAndDefensivelyCopiesSpans() {
        ArrayList<HudSpan> source = new ArrayList<HudSpan>();
        source.add(new HudSpan("value", null, HudTone.INFO));
        HudLine rich = HudLine.rich("line", source);
        source.clear();
        assertEquals(1, rich.getSpans().size());
        assertNull(rich.getSpans().get(0).getText());
        assertThrows(UnsupportedOperationException.class, () -> rich.getSpans().clear());
        assertThrows(IllegalArgumentException.class, () -> new HudSpan(" ", "x", HudTone.NORMAL));
        assertThrows(NullPointerException.class, () -> new HudSpan("x", "x", null));
        assertThrows(IllegalArgumentException.class, () -> HudLine.rich("duplicates",
                new HudSpan("same", "A", HudTone.NORMAL), new HudSpan("same", "B", HudTone.INFO)));

        HudLine legacy = HudLine.progress("legacy", "value", HudTone.WARNING, 0.5F);
        assertEquals(1, legacy.getSpans().size());
        assertEquals("legacy", legacy.getSpans().get(0).getId());
        assertEquals(legacy.getText(), legacy.getSpans().get(0).getText());
        assertEquals(legacy.getTone(), legacy.getSpans().get(0).getTone());
    }

    @Test public void clearInvalidatesExistingRegistrationsAndCloseRemainsIdempotent() {
        HudRegistry registry = new HudRegistry();
        HudRegistration hud = registry.register(spec("hud", HudAnchor.TOP_LEFT, 0), this::oneLine);
        HudRegistration avoidance = registry.registerAvoidance("avoid", () -> HudInsets.NONE);
        registry.clear();
        assertTrue(hud.isClosed());
        assertTrue(avoidance.isClosed());
        hud.close();
        avoidance.close();
        assertTrue(registry.snapshot(null).isEmpty());
        assertNotNull(registry.register(spec("hud", HudAnchor.TOP_LEFT, 0), this::oneLine));
    }

    private HudSnapshot oneLine() { return HudSnapshot.of(HudLine.text("line", "value")); }
    private static HudSpec spec(String id, HudAnchor anchor, int order) {
        return HudSpec.builder(id).anchor(anchor).stackOrder(order).build();
    }
}
