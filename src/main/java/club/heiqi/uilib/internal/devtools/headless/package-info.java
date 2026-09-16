/**
 * headless 运行时设施 —— Qt {@code -platform offscreen} 的对应物。
 *
 * <h3>定位（表达什么）</h3>
 * <p>同一份 UI 代码，在无游戏、无窗口的进程里装配、布局、绘制：可脚本化注入输入、可导出像素。
 * 首要用途是 agent 与开发者的快速迭代（改一行 → 秒级出图 → 看图判断）；测试与 CI 回归是顺带用途。</p>
 *
 * <h3>顶层语义（顺序即依赖顺序：先语义、再组织、后实现）</h3>
 * <ol>
 *   <li><b>会话 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessSession}</b>：
 *       一次装配 → 多次推进 → 多次出图；会话独占并最终释放 GL 上下文与帧资源。</li>
 *   <li><b>请求 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessRequest}</b>：
 *       页面 + 尺寸 + 帧计划 + 输入脚本 + 产物选择，不可变。</li>
 *   <li><b>产物 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessArtifact}</b>：
 *       像素 + 自检 + GL 能力描述；<b>像素必须带自检</b>，禁止「图有效但无证据」。</li>
 *   <li><b>能力 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessCapabilities}</b>：
 *       GL / 字体 / stencil 等运行期事实，启动即探测并声明，不假定。</li>
 *   <li><b>失败 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessFailure}</b>：
 *       能力缺失、上下文创建失败、读回失败等一律显式抛出并带阶段标签，不静默降级为空内容。</li>
 * </ol>
 *
 * <h3>不变量</h3>
 * <ol>
 *   <li><b>单一渲染路径</b>：一律经生产后端
 *       {@code club.heiqi.uilib.ui.render.UiRenderContext}、生产帧管线
 *       {@code club.heiqi.uilib.ui.scene.host.SceneFramePipeline} 与唯一装配点
 *       {@code club.heiqi.uilib.ui.scene.host.SceneHostAssembly}；本包不另造第二套后端或第二套装配。</li>
 *   <li><b>像素可归因</b>：每张输出图都携带自检（墨水率、颜色数、glGetError）与请求参数，
 *       缺失即视为设施故障而不是「UI 没画」。</li>
 *   <li><b>无隐式静态量</b>：尺寸、缩放、帧数、输出路径全部来自请求；不读全局单例的隐藏状态。</li>
 *   <li><b>失败显式</b>：任何能力缺失走 {@link club.heiqi.uilib.internal.devtools.headless.HeadlessFailure}，
 *       对照 {@code UiRenderBackend} 若干 default 静默 no-op 的历史教训。</li>
 *   <li><b>资源闭合</b>：{@code HeadlessSession} 关闭后 GL 资源、帧缓冲与宿主 runtime 全部释放。</li>
 * </ol>
 *
 * <h3>打包边界</h3>
 * <p>本包是开发期设施：{@code build.gradle.kts} 在全部 Jar 型产物上排除本包，并由
 * {@code verifyHeadlessNotPackaged} 门禁逐个打开产物断言。禁止在此包引入生产运行期必需的类。</p>
 */
package club.heiqi.uilib.internal.devtools.headless;
