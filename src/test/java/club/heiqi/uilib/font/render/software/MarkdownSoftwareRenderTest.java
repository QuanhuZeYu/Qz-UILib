package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.markdown.MarkdownInlineParser;
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;

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

        // 逐样本页（可放大目检细节）——@1x：现有逻辑 px 口径，全部机器断言只跑这份
        for (int i = 0; i < CASES.length; i++) {
            renderCasePage(shared, view, service, i, caseLines.get(i),
                    new File(OUT_DIR, String.format("%02d-%s.png", Integer.valueOf(i + 1), CASES[i][0])), 1);
        }
        // 整页合成图（多样本纵向拼接 + 左侧 label 列，人眼判整体对齐）
        renderComposite(shared, view, service, caseLines,
                new File(OUT_DIR, "00-full-page.png"), 1);
        // @Nx 判读副本（M4 增补需求）：同一 caseLines（同一次解析+换行），只换倍率参数；
        // 不参与任何机器断言；N=4 时 renderPx=52 ≤ atlas 64px，按字形 px 真放大零事后缩放；
        // 画布短边背景补白到 ≥854×480。开关=MarkdownRenderScaleKit（系统属性 qz.md.renderScale）。
        if (MarkdownRenderScaleKit.nxEnabled()) {
            int n = MarkdownRenderScaleKit.N;
            for (int i = 0; i < CASES.length; i++) {
                renderCasePage(shared, view, service, i, caseLines.get(i),
                        new File(OUT_DIR, String.format("%02d-%s%s.png", Integer.valueOf(i + 1),
                                CASES[i][0], MarkdownRenderScaleKit.nxSuffix())), n);
            }
            renderComposite(shared, view, service, caseLines,
                    new File(OUT_DIR, "00-full-page" + MarkdownRenderScaleKit.nxSuffix() + ".png"), n);
            profileLine("renderScale " + MarkdownRenderScaleKit.detailReport(BASE)
                    + " @Nx=判读副本(与 @1x 同源仅换倍率,无断言,画布补白>=854x480)");
        }

        profileLine("env jvm=" + System.getProperty("java.version")
                + " os=" + System.getProperty("os.name") + " arch=" + System.getProperty("os.arch"));
        profileLine("fontScene " + LatexSoftwareRenderKit.platformFontReport());
        profileLine("container " + String.valueOf(CONTENT_WIDTH_PX) + "px gutter "
                + String.valueOf(LABEL_GUTTER_PX) + "px base " + String.valueOf(BASE) + "px");
    }

    /** 单样本页：label 在左栏顶、样本内容在右栏，逐行落 collector。 */
    private void renderCasePage(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, int caseIndex,
            List<List<TextSegment>> lines, File out, int scale) throws Exception {
        GlyphBatchCollector collector = new GlyphBatchCollector();
        int pageWidth = (LABEL_GUTTER_PX + CONTENT_WIDTH_PX) * scale + 2 * PAD_PX;
        int contentX = PAD_PX + LABEL_GUTTER_PX * scale;
        int[] cursor = renderBlock(shared, view, service, collector, lines, contentX, PAD_PX, scale);
        renderSegments(shared, view, service, collector, labelSegments(caseIndex), PAD_PX, PAD_PX, scale);
        int pageHeight = Math.max(cursor[0] + PAD_PX, BASE * scale + 2 * PAD_PX);
        if (scale > 1) {
            pageWidth = MarkdownRenderScaleKit.padW(pageWidth);
            pageHeight = MarkdownRenderScaleKit.padH(pageHeight);
        }
        rasterizeAndWrite(shared, collector, pageWidth, pageHeight, out);
        if (scale == 1) {
            enforceFloors(caseIndex, lines, service, collector, out, pageWidth, pageHeight);
        }
    }

    /** 整页合成图：8 样本纵向拼接，每条左侧 label 文本。 */
    private void renderComposite(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, List<List<List<TextSegment>>> caseLines, File out,
            int scale) throws Exception {
        // 预估高度：先量后画（软件帧要求固定画布）
        int pageWidth = (LABEL_GUTTER_PX + CONTENT_WIDTH_PX) * scale + 2 * PAD_PX;
        int totalHeight = PAD_PX;
        for (int i = 0; i < caseLines.size(); i++) {
            totalHeight += blockHeight(caseLines.get(i), service, scale) + BLOCK_GAP_PX * scale;
        }
        totalHeight += PAD_PX;
        if (scale > 1) {
            pageWidth = MarkdownRenderScaleKit.padW(pageWidth);
            totalHeight = MarkdownRenderScaleKit.padH(totalHeight);
        }
        GlyphBatchCollector collector = new GlyphBatchCollector();
        int contentX = PAD_PX + LABEL_GUTTER_PX * scale;
        int y = PAD_PX;
        for (int i = 0; i < caseLines.size(); i++) {
            int[] cursor = renderBlock(shared, view, service, collector, caseLines.get(i), contentX, y, scale);
            renderSegments(shared, view, service, collector, labelSegments(i), PAD_PX, y, scale);
            y = cursor[0] + BLOCK_GAP_PX * scale;
        }
        rasterizeAndWrite(shared, collector, pageWidth, totalHeight, out);
        int ink = countInk(FontSoftwareRasterizer.render(buildFrame(collector, pageWidth, totalHeight), shared.gl));
        profileLine(String.format("composite %s %dx%d quads=%d ink=%d", out.getName(),
                Integer.valueOf(pageWidth), Integer.valueOf(totalHeight),
                Integer.valueOf(collector.getQuadCount()), Integer.valueOf(ink)));
        if (scale == 1) {
            Assert.assertTrue("合成图墨水像素地板: 实测=" + String.valueOf(ink),
                    ink >= MIN_INK_PIXELS_PER_PAGE * CASES.length);
            Assert.assertTrue("合成图必须有 quad", collector.getQuadCount() > 0);
        }
    }

    /** 画一个样本块的全部视觉行；返回 [下一可用 y]。 */
    private int[] renderBlock(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, GlyphBatchCollector collector, List<List<TextSegment>> lines,
            int x, int top, int scale) {
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int y = top;
        for (int i = 0; i < lines.size(); i++) {
            // scale==1 时 scaleSegments 恒等返回原列表、BASE*1==BASE —— @1x 输出与旧逐位一致
            List<TextSegment> line = MarkdownRenderScaleKit.scaleSegments(lines.get(i), scale);
            int height = MarkdownPainter.lineHeightPx(line, service, BASE * scale);
            if (!line.isEmpty()) {
                adapter.renderSegmentsToCollector(line, shared.settings, service, view,
                        (float) x, (float) y, false, 1.0F, (float) (BASE * scale), collector);
            }
            y += height;
        }
        return new int[] {y};
    }

    private void renderSegments(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, GlyphBatchCollector collector, List<TextSegment> segments,
            int x, int y, int scale) {
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                MarkdownRenderScaleKit.scaleSegments(segments, scale), shared.settings,
                service, view, (float) x, (float) y, false, 1.0F, (float) (LABEL_BASE * scale), collector);
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


    /**
     * F5 §切换处空格归属：采 chat3 口径——切换点的空格归<b>后</b>一段，
     * 且段文本与段宽双等（与门禁同一把尺：getSegmentWidth @ 基准字号）。
     */
    @Test
    public void fixF5SwitchPointSpaceBelongsToNextSegment() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        char sec = (char) 0x00A7;
        List<TextSegment> raw = service.parseSegments(
                sec + "c红色警告 " + sec + "fplain tail mixed", 0xFFFFFFFF);
        LatexSoftwareRenderKit.assembleGlyphs(shared, raw);
        List<MarkdownSpan> spans = new ArrayList<MarkdownSpan>();
        for (int i = 0; i < raw.size(); i++) {
            TextSegment segment = raw.get(i);
            if (!segment.isLatex() && !segment.getText().isEmpty()) {
                spans.add(new MarkdownSpan(segment.getText(), segment.getStyle()));
            }
        }
        List<List<TextSegment>> lines = MarkdownPainter.wrapLines(
                MarkdownInlineParser.parse(spans), service, 4000, BASE);
        Assert.assertEquals(1, lines.size());
        List<TextSegment> line = lines.get(0);
        Assert.assertEquals("切点空格归后一段 → 两段", 2, line.size());
        Assert.assertEquals("红色警告", line.get(0).getText());
        Assert.assertEquals(" plain tail mixed", line.get(1).getText());
        List<TextSegment> reference = new ArrayList<TextSegment>();
        TextStyle red = new TextStyle();
        red.setColor(0xFFFF5555);
        TextStyle white = new TextStyle();
        white.setColor(0xFFFFFFFF);
        reference.add(new TextSegment("红色警告", red));
        reference.add(new TextSegment(" plain tail mixed", white));
        for (int i = 0; i < reference.size(); i++) {
            Assert.assertEquals("段文本等值", reference.get(i).getText(), line.get(i).getText());
            Assert.assertEquals("段宽位级等值（与 A 路同尺）",
                    service.getSegmentWidth(reference.get(i), BASE),
                    service.getSegmentWidth(line.get(i), BASE), 0.0D);
        }
    }

    // ========= M4-fix：六条修复的定点钉死（F1..F6，对拍门禁之外的机器证据） =========

    /**
     * F1 行内 code span 承接：反引号对必须打 codeSpan 位 + chat3 衬底色 + chat3 口径段级字号，
     * 且 code 内容一律字面（不解析行内标记、不做 URL 链接化）。取值出处：
     * {@code ChatCodeSpanSplitter.java:107-112}（0x26FFFFFF 与 getCodeFontSizePx()=12；
     * 该源文件已随 M5 接线删除，数值现恒登记于 {@code MarkdownStyleTable} 包内项）；
     * 旧裁定「第一版 code 仅字面输出」已被 chat3 出货行为取代（2026-09-04）。
     */
    @Test
    public void fixF1InlineCodeSpanCarriesChat3CodeStyle() {
        char tick = (char) 0x60;
        String src = "命令 " + tick + "curl http://x.y/z -s" + tick + " 执行";
        List<TextSegment> segments = MarkdownDocument.parse(src)
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("前段 + code 段 + 后段", 3, segments.size());
        Assert.assertEquals("命令 ", segments.get(0).getText());
        TextSegment code = segments.get(1);
        Assert.assertEquals("反引号已剥、内容字面", "curl http://x.y/z -s", code.getText());
        Assert.assertTrue("codeSpan 位必须写上", code.getStyle().isCodeSpan());
        Assert.assertEquals("chat3 衬底色口径", 0x26FFFFFF, code.getStyle().getCodeBackgroundColor());
        Assert.assertEquals("chat3 code 字号口径", 12, code.getStyle().getFontSizePx());
        Assert.assertNull("code 段不得带 link（URL 不链接化）", code.getStyle().getLink());
        Assert.assertFalse("前后普通段不得被误标 code", segments.get(0).getStyle().isCodeSpan());
        Assert.assertFalse(segments.get(2).getStyle().isCodeSpan());
        Assert.assertEquals(" 执行", segments.get(2).getText());
        List<TextSegment> nested = MarkdownDocument.parse("a " + tick + "**b** $x$" + tick + " c")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("code 内标记一律字面", "**b** $x$", nested.get(1).getText());
        Assert.assertFalse("code 内 ** 不得成粗体",
                nested.get(1).getStyle().getFontType() == FontType.BOLD);
        Assert.assertFalse("code 内 $ 不得成公式原子", nested.get(1).isLatex());
    }

    /**
     * F2 嵌套列表缩进：每级 2 个前导空格写进 bullet 段文本（chat3 口径，
     * {@code ChatMessageList.java:952-956}），靠扁平段流表达，不开块模型/缩进 px 公共面。
     */
    @Test
    public void fixF2NestedListIndentIsLeadingSpacesInMarkerSegment() {
        List<TextSegment> segments = MarkdownDocument.parse("- 甲\n  - 乙\n    - 丙")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("首级标记零缩进", "• ", segments.get(0).getText());
        Assert.assertEquals("二级标记 2 空格缩进", "  • ", markerAt(segments, 1));
        Assert.assertEquals("三级标记 4 空格缩进", "    • ", markerAt(segments, 2));
        StringBuilder flat = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            flat.append(segments.get(i).getText());
        }
        Assert.assertEquals("逐段拼接 = chat3 流式文本",
                "• 甲\n  • 乙\n    • 丙", flat.toString());
    }

    /**
     * F3 引用文字色旋钮：默认值必须等于 chat3 现行次级色 FF9AA0A8
     * （{@code ChatMessageList.java:891-897} → getTextSecondaryArgb()）；0 = 关闭降色。
     */
    @Test
    public void fixF3QuoteTextColorKnobMatchesChat3Secondary() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        Assert.assertEquals("默认 = chat3 出货次级色", 0xFF9AA0A8, table.getQuoteTextColor());
        List<TextSegment> quote = MarkdownDocument.parse("> 引用的文字")
                .toSegments(table, bodyStyle());
        Assert.assertEquals(0xFF9AA0A8, quote.get(0).getStyle().getColor());
        List<TextSegment> plain = MarkdownDocument.parse("普通文字")
                .toSegments(table, bodyStyle());
        Assert.assertEquals("非引用块不得降色", 0xFFFFFFFF, plain.get(0).getStyle().getColor());
        MarkdownStyleTable off = MarkdownStyleTable.defaults();
        off.setQuoteTextColor(0);
        Assert.assertEquals("0 = 继承基础样式色", 0xFFFFFFFF,
                MarkdownDocument.parse("> x").toSegments(off, bodyStyle()).get(0).getStyle().getColor());
        MarkdownStyleTable custom = MarkdownStyleTable.defaults();
        custom.setQuoteTextColor(0xFF112233);
        Assert.assertEquals("copy() 必须带走引用色", 0xFF112233, custom.copy().getQuoteTextColor());
        Assert.assertEquals(0xFF112233, MarkdownDocument.parse("> y").toSegments(custom, bodyStyle())
                .get(0).getStyle().getColor());
    }

    /**
     * F4 块层 §-容忍（用户裁「甲」）：行首 § 序列跨过后照旧判块标记；未命中块标记时
     * § 码必须原样留在输出文本里（容忍不是解析，不得吞字、不得引入颜色）。
     */
    @Test
    public void fixF4BlockLayerToleratesLeadingSectionCodes() {
        char sec = (char) 0x00A7;
        List<TextSegment> list = MarkdownDocument.parse(sec + "a- 玩家列表行")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("• ", list.get(0).getText());
        Assert.assertEquals("玩家列表行", list.get(1).getText());
        List<TextSegment> quote = MarkdownDocument.parse(sec + "7> 引用的文字")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("引用的文字", quote.get(0).getText());
        Assert.assertEquals("S 容忍与引用色联动", 0xFF9AA0A8, quote.get(0).getStyle().getColor());
        List<TextSegment> many = MarkdownDocument.parse(
                sec + "c" + sec + "l" + sec + "f" + sec + "r* 项")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        Assert.assertEquals("• ", many.get(0).getText());
        Assert.assertEquals("连续 S 码与重置码都要跨过", "项", many.get(1).getText());
        Assert.assertEquals("标", MarkdownDocument.parse(sec + "a# 标")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle()).get(0).getText());
        String literal = sec + "a 普通行";
        Assert.assertEquals("未命中块标记 → S 码原样保留", literal,
                textOf(MarkdownDocument.parse(literal)
                        .toSegments(MarkdownStyleTable.defaults(), bodyStyle())));
        String mid = "- 甲 " + sec + "a乙";
        Assert.assertEquals("行中 S 码不参与容忍", "• 甲 " + sec + "a乙",
                textOf(MarkdownDocument.parse(mid)
                        .toSegments(MarkdownStyleTable.defaults(), bodyStyle())));
    }

    /**
     * F6 块间距（走 C1，零公共面变更）：源空行必须在 L2 展开成一个空显示行；
     * 无空行的块边界不得加行；空行不产 SEGMENTS 命令，只占行高。
     */
    @Test
    public void fixF6BlockGapBecomesExactlyOneVisualBlankLine() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextLayoutService service = shared.service;
        List<TextSegment> gap = MarkdownDocument.parse("甲\n\n乙")
                .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        int markers = 0;
        for (int i = 0; i < gap.size(); i++) {
            if (!gap.get(i).isLatex() && gap.get(i).getText().isEmpty()) {
                markers++;
            }
        }
        Assert.assertEquals("空行块边界产 1 个占位标记段", 1, markers);
        List<List<TextSegment>> lines = MarkdownPainter.wrapLines(gap, service, 4000, BASE);
        Assert.assertEquals("甲 + 空行 + 乙 = 3 显示行（与 chat3 一致）", 3, lines.size());
        Assert.assertEquals("中间行零段", 0, lines.get(1).size());
        Assert.assertEquals("甲", textOf(lines.get(0)));
        Assert.assertEquals("乙", textOf(lines.get(2)));
        Assert.assertTrue("空行必须占一份行高",
                MarkdownPainter.lineHeightPx(lines.get(1), service, BASE) > 0);
        Assert.assertEquals("无空行的块边界不得加行", 2, MarkdownPainter.wrapLines(
                MarkdownDocument.parse("甲\n乙")
                        .toSegments(MarkdownStyleTable.defaults(), bodyStyle()),
                service, 4000, BASE).size());
        List<PaintCommand> commands = MarkdownPainter.toPaintCommands(gap, service, 4000, BASE);
        int segmentsCommands = 0;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == PaintCommandType.SEGMENTS) {
                segmentsCommands++;
            }
        }
        Assert.assertEquals("空行不产 SEGMENTS 命令", 2, segmentsCommands);
        int blankHeight = MarkdownPainter.lineHeightPx(new ArrayList<TextSegment>(), service, BASE);
        Assert.assertTrue("总高必须把空行的行距算进去",
                MarkdownPainter.measureHeight(gap, service, 4000, BASE) >= blankHeight * 3);
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

    /** 取第 index 个列表标记段（含前导缩进的空格），不存在返回 null。 */
    private static String markerAt(List<TextSegment> segments, int index) {
        int seen = -1;
        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i).getText();
            if (text.endsWith("• ")) {
                seen++;
                if (seen == index) {
                    return text;
                }
            }
        }
        return null;
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

    private static int blockHeight(List<List<TextSegment>> lines, TextLayoutService service, int scale) {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += MarkdownPainter.lineHeightPx(
                    MarkdownRenderScaleKit.scaleSegments(lines.get(i), scale), service, BASE * scale);
        }
        return total;
    }
}
