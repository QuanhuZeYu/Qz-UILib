package club.heiqi.uilib.ui.scene.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontService;

/** 非 JUnit 真实 CPU 链路；不加载 MC/Forge/AWT，不用 GPU 是否就绪参与度量。 */
public final class FontTextScenario {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static TextPolicy policy(TextPolicy.Direction direction, TextPolicy.Wrap wrap, String language) {
        return new TextPolicy(direction, wrap, language, 0, 0);
    }
    private static List<TextLayout.Glyph> glyphs(TextLayout layout) {
        List<TextLayout.Glyph> result = new ArrayList<TextLayout.Glyph>();
        for (TextLayout.Line line : layout.lines()) result.addAll(line.glyphs());
        return result;
    }
    private static void facts(TextLayout layout) {
        float width = 0, height = 0;
        for (TextLayout.Line line : layout.lines()) {
            require(line.lineHeight > 0, "真实行高为空");
            require(line.logicalEnd >= line.logicalStart, "行来源倒置");
            for (TextLayout.Glyph glyph : line.glyphs()) {
                require(glyph.logicalStart >= line.logicalStart && glyph.logicalEnd <= line.logicalEnd, "字形范围跨越最终行");
                require(glyph.sourceEnd > glyph.sourceStart, "字形原source范围为空");
                require(Float.isFinite(glyph.x) && Float.isFinite(glyph.y), "字形位置无效");
                require(glyph.inkBounds != null, "glyph没有不可变墨迹边界载荷");
            }
            width = Math.max(width, line.advance); height += line.lineHeight;
        }
        require(layout.width == (int)Math.ceil(width) && layout.height == (int)Math.ceil(height), "scene边界与浮点advance/行高不一致");
    }
    private static void bidi(FontTextService service, TextStyle style) {
        String text = "中文 A (שלום 123) العربية \u2066xyz\u2069 Z";
        TextLayout layout = service.layout(text, style, 1000, TextPolicy.SINGLE_LINE); facts(layout);
        require(layout.lines().get(0).text.equals(text), "Bidi布局改写逻辑原文");
        boolean reverse = false; int previous = -1;
        for (TextLayout.Glyph g : glyphs(layout)) {
            if (g.logicalStart < previous) reverse = true;
            previous = g.logicalStart;
            require(!text.substring(g.logicalStart, g.logicalEnd).equals("\u2066")
                && !text.substring(g.logicalStart, g.logicalEnd).equals("\u2069"), "Bidi控制被绘制");
        }
        require(reverse, "mixed Bidi没有产生逆逻辑视觉glyph顺序");
        TextLayout rtl = service.layout("ABC שלום DEF", style, 1000, policy(TextPolicy.Direction.RTL, TextPolicy.Wrap.NONE, "he"));
        require(glyphs(rtl).get(0).logicalStart > 0, "显式RTL段落基序未生效");
        System.out.println("PASS mixedBidi 逻辑原文/视觉逆序/隔离控制/显式RTL glyphs=" + glyphs(layout).size());
    }
    private static void arabic(FontTextService service, FontSource arabic) throws Exception {
        TextStyle style = new TextStyle("explicit", 28, -1, Collections.singletonList(arabic));
        TextPolicy p = policy(TextPolicy.Direction.RTL, TextPolicy.Wrap.EMERGENCY, "ar");
        TextLayout layout = service.layout("السلامعليكمالسلامعليكم", style, 65, p); facts(layout);
        require(layout.lines().size() > 1, "Arabic没有应急断行");
        try (NativeFontService nativeService = new NativeFontService(); NativeFontFace face = arabic.openFace(nativeService, 28)) {
            for (TextLayout.Line line : layout.lines()) {
                List<NativeFontFace.Glyph> actual = face.shape(line.text, 0, line.text.length(), NativeFontFace.Direction.RTL, "ar", "Arab", true, true);
                require(actual.size() == line.glyphs().size(), "最终Arabic行没有重新整形");
                float advance = 0;
                for (int i = 0; i < actual.size(); i++) {
                    require(actual.get(i).glyphId == line.glyphs().get(i).glyphId, "Arabic最终行glyph不是独立行连接形");
                    advance += actual.get(i).xAdvance;
                }
                require(Math.abs(advance - line.advance) < .001f, "Arabic最终行真实advance不一致");
            }
        }
        System.out.println("PASS Arabic 最终行真实HB独立验证 lines=" + layout.lines().size());
    }
    private static void indicThai(FontTextService service, FontSource indic, FontSource thai) {
        TextStyle indicStyle = new TextStyle("explicit", 30, -1, Collections.singletonList(indic));
        String text = "क्षि हिन्दी क्षि";
        TextLayout layout = service.layout(text, indicStyle, 8, policy(TextPolicy.Direction.LTR, TextPolicy.Wrap.EMERGENCY, "hi")); facts(layout);
        BreakIterator graphemes = BreakIterator.getCharacterInstance(ULocale.forLanguageTag("hi")); graphemes.setText(text);
        boolean overwide = false;
        for (TextLayout.Line line : layout.lines()) {
            require(graphemes.isBoundary(line.logicalStart) && graphemes.isBoundary(line.logicalEnd), "Indic断点进入字素");
            overwide |= line.advance > 8;
        }
        require(overwide && layout.diagnostics().isEmpty(), "Indic超宽单簇被拆开或没有真实匹配");
        String thaiText = "ภาษาไทยภาษาไทยภาษาไทย";
        TextLayout thaiLayout = service.layout(thaiText, new TextStyle("explicit", 24, -1, Collections.singletonList(thai)), 85,
            policy(TextPolicy.Direction.LTR, TextPolicy.Wrap.NORMAL, "th")); facts(thaiLayout);
        BreakIterator dictionary = BreakIterator.getLineInstance(ULocale.forLanguageTag("th")); dictionary.setText(thaiText);
        require(thaiLayout.lines().size() > 1, "Thai字典没有断行");
        for (TextLayout.Line line : thaiLayout.lines()) require(dictionary.isBoundary(line.logicalEnd), "Thai断点不在ICU字典边界");
        System.out.println("PASS Indic 簇完整与超宽保留；Thai dictionary lines=" + thaiLayout.lines().size());
    }
    private static void emoji(FontTextService service, TextStyle style, FontSource emoji) throws Exception {
        String[] sequences = {"😀", "👩🏽‍💻", "🇨🇳", "1️⃣", "☀️"};
        for (String sequence : sequences) {
            TextLayout layout = service.layout(sequence, style, 1, policy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und")); facts(layout);
            List<TextLayout.Glyph> glyphs = glyphs(layout);
            require(layout.lines().size() == 1 && glyphs.size() == 1, "emoji完整序列被拆开: " + sequence);
            TextLayout.Glyph g = glyphs.get(0);
            require(g.source.equals(emoji) && g.color && !g.replacement, "Unifont抢emoji或非实际彩色序列: " + sequence);
            try (NativeFontService nativeService = new NativeFontService(); NativeFontFace face = g.source.openFace(nativeService, g.size)) {
                GlyphBitmap bitmap = face.rasterize(g.glyphId);
                require(bitmap.color && bitmap.width > 0 && bitmap.height > 0, "plan的实际glyph身份不能生成彩色像素");
                NativeFontFace.InkBounds bounds = g.inkBounds;
                require(bounds.known && bounds.left <= bitmap.bearingX + .001f && bounds.top <= -bitmap.bearingY + .001f
                    && bounds.right + .001f >= bitmap.bearingX + bitmap.width * bitmap.pixelScale
                    && bounds.bottom + .001f >= -bitmap.bearingY + bitmap.height * bitmap.pixelScale,
                    "实际emoji墨迹边界没有包含同identity光栅像素");
            }
        }
        for (String text : Arrays.asList("☀", "☀︎", "😀︎")) {
            TextLayout layout = service.layout(text, style, 1000, TextPolicy.SINGLE_LINE);
            for (TextLayout.Glyph g : glyphs(layout)) require(!g.color, "VS15/默认text错误输出彩色");
        }
        System.out.println("PASS emoji default/VS15/VS16/ZWJ/modifier/flag/keycap actualFT color");
    }
    private static void emojiVersions(FontTextService service, TextStyle style) throws Exception {
        require(service.emojiVersion().equals("17.0"), "正式Emoji版本未锁定17.0");
        for (int cp : new int[] {0x1f6d8, 0x1fa8a, 0x1fa8e, 0x1fac8, 0x1facd, 0x1faea, 0x1faef}) {
            String sequence = new String(Character.toChars(cp));
            TextLayout layout = service.layout(sequence, style, 1, policy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und"));
            require(glyphs(layout).size() == 1 && glyphs(layout).get(0).color && !glyphs(layout).get(0).replacement,
                "Unicode17新默认emoji被ICU16属性误判: " + Integer.toHexString(cp));
        }
        int variations = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                FontTextScenario.class.getClassLoader().getResourceAsStream("assets/qz_uilib/unicode/emoji17/emoji-variation-sequences.txt"), StandardCharsets.UTF_8))) {
            for (String record; (record = reader.readLine()) != null;) {
                String body = record.split("#", 2)[0].trim(); if (body.isEmpty()) continue;
                String[] fields = body.split(";"); if (!fields[1].trim().equals("text style")) continue;
                StringBuilder text = new StringBuilder();
                for (String code : fields[0].trim().split("\\s+")) text.appendCodePoint(Integer.parseInt(code, 16));
                TextLayout layout = service.layout(text.toString(), style, 1000, TextPolicy.SINGLE_LINE);
                require(!glyphs(layout).isEmpty(), "VS15没有实际glyph");
                for (TextLayout.Glyph g : glyphs(layout)) require(!g.color && !g.replacement, "标准VS15未找到原列表内真实单色glyph: " + fields[0]);
                variations++;
            }
        }
        require(variations == 371, "没有覆盖371标准VS15序列");
        System.out.println("PASS Emoji17 七个新增默认码点；全部标准 VS15 text variations=" + variations);
    }
    private static void rgiBatch(FontTextService service, TextStyle style, String root) throws Exception {
        java.io.InputStream canonical = FontTextScenario.class.getClassLoader().getResourceAsStream("font/emoji-rgi-17.0.txt");
        if (canonical == null) throw new java.io.IOException("缺少完整RGI独立canonical test resource");
        int count = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(canonical, StandardCharsets.UTF_8))) {
        for (String record; (record = reader.readLine()) != null;) {
            String body = record.split("#", 2)[0].trim(); if (body.isEmpty()) continue;
            StringBuilder text = new StringBuilder();
            for (String cp : body.split(";")[0].trim().split("\\s+")) text.appendCodePoint(Integer.parseInt(cp, 16));
            TextLayout layout = service.layout(text.toString(), style, 1, policy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und"));
            require(layout.lines().size() == 1 && glyphs(layout).size() == 1, "规范RGI高层匹配拆分: " + body);
            TextLayout.Glyph g = glyphs(layout).get(0);
            require(g.color && !g.replacement && g.logicalStart == 0 && g.logicalEnd == text.length(), "规范RGI未正确彩色匹配/完整来源: " + body);
            count++;
        }
        }
        require(count == 3953, "官方RGI集合规模不符");
        System.out.println("PASS canonical Emoji17 RGI high-level matching/cluster/color=" + count);
    }
    private static void controls(FontTextService service, TextStyle style) {
        String text = "A\b\u001b😀\tB\r\nC\rD\u2028E\u2029F";
        TextPolicy tabs = new TextPolicy(TextPolicy.Direction.LTR, TextPolicy.Wrap.NONE, "und", 100, 0);
        TextLayout layout = service.layout(text, style, 1000, tabs); facts(layout);
        require(layout.originalText.equals(text) && layout.logicalText.equals(text), "控制可视化改写业务原文");
        require(layout.lines().size() == 5, "CRLF/CR/LS/PS换行失败");
        int markerCount = 0;
        for (TextLayout.Diagnostic d : layout.diagnostics()) if (d.kind == TextLayout.Diagnostic.Kind.CONTROL_MARKER) markerCount++;
        require(markerCount == 2, "BS/ESC没有诊断marker");
        for (TextLayout.Glyph g : glyphs(layout)) {
            if (g.logicalStart == text.indexOf('B')) require(Math.abs(g.x - 100) < .001f, "显式tabstop没有到下个制表位");
            if (g.logicalStart == text.indexOf('\b') || g.logicalStart == text.indexOf('\u001b'))
                require(g.sourceStart == g.logicalStart && g.sourceEnd == g.logicalStart + 1, "marker破坏原source映射");
        }
        byte[] bytes = "中\b😀Z".getBytes(StandardCharsets.UTF_8);
        TextLayout utf8 = service.layout(TextInput.utf8(bytes), style, 1000, TextPolicy.SINGLE_LINE); facts(utf8);
        for (TextLayout.Glyph g : glyphs(utf8)) if (g.logicalStart == 2) require(g.sourceStart == 4 && g.sourceEnd == 8, "UTF8 emoji经marker后的来源错误");
        TextLayout invalid = service.layout("X\uD800Y", style, 1000, TextPolicy.SINGLE_LINE);
        require(invalid.logicalText.equals("X�Y") && invalid.originalText.charAt(1) == '\uD800', "无效UTF16未清理/污染原文");
        require(invalid.diagnostics().get(0).kind == TextLayout.Diagnostic.Kind.INVALID_INPUT, "无效输入缺诊断");
        System.out.println("PASS controls CRLF/CR/LS/PS tabstop BS/ESC byte/UTF16来源 invalidinput");
    }
    private static void resources(FontTextService service, List<FontSource> defaults, TextStyle style, String root) throws Exception {
        FontSource alternate = FontSource.file("alternate-unifont", 7, Paths.get(root, "src/main/resources/assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf"),
            0, false, Collections.<String>emptySet());
        TextLayout first = service.layout("A", new TextStyle("explicit", 24, -1, Arrays.asList(alternate, defaults.get(0))), 1000, TextPolicy.SINGLE_LINE);
        TextLayout second = service.layout("A", new TextStyle("explicit", 24, -1, Arrays.asList(defaults.get(0), alternate)), 1000, TextPolicy.SINGLE_LINE);
        require(glyphs(first).get(0).source.equals(alternate) && glyphs(second).get(0).source.equals(defaults.get(0)), "candidate reorder污染匹配");
        TextLayout old = service.layout("A😀", style, 1000, TextPolicy.SINGLE_LINE);
        TextLayout recolored = service.layout("A😀", style.withColor(0x12345678), 1000, TextPolicy.SINGLE_LINE);
        require(old.lines().get(0) == recolored.lines().get(0), "颜色改变重排glyph");
        final int[] notices = {0}; AutoCloseable subscription = service.onInvalidated(() -> notices[0]++);
        service.reload(Collections.singletonList(alternate));
        TextLayout changed = service.layout("A", style, 1000, TextPolicy.SINGLE_LINE);
        require(changed.generation > old.generation && glyphs(changed).get(0).source.equals(alternate) && notices[0] == 1, "资源换代/默认列表/通知失效失败");
        require(glyphs(old).get(0).source.equals(defaults.get(0)), "reload改写旧plan source");
        final TextLayout.Glyph saved = glyphs(old).get(0);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try (NativeFontService scope = new NativeFontService(); NativeFontFace face = saved.source.openFace(scope, saved.size)) {
                require(face.rasterize(saved.glyphId).width > 0, "旧plan无法在新native线程生成同一glyph");
            } catch (Throwable error) { failure.set(error); }
        }); worker.start(); worker.join(); if (failure.get() != null) throw new AssertionError(failure.get());
        FontSource damaged = new FontSource("broken", 1, "scenario bytes", new byte[] {1,2,3,4}, 0, false, Collections.<String>emptySet());
        TextLayout bad = service.layout("A", new TextStyle("explicit", 24, -1, Arrays.asList(damaged, alternate)), 1000, TextPolicy.SINGLE_LINE);
        require(glyphs(bad).get(0).source.equals(alternate), "损坏字体参与匹配");
        boolean diagnosed = false;
        for (TextLayout.Diagnostic d : bad.diagnostics()) diagnosed |= d.kind == TextLayout.Diagnostic.Kind.FONT_RESOURCE;
        require(diagnosed, "损坏字体资源缺入口诊断");
        subscription.close(); service.reload(defaults);
        for (int size = 8; size < 40; size++) service.layout("cache " + size, new TextStyle("default", size, -1), 1000, TextPolicy.SINGLE_LINE);
        require(service.cachedFaceCount() <= 4 && service.cachedLayoutCount() <= 8, "face/layout cache无界");
        System.out.println("PASS candidate reorder/颜色复用/资源换代旧plan/独立线程重开/损坏资源/cache bounds faces="
            + service.cachedFaceCount() + " layouts=" + service.cachedLayoutCount());
    }
    /** 真实评审发现：WJ/GL禁止边界、Common tatweel连接范围与长文冷排版。 */
    private static void reviewRegressions(FontTextService service, List<FontSource> defaults, TextStyle style) throws Exception {
        TextPolicy emergency = policy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und");
        for (String text : Arrays.asList("A\u2060B", "A\ufeffB", "A\u00a0B", "A\u202fB", "A\u2011B")) {
            TextLayout layout = service.layout(text, style, 13, emergency); facts(layout);
            require(layout.lines().size() == 1 && layout.lines().get(0).text.equals(text), "EMERGENCY绕过明确禁止断点: " + text);
        }
        for (String text : Arrays.asList("A \u00a0B", "A-\u00a0B")) {
            TextLayout layout = service.layout(text, style, 24, TextPolicy.NORMAL); facts(layout);
            require(layout.lines().size() == 2 && layout.lines().get(0).logicalEnd == 2
                && layout.lines().get(0).advance == 24 && layout.lines().get(1).advance == 24,
                "GL前SP/HY的LB12a合法NORMAL断点被错误禁用");
        }
        require(service.layout("A \u2060B", style, 24, TextPolicy.NORMAL).lines().size() == 1,
            "WJ前空格错误套用GL的LB12a例外");
        for (String text : Arrays.asList("بـب", "بــب")) {
            TextLayout layout = service.layout(text, style, 1000, TextPolicy.SINGLE_LINE); facts(layout);
            List<TextLayout.Glyph> placed = glyphs(layout);
            try (NativeFontService scope = new NativeFontService(); NativeFontFace face = defaults.get(1).openFace(scope, style.size)) {
                List<NativeFontFace.Glyph> reference = face.shape(text, 0, text.length(), NativeFontFace.Direction.RTL, "und", "Arab", true, true);
                require(reference.size() == placed.size(), "连接范围glyph数不一致"); float advance = 0;
                for (int i = 0; i < reference.size(); i++) {
                    require(placed.get(i).source.equals(defaults.get(1)) && placed.get(i).glyphId == reference.get(i).glyphId,
                        "Common tatweel拆入另一候选破坏连接: " + text);
                    advance += reference.get(i).xAdvance;
                }
                require(Math.abs(layout.advanceWidth - advance) < .001f, "tatweel连接的实际advance不一致");
                System.out.println("PASS Arabic Common/Script_Extensions joining range " + text + " advance=" + advance);
            }
        }
        FontSource broken = new FontSource("broken-review-id", 1, "review:broken-resource", new byte[] {1,2,3,4}, 0, false, Collections.<String>emptySet());
        try {
            service.layout("A", new TextStyle("explicit", 24, -1, Collections.singletonList(broken)), 1000, TextPolicy.SINGLE_LINE);
            throw new AssertionError("全损坏候选没有资源错误");
        } catch (IllegalStateException error) {
            require(error.getMessage().contains("broken-review-id") && error.getMessage().contains("review:broken-resource")
                && error.getMessage().contains("FreeType"), "最终资源错误丢失身份/origin/原生原因: " + error);
        }
        // 每个输入只调用一次，均为layout cache miss；记录耗时，不设置机器相关硬阈值。
        for (int count : new int[] {512, 1024, 2048, 4096, 8192}) {
            char[] chars = new char[count]; Arrays.fill(chars, 'a'); String text = new String(chars);
            long begin = System.nanoTime(); TextLayout layout = service.layout(text, style, 24, emergency);
            double milliseconds = (System.nanoTime() - begin) / 1000000d; facts(layout);
            require(layout.lines().size() == count / 2 && glyphs(layout).size() == count, "长文局部断行丢字/行数错误");
            System.out.println("PASS cold-layout UTF16=" + count + " lines=" + layout.lines().size() + " ms=" + milliseconds);
        }
        require(!service.layout("a\u00ad", style, 13, emergency).lines().isEmpty(), "段落尾软连字符使断点循环不前进");
        System.out.println("PASS WJ/GL prohibited boundaries; resource failure identity/origin/native cause");
    }
    public static void main(String[] args) throws Exception {
        String root = args.length == 0 ? "." : args[0];
        List<FontSource> defaults = FontTextService.defaultSources(FontTextScenario.class.getClassLoader(), 1);
        for (FontSource source : defaults) require(!source.isLoaded(), "默认候选构造时预加载全部字体");
        TextStyle style = new TextStyle("default", 24, -1);
        try (FontTextService service = new FontTextService(defaults, 4, 8, 4096)) {
            bidi(service, style); arabic(service, defaults.get(1)); indicThai(service, defaults.get(2), defaults.get(3));
            emoji(service, style, defaults.get(4)); emojiVersions(service, style); rgiBatch(service, style, root);
            controls(service, style); resources(service, defaults, style, root);
            reviewRegressions(service, defaults, style);
        }
        System.out.println("FontTextScenario PASS (ICU76.1 realFT/HB Java8); 未完成: 省略/软连字符可见断点/系统字体能力自动探针/编辑器affinity/性能与GPU验收");
    }
}
