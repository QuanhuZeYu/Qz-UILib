package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 真实 FT/HB 正文族、数学文字作用域与代际快照；无 MC、AWT 或第二整形路径。 */
public final class MathTextFontScenario extends TestApplication {
    private static final String BASE = "assets/qz_uilib/fonts/math-text/";
    private static final String[] ALIASES = {
        MathTextFontSources.ROMAN, MathTextFontSources.SANS, MathTextFontSources.MONO
    };
    private static final String[] FAMILIES = {"Noto Serif", "Noto Sans", "IBM Plex Mono"};
    private static final String[] COMMANDS = {"textrm", "textsf", "texttt"};
    private static final FontStyle[] STYLES = {
        FontStyle.NORMAL,
        new FontStyle(700, FontStyle.Posture.NORMAL),
        new FontStyle(400, FontStyle.Posture.ITALIC),
        new FontStyle(700, FontStyle.Posture.ITALIC)
    };

    public static void main(String[] args) throws Exception {
        new MathTextFontScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("固定上游SHA完整OFL及真实四成员元数据", this::resources);
        scenario("三族四样式多em真实HB度量及无合成光栅", this::faces);
        scenario("MathContent到Engine真实familyweightposture作用域", this::commands);
        scenario("verb与标签共用具名字体真实管线", this::verbatimAndTags);
        scenario("公开具名尾链非Latin覆盖与explicit完整覆盖", this::candidates);
        scenario("registerreload通知换代LRU及旧glyph重开", this::lifetime);
        scenario("默认资源退休后数学正文尾链自动换代", this::retiredDefaults);
    }

    private void resources() throws Exception {
        String manifest = new String(resource(BASE + "manifest.json"), StandardCharsets.UTF_8);
        Matcher entries =
                Pattern.compile(
                                "\"file\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"bytes\"\\s*:\\s*(\\d+)\\s*,\\s*\"sha256\"\\s*:\\s*\"([a-f0-9]{64})\"")
                        .matcher(manifest);
        int count = 0, fonts = 0;
        long bytes = 0;
        while (entries.find()) {
            byte[] data = resource(BASE + entries.group(1));
            require(
                    data.length == Integer.parseInt(entries.group(3)),
                    "资源长度不匹配: " + entries.group(1));
            require(sha(data).equals(entries.group(4)), "资源SHA不匹配: " + entries.group(1));
            require(
                    entries.group(2)
                            .matches(
                                    "https://raw[.]githubusercontent[.]com/google/fonts/[a-f0-9]{40}/.+"),
                    "来源未pin commit");
            count++;
            bytes += data.length;
            if (entries.group(1).endsWith(".ttf")) fonts++;
        }
        require(count == 18 && fonts == 12, "manifest未完整覆盖四款三族及license/metadata");
        Map<String, List<FontSource>> sources = MathTextFontSources.namedSources(1);
        require(sources.size() == 3, "具名族名单不完整");
        for (int i = 0; i < ALIASES.length; i++) {
            String directory = i == 0 ? "roman/" : i == 1 ? "sans/" : "mono/";
            String license =
                    new String(resource(BASE + directory + "OFL.txt"), StandardCharsets.UTF_8);
            require(
                    license.contains("Copyright")
                            && license.contains("SIL OPEN FONT LICENSE Version 1.1")
                            && license.contains("TERMINATION")
                            && license.trim().endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."),
                    "完整OFL缺失");
            String metadata =
                    new String(resource(BASE + directory + "METADATA.pb"), StandardCharsets.UTF_8);
            require(
                    metadata.contains("license: \"OFL\"")
                            && metadata.contains("name: \"" + FAMILIES[i] + "\""),
                    "上游family/license元数据不一致");
            require(sources.get(ALIASES[i]).size() == 1, "纯族工厂存在隐式tail");
            FontSource family = sources.get(ALIASES[i]).get(0);
            require(family.resolve(FontStyle.NORMAL, false).size() == 4, "真实family成员数不为4");
            for (FontStyle style : STYLES) {
                FontSource source = family.resolve(style, false).get(0);
                require(source.metadata().family.equals(FAMILIES[i]), "真实family与alias不符");
                require(
                        source.metadata().style.equals(style) && source.actualStyle().equals(style),
                        "真实样式未来自字体元数据");
                require(source.axisValues().isEmpty(), "静态face被包装成轴实例");
                System.out.println(
                        ALIASES[i]
                                + " "
                                + source.metadata().family
                                + "/"
                                + source.metadata().subfamily
                                + " actual="
                                + source.actualStyle()
                                + " SHA="
                                + source.contentFingerprint());
            }
        }
        System.out.println("固定资源 entries=" + count + " fonts=" + fonts + " bytes=" + bytes);
    }

    private void faces() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine());
                FontSession raster = new LwjglFontEngine().openSession()) {
            require(service.generation() == 1, "构造具名初始化不应增加generation");
            for (int i = 0; i < ALIASES.length; i++)
                for (FontStyle fs : STYLES)
                    for (float em : new float[] {18, 32.3f, 53}) {
                        TextStyle style = style(ALIASES[i], em, fs, null);
                        TextLayout w = text(service, "W", style),
                                narrow = text(service, "i", style);
                        assertText(w, FAMILIES[i], fs, em);
                        assertText(narrow, FAMILIES[i], fs, em);
                        float difference = Math.abs(w.advanceWidth - narrow.advanceWidth);
                        require(
                                i == 2 ? difference < .001f : difference > em * .2f,
                                "mono/比例族advance不符");
                        // Noto 两族的 Greek/Cyrillic 实际由此原生探针核验；mono 不承诺未覆盖的脚本。
                        if (i < 2)
                            for (String sample : new String[] {"αΩ", "Жя"})
                                assertText(text(service, sample, style), FAMILIES[i], fs, em);
                        TextLayout.Glyph g = w.lines().get(0).glyphs().get(0);
                        try (FontFace face = g.source.openFace(raster, em)) {
                            require(
                                    Math.abs(
                                                    face.glyphHorizontalAdvance(g.glyphId)
                                                            - w.advanceWidth)
                                            < .001f,
                                    "HB真实advance与布局不一致");
                            GlyphBitmap image = face.rasterize(g.glyphId, g.rasterStyle);
                            require(
                                    image.width > 0 && image.height > 0 && alpha(image) > 0,
                                    "真实样式未产生像素");
                        }
                        TextEffects obsolete =
                                new TextEffects(true, true, false, false, false, 9, .7f);
                        TextLayout legacy = text(service, "W", style.withEffects(obsolete));
                        require(
                                Math.abs(legacy.advanceWidth - w.advanceWidth) < .001f
                                        && legacy.lines()
                                                .get(0)
                                                .glyphs()
                                                .get(0)
                                                .rasterStyle
                                                .normal(),
                                "遗留effects合成污染真实face");
                        System.out.println(
                                ALIASES[i]
                                        + " "
                                        + fs
                                        + " em="
                                        + em
                                        + " W="
                                        + w.advanceWidth
                                        + " i="
                                        + narrow.advanceWidth);
                    }
        }
    }

    private void commands() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            for (int i = 0; i < ALIASES.length; i++)
                for (float em : new float[] {18, 32.3f, 53}) {
                    for (int s = 0; s < STYLES.length; s++) {
                        String expression =
                                "\\"
                                        + COMMANDS[i]
                                        + "{"
                                        + (s >= 2 ? "\\textit{" : "")
                                        + (s % 2 == 1 ? "\\textbf{" : "")
                                        + "Wi"
                                        + (s % 2 == 1 ? "}" : "")
                                        + (s >= 2 ? "}" : "")
                                        + "}";
                        MathLayout layout =
                                math(
                                        service,
                                        expression,
                                        style("default", em, FontStyle.NORMAL, null));
                        for (MathLayout.TextPiece piece : layout.pieces)
                            assertText(piece.text, FAMILIES[i], STYLES[s], em);
                        TextLayout.Glyph w = glyphs(layout).get(0), narrow = glyphs(layout).get(1);
                        require(
                                i == 2
                                        ? Math.abs(w.xAdvance - narrow.xAdvance) < .001f
                                        : w.xAdvance > narrow.xAdvance,
                                "数学文字作用域未真实改变字宽");
                        require(
                                w.sourceStart >= 0 && narrow.sourceEnd <= expression.length(),
                                "glyph来源越界");
                    }
                }
            TextStyle serif = style(MathTextFontSources.ROMAN, 32.3f, FontStyle.NORMAL, null);
            MathLayout inherit = math(service, "\\text{Wi}", serif);
            for (MathLayout.TextPiece p : inherit.pieces)
                assertText(p.text, FAMILIES[0], FontStyle.NORMAL, 32.3f);
            MathLayout emphasis = math(service, "\\textrm{\\textit{\\emph{Wi}}}", serif);
            for (MathLayout.TextPiece p : emphasis.pieces)
                assertText(p.text, FAMILIES[0], FontStyle.NORMAL, 32.3f);
            TextStyle italic = style(MathTextFontSources.SANS, 32.3f, STYLES[2], null);
            MathLayout boldInherited = math(service, "\\textbf{Wi}", italic);
            for (MathLayout.TextPiece p : boldInherited.pieces)
                assertText(p.text, FAMILIES[1], STYLES[3], 32.3f);
            MathLayout plainBold =
                    math(service, "\\textbf{Wi}", style("default", 32.3f, FontStyle.NORMAL, null));
            for (MathLayout.TextPiece p : plainBold.pieces)
                assertText(p.text, FAMILIES[0], STYLES[1], 32.3f);
            for (MathLayout.TextPiece p :
                    math(service, "\\textit{Wi}", style("default", 32.3f, FontStyle.NORMAL, null))
                            .pieces) assertText(p.text, FAMILIES[0], STYLES[2], 32.3f);
            for (MathLayout.TextPiece p :
                    math(
                                    service,
                                    "\\textbf{\\textit{Wi}}",
                                    style("default", 32.3f, FontStyle.NORMAL, null))
                            .pieces) assertText(p.text, FAMILIES[0], STYLES[3], 32.3f);
            service.register(
                    "math-text-custom-body",
                    MathTextFontSources.namedSources(7).get(MathTextFontSources.SANS));
            TextStyle custom = style("math-text-custom-body", 32.3f, STYLES[1], null);
            for (MathLayout.TextPiece p : math(service, "\\textit{Wi}", custom).pieces)
                assertText(p.text, FAMILIES[1], STYLES[3], 32.3f);
            System.out.println(
                    "plain textbf(default) source="
                            + glyphs(plainBold).get(0).source
                            + " actual="
                            + glyphs(plainBold).get(0).source.actualStyle()
                            + " diagnostics="
                            + diagnostics(plainBold));
        }
    }

    private void verbatimAndTags() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            for (float em : new float[] {18, 32.3f, 53}) {
                MathLayout verb =
                        math(service, "\\verb|Wi|", style("default", em, FontStyle.NORMAL, null));
                for (MathLayout.TextPiece p : verb.pieces)
                    assertText(p.text, FAMILIES[2], FontStyle.NORMAL, em);
                List<TextLayout.Glyph> vg = glyphs(verb);
                require(
                        vg.size() == 2 && Math.abs(vg.get(0).xAdvance - vg.get(1).xAdvance) < .001f,
                        "verb未使用真实等宽族");
                MathLayout tagged =
                        math(
                                service,
                                "x\\tag{\\textsf{\\textbf{Wi}}}",
                                style("default", em, FontStyle.NORMAL, null));
                int labelGlyphs = 0;
                for (TextLayout.Glyph g : glyphs(tagged))
                    if (g.source.metadata().family.equals(FAMILIES[1])) {
                        require(
                                g.source.actualStyle().equals(STYLES[1]) && g.rasterStyle.normal(),
                                "标签text scope未使用真实bold");
                        labelGlyphs++;
                    }
                require(labelGlyphs == 2, "标签正文未选择具名sans族");
            }
        }
    }

    private void candidates() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            List<FontSource> defaults = service.defaultCandidates();
            Map<String, List<FontSource>> chains = MathTextFontSources.namedSources(1, defaults);
            for (String alias : ALIASES) {
                List<FontSource> chain = chains.get(alias);
                require(
                        chain.size() == defaults.size() + 1
                                && chain.subList(1, chain.size()).equals(defaults),
                        "公开具名tail不是原配置列表");
            }
            for (FontSource source : defaults)
                require(!source.id.startsWith("math-text-"), "默认文字链被污染");
            MathLayout cjk =
                    math(service, "\\textrm{中文 α}", style("default", 32, FontStyle.NORMAL, null));
            boolean foundDefault = false, foundGreek = false;
            for (TextLayout.Glyph g : glyphs(cjk)) {
                require(g.glyphId != 0 && !g.replacement, "具名候选丢失CJK/Greek覆盖");
                if (g.source.id.equals("unifont")) foundDefault = true;
                if (g.source.metadata().family.equals("Noto Serif")) foundGreek = true;
            }
            require(foundDefault && foundGreek, "CJK显式默认tail或Greek真实族未被选择");
            for (MathLayout.TextPiece p :
                    math(service, "\\textsf{α}", style("default", 32, FontStyle.NORMAL, null))
                            .pieces) assertText(p.text, "Noto Sans", FontStyle.NORMAL, 32);
            List<FontSource> override =
                    new ArrayList<FontSource>(
                            MathTextFontSources.namedSources(9).get(MathTextFontSources.SANS));
            override.add(
                    FontSource.fromResource(
                            "explicit-stix",
                            9,
                            "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf",
                            false));
            TextStyle explicit = style("default", 32, FontStyle.NORMAL, override);
            MathLayout overridden = math(service, "\\textrm{\\textbf{Wi}}\\texttt{Wi}", explicit);
            for (TextLayout.Glyph g : glyphs(overridden))
                require(
                        g.source.metadata().family.equals("Noto Sans"),
                        "explicit root候选未完整覆盖named");
            for (MathLayout.TextPiece p : math(service, "\\verb|Wi|", explicit).pieces)
                assertText(p.text, FAMILIES[1], FontStyle.NORMAL, 32);
            boolean emptyRejected = false;
            try {
                text(
                        service,
                        "W",
                        style(
                                MathTextFontSources.ROMAN,
                                32,
                                FontStyle.NORMAL,
                                Collections.<FontSource>emptyList()));
            } catch (IllegalStateException expected) {
                emptyRejected = expected.getMessage().contains("候选列表无有效文字字体");
            }
            require(emptyRejected, "显式空候选被隐式fallback补齐");
            TextLayout ordinary = text(service, "Wi", style("default", 32, FontStyle.NORMAL, null));
            require(
                    ordinary.lines().get(0).glyphs().get(0).source.equals(defaults.get(0)),
                    "普通默认font改变");
        }
    }

    private void lifetime() throws Exception {
        TextLayout.Glyph retained;
        String hash;
        try (FontTextService service =
                new FontTextService(
                        FontTextService.defaultSources(getClass().getClassLoader(), 1),
                        new LwjglFontEngine(),
                        1,
                        16,
                        1024)) {
            TextStyle style = style(MathTextFontSources.ROMAN, 32, STYLES[3], null);
            TextLayout old = text(service, "W", style);
            retained = old.lines().get(0).glyphs().get(0);
            hash = retained.source.contentFingerprint();
            int[] changes = {0};
            try (AutoCloseable listener = service.onInvalidated(() -> changes[0]++)) {
                long before = service.generation();
                FontSource replacement =
                        MathTextFontSources.namedSources(2).get(MathTextFontSources.SANS).get(0);
                service.register(MathTextFontSources.ROMAN, Collections.singletonList(replacement));
                require(
                        service.generation() == before + 1
                                && changes[0] == 1
                                && service.cachedFaceCount() == 0,
                        "register未释放face/失效");
                TextLayout current = text(service, "W", style);
                assertText(current, "Noto Sans", STYLES[3], 32);
                require(
                        current.generation != old.generation
                                && current.lines().get(0).glyphs().get(0).source.revision == 2,
                        "register仍复用旧source或generation");
                text(service, "W", style(MathTextFontSources.MONO, 53, STYLES[1], null));
                require(service.cachedFaceCount() <= 1, "LRU预算失效");
                service.reload(FontTextService.defaultSources(getClass().getClassLoader(), 3));
                require(
                        service.generation() == before + 2
                                && changes[0] == 2
                                && service.cachedFaceCount() == 0,
                        "reload未失效");
                assertText(text(service, "W", style), "Noto Sans", STYLES[3], 32);
                // 默认链换代由公开工厂显式发布新named tail，不在每次layout中静默补入。
                List<FontSource> chain =
                        MathTextFontSources.namedSources(4, service.defaultCandidates())
                                .get(MathTextFontSources.ROMAN);
                service.register(MathTextFontSources.ROMAN, chain);
                TextLayout next = text(service, "W", style);
                require(
                        next.lines().get(0).glyphs().get(0).source.revision == 4 && changes[0] == 3,
                        "公开named chain换代未生效");
                TextLayout chinese = text(service, "中文", style);
                require(
                        chinese.lines().get(0).glyphs().get(0).source.revision == 3
                                && !chinese.lines().get(0).glyphs().get(0).replacement,
                        "显式发布tail未采用新默认source");
            }
            require(
                    retained.source.revision == 1
                            && retained.source.contentFingerprint().equals(hash),
                    "旧source随注册重载改变");
        }
        // 服务及其所有旧face均已关闭，glyph只靠owned source在独立新session重开，不能复用live地址。
        try (FontSession raster = new LwjglFontEngine().openSession();
                FontFace face = retained.source.openFace(raster, retained.logicalEm)) {
            GlyphBitmap image = face.rasterize(retained.glyphId, retained.rasterStyle);
            require(
                    image.width > 0
                            && alpha(image) > 0
                            && retained.source.contentFingerprint().equals(hash),
                    "关闭后glyph不具有独立source快照");
        }
    }

    private static final class ResourceGeneration extends ClassLoader {
        boolean retired;
        int rejected;

        ResourceGeneration() {
            super(MathTextFontScenario.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (retired && name.startsWith("assets/qz_uilib/")) {
                rejected++;
                throw new IllegalStateException("已退休的资源代际: " + name);
            }
            return super.getResourceAsStream(name);
        }
    }

    private void retiredDefaults() throws Exception {
        ResourceGeneration initial = new ResourceGeneration();
        try (FontTextService service =
                new FontTextService(
                        FontTextService.defaultSources(initial, 1), new LwjglFontEngine())) {
            int[] invalidations = {0};
            service.onInvalidated(() -> invalidations[0]++);
            // 和 MC 首次 reload 一样，不预加载将退休的默认资源。
            for (int revision = 2; revision <= 3; revision++) {
                ResourceGeneration next = new ResourceGeneration();
                long generation = service.generation();
                service.reload(FontTextService.defaultSources(next, revision));
                initial.retired = true;
                require(
                        service.generation() == generation + 1 && invalidations[0] == revision - 1,
                        "同一次资源发布重复触发失效");
                for (String alias :
                        new String[] {
                            MathTextFontSources.ROMAN,
                            MathTextFontSources.SANS,
                            MathTextFontSources.MONO
                        }) {
                    TextLayout value =
                            text(service, "Item Ω 中文", style(alias, 28, FontStyle.NORMAL, null));
                    for (TextLayout.Diagnostic diagnostic : value.diagnostics())
                        require(
                                diagnostic.kind != TextLayout.Diagnostic.Kind.FONT_RESOURCE,
                                "数学正文仍读取退休资源: " + diagnostic.message);
                    for (TextLayout.Line line : value.lines())
                        for (TextLayout.Glyph glyph : line.glyphs()) {
                            require(!glyph.replacement && glyph.glyphId != 0, "换代后数学正文缺字");
                            if (glyph.source.id.equals("unifont"))
                                require(glyph.source.revision == revision, "中文尾链不属于当前资源代际");
                        }
                }
                require(initial.rejected == 0, "读取了退休加载器");
                initial = next;
            }
        }
    }

    private static TextStyle style(
            String alias, float em, FontStyle fontStyle, List<FontSource> candidates) {
        return new TextStyle(
                alias,
                em,
                0xffffffff,
                candidates,
                TextEffects.NORMAL,
                TextDecoration.NONE,
                fontStyle);
    }

    private static TextLayout text(FontTextService service, String value, TextStyle style) {
        return service.layout(value, style, Integer.MAX_VALUE, TextPolicy.NO_WRAP);
    }

    private static MathLayout math(FontTextService service, String source, TextStyle style) {
        MathContent content = MathContent.parse(source);
        require(content.diagnostics.isEmpty(), "解析失败: " + source + " " + content.diagnostics);
        MathLayout layout =
                new MathLayoutEngine()
                        .layout(
                                content,
                                style,
                                MathStyle.DISPLAY,
                                service,
                                Collections.<String, MathEmbedMetrics>emptyMap());
        require(
                !layout.fallback && !glyphs(layout).isEmpty(),
                "数学布局失败: " + source + " " + diagnostics(layout));
        return layout;
    }

    private static List<TextLayout.Glyph> glyphs(MathLayout layout) {
        List<TextLayout.Glyph> result = new ArrayList<TextLayout.Glyph>();
        for (MathLayout.TextPiece p : layout.pieces) result.addAll(p.text.lines().get(0).glyphs());
        return result;
    }

    private static String diagnostics(MathLayout layout) {
        List<String> messages = new ArrayList<String>();
        for (MathLayout.Diagnostic d : layout.diagnostics) messages.add(d.stage + ":" + d.message);
        return messages.toString();
    }

    private static void assertText(TextLayout layout, String family, FontStyle style, float em) {
        require(!layout.lines().get(0).glyphs().isEmpty(), "未生成字形");
        for (TextLayout.Glyph g : layout.lines().get(0).glyphs()) {
            require(
                    g.source.metadata().family.equals(family),
                    "实际family不符: " + g.source.metadata().family + " != " + family);
            require(
                    g.source.actualStyle().equals(style),
                    "实际样式不符: " + g.source.actualStyle() + " != " + style);
            require(g.glyphId != 0 && !g.replacement && g.rasterStyle.normal(), "缺字或合成样式");
            require(g.logicalEm == em && g.resolvedStyle.fontStyle.equals(style), "em/请求样式未保留");
        }
        for (TextLayout.Diagnostic d : layout.diagnostics())
            require(d.kind != TextLayout.Diagnostic.Kind.STYLE_FALLBACK, "真实样式被降级: " + d.message);
    }

    private static long alpha(GlyphBitmap bitmap) {
        long sum = 0;
        byte[] pixels = bitmap.pixels();
        for (int i = 3; i < pixels.length; i += 4) sum += pixels[i] & 255;
        return sum;
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream in =
                MathTextFontScenario.class.getClassLoader().getResourceAsStream(path)) {
            require(in != null, "资源缺失: " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int n; (n = in.read(block)) != -1; ) out.write(block, 0, n);
            return out.toByteArray();
        }
    }
}
