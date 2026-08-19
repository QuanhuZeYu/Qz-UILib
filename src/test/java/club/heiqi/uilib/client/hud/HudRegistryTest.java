package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudWindowFactory;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** HUD 注册表、封板帧列表与句柄契约测试。 */
public class HudRegistryTest {
    private static final HudWindowFactory FACTORY = rt -> SceneNode.row();

    @Test public void rejectsDuplicateIdsAndCloseIsIdempotent() {
        HudRegistry registry = new HudRegistry();
        HudRegistration first = registry.register(spec("same", HudAnchor.TOP_LEFT, 0), FACTORY);
        try {
            registry.register(spec("same", HudAnchor.TOP_LEFT, 0), FACTORY);
            fail("duplicate id must fail");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("same")); }
        first.close(); first.close();
        assertTrue(first.isClosed());
        assertNotNull(registry.register(spec("same", HudAnchor.TOP_LEFT, 0), FACTORY));
    }

    @Test public void frameEntriesIsAnImmutableRegistrationOrderSnapshot() {
        HudRegistry registry = new HudRegistry();
        registry.register(spec("first", HudAnchor.TOP_LEFT, 0), FACTORY);
        HudRegistration second = registry.register(spec("second", HudAnchor.TOP_LEFT, 0), FACTORY);
        List<HudRegistry.Entry> frame = registry.frameEntries();
        assertEquals(2, frame.size());
        assertEquals("first", frame.get(0).spec.getId());
        assertEquals("second", frame.get(1).spec.getId());
        assertThrows(UnsupportedOperationException.class, () -> frame.clear());
        second.close();
        assertEquals(1, registry.frameEntries().size());
        assertEquals("first", registry.frameEntries().get(0).spec.getId());
    }

    @Test public void clearInvalidatesExistingRegistrationsAndCloseRemainsIdempotent() {
        HudRegistry registry = new HudRegistry();
        HudRegistration hud = registry.register(spec("hud", HudAnchor.TOP_LEFT, 0), FACTORY);
        HudRegistration avoidance = registry.registerAvoidance("avoid", () -> HudInsets.NONE);
        registry.clear();
        assertTrue(hud.isClosed());
        assertTrue(avoidance.isClosed());
        hud.close();
        avoidance.close();
        assertTrue(registry.frameEntries().isEmpty());
        assertNotNull(registry.register(spec("hud", HudAnchor.TOP_LEFT, 0), FACTORY));
    }

    private static HudSpec spec(String id, HudAnchor anchor, int order) {
        return HudSpec.builder(id).anchor(anchor).stackOrder(order).build();
    }
}
