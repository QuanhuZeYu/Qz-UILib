package club.heiqi.uilib.internal.devtools.headless;

import org.junit.Assume;

/**
 * 直启出图门禁的共用判据：把「运行环境不具备」与「出图真的坏了」分开。
 *
 * <h3>它守的区分</h3>
 * <p>本包与 {@code font.render.software.HeadlessTextParityTest} 的若干门禁都靠<b>进程外直启</b>
 * {@code HeadlessShotMain} 做判据。直启失败有两种：一种是这台机器根本建不出 GL 上下文
 * （natives 加载失败 / 无窗口句柄能力），退出码 {@link HeadlessShotMain#EXIT_ENVIRONMENT_UNAVAILABLE}；
 * 另一种是能出图但设施 / 内容坏了（退出码 3 / 4）。若一律断言失败，门禁在无图形环境上会变成
 * 「环境检测器」；若一律跳过，真实回归又会被掩盖。故只放行前者，其余非 0 一律红。</p>
 *
 * <h3>实测成因（保留以免后人重复定位）</h3>
 * <p>CI（ubuntu + Zulu 17）的 {@code libjawt.so} 不导出 {@code SUNWprivate_1.1} 版本符号，
 * LWJGL2 的 {@code liblwjgl64.so} 在 {@code dlopen} 阶段即失败：</p>
 * <pre>
 * UnsatisfiedLinkError: .../liblwjgl64.so: .../lib/libjawt.so:
 *     version 'SUNWprivate_1.1' not found (required by .../liblwjgl64.so)
 * </pre>
 * <p>本机与发布 workflow 都是 Temurin，不受影响。跳过不等于「CI 免检」：发布 workflow 的
 * {@code test build} 建在同一条链路上，缺 GL 时同样跳过 —— 真正的覆盖来自有 GL 的机器
 * （本机 / Temurin runner），它们照旧完整执行。</p>
 */
public final class HeadlessShotGate {

    private HeadlessShotGate() {
    }

    /**
     * 直启因运行环境不具备而失败时跳过本用例；其余非 0 退出码由调用方照旧断言为失败。
     *
     * @param label  直启标签（用例内的产物名，出现在跳过原因里）
     * @param exit   子进程退出码
     * @param output 子进程输出（跳过原因要自证，故整段带入）
     */
    public static void assumeEnvironmentAvailable(String label, int exit, String output) {
        Assume.assumeFalse("headless 出图运行环境不具备（" + label + " exit=" + exit
                + "，natives / GL 上下文建不起来）——跳过该门禁，不是出图缺陷：\n" + output,
                exit == HeadlessShotMain.EXIT_ENVIRONMENT_UNAVAILABLE);
    }
}
