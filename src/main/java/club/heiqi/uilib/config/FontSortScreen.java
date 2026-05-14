package club.heiqi.uilib.config;

import java.util.List;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 字体排序二级配置页面。
 */
final class FontSortScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final FontSortDraftSink draftSink;
    private final HtmlLikeDocumentWidget documentWidget;
    private final FontSortOrderControl orderControl;
    private final TextNode statusText;

    /**
     * 创建字体排序二级页。
     *
     * @param parentScreen 父配置页
     * @param initialOrder 初始字体顺序
     * @param draftSink 草稿回写器
     */
    FontSortScreen(GuiScreen parentScreen, List<String> initialOrder, FontSortDraftSink draftSink) {
        this(parentScreen, initialOrder, draftSink, DefaultTextMeasureService.getInstance());
    }

    /**
     * 使用指定文本测量服务创建字体排序二级页。
     *
     * @param parentScreen 父配置页
     * @param initialOrder 初始字体顺序
     * @param draftSink 草稿回写器
     * @param textMeasureService 文本测量服务
     */
    FontSortScreen(GuiScreen parentScreen, List<String> initialOrder, FontSortDraftSink draftSink,
            TextMeasureService textMeasureService) {
        this.parentScreen = parentScreen;
        this.draftSink = draftSink;

        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        configureRoot(root);
        appendHero(document, root);
        this.statusText = appendToolbar(document, root);

        this.documentWidget = new HtmlLikeDocumentWidget(document, 960, 720,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.documentWidget.setViewportRootScrollingEnabled(true);
        this.documentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        this.orderControl = new FontSortOrderControl(document, documentWidget, initialOrder,
                new FontSortOrderControl.FontSortOrderChangeListener() {
                    @Override
                    public void onOrderChanged(List<String> orderedItems) {
                        updateDraft(orderedItems);
                    }
                });
        root.append(orderControl.getElement());
        refreshStatus(orderControl.getItemsSnapshot());
    }

    @Override
    protected void buildUi(Widget root) {
        root.addChild(documentWidget);
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        setRootPadding(0, 0, 0, 0);
        documentWidget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
    }

    @Override
    public void handleInputFrame(UiInputFrame frame) {
        if (handleShortcuts(frame)) {
            return;
        }
        super.handleInputFrame(frame);
    }

    private void configureRoot(ElementNode root) {
        root.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(22))
                .setBackgroundColor(0xF0080F1C)
                .setTextColor(0xFFE5EEFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
    }

    private void appendHero(UiDocument document, ElementNode parent) {
        ElementNode hero = document.element("header");
        hero.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(0xFFF8FAFC);
        hero.appendText("字体排序");
        ElementNode description = document.div();
        description.style().setTextColor(0xFFD7E4FF).setMargin(UiStyleLength.px(6));
        description.appendText("上方字体优先参与回退匹配。支持拖拽排序，也支持在每行序号输入框中直接输入目标位置。");
        hero.append(description);
        parent.append(hero);
    }

    private TextNode appendToolbar(UiDocument document, ElementNode parent) {
        ElementNode toolbar = document.div();
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleLength.px(14))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xCC111827)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16));

        DocumentButtonControl backButton = createButton(document, "返回配置页");
        backButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                requestBack();
            }
        });
        toolbar.append(backButton.getElement());

        ElementNode status = document.div();
        status.style()
                .setFlexGrow(1.0F)
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        TextNode text = status.appendText("");
        toolbar.append(status);

        parent.append(toolbar);
        return text;
    }

    private DocumentButtonControl createButton(UiDocument document, String label) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFFCBD5E1);
    }

    private boolean handleShortcuts(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            if (keyEvent != null && keyEvent.getAction() == UiKeyEvent.Action.PRESSED
                    && keyEvent.getKeyCode() == Keyboard.KEY_ESCAPE) {
                requestBack();
                return true;
            }
        }
        return false;
    }

    private void updateDraft(List<String> orderedItems) {
        if (draftSink != null) {
            draftSink.onFontSortDraftChanged(orderedItems);
        }
        refreshStatus(orderedItems);
    }

    private void refreshStatus(List<String> orderedItems) {
        if (statusText != null) {
            statusText.setText("未保存草稿会回写到上一级配置页，按 Ctrl+S 保存。当前："
                    + FontSortOrderControl.summarizeItems(orderedItems, 4));
        }
    }

    private void requestBack() {
        final GuiScreen targetScreen = parentScreen;
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null) {
                    minecraft.displayGuiScreen(targetScreen);
                }
            }
        });
    }

    /**
     * 字体排序草稿回写器。
     */
    interface FontSortDraftSink {

        /**
         * 当二级页更新字体顺序草稿时触发。
         *
         * @param orderedItems 最新字体顺序
         */
        void onFontSortDraftChanged(List<String> orderedItems);
    }
}
