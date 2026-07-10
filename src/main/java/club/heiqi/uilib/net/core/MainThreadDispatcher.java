package club.heiqi.uilib.net.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 双端主线程派发队列。
 *
 * <h3>drain 预算（入口固定快照）</h3>
 * <p>{@link #drainClient()} / {@link #drainServer()} 在入口捕获 {@code queue.size()} 作为本轮预算，
 * 最多 {@code poll} 该数量任务。drain 期间 {@link #enqueue} 的新任务<strong>绝不</strong>在本次消费，
 * 留在队列由<strong>下一 tick</strong>（next-drain）再跑。保证：</p>
 * <ul>
 *   <li>已有任务 FIFO 顺序不变</li>
 *   <li>单任务异常隔离，不阻断同预算内后续任务</li>
 *   <li>producer / coordinator 在 drain 中 re-enqueue 不会同 tick 自旋耗尽</li>
 * </ul>
 *
 * <p>语义对照：入口队列 {@code [coordinator, producer-submit]} 时，第一次 drain 仅执行
 * coordinator（预算 2 中若 producer 在 coordinator 后入队则不在本轮；若入口已有 2 项则各执行一次，
 * 期间新增的第三项留 next-drain）。coordinator 在 apply 中/后若再 submit，新 pending 的
 * 调度任务仅 next-drain 消费。</p>
 *
 * <p>单 Runnable 的 {@link RuntimeException} / 非致命 {@link Error} 被隔离并日志，
 * 继续 drain 预算内后续任务。{@link VirtualMachineError}、{@link ThreadDeath}、
 * {@link LinkageError} 不吞掉。</p>
 */
public final class MainThreadDispatcher {

    private static final MainThreadDispatcher INSTANCE = new MainThreadDispatcher();

    private final Queue<Runnable> clientQueue = new ConcurrentLinkedQueue<Runnable>();
    private final Queue<Runnable> serverQueue = new ConcurrentLinkedQueue<Runnable>();

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
     * <p>若当前侧正在 drain，本任务计入<strong>next-drain</strong>，不会被本次预算消费。</p>
     *
     * @param side 目标侧
     * @param runnable 任务
     */
    public void enqueue(NetSide side, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (side == NetSide.CLIENT) {
            clientQueue.add(runnable);
        } else {
            serverQueue.add(runnable);
        }
    }

    /**
     * 排空客户端队列（入口 size 快照预算；期间 enqueue 的任务留 next-drain）。
     */
    public void drainClient() {
        drain(clientQueue, "CLIENT");
    }

    /**
     * 排空服务端队列（入口 size 快照预算；期间 enqueue 的任务留 next-drain）。
     */
    public void drainServer() {
        drain(serverQueue, "SERVER");
    }

    /**
     * 当前 CLIENT 队列任务数（测试探针；跨包测试可见）。
     *
     * @return 队列 size（ConcurrentLinkedQueue 近似）
     */
    public int clientQueueSize() {
        return clientQueue.size();
    }

    /**
     * 当前 SERVER 队列任务数（测试探针）。
     *
     * @return 队列 size
     */
    public int serverQueueSize() {
        return serverQueue.size();
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
     * 入口固定快照预算：捕获 size 后最多 poll 该数；期间新增绝不本次消费。
     */
    private void drain(Queue<Runnable> queue, String sideLabel) {
        // 入口快照：ConcurrentLinkedQueue.size 为 O(n) 近似，仅作本轮上界
        int budget = queue.size();
        for (int i = 0; i < budget; i++) {
            Runnable runnable = queue.poll();
            if (runnable == null) {
                break;
            }
            try {
                runnable.run();
            } catch (RuntimeException e) {
                report(sideLabel, e);
            } catch (AssertionError e) {
                report(sideLabel, e);
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
