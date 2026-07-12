package club.heiqi.uilib.client;

import club.heiqi.uilib.client.hud.HudViewportMetrics;
import club.heiqi.uilib.client.hud.MinecraftHudEnvironment;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** listener 生产视口提取不受 Minecraft GUI scale 影响的边界测试。 */
public class UiHudRenderListenerViewportTest {
    @Test public void productionViewportAndCompleteRenderStayIdenticalAcrossMinecraftScales() {
        int[] guiScales = { 1, 2, 3, 0 };
        Capture expected = null;
        for (int guiScale : guiScales) {
            TestEnvironment environment = new TestEnvironment(321, 181, guiScale);
            HudViewportMetrics viewport = UiHudRenderListener.viewport(environment);
            assertEquals(321, viewport.getWidth());
            assertEquals(181, viewport.getHeight());
            assertEquals(guiScale, environment.guiScale());

            Capture actual = render(viewport);
            if (expected == null) expected = actual;
            else {
                assertEquals(expected.viewport, actual.viewport);
                assertEquals(expected.commands, actual.commands);
                assertEquals(expected.calls, actual.calls);
            }
        }
    }

    private static Capture render(HudViewportMetrics viewport) {
        FixedTextMeasurer measurer = new FixedTextMeasurer(7, 11);
        SceneNode root = SceneNode.column().setPadding(3).setClipChildren(true)
                .setBackgroundColor(0xA0102030).setPreferredWidth(57).setPreferredHeight(29);
        root.appendChild(new SceneNode().setText("viewport").setFontSize(13)
                .setPreferredWidth(49).setPreferredHeight(17));
        new SceneLayoutEngine(measurer).layout(root, new Constraints(viewport.getWidth(), viewport.getHeight()));
        PaintPlan plan = new ScenePaintEngine(measurer).paint(root).getPlan();
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(plan, backend, 5, 7);
        return new Capture(viewport.getWidth() + "x" + viewport.getHeight(),
                plan.getCommands().toString(), backend.getCalls().toString());
    }

    private static final class TestEnvironment implements MinecraftHudEnvironment {
        private final int width;
        private final int height;
        private final int guiScale;
        TestEnvironment(int width, int height, int guiScale) {
            this.width = width; this.height = height; this.guiScale = guiScale;
        }
        @Override public int displayWidth() { return width; }
        @Override public int displayHeight() { return height; }
        @Override public int guiScale() { return guiScale; }
    }

    private static final class Capture {
        private final String viewport;
        private final String commands;
        private final String calls;
        Capture(String viewport, String commands, String calls) {
            this.viewport = viewport; this.commands = commands; this.calls = calls;
        }
    }
}
