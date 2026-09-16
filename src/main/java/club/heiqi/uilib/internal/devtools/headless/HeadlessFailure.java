package club.heiqi.uilib.internal.devtools.headless;

import java.io.PrintStream;

/**
 * headless 设施的统一失败类型：能力缺失、上下文创建失败、装配失败、读回/编码失败一律以此抛出。
 *
 * <p>为什么需要显式类型：{@code UiRenderBackend} 的若干 default 方法（{@code drawImage} /
 * {@code drawSegments} / {@code publishTextDemand}）带静默 no-op 兜底，本仓历史多次出现
 * 「没抛异常但内容缺失」。headless 设施要求失败可归因，故把阶段（{@link Stage}）编进异常，
 * 调用方与 agent 可据此区分「环境没准备好」与「UI 代码有问题」。</p>
 */
public final class HeadlessFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 失败阶段：定位「哪一层没准备好」，而不是只给一句消息。 */
    public enum Stage {
        /** 能力探测（GL 可用性、字体环境、尺寸上限）。 */
        CAPABILITY("能力探测"),
        /** GL 上下文 / 离屏帧缓冲。 */
        CONTEXT("GL 上下文"),
        /** scene 装配与宿主构建。 */
        ASSEMBLY("scene 装配"),
        /** 帧推进（布局 / 绘制 / 路由）。 */
        FRAME("帧推进"),
        /** 像素读回。 */
        READBACK("像素读回"),
        /** PNG 编码与落盘。 */
        ENCODE("PNG 编码");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        /** @return 中文阶段名，用于诊断输出 */
        public String label() {
            return label;
        }
    }

    private final Stage stage;

    public HeadlessFailure(Stage stage, String message) {
        super("[" + stage.label() + "] " + message);
        this.stage = stage;
    }

    public HeadlessFailure(Stage stage, String message, Throwable cause) {
        super("[" + stage.label() + "] " + message, cause);
        this.stage = stage;
    }

    /** @return 失败阶段 */
    public Stage stage() {
        return stage;
    }

    /**
     * 打印诊断（不抛出）：agent 与开发者的默认阅读入口。
     *
     * @param out 目标输出流
     */
    public void printDiagnosis(PrintStream out) {
        out.println("[headless] FAILED stage=" + stage.name() + " (" + stage.label() + ")");
        out.println("[headless] " + getMessage());
        Throwable cause = getCause();
        if (cause != null) {
            out.println("[headless] cause: " + cause.getClass().getName() + ": " + cause.getMessage());
        }
    }
}
