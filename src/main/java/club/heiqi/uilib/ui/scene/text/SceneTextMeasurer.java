package club.heiqi.uilib.ui.scene.text;

/**
 * scene 核心的窄端口文本度量接口。
 *
 * <h3>定位：scene 核心与渲染层度量服务之间的零依赖接缝</h3>
 * <p>scene 核心包（layout/paint/node）严禁 import 任何平台类、渲染上下文或
 * {@code ui.text.*} 度量实现（I10）。布局引擎只认本接口，真实度量由装配层 adapter
 * （{@code TextMeasureServiceSceneAdapter}）持有渲染侧 {@code TextMeasureService} 委托完成（I6）。</p>
 *
 * <p>三个方法均以 UI 像素为单位（除 {@link #epoch()}），布局引擎据此做叶节点 shrink-to-fit
 * 与多行行高累计。</p>
 */
public interface SceneTextMeasurer {

    /**
     * 测量指定字号下单行文本的 UI 像素宽度。
     *
     * @param text       文本内容（可为 null，由实现按空串处理）
     * @param fontSizePx UI 像素字号
     * @return UI 像素宽度
     */
    int measureWidth(String text, int fontSizePx);

    /**
     * 获取指定字号下的 UI 像素行高。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素行高
     */
    int lineHeight(int fontSizePx);

    /**
     * 获取指定字号下的 UI 像素字体上升量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素上升量
     */
    default int ascent(int fontSizePx) {
        return lineHeight(fontSizePx);
    }

    /**
     * 获取指定字号下的 UI 像素字体下降量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素下降量
     */
    default int descent(int fontSizePx) {
        return 0;
    }

    /**
     * 获取字体运行时纪元。
     *
     * <p>底层字体运行时变化时返回新值，布局引擎据此驱动文本叶节点测量失效（只向上冒泡标脏）。</p>
     *
     * @return 当前字体运行时纪元
     */
    int epoch();
}
