/**
 * scene 测试搭台设施层（testkit）—— 跨 L2/L3、跨 input/paint 子系统的可复用测试 helper 归属。
 *
 * <h3>定位（区别于各子包）</h3>
 * <ul>
 *   <li>本包只放「跨包复用的搭台 helper」：把多个测试里反复出现的样板范式（layout→paint→replay
 *       捕获、取中心→route→flush 交互注入）收口为统一入口，消除坐标硬编码与 11 参事件构造易错样板。</li>
 *   <li>区别于 {@code scene/mock/}（{@code PlatformInputSource} 替身实现，属桥封板契约范畴）、
 *       {@code scene/integration/}（具体测试用例）、{@code scene/layout/}（L2 纯数学测试用例）。
 *       本包不含测试用例，只含 helper 工具与它们的自测。</li>
 * </ul>
 *
 * <h3>命名规范</h3>
 * <ul>
 *   <li><b>{@code SceneXxxHarness}</b>：有状态、需 mount/flush 生命周期的搭台（如
 *       {@link SceneInteractionHarness}，持有 runtime + root + layoutEngine）。</li>
 *   <li><b>{@code SceneXxxCapture}</b>：无状态 static 工具（如
 *       {@link ScenePaintCapture}，跑通链路返回记录）。</li>
 * </ul>
 *
 * <h3>准入判据</h3>
 * <p>跨包复用的搭台 helper 进本包；单包私有的探针 / 断言留原文件（如 {@code LayoutAssertions}
 * 留 {@code layout/}、控件结构探针留 {@code control/}）。</p>
 *
 * <h3>输入侧两入口边界（简版）</h3>
 * <p>scene 输入测试有两条正交入口，务必按场景选择，不要混用：</p>
 * <ul>
 *   <li><b>入口 A「编程注入帧」（默认入口）</b>：{@link SceneInteractionHarness}（首选）
 *       或裸 {@code InputFrameBuilder}（白盒回退）→ {@code SceneRuntime.route}。
 *       覆盖交互路由 + 状态机（click / hover / scroll / key）。交互测试一律走此入口。
 *       <b>harness 优先，5 类白盒场景回退裸建，判据见 §7.1</b>。</li>
 *   <li><b>入口 B「桥封板契约」</b>：{@code MockPlatformInputSource}（位于 {@code scene/input/mock/}，
 *       <b>非本包</b>）。仅用于封板状态机 / 键映射 / 不可变性测试。<b>不用于交互测试</b>——
 *       {@code MockPlatformInputSource} ≡ {@code InputFrameBuilder} 壳，交互测试经它零覆盖增益
 *       （「绕过桥」非缺陷）。桥内部差分（{@code LwjglInputSource} + {@code PlatformStateReader}）
 *       属独立桥单测范畴，与本入口正交。</li>
 * </ul>
 *
 * <h3>渲染侧顶点断言边界</h3>
 * <ul>
 *   <li>{@link ScenePaintCapture#paintAndCapture} 是渲染出口顶点验证的唯一入口
 *       （layout → paint → {@code ScenePaintReplayer#replay} → {@code RecordingRenderBackend}）。</li>
 *   <li><b>断言口径：变换前 box + transform 分量分离</b>（与 hit-test 同口径）——box 顶点
 *       （{@code fillRect} 的 left/top/right/bottom）反映 layout 几何，transform 仅出现在
 *       {@code pushTransform} 的 7 个浮点分量里，二者分离不叠加。</li>
 *   <li><b>不做「变换后顶点」</b>：变换后的最终像素位置属 GPU 顶点层，纯 JUnit mock backend
 *       不可观测。该边界登记为偏离（见偏离登记 {@code 2026-06-26-hit-test}，旧决策已删除）。</li>
 *   <li>控件顶点断言按需：仅独特自绘结构才补，默认复用 {@code SceneBackendContractTest} 8 场景
 *       + L2 坐标不变量。</li>
 * </ul>
 *
 * <p><b>详细边界与速查表</b>见 {@code docs/架构/测试体系约定.md} §7。</p>
 */
package club.heiqi.uilib.ui.scene.testkit;
