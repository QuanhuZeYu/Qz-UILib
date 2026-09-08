package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 页面私有 L3：源与样式在构造时冻结；像素计划仅随宽、字号、度量服务或纪元变化重建。 */
final class MarkdownPageContent {
    private final MarkdownDocument.LayoutContent content;
    private TextLayoutService cachedMeasurer;
    private int cachedWidth;
    private int cachedFont;
    private int cachedEpoch;
    private MarkdownPainter.ContentLayout cached;

    MarkdownPageContent(String source, MarkdownStyleTable styles, TextStyle base) {
        // 每次挂载仅解析一次。修改源或样式需要创建新消费实例，旧计划不会跨文档复用。
        content = MarkdownDocument.parse(source).toLayoutContent(styles, base);
    }

    MarkdownPainter.ContentLayout layout(TextLayoutService measurer, int width, int font, int epoch) {
        int safeWidth = Math.max(1, width);
        if (cached != null && cachedMeasurer == measurer && cachedWidth == safeWidth
                && cachedFont == font && cachedEpoch == epoch) {
            // 命中跳过全 cell 度量、列宽分配、换行、行高、定位及 PaintCommand 构造。
            return cached;
        }
        MarkdownPainter.ContentLayout next = MarkdownPainter.layoutContent(content, measurer, safeWidth, font);
        cachedMeasurer = measurer;
        cachedWidth = safeWidth;
        cachedFont = font;
        cachedEpoch = epoch;
        cached = next;
        return next;
    }

    static SceneNode create(SceneRuntime rt, String source, MarkdownStyleTable styles, TextStyle base,
            int font, Supplier<TextLayoutService> measurers, IntSupplier epochs) {
        MarkdownPageContent cache = new MarkdownPageContent(source, styles, base);
        SceneNode body = SceneNode.column(0).setFillParentWidth(true).setHitTestable(false);
        Signal<List<SceneNode>> children = Signal.create(Collections.<SceneNode>emptyList());
        rt.forEach(body, children, node -> node, node -> node);
        int[] width = {0};
        int[] epoch = {epochs.getAsInt()};
        MarkdownPainter.ContentLayout[] published = {null};
        Runnable refresh = () -> {
            if (width[0] <= 0) return;
            TextLayoutService measurer = measurers.get();
            MarkdownPainter.ContentLayout plan = cache.layout(measurer, width[0], font, epoch[0]);
            if (published[0] == plan) return;
            published[0] = plan;
            body.setPreferredHeight(Math.max(1, plan.getHeightPx()));
            children.set(nodes(plan, measurer));
        };
        // 与 SceneTextAreaPrimitive 同源：最终布局盒发布后读取真实内容宽，两趟收敛。
        rt.bind(rt.layoutDoneSignal(), done -> {
            LayoutBox box = (LayoutBox) body.getCachedLayout();
            if (box == null) return;
            int available = Math.max(0, box.getWidth() - body.getPaddingLeft() - body.getPaddingRight());
            if (available == width[0]) return;
            width[0] = available;
            refresh.run();
        });
        // 帧订阅只比较整数纪元；稳态不解析、不度量、不构造命令、不写节点/子树。
        rt.bind(rt.__frameTimeNanos(), frame -> {
            int current = epochs.getAsInt();
            if (current == epoch[0]) return;
            epoch[0] = current;
            refresh.run();
        });
        return body;
    }

    /**
     * 仅搬运 L2 已定位命令。COLUMN 的 margin 表达相邻绘制原点差，可为负（背景与文字重叠）。
     * 父盒必须由 ContentLayout 钉高，不取末命令 bottom。段流命令只携带原点，
     * 使用同源度量补齐叶盒供 scene 可见性判断；不重新分配列宽或换行。
     * 仅计划变化时翻译一次，scroll/clip/paint 仍归既有 scene 管线。
     */
    static List<SceneNode> nodes(MarkdownPainter.ContentLayout plan, TextLayoutService measurer) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        int cursor = 0;
        for (PaintCommand command : plan.getCommands()) {
            // 本页与既有 markdown 行同为非交互预览：链接样式保留，命中元数据不装配，
            // 不写 scene paint 引擎拥有的命中缓存。headless 另锁完整 L2 region 几何。
            if (command.getType() == club.heiqi.uilib.ui.scene.paint.PaintCommandType.LINK_REGION) continue;
            SceneNode node = new SceneNode().setHitTestable(false);
            int width = Math.max(1, command.getRight() - command.getLeft());
            int height = Math.max(1, command.getBottom() - command.getTop());
            switch (command.getType()) {
                case BACKGROUND:
                    node.setBackgroundColor(command.getColor()).setCornerRadius(command.getCornerRadius());
                    break;
                case SEGMENTS:
                    node.setSegments(command.getSegments());
                    node.setFontSize(command.getTextStyle().getFontSize());
                    node.setTextVerticalAlign(TextVerticalAlign.TOP);
                    width = Math.max(1, MarkdownPainter.lineWidthPx(command.getSegments(), measurer, node.getFontSize()));
                    height = Math.max(1, MarkdownPainter.lineHeightPx(command.getSegments(), measurer, node.getFontSize()));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported Markdown page command: " + command.getType());
            }
            node.setPreferredWidth(width).setPreferredHeight(height);
            node.setMargin(command.getTop() - cursor, 0, 0, command.getLeft());
            cursor = command.getTop() + height;
            out.add(node);
        }
        return out;
    }
}
