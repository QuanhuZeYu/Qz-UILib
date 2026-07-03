/**
 * scene 测试包总纲 —— UI 场景子系统的测试分层与各子包职责索引。
 *
 * <h3>分层总纲</h3>
 * <ul>
 *   <li><b>L2 纯数学（{@code layout/}）</b>：仅 new SceneNode + SceneLayoutEngine.layout + 数值/失效断言，
 *       零 runtime/signal/input/paint 依赖，确定、快速，CI 硬门禁。</li>
 *   <li><b>L3 集成（{@code integration/}）</b>：跨子系统协作（runtime + signal + input + paint），
 *       端到端行为验证，运行宽松，非 CI 硬门禁。</li>
 *   <li><b>其余子包</b>：按各自子系统归属（node / control / input / paint / overlay / runtime / text），
 *       不强制纯数学，但同样应优先复用本目录的共享设施。</li>
 * </ul>
 *
 * <h3>各子包职责（一行）</h3>
 * <ul>
 *   <li>{@code layout/} —— L2 布局数学层（I4/I7/I12/§4.5 不变量，CI 硬门禁）。</li>
 *   <li>{@code node/} —— SceneNode 脏标记 / 失效传播（含 I4 失效级别矩阵 InvalidationLevelMatrixTest）。</li>
 *   <li>{@code control/} —— 控件装配结构（按钮 / 列表 / 表格 / 滑块等控件的 SceneNode 装配断言）。</li>
 *   <li>{@code input/} —— 输入路由（hit-test / focus / cursor / overlay dismiss / key mapping）。</li>
 *   <li>{@code integration/} —— 跨子系统 L3 集成（runtime + signal + input + paint 端到端）。</li>
 *   <li>{@code paint/} —— paint 引擎（fragment / composite replay / transform / backend contract）。</li>
 *   <li>{@code overlay/} —— overlay 锚定与 host。</li>
 *   <li>{@code runtime/} —— 声明式运行时（SceneRuntime / declarative primitives / portal）。</li>
 *   <li>{@code text/} —— 文本度量并发性（measure 幂等 / 宽度稳定性）。</li>
 * </ul>
 *
 * <h3>共享设施指针</h3>
 * <ul>
 *   <li>{@link club.heiqi.uilib.ui.scene.FixedTextMeasurer}（本目录根）—— 确定性文本度量桩，
 *       每字符宽 8px / 行高 16px，所有需要文本度量的测试（含 L2）必用此桩，禁真机字体。</li>
 *   <li>{@link club.heiqi.uilib.ui.scene.layout.LayoutAssertions}（{@code layout/} 包）——
 *       布局不变量断言库，几何 / 失效断言优先复用，勿裸写 assertEquals。</li>
 * </ul>
 *
 * <p><b>分层细则</b>见 {@code layout/package-info.java} 与 {@code integration/package-info.java}；
 * <b>防错清单</b>见 {@code docs/传感层/测试体系约定.md}。</p>
 */
package club.heiqi.uilib.ui.scene;
