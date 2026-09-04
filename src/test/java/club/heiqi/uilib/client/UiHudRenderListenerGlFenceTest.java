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

    /**
     * 负向清单：守卫不得使用重兼容/诊断 API。
     *
     * <p>本方法原先只有十条 {@code assertFalse}，**没有任何正锚**：把 {@code HudGlStateGuard}
     * 整个掏空（甚至删掉那十条名字涉及的实现）也照样全绿。四条正锚的作用是先证明
     * 「读到了真的守卫源码」，再让负向清单有意义——R1 内聚轮（2026-09-04）补。
     * GL11 出现次数下界取自实测（本文件 {@code GL11.} 共 62 处），骤降即说明捕获/恢复被拆。</p>
     */
    @Test
    public void guardAvoidsHeavyCompatibilityFboAndDiagnosticApis() throws Exception {
        String source = source(GUARD);
        assertTrue("守卫源码必须读得到（空内容会让下面十条负向断言全部空转）", source.length() > 1000);
        assertTrue("守卫必须仍持有可注入的 GL 抽象", source.contains("private final GlAccess gl;"));
        assertTrue("围栏入口 run 必须存在", source.contains("void run(Runnable frame)"));
        assertTrue("捕获必须真读 GL 状态", source.contains("gl.isEnabled(GL11.GL_DEPTH_TEST)"));
        assertTrue("恢复路径必须存在", source.contains("private void restore()"));
        assertTrue("GL11 用量骤降说明捕获/恢复被拆（实测 62 处，地板 30）", occurrences(source, "GL11.") >= 30);

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
