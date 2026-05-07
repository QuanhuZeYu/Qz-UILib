package club.heiqi.uilib.ui.screen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;

/**
 * UI 界面全局协调器。
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
     * 刷新一帧 UI 输入与界面路由。
     */
    public void tick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        UiInputFrame frame = UiInputService.getInstance().collectFrame();

        GuiScreen currentScreen = minecraft.currentScreen;
        if (currentScreen instanceof BaseScreen) {
            ((BaseScreen) currentScreen).handleInputFrame(frame);
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
