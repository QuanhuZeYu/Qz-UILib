package club.heiqi.uilib.font.render.software;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
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

    /** 诊断用：当前共享装配的 awt 基准字号（ink 表值换算到渲染像素的同源口径）。 */
    public static double currentAwtCharSize() {
        return shared().settings.getAwtCharSize();
    }

    /** 契约测试用：共享 TextLayoutService（真机同源度量实现）。 */
    public static club.heiqi.uilib.font.layout.TextLayoutService currentService() {
        return shared().service;
    }

    /** 垂直度量探针串：与 {@code FontRuntimeMetrics.METRICS_SAMPLE} 同源，不另起口径。 */
    private static final String DIAG_METRICS_SAMPLE = "Ag";

    /** 推进指纹探针串：宽窄与大写混排，靠巧合撞上另一套物理字体的概率极低。 */
    private static final String DIAG_WIDTH_SAMPLE = "Hx01MWil";

    /** 与 {@code FontRuntimeMetrics.FONT_RENDER_CONTEXT} 同口径：恒等变换 + AA 开启。 */
    private static final FontRenderContext DIAG_FRC = new FontRenderContext(null, true, true);

    /** 字体现场只算一次（内含逐家族指纹扫描），供多处断言消息复用。 */
    private static String platformFontReport;

    /**
     * 当前 JVM 解析到的字体现场，单行紧凑串。<b>纯观测</b>：不参与任何判定，内部任何异常都降级为
     * {@code diagError=...} —— 诊断本身绝不允许把测试搞红。
     *
     * <p><b>为什么要它</b>：{@code "Dialog"} 是 AWT <b>逻辑字体</b>，由操作系统解析成不同物理字体
     * （Windows 走 Microsoft Sans Serif/Tahoma 一类，Ubuntu 走 DejaVu 一类）。生产路径用的是同一个
     * 逻辑字体（{@code FontRuntimeMetrics#prepare} 在 catalog 缺位时 {@code new Font("Dialog", ...)}），
     * 而仓库不携带任何 TTF —— 所以这里量到的就是该平台上玩家实际会看到的度量，不是测试环境特有。
     * 分数分子与主线的间隙正由这些垂直度量推导，因此「间隙是否贴死」是个平台相关量。</p>
     *
     * <p><b>物理家族名没有公开反解 API</b>，改用度量当指纹：整数 advance/height 走 {@code FontMetrics}
     * （与 vanilla 宽度口径同源），ascent/descent/leading 走 {@code LineMetrics}；再拿同字号指纹去本机
     * 已安装家族里比对，命中名即该逻辑字体在本机最可能解析到的物理家族。</p>
     */
    public static String platformFontReport() {
        if (platformFontReport == null) {
            platformFontReport = buildPlatformFontReport();
        }
        return platformFontReport;
    }

    private static String buildPlatformFontReport() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("jvm=").append(System.getProperty("java.version"));
            sb.append(" os=").append(System.getProperty("os.name"));
            sb.append("/").append(System.getProperty("os.arch"));
            Font base = new Font("Dialog", Font.PLAIN, 14);
            sb.append(" Dialog@14=").append(fontFingerprint(base));
            sb.append(" @16=").append(fontFingerprint(new Font("Dialog", Font.PLAIN, 16)));
            sb.append(" @24=").append(fontFingerprint(new Font("Dialog", Font.PLAIN, 24)));
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            sb.append(" fam=").append(matchingFamilies(families, base));
        } catch (Throwable t) {
            sb.setLength(0);
            sb.append("diagError=").append(t.getClass().getName()).append(":").append(t.getMessage());
        }
        return sb.toString();
    }

    /** 一套字体在某字号下的整数度量指纹：advance 宽 / 行高 / 垂直三段。 */
    private static String fontFingerprint(Font font) {
        FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(font);
        LineMetrics lm = font.getLineMetrics(DIAG_METRICS_SAMPLE, DIAG_FRC);
        return fm.stringWidth(DIAG_WIDTH_SAMPLE) + "/" + fm.getHeight()
                + "/a" + diagRound(lm.getAscent()) + "d" + diagRound(lm.getDescent())
                + "l" + diagRound(lm.getLeading());
    }

    /** 同字号逐家族比对指纹；命中名以竖线连接（最多列 4 个），并附命中数与扫描总数。 */
    private static String matchingFamilies(String[] families, Font logical) {
        String target = fontFingerprint(logical);
        String logicalFamily = logical.getFamily();
        StringBuilder hits = new StringBuilder();
        int count = 0;
        for (String family : families) {
            Font candidate = new Font(family, Font.PLAIN, logical.getSize());
            if (logicalFamily.equals(candidate.getFamily())) {
                continue; // 跳过逻辑字体自身：它也出现在家族列表里
            }
            if (target.equals(fontFingerprint(candidate))) {
                count++;
                if (count <= 4) {
                    hits.append(count == 1 ? "" : "|").append(family);
                }
            }
        }
        if (count == 0) {
            return "none(fp=" + target + ",scanned=" + families.length + ")";
        }
        hits.append("(").append(count).append("/").append(families.length).append(")");
        return hits.toString();
    }

    private static String diagRound(float value) {
        return String.valueOf(Math.round(value * 10.0F) / 10.0F);
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

    /**
     * 以共享服务（真度量）布局 latex 源码（盒级验收/诊断）。
     *
     * <p>先装配公式涉及的字形（与 render 同口径：ink 表就绪后再布局），否则 inkWidth/
     * inkCenterOffsetY 走回退值，headless 布局与真机（字形已生成）布局错位（如根号横线
     * 左端按 advance 而非勾 ink 右缘）。</p>
     */
    public static club.heiqi.uilib.font.latex.layout.MathBox layout(String latexSource, int baseFontSizePx) {
        Shared shared = shared();
        club.heiqi.uilib.font.layout.TextStyle style = new club.heiqi.uilib.font.layout.TextStyle();
        style.resetAll(0xFFFFFFFF);
        List<club.heiqi.uilib.font.latex.LatexNode> nodes = club.heiqi.uilib.font.latex.LatexParser
                .parse(latexSource);
        Set<Integer> codepoints = new LinkedHashSet<Integer>();
        for (club.heiqi.uilib.font.latex.LatexNode node : nodes) {
            collectNode(node, codepoints);
        }
        assembleCodepoints(shared, codepoints);
        return new club.heiqi.uilib.font.latex.layout.MathLayoutService().layout(
                nodes, baseFontSizePx, shared.service.createMathMetrics(style, baseFontSizePx));
    }

    /** 渲染并写 PNG。 */
    public static void renderToPng(String richText, int baseFontSizePx, File out) throws Exception {
        RenderResult result = render(richText, baseFontSizePx, true);
        FontSoftwareRasterizer.writePng(result.pixels, result.width, result.height, out);
    }

    /** 为渲染所需码点生成字形并装配到软件字符页（真 skyline + 真上传路径；已常驻码点跳过）。 */
    private static void assembleGlyphs(Shared shared, List<TextSegment> segments) {
        assembleCodepoints(shared, collectCodepoints(segments));
    }

    /** 生成并装配给定码点集合（layout 与 render 共用的同源入口）。 */
    private static void assembleCodepoints(Shared shared, Set<Integer> codepoints) {
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
                // \middle 分段：全部内容段与段间定界符都要装配（此前只收首段，
                // \frac{c}{d} 与 \middle| 字形缺装配 → 渲染无 quad）
                for (LatexNode part : leftRight.getParts()) {
                    collectNode(part, out);
                }
                for (String middle : leftRight.getMiddleDelimiters()) {
                    collectText(middle, out);
                }
                collectText(leftRight.getRightDelimiter(), out);
                return;
            case MATRIX:
                // 矩阵外围定界符是布局常量（不在 AST 文本里），必须显式纳入装配码点集合——
                // 否则 pmatrix 圆括号 / cases 花括号在一次性 headless 装配里缺失（真机按需生成不受影响）
                switch (((LatexMatrix) node).getFence()) {
                    case PAREN:
                        collectText("(", out);
                        collectText(")", out);
                        break;
                    case BRACKET:
                        collectText("[", out);
                        collectText("]", out);
                        break;
                    case BAR:
                        collectText("|", out);
                        collectText("|", out);
                        break;
                    case CASES:
                        collectText("{", out);
                        break;
                    default:
                        break;
                }
                for (List<List<LatexNode>> row : ((LatexMatrix) node).getRows()) {
                    for (List<LatexNode> cell : row) {
                        for (LatexNode cellNode : cell) {
                            collectNode(cellNode, out);
                        }
                    }
                }
                return;
            case BINOM:
                // \binom 的圆括号同样由布局常量生成（TeX \left( … \right) 语义）
                collectText("(", out);
                collectText(")", out);
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
