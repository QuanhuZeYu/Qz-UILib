package club.heiqi.uilib.ui.render;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/**
 * {@link UiRenderBackend} 的宿主侧能力门面（host 层专用）。
 *
 * <p>{@link UiRenderBackend} 是 scene 抽象层可见的最小出口契约（宪章信条六），刻意不含
 * backdrop-filter 等宿主增强能力；{@link UiRenderContext} 在 MC 平台实现上提供这些能力。
 * 宿主层（HUD 壳、屏幕桥、内部 devtools）需要消费增强能力时，统一经本门面向后端的
 * 具体类型查询转发：后端不支持时静默降级，宿主代码不直接接触 GL，也不把增强方法
 * 泄进 scene 可见的抽象契约。快照采样 framebuffer 由 UiRenderContext 内部自取，
 * 调用方无需感知。</p>
 */
public final class UiRenderBackends {

    private UiRenderBackends() {
    }

    /**
     * 向支持 backdrop-filter 的后端转发一次背后内容滤镜绘制。
     *
     * @param backend    渲染后端
     * @param left       左侧坐标
     * @param top        顶部坐标
     * @param right      右侧坐标
     * @param bottom     底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率，1.0 表示不改变
     * @param cornerRadius 圆角半径
     */
    public static void backdropFilter(UiRenderBackend backend, int left, int top, int right, int bottom,
            int blurRadius, float saturation, int cornerRadius) {
        if (backend instanceof UiRenderContext) {
            ((UiRenderContext) backend).drawBackdropFilter(left, top, right, bottom, blurRadius, saturation,
                    UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, cornerRadius)));
        }
    }

    /**
     * 向支持 backdrop-filter 的后端转发一次带 iOS 材质档的背后滤镜绘制。
     *
     * @param backend      渲染后端
     * @param left         左侧坐标
     * @param top          顶部坐标
     * @param right        右侧坐标
     * @param bottom       底部坐标
     * @param blurRadius   模糊半径像素
     * @param saturation   饱和度倍率；material 非空时被材质档取代
     * @param cornerRadius 圆角半径
     * @param material     iOS 风格材质档；null 走旧线性饱和度语义
     */
    public static void backdropFilter(UiRenderBackend backend, int left, int top, int right, int bottom,
            int blurRadius, float saturation, int cornerRadius, UiGlassMaterial material) {
        if (backend instanceof UiRenderContext) {
            ((UiRenderContext) backend).drawBackdropFilter(left, top, right, bottom, blurRadius, saturation,
                    UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, cornerRadius)), material);
        }
    }

    /**
     * 向支持 backdrop-filter 的后端转发一次带完整效果配方（经典/Liquid Glass）的绘制。
     *
     * @param backend      渲染后端
     * @param left         左侧坐标
     * @param top          顶部坐标
     * @param right        右侧坐标
     * @param bottom       底部坐标
     * @param blurRadius   模糊半径像素
     * @param saturation   饱和度倍率；effect 带材质档时被材质配方接管（作 vibrancy 乘子）
     * @param cornerRadius 圆角半径
     * @param effect       效果配方；null 走旧线性饱和度语义
     */
    public static void backdropFilter(UiRenderBackend backend, int left, int top, int right, int bottom,
            int blurRadius, float saturation, int cornerRadius, UiBackdropEffect effect) {
        if (backend instanceof UiRenderContext) {
            ((UiRenderContext) backend).drawBackdropFilter(left, top, right, bottom, blurRadius, saturation,
                    UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, cornerRadius)), effect);
        }
    }
}
