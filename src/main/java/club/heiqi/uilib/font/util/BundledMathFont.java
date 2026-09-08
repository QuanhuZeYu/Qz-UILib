package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.ProceduralAccentShape;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathFontParameters;
import club.heiqi.uilib.font.latex.layout.MathFontSupport;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction;
import club.heiqi.uilib.font.latex.layout.MathGlyphMetrics;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.latex.layout.MathStretchAxis;
import club.heiqi.uilib.font.latex.layout.ProceduralAccentSpec;

/**
 * 固定 STIX 物理 face 与 SHA 绑定的离线 MATH 数据。只缓存不可变资源，不缓存布局结果。
 * 不提供未导出的 MathKernInfo；调用方必须在目录 generation 屏障内使用此能力。
 * 已解析的数学字形包含真实样式，绘制时不得再次斜切或伪粗体。
 */
public final class BundledMathFont implements MathFontSupport {
    private static final String ROOT = "/assets/qz_uilib/fonts/math/stix-two/";
    private static final String FONT_FILE = "STIXTwoMath-Regular.otf";
    private static final String FONT_SHA = "95bc2729e41faf93b0bcae9e96c4dc4da45855067fd0581e621e30734fe8d90b";
    private static final String DATA_SHA = "5bb2eb12c65fced8fe6dba2f679f369c89090b79bb9a5129739df7e7929a1eca";
    private static final String PROFILE = "stix-two-math-v1";
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private final Font physicalFont;
    // JSON 树仅在构造时解析、验证，之后既不修改也不向外暴露。
    private final JsonObject data;
    private final int unitsPerEm;
    private final int glyphCount;
    private final String faceKey;

    private static final class Holder {
        private static final BundledMathFont INSTANCE = load();
    }

    /** 解析固定资源的不可变单例；生产注册必须经 FontRegistry candidate 发布。 */
    public static BundledMathFont shared() { return Holder.INSTANCE; }

    private static BundledMathFont load() {
        try {
            byte[] fontBytes = resource(FONT_FILE);
            byte[] dataBytes = resource("math-data.json");
            JsonObject manifest = json(resource("source-manifest.json"));
            require(manifest.get("schemaVersion").getAsInt() == 1, "manifest schema");
            require(manifest.get("faceIndex").getAsInt() == 0, "manifest face index");
            require("2.12 b168".equals(manifest.get("version").getAsString()), "font version");
            require("STIX Two Math".equals(manifest.get("family").getAsString()), "font family");
            require(!manifest.get("modified").getAsBoolean(), "modified font");
            verifyResource(manifest, FONT_FILE, fontBytes, FONT_SHA);
            verifyResource(manifest, "math-data.json", dataBytes, DATA_SHA);
            JsonObject data = json(dataBytes);
            require(data.get("schemaVersion").getAsInt() == 1, "data schema");
            require(data.get("faceIndex").getAsInt() == 0, "data face index");
            require(FONT_SHA.equals(data.get("fontSha256").getAsString()), "data font binding");
            require(PROFILE.equals(data.get("profile").getAsString()), "data profile");
            // createFont 从字节显式创建物理 face，绝不按 family 名查系统逻辑字体。
            Font physical = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(fontBytes));
            return new BundledMathFont(physical, data);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load pinned STIX math font", failure);
        }
    }

    private BundledMathFont(Font physicalFont, JsonObject data) {
        this.physicalFont = physicalFont;
        this.data = data;
        unitsPerEm = data.get("unitsPerEm").getAsInt();
        glyphCount = data.get("glyphCount").getAsInt();
        faceKey = "sha256:" + FONT_SHA + ":face:0:profile:" + PROFILE;
        require(unitsPerEm == 1000 && glyphCount == physicalFont.getNumGlyphs(), "physical face metrics");
        require(data.getAsJsonArray("advanceWidths").size() == glyphCount, "advance count");
        for (JsonElement advance : data.getAsJsonArray("advanceWidths")) {
            require(advance.getAsInt() >= 0, "negative advance");
        }
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("cmap").entrySet()) {
            int cp = Integer.parseInt(entry.getKey());
            require(Character.isValidCodePoint(cp) && !(cp >= 0xd800 && cp <= 0xdfff), "cmap codepoint");
            require(validGlyph(entry.getValue().getAsInt()), "cmap glyph");
        }
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("deviceAdjustments").entrySet()) {
            JsonObject device = entry.getValue().getAsJsonObject();
            int start = device.get("startPpem").getAsInt();
            int end = device.get("endPpem").getAsInt();
            require(start > 0 && end >= start && device.getAsJsonArray("deltaPixels").size() == end - start + 1,
                    "device range");
        }
        // 在 candidate 准备阶段验证所有配方，不能等布局时才发现无效部件。
        for (MathStretchAxis axis : MathStretchAxis.values()) {
            for (Map.Entry<String, JsonElement> entry : constructions(axis).entrySet()) {
                require(validGlyph(Integer.parseInt(entry.getKey())), "construction glyph");
                construction(ref(Integer.parseInt(entry.getKey())), axis, unitsPerEm);
            }
        }
        constants(unitsPerEm);
    }

    public String getFaceKey() { return faceKey; }

    /** 内容身份必须与本资源一致；未知 face、程序形状和 .notdef 返回 null。 */
    public Font getPhysicalFont(MathGlyphRef glyph, int sizePx) {
        requireSize(sizePx);
        return owns(glyph) ? physicalFont.deriveFont(Font.PLAIN, (float) sizePx) : null;
    }

    @Override
    public MathGlyphRef resolve(int codepoint, MathFontStyle style, FontType weight) {
        if (style == null || weight == null) { throw new IllegalArgumentException("style/weight must not be null"); }
        if (!Character.isValidCodePoint(codepoint) || (codepoint >= 0xd800 && codepoint <= 0xdfff)) { return null; }
        int mapped = alphabetCodepoint(codepoint, style, weight);
        JsonElement glyph = data.getAsJsonObject("cmap").get(Integer.toString(mapped));
        if (glyph == null || !validGlyph(glyph.getAsInt()) || !physicalFont.canDisplay(mapped)) { return null; }
        // 对 supplementary 字符仍按码点输入；后续 measure/raster 均明确走这个物理 gid。
        int awtGlyph = physicalFont.createGlyphVector(FRC, new String(Character.toChars(mapped))).getGlyphCode(0);
        return awtGlyph == glyph.getAsInt() ? ref(awtGlyph) : null;
    }

    @Override
    public MathGlyphMetrics measure(MathGlyphRef glyph, int effectiveSizePx) {
        requireSize(effectiveSizePx);
        if (glyph != null && glyph.getKind() == MathGlyphRef.Kind.PROCEDURAL_ACCENT) {
            ProceduralAccentSpec spec = glyph.getProceduralAccent();
            if (spec.getProfileRevision() != ProceduralAccentShape.PROFILE_REVISION
                    || spec.getStrokeUnits() >= spec.getWidthUnits() || spec.getStrokeUnits() >= spec.getHeightUnits()) {
                return null;
            }
            // 与 GlyphGenerator 使用同一中心路径/描边，按有效字号测量，不用光栅倍率反算逻辑 ink。
            Rectangle2D ink = ProceduralAccentShape.create(spec, effectiveSizePx).getBounds2D();
            float width = (float) (spec.getWidthUnits() * (effectiveSizePx / 65536.0));
            return new MathGlyphMetrics(width, (float) ink.getMinX(), (float) ink.getMinY(),
                    (float) ink.getMaxX(), (float) ink.getMaxY(), 0, true, width / 2);
        }
        Font font = getPhysicalFont(glyph, effectiveSizePx);
        if (font == null) { return null; }
        int id = glyph.getGlyphId();
        Rectangle2D ink = font.createGlyphVector(FRC, new int[] { id }).getGlyphVisualBounds(0).getBounds2D();
        JsonElement attachment = data.getAsJsonObject("topAccentAttachments").get(Integer.toString(id));
        return new MathGlyphMetrics(scale(data.getAsJsonArray("advanceWidths").get(id).getAsInt(), effectiveSizePx),
                (float) ink.getMinX(), (float) ink.getMinY(), (float) ink.getMaxX(), (float) ink.getMaxY(),
                value("italicCorrections", Integer.toString(id), effectiveSizePx), attachment != null,
                attachment == null ? 0 : value("topAccentAttachments", Integer.toString(id), effectiveSizePx));
    }

    @Override
    public MathFontParameters constants(int effectiveSizePx) {
        requireSize(effectiveSizePx);
        return new MathFontParameters(value("constants", "AxisHeight", effectiveSizePx),
                value("constants", "RadicalRuleThickness", effectiveSizePx),
                value("constants", "RadicalVerticalGap", effectiveSizePx),
                value("constants", "RadicalDisplayStyleVerticalGap", effectiveSizePx),
                value("constants", "RadicalExtraAscender", effectiveSizePx),
                data.getAsJsonObject("constants").get("RadicalDegreeBottomRaisePercent").getAsInt(),
                value("constants", "AccentBaseHeight", effectiveSizePx));
    }

    @Override
    public MathGlyphConstruction construction(MathGlyphRef glyph, MathStretchAxis axis, int effectiveSizePx) {
        requireSize(effectiveSizePx);
        if (axis == null) { throw new IllegalArgumentException("axis must not be null"); }
        if (!owns(glyph)) { return null; }
        JsonObject recipe = constructions(axis).getAsJsonObject(Integer.toString(glyph.getGlyphId()));
        if (recipe == null) { return null; }
        List<MathGlyphConstruction.Variant> variants = new ArrayList<MathGlyphConstruction.Variant>();
        for (JsonElement item : recipe.getAsJsonArray("variants")) {
            JsonObject variant = item.getAsJsonObject();
            variants.add(new MathGlyphConstruction.Variant(checkedRef(variant.get("glyphId").getAsInt()),
                    scale(variant.get("advance").getAsInt(), effectiveSizePx)));
        }
        MathGlyphConstruction.Assembly assembly = null;
        if (!recipe.get("assembly").isJsonNull()) {
            JsonObject source = recipe.getAsJsonObject("assembly");
            if (!source.get("validConnectorLengths").getAsBoolean()) {
                // 固定原件 U+0305/gid746 含 connector > fullAdvance，只禁用该 assembly。
                // 保留有序原生 variants；不篡改源数据，也不因这一配方拒绝整个 font。
                require(axis == MathStretchAxis.HORIZONTAL && glyph.getGlyphId() == 746, "unexpected unsafe assembly");
            } else {
                List<MathGlyphConstruction.Part> parts = new ArrayList<MathGlyphConstruction.Part>();
                for (JsonElement item : source.getAsJsonArray("parts")) {
                    JsonObject part = item.getAsJsonObject();
                    parts.add(new MathGlyphConstruction.Part(checkedRef(part.get("glyphId").getAsInt()),
                            scale(part.get("startConnector").getAsInt(), effectiveSizePx),
                            scale(part.get("endConnector").getAsInt(), effectiveSizePx),
                            scale(part.get("fullAdvance").getAsInt(), effectiveSizePx), part.get("extender").getAsBoolean()));
                }
                float overlap = scale(data.get("minConnectorOverlap").getAsInt(), effectiveSizePx);
                // horizontal gid1510/1514/1532 的原始配方有相邻 startConnector=0，
                // 无法满足全局 min overlap。保留 variants，只让这些 assembly 缺省；
                // 不把全局最小重叠偷偷降低到 0，也不拒绝圆括号/根号等有效配方。
                if (supportsOverlap(parts, overlap)) {
                    // 此固定 schema 的 assembly correction 没有 Device 表。
                    assembly = new MathGlyphConstruction.Assembly(parts, overlap,
                            scale(source.get("italicCorrection").getAsInt(), effectiveSizePx));
                } else {
                    int id = glyph.getGlyphId();
                    require(axis == MathStretchAxis.HORIZONTAL && (id == 1510 || id == 1514 || id == 1532),
                            "unexpected short assembly seam");
                }
            }
        }
        return new MathGlyphConstruction(variants, assembly);
    }

    private static boolean supportsOverlap(List<MathGlyphConstruction.Part> parts, float overlap) {
        MathGlyphConstruction.Part previous = null;
        for (MathGlyphConstruction.Part part : parts) {
            if (previous != null && (previous.getEndConnector() < overlap || part.getStartConnector() < overlap)) {
                return false;
            }
            if (part.isExtender() && (part.getStartConnector() < overlap || part.getEndConnector() < overlap)) {
                return false;
            }
            previous = part;
        }
        return true;
    }

    private JsonObject constructions(MathStretchAxis axis) {
        return data.getAsJsonObject("constructions").getAsJsonObject(axisName(axis));
    }

    private static String axisName(MathStretchAxis axis) {
        return axis == MathStretchAxis.HORIZONTAL ? "horizontal" : "vertical";
    }

    private boolean validGlyph(int id) { return id > 0 && id < glyphCount && id != physicalFont.getMissingGlyphCode(); }
    private boolean owns(MathGlyphRef glyph) {
        return glyph != null && glyph.getKind() == MathGlyphRef.Kind.FONT_GLYPH
                && faceKey.equals(glyph.getFaceKey()) && validGlyph(glyph.getGlyphId());
    }
    private MathGlyphRef ref(int id) { return MathGlyphRef.forFontGlyph(faceKey, id); }
    private MathGlyphRef checkedRef(int id) { require(validGlyph(id), "recipe glyph"); return ref(id); }
    private float scale(int units, int sizePx) { return (float) ((double) units * sizePx / unitsPerEm); }
    private float value(String table, String key, int sizePx) {
        JsonElement raw = data.getAsJsonObject(table).get(key);
        return raw == null ? 0 : scale(raw.getAsInt(), sizePx) + deviceDelta(table + "." + key, sizePx);
    }
    // Device delta 本身为 pixel，只按有效 logical ppem 查一次，不依赖 renderScale/raster size。
    private int deviceDelta(String key, int effectivePpem) {
        JsonObject device = data.getAsJsonObject("deviceAdjustments").getAsJsonObject(key);
        if (device == null) { return 0; }
        int index = effectivePpem - device.get("startPpem").getAsInt();
        JsonArray deltas = device.getAsJsonArray("deltaPixels");
        return index < 0 || index >= deltas.size() ? 0 : deltas.get(index).getAsInt();
    }

    /** 仅映射明确的数学 alphabet；符号、已编码数学字母和未知文字原样查 cmap。 */
    private static int alphabetCodepoint(int cp, MathFontStyle style, FontType weight) {
        boolean bold = style == MathFontStyle.BOLD || weight == FontType.BOLD;
        boolean latin = (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
        boolean greekSmall = (cp >= 0x3b1 && cp <= 0x3c9) || greekVariant(cp) >= 0;
        boolean italic = style == MathFontStyle.ITALIC
                || ((style == MathFontStyle.INHERIT || style == MathFontStyle.MATH_NORMAL) && (latin || greekSmall));
        if (cp >= 'A' && cp <= 'Z') {
            return !bold && !italic ? cp : (bold ? (italic ? 0x1d468 : 0x1d400) : 0x1d434) + cp - 'A';
        }
        if (cp >= 'a' && cp <= 'z') {
            if (!bold && italic && cp == 'h') { return 0x210e; } // Unicode italic h 的历史例外。
            return !bold && !italic ? cp : (bold ? (italic ? 0x1d482 : 0x1d41a) : 0x1d44e) + cp - 'a';
        }
        if (cp >= '0' && cp <= '9') { return bold ? 0x1d7ce + cp - '0' : cp; }
        if (!bold && !italic) { return cp; }
        int greekBase = bold ? (italic ? 0x1d71c : 0x1d6a8) : 0x1d6e2;
        // Greek mathematical blocks insert CAPITAL THETA SYMBOL at the U+03A2 hole.
        if (cp >= 0x391 && cp <= 0x3a9 && cp != 0x3a2) { return greekBase + cp - 0x391; }
        if (cp == 0x3f4) { return greekBase + 17; }
        if (cp >= 0x3b1 && cp <= 0x3c9) { return greekBase + 26 + cp - 0x3b1; }
        int variant = greekVariant(cp);
        if (variant >= 0) { return greekBase + 52 + variant; }
        if (!bold && italic && cp == 0x131) { return 0x1d6a4; }
        if (!bold && italic && cp == 0x237) { return 0x1d6a5; }
        // partial/nabla/operators 不因 mathbf/mathit 被当作 alphabet 强制替换。
        return cp;
    }

    private static int greekVariant(int cp) {
        switch (cp) {
            case 0x3f5: return 0; // epsilon symbol (lunated epsilon)
            case 0x3d1: return 1; // theta symbol
            case 0x3f0: return 2; // kappa symbol
            case 0x3d5: return 3; // phi symbol
            case 0x3f1: return 4; // rho symbol
            case 0x3d6: return 5; // pi symbol
            default: return -1;
        }
    }

    private static void requireSize(int sizePx) {
        if (sizePx <= 0) { throw new IllegalArgumentException("effectiveSizePx must be positive"); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException("Invalid pinned math font: " + message); }
    }
    private static byte[] resource(String name) throws Exception {
        try (InputStream input = BundledMathFont.class.getResourceAsStream(ROOT + name)) {
            require(input != null, "missing resource " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); }
            return output.toByteArray();
        }
    }
    private static JsonObject json(byte[] bytes) {
        return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void verifyResource(JsonObject manifest, String name, byte[] bytes, String pinnedSha) throws Exception {
        JsonObject record = manifest.getAsJsonObject("files").getAsJsonObject(name);
        require(record.get("bytes").getAsInt() == bytes.length, "resource size " + name);
        require(pinnedSha.equals(record.get("sha256").getAsString()), "manifest digest " + name);
        StringBuilder sha = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            sha.append(Character.forDigit((value & 255) >>> 4, 16));
            sha.append(Character.forDigit(value & 15, 16));
        }
        require(pinnedSha.equals(sha.toString()), "resource digest " + name);
    }
}
