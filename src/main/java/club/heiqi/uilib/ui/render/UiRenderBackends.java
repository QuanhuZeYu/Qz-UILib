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
        UiRenderContext context = resolveContext(backend);
        if (context == null) {
            return;
        }
        float scale = accumulatedScale(backend);
        context.drawBackdropFilter(px(left, scale), px(top, scale), px(right, scale), px(bottom, scale),
                px(blurRadius, scale), saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(px(Math.max(0, cornerRadius), scale)), effect);
    }

    /**
     * 向支持 backdrop-filter 的后端转发一次声明式玻璃绘制（scene 通道回放专用）。
     *
     * <p>入参是 <strong>logical px</strong>（replayer 在 scaled 链内层回放，命令坐标与
     * 节点布局同域），故必须与 {@link #backdropFilter(UiRenderBackend, int, int, int, int,
     * int, float, int, UiBackdropEffect)} 走同一套 scaled 穿透 + 累计缩放换算——
     * 直接 instanceof 会在 GUI scale != 1 的 HUD 下静默丢玻璃。</p>
     *
     * @param backend     渲染后端（可为 scaled 装饰器）
     * @param left        左侧坐标（logical px）
     * @param top         顶部坐标（logical px）
     * @param right       右侧坐标（logical px）
     * @param bottom      底部坐标（logical px）
     * @param backdrop    节点声明的玻璃配方；null 或非活跃时不绘
     * @param cornerRadii 四角圆角（logical px）
     */
    public static void backdropFilter(UiRenderBackend backend, int left, int top, int right, int bottom,
            UiBackdrop backdrop, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        if (backdrop == null || !backdrop.isActive()) {
            return;
        }
        UiRenderContext context = resolveContext(backend);
        if (context == null) {
            return;
        }
        float scale = accumulatedScale(backend);
        context.drawBackdropFilter(px(left, scale), px(top, scale), px(right, scale), px(bottom, scale),
                px(backdrop.getBlurRadius(), scale), backdrop.getSaturation(),
                cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0)
                        : cornerRadii.scale(scale), backdrop.getEffect());
    }

    /**
     * 为一次回放开启 backdrop 批次（穿透 scaled 链，与 {@link #backdropFilter} 同一口径）。
     *
     * <p>批次内所有兄弟玻璃共享同一份背景采样：语义上互不透过（对齐 iOS 同一 visual
     * effect 层级），性能上把 N 次快照捕获降为 1 次。后端不支持时静默 no-op。</p>
     *
     * @param backend 渲染后端（可为 scaled 装饰器）
     */
    public static void beginBackdropBatch(UiRenderBackend backend) {
        UiRenderContext context = resolveContext(backend);
        if (context != null) {
            context.beginBackdropBatch();
        }
    }

    /** 结束 backdrop 批次，与 {@link #beginBackdropBatch(UiRenderBackend)} 严格配对。 */
    public static void endBackdropBatch(UiRenderBackend backend) {
        UiRenderContext context = resolveContext(backend);
        if (context != null) {
            context.endBackdropBatch();
        }
    }

    /**
     * 穿透 scaled 装饰器链取回真实渲染上下文。
     *
     * <p>为什么必须穿透而不是只认 instanceof：HUD 宿主在 GUI scale != 1 时（MC 常态）
     * 把后端包成 ScaledRenderBackend，它不是 UiRenderContext，早期版本在此静默返回、
     * 玻璃整块不渲染且无任何报错——正是本仓反复踩的"能力探测静默降级"。链可嵌套，
     * 故循环下钻。</p>
     */
    private static UiRenderContext resolveContext(UiRenderBackend backend) {
        UiRenderBackend current = backend;
        // 链长有限且极短（通常 0~1 层）；上限只为防御异常构造，不承载语义。
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof UiRenderContext) {
                return (UiRenderContext) current;
            }
            if (!(current instanceof ScaledRenderBackend)) {
                return null;
            }
            current = ((ScaledRenderBackend) current).delegate();
        }
        return null;
    }

    /** 累加 scaled 链上的全部缩放倍率（logical -> framebuffer 的单次换算量）。 */
    private static float accumulatedScale(UiRenderBackend backend) {
        float product = 1.0F;
        UiRenderBackend current = backend;
        for (int depth = 0; current instanceof ScaledRenderBackend && depth < 8; depth++) {
            ScaledRenderBackend scaled = (ScaledRenderBackend) current;
            product *= scaled.scale();
            current = scaled.delegate();
        }
        return product;
    }

    private static int px(int logical, float scale) {
        return scale == 1.0F ? logical : Math.round(logical * scale);
    }
}