package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 段流/纯文本的 y 锚点契约守卫：防「双重 ascent」回归。
 *
 * <p>2026-09-02 真机反馈「气泡内文字偏下」。取证结论：{@code UiRenderContext.drawSegments}
 * 把 paint 层交来的 em-box 顶又加了一次 {@code getAscent(fontSize)} 才传给字形批，而字形批
 * 的 y 参数<b>本来就是字格顶</b>——quad 生成时内部按 {@code lineBaselineY * baselineScale}
 * 换算到基线（{@code DefaultFontRendererAdapter.drawPreparedTextIntoCollector} 里装饰线
 * 那段注释自证；{@code collectDecorations} 的下划线/高亮矩形同样以 drawY 为顶）。
 * 于是 ascent 被算两遍，13px 字号整体下沉 8px，气泡文字贴到下缘。</p>
 *
 * <p>陷阱的成因是<b>命名说谎</b>：{@code drawBaselineAlignedString} 沿自 vanilla，参数却是
 * 顶锚；同一锚点在 TEXT 路径（{@code drawTextResolved}）透传正确、只在 SEGMENTS 路径走样，
 * 是「1 权威 + N 处重实现」在纵轴上的又一例。headless 测试看不见（RecordingRenderBackend
 * 只记录命令坐标、不做锚点换算），所以必须用源码锁。</p>
 */
public class UiSegmentsBaselineAnchorTest {

    private static final String CONTEXT =
            "src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java";
    private static final String ADAPTER =
            "src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java";

    /** SEGMENTS 路径必须原样透传 y，绝不再加 ascent。 */
    @Test
    public void drawSegmentsPassesEmBoxTopThrough() throws IOException {
        String body = methodBody(read(CONTEXT), "public void drawSegments(");
        assertTrue("段流必须把 y 原样透传给字形批，实际方法体=" + body,
                body.indexOf("drawSegments(segments, x, y,") >= 0);
        assertFalse("段流路径不得再加 ascent（字形批内部已换算一次）：" + body,
                body.indexOf("getAscent") >= 0);
    }

    /** TEXT 路径同锚点：两条路必须一致，否则修一条会把另一条带偏。 */
    @Test
    public void drawTextPathUsesSameEmBoxTopAnchor() throws IOException {
        String body = methodBody(read(CONTEXT), "protected void drawTextResolved(");
        assertFalse("TEXT 路径也不得加 ascent（与 SEGMENTS 同口径）：" + body,
                body.indexOf("getAscent") >= 0);
    }

    /**
     * 字形批的 y 参数文档必须写明「字格顶」。
     *
     * <p>这条锁看着像文档洁癖，实则是本缺陷的<b>复发入口</b>：方法名里的 BaselineAligned
     * 会让下一个调用方认定 y 是基线并主动补 ascent。文档一旦改回「基线 Y」，同样的 8px
     * 下沉会再犯一次。</p>
     */
    @Test
    public void glyphBatchDocumentsTopAnchorNotBaseline() throws IOException {
        String source = read(ADAPTER);
        int at = source.indexOf("public int drawSegments(List<TextSegment> segments, float x, float y");
        assertTrue("找不到字形批 drawSegments 签名", at >= 0);
        String doc = source.substring(Math.max(0, at - 1600), at);
        assertTrue("drawSegments 的 @param y 文档必须写明字格顶（方法名会误导调用方补基线）",
                doc.indexOf("字格顶") >= 0);
    }

    /** 取方法签名之后第一个配平的代码块（含嵌套），纯括号计数，不用正则。 */
    private static String methodBody(String source, String signature) {
        int at = source.indexOf(signature);
        if (at < 0) {
            fail("找不到方法 " + signature);
        }
        int open = source.indexOf((char) 123, at);
        if (open < 0) {
            fail(signature + " 之后没有方法体");
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == (char) 123) {
                depth++;
            } else if (ch == (char) 125) {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        fail(signature + " 的方法体括号不配平");
        return "";
    }

    /** 兼容不同 Gradle 测试工作目录：优先相对路径，必要时逐级向上查找。 */
    private static String read(String relativePath) throws IOException {
        Path direct = Paths.get(relativePath);
        if (Files.isRegularFile(direct)) {
            return new String(Files.readAllBytes(direct), StandardCharsets.UTF_8);
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 " + relativePath);
    }
}
