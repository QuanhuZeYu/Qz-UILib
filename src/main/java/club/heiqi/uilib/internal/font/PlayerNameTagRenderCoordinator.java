package club.heiqi.uilib.internal.font;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

/**
 * 将玩家名称标签限制在单次 {@code RenderGlobal.renderEntities} 调用域内延后。
 *
 * <p>捕获项只存活于当前线程的当前 host scope；host 或回放异常都会丢弃尚未执行的尾项。
 * Angelica ABI 由可选 Mixin 握手，通用路径不直接引用 Angelica 类。</p>
 */
public final class PlayerNameTagRenderCoordinator {

    static final String ANGELICA_MOD_ID = "angelica";
    static final String SUPPORTED_ANGELICA_VERSION = "2.1.50";

    private static volatile boolean angelicaReplayGuardInstalled;

    private static final CompatibilityPolicy CAPTURE_POLICY = new CompatibilityPolicy(
            new FmlAngelicaEnvironmentProbe(),
            new GuardAvailability() {
                @Override
                public boolean isInstalled() {
                    return angelicaReplayGuardInstalled;
                }
            },
            new WarningSink() {
                @Override
                public void warn(String message) {
                    MyMod.LOG.warn(message);
                }
            });

    private static final ScopeCoordinator COORDINATOR = new ScopeCoordinator(
            CAPTURE_POLICY,
            new ReplayRunner() {
                @Override
                public void run(Runnable batch) {
                    runReplayBatch(batch);
                }
            });

    private PlayerNameTagRenderCoordinator() {}

    /**
     * 在一次原版世界实体遍历外建立独立捕获域，并在 host 正常返回后回放。
     *
     * @param hostCall 原始 {@code RenderGlobal.renderEntities} 调用链
     */
    public static void runHostPass(Runnable hostCall) {
        COORDINATOR.runHostPass(hostCall);
    }

    /**
     * 捕获当前调用，或在没有可用捕获域时立即执行原调用链。
     *
     * @param originalCall 保留全部 wrapper 的原调用
     */
    public static void captureOrRun(Runnable originalCall) {
        COORDINATOR.captureOrRun(originalCall);
    }

    /**
     * 为可选 Mixin 提供唯一的批次执行调用点。
     *
     * @param batch 当前 scope 的 FIFO 回放批次
     */
    private static void runReplayBatch(Runnable batch) {
        final EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        entityRenderer.enableLightmap(0.0D);
        try {
            batch.run();
        } finally {
            entityRenderer.disableLightmap(0.0D);
        }
    }

    /** 单次 host 调用域的线程局部 FIFO 协调器。 */
    static final class ScopeCoordinator {

        private final CapturePolicy capturePolicy;
        private final ReplayRunner replayRunner;
        private final ThreadLocal<Deque<Scope>> scopes = new ThreadLocal<Deque<Scope>>();

        ScopeCoordinator(CapturePolicy capturePolicy, ReplayRunner replayRunner) {
            if (capturePolicy == null || replayRunner == null) {
                throw new IllegalArgumentException("capturePolicy and replayRunner must not be null");
            }
            this.capturePolicy = capturePolicy;
            this.replayRunner = replayRunner;
        }

        /** 建立、结束并清理一个 host scope。 */
        void runHostPass(Runnable hostCall) {
            requireCall(hostCall);
            if (!capturePolicy.permitsCapture()) {
                hostCall.run();
                return;
            }

            Deque<Scope> stack = scopes.get();
            if (stack == null) {
                stack = new ArrayDeque<Scope>();
                scopes.set(stack);
            }

            final Scope scope = new Scope();
            stack.push(scope);
            try {
                hostCall.run();
                scope.capturing = false;
                if (!scope.pending.isEmpty()) {
                    replayRunner.run(new Runnable() {
                        @Override
                        public void run() {
                            drain(scope);
                        }
                    });
                }
            } finally {
                scope.capturing = false;
                scope.pending.clear();
                stack.pop();
                if (stack.isEmpty()) {
                    scopes.remove();
                }
            }
        }

        /** 捕获到当前顶层 scope；回放期与无 scope 路径均立即执行。 */
        void captureOrRun(Runnable originalCall) {
            requireCall(originalCall);
            Deque<Scope> stack = scopes.get();
            Scope scope = stack == null ? null : stack.peek();
            if (scope == null || !scope.capturing) {
                originalCall.run();
                return;
            }
            scope.pending.addLast(originalCall);
        }

        /** 返回当前线程 scope 深度，仅供同包测试核对异常清理。 */
        int scopeDepth() {
            Deque<Scope> stack = scopes.get();
            return stack == null ? 0 : stack.size();
        }

        private static void drain(Scope scope) {
            Runnable pending;
            while ((pending = scope.pending.pollFirst()) != null) {
                pending.run();
            }
        }

        private static void requireCall(Runnable call) {
            if (call == null) {
                throw new IllegalArgumentException("render call must not be null");
            }
        }
    }

    /** 当前 scope 的捕获状态与 FIFO。 */
    private static final class Scope {

        private final Deque<Runnable> pending = new ArrayDeque<Runnable>();
        private boolean capturing = true;
    }

    /** 判断本次 host 是否可安全捕获。 */
    interface CapturePolicy {

        boolean permitsCapture();
    }

    /** 执行整个回放批次，生产实现由可选 Mixin 包装。 */
    interface ReplayRunner {

        void run(Runnable batch);
    }

    /** 查询 Angelica FML 容器状态。 */
    interface AngelicaEnvironmentProbe {

        AngelicaEnvironment inspect();
    }

    /** 查询可选回放围栏是否已由 Mixin 安装。 */
    interface GuardAvailability {

        boolean isInstalled();
    }

    /** 单次兼容降级告警出口。 */
    interface WarningSink {

        void warn(String message);
    }

    /** Angelica 是否存在及其原始版本字符串。 */
    static final class AngelicaEnvironment {

        private final boolean present;
        private final String version;

        private AngelicaEnvironment(boolean present, String version) {
            this.present = present;
            this.version = version;
        }

        static AngelicaEnvironment absent() {
            return new AngelicaEnvironment(false, null);
        }

        static AngelicaEnvironment present(String version) {
            return new AngelicaEnvironment(true, version);
        }
    }

    /**
     * 仅允许无 Angelica，或精确受支持版本且可选围栏已完成握手的捕获。
     */
    static final class CompatibilityPolicy implements CapturePolicy {

        private final AngelicaEnvironmentProbe environmentProbe;
        private final GuardAvailability guardAvailability;
        private final WarningSink warningSink;
        private final AtomicBoolean warned = new AtomicBoolean();

        CompatibilityPolicy(
                AngelicaEnvironmentProbe environmentProbe,
                GuardAvailability guardAvailability,
                WarningSink warningSink) {
            this.environmentProbe = environmentProbe;
            this.guardAvailability = guardAvailability;
            this.warningSink = warningSink;
        }

        @Override
        public boolean permitsCapture() {
            final AngelicaEnvironment environment;
            try {
                environment = environmentProbe.inspect();
            } catch (RuntimeException exception) {
                warnOnce("玩家标签延后无法读取 Angelica 版本，已保持即时绘制："
                        + exception.getClass().getName());
                return false;
            } catch (LinkageError error) {
                warnOnce("玩家标签延后无法链接 Angelica 兼容探针，已保持即时绘制："
                        + error.getClass().getName());
                return false;
            }

            if (environment == null) {
                warnOnce("玩家标签延后收到空兼容状态，已保持即时绘制");
                return false;
            }
            if (!environment.present) {
                return true;
            }
            if (!SUPPORTED_ANGELICA_VERSION.equals(environment.version)) {
                warnOnce("玩家标签延后仅支持 Angelica " + SUPPORTED_ANGELICA_VERSION
                        + "，当前版本为 " + String.valueOf(environment.version) + "，已保持即时绘制");
                return false;
            }
            if (!guardAvailability.isInstalled()) {
                warnOnce("玩家标签延后的 Angelica 回放围栏未安装，已保持即时绘制");
                return false;
            }
            return true;
        }

        private void warnOnce(String message) {
            if (warned.compareAndSet(false, true)) {
                warningSink.warn(message);
            }
        }
    }

    /** 从 FML 的 mod ID 索引读取 Angelica 环境。 */
    private static final class FmlAngelicaEnvironmentProbe implements AngelicaEnvironmentProbe {

        @Override
        public AngelicaEnvironment inspect() {
            Map<String, ModContainer> indexedMods = Loader.instance().getIndexedModList();
            ModContainer angelica = indexedMods == null ? null : indexedMods.get(ANGELICA_MOD_ID);
            return angelica == null
                    ? AngelicaEnvironment.absent()
                    : AngelicaEnvironment.present(angelica.getVersion());
        }
    }
}
