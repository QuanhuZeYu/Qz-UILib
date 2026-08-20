package club.heiqi.uilib.font.render.software;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import club.heiqi.uilib.font.FontRuntimeMetrics;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphGenerator;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexBinom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.page.SoftwareGlyphPageAssembler;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.font.util.UnicodeTextClassifier;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * LaTeX headless 软件渲染编排（testkit）：rich 文本 → 真度量布局 → 真字形生成 →
 * 软件页装配 → 真机同一展平/收集逻辑 → 软件光栅化 → 像素。
 *
 * <p>与真机共享生产层（{@code TextLayoutService}/{@code GlyphGenerator}/
 * {@code DefaultFontRendererAdapter.renderSegmentsToCollector}），唯一分叉是执行层：
 * 真机 {@code FontBatchRenderer.flush} 走 GL，本场地走 {@link FontSoftwareRasterizer}。</p>
 */
public final class LatexSoftwareRenderKit {

    /** 单次渲染结果。 */
    public static final class RenderResult {

        public final int[] pixels;
        public final int width;
        public final int height;
        public final int advanceWidth;
        public final GlyphBatchCollector collector;
        public final GlyphRuntimeTables tables;

        RenderResult(int[] pixels, int width, int height, int advanceWidth, GlyphBatchCollector collector,
                GlyphRuntimeTables tables) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.advanceWidth = advanceWidth;
            this.collector = collector;
            this.tables = tables;
        }

        /** 非背景像素计数（墨水覆盖）。 */
        public int inkPixelCount(int backgroundArgb) {
            int count = 0;
            for (int pixel : pixels) {
                if (pixel != (backgroundArgb | 0xFF000000)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final int BACKGROUND = 0xFF202020;
    private static final int ORIGIN_X = 4;
    private static final int ORIGIN_Y = 4;
    private static final int PAD = 2;

    /** 共享服务装配：{@code GlyphRuntimeTables} 为百万级 direct-index 表，每实例约 123MiB，必须共享。 */
    static final class Shared {

        final SoftwareGlApi gl;
        final GlyphPageManager manager;
        final GlyphRuntimeTables tables;
        final FontRuntimeSettings settings;
        final FontMatcher fontMatcher;
        final DerivedFontCache derivedFontCache;
        final TextLayoutService service;
        final List<GlyphPage> pages = new ArrayList<GlyphPage>();

        Shared() {
            gl = new SoftwareGlApi();
            manager = new GlyphPageManager(gl);
            tables = manager.getRuntimeTables();
            settings = FontRuntimeSettings.capture();
            tables.setFontMetrics(FontRuntimeMetrics.prepare(settings, null));
            FontCatalog catalog = new FontCatalog();
            catalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
            derivedFontCache = new DerivedFontCache(catalog);
            fontMatcher = new FontMatcher(catalog, derivedFontCache);
            fontMatcher.setRuntimeTables(1, tables);
            service = new TextLayoutService(fontMatcher, manager, derivedFontCache);
            service.setRuntimeVersion(1);
        }
    }

    private static Shared shared;

    private static synchronized Shared shared() {
        if (shared == null) {
            shared = new Shared();
        }
        return shared;
    }

    /** 释放共享装配（{@code GlyphRuntimeTables} 约 123MiB）：测试类 @AfterClass 调用，避免挤压测试 JVM 堆。 */
    public static synchronized void resetShared() {
        shared = null;
    }

    private LatexSoftwareRenderKit() {}

    /** 以真字形 ink 渲染 rich 文本（软件光栅化）。 */
    public static RenderResult render(String richText, int baseFontSizePx) {
        return render(richText, baseFontSizePx, true);
    }

    /**
     * 渲染 rich 文本。
     *
     * @param richText       含 {@code <latex>} 的富文本
     * @param baseFontSizePx 基准字号（px）
     * @param realGlyphs     true = 采样真字形 ink；false = ink 框模式（几何验收、跨机器确定）
     * @return 渲染结果
     */
    public static RenderResult render(String richText, int baseFontSizePx, boolean realGlyphs) {
        Shared shared = shared();
        GlyphRuntimeTables tables = shared.tables;
        FontRuntimeSettings settings = shared.settings;

        List<TextSegment> segments = shared.service.layoutSegments(richText, 0xFFFFFFFF,
                TextContentMode.RICH_TAGS, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        assembleGlyphs(shared, segments);

        GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(tables, shared.manager, 1);
        GlyphBatchCollector collector = new GlyphBatchCollector();
        int advanced = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(segments, settings,
                shared.service, view, ORIGIN_X, ORIGIN_Y, false, 1.0F, baseFontSizePx, collector);

        int[] bbox = computeBoundingBox(collector);
        int width = Math.max(16, bbox[2] + 1 + PAD);
        int height = Math.max(16, bbox[3] + 1 + PAD);
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
        int[] pixels = realGlyphs ? FontSoftwareRasterizer.render(frame, shared.gl)
                : FontSoftwareRasterizer.render(frame);
        return new RenderResult(pixels, width, height, advanced, collector, tables);
    }

    /** 以共享服务（真度量）布局 latex 源码（盒级验收/诊断）。 */
    public static club.heiqi.uilib.font.latex.layout.MathBox layout(String latexSource, int baseFontSizePx) {
        Shared shared = shared();
        club.heiqi.uilib.font.layout.TextStyle style = new club.heiqi.uilib.font.layout.TextStyle();
        style.resetAll(0xFFFFFFFF);
        return new club.heiqi.uilib.font.latex.layout.MathLayoutService().layout(
                club.heiqi.uilib.font.latex.LatexParser.parse(latexSource), baseFontSizePx,
                shared.service.createMathMetrics(style, baseFontSizePx));
    }

    /** 渲染并写 PNG。 */
    public static void renderToPng(String richText, int baseFontSizePx, File out) throws Exception {
        RenderResult result = render(richText, baseFontSizePx, true);
        FontSoftwareRasterizer.writePng(result.pixels, result.width, result.height, out);
    }

    /** 为渲染所需码点生成字形并装配到软件字符页（真 skyline + 真上传路径；已常驻码点跳过）。 */
    private static void assembleGlyphs(Shared shared, List<TextSegment> segments) {
        Set<Integer> codepoints = collectCodepoints(segments);
        GlyphGenerator generator = new GlyphGenerator(shared.fontMatcher, shared.derivedFontCache);
        if (shared.pages.isEmpty()) {
            shared.pages.add(SoftwareGlyphPageAssembler.createPage(1, 0, shared.settings.getTextureSize(),
                    shared.settings.getPageGlyphSize(), shared.settings.getLerpMode(), shared.gl));
            shared.tables.setPage(FontType.NORMAL, 0, shared.pages.get(0));
        }
        long requestId = System.nanoTime();
        for (int codepoint : codepoints) {
            if (shared.tables.stateArray(FontType.NORMAL)[codepoint]
                    == GlyphRuntimeTables.STATE_RESIDENT) {
                continue;
            }
            GlyphRequestToken token = new GlyphRequestToken(1, requestId++, codepoint, FontType.NORMAL);
            GlyphGenerationResult result = generator.generate(new GlyphGenerationTask(token,
                    shared.settings.getPageGlyphSize(), GlyphGenerationPriority.HIGH));
            if (result == null || result.getGlyphInfo() == null || !result.getGlyphInfo().hasBitmap()) {
                continue;
            }
            GlyphPage page = findPage(shared.pages, result.getGlyphInfo(), shared.settings, shared.gl);
            shared.tables.setPage(FontType.NORMAL, page.getPageIndex(), page);
            SoftwareGlyphPageAssembler.publish(FontType.NORMAL, page, shared.tables, codepoint,
                    result.getGlyphInfo(), result.getImage(), token);
        }
    }

    private static GlyphPage findPage(List<GlyphPage> pages,
            club.heiqi.uilib.font.glyph.GlyphInfo info, FontRuntimeSettings settings, SoftwareGlApi gl) {
        for (GlyphPage page : pages) {
            if (page.canAllocate(info.getSlotWidth(), info.getSlotHeight())) {
                return page;
            }
        }
        GlyphPage page = SoftwareGlyphPageAssembler.createPage(1, pages.size(), settings.getTextureSize(),
                settings.getPageGlyphSize(), settings.getLerpMode(), gl);
        pages.add(page);
        return page;
    }

    /** 收集渲染所需码点：普通段文本 + 公式 AST 原子文本（含重音字符与定界符）。 */
    static Set<Integer> collectCodepoints(List<TextSegment> segments) {
        Set<Integer> codepoints = new LinkedHashSet<Integer>();
        for (TextSegment segment : segments) {
            if (segment.isLatex()) {
                List<LatexNode> nodes = LatexParser.parse(segment.getLatexSource());
                for (LatexNode node : nodes) {
                    collectNode(node, codepoints);
                }
                continue;
            }
            String text = segment.getText();
            for (int index = 0; index < text.length(); ) {
                int codepoint = text.codePointAt(index);
                index += Character.charCount(codepoint);
                if (!UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                    codepoints.add(Integer.valueOf(codepoint));
                }
            }
        }
        return codepoints;
    }

    private static void collectNode(LatexNode node, Set<Integer> out) {
        if (node == null) {
            return;
        }
        switch (node.getKind()) {
            case ATOM:
                collectText(((LatexAtom) node).getText(), out);
                return;
            case SUP_SUB:
                LatexSupSub supSub = (LatexSupSub) node;
                collectNode(supSub.getBase(), out);
                collectNode(supSub.getSup(), out);
                collectNode(supSub.getSub(), out);
                return;
            case FRAC:
                collectNode(((LatexFrac) node).getNumerator(), out);
                collectNode(((LatexFrac) node).getDenominator(), out);
                return;
            case SQRT:
                // 根号字形（U+221A）是布局常量、不在 AST 文本里，必须显式纳入装配码点集合
                collectText("\u221A", out);
                collectNode(((LatexSqrt) node).getIndex(), out);
                collectNode(((LatexSqrt) node).getRadicand(), out);
                return;
            case GROUP:
                for (LatexNode child : ((LatexGroup) node).getChildren()) {
                    collectNode(child, out);
                }
                return;
            case LEFT_RIGHT:
                LatexLeftRight leftRight = (LatexLeftRight) node;
                collectText(leftRight.getLeftDelimiter(), out);
                collectNode(leftRight.getContent(), out);
                collectText(leftRight.getRightDelimiter(), out);
                return;
            case MATRIX:
                for (List<List<LatexNode>> row : ((LatexMatrix) node).getRows()) {
                    for (List<LatexNode> cell : row) {
                        for (LatexNode cellNode : cell) {
                            collectNode(cellNode, out);
                        }
                    }
                }
                return;
            case BINOM:
                collectNode(((LatexBinom) node).getUpper(), out);
                collectNode(((LatexBinom) node).getLower(), out);
                return;
            case ACCENT:
                LatexAccent accent = (LatexAccent) node;
                collectText(accent.getAccentText(), out);
                collectNode(accent.getBase(), out);
                return;
            case SPACE:
                return;
            default:
                return;
        }
    }

    private static void collectText(String text, Set<Integer> out) {
        if (text == null) {
            return;
        }
        for (int index = 0; index < text.length(); ) {
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            if (!UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                out.add(Integer.valueOf(codepoint));
            }
        }
    }

    /** 计算收集侧全部顶点的包围盒 [minX, minY, maxX, maxY]。 */
    static int[] computeBoundingBox(GlyphBatchCollector collector) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        List<GlyphRenderBatch> batches = new ArrayList<GlyphRenderBatch>();
        if (!collector.getMarkBackgroundBatch().isEmpty()) {
            batches.add(collector.getMarkBackgroundBatch());
        }
        for (int index = 0; index < collector.getActivePageCount(); index++) {
            GlyphRenderBatch batch = collector.getActiveBatch(index);
            if (batch != null && !batch.isEmpty()) {
                batches.add(batch);
            }
        }
        if (!collector.getDecorationBatch().isEmpty()) {
            batches.add(collector.getDecorationBatch());
        }
        for (GlyphRenderBatch batch : batches) {
            float[] v = batch.copyVertexData();
            int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
            for (int quad = 0; quad < batch.getQuadCount(); quad++) {
                for (int vertex = 0; vertex < GlyphRenderBatch.VERTICES_PER_QUAD; vertex++) {
                    int offset = (quad * GlyphRenderBatch.VERTICES_PER_QUAD + vertex) * stride;
                    int x = (int) Math.floor(v[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS]);
                    int y = (int) Math.floor(v[offset + GlyphRenderBatch.POSITION_OFFSET_FLOATS + 1]);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {minX, minY, maxX, maxY};
    }
}
