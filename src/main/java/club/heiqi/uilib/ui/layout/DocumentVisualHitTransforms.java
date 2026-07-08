package club.heiqi.uilib.ui.layout;

import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.base.values.UiTransform;

/**
 * 视觉命中中的 transform 坐标映射辅助。
 */
final class DocumentVisualHitTransforms {

    private DocumentVisualHitTransforms() {}

    /**
     * 将文档坐标按当前盒的 transform 反向映射到该盒的未变换视觉坐标系。
     *
     * @param box 布局盒
     * @param boxOffsetX 盒视觉 X 偏移
     * @param boxOffsetY 盒视觉 Y 偏移
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 反向映射后的点；矩阵不可逆时返回 null
     */
    static UiTransform.Point inverseTransformPoint(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY,
            float documentX, float documentY, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        UiTransform transform = DocumentRuntimeTransforms.resolveTransform(box, currentTimeNanos,
                animationTimeline);
        if (transform == null || transform.isIdentity()) {
            return new UiTransform.Point(documentX, documentY);
        }
        return transform.inverseTransformPoint(documentX, documentY, box.getLeft() + boxOffsetX,
                box.getTop() + boxOffsetY, box.getWidth(), box.getHeight());
    }
}
