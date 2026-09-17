package club.heiqi.uilib.ui.env;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.i18n.LanguageEpochService;
import club.heiqi.uilib.resource.ResourceReloadService;

/**
 * 生产环境适配器 —— 把 UILib 既有的进程级环境量（静态字段 / 进程单例）适配为
 * {@link UiEnvironment} 的域实现。
 *
 * <h3>它不做的事</h3>
 * <p>本类<b>不持有任何状态</b>：每个方法都是一次转发直读（{@code Config.useDebug} 是普通静态字段，
 * 每次读都是当前值；两个服务的代际是 {@code volatile long} 直读）。因此多实例语义完全等价，
 * 无需单点装配；反过来，<b>将来若引入有状态的环境量（需要缓存 / 订阅 / 生命周期），必须改为
 * 单点装配</b>，不得在本类里加字段。</p>
 *
 * <h3>读取点接线进度（后续批次的口径）</h3>
 * <p>本类只提供<b>端口</b>；既有读取点改走端口的前提是「环境引用在该位置可达」。
 * 已接入：{@code SceneFramePipeline#phaseReplay}（每帧一次，有 runtime 引用可直达）。
 * 仍未接线的读取点按可达性分两类，避免下次接手时重新盘一遍：</p>
 * <ul>
 *   <li><b>有 runtime 引用、但改端口读解决不了</b>：{@code UiHudRenderListener.registerDebugHud}
 *       的 {@code Computed.create(() -> Config.uiDebug)} —— 它是<b>真事故</b>（Computed 读非 signal
 *       量 = 一次性快照，实测关闭→开启不重算），但把它改成 {@code rt.environment()...} 读只是把快照
 *       换个来源，<b>不解决</b>；正解是先给诊断域补可订阅通道，再让该点订阅。</li>
 *   <li><b>需先补「节点 → 环境」通道</b>：控件内的 {@code Config.useDebug} 采样点
 *       （{@code ScenePickerPanel} / {@code SearchResultList} / {@code VariantChooser} /
 *       {@code MemberGrid} / {@code PickerIconCache}）、{@code UiPerformanceMonitor}、
 *       {@code HostImageSource}，以及 {@code PickerIconResolver} / {@code PickerRevisionBridge}
 *       的资源代际与语言代际比对。节点侧今日只有 {@code SceneFontEnvironment} 一条「沿父链上溯」
 *       通道（且方向是 runtime → 节点），要成批接线需把它扩成通用环境引用，属独立批次。</li>
 * </ul>
 *
 * <h3>诊断开关当前是双源（收敛前必须同步）</h3>
 * <p>诊断域只接上了帧管线的采样<b>判定</b>；{@code UiPerformanceMonitor} 的计数记录与统计读取
 * （同样以 {@code Config.useDebug} 门控）仍是静态直读。生产路径下两者同源同值（本类即转发该字段），
 * 行为等价；但 headless / 测试注入自定义诊断实现时，<b>只开一边会得到「有会话无计数」或
 * 「有计数无门控」的半开态</b>。接 {@code UiPerformanceMonitor} 时一并收敛，收敛后以环境端口为准。</p>
 */
public final class ProcessUiEnvironment implements UiEnvironment {

    /** 无状态单例（多实例亦等价，此处仅为免重复分配）。 */
    public static final ProcessUiEnvironment INSTANCE = new ProcessUiEnvironment();

    /** 诊断域：直读 {@code Config.useDebug}（普通静态字段，每次读为当前值）。 */
    private static final DiagnosticsEnvironment DIAGNOSTICS = new DiagnosticsEnvironment() {
        @Override
        public boolean debugEnabled() {
            return Config.useDebug;
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
