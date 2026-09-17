package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.font.util.UnicodeTextClassifier;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 装配层 adapter —— 把渲染侧 {@link TextMeasureService} 适配为 scene 核心窄端口 {@link SceneTextMeasurer}。
 *
 * <h3>定位：scene 核心与 ui.text 之间的合法接缝（唯一桥接点）</h3>
 * <p>本类位于 scene/text 装配子包，<b>允许 import {@code ui.text.*}</b>，是 scene 核心与
 * 渲染侧度量服务的唯一桥接点。scene 核心包（layout/paint/node）只认 {@link SceneTextMeasurer}，
 * 真实度量逻辑全部复用渲染层（不重造度量），由本 adapter 三方法委托完成。</p>
 *
 * <p>装配根（如 {@code AbstractSceneHostWidget}）在构造 {@code SceneLayoutEngine} 时 new 本 adapter 注入，
 * 使引擎在不感知任何平台/渲染类型的前提下拿到真实字体度量。</p>
 *
 * <h3>字号 0 的短路（本类是度量边界）</h3>
 * <p>{@code fontSizePx <= 0} 时全部度量返回零值：宽 0、行高 0、上下度量 0、不拆行、不裁剪、无链接区域。
 * 这是「字号 0 = 文本不占空间」这条语义的<b>唯一度量侧落点</b>——渲染侧字形路径里的
 * {@code Math.max(1, …)}（atlas 槽位、纹理尺寸）属进入栅格化之后的内部尺寸，此处短路后不会走到，
 * 故不需要、也不应该逐一放宽（那会让 0 在每个层级被重新解释成不同的最小值）。</p>
 *
 * <p>非零输入逐位不变：短路只对 {@code <= 0} 生效，其余路径与本类此前完全一致。</p>
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
        if (fontSizePx <= 0) {
            return 0;
        }
        return textMeasureService.getStringWidth(text, TextMeasureStyle.fontSizePx(fontSizePx));
    }

    @Override
    public boolean isLineBreak(int codepoint) {
        return UnicodeTextClassifier.isLineBreak(codepoint);
    }

    @Override
    public int lineHeight(int fontSizePx) {
        if (fontSizePx <= 0) {
            return 0;
        }
        return textMeasureService.getLineHeight(TextMeasureStyle.fontSizePx(fontSizePx));
    }

    @Override
    public int ascent(int fontSizePx) {
        return fontSizePx <= 0 ? 0 : textMeasureService.getAscent(fontSizePx);
    }

    @Override
    public int descent(int fontSizePx) {
        return fontSizePx <= 0 ? 0 : textMeasureService.getDescent(fontSizePx);
    }

    @Override
    public int lineGap(int fontSizePx) {
        return fontSizePx <= 0 ? 0 : textMeasureService.getLineGap(fontSizePx);
    }

    @Override
    public int epoch() {
        return textMeasureService.getEpoch();
    }

    @Override
    public java.util.List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
        String safeText = text == null ? "" : text;
        if (fontSizePx <= 0) {
            // 零字号不拆行：整段原样单行（宽度为 0，换行无意义，且要保住文本内容供上层读回）
            return java.util.Collections.singletonList(safeText);
        }
        // 非 wrap（wrapWidth<=0）同样按硬换行拆行：无限宽下软换行不触发，
        // 硬换行经 wrap 重建保证样式跨行续传（<br>/\n 不再被渲染层吞掉）。
        int effectiveWrapWidth = wrapWidth <= 0 ? Integer.MAX_VALUE : wrapWidth;
        // 字号必须跟着进换行（与下方 trimToWidth 同源口径）：不走带 style 的重载，
        // 测量就退回基准 charSize，非基准字号下每行按比例溢出容器被裁 —— 现象是
        // 「长 URL 首行右侧凭空消失」，布局与拆行都不报错。
        return textMeasureService.listFormattedStringToWidth(safeText, effectiveWrapWidth,
                new TextMeasureStyle(fontSizePx, toTextContentMode(textMode),
                        club.heiqi.uilib.ui.base.props.UiFontWeight.NORMAL,
                        club.heiqi.uilib.ui.base.props.UiFontStyle.NORMAL));
    }

    @Override
    public int lineHeight(String text, int fontSizePx, int textMode) {
        String safeText = text == null ? "" : text;
        if (fontSizePx <= 0) {
            return 0;
        }
        return textMeasureService.getLineHeight(safeText,
                new TextMeasureStyle(fontSizePx, toTextContentMode(textMode), club.heiqi.uilib.ui.base.props.UiFontWeight.NORMAL,
                        club.heiqi.uilib.ui.base.props.UiFontStyle.NORMAL));
    }

    @Override
    public String trimToWidth(String text, int fontSizePx, int width, int textMode) {
        String safeText = text == null ? "" : text;
        if (width <= 0 || fontSizePx <= 0) {
            return safeText;
        }
        // 必须走带 style 重载：无 style 重载按渲染层基准字号（charSize）裁剪，与节点 fontSizePx 脱钩，
        // 非基准字号下省略号测距错误（SceneLineClamp 依赖本方法的字号感知裁剪）。
        return textMeasureService.trimStringToWidth(safeText, width,
                new TextMeasureStyle(fontSizePx, toTextContentMode(textMode),
                        club.heiqi.uilib.ui.base.props.UiFontWeight.NORMAL,
                        club.heiqi.uilib.ui.base.props.UiFontStyle.NORMAL));
    }

    @Override
    public java.util.List<TextLinkRegion> linkRegions(String line, int fontSizePx, int textMode) {
        String safeLine = line == null ? "" : line;
        if (fontSizePx <= 0) {
            return java.util.Collections.emptyList();
        }
        java.util.List<club.heiqi.uilib.ui.text.TextLinkRegion> regions =
                textMeasureService.getLinkRegions(safeLine,
                        new TextMeasureStyle(fontSizePx, toTextContentMode(textMode),
                                club.heiqi.uilib.ui.base.props.UiFontWeight.NORMAL,
                                club.heiqi.uilib.ui.base.props.UiFontStyle.NORMAL));
        java.util.List<TextLinkRegion> mapped = new java.util.ArrayList<TextLinkRegion>();
        for (club.heiqi.uilib.ui.text.TextLinkRegion region : regions) {
            mapped.add(new TextLinkRegion(region.getStartX(), region.getWidth(), region.getUrl()));
        }
        return mapped;
    }

    /**
     * scene → ui.text 内容模式的<b>唯一映射点</b>。
     *
     * <p>SceneTextMode 不得 import {@code ui.text.*}（渲染类型止于本装配层），本方法的 switch 是 scene 装配
     * 接缝内的唯一映射点。渲染层（UiRenderContext）不再经本方法取模式（2026-09-01 越界引用
     * 收回）：其 drawText(int textMode) 重载直取 scene 值类型 SceneTextMode.fromCode 归一后
     * 按 code 映射 TextContentMode——code↔ordinal 逐位对齐由 SceneTextModeTest 编译期守卫
     * 锁死，故不构成第二套 switch；ScenePackageIsolationTest 反向守卫禁止 render 再伸入本类。</p>
     *
     * @param mode scene 内容模式（非 null）
     * @return 渲染侧对应模式
     */
    public static TextContentMode toTextContentMode(SceneTextMode mode) {
        switch (mode) {
            case MINECRAFT_FORMATTED:
                return TextContentMode.MINECRAFT_FORMATTED;
            case RICH_TAGS:
                return TextContentMode.RICH_TAGS;
            default:
                return TextContentMode.UILIB_RAW;
        }
    }

    /**
     * 遗留 int 编码映射（先归一为 {@link SceneTextMode} 再走唯一映射点）。
     *
     * @param textMode 稳定编码（0/1/2，越界回落原始文本）
     * @return 渲染侧对应模式
     */
    public static TextContentMode toTextContentMode(int textMode) {
        return toTextContentMode(SceneTextMode.fromCode(textMode));
    }
}
