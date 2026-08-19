package club.heiqi.uilib.ui.screen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;

/**
 * UI 界面全局协调器（切换界面任务的延后冲刷队列）。
 */
public class UiScreenManager {

    private static final UiScreenManager INSTANCE = new UiScreenManager();

    private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<Runnable>();

    private UiScreenManager() {}

    /**
     * 获取界面协调器单例。
     *
     * @return 协调器实例
     */
    public static UiScreenManager getInstance() {
        return INSTANCE;
    }

    /**
     * 把需要切换界面的操作延后到当前帧输入分发完成后执行。
     *
     * @param task 待执行任务
     */
    public void enqueue(Runnable task) {
        if (task == null) {
            return;
        }
        pendingTasks.add(task);
    }

    /**
     * 刷新一帧界面任务队列（旧 BaseScreen 输入路由已随旧壳删除，
     * scene 栈输入经 McScreenBridge 旁路独立驱动）。
     */
    public void tick() {
        if (Minecraft.getMinecraft() == null) {
            return;
        }
        runPendingTasks();
    }

    /**
     * 仅供测试或无 Minecraft 上下文时主动冲刷延后任务。
     */
    void flushPendingTasks() {
        runPendingTasks();
    }

    private void runPendingTasks() {
        while (!pendingTasks.isEmpty()) {
            Runnable task = pendingTasks.poll();
            if (task != null) {
                task.run();
            }
        }
    }
}
