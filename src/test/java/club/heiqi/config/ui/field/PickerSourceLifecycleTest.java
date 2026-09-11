package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;

/**
 * 候选源会话释放账本守卫（P4 遗漏项 U-B1 闭合；P6 补充轮）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.4（释放点）/ §5.3（触发点）、
 * {@code PickerCandidateSource#release()} javadoc（客户端断开 / 世界退出路径，不在关屏调用）、
 * {@code team/P4-实现记录.md} §常驻范围表（Miner {@code PickerCandidateSource} = 进程级会话）。</p>
 *
 * <p>覆盖四条不变量：①每个源释放<b>恰一次</b>（同实例重复登记去重）；
 * ②重复释放<b>幂等</b>且释放后再次打开（读取）可重建、版本号不回绕；
 * ③单源失败隔离、未登记源不受影响；④非主线程调用 fail-fast（ADR A-01）。</p>
 */
public class PickerSourceLifecycleTest {

    @Before
    public void setUp() {
        PickerSourceLifecycle.__resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    @After
    public void tearDown() {
        PickerSourceLifecycle.__resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    /** ① 释放恰一次：同一实例被多个屏登记也只释放一次（账本按实例去重），null 登记被忽略。 */
    @Test
    public void releaseIsInvokedExactlyOncePerSourceEvenWithRepeatedRegistration() {
        RecordingSource first = new RecordingSource(3);
        RecordingSource second = new RecordingSource(3);
        PickerSourceLifecycle.track(first);
        PickerSourceLifecycle.track(first);
        PickerSourceLifecycle.track(first);
        PickerSourceLifecycle.track(null);
        PickerSourceLifecycle.track(second);

        Assert.assertEquals("重复登记只记一条（含 null 忽略）", 2, PickerSourceLifecycle.trackedCount());
        Assert.assertEquals("返回值 = 实际释放的源数", 2, PickerSourceLifecycle.releaseAll("client_disconnect"));

        Assert.assertEquals("每个源恰释放一次", 1, first.releaseCalls);
        Assert.assertEquals(1, second.releaseCalls);
    }

    /** ② 重复释放幂等 + 释放后再次打开可重建（SPI 契约：释放后仍可用、版本号不回绕）。 */
    @Test
    public void repeatedReleaseIsIdempotentAndSourceRebuildsOnNextOpen() {
        RecordingSource source = new RecordingSource(3);
        PickerSourceLifecycle.track(source);
        source.onEnvironmentChanged(7L, 9L);
        Assert.assertEquals("首次打开：窗口可用", 2, source.page(PickerQuery.browse(0, null), 0, 2).size());
        Assert.assertTrue("读取即建立缓存", source.cachePresent);
        long revisionBefore = source.registryRevision();

        PickerSourceLifecycle.releaseAll("client_disconnect");
        PickerSourceLifecycle.releaseAll("client_disconnect");

        Assert.assertEquals("两次断连各自释放一次", 2, source.releaseCalls);
        Assert.assertFalse("重复释放后仍为空态（幂等，不产生副作用）", source.cachePresent);
        Assert.assertEquals("释放不得回绕版本号（SPI 契约）", revisionBefore, source.registryRevision());

        Assert.assertEquals("释放后再次打开：读取仍可用", 2, source.page(PickerQuery.browse(0, null), 0, 2).size());
        Assert.assertTrue("按首次建快照语义重建", source.cachePresent);
    }

    /** ③a 未登记的源不受会话释放影响（不误放别人的源）。 */
    @Test
    public void untrackedSourceIsNeverReleased() {
        RecordingSource untracked = new RecordingSource(1);

        Assert.assertEquals("空账本释放数为 0", 0, PickerSourceLifecycle.releaseAll("client_disconnect"));
        Assert.assertEquals(0, untracked.releaseCalls);
    }

    /** ③b 单源失败隔离：一个源抛异常不阻断其余源，返回值只计成功项。 */
    @Test
    public void oneFailingSourceDoesNotBlockTheOthers() {
        RecordingSource failing = new RecordingSource(1).failOnRelease();
        RecordingSource healthy = new RecordingSource(1);
        PickerSourceLifecycle.track(failing);
        PickerSourceLifecycle.track(healthy);

        Assert.assertEquals("返回值只计成功释放的源", 1, PickerSourceLifecycle.releaseAll("client_disconnect"));
        Assert.assertEquals(1, failing.releaseCalls);
        Assert.assertEquals("失败源之后的源仍被释放", 1, healthy.releaseCalls);
    }

    /** ④ 非主线程 fail-fast：不静默跳过释放；主线程判定源放行后正常释放（ADR A-01）。 */
    @Test
    public void releaseOnNonMainThreadFailsFastWithoutReleasing() {
        RecordingSource source = new RecordingSource(1);
        PickerSourceLifecycle.track(source);
        PickerSourceGuard.__installThreadOracleForTests(new PickerSourceGuard.ThreadOracle() {
            @Override
            public boolean isMainThread() {
                return false;
            }

            @Override
            public String describe() {
                return "test:not-main";
            }
        });
        try {
            PickerSourceLifecycle.releaseAll("client_disconnect");
            Assert.fail("非主线程释放必须 fail-fast");
        } catch (IllegalStateException expected) {
            Assert.assertTrue("异常须点明被守护的入口", expected.getMessage().contains("release"));
        }
        Assert.assertEquals("fail-fast 前不得发生任何释放", 0, source.releaseCalls);

        PickerSourceGuard.__installThreadOracleForTests(new PickerSourceGuard.ThreadOracle() {
            @Override
            public boolean isMainThread() {
                return true;
            }

            @Override
            public String describe() {
                return "test:main";
            }
        });
        Assert.assertEquals(1, PickerSourceLifecycle.releaseAll("client_disconnect"));
    }

    /** 内存候选源替身：记录释放次数，模拟「缓存存在性」与「释放不改版本」两条 SPI 契约。 */
    private static final class RecordingSource implements PickerCandidateSource {
        private final int total;
        private int releaseCalls;
        private boolean cachePresent;
        private boolean failOnRelease;
        private long revision;

        private RecordingSource(int total) {
            this.total = total;
        }

        private RecordingSource failOnRelease() {
            failOnRelease = true;
            return this;
        }

        @Override
        public int size() {
            return total;
        }

        @Override
        public long registryRevision() {
            return revision;
        }

        @Override
        public long nameRevision() {
            return revision;
        }

        @Override
        public long iconRevision() {
            return revision;
        }

        @Override
        public void onEnvironmentChanged(long nameEpoch, long resourceEpoch) {
            revision++;
        }

        @Override
        public int matchCount(PickerQuery query) {
            return total;
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            cachePresent = true;
            List<SearchPickerData.Candidate> window = new ArrayList<SearchPickerData.Candidate>();
            for (int index = offset; index < Math.min(total, offset + limit); index++) {
                window.add(new SearchPickerData.Candidate("k" + (index + 1), "k" + (index + 1),
                        Collections.<SearchPickerData.Variant>emptyList()));
            }
            return window;
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            return null;
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            return Collections.emptyList();
        }

        @Override
        public void release() {
            releaseCalls++;
            if (failOnRelease) {
                throw new IllegalStateException("host release failure");
            }
            cachePresent = false;
        }
    }
}
