package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import club.heiqi.uilib.font.config.FontConfig;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.layout.markdown.MarkdownTableModel;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** Page 与出图消费同一定位计划；无原版 GUI/GL，软件 atlas/replay 全复用既有测试能力。 */
public class MarkdownTableSoftwareRenderTest {
    private int savedWidthMissBudget;
    @Before public void stableMetrics() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void restoreMetrics() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }
    private static final File OUT = new File(System.getProperty("qz.md.table.output", "build/reports/markdown-table-render"));
    private static final int BASE = 14;
    private static final int BG = 0xFF17151B;
    private static final int PAD = 16;
    private static final String SOURCE = "# Markdown tables\nBefore the table.\n\n"
            + "| Left | Center | Right |\n| :--- | :---: | ---: |\n"
            + "| **Bold** | Words that wrap within a narrow cell without losing the row border | 128 |\n"
            + "| [Guide](https://example.com) | `code` and ~~old~~ | 16 |\n"
            + "| Formula | text [$\\frac{1}{\\frac{2}{3}}$](https://example.com/math) tail [next](https://example.com/next) | 1 |\n\nAfter the table.\n\n"
            + "> | Quoted | Value |\n> | --- | ---: |\n> | Text | 2 |\n\n"
            + "- | List first block | Value |\n  | --- | ---: |\n  | Cell | 3 |";

    @AfterClass public static void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void writesFullPageAndNarrowComparisonWithDerivedFloors() throws Exception {
        Assert.assertTrue(OUT.isDirectory() || OUT.mkdirs());
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE);
        TextStyle base = new TextStyle();
        base.setColor(0xFFF2EDF5);
        MarkdownDocument.LayoutContent content = MarkdownDocument.parse(SOURCE).toLayoutContent(styles, base);
        List<TextSegment> segments = new ArrayList<TextSegment>();
        for (MarkdownLayoutLine line : content.getLines()) segments.addAll(line.getSegments());
        for (MarkdownDocument.TableUnit unit : content.getTables()) {
            List<MarkdownTableModel.Row> rows = new ArrayList<MarkdownTableModel.Row>();
            rows.add(unit.getModel().getHeader());
            rows.addAll(unit.getModel().getRows());
            for (MarkdownTableModel.Row row : rows) {
                for (MarkdownTableModel.Cell cell : row.getCells()) segments.addAll(cell.getSegments());
            }
        }
        // atlas 装配会发布 ink 度量：装配在布局之前，度量和最终字形来自同一服务。
        LatexSoftwareRenderKit.assembleGlyphs(shared, segments);
        MarkdownPainter.ContentLayout wide = MarkdownPainter.layoutContent(content, shared.service, 420, BASE);
        MarkdownPainter.ContentLayout narrow = MarkdownPainter.layoutContent(content, shared.service, 220, BASE);
        Assert.assertTrue("窄列样本必须增加视觉高度，代理须可区分", narrow.getHeightPx() > wide.getHeightPx());
        Assert.assertEquals("普通表、引用表、列表首块表均真实识别", 3, content.getTables().size());
        int backgrounds = 0, lines = 0, regions = 0;
        PaintCommand precedingLine = null;
        boolean link = false;
        for (PaintCommand command : narrow.getCommands()) {
            Assert.assertTrue("所有命令在计划纵向范围内", command.getTop() >= 0
                    && command.getBottom() <= narrow.getHeightPx());
            if (command.getType() == PaintCommandType.BACKGROUND) {
                backgrounds++;
                Assert.assertTrue(command.getRight() > command.getLeft());
                Assert.assertTrue(command.getBottom() > command.getTop());
            } else if (command.getType() == PaintCommandType.SEGMENTS) {
                lines++;
                precedingLine = command;
                for (TextSegment segment : command.getSegments()) link |= segment.getStyle().getLink() != null;
            } else if (command.getType() == PaintCommandType.LINK_REGION) {
                regions++;
                Assert.assertNotNull("命中区域必须跟随所属段流", precedingLine);
                if ("https://example.com/math".equals(command.getLinkUrl())) {
                    club.heiqi.uilib.font.latex.layout.MathBox math = LatexSoftwareRenderKit.layout(
                            "\\frac{1}{\\frac{2}{3}}", BASE);
                    Assert.assertTrue("高公式代理必须超出普通文本行高", math.getTotalHeight()
                            > MarkdownPainter.lineHeightPx(java.util.Collections.singletonList(
                                    new TextSegment("text", base)), shared.service, BASE));
                    Assert.assertTrue("公式链接包含MathBox完整高深", command.getBottom() - command.getTop()
                            >= (int) Math.ceil(math.getTotalHeight()));
                    Assert.assertTrue("高公式命中区允许上伸到文本原点以上", command.getTop() <= precedingLine.getTop());
                } else {
                    Assert.assertEquals("普通链接与其文本原点同高", precedingLine.getTop(), command.getTop());
                }
                Assert.assertTrue(command.getBottom() > command.getTop());
                Assert.assertTrue(command.getLeft() >= precedingLine.getLeft());
                int lineRight = precedingLine.getLeft() + MarkdownPainter.lineWidthPx(
                        precedingLine.getSegments(), shared.service, BASE);
                Assert.assertTrue("链接区域在所属段流推进范围内", command.getRight() <= lineRight);
                boolean found = false;
                for (TextSegment segment : precedingLine.getSegments()) {
                    found |= command.getLinkUrl().equals(segment.getStyle().getLink());
                }
                Assert.assertTrue("链接URL来自当前段流", found);
            }
        }
        Assert.assertTrue("必须有网格/表头背景", backgrounds > content.getTables().size());
        Assert.assertTrue("每表至少含实际段流", lines > content.getTables().size());
        Assert.assertTrue("链接样式仍在段流中", link);
        Assert.assertTrue("普通链接、公式链接和尾部链接区域不可空转", regions >= 3);
        render(shared, wide, narrow, 1, new File(OUT, "00-full-page.png"));
        if (MarkdownRenderScaleKit.nxEnabled()) {
            render(shared, wide, narrow, MarkdownRenderScaleKit.N,
                    new File(OUT, "00-full-page" + MarkdownRenderScaleKit.nxSuffix() + ".png"));
        }
        String profile = "font=" + LatexSoftwareRenderKit.platformFontReport()
                + "\nmetricProbeWidth=" + shared.service.getSegmentWidth(new TextSegment("Hx01MWil", base), BASE)
                + "\nwideWidth=" + wide.getWidthPx() + " wideHeight=" + wide.getHeightPx()
                + "\nnarrowWidth=" + narrow.getWidthPx() + " narrowHeight=" + narrow.getHeightPx()
                + "\nbackgroundCommands=" + backgrounds + " segmentCommands=" + lines
                + "\n" + MarkdownRenderScaleKit.detailReport(BASE) + "\n";
        Files.write(new File(OUT, "profiles.txt").toPath(), profile.getBytes(StandardCharsets.UTF_8));
    }

    private static void render(LatexSoftwareRenderKit.Shared shared, MarkdownPainter.ContentLayout wide,
            MarkdownPainter.ContentLayout narrow, int scale, File file) throws Exception {
        GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1);
        GlyphBatchCollector collector = new GlyphBatchCollector();
        replay(shared, view, collector, wide, PAD, PAD, scale);
        replay(shared, view, collector, narrow, PAD, wide.getHeightPx() + PAD * 3, scale);
        int width = (Math.max(wide.getWidthPx(), narrow.getWidthPx()) + PAD * 2) * scale;
        int height = (wide.getHeightPx() + narrow.getHeightPx() + PAD * 4) * scale;
        if (scale > 1) {
            width = Math.max(width, MarkdownRenderScaleKit.MIN_CANVAS_W);
            height = Math.max(height, MarkdownRenderScaleKit.MIN_CANVAS_H);
        }
        SoftwareRenderFrame frame = new SoftwareRenderFrame(width, height, BG);
        if (!collector.getMarkBackgroundBatch().isEmpty()) frame.addBatch(collector.getMarkBackgroundBatch());
        for (int i = 0; i < collector.getActivePageCount(); i++) frame.addBatch(collector.getActiveBatch(i));
        if (!collector.getDecorationBatch().isEmpty()) frame.addBatch(collector.getDecorationBatch());
        int[] pixels = FontSoftwareRasterizer.render(frame, shared.gl);
        if (scale == 1) {
            // C9关系形地板：单独回放glyph batches，网格/表头/行内code背景不能掩盖文字空跑。
            SoftwareRenderFrame textFrame = new SoftwareRenderFrame(width, height, BG);
            int quads = 0;
            for (int i = 0; i < collector.getActivePageCount(); i++) {
                textFrame.addBatch(collector.getActiveBatch(i));
                quads += collector.getActiveBatch(i).getQuadCount();
            }
            int ink = 0;
            for (int pixel : FontSoftwareRasterizer.render(textFrame, shared.gl)) if (pixel != BG) ink++;
            Assert.assertTrue("真实字形quad必须非空", quads > 0);
            Assert.assertTrue("字形墨水>=实际glyph quad数：ink=" + ink + " quads=" + quads, ink >= quads);
        }
        FontSoftwareRasterizer.writePng(pixels, width, height, file);
    }

    private static void replay(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            GlyphBatchCollector collector, MarkdownPainter.ContentLayout plan, int x, int y, int scale) {
        for (PaintCommand command : plan.getCommands()) {
            int left = (x + command.getLeft()) * scale;
            int top = (y + command.getTop()) * scale;
            if (command.getType() == PaintCommandType.BACKGROUND) {
                collector.collectMarkBackground(left, top, (command.getRight() - command.getLeft()) * scale,
                        (command.getBottom() - command.getTop()) * scale, command.getColor());
            } else if (command.getType() == PaintCommandType.SEGMENTS) {
                DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                        MarkdownRenderScaleKit.scaleSegments(command.getSegments(), scale), shared.settings,
                        shared.service, view, left, top, false, 1.0F,
                        command.getTextStyle().getFontSize() * scale, collector);
            } else if (command.getType() == PaintCommandType.LINK_REGION) {
                // 命中元数据不产像素，与既有 ScenePaintReplayer 同口径。
                Assert.assertFalse(command.getLinkUrl().isEmpty());
            } else {
                Assert.fail("未消费命令: " + command.getType());
            }
        }
    }
}
