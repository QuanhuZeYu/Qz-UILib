package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementScrollEvent;
import club.heiqi.uilib.ui.dom.DocumentElementScrollHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 固定行高 option 候选列表虚拟化助手。
 */
final class DocumentVirtualizedOptionList {

    private final ElementNode viewportElement;
    private final ElementNode topSpacerElement;
    private final ElementNode bottomSpacerElement;
    private final List<OptionView> optionViews = new ArrayList<OptionView>();
    private final OptionViewInitializer optionViewInitializer;
    private final OptionViewBinder optionViewBinder;
    private final int optionHeight;
    private final int overscanOptions;
    private int maxVisibleOptions;
    private int itemCount;
    private int firstRenderedIndex = -1;
    private int renderedOptionCount = -1;

    /**
     * 创建固定行高虚拟候选列表。
     *
     * @param document 所属 HTML-like 文档
     * @param viewportElement 可滚动候选面板元素
     * @param optionHeight 单行候选高度
     * @param maxVisibleOptions 最大可见候选行数
     * @param overscanOptions 上下额外渲染候选行数
     * @param optionViewInitializer option 节点初始化回调
     * @param optionViewBinder 真实索引绑定回调
     */
    DocumentVirtualizedOptionList(UiDocument document, ElementNode viewportElement, int optionHeight,
            int maxVisibleOptions, int overscanOptions, OptionViewInitializer optionViewInitializer,
            OptionViewBinder optionViewBinder) {
        UiDocument resolvedDocument = Objects.requireNonNull(document, "document");
        this.viewportElement = Objects.requireNonNull(viewportElement, "viewportElement");
        this.optionHeight = Math.max(1, optionHeight);
        this.maxVisibleOptions = Math.max(1, maxVisibleOptions);
        this.overscanOptions = Math.max(0, overscanOptions);
        this.optionViewInitializer = Objects.requireNonNull(optionViewInitializer, "optionViewInitializer");
        this.optionViewBinder = Objects.requireNonNull(optionViewBinder, "optionViewBinder");
        this.topSpacerElement = resolvedDocument.div();
        this.bottomSpacerElement = resolvedDocument.div();
        configureSpacer(topSpacerElement);
        configureSpacer(bottomSpacerElement);
        viewportElement.append(topSpacerElement);
        viewportElement.append(bottomSpacerElement);
        viewportElement.setScrollHandler(new DocumentElementScrollHandler() {
            @Override
            public void onScroll(DocumentElementScrollEvent event) {
                updateRenderedOptionsForScroll(event.getScrollTop());
            }
        });
    }

    /**
     * 更新完整候选数量。
     *
     * @param itemCount 完整候选数量
     * @param resetScroll 是否重置滚动窗口
     */
    void setItemCount(int itemCount, boolean resetScroll) {
        this.itemCount = Math.max(0, itemCount);
        resizeOptionViews();
        firstRenderedIndex = -1;
        renderedOptionCount = -1;
        int scrollTop = resetScroll ? 0 : viewportElement.getScrollTop();
        updateRenderedOptionsForScroll(scrollTop);
        if (resetScroll) {
            viewportElement.scrollTo(viewportElement.getScrollLeft(), 0);
        }
    }

    /**
     * 更新最大可见候选行数。
     *
     * @param maxVisibleOptions 最大可见候选行数
     */
    void setMaxVisibleOptions(int maxVisibleOptions) {
        int resolvedMaxVisibleOptions = Math.max(1, maxVisibleOptions);
        if (this.maxVisibleOptions == resolvedMaxVisibleOptions) {
            return;
        }
        this.maxVisibleOptions = resolvedMaxVisibleOptions;
        resizeOptionViews();
        firstRenderedIndex = -1;
        renderedOptionCount = -1;
        updateRenderedOptionsForScroll(viewportElement.getScrollTop());
    }

    /**
     * 按当前滚动偏移刷新虚拟窗口。
     */
    void refreshForCurrentScroll() {
        updateRenderedOptionsForScroll(viewportElement.getScrollTop());
    }

    /**
     * 将指定完整候选索引滚入可视区域。
     *
     * @param itemIndex 完整候选索引
     */
    void scrollToIndex(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) {
            return;
        }
        int currentScrollTop = viewportElement.getScrollTop();
        int viewportHeight = resolveScrollHeight(maxVisibleOptions);
        int optionTop = resolveScrollHeight(itemIndex);
        int optionBottom = resolveScrollHeight(itemIndex + 1);
        int targetScrollTop = currentScrollTop;
        if (optionTop < currentScrollTop) {
            targetScrollTop = optionTop;
        } else if ((long) optionBottom > (long) currentScrollTop + viewportHeight) {
            targetScrollTop = Math.max(0, optionBottom - viewportHeight);
        }
        updateRenderedOptionsForScroll(targetScrollTop);
        viewportElement.scrollTo(viewportElement.getScrollLeft(), targetScrollTop);
    }

    /**
     * 返回 option 复用节点数量。
     *
     * @return option 复用节点数量
     */
    int getOptionViewCount() {
        return optionViews.size();
    }

    /**
     * 返回指定复用节点视图。
     *
     * @param viewIndex 复用节点索引
     * @return 复用节点视图
     */
    OptionView getOptionView(int viewIndex) {
        return optionViews.get(viewIndex);
    }

    private void resizeOptionViews() {
        int targetViewCount = Math.min(itemCount, resolveOptionViewCapacity());
        while (optionViews.size() > targetViewCount) {
            OptionView optionView = optionViews.remove(optionViews.size() - 1);
            viewportElement.removeChild(optionView.element);
        }
        while (optionViews.size() < targetViewCount) {
            OptionView optionView = createOptionView(viewportElement.getOwnerDocument());
            optionViews.add(optionView);
            viewportElement.insertBefore(optionView.element, bottomSpacerElement);
        }
    }

    private OptionView createOptionView(UiDocument document) {
        ElementNode optionElement = document.option();
        TextNode textNode = optionElement.appendText("");
        OptionView optionView = new OptionView(optionElement, textNode);
        optionViewInitializer.initialize(optionView);
        return optionView;
    }

    private void updateRenderedOptionsForScroll(int scrollTop) {
        if (optionViews.isEmpty()) {
            firstRenderedIndex = 0;
            renderedOptionCount = 0;
            topSpacerElement.style().setHeight(UiStyleLength.px(0));
            bottomSpacerElement.style().setHeight(UiStyleLength.px(0));
            return;
        }
        int firstVisibleIndex = Math.max(0, scrollTop / optionHeight);
        int nextFirstIndex = Math.max(0, firstVisibleIndex - overscanOptions);
        int maxFirstIndex = Math.max(0, itemCount - optionViews.size());
        nextFirstIndex = Math.min(nextFirstIndex, maxFirstIndex);
        int nextRenderedCount = Math.min(optionViews.size(), itemCount - nextFirstIndex);
        if (firstRenderedIndex == nextFirstIndex && renderedOptionCount == nextRenderedCount) {
            return;
        }
        firstRenderedIndex = nextFirstIndex;
        renderedOptionCount = nextRenderedCount;
        topSpacerElement.style().setHeight(UiStyleLength.px(resolveScrollHeight(firstRenderedIndex)));
        bottomSpacerElement.style().setHeight(UiStyleLength.px(resolveScrollHeight(
                itemCount - firstRenderedIndex - renderedOptionCount)));
        for (int viewIndex = 0; viewIndex < optionViews.size(); viewIndex++) {
            OptionView optionView = optionViews.get(viewIndex);
            int itemIndex = firstRenderedIndex + viewIndex;
            optionView.itemIndex = itemIndex;
            optionViewBinder.bind(optionView, itemIndex);
        }
    }

    private void configureSpacer(ElementNode spacerElement) {
        spacerElement.setAttribute("aria-hidden", "true");
        spacerElement.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                return true;
            }
        });
        spacerElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.px(0));
    }

    private int resolveOptionViewCapacity() {
        long capacity = (long) maxVisibleOptions + (long) overscanOptions * 2L + 1L;
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private int resolveScrollHeight(int optionCount) {
        long height = (long) Math.max(0, optionCount) * optionHeight;
        return height > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) height;
    }

    /**
     * option 复用节点初始化回调。
     */
    interface OptionViewInitializer {

        /**
         * 初始化 option 复用节点。
         *
         * @param optionView option 复用节点视图
         */
        void initialize(OptionView optionView);
    }

    /**
     * option 复用节点绑定回调。
     */
    interface OptionViewBinder {

        /**
         * 绑定复用节点对应的完整候选索引。
         *
         * @param optionView option 复用节点视图
         * @param itemIndex 完整候选索引
         */
        void bind(OptionView optionView, int itemIndex);
    }

    /**
     * 单个可复用 option 节点视图。
     */
    static final class OptionView {

        private final ElementNode element;
        private final TextNode text;
        private int itemIndex = -1;

        private OptionView(ElementNode element, TextNode text) {
            this.element = element;
            this.text = text;
        }

        /**
         * 返回 option 元素。
         *
         * @return option 元素
         */
        ElementNode getElement() {
            return element;
        }

        /**
         * 返回 option 文本节点。
         *
         * @return option 文本节点
         */
        TextNode getText() {
            return text;
        }

        /**
         * 返回当前绑定的完整候选索引。
         *
         * @return 完整候选索引
         */
        int getItemIndex() {
            return itemIndex;
        }
    }
}
