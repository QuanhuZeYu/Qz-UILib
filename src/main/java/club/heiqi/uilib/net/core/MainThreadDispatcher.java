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
 * <p>drain 时单 Runnable 的 {@link RuntimeException} / 非致命 {@link Error} 被隔离并日志，
 * 继续 drain 后续任务，避免任意网络/配置任务阻断整队列。
 * {@link VirtualMachineError}、{@link ThreadDeath}、{@link LinkageError} 不吞掉。</p>
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
     * 排空客户端队列。
     */
    public void drainClient() {
        drain(clientQueue, "CLIENT");
    }

    /**
     * 排空服务端队列。
     */
    public void drainServer() {
        drain(serverQueue, "SERVER");
    }

    /**
     * 当前 CLIENT 队列任务数（测试探针）。
     *
     * @return 队列 size（ConcurrentLinkedQueue 近似）
     */
    int clientQueueSize() {
        return clientQueue.size();
    }

    /**
     * 安装错误回调（测试）；null 恢复默认日志。
     *
     * @param sink 回调
     */
    void setErrorSink(ErrorSink sink) {
        errorSink.set(sink);
    }

    private void drain(Queue<Runnable> queue, String sideLabel) {
        Runnable runnable;
        while ((runnable = queue.poll()) != null) {
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
