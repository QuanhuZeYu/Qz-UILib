package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 显式提供 rounded structural clip 的滚动视口容器。
 *
 * <p>该类只负责结构性子树裁剪语义，不承担 surface 外观；
 * 页面壳若需要圆角内容裁剪，应显式选择本容器，而不是让基础视口或 surface 样式隐式承担后代 clip。</p>
 */
public class RoundedScrollViewportWidget extends ScrollViewportWidget {

    private int structuralOuterCornerRadius;

    public RoundedScrollViewportWidget() {
        super();
    }

    public RoundedScrollViewportWidget(TextMeasureService textMeasureService) {
        super(textMeasureService);
    }

    /**
     * 应用对子树生效的结构性圆角裁剪半径。
     *
     * @param cornerRadius 圆角半径
     */
    protected final void applyRoundedStructuralClipCornerRadius(int cornerRadius) {
        structuralOuterCornerRadius = Math.max(0, cornerRadius);
    }

    @Override
    protected int pushAdditionalChildVisualClips(club.heiqi.uilib.ui.render.UiRenderContext context) {
        if (structuralOuterCornerRadius <= 0) {
            return 0;
        }
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        context.pushClip(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), structuralOuterCornerRadius);
        return 1;
    }

    @Override
    protected StructuralHitClip appendAdditionalChildHitClips(StructuralHitClip inheritedHitClip) {
        if (structuralOuterCornerRadius <= 0) {
            return inheritedHitClip;
        }
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        return appendStructuralHitClip(inheritedHitClip,
                new int[] { absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight() },
                structuralOuterCornerRadius);
    }

    @Override
    protected int getChildClipCornerRadius() {
        return 0;
    }
}
