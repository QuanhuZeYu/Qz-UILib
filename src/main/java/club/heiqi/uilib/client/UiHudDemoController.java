package club.heiqi.uilib.client;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.hud.UiHudDocumentRegistration;
import club.heiqi.uilib.ui.hud.UiHudLayerType;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * HUD 双层示例控制器。
 */
public final class UiHudDemoController {

    private static final UiHudDemoController INSTANCE = new UiHudDemoController();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private UiHudDocumentRegistration passiveRegistration;
    private UiHudDocumentRegistration interactiveRegistration;
    private TextNode passiveStatusText;
    private TextNode passiveClockText;
    private TextNode interactiveSummaryText;
    private TextNode interactiveSwitchText;
    private int interactiveClickCount;
    private boolean markersEnabled = true;
    private String noteText = "把鼠标移到背包界面后尝试编辑我";

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
        markersEnabled = true;
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
        interactiveSummaryText = null;
        interactiveSwitchText = null;
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
                .setRight(UiStyleLength.px(14))
                .setTop(UiStyleLength.px(14))
                .setWidth(UiStyleLength.px(248))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xE61A2233)
                .setBorderColor(0xFF8A6CFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFF3F1FF);
        root.append(panel);

        ElementNode title = document.div();
        title.style().setTextColor(0xFFC9B6FF);
        title.appendText("INTERACTIVE HUD");
        panel.append(title);

        interactiveSummaryText = panel.appendText("");
        interactiveSwitchText = panel.appendText("");

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
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        panel.append(noteInput.getElement());

        ElementNode toggleRow = document.div();
        toggleRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setMargin(UiStyleLength.px(8));
        panel.append(toggleRow);
        toggleRow.appendText("保留底部提示标记");

        DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document)
                .setToggled(markersEnabled)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        markersEnabled = event.isToggled();
                        refreshTexts();
                    }
                });
        toggleRow.append(toggle.getElement());

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
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleLength.px(0));
        panel.append(button.getElement());
    }

    private synchronized void refreshTexts() {
        if (passiveStatusText != null) {
            passiveStatusText.setText("纯 HUD 层：显示在 hotbar/生命值/经验值这一层，上屏但不可交互。");
        }
        if (passiveClockText != null) {
            passiveClockText.setText("最近刷新：" + TIME_FORMAT.format(new Date()));
        }
        if (interactiveSummaryText != null) {
            interactiveSummaryText.setText("容器界面上方可见。点击次数 " + interactiveClickCount + "，备注：" + noteText + "。");
        }
        if (interactiveSwitchText != null) {
            interactiveSwitchText.setText(markersEnabled
                    ? "底部提示标记：保留"
                    : "底部提示标记：已关闭，仅右上角交互面板保留");
        }
    }
}
