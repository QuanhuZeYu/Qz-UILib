package club.heiqi.uilib.ui.env;

/**
 * UI 环境端口 —— 宿主向 UI 框架提供的、<b>不产生于 UI 自身</b>的只读环境事实。
 *
 * <h3>语义（三条不变量）</h3>
 * <ol>
 *   <li><b>只读，方向自外向内</b>：写入口一律在宿主侧（生产 = 配置回灌 / 资源重载 / 语言切换；
 *       headless = 命令行参数；测试 = 测试侧实现）。框架只读，不写。与之相对，
 *       {@link club.heiqi.uilib.ui.scene.node.SceneFontEnvironment} 是 runtime 向<b>节点</b>的
 *       下行暴露（节点沿父链上溯），两者方向相反，不得互相顶替。</li>
 *   <li><b>缺席等价</b>：{@link #empty()} 表示「宿主未声明任何环境事实」，其每个域的返回值
 *       必须与今日「未安装态」逐位等价 —— 具体缺席值写在各域接口的 javadoc 里，
 *       新增域时必须一并写明。</li>
 *   <li><b>值读纪律</b>：环境值是 O(1)、零分配的帧内直读；<b>禁止</b>把读到的值缓存进构造期
 *       字段或 {@code Computed.create(...)} 快照 —— 后者在派生函数里读非 signal 量会变成
 *       一次性快照，源变化永不重算（见 {@code Computed} javadoc「陷阱二」，仓库内已有实测事故）。</li>
 * </ol>
 *
 * <h3>为什么需要它</h3>
 * <p>框架此前获取环境事实有两条路：<b>进程级静态/单例直读</b>（{@code Config.useDebug} 曾被 23 处
 * 每帧读、{@code LanguageEpochService} 自述「不是 signal 通道」）与<b>构造期注入</b>
 * （{@code HudScaleSetting}、{@code SceneThemes.install(runtime, signal)}）。前者在 headless
 * 出图与测试里<b>无法替换</b>、在运行期<b>无法通知</b>消费者；后者已被证明可用，却各自为政、
 * 每加一个环境量就要多改一次宿主构造签名。本接口把后者上升为统一语义，给前者一个明确归属：
 * 新增环境量 = 新增域接口 + 在宿主侧接一次适配器，不破既有实现、不散落第二套读取口径。</p>
 *
 * <h3>增量接入（开闭）</h3>
 * <p>域访问器全部是 {@code default} 方法并返回该域的缺席实现，因此<b>新增域不破坏既有实现类</b>；
 * 宿主只需覆盖自己真正提供的域。每个域按需暴露三类成员：<b>值读</b>（必需）、
 * <b>代际</b>（可选，单调不减 long，用于缓存失效）、<b>订阅</b>（可选，signal，用于响应式派生）。
 * 本版落三域的值读与代际；订阅<b>按需暴露</b>——诊断域已接入（调试浮层显隐的响应式派生，
 * 见 {@link DiagnosticsEnvironment#debugOverlayChanges()}），其余域等出现响应式派生消费方时再补，
 * 不预先铺无人消费的通道。</p>
 *
 * <h3>注入路径</h3>
 * <p>唯一注入点是构造依赖：{@code SceneRuntime(SceneTextMeasurer, UiEnvironment)}
 * （生产由 {@code SceneHostAssembly.assemble} 单点装配）。<b>不是</b>服务定位器：
 * 没有静态 getter、没有线程上下文查找，多 runtime（测试并行、HUD 每窗口自建 runtime）
 * 天然隔离。</p>
 *
 * @see club.heiqi.uilib.ui.env.DiagnosticsEnvironment
 * @see club.heiqi.uilib.ui.env.LocaleEnvironment
 * @see club.heiqi.uilib.ui.env.ResourceEnvironment
 */
public interface UiEnvironment {

    /**
     * 缺席态单例：宿主未声明任何环境事实。各域返回值见各域 javadoc 的「缺席值」条。
     *
     * <p>无状态、可安全跨 runtime 共享。</p>
     */
    UiEnvironment EMPTY = new UiEnvironment() {
    };

    /**
     * 缺席态入口（与 {@link #EMPTY} 同一实例）。
     *
     * @return 缺席态环境
     */
    static UiEnvironment empty() {
        return EMPTY;
    }

    /**
     * 诊断（调试开关）域。
     *
     * @return 诊断环境；未覆盖时为缺席实现
     */
    default DiagnosticsEnvironment diagnostics() {
        return DiagnosticsEnvironment.EMPTY;
    }

    /**
     * 语言（文本代际）域。
     *
     * @return 语言环境；未覆盖时为缺席实现
     */
    default LocaleEnvironment locale() {
        return LocaleEnvironment.EMPTY;
    }

    /**
     * 资源包（资源代际）域。
     *
     * @return 资源环境；未覆盖时为缺席实现
     */
    default ResourceEnvironment resources() {
        return ResourceEnvironment.EMPTY;
    }
}
