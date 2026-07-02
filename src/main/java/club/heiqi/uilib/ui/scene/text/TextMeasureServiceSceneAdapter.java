package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 装配层 adapter —— 把渲染侧 {@link TextMeasureService} 适配为 scene 核心窄端口 {@link SceneTextMeasurer}。
 *
 * <h3>定位：scene 核心与 ui.text 之间的合法接缝（I6/I10）</h3>
 * <p>本类位于 scene/text 装配子包，<b>允许 import {@code ui.text.*}</b>，是 scene 核心与
 * 渲染侧度量服务的唯一桥接点。scene 核心包（layout/paint/node）只认 {@link SceneTextMeasurer}，
 * 真实度量逻辑全部复用渲染层（I6：不重造度量），由本 adapter 三方法委托完成。</p>
 *
 * <p>装配根（如 {@code AbstractSceneHostWidget}）在构造 {@code SceneLayoutEngine} 时 new 本 adapter 注入，
 * 使引擎在不感知任何平台/渲染类型的前提下拿到真实字体度量。</p>
 */
public final class TextMeasureServiceSceneAdapter implements SceneTextMeasurer {

    /**
     * 被委托的渲染侧文本测量服务
     */
    private final TextMeasureService textMeasureService;

    /**
     * 创建 adapter。
     *
     * @param textMeasureService 渲染侧文本测量服务（非 null）
     */
    public TextMeasureServiceSceneAdapter(TextMeasureService textMeasureService) {
        if (textMeasureService == null) {
            throw new IllegalArgumentException("TextMeasureService 不可为 null");
        }
        this.textMeasureService = textMeasureService;
    }

    @Override
    public int measureWidth(String text, int fontSizePx) {
        return textMeasureService.getStringWidth(text, TextMeasureStyle.fontSizePx(fontSizePx));
    }

    @Override
    public int lineHeight(int fontSizePx) {
        return textMeasureService.getLineHeight(TextMeasureStyle.fontSizePx(fontSizePx));
    }

    @Override
    public int ascent(int fontSizePx) {
        return textMeasureService.getAscent(fontSizePx);
    }

    @Override
    public int descent(int fontSizePx) {
        return textMeasureService.getDescent(fontSizePx);
    }

    @Override
    public int lineGap(int fontSizePx) {
        return textMeasureService.getLineGap(fontSizePx);
    }

    @Override
    public int epoch() {
        return textMeasureService.getEpoch();
    }
}
