package club.heiqi.uilib.ui.scene.paint;

/**
 * 绘制命令类型枚举，定义 Display List 中每条绘制命令的语义类型。
 *
 * <p>渲染层根据命令类型选择对应的绘制操作，不感知任何上层概念（宪章信条六/I6）。
 * 每条命令仅携带构建期固化的数据，回放期零节点反查。</p>
 *
 * <p>当前仅包含"矩形+文本"切片所需的最小命令集，后续按需扩展。</p>
 */
public enum PaintCommandType {

    /**
     * 填充矩形/背景色。
     *
     * <p>渲染层操作：在指定矩形区域内用指定颜色填充表面，
     * 可能包含圆角、透明度等效果参数。</p>
     */
    BACKGROUND,

    /**
     * 绘制一行文本。
     *
     * <p>渲染层操作：在指定坐标位置，按 {@link TextStyle} 指定的颜色、
     * 字号等样式绘制文本内容。</p>
     */
    TEXT,

    /**
     * 进入 group opacity 合成作用域（Phase 3B，合成级动画）。
     *
     * <p>渲染层操作：调用 {@code ctx.pushPaintContext(left, top, right, bottom, opacity)}
     * 开启一个 group opacity 合成层。该作用域内的所有后续命令（直到配对的
     * {@link #POP_OPACITY}）整体按 opacity 合成，半透明子树叠加语义正确
     * （嵌套相乘由渲染层离屏层栈天然完成，回放器只传该层局部 opacity）。</p>
     *
     * <p>仅当节点 opacity &lt; 1.0 时由绘制引擎产出；opacity==1.0 走快速路径不产生本命令。
     * 命令携带绝对屏幕区域（left/top/right/bottom）+ 局部 opacity。</p>
     */
    PUSH_OPACITY,

    /**
     * 退出 group opacity 合成作用域（Phase 3B，与 {@link #PUSH_OPACITY} 配对）。
     *
     * <p>渲染层操作：调用 {@code ctx.popPaintContext()}，关闭最近一层 group opacity
     * 合成层。push/pop 由绘制引擎 paintNode 递归骨架保证严格配对、正确嵌套。</p>
     */
    POP_OPACITY

    // 预留扩展（本切片不实现，仅作占位注释）：
    // BORDER  - 绘制边框（后续扩展）
    // IMAGE   - 绘制图片/纹理（后续扩展）
    // CLIP_START / CLIP_END - 裁剪作用域（后续扩展）
    // TRANSFORM - 变换作用域（后续扩展，本期 transform 走 offset 通路不走此命令）
}
