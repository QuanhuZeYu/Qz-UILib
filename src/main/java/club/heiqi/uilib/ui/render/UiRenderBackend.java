package club.heiqi.uilib.ui.render;

import java.util.List;

import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * scene 渲染出口契约线（架构宪章信条六）。
 *
 * <p>scene 数据层（layout / paint / node）经 Display List 产出纯数据绘制命令，
 * scene 回放器（ScenePaintReplayer）把这些命令翻译为对本接口的调用。scene 核心
 * 只通过本接口认识渲染层，绝不持有任何具体后端类，从而兑现信条六承诺：换渲染
 * 后端（Vulkan / Metal / WebGPU / AWT 等）只需另写一份本接口的实现，scene 核心
 * 代码零改动即可移植到任意 Java 程序。</p>
 *
 * <p>本接口的方法全部是平台无关的纯数值绘制指令（坐标、颜色、文本、不透明度、
 * transform 分量、圆角等），不出现任何 GL / Minecraft 类型，也不出现 signal /
 * 组件 / DOM 概念（守不变量 I6）。实现方负责把这些指令翻译成具体平台的绘制调用：
 * Minecraft 平台的实现是 {@link UiRenderContext}，它把每条指令焊到 Tessellator +
 * LWJGL GL 调用上；移植到其它平台只需另写一份实现，无需触碰 scene 核心。</p>
 *
 * <p>方法集 = scene 回放器当前实际回放命令所需的全部能力。本接口只收录 scene
 * 出口契约真正消费的方法，不收录 {@link UiRenderContext} 面向旧栈的其它重载。</p>
 */
public interface UiRenderBackend {

    /**
     * 返回把本后端坐标按 {@code scale} 放大的装饰器（屏幕级宿主边界恰好换算一次）。
     *
     * <p>{@code scale == 1} 时返回自身（零开销）；实现统一由 {@link ScaledRenderBackend} 承载，
     * 宿主不应再手写坐标缩放转发样板。</p>
     *
     * @param scale 缩放倍率
     * @return 缩放后的后端
     */
    default UiRenderBackend scaled(float scale) {
        return Float.compare(scale, 1F) == 0 ? this : new ScaledRenderBackend(this, scale);
    }

    /**
     * 在实际 replay 前批量发布本 plan 的 visible text demand。旧 backend 默认忽略该调度提示。
     *
     * @param texts 当前 plan 中的 raw 文本
     */
    default void publishTextDemand(List<String> texts) {
        // 不支持异步字体调度的 backend 保持无副作用。
    }

    /**
     * 绘制填充矩形。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param color ARGB 颜色
     */
    void fillRect(int left, int top, int right, int bottom, int color);

    /**
     * 绘制平台中立图片。默认空实现保持既有 backend 源码兼容。
     *
     * @param source 图片源
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    default void drawImage(SceneImageSource source, int left, int top, int right, int bottom) {
        // 旧 backend 不支持图片时保持无副作用。
    }

    /**
     * 绘制带圆角的表面。
     *
     * <p>第 7 参降级为 {@code int cornerRadius}（uniform 单值），避免 scene 回放器
     * 反向依赖 {@code ui.style} 包的 {@code ResolvedCornerRadii} 类型（守不变量 I6）。
     * render 层实现方负责把该单值转成内部所需的分角圆角结构。</p>
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param fillColor 填充颜色
     * @param borderColor 边框颜色
     * @param cornerRadius 圆角半径（uniform 单值）
     */
    void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            int cornerRadius);

    /**
     * 绘制矩形边框。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param color ARGB 颜色
     */
    void drawBorder(int left, int top, int right, int bottom, int color);

    /**
     * 压入一个支持圆角的视觉裁剪区域。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param cornerRadius 圆角半径；为 0 时退化为普通矩形裁剪
     */
    void pushClip(int left, int top, int right, int bottom, int cornerRadius);

    /**
     * 弹出最近压入的裁剪区域，与 {@link #pushClip} 严格配对。
     */
    void popClip();

    /**
     * 绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     */
    void drawText(String text, int x, int y, int color, boolean shadow);

    /**
     * 按指定 UI 像素字号绘制文本。
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param fontSizePx UI 像素字号
     */
    void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx);

    /**
     * 按指定 UI 像素字号与内容模式绘制文本。
     *
     * <p>{@code textMode} 编码与 scene paint 契约的 TEXT_MODE_* 常量一致
     * （0=原始文本 / 1=Minecraft § / 2=富文本标签）。默认实现回落旧路径（忽略模式），
     * 渲染层实现按需映射到自己的内容模式类型。</p>
     *
     * @param text 文本
     * @param x 绘制 X
     * @param y 绘制 Y
     * @param color ARGB 颜色
     * @param shadow 是否带阴影
     * @param fontSizePx UI 像素字号
     * @param textMode 内容模式编码
     */
    default void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx, int textMode) {
        drawText(text, x, y, color, shadow, fontSizePx);
    }

    /**
     * 进入 group opacity 合成作用域。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param opacity 当前上下文的局部 opacity
     */
    void pushGroupOpacity(int left, int top, int right, int bottom, float opacity);

    /**
     * 退出 group opacity 合成作用域，与 {@link #pushGroupOpacity} 严格配对。
     */
    void popGroupOpacity();

    /**
     * 压入 transform 顶点变换作用域（纯数值，全 primitive，零 scene / DOM 概念）。
     *
     * @param translateX X 轴平移量（浮点像素）
     * @param translateY Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX X 轴缩放倍率
     * @param scaleY Y 轴缩放倍率
     * @param originXRatio 变换原点 X 比率（box 归一化坐标）
     * @param originYRatio 变换原点 Y 比率（box 归一化坐标）
     * @param left 绝对左边界（像素）
     * @param top 绝对上边界（像素）
     * @param right 绝对右边界（像素）
     * @param bottom 绝对下边界（像素）
     */
    void pushTransform(float translateX, float translateY, float rotateDegrees,
            float scaleX, float scaleY, float originXRatio, float originYRatio,
            int left, int top, int right, int bottom);

    /**
     * 弹出最近压入的 transform 作用域，与 {@link #pushTransform} 严格配对。
     */
    void popTransform();

    /**
     * 进入 transform 离屏图层作用域（B6 FBO 方案，transform+clip 叠加正确处理）。
     *
     * <p>内部借 FBO 离屏层 + MODELVIEW 归 I + 重建父 clip，使段内 scissor 在未变换坐标系下
     * 轴对齐正确裁剪（解决 rotate 下 scissor 矩形无视 GL 矩阵的物理限制）。POP 时切回父 FBO +
     * 压 T 矩阵 + 回贴贴图（吃 T 旋转，父 clip 二次裁切）。与 {@link #popTransformLayer} 严格配对。</p>
     *
     * <p>FBO 不可用时降级为「保留 clip 放弃 transform」：不进 FBO、不压 T 矩阵，
     * 段内子树在未变换坐标下正确裁剪但失去 transform 视觉效果。</p>
     *
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     * @param left          绝对左边界（像素）
     * @param top           绝对上边界（像素）
     * @param right         绝对右边界（像素）
     * @param bottom        绝对下边界（像素）
     */
    void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
            float scaleX, float scaleY, float originXRatio, float originYRatio,
            int left, int top, int right, int bottom);

    /**
     * 退出 transform 离屏图层作用域，与 {@link #pushTransformLayer} 严格配对。
     */
    void popTransformLayer();
}
