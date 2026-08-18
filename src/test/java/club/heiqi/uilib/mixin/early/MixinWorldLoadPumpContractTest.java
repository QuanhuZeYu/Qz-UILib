package club.heiqi.uilib.mixin.early;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 世界加载上传泵的注入点与调用契约源码测试。
 */
public class MixinWorldLoadPumpContractTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java/club/heiqi/uilib");
    private static final Path PUMP_MIXIN = MAIN_ROOT.resolve("mixin/early/MixinMinecraftWorldLoadPump.java");
    private static final Path FONT_SERVICE = MAIN_ROOT.resolve("font/FontService.java");
    private static final Path EARLY_MIXINS = MAIN_ROOT.resolve("mixin/early/EarlyMixins.java");

    /** 泵 mixin 只注入服务端启动等待循环的 sleep 点与 loadWorld 入口，且只调用上传泵。 */
    @Test
    public void pumpMixinInjectsExactlyTwoVanillaPointsAndOnlyCallsPump() throws IOException {
        String mixin = source(PUMP_MIXIN);

        assertTrue(mixin.contains("@Mixin(value = Minecraft.class, priority = 900)"));
        assertEquals(2, occurrences(mixin, "@Inject("));
        assertTrue(mixin.contains(
                "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"));
        assertTrue(mixin.contains("target = \"Ljava/lang/Thread;sleep(J)V\""));
        assertTrue(mixin.contains("loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"));
        assertTrue(mixin.contains("@At(\"HEAD\")"));
        assertEquals(2, occurrences(mixin, "FontService.getInstance().pumpWorldLoadUploads()"));
        assertEquals(0, occurrences(mixin, "flushPendingUploads"));
        assertEquals(0, occurrences(mixin, "tickMainThread"));
        assertEquals(0, occurrences(mixin, "tickDrawStage"));
    }

    /** 上传泵：未初始化静默返回、只调页管理器批上传、异常只告警不传播。 */
    @Test
    public void pumpMethodIsSilentUninitializedAndOnlyFlushesPageManager() throws IOException {
        String service = source(FONT_SERVICE);
        String pump = methodBody(service, "public void pumpWorldLoadUploads()");

        assertTrue(pump.contains("if (!initialized.get())"));
        assertTrue(pump.contains("return;"));
        assertTrue(pump.contains("FontRuntimeAccess.run(runtimeOwnerToken"));
        assertTrue(pump.contains("glyphPageManager.flushPendingUploads(64)"));
        assertTrue(pump.contains("catch (RuntimeException"));
        assertTrue(pump.contains("logWorldLoadPumpFailureOnce(exception)"));
        assertTrue(service.contains("WORLD_LOAD_PUMP_FAILURE_LOGGED"));
        assertEquals("泵不得推进 reload/reconcile/租约",
                0, occurrences(pump, "reconcileReload") + occurrences(pump, "tickMainThread"));
    }

    /** 泵 mixin 必须在客户端 early mixin 列表注册。 */
    @Test
    public void pumpMixinIsRegisteredInClientEarlyMixins() throws IOException {
        String earlyMixins = source(EARLY_MIXINS);

        assertTrue(earlyMixins.contains("mixins.add(\"MixinMinecraftWorldLoadPump\");"));
    }

    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String methodBody(String source, String methodMarker) {
        int method = source.indexOf(methodMarker);
        org.junit.Assert.assertTrue("缺少方法标记：" + methodMarker, method >= 0);
        int openingBrace = source.indexOf('{', method);
        org.junit.Assert.assertTrue("方法缺少起始花括号：" + methodMarker, openingBrace >= 0);
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
        org.junit.Assert.fail("方法花括号未配平：" + methodMarker);
        return "";
    }
}
