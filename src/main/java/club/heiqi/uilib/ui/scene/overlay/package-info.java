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
 *
 * <p><b>R11/R13 镜像</b>：浮层显隐 signal 必须<b>独立可写</b>（R13，control 包权威源），
 * 禁派生自 {@code focused} 等路由瞬态态——DOWN 隐式失焦会在跨帧间掐断派生显隐态致浮层中途卸载、
 * CLICK 不合成（真因 D1）。overlay 命中豁免只是框架止血，控件层仍须守 R13 把 expanded 建成
 * 独立 Signal。→ {@code control/package-info.java} R13 段。</p>
 */
package club.heiqi.uilib.ui.scene.overlay;
