package club.heiqi.uilib.internal.font.angelica;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/** Angelica 标签回放 phase 与 entity/item 状态生命周期测试。 */
public class AngelicaNameTagReplayGuardTest {

    /** 正常批次进入 entities phase，并按 entity、item 顺序恢复合法旧 entity=-1。 */
    @Test
    public void normalBatchRestoresEntityThenItem() {
        final FakeStateAccess state = new FakeStateAccess(true, -1, 37);
        boolean replayed = AngelicaNameTagReplayGuard.runGuarded(
                new Runnable() {
                    @Override
                    public void run() {
                        state.events.add("batch");
                        state.entity = 91;
                        state.item = 92;
                    }
                },
                state,
                new AtomicBoolean(),
                noOpWarning());

        Assert.assertTrue(replayed);
        Assert.assertEquals(-1, state.entity);
        Assert.assertEquals(37, state.item);
        Assert.assertTrue(state.phaseNone);
        Assert.assertEquals(Arrays.asList(
                "phase", "get-entity", "get-item", "begin", "batch", "end", "entity:-1", "item:37"),
                state.events);
    }

    /** 批次异常仍结束 phase、恢复 entity/item，并传播同一异常。 */
    @Test
    public void batchFailureStillRestoresStateAndPropagates() {
        final FakeStateAccess state = new FakeStateAccess(true, 14, 38);
        final RuntimeException failure = new RuntimeException("batch failed");

        try {
            AngelicaNameTagReplayGuard.runGuarded(
                    new Runnable() {
                        @Override
                        public void run() {
                            state.events.add("batch");
                            state.entity = 99;
                            state.item = 100;
                            throw failure;
                        }
                    },
                    state,
                    new AtomicBoolean(),
                    noOpWarning());
            Assert.fail("批次异常必须传播");
        } catch (RuntimeException actual) {
            Assert.assertSame(failure, actual);
        }

        Assert.assertEquals(14, state.entity);
        Assert.assertEquals(38, state.item);
        Assert.assertTrue(state.phaseNone);
        Assert.assertEquals(Arrays.asList(
                "phase", "get-entity", "get-item", "begin", "batch", "end", "entity:14", "item:38"),
                state.events);
    }

    /** endEntities 自身异常也不得阻止 entity/item 恢复。 */
    @Test
    public void endFailureStillRestoresEntityAndItem() {
        final FakeStateAccess state = new FakeStateAccess(true, 7, 41);
        final RuntimeException failure = new RuntimeException("end failed");
        state.endFailure = failure;

        try {
            AngelicaNameTagReplayGuard.runGuarded(
                    new Runnable() {
                        @Override
                        public void run() {
                            state.events.add("batch");
                            state.entity = 70;
                            state.item = 71;
                        }
                    },
                    state,
                    new AtomicBoolean(),
                    noOpWarning());
            Assert.fail("endEntities 异常必须传播");
        } catch (RuntimeException actual) {
            Assert.assertSame(failure, actual);
        }

        Assert.assertEquals(7, state.entity);
        Assert.assertEquals(41, state.item);
        Assert.assertEquals("entity:7", state.events.get(state.events.size() - 2));
        Assert.assertEquals("item:41", state.events.get(state.events.size() - 1));
    }

    /** 非 NONE phase 不执行批次、不改状态，并在重复命中时只告警一次。 */
    @Test
    public void invalidPhaseDropsBatchAndWarnsOnce() {
        FakeStateAccess state = new FakeStateAccess(false, 8, 42);
        AtomicBoolean warned = new AtomicBoolean();
        final AtomicInteger warningCount = new AtomicInteger();
        AngelicaNameTagReplayGuard.WarningSink warning = new AngelicaNameTagReplayGuard.WarningSink() {
            @Override
            public void warn() {
                warningCount.incrementAndGet();
            }
        };
        Runnable batch = new Runnable() {
            @Override
            public void run() {
                Assert.fail("非法 phase 不得执行标签批次");
            }
        };

        Assert.assertFalse(AngelicaNameTagReplayGuard.runGuarded(batch, state, warned, warning));
        Assert.assertFalse(AngelicaNameTagReplayGuard.runGuarded(batch, state, warned, warning));

        Assert.assertEquals(1, warningCount.get());
        Assert.assertEquals(8, state.entity);
        Assert.assertEquals(42, state.item);
        Assert.assertEquals(Arrays.asList("phase", "phase"), state.events);
    }

    private static AngelicaNameTagReplayGuard.WarningSink noOpWarning() {
        return new AngelicaNameTagReplayGuard.WarningSink() {
            @Override
            public void warn() {}
        };
    }

    /** 模拟 setCurrentEntity 会把 item ID 清零的 Angelica 状态。 */
    private static final class FakeStateAccess implements AngelicaNameTagReplayGuard.StateAccess {

        private final List<String> events = new ArrayList<String>();
        private boolean phaseNone;
        private int entity;
        private int item;
        private RuntimeException endFailure;

        private FakeStateAccess(boolean phaseNone, int entity, int item) {
            this.phaseNone = phaseNone;
            this.entity = entity;
            this.item = item;
        }

        @Override
        public boolean isPhaseNone() {
            events.add("phase");
            return phaseNone;
        }

        @Override
        public int getCurrentEntity() {
            events.add("get-entity");
            return entity;
        }

        @Override
        public int getCurrentItem() {
            events.add("get-item");
            return item;
        }

        @Override
        public void beginEntities() {
            events.add("begin");
            phaseNone = false;
        }

        @Override
        public void endEntities() {
            events.add("end");
            phaseNone = true;
            if (endFailure != null) {
                throw endFailure;
            }
        }

        @Override
        public void setCurrentEntity(int entityId) {
            events.add("entity:" + entityId);
            entity = entityId;
            item = 0;
        }

        @Override
        public void setCurrentItem(int itemId) {
            events.add("item:" + itemId);
            item = itemId;
        }
    }
}
