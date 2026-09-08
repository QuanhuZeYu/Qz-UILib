package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;
import club.heiqi.uilib.font.glyph.MathGlyphKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 活动 glyph 请求簿记：generation+码点+字重 → token/优先级/active 生命周期。
 * 纯数据结构（{@link GlyphPageManager} 持锁访问），不触碰 runtimeTables 状态。
 */
final class GlyphDemandRegistry {

    private final Map<Object, ActiveGlyphDemand> demands = new HashMap<Object, ActiveGlyphDemand>();

    ActiveGlyphDemand put(int generation, int codepoint, FontType fontType, GlyphRequestToken token,
            int priority) {
        return demands.put(Long.valueOf(packRequestKey(generation, codepoint, fontType)),
                new ActiveGlyphDemand(token, priority));
    }

    ActiveGlyphDemand get(int generation, int codepoint, FontType fontType) {
        return demands.get(Long.valueOf(packRequestKey(generation, codepoint, fontType)));
    }

    ActiveGlyphDemand get(GlyphRequestToken token) {
        return demands.get(key(token));
    }

    ActiveGlyphDemand remove(GlyphRequestToken token) {
        return demands.remove(key(token));
    }

    ActiveGlyphDemand get(MathGlyphKey key) { return demands.get(key); }

    void put(GlyphRequestToken token, int priority) {
        demands.put(key(token), new ActiveGlyphDemand(token, priority));
    }

    private Object key(GlyphRequestToken token) {
        return token.getKind() == GlyphRequestToken.Kind.MATH_GLYPH
                ? new MathGlyphKey(token.getGeneration(), token.getMathGlyphRef(), token.getRasterSize(), token.getTileIndex())
                : Long.valueOf(packRequestKey(token.getGeneration(), token.getCodepoint(), token.getFontType()));
    }

    void clear() {
        demands.clear();
    }

    /** generation(32b) + codepoint(21b) + fontType(1b) 打包为稳定请求 key。 */
    private static long packRequestKey(int generation, int codepoint, FontType fontType) {
        long versionBits = ((long) generation & 0xFFFFFFFFL) << 32;
        long codepointBits = ((long) codepoint & 0x1FFFFFL) << 1;
        long typeBit = fontType == FontType.BOLD ? 1L : 0L;
        return versionBits | codepointBits | typeBit;
    }

    /** 单个活动请求：token + 可提升优先级 + active 生命周期标记。 */
    static final class ActiveGlyphDemand {

        final GlyphRequestToken token;
        final AtomicInteger priority;
        final AtomicBoolean active = new AtomicBoolean(true);

        private ActiveGlyphDemand(GlyphRequestToken token, int priority) {
            this.token = token;
            this.priority = new AtomicInteger(priority);
        }
    }
}
