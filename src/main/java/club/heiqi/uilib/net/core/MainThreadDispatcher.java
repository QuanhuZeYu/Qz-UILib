package club.heiqi.uilib.net.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.util.LaunchSide;

/**
 * 双端主线程派发队列。
 *
 * <h3>drain 批次交换（真正 batch swap）</h3>
 * <p>每 side 维护 {@code lock + current queue}：</p>
 * <ul>
 *   <li>{@link #enqueue} 在 lock 内加入 <strong>current</strong></li>
 *   <li>{@link #drainClient}/{@link #drainServer} 在 lock 内原子 swap 为新空 queue，
 *       锁外只消费旧 batch</li>
 *   <li>swap 后任何 enqueue 只能进新队列，由<strong>下一 tick</strong>处理</li>
 *   <li>批内 FIFO；无丢失</li>
 * </ul>
 * <p><b>禁止</b> {@code ConcurrentLinkedQueue.size()} 快照预算——size 为 O(n) 近似且与
 * 并发 producer 无精确 barrier。本实现以 swap 建立精确批次边界。</p>
 *
 * <h3>per-side drain owner（机械守卫）</h3>
 * <p>生产 Forge 主线程单线程 drain，但测试/误用可能并发调用 drain。
 * 每 side 用 {@link AtomicBoolean} drain owner CAS：第二 drainer 拒绝并返回 0，
 * <strong>不可</strong>同时消费两个 batch。</p>
 *
 * <p>保证：</p>
 * <ul>
 *   <li>已有任务 FIFO 顺序不变</li>
 *   <li>单任务 {@link RuntimeException} / 非致命 {@link Error} 隔离，不阻断同 batch 后续</li>
 *   <li>{@link AssertionError}：无论任务体直接抛，或 {@link ErrorSink} 回调抛，
 *       传播前均将旧 batch 未消费尾部按原顺序<strong>前置</strong>到当前 next batch，
 *       再 rethrow——下一 drain 先跑旧尾再跑期间新任务；测试 hook / 断言必须回传 JUnit</li>
 *   <li>{@link ErrorSink} 不得吞掉 {@link AssertionError}；若 sink 抛 AssertionError，
 *       与任务体 Assertion 同路径：尾重排 + rethrow</li>
 *   <li>producer / coordinator 在 drain 中 re-enqueue 不会同 tick 自旋耗尽</li>
 *   <li>专用服务端拒绝 {@link NetSide#CLIENT} 入队——该侧的排空通道只由
 *       {@code ClientTickEvent} 驱动，照旧入队等于承诺执行但永不执行，
 *       且留下无人消费的无界队列；改为丢弃 + 告警一次</li>
 * </ul>
 *
 * <p>{@link VirtualMachineError}、{@link ThreadDeath}、{@link LinkageError} 不吞掉。</p>
 */
public final class MainThreadDispatcher {

    private static final MainThreadDispatcher INSTANCE = new MainThreadDispatcher();

    private final Object clientLock = new Object();
    private final Object serverLock = new Object();
    /** 当前 CLIENT 入队队列；drain 时与空队列 swap */
    private Queue<Runnable> clientQueue = new ArrayDeque<Runnable>();
    /** 当前 SERVER 入队队列；drain 时与空队列 swap */
    private Queue<Runnable> serverQueue = new ArrayDeque<Runnable>();

    /** CLIENT 侧 drain owner：true 表示已有 drainer 在消费 */
    private final AtomicBoolean clientDrainOwner = new AtomicBoolean(false);
    /** SERVER 侧 drain owner */
    private final AtomicBoolean serverDrainOwner = new AtomicBoolean(false);

    /**
     * 可选错误回调（测试注入）；null 时用 MyMod.LOG。
     * <p>契约：sink 不得抛 {@link AssertionError} 后指望调用方吞掉——
     * 若 sink 抛 AssertionError，drain 会尾重排并 rethrow（与任务体 Assertion 同路径）。
     * 推荐 sink 只记录，不抛。</p>
     */
    private final AtomicReference<ErrorSink> errorSink = new AtomicReference<ErrorSink>(null);

    /** CLIENT 任务被侧别判据拒绝的告警开关（只告一次，避免刷屏）。 */
    private final AtomicBoolean clientRejectLogged = new AtomicBoolean(false);

    /** 启动侧覆盖，仅测试用；null 表示读真实启动侧。 */
    private static final AtomicReference<LaunchSide> LAUNCH_SIDE_OVERRIDE =
            new AtomicReference<LaunchSide>(null);

    private MainThreadDispatcher() {}

    /**
     * 测试接缝：覆盖启动侧判定，用于在同一个 JVM 里分别跑客户端与服务端分支。
     *
     * @param launchSide 覆盖值；传 null 恢复真实启动侧
     */
    static void overrideLaunchSideForTests(LaunchSide launchSide) {
        LAUNCH_SIDE_OVERRIDE.set(launchSide);
    }

    private static LaunchSide launchSide() {
        LaunchSide override = LAUNCH_SIDE_OVERRIDE.get();
        return override != null ? override : LaunchSide.LAUNCH;
    }

    /**
     * 返回单例。
     *
     * @return 派发器
     */
    public static MainThreadDispatcher getInstance() {
        return INSTANCE;
    }

    /**
     * 返回可传给 CompletableFuture 的主线程 executor。
     *
     * @return executor
     */
    public Executor asExecutor(final NetSide side) {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                enqueue(side, command);
            }
        };
    }

    /**
     * 入队到指定主线程。
     *
     * <p>若当前侧正在 drain（已 swap），本任务进入<strong>新 current</strong>，
     * 计入 next-drain，不会被本次 batch 消费。</p>
     *
     * <p><b>专用服务端不接受 {@link NetSide#CLIENT} 任务</b>：{@code drainClient()} 的唯一
     * 触发点是 {@code ClientTickEvent}，服务端永远不会发。照旧入队就等于"承诺执行但
     * 永不执行"，且队列无人消费、无界增长（#71 同族审计 B5）。这里直接拒绝入队并
     * 告警一次，而不是抛异常——同一份代码在两侧跑，不该让服务端因为一段 UI 逻辑崩掉。</p>
     *
     * @param side 目标侧
     * @param runnable 任务
     */
    public void enqueue(NetSide side, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (side == NetSide.CLIENT) {
            if (launchSide().isDedicatedServer()) {
                rejectClientTaskOnDedicatedServer();
                return;
            }
            synchronized (clientLock) {
                clientQueue.add(runnable);
            }
        } else {
            synchronized (serverLock) {
                serverQueue.add(runnable);
            }
        }
    }

    private void rejectClientTaskOnDedicatedServer() {
        if (!clientRejectLogged.compareAndSet(false, true)) {
            return;
        }
        MyMod.LOG.warn("专用服务端没有客户端主线程排空通道（drainClient 只由 ClientTickEvent 触发），"
                + "NetSide.CLIENT 任务不会执行；本次入队已丢弃，后续同类入队同样丢弃且不再重复告警。"
                + "服务端需要跑的逻辑请改用 NetSide.SERVER 入队。");
    }

    /**
     * 排空客户端队列（原子 swap 旧 batch；期间 enqueue 进新队列留 next-drain）。
     * <p>并发第二 drainer 拒绝并返回 0（不消费）。</p>
     *
     * @return 本轮消费的任务数；第二 drainer 或空队列为 0
     */
    public int drainClient() {
        return drainSide(clientLock, clientDrainOwner, true);
    }

    /**
     * 排空服务端队列（原子 swap 旧 batch；期间 enqueue 进新队列留 next-drain）。
     * <p>并发第二 drainer 拒绝并返回 0（不消费）。</p>
     *
     * @return 本轮消费的任务数；第二 drainer 或空队列为 0
     */
    public int drainServer() {
        return drainSide(serverLock, serverDrainOwner, false);
    }

    /**
     * 当前 CLIENT 队列任务数（测试探针；跨包测试可见）。
     *
     * @return 当前入队队列 size（精确，持 lock）
     */
    public int clientQueueSize() {
        synchronized (clientLock) {
            return clientQueue.size();
        }
    }

    /**
     * 当前 SERVER 队列任务数（测试探针）。
     *
     * @return 当前入队队列 size（精确，持 lock）
     */
    public int serverQueueSize() {
        synchronized (serverLock) {
            return serverQueue.size();
        }
    }

    /**
     * CLIENT 侧是否正被某 drainer 占用（测试探针）。
     *
     * @return true 表示 drain 进行中
     */
    public boolean isClientDrainOwned() {
        return clientDrainOwner.get();
    }

    /**
     * SERVER 侧是否正被某 drainer 占用（测试探针）。
     *
     * @return true 表示 drain 进行中
     */
    public boolean isServerDrainOwned() {
        return serverDrainOwner.get();
    }

    /**
     * 安装错误回调（测试）；null 恢复默认日志。
     *
     * @param sink 回调
     */
    void setErrorSink(ErrorSink sink) {
        errorSink.set(sink);
    }

    /**
     * 原子 swap 取走旧 batch，锁外消费；swap 后 enqueue 只进新队列。
     * per-side drain owner CAS：第二 drainer 立即返回 0。
     *
     * @return 本轮消费任务数
     */
    private int drainSide(Object lock, AtomicBoolean drainOwner, boolean client) {
        if (!drainOwner.compareAndSet(false, true)) {
            // 第二 drainer 拒绝：不可同时消费两个 batch
            return 0;
        }
        try {
            final Queue<Runnable> batch;
            synchronized (lock) {
                if (client) {
                    batch = clientQueue;
                    clientQueue = new ArrayDeque<Runnable>();
                } else {
                    batch = serverQueue;
                    serverQueue = new ArrayDeque<Runnable>();
                }
            }
            return drainBatch(lock, client, batch, client ? "CLIENT" : "SERVER");
        } finally {
            drainOwner.set(false);
        }
    }

    /**
     * @return 实际 run 过的任务数（含抛异常的）
     */
    private int drainBatch(Object lock, boolean client, Queue<Runnable> batch, String sideLabel) {
        int ran = 0;
        Runnable runnable;
        while ((runnable = batch.poll()) != null) {
            ran++;
            try {
                runnable.run();
            } catch (RuntimeException e) {
                // ErrorSink 若抛 AssertionError：统一尾重排 + rethrow
                reportOrRethrowAssertion(sideLabel, e, lock, client, batch);
            } catch (AssertionError e) {
                // 锁内：旧 batch 未消费尾部按原顺序前置到当前 next batch，再 rethrow
                prependRemainingToNextBatch(lock, client, batch);
                throw e;
            } catch (Error e) {
                if (e instanceof VirtualMachineError
                        || e instanceof ThreadDeath
                        || e instanceof LinkageError) {
                    throw e;
                }
                reportOrRethrowAssertion(sideLabel, e, lock, client, batch);
            }
        }
        return ran;
    }

    /**
     * 报告错误；若 ErrorSink 抛出 {@link AssertionError}，则尾重排后 rethrow
     *（与任务体 Assertion 同路径，保证测试断言可见且旧尾不丢）。
     */
    private void reportOrRethrowAssertion(String sideLabel, Throwable t,
                                          Object lock, boolean client, Queue<Runnable> batch) {
        try {
            report(sideLabel, t);
        } catch (AssertionError ae) {
            prependRemainingToNextBatch(lock, client, batch);
            throw ae;
        }
    }

    /**
     * AssertionError 传播前：将旧 batch 剩余任务按原 FIFO 前置到当前 next batch，
     * 保证下一 drain 先跑旧尾再跑期间新任务。
     */
    private void prependRemainingToNextBatch(Object lock, boolean client, Queue<Runnable> remaining) {
        synchronized (lock) {
            ArrayDeque<Runnable> merged = new ArrayDeque<Runnable>();
            Runnable r;
            while ((r = remaining.poll()) != null) {
                merged.addLast(r);
            }
            Queue<Runnable> current = client ? clientQueue : serverQueue;
            for (Runnable existing : current) {
                merged.addLast(existing);
            }
            if (client) {
                clientQueue = merged;
            } else {
                serverQueue = merged;
            }
        }
    }

    private void report(String sideLabel, Throwable t) {
        ErrorSink sink = errorSink.get();
        if (sink != null) {
            sink.onError(sideLabel, t);
            return;
        }
        MyMod.LOG.error("MainThreadDispatcher " + sideLabel + " task failed, continuing drain", t);
    }

    /** 错误汇（测试/可选注入）。 */
    interface ErrorSink {
        /**
         * 任务失败回调。
         * <p>契约：不得依赖调用方吞掉 {@link AssertionError}；
         * 若本方法抛 AssertionError，drain 会尾重排并 rethrow。</p>
         *
         * @param sideLabel CLIENT / SERVER
         * @param t         任务异常
         */
        void onError(String sideLabel, Throwable t);
    }
}
