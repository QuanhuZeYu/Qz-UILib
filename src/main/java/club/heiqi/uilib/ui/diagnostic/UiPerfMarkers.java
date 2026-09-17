package club.heiqi.uilib.ui.diagnostic;

/**
 * UI 采样标记名常量表 —— {@link UiPerformanceMonitor} 的唯一合法标记来源。
 *
 * <h3>为什么集中成常量</h3>
 * <p>采样器在热路径上被调用，标记名必须是编译期常量：禁止在调用点做字符串拼接
 * （每帧拼接会产生无意义的临时对象，且使 {@code debug=false} 的"零开销"承诺失效）。
 * 集中一处也让真机日志的解析脚本只需依赖本表。</p>
 *
 * <h3>命名约定</h3>
 * <p>{@code 域.对象.动作}，全小写 ASCII，无本地化文本。阶段名（记录耗时，单位纳秒）
 * 经 {@link UiPerformanceMonitor#recordPhase}；计数名（记录数量增量）经
 * {@link UiPerformanceMonitor#recordCounter}。两类共享同一字符串表，但语义不可混用：
 * 阶段名出现在日志的 {@code phases=} 段，计数名出现在 {@code counters=} 段。</p>
 *
 * <h3>埋点语义清单（P3 窗口化重写时必须保留）</h3>
 * <table border="1">
 *   <tr><th>标记</th><th>类型</th><th>语义（必须保留的观测口径）</th></tr>
 *   <tr><td>{@link #PHASE_PICKER_GRID_TRANSFORM}</td><td>阶段</td>
 *       <td>「结果信号已就绪 → 网格项列表可用」的派生耗时，含文本省略与图标源解析。</td></tr>
 *   <tr><td>{@link #PHASE_PICKER_LIST_MOUNT}</td><td>阶段</td>
 *       <td>「行模型派生 + keyed 挂载」耗时。窗口化后仍必须能观测首帧全量挂载的代价。</td></tr>
 *   <tr><td>{@link #PHASE_PICKER_OPEN_MAIN}</td><td>阶段</td>
 *       <td>主面板内容构建耗时（portal 内容 builder）。</td></tr>
 *   <tr><td>{@link #PHASE_PICKER_OPEN_VARIANT}</td><td>阶段</td>
 *       <td>变体浮层内容构建耗时。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_CANDIDATES}</td><td>计数</td>
 *       <td>本次派生看到的候选总数（空查询 = 全量浏览规模，是"全量挂载"的直接输入）。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_RESULTS}</td><td>计数</td>
 *       <td>本次派生实际参与挂载的项数（非空查询时 = 搜索结果数）。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_LIST_ROWS}</td><td>计数</td>
 *       <td>行数。窗口化后语义变为"窗口内挂载行数"，与 {@code COUNTER_PICKER_TOTAL_ROWS} 配对使用。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_TOTAL_ROWS}</td><td>计数</td>
 *       <td>数据总行数（与挂载行数分离，用于证明虚拟化比例）。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_LIST_CELLS}</td><td>计数</td>
 *       <td>挂载单元数（= 节点数的主要来源）。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_ICON_CREATED}</td><td>计数</td>
 *       <td>新建图标源次数（每次 = 一次物品栈拷贝 + 一个图片源对象）。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_ICON_CACHED}</td><td>计数</td>
 *       <td>图标缓存命中次数。与 created 配对给出命中率。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_VARIANT_ROWS}</td><td>计数</td>
 *       <td>变体浮层挂载行数。</td></tr>
 *   <tr><td>{@link #COUNTER_PICKER_MEMBERS}</td><td>计数</td>
 *       <td>成员网格挂载的成员卡片数。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_NODES}</td><td>计数</td>
 *       <td>本帧参与绘制的节点数（主树 + 全部 overlay）。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_COMMANDS}</td><td>计数</td>
 *       <td>本帧绘制计划命令条数。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_OVERLAYS}</td><td>计数</td>
 *       <td>本帧回放的 overlay 数量。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_BACKDROP_SURFACES}</td><td>计数</td>
 *       <td>本帧进入 shader 路径的玻璃表面数（短路/裁剪/降级不计）。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_BACKDROP_AREA_PX}</td><td>计数</td>
 *       <td>本帧玻璃面积（屏幕像素累加），与表面数配对给出真实规模。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_BACKDROP_CAPTURES}</td><td>计数</td>
 *       <td>本帧主层快照真实捕获次数（复用命中不计）。</td></tr>
 *   <tr><td>{@link #COUNTER_FRAME_BACKDROP_TAPS}</td><td>计数</td>
 *       <td>本帧采样次数估计值 = 玻璃面积 × 档位抽头预算（full/eco A/B 用）。</td></tr>
 * </table>
 */
public final class UiPerfMarkers {

    // ==================== 帧级 ====================

    /** 计数：本帧参与绘制的节点数（主树 + 全部 overlay 子树）。 */
    public static final String COUNTER_FRAME_NODES = "frame.nodes";

    /** 计数：本帧绘制计划命令条数（主树 + 全部 overlay 回放命令合计）。 */
    public static final String COUNTER_FRAME_COMMANDS = "frame.commands";

    /** 计数：本帧回放的 overlay 数量。 */
    public static final String COUNTER_FRAME_OVERLAYS = "frame.overlays";

    // ==================== 背景滤镜（磨玻璃） ====================

    /**
     * 计数：本帧进入 shader 路径的背景滤镜表面数。
     *
     * <p>口径 = 真正走到 shader 并完成绘制的表面；被页面策略/档位短路、被裁剪盒短路、
     * 降级到固定管线或 tint 兜底的表面都不计。与 {@link #COUNTER_FRAME_BACKDROP_AREA_PX}
     * 配对即可看出「玻璃表面数 × 平均面积」的真实规模。</p>
     */
    public static final String COUNTER_FRAME_BACKDROP_SURFACES = "frame.backdrop.surfaces";

    /** 计数：本帧进入 shader 路径的玻璃面积（屏幕像素，逐表面 l×h 累加）。 */
    public static final String COUNTER_FRAME_BACKDROP_AREA_PX = "frame.backdrop.areaPx";

    /** 计数：本帧主层快照的真实捕获次数（同帧复用/atlas 命中不计），用于证明快照复用效率。 */
    public static final String COUNTER_FRAME_BACKDROP_CAPTURES = "frame.backdrop.captures";

    /**
     * 计数：本帧玻璃采样次数估计值（= Σ 名义面积 × 当前档位抽头预算 × 绘制遍数）。
     *
     * <p>口径（2026-09-12 冻结）：面积取名义矩形面积（不按 clip 缩减），遍数取该表面实际
     * draw 次数——{@code isolatedLayer}（读取父 FBO 写入独立透明层）需要两遍 draw，
     * 故遍数=2，其余=1；漏计第二遍会把独立层的采样量低估一半。</p>
     *
     * <p>不是精确的 texture2D 次数（同一像素在不同分支下抽头数不同），而是给 full/eco 档位
     * A/B 用的量级指标：同比变化即采样预算变化。见 {@link club.heiqi.uilib.ui.render.BackdropQuality}。</p>
     */
    public static final String COUNTER_FRAME_BACKDROP_TAPS = "frame.backdrop.taps";

    // ==================== 选择器・阶段 ====================

    /** 阶段：结果信号就绪到网格项列表可用的派生耗时（含文本省略与图标源解析）。 */
    public static final String PHASE_PICKER_GRID_TRANSFORM = "picker.grid.transform";

    /** 阶段：行模型派生 + keyed 挂载耗时。 */
    public static final String PHASE_PICKER_LIST_MOUNT = "picker.list.mount";

    /** 阶段：主面板内容构建耗时。 */
    public static final String PHASE_PICKER_OPEN_MAIN = "picker.open.main";

    /** 阶段：变体浮层内容构建耗时。 */
    public static final String PHASE_PICKER_OPEN_VARIANT = "picker.open.variant";

    // ==================== 选择器・计数 ====================

    /** 计数：候选总数（空查询下即全量浏览规模）。 */
    public static final String COUNTER_PICKER_CANDIDATES = "picker.candidates";

    /** 计数：本次实际参与挂载的项数（非空查询下即搜索结果数）。 */
    public static final String COUNTER_PICKER_RESULTS = "picker.results";

    /** 计数：挂载行数。 */
    public static final String COUNTER_PICKER_LIST_ROWS = "picker.list.rows";

    /** 计数：数据总行数（用于与挂载行数对比得出虚拟化比例）。 */
    public static final String COUNTER_PICKER_TOTAL_ROWS = "picker.list.totalRows";

    /** 计数：挂载单元数。 */
    public static final String COUNTER_PICKER_LIST_CELLS = "picker.list.cells";

    /** 计数：由视口高度与轨道高派生的可视行数（与挂载行数配对即可证明虚拟化比例）。 */
    public static final String COUNTER_PICKER_VISIBLE_ROWS = "picker.list.visibleRows";

    /** 计数：新建图标源次数。 */
    public static final String COUNTER_PICKER_ICON_CREATED = "picker.icon.created";

    /** 计数：图标缓存命中次数。 */
    public static final String COUNTER_PICKER_ICON_CACHED = "picker.icon.cached";

    /** 计数：变体浮层挂载行数。 */
    public static final String COUNTER_PICKER_VARIANT_ROWS = "picker.variant.rows";

    /** 计数：成员网格挂载成员卡片数。 */
    public static final String COUNTER_PICKER_MEMBERS = "picker.members";

    // ==================== 图片 ====================

    /**
     * 计数：新建宿主物品图标源次数（每次 = 一次物品栈拷贝 + 一个图片源对象）。
     *
     * <p>写入者必须是<b>持有诊断环境的调用方</b>（图标缓存 / 装配层）：图片源工厂
     * （{@code HostImageSource} 的静态工厂）无环境引用，按环境端口纪律不得直读配置开关，故不埋点。
     * 与 {@link #COUNTER_PICKER_ICON_CACHED}（缓存命中）互补，二者之比即图标复用率。</p>
     */
    public static final String COUNTER_IMAGE_ICON_CREATED = "image.icon.created";

    private UiPerfMarkers() {
    }
}
