package club.heiqi.uilib.ui.render;

/**
 * 页面级背景模糊预设。
 *
 * <p>预设只描述页面作者可理解的策略意图，不暴露 OpenGL、FBO 或 Minecraft GUI 生命周期细节。</p>
 */
public enum BackdropBlurPreset {
    /** 完全继承全局背景模糊配置。 */
    DEFAULT,

    /** 禁用当前页面的宿主级背景模糊和元素级 backdrop-filter。 */
    DISABLED,

    /** 降低当前页面的模糊成本，优先保证性能。 */
    PERFORMANCE,

    /** 提高当前页面的视觉质量，允许更高渲染成本。 */
    QUALITY,

    /** 优先使用兼容路径，降低 shader/FBO 兼容风险。 */
    COMPATIBILITY
}
