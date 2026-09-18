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

    /** 无 Angelica 不要求可选围栏；四档已复核版本（2.8.x 两档 + 2.9.x 两档）且握手完成时都允许捕获。 */
    @Test
    public void compatibilityAllowsAbsentOrReviewedGuardedAngelica() {
        List<String> absentWarnings = new ArrayList<String>();
        Assert.assertTrue(policy(AngelicaEnvironment.absent(), false, absentWarnings).permitsCapture());
        Assert.assertEquals(0, absentWarnings.size());

        List<String> beta57Warnings = new ArrayList<String>();
        Assert.assertTrue(policy(AngelicaEnvironment.present("1.0.0-beta57"), true, beta57Warnings).permitsCapture());
        Assert.assertEquals(0, beta57Warnings.size());

        List<String> beta66bWarnings = new ArrayList<String>();
        Assert.assertTrue(policy(AngelicaEnvironment.present("1.0.0-beta66b"), true, beta66bWarnings).permitsCapture());
        Assert.assertEquals(0, beta66bWarnings.size());

        List<String> beta2Warnings = new ArrayList<String>();
        Assert.assertTrue(policy(AngelicaEnvironment.present("2.1.50"), true, beta2Warnings).permitsCapture());
        Assert.assertEquals(0, beta2Warnings.size());

        List<String> beta3Warnings = new ArrayList<String>();
        Assert.assertTrue(policy(AngelicaEnvironment.present("2.2.10"), true, beta3Warnings).permitsCapture());
        Assert.assertEquals(0, beta3Warnings.size());
    }

    /**
     * 版本号不再是闸门：未复核版本照旧允许捕获（行为由围栏的能力档位决定），只留一条复核提示；
     * 握手未完成仍 fail-open，且每个策略实例只告警一次。
     */
    @Test
    public void compatibilityAllowsUnreviewedVersionWithSingleNoticeAndStillRequiresGuard() {
        List<String> unknownWarnings = new ArrayList<String>();
        // 比已复核版本更新的未复核版本：按能力档位启用，不因版本号被拒。
        CompatibilityPolicy unknown = policy(AngelicaEnvironment.present("2.2.11"), true, unknownWarnings);
        Assert.assertTrue(unknown.permitsCapture());
        Assert.assertTrue(unknown.permitsCapture());
        Assert.assertEquals(1, unknownWarnings.size());

        List<String> legacyAdjacentWarnings = new ArrayList<String>();
        CompatibilityPolicy legacyAdjacent = policy(
                AngelicaEnvironment.present("2.1.51"), true, legacyAdjacentWarnings);
        Assert.assertTrue(legacyAdjacent.permitsCapture());
        Assert.assertEquals(1, legacyAdjacentWarnings.size());

        List<String> guardWarnings = new ArrayList<String>();
        CompatibilityPolicy missingGuard = policy(AngelicaEnvironment.present("2.2.10"), false, guardWarnings);
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
