package club.heiqi.uilib.ui.input;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiInputRouter` 的输入分发契约测试。
 */
public class UiInputRouterTest {

    /**
     * 验证 Tab 会先交给当前聚焦组件处理内部焦点遍历，未消费时再切换全局组件焦点。
     */
    @Test
    public void shouldLetFocusedWidgetHandleTraversalBeforeMovingGlobalFocus() {
        Widget root = new Widget();
        TraversalWidget firstWidget = new TraversalWidget(true);
        TraversalWidget secondWidget = new TraversalWidget(false);
        root.applyLayoutBounds(0, 0, 120, 40);
        firstWidget.applyLayoutBounds(0, 0, 40, 20);
        secondWidget.applyLayoutBounds(60, 0, 40, 20);
        root.addChild(firstWidget).addChild(secondWidget);
        UiInputRouter router = new UiInputRouter();

        router.route(root, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 5, 5, 0, 0, 0, 0, 1L)));
        router.route(root, keyFrame(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L)));

        Assert.assertTrue(firstWidget.focused);
        Assert.assertFalse(secondWidget.focused);
        Assert.assertEquals(1, firstWidget.traversalCount);

        firstWidget.consumeTraversal = false;
        router.route(root, keyFrame(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 3L)));

        Assert.assertFalse(firstWidget.focused);
        Assert.assertTrue(secondWidget.focused);
        Assert.assertEquals(2, firstWidget.traversalCount);
        Assert.assertEquals(1, secondWidget.traversalEnterCount);
    }

    /**
     * 验证 HTML 文档组件内部元素失焦后，不再继续占用全局焦点状态。
     */
    @Test
    public void shouldTreatHtmlDocumentWidgetAsUnfocusedAfterInnerElementBlurs() {
        Widget root = new Widget();
        root.applyLayoutBounds(0, 0, 160, 60);

        UiDocument document = UiDocument.create();
        document.getRootElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(24));
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(24));
        document.getRootElement().append(textInputControl.getElement());

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 24, new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 24);
        root.addChild(widget);

        UiInputRouter router = new UiInputRouter();
        router.route(root, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L)));
        Assert.assertTrue(router.hasFocusedWidget());

        widget.onFocusChanged(false);

        Assert.assertFalse(router.hasFocusedWidget());
    }

    private static UiInputFrame mouseFrame(UiMouseEvent event) {
        return new UiInputFrame(event.getMouseX(), event.getMouseY(), Collections.singletonList(event),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList());
    }

    private static UiInputFrame keyFrame(UiKeyEvent event) {
        return new UiInputFrame(0, 0, Collections.<UiMouseEvent>emptyList(), Collections.singletonList(event),
                Collections.<UiTextInputEvent>emptyList());
    }

    /**
     * 记录焦点遍历调用的测试组件。
     */
    private static final class TraversalWidget extends Widget {

        private boolean consumeTraversal;
        private boolean focused;
        private int traversalCount;
        private int traversalEnterCount;

        private TraversalWidget(boolean consumeTraversal) {
            this.consumeTraversal = consumeTraversal;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public void onFocusChanged(boolean focused) {
            this.focused = focused;
        }

        @Override
        public void onFocusTraversalEntered(boolean reverse) {
            traversalEnterCount++;
        }

        @Override
        public boolean onFocusTraversal(boolean reverse) {
            traversalCount++;
            return consumeTraversal;
        }
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxChars = Math.max(0, targetWidth / 6);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return java.util.Collections.singletonList(text == null ? "" : trimStringToWidth(text, wrapWidth));
        }
    }
}
