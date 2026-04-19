package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.render.UiBackdropEffectSpec;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小视口容器，负责提供 padding 与可选背景/边框绘制。
 */
public class ViewportWidget extends Widget {

    private UiSurfaceStyle surfaceStyle = UiSurfaceStyle.none();
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private UiBackdropEffectSpec backdropEffectSpec = UiBackdropEffectSpec.none();

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        context.drawSurface(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), surfaceStyle);
        if (backdropEffectSpec.enabled) {
            context.enqueueBackdropEffect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(),
                    backdropEffectSpec);
        }
    }

    /**
     * 应用视口内边距。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param padding 四边统一留白
     */
    public final void applyViewportPadding(int padding) {
        applyViewportPadding(padding, padding, padding, padding);
    }

    /**
     * 应用视口内边距。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param left 左侧留白
     * @param top 上侧留白
     * @param right 右侧留白
     * @param bottom 下侧留白
     */
    public final void applyViewportPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left);
        paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right);
        paddingBottom = Math.max(0, bottom);
        requestLayout();
    }

    /**
     * 应用视口表面样式。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param surfaceStyle 表面样式；为空时恢复为空表面
     */
    public final void applyViewportSurfaceStyle(UiSurfaceStyle surfaceStyle) {
        this.surfaceStyle = Objects.requireNonNull(surfaceStyle, "surfaceStyle");
    }

    /**
     * 应用视口级 backdrop effect 配置。
     *
     * <p>该方法只负责登记宿主 effect 请求，不改变视口对子树的结构裁剪语义。</p>
     *
     * @param backdropEffectSpec effect 配置；为空时恢复为空 effect
     */
    public final void applyViewportBackdropEffect(UiBackdropEffectSpec backdropEffectSpec) {
        this.backdropEffectSpec = backdropEffectSpec == null ? UiBackdropEffectSpec.none() : backdropEffectSpec;
    }

    protected int getPaddingLeft() {
        return paddingLeft;
    }

    protected int getPaddingTop() {
        return paddingTop;
    }

    protected int getPaddingRight() {
        return paddingRight;
    }

    protected int getPaddingBottom() {
        return paddingBottom;
    }
}
