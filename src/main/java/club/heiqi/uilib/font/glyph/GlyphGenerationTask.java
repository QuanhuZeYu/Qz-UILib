package club.heiqi.uilib.font.glyph;

import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

/**
 * 字符生成任务定义。
 */
public class GlyphGenerationTask {

    private final int runtimeVersion;
    private final GlyphRequestToken token;
    private final Integer codepoint;
    private final MathGlyphRef mathGlyphRef;
    private final int tileIndex;
    private final FontType fontType;
    private final int glyphSize;
    private final GlyphGenerationPriority priority;
    private final AtomicReference<GlyphDemandLevel> demandLevel;

    /**
     * 创建字符生成任务。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param glyphSize 字符格大小
     * @param priority 生成优先级
     */
    public GlyphGenerationTask(int runtimeVersion, int codepoint, FontType fontType, int glyphSize,
            GlyphGenerationPriority priority) {
        this(runtimeVersion, null, codepoint, fontType, glyphSize, priority,
                new AtomicReference<GlyphDemandLevel>(requirePriority(priority)));
    }

    GlyphGenerationTask(int runtimeVersion, int codepoint, FontType fontType, int glyphSize,
            GlyphDemandLevel demandLevel) {
        this(runtimeVersion, null, codepoint, fontType, glyphSize, requireDemandLevel(demandLevel).toLegacyPriority(),
                new AtomicReference<GlyphDemandLevel>(demandLevel));
    }

    /**
     * 创建已领取 token 的 worker 任务。
     *
     * @param token 请求 token
     * @param glyphSize 字符格大小
     * @param priority 生成优先级
     */
    public GlyphGenerationTask(GlyphRequestToken token, int glyphSize, GlyphGenerationPriority priority) {
        this(requireToken(token).getGeneration(), token,
                token.getKind() == GlyphRequestToken.Kind.CODEPOINT ? token.getCodepoint() : null,
                token.getKind() == GlyphRequestToken.Kind.CODEPOINT ? token.getFontType() : null,
                token.getKind() == GlyphRequestToken.Kind.MATH_GLYPH ? token.getMathGlyphRef() : null,
                glyphSize, token.getKind() == GlyphRequestToken.Kind.MATH_GLYPH ? token.getTileIndex() : 0,
                priority, new AtomicReference<GlyphDemandLevel>(requirePriority(priority)));
    }

    private GlyphGenerationTask(int runtimeVersion, GlyphRequestToken token, int codepoint, FontType fontType,
            int glyphSize, GlyphGenerationPriority priority, AtomicReference<GlyphDemandLevel> demandLevel) {
        this(runtimeVersion, token, codepoint, fontType, null, glyphSize, 0, priority, demandLevel);
    }

    private GlyphGenerationTask(int runtimeVersion, GlyphRequestToken token, Integer codepoint, FontType fontType,
            MathGlyphRef mathGlyphRef, int glyphSize, int tileIndex, GlyphGenerationPriority priority,
            AtomicReference<GlyphDemandLevel> demandLevel) {
        if ((mathGlyphRef == null && fontType == null) || priority == null) {
            throw new IllegalArgumentException("fontType 和 priority 不得为 null");
        }
        if (mathGlyphRef != null && (glyphSize <= 0 || tileIndex < 0
                || (token != null && token.getRasterSize() != glyphSize))) {
            throw new IllegalArgumentException("数学任务尺寸必须为正且与 token 一致，tileIndex 必须非负");
        }
        this.mathGlyphRef = mathGlyphRef;
        this.tileIndex = token == null ? tileIndex : 0;
        this.runtimeVersion = runtimeVersion;
        this.token = token;
        this.codepoint = codepoint;
        this.fontType = fontType;
        // 已 claim 数学任务仅以 token 持有权威尺寸。
        this.glyphSize = mathGlyphRef != null && token != null ? 0 : glyphSize;
        this.priority = priority;
        this.demandLevel = demandLevel;
    }

    /** 创建尚未 claim 的数学需求；claim 后继续共享优先级提升。 */
    public static GlyphGenerationTask forMathGlyph(int runtimeVersion, MathGlyphRef glyphRef, int rasterSize,
            int tileIndex, GlyphGenerationPriority priority) {
        return mathDemand(runtimeVersion, glyphRef, rasterSize, tileIndex, priority, requirePriority(priority));
    }

    static GlyphGenerationTask forMathGlyph(int runtimeVersion, MathGlyphRef glyphRef, int rasterSize,
            int tileIndex, GlyphDemandLevel demandLevel) {
        return mathDemand(runtimeVersion, glyphRef, rasterSize, tileIndex,
                requireDemandLevel(demandLevel).toLegacyPriority(), demandLevel);
    }

    private static GlyphGenerationTask mathDemand(int runtimeVersion, MathGlyphRef glyphRef, int rasterSize,
            int tileIndex, GlyphGenerationPriority priority, GlyphDemandLevel demandLevel) {
        if (glyphRef == null) { throw new IllegalArgumentException("glyphRef 不得为 null"); }
        return new GlyphGenerationTask(runtimeVersion, null, null, null, glyphRef, rasterSize, tileIndex,
                priority, new AtomicReference<GlyphDemandLevel>(demandLevel));
    }

    public GlyphRequestToken.Kind getKind() {
        return mathGlyphRef == null ? GlyphRequestToken.Kind.CODEPOINT : GlyphRequestToken.Kind.MATH_GLYPH;
    }

    public MathGlyphRef getMathGlyphRef() { requireKind(GlyphRequestToken.Kind.MATH_GLYPH); return mathGlyphRef; }
    public int getRasterSize() { requireKind(GlyphRequestToken.Kind.MATH_GLYPH); return getGlyphSize(); }
    public int getTileIndex() {
        requireKind(GlyphRequestToken.Kind.MATH_GLYPH);
        return token == null ? tileIndex : token.getTileIndex();
    }

    private void requireKind(GlyphRequestToken.Kind expected) {
        if (getKind() != expected) { throw new IllegalStateException("任务类型不匹配: " + getKind()); }
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    /**
     * 获取 manager 原子 claim 返回的 token。
     *
     * @return 已领取 token；尚未提交的 demand 返回 null
     */
    public GlyphRequestToken getToken() {
        return token;
    }

    public int getCodepoint() {
        requireKind(GlyphRequestToken.Kind.CODEPOINT);
        return codepoint;
    }

    public FontType getFontType() {
        requireKind(GlyphRequestToken.Kind.CODEPOINT);
        return fontType;
    }

    public int getGlyphSize() {
        return mathGlyphRef != null && token != null ? token.getRasterSize() : glyphSize;
    }

    public GlyphGenerationPriority getPriority() {
        return priority;
    }

    GlyphGenerationTask claimedBy(GlyphRequestToken claimedToken) {
        GlyphRequestToken checkedToken = requireToken(claimedToken);
        if (token != null) {
            throw new IllegalStateException("字符生成任务已领取 token");
        }
        if (checkedToken.getGeneration() != runtimeVersion || checkedToken.getKind() != getKind()
                || (getKind() == GlyphRequestToken.Kind.CODEPOINT
                        ? checkedToken.getCodepoint() != codepoint || checkedToken.getFontType() != fontType
                        : !checkedToken.getMathGlyphRef().equals(mathGlyphRef)
                                || checkedToken.getRasterSize() != getRasterSize()
                                || checkedToken.getTileIndex() != getTileIndex())) {
            throw new IllegalArgumentException("claim token 与 glyph demand 不一致");
        }
        return new GlyphGenerationTask(runtimeVersion, checkedToken, codepoint, fontType, mathGlyphRef,
                getGlyphSize(), tileIndex, priority, demandLevel);
    }

    GlyphDemandLevel getDemandLevel() {
        return demandLevel.get();
    }

    boolean promoteTo(GlyphDemandLevel promotedLevel) {
        GlyphDemandLevel checkedLevel = requireDemandLevel(promotedLevel);
        while (true) {
            GlyphDemandLevel current = demandLevel.get();
            if (current.getPriorityOrder() >= checkedLevel.getPriorityOrder()) {
                return false;
            }
            if (demandLevel.compareAndSet(current, checkedLevel)) {
                return true;
            }
        }
    }

    private static GlyphRequestToken requireToken(GlyphRequestToken token) {
        if (token == null) {
            throw new IllegalArgumentException("token 不得为 null");
        }
        return token;
    }

    private static GlyphDemandLevel requirePriority(GlyphGenerationPriority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("priority 不得为 null");
        }
        return GlyphDemandLevel.fromLegacyPriority(priority);
    }

    private static GlyphDemandLevel requireDemandLevel(GlyphDemandLevel demandLevel) {
        if (demandLevel == null) {
            throw new IllegalArgumentException("demandLevel 不得为 null");
        }
        return demandLevel;
    }
}
