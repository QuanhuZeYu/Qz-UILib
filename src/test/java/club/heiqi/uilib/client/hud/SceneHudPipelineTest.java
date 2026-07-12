package club.heiqi.uilib.client.hud;

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
}
