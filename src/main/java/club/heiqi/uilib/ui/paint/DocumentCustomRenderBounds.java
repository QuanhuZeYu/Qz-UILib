package club.heiqi.uilib.ui.paint;

import java.util.IdentityHashMap;
import java.util.Map;

import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxLocation;

/**
 * 绘制命令构建期固化的自定义渲染器边界快照。
 *
 * <p>自定义渲染器（文本控件选区/光标/行号层）在绘制命令回放期需要视口/内容/图层的文档坐标边界与滚动偏移。
 * 旧实现每帧十余次调 {@code element.getDocumentBounds()} / {@code element.getScrollLeft()}，每次都经
 * {@code resolveLayoutBoxForBoundsQuery()} 全树遍历动画时间线推进运行态，构成稳态帧时残留热点。</p>
 *
 * <p>本快照在 {@link DocumentPaintEngine#buildPaintCommands} 构建期一次性持有当趟视觉场景的
 * {@code 元素 -> BoxLocation} 身份索引与当帧滚动态，按需把任意元素解析为文档坐标 {@link DocumentElementBounds}
 * （与 {@code HtmlLikeDocumentWidget.requestElementBounds} 同口径），并做 O(1) 备忘。绘制命令在动画期逐帧、
 * 滚动时按版本重建，故构建期固化的文档坐标边界与回放期实时查询等价，且回放期不再推进动画时间线。</p>
 *
 * <p>单趟绘制重放在渲染线程串行执行，构建与回放之间索引/滚动态不变，故备忘缓存无并发问题。</p>
 */
public final class DocumentCustomRenderBounds {

    private final Map<ElementNode, BoxLocation> boxLocationIndex;
    private final DocumentScrollState scrollState;
    private final Map<ElementNode, DocumentElementBounds> resolvedBoundsMemo =
            new IdentityHashMap<ElementNode, DocumentElementBounds>();

    DocumentCustomRenderBounds(Map<ElementNode, BoxLocation> boxLocationIndex, DocumentScrollState scrollState) {
        this.boxLocationIndex = boxLocationIndex;
        this.scrollState = scrollState;
    }

    /**
     * 解析元素在文档局部坐标系下的布局边界。
     *
     * @param element 目标元素；为 null 或不在当前视觉场景时返回不可用边界
     * @return 文档坐标布局边界
     */
    public DocumentElementBounds boundsOf(ElementNode element) {
        if (element == null) {
            return DocumentElementBounds.unavailable();
        }
        DocumentElementBounds memo = resolvedBoundsMemo.get(element);
        if (memo != null) {
            return memo;
        }
        DocumentElementBounds resolved = resolveBounds(element);
        resolvedBoundsMemo.put(element, resolved);
        return resolved;
    }

    /**
     * 返回元素当前横向滚动偏移。
     *
     * @param element 目标元素
     * @return 横向滚动偏移；无滚动态时返回 0
     */
    public int scrollLeftOf(ElementNode element) {
        return scrollState == null || element == null ? 0 : scrollState.getScrollLeft(element);
    }

    /**
     * 返回元素当前纵向滚动偏移。
     *
     * @param element 目标元素
     * @return 纵向滚动偏移；无滚动态时返回 0
     */
    public int scrollTopOf(ElementNode element) {
        return scrollState == null || element == null ? 0 : scrollState.getScrollTop(element);
    }

    private DocumentElementBounds resolveBounds(ElementNode element) {
        BoxLocation location = boxLocationIndex.get(element);
        if (location == null) {
            return DocumentElementBounds.unavailable();
        }
        // 与 HtmlLikeDocumentWidget.requestElementBounds 同口径：box 局部坐标 + boxOffset = 文档坐标。
        BoxContext boxContext = location.getBoxContext();
        DocumentLayoutBox box = boxContext.getBox();
        int offsetX = boxContext.getBoxOffsetX();
        int offsetY = boxContext.getBoxOffsetY();
        return DocumentElementBounds.of(box.getLeft() + offsetX, box.getTop() + offsetY, box.getWidth(),
                box.getHeight(), box.getContentLeft() + offsetX, box.getContentTop() + offsetY,
                box.getContentWidth(), box.getContentHeight());
    }
}
