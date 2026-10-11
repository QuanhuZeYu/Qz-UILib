package club.heiqi.uilib.ui.scene.text.font;

import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.InlineBox;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.UnicodeText.Unit;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.nativefont.MathData;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.math.MathAxis;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.unicode.EmojiData;
import club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes;

import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BidiRun;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * ICU Unicode 算法 + 真实 FT/HB 共享排版服务。创建、测量、重载、关闭均在创建线程。 度量不申请 GPU 资源；光栅消费者用 Glyph.source.openFace
 * 在自己的 native 线程重开。 有界 face/layout/color-probe 缓存。默认资源只是显式候选列表，不建立第二条 fallback 链。
 * 当前未实现省略、采用软连字符断点时的可见连字符和编辑器双向光标 affinity； 软连字符断点暂禁用并诊断，不能把无连字符的结果冒充该合同。
 */
public final class FontTextService implements SceneTextMeasurer, AutoCloseable {
    private final Thread thread = Thread.currentThread();
    private final FontSession nativeService;
    private EmojiData emojiData;
    private UnicodeGraphemes.Source unicodeRules;
    private UnicodeGraphemes.Profile unicodeProfile;
    private final int faceLimit, layoutLimit, cachedTextLimit;
    private final LinkedHashMap<FaceKey, FontFace> faces =
            new LinkedHashMap<FaceKey, FontFace>(16, .75f, true);
    private final LinkedHashMap<FaceKey, String> failures =
            new LinkedHashMap<FaceKey, String>(16, .75f, true);
    private final LinkedHashMap<ColorKey, Boolean> colors =
            new LinkedHashMap<ColorKey, Boolean>(16, .75f, true);
    private final LinkedHashMap<InkKey, InkBounds> ink =
            new LinkedHashMap<InkKey, InkBounds>(16, .75f, true);
    private final LinkedHashMap<LayoutKey, TextLayout> layouts =
            new LinkedHashMap<LayoutKey, TextLayout>(16, .75f, true);
    // 构建结果的修正来自 assembly 而非末部件；弱键只随调用方持有的布局存活。
    private final Map<TextLayout, Float> assemblyItalic =
            new java.util.WeakHashMap<TextLayout, Float>();
    private final Map<String, List<FontSource>> named = new HashMap<String, List<FontSource>>();
    // 服务发布的数学正文尾链随默认资源换代；显式 register 后由调用方拥有该名称。
    private final Set<String> defaultMathTextNames = new TreeSet<>();
    private final List<Runnable> invalidation = new ArrayList<Runnable>();
    private List<FontSource> defaults;
    private final LinkedHashMap<FaceKey, List<ObfuscatedGlyph>> obfuscationPools =
            new LinkedHashMap<FaceKey, List<ObfuscatedGlyph>>(16, .75f, true);
    private long generation = 1;
    private boolean closed;

    public FontTextService(FontEngine engine) throws IOException {
        this(defaultSources(FontTextService.class.getClassLoader(), 1), engine);
    }

    public FontTextService(List<FontSource> defaults, FontEngine engine) {
        this(defaults, engine, 16, 128, 8192);
    }

    public FontTextService(
            List<FontSource> defaults,
            FontEngine engine,
            int faceLimit,
            int layoutLimit,
            int cachedTextLimit) {
        this(
                defaults,
                engine,
                faceLimit,
                layoutLimit,
                cachedTextLimit,
                UnicodeGraphemes.defaults());
    }

    public FontTextService(
            List<FontSource> defaults,
            FontEngine engine,
            int faceLimit,
            int layoutLimit,
            int cachedTextLimit,
            UnicodeGraphemes.Source rules) {
        if (faceLimit < 1 || layoutLimit < 0 || cachedTextLimit < 0)
            throw new IllegalArgumentException("无效字体缓存预算");
        this.faceLimit = faceLimit;
        this.layoutLimit = layoutLimit;
        this.cachedTextLimit = cachedTextLimit;
        this.defaults = copy(defaults);
        unicodeRules = Objects.requireNonNull(rules, "rules");
        unicodeProfile = Objects.requireNonNull(rules.snapshot(), "profile");
        emojiData = unicodeProfile.emoji;
        // 初始化具名正文源不走 register，保留初始 generation；配置的默认候选列表不变。
        named.putAll(MathTextFontSources.namedSources(1, this.defaults));
        defaultMathTextNames.addAll(named.keySet());
        nativeService = Objects.requireNonNull(engine, "engine").openSession();
        // 数学资源是独立具名链；懒加载沿 FontSource，不引入 MC/GL 或普通文字隐式回退。
        named.put(
                "math",
                Collections.singletonList(
                        FontSource.fromResource(
                                "stix-two-math",
                                1,
                                "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf",
                                false)));
    }

    /** 当前默认资源集合；完整脚本/RGI版本覆盖仍由资源清单和覆盖探针验收。 */
    public static List<FontSource> defaultSources(ClassLoader loader, long revision)
            throws IOException {
        String base = "assets/qz_uilib/fonts/";
        return Collections.unmodifiableList(
                Arrays.asList(
                        FontSource.resource(
                                "unifont",
                                revision,
                                loader,
                                base + "unifont/unifont-16.0.04.otf",
                                false,
                                Collections.<String>emptySet()),
                        FontSource.resource(
                                "noto-arabic",
                                revision,
                                loader,
                                base + "noto/NotoSansArabic.ttf",
                                false,
                                set("Arab")),
                        FontSource.resource(
                                "noto-devanagari",
                                revision,
                                loader,
                                base + "noto/NotoSansDevanagari.ttf",
                                false,
                                set("Deva")),
                        FontSource.resource(
                                "noto-thai",
                                revision,
                                loader,
                                base + "noto/NotoSansThai.ttf",
                                false,
                                set("Thai")),
                        FontSource.resource(
                                "noto-color-emoji",
                                revision,
                                loader,
                                base + "noto/NotoColorEmoji.ttf",
                                true,
                                Collections.<String>emptySet()),
                        FontSource.resource(
                                "noto-emoji-text",
                                revision,
                                loader,
                                base + "noto/NotoEmoji.ttf",
                                false,
                                Collections.<String>emptySet()),
                        FontSource.resource(
                                "noto-bengali",
                                revision,
                                loader,
                                base + "noto/NotoSansBengali.ttf",
                                false,
                                set("Beng")),
                        FontSource.resource(
                                "noto-gurmukhi",
                                revision,
                                loader,
                                base + "noto/NotoSansGurmukhi.ttf",
                                false,
                                set("Guru")),
                        FontSource.resource(
                                "noto-gujarati",
                                revision,
                                loader,
                                base + "noto/NotoSansGujarati.ttf",
                                false,
                                set("Gujr")),
                        FontSource.resource(
                                "noto-oriya",
                                revision,
                                loader,
                                base + "noto/NotoSansOriya.ttf",
                                false,
                                set("Orya")),
                        FontSource.resource(
                                "noto-tamil",
                                revision,
                                loader,
                                base + "noto/NotoSansTamil.ttf",
                                false,
                                set("Taml")),
                        FontSource.resource(
                                "noto-telugu",
                                revision,
                                loader,
                                base + "noto/NotoSansTelugu.ttf",
                                false,
                                set("Telu")),
                        FontSource.resource(
                                "noto-kannada",
                                revision,
                                loader,
                                base + "noto/NotoSansKannada.ttf",
                                false,
                                set("Knda")),
                        FontSource.resource(
                                "noto-malayalam",
                                revision,
                                loader,
                                base + "noto/NotoSansMalayalam.ttf",
                                false,
                                set("Mlym")),
                        FontSource.resource(
                                "noto-sinhala",
                                revision,
                                loader,
                                base + "noto/NotoSansSinhala.ttf",
                                false,
                                set("Sinh"))));
    }

    private static Set<String> set(String value) {
        return Collections.singleton(value);
    }

    private static List<FontSource> copy(List<FontSource> sources) {
        ArrayList<FontSource> result =
                new ArrayList<FontSource>(Objects.requireNonNull(sources, "sources"));
        for (FontSource s : result) Objects.requireNonNull(s, "source");
        return Collections.unmodifiableList(result);
    }

    private void check() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("FontTextService belongs to its creating thread");
        if (closed) throw new IllegalStateException("FontTextService is closed");
        UnicodeGraphemes.Profile next = Objects.requireNonNull(unicodeRules.snapshot(), "profile");
        if (next != unicodeProfile) {
            unicodeProfile = next;
            emojiData = next.emoji;
            invalidate();
        }
    }

    /** 调用方在所属域换入共享规则快照后显式通知，沿现有 onInvalidated 使稳定页面重排。 */
    public void refreshUnicodeRules() {
        check();
    }

    public long generation() {
        check();
        return generation;
    }

    public String emojiVersion() {
        check();
        return emojiData.version;
    }

    /** 仅切换本字体服务的规则；协同编辑重载由调用方提供共享 Source。 */
    public void reloadEmojiData(EmojiData data) {
        check();
        unicodeRules = UnicodeGraphemes.fixed(new UnicodeGraphemes.Profile(data));
        check();
    }

    public int cachedFaceCount() {
        check();
        return faces.size();
    }

    public int cachedLayoutCount() {
        check();
        return layouts.size();
    }

    public List<FontSource> defaultCandidates() {
        check();
        return defaults;
    }

    /** 宿主将这个变化通知接入其正常 measure/layout 失效，不在 replay 改计划。 */
    public AutoCloseable onInvalidated(final Runnable listener) {
        check();
        invalidation.add(Objects.requireNonNull(listener, "listener"));
        return new AutoCloseable() {
            public void close() {
                check();
                invalidation.remove(listener);
            }
        };
    }

    public void register(String name, List<FontSource> candidates) {
        check();
        named.put(Objects.requireNonNull(name, "name"), copy(candidates));
        defaultMathTextNames.remove(name);
        invalidate();
    }

    /** 默认资源及服务发布的数学正文尾链一起换代；显式注册列表仍由提供者另行换代。 */
    public void reload(List<FontSource> candidates) {
        check();
        defaults = copy(candidates);
        // MC 退休旧加载器后，未加载的旧尾链不能再读资源；先发布新链，再统一失效。
        Map<String, List<FontSource>> refreshed =
                MathTextFontSources.namedSources(generation + 1, defaults);
        for (String name : defaultMathTextNames) named.put(name, refreshed.get(name));
        invalidate();
    }

    private void invalidate() {
        generation++;
        layouts.clear();
        assemblyItalic.clear();
        colors.clear();
        ink.clear();
        failures.clear();
        obfuscationPools.clear();
        for (FontFace face : faces.values()) face.close();
        faces.clear();
        for (Runnable listener : new ArrayList<Runnable>(invalidation)) listener.run();
    }

    /** 默认数学声明使用具名 math；显式候选完整覆盖它，不向普通文本链添加数学字体。 端口仅持有服务和样式；LRU/reload 后每次重新取得当前 source/face。 */
    @Override
    public MathFont mathFont(TextStyle style) {
        check();
        Objects.requireNonNull(style, "style");
        return mathSelection(style) == null ? null : new ServiceMathFont(style);
    }

    private MathSelection mathSelection(TextStyle style) {
        check();
        List<FontSource> sources =
                style.candidates() == null ? named.get("math") : style.candidates();
        if (sources == null) return null;
        UnicodeText doc = new UnicodeText(TextInput.string(""), TextPolicy.NO_WRAP);
        for (FontSource candidate : sources) {
            List<FontSource> resolved;
            try {
                resolved = candidate.resolve(style.fontStyle, nativeService.supportsVariations());
            } catch (IllegalStateException | IllegalArgumentException error) {
                // 解析失败仍属于原候选，不能因为后继 MATH face 可用而丢失资源诊断。
                String cause = error.getCause() == null ? "" : "; cause=" + error.getCause();
                doc.diagnostics.add(
                        new Diagnostic(
                                Diagnostic.Kind.FONT_RESOURCE,
                                0,
                                0,
                                candidate.id + " (" + candidate.origin + "): " + error + cause));
                continue;
            }
            for (FontSource source : resolved) {
                FontFace current = face(source, style.logicalEm, doc);
                if (current != null && current.hasMathData()) {
                    TextStyle selected =
                            new TextStyle(
                                    "math",
                                    style.logicalEm,
                                    style.color,
                                    Collections.singletonList(source),
                                    style.effects,
                                    style.decoration,
                                    style.fontStyle);
                    return new MathSelection(source, current, selected, doc.diagnostics);
                }
            }
        }
        return null;
    }

    /** 仅在一次调用栈内有效，不作为 MathFont 的持久字段。 */
    private static final class MathSelection {
        final FontSource source;
        final FontFace face;
        final TextStyle style;
        final List<Diagnostic> diagnostics;

        MathSelection(
                FontSource source, FontFace face, TextStyle style, List<Diagnostic> diagnostics) {
            this.source = source;
            this.face = face;
            this.style = style;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
        }
    }

    private final class ServiceMathFont implements MathFont {
        private final TextStyle requested;

        ServiceMathFont(TextStyle requested) {
            this.requested = requested;
        }

        private MathSelection current() {
            MathSelection selected = mathSelection(requested);
            if (selected == null) throw new UnsupportedOperationException("当前候选链没有可用数学字体");
            return selected;
        }

        public float constant(Constant value) {
            Objects.requireNonNull(value, "value");
            return current().face.mathConstant(value.id);
        }

        public TextLayout text(String value) {
            Objects.requireNonNull(value, "value");
            MathSelection selected = current();
            TextLayout result =
                    layout(value, selected.style, Integer.MAX_VALUE, TextPolicy.NO_WRAP);
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.kind == Diagnostic.Kind.MISSING_GLYPH
                        || diagnostic.kind == Diagnostic.Kind.INVALID_INPUT)
                    throw new UnsupportedOperationException("数学文字无法完整生成: " + diagnostic.message);
            }
            // 候选资源 warning 与生成失败分开；后继真实字形可用时保持结果并附尝试序列。
            for (TextLayout.Line line : result.lines())
                for (TextLayout.Glyph glyph : line.glyphs())
                    if (glyph.glyphId == 0 || glyph.replacement)
                        throw new UnsupportedOperationException("数学文字未生成有效字形");
            if (selected.diagnostics.isEmpty()) return result;
            List<Diagnostic> diagnostics = new ArrayList<Diagnostic>(selected.diagnostics);
            diagnostics.addAll(result.diagnostics());
            return new TextLayout(
                    result.width,
                    result.height,
                    result.advanceWidth,
                    result.advanceHeight,
                    result.style,
                    result.lines(),
                    result.logicalText,
                    result.originalText,
                    result.generation,
                    diagnostics,
                    result.input);
        }

        public TextLayout stretch(int codepoint, float minimumHeight) {
            return stretch(codepoint, minimumHeight, MathAxis.VERTICAL);
        }

        public TextLayout stretch(int codepoint, float minimumAdvance, MathAxis axis) {
            return variantLayout(
                    codepoint, minimumAdvance, Objects.requireNonNull(axis, "axis"), true);
        }

        @Override
        public TextLayout displayOperator(int codepoint, float preferredHeight) {
            return variantLayout(codepoint, preferredHeight, MathAxis.VERTICAL, false);
        }

        /** 轴向伸缩必须满足目标；大算子建议高度允许选完整的最大真实字形。 */
        private TextLayout variantLayout(
                int codepoint, float minimumAdvance, MathAxis axis, boolean strict) {
            if (!Character.isValidCodePoint(codepoint)
                    || codepoint >= 0xd800 && codepoint <= 0xdfff)
                throw new IllegalArgumentException("无效数学码点");
            if (!Float.isFinite(minimumAdvance) || minimumAdvance < 0)
                throw new IllegalArgumentException("数学伸缩目标必须有限且非负");
            MathSelection selected = current();
            int base = selected.face.glyphIndex(codepoint);
            if (base == 0)
                throw new UnsupportedOperationException(
                        "数学字体缺字 U+" + Integer.toHexString(codepoint));
            List<MathData.Variant> variants = selected.face.mathVariants(base, axis);
            MathData.Variant chosen = null;
            MathData.Variant largest = null;
            float maximum = 0;
            for (MathData.Variant variant : variants) {
                maximum = Math.max(maximum, variant.stretchAdvance);
                if (largest == null || variant.stretchAdvance > largest.stretchAdvance)
                    largest = variant;
                if (variant.stretchAdvance >= minimumAdvance
                        && (chosen == null || variant.stretchAdvance < chosen.stretchAdvance))
                    chosen = variant;
            }
            // 没有变体时仅使用原字形的真实能力，不用 fontsize/拉伸假造伸缩能力。
            if (variants.isEmpty()) {
                InkBounds baseInk = selected.face.glyphInkBounds(base);
                if (baseInk.known) {
                    maximum =
                            axis == MathAxis.VERTICAL
                                    ? baseInk.bottom - baseInk.top
                                    : selected.face.glyphHorizontalAdvance(base);
                    if (!strict || maximum >= minimumAdvance)
                        chosen = new MathData.Variant(base, maximum, axis);
                }
            }
            if (chosen == null && !strict) chosen = largest;
            if (chosen == null && strict) {
                try {
                    return buildMathAssembly(
                            selected,
                            codepoint,
                            selected.face.mathAssembly(base, axis),
                            minimumAdvance,
                            axis);
                } catch (UnsupportedOperationException error) {
                    throw new UnsupportedOperationException(
                            "数学伸缩不可构建 axis="
                                    + axis
                                    + " U+"
                                    + Integer.toHexString(codepoint)
                                    + " font="
                                    + selected.source
                                    + " em="
                                    + requested.logicalEm
                                    + ": requested="
                                    + minimumAdvance
                                    + ", maximumVariant="
                                    + maximum
                                    + "; "
                                    + error.getMessage(),
                            error);
                }
            }
            if (chosen == null)
                throw new UnsupportedOperationException(
                        "数学完整字形不可用 U+"
                                + Integer.toHexString(codepoint)
                                + " font="
                                + selected.source);
            return buildMathGlyph(selected, codepoint, chosen);
        }

        /** 两种大小语义共享同一真实字形度量、资源诊断与绘制身份。 */
        private TextLayout buildMathGlyph(
                MathSelection selected, int codepoint, MathData.Variant variant) {
            int glyphId = variant.glyphId;
            boolean horizontal = variant.axis == MathAxis.HORIZONTAL;
            // glyph 的 hinted h advance 保留；横向 MATH nominal span 是另一份逻辑盒事实。
            float advance = selected.face.glyphHorizontalAdvance(glyphId);
            InkBounds bounds = selected.face.glyphInkBounds(glyphId);
            if (!bounds.known) throw new UnsupportedOperationException("数学 variant 墨迹边界不可用");
            float ascent = Math.max(0, -bounds.top), descent = Math.max(0, bounds.bottom);
            float height = ascent + descent;
            float left = Math.min(0, bounds.left);
            float width = Math.max(advance, bounds.right) - left;
            float logicalAdvance = horizontal ? variant.stretchAdvance : advance;
            // MATH span 与已归一的 glyph 包络取并集，不能再把负 bearing 的左移加到 span。
            if (horizontal) width = Math.max(width, logicalAdvance);
            String value = new String(Character.toChars(codepoint));
            int end = value.length();
            TextLayout.Glyph glyph =
                    new TextLayout.Glyph(
                            selected.source,
                            requested.logicalEm,
                            glyphId,
                            0,
                            end,
                            0,
                            end,
                            -left,
                            ascent,
                            advance,
                            0,
                            false,
                            false,
                            bounds,
                            requested.logicalEm,
                            1,
                            GlyphRasterStyle.NORMAL,
                            selected.style,
                            0);
            TextLayout.Cluster cluster =
                    new TextLayout.Cluster(
                            0,
                            end,
                            0,
                            end,
                            0,
                            1,
                            horizontal ? 0 : -left,
                            logicalAdvance,
                            selected.style);
            TextLayout.Line line =
                    new TextLayout.Line(
                            value,
                            0,
                            0,
                            0,
                            end,
                            0,
                            end,
                            width,
                            ascent,
                            ascent,
                            descent,
                            height,
                            Collections.singletonList(glyph),
                            Collections.singletonList(cluster),
                            Collections.<TextLayout.Decoration>emptyList());
            UnicodeText doc = new UnicodeText(TextInput.string(value), TextPolicy.NO_WRAP);
            doc.diagnostics.addAll(selected.diagnostics);
            styleDiagnostic(selected.source, selected.style, doc, 0, end);
            return new TextLayout(
                    ceil(width),
                    ceil(height),
                    width,
                    height,
                    selected.style,
                    Collections.singletonList(line),
                    value,
                    value,
                    generation,
                    doc.diagnostics,
                    TextInput.string(value));
        }

        /** 部件轴向原点由 fullAdvance/overlap 决定；横向共用 baseline，竖向共用 X 原点。 */
        private TextLayout buildMathAssembly(
                MathSelection selected,
                int codepoint,
                MathData.Assembly assembly,
                float minimumAdvance,
                MathAxis axis) {
            if (assembly != null && assembly.axis != axis)
                throw new UnsupportedOperationException("assembly 轴与请求不一致");
            MathAssembly.Result result = MathAssembly.build(assembly, minimumAdvance);
            boolean horizontal = axis == MathAxis.HORIZONTAL;
            List<MathAssembly.Placement> positions = result.placements();
            List<InkBounds> bounds = new ArrayList<InkBounds>(positions.size());
            float[] advances = new float[positions.size()];
            Map<Integer, InkBounds> partInk = new HashMap<Integer, InkBounds>();
            Map<Integer, Float> partAdvance = new HashMap<Integer, Float>();
            float left = 0, right = 0, top = 0, bottom = 0, advance = 0;
            for (int i = 0; i < positions.size(); i++) {
                MathAssembly.Placement position = positions.get(i);
                int id = position.part.glyphId;
                InkBounds ink = partInk.get(id);
                if (ink == null) {
                    ink = selected.face.glyphInkBounds(id);
                    if (!ink.known) throw new UnsupportedOperationException("assembly 部件墨迹不可用");
                    partInk.put(id, ink);
                    partAdvance.put(id, selected.face.glyphHorizontalAdvance(id));
                }
                bounds.add(ink);
                advances[i] = partAdvance.get(id);
                float x = horizontal ? position.offset : 0;
                float y = horizontal ? 0 : -position.offset;
                advance = horizontal ? result.stretchAdvance : Math.max(advance, advances[i]);
                left = Math.min(left, x + ink.left);
                right =
                        Math.max(
                                right,
                                horizontal
                                        ? Math.max(result.stretchAdvance, x + ink.right)
                                        : Math.max(advances[i], ink.right));
                top = Math.min(top, y + ink.top);
                bottom = Math.max(bottom, y + ink.bottom);
            }
            // 墨迹只决定整体包围盒及基线平移，不能拿每个部件的 ink 高度当连接锚点。
            float ascent = -top, descent = bottom, height = ascent + descent, width = right - left;
            String value = new String(Character.toChars(codepoint));
            int end = value.length();
            List<TextLayout.Glyph> glyphs = new ArrayList<TextLayout.Glyph>(positions.size());
            for (int i = 0; i < positions.size(); i++) {
                MathAssembly.Placement position = positions.get(i);
                glyphs.add(
                        new TextLayout.Glyph(
                                selected.source,
                                requested.logicalEm,
                                position.part.glyphId,
                                0,
                                end,
                                0,
                                end,
                                -left + (horizontal ? position.offset : 0),
                                ascent - (horizontal ? 0 : position.offset),
                                advances[i],
                                0,
                                false,
                                false,
                                bounds.get(i),
                                requested.logicalEm,
                                1,
                                GlyphRasterStyle.NORMAL,
                                selected.style,
                                0));
            }
            TextLayout.Cluster cluster =
                    new TextLayout.Cluster(
                            0, end, 0, end, 0, glyphs.size(), -left, advance, selected.style);
            TextLayout.Line line =
                    new TextLayout.Line(
                            value,
                            0,
                            0,
                            0,
                            end,
                            0,
                            end,
                            width,
                            ascent,
                            ascent,
                            descent,
                            height,
                            glyphs,
                            Collections.singletonList(cluster),
                            Collections.<TextLayout.Decoration>emptyList());
            UnicodeText doc = new UnicodeText(TextInput.string(value), TextPolicy.NO_WRAP);
            doc.diagnostics.addAll(selected.diagnostics);
            styleDiagnostic(selected.source, selected.style, doc, 0, end);
            TextLayout layout =
                    new TextLayout(
                            ceil(width),
                            ceil(height),
                            width,
                            height,
                            selected.style,
                            Collections.singletonList(line),
                            value,
                            value,
                            generation,
                            doc.diagnostics,
                            TextInput.string(value));
            assemblyItalic.put(layout, assembly.italicCorrection);
            return layout;
        }

        public float topAccentAttachment(TextLayout layout) {
            Objects.requireNonNull(layout, "layout");
            MathSelection selected = current();
            if (layout.generation != generation)
                throw new IllegalArgumentException("数学布局已换代，须重新布局后查询附件点");
            TextLayout.Glyph single = null;
            int count = 0;
            for (TextLayout.Line line : layout.lines()) {
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    if (!glyph.source.equals(selected.source)
                            || glyph.logicalEm != requested.logicalEm)
                        throw new IllegalArgumentException("数学布局不是当前字体实例");
                    single = glyph;
                    count++;
                }
            }
            if (count != 1 || layout.lines().size() != 1 || assemblyItalic.containsKey(layout))
                return Float.NaN;
            float attachment = selected.face.mathTopAccentAttachment(single.glyphId);
            return Float.isNaN(attachment) ? Float.NaN : single.x + attachment;
        }

        public float italicCorrection(TextLayout layout) {
            Objects.requireNonNull(layout, "layout");
            MathSelection selected = current();
            if (layout.generation != generation)
                throw new IllegalArgumentException("数学布局已换代，须重新布局后查询修正");
            TextLayout.Glyph terminal = null;
            for (TextLayout.Line line : layout.lines()) {
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    if (!glyph.source.equals(selected.source)
                            || glyph.logicalEm != requested.logicalEm)
                        throw new IllegalArgumentException("数学布局不是当前字体实例");
                    terminal = glyph;
                }
            }
            Float correction = assemblyItalic.get(layout);
            return correction != null
                    ? correction
                    : terminal == null ? 0 : selected.face.mathItalicCorrection(terminal.glyphId);
        }
    }

    private List<FontSource> candidates(TextStyle style) {
        if (style.candidates() != null) return style.candidates();
        List<FontSource> result = named.get(style.font);
        // 旧任意名称均映射配置的默认列表，没有基于名称偷偷添加字体。
        return result == null ? defaults : result;
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width) {
        return layout(text, style, width, TextPolicy.NORMAL);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
        check();
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(policy, "policy");
        if (!style.effects.equals(TextEffects.NORMAL)
                || !style.decoration.equals(TextDecoration.NONE))
            return layout(
                    new AttributedText(
                            TextInput.string(text),
                            text,
                            style,
                            text.isEmpty()
                                    ? Collections.<StyleSpan>emptyList()
                                    : Collections.singletonList(
                                            new StyleSpan(0, text.length(), style))),
                    width,
                    policy);
        List<FontSource> sources = candidates(style);
        if (!style.fontStyle.equals(club.heiqi.uilib.ui.scene.text.FontStyle.NORMAL))
            return layout(AttributedText.plain(TextInput.string(text), style), width, policy);
        LayoutKey key = new LayoutKey(text, sources, style.logicalEm, width, policy);
        TextLayout cached = layouts.get(key);
        if (cached != null) return rebindResolvedStyle(cached, style, key.sources);
        TextLayout result = build(TextInput.string(text), text, style, width, policy, sources);
        if (layoutLimit > 0 && text.length() <= cachedTextLimit) {
            layouts.put(key, result);
            trim(layouts, layoutLimit);
        }
        return result;
    }

    /** 仅plain缓存命中可调用：完整LayoutKey已证明解析后的有序候选及em相等。公开withStyle仍严格检查原selector。 */
    private TextLayout rebindResolvedStyle(
            TextLayout cached, TextStyle style, List<FontSource> resolvedSources) {
        if (!candidates(cached.style).equals(resolvedSources)
                || !candidates(style).equals(resolvedSources)
                || cached.style.logicalEm != style.logicalEm
                || !cached.style.effects.equals(style.effects)
                || !cached.style.decoration.equals(style.decoration))
            throw new IllegalArgumentException("缓存样式没有等价的实际字体身份");
        if (cached.style.sameFont(style)) return cached.withStyle(style);
        List<TextLayout.Line> lines = new ArrayList<TextLayout.Line>();
        for (TextLayout.Line line : cached.lines()) {
            List<TextLayout.Glyph> glyphs = new ArrayList<TextLayout.Glyph>();
            List<TextLayout.Cluster> clusters = new ArrayList<TextLayout.Cluster>();
            List<TextLayout.Decoration> decorations = new ArrayList<TextLayout.Decoration>();
            for (TextLayout.Glyph g : line.glyphs())
                glyphs.add(
                        new TextLayout.Glyph(
                                g.source,
                                g.size,
                                g.glyphId,
                                g.logicalStart,
                                g.logicalEnd,
                                g.sourceStart,
                                g.sourceEnd,
                                g.x,
                                g.y,
                                g.xAdvance,
                                g.yAdvance,
                                g.color,
                                g.replacement,
                                g.inkBounds,
                                g.logicalEm,
                                g.rasterToLayout,
                                g.rasterStyle,
                                style,
                                g.clusterIndex));
            for (TextLayout.Cluster c : line.clusters())
                clusters.add(
                        new TextLayout.Cluster(
                                c.logicalStart,
                                c.logicalEnd,
                                c.sourceStart,
                                c.sourceEnd,
                                c.glyphIndexStart,
                                c.glyphIndexEnd,
                                c.x,
                                c.advance,
                                style,
                                c.rtl));
            for (TextLayout.Decoration d : line.decorations())
                decorations.add(
                        new TextLayout.Decoration(
                                d.kind,
                                d.x,
                                d.y,
                                d.width,
                                d.height,
                                style.color,
                                d.logicalStart,
                                d.logicalEnd,
                                d.sourceStart,
                                d.sourceEnd));
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x,
                            line.y,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance,
                            line.baseline,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            glyphs,
                            clusters,
                            decorations,
                            line.paragraphRtl));
        }
        return new TextLayout(
                cached.width,
                cached.height,
                cached.advanceWidth,
                cached.advanceHeight,
                style,
                lines,
                cached.logicalText,
                cached.originalText,
                cached.generation,
                cached.diagnostics(),
                cached.input);
    }

    /** 输入对象携带 byte/UTF16 来源，因此不与 String cache 混用。 */
    public TextLayout layout(TextInput text, TextStyle style, int width, TextPolicy policy) {
        check();
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(policy, "policy");
        return layout(
                AttributedText.plain(Objects.requireNonNull(text, "text"), style), width, policy);
    }

    private FontFace face(FontSource source, float size, UnicodeText doc) {
        FaceKey key = new FaceKey(source, size);
        FontFace found = faces.get(key);
        if (found != null) return found;
        String failure = failures.get(key);
        if (failure == null) {
            try {
                found = source.openFace(nativeService, size);
                faces.put(key, found);
                while (faces.size() > faceLimit) {
                    FaceKey oldest = faces.keySet().iterator().next();
                    faces.remove(oldest).close();
                }
                return found;
            } catch (IllegalStateException | IllegalArgumentException error) {
                failure = error.toString();
                failures.put(key, failure);
                trim(failures, faceLimit);
            }
        }
        String message = source + " (" + source.origin + "): " + failure;
        for (Diagnostic d : doc.diagnostics)
            if (d.kind == Diagnostic.Kind.FONT_RESOURCE && d.message.equals(message)) return null;
        doc.diagnostics.add(new Diagnostic(Diagnostic.Kind.FONT_RESOURCE, 0, 0, message));
        return null;
    }

    private boolean color(FontSource source, float size, int glyph, FontFace face) {
        ColorKey key = new ColorKey(source, size, glyph);
        Boolean found = colors.get(key);
        if (found == null) {
            GlyphBitmap bitmap = face.rasterize(glyph);
            found = false;
            if (bitmap.color && bitmap.width > 0 && bitmap.height > 0) {
                byte[] pixels = bitmap.pixels();
                for (int i = 3; i < pixels.length; i += 4)
                    if (pixels[i] != 0) {
                        found = true;
                        break;
                    }
            }
            colors.put(key, found);
            trim(colors, 1024);
        }
        return found;
    }

    /** 仅加载真实glyph metrics/bitmap，不执行FT_Render_Glyph。实际identity与光栅请求完全相同。 */
    private InkBounds inkBounds(
            FontSource source, float size, int glyph, GlyphRasterStyle variant, UnicodeText doc) {
        InkKey key = new InkKey(source, size, glyph, variant);
        InkBounds result = ink.get(key);
        if (result == null) {
            FontFace face = face(source, size, doc);
            if (face == null) throw new IllegalStateException("已匹配字体无法取得真实墨迹边界");
            result = face.glyphInkBounds(glyph, variant);
            ink.put(key, result);
            trim(ink, 1024);
        }
        return result;
    }

    private static <K, V> void trim(LinkedHashMap<K, V> cache, int limit) {
        while (cache.size() > limit) cache.remove(cache.keySet().iterator().next());
    }

    private void match(
            UnicodeText doc,
            List<Unit> units,
            Bidi bidi,
            int paragraphStart,
            TextStyle style,
            TextPolicy policy,
            List<FontSource> sources) {
        Map<MatchKey, FontSource> independent = new HashMap<MatchKey, FontSource>();
        for (int i = 0; i < units.size(); ) {
            Unit first = units.get(i);
            if (first.inlineBox != null) {
                i++;
                continue;
            }
            int j = i + 1;
            if (UnicodeText.complex(first.script)) {
                while (j < units.size()
                        && units.get(j).inlineBox == null
                        && units.get(j).script == first.script
                        && !units.get(j).emoji
                        && bidi.getLevelAt(units.get(j).start - paragraphStart)
                                == bidi.getLevelAt(first.start - paragraphStart)
                        && units.get(j).shapeStyle.sameFont(first.shapeStyle)) j++;
            }
            int start = first.start, end = units.get(j - 1).end;
            TextStyle leading = first.shapeStyle;
            for (int k = i; k < j; k++) {
                Unit unit = units.get(k);
                if (!unit.shapeStyle.sameFont(leading)) {
                    doc.diagnostics.add(
                            new Diagnostic(
                                    Diagnostic.Kind.STYLE_COALESCED,
                                    doc.source(start),
                                    doc.sourceEnd(end),
                                    "必要连接范围字体/字号统一使用逻辑首样式"));
                    unit.shapeStyle = leading;
                }
            }
            style = leading;
            sources = resolvedCandidates(leading, doc);
            if (!UnicodeText.visible(doc.display, start, end) || doc.display.charAt(start) == '	') {
                i = j;
                continue;
            }
            ShapeDirection direction =
                    (bidi.getLevelAt(start - paragraphStart) & 1) == 0
                            ? ShapeDirection.LTR
                            : ShapeDirection.RTL;
            // 独立字素的匹配没有跨字素连接上下文，当前layout内相同字素复用候选结果。
            String matchText =
                    UnicodeText.complex(first.script) ? null : doc.display.substring(start, end);
            MatchKey matchKey =
                    matchText == null
                            ? null
                            : new MatchKey(
                                    sources,
                                    style.fontStyle,
                                    style.logicalEm,
                                    first.script,
                                    first.emoji,
                                    direction,
                                    matchText);
            FontSource selected = matchKey == null ? null : independent.get(matchKey);
            if (selected != null) {
                styleDiagnostic(selected, style, doc, start, end);
                for (int k = i; k < j; k++) units.get(k).source = selected;
                i = j;
                continue;
            }
            for (FontSource candidate : sources) {
                if (UnicodeText.complex(first.script)
                        && !candidate.supportsScript(UScript.getShortName(first.script))) continue;
                if (first.emoji && !candidate.color) continue;
                FontFace f = face(candidate, style.logicalEm, doc);
                if (f == null) continue;
                String context = matchText == null ? doc.display : matchText;
                int from = matchText == null ? start : 0,
                        limit = matchText == null ? end : matchText.length();
                List<ShapedGlyph> shaped =
                        f.shape(
                                context,
                                from,
                                limit,
                                direction,
                                policy.language,
                                UScript.getShortName(first.script),
                                matchText != null,
                                matchText != null);
                boolean ok = !shaped.isEmpty();
                int visible = 0;
                TreeSet<Integer> clusters = new TreeSet<Integer>();
                clusters.add(limit);
                for (ShapedGlyph g : shaped) clusters.add(g.cluster);
                for (ShapedGlyph g : shaped) {
                    if (g.cluster >= limit
                            || !UnicodeText.visible(context, g.cluster, clusters.higher(g.cluster)))
                        continue;
                    visible++;
                    if (g.glyphId == 0) {
                        ok = false;
                        break;
                    }
                    // Emoji 默认/VS16必须实际彩色；VS15或默认文字必须不能输出彩色 bitmap。
                    if ((first.emoji || first.emojiLike || candidate.color)
                            && color(candidate, style.logicalEm, g.glyphId, f) != first.emoji) {
                        ok = false;
                        break;
                    }
                }
                // 一个 emoji 字素必须形成一个真实彩色序列，不把散开的码点宣称正确生成。
                if (first.emoji && visible != 1) ok = false;
                if (ok) {
                    selected = candidate;
                    if (matchKey != null) independent.put(matchKey, selected);
                    break;
                }
            }
            if (selected == null) {
                doc.diagnostics.add(
                        new Diagnostic(
                                Diagnostic.Kind.MISSING_GLYPH,
                                doc.source(start),
                                doc.source(end),
                                "候选列表无法正确生成 "
                                        + UScript.getShortName(first.script)
                                        + " 范围；使用列表内稳定替代"));
                int replacement = 0;
                for (FontSource candidate : sources) {
                    FontFace f = face(candidate, style.logicalEm, doc);
                    if (f == null) continue;
                    int id = f.glyphIndex(0xfffd);
                    if (id != 0 && !color(candidate, style.logicalEm, id, f)) {
                        selected = candidate;
                        replacement = id;
                        break;
                    }
                }
                if (selected == null)
                    for (FontSource candidate : sources) {
                        FontFace f = face(candidate, style.logicalEm, doc);
                        if (f != null && !color(candidate, style.logicalEm, 0, f)) {
                            selected = candidate;
                            break;
                        }
                    }
                if (selected == null) {
                    StringBuilder failure =
                            new StringBuilder("候选列表无有效文字字体，不能生成稳定替代: ").append(sources);
                    for (Diagnostic diagnostic : doc.diagnostics)
                        failure.append("\n")
                                .append(diagnostic.kind)
                                .append(": ")
                                .append(diagnostic.message);
                    throw new IllegalStateException(failure.toString());
                }
                for (int k = i; k < j; k++) {
                    units.get(k).replacement = true;
                    units.get(k).replacementId = replacement;
                }
            }
            styleDiagnostic(selected, style, doc, start, end);
            for (int k = i; k < j; k++) units.get(k).source = selected;
            i = j;
        }
    }

    private List<FontSource> resolvedCandidates(TextStyle style, UnicodeText doc) {
        List<FontSource> result = new ArrayList<FontSource>();
        for (FontSource source : candidates(style))
            try {
                result.addAll(source.resolve(style.fontStyle, nativeService.supportsVariations()));
            } catch (RuntimeException error) {
                doc.diagnostics.add(
                        new Diagnostic(
                                Diagnostic.Kind.FONT_RESOURCE,
                                0,
                                0,
                                source + " (" + source.origin + "): SFNT元数据解析失败: " + error));
            }
        return result;
    }

    private void styleDiagnostic(
            FontSource source, TextStyle style, UnicodeText doc, int start, int end) {
        if (!source.actualStyle().equals(style.fontStyle))
            doc.diagnostics.add(
                    new Diagnostic(
                            Diagnostic.Kind.STYLE_FALLBACK,
                            doc.source(start),
                            doc.sourceEnd(end),
                            "请求="
                                    + style.fontStyle
                                    + " 实际="
                                    + source.actualStyle()
                                    + " family="
                                    + source.metadata().family
                                    + " origin="
                                    + source.origin
                                    + " face="
                                    + source.faceIndex
                                    + " axes="
                                    + source.axisValues()));
        if (!nativeService.supportsVariations() && !source.metadata().axes.isEmpty())
            doc.diagnostics.add(
                    new Diagnostic(
                            Diagnostic.Kind.UNSUPPORTED,
                            doc.source(start),
                            doc.sourceEnd(end),
                            "字体后端不支持可变轴，采用静态实际成员: " + source));
    }

    private TextLayout build(
            TextInput input,
            String original,
            TextStyle style,
            int width,
            TextPolicy policy,
            List<FontSource> sources) {
        if (style.logicalEm > 32767) throw new IllegalArgumentException("native em 字号上限为32767");
        return build(AttributedText.plain(input, style), original, width, policy);
    }

    @Override
    public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
        check();
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(policy, "policy");
        if (!Float.isFinite(width)) throw new IllegalArgumentException("无效布局宽度");
        return build(text, text.originalText, width, policy);
    }

    /** 属性布局不实现动画；保留真实正文及来源，给出稳定范围诊断，不在 paint 反复抛错。 */
    private static AttributedText supportedText(AttributedText value) {
        boolean unsupported =
                value.baseStyle.effects.obfuscated || value.trailingStyle.effects.obfuscated;
        for (StyleSpan s : value.spans()) unsupported |= s.style.effects.obfuscated;
        if (!unsupported) return value;
        List<StyleSpan> spans = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>(value.diagnostics());
        for (StyleSpan s : value.spans()) {
            if (s.style.effects.obfuscated)
                diagnostics.add(
                        new Diagnostic(
                                Diagnostic.Kind.UNSUPPORTED,
                                value.input.sourceStart(s.logicalStart),
                                value.input.sourceEnd(s.logicalEnd - 1),
                                "属性布局不支持混淆动画，显示原正文"));
            spans.add(new StyleSpan(s.logicalStart, s.logicalEnd, withoutObfuscation(s.style)));
        }
        if (spans.isEmpty() && value.trailingStyle.effects.obfuscated)
            diagnostics.add(
                    new Diagnostic(
                            Diagnostic.Kind.UNSUPPORTED,
                            0,
                            value.input.sourceRight(value.input.text.length()),
                            "属性布局不支持混淆动画，保留空正文"));
        return new AttributedText(
                        value.input,
                        value.originalText,
                        withoutObfuscation(value.baseStyle),
                        spans,
                        withoutObfuscation(value.trailingStyle),
                        diagnostics)
                .withInlineBoxes(value.inlineBoxes());
    }

    private static TextStyle withoutObfuscation(TextStyle style) {
        TextEffects e = style.effects;
        return e.obfuscated
                ? style.withEffects(
                        new TextEffects(
                                e.bold,
                                e.italic,
                                e.underline,
                                e.strike,
                                false,
                                e.boldAdvance,
                                e.italicShear))
                : style;
    }

    private TextLayout build(
            AttributedText attributed, String original, float width, TextPolicy policy) {
        attributed = supportedText(attributed);
        TextInput input = attributed.input;
        TextStyle style = attributed.baseStyle;
        List<FontSource> sources = candidates(style);
        UnicodeText doc = new UnicodeText(input, policy, attributed.inlineBoxes());
        doc.diagnostics.addAll(attributed.diagnostics());
        List<TextLayout.Line> lines = new ArrayList<TextLayout.Line>();
        float y = 0, maxWidth = 0;
        for (int start = 0; start <= doc.display.length(); ) {
            int newline = doc.display.indexOf('\n', start),
                    end = newline < 0 ? doc.display.length() : newline;
            // 空段也有活动逻辑样式，末段使用显式trailing状态，不能继承整个输入的base度量。
            style = attributed.styleAt(doc.logical[start]);
            sources = candidates(style);
            String paragraph = doc.display.substring(start, end);
            Bidi bidi = new Bidi();
            byte level =
                    policy.direction == TextPolicy.Direction.AUTO
                            ? Bidi.LEVEL_DEFAULT_LTR
                            : (byte) (policy.direction == TextPolicy.Direction.LTR ? 0 : 1);
            bidi.setPara(paragraph, level, null);
            List<Unit> units = doc.units(start, end, unicodeProfile);
            int spanCursor = 0;
            List<StyleSpan> requested = attributed.spans();
            while (spanCursor < requested.size()
                    && requested.get(spanCursor).logicalEnd <= doc.logical[start]) spanCursor++;
            for (Unit unit : units) {
                unit.style = attributed.styleAt(doc.logical[unit.start]);
                unit.shapeStyle = unit.style;
                while (spanCursor < requested.size()
                        && requested.get(spanCursor).logicalEnd <= doc.logical[unit.start])
                    spanCursor++;
                for (int k = spanCursor + 1;
                        k < requested.size()
                                && requested.get(k).logicalStart < doc.logical[unit.end];
                        k++)
                    if (!requested.get(k).style.equals(unit.style)) {
                        doc.diagnostics.add(
                                new Diagnostic(
                                        Diagnostic.Kind.STYLE_COALESCED,
                                        doc.source(unit.start),
                                        doc.sourceEnd(unit.end),
                                        "字素内部样式统一使用逻辑首样式"));
                        break;
                    }
            }
            match(doc, units, bidi, start, style, policy, sources);
            TreeSet<Integer> safe = new TreeSet<Integer>();
            safe.add(start);
            safe.add(end);
            for (Unit unit : units) {
                safe.add(unit.start);
                safe.add(unit.end);
            }
            UnitIndex index = new UnitIndex(units);
            Draft whole = shape(doc, index, bidi, start, start, end, style, policy, sources);
            // 排除 ICU 字素内部以及 HB 已整形 cluster 内部；超宽单簇仍完整。
            for (Placed glyph : whole.glyphs)
                safe.subSet(glyph.start, false, glyph.end, false).clear();
            for (java.util.Iterator<Integer> boundaries = safe.iterator(); boundaries.hasNext(); ) {
                int boundary = boundaries.next();
                if (boundary > start
                        && boundary < end
                        && UnicodeText.prohibitsBreak(doc.display, boundary)) boundaries.remove();
            }
            BreakIterator breaks =
                    BreakIterator.getLineInstance(ULocale.forLanguageTag(policy.language));
            breaks.setText(paragraph);
            TreeSet<Integer> normal = new TreeSet<Integer>();
            for (int b = breaks.first(); b != BreakIterator.DONE; b = breaks.next())
                if (safe.contains(start + b)) normal.add(start + b);
            for (int p = start; p < end; p++)
                if (doc.display.charAt(p) == 0x00ad) {
                    normal.remove(p + 1);
                    safe.remove(p + 1);
                    doc.diagnostics.add(
                            new Diagnostic(
                                    Diagnostic.Kind.UNSUPPORTED,
                                    doc.source(p),
                                    doc.source(p + 1),
                                    "当前未实现软连字符断点的可见连字符，该断点不采用"));
                }
            // 段落终点始终结束本行；例如末尾SHY并不是需要显示连字符的自动断点。
            safe.add(end);
            normal.add(end);
            // 段落只整形一次建立前缀；每行只按最终候选范围重整形，不再shape剩余整段。
            BreakPlan plan =
                    new BreakPlan(
                            policy.wrap == TextPolicy.Wrap.EMERGENCY ? safe : normal,
                            whole.prefix(start, end),
                            start);
            int a = start;
            do {
                int first = a < end ? plan.firstAfter(a) : end;
                int b =
                        policy.wrap == TextPolicy.Wrap.NONE || a == end
                                ? end
                                : plan.estimate(a, Math.max(0, width));
                Draft chosen =
                        a == start && b == end
                                ? whole
                                : shape(doc, index, bidi, start, a, b, style, policy, sources);
                if (policy.wrap != TextPolicy.Wrap.NONE && a < end) {
                    while (chosen.width > Math.max(0, width) && b > first) {
                        b = plan.before(b);
                        chosen = shape(doc, index, bidi, start, a, b, style, policy, sources);
                    }
                    // Arabic行端连接形和tabstop可改变局部宽度；只校正相邻断点，保留单个超宽安全范围。
                    while (chosen.width <= Math.max(0, width) && b < end) {
                        int next = plan.firstAfter(b);
                        Draft expanded =
                                shape(doc, index, bidi, start, a, next, style, policy, sources);
                        if (expanded.width > Math.max(0, width)) break;
                        b = next;
                        chosen = expanded;
                    }
                }
                float h =
                        policy.lineHeight > 0
                                ? policy.lineHeight
                                : Math.max(chosen.ascent + chosen.descent, chosen.metricHeight);
                // 字体规格2.5：显式行高按声明布局；真实度量与基线保留，溢出/裁剪归呈现层。
                if (h <= 0) h = style.logicalEm;
                float baseline = y + chosen.ascent;
                List<TextLayout.Glyph> glyphs = new ArrayList<TextLayout.Glyph>();
                List<TextLayout.Cluster> cells = new ArrayList<TextLayout.Cluster>();
                List<TextLayout.Decoration> decorations = new ArrayList<TextLayout.Decoration>();
                Bidi finalDirection = a == b ? null : bidi.setLine(a - start, b - start);
                Map<Cell, Integer> cellIds = new java.util.IdentityHashMap<Cell, Integer>();
                for (Cell c : chosen.cells) {
                    int id = cells.size();
                    cellIds.put(c, id);
                    int ls = doc.logical[c.start],
                            le = doc.logical[c.end],
                            ss = doc.source(c.start),
                            se = doc.sourceEnd(c.end);
                    cells.add(
                            new TextLayout.Cluster(
                                    ls,
                                    le,
                                    ss,
                                    se,
                                    c.glyphStart,
                                    c.glyphEnd,
                                    c.x,
                                    c.advance,
                                    c.style,
                                    finalDirection != null
                                            && (finalDirection.getLevelAt(c.start - a) & 1) != 0));
                    TextDecoration d = c.style.decoration;
                    if (c.style.effects.underline && d.underlineThickness > 0)
                        decorations.add(
                                new TextLayout.Decoration(
                                        TextLayout.Decoration.Kind.UNDERLINE,
                                        c.x + d.underlineStartOffset,
                                        baseline + d.underlineTop,
                                        c.advance - d.underlineStartOffset,
                                        d.underlineThickness,
                                        c.style.color,
                                        ls,
                                        le,
                                        ss,
                                        se));
                    if (c.style.effects.strike && d.strikeThickness > 0)
                        decorations.add(
                                new TextLayout.Decoration(
                                        TextLayout.Decoration.Kind.STRIKE,
                                        c.x,
                                        baseline + d.strikeTop,
                                        c.advance,
                                        d.strikeThickness,
                                        c.style.color,
                                        ls,
                                        le,
                                        ss,
                                        se));
                }
                for (Placed g : chosen.glyphs) {
                    TextStyle paint = g.cell.style;
                    GlyphRasterStyle variant = variant(paint, 1);
                    glyphs.add(
                            new TextLayout.Glyph(
                                    g.source,
                                    paint.logicalEm,
                                    g.id,
                                    doc.logical[g.cell.start],
                                    doc.logical[g.cell.end],
                                    doc.source(g.cell.start),
                                    doc.sourceEnd(g.cell.end),
                                    g.x,
                                    baseline + g.y,
                                    g.advance,
                                    g.yAdvance,
                                    g.color,
                                    g.replacement,
                                    inkBounds(g.source, paint.logicalEm, g.id, variant, doc),
                                    paint.logicalEm,
                                    1,
                                    variant,
                                    paint,
                                    cellIds.get(g.cell)));
                }
                lines.add(
                        new TextLayout.Line(
                                input.text.substring(doc.logical[a], doc.logical[b]),
                                0,
                                (int) Math.floor(y),
                                doc.logical[a],
                                doc.logical[b],
                                input.sourceLeft(doc.logical[a]),
                                b == end
                                        ? input.sourceRight(doc.logical[b])
                                        : input.sourceLeft(doc.logical[b]),
                                chosen.width,
                                baseline,
                                chosen.ascent,
                                chosen.descent,
                                h,
                                glyphs,
                                cells,
                                decorations,
                                (bidi.getParaLevel() & 1) != 0));
                maxWidth = Math.max(maxWidth, chosen.width);
                y += h;
                a = b;
            } while (a < end);
            if (newline < 0) break;
            start = end + 1;
        }
        return new TextLayout(
                ceil(maxWidth),
                ceil(y),
                maxWidth,
                y,
                attributed.baseStyle,
                lines,
                input.text,
                original,
                generation,
                doc.diagnostics,
                input);
    }

    private static final class ObfuscatedGlyph {
        final FontSource source;
        final int id;
        final float advance;

        ObfuscatedGlyph(FontSource source, int id, float advance) {
            this.source = source;
            this.id = id;
            this.advance = advance;
        }
    }

    /** 候选池只来自显式列表；一次性真实HB验证，最多32个source/em池、每池94个ASCII glyph。 */
    private List<ObfuscatedGlyph> obfuscationPool(
            FontSource preferred, float em, UnicodeText doc, TextStyle style) {
        List<FontSource> sources = new ArrayList<FontSource>();
        sources.add(preferred);
        for (FontSource source : resolvedCandidates(style, doc))
            if (!sources.contains(source)) sources.add(source);
        for (FontSource source : sources) {
            if (source.color) continue;
            FaceKey key = new FaceKey(source, em);
            List<ObfuscatedGlyph> cached = obfuscationPools.get(key);
            if (cached != null && !cached.isEmpty()) return cached;
            if (cached != null) continue;
            FontFace f = face(source, em, doc);
            if (f == null) continue;
            List<ObfuscatedGlyph> pool = new ArrayList<ObfuscatedGlyph>();
            for (int cp = 33; cp <= 126; cp++) {
                List<ShapedGlyph> shaped =
                        f.shape(String.valueOf((char) cp), ShapeDirection.LTR, "und");
                if (shaped.size() == 1) {
                    ShapedGlyph g = shaped.get(0);
                    if (g.glyphId != 0 && g.xAdvance > 0 && !color(source, em, g.glyphId, f))
                        pool.add(new ObfuscatedGlyph(source, g.glyphId, g.xAdvance));
                }
            }
            obfuscationPools.put(key, Collections.unmodifiableList(pool));
            trim(obfuscationPools, 32);
            if (!pool.isEmpty()) return pool;
        }
        throw new IllegalStateException("显式候选没有可用于完整簇混淆的实际单色glyph");
    }

    /**
     * 混淆只替换完整安全簇；复杂脚本的连续必要face范围整体替换为单色中性cell。
     * 独立字素优先真实等advance桶；无精确桶/复杂range使用完整cell等比收纳并居中，绝不假称仍保原连接形态。
     * tick/salt由宿主捕获，逻辑advance/断行/source/style保持原计划；不重新整形原字符串。
     */
    public TextLayout obfuscate(TextLayout layout, long tick, long salt) {
        TreeMap<Integer, TextStyle> styles = new TreeMap<Integer, TextStyle>();
        styles.put(0, layout.style);
        for (TextLayout.Line line : layout.lines())
            for (TextLayout.Cluster cluster : line.clusters())
                styles.put(cluster.logicalStart, cluster.style);
        List<StyleSpan> spans = new ArrayList<StyleSpan>();
        List<Integer> starts = new ArrayList<Integer>(styles.keySet());
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i),
                    end = i + 1 < starts.size() ? starts.get(i + 1) : layout.logicalText.length();
            if (start < end) spans.add(new StyleSpan(start, end, styles.get(start)));
        }
        TextInput input =
                layout.input == null ? TextInput.string(layout.logicalText) : layout.input;
        AttributedText requests =
                new AttributedText(input, layout.originalText, layout.style, spans);
        return obfuscate(layout, requests, tick, salt);
    }

    public TextLayout obfuscate(TextLayout layout, AttributedText request, long tick, long salt) {
        check();
        UnicodeText doc = new UnicodeText(request.input);
        List<TextLayout.Line> lines = new ArrayList<TextLayout.Line>();
        for (TextLayout.Line line : layout.lines()) {
            List<TextLayout.Glyph> output = new ArrayList<TextLayout.Glyph>();
            List<TextLayout.Cluster> clusters = new ArrayList<TextLayout.Cluster>();
            List<TextLayout.Cluster> original = line.clusters();
            int glyphCursor = 0;
            for (int ci = 0; ci < original.size(); ) {
                TextLayout.Cluster cell = original.get(ci);
                int end = ci + 1;
                boolean replace = request.styleAt(cell.logicalStart).effects.obfuscated;
                boolean complex =
                        cell.glyphIndexEnd > cell.glyphIndexStart
                                && !line.glyphs()
                                        .get(cell.glyphIndexStart)
                                        .source
                                        .supportedScripts()
                                        .isEmpty();
                if (complex) {
                    FontSource source = line.glyphs().get(cell.glyphIndexStart).source;
                    while (end < original.size()) {
                        TextLayout.Cluster next = original.get(end);
                        if (next.glyphIndexStart == next.glyphIndexEnd
                                || line.glyphs().get(next.glyphIndexStart).source != source) break;
                        end++;
                    }
                }
                int glyphEnd = original.get(end - 1).glyphIndexEnd;
                if (complex) {
                    TextLayout.Cluster leading = cell;
                    for (int j = ci + 1; j < end; j++)
                        if (original.get(j).logicalStart < leading.logicalStart)
                            leading = original.get(j);
                    replace = request.styleAt(leading.logicalStart).effects.obfuscated;
                    cell = leading;
                    int rangeEnd = leading.sourceEnd;
                    for (int j = ci; j < end; j++)
                        rangeEnd = Math.max(rangeEnd, original.get(j).sourceEnd);
                    for (int j = ci; j < end; j++)
                        if (request.styleAt(original.get(j).logicalStart).effects.obfuscated
                                != replace) {
                            doc.diagnostics.add(
                                    new Diagnostic(
                                            Diagnostic.Kind.STYLE_COALESCED,
                                            leading.sourceStart,
                                            rangeEnd,
                                            "复杂脚本必要范围混淆状态使用逻辑首样式"));
                            break;
                        }
                }
                if (replace && cell.advance > 0 && cell.glyphIndexStart < cell.glyphIndexEnd) {
                    float width = 0;
                    int logicalStart = Integer.MAX_VALUE,
                            logicalEnd = 0,
                            sourceStart = Integer.MAX_VALUE,
                            sourceEnd = 0;
                    float x = cell.x;
                    for (int j = ci; j < end; j++) {
                        TextLayout.Cluster c = original.get(j);
                        width += c.advance;
                        logicalStart = Math.min(logicalStart, c.logicalStart);
                        logicalEnd = Math.max(logicalEnd, c.logicalEnd);
                        sourceStart = Math.min(sourceStart, c.sourceStart);
                        sourceEnd = Math.max(sourceEnd, c.sourceEnd);
                        x = Math.min(x, c.x);
                    }
                    TextLayout.Glyph first = line.glyphs().get(cell.glyphIndexStart);
                    List<ObfuscatedGlyph>
                            pool =
                                    obfuscationPool(
                                            first.source, cell.style.logicalEm, doc, cell.style),
                            equal = new ArrayList<ObfuscatedGlyph>();
                    float target = width;
                    for (ObfuscatedGlyph candidate : pool)
                        if (candidate.advance == target) equal.add(candidate);
                    List<ObfuscatedGlyph> selection = equal.isEmpty() ? pool : equal;
                    long seed = tick * 0x9e3779b97f4a7c15L + salt + logicalStart * 31L + logicalEnd;
                    seed ^= seed >>> 33;
                    ObfuscatedGlyph chosen =
                            selection.get((int) Math.floorMod(seed, (long) selection.size()));
                    float fit = Math.min(1, width / (chosen.advance));
                    float ratio = fit;
                    GlyphRasterStyle variant = variant(cell.style, ratio);
                    InkBounds raw =
                            inkBounds(chosen.source, cell.style.logicalEm, chosen.id, variant, doc);
                    InkBounds mapped =
                            raw.known
                                    ? new InkBounds(
                                            true,
                                            raw.left * ratio,
                                            raw.top * ratio,
                                            raw.right * ratio,
                                            raw.bottom * ratio)
                                    : InkBounds.UNKNOWN;
                    int begin = output.size();
                    output.add(
                            new TextLayout.Glyph(
                                    chosen.source,
                                    cell.style.logicalEm,
                                    chosen.id,
                                    logicalStart,
                                    logicalEnd,
                                    sourceStart,
                                    sourceEnd,
                                    x + (width - (chosen.advance) * fit) / 2,
                                    line.baseline,
                                    width,
                                    0,
                                    false,
                                    false,
                                    mapped,
                                    cell.style.logicalEm * fit,
                                    ratio,
                                    variant,
                                    cell.style,
                                    clusters.size()));
                    clusters.add(
                            new TextLayout.Cluster(
                                    logicalStart,
                                    logicalEnd,
                                    sourceStart,
                                    sourceEnd,
                                    begin,
                                    output.size(),
                                    x,
                                    width,
                                    cell.style,
                                    cell.rtl));
                } else {
                    for (int j = ci; j < end; j++) {
                        TextLayout.Cluster c = original.get(j);
                        int begin = output.size();
                        for (int g = c.glyphIndexStart; g < c.glyphIndexEnd; g++) {
                            TextLayout.Glyph a = line.glyphs().get(g);
                            output.add(
                                    new TextLayout.Glyph(
                                            a.source,
                                            a.size,
                                            a.glyphId,
                                            a.logicalStart,
                                            a.logicalEnd,
                                            a.sourceStart,
                                            a.sourceEnd,
                                            a.x,
                                            a.y,
                                            a.xAdvance,
                                            a.yAdvance,
                                            a.color,
                                            a.replacement,
                                            a.inkBounds,
                                            a.logicalEm,
                                            a.rasterToLayout,
                                            a.rasterStyle,
                                            a.resolvedStyle,
                                            clusters.size()));
                        }
                        clusters.add(
                                new TextLayout.Cluster(
                                        c.logicalStart,
                                        c.logicalEnd,
                                        c.sourceStart,
                                        c.sourceEnd,
                                        begin,
                                        output.size(),
                                        c.x,
                                        c.advance,
                                        c.style,
                                        c.rtl));
                    }
                }
                glyphCursor = glyphEnd;
                ci = end;
            }
            if (glyphCursor != line.glyphs().size())
                throw new IllegalStateException("混淆簇未覆盖全部glyph");
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x,
                            line.y,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance,
                            line.baseline,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            output,
                            clusters,
                            line.decorations(),
                            line.paragraphRtl));
        }
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>(layout.diagnostics());
        diagnostics.addAll(doc.diagnostics);
        return new TextLayout(
                layout.width,
                layout.height,
                layout.advanceWidth,
                layout.advanceHeight,
                layout.style,
                lines,
                layout.logicalText,
                layout.originalText,
                layout.generation,
                diagnostics,
                layout.input);
    }

    private static GlyphRasterStyle variant(TextStyle style, float ratio) {
        return GlyphRasterStyle.NORMAL;
    }

    /** 不shape、不render，同glyphId实际采样边界映回逻辑坐标。 */
    public TextLayout sample(TextLayout layout, RasterSampling sampling) {
        check();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(sampling, "sampling");
        UnicodeText doc =
                new UnicodeText(
                        layout.input == null ? TextInput.string(layout.logicalText) : layout.input);
        List<TextLayout.Line> lines = new ArrayList<TextLayout.Line>();
        for (TextLayout.Line line : layout.lines()) {
            List<TextLayout.Glyph> glyphs = new ArrayList<TextLayout.Glyph>();
            for (TextLayout.Glyph g : line.glyphs()) {
                float em = g.logicalEm * sampling.pixelsPerLayoutUnit;
                if (!Float.isFinite(em) || em <= 0 || em > 32767)
                    throw new IllegalArgumentException("采样em超过原生范围");
                float ratio = g.logicalEm / em;
                TextStyle style = g.resolvedStyle == null ? layout.style : g.resolvedStyle;
                GlyphRasterStyle variant = variant(style, ratio);
                InkBounds raw = inkBounds(g.source, em, g.glyphId, variant, doc);
                InkBounds mapped =
                        raw.known
                                ? new InkBounds(
                                        true,
                                        raw.left * ratio,
                                        raw.top * ratio,
                                        raw.right * ratio,
                                        raw.bottom * ratio)
                                : InkBounds.UNKNOWN;
                glyphs.add(
                        new TextLayout.Glyph(
                                g.source,
                                em,
                                g.glyphId,
                                g.logicalStart,
                                g.logicalEnd,
                                g.sourceStart,
                                g.sourceEnd,
                                g.x,
                                g.y,
                                g.xAdvance,
                                g.yAdvance,
                                g.color,
                                g.replacement,
                                mapped,
                                g.logicalEm,
                                ratio,
                                variant,
                                style,
                                g.clusterIndex));
            }
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x,
                            line.y,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance,
                            line.baseline,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            glyphs,
                            line.clusters(),
                            line.decorations(),
                            line.paragraphRtl));
        }
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>(layout.diagnostics());
        diagnostics.addAll(doc.diagnostics);
        return new TextLayout(
                layout.width,
                layout.height,
                layout.advanceWidth,
                layout.advanceHeight,
                layout.style,
                lines,
                layout.logicalText,
                layout.originalText,
                layout.generation,
                diagnostics,
                layout.input);
    }

    private static int ceil(float value) {
        if (!Float.isFinite(value) || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("文本布局尺寸超出 scene 范围");
        return (int) Math.ceil(Math.max(0, value));
    }

    private Draft shape(
            UnicodeText doc,
            UnitIndex units,
            Bidi paragraphBidi,
            int paragraphStart,
            int start,
            int end,
            TextStyle style,
            TextPolicy policy,
            List<FontSource> sources) {
        Draft draft = new Draft();
        if (start == end) {
            defaultMetrics(draft, style, doc, sources);
            return draft;
        }
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
                boolean special =
                        doc.display.charAt(s) == '	'
                                || !UnicodeText.visible(doc.display, s, e)
                                || u.replacement
                                || u.inlineBox != null
                                || u.source == null;
                Span last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
                if (!special
                        && last != null
                        && !last.special
                        && last.unit.source.equals(u.source)
                        && last.unit.script == u.script
                        && last.unit.shapeStyle.sameFont(u.shapeStyle)
                        && last.end == s) last.end = e;
                else spans.add(new Span(s, e, u, special));
            }
            if (run.isOddRun()) Collections.reverse(spans);
            for (Span span : spans) {
                Unit u = span.unit;
                style = u.shapeStyle;
                sources = candidates(style);
                int glyphBegin = draft.glyphs.size();
                float spanPen = pen;
                if (u.inlineBox != null) {
                    // 中性对象参与当前视觉 run，但内部内容不进入 HB 或字体回退。
                    InlineBox box = u.inlineBox;
                    draft.cells.add(
                            new Cell(
                                    span.start,
                                    span.end,
                                    pen,
                                    box.width,
                                    u.style,
                                    glyphBegin,
                                    glyphBegin));
                    // prefix 对无字形 advance 使用同一 Gap 事实，只累计一次对象宽度。
                    draft.spaces.add(new Gap(span.start, span.end, box.width));
                    draft.ascent = Math.max(draft.ascent, box.baseline);
                    draft.descent = Math.max(draft.descent, box.height - box.baseline);
                    pen += box.width;
                    continue;
                }
                if (doc.display.charAt(span.start) == '	') {
                    float tab =
                            policy.tabSize > 0
                                    ? policy.tabSize
                                    : spaceAdvance(style, doc, sources, policy) * 4;
                    float advance = (float) ((Math.floor(pen / tab) + 1) * tab - pen);
                    draft.cells.add(
                            new Cell(
                                    span.start,
                                    span.end,
                                    pen,
                                    advance,
                                    u.style,
                                    glyphBegin,
                                    glyphBegin));
                    draft.spaces.add(new Gap(span.start, span.end, advance));
                    pen += advance;
                    continue;
                }
                if (!UnicodeText.visible(doc.display, span.start, span.end)) continue;
                if (u.source == null) {
                    float advance = style.logicalEm;
                    draft.cells.add(
                            new Cell(
                                    span.start,
                                    span.end,
                                    pen,
                                    advance,
                                    u.style,
                                    glyphBegin,
                                    glyphBegin));
                    draft.spaces.add(new Gap(span.start, span.end, advance));
                    pen += advance;
                    continue;
                }
                FontFace f = face(u.source, style.logicalEm, doc);
                if (f == null) throw new IllegalStateException("已匹配字体无法重开");
                draft.metrics(f.metrics());
                if (u.replacement) {
                    // U+FFFD 不可用时使用同列表第一有效 face 的 .notdef；身份仍可真实光栅。
                    List<ShapedGlyph> replacement =
                            f.shape(
                                    u.replacementId == 0 ? "\uffff" : "\ufffd",
                                    ShapeDirection.LTR,
                                    policy.language);
                    ShapedGlyph g = replacement.get(0);
                    draft.glyphs.add(
                            new Placed(
                                    u.source,
                                    u.replacementId,
                                    span.start,
                                    span.end,
                                    pen + g.xOffset,
                                    -g.yOffset,
                                    g.xAdvance,
                                    -g.yAdvance,
                                    false,
                                    true));
                    pen += g.xAdvance;
                    pen = finishSpan(draft, units, doc, glyphBegin, spanPen, pen, u.script);
                    continue;
                }
                List<ShapedGlyph> shaped =
                        f.shape(
                                context,
                                span.start - start,
                                span.end - start,
                                run.isOddRun() ? ShapeDirection.RTL : ShapeDirection.LTR,
                                policy.language,
                                UScript.getShortName(u.script),
                                span.start == start,
                                span.end == end);
                TreeSet<Integer> clusters = new TreeSet<Integer>();
                clusters.add(span.end - start);
                for (ShapedGlyph g : shaped) clusters.add(g.cluster);
                for (ShapedGlyph g : shaped) {
                    int c = start + g.cluster, next = start + clusters.higher(g.cluster);
                    int clusterStart = c, clusterEnd = next;
                    // 视觉命中始终返回完整 ICU 字素；HB 连字还可覆盖多个字素。
                    Unit atStart = unitStarts.floorEntry(c).getValue();
                    Unit atEnd = unitStarts.lowerEntry(next).getValue();
                    clusterStart = atStart.start;
                    clusterEnd = atEnd.end;
                    boolean draw = UnicodeText.visible(doc.display, c, next);
                    if (draw)
                        draft.glyphs.add(
                                new Placed(
                                        u.source,
                                        g.glyphId,
                                        clusterStart,
                                        clusterEnd,
                                        pen + g.xOffset,
                                        -g.yOffset,
                                        g.xAdvance,
                                        -g.yAdvance,
                                        u.source.color
                                                && color(u.source, style.logicalEm, g.glyphId, f),
                                        false));
                    else if (g.xAdvance != 0)
                        draft.spaces.add(new Gap(clusterStart, clusterEnd, g.xAdvance));
                    pen += g.xAdvance;
                }
                pen = finishSpan(draft, units, doc, glyphBegin, spanPen, pen, u.script);
            }
        }
        draft.width = pen;
        if (draft.ascent + draft.descent == 0) defaultMetrics(draft, style, doc, sources);
        return draft;
    }

    /** 必要连接范围内保留HB墨迹相对位置，完整安全簇只增加一次advance。 */
    private float finishSpan(
            Draft draft,
            UnitIndex units,
            UnicodeText doc,
            int begin,
            float spanPen,
            float pen,
            int script) {
        LinkedHashMap<Integer, Cell> grouped = new LinkedHashMap<Integer, Cell>();
        float nativePen = spanPen;
        for (int i = begin; i < draft.glyphs.size(); i++) {
            Placed g = draft.glyphs.get(i);
            Cell cell = grouped.get(g.start);
            if (cell == null) {
                TextStyle paint = units.starts.floorEntry(g.start).getValue().style;
                TextStyle shape = units.starts.floorEntry(g.start).getValue().shapeStyle;
                paint =
                        new TextStyle(
                                shape.font,
                                shape.logicalEm,
                                paint.color,
                                shape.candidates(),
                                paint.effects,
                                paint.decoration,
                                shape.fontStyle);
                cell = new Cell(g.start, g.end, nativePen, 0, paint, i, i);
                grouped.put(g.start, cell);
            }
            cell.end = Math.max(cell.end, g.end);
            cell.glyphEnd = i + 1;
            cell.advance += g.advance;
            g.cell = cell;
            nativePen += g.advance;
        }
        List<Cell> cells = new ArrayList<Cell>(grouped.values());
        for (int i = 1; i < cells.size(); ) {
            Cell prev = cells.get(i - 1), next = cells.get(i);
            if (prev.start < next.end && next.start < prev.end) {
                int leading = Math.min(prev.start, next.start);
                prev.start = leading;
                prev.end = Math.max(prev.end, next.end);
                prev.advance += next.advance;
                prev.glyphEnd = next.glyphEnd;
                TextStyle requested = units.starts.floorEntry(leading).getValue().style;
                prev.style =
                        new TextStyle(
                                prev.style.font,
                                prev.style.logicalEm,
                                requested.color,
                                prev.style.candidates(),
                                requested.effects,
                                requested.decoration,
                                prev.style.fontStyle);
                for (int g = next.glyphStart; g < next.glyphEnd; g++)
                    draft.glyphs.get(g).cell = prev;
                cells.remove(i);
            } else i++;
        }
        float extra = 0;
        for (Cell cell : cells) {
            float delta = 0;
            for (Unit unit : units.starts.subMap(cell.start, true, cell.end, false).values())
                if (!unit.style.effects.equals(cell.style.effects)
                        || unit.style.color != cell.style.color
                        || !unit.style.decoration.equals(cell.style.decoration)) {
                    boolean exists = false;
                    for (Diagnostic d : doc.diagnostics)
                        if (d.kind == Diagnostic.Kind.STYLE_COALESCED
                                && d.sourceStart == doc.source(cell.start)
                                && d.sourceEnd == doc.sourceEnd(cell.end)) {
                            exists = true;
                            break;
                        }
                    if (!exists)
                        doc.diagnostics.add(
                                new Diagnostic(
                                        Diagnostic.Kind.STYLE_COALESCED,
                                        doc.source(cell.start),
                                        doc.sourceEnd(cell.end),
                                        "HB安全簇内部效果统一使用逻辑首样式"));
                    break;
                }
            cell.x += extra;
            cell.advance += delta;
            if (!UnicodeText.complex(script))
                for (int i = cell.glyphStart; i < cell.glyphEnd; i++)
                    draft.glyphs.get(i).x += extra;
            if (delta != 0) draft.glyphs.get(cell.glyphEnd - 1).advance += delta;
            extra += delta;
            draft.cells.add(cell);
        }
        return pen + extra;
    }

    private static final class Cell {
        int start, end, glyphEnd;
        final int glyphStart;
        float x, advance;
        TextStyle style;

        Cell(
                int start,
                int end,
                float x,
                float advance,
                TextStyle style,
                int glyphStart,
                int glyphEnd) {
            this.start = start;
            this.end = end;
            this.x = x;
            this.advance = advance;
            this.style = style;
            this.glyphStart = glyphStart;
            this.glyphEnd = glyphEnd;
        }
    }

    private void defaultMetrics(
            Draft draft, TextStyle style, UnicodeText doc, List<FontSource> sources) {
        for (FontSource source : resolvedCandidates(style, doc)) {
            FontFace f = face(source, style.logicalEm, doc);
            if (f != null) {
                draft.metrics(f.metrics());
                return;
            }
        }
        draft.ascent = style.logicalEm;
        draft.metricHeight = style.logicalEm;
    }

    private float spaceAdvance(
            TextStyle style, UnicodeText doc, List<FontSource> sources, TextPolicy policy) {
        for (FontSource source : resolvedCandidates(style, doc)) {
            FontFace f = face(source, style.logicalEm, doc);
            if (f != null && f.glyphIndex(32) != 0) {
                float advance = 0;
                for (ShapedGlyph g : f.shape(" ", ShapeDirection.LTR, policy.language))
                    advance += g.xAdvance;
                if (advance > 0) return advance;
            }
        }
        return style.logicalEm / 2f;
    }

    @Override
    public void close() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("FontTextService belongs to its creating thread");
        if (closed) return;
        closed = true;
        layouts.clear();
        assemblyItalic.clear();
        colors.clear();
        ink.clear();
        failures.clear();
        faces.clear();
        obfuscationPools.clear();
        named.clear();
        invalidation.clear();
        defaults = Collections.emptyList();
        nativeService.close();
    }

    private static final class MatchKey {
        final List<FontSource> sources;
        final club.heiqi.uilib.ui.scene.text.FontStyle request;
        final float em;
        final int script;
        final boolean emoji;
        final ShapeDirection direction;
        final String text;

        MatchKey(
                List<FontSource> sources,
                club.heiqi.uilib.ui.scene.text.FontStyle request,
                float em,
                int script,
                boolean emoji,
                ShapeDirection direction,
                String text) {
            this.sources = sources;
            this.request = request;
            this.em = em;
            this.script = script;
            this.emoji = emoji;
            this.direction = direction;
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MatchKey)) return false;
            MatchKey k = (MatchKey) o;
            return request.equals(k.request)
                    && em == k.em
                    && script == k.script
                    && emoji == k.emoji
                    && direction == k.direction
                    && text.equals(k.text)
                    && sources.equals(k.sources);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sources, request, em, script, emoji, direction, text);
        }
    }

    private static class FaceKey {
        final FontSource source;
        final float size;

        FaceKey(FontSource source, float size) {
            this.source = source;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            return o != null
                    && getClass() == o.getClass()
                    && source.equals(((FaceKey) o).source)
                    && size == ((FaceKey) o).size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, size);
        }
    }

    private static final class InkKey extends FaceKey {
        final int glyph;
        final GlyphRasterStyle style;

        InkKey(FontSource source, float size, int glyph, GlyphRasterStyle style) {
            super(source, size);
            this.glyph = glyph;
            this.style = style;
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o)
                    && glyph == ((InkKey) o).glyph
                    && style.equals(((InkKey) o).style);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), glyph, style);
        }
    }

    private static final class ColorKey extends FaceKey {
        final int glyph;

        ColorKey(FontSource source, float size, int glyph) {
            super(source, size);
            this.glyph = glyph;
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o) && glyph == ((ColorKey) o).glyph;
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + glyph;
        }
    }

    private static final class LayoutKey {
        final String text;
        final List<FontSource> sources;
        final float size;
        final int width;
        final TextPolicy policy;

        LayoutKey(String text, List<FontSource> sources, float size, int width, TextPolicy policy) {
            this.text = text;
            this.sources = sources;
            this.size = size;
            this.width = width;
            this.policy = policy;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LayoutKey)) return false;
            LayoutKey k = (LayoutKey) o;
            return size == k.size
                    && width == k.width
                    && text.equals(k.text)
                    && sources.equals(k.sources)
                    && policy.equals(k.policy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, sources, size, width, policy);
        }
    }

    private static final class UnitIndex {
        final TreeMap<Integer, Unit> starts = new TreeMap<Integer, Unit>();

        UnitIndex(List<Unit> units) {
            for (Unit unit : units) starts.put(unit.start, unit);
        }
    }

    /** 安全点的累计真实advance；二分只选初步断点，最终行仍真实shape校验。 */
    private static final class BreakPlan {
        final int[] points;
        final double[] prefix;
        final int base;

        BreakPlan(TreeSet<Integer> points, double[] prefix, int base) {
            this.points = new int[points.size()];
            int i = 0;
            for (int point : points) this.points[i++] = point;
            this.prefix = prefix;
            this.base = base;
        }

        int afterIndex(int point) {
            int index = Arrays.binarySearch(points, point);
            return index >= 0 ? index + 1 : -index - 1;
        }

        int firstAfter(int point) {
            return points[Math.min(afterIndex(point), points.length - 1)];
        }

        int before(int point) {
            int index = Arrays.binarySearch(points, point);
            return points[index >= 0 ? index - 1 : -index - 2];
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
        final int start;
        int end;
        final Unit unit;
        final boolean special;

        Span(int start, int end, Unit unit, boolean special) {
            this.start = start;
            this.end = end;
            this.unit = unit;
            this.special = special;
        }
    }

    private static final class Gap {
        final int start, end;
        final float advance;

        Gap(int start, int end, float advance) {
            this.start = start;
            this.end = end;
            this.advance = advance;
        }
    }

    private static final class Placed {
        final FontSource source;
        final int id, start, end;
        float x, advance;
        final float y, yAdvance;
        final boolean color, replacement;
        Cell cell;

        Placed(
                FontSource source,
                int id,
                int start,
                int end,
                float x,
                float y,
                float advance,
                float yAdvance,
                boolean color,
                boolean replacement) {
            this.source = source;
            this.id = id;
            this.start = start;
            this.end = end;
            this.x = x;
            this.y = y;
            this.advance = advance;
            this.yAdvance = yAdvance;
            this.color = color;
            this.replacement = replacement;
        }
    }

    private static final class Draft {
        final List<Placed> glyphs = new ArrayList<Placed>();
        final List<Gap> spaces = new ArrayList<Gap>();
        final List<Cell> cells = new ArrayList<Cell>();
        float width, ascent, descent, metricHeight;

        void metrics(FontMetrics m) {
            ascent = Math.max(ascent, m.ascent);
            descent = Math.max(descent, m.descent);
            metricHeight = Math.max(metricHeight, m.lineHeight);
        }

        double[] prefix(int start, int end) {
            double[] prefix = new double[end - start + 1];
            for (Placed g : glyphs) prefix[g.end - start] += g.advance;
            for (Gap gap : spaces) prefix[gap.end - start] += gap.advance;
            for (int i = 1; i < prefix.length; i++) prefix[i] += prefix[i - 1];
            return prefix;
        }
    }
}
