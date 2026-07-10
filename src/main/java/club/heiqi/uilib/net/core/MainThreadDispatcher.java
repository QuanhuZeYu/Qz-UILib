package club.heiqi.uilib.net.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.transport.NetSide;

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
 * <p>保证：</p>
 * <ul>
 *   <li>已有任务 FIFO 顺序不变</li>
 *   <li>单任务 {@link RuntimeException} / 非致命 {@link Error} 隔离，不阻断同 batch 后续</li>
 *   <li>producer / coordinator 在 drain 中 re-enqueue 不会同 tick 自旋耗尽</li>
 * </ul>
 *
 * <p>{@link VirtualMachineError}、{@link ThreadDeath}、{@link LinkageError} 不吞掉。
 * <strong>{@link AssertionError} 不捕获</strong>——测试 hook / 断言必须回传 JUnit，不得被 drain 吞掉。</p>
 */
public final class MainThreadDispatcher {

    private static final MainThreadDispatcher INSTANCE = new MainThreadDispatcher();

    private final Object clientLock = new Object();
    private final Object serverLock = new Object();
    /** 当前 CLIENT 入队队列；drain 时与空队列 swap */
    private Queue<Runnable> clientQueue = new ArrayDeque<Runnable>();
    /** 当前 SERVER 入队队列；drain 时与空队列 swap */
    private Queue<Runnable> serverQueue = new ArrayDeque<Runnable>();

    /**
     * 可选错误回调（测试注入）；null 时用 MyMod.LOG。
     */
    private final AtomicReference<ErrorSink> errorSink = new AtomicReference<ErrorSink>(null);

    private MainThreadDispatcher() {}

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
     * @param side 目标侧
     * @param runnable 任务
     */
    public void enqueue(NetSide side, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (side == NetSide.CLIENT) {
            synchronized (clientLock) {
                clientQueue.add(runnable);
            }
        } else {
            synchronized (serverLock) {
                serverQueue.add(runnable);
            }
        }
    }

    /**
     * 排空客户端队列（原子 swap 旧 batch；期间 enqueue 进新队列留 next-drain）。
     */
    public void drainClient() {
        drainSide(clientLock, true);
    }

    /**
     * 排空服务端队列（原子 swap 旧 batch；期间 enqueue 进新队列留 next-drain）。
     */
    public void drainServer() {
        drainSide(serverLock, false);
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
     * 安装错误回调（测试）；null 恢复默认日志。
     *
     * @param sink 回调
     */
    void setErrorSink(ErrorSink sink) {
        errorSink.set(sink);
    }

    /**
     * 原子 swap 取走旧 batch，锁外消费；swap 后 enqueue 只进新队列。
     */
    private void drainSide(Object lock, boolean client) {
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
        drainBatch(batch, client ? "CLIENT" : "SERVER");
    }

    private void drainBatch(Queue<Runnable> batch, String sideLabel) {
        Runnable runnable;
        while ((runnable = batch.poll()) != null) {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                report(sideLabel, e);
            } catch (AssertionError e) {
                // 不捕获：测试 hook / JUnit 断言必须回传
                throw e;
            } catch (Error e) {
                if (e instanceof VirtualMachineError
                        || e instanceof ThreadDeath
                        || e instanceof LinkageError) {
                    throw e;
                }
                report(sideLabel, e);
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
        void onError(String sideLabel, Throwable t);
    }
}
