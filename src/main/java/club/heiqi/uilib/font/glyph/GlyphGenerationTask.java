package club.heiqi.uilib.font.glyph;

import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成任务定义。
 */
public class GlyphGenerationTask {

    private final int runtimeVersion;
    private final GlyphRequestToken token;
    private final int codepoint;
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
        this(requireToken(token).getGeneration(), token, token.getCodepoint(), token.getFontType(), glyphSize,
                priority, new AtomicReference<GlyphDemandLevel>(requirePriority(priority)));
    }

    private GlyphGenerationTask(int runtimeVersion, GlyphRequestToken token, int codepoint, FontType fontType,
            int glyphSize, GlyphGenerationPriority priority, AtomicReference<GlyphDemandLevel> demandLevel) {
        if (fontType == null || priority == null) {
            throw new IllegalArgumentException("fontType 和 priority 不得为 null");
        }
        this.runtimeVersion = runtimeVersion;
        this.token = token;
        this.codepoint = codepoint;
        this.fontType = fontType;
        this.glyphSize = glyphSize;
        this.priority = priority;
        this.demandLevel = demandLevel;
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
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }

    public int getGlyphSize() {
        return glyphSize;
    }

    public GlyphGenerationPriority getPriority() {
        return priority;
    }

    GlyphGenerationTask claimedBy(GlyphRequestToken claimedToken) {
        GlyphRequestToken checkedToken = requireToken(claimedToken);
        if (token != null) {
            throw new IllegalStateException("字符生成任务已领取 token");
        }
        if (checkedToken.getGeneration() != runtimeVersion || checkedToken.getCodepoint() != codepoint
                || checkedToken.getFontType() != fontType) {
            throw new IllegalArgumentException("claim token 与 glyph demand 不一致");
        }
        return new GlyphGenerationTask(checkedToken.getGeneration(), checkedToken, checkedToken.getCodepoint(),
                checkedToken.getFontType(), glyphSize, priority, demandLevel);
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
