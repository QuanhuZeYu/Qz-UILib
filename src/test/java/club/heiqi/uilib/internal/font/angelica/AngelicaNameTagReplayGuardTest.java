package club.heiqi.uilib.internal.font.angelica;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Angelica 标签回放 phase、entity/item 生命周期与双版本恢复入口分派测试。 */
public class AngelicaNameTagReplayGuardTest {

    /** 正常批次进入 entities phase，并经配对 API 恢复合法旧 entity=-1 / item=37。 */
    @Test
    public void normalBatchRestoresEntityAndItem() {
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
                "phase", "get-entity", "get-item", "begin", "batch", "end", "entity-item:-1:37"),
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
                "phase", "get-entity", "get-item", "begin", "batch", "end", "entity-item:14:38"),
                state.events);
    }

    /** endEntities 自身异常也不得阻止 entity/item 配对恢复。 */
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
        Assert.assertEquals("entity-item:7:41", state.events.get(state.events.size() - 1));
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

    /** 2.2.10 形态：只有配对入口 → 解析为 paired 且只走一次配对调用。 */
    @Test
    public void recoveryDispatchPrefersPairedContract() {
        AngelicaNameTagReplayGuard.RecoveryDispatch dispatch =
                AngelicaNameTagReplayGuard.RecoveryDispatch.resolve(PairedState.class);

        Assert.assertTrue(dispatch.isUsable());
        PairedState state = new PairedState();
        dispatch.restore(state, 91, 92);
        Assert.assertEquals(Arrays.asList("entity-item:91:92"), state.events);
    }

    /** 2.1.50 形态：无配对入口、两个单参入口均为 public → 先 entity、后 item。 */
    @Test
    public void recoveryDispatchFallsBackToTwoStepContract() {
        AngelicaNameTagReplayGuard.RecoveryDispatch dispatch =
                AngelicaNameTagReplayGuard.RecoveryDispatch.resolve(LegacyState.class);

        Assert.assertTrue(dispatch.isUsable());
        LegacyState state = new LegacyState();
        dispatch.restore(state, 14, 38);
        Assert.assertEquals(Arrays.asList("entity:14", "item:38"), state.events);
    }

    /** 2.2.10 兜底形态：单参入口为 private 且无配对入口 → 只认 public 契约，判定不可用。 */
    @Test
    public void recoveryDispatchIgnoresNonPublicSingleArgumentContract() {
        AngelicaNameTagReplayGuard.RecoveryDispatch dispatch =
                AngelicaNameTagReplayGuard.RecoveryDispatch.resolve(PrivateEntityState.class);

        Assert.assertFalse(dispatch.isUsable());
    }

    /** 契约与 null owner 都不可用：restore 必须抛出而非静默。 */
    @Test
    public void recoveryDispatchRejectsMissingContract() {
        AngelicaNameTagReplayGuard.RecoveryDispatch dispatch =
                AngelicaNameTagReplayGuard.RecoveryDispatch.resolve(EmptyState.class);

        Assert.assertFalse(dispatch.isUsable());
        Assert.assertFalse(AngelicaNameTagReplayGuard.RecoveryDispatch.resolve(null).isUsable());
        try {
            dispatch.restore(new EmptyState(), 1, 2);
            Assert.fail("不可用分派不得静默恢复");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("not usable"));
        }
    }

    /** 无 Angelica 运行时的生产入口安全网：只执行批次、不进 phase 围栏、不抛错。 */
    @Test
    public void productionEntryFallsBackToImmediateRunWhenRecoveryIsUnavailable() {
        Assume.assumeFalse("测试 JVM 具备 Angelica 运行时，跳过无 Angelica 的安全网分支",
                angelicaRuntimePresent());

        final AtomicInteger runs = new AtomicInteger();
        AngelicaNameTagReplayGuard.runGuarded(new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });

        Assert.assertEquals(1, runs.get());
    }

    /** 按类名探测 Angelica 运行时；测试编译面不含上游类型，故只能用字符串。 */
    private static boolean angelicaRuntimePresent() {
        try {
            Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState", false,
                    AngelicaNameTagReplayGuardTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        } catch (LinkageError error) {
            return false;
        } catch (SecurityException exception) {
            return false;
        }
    }

    private static AngelicaNameTagReplayGuard.WarningSink noOpWarning() {
        return new AngelicaNameTagReplayGuard.WarningSink() {
            @Override
            public void warn() {}
        };
    }

    /** 模拟 Angelica 2.2.10 的 entity/item 配对状态。 */
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
        public void setCurrentEntityAndItem(int entityId, int itemId) {
            events.add("entity-item:" + entityId + ":" + itemId);
            entity = entityId;
            item = itemId;
        }
    }

    /** 2.2.10 形态假 owner：只有配对入口。 */
    public static final class PairedState {

        private final List<String> events = new ArrayList<String>();

        public void setCurrentEntityAndItem(int entityId, int itemId) {
            events.add("entity-item:" + entityId + ":" + itemId);
        }
    }

    /** 2.1.50 形态假 owner：两个单参入口都是 public。 */
    public static final class LegacyState {

        private final List<String> events = new ArrayList<String>();

        public void setCurrentEntity(int entityId) {
            events.add("entity:" + entityId);
        }

        public void setCurrentRenderedItem(int itemId) {
            events.add("item:" + itemId);
        }
    }

    /** 2.2.10 兜底形态假 owner：单参 entity 入口为 private，且没有配对入口。 */
    public static final class PrivateEntityState {

        private final List<String> events = new ArrayList<String>();

        public void setCurrentRenderedItem(int itemId) {
            events.add("item:" + itemId);
        }

        @SuppressWarnings("unused")
        private void setCurrentEntity(int entityId) {
            events.add("entity:" + entityId);
        }
    }

    /** 两版契约都不具备的假 owner。 */
    public static final class EmptyState {}
}
