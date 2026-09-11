package club.heiqi.config.ui.field;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PickerSourceGuard} 线程守卫测试（判据 A-01 的 UILib 侧一半）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.3。误用必须 fail-fast 抛异常，
 * 不得静默返回空数据。</p>
 */
public class PickerSourceGuardTest {

    private final Thread mainThread = Thread.currentThread();

    @Before
    public void setUp() {
        PickerSourceGuard.__resetForTests();
    }

    @After
    public void tearDown() {
        PickerSourceGuard.__resetForTests();
    }

    private void installCurrentThreadOracle() {
        PickerSourceGuard.__installThreadOracleForTests(new PickerSourceGuard.ThreadOracle() {
            @Override
            public boolean isMainThread() {
                return Thread.currentThread() == mainThread;
            }

            @Override
            public String describe() {
                return "test-oracle";
            }
        });
    }

    /** 主线程调用放行；非主线程调用 fail-fast（异常信息含入口名与线程名）。 */
    @Test
    public void offThreadCallFailsFast() throws Exception {
        installCurrentThreadOracle();
        assertTrue(PickerSourceGuard.hasThreadOracle());

        PickerSourceGuard.requireMainThread("page");

        final AtomicReference<Throwable> captured = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PickerSourceGuard.requireMainThread("exact");
                } catch (Throwable error) {
                    captured.set(error);
                }
            }
        }, "picker-worker");
        worker.start();
        worker.join();

        Throwable error = captured.get();
        assertTrue("非主线程调用必须抛异常", error instanceof IllegalStateException);
        assertTrue("异常信息须含入口名", error.getMessage().contains("exact"));
        assertTrue("异常信息须含线程名", error.getMessage().contains("picker-worker"));
        assertTrue("异常信息须含判定源", error.getMessage().contains("test-oracle"));
    }

    /** 无判定源（headless 无客户端宿主）时降级放行：契约由装配期源码守卫承担。 */
    @Test
    public void missingOracleDegradesToAllow() {
        assertFalse(PickerSourceGuard.hasThreadOracle());
        PickerSourceGuard.requireMainThread("page");
        PickerSourceGuard.requireMainThread("matchCount");
        PickerSourceGuard.requireMainThread("exact");
    }

    /** 卸载判定源后恢复降级语义（install(null) 是合法卸载）。 */
    @Test
    public void oracleCanBeUninstalled() {
        installCurrentThreadOracle();
        assertTrue(PickerSourceGuard.hasThreadOracle());
        PickerSourceGuard.installThreadOracle(null);
        assertFalse(PickerSourceGuard.hasThreadOracle());
        PickerSourceGuard.requireMainThread("page");
    }

    /** 判定源恒返回 false 时，任何调用都失败（fail-fast 不依赖具体线程身份）。 */
    @Test
    public void alwaysFalseOracleAlwaysFails() {
        PickerSourceGuard.__installThreadOracleForTests(new PickerSourceGuard.ThreadOracle() {
            @Override
            public boolean isMainThread() {
                return false;
            }

            @Override
            public String describe() {
                return "always-false";
            }
        });
        try {
            PickerSourceGuard.requireMainThread("page");
            fail("expected fail-fast");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("page"));
        }
    }
}
