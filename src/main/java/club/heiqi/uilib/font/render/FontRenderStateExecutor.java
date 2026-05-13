package club.heiqi.uilib.font.render;

/**
 * 字体渲染状态边界执行器。
 */
public interface FontRenderStateExecutor {

    /**
     * 在状态保护边界中执行任务。
     *
     * @param task 要执行的任务
     */
    void run(Runnable task);
}
