package club.heiqi.uilib.internal.font;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.AngelicaEnvironment;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.AngelicaEnvironmentProbe;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.CapturePolicy;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.CompatibilityPolicy;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.GuardAvailability;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.ReplayRunner;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.ScopeCoordinator;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator.WarningSink;

/** 玩家标签 host scope、FIFO、异常清理与兼容策略测试。 */
public class PlayerNameTagRenderCoordinatorTest {

    /** host 返回后才按计分板、普通名的捕获顺序回放。 */
    @Test
    public void replaysCapturedCallsInFifoAfterHostReturns() {
        final List<String> events = new ArrayList<String>();
        final ScopeCoordinator coordinator = coordinator(true);

        coordinator.runHostPass(new Runnable() {
            @Override
            public void run() {
                events.add("host-start");
                coordinator.captureOrRun(add(events, "scoreboard"));
                coordinator.captureOrRun(add(events, "player-name"));
                events.add("tile-entities-finished");
                Assert.assertEquals(Arrays.asList("host-start", "tile-entities-finished"), events);
            }
        });

        Assert.assertEquals(Arrays.asList(
                "host-start", "tile-entities-finished", "scoreboard", "player-name"), events);
        Assert.assertEquals(0, coordinator.scopeDepth());
    }

    /** 嵌套 host 拥有独立 FIFO，内层回放也不得泄漏进仍捕获的外层 scope。 */
    @Test
    public void nestedHostsKeepQueuesIndependent() {
        final List<String> events = new ArrayList<String>();
        final ScopeCoordinator coordinator = coordinator(true);

        coordinator.runHostPass(new Runnable() {
            @Override
            public void run() {
                coordinator.captureOrRun(add(events, "outer-first"));
                coordinator.runHostPass(new Runnable() {
                    @Override
                    public void run() {
                        coordinator.captureOrRun(new Runnable() {
                            @Override
                            public void run() {
                                events.add("inner");
                                coordinator.captureOrRun(add(events, "inner-replay-immediate"));
                            }
                        });
                        events.add("inner-host-end");
                    }
                });
                events.add("after-inner");
                coordinator.captureOrRun(add(events, "outer-second"));
                events.add("outer-host-end");
            }
        });

        Assert.assertEquals(Arrays.asList(
                "inner-host-end",
                "inner",
                "inner-replay-immediate",
                "after-inner",
                "outer-host-end",
                "outer-first",
                "outer-second"), events);
        Assert.assertEquals(0, coordinator.scopeDepth());
    }

    /** host 抛异常时不回放，并清理当前线程 scope。 */
    @Test
    public void hostFailureDiscardsQueueAndPropagates() {
        final List<String> events = new ArrayList<String>();
        final ScopeCoordinator coordinator = coordinator(true);
        final RuntimeException failure = new RuntimeException("host failed");

        try {
            coordinator.runHostPass(new Runnable() {
                @Override
                public void run() {
                    coordinator.captureOrRun(add(events, "must-not-replay"));
                    throw failure;
                }
            });
            Assert.fail("host 异常必须传播");
        } catch (RuntimeException actual) {
            Assert.assertSame(failure, actual);
        }

        Assert.assertTrue(events.isEmpty());
        Assert.assertEquals(0, coordinator.scopeDepth());
        coordinator.captureOrRun(add(events, "outside-immediate"));
        Assert.assertEquals(Arrays.asList("outside-immediate"), events);
    }

    /** 回放首项抛异常时丢弃尾项、清 scope，并传播原异常。 */
    @Test
    public void replayFailureDiscardsTailAndPropagates() {
        final List<String> events = new ArrayList<String>();
        final ScopeCoordinator coordinator = coordinator(true);
        final RuntimeException failure = new RuntimeException("replay failed");

        try {
            coordinator.runHostPass(new Runnable() {
                @Override
                public void run() {
                    coordinator.captureOrRun(new Runnable() {
                        @Override
                        public void run() {
                            events.add("first");
                            throw failure;
                        }
                    });
                    coordinator.captureOrRun(add(events, "discarded-tail"));
                }
            });
            Assert.fail("回放异常必须传播");
        } catch (RuntimeException actual) {
            Assert.assertSame(failure, actual);
        }

        Assert.assertEquals(Arrays.asList("first"), events);
        Assert.assertEquals(0, coordinator.scopeDepth());
    }

    /** 无 scope 与不支持兼容环境都立即执行原调用，不进入 replay runner。 */
    @Test
    public void unsupportedOrMissingScopeFailsOpenToImmediateCall() {
        final List<String> events = new ArrayList<String>();
        final AtomicInteger replayBatches = new AtomicInteger();
        final ScopeCoordinator coordinator = new ScopeCoordinator(
                constantPolicy(false),
                new ReplayRunner() {
                    @Override
                    public void run(Runnable batch) {
                        replayBatches.incrementAndGet();
                        batch.run();
                    }
                });

        coordinator.captureOrRun(add(events, "no-scope"));
        coordinator.runHostPass(new Runnable() {
            @Override
            public void run() {
                coordinator.captureOrRun(add(events, "unsupported"));
            }
        });

        Assert.assertEquals(Arrays.asList("no-scope", "unsupported"), events);
        Assert.assertEquals(0, replayBatches.get());
        Assert.assertEquals(0, coordinator.scopeDepth());
    }

    /** 无 Angelica 不要求可选围栏；精确 2.1.50 且握手完成时允许捕获。 */
    @Test
    public void compatibilityAllowsAbsentOrExactGuardedAngelica() {
        Assert.assertTrue(policy(AngelicaEnvironment.absent(), false, new ArrayList<String>()).permitsCapture());
        Assert.assertTrue(policy(
                AngelicaEnvironment.present("2.1.50"), true, new ArrayList<String>()).permitsCapture());
    }

    /** 未知版本与缺失握手均 fail-open，且每个策略实例只告警一次。 */
    @Test
    public void compatibilityRejectsUnknownVersionOrMissingGuardOnce() {
        List<String> unknownWarnings = new ArrayList<String>();
        CompatibilityPolicy unknown = policy(AngelicaEnvironment.present("2.1.51"), true, unknownWarnings);
        Assert.assertFalse(unknown.permitsCapture());
        Assert.assertFalse(unknown.permitsCapture());
        Assert.assertEquals(1, unknownWarnings.size());

        List<String> guardWarnings = new ArrayList<String>();
        CompatibilityPolicy missingGuard = policy(AngelicaEnvironment.present("2.1.50"), false, guardWarnings);
        Assert.assertFalse(missingGuard.permitsCapture());
        Assert.assertFalse(missingGuard.permitsCapture());
        Assert.assertEquals(1, guardWarnings.size());
    }

    /** FML 环境探针异常不得建立 scope，并只告警一次。 */
    @Test
    public void compatibilityProbeFailureFailsOpenOnce() {
        final List<String> warnings = new ArrayList<String>();
        CompatibilityPolicy policy = new CompatibilityPolicy(
                new AngelicaEnvironmentProbe() {
                    @Override
                    public AngelicaEnvironment inspect() {
                        throw new IllegalStateException("loader unavailable");
                    }
                },
                installed(true),
                warningSink(warnings));

        Assert.assertFalse(policy.permitsCapture());
        Assert.assertFalse(policy.permitsCapture());
        Assert.assertEquals(1, warnings.size());
    }

    private static ScopeCoordinator coordinator(boolean permitted) {
        return new ScopeCoordinator(
                constantPolicy(permitted),
                new ReplayRunner() {
                    @Override
                    public void run(Runnable batch) {
                        batch.run();
                    }
                });
    }

    private static CapturePolicy constantPolicy(final boolean permitted) {
        return new CapturePolicy() {
            @Override
            public boolean permitsCapture() {
                return permitted;
            }
        };
    }

    private static CompatibilityPolicy policy(
            final AngelicaEnvironment environment,
            boolean guardInstalled,
            List<String> warnings) {
        return new CompatibilityPolicy(
                new AngelicaEnvironmentProbe() {
                    @Override
                    public AngelicaEnvironment inspect() {
                        return environment;
                    }
                },
                installed(guardInstalled),
                warningSink(warnings));
    }

    private static GuardAvailability installed(final boolean installed) {
        return new GuardAvailability() {
            @Override
            public boolean isInstalled() {
                return installed;
            }
        };
    }

    private static WarningSink warningSink(final List<String> warnings) {
        return new WarningSink() {
            @Override
            public void warn(String message) {
                warnings.add(message);
            }
        };
    }

    private static Runnable add(final List<String> events, final String event) {
        return new Runnable() {
            @Override
            public void run() {
                events.add(event);
            }
        };
    }
}
