package club.heiqi.uilib.ui.render;

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
     * 进入 group opacity 合成作用域。
     *
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param opacity 当前上下文的局部 opacity
     */
    void pushPaintContext(int left, int top, int right, int bottom, float opacity);

    /**
     * 退出 group opacity 合成作用域，与 {@link #pushPaintContext} 严格配对。
     */
    void popPaintContext();

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
}
