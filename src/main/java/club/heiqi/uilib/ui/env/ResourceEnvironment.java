package club.heiqi.uilib.ui.env;

/**
 * 资源环境域 —— 「资源包重载代际」的只读端口。
 *
 * <p><b>语义</b>：{@link #resourceEpoch()} 单调不减，每次资源包重载 +1；它是图标缓存清空、
 * 候选源 {@code iconRevision} 摄入的唯一依据。读取 O(1)，可每帧读；消费者比对代际决定是否重建，
 * 不在代际推进时同步重建任何缓存（重建由首个消费者惰性触发）。</p>
 *
 * <p><b>约定</b>：未注册到可重载资源管理器时（headless / 服务端 JVM），代际恒为 0 且永不推进 ——
 * 这是「无资源变更」的正确表达，不是错误态，消费者不得据此报错或降级。</p>
 *
 * <p><b>缺席值</b>：{@code 0}。与今日未注册 {@code ResourceReloadService} 时逐位等价。</p>
 */
public interface ResourceEnvironment {

    /**
     * 缺席实现：代际恒为 0（无资源变更）。
     */
    ResourceEnvironment EMPTY = new ResourceEnvironment() {
        @Override
        public long resourceEpoch() {
            return 0L;
        }
    };

    /**
     * 当前资源代际（单调不减）。
     *
     * @return 资源代际
     */
    long resourceEpoch();
}
