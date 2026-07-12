/**
 * scene 栈高级表单组合能力 —— 字段外壳 + 主题 token 的泛型组合层。
 *
 * <p>本包是 scene 新栈在「表单」场景的高级组合能力收口：把「字段卡片外壳」
 * （label + helper + 控件 mount 槽 + error 提示 + dirty 标记）从 config.ui 提炼下沉为
 * 零业务依赖的通用工具，供 config.ui 适配层与未来其它表单消费方复用。</p>
 *
 * <h2>层定位与依赖铁律</h2>
 *
 * <ul>
 *   <li><b>零 config 依赖</b>：本包禁止 import 任何 {@code club.heiqi.config.*}。
 *       FormFieldShell 只吃 {@link java.lang.String} /
 *       {@link club.heiqi.uilib.ui.reactive.ReadableSignal} /
 *       {@link java.util.function.Supplier} / {@link club.heiqi.uilib.ui.scene.form.FormTheme}，
 *       不感知 {@code FieldSpec} / {@code DraftSignalAdapter} 等业务类型。</li>
 *   <li><b>零 MC/Forge/GL 依赖（守 I10）</b>：本包禁止 import 任何 Minecraft / Forge / GL 平台类型，
 *       与 scene 栈其余子包一致，保持纯 Java 响应式组合层。</li>
 *   <li><b>守 I1-I12 + R1-R13</b>：本包组合逻辑遵守宪章不变量与控件契约红线——
 *       外观随状态变化只经 {@code rt.bind/bindComputed} 派生（I1/I11/R4），
 *       控件 mount 槽由 caller 以 {@code Supplier<SceneNode>} 注入，本包不建业务控件。</li>
 * </ul>
 *
 * <p>本包是 Oracle U1 规划的泛型表单框架第一步：字段外壳 + 主题 token 下沉。
 * 后续步骤将在此包内继续沉淀泛型字段描述符 {@code D} 与表单骨架。</p>
 */
package club.heiqi.uilib.ui.scene.form;
