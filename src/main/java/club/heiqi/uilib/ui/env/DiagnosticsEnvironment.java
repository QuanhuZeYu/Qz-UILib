package club.heiqi.uilib.ui.env;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 诊断环境域 —— 「本次运行的调试事实」的只读端口。
 *
 * <h3>承载两个互相独立的事实</h3>
 * <ul>
 *   <li>{@link #debugEnabled()}：是否<b>采集</b>调试数据（性能采样、帧级规模统计）。
 *       消费者是 {@code SceneFramePipeline} 与 {@code UiPerformanceMonitor}。</li>
 *   <li>{@link #debugOverlayEnabled()}：是否<b>显示</b>框架自带的调试浮层（debug HUD）。
 *       消费者是调试 HUD 的内容树。</li>
 * </ul>
 * <p>二者在配置面本就是两个字段（{@code Config.useDebug} / {@code Config.uiDebug}），
 * 只采不显与只显不采都是合法组合，故不合并为单值。</p>
 *
 * <h3>读取纪律</h3>
 * <p>值读是<b>帧内直读</b>语义（每帧开头读一次即天然获得最新值），
 * 禁止缓存进构造期字段，也禁止包进 {@code Computed.create(...)} —— 后者不建立任何依赖，
 * 会退化成一次性快照（仓库内已有实测事故：{@code Config.uiDebug} 被 Computed 包装后
 * 关闭→开启不重算）。需要<b>响应式派生</b>的消费方改用 {@link #debugOverlayChanges()}。</p>
 *
 * <h3>按需暴露订阅</h3>
 * <p>订阅通道只给「确有响应式派生消费方」的成员：{@link #debugOverlayChanges()} 服务于
 * 调试浮层的显隐派生（内容树随开关挂载/卸载）。{@link #debugEnabled()} 的全部消费方都是
 * 帧内直读（帧管线、性能采样器），配订阅只会多出一条无人消费的通道，故不配。</p>
 *
 * <h3>缺席值</h3>
 * <p>{@code false}（不采样、不显示）与恒 {@code false} 的订阅源。与今日两个配置字段的初值一致，
 * 因此缺席态与「未安装」逐位等价。</p>
 */
public interface DiagnosticsEnvironment {

    /**
     * 缺席订阅源：恒 {@code false} 且永不变化。
     *
     * <p>接口常量形式是刻意的——订阅通道必须<b>每次返回同一实例</b>，
     * 否则每次调用都会在依赖图里留下一个永不释放的节点（{@code Signal.create} 每次分配新对象）。</p>
     */
    ReadableSignal<Boolean> ALWAYS_DISABLED = Signal.create(Boolean.FALSE);

    /**
     * 缺席实现：恒不采样、恒不显示。
     */
    DiagnosticsEnvironment EMPTY = new DiagnosticsEnvironment() {
        @Override
        public boolean debugEnabled() {
            return false;
        }
    };

    /**
     * 是否启用调试采样。
     *
     * @return true = 采集调试数据（性能计数器、帧级规模统计）
     */
    boolean debugEnabled();

    /**
     * 是否显示框架自带的调试浮层。
     *
     * @return true = 显示调试浮层
     */
    default boolean debugOverlayEnabled() {
        return false;
    }

    /**
     * 调试浮层显隐的订阅通道（响应式派生用）。
     *
     * <p>只读：写入口在宿主侧（生产 = 配置回灌）。返回的源在每次调用时必须是同一实例
     * （见 {@link #ALWAYS_DISABLED}）。</p>
     *
     * @return 可订阅的显隐信号；未覆盖时恒 false
     */
    default ReadableSignal<Boolean> debugOverlayChanges() {
        return ALWAYS_DISABLED;
    }
}
