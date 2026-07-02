/**
 * L2 纯数学布局测试层 —— 锚定 {@code NORTH_STAR.md} 的 I4 / I7 / I12 / §4.5 数学不变量。
 *
 * <h3>L2 定义</h3>
 * <p>纯数学层：零 runtime / signal / input / paint 依赖。测试只做三件事——
 * new {@link club.heiqi.uilib.ui.scene.node.SceneNode} 构造场景树 → 调
 * {@link club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine#layout} 跑布局 →
 * 用数值断言（坐标 / 尺寸 / 失效位）验证产出。
 * 这一层<b>确定、快速、可重入</b>，是 CI 的硬门禁：任何 PR 必须先过 L2 全绿。</p>
 *
 * <h3>准入判据（vs integration/ 的 L3）</h3>
 * <p>凡测试触到下列任一子系统即属 L3，必须进 {@code scene/integration/}，<b>不进本包</b>：</p>
 * <ul>
 *   <li>{@code SceneRuntime}（声明式装配运行时）；</li>
 *   <li>{@code ReactiveScheduler} / reactive signal（信号调度）；</li>
 *   <li>输入路由（hit-test / focus / cursor / overlay dismiss）；</li>
 *   <li>paint 引擎（fragment / composite replay / transform）。</li>
 * </ul>
 * <p>本包的测试<b>只</b>构造裸 SceneNode + layout 引擎，不 new SceneRuntime、不订阅信号、
 * 不喂输入、不触发绘制。</p>
 *
 * <h3>强制纪律（防错）</h3>
 * <ul>
 *   <li><b>文本度量</b>：必须用 {@link club.heiqi.uilib.ui.scene.FixedTextMeasurer}(8,16)，
 *       禁依赖真机字体 / font renderer（确定性桩保证跨环境一致）。</li>
 *   <li><b>几何 / 失效断言</b>：必须复用 {@link LayoutAssertions}，禁在测试里裸 {@code assertEquals}
 *       断坐标 / 尺寸 / 失效位。<b>例外</b>：grow 主轴分配（只断主轴尺寸，交叉轴受 STRETCH 影响难预期，
 *       见 GrowAllocationTableTest）、缓存计数/并行池等非全维几何量允许裸 assertEquals。</li>
 *   <li><b>engine 实例</b>：每个测试用例用独立 engine 实例（per-field final 或 per-test new 均可），
 *       禁跨用例复用同一 engine。</li>
 *   <li><b>import 禁区</b>：L2 测试禁 import reactive / runtime / input / paint 包，违者即应下沉 L3。</li>
 *   <li><b>I4 矩阵</b>：新增任何 setter，其失效级别必须补进
 *       {@code scene/node/InvalidationLevelMatrixTest} 的矩阵表。</li>
 *   <li><b>grow 场景</b>：grow 分配新场景必须补进 {@link GrowAllocationTableTest} 的 Case builder，
 *       期望值需 Oracle 手算并交叉校验。</li>
 * </ul>
 *
 * <h3>本包文件清单</h3>
 * <ul>
 *   <li>{@link LayoutAssertions} —— 布局不变量断言库（I4/I7/I12/求和不变量的可复用 static 方法）。</li>
 *   <li>{@link LayoutAssertionsTest} —— 断言库自身的自测（守卫跳过 / 断言失败信息正确）。</li>
 *   <li>{@link SceneLayoutEngineTest} —— 布局引擎主测（约束下传 / shrink / fill / 嵌套）。</li>
 *   <li>{@link GrowAllocationTableTest} —— 24 场景 grow 分配表（主轴分配快照，裸 assertEquals 例外所在）。</li>
 *   <li>{@link RowGrowWidthAllocationTest} —— ROW 方向 grow 既有回归。</li>
 *   <li>{@link CoordinateInvariantTest} —— I12 / §4.5 坐标累加 + scrollOffsetY 注入数学契约。</li>
 *   <li>{@link SubtreeNodeCountTest} —— 子树节点计数缓存正确性。</li>
 *   <li>{@link SceneParallelExecutorTest} —— 并行池基建（executor 隔离 / 任务调度）。</li>
 * </ul>
 *
 * <p><b>详细防错清单</b>见 {@code docs/架构/测试体系约定.md}。</p>
 */
package club.heiqi.uilib.ui.scene.layout;
