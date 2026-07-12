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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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

    @Test public void intrinsicWidthTracksShortestLongestAndDynamicText() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        SceneHudHost.RetainedHud hud = new SceneHudHost.RetainedHud(
                HudSpec.builder("intrinsic").minWidth(1).build(), measurer);
        hud.accept(HudSnapshot.of(HudLine.text("one", "A"), HudLine.text("two", "Longest")));
        ReactiveScheduler.get().flush();
        int multiLineWidth = hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80)).getWidth();
        assertEquals(7 * 8 + HudTokens.NORMAL.paddingX * 2, multiLineWidth);

        hud.accept(HudSnapshot.of(HudLine.text("one", "AB")));
        ReactiveScheduler.get().flush();
        int shortWidth = hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80)).getWidth();
        hud.accept(HudSnapshot.of(HudLine.text("one", "A much longer value")));
        ReactiveScheduler.get().flush();
        int longWidth = hud.layout(SceneHudHost.HudSceneConstraints.measurement(200, 80)).getWidth();
        assertTrue(shortWidth < longWidth);
    }

    @Test public void shortBackgroundIsIntrinsicAndLongContentClampsToViewport() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("short").margin(2).minWidth(1).build(),
                () -> HudSnapshot.of(HudLine.text("line", "A")));
        RecordingRenderBackend shortBackend = new RecordingRenderBackend();
        new SceneHudHost(registry, new FixedTextMeasurer(8, 16)).render(shortBackend, 200, 80, true, false);
        RecordingRenderBackend.RenderCall shortClip = firstCall(shortBackend, "pushClip");
        assertNotNull(shortClip);
        assertEquals(8 + HudTokens.NORMAL.paddingX * 2, shortClip.getInt(2) - shortClip.getInt(0));
        assertTrue(shortClip.getInt(2) - shortClip.getInt(0) < 200);

        HudRegistry longRegistry = new HudRegistry();
        longRegistry.register(HudSpec.builder("long").margin(3).minWidth(1).build(),
                () -> HudSnapshot.of(HudLine.text("line", "content much wider than viewport")));
        RecordingRenderBackend longBackend = new RecordingRenderBackend();
        new SceneHudHost(longRegistry, new FixedTextMeasurer(8, 16)).render(longBackend, 50, 80, true, false);
        RecordingRenderBackend.RenderCall longClip = firstCall(longBackend, "pushClip");
        assertEquals(44, longClip.getInt(2) - longClip.getInt(0));
    }

    @Test public void explicitMaxWidthIsHardLimitAgainstDefaultMinimumAndIntrinsicWidth() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("max-only").margin(0).maxWidth(20).build(),
                () -> HudSnapshot.of(HudLine.text("line", "content wider than max")));
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, new FixedTextMeasurer(8, 16)).render(backend, 200, 80, true, false);

        RecordingRenderBackend.RenderCall clip = firstCall(backend, "pushClip");
        assertNotNull(clip);
        assertEquals(20, clip.getInt(2) - clip.getInt(0));
        assertThrows(IllegalArgumentException.class,
                () -> HudSpec.builder("inverted-explicit").minWidth(21).maxWidth(20).build());
    }

    @Test public void progressFillUsesActualTrackWidthAcrossProgressLabelsAndClamp() {
        assertProgressGeometry("short-zero", "A", 0F, 200, 1);
        assertProgressGeometry("short-half", "A", 0.5F, 200, 1);
        assertProgressGeometry("short-full", "A", 1F, 200, 1);
        assertProgressGeometry("long-half", "Longest label", 0.5F, 200, 1);
        assertProgressGeometry("clamped-half", "content wider than clamp", 0.5F, 200, 44);
    }

    private static void assertProgressGeometry(String id, String label, float progress,
                                               int viewportWidth, int maxWidth) {
        HudRegistry registry = new HudRegistry();
        HudSpec.Builder builder = HudSpec.builder(id).margin(0).minWidth(1);
        if (maxWidth > 1) builder.maxWidth(maxWidth);
        registry.register(builder.build(), () -> HudSnapshot.of(HudLine.progress("line", label, HudTone.INFO, progress)));
        SceneHudHost host = new SceneHudHost(registry, new FixedTextMeasurer(8, 16));
        RecordingRenderBackend backend = new RecordingRenderBackend();
        host.render(backend, viewportWidth, 80, true, false);

        RecordingRenderBackend.RenderCall track = null;
        RecordingRenderBackend.RenderCall fill = null;
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if (!"fillRect".equals(call.methodName())) continue;
            if (call.getInt(4) == 0x60000000) track = call;
            if (call.getInt(4) == 0xFF55FFFF) fill = call;
        }
        assertNotNull("progress track 应进入实际 PaintPlan", track);
        int trackWidth = track.getInt(2) - track.getInt(0);
        int fillWidth = fill == null ? 0 : fill.getInt(2) - fill.getInt(0);
        assertEquals(Math.round(trackWidth * progress), fillWidth);
        if (maxWidth > 1) assertEquals(maxWidth - HudTokens.NORMAL.paddingX * 2, trackWidth);
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

    @Test public void independentHudScalePreservesLogicalPlanAndScalesCompleteReplayExactlyOnce() throws Exception {
        float[] scales = { 1F, 1.25F, 1.5F, 1.75F, 2F };
        HudFrame baseline = captureHudFrame(scales[0]);
        assertFalse("实际 HUD scene 应产生绘制命令", baseline.plan.getCommands().isEmpty());
        assertFalse("实际 HUD scene 应产生 backend replay 调用", baseline.calls.isEmpty());
        for (float scale : scales) {
            HudFrame actual = scale == scales[0] ? baseline : captureHudFrame(scale);
            assertCompleteLogicalPlan(baseline.plan, actual.plan, scale);
            assertCompleteScaledReplay(baseline.calls, actual.calls, scale);
        }
    }

    /** 捕获实际 HUD host 一帧的完整 logical plan 与 backend replay。 */
    private static HudFrame captureHudFrame(float scale) throws Exception {
        HudScaleSetting setting = new HudScaleSetting();
        setting.set(scale);
        SceneHudHost host = new SceneHudHost(compactRegistry(), new FixedTextMeasurer(8, 16), setting);
        RecordingRenderBackend backend = new RecordingRenderBackend();
        host.render(backend, Math.round(400 * scale), Math.round(200 * scale), true, false);

        Field retainedField = SceneHudHost.class.getDeclaredField("retained");
        retainedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, SceneHudHost.RetainedHud> retained =
                (Map<String, SceneHudHost.RetainedHud>) retainedField.get(host);
        SceneHudHost.RetainedHud hud = retained.get("scaled");
        club.heiqi.uilib.ui.scene.layout.LayoutBox box =
                (club.heiqi.uilib.ui.scene.layout.LayoutBox) hud.root().getCachedLayout();
        PaintPlan content = new ScenePaintEngine(new FixedTextMeasurer(8, 16)).paint(hud.root()).getPlan();
        PaintPlan complete = new PaintPlan().addClipPush(0, 0, box.getWidth(), box.getHeight(), 0);
        for (PaintCommand command : content.getCommands()) complete.addCommand(command);
        complete.addClipPop();
        return new HudFrame(complete, backend.getCalls());
    }

    /** 逐命令证明 scale 进入 backend 前不改变 logical PaintPlan。 */
    private static void assertCompleteLogicalPlan(PaintPlan expected, PaintPlan actual, float scale) {
        assertEquals("scale=" + scale + " logical 命令总数",
                expected.getCommands().size(), actual.getCommands().size());
        for (int i = 0; i < expected.getCommands().size(); i++) {
            assertEquals("scale=" + scale + " logical command=" + i,
                    expected.getCommands().get(i), actual.getCommands().get(i));
        }
    }

    /** 按既有全出口契约逐调用、逐参数证明实际 scene 输出只缩放一次。 */
    private static void assertCompleteScaledReplay(List<RecordingRenderBackend.RenderCall> expected,
                                                   List<RecordingRenderBackend.RenderCall> actual,
                                                   float scale) {
        assertEquals("scale=" + scale + " replay 调用总数", expected.size(), actual.size());
        for (int callIndex = 0; callIndex < expected.size(); callIndex++) {
            RecordingRenderBackend.RenderCall base = expected.get(callIndex);
            RecordingRenderBackend.RenderCall scaled = actual.get(callIndex);
            assertEquals("scale=" + scale + " call=" + callIndex + " 方法", base.methodName(), scaled.methodName());
            Object[] baseArgs = base.args();
            Object[] scaledArgs = scaled.args();
            assertEquals("scale=" + scale + " call=" + callIndex + " 参数总数", baseArgs.length, scaledArgs.length);
            for (int argumentIndex = 0; argumentIndex < baseArgs.length; argumentIndex++) {
                Object expectedArg = scaledArgument(base.methodName(), argumentIndex, baseArgs[argumentIndex], scale);
                assertEquals("scale=" + scale + " call=" + callIndex + " 参数 " + argumentIndex,
                        expectedArg, scaledArgs[argumentIndex]);
            }
        }
    }

    private static Object scaledArgument(String method, int index, Object value, float scale) {
        if (!(value instanceof Number)) return value;
        if (("pushTransform".equals(method) || "pushTransformLayer".equals(method)) && index < 2) {
            return ((Number) value).floatValue() * scale;
        }
        if (isScaledIntegerArgument(method, index)) return Math.round(((Number) value).intValue() * scale);
        return value;
    }

    private static boolean isScaledIntegerArgument(String method, int index) {
        if ("pushClip".equals(method)) return index < 5;
        if ("fillRect".equals(method) || "drawBorder".equals(method) || "drawImage".equals(method)) {
            return index >= ("drawImage".equals(method) ? 1 : 0) && index < ("drawImage".equals(method) ? 5 : 4);
        }
        if ("drawSurface".equals(method)) return index < 4 || index == 6;
        if ("drawText".equals(method)) return index == 1 || index == 2 || index == 5;
        if ("pushGroupOpacity".equals(method)) return index < 4;
        return ("pushTransform".equals(method) || "pushTransformLayer".equals(method)) && index >= 7;
    }

    /** 单档实际 HUD scene 的双出口捕获。 */
    private static final class HudFrame {
        private final PaintPlan plan;
        private final List<RecordingRenderBackend.RenderCall> calls;

        private HudFrame(PaintPlan plan, List<RecordingRenderBackend.RenderCall> calls) {
            this.plan = plan;
            this.calls = calls;
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
                .allMatch(call -> call.getInt(5) == 12));
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

    private static RecordingRenderBackend.RenderCall firstCall(RecordingRenderBackend backend, String method) {
        return backend.getCalls().stream().filter(call -> method.equals(call.methodName())).findFirst().orElse(null);
    }
}
