package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.math.MathLexer.Token;

/** 每式共享的资源账本；暂停的输入队列仍占待处理额度，临时展开不能重置累计量。 */
final class MathExpansionBudget {
    static final int SOURCE_UNITS = 16384;

    // 源长不代表展开成本：生成量和同时待处理量各允许原源长界限的四倍。
    // 累计访问/复制允许十六轮完整额度，防止少量替换反复搬运大参数。
    // 三项分别约束新生成、同时驻留与累计成本，不能用替换次数互相抵扣。
    private static final long TOKEN_LIMIT = 4L * SOURCE_UNITS;
    private static final long WORK_LIMIT = 16L * TOKEN_LIMIT;
    private long generated, pending, work;

    void generate(long count, Token origin) {
        checkPending(count, origin);
        if (count > TOKEN_LIMIT - generated) throw exceeded(origin, "累计生成token");
        generated += count;
    }

    void enqueue(int count, Token origin) {
        checkPending(count, origin);
        work(count, origin);
        pending += count;
    }

    private void checkPending(long count, Token origin) {
        if (count > TOKEN_LIMIT - pending) throw exceeded(origin, "待处理token");
    }

    void release(int count) {
        pending -= count;
    }

    // 长控制词与 verb 正文也是工作量，不能把一个 token 一律当作一个字符。
    static int weight(Token token) {
        return Math.max(1, token.text.length());
    }

    void work(long count, Token origin) {
        if (count > WORK_LIMIT - work) throw exceeded(origin, "累计token工作量");
        work += count;
    }

    private MathLexer.Failure exceeded(Token origin, String resource) {
        return new MathLexer.Failure(origin, "宏展开资源超过界限：" + resource);
    }
}
