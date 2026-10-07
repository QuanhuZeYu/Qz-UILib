package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BidiRun;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.UnicodeText.Unit;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontService;

/**
 * ICU Unicode 算法 + 真实 FT/HB 共享排版服务。创建、测量、重载、关闭均在创建线程。
 * 度量不申请 GPU 资源；光栅消费者用 Glyph.source.openFace 在自己的 native 线程重开。
 * 有界 face/layout/color-probe 缓存。默认资源只是显式候选列表，不建立第二条 fallback 链。
 * 当前未实现省略、采用软连字符断点时的可见连字符和编辑器双向光标 affinity；
 * 软连字符断点暂禁用并诊断，不能把无连字符的结果冒充该合同。
 */
public final class FontTextService implements SceneTextMeasurer, AutoCloseable {
    private final Thread thread = Thread.currentThread();
    private final NativeFontService nativeService;
    private EmojiData emojiData;
    private final int faceLimit, layoutLimit, cachedTextLimit;
    private final LinkedHashMap<FaceKey, NativeFontFace> faces = new LinkedHashMap<FaceKey, NativeFontFace>(16, .75f, true);
    private final LinkedHashMap<FaceKey, String> failures = new LinkedHashMap<FaceKey, String>(16, .75f, true);
    private final LinkedHashMap<ColorKey, Boolean> colors = new LinkedHashMap<ColorKey, Boolean>(16, .75f, true);
    private final LinkedHashMap<InkKey, NativeFontFace.InkBounds> ink = new LinkedHashMap<InkKey, NativeFontFace.InkBounds>(16, .75f, true);
    private final LinkedHashMap<LayoutKey, TextLayout> layouts = new LinkedHashMap<LayoutKey, TextLayout>(16, .75f, true);
    private final Map<String, List<FontSource>> named = new HashMap<String, List<FontSource>>();
    private final List<Runnable> invalidation = new ArrayList<Runnable>();
    private List<FontSource> defaults;
    private long generation = 1;
    private boolean closed;
    public FontTextService() throws IOException { this(defaultSources(FontTextService.class.getClassLoader(), 1)); }
    public FontTextService(List<FontSource> defaults) { this(defaults, 16, 128, 8192); }
    public FontTextService(List<FontSource> defaults, int faceLimit, int layoutLimit, int cachedTextLimit) {
        if (faceLimit < 1 || layoutLimit < 0 || cachedTextLimit < 0) throw new IllegalArgumentException("无效字体缓存预算");
        this.faceLimit = faceLimit; this.layoutLimit = layoutLimit; this.cachedTextLimit = cachedTextLimit;
        this.defaults = copy(defaults);
        try { emojiData = EmojiData.bundled(FontTextService.class.getClassLoader()); }
        catch (IOException error) { throw new IllegalArgumentException("Emoji资源无法载入", error); }
        nativeService = new NativeFontService();
    }
    /** 当前默认资源集合；完整脚本/RGI版本覆盖仍由资源清单和覆盖探针验收。 */
    public static List<FontSource> defaultSources(ClassLoader loader, long revision) throws IOException {
        String base = "assets/qz_uilib/fonts/";
        return Collections.unmodifiableList(Arrays.asList(
            FontSource.resource("unifont", revision, loader, base + "unifont/unifont-16.0.04.otf", false, Collections.<String>emptySet()),
            FontSource.resource("noto-arabic", revision, loader, base + "noto/NotoSansArabic.ttf", false, set("Arab")),
            FontSource.resource("noto-devanagari", revision, loader, base + "noto/NotoSansDevanagari.ttf", false, set("Deva")),
            FontSource.resource("noto-thai", revision, loader, base + "noto/NotoSansThai.ttf", false, set("Thai")),
            FontSource.resource("noto-color-emoji", revision, loader, base + "noto/NotoColorEmoji.ttf", true, Collections.<String>emptySet()),
            FontSource.resource("noto-emoji-text", revision, loader, base + "noto/NotoEmoji.ttf", false, Collections.<String>emptySet()),
            FontSource.resource("noto-bengali", revision, loader, base + "noto/NotoSansBengali.ttf", false, set("Beng")),
            FontSource.resource("noto-gurmukhi", revision, loader, base + "noto/NotoSansGurmukhi.ttf", false, set("Guru")),
            FontSource.resource("noto-gujarati", revision, loader, base + "noto/NotoSansGujarati.ttf", false, set("Gujr")),
            FontSource.resource("noto-oriya", revision, loader, base + "noto/NotoSansOriya.ttf", false, set("Orya")),
            FontSource.resource("noto-tamil", revision, loader, base + "noto/NotoSansTamil.ttf", false, set("Taml")),
            FontSource.resource("noto-telugu", revision, loader, base + "noto/NotoSansTelugu.ttf", false, set("Telu")),
            FontSource.resource("noto-kannada", revision, loader, base + "noto/NotoSansKannada.ttf", false, set("Knda")),
            FontSource.resource("noto-malayalam", revision, loader, base + "noto/NotoSansMalayalam.ttf", false, set("Mlym")),
            FontSource.resource("noto-sinhala", revision, loader, base + "noto/NotoSansSinhala.ttf", false, set("Sinh"))));
    }
    private static Set<String> set(String value) { return Collections.singleton(value); }
    private static List<FontSource> copy(List<FontSource> sources) {
        ArrayList<FontSource> result = new ArrayList<FontSource>(Objects.requireNonNull(sources, "sources"));
        for (FontSource s : result) Objects.requireNonNull(s, "source");
        return Collections.unmodifiableList(result);
    }
    private void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("FontTextService belongs to its creating thread");
        if (closed) throw new IllegalStateException("FontTextService is closed");
    }
    public long generation() { check(); return generation; }
    public String emojiVersion() { check(); return emojiData.version; }
    public void reloadEmojiData(EmojiData data) { check(); emojiData = Objects.requireNonNull(data, "data"); invalidate(); }
    public int cachedFaceCount() { check(); return faces.size(); }
    public int cachedLayoutCount() { check(); return layouts.size(); }
    public List<FontSource> defaultCandidates() { check(); return defaults; }
    /** 宿主将这个变化通知接入其正常 measure/layout 失效，不在 replay 改计划。 */
    public AutoCloseable onInvalidated(final Runnable listener) {
        check(); invalidation.add(Objects.requireNonNull(listener, "listener"));
        return new AutoCloseable() { public void close() { check(); invalidation.remove(listener); } };
    }
    public void register(String name, List<FontSource> candidates) {
        check(); named.put(Objects.requireNonNull(name, "name"), copy(candidates)); invalidate();
    }
    /** 默认名称 default/minecraft 随资源列表换代；显式注册列表由提供者另行换代。 */
    public void reload(List<FontSource> candidates) { check(); defaults = copy(candidates); invalidate(); }
    private void invalidate() {
        generation++; layouts.clear(); colors.clear(); ink.clear(); failures.clear();
        for (NativeFontFace face : faces.values()) face.close(); faces.clear();
        for (Runnable listener : new ArrayList<Runnable>(invalidation)) listener.run();
    }
    private List<FontSource> candidates(TextStyle style) {
        if (style.candidates() != null) return style.candidates();
        List<FontSource> result = named.get(style.font);
        // 旧任意名称均映射配置的默认列表，没有基于名称偷偷添加字体。
        return result == null ? defaults : result;
    }
    @Override public TextLayout layout(String text, TextStyle style, int width) { return layout(text, style, width, TextPolicy.NORMAL); }
    @Override public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
        check(); Objects.requireNonNull(text, "text"); Objects.requireNonNull(style, "style"); Objects.requireNonNull(policy, "policy");
        if(!style.effects.equals(TextEffects.NORMAL)||!style.decoration.equals(TextDecoration.NONE)) return layout(new AttributedText(TextInput.string(text),text,style,text.isEmpty()?Collections.<StyleSpan>emptyList():Collections.singletonList(new StyleSpan(0,text.length(),style))),width,policy);
        List<FontSource> sources = candidates(style);
        LayoutKey key = new LayoutKey(text, sources, style.logicalEm, width, policy);
        TextLayout cached = layouts.get(key);
        if (cached != null) return rebindResolvedStyle(cached,style,key.sources);
        TextLayout result = build(TextInput.string(text), text, style, width, policy, sources);
        if (layoutLimit > 0 && text.length() <= cachedTextLimit) { layouts.put(key, result); trim(layouts, layoutLimit); }
        return result;
    }
    /** 仅plain缓存命中可调用：完整LayoutKey已证明解析后的有序候选及em相等。公开withStyle仍严格检查原selector。 */
    private TextLayout rebindResolvedStyle(TextLayout cached,TextStyle style,List<FontSource> resolvedSources) {
        if(!candidates(cached.style).equals(resolvedSources)||!candidates(style).equals(resolvedSources)||cached.style.logicalEm!=style.logicalEm
            ||!cached.style.effects.equals(style.effects)||!cached.style.decoration.equals(style.decoration))throw new IllegalArgumentException("缓存样式没有等价的实际字体身份");
        if(cached.style.sameFont(style))return cached.withStyle(style);
        List<TextLayout.Line> lines=new ArrayList<TextLayout.Line>();
        for(TextLayout.Line line:cached.lines()){
            List<TextLayout.Glyph> glyphs=new ArrayList<TextLayout.Glyph>();List<TextLayout.Cluster> clusters=new ArrayList<TextLayout.Cluster>();List<TextLayout.Decoration> decorations=new ArrayList<TextLayout.Decoration>();
            for(TextLayout.Glyph g:line.glyphs())glyphs.add(new TextLayout.Glyph(g.source,g.size,g.glyphId,g.logicalStart,g.logicalEnd,g.sourceStart,g.sourceEnd,g.x,g.y,g.xAdvance,g.yAdvance,g.color,g.replacement,g.inkBounds,g.logicalEm,g.rasterToLayout,g.rasterStyle,style,g.clusterIndex));
            for(TextLayout.Cluster c:line.clusters())clusters.add(new TextLayout.Cluster(c.logicalStart,c.logicalEnd,c.sourceStart,c.sourceEnd,c.glyphIndexStart,c.glyphIndexEnd,c.x,c.advance,style));
            for(TextLayout.Decoration d:line.decorations())decorations.add(new TextLayout.Decoration(d.kind,d.x,d.y,d.width,d.height,style.color,d.logicalStart,d.logicalEnd,d.sourceStart,d.sourceEnd));
            lines.add(new TextLayout.Line(line.text,line.x,line.y,line.logicalStart,line.logicalEnd,line.sourceStart,line.sourceEnd,line.advance,line.baseline,line.ascent,line.descent,line.lineHeight,glyphs,clusters,decorations));
        }
        return new TextLayout(cached.width,cached.height,cached.advanceWidth,cached.advanceHeight,style,lines,cached.logicalText,cached.originalText,cached.generation,cached.diagnostics(),cached.input);
    }
    /** 输入对象携带 byte/UTF16 来源，因此不与 String cache 混用。 */
    public TextLayout layout(TextInput text, TextStyle style, int width, TextPolicy policy) {
        check(); Objects.requireNonNull(style, "style"); Objects.requireNonNull(policy, "policy");
        return layout(AttributedText.plain(Objects.requireNonNull(text,"text"),style),width,policy);
    }
    private NativeFontFace face(FontSource source, float size, UnicodeText doc) {
        FaceKey key = new FaceKey(source, size);
        NativeFontFace found = faces.get(key); if (found != null) return found;
        String failure = failures.get(key);
        if (failure == null) {
            try {
                found = source.openFace(nativeService, size);
                faces.put(key, found);
                while (faces.size() > faceLimit) {
                    FaceKey oldest = faces.keySet().iterator().next(); faces.remove(oldest).close();
                }
                return found;
            } catch (IllegalStateException | IllegalArgumentException error) {
                failure = error.toString(); failures.put(key, failure); trim(failures, faceLimit);
            }
        }
        String message = source + " (" + source.origin + "): " + failure;
        for (Diagnostic d : doc.diagnostics) if (d.kind == Diagnostic.Kind.FONT_RESOURCE && d.message.equals(message)) return null;
        doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.FONT_RESOURCE, 0, 0, message)); return null;
    }
    private boolean color(FontSource source, float size, int glyph, NativeFontFace face) {
        ColorKey key = new ColorKey(source, size, glyph); Boolean found = colors.get(key);
        if (found == null) {
            GlyphBitmap bitmap = face.rasterize(glyph);
            found = false;
            if (bitmap.color && bitmap.width > 0 && bitmap.height > 0) {
                byte[] pixels = bitmap.pixels();
                for (int i = 3; i < pixels.length; i += 4) if (pixels[i] != 0) { found = true; break; }
            }
            colors.put(key, found); trim(colors, 1024);
        }
        return found;
    }
    /** 仅加载真实glyph metrics/bitmap，不执行FT_Render_Glyph。实际identity与光栅请求完全相同。 */
    private NativeFontFace.InkBounds inkBounds(FontSource source, float size, int glyph, GlyphRasterStyle variant, UnicodeText doc) {
        InkKey key = new InkKey(source, size, glyph,variant); NativeFontFace.InkBounds result = ink.get(key);
        if (result == null) {
            NativeFontFace face = face(source, size, doc);
            if (face == null) throw new IllegalStateException("已匹配字体无法取得真实墨迹边界");
            result = face.glyphInkBounds(glyph,variant); ink.put(key, result); trim(ink, 1024);
        }
        return result;
    }
    private static <K,V> void trim(LinkedHashMap<K,V> cache, int limit) {
        while (cache.size() > limit) cache.remove(cache.keySet().iterator().next());
    }
    private void match(UnicodeText doc, List<Unit> units, Bidi bidi, int paragraphStart, TextStyle style,
            TextPolicy policy, List<FontSource> sources) {
        Map<MatchKey, FontSource> independent = new HashMap<MatchKey, FontSource>();
        for (int i = 0; i < units.size();) {
            Unit first = units.get(i); int j = i + 1;
            if (UnicodeText.complex(first.script)) {
                while (j < units.size() && units.get(j).script == first.script && !units.get(j).emoji
                    && bidi.getLevelAt(units.get(j).start - paragraphStart) == bidi.getLevelAt(first.start - paragraphStart)) j++;
            }
            int start = first.start, end = units.get(j - 1).end;
            TextStyle leading=first.shapeStyle;
            for(int k=i;k<j;k++){Unit unit=units.get(k);if(!unit.shapeStyle.sameFont(leading)){
                doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.STYLE_COALESCED,doc.source(start),doc.sourceEnd(end),"必要连接范围字体/字号统一使用逻辑首样式"));
                unit.shapeStyle=leading;
            }}
            style=leading;sources=candidates(leading);
            if (!UnicodeText.visible(doc.display, start, end) || doc.display.charAt(start) == '	') { i = j; continue; }
            NativeFontFace.Direction direction = (bidi.getLevelAt(start - paragraphStart) & 1) == 0
                ? NativeFontFace.Direction.LTR : NativeFontFace.Direction.RTL;
            // 独立字素的匹配没有跨字素连接上下文，当前layout内相同字素复用候选结果。
            String matchText = UnicodeText.complex(first.script) ? null : doc.display.substring(start, end);
            MatchKey matchKey = matchText==null?null:new MatchKey(sources,style.logicalEm,first.script,first.emoji,direction,matchText);
            FontSource selected = matchKey == null ? null : independent.get(matchKey);
            if (selected != null) { for (int k = i; k < j; k++) units.get(k).source = selected; i = j; continue; }
            for (FontSource candidate : sources) {
                if (UnicodeText.complex(first.script) && !candidate.supportsScript(UScript.getShortName(first.script))) continue;
                if (first.emoji && !candidate.color) continue;
                NativeFontFace f = face(candidate, style.logicalEm, doc); if (f == null) continue;
                String context = matchText == null ? doc.display : matchText;
                int from = matchText == null ? start : 0, limit = matchText == null ? end : matchText.length();
                List<NativeFontFace.Glyph> shaped = f.shape(context, from, limit, direction, policy.language,
                    UScript.getShortName(first.script), matchText != null, matchText != null);
                boolean ok = !shaped.isEmpty(); int visible = 0;
                TreeSet<Integer> clusters = new TreeSet<Integer>(); clusters.add(limit);
                for (NativeFontFace.Glyph g : shaped) clusters.add(g.cluster);
                for (NativeFontFace.Glyph g : shaped) {
                    if (g.cluster >= limit || !UnicodeText.visible(context, g.cluster, clusters.higher(g.cluster))) continue;
                    visible++;
                    if (g.glyphId == 0) { ok = false; break; }
                    // Emoji 默认/VS16必须实际彩色；VS15或默认文字必须不能输出彩色 bitmap。
                    if ((first.emoji || first.emojiLike || candidate.color) && color(candidate, style.logicalEm, g.glyphId, f) != first.emoji) { ok = false; break; }
                }
                // 一个 emoji 字素必须形成一个真实彩色序列，不把散开的码点宣称正确生成。
                if (first.emoji && visible != 1) ok = false;
                if (ok) { selected = candidate; if (matchKey != null) independent.put(matchKey, selected); break; }
            }
            if (selected == null) {
                doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.MISSING_GLYPH, doc.source(start), doc.source(end),
                    "候选列表无法正确生成 " + UScript.getShortName(first.script) + " 范围；使用列表内稳定替代"));
                int replacement = 0;
                for (FontSource candidate : sources) {
                    NativeFontFace f = face(candidate, style.logicalEm, doc); if (f == null) continue;
                    int id = f.glyphIndex(0xfffd);
                    if (id != 0 && !color(candidate, style.logicalEm, id, f)) { selected = candidate; replacement = id; break; }
                }
                if (selected == null) for (FontSource candidate : sources) {
                    NativeFontFace f = face(candidate, style.logicalEm, doc); if (f != null && !color(candidate, style.logicalEm, 0, f)) { selected = candidate; break; }
                }
                if (selected == null) {
                    StringBuilder failure = new StringBuilder("候选列表无有效文字字体，不能生成稳定替代: ").append(sources);
                    for (Diagnostic diagnostic : doc.diagnostics) failure.append("\n").append(diagnostic.kind).append(": ").append(diagnostic.message);
                    throw new IllegalStateException(failure.toString());
                }
                for (int k = i; k < j; k++) { units.get(k).replacement = true; units.get(k).replacementId = replacement; }
            }
            for (int k = i; k < j; k++) units.get(k).source = selected;
            i = j;
        }
    }
    private TextLayout build(TextInput input, String original, TextStyle style, int width, TextPolicy policy, List<FontSource> sources) {
        if (style.logicalEm > 32767) throw new IllegalArgumentException("native em 字号上限为32767");
        return build(AttributedText.plain(input,style),original,width,policy);
    }
    @Override public TextLayout layout(AttributedText text,float width,TextPolicy policy) {
        check();Objects.requireNonNull(text,"text");Objects.requireNonNull(policy,"policy");
        if(!Float.isFinite(width))throw new IllegalArgumentException("无效布局宽度");
        for(StyleSpan span:text.spans())if(span.style.effects.obfuscated)throw new UnsupportedOperationException("混淆动画不属于当前属性布局切片");
        if(text.baseStyle.effects.obfuscated)throw new UnsupportedOperationException("混淆动画尚未实现");
        return build(text,text.originalText,width,policy);
    }
    private TextLayout build(AttributedText attributed,String original,float width,TextPolicy policy) {
        TextInput input=attributed.input;TextStyle style=attributed.baseStyle;List<FontSource> sources=candidates(style);
        UnicodeText doc = new UnicodeText(input);doc.diagnostics.addAll(attributed.diagnostics()); List<TextLayout.Line> lines = new ArrayList<TextLayout.Line>();
        float y = 0, maxWidth = 0;
        for (int start = 0; start <= doc.display.length();) {
            int newline = doc.display.indexOf('\n', start), end = newline < 0 ? doc.display.length() : newline;
            // 空段也有活动逻辑样式，末段使用显式trailing状态，不能继承整个输入的base度量。
            style=attributed.styleAt(doc.logical[start]);sources=candidates(style);
            String paragraph = doc.display.substring(start, end); Bidi bidi = new Bidi();
            byte level = policy.direction == TextPolicy.Direction.AUTO ? Bidi.LEVEL_DEFAULT_LTR
                : (byte) (policy.direction == TextPolicy.Direction.LTR ? 0 : 1);
            bidi.setPara(paragraph, level, null);
            List<Unit> units = doc.units(start, end, policy.language, emojiData);
            int spanCursor=0;List<StyleSpan> requested=attributed.spans();
            while(spanCursor<requested.size()&&requested.get(spanCursor).logicalEnd<=doc.logical[start])spanCursor++;
            for(Unit unit:units){unit.style=attributed.styleAt(doc.logical[unit.start]);unit.shapeStyle=unit.style;
                while(spanCursor<requested.size()&&requested.get(spanCursor).logicalEnd<=doc.logical[unit.start])spanCursor++;
                for(int k=spanCursor+1;k<requested.size()&&requested.get(k).logicalStart<doc.logical[unit.end];k++)if(!requested.get(k).style.equals(unit.style)){
                    doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.STYLE_COALESCED,doc.source(unit.start),doc.sourceEnd(unit.end),"字素内部样式统一使用逻辑首样式"));break;
                }
            }
            match(doc, units, bidi, start, style, policy, sources);
            TreeSet<Integer> safe = new TreeSet<Integer>(); safe.add(start); safe.add(end);
            for (Unit unit : units) { safe.add(unit.start); safe.add(unit.end); }
            UnitIndex index = new UnitIndex(units);
            Draft whole = shape(doc, index, bidi, start, start, end, style, policy, sources);
            // 排除 ICU 字素内部以及 HB 已整形 cluster 内部；超宽单簇仍完整。
            for (Placed glyph : whole.glyphs) safe.subSet(glyph.start, false, glyph.end, false).clear();
            for (java.util.Iterator<Integer> boundaries = safe.iterator(); boundaries.hasNext();) {
                int boundary = boundaries.next();
                if (boundary > start && boundary < end && UnicodeText.prohibitsBreak(doc.display, boundary)) boundaries.remove();
            }
            BreakIterator breaks = BreakIterator.getLineInstance(ULocale.forLanguageTag(policy.language)); breaks.setText(paragraph);
            TreeSet<Integer> normal = new TreeSet<Integer>();
            for (int b = breaks.first(); b != BreakIterator.DONE; b = breaks.next()) if (safe.contains(start + b)) normal.add(start + b);
            for (int p = start; p < end; p++) if (doc.display.charAt(p) == 0x00ad) {
                normal.remove(p + 1); safe.remove(p + 1);
                doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.UNSUPPORTED, doc.source(p), doc.source(p + 1),
                    "当前未实现软连字符断点的可见连字符，该断点不采用"));
            }
            // 段落终点始终结束本行；例如末尾SHY并不是需要显示连字符的自动断点。
            safe.add(end); normal.add(end);
            // 段落只整形一次建立前缀；每行只按最终候选范围重整形，不再shape剩余整段。
            BreakPlan plan = new BreakPlan(policy.wrap == TextPolicy.Wrap.EMERGENCY ? safe : normal, whole.prefix(start, end), start);
            int a = start;
            do {
                int first = a < end ? plan.firstAfter(a) : end;
                int b = policy.wrap == TextPolicy.Wrap.NONE || a == end ? end : plan.estimate(a, Math.max(0, width));
                Draft chosen = a == start && b == end ? whole : shape(doc, index, bidi, start, a, b, style, policy, sources);
                if (policy.wrap != TextPolicy.Wrap.NONE && a < end) {
                    while (chosen.width > Math.max(0, width) && b > first) {
                        b = plan.before(b); chosen = shape(doc, index, bidi, start, a, b, style, policy, sources);
                    }
                    // Arabic行端连接形和tabstop可改变局部宽度；只校正相邻断点，保留单个超宽安全范围。
                    while (chosen.width <= Math.max(0, width) && b < end) {
                        int next = plan.firstAfter(b);
                        Draft expanded = shape(doc, index, bidi, start, a, next, style, policy, sources);
                        if (expanded.width > Math.max(0, width)) break;
                        b = next; chosen = expanded;
                    }
                }
                float h = policy.lineHeight > 0 ? policy.lineHeight : Math.max(chosen.ascent + chosen.descent, chosen.metricHeight);
                if (h <= 0) h = style.logicalEm;
                float baseline = y + chosen.ascent;
                List<TextLayout.Glyph> glyphs = new ArrayList<TextLayout.Glyph>();
                List<TextLayout.Cluster> cells=new ArrayList<TextLayout.Cluster>();List<TextLayout.Decoration> decorations=new ArrayList<TextLayout.Decoration>();
                Map<Cell,Integer> cellIds=new java.util.IdentityHashMap<Cell,Integer>();
                for(Cell c:chosen.cells){int id=cells.size();cellIds.put(c,id);
                    int ls=doc.logical[c.start],le=doc.logical[c.end],ss=doc.source(c.start),se=doc.sourceEnd(c.end);
                    cells.add(new TextLayout.Cluster(ls,le,ss,se,c.glyphStart,c.glyphEnd,c.x,c.advance,c.style));
                    TextDecoration d=c.style.decoration;
                    if(c.style.effects.underline&&d.underlineThickness>0)decorations.add(new TextLayout.Decoration(TextLayout.Decoration.Kind.UNDERLINE,c.x+d.underlineStartOffset,baseline+d.underlineTop,c.advance-d.underlineStartOffset,d.underlineThickness,c.style.color,ls,le,ss,se));
                    if(c.style.effects.strike&&d.strikeThickness>0)decorations.add(new TextLayout.Decoration(TextLayout.Decoration.Kind.STRIKE,c.x,baseline+d.strikeTop,c.advance,d.strikeThickness,c.style.color,ls,le,ss,se));
                }
                for (Placed g : chosen.glyphs) {TextStyle paint=g.cell.style;GlyphRasterStyle variant=variant(paint,1);
                    glyphs.add(new TextLayout.Glyph(g.source,paint.logicalEm,g.id,
                    doc.logical[g.cell.start],doc.logical[g.cell.end],doc.source(g.cell.start),doc.sourceEnd(g.cell.end),
                    g.x,baseline+g.y,g.advance,g.yAdvance,g.color,g.replacement,
                    inkBounds(g.source,paint.logicalEm,g.id,variant,doc),paint.logicalEm,1,variant,paint,cellIds.get(g.cell)));
                }
                lines.add(new TextLayout.Line(input.text.substring(doc.logical[a], doc.logical[b]), 0, (int) Math.floor(y),
                    doc.logical[a], doc.logical[b],input.sourceLeft(doc.logical[a]),b==end?input.sourceRight(doc.logical[b]):input.sourceLeft(doc.logical[b]), chosen.width,baseline,
                    chosen.ascent,chosen.descent,h,glyphs,cells,decorations));
                maxWidth = Math.max(maxWidth, chosen.width); y += h; a = b;
            } while (a < end);
            if (newline < 0) break; start = end + 1;
        }
        return new TextLayout(ceil(maxWidth), ceil(y), maxWidth, y, attributed.baseStyle, lines, input.text, original, generation, doc.diagnostics,input);
    }
    private static GlyphRasterStyle variant(TextStyle style,float ratio){return new GlyphRasterStyle(style.effects.boldAdvance/ratio,style.effects.italicShear);}
    /** 不shape、不render，同glyphId实际采样边界映回逻辑坐标。 */
    public TextLayout sample(TextLayout layout,RasterSampling sampling){
        check();Objects.requireNonNull(layout,"layout");Objects.requireNonNull(sampling,"sampling");
        UnicodeText doc=new UnicodeText(layout.input==null?TextInput.string(layout.logicalText):layout.input);List<TextLayout.Line> lines=new ArrayList<TextLayout.Line>();
        for(TextLayout.Line line:layout.lines()){List<TextLayout.Glyph> glyphs=new ArrayList<TextLayout.Glyph>();
            for(TextLayout.Glyph g:line.glyphs()){float em=g.logicalEm*sampling.pixelsPerLayoutUnit;
                if(!Float.isFinite(em)||em<=0||em>32767)throw new IllegalArgumentException("采样em超过原生范围");
                float ratio=g.logicalEm/em;TextStyle style=g.resolvedStyle==null?layout.style:g.resolvedStyle;
                GlyphRasterStyle variant=variant(style,ratio);NativeFontFace.InkBounds raw=inkBounds(g.source,em,g.glyphId,variant,doc);
                NativeFontFace.InkBounds mapped=raw.known?new NativeFontFace.InkBounds(true,raw.left*ratio,raw.top*ratio,raw.right*ratio,raw.bottom*ratio):NativeFontFace.InkBounds.UNKNOWN;
                glyphs.add(new TextLayout.Glyph(g.source,em,g.glyphId,g.logicalStart,g.logicalEnd,g.sourceStart,g.sourceEnd,g.x,g.y,g.xAdvance,g.yAdvance,g.color,g.replacement,mapped,g.logicalEm,ratio,variant,style,g.clusterIndex));
            }
            lines.add(new TextLayout.Line(line.text,line.x,line.y,line.logicalStart,line.logicalEnd,line.sourceStart,line.sourceEnd,line.advance,line.baseline,line.ascent,line.descent,line.lineHeight,glyphs,line.clusters(),line.decorations()));
        }
        List<Diagnostic> diagnostics=new ArrayList<Diagnostic>(layout.diagnostics());diagnostics.addAll(doc.diagnostics);
        return new TextLayout(layout.width,layout.height,layout.advanceWidth,layout.advanceHeight,layout.style,lines,layout.logicalText,layout.originalText,layout.generation,diagnostics,layout.input);
    }
    private static int ceil(float value) {
        if (!Float.isFinite(value) || value > Integer.MAX_VALUE) throw new IllegalArgumentException("文本布局尺寸超出 scene 范围");
        return (int) Math.ceil(Math.max(0, value));
    }
    private Draft shape(UnicodeText doc, UnitIndex units, Bidi paragraphBidi, int paragraphStart, int start, int end,
            TextStyle style, TextPolicy policy, List<FontSource> sources) {
        Draft draft = new Draft();
        if (start == end) { defaultMetrics(draft, style, doc, sources); return draft; }
        Bidi lineBidi = paragraphBidi.setLine(start - paragraphStart, end - paragraphStart);
        String context = doc.display.substring(start, end);
        TreeMap<Integer, Unit> unitStarts = units.starts;
        float pen = 0;
        for (int r = 0; r < lineBidi.countRuns(); r++) {
            BidiRun run = lineBidi.getVisualRun(r);
            int runStart = start + run.getStart(), runEnd = start + run.getLimit();
            List<Span> spans = new ArrayList<Span>();
            int firstUnit = unitStarts.floorKey(runStart);
            for (Unit u : unitStarts.subMap(firstUnit, true, runEnd, false).values()) {
                int s = Math.max(runStart, u.start), e = Math.min(runEnd, u.end);
                boolean special = doc.display.charAt(s) == '	' || !UnicodeText.visible(doc.display, s, e) || u.replacement || u.source == null;
                Span last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
                if (!special && last != null && !last.special && last.unit.source.equals(u.source) && last.unit.script == u.script && last.unit.shapeStyle.sameFont(u.shapeStyle) && last.end == s)
                    last.end = e;
                else spans.add(new Span(s, e, u, special));
            }
            if (run.isOddRun()) Collections.reverse(spans);
            for (Span span : spans) {
                Unit u = span.unit;style=u.shapeStyle;sources=candidates(style);
                int glyphBegin=draft.glyphs.size();float spanPen=pen;
                if (doc.display.charAt(span.start) == '	') {
                    float tab = policy.tabSize > 0 ? policy.tabSize : spaceAdvance(style, doc, sources, policy) * 4;
                    float advance = (float) ((Math.floor(pen / tab) + 1) * tab - pen);
                    draft.cells.add(new Cell(span.start,span.end,pen,advance,u.style,glyphBegin,glyphBegin));
                    draft.spaces.add(new Gap(span.start, span.end, advance)); pen += advance; continue;
                }
                if (!UnicodeText.visible(doc.display, span.start, span.end)) continue;
                if (u.source == null) { float advance = style.logicalEm; draft.cells.add(new Cell(span.start,span.end,pen,advance,u.style,glyphBegin,glyphBegin));
                    draft.spaces.add(new Gap(span.start, span.end, advance)); pen += advance; continue; }
                NativeFontFace f = face(u.source, style.logicalEm, doc); if (f == null) throw new IllegalStateException("已匹配字体无法重开");
                draft.metrics(f.metrics());
                if (u.replacement) {
                    // U+FFFD 不可用时使用同列表第一有效 face 的 .notdef；身份仍可真实光栅。
                    List<NativeFontFace.Glyph> replacement = f.shape(u.replacementId == 0 ? "\uffff" : "\ufffd",
                        NativeFontFace.Direction.LTR, policy.language);
                    NativeFontFace.Glyph g = replacement.get(0);
                    draft.glyphs.add(new Placed(u.source, u.replacementId, span.start, span.end, pen + g.xOffset, -g.yOffset,
                        g.xAdvance, -g.yAdvance, false, true)); pen += g.xAdvance;
                    pen=finishSpan(draft,units,doc,glyphBegin,spanPen,pen,u.script);continue;
                }
                List<NativeFontFace.Glyph> shaped = f.shape(context, span.start - start, span.end - start,
                    run.isOddRun() ? NativeFontFace.Direction.RTL : NativeFontFace.Direction.LTR, policy.language,
                    UScript.getShortName(u.script), span.start == start, span.end == end);
                TreeSet<Integer> clusters = new TreeSet<Integer>(); clusters.add(span.end - start);
                for (NativeFontFace.Glyph g : shaped) clusters.add(g.cluster);
                for (NativeFontFace.Glyph g : shaped) {
                    int c = start + g.cluster, next = start + clusters.higher(g.cluster);
                    int clusterStart = c, clusterEnd = next;
                    // 视觉命中始终返回完整 ICU 字素；HB 连字还可覆盖多个字素。
                    Unit atStart = unitStarts.floorEntry(c).getValue();
                    Unit atEnd = unitStarts.lowerEntry(next).getValue();
                    clusterStart = atStart.start; clusterEnd = atEnd.end;
                    boolean draw = UnicodeText.visible(doc.display, c, next);
                    if (draw) draft.glyphs.add(new Placed(u.source, g.glyphId, clusterStart, clusterEnd, pen + g.xOffset,
                        -g.yOffset, g.xAdvance, -g.yAdvance, u.source.color && color(u.source, style.logicalEm, g.glyphId, f), false));
                    else if (g.xAdvance != 0) draft.spaces.add(new Gap(clusterStart, clusterEnd, g.xAdvance));
                    pen += g.xAdvance;
                }
                pen=finishSpan(draft,units,doc,glyphBegin,spanPen,pen,u.script);
            }
        }
        draft.width = pen;
        if (draft.ascent + draft.descent == 0) defaultMetrics(draft, style, doc, sources);
        return draft;
    }
    /** 必要连接范围内保留HB墨迹相对位置，完整安全簇只增加一次advance。 */
    private float finishSpan(Draft draft,UnitIndex units,UnicodeText doc,int begin,float spanPen,float pen,int script) {
        LinkedHashMap<Integer,Cell> grouped=new LinkedHashMap<Integer,Cell>();float nativePen=spanPen;
        for(int i=begin;i<draft.glyphs.size();i++){
            Placed g=draft.glyphs.get(i);Cell cell=grouped.get(g.start);
            if(cell==null){TextStyle paint=units.starts.floorEntry(g.start).getValue().style;
                TextStyle shape=units.starts.floorEntry(g.start).getValue().shapeStyle;
                paint=new TextStyle(shape.font,shape.logicalEm,paint.color,shape.candidates(),paint.effects,paint.decoration);
                cell=new Cell(g.start,g.end,nativePen,0,paint,i,i);grouped.put(g.start,cell);
            }
            cell.end=Math.max(cell.end,g.end);cell.glyphEnd=i+1;cell.advance+=g.advance;g.cell=cell;nativePen+=g.advance;
        }
        List<Cell> cells=new ArrayList<Cell>(grouped.values());
        for(int i=1;i<cells.size();){Cell prev=cells.get(i-1),next=cells.get(i);
            if(prev.start<next.end&&next.start<prev.end){
                int leading=Math.min(prev.start,next.start);prev.start=leading;prev.end=Math.max(prev.end,next.end);prev.advance+=next.advance;prev.glyphEnd=next.glyphEnd;
                TextStyle requested=units.starts.floorEntry(leading).getValue().style;
                prev.style=new TextStyle(prev.style.font,prev.style.logicalEm,requested.color,prev.style.candidates(),requested.effects,requested.decoration);
                for(int g=next.glyphStart;g<next.glyphEnd;g++)draft.glyphs.get(g).cell=prev;cells.remove(i);
            }else i++;
        }
        float extra=0;
        for(Cell cell:cells){float delta=cell.advance!=0?cell.style.effects.boldAdvance:0;
            for(Unit unit:units.starts.subMap(cell.start,true,cell.end,false).values())if(!unit.style.effects.equals(cell.style.effects)||unit.style.color!=cell.style.color||!unit.style.decoration.equals(cell.style.decoration)){
                boolean exists=false;for(Diagnostic d:doc.diagnostics)if(d.kind==Diagnostic.Kind.STYLE_COALESCED&&d.sourceStart==doc.source(cell.start)&&d.sourceEnd==doc.sourceEnd(cell.end)){exists=true;break;}
                if(!exists)doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.STYLE_COALESCED,doc.source(cell.start),doc.sourceEnd(cell.end),"HB安全簇内部效果统一使用逻辑首样式"));break;
            }
            cell.x+=extra;cell.advance+=delta;
            if(!UnicodeText.complex(script))for(int i=cell.glyphStart;i<cell.glyphEnd;i++)draft.glyphs.get(i).x+=extra;
            if(delta!=0)draft.glyphs.get(cell.glyphEnd-1).advance+=delta;
            extra+=delta;draft.cells.add(cell);
        }
        return pen+extra;
    }
    private static final class Cell {
        int start,end,glyphEnd;final int glyphStart;float x,advance;TextStyle style;
        Cell(int start,int end,float x,float advance,TextStyle style,int glyphStart,int glyphEnd){this.start=start;this.end=end;this.x=x;this.advance=advance;this.style=style;this.glyphStart=glyphStart;this.glyphEnd=glyphEnd;}
    }
    private void defaultMetrics(Draft draft, TextStyle style, UnicodeText doc, List<FontSource> sources) {
        for (FontSource source : sources) { NativeFontFace f = face(source, style.logicalEm, doc); if (f != null) { draft.metrics(f.metrics()); return; } }
        draft.ascent = style.logicalEm; draft.metricHeight = style.logicalEm;
    }
    private float spaceAdvance(TextStyle style, UnicodeText doc, List<FontSource> sources, TextPolicy policy) {
        for (FontSource source : sources) {
            NativeFontFace f = face(source, style.logicalEm, doc);
            if (f != null && f.glyphIndex(32) != 0) {
                float advance = 0; for (NativeFontFace.Glyph g : f.shape(" ", NativeFontFace.Direction.LTR, policy.language)) advance += g.xAdvance;
                if (advance > 0) return advance+style.effects.boldAdvance;
            }
        }
        return style.logicalEm / 2f;
    }
    @Override public void close() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("FontTextService belongs to its creating thread");
        if (closed) return; closed = true;
        layouts.clear(); colors.clear(); ink.clear(); failures.clear(); faces.clear(); named.clear(); invalidation.clear(); defaults = Collections.emptyList();
        nativeService.close();
    }
    private static final class MatchKey {
        final List<FontSource> sources;final float em;final int script;final boolean emoji;final NativeFontFace.Direction direction;final String text;
        MatchKey(List<FontSource> sources,float em,int script,boolean emoji,NativeFontFace.Direction direction,String text){this.sources=sources;this.em=em;this.script=script;this.emoji=emoji;this.direction=direction;this.text=text;}
        @Override public boolean equals(Object o){if(!(o instanceof MatchKey))return false;MatchKey k=(MatchKey)o;return em==k.em&&script==k.script&&emoji==k.emoji&&direction==k.direction&&text.equals(k.text)&&sources.equals(k.sources);}
        @Override public int hashCode(){return Objects.hash(sources,em,script,emoji,direction,text);}
    }
    private static class FaceKey {
        final FontSource source; final float size;
        FaceKey(FontSource source, float size) { this.source = source; this.size = size; }
        @Override public boolean equals(Object o) { return o != null && getClass() == o.getClass() && source.equals(((FaceKey)o).source) && size == ((FaceKey)o).size; }
        @Override public int hashCode() { return Objects.hash(source, size); }
    }
    private static final class InkKey extends FaceKey {
        final int glyph;final GlyphRasterStyle style;
        InkKey(FontSource source,float size,int glyph,GlyphRasterStyle style){super(source,size);this.glyph=glyph;this.style=style;}
        @Override public boolean equals(Object o){return super.equals(o)&&glyph==((InkKey)o).glyph&&style.equals(((InkKey)o).style);}
        @Override public int hashCode(){return Objects.hash(super.hashCode(),glyph,style);}
    }
    private static final class ColorKey extends FaceKey {
        final int glyph; ColorKey(FontSource source, float size, int glyph) { super(source, size); this.glyph = glyph; }
        @Override public boolean equals(Object o) { return super.equals(o) && glyph == ((ColorKey)o).glyph; }
        @Override public int hashCode() { return super.hashCode() * 31 + glyph; }
    }
    private static final class LayoutKey {
        final String text; final List<FontSource> sources; final float size; final int width; final TextPolicy policy;
        LayoutKey(String text, List<FontSource> sources, float size, int width, TextPolicy policy) {
            this.text = text; this.sources = sources; this.size = size; this.width = width; this.policy = policy;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof LayoutKey)) return false; LayoutKey k = (LayoutKey)o;
            return size == k.size && width == k.width && text.equals(k.text) && sources.equals(k.sources) && policy.equals(k.policy);
        }
        @Override public int hashCode() { return Objects.hash(text, sources, size, width, policy); }
    }
    private static final class UnitIndex {
        final TreeMap<Integer, Unit> starts = new TreeMap<Integer, Unit>();
        UnitIndex(List<Unit> units) { for (Unit unit : units) starts.put(unit.start, unit); }
    }
    /** 安全点的累计真实advance；二分只选初步断点，最终行仍真实shape校验。 */
    private static final class BreakPlan {
        final int[] points;
        final double[] prefix;
        final int base;
        BreakPlan(TreeSet<Integer> points, double[] prefix, int base) {
            this.points = new int[points.size()]; int i = 0;
            for (int point : points) this.points[i++] = point;
            this.prefix = prefix; this.base = base;
        }
        int afterIndex(int point) {
            int index = Arrays.binarySearch(points, point);
            return index >= 0 ? index + 1 : -index - 1;
        }
        int firstAfter(int point) { return points[Math.min(afterIndex(point), points.length - 1)]; }
        int before(int point) {
            int index = Arrays.binarySearch(points, point); return points[index >= 0 ? index - 1 : -index - 2];
        }
        int estimate(int start, float width) {
            int first = afterIndex(start), low = first, high = points.length;
            double target = prefix[start - base] + width;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (prefix[points[mid] - base] <= target) low = mid + 1;
                else high = mid;
            }
            return points[Math.max(first, low - 1)];
        }
    }
    private static final class Span {
        final int start; int end; final Unit unit; final boolean special;
        Span(int start, int end, Unit unit, boolean special) { this.start = start; this.end = end; this.unit = unit; this.special = special; }
    }
    private static final class Gap {
        final int start, end; final float advance;
        Gap(int start, int end, float advance) { this.start = start; this.end = end; this.advance = advance; }
    }
    private static final class Placed {
        final FontSource source; final int id, start, end; float x, advance; final float y, yAdvance; final boolean color, replacement;
        Cell cell;
        Placed(FontSource source, int id, int start, int end, float x, float y, float advance, float yAdvance, boolean color, boolean replacement) {
            this.source = source; this.id = id; this.start = start; this.end = end; this.x = x; this.y = y;
            this.advance = advance; this.yAdvance = yAdvance; this.color = color; this.replacement = replacement;
        }
    }
    private static final class Draft {
        final List<Placed> glyphs = new ArrayList<Placed>(); final List<Gap> spaces = new ArrayList<Gap>();
        final List<Cell> cells=new ArrayList<Cell>();
        float width, ascent, descent, metricHeight;
        void metrics(NativeFontFace.Metrics m) { ascent = Math.max(ascent, m.ascent); descent = Math.max(descent, m.descent); metricHeight = Math.max(metricHeight, m.lineHeight); }
        double[] prefix(int start, int end) {
            double[] prefix = new double[end - start + 1];
            for (Placed g : glyphs) prefix[g.end - start] += g.advance;
            for (Gap gap : spaces) prefix[gap.end - start] += gap.advance;
            for (int i = 1; i < prefix.length; i++) prefix[i] += prefix[i - 1];
            return prefix;
        }
    }
}
