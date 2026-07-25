package club.heiqi.uilib.mixin.early.nametag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/** 玩家标签调用点、lightmap 状态、兼容隔离与非发布依赖的源码契约测试。 */
public class PlayerNameTagMixinContractTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java/club/heiqi/uilib");
    private static final Path HOST_MIXIN = MAIN_ROOT.resolve(
            "mixin/early/nametag/MixinEntityRendererPlayerNameTagPass.java");
    private static final Path LIVING_MIXIN = MAIN_ROOT.resolve(
            "mixin/early/nametag/MixinRendererLivingEntityPlayerNameTag.java");
    private static final Path PLAYER_MIXIN = MAIN_ROOT.resolve(
            "mixin/early/nametag/MixinRenderPlayerScoreboardNameTag.java");
    private static final Path ANGELICA_MIXIN = MAIN_ROOT.resolve(
            "mixin/early/nametag/MixinAngelicaPlayerNameTagReplay.java");
    private static final Path COORDINATOR = MAIN_ROOT.resolve(
            "internal/font/PlayerNameTagRenderCoordinator.java");
    private static final Path ANGELICA_GUARD = MAIN_ROOT.resolve(
            "internal/font/angelica/AngelicaNameTagReplayGuard.java");
    private static final Path EARLY_MIXINS = MAIN_ROOT.resolve("mixin/early/EarlyMixins.java");
    private static final Path DEPENDENCIES = Paths.get("dependencies.gradle");

    /** host 只包装 renderWorld(FJ)V 内两个 RenderGlobal.renderEntities 调用。 */
    @Test
    public void hostWrapsExactlyTwoVanillaEntityPassCalls() throws IOException {
        String source = source(HOST_MIXIN);

        assertEquals(1, occurrences(source, "@WrapOperation("));
        assertTrue(source.contains("method = \"renderWorld(FJ)V\""));
        assertTrue(source.contains(
                "target = \"Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(\""));
        assertTrue(source.contains("Lnet/minecraft/entity/EntityLivingBase;"));
        assertTrue(source.contains("Lnet/minecraft/client/renderer/culling/ICamera;F)V"));
        assertExactCountContract(source, 2);
        assertTrue(source.contains("PlayerNameTagRenderCoordinator.runHostPass"));
        assertEquals(1, occurrences(source,
                "original.call(renderGlobal, viewEntity, camera, partialTicks);"));
    }

    /** 普通名与计分板各只包装两个最终 func_147906_a 调用点。 */
    @Test
    public void playerLabelMixinsUseExactOwnersDescriptorsAndFilters() throws IOException {
        String living = source(LIVING_MIXIN);
        String player = source(PLAYER_MIXIN);

        assertCallPointMixin(
                living,
                "@Mixin(value = RendererLivingEntity.class, priority = 900)",
                "func_96449_a(Lnet/minecraft/entity/EntityLivingBase;DDDLjava/lang/String;FD)V",
                "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;func_147906_a(");
        assertCallPointMixin(
                player,
                "@Mixin(value = RenderPlayer.class, priority = 900)",
                "func_96449_a(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDLjava/lang/String;FD)V",
                "Lnet/minecraft/client/renderer/entity/RenderPlayer;func_147906_a(");
    }

    /** 可选 Mixin 只包装 coordinator 唯一 Runnable.run，并在 clinit TAIL 握手。 */
    @Test
    public void angelicaMixinOnlyGuardsReplayBatchAndInstallsHandshake() throws IOException {
        String mixin = source(ANGELICA_MIXIN);
        String coordinator = source(COORDINATOR);
        String replayMethod = methodBody(coordinator, "private static void runReplayBatch(Runnable batch)");

        assertTrue(mixin.contains(
                "@Mixin(value = PlayerNameTagRenderCoordinator.class, remap = false)"));
        assertTrue(mixin.contains("@Shadow"));
        assertTrue(mixin.contains("@Inject(method = \"<clinit>\", at = @At(\"TAIL\"), require = 1)"));
        assertTrue(mixin.contains("angelicaReplayGuardInstalled = true;"));
        assertTrue(mixin.contains("method = \"runReplayBatch(Ljava/lang/Runnable;)V\""));
        assertTrue(mixin.contains("target = \"Ljava/lang/Runnable;run()V\""));
        assertExactCountContract(mixin, 1);
        assertEquals(1, occurrences(mixin, "original.call(batch);"));
        assertEquals(1, occurrences(replayMethod, "batch.run();"));
        assertEquals(1, occurrences(replayMethod, "entityRenderer.enableLightmap(0.0D);"));
        assertEquals(1, occurrences(replayMethod, "entityRenderer.disableLightmap(0.0D);"));
        assertInOrder(
                replayMethod,
                "final EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;",
                "entityRenderer.enableLightmap(0.0D);",
                "try {",
                "batch.run();",
                "} finally {",
                "entityRenderer.disableLightmap(0.0D);");
    }

    /** 通用路径无 Angelica ABI，且方案不恢复 Redirect、HEAD cancel 或 world-last。 */
    @Test
    public void genericSourcesStayAngelicaFreeAndAvoidDetachedReplay() throws IOException {
        String generic = source(COORDINATOR)
                + source(HOST_MIXIN)
                + source(LIVING_MIXIN)
                + source(PLAYER_MIXIN)
                + source(EARLY_MIXINS);
        String allMixins = source(HOST_MIXIN)
                + source(LIVING_MIXIN)
                + source(PLAYER_MIXIN)
                + source(ANGELICA_MIXIN);

        assertFalse(generic.contains("import net.coderbot.iris"));
        assertFalse(generic.contains("GbufferPrograms"));
        assertFalse(generic.contains("CapturedRenderingState"));
        assertFalse(allMixins.contains("@Redirect"));
        assertFalse(allMixins.contains("@At(\"HEAD\")"));
        assertFalse(allMixins.contains("cancellable = true"));
        assertFalse(allMixins.contains("RenderWorldLastEvent"));
        assertFalse(allMixins.contains("PlayerNameTagReplayQueue"));
    }

    /** Angelica ABI 只落在可选 guard，依赖保留开发期、非发布语义。 */
    @Test
    public void angelicaAbiAndDependencyRemainOptionalAndNonPublishable() throws IOException {
        String guard = source(ANGELICA_GUARD);
        String dependencies = source(DEPENDENCIES);

        assertTrue(guard.contains("import net.coderbot.iris.layer.GbufferPrograms;"));
        assertTrue(guard.contains("import net.coderbot.iris.pipeline.WorldRenderingPhase;"));
        assertTrue(guard.contains("import net.coderbot.iris.uniforms.CapturedRenderingState;"));
        assertTrue(dependencies.contains(
                "devOnlyNonPublishable(project.elytraModpackVersion.gtnhdev(\"Angelica\"))"));
        assertFalse(dependencies.contains(
                "runtimeOnlyNonPublishable(project.elytraModpackVersion.gtnhdev(\"Angelica\"))"));
        assertFalse(dependencies.toLowerCase(java.util.Locale.ROOT).contains("mixinextras"));
    }

    private static void assertCallPointMixin(
            String source,
            String mixinMarker,
            String methodDescriptor,
            String invocationOwner) {
        assertEquals(1, occurrences(source, "@WrapOperation("));
        assertTrue(source.contains(mixinMarker));
        assertTrue(source.contains("method = \"" + methodDescriptor + "\""));
        assertTrue(source.contains("target = \"" + invocationOwner));
        assertTrue(source.contains("Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V"));
        assertExactCountContract(source, 2);
        assertTrue(source.contains("!FontConfig.replaceOrigin"));
        assertTrue(source.contains("entity instanceof AbstractClientPlayer"));
        assertTrue(source.contains("PlayerNameTagRenderCoordinator.captureOrRun"));
        assertEquals(2, occurrences(source,
                "original.call(renderer, entity, text, x, y, z, maxDistance);"));
        assertEquals(1, occurrences(source, "OpenGlHelper.lastBrightnessX"));
        assertEquals(1, occurrences(source, "OpenGlHelper.lastBrightnessY"));
        assertEquals(1, occurrences(source, "OpenGlHelper.setLightmapTextureCoords("));
        assertEquals(2, occurrences(source, "capturedLightmapX"));
        assertEquals(2, occurrences(source, "capturedLightmapY"));
        assertInOrder(
                source,
                "final float capturedLightmapX = OpenGlHelper.lastBrightnessX;",
                "final float capturedLightmapY = OpenGlHelper.lastBrightnessY;",
                "PlayerNameTagRenderCoordinator.captureOrRun",
                "OpenGlHelper.setLightmapTextureCoords(",
                "OpenGlHelper.lightmapTexUnit,",
                "capturedLightmapX,",
                "capturedLightmapY);",
                "original.call(renderer, entity, text, x, y, z, maxDistance);");
    }

    private static void assertExactCountContract(String source, int count) {
        assertTrue(source.contains("require = " + count));
        assertTrue(source.contains("expect = " + count));
        assertTrue(source.contains("allow = " + count));
    }

    /** 读取 UTF-8 源码或 Gradle 依赖声明。 */
    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 按方法标记提取配平花括号后的方法体。 */
    private static String methodBody(String source, String marker) {
        int method = source.indexOf(marker);
        Assert.assertTrue("缺少方法标记：" + marker, method >= 0);
        int openingBrace = source.indexOf('{', method);
        Assert.assertTrue("方法缺少起始花括号：" + marker, openingBrace >= 0);
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
        Assert.fail("方法花括号未配平：" + marker);
        return "";
    }

    /** 统计固定源码片段出现次数。 */
    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    /** 断言固定源码片段按给定顺序各出现一次。 */
    private static void assertInOrder(String source, String... needles) {
        int cursor = 0;
        for (String needle : needles) {
            int found = source.indexOf(needle, cursor);
            Assert.assertTrue("源码片段缺失或顺序错误：" + needle, found >= 0);
            cursor = found + needle.length();
        }
    }
}
