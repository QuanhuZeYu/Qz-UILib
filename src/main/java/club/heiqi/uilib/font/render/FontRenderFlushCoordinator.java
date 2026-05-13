package club.heiqi.uilib.font.render;

import java.util.Objects;

/**
 * 协调一次文本绘制尾声的批次提交顺序与状态边界。
 */
public class FontRenderFlushCoordinator {

    /**
     * 在同一份状态保护边界中依次提交字形批次与装饰线批次。
     *
     * @param stateExecutor 状态边界执行器
     * @param glyphFlushTask 字形批次提交任务
     * @param decorationFlushTask 装饰线批次提交任务
     */
    public void flush(FontRenderStateExecutor stateExecutor, Runnable glyphFlushTask, Runnable decorationFlushTask) {
        Objects.requireNonNull(stateExecutor, "stateExecutor");
        Objects.requireNonNull(glyphFlushTask, "glyphFlushTask");
        Objects.requireNonNull(decorationFlushTask, "decorationFlushTask");
        stateExecutor.run(new Runnable() {
            @Override
            public void run() {
                glyphFlushTask.run();
                decorationFlushTask.run();
            }
        });
    }
}
