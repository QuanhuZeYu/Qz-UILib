package club.heiqi.uilib.mixin.early;

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

/**
 * MixinFontRenderer draw 注入点显式 cancel 契约测试。
 *
 * <p>三个 draw 接管点（drawString(I)/drawString(IIIIZ)/drawStringWithShadow）在 handled 分支内
 * 必须显式 {@code cir.cancel()}，明示「接管即替换」；cancel 只允许出现在 isHandled() 分支内，
 * 禁止无条件 cancel。五个度量注入点（getStringWidth/listFormattedStringToWidth/splitStringWidth/
 * trimStringToWidth×2）纯度量、无 GL 副作用，有意不 cancel。drawSplitString 的 {@code ci.cancel()}
 * 为范本基线，保持「接管守卫内取消」。</p>
 */
public class MixinFontRendererCancelContractTest {

    private static final Path MIXIN = Paths.get(
            "src/main/java/club/heiqi/uilib/mixin/early/MixinFontRenderer.java");

    /** drawString(String,int,int,int)：handled 分支内必须 setReturnValue 后显式 cancel。 */
    @Test
    public void drawStringHandledPathCancels() throws IOException {
        String body = methodBody(source(MIXIN), "method = \"drawString(Ljava/lang/String;III)I\"");
        assertHandledBranchHasExplicitCancel(body, "drawString(III)I");
    }

    /** drawString(String,int,int,int,boolean)（阴影变体）：handled 分支内必须显式 cancel。 */
    @Test
    public void drawStringShadowVariantHandledPathCancels() throws IOException {
        String body = methodBody(source(MIXIN), "method = \"drawString(Ljava/lang/String;IIIZ)I\"");
        assertHandledBranchHasExplicitCancel(body, "drawString(IIIIZ)");
    }

    /** drawStringWithShadow：handled 分支内必须显式 cancel。 */
    @Test
    public void drawStringWithShadowHandledPathCancels() throws IOException {
        String body = methodBody(source(MIXIN), "method = \"drawStringWithShadow\"");
        assertHandledBranchHasExplicitCancel(body, "drawStringWithShadow");
    }

    /** 边界：draw 接管点共恰 3 处 cir.cancel()，一律位于 isHandled() 分支内，禁止无条件 cancel。 */
    @Test
    public void cancelNeverEscapesHandledBranches() throws IOException {
        String source = source(MIXIN);
        assertEquals("draw 接管点必须恰有 3 处显式 cir.cancel()", 3, occurrences(source, "cir.cancel();"));
        assertEquals("范本 drawSplitString 保持 1 处 ci.cancel()", 1, occurrences(source, "ci.cancel();"));
        assertTrue("cancel 必须位于 isHandled() 分支判定之后",
                source.indexOf("cir.cancel();") > source.indexOf("if (result.isHandled()) {"));
    }

    /** 度量注入点逐点：纯度量、无 GL 副作用，即使 handled 也只 setReturnValue、不得 cancel。 */
    @Test
    public void metricInjectionPointsMustNotCancel() throws IOException {
        String source = source(MIXIN);
        String[] metricInjectMarkers = {
            "method = \"getStringWidth\"",
            "method = \"listFormattedStringToWidth\"",
            "method = \"splitStringWidth\"",
            "method = \"trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;\"",
            "method = \"trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;\""
        };
        for (String marker : metricInjectMarkers) {
            String body = methodBody(source, marker);
            assertFalse(marker + " 度量注入点不得出现 cancel", body.contains("cancel"));
            assertTrue(marker + " 度量注入点仍须持有 handled 返回值",
                    body.contains("cir.setReturnValue(result.getValue());"));
        }
        assertTrue("度量注入点必须附「有意不 cancel」说明注释", source.contains("有意不 cancel"));
    }

    /** 范本守护：drawSplitString 的 ci.cancel() 必须位于接管守卫分支内，守卫之外不得取消。 */
    @Test
    public void drawSplitStringCancelBaselineGuarded() throws IOException {
        String body = methodBody(source(MIXIN), "method = \"drawSplitString\"");
        assertTrue("范本 drawSplitString 必须 ci.cancel()", body.contains("ci.cancel();"));
        int guardStart = body.indexOf("if (qzuilib$fontInvoker.drawSplitString(");
        Assert.assertTrue("范本取消必须由接管守卫触发", guardStart >= 0);
        assertTrue("范本 cancel 必须位于守卫分支内", body.indexOf("ci.cancel();") > guardStart);
        assertFalse("守卫之外（drawSplitString 未接管时）不得取消", body.substring(0, guardStart).contains("cancel"));
    }

    private static void assertHandledBranchHasExplicitCancel(String body, String label) {
        int ifStart = body.indexOf("if (result.isHandled()) {");
        Assert.assertTrue(label + " 缺少 isHandled() 分支", ifStart >= 0);
        int braceOpen = body.indexOf('{', ifStart);
        int braceClose = matchingBrace(body, braceOpen);
        String branch = body.substring(braceOpen + 1, braceClose);
        assertTrue(label + " handled 分支必须 setReturnValue(result.getValue())",
                branch.contains("cir.setReturnValue(result.getValue());"));
        assertTrue(label + " handled 分支必须显式 cir.cancel()", branch.contains("cir.cancel();"));
        assertTrue(label + " cancel 必须位于 setReturnValue 之后",
                branch.indexOf("cir.cancel();") > branch.indexOf("cir.setReturnValue(result.getValue());"));
        assertFalse(label + " isHandled() 分支之外禁止 cancel（无条件 cancel 禁止）",
                body.substring(0, ifStart).contains(".cancel()"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** 按方法标记提取配平花括号后的方法体。 */
    private static String methodBody(String source, String marker) {
        int method = source.indexOf(marker);
        Assert.assertTrue("缺少方法标记：" + marker, method >= 0);
        int openingBrace = source.indexOf('{', method);
        Assert.assertTrue("方法缺少起始花括号：" + marker, openingBrace >= 0);
        return source.substring(openingBrace + 1, matchingBrace(source, openingBrace));
    }

    /** 从起始花括号起配平到对应闭合花括号。 */
    private static int matchingBrace(String source, int openingBrace) {
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        Assert.fail("花括号未配平");
        return -1;
    }

    /** 统计固定源码片段出现次数。 */
    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }
}
