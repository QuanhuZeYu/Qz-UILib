package club.heiqi.uilib.internal.devtools.headless;

import java.io.PrintStream;

/**
 * headless 设施的统一失败类型：能力缺失、上下文创建失败、装配失败、读回/编码失败一律以此抛出。
 *
 * <p>为什么需要显式类型：{@code UiRenderBackend} 的若干 default 方法（{@code drawImage} /
 * {@code drawSegments} / {@code publishTextDemand}）带静默 no-op 兜底，本仓历史多次出现
 * 「没抛异常但内容缺失」。headless 设施要求失败可归因，故把阶段（{@link Stage}）编进异常，
 * 调用方与 agent 可据此区分「环境没准备好」与「UI 代码有问题」。</p>
 *
 * <p><b>阶段与「环境是否具备」是两个正交维度</b>：{@link Stage} 说「哪一层没准备好」，
 * {@link #isEnvironmentUnavailable()} 说「这台机器有没有能力跑 headless 出图」。
 * 后者单独成维是因为同属 {@code Stage.CONTEXT} 的两种情况处置完全相反：natives 加载失败 /
 * 上下文建不起来是运行环境的事实（调用方应跳过或换环境），而 FBO 不完整、像素面已关闭是
 * 设施自身的缺陷（必须红）。把它塞进 {@code Stage} 枚举会让「阶段」同时承担两种含义。</p>
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
    /** 失败是否源于运行环境不具备（而非被测代码）：见 {@link #isEnvironmentUnavailable()}。 */
    private final boolean environmentUnavailable;

    public HeadlessFailure(Stage stage, String message) {
        this(stage, message, null, false);
    }

    public HeadlessFailure(Stage stage, String message, Throwable cause) {
        this(stage, message, cause, false);
    }

    private HeadlessFailure(Stage stage, String message, Throwable cause, boolean environmentUnavailable) {
        super("[" + stage.label() + "] " + message, cause);
        this.stage = stage;
        this.environmentUnavailable = environmentUnavailable;
    }

    /**
     * 运行环境不具备：本进程连 GL 上下文都拿不到（natives 加载失败 / AWT 无窗口句柄能力 /
     * 上下文创建失败），此后任何出图都不可能成功。
     *
     * <p>与普通构造的区别只有一个：{@code HeadlessShotMain} 据此返回专用退出码
     * （{@code EXIT_ENVIRONMENT_UNAVAILABLE}），测试侧据此 {@code Assume} 跳过而不是报缺陷。
     * 用工厂方法而不是 public 构造，是为了让「这是环境事实」在<b>抛出点</b>就写明，
     * 而不是让调用方从消息文本里猜。</p>
     *
     * @param stage   失败阶段（目前仅 {@link Stage#CONTEXT} 使用）
     * @param message 诊断文案
     * @param cause   原始异常（可为 null）
     * @return 标记为环境不具备的失败
     */
    public static HeadlessFailure environmentUnavailable(Stage stage, String message, Throwable cause) {
        return new HeadlessFailure(stage, message, cause, true);
    }

    /** @return 失败阶段 */
    public Stage stage() {
        return stage;
    }

    /** @return true = 运行环境不具备 headless 出图能力，不是被测代码的缺陷 */
    public boolean isEnvironmentUnavailable() {
        return environmentUnavailable;
    }

    /**
     * 打印诊断（不抛出）：agent 与开发者的默认阅读入口。
     *
     * @param out 目标输出流
     */
    public void printDiagnosis(PrintStream out) {
        out.println("[headless] FAILED stage=" + stage.name() + " (" + stage.label() + ")");
        if (environmentUnavailable) {
            // 单独一行且用固定词 ENV-UNAVAILABLE：CI 日志与测试输出都靠它一眼区分
            // 「这台机器跑不了」与「能出图但坏了」，不必去读下面的 cause 文本。
            out.println("[headless] ENV-UNAVAILABLE：运行环境不具备 headless 出图能力，"
                    + "本次终止不是被测代码的缺陷");
        }
        out.println("[headless] " + getMessage());
        Throwable cause = getCause();
        if (cause != null) {
            out.println("[headless] cause: " + cause.getClass().getName() + ": " + brief(cause));
        }
    }

    /**
     * 截断异常文案：{@code UnsatisfiedLinkError} 会把整条 {@code java.library.path} 拼进消息（可达数千字符），
     * 直接透传会让诊断不可读。全设施共用这一份实现。
     *
     * @param failure 异常
     * @return 截断后的单行描述
     */
    static String brief(Throwable failure) {
        String message = failure.getMessage();
        if (message == null) {
            return failure.getClass().getSimpleName();
        }
        String single = message.replace('\n', ' ').trim();
        return single.length() <= 200 ? single : single.substring(0, 200) + "…";
    }
}
