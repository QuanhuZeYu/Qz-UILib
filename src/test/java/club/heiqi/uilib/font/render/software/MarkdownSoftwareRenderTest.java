package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/**
 * markdown L2 headless 出图验收（规划 §五之二：M3 两条必做验收面之一）。
 *
 * <p><b>装配复用</b>：与 {@link LatexSoftwareRenderKit} 共享同一份 {@code GlyphRuntimeTables}
 * （每实例约 123MiB，严禁另建 FontService/装配链），生产层全同源——
 * {@code TextLayoutService} 度量 → {@code MarkdownPainter.wrapLines} 换行 →
 * {@code DefaultFontRendererAdapter.renderSegmentsToCollector}（真机同一展平/收集逻辑）→
 * {@code FontSoftwareRasterizer} 软件光栅化。@AfterClass 释放共享装配。</p>
 *
 * <p><b>产物</b>：{@code build/reports/markdown-render/00-full-page.png}（全部样本纵向拼接 +
 * 每条左侧 label 列，人眼判对齐）+ {@code %02d-<case>.png} 逐样本放大页 + {@code profiles.txt}
 * 记环境（JVM/OS/字体现场指纹）与逐样本数值（行数/行宽/quad 数/墨水像素）。</p>
 *
 * <p><b>场地既有特性（如实声明）</b>：软件光栅器对 CJK 字形有水平重影（无 AA 多抽头 +
 * ink-bleed 外扩区直出所致），既有 LaTeX 场地 {@code \text{速度}} 出图同样如此（本轮以
 * HEAD 原代码对照实测，非 M3 回归）。人眼判读因此聚焦结构面：换行位置、字号阶梯、
 * 基线节奏、下划线/删除线、公式落位、列表符号；字形观感由真机验收。</p>
 *
 * <p><b>机器判地板</b>（不只「文件存在」）：每页非背景像素数 ≥ 地板值、每视觉行实测宽 ≤
 * 容器宽、行框顶 y 严格单调递增、链接样本必产带 URL 的 LINK_REGION、标题样本首行行高
 * 必然大于正文行高、围栏代码内容必为字面段。默认随 build 全量执行，无 Assume 门控。</p>
 */
public class MarkdownSoftwareRenderTest {

    private static final File OUT_DIR = new File("build/reports/markdown-render");
    private static final int BACKGROUND = 0xFF202020;
    private static final int BASE = 16;
    private static final int LABEL_BASE = 13;
    private static final int CONTENT_WIDTH_PX = 480;
    private static final int LABEL_GUTTER_PX = 150;
    private static final int PAD_PX = 8;
    private static final int BLOCK_GAP_PX = 18;

    /** 每页墨水像素地板：低于此值即「空跑蒙绿」级别（实测值记入 profiles.txt 供收紧）。 */
    private static final int MIN_INK_PIXELS_PER_PAGE = 500;

    /** 单个样本块：{ASCII slug, 中文 label, markdown 源}。 */
    private static final String[][] CASES = {
            {"heading", "标题1..6", "# 一级标题\n## 二级标题\n### 三级标题\n#### 四级标题\n正文回到基准字号。"},
            {"code-fence", "围栏代码", "```java\nclass Qz {\n    // 注释里 **星号** $公式$ > 引号 全字面\n}\n```"},
            {"nested-quote", "嵌套引用", "> 一层引用文本\n>> 二层引用文本\n>>> 三层引用文本\n> 惰性续行进上块"},
            {"list-continuation", "列表续行", "- 甲项首行\n  甲项缩进续行（懒延续）\n- 乙项\n  - 嵌套子项\n3. 有序三\n4) 有序四"},
            {"hard-break", "硬换行", "硬换行第一行  \n硬换行第二行（上行尾两空格）\\\n第三行（反斜杠硬换行）\n\n这是一段足够长的中文正文用于演示软换行在容器宽度处的折行行为，混合 English words 与 averyveryverylongunbreakstoken 时按词边界回退、超长 token 字符级硬断。"},
            {"inline-latex", "行内公式", "质能等价 $e = mc^2$ 与分数 $\\frac{1}{2}$ 混排在正文基线上。\n根号：$\\sqrt{x^2 + y^2}$ 收尾。"},
            {"link", "链接段", "访问 [Qz 主页](https://example.com/qz) 与 **粗体中的[嵌套链接](https://a.test)**。"},
            {"thematic-break", "分隔线", "上半句。\n---\n下半句。~~删除线~~ 与 `code span` 字面。"},
    };

    private static final StringBuilder PROFILE = new StringBuilder();

    /** 测量期本类临时解除宽度 miss 预算：预算近似（64/16ms 窗）会让同一码点在
     *  「换行时」与「地板复测时」取到不同宽度（实测漂移 480→669），出图判据必须
     *  在稳定度量下执行；用完复原全局值，不影响其它测试类。 */
    private int savedWidthMissBudget = -1;

    @Before
    public void liftWidthMissBudget() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }

    @After
    public void restoreWidthMissBudget() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }

    /** 释放共享字形表（约 123MiB），避免挤压测试 JVM 堆（与 LatexSoftwareRenderTest 同纪律）。 */
    @AfterClass
    public static void releaseSharedTables() throws Exception {
        if (PROFILE.length() > 0) {
            OUT_DIR.mkdirs();
            Files.write(new File(OUT_DIR, "profiles.txt").toPath(),
                    PROFILE.toString().getBytes(StandardCharsets.UTF_8));
        }
        LatexSoftwareRenderKit.resetShared();
    }

    // ==================== 出图主验收 ====================

    /** 逐样本出图 + 整页合成图 + 机器判地板（行宽上限/基线单调/墨水下限/LINK_REGION）。 */
    @Test
    public void writesPerCaseAndCompositePngsWithFloors() throws Exception {
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            throw new IllegalStateException("无法创建出图目录: " + OUT_DIR);
        }
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        MarkdownStyleTable styles = demoStyles();

        // 先一次性装配全部样本（含 label 文本）码点；再换行——装配会发布 ink 度量，
        // 换行必须用装配后的同源度量（否则行宽判据与渲染口径错位）
        List<TextSegment> allSegments = new ArrayList<TextSegment>();
        List<List<TextSegment>> segmentsPerCase = new ArrayList<List<TextSegment>>();
        for (int i = 0; i < CASES.length; i++) {
            List<TextSegment> segments = MarkdownDocument.parse(CASES[i][2])
                    .toSegments(styles, bodyStyle());
            allSegments.addAll(segments);
            allSegments.addAll(labelSegments(i));
            segmentsPerCase.add(segments);
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, allSegments);
        GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1);
        List<List<List<TextSegment>>> caseLines = new ArrayList<List<List<TextSegment>>>();
        for (int i = 0; i < segmentsPerCase.size(); i++) {
            caseLines.add(MarkdownPainter.wrapLines(segmentsPerCase.get(i), service,
                    CONTENT_WIDTH_PX, BASE));
        }

        // 逐样本页（可放大目检细节）
        for (int i = 0; i < CASES.length; i++) {
            renderCasePage(shared, view, service, i, caseLines.get(i),
                    new File(OUT_DIR, String.format("%02d-%s.png", Integer.valueOf(i + 1), CASES[i][0])));
        }
        // 整页合成图（多样本纵向拼接 + 左侧 label 列，人眼判整体对齐）
        renderComposite(shared, view, service, caseLines,
                new File(OUT_DIR, "00-full-page.png"));

        profileLine("env jvm=" + System.getProperty("java.version")
                + " os=" + System.getProperty("os.name") + " arch=" + System.getProperty("os.arch"));
        profileLine("fontScene " + LatexSoftwareRenderKit.platformFontReport());
        profileLine("container " + String.valueOf(CONTENT_WIDTH_PX) + "px gutter "
                + String.valueOf(LABEL_GUTTER_PX) + "px base " + String.valueOf(BASE) + "px");
    }

    /** 单样本页：label 在左栏顶、样本内容在右栏，逐行落 collector。 */
    private void renderCasePage(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, int caseIndex,
            List<List<TextSegment>> lines, File out) throws Exception {
        GlyphBatchCollector collector = new GlyphBatchCollector();
        int pageWidth = LABEL_GUTTER_PX + CONTENT_WIDTH_PX + 2 * PAD_PX;
        int contentX = PAD_PX + LABEL_GUTTER_PX;
        int[] cursor = renderBlock(shared, view, service, collector, lines, contentX, PAD_PX);
        renderSegments(shared, view, service, collector, labelSegments(caseIndex), PAD_PX, PAD_PX);
        int pageHeight = Math.max(cursor[0] + PAD_PX, BASE + 2 * PAD_PX);
        rasterizeAndWrite(shared, collector, pageWidth, pageHeight, out);
        enforceFloors(caseIndex, lines, service, collector, out, pageWidth, pageHeight);
    }

    /** 整页合成图：8 样本纵向拼接，每条左侧 label 文本。 */
    private void renderComposite(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, List<List<List<TextSegment>>> caseLines, File out) throws Exception {
        // 预估高度：先量后画（软件帧要求固定画布）
        int pageWidth = LABEL_GUTTER_PX + CONTENT_WIDTH_PX + 2 * PAD_PX;
        int totalHeight = PAD_PX;
        for (int i = 0; i < caseLines.size(); i++) {
            totalHeight += blockHeight(caseLines.get(i), service) + BLOCK_GAP_PX;
        }
        totalHeight += PAD_PX;
        GlyphBatchCollector collector = new GlyphBatchCollector();
        int contentX = PAD_PX + LABEL_GUTTER_PX;
        int y = PAD_PX;
        for (int i = 0; i < caseLines.size(); i++) {
            int[] cursor = renderBlock(shared, view, service, collector, caseLines.get(i), contentX, y);
            renderSegments(shared, view, service, collector, labelSegments(i), PAD_PX, y);
            y = cursor[0] + BLOCK_GAP_PX;
        }
        rasterizeAndWrite(shared, collector, pageWidth, totalHeight, out);
        int ink = countInk(FontSoftwareRasterizer.render(buildFrame(collector, pageWidth, totalHeight), shared.gl));
        profileLine(String.format("composite %s %dx%d quads=%d ink=%d", out.getName(),
                Integer.valueOf(pageWidth), Integer.valueOf(totalHeight),
                Integer.valueOf(collector.getQuadCount()), Integer.valueOf(ink)));
        Assert.assertTrue("合成图墨水像素地板: 实测=" + String.valueOf(ink),
                ink >= MIN_INK_PIXELS_PER_PAGE * CASES.length);
        Assert.assertTrue("合成图必须有 quad", collector.getQuadCount() > 0);
    }

    /** 画一个样本块的全部视觉行；返回 [下一可用 y]。 */
    private int[] renderBlock(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, GlyphBatchCollector collector, List<List<TextSegment>> lines,
            int x, int top) {
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int y = top;
        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            int height = MarkdownPainter.lineHeightPx(line, service, BASE);
            if (!line.isEmpty()) {
                adapter.renderSegmentsToCollector(line, shared.settings, service, view,
                        (float) x, (float) y, false, 1.0F, (float) BASE, collector);
            }
            y += height;
        }
        return new int[] {y};
    }

    private void renderSegments(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, GlyphBatchCollector collector, List<TextSegment> segments,
            int x, int y) {
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(segments, shared.settings,
                service, view, (float) x, (float) y, false, 1.0F, (float) LABEL_BASE, collector);
    }

    private SoftwareRenderFrame buildFrame(GlyphBatchCollector collector, int width, int height) {
        SoftwareRenderFrame frame = new SoftwareRenderFrame(width, height, BACKGROUND);
        if (!collector.getMarkBackgroundBatch().isEmpty()) {
            frame.addBatch(collector.getMarkBackgroundBatch());
        }
        for (int index = 0; index < collector.getActivePageCount(); index++) {
            frame.addBatch(collector.getActiveBatch(index));
        }
        if (!collector.getDecorationBatch().isEmpty()) {
            frame.addBatch(collector.getDecorationBatch());
        }
        return frame;
    }

    private void rasterizeAndWrite(LatexSoftwareRenderKit.Shared shared, GlyphBatchCollector collector,
            int width, int height, File out) throws Exception {
        SoftwareRenderFrame frame = buildFrame(collector, width, height);
        int[] pixels = FontSoftwareRasterizer.render(frame, shared.gl);
        FontSoftwareRasterizer.writePng(pixels, width, height, out);
    }

    /** 机器判地板 + profiles 记录。 */
    private void enforceFloors(int caseIndex, List<List<TextSegment>> lines, TextLayoutService service,
            GlyphBatchCollector collector, File out, int width, int height) throws Exception {
        Assert.assertTrue("PNG 应已写出且非平凡: " + out, out.isFile() && out.length() > 1000);
        int previousTop = -1;
        int maxLineWidth = 0;
        int nonEmptyLines = 0;
        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            int lineWidth = MarkdownPainter.lineWidthPx(line, service, BASE);
            Assert.assertTrue("行宽不得超容器: case=" + CASES[caseIndex][0] + " line#" + i
                    + " 实测=" + String.valueOf(lineWidth) + " 容器=" + String.valueOf(CONTENT_WIDTH_PX)
                    + " 文本=<" + textOf(line) + "> 段数=" + String.valueOf(line.size()),
                    lineWidth <= CONTENT_WIDTH_PX);
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
            int top = previousTop < 0 ? -1 : previousTop
                    + MarkdownPainter.lineHeightPx(lines.get(i - 1), service, BASE);
            if (i > 0) {
                Assert.assertTrue("基线（行框顶 y）必须严格单调递增: case=" + CASES[caseIndex][0]
                        + " line#" + i, top > previousTop);
            }
            previousTop = top < 0 ? 0 : top;
            if (!line.isEmpty()) {
                nonEmptyLines++;
            }
        }
        Assert.assertTrue("样本必须产出非空视觉行: " + CASES[caseIndex][0], nonEmptyLines > 0);
        int[] pixels = readBack(out);
        int ink = countInk(pixels);
        Assert.assertTrue("页墨水像素地板: case=" + CASES[caseIndex][0] + " 实测=" + String.valueOf(ink),
                ink >= MIN_INK_PIXELS_PER_PAGE);
        Assert.assertTrue("quad 数地板: case=" + CASES[caseIndex][0],
                collector.getQuadCount() >= nonEmptyLines);
        profileLine(String.format("case %02d-%s lines=%d maxLineW=%d %dx%d quads=%d ink=%d bytes=%d",
                Integer.valueOf(caseIndex + 1), CASES[caseIndex][0], Integer.valueOf(lines.size()),
                Integer.valueOf(maxLineWidth), Integer.valueOf(width), Integer.valueOf(height),
                Integer.valueOf(collector.getQuadCount()), Integer.valueOf(ink),
                Long.valueOf(out.length())));
    }

    private int[] readBack(File png) throws Exception {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png);
        Assert.assertNotNull("PNG 必须可解码: " + png, image);
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        return pixels;
    }

    private static int countInk(int[] pixels) {
        int opaqueBackground = BACKGROUND | 0xFF000000;
        int count = 0;
        for (int pixel : pixels) {
            if (pixel != opaqueBackground) {
                count++;
            }
        }
        return count;
    }

    // ==================== 语义地板（与出图分离、纯逻辑） ====================

    /** L2 命令流地板：SEGMENTS 逐行、top 严格单调、链接必产 LINK_REGION、空行不产命令只占高。 */
    @Test
    public void paintCommandStreamIsWellFormed() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        List<TextSegment> segments = MarkdownDocument.parse(
                "# 标题\n\n段落里[链接](https://hit.test/x)结尾。\n\n第二段。")
                .toSegments(demoStyles(), bodyStyle());
        List<PaintCommand> commands =
                MarkdownPainter.toPaintCommands(segments, service, CONTENT_WIDTH_PX, BASE);
        Assert.assertTrue("命令流非空", !commands.isEmpty());
        int lastTop = -1;
        int segmentsCommands = 0;
        int linkRegions = 0;
        for (int i = 0; i < commands.size(); i++) {
            PaintCommand command = commands.get(i);
            if (command.getType() == PaintCommandType.SEGMENTS) {
                segmentsCommands++;
                Assert.assertTrue("SEGMENTS top 必须严格单调: " + command.getTop(),
                        command.getTop() > lastTop || lastTop < 0);
                lastTop = Math.max(lastTop, command.getTop());
                Assert.assertTrue("SEGMENTS 段流不可为空", command.getSegments() != null
                        && !command.getSegments().isEmpty());
            } else if (command.getType() == PaintCommandType.LINK_REGION) {
                linkRegions++;
                Assert.assertEquals("链接命中区必须携带 URL", "https://hit.test/x", command.getLinkUrl());
                Assert.assertTrue("命中区必须高于 0 宽", command.getRight() > command.getLeft());
                Assert.assertTrue("命中区必须落在某行框内（top 不早于首命令）",
                        command.getTop() >= 0);
            }
        }
        Assert.assertTrue("至少 3 个非空行 → SEGMENTS ≥ 3，实测=" + String.valueOf(segmentsCommands),
                segmentsCommands >= 3);
        Assert.assertTrue("链接段必须产 LINK_REGION，实测=" + String.valueOf(linkRegions),
                linkRegions >= 1);
        // measureHeight 与命令游标同源：总高 ≥ 末 SEGMENTS top + 其行高
        int totalHeight = MarkdownPainter.measureHeight(segments, service, CONTENT_WIDTH_PX, BASE);
        Assert.assertTrue("总高必须覆盖末命令行框: total=" + String.valueOf(totalHeight)
                + " lastTop=" + String.valueOf(lastTop), totalHeight > lastTop);
    }

    /** 换行行为矩阵（K3 语义在段流形态上的等价复写；宽度判据全用实测值，不依赖平台常量）。 */
    @Test
    public void wrapSemanticsMatrix() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        TextStyle base = bodyStyle();

        // 1) 硬换行：段流独立 "\n" 段与段内嵌 "\n" 都断行
        List<List<TextSegment>> hard = MarkdownPainter.wrapLines(
                MarkdownDocument.parse("甲行\n乙行").toSegments(base), service, 400, BASE);
        Assert.assertEquals("\\n 硬断两行", 2, hard.size());

        // 2) 围栏代码内多行进同一段：段内 \n 也要断行
        List<List<TextSegment>> fenced = MarkdownPainter.wrapLines(
                MarkdownDocument.parse("```\nline1\nline2\nline3\n```").toSegments(base),
                service, 400, BASE);
        Assert.assertEquals("围栏 3 行", 3, fenced.size());
        Assert.assertEquals("围栏内容字面", "line1", textOf(fenced.get(0)));

        // 3) 词边界回退：以实测宽度构造「两词各自放得下、连空格放不下」的容器宽，
        //    断行必须发生在空白处（续行是完整 world），不是词内硬断（K3 语义）
        int helloWidth = MarkdownPainter.lineWidthPx(MarkdownInline("hello"), service, BASE);
        int worldWidth = MarkdownPainter.lineWidthPx(MarkdownInline("world"), service, BASE);
        int maxW = Math.max(helloWidth, worldWidth) + 4;
        List<List<TextSegment>> word = MarkdownPainter.wrapLines(
                MarkdownInline("hello world"), service, maxW, BASE);
        Assert.assertEquals("超宽必在空白处回退而非词内硬断: maxW=" + maxW, 2, word.size());
        Assert.assertEquals("hello", textOf(word.get(0)));
        Assert.assertEquals("world", textOf(word.get(1)));

        // 4) 无空格长串：字符级硬断且每行至少 1 字符、宽不超容器
        String longToken = "averyveryverylongunbreakstoken";
        List<List<TextSegment>> hard2 = MarkdownPainter.wrapLines(
                MarkdownInline(longToken), service, 60, BASE);
        Assert.assertTrue("长 token 必须被硬断成多行", hard2.size() > 1);
        StringBuilder rejoined = new StringBuilder();
        for (int i = 0; i < hard2.size(); i++) {
            List<TextSegment> line = hard2.get(i);
            Assert.assertTrue("硬断行不可为空行", !line.isEmpty());
            Assert.assertTrue("硬断行宽不得超容器: " + MarkdownPainter.lineWidthPx(line, service, BASE),
                    MarkdownPainter.lineWidthPx(line, service, BASE) <= 60);
            rejoined.append(textOf(line));
        }
        Assert.assertEquals("字符级硬断不丢内容", longToken, rejoined.toString());

        // 5) 公式原子：单条 latex 段宽超容器也不得被拆（产单行、宽可超——原子语义）
        List<TextSegment> mathOnly = Collections.singletonList(
                TextSegment.forLatex("\\frac{12345678}{98765432}", base.copy()));
        List<List<TextSegment>> atomic = MarkdownPainter.wrapLines(mathOnly, service, 40, BASE);
        Assert.assertEquals("公式段不可拆 → 单行", 1, atomic.size());
        Assert.assertTrue("公式段整段透传", atomic.get(0).get(0).isLatex());

        // 6) 空输入 → 单空行；空行行高按基准字号
        List<List<TextSegment>> empty = MarkdownPainter.wrapLines(
                Collections.<TextSegment>emptyList(), service, 400, BASE);
        Assert.assertEquals("空段流 → 单空行", 1, empty.size());
        Assert.assertTrue(empty.get(0).isEmpty());
        Assert.assertTrue("空行行高 ≥ 基准行高",
                MarkdownPainter.lineHeightPx(empty.get(0), service, BASE)
                        >= service.getAscent(BASE));

        // 7) null 度量必须 fail-fast（度量同源无后门）
        try {
            MarkdownPainter.wrapLines(mathOnly, null, 400, BASE);
            Assert.fail("measurer=null 必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    /** 标题阶梯：样式表登记的字号增量必须体现在行高单调上（人眼看图的真假判据先机器化一层）。 */
    @Test
    public void headingSizeLadderReachesLineGeometry() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        List<List<TextSegment>> lines = MarkdownPainter.wrapLines(
                MarkdownDocument.parse("# 一\n## 二\n### 三\n正文").toSegments(demoStyles(), bodyStyle()),
                service, CONTENT_WIDTH_PX, BASE);
        Assert.assertEquals(4, lines.size());
        int h1 = MarkdownPainter.lineHeightPx(lines.get(0), service, BASE);
        int h2 = MarkdownPainter.lineHeightPx(lines.get(1), service, BASE);
        int h3 = MarkdownPainter.lineHeightPx(lines.get(2), service, BASE);
        int body = MarkdownPainter.lineHeightPx(lines.get(3), service, BASE);
        Assert.assertTrue("H1>H2>H3>正文 行高阶梯（实测 " + h1 + ">" + h2 + ">" + h3 + ">" + body + "）",
                h1 > h2 && h2 > h3 && h3 > body);
    }

    // ==================== 共用装配 ====================

    private static List<TextSegment> labelSegments(int caseIndex) {
        TextStyle label = new TextStyle();
        label.setColor(0xFF8ADFFF);
        return Collections.singletonList(new TextSegment(
                String.format("%02d %s", Integer.valueOf(caseIndex + 1), CASES[caseIndex][1]), label));
    }

    private static List<TextSegment> MarkdownInline(String text) {
        // 行内解析走 L1 既有入口，段样式与文档路径一致
        return MarkdownInlineParse(text);
    }

    private static List<TextSegment> MarkdownInlineParse(String text) {
        return MarkdownDocument.parse(text).toSegments(bodyStyle());
    }

    private static TextStyle bodyStyle() {
        return new TextStyle();
    }

    private static MarkdownStyleTable demoStyles() {
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE);
        styles.setHeadingFontSizeDeltaPx(1, 10);
        styles.setHeadingFontSizeDeltaPx(2, 7);
        styles.setHeadingFontSizeDeltaPx(3, 4);
        styles.setHeadingFontSizeDeltaPx(4, 2);
        styles.setHeadingFontSizeDeltaPx(5, 1);
        styles.setHeadingFontSizeDeltaPx(6, 0);
        return styles;
    }

    private static String textOf(List<TextSegment> line) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < line.size(); i++) {
            builder.append(line.get(i).getText());
        }
        return builder.toString();
    }

    private static void profileLine(String line) {
        PROFILE.append(line).append(System.lineSeparator());
    }

    private static int blockHeight(List<List<TextSegment>> lines, TextLayoutService service) {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += MarkdownPainter.lineHeightPx(lines.get(i), service, BASE);
        }
        return total;
    }
}
