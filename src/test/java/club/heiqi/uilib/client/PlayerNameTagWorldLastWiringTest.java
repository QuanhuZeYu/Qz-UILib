package club.heiqi.uilib.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/** 玩家名称标签 world-last 捕获、回放与生命周期接线的源码契约测试。 */
public class PlayerNameTagWorldLastWiringTest {

    private static final Path MIXIN = Paths.get(
            "src/main/java/club/heiqi/uilib/mixin/early/MixinRenderPlayerNameTag.java");
    private static final Path LISTENER = Paths.get(
            "src/main/java/club/heiqi/uilib/client/PlayerNameTagRenderListener.java");
    private static final Path CLIENT_PROXY = Paths.get(
            "src/main/java/club/heiqi/uilib/ClientProxy.java");

    /** Mixin 只捕获启用替换的玩家调用，并仅在成功入队后取消原调用。 */
    @Test
    public void mixinDefersOnlyConfiguredPlayerCalls() throws IOException {
        String source = source(MIXIN);
        String injection = methodBody(source, "private void qzuilib$deferPlayerNameTag(");

        assertTrue(source.contains("@Mixin(Render.class)"));
        assertTrue(source.contains("@Inject(method = \"func_147906_a\""));
        assertTrue(injection.contains("!FontConfig.replaceOrigin"));
        assertTrue(injection.contains("!(entity instanceof EntityPlayer)"));
        assertTrue(injection.contains("PlayerNameTagReplayQueue.isReplaying()"));
        int defer = injection.indexOf("PlayerNameTagReplayQueue.defer(");
        int cancel = injection.indexOf("ci.cancel();");
        assertTrue("必须先成功排队再取消原调用", defer >= 0 && cancel > defer);
    }

    /** 回放通过同一原方法 Invoker，Mixin 不复制原版标签几何或 GL 双 pass。 */
    @Test
    public void mixinReplaysOriginalMethodWithoutGeometryCopy() throws IOException {
        String source = source(MIXIN);

        assertTrue(source.contains("implements ReplayTarget"));
        assertTrue(source.contains("@Invoker(\"func_147906_a\")"));
        assertTrue(source.contains("public abstract void qzuilib$invokeNameTag("));
        assertFalse(source.contains("FontRenderer"));
        assertFalse(source.contains("Tessellator"));
        assertFalse(source.contains("GL11"));
    }

    /** listener 在 LOWEST world-last 排空，并在 RenderTick START/END 清理残留。 */
    @Test
    public void listenerOwnsWorldLastDrainAndBothTickBoundaries() throws IOException {
        String source = source(LISTENER);
        String worldLast = methodBody(source, "public void onRenderWorldLast(");
        String renderTick = methodBody(source, "public void onRenderTick(");

        assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(worldLast.contains("PlayerNameTagReplayQueue.drain();"));
        assertTrue(renderTick.contains("TickEvent.Phase.START"));
        assertTrue(renderTick.contains("TickEvent.Phase.END"));
        assertTrue(renderTick.contains("clearPendingReplays();"));
    }

    /** ClientProxy 把同一 listener 注册到 Forge/FML 总线，并在断连时清队列。 */
    @Test
    public void clientProxyRegistersBothBusesAndClearsOnDisconnect() throws IOException {
        String source = source(CLIENT_PROXY);
        String disconnect = methodBody(source, "public void onClientDisconnect(");

        assertTrue(source.contains("MinecraftForge.EVENT_BUS.register(playerNameTagRenderListener);"));
        assertTrue(source.contains("FMLCommonHandler.instance().bus().register(playerNameTagRenderListener);"));
        assertTrue(disconnect.contains("playerNameTagRenderListener.clearPendingReplays();"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
