package club.heiqi.config.ui.field;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * PickerSourceGuard —— 候选源 SPI 的客户端主线程守卫（不变量：候选源只允许主线程访问）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.3。候选物化触碰 {@code Block.blockRegistry}、
 * {@code getSubBlocks}、{@code StatCollector}、{@code Minecraft.getMinecraft()} 等非线程安全设施，
 * 因此 {@code PickerCandidateSource} 的每个入口都必须在调用前做 O(1) 主线程断言，<b>误用 fail-fast
 * 抛异常</b>而不是返回空数据（后者会把线程缺陷伪装成「没有候选」）。</p>
 *
 * <p><b>为什么不是注释而是断言</b>：框架层（scene/config）现状不含任何线程校验，把「框架已保证主线程」
 * 写成前提会把契约挂在假设上；本类以断言承担契约，且断言只做一次 volatile 读 + 一次比较，
 * 不引入每帧成本。</p>
 *
 * <h3>判定源（oracle）与降级</h3>
 * <ul>
 *   <li>客户端装配期安装平台判定源（{@code club.heiqi.uilib.client.MinecraftMainThreadOracle}，
 *       由 {@code ClientProxy} 引导）：<b>生产路径恒有判定源</b>；</li>
 *   <li>无判定源时（headless 单测、无客户端宿主）：无从判定 → 放行并一次性 WARN。
 *       该降级不会掩盖生产缺陷，因为生产路径必然安装判定源；「装配期是否安装」由源码级守卫测试钉死
 *       （{@code PickerSourceGuardWiringTest}），不依赖本类的运行时行为。</li>
 * </ul>
 *
 * <p>本类平台无关（{@code club.heiqi.config.*} 不 import Minecraft 类型），平台判定源经
 * {@link #installThreadOracle(ThreadOracle)} 注入。</p>
 */
public final class PickerSourceGuard {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/ConfigUI");

    /** 主线程判定源（平台适配器实现；无客户端宿主时缺省）。 */
    public interface ThreadOracle {

        /** @return 当前线程是否为客户端主线程 */
        boolean isMainThread();

        /** @return 判定源描述（用于异常信息与诊断） */
        String describe();
    }

    private static volatile ThreadOracle oracle;
    private static final AtomicBoolean MISSING_ORACLE_WARNED = new AtomicBoolean(false);

    private PickerSourceGuard() {
    }

    /**
     * 安装主线程判定源（客户端装配期调用；传 null 卸载）。
     *
     * @param threadOracle 平台判定源，可为 null
     */
    public static void installThreadOracle(ThreadOracle threadOracle) {
        oracle = threadOracle;
    }

    /** @return 是否已安装判定源（诊断/守卫测试用） */
    public static boolean hasThreadOracle() {
        return oracle != null;
    }

    /**
     * 断言当前处于客户端主线程。
     *
     * @param api 被守护的入口名（如 {@code "page"}、{@code "matchCount"}、{@code "exact"}）
     * @throws IllegalStateException 非主线程调用（fail-fast，不返回空数据）
     */
    public static void requireMainThread(String api) {
        ThreadOracle current = oracle;
        if (current == null) {
            if (MISSING_ORACLE_WARNED.compareAndSet(false, true)) {
                LOG.warn("[QzUiLib/ConfigUI] 未安装主线程判定源，候选源线程断言降级为放行："
                        + "无客户端宿主的 headless 路径属预期；生产路径应由 ClientProxy 安装判定源");
            }
            return;
        }
        if (!current.isMainThread()) {
            Thread thread = Thread.currentThread();
            throw new IllegalStateException("PickerCandidateSource." + api + " 只允许客户端主线程调用，"
                    + "但当前线程是 " + thread.getName() + "（判定源：" + current.describe() + "）");
        }
    }

    /**
     * 测试用：安装判定源（不改变生产装配路径）。
     *
     * @param threadOracle 判定源
     */
    public static void __installThreadOracleForTests(ThreadOracle threadOracle) {
        installThreadOracle(threadOracle);
    }

    /** 测试用：卸载判定源并复位一次性告警。 */
    public static void __resetForTests() {
        installThreadOracle(null);
        MISSING_ORACLE_WARNED.set(false);
    }
}
