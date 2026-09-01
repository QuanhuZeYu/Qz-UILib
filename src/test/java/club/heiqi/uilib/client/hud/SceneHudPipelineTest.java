package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** HUD 虚拟窗口（窗口工厂 + scene 代码）经 layout→paint→replay 出口的 headless 契约测试。 */
public class SceneHudPipelineTest {
    private static final FixedTextMeasurer MEASURER = new FixedTextMeasurer(8, 16);

    @Before public void setUp() { ReactiveScheduler.get().reset(); }
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    private static void registerText(HudRegistry registry, String id, String text) {
        registry.register(HudSpec.builder(id).build(), rt -> SceneNode.row()
                .setHitTestable(false).setText(text).setTextColor(0xFFFFFFFF).setFontSize(14));
    }

    @Test public void windowContentPaintsThroughPipelineWithoutGuiScreen() {
        HudRegistry registry = new HudRegistry();
        registerText(registry, "hello", "HUD");
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 100, 40, true, false);
        assertTrue(backend.getCalls().stream().anyMatch(call -> "drawText".equals(call.methodName())
                && "HUD".equals(call.getString(0))));
    }

    @Test public void sceneRichTextWorksInsideHudWindow() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("rich").build(), rt -> {
            SceneNode root = SceneNode.row().setHitTestable(false);
            rt.mount(root, SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create("<color=#FF5533>HUD</color>"), 0xFFFFFFFF, 14,
                    TextStyle.TEXT_MODE_RICH_TAGS)));
            return root;
        });
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 100, 40, true, false);
        // RICH 模式 drawText 原文含标签（渲染层按 mode 解析着色），断言内容子串即可
        assertTrue(backend.getCalls().stream().anyMatch(call -> "drawText".equals(call.methodName())
                && call.getString(0).contains("HUD")));
    }

    @Test public void signalDrivenContentInvalidatesLayoutAndChangesPaintPlan() {
        Signal<String> text = Signal.create("A");
        SceneHudHost.RetainedWindow window = new SceneHudHost.RetainedWindow(
                new HudRegistry.Entry(HudSpec.builder("signal").build(), rt -> {
                    SceneNode root = SceneNode.row().setHitTestable(false).setFontSize(14);
                    rt.bindText(root, text);
                    return root;
                }, 0L), MEASURER);
        window.measure(200, 80);
        ReactiveScheduler.get().flush();
        PaintPlan first = new ScenePaintEngine(MEASURER).paint(window.root()).getPlan();
        text.set("Changed");
        ReactiveScheduler.get().flush();
        window.measure(200, 80);
        PaintPlan second = new ScenePaintEngine(MEASURER).paint(window.root()).getPlan();
        assertNotEquals(first.getCommands().toString(), second.getCommands().toString());
    }

    @Test public void emptyContentHidesWholeWindowIncludingShell() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("hidden").build(), rt -> {
            Signal<Boolean> show = Signal.create(false);
            SceneNode root = SceneNode.row().setHitTestable(false);
            rt.show(root, show, () -> SceneNode.row().setHitTestable(false).setText("X").setFontSize(14));
            return root;
        });
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 100, 40, true, false);
        assertTrue(backend.getCalls().stream().noneMatch(call ->
                "drawText".equals(call.methodName()) || "fillRect".equals(call.methodName())));
    }

    /** 同锚点多窗堆叠：放置回写探针给出与渲染一致的权威盒，且互不重叠（方案 V4 补零覆盖）。 */
    @Test public void sameAnchorStackingExposesAuthoritativePlacements() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("low").anchor(HudAnchor.BOTTOM_LEFT).stackOrder(0)
                .margin(3).minWidth(1).build(),
                rt -> SceneNode.row().setHitTestable(false).setText("A").setFontSize(14));
        registry.register(HudSpec.builder("high").anchor(HudAnchor.BOTTOM_LEFT).stackOrder(1)
                .margin(3).minWidth(1).build(),
                rt -> SceneNode.row().setHitTestable(false).setText("BB").setFontSize(14));
        SceneHudHost host = new SceneHudHost(registry, MEASURER);
        host.render(new RecordingRenderBackend(), 200, 100, true, false);

        club.heiqi.uilib.ui.scene.layout.AnchorRect low = host.currentPlacement("low");
        club.heiqi.uilib.ui.scene.layout.AnchorRect high = host.currentPlacement("high");
        assertNotNull(low);
        assertNotNull(high);
        // 底锚点自下向上堆叠：低槽贴底（底边 = 100 - margin），高槽在其上方且留 STACK_GAP
        assertEquals(3, low.getX()); // BOTTOM_LEFT: x = margin
        assertEquals(100 - 3, low.getY() + low.getHeight());
        assertEquals(low.getY() - HudTokens.STACK_GAP, high.getY() + high.getHeight());
        assertTrue("同锚点两窗不得重叠", high.getY() + high.getHeight() < low.getY());
        // 未注册 id 与清理后的探针语义：null 而非陈旧盒
        assertNull(host.currentPlacement("absent"));
        host.clearWorld();
        assertNull(host.currentPlacement("low"));
    }

    /** 不可见窗口无本帧放置（返回 null 而非上一帧陈旧盒）。 */
    @Test public void invisibleWindowHasNoPlacement() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("gone").build(), rt -> SceneNode.row()
                .setHitTestable(false).setText("X").setFontSize(14));
        SceneHudHost host = new SceneHudHost(registry, MEASURER);
        host.render(new RecordingRenderBackend(), 100, 40, false, false); // 不在世界 → 不可见
        assertNull(host.currentPlacement("gone"));
    }

    @Test public void windowFactoryFailureIsIsolated() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("broken").build(),
                rt -> { throw new IllegalStateException("boom"); });
        registerText(registry, "healthy", "OK");
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 100, 40, true, false);
        assertTrue(backend.getCalls().stream().anyMatch(call -> "drawText".equals(call.methodName())
                && "OK".equals(call.getString(0))));
    }

    @Test public void constrainedHostEmitsClipInsideViewport() {
        HudRegistry registry = new HudRegistry();
        registerText(registry, "large", "content wider than viewport");
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 40, 24, true, false);
        RecordingRenderBackend.RenderCall clip = backend.getCalls().stream()
                .filter(call -> "pushClip".equals(call.methodName())).findFirst().orElse(null);
        assertNotNull(clip);
        assertTrue(clip.getInt(0) >= 0 && clip.getInt(1) >= 0);
        assertTrue(clip.getInt(2) <= 40 && clip.getInt(3) <= 24);
    }

    @Test public void shortContentIsIntrinsicAndLongContentClampsToViewport() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("short").margin(2).minWidth(1).build(), rt -> SceneNode.row()
                .setHitTestable(false).setText("A").setFontSize(14));
        RecordingRenderBackend shortBackend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(shortBackend, 200, 80, true, false);
        RecordingRenderBackend.RenderCall shortClip = firstCall(shortBackend, "pushClip");
        assertNotNull(shortClip);
        assertEquals(8 + HudTokens.NORMAL.paddingX * 2, shortClip.getInt(2) - shortClip.getInt(0));
        assertTrue(shortClip.getInt(2) - shortClip.getInt(0) < 200);

        HudRegistry longRegistry = new HudRegistry();
        longRegistry.register(HudSpec.builder("long").margin(3).build(), rt -> SceneNode.row()
                .setHitTestable(false).setText("content much wider than viewport").setFontSize(14));
        RecordingRenderBackend longBackend = new RecordingRenderBackend();
        new SceneHudHost(longRegistry, MEASURER).render(longBackend, 50, 80, true, false);
        RecordingRenderBackend.RenderCall longClip = firstCall(longBackend, "pushClip");
        assertEquals(44, longClip.getInt(2) - longClip.getInt(0));
    }

    @Test public void explicitMaxWidthIsHardLimitAgainstDefaultMinimumAndIntrinsicWidth() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("max-only").margin(0).maxWidth(20).build(),
                rt -> SceneNode.row().setHitTestable(false)
                        .setText("content wider than max").setFontSize(14));
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new SceneHudHost(registry, MEASURER).render(backend, 200, 80, true, false);

        RecordingRenderBackend.RenderCall clip = firstCall(backend, "pushClip");
        assertNotNull(clip);
        assertEquals(20, clip.getInt(2) - clip.getInt(0));
        assertThrows(IllegalArgumentException.class,
                () -> HudSpec.builder("inverted-explicit").minWidth(21).maxWidth(20).build());
    }

    @Test public void placementRelayoutKeepsShellDeclarationAndClipsToNewViewport() {
        SceneHudHost.RetainedWindow window = new SceneHudHost.RetainedWindow(
                new HudRegistry.Entry(HudSpec.builder("declaration").build(),
                        rt -> SceneNode.row().setHitTestable(false)
                                .setText("content wider than both viewports").setFontSize(14), 0L), MEASURER);
        window.measure(100, 40);
        window.measure(28, 18);
        assertEquals(0, window.root().getPreferredWidth());
        assertEquals(0, window.root().getPreferredHeight());
        assertEquals(SceneNode.WidthSizing.SHRINK, window.root().getWidthSizing());

        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("placed").margin(1).build(),
                rt -> SceneNode.row().setHitTestable(false)
                        .setText("content wider than both viewports").setFontSize(14));
        SceneHudHost host = new SceneHudHost(registry, MEASURER);
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
        HudRegistration registration = registry.register(HudSpec.builder("persistent").build(),
                rt -> SceneNode.row().setHitTestable(false).setText("reconnected").setFontSize(14));
        SceneHudHost host = new SceneHudHost(registry, MEASURER);
        host.render(new RecordingRenderBackend(), 100, 40, true, false);
        host.clearWorld();
        assertFalse(registration.isClosed());
        RecordingRenderBackend backend = new RecordingRenderBackend();
        host.render(backend, 100, 40, true, false);
        assertTrue(backend.getCalls().stream().anyMatch(call ->
                "drawText".equals(call.methodName()) && "reconnected".equals(call.getString(0))));
    }

    @Test public void renderPathDoesNotMutateShellSizingDeclaration() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/club/heiqi/uilib/client/hud/SceneHudHost.java")),
                StandardCharsets.UTF_8);
        String renderPath = source.substring(source.indexOf("public void render("), source.indexOf("public void clearWorld()"));
        assertFalse(renderPath.contains("root.setPreferredWidth"));
        assertFalse(renderPath.contains("root.setPreferredHeight"));
        assertFalse(renderPath.contains("root.setWidthSizing"));
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
        SceneHudHost host = new SceneHudHost(scaledRegistry(), MEASURER, setting);
        RecordingRenderBackend backend = new RecordingRenderBackend();
        host.render(backend, Math.round(400 * scale), Math.round(200 * scale), true, false);

        Field retainedField = SceneHudHost.class.getDeclaredField("retained");
        retainedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, SceneHudHost.RetainedWindow> retained =
                (Map<String, SceneHudHost.RetainedWindow>) retainedField.get(host);
        SceneHudHost.RetainedWindow window = retained.get("scaled");
        club.heiqi.uilib.ui.scene.layout.LayoutBox box =
                (club.heiqi.uilib.ui.scene.layout.LayoutBox) window.root().getCachedLayout();
        PaintPlan content = new ScenePaintEngine(MEASURER).paint(window.root()).getPlan();
        PaintPlan complete = new PaintPlan().addClipPush(0, 0, box.getWidth(), box.getHeight(), 0);
        for (club.heiqi.uilib.ui.scene.paint.PaintCommand command : content.getCommands()) complete.addCommand(command);
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

    private static HudRegistry scaledRegistry() {
        HudRegistry registry = new HudRegistry();
        registry.register(HudSpec.builder("scaled").build(),
                rt -> SceneNode.row().setHitTestable(false).setText("HUD").setFontSize(14));
        return registry;
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static RecordingRenderBackend.RenderCall firstCall(RecordingRenderBackend backend, String method) {
        return backend.getCalls().stream().filter(call -> method.equals(call.methodName())).findFirst().orElse(null);
    }
}
