package club.heiqi.uilib.ui.env;

/**
 * 诊断环境域 —— 「本次运行是否采集调试数据」这一事实的只读端口。
 *
 * <p><b>消费者</b>：帧管线（采样开启时才统计帧级绘制规模，否则整段跳过）、
 * {@code UiPerformanceMonitor}、控件内的耗时采样点。</p>
 *
 * <p><b>读取纪律</b>：本值是<b>帧内直读</b>语义（每帧开头读一次即天然获得最新值），
 * 禁止缓存进构造期字段，也禁止包进 {@code Computed.create(...)} —— 后者不建立任何依赖，
 * 会退化成一次性快照（仓库内已有实测事故：{@code Config.uiDebug} 被 Computed 包装后
 * 关闭→开启不重算）。</p>
 *
 * <p><b>缺席值</b>：{@code false}（不采样）。与今日 {@code Config.useDebug} 的字段初值一致，
 * 因此缺席态与「未安装」逐位等价。</p>
 */
public interface DiagnosticsEnvironment {

    /**
     * 缺席实现：恒不采样。
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
}
