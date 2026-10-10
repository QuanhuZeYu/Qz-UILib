package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.image.ClientImageServices;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/** 固定参照全身份的真实 SceneMath/FT/HB/GL 消费者；登记与图像人工审阅分开取证。 */
public final class MathCoverageScenario extends TestApplication {
    private static final int MAX_W = 2048, MAX_H = 1536;
    private int COLS = 4, ROWS = 8, CW = 400, CH = 192, W = 1600, H = 1536;
    private long window, serial;
    private GLFWErrorCallback errors;
    private ClientFontServices fonts;
    private ClientImageServices images;
    private GlPaintPlanRenderer renderer;
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private SceneHost host;
    private int ready, demand, failed, executed;
    private club.heiqi.uilib.ui.scene.text.font.FontSource explicitCandidate;
    private Path output;
    private final StringBuilder
            glyphEvidence = new StringBuilder("# caseId\tem\tpieceUTF16\tglyphUTF16\tfacts\n"),
            typedEvidence = new StringBuilder("# caseId\tem\tkind\tstart\tend\tvalue\tGlyphSpec\n");
    private final Map<String, String> profileFamilies = new HashMap<>();
    private final StringBuilder evidence = new StringBuilder();
    private final List<Case> cases = new ArrayList<>();

    private static final class Case {
        final String id, kind, mode, token, context, outcome, scope, source, types, expected;

        Case(String[] f) {
            id = f[0];
            kind = f[1];
            mode = f[2];
            token = f[3];
            context = f[4];
            outcome = f[5];
            scope = f[6];
            source = f[7];
            types = f[8];
            expected = f[9];
        }
    }

    public static void main(String[] args) throws Exception {
        new MathCoverageScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        output =
                Paths.get(
                        arguments().length == 0 ? "build/math-complete/coverage" : arguments()[0]);
        Files.createDirectories(output);
        try (InputStream input =
                getClass().getResourceAsStream("/math/reference/coverage-corpus.tsv")) {
            require(input != null, "缺少固定 coverage-corpus.tsv");
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] fields = line.split("\t", -1);
                    require(fields.length == 10, "coverage TSV 列数错误");
                    for (int i = 0; i < fields.length; i++) fields[i] = unescape(fields[i]);
                    cases.add(new Case(fields));
                }
            }
        }
        require(!cases.isEmpty(), "固定语料为空");
        errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        own(
                () -> {
                    if (window != 0) {
                        glfwMakeContextCurrent(0);
                        glfwDestroyWindow(window);
                        window = 0;
                    }
                    GL.setCapabilities(null);
                    glfwTerminate();
                    glfwSetErrorCallback(null);
                    errors.free();
                });
        require(glfwInit(), "GLFW初始化失败");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(MAX_W, MAX_H, "MathCoverageScenario", 0, 0);
        require(window != 0, "真实GL上下文创建失败");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glViewport(0, 0, W, H);
        glDisable(GL_DEPTH_TEST);
        evidence.append("# vendor=")
                .append(glGetString(GL_VENDOR))
                .append(" renderer=")
                .append(glGetString(GL_RENDERER))
                .append(" version=")
                .append(glGetString(GL_VERSION))
                .append('\n');
        evidence.append(
                "# id\tem\ttheme\tstatus\tartifact\tregion\tbox\treferenceTypes\tactualText"
                        + "\tfontGlyphs\tdetail\n");
        GlApi gl = new Lwjgl3GlApi();
        fonts =
                own(
                        new ClientFontServices(
                                getClass().getClassLoader(),
                                1,
                                ClientFontServices.Settings.defaults(),
                                new LwjglFontEngine(),
                                gl,
                                GlProgramProvider.NONE));
        images =
                own(
                        new ClientImageServices(
                                gl,
                                source -> () -> source.bitmap,
                                SharedImageService.Budget.defaults()));
        renderer = fonts.createPlanRenderer(images.textures());
        own(() -> fonts.releasePlanRenderer(renderer));
        TextLayout nativeX = fonts.fonts().mathFont(new TextStyle("default", 18, -1)).text("x");
        explicitCandidate = nativeX.lines().get(0).glyphs().get(0).source;
        host =
                new SceneHost() {
                    public int width() {
                        return W;
                    }

                    public int height() {
                        return H;
                    }

                    public long metricsRevision() {
                        return fonts.fonts().generation() ^ fonts.readyVersion();
                    }

                    public void prepareResources() {
                        images.prepareResources();
                    }

                    public void replay(PaintPlan paint) {
                        fonts.beginPresentation(++serial);
                        try (GlPaintPlanRenderer.Prepared p =
                                fonts.prepare(renderer, paint, new LayoutBox(0, 0, W, H))) {
                            ready = p.readyCount();
                            demand = p.demandCount();
                            p.render(FramebufferCoordinates.orthographic(W, H), H, false);
                        } finally {
                            fonts.endPresentation();
                        }
                    }
                };
    }

    @Override
    protected void runTests() throws Exception {
        // 可选身份筛选仅用于定位；完整验收不传第二个参数，不能把筛选运行作为全量 PASS。
        String filter = arguments().length > 1 ? arguments()[1] : "";
        List<Case> selected = new ArrayList<>();
        for (Case c : cases) if (filter.isEmpty() || c.id.contains(filter)) selected.add(c);
        require(!selected.isEmpty(), "身份筛选无样例");
        for (int variant = 0; variant < 2; variant++) {
            final float em = variant == 0 ? 18 : 31.5f;
            final boolean dark = variant == 0;
            for (int start = 0, pageIndex = 0; start < selected.size(); pageIndex++) {
                boolean spacious = spacious(selected.get(start));
                COLS = spacious ? 2 : 4;
                ROWS = spacious ? 4 : 8;
                CW = spacious ? 1024 : 400;
                CH = spacious ? 320 : 192;
                W = COLS * CW;
                H = ROWS * CH;
                int end = start + 1;
                while (end < selected.size()
                        && end - start < COLS * ROWS
                        && spacious(selected.get(end)) == spacious) end++;
                final int pageStart = start, index = pageIndex;
                List<Case> batch = new ArrayList<>(selected.subList(start, end));
                scenario(
                        "math coverage em="
                                + em
                                + " theme="
                                + (dark ? "dark" : "light")
                                + " page="
                                + index,
                        () -> renderPage(batch, index, em, dark));
                start = end;
            }
        }
        saveEvidence();
        require(
                failed == 0,
                "完整覆盖真实失败=" + failed + "/" + executed + "，见 evidence.tsv；不将有效公式 fallback 当通过");
    }

    private void renderPage(List<Case> batch, int pageIndex, float em, boolean dark)
            throws Exception {
        final List<SceneNode> nodes = new ArrayList<>();
        final List<MathContent> parsed = new ArrayList<>();
        TextStyle style =
                new TextStyle(
                        "default",
                        em,
                        dark ? 0xffeeeeee : 0xff18212e,
                        null,
                        club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                        club.heiqi.uilib.ui.scene.text.TextDecoration.NONE);
        SceneTheme theme = (dark ? SceneTheme.dark() : SceneTheme.light()).withTextStyle(style);
        // 每页独立挂载并及时关闭；不把数千幅大图、ScenePageSession 或需求同时持有。
        try (ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        fonts,
                        ctx -> {
                            SceneNode root = new SceneNode().size(Size.fixed(W), Size.fixed(H));
                            for (int row = 0; row < ROWS; row++) {
                                SceneNode strip =
                                        new SceneNode()
                                                .axis(SceneNode.Axis.ROW)
                                                .size(Size.fixed(W), Size.fixed(CH));
                                for (int col = 0; col < COLS; col++) {
                                    int i = row * COLS + col;
                                    if (i >= batch.size()) break;
                                    Case c = batch.get(i);
                                    MathContent content = MathContent.parse(c.source);
                                    parsed.add(content);
                                    SceneTheme caseTheme =
                                            c.context.startsWith("explicit-candidates:")
                                                    ? theme.withTextStyle(
                                                            new TextStyle(
                                                                    "default",
                                                                    em,
                                                                    style.color,
                                                                    Collections.singletonList(
                                                                            explicitCandidate),
                                                                    club.heiqi.uilib.ui.scene.text
                                                                            .TextEffects.NORMAL,
                                                                    club.heiqi.uilib.ui.scene.text
                                                                            .TextDecoration.NONE))
                                                    : theme;
                                    SceneNode node =
                                            SceneMath.create(
                                                    ctx,
                                                    domain.signal(content),
                                                    domain.signal(caseTheme),
                                                    c.mode.equals("display")
                                                            ? MathStyle.DISPLAY
                                                            : MathStyle.TEXT,
                                                    Collections.emptyMap());
                                    nodes.add(node);
                                    strip.add(
                                            new SceneNode()
                                                    .size(Size.fixed(CW), Size.fixed(CH))
                                                    .padding(8)
                                                    .clip(true)
                                                    .add(
                                                            new SceneNode()
                                                                    .text(c.id + " " + c.token)
                                                                    .style(
                                                                            new TextStyle(
                                                                                    "default",
                                                                                    11,
                                                                                    dark
                                                                                            ? 0xff95a4b8
                                                                                            : 0xff586578))
                                                                    .size(
                                                                            Size.fixed(CW - 16),
                                                                            Size.fixed(22)))
                                                    .add(new SceneNode().padding(32).add(node)));
                                }
                                root.add(strip);
                            }
                            ctx.mount(root);
                        })) {
            BufferedImage pixels = renderReady(page, dark);
            String artifact =
                    "atlas-" + (dark ? "dark" : "light") + "-" + em + "-" + pageIndex + ".png";
            require(ImageIO.write(pixels, "png", output.resolve(artifact).toFile()), "PNG编码器不可用");
            domain.execute(
                    () -> {
                        for (int i = 0; i < batch.size(); i++) {
                            Case c = batch.get(i);
                            SceneNode node = nodes.get(i);
                            MathLayout layout = SceneMath.layout(node);
                            LayoutBox region = page.pipeline().current().geometry.get(node).box;
                            inspect(c, parsed.get(i), layout, region, pixels, em, dark, artifact);
                        }
                    });
            require(glGetError() == GL_NO_ERROR, "正式GL错误");
            pixels.flush();
            saveEvidence();
        }
    }

    private void inspect(
            Case c,
            MathContent content,
            MathLayout value,
            LayoutBox region,
            BufferedImage pixels,
            float em,
            boolean dark,
            String artifact) {
        StringBuilder problems = new StringBuilder(),
                text = new StringBuilder(),
                glyphs = new StringBuilder(),
                notes = new StringBuilder(),
                typed = new StringBuilder();
        try {
            typedFacts(
                    field(content, "root"),
                    c.source.length(),
                    typed,
                    problems,
                    escape(c.id) + "\t" + em);
            typedEvidence.append(typed);
        } catch (Exception e) {
            problems.append("typed事实读取失败:").append(e).append(';');
        }
        if (!content.source.equals(c.source)) problems.append("source改变;");
        if (!finite(value.width, value.height, value.baseline)) problems.append("非finitebox;");
        boolean rejected = c.outcome.equals("error");
        if (rejected) {
            if (!value.fallback
                    || value.sourceFallback == null
                    || !value.sourceFallback.logicalText.equals(c.source)
                    || value.diagnostics.isEmpty()) problems.append("拒绝项未完整原文诊断;");
        } else if (value.fallback) problems.append("有效项整式fallback;");
        Set<String> acceptedStyles = new HashSet<>();
        for (MathLayout.TextPiece p : value.pieces)
            for (TextLayout.Diagnostic d : p.text.diagnostics())
                if (allowedCjkStyle(c, p, d, em))
                    acceptedStyles.add(p.sourceStart + ":" + p.sourceEnd + ":" + d.message);
        boolean styleFallbackObserved = false;
        for (MathLayout.Diagnostic d : value.diagnostics) {
            if (d.start < 0 || d.end < d.start || d.end > c.source.length())
                problems.append("UTF16诊断越界;");
            boolean allowed =
                    d.stage.equals("font")
                            && acceptedStyles.contains(d.start + ":" + d.end + ":" + d.message);
            if (allowed) styleFallbackObserved = true;
            else if (!rejected) problems.append("有效项诊断;");
            notes.append("diagnostic:")
                    .append(d.stage)
                    .append('@')
                    .append(d.start)
                    .append(':')
                    .append(d.end)
                    .append(':')
                    .append(d.message)
                    .append(';');
        }
        boolean rejectedNativeWarning = false;
        int ink = 0, glyphCount = 0;
        Set<String> actualFonts = new LinkedHashSet<>();
        float[] bounds = {0, 0, value.width, value.height};
        for (MathLayout.TextPiece p : value.pieces) {
            if (!finite(p.x, p.y)
                    || p.sourceStart < 0
                    || p.sourceEnd < p.sourceStart
                    || p.sourceEnd > c.source.length()) problems.append("piece来源/位置非法;");
            text.append(p.text.logicalText);
            for (TextLayout.Diagnostic d : p.text.diagnostics())
                if (d.kind == TextLayout.Diagnostic.Kind.MISSING_GLYPH) {
                    if (rejected && expectedRejectedWarning(c, p, d)) rejectedNativeWarning = true;
                    else problems.append("MISSING_GLYPH:").append(d.message).append(';');
                }
            for (TextLayout.Line line : p.text.lines())
                for (TextLayout.Glyph g : line.glyphs()) {
                    if ((g.replacement || g.glyphId == 0)
                            && !(rejected && expectedRejectedGlyph(c, p, g)))
                        problems.append("缺字:").append(p.text.logicalText).append(';');
                    if (g.source == null || !finite(g.x, g.y, g.logicalEm, g.rasterToLayout))
                        problems.append("glyph事实非法;");
                    if (c.context.startsWith("explicit-candidates:")
                            && !g.source.equals(explicitCandidate))
                        problems.append("explicit candidates身份改变;");
                    if (!finite(
                                    g.size,
                                    g.xAdvance,
                                    g.yAdvance,
                                    g.inkBounds.left,
                                    g.inkBounds.top,
                                    g.inkBounds.right,
                                    g.inkBounds.bottom)
                            || g.logicalEm <= 0
                            || g.rasterToLayout <= 0
                            || g.size <= 0) problems.append("glyph尺度/墨迹非法;");
                    glyphCount++;
                    actualFonts.add(g.source.toString() + "/" + g.source.actualStyle());
                    if (g.inkBounds.known)
                        expand(
                                bounds,
                                p.x + g.x + g.inkBounds.left,
                                p.y + g.y + g.inkBounds.top,
                                p.x + g.x + g.inkBounds.right,
                                p.y + g.y + g.inkBounds.bottom);
                    profileStyle(c, p, g, em, problems);
                    glyphs.append("cp=")
                            .append(codepoints(glyphText(p.text, g)))
                            .append(" source=")
                            .append(g.source)
                            .append(':')
                            .append(g.glyphId)
                            .append('@')
                            .append(g.logicalEm)
                            .append("/generation=")
                            .append(p.text.generation)
                            .append("/rawFont=")
                            .append(g.source.origin)
                            .append("/face=")
                            .append(g.source.faceIndex)
                            .append("/style=")
                            .append(g.source.actualStyle())
                            .append("/xAdvance=")
                            .append(g.xAdvance)
                            .append("/lineAdvance=")
                            .append(line.advance)
                            .append("/textAdvance=")
                            .append(p.text.advanceWidth)
                            .append("/rasterEm=")
                            .append(g.size)
                            .append("/uniformScale=")
                            .append(g.rasterToLayout)
                            .append("/ink=")
                            .append(g.inkBounds.left)
                            .append(',')
                            .append(g.inkBounds.top)
                            .append(',')
                            .append(g.inkBounds.right)
                            .append(',')
                            .append(g.inkBounds.bottom)
                            .append(';');
                    glyphEvidence
                            .append(escape(c.id))
                            .append('\t')
                            .append(em)
                            .append('\t')
                            .append(p.sourceStart)
                            .append(':')
                            .append(p.sourceEnd)
                            .append('\t')
                            .append(g.logicalStart)
                            .append(':')
                            .append(g.logicalEnd)
                            .append('\t')
                            .append(escape(glyphs.toString()))
                            .append('\n');
                    glyphs.setLength(0);
                }
        }
        glyphs.append("count=").append(glyphCount).append(' ').append(actualFonts);
        // spacing、零 advance 墨迹与私用字体编码分别取证，不把它们强行当普通字符。
        if (!rejected && !c.expected.isEmpty()) {
            if (spacesOnly(c.expected)) {
                if (value.width <= 0) problems.append("spacing几何丢失;");
                if (c.mode.equals("text")) {
                    boolean nativeBlank = false;
                    for (MathLayout.TextPiece p : value.pieces)
                        if (spacesOnly(p.text.logicalText) && p.text.advanceWidth > 0)
                            for (TextLayout.Line line : p.text.lines())
                                if (line.advance > 0)
                                    for (TextLayout.Glyph g : line.glyphs())
                                        if (!g.replacement
                                                && g.glyphId != 0
                                                && g.xAdvance > 0
                                                && spacesOnly(glyphText(p.text, g)))
                                            nativeBlank = true;
                    if (!nativeBlank) problems.append("text SPACE缺真实原生glyph/line正advance;");
                } else if (glyphCount != 0) problems.append("math SPACE不得伪造可见glyph;");
            } else if (variantExpectation(c.expected) != null) {
                int[] variant = variantExpectation(c.expected);
                boolean found = false;
                for (MathLayout.TextPiece p : value.pieces)
                    for (TextLayout.Line line : p.text.lines())
                        for (TextLayout.Glyph g : line.glyphs())
                            if (g.glyphId == variant[1]
                                    && g.source.origin.endsWith("/math/STIXTwoMath-Regular.otf")
                                    && p.text.logicalText.indexOf(variant[0]) >= 0) found = true;
                if (!found) problems.append("标准FE00变体真实glyph缺失;");
            } else if (letterlikeExpectation(c) != null) {
                try {
                    if (!verifiedLetterlike(c, content, value))
                        problems.append("数学alphabet空洞typed/font/STIXglyph语义不符;");
                } catch (ReflectiveOperationException error) {
                    problems.append("数学alphabet typed反射取证失败:").append(error).append(';');
                }
            } else if (semanticPua(c.expected) != null) {
                String mapped = semanticPua(c.expected);
                int[] ids = puaGlyphs(c.expected);
                List<Integer> actualIds = new ArrayList<>();
                for (MathLayout.TextPiece p : value.pieces)
                    if (p.text.logicalText.equals(mapped))
                        for (TextLayout.Line line : p.text.lines())
                            for (TextLayout.Glyph g : line.glyphs())
                                if (!g.replacement
                                        && g.glyphId != 0
                                        && g.source.origin.endsWith(
                                                "/math/STIXTwoMath-Regular.otf"))
                                    actualIds.add(g.glyphId);
                if (actualIds.size() != ids.length) problems.append("PUA标准语义真实glyph数量不符;");
                else
                    for (int i = 0; i < ids.length; i++)
                        if (actualIds.get(i) != ids[i]) problems.append("PUA标准语义实际STIXglyph不符;");
            } else if (c.expected.equals("\ue020")) {
                if (value.strokes.isEmpty() || typed.indexOf("replacement=\ue020") < 0)
                    problems.append("not声明语义/stroke缺失;");
            } else if (text.indexOf(c.expected) < 0
                    && semanticCharacters(text.toString(), false)
                                    .indexOf(semanticCharacters(c.expected, true))
                            < 0) problems.append("oracle字符缺失:").append(c.expected).append(';');
        }
        for (MathLayout.Rule rule : value.rules)
            expand(bounds, rule.x, rule.y, rule.x + rule.width, rule.y + rule.height);
        for (MathLayout.Stroke stroke : value.strokes)
            expand(
                    bounds,
                    Math.min(stroke.x1, stroke.x2) - stroke.width / 2,
                    Math.min(stroke.y1, stroke.y2) - stroke.width / 2,
                    Math.max(stroke.x1, stroke.x2) + stroke.width / 2,
                    Math.max(stroke.y1, stroke.y2) + stroke.width / 2);
        LayoutBox inkRegion =
                new LayoutBox(
                        region.x + (int) Math.floor(bounds[0]),
                        region.y + (int) Math.floor(bounds[1]),
                        Math.max(0, (int) Math.ceil(bounds[2]) - (int) Math.floor(bounds[0])),
                        Math.max(0, (int) Math.ceil(bounds[3]) - (int) Math.floor(bounds[1])));
        LayoutBox cell = new LayoutBox((region.x / CW) * CW, (region.y / CH) * CH, CW, CH);
        if (inkRegion.x < cell.x
                || inkRegion.y < cell.y
                || inkRegion.x + inkRegion.width > cell.x + CW
                || inkRegion.y + inkRegion.height > cell.y + CH)
            problems.append("公式墨迹超出atlascell;");
        LayoutBox visible = inkRegion.intersect(cell).intersect(new LayoutBox(0, 0, W, H));
        int background = dark ? 0xff172131 : 0xfff5f7fa;
        for (int y = visible.y; y < visible.y + visible.height; y++)
            for (int x = visible.x; x < visible.x + visible.width; x++)
                if (pixels.getRGB(x, y) != background) ink++;
        if (!rejected
                && (hasVisibleGlyph(value) || !value.rules.isEmpty() || !value.strokes.isEmpty())
                && ink == 0) problems.append("有墨迹盒无GPU像素;");
        boolean bad = problems.length() > 0;
        if (bad) failed++;
        executed++;
        evidence.append(escape(c.id))
                .append('	')
                .append(em)
                .append('	')
                .append(dark ? "dark" : "light")
                .append('	')
                .append(
                        bad
                                ? "FAIL"
                                : rejectedNativeWarning
                                        ? "observed-rejected-with-native-warning"
                                        : styleFallbackObserved
                                                ? "observed-with-style-fallback"
                                                : "observed-unreviewed")
                .append('	')
                .append(artifact)
                .append('	')
                .append(region.x)
                .append(',')
                .append(region.y)
                .append(',')
                .append(region.width)
                .append(',')
                .append(region.height)
                .append('	')
                .append(value.width)
                .append(',')
                .append(value.height)
                .append(',')
                .append(value.baseline)
                .append('	')
                .append(escape(c.types))
                .append('	')
                .append(escape(text.toString()))
                .append('	')
                .append(escape(glyphs.toString()))
                .append('	')
                .append(
                        escape(
                                "scope="
                                        + c.scope
                                        + " context="
                                        + c.context
                                        + " outcome="
                                        + c.outcome
                                        + " ink="
                                        + ink
                                        + " typed=typed-sources.tsv glyphs=glyph-facts.tsv "
                                        + " "
                                        + notes
                                        + " "
                                        + problems))
                .append('\n');
        if (bad) System.err.println("COVERAGE FAIL " + c.id + " " + c.source + " -> " + problems);
    }

    /** 仅观察已有 typed AST，不给生产新增测试 API 或另做 parser。 */
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void typedFacts(
            Object node, int length, StringBuilder out, StringBuilder problems, String prefix)
            throws Exception {
        if (node == null) return;
        int start = (Integer) field(node, "start"), end = (Integer) field(node, "end");
        if (start < 0 || end < start || end > length) problems.append("typed UTF16范围越界;");
        out.append(prefix)
                .append('\t')
                .append(field(node, "kind"))
                .append('\t')
                .append(start)
                .append('\t')
                .append(end)
                .append('\t')
                .append(escape(String.valueOf(field(node, "value"))))
                .append('\t');
        Object payload = field(node, "payload");
        if (payload != null && payload.getClass().getSimpleName().equals("GlyphSpec")) {
            out.append("GlyphSpec[");
            for (String name :
                    new String[] {
                        "originalToken",
                        "mode",
                        "atomGroup",
                        "catalogFont",
                        "replacement",
                        "styledFont"
                    }) out.append(name).append('=').append(field(payload, name)).append(',');
            out.append("]; ");
        }
        out.append('\n');
        for (Object child : (List<?>) field(node, "children"))
            typedFacts(child, length, out, problems, prefix);
        for (String name : new String[] {"first", "second", "third"})
            typedFacts(field(node, name), length, out, problems, prefix);
    }

    private static String codepoints(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            out.append("U+")
                    .append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT))
                    .append(',');
        }
        return out.toString();
    }

    private static boolean spacious(Case c) {
        return Arrays.asList("function", "environment", "acceptance", "negative", "plugin-context")
                .contains(c.kind);
    }

    private static boolean spacesOnly(String value) {
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) return false;
        }
        return !value.isEmpty();
    }

    private static void expand(float[] bounds, float left, float top, float right, float bottom) {
        bounds[0] = Math.min(bounds[0], left);
        bounds[1] = Math.min(bounds[1], top);
        bounds[2] = Math.max(bounds[2], right);
        bounds[3] = Math.max(bounds[3], bottom);
    }

    private static String glyphText(TextLayout text, TextLayout.Glyph glyph) {
        int start = Math.max(0, Math.min(text.logicalText.length(), glyph.logicalStart)),
                end = Math.max(start, Math.min(text.logicalText.length(), glyph.logicalEnd));
        return text.logicalText.substring(start, end);
    }

    private static boolean hasVisibleGlyph(MathLayout value) {
        for (MathLayout.TextPiece p : value.pieces)
            for (TextLayout.Line line : p.text.lines())
                for (TextLayout.Glyph g : line.glyphs())
                    if (!spacesOnly(glyphText(p.text, g))
                            && (g.inkBounds.known
                                    ? g.inkBounds.right > g.inkBounds.left
                                            && g.inkBounds.bottom > g.inkBounds.top
                                    : true)) return true;
        return false;
    }

    /** Unicode17标准变体＋正式STIX cmap14实读，原catalog PUA不作真实Unicode字符比较。 */
    private static int[] variantExpectation(String expected) {
        if (expected.length() != 1) return null;
        switch (expected.charAt(0)) {
            case 0xe00c:
                return new int[] {0x2268, 1816};
            case 0xe00d:
                return new int[] {0x2269, 1818};
            case 0xe01a:
                return new int[] {0x228a, 1869};
            case 0xe01b:
                return new int[] {0x228b, 1871};
            case 0xe017:
                return new int[] {0x2acb, 3119};
            case 0xe019:
                return new int[] {0x2acc, 3121};
            default:
                return null;
        }
    }

    /** 数学字体段的Unicode空洞由固定token与typed事实限定，不以NFKD把Fraktur/Script/BB混作普通字母。 */
    private static int[] letterlikeExpectation(Case c) {
        if (!c.kind.equals("symbol")
                || c.expected.length() != 1
                || c.token.codePointCount(0, c.token.length()) != 1) return null;
        String holes = "ℬℰℱℋℐℒℳℛℭℌℑℜℨℂℍℕℙℚℝℤ", letters = "BEFHILMRCHIRZCHNPQRZ";
        int[] ids = {
            1245, 1248, 1249, 1221, 1226, 1228, 1250, 1234, 1246, 1222, 1227, 1235, 1242, 1217,
            1223, 1230, 1232, 1233, 1236, 1238
        };
        int token = c.token.codePointAt(0);
        for (int i = 0; i < holes.length(); i++)
            if (c.expected.charAt(0) == letters.charAt(i)) {
                int base = i < 8 ? 0x1d49c : i < 13 ? 0x1d504 : 0x1d538;
                if (token == holes.charAt(i) || token == base + letters.charAt(i) - 'A')
                    return new int[] {holes.charAt(i), ids[i], i < 8 ? 0 : i < 13 ? 1 : 2};
            }
        return null;
    }

    private static boolean matchingLetterlikeNode(Object node, Case c, int[] wanted)
            throws ReflectiveOperationException {
        if (node == null) return false;
        Object payload = field(node, "payload");
        if (payload != null
                && payload.getClass().getSimpleName().equals("GlyphSpec")
                && c.token.equals(field(payload, "originalToken"))
                && c.expected.equals(field(payload, "replacement"))
                && c.mode.equals(field(payload, "mode"))) {
            String style = new String[] {"mathscr", "mathfrak", "mathbb"}[wanted[2]];
            if (style.equals(field(payload, "styledFont"))
                    && "main".equals(field(payload, "catalogFont"))) return true;
            if (c.token.codePointAt(0) == wanted[0]
                    && wanted[2] == 2
                    && field(payload, "styledFont") == null
                    && "ams".equals(field(payload, "catalogFont"))
                    && "textord".equals(field(payload, "atomGroup"))) return true;
        }
        for (Object child : (List<?>) field(node, "children"))
            if (matchingLetterlikeNode(child, c, wanted)) return true;
        for (String name : new String[] {"first", "second", "third"})
            if (matchingLetterlikeNode(field(node, name), c, wanted)) return true;
        return false;
    }

    private static boolean verifiedLetterlike(Case c, MathContent content, MathLayout value)
            throws ReflectiveOperationException {
        int[] wanted = letterlikeExpectation(c);
        if (!matchingLetterlikeNode(field(content, "root"), c, wanted)) return false;
        String cp = new String(Character.toChars(wanted[0]));
        int count = 0;
        for (MathLayout.TextPiece p : value.pieces)
            if (p.text.logicalText.equals(cp))
                for (TextLayout.Line line : p.text.lines())
                    for (TextLayout.Glyph g : line.glyphs()) {
                        if (g.replacement
                                || g.glyphId != wanted[1]
                                || !g.source.id.equals("stix-two-math")
                                || !g.source.origin.endsWith("/math/STIXTwoMath-Regular.otf"))
                            return false;
                        count++;
                    }
        return count == 1;
    }

    /** 固定符号目录PUA有明确Unicode语义，v12 Array9与coverage双em正式HB核验，不泛归约任何关系符号。 */
    private static String semanticPua(String expected) {
        if (expected.length() != 1) return null;
        switch (expected.charAt(0)) {
            case 0xe131:
                return "𝚤";
            case 0xe237:
                return "𝚥";
            case 0xe00e:
                return "≧̸";
            case 0xe00f:
                return "⩾̸";
            case 0xe010:
                return "⩽̸";
            case 0xe011:
                return "≦̸";
            case 0xe006:
                return "∤";
            case 0xe007:
                return "∦";
            case 0xe016:
                return "⫅̸";
            case 0xe018:
                return "⫆̸";
            default:
                return null;
        }
    }

    private static int[] puaGlyphs(String expected) {
        switch (expected.charAt(0)) {
            case 0xe131:
                return new int[] {3929};
            case 0xe237:
                return new int[] {3930};
            case 0xe00e:
                return new int[] {1814, 844};
            case 0xe00f:
                return new int[] {3037, 844};
            case 0xe010:
                return new int[] {3036, 844};
            case 0xe011:
                return new int[] {1813, 844};
            case 0xe006:
                return new int[] {1686};
            case 0xe007:
                return new int[] {1690};
            case 0xe016:
                return new int[] {3112, 844};
            case 0xe018:
                return new int[] {3113, 844};
            default:
                throw new IllegalArgumentException("未知PUA不能标绿");
        }
    }

    /** 非法公式原文进入已有默认文本入口；准确缺字warning保留，不伪称它是成功数学glyph。 */
    private static boolean expectedRejectedWarning(
            Case c, MathLayout.TextPiece p, TextLayout.Diagnostic d) {
        return c.kind.equals("negative")
                && c.source.equals("𝑨+\\notACommand{x}")
                && p.text.logicalText.equals(c.source)
                && p.sourceStart == 0
                && p.sourceEnd == c.source.length()
                && d.sourceStart == 0
                && d.sourceEnd == 2
                && d.kind == TextLayout.Diagnostic.Kind.MISSING_GLYPH
                && d.message.equals("候选列表无法正确生成 Zyyy 范围；使用列表内稳定替代");
    }

    private static boolean expectedRejectedGlyph(
            Case c, MathLayout.TextPiece p, TextLayout.Glyph g) {
        if (!g.replacement
                || g.glyphId == 0
                || !g.source.origin.endsWith("/unifont/unifont-16.0.04.otf")
                || g.logicalStart != 0
                || g.logicalEnd != 2
                || !glyphText(p.text, g).equals("𝑨")) return false;
        for (TextLayout.Diagnostic d : p.text.diagnostics())
            if (expectedRejectedWarning(c, p, d)) return true;
        return false;
    }

    private static club.heiqi.uilib.ui.scene.text.FontStyle requestedProfileStyle(Case c) {
        String member = c.context.substring(c.context.lastIndexOf(':') + 1);
        return club.heiqi.uilib.ui.scene.text.FontStyle.minecraft(
                member.contains("bold"), member.contains("italic"));
    }

    /** 只接受已知CJK或MonoGreek公开tail profile的真实普通face与准确STYLE_FALLBACK，不接受其它font阶段错误。 */
    private static boolean allowedCjkStyle(
            Case c, MathLayout.TextPiece p, TextLayout.Diagnostic d, float em) {
        if (!c.kind.equals("profile")
                || !c.context.startsWith("unicode-text-real-family:")
                || d.kind != TextLayout.Diagnostic.Kind.STYLE_FALLBACK) return false;
        if (d.sourceStart < 0
                || d.sourceEnd > p.text.logicalText.length()
                || d.sourceEnd <= d.sourceStart
                || !(p.text.logicalText.substring(d.sourceStart, d.sourceEnd).equals("中")
                        || c.token.startsWith("texttt:")
                                && p.text.logicalText
                                        .substring(d.sourceStart, d.sourceEnd)
                                        .equals("Ω"))) return false;
        for (TextLayout.Line line : p.text.lines())
            for (TextLayout.Glyph g : line.glyphs()) {
                if (g.logicalStart > d.sourceStart
                        || g.logicalEnd < d.sourceEnd
                        || g.replacement
                        || g.glyphId == 0
                        || !g.rasterStyle.normal()) continue;
                if (!g.source.origin.endsWith("/unifont/unifont-16.0.04.otf")
                        || !g.source
                                .actualStyle()
                                .equals(club.heiqi.uilib.ui.scene.text.FontStyle.NORMAL)
                        || g.resolvedStyle == null
                        || !g.resolvedStyle.fontStyle.equals(requestedProfileStyle(c))) continue;
                String message =
                        "请求="
                                + g.resolvedStyle.fontStyle
                                + " 实际="
                                + g.source.actualStyle()
                                + " family="
                                + g.source.metadata().family
                                + " origin="
                                + g.source.origin
                                + " face="
                                + g.source.faceIndex
                                + " axes="
                                + g.source.axisValues();
                if (d.message.equals(message)) return true;
            }
        return false;
    }

    private void profileStyle(
            Case c, MathLayout.TextPiece p, TextLayout.Glyph g, float em, StringBuilder problems) {
        if (!c.kind.equals("profile") || !c.context.startsWith("unicode-text-real-family:")) return;
        if (!g.rasterStyle.normal()) problems.append("文字字族默认合成样式;");
        String chars = glyphText(p.text, g);
        for (String letter : new String[] {"A", "Ω"})
            if (chars.contains(letter)) {
                club.heiqi.uilib.ui.scene.text.FontStyle request = requestedProfileStyle(c);
                boolean monoGreek =
                        letter.equals("Ω")
                                && c.token.startsWith("texttt:")
                                && g.source.origin.endsWith("/unifont/unifont-16.0.04.otf")
                                && g.source
                                        .actualStyle()
                                        .equals(club.heiqi.uilib.ui.scene.text.FontStyle.NORMAL);
                boolean warningMatched =
                        request.equals(club.heiqi.uilib.ui.scene.text.FontStyle.NORMAL);
                if (monoGreek)
                    for (TextLayout.Diagnostic d : p.text.diagnostics())
                        if (allowedCjkStyle(c, p, d, em)
                                && d.sourceStart == g.logicalStart
                                && d.sourceEnd == g.logicalEnd) warningMatched = true;
                if (g.resolvedStyle == null
                        || !g.resolvedStyle.fontStyle.equals(request)
                        || !(monoGreek ? warningMatched : g.source.actualStyle().equals(request)))
                    problems.append("Latin/Greek真实member或准确tail降级不符;");
                String expectedFamily =
                        c.token.startsWith("textrm:")
                                ? "Noto Serif"
                                : c.token.startsWith("textsf:") ? "Noto Sans" : "IBM Plex Mono";
                if (!g.source.metadata().family.equals(monoGreek ? "Unifont" : expectedFamily))
                    problems.append("profile请求真实字族身份不符;");
                String key = c.token.split(":")[0] + "@" + em + ":" + letter;
                if (c.context.endsWith(":normal"))
                    profileFamilies.put(key, g.source.metadata().family);
                if (!g.source.metadata().family.equals(profileFamilies.get(key)))
                    problems.append("Latin/Greek真实字族身份改变;");
            }
    }

    private BufferedImage renderReady(ScenePageSession page, boolean dark) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        do {
            glViewport(0, 0, W, H);
            glDisable(GL_SCISSOR_TEST);
            if (dark) glClearColor(23 / 255f, 33 / 255f, 49 / 255f, 1);
            else glClearColor(245 / 255f, 247 / 255f, 250 / 255f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            page.frame();
            if (ready == demand) {
                glFinish();
                return readPixels();
            }
            Thread.sleep(4);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("正式GPU需求未就绪: " + ready + "/" + demand);
    }

    private BufferedImage readPixels() {
        ByteBuffer bytes = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - 1 - y) * W + x) * 4;
                    image.setRGB(
                            x,
                            y,
                            (bytes.get(i + 3) & 255) << 24
                                    | (bytes.get(i) & 255) << 16
                                    | (bytes.get(i + 1) & 255) << 8
                                    | (bytes.get(i + 2) & 255));
                }
            return image;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private void saveEvidence() throws Exception {
        Files.write(
                output.resolve("evidence.tsv"),
                evidence.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(
                output.resolve("glyph-facts.tsv"),
                glyphEvidence.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(
                output.resolve("typed-sources.tsv"),
                typedEvidence.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 只归约 Mathematical Alphanumeric Symbols；不对重音、圈字、单位等做全 Unicode NFKD。 */
    private static String semanticCharacters(String text, boolean expectation) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String atom = new String(Character.toChars(cp));
            if (cp >= 0x1d6a8 && cp <= 0x1d7c9) {
                int offset = (cp - 0x1d6a8) % 58;
                if (offset == 17) {
                    out.appendCodePoint(0x3f4);
                    continue;
                }
                if (offset >= 52) {
                    out.appendCodePoint(
                            new int[] {0x3f5, 0x3d1, 0x3f0, 0x3d5, 0x3f1, 0x3d6}[offset - 52]);
                    continue;
                }
            }
            boolean hole =
                    !expectation && cp == 0x210e; // 数学斜体h唯一空洞；Fraktur/Script/DoubleStruck保持原字形语义。
            out.append(
                    (cp >= 0x1d400 && cp <= 0x1d7ff) || hole
                            ? java.text.Normalizer.normalize(atom, java.text.Normalizer.Form.NFKD)
                            : atom);
        }
        return out.toString();
    }

    private static boolean finite(float... xs) {
        for (float x : xs) if (!Float.isFinite(x)) return false;
        return true;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else out.append(c);
        }
        return out.toString();
    }
}
