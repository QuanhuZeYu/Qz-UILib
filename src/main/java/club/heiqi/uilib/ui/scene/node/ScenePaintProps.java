package club.heiqi.uilib.ui.scene.node;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.image.SceneImageRect;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * SceneNode 绘制/合成属性值容器。
 *
 * <p>本类只保存字段值，不持有 {@link SceneNode} 引用，不做去重判断，不打脏标记。</p>
 */
final class ScenePaintProps {

    /** 背景颜色（ARGB），默认 0（透明） */
    int backgroundColor;

    /**
     * 背后滤镜配方（磨玻璃 / Liquid Glass）；null = 不启用。
     *
     * <p>PAINT 级属性（只改绘制输出、不改盒尺寸），与 backgroundColor 同构。
     * 值对象不可变，故 fragment 复用与跨线程读安全。</p>
     */
    UiBackdrop backdrop;

    /** 平台中立图片源；对象身份变化只触发 PAINT。 */
    SceneImageSource imageSource;

    /** 图片在节点局部坐标中的目标矩形；null 表示铺满布局盒。 */
    SceneImageRect imageRect;

    /** 不透明度，默认 1.0f（完全不透明） */
    float opacity = 1.0f;

    /** 合成级变换，默认 null（无变换） */
    Transform transform;

    /**
     * 合成级变换偏好离屏图层栅格化（B6 方案）：transform 非恒等时优先走
     * PUSH_TRANSFORM_LAYER（FBO 内 identity 栅格化 + 贴回施加 transform），
     * 而非 PUSH_TRANSFORM 逐顶点变换。默认 false（保持纯顶点变换）。
     */
    boolean preferTransformLayer;

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

    /**
     * 四角独立圆角半径（像素）。默认 -1 = 未做分角设置，回退 {@link #cornerRadius}；
     * 任一 >=0 表示已显式设置四角（per-corner 生效，忽略 uniform 值）。
     * 只影响绘制输出，标 PAINT。
     */
    int cornerRadiusTopLeft = -1;
    int cornerRadiusTopRight = -1;
    int cornerRadiusBottomRight = -1;
    int cornerRadiusBottomLeft = -1;

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

    // ==================== 分角圆角访问（T4a） ====================

    /**
     * 是否已做四角独立圆角设置。
     *
     * @return 四个分角值任一 >=0 即 true（-1 为「未分角、回退 uniform」哨兵）
     */
    boolean isPerCorner() {
        return cornerRadiusTopLeft >= 0 || cornerRadiusTopRight >= 0
                || cornerRadiusBottomRight >= 0 || cornerRadiusBottomLeft >= 0;
    }

    /** @return 左上圆角（像素）；-1 = 未分角设置，回退 {@link #cornerRadius} */
    int getCornerRadiusTopLeft() { return cornerRadiusTopLeft; }

    /** @return 右上圆角（像素）；-1 = 未分角设置，回退 {@link #cornerRadius} */
    int getCornerRadiusTopRight() { return cornerRadiusTopRight; }

    /** @return 右下圆角（像素）；-1 = 未分角设置，回退 {@link #cornerRadius} */
    int getCornerRadiusBottomRight() { return cornerRadiusBottomRight; }

    /** @return 左下圆角（像素）；-1 = 未分角设置，回退 {@link #cornerRadius} */
    int getCornerRadiusBottomLeft() { return cornerRadiusBottomLeft; }
}
