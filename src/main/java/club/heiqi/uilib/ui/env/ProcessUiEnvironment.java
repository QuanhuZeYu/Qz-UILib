package club.heiqi.uilib.ui.env;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.i18n.LanguageEpochService;
import club.heiqi.uilib.resource.ResourceReloadService;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 生产环境适配器 —— 把 UILib 既有的进程级环境量（静态字段 / 进程单例 / 进程级信号）适配为
 * {@link UiEnvironment} 的域实现。
 *
 * <h3>状态与单点</h3>
 * <p>语言域与资源域是纯转发直读（{@code Config.useDebug} 是普通静态字段，每次读都是当前值；
 * 两个服务的代际是 {@code volatile long} 直读），多实例语义等价。诊断域则持有<b>一个进程级订阅源</b>
 * （调试浮层的显隐信号）——它使本类从「无状态转发」升级为「进程级诊断通道的单点持有者」，
 * 故构造器私有、唯一 {@link #INSTANCE}，<b>禁止复制实例</b>（复制会让订阅源分裂成多条通道，
 * 派生方订阅到的那条可能永远不更新）。</p>
 *
 * <h3>诊断开关的唯一权威与唯一写入口</h3>
 * <p>值读直读配置字段（每帧最新），订阅通道是它的<b>投影</b>，由
 * {@link #publishUiDebug(boolean)} 在配置回灌时同步。因此：生产路径下每个调试开关只有两处出现——
 * 配置字段（值读权威）与<b>唯一写入口</b> {@code ConfigValueBridge.applyGeneral}（写完字段后投影）。
 * 直接写 {@code Config.useDebug} / {@code Config.uiDebug} 而不投影会让订阅方停留在旧值
 * （值读方不受影响）。测试侧要驱动订阅请用 {@link #publishUiDebug(boolean)}，或直接注入自定义
 * {@link DiagnosticsEnvironment}，<b>不要</b>改进程单例（那会污染同 JVM 的其它测试）。</p>
 *
 * <h3>读取点接线进度（后续批次的口径）</h3>
 * <p>本类只提供<b>端口</b>；既有读取点改走端口的前提是「环境引用在该位置可达」。
 * 已接入的读取点：</p>
 * <ul>
 *   <li>{@code SceneFramePipeline#phaseReplay}（每帧一次，runtime 引用直达）。</li>
 *   <li>{@code UiPerformanceMonitor#beginFrame}（<b>帧入口是全类唯一的诊断环境入口</b>：
 *       采样会话持有诊断域引用而非开关快照，故帧中途改开关仍然即时生效）。</li>
 *   <li>调试 HUD 的显隐（订阅 {@link DiagnosticsEnvironment#debugOverlayChanges()}，
 *       替换了旧的 {@code Computed.create(() -> Config.uiDebug)} 一次性快照）。</li>
 *   <li>控件内的采样埋点：能拿到 runtime 的走 {@code rt.environment().diagnostics()}；
 *       拿不到 runtime 的由持有方构造注入诊断域（{@code PickerIconCache}）。</li>
 * </ul>
 * <p>仍未接线的读取点只剩<b>代际比对类</b>：{@code PickerIconResolver} / {@code PickerRevisionBridge}
 * 的资源代际与语言代际比对尚未改走 {@link ResourceEnvironment} / {@link LocaleEnvironment}，
 * 与诊断域无关，属独立批次。</p>
 */
public final class ProcessUiEnvironment implements UiEnvironment {

    /** 无状态单例（语言/资源域多实例亦等价；诊断域的订阅源要求单点，见类 javadoc）。 */
    public static final ProcessUiEnvironment INSTANCE = new ProcessUiEnvironment();

    /**
     * 调试浮层显隐的进程级订阅源（诊断域 {@link DiagnosticsEnvironment#debugOverlayChanges()} 的实体）。
     *
     * <p>初值与 {@code Config.uiDebug} 字段初值一致，故「未回灌」与「未安装」逐位等价。</p>
     */
    private static final Signal<Boolean> DEBUG_OVERLAY = Signal.create(Boolean.FALSE);

    /** 诊断域：值读直读两个配置字段，订阅通道由 {@link #publishUiDebug(boolean)} 投影。 */
    private static final DiagnosticsEnvironment DIAGNOSTICS = new DiagnosticsEnvironment() {
        @Override
        public boolean debugEnabled() {
            return Config.useDebug;
        }

        @Override
        public boolean debugOverlayEnabled() {
            return Config.uiDebug;
        }

        @Override
        public ReadableSignal<Boolean> debugOverlayChanges() {
            return DEBUG_OVERLAY;
        }
    };

    /** 语言域：语言码走静态探测，代际走进程单例。 */
    private static final LocaleEnvironment LOCALE = new LocaleEnvironment() {
        @Override
        public String languageCode() {
            return LanguageEpochService.currentLanguageCode();
        }

        @Override
        public long nameEpoch() {
            return LanguageEpochService.getInstance().nameEpoch();
        }
    };

    /** 资源域：代际走进程单例（未注册时恒 0）。 */
    private static final ResourceEnvironment RESOURCES = new ResourceEnvironment() {
        @Override
        public long resourceEpoch() {
            return ResourceReloadService.getInstance().resourceEpoch();
        }
    };

    private ProcessUiEnvironment() {
    }

    /**
     * 把调试浮层开关的最新值投影到诊断域的订阅通道。
     *
     * <p><b>宿主侧写入口的唯一调用点</b>是配置回灌 {@code ConfigValueBridge.applyGeneral}：
     * 启动加载 / 配置页保存 / 磁盘热更三条通道都经它，故改配置即改显隐，无需重启或重开界面。</p>
     *
     * <p>不做入口去重：{@code Signal.set} 的契约是「入队、帧末按净变化生效」，同值写由
     * {@link club.heiqi.uilib.ui.reactive.ReactiveScheduler} 在 flush 阶段挡掉。本方法只在配置回灌时
     * 调用（非每帧路径），且在此处用 {@code get()} 预判还会在响应式上下文内误建订阅。</p>
     *
     * @param enabled 最新的调试浮层开关值
     */
    public static void publishUiDebug(boolean enabled) {
        DEBUG_OVERLAY.set(Boolean.valueOf(enabled));
    }

    @Override
    public DiagnosticsEnvironment diagnostics() {
        return DIAGNOSTICS;
    }

    @Override
    public LocaleEnvironment locale() {
        return LOCALE;
    }

    @Override
    public ResourceEnvironment resources() {
        return RESOURCES;
    }
}
