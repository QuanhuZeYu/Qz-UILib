package club.heiqi.uilib.internal.devtools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.hud.UiHudDocumentRegistration;
import club.heiqi.uilib.ui.hud.UiHudLayerType;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiBoxSizing;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 内部开发工具使用的 HUD 双层示例控制器。
 */
public final class UiHudDemoController {

    private static final UiHudDemoController INSTANCE = new UiHudDemoController();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private UiHudDocumentRegistration passiveRegistration;
    private UiHudDocumentRegistration interactiveRegistration;
    private TextNode passiveStatusText;
    private TextNode passiveClockText;
    private TextNode interactiveScrollProbeText;
    private TextNode interactiveBodySummaryText;
    private int interactiveClickCount;
    private boolean debugInfoVisible = true;
    private String noteText = "把鼠标移到背包界面后尝试编辑我";
    private ElementNode interactiveScrollContent;
    private ElementNode interactiveDebugSection;

    private UiHudDemoController() {}

    /**
     * 返回 HUD 示例控制器单例。
     *
     * @return HUD 示例控制器
     */
    public static UiHudDemoController getInstance() {
        return INSTANCE;
    }

    /**
     * 切换 HUD 示例开关。
     *
     * @return 当前是否已启用
     */
    public synchronized boolean toggle() {
        if (isEnabled()) {
            disable();
            return false;
        }
        enable();
        return true;
    }

    /**
     * 判断 HUD 示例是否已启用。
     *
     * @return 是否已启用
     */
    public synchronized boolean isEnabled() {
        return passiveRegistration != null || interactiveRegistration != null;
    }

    private void enable() {
        disable();
        interactiveClickCount = 0;
        debugInfoVisible = true;
        noteText = "把鼠标移到背包界面后尝试编辑我";
        passiveRegistration = UiHudDocumentHost.getInstance().register(UiHudLayerType.PASSIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        buildPassiveDocument(document);
                    }
                });
        interactiveRegistration = UiHudDocumentHost.getInstance().register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        buildInteractiveDocument(document);
                    }
                });
        refreshTexts();
    }

    private void disable() {
        if (passiveRegistration != null) {
            passiveRegistration.unregister();
            passiveRegistration = null;
        }
        if (interactiveRegistration != null) {
            interactiveRegistration.unregister();
            interactiveRegistration = null;
        }
        passiveStatusText = null;
        passiveClockText = null;
        interactiveScrollProbeText = null;
        interactiveBodySummaryText = null;
        interactiveScrollContent = null;
        interactiveDebugSection = null;
    }

    private void buildPassiveDocument(UiDocument document) {
        ElementNode root = document.getRootElement();

        ElementNode panel = document.div();
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(10))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(206))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xD0131A28)
                .setBorderColor(0xFF62A4FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFEAF2FF);
        root.append(panel);

        ElementNode title = document.div();
        title.style().setTextColor(0xFF9FD0FF);
        title.appendText("PASSIVE HUD");
        panel.append(title);

        passiveStatusText = panel.appendText("");
        passiveClockText = panel.appendText("");

        ElementNode anchor = document.div();
        anchor.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setBottom(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPadding(UiStyleLength.px(0));
        root.append(anchor);

        ElementNode badge = document.div();
        badge.style()
                .setMargin(UiStyleLength.px(0))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xCC0F1726)
                .setBorderColor(0xFF4C7ED8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFD8E8FF);
        badge.appendText("这条被动层会在背包/箱子/菜单打开时隐藏");
        anchor.append(badge);
    }

    private void buildInteractiveDocument(UiDocument document) {
        ElementNode root = document.getRootElement();

        ElementNode panel = document.div();
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(336))
                .setHeight(UiStyleLength.px(420))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xEE12192A)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setRowGap(UiStyleLength.px(10))
                .setTextColor(0xFFF3F1FF);
        root.append(panel);

        ElementNode dragBar = document.div();
        dragBar.style()
                .setWidth(UiStyleLength.auto())
                .setFlexShrink(0.0F)
                .setMargin(UiStyleLength.px(0))
                .setPadding(UiStyleLength.px(4))
                .setBackgroundColor(0x334C1D95)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFDED7FF);
        dragBar.appendText("HUD 工具浮窗 · 拖住这里移动");
        panel.append(dragBar);
        DocumentDraggableSupport.attach(panel, dragBar, DocumentDraggableSupport.DragAxis.BOTH);

        ElementNode controlCard = document.div();
        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xAA0F172A)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        panel.append(controlCard);

        ElementNode controlTitle = document.div();
        controlTitle.style().setTextColor(0xFF93C5FD);
        controlTitle.appendText("调试开关");
        controlCard.append(controlTitle);

        ElementNode debugToggleCard = document.div();
        debugToggleCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0x88121D33)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(8));
        controlCard.append(debugToggleCard);

        appendInlineTextLine(document, debugToggleCard, "显示 HUD 调试信息");

        DocumentToggleSwitchControl debugToggle = new DocumentToggleSwitchControl(document)
                .setToggled(debugInfoVisible)
                .setTrackColors(0xFF475569, 0xFF2563EB, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        debugInfoVisible = event.isToggled();
                        refreshTexts();
                    }
                });
        ElementNode debugToggleHost = document.div();
        debugToggleHost.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        debugToggleHost.append(debugToggle.getElement());
        debugToggleCard.append(debugToggleHost);

        appendInlineTextLine(document, controlCard, "底部提示标记：保留");

        ElementNode scrollContent = document.div();
        scrollContent.style()
                .setFlexGrow(1.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xCC0B1220)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        panel.append(scrollContent);
        interactiveScrollContent = scrollContent;

        ElementNode contentBody = document.div();
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6))
                .setHeight(UiStyleLength.auto());
        scrollContent.append(contentBody);

        ElementNode overviewCard = document.div();
        overviewCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.auto())
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA182131)
                .setBorderColor(0xFF4C1D95)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(3));
        contentBody.append(overviewCard);

        ElementNode overviewTitle = document.div();
        overviewTitle.style().setTextColor(0xFFC4B5FD);
        overviewTitle.appendText("会话概览");
        overviewCard.append(overviewTitle);
        interactiveBodySummaryText = appendScrollTextLine(document, overviewCard, "");

        DocumentTextInputControl noteInput = new DocumentTextInputControl(document)
                .setPlaceholder("在容器界面中输入备注")
                .setText(noteText)
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        noteText = event.getText();
                        refreshTexts();
                    }
                });
        noteInput.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        ElementNode noteCard = document.div();
        noteCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.auto())
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA14213A)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(4));
        contentBody.append(noteCard);

        ElementNode noteTitle = document.div();
        noteTitle.style().setTextColor(0xFF93C5FD);
        noteTitle.appendText("容器备注");
        noteCard.append(noteTitle);
        noteCard.append(noteInput.getElement());

        DocumentButtonControl button = new DocumentButtonControl(document, "记录一次点击")
                .setBackgroundColors(0xFF7C3AED, 0xFF5B21B6, 0xFF334155)
                .setFocusBorderColor(0xFFD8B4FE)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        interactiveClickCount++;
                        refreshTexts();
                    }
                });
        button.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        noteCard.append(button.getElement());

        interactiveDebugSection = document.div();
        interactiveDebugSection.style()
                .setDisplay(debugInfoVisible ? UiDisplay.FLEX : UiDisplay.NONE)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA101826)
                .setBorderColor(0xFF0EA5E9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(3))
                .setTextColor(0xFFDBEAFE);
        contentBody.append(interactiveDebugSection);

        ElementNode debugTitle = document.div();
        debugTitle.style().setTextColor(0xFF7DD3FC);
        debugTitle.appendText("HUD DEBUG");
        interactiveDebugSection.append(debugTitle);

        interactiveScrollProbeText = appendScrollTextLine(document, interactiveDebugSection, "");

        ElementNode tipsCard = document.div();
        tipsCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.auto())
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA1F2937)
                .setBorderColor(0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(3));
        contentBody.append(tipsCard);

        ElementNode tipsTitle = document.div();
        tipsTitle.style().setTextColor(0xFFE5E7EB);
        tipsTitle.appendText("操作建议");
        tipsCard.append(tipsTitle);

        for (int index = 1; index <= 5; index++) {
            appendTextLine(document, tipsCard, "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。");
        }
    }

    private synchronized void refreshTexts() {
        if (passiveStatusText != null) {
            passiveStatusText.setText("纯 HUD 层：显示在 hotbar/生命值/经验值这一层，上屏但不可交互。");
        }
        if (passiveClockText != null) {
            passiveClockText.setText("最近刷新：" + TIME_FORMAT.format(new Date()));
        }
        if (interactiveScrollProbeText != null) {
            interactiveScrollProbeText.setText(buildScrollProbeText());
        }
        if (interactiveBodySummaryText != null) {
            interactiveBodySummaryText.setText("容器界面上方可见。点击次数 " + interactiveClickCount + "，备注：" + noteText + "。");
        }
        if (interactiveDebugSection != null) {
            interactiveDebugSection.style().setDisplay(debugInfoVisible ? UiDisplay.FLEX : UiDisplay.NONE);
        }
    }

    /**
     * 在 HUD 渲染前刷新调试文本，便于观察滚轮输入与内部滚动状态。
     */
    public synchronized void refreshDiagnosticsBeforeRender() {
        if (!isEnabled()) {
            return;
        }
        refreshTexts();
    }

    private TextNode appendScrollTextLine(UiDocument document, ElementNode parent, String text) {
        return appendTextLine(document, parent, text);
    }

    private TextNode appendInlineTextLine(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        TextNode textNode = line.appendText(text);
        parent.append(line);
        return textNode;
    }

    private TextNode appendTextLine(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        TextNode textNode = line.appendText(text);
        parent.append(line);
        return textNode;
    }

    private String buildScrollProbeText() {
        HtmlLikeDocumentWidget widget = UiHudDocumentHost.getInstance().getFirstInteractiveWidgetForDiagnostics();
        if (widget == null || interactiveScrollContent == null) {
            return "滚轮监控\n阶段: 组件未就绪";
        }
        HtmlLikeDocumentWidget.ScrollInputDiagnosticsSnapshot inputSnapshot =
                widget.getScrollInputDiagnosticsSnapshot();
        int mouseX = UiInputService.getInstance().getMouseX();
        int mouseY = UiInputService.getInstance().getMouseY();
        ElementNode hitElement = widget.findElementAt(mouseX, mouseY);
        boolean hitInsideScrollContent = isSameOrDescendantOf(hitElement, interactiveScrollContent);
        int scrollTop = widget.getScrollTop(interactiveScrollContent);
        int maxScrollTop = widget.getMaxScrollTop(interactiveScrollContent);
        String phase;
        if (inputSnapshot.getEventCount() <= 0) {
            phase = "未收到滚轮";
        } else if (maxScrollTop <= 0) {
            phase = "无滚动范围";
        } else if (!inputSnapshot.isLastConsumed()) {
            phase = "有范围但未命中宿主";
        } else if (scrollTop <= 0) {
            phase = "已消费但偏移未变化";
        } else {
            phase = "滚动生效";
        }
        return "滚轮监控\n阶段: " + phase
                + "\n鼠标: " + mouseX + ", " + mouseY
                + "  命中: " + describeElement(hitElement)
                + "  滚动区: " + (hitInsideScrollContent ? "是" : "否")
                + "\n事件: " + inputSnapshot.getEventCount()
                + "  delta: " + inputSnapshot.getLastWheelDelta()
                + "  消费: " + (inputSnapshot.isLastConsumed() ? "是" : "否")
                + "\n偏移: " + scrollTop + " / " + maxScrollTop;
    }

    private boolean isSameOrDescendantOf(ElementNode element, ElementNode ancestor) {
        if (element == null || ancestor == null) {
            return false;
        }
        for (ElementNode current = element; current != null; current = current.getParent() instanceof ElementNode
                ? (ElementNode) current.getParent()
                : null) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private String describeElement(ElementNode element) {
        if (element == null) {
            return "无";
        }
        return element.getTagName();
    }
}
