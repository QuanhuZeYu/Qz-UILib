package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的标签页控件。
 */
public final class DocumentTabControl {

    private static final java.util.concurrent.atomic.AtomicLong INSTANCE_COUNTER = new java.util.concurrent.atomic.AtomicLong();

    private static long nextInstanceId() {
        return INSTANCE_COUNTER.incrementAndGet();
    }

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode tabBarElement;
    private final ElementNode panelElement;
    private final List<TabEntry> tabs = new ArrayList<TabEntry>();
    private final long controlInstanceId;
    private final String panelId;
    private DocumentTabChangeHandler changeHandler;
    private int activeIndex = -1;
    private int focusedIndex = -1;
    private int focusVisibleIndex = -1;
    private int hoveredIndex = -1;
    private boolean enabled = true;
    private int tabBarBackgroundColor = 0xFF111827;
    private int activeTabBackgroundColor = 0xFF2563EB;
    private int inactiveTabBackgroundColor = 0xFF1F2937;
    private int hoverTabBackgroundColor = 0xFF374151;
    private int disabledTabBackgroundColor = 0xFF334155;
    private int textColor = 0xFFCBD5E1;
    private int activeTextColor = 0xFFFFFFFF;
    private int disabledTextColor = 0xFF64748B;
    private int activeIndicatorColor = 0xFF38BDF8;
    private int focusBorderColor = 0xFFBFDBFE;

    /**
     * 创建标签页控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentTabControl(UiDocument document) {
        this.document = document;
        this.element = document.div();
        this.tabBarElement = document.div();
        this.panelElement = document.div();
        this.controlInstanceId = nextInstanceId();
        this.panelId = "qz-tabpanel-" + controlInstanceId;
        element.append(tabBarElement);
        element.append(panelElement);
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回标签页控件根元素。
     *
     * @return 标签页控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 添加标签页。
     *
     * @param label 标签文本
     * @param builder 内容构建器
     * @return 当前标签页控件
     */
    public DocumentTabControl addTab(String label, DocumentTabContentBuilder builder) {
        ElementNode tabElement = document.div();
        final ElementNode resolvedTabElement = tabElement;
        ElementNode labelElement = document.span();
        TextNode labelText = labelElement.appendText(normalizeLabel(label));
        tabElement.append(labelElement);
        String tabId = "qz-tab-" + controlInstanceId + "-" + tabs.size();
        tabElement.setId(tabId);
        tabElement.setAttribute("role", "tab")
                .setAttribute("tabindex", "0")
                .setAttribute("aria-controls", panelId);
        tabElement.setFocusable(enabled);
        tabElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setCursor(UiCursor.POINTER)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        tabElement.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                int tabIndex = indexOfTab(resolvedTabElement);
                if (tabIndex < 0) {
                    return false;
                }
                focusedIndex = tabIndex;
                selectIndex(tabIndex, true, false);
                return true;
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                int tabIndex = indexOfTab(resolvedTabElement);
                hoveredIndex = event.isHovered() && enabled && tabIndex >= 0 ? tabIndex : -1;
                updateVisualState();
                return false;
            }
        }).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                int tabIndex = indexOfTab(resolvedTabElement);
                if (tabIndex < 0) {
                    return;
                }
                if (event.isFocused()) {
                    focusedIndex = tabIndex;
                    focusVisibleIndex = event.isFocusVisible() && enabled ? tabIndex : -1;
                } else if (focusVisibleIndex == tabIndex) {
                    focusVisibleIndex = -1;
                }
                updateVisualState();
            }
        });
        tabs.add(new TabEntry(labelText.getText(), builder, tabElement, labelElement));
        tabBarElement.append(tabElement);
        if (focusedIndex < 0) {
            focusedIndex = 0;
        }
        updateVisualState();
        return this;
    }

    /**
     * 移除指定标签页。
     *
     * @param index 标签索引
     * @return 当前标签页控件
     */
    public DocumentTabControl removeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return this;
        }
        TabEntry removed = tabs.remove(index);
        tabBarElement.removeChild(removed.tabElement);
        if (removed.cachedContent != null && removed.cachedContent.getParent() == panelElement) {
            panelElement.removeChild(removed.cachedContent);
        }
        if (tabs.isEmpty()) {
            activeIndex = -1;
            focusedIndex = -1;
            focusVisibleIndex = -1;
            hoveredIndex = -1;
            panelElement.clearChildren();
        } else {
            if (activeIndex == index) {
                activeIndex = Math.min(index, tabs.size() - 1);
                mountActiveTab();
            } else if (activeIndex > index) {
                activeIndex--;
            }
            focusedIndex = Math.max(0, Math.min(focusedIndex, tabs.size() - 1));
            focusVisibleIndex = -1;
            hoveredIndex = -1;
        }
        updateVisualState();
        return this;
    }

    /**
     * 清空全部标签页。
     *
     * @return 当前标签页控件
     */
    public DocumentTabControl clearTabs() {
        tabs.clear();
        tabBarElement.clearChildren();
        panelElement.clearChildren();
        activeIndex = -1;
        focusedIndex = -1;
        focusVisibleIndex = -1;
        hoveredIndex = -1;
        updateVisualState();
        return this;
    }

    /**
     * 设置活动标签索引，默认不触发切换事件。
     *
     * @param activeIndex 活动标签索引
     * @return 当前标签页控件
     */
    public DocumentTabControl setActiveIndex(int activeIndex) {
        return setActiveIndex(activeIndex, false);
    }

    /**
     * 设置活动标签索引。
     *
     * @param activeIndex 活动标签索引
     * @param notify 是否触发切换事件
     * @return 当前标签页控件
     */
    public DocumentTabControl setActiveIndex(int activeIndex, boolean notify) {
        selectIndex(activeIndex, notify, false);
        return this;
    }

    /**
     * 返回活动标签索引。
     *
     * @return 活动标签索引；无标签时为 -1
     */
    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * 返回标签数量。
     *
     * @return 标签数量
     */
    public int getTabCount() {
        return tabs.size();
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前标签页控件
     */
    public DocumentTabControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focusVisibleIndex = -1;
            hoveredIndex = -1;
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("aria-disabled");
        }
        for (TabEntry tab : tabs) {
            tab.tabElement.setFocusable(enabled);
        }
        updateVisualState();
        return this;
    }

    /**
     * 判断控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置标签页切换处理器。
     *
     * @param changeHandler 切换处理器；为 null 时清除
     * @return 当前标签页控件
     */
    public DocumentTabControl setChangeHandler(DocumentTabChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置标签栏与标签背景色。
     *
     * @param background 标签栏背景色
     * @param active 活动标签背景色
     * @param inactive 非活动标签背景色
     * @param hover 悬停标签背景色
     * @param disabled 禁用标签背景色
     * @return 当前标签页控件
     */
    public DocumentTabControl setTabBarColors(int background, int active, int inactive, int hover, int disabled) {
        this.tabBarBackgroundColor = background;
        this.activeTabBackgroundColor = active;
        this.inactiveTabBackgroundColor = inactive;
        this.hoverTabBackgroundColor = hover;
        this.disabledTabBackgroundColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置标签文本颜色。
     *
     * @param normal 普通文本颜色
     * @param active 活动标签文本颜色
     * @param disabled 禁用文本颜色
     * @return 当前标签页控件
     */
    public DocumentTabControl setTabTextColors(int normal, int active, int disabled) {
        this.textColor = normal;
        this.activeTextColor = active;
        this.disabledTextColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置活动标签指示色。
     *
     * @param activeIndicatorColor 活动标签指示色
     * @return 当前标签页控件
     */
    public DocumentTabControl setActiveIndicatorColor(int activeIndicatorColor) {
        this.activeIndicatorColor = activeIndicatorColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前标签页控件
     */
    public DocumentTabControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 强制重新构建指定标签页内容缓存。
     *
     * @param index 标签索引
     * @return 当前标签页控件
     */
    public DocumentTabControl rebuildTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return this;
        }
        tabs.get(index).cachedContent = null;
        if (index == activeIndex) {
            mountActiveTab();
        }
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "tablist-container");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
        tabBarElement.setAttribute("role", "tablist");
        tabBarElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(6))
                .setBorderRadius(UiStyleLength.px(10))
                .setBackgroundColor(tabBarBackgroundColor);
        panelElement.setId(panelId);
        panelElement.setAttribute("role", "tabpanel");
        panelElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setBorderColor(activeIndicatorColor)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.VISIBLE);
    }

    private void installHandlers() {
        tabBarElement.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!enabled || event.getAction() != UiKeyEvent.Action.PRESSED || tabs.isEmpty()) {
                    return false;
                }
                int keyCode = event.getKeyCode();
                if (keyCode == Keyboard.KEY_LEFT) {
                    moveFocus(-1, event);
                    return true;
                }
                if (keyCode == Keyboard.KEY_RIGHT) {
                    moveFocus(1, event);
                    return true;
                }
                if (keyCode == Keyboard.KEY_HOME) {
                    focusAndActivate(0, event);
                    return true;
                }
                if (keyCode == Keyboard.KEY_END) {
                    focusAndActivate(tabs.size() - 1, event);
                    return true;
                }
                if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN
                        || keyCode == Keyboard.KEY_NUMPADENTER) {
                    selectIndex(focusedIndex < 0 ? 0 : focusedIndex, true, true);
                    return true;
                }
                return false;
            }
        });
    }

    private void moveFocus(int delta, DocumentElementKeyEvent event) {
        int baseIndex = focusedIndex >= 0 ? focusedIndex : Math.max(0, activeIndex);
        int nextIndex = Math.max(0, Math.min(baseIndex + delta, tabs.size() - 1));
        focusAndActivate(nextIndex, event);
    }

    private void focusAndActivate(int index, DocumentElementKeyEvent event) {
        focusedIndex = index;
        focusVisibleIndex = index;
        event.requestFocus(tabs.get(index).tabElement, true);
        selectIndex(index, true, true);
    }

    private void selectIndex(int requestedIndex, boolean notify, boolean keyboardTriggered) {
        if (tabs.isEmpty()) {
            activeIndex = -1;
            panelElement.clearChildren();
            updateVisualState();
            return;
        }
        int nextIndex = Math.max(0, Math.min(requestedIndex, tabs.size() - 1));
        if (activeIndex == nextIndex) {
            updateVisualState();
            return;
        }
        activeIndex = nextIndex;
        focusedIndex = nextIndex;
        mountActiveTab();
        updateVisualState();
        if (notify && changeHandler != null) {
            changeHandler.onTabChanged(new DocumentTabChangeEvent(this, element, activeIndex,
                    tabs.get(activeIndex).label, keyboardTriggered));
        }
    }

    private void mountActiveTab() {
        panelElement.clearChildren();
        if (activeIndex < 0 || activeIndex >= tabs.size()) {
            panelElement.removeAttribute("aria-labelledby");
            return;
        }
        panelElement.setAttribute("aria-labelledby", tabs.get(activeIndex).tabElement.getId());
        panelElement.append(ensureContent(activeIndex));
    }

    private ElementNode ensureContent(int index) {
        TabEntry entry = tabs.get(index);
        if (entry.cachedContent == null) {
            ElementNode content = document.div();
            if (entry.builder != null) {
                entry.builder.build(content, document);
            }
            entry.cachedContent = content;
        }
        return entry.cachedContent;
    }

    private void updateVisualState() {
        tabBarElement.style().setBackgroundColor(tabBarBackgroundColor);
        for (int index = 0; index < tabs.size(); index++) {
            TabEntry entry = tabs.get(index);
            boolean active = index == activeIndex;
            int backgroundColor;
            if (!enabled) {
                backgroundColor = disabledTabBackgroundColor;
            } else if (active) {
                backgroundColor = activeTabBackgroundColor;
            } else if (index == hoveredIndex) {
                backgroundColor = hoverTabBackgroundColor;
            } else {
                backgroundColor = inactiveTabBackgroundColor;
            }
            entry.tabElement.setAttribute("aria-selected", String.valueOf(active));
            entry.tabElement.style()
                    .setBackgroundColor(backgroundColor)
                    .setBorderColor(index == focusVisibleIndex ? focusBorderColor
                            : (active ? activeIndicatorColor : backgroundColor))
                    .setTextColor(enabled ? (active ? activeTextColor : textColor) : disabledTextColor)
                    .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED);
            entry.labelElement.style().setTextColor(enabled ? (active ? activeTextColor : textColor)
                    : disabledTextColor);
        }
    }

    private static String normalizeLabel(String label) {
        return label == null ? "" : label;
    }

    private int indexOfTab(ElementNode tabElement) {
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).tabElement == tabElement) {
                return index;
            }
        }
        return -1;
    }

    private static final class TabEntry {

        private final String label;
        private final DocumentTabContentBuilder builder;
        private final ElementNode tabElement;
        private final ElementNode labelElement;
        private ElementNode cachedContent;

        private TabEntry(String label, DocumentTabContentBuilder builder, ElementNode tabElement,
                ElementNode labelElement) {
            this.label = label == null ? "" : label;
            this.builder = builder;
            this.tabElement = tabElement;
            this.labelElement = labelElement;
        }
    }
}
