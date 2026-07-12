package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudTone;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** HUD scene 内容通过 layout→paint→replay 出口的 headless 捕获测试。 */
public class SceneHudPipelineTest {
    @Before public void setUp() { ReactiveScheduler.get().reset(); }
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    @Test public void textHudPaintsThroughDisplayListWithoutGuiScreen() {
        SceneNode root = SceneNode.column().setPadding(2).setBackgroundColor(0xA0000000)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK).setHitTestable(false);
        root.appendChild(new SceneNode().setText("HUD").setTextColor(0xFFFFFFFF).setHitTestable(false));
        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(root, 100, 40);
        assertNotNull(ScenePaintCapture.firstFill(backend));
        assertTrue(backend.getCalls().stream().anyMatch(call -> "drawText".equals(call.methodName())));
    }

    @Test public void snapshotSignalFlushInvalidatesLayoutAndChangesPaintPlan() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        SceneHudHost.RetainedHud hud = new SceneHudHost.RetainedHud(HudSpec.builder("signal").build(), measurer);
        hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80));
        hud.accept(HudSnapshot.of(HudLine.text("line", "A")));
        assertFalse(hud.root().__isSelfLayoutDirty());
        ReactiveScheduler.get().flush();
        assertTrue(hud.root().__isSelfLayoutDirty() || hud.root().__isDescendantLayoutDirty());
        hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80));
        PaintPlan first = new ScenePaintEngine(measurer).paint(hud.root()).getPlan();
        hud.accept(HudSnapshot.of(HudLine.progress("line", "Changed", HudTone.WARNING, 0.5F)));
        ReactiveScheduler.get().flush();
        assertTrue(hud.root().__isDescendantLayoutDirty());
        hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80));
        PaintPlan second = new ScenePaintEngine(measurer).paint(hud.root()).getPlan();
        assertNotEquals(first.getCommands().toString(), second.getCommands().toString());
    }

    @Test public void constrainedHostEmitsClipInsideViewport() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("large").build(), () -> HudSnapshot.of(
                HudLine.text("one", "content wider than viewport"),
                HudLine.text("two", "second line"), HudLine.text("three", "third line")));
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, new FixedTextMeasurer(8, 16)).render(backend, 40, 24, true, false);
        RecordingRenderBackend.RenderCall clip = backend.getCalls().stream()
                .filter(call -> "pushClip".equals(call.methodName())).findFirst().orElse(null);
        assertNotNull(clip);
        assertTrue(clip.getInt(0) >= 0 && clip.getInt(1) >= 0);
        assertTrue(clip.getInt(2) <= 40 && clip.getInt(3) <= 24);
    }

    @Test public void renderPathDoesNotMutateRetainedNodeSizing() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/club/heiqi/uilib/client/hud/SceneHudHost.java")),
                StandardCharsets.UTF_8);
        String renderPath = source.substring(source.indexOf("public void render("), source.indexOf("public void clearWorld()"));
        assertFalse(renderPath.contains("root.setPreferredWidth"));
        assertFalse(renderPath.contains("root.setPreferredHeight"));
        assertFalse(renderPath.contains("root.setWidthSizing"));
    }

    @Test public void placementRelayoutKeepsRetainedRootDeclarationAndClipsToNewViewport() {
        SceneHudHost.RetainedHud retained = new SceneHudHost.RetainedHud(
                HudSpec.builder("declaration").build(), new FixedTextMeasurer(8, 16));
        retained.accept(HudSnapshot.of(HudLine.text("line", "content wider than both viewports")));
        ReactiveScheduler.get().flush();
        retained.layout(SceneHudHost.HudSceneConstraints.measurement(100, 40));
        retained.layout(new SceneHudHost.HudSceneConstraints(28, 18));
        assertEquals(0, retained.root().getPreferredWidth());
        assertEquals(0, retained.root().getPreferredHeight());
        assertEquals(SceneNode.WidthSizing.SHRINK, retained.root().getWidthSizing());

        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("placed").margin(1).build(), () -> HudSnapshot.of(
                HudLine.text("line", "content wider than both viewports")));
        SceneHudHost host = new SceneHudHost(registry, new FixedTextMeasurer(8, 16));

        RecordingRenderBackend wide = new RecordingRenderBackend();
        host.render(wide, 100, 40, true, false);
        RecordingRenderBackend narrow = new RecordingRenderBackend();
        host.render(narrow, 30, 20, true, false);

        RecordingRenderBackend.RenderCall clip = narrow.getCalls().stream()
                .filter(call -> "pushClip".equals(call.methodName())).findFirst().orElse(null);
        assertNotNull(clip);
        assertTrue(clip.getInt(2) - clip.getInt(0) <= 28);
        assertTrue(clip.getInt(3) - clip.getInt(1) <= 20);
    }

    @Test public void sessionResetKeepsRegistrationAndReconnectRebuildsScene() {
        HudRegistry registry = new HudRegistry();
        club.heiqi.uilib.ui.hud.api.HudRegistration registration = registry.register(
                HudSpec.builder("persistent").build(), () -> HudSnapshot.of(HudLine.text("line", "reconnected")));
        SceneHudHost host = new SceneHudHost(registry, new FixedTextMeasurer(8, 16));
        host.render(new RecordingRenderBackend(), 100, 40, true, false);
        host.clearWorld();
        assertFalse(registration.isClosed());
        RecordingRenderBackend backend = new RecordingRenderBackend();
        host.render(backend, 100, 40, true, false);
        assertTrue(backend.getCalls().stream().anyMatch(call ->
                "drawText".equals(call.methodName()) && "reconnected".equals(call.getString(0))));
    }

    @Test public void hudSourcesRejectMinecraftScaledCoordinates() throws Exception {
        String listener = source("src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java");
        String host = source("src/main/java/club/heiqi/uilib/client/hud/SceneHudHost.java");
        assertFalse(listener.contains("ScaledResolution"));
        assertFalse(host.contains("ScaledResolution"));
        assertFalse(host.contains("guiScale"));
    }

    @Test public void independentHudScaleScalesGeometryClipAndFontExactlyOnce() {
        float[] scales = { 1F, 1.25F, 1.5F, 1.75F, 2F };
        for (float scale : scales) {
            HudRegistry registry = compactRegistry();
            HudScaleSetting setting = new HudScaleSetting();
            setting.set(scale);
            RecordingRenderBackend backend = new RecordingRenderBackend();
            new SceneHudHost(registry, new FixedTextMeasurer(8, 16), setting)
                    .render(backend, 400, 200, true, false);
            RecordingRenderBackend.RenderCall clip = backend.getCalls().stream()
                    .filter(call -> "pushClip".equals(call.methodName())).findFirst().orElse(null);
            RecordingRenderBackend.RenderCall text = backend.getCalls().stream()
                    .filter(call -> "drawText".equals(call.methodName())).findFirst().orElse(null);
            assertNotNull(clip); assertNotNull(text);
            assertEquals(Math.round(8 * scale), clip.getInt(0));
            assertEquals(Math.round(8 * scale), clip.getInt(1));
            assertEquals(Math.round(10 * scale), text.getInt(5));
        }
    }

    @Test public void tokenLineBoxesDoNotOverlapProgress() {
        SceneHudHost.RetainedHud hud = new SceneHudHost.RetainedHud(HudSpec.builder("tokens").build(),
                new FixedTextMeasurer(8, 16));
        hud.accept(HudSnapshot.of(HudLine.progress("a", "A", HudTone.INFO, 0.5F),
                HudLine.text("b", "B")));
        ReactiveScheduler.get().flush();
        hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 100));
        SceneNode first = hud.root().__getChildren().get(0);
        SceneNode second = hud.root().__getChildren().get(1);
        club.heiqi.uilib.ui.scene.layout.LayoutBox a =
                (club.heiqi.uilib.ui.scene.layout.LayoutBox) first.getCachedLayout();
        club.heiqi.uilib.ui.scene.layout.LayoutBox b =
                (club.heiqi.uilib.ui.scene.layout.LayoutBox) second.getCachedLayout();
        assertTrue(a.getY() + a.getHeight() <= b.getY());
        assertEquals(HudTokens.NORMAL.fontSize, first.__getChildren().get(0).getFontSize());
    }

    @Test public void compactTextOnlyAndProgressExitPreserveTokenGeometry() {
        SceneHudHost.RetainedHud hud = new SceneHudHost.RetainedHud(
                HudSpec.builder("compact-exit").compact(true).build(), new FixedTextMeasurer(8, 10));
        hud.accept(HudSnapshot.of(HudLine.text("a", "A"),
                HudLine.progress("b", "B", HudTone.INFO, 0.5F), HudLine.text("c", "C")));
        ReactiveScheduler.get().flush();
        LayoutAssertions.assertCompactRows(hud);

        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("compact-exit").compact(true).build(), () -> HudSnapshot.of(
                HudLine.text("a", "A"), HudLine.progress("b", "B", HudTone.INFO, 0.5F), HudLine.text("c", "C")));
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, new FixedTextMeasurer(8, 10))
                .render(backend, FramebufferViewportFactory.create(160, 80), true, false);
        assertTrue(backend.getCalls().stream().filter(call -> "drawText".equals(call.methodName()))
                .allMatch(call -> call.getInt(5) == 10));
        RecordingRenderBackend.RenderCall clip = backend.getCalls().stream()
                .filter(call -> "pushClip".equals(call.methodName())).findFirst().orElse(null);
        assertNotNull(clip);
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) if ("drawText".equals(call.methodName())) {
            assertTrue(call.getInt(1) >= clip.getInt(0) && call.getInt(1) < clip.getInt(2));
            assertTrue(call.getInt(2) >= clip.getInt(1) && call.getInt(2) < clip.getInt(3));
        }
    }

    private static final class LayoutAssertions {
        private static void assertCompactRows(SceneHudHost.RetainedHud hud) {
            club.heiqi.uilib.ui.scene.layout.LayoutBox root = hud.layout(
                    SceneHudHost.HudSceneConstraints.measurement(160, 80));
            int previousBottom = HudTokens.COMPACT.paddingY;
            for (int i = 0; i < hud.root().__getChildren().size(); i++) {
                SceneNode row = hud.root().__getChildren().get(i);
                club.heiqi.uilib.ui.scene.layout.LayoutBox box =
                        (club.heiqi.uilib.ui.scene.layout.LayoutBox) row.getCachedLayout();
                assertTrue(box.getY() >= previousBottom);
                assertEquals(i == 1 ? HudTokens.COMPACT.lineHeight + HudTokens.COMPACT.progressHeight
                        : HudTokens.COMPACT.lineHeight, row.getPreferredHeight());
                assertEquals(HudTokens.COMPACT.lineBox, row.__getChildren().get(0).getPreferredHeight());
                assertEquals(HudTokens.COMPACT.fontSize, row.__getChildren().get(0).getFontSize());
                assertEquals(i == 1 ? HudTokens.COMPACT.progressHeight : 0,
                        row.__getChildren().get(1).getPreferredHeight());
                previousBottom = box.getY() + box.getHeight();
            }
            assertEquals(previousBottom + HudTokens.COMPACT.paddingY, root.getHeight());
        }
    }

    private static HudRegistry compactRegistry() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("scaled").compact(true).build(),
                () -> HudSnapshot.of(HudLine.progress("line", "HUD", HudTone.INFO, 0.5F)));
        return registry;
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
