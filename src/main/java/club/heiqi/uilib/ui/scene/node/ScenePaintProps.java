package club.heiqi.uilib.ui.scene.node;

/**
 * SceneNode 绘制/合成属性值容器。
 *
 * <p>本类只保存字段值，不持有 {@link SceneNode} 引用，不做去重判断，不打脏标记。</p>
 */
final class ScenePaintProps {

    /** 背景颜色（ARGB），默认 0（透明） */
    int backgroundColor;

    /** 不透明度，默认 1.0f（完全不透明） */
    float opacity = 1.0f;

    /** 合成级变换，默认 null（无变换） */
    Transform transform;

    /**
     * 边框颜色（ARGB），默认 0（无边框）。
     *
     * <p>第 0 段裁决：边框不占布局空间（box-sizing: border-box 简化），
     * 故边框相关属性只标 PAINT，绝不标 LAYOUT。</p>
     */
    int borderColor;

    /** 边框宽度（像素），默认 0（无边框）。第 0 段裁决：边框不占布局空间，只标 PAINT */
    int borderWidth;

    /** 圆角半径（像素），默认 0（直角）。只影响绘制输出，标 PAINT */
    int cornerRadius;

    /** 是否裁剪超出本节点边界的子节点绘制，默认 false。只影响绘制裁剪，标 PAINT */
    boolean clipChildren;

    /**
     * 文本颜色（ARGB），默认 0xFFFFFFFF（白色，兼容现有默认）。
     *
     * <p>文本颜色变化只改绘制输出、不改文字尺寸，故只标 PAINT，
     * 绝不像 {@link SceneNode#setText} 那样标 LAYOUT+PAINT。</p>
     */
    int textColor = 0xFFFFFFFF;

    /** 文本在布局盒内的水平对齐方式，默认贴左。PAINT 级属性，不影响盒尺寸。 */
    TextHorizontalAlign textHorizontalAlign = TextHorizontalAlign.LEFT;

    /** 文本在布局盒内的垂直对齐方式，默认居中。PAINT 级属性，不影响盒尺寸。 */
    TextVerticalAlign textVerticalAlign = TextVerticalAlign.CENTER;
}
