/**
 * scene 通用浮层数据地基。
 *
 * <p>overlay 是数据层维护的额外 root 列表：主树之外的浮层 root 由
 * {@link club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost} 以栈顺序保存，后续 host/router
 * 只按 bottom-first 或 top-first 快照消费。</p>
 *
 * <p>浮层显隐必须经 signal 到 portal 派生；dismiss 只触发调用方登记的 signal 写入请求，
 * 不在 overlay 包内命令式摘除业务状态。包内禁止平台 import、渲染上下文、字体实现和
 * 旧文本度量实现依赖。</p>
 */
package club.heiqi.uilib.ui.scene.overlay;
