package club.heiqi.uilib.ui.scene.testkit;

import club.heiqi.uilib.ui.env.DiagnosticsEnvironment;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 测试侧 scene runtime 构造收口 —— 环境端口缺席态的统一出口。
 *
 * <h3>为什么要有它</h3>
 * <p>{@link SceneRuntime} 的唯一公开构造要求调用方<b>显式表态</b>环境端口（传 {@code null} 会
 * 快速失败，不再有「缺省即缺席」的静默路径）。测试的绝大多数场景并不关心环境事实，
 * 逐处写 {@code new SceneRuntime(measurer, UiEnvironment.empty())} 只是样板；本类把该表态收口为
 * 一处，使「测试用的是缺席环境」这件事在调用点一眼可见，也让将来「测试需要真环境」时只改一处。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>{@link #runtime()} / {@link #runtime(SceneTextMeasurer)} 给出的是<b>缺席环境</b>：
 *       与改前 {@code new SceneRuntime()} / {@code new SceneRuntime(measurer)} 的行为逐位等价
 *       （缺省环境端口时三个域的缺席值 = 今日未安装态：不采样、语言码不可得、资源代际 0）。</li>
 *   <li>需要观察特定环境事实（调试开关、语言码、资源代际）的测试走
 *       {@link #runtime(SceneTextMeasurer, UiEnvironment)}，注入自定义实现，
 *       <b>不要</b>去改进程单例（那会污染同 JVM 的其它测试）。</li>
 * </ul>
 *
 * @see club.heiqi.uilib.ui.env.UiEnvironment
 */
public final class SceneTestEnvironments {

    private SceneTestEnvironments() {
    }

    /**
     * 缺席环境 + 无度量端口（等价于改前的 {@code new SceneRuntime()}）。
     *
     * <p>无度量端口时调 {@link SceneRuntime#measureTextWidth} / {@link SceneRuntime#lineHeight}
     * 会抛 {@link IllegalStateException}；控件构建期需要度量的测试请用
     * {@link #runtime(SceneTextMeasurer)}。</p>
     *
     * @return 新 runtime
     */
    public static SceneRuntime runtime() {
        return new SceneRuntime(null, UiEnvironment.empty());
    }

    /**
     * 缺席环境 + 指定度量端口（等价于改前的 {@code new SceneRuntime(measurer)}）。
     *
     * @param measurer 文本度量端口，可为 null
     * @return 新 runtime
     */
    public static SceneRuntime runtime(SceneTextMeasurer measurer) {
        return new SceneRuntime(measurer, UiEnvironment.empty());
    }

    /**
     * 指定环境 + 指定度量端口（环境隔离测试入口）。
     *
     * @param measurer    文本度量端口，可为 null
     * @param environment 宿主环境端口，不可为 null
     * @return 新 runtime
     */
    public static SceneRuntime runtime(SceneTextMeasurer measurer, UiEnvironment environment) {
        return new SceneRuntime(measurer, environment);
    }

    /**
     * @return 缺席环境（测试默认；与 {@link UiEnvironment#empty()} 同一实例）
     */
    public static UiEnvironment emptyEnvironment() {
        return UiEnvironment.empty();
    }

    /**
     * 诊断开启环境：只覆盖诊断域（{@code debugEnabled() == true}），语言与资源域缺席。
     *
     * <p><b>开关单源</b>：帧管线与 {@code UiPerformanceMonitor} 都只读注入的诊断域，
     * 故「完整采样」只需注入本环境<b>一处</b>，不需要再写任何配置静态字段。</p>
     *
     * @return 诊断开启的测试环境（无状态单例）
     */
    public static UiEnvironment debugEnabled() {
        return DEBUG_ENABLED;
    }

    /** 诊断开启环境单例（无状态，可跨 runtime 共享）。 */
    private static final UiEnvironment DEBUG_ENABLED = new UiEnvironment() {
        @Override
        public DiagnosticsEnvironment diagnostics() {
            return () -> true;
        }
    };
}
