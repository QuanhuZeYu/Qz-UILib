package club.heiqi.uilib.font.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/** vanilla/direct adapter 的整串 demand 与 GL guard 顺序源码合同。 */
public class DefaultFontRendererDemandOrderContractTest {

    private static final Path ADAPTER = Paths.get(
            "src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java");

    @Test
    public void everyDirectDrawPreparesDemandBeforeEnteringRenderStateGuard() throws IOException {
        String source = source();
        assertDirectDrawOrder(source,
                "public int drawBaselineAlignedString(String text, int x, int y, int color, boolean dropShadow,\n"
                        + "            TextContentMode textContentMode, UiFontWeight fontWeight");
        assertDirectDrawOrder(source,
                "public int drawBaselineAlignedStringScaled(String text, float x, float y, int color, "
                        + "boolean dropShadow,\n            TextContentMode textContentMode, UiFontWeight fontWeight");
        assertDirectDrawOrder(source,
                "public int drawBaselineAlignedStringPx(String text, float x, float y, int color, "
                        + "boolean dropShadow,\n            TextMeasureStyle style)");
    }

    @Test
    public void preparationAndDrawAreCpuOnlyWithoutMidDrawUpload() throws IOException {
        String source = source();
        String preparation = methodBody(source, "private PreparedText prepareTextDemand(");
        String publication = methodBody(source, "private void submitVisibleDemandIfNeeded(");
        String draw = methodBody(source, "private int drawPreparedText(");
        String initialization = methodBody(source, "private void initializeForRender(");
        String readiness = methodBody(source, "private boolean requiresGlyphDemand(");

        assertTrue(preparation.contains("submitVisibleDemandIfNeeded"));
        assertTrue(publication.contains("submitGlyphGeneration"));
        assertFalse(preparation.contains("renderStateGuard"));
        assertFalse(preparation.contains("tickDrawStage"));
        assertFalse(preparation.contains("getPageTextureId"));
        // 批上传迁移到 RenderTick START 稳定阶段后，draw 收集路径不再触发任何上传；
        // 唯一例外：主渲染上下文未捕获（Splash 阶段）时按需泵送一次上传。
        assertFalse(draw.contains("tickDrawStage"));
        assertFalse(draw.contains("flushPendingUploads"));
        assertTrue(draw.contains("pumpWorldLoadUploads"));
        assertTrue(draw.contains("isRenderThreadCaptured"));
        assertTrue(draw.contains("getPageTextureId"));
        assertTrue(initialization.contains("renderStateGuard.run"));
        assertTrue(initialization.contains("fontService.initialize"));
        assertTrue(readiness.contains("isCurrentPage"));
        assertTrue(readiness.contains("getSlotWidth"));
        assertFalse("prepare 热区不应恢复逐 glyph 对象分配", source.contains("PreparedGlyph"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(ADAPTER), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void assertDirectDrawOrder(String source, String methodMarker) {
        String body = methodBody(source, methodMarker);
        int initialization = body.indexOf("initializeForRender(fontService)");
        int preparation = body.indexOf("PreparedText preparedText = prepareTextDemand");
        int guardedDraw = body.indexOf("return drawWithRenderStateGuardIfNeeded");
        assertTrue("direct draw 初始化必须先经过受保护 lifecycle 边界", initialization >= 0);
        assertTrue("direct draw 必须在 guarded draw 前 prepare/submit demand", preparation > initialization);
        assertTrue("direct draw 的 upload/draw 必须晚于 demand", guardedDraw > preparation);
    }

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
