/**
 * L3 集成测试层 —— 依赖 SceneRuntime/ReactiveScheduler/Input/Paint 多子系统协作。
 *
 * <p>本包的测试需要搭建完整运行时（runtime + reactive signal + input 路由 + paint 引擎），
 * 属于跨子系统集成验证，允许较宽松运行，不作为 CI 硬门禁
 * （区别于 {@code layout/} 包的 L2 纯数学层，后者零 runtime/signal/input 依赖、确定、快速）。</p>
 *
 * <p>分层规则：</p>
 * <ul>
 *   <li>L2 纯数学（{@code scene.layout}）：仅依赖 layout 引擎本身的 public API + JUnit，
 *       数值断言、不变量校验，CI 硬门禁</li>
 *   <li>L3 集成（{@code scene.integration}）：多子系统协作，端到端行为验证，宽松运行</li>
 * </ul>
 *
 * <p><b>测试体系防错清单</b>见 {@code docs/架构/测试体系约定.md}。</p>
 */
package club.heiqi.uilib.ui.scene.integration;
