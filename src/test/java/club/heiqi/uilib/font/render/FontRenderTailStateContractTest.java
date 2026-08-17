package club.heiqi.uilib.font.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/**
 * 字体渲染尾状态补丁与原版幂等基线的源码契约测试。
 *
 * <p>幂等基线 = 原版 FontRenderer 本体尾状态：ALPHA_TEST enable、末字形 glColor4f、字体页纹理保持绑定。
 * 原时机接管路径（FontRendererFallbackInvoker 同步 drawString）在守卫 pop 后补齐该尾状态；位移时机路径
 * （PlayerNameTagRenderCoordinator 回放、HUD、deferred flush scope）不经过、也不补该尾状态。</p>
 */
public class FontRenderTailStateContractTest {

    private static final Path INVOKER = Paths.get(
            "src/main/java/club/heiqi/uilib/font/FontRendererFallbackInvoker.java");
    private static final Path BATCH_RENDERER = Paths.get(
            "src/main/java/club/heiqi/uilib/font/render/FontBatchRenderer.java");
    private static final Path STATE_SUPPORT = Paths.get(
            "src/main/java/club/heiqi/uilib/font/render/FontRenderStateSupport.java");
    private static final Path REPLAY_COORDINATOR = Paths.get(
            "src/main/java/club/heiqi/uilib/internal/font/PlayerNameTagRenderCoordinator.java");
    private static final Path HUD_LISTENER = Paths.get(
            "src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java");
    private static final Path ADAPTER = Paths.get(
            "src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java");

    /** 原时机路径：尾状态补丁在 adapter 同步 draw（守卫已 pop）之后执行，并补齐三项原版尾状态。 */
    @Test
    public void invokerAppliesVanillaTailStateAfterGuardedDraw() throws IOException {
        String source = source(INVOKER);
        String drawBody = methodBody(source, "public InvocationResult<Integer> drawString(");
        String patchBody = methodBody(source, "private static void applyVanillaDrawStringTailState(");

        int guardedDraw = drawBody.indexOf("drawBaselineAlignedString");
        int tailPatch = drawBody.indexOf("applyVanillaDrawStringTailState");
        assertTrue("invoker drawString 必须经过 adapter 同步 draw", guardedDraw >= 0);
        assertTrue("尾状态补丁必须在 adapter draw 之后（守卫 pop 之后）执行", tailPatch > guardedDraw);
        assertTrue("补丁必须无条件启用 ALPHA_TEST", patchBody.contains("glEnable(GL11.GL_ALPHA_TEST)"));
        assertTrue("补丁必须补齐末字形色", patchBody.contains("glColor4f"));
        assertTrue("补丁必须补齐最后页纹理绑定", patchBody.contains("glBindTexture"));    }

    /** 批渲染器在 flush 侧记录末字形色与最后页纹理，并在空帧 flush 时清空记录。 */
    @Test
    public void batchRendererRecordsTailStateAtFlushSide() throws IOException {
        String source = source(BATCH_RENDERER);
        String flushBody = methodBody(source, "public int flushWithinActiveState(");
        String clearBody = methodBody(source, "public void clearFrame()");

        assertTrue("flush 主路径必须记录尾状态", flushBody.contains("recordLastFlushTailState(boundTextureId)"));
        assertTrue("空帧 flush 必须清空尾状态记录", flushBody.contains("resetLastFlushTailState()"));
        assertTrue("clearFrame 必须清空收集侧末字形色", clearBody.contains("lastCollectedGlyphColor = NO_GLYPH_COLOR"));
        assertTrue("必须暴露末字形色 getter", source.contains("getLastFlushGlyphColor()"));
        assertTrue("必须暴露最后页纹理 getter", source.contains("getLastFlushBoundTextureId()"));
        assertTrue("必须暴露 flush 序号 getter", source.contains("getLastFlushSequence()"));
    }

    /** 位移时机路径（回放/HUD/deferred）不补尾状态，只依赖守卫还原进入态。 */
    @Test
    public void displacedTimingPathsDoNotApplyTailState() throws IOException {
        String coordinator = source(REPLAY_COORDINATOR);
        String replayBody = methodBody(coordinator, "private static void runReplayBatch(");
        assertFalse("回放路径不得补末字形色", replayBody.contains("glColor4f"));
        assertFalse("回放路径不得补 ALPHA_TEST", replayBody.contains("GL_ALPHA_TEST"));
        assertFalse("回放路径不得补纹理绑定", replayBody.contains("glBindTexture"));

        String hud = source(HUD_LISTENER);
        assertFalse("HUD 路径不得补 ALPHA_TEST", hud.contains("GL_ALPHA_TEST"));
        assertFalse("HUD 路径不得补末字形色", hud.contains("glColor4f"));

        String adapter = source(ADAPTER);
        assertFalse("deferred flush scope 宿主不得补 ALPHA_TEST", adapter.contains("GL_ALPHA_TEST"));
        assertFalse("deferred flush scope 宿主不得补末字形色", adapter.contains("glColor4f"));
    }

    /** 字体共享准备使用与原版世界路径一致的混合函数 (770,771,1,0)，不累积 dst-alpha。 */
    @Test
    public void fontStateSupportUsesVanillaConsistentBlendFunc() throws IOException {
        String body = methodBody(source(STATE_SUPPORT), "public static void prepareTextRenderState()");

        assertTrue("混合函数必须与原版一致（src_alpha, 1-src_alpha, one, zero）",
                body.contains("glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,"));
        assertTrue("dst-alpha 因子必须为 GL_ZERO", body.contains("GL11.GL_ONE, GL11.GL_ZERO)"));
        assertFalse("dst-alpha 不得再使用 ONE_MINUS_SRC_ALPHA 累积", body.contains("GL11.GL_ONE_MINUS_SRC_ALPHA);"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** 按方法标记提取配平花括号后的方法体。 */
    private static String methodBody(String source, String methodMarker) {
        int method = source.indexOf(methodMarker);
        Assert.assertTrue("缺少方法标记：" + methodMarker, method >= 0);
        int openingBrace = source.indexOf('{', method);
        Assert.assertTrue("方法缺少起始花括号：" + methodMarker, openingBrace >= 0);

        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, index);
                }
            }
        }
        Assert.fail("方法花括号未配平：" + methodMarker);
        return "";
    }
}
