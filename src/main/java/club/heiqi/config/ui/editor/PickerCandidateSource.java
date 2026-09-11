package club.heiqi.config.ui.editor;

import java.util.List;

/**
 * 惰性候选源 SPI（查询式只读数据源）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2/§1.3/§1.6。候选源只回答
 * 「有多少 / 命中多少 / 第几段 / 单个是谁 / 分类有哪些」，<b>不提供列表、不提供缓存、
 * 不提供窗口几何</b>（窗口数学全归 UILib 控件）。</p>
 *
 * <h3>线程契约（不可协商）</h3>
 * <p><b>全部方法只允许客户端主线程调用</b>：候选物化会触碰 {@code Block.blockRegistry}、
 * {@code getSubBlocks}、{@code StatCollector}、{@code Minecraft.getMinecraft()} 等非线程安全设施；
 * 禁止 Netty 线程、并行 tick 线程、任何 {@code CompletableFuture}/线程池包装，也<b>不允许并发查询</b>
 * （含同帧重入并发语义）。误用必须 fail-fast 抛异常，不得返回空数据。UILib 调用点在调用前经
 * {@code club.heiqi.config.ui.field.PickerSourceGuard#requireMainThread} 断言；实现方入口
 * （如 Miner 的 {@code ensureFresh()}）同样必须断言。</p>
 *
 * <h3>顺序与唯一性</h3>
 * <ul>
 *   <li>同一 {@code (query, PickerSourceVersion)} 下 {@link #page} 顺序稳定（重复调用逐项相等）；
 *       分片与整取一致（{@code page(q,0,N) == concat(分片)}）；</li>
 *   <li>浏览 lane 顺序 = 注册（插入）序；搜索 lane 顺序 = 命中序（rank 升序 + 确定性 tie-break）；</li>
 *   <li>同一 {@code registry} 版本内 {@link #page} 返回的 candidateKey <b>不得重复</b>。</li>
 * </ul>
 *
 * <h3>失效通道</h3>
 * <p>环境代际（名称/资源）只经 {@link #onEnvironmentChanged(long, long)} 下行；实现只消费 UILib
 * 已推入的代际，不自行轮询语言/资源状态。清单（registry）失效由实现侧事件标脏 + 惰性重建承担。</p>
 *
 * <h3>版本绑定</h3>
 * <p>UILib 侧一切缓存键 = {@code (sourceInstance, PickerSourceVersion, 业务键)}；不得把 UI 节点
 * 引用或面板生命周期对象混入键。</p>
 */
public interface PickerCandidateSource {

    /**
     * @return 空查询可浏览的全部条目数（O(1)，不物化候选）
     */
    int size();

    /**
     * @return 候选清单（成员/顺序/Block 实例）版本号；变化时自增，同一实例内单调不减
     */
    long registryRevision();

    /**
     * @return 显示名/分类标签版本号；唯一来源 = {@link #onEnvironmentChanged} 推入的 nameEpoch 变化
     */
    long nameRevision();

    /**
     * @return 图标版本号；唯一来源 = {@link #onEnvironmentChanged} 推入的 resourceEpoch 变化。
     *         不含分级代际（分级是 UILib 侧进程静态表的失效，不下行）
     */
    long iconRevision();

    /**
     * 三段版本号的对象级载体。
     *
     * <p>默认实现由三段访问器合成（三次 O(1) long 读，无分配以外的成本）；实现者可覆写为自持快照，
     * 但<b>不得改变三段语义</b>：只反映「已摄入的环境代际 + 已发生的清单变化」。</p>
     *
     * @return source 已摄入的版本快照
     */
    default PickerSourceVersion version() {
        return new PickerSourceVersion(registryRevision(), nameRevision(), iconRevision());
    }

    /**
     * 命中总数（越界安全）：浏览 lane 恒等于 {@link #size()}；搜索 lane 返回<b>真实命中数</b>
     * （可大于装配层的 {@code searchMaxItems} 上限，截断由调用方按 {@code matchCount > maxItems} 判定）。
     *
     * @param query 查询条件（非 null）
     * @return 命中总数
     */
    int matchCount(PickerQuery query);

    /**
     * 惰性分片：返回全局序列 {@code [offset, offset + limit)} 的候选。
     *
     * <p>返回条数 = {@code min(limit, matchCount(query) - offset)}（末尾短页合法；
     * {@code offset >= matchCount} 时为空列表）；实现须保证与整取一致，且单页物化量与
     * {@code limit} 成正比（典型 = 窗口行数 × 列数），与候选总数 N 无关。</p>
     *
     * @param query  查询条件（非 null）
     * @param offset 全局起始下标（不得为负）
     * @param limit  期望条数（不得为负）
     * @return 只读分片（非 null）
     */
    List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit);

    /**
     * 精确单点：O(1) 定位 + 至多一次分片物化。
     *
     * <p>用于当前成员解析（避免「每个 key 一次 O(N) 全表搜索」）。</p>
     *
     * @param candidateKey 候选键
     * @return 候选；未命中返回 null
     */
    SearchPickerData.Candidate exact(String candidateKey);

    /**
     * @param dimension 分类维度（语义沿用 {@link CategorizedValueEditorProvider#categories(int)}）；不得为负
     * @return 分类导航行（count 可动态：-1 表示未知）；无分组返回空列表
     */
    List<SearchPickerCategories.Category> categories(int dimension);

    /**
     * 环境代际下行（唯一通道）：UILib 推送 nameEpoch/resourceEpoch 的当前值。
     *
     * <p>实现记录「上次摄入值」，摄入值变化即自增对应版本号（name → {@link #nameRevision()}，
     * resource → {@link #iconRevision()}）；<b>不推送则 {@link #version()} 恒等</b>（Z-4 判据）。
     * 主线程、同步、无分配。</p>
     *
     * @param nameEpoch     当前文本代际
     * @param resourceEpoch 当前资源代际
     */
    default void onEnvironmentChanged(long nameEpoch, long resourceEpoch) {
    }

    /**
     * 释放分片/图标缓存与清单快照（客户端断开、世界退出路径）。
     *
     * <p>不在每次关屏调用；不改变 revision 语义（释放后再次读取按首次建快照语义重建，
     * 版本号不得回绕）。</p>
     */
    default void release() {
    }
}
