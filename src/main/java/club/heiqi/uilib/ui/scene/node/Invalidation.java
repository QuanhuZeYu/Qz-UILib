package club.heiqi.uilib.ui.scene.node;

/**
 * 失效级别枚举 —— 对应信条五"分级失效"的三级模型。
 *
 * <p>每个 effect 触发时必须声明它影响哪一级，只打对应脏标记：
 * <ul>
 *   <li><b>LAYOUT</b>：文本/尺寸/增删节点 → 需重布局 → 重绘 → 重合成</li>
 *   <li><b>PAINT</b>：颜色/背景/边框 → 跳过布局、需重绘 → 重合成</li>
 *   <li><b>COMPOSITE</b>：transform/opacity → 跳过布局和绘制、仅 GPU 重新合成</li>
 * </ul>
 *
 * <p>调用方不应手选级别，而是通过 {@link SceneNode} 强类型 setter 自动打出正确的失效级别，
 * 以降低 I4"打错级别"的风险。</p>
 */
public enum Invalidation {
    /** 布局级失效：文本/尺寸/增删节点变化，触发重布局并连带重绘和重新合成 */
    LAYOUT,
    /** 绘制级失效：颜色/背景/边框变化，跳过布局但触发重绘和重新合成 */
    PAINT,
    /** 合成级失效：transform/opacity 变化，跳过布局和绘制，仅重新合成 */
    COMPOSITE
}
