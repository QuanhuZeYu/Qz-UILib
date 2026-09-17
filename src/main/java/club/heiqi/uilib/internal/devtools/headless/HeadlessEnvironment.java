package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.ui.env.DiagnosticsEnvironment;
import club.heiqi.uilib.ui.env.UiEnvironment;

/**
 * headless 环境端口：把请求声明的环境事实投影为 {@link UiEnvironment} 的域实现。
 *
 * <h3>为什么需要它</h3>
 * <p>{@code package-info} 的不变量 3 声明「尺寸、缩放、帧数、输出路径全部来自请求；不读全局单例的隐藏
 * 状态」，但环境事实此前仍取 {@code SceneHostAssembly.defaultEnvironment()} —— 在 headless 进程里那是
 * {@code ProcessUiEnvironment}，即「读生产配置的隐藏状态」：同一份请求在不同 {@code Config} 下出图不同，
 * 且诊断采样永远打不开（{@code Config.useDebug} 缺省 false）。本类兑现这条不变量：环境事实由
 * {@link HeadlessRequest} 声明、会话装配时注入，与生产走<b>同一端口、不同实现</b>，不新增旁路开关。</p>
 *
 * <h3>只覆盖诊断域</h3>
 * <p>语言域与资源域按缺席实现（{@link UiEnvironment#locale()} / {@link UiEnvironment#resources()} 的
 * default）：二者在生产分别由 {@code LanguageEpochService} / {@code ResourceReloadService} 供给，headless
 * 进程里这两个服务不存在，注入一个「假语言码 / 假代际」只会让消费者读到不存在的事实。将来要出多语言
 * 矩阵时，正确做法是先让 headless 具备真实文本供给，而不是在此伪造语言码。</p>
 *
 * <h3>调试浮层的缺席是事实而非缺省</h3>
 * <p>{@link DiagnosticsEnvironment#debugOverlayEnabled()} 保持缺席值 {@code false}：调试浮层在生产是
 * client HUD 注册表里的一份内容（{@code UiHudRenderListener#registerDebugHud}），headless 没有注册表，
 * 因此确实没有浮层可显示 —— 这与 {@link DiagnosticsEnvironment#EMPTY} 的值相同而语义不同。将来 headless
 * 补上 HUD 注册表模拟时，浮层开关随该批次接入，不在此提前声明 {@code true}。</p>
 *
 * <h3>值读纪律</h3>
 * <p>本实现的值读是构造期定值（headless 会话内环境不变），故无条件直读 final 字段：请求是不可变的，
 * 一条命令一个进程，不存在运行期变更，也就不需要代际或订阅通道。生产侧的「帧内直读最新值」纪律在此
 * 退化为常量读，语义等价且无快照风险。</p>
 */
final class HeadlessEnvironment implements UiEnvironment {

    /**
     * 诊断关闭态的环境：与 {@link DiagnosticsEnvironment#EMPTY} 同实例。
     *
     * <p>复用同一实例而非新建：缺席值必须逐位等价，共享单例使等价性由构造事实保证，而不是由
     * 两处字面量恰好相同保证。</p>
     */
    private final DiagnosticsEnvironment diagnostics;

    private HeadlessEnvironment(DiagnosticsEnvironment diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * 采样开启态的诊断域。
     *
     * <p>匿名实现只覆盖 {@link DiagnosticsEnvironment#debugEnabled()}：另外两个成员按接口 default 取
     * 缺席值，理由见类 javadoc「调试浮层的缺席是事实而非缺省」。</p>
     */
    private static final DiagnosticsEnvironment ENABLED = new DiagnosticsEnvironment() {
        @Override
        public boolean debugEnabled() {
            return true;
        }
    };

    /**
     * 按请求构造环境。
     *
     * @param diagnosticsEnabled 是否打开诊断采样（{@code --debug}）
     * @return 环境端口；关闭态复用缺席实现
     */
    static HeadlessEnvironment of(boolean diagnosticsEnabled) {
        return new HeadlessEnvironment(diagnosticsEnabled ? ENABLED : DiagnosticsEnvironment.EMPTY);
    }

    @Override
    public DiagnosticsEnvironment diagnostics() {
        return diagnostics;
    }
}
