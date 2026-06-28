package club.heiqi.uilib.ui.scene.layout;

/**
 * 子级交叉轴对齐覆盖，单独覆盖父容器的 {@link CrossAxisAlign}。
 *
 * <p>语义对齐 CSS {@code align-self}：子节点可单独声明自己在交叉轴上的对齐方式，
 * 覆盖父容器 {@link SceneNode#getCrossAxisAlign()} 的设置。</p>
 *
 * <ul>
 *   <li>{@link #AUTO}：继承父级 {@code crossAxisAlign}（默认，零回归）。</li>
 *   <li>{@link #START}：靠交叉轴起点对齐（覆盖父级）。</li>
 *   <li>{@link #CENTER}：沿交叉轴居中（覆盖父级）。</li>
 *   <li>{@link #END}：靠交叉轴终点对齐（覆盖父级）。</li>
 *   <li>{@link #STRETCH}：沿交叉轴拉伸填满（覆盖父级）。</li>
 * </ul>
 *
 * <p>默认值为 {@link #AUTO}，保证不设置时回退父级 {@code crossAxisAlign}，
 * 与现有引擎行为完全一致（零回归）。有效对齐的解析在
 * {@code FlexLayouter.effectiveCrossAlign} 内集中处理。</p>
 */
public enum AlignSelf {
    /** 继承父级 crossAxisAlign（默认，零回归） */
    AUTO,
    /** 靠交叉轴起点对齐（覆盖父级） */
    START,
    /** 沿交叉轴居中（覆盖父级） */
    CENTER,
    /** 靠交叉轴终点对齐（覆盖父级） */
    END,
    /** 沿交叉轴拉伸填满（覆盖父级） */
    STRETCH
}
