package club.heiqi.uilib.net.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import club.heiqi.uilib.net.transport.NetSide;

/**
 * 双端主线程派发队列。
 */
public final class MainThreadDispatcher {

    private static final MainThreadDispatcher INSTANCE = new MainThreadDispatcher();

    private final Queue<Runnable> clientQueue = new ConcurrentLinkedQueue<Runnable>();
    private final Queue<Runnable> serverQueue = new ConcurrentLinkedQueue<Runnable>();

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
        drain(clientQueue);
    }

    /**
     * 排空服务端队列。
     */
    public void drainServer() {
        drain(serverQueue);
    }

    private void drain(Queue<Runnable> queue) {
        Runnable runnable;
        while ((runnable = queue.poll()) != null) {
            runnable.run();
        }
    }
}
