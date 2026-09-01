package club.heiqi.uilib.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** listener 的唯一 HUD 帧围栏位置与禁用 API 源码结构门禁。 */
public class UiHudRenderListenerGlFenceTest {
    private static final Path LISTENER = Paths.get(
            "src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java");
    private static final Path GUARD = Paths.get(
            "src/main/java/club/heiqi/uilib/client/HudGlStateGuard.java");

    @Test
    public void ignoredEventsReturnBeforeTheSingleFrameFence() throws Exception {
        String source = source(LISTENER);
        int eventCheck = source.indexOf("event == null || event.type != RenderGameOverlayEvent.ElementType.ALL");
        int minecraftCheck = source.indexOf("if (minecraft == null) return;");
        int fence = source.indexOf("HUD_GL_STATE_GUARD.run(");

        assertTrue(eventCheck >= 0);
        assertTrue(minecraftCheck > eventCheck);
        assertTrue(fence > minecraftCheck);
        assertEquals(1, occurrences(source, "HUD_GL_STATE_GUARD.run("));
    }

    @Test
    public void completeHudLifecycleAndBothCleanupsStayInsideFenceDelegate() throws Exception {
        String source = source(LISTENER);
        int fence = source.indexOf("HUD_GL_STATE_GUARD.run(() -> renderHudFrame(");
        int renderMethod = source.indexOf("private void renderHudFrame(");
        int projection = source.indexOf("GL11.glMatrixMode(GL11.GL_PROJECTION)", renderMethod);
        int prepare = source.indexOf("UiHostRenderSupport.prepareMainUiRenderState()", renderMethod);
        int compositorBegin = source.indexOf("compositor.beginFrame()", renderMethod);
        int snapshotsBegin = source.indexOf("snapshots.beginFrame()", renderMethod);
        int context = source.indexOf("UiHostRenderSupport.createRenderContext(", renderMethod);
        int hostRender = source.indexOf("host.render(", renderMethod);
        int cleanup = source.indexOf("finishHudFrame()", renderMethod);

        assertTrue(fence >= 0);
        assertTrue(renderMethod > fence);
        assertTrue(projection > renderMethod);
        assertTrue(prepare > projection);
        assertTrue(compositorBegin > prepare);
        assertTrue(snapshotsBegin > compositorBegin);
        assertTrue(context > snapshotsBegin);
        assertTrue(hostRender > context);
        assertTrue(cleanup > hostRender);
        assertTrue(source.indexOf("snapshots.finishFrame()", cleanup) > cleanup);
        assertTrue(source.indexOf("compositor.finishFrame()", cleanup) > cleanup);
        assertFalse(source.contains("glPushMatrix"));
        assertFalse(source.contains("glPopMatrix"));
    }

    @Test
    public void guardAvoidsHeavyCompatibilityFboAndDiagnosticApis() throws Exception {
        String source = source(GUARD);
        assertFalse(source.contains("glPushAttrib"));
        assertFalse(source.contains("glPushClientAttrib"));
        assertFalse(source.contains("glClientActiveTexture"));
        assertFalse(source.contains("GL_TEXTURE_MATRIX"));
        assertFalse(source.contains("GL_TEXTURE_STACK_DEPTH"));
        assertFalse(source.contains("Tessellator"));
        assertFalse(source.contains("glGetError"));
        assertFalse(source.contains("Framebuffer"));
        assertFalse(source.contains("Renderbuffer"));
        assertFalse(source.contains("findDrift"));
    }

    @Test
    public void fenceIsNotReferencedByHudHostNodeCommandOrTextPaths() throws Exception {
        Path clientRoot = Paths.get("src/main/java/club/heiqi/uilib/client");
        int references = 0;
        try (java.util.stream.Stream<Path> files = Files.walk(clientRoot)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                if (file.equals(GUARD)) continue;
                references += occurrences(source(file), "HudGlStateGuard");
            }
        }
        assertEquals("围栏只允许由唯一 Forge HUD listener 持有", 2, references);
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 统计固定源码片段出现次数。 */
    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
