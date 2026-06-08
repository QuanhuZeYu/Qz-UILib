package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusOutEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusOutHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 的焦点与键盘语义回归测试。
 */
public class HtmlLikeDocumentWidgetFocusKeyboardTest {

    /**
     * 验证 HTML-like 组件会聚焦命中元素并向其分发文本与键盘事件。
     */
    @Test
    public void shouldFocusHitElementAndDispatchTextAndKeyEvents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode input = document.div();
        final List<DocumentElementFocusEvent> focusEvents = new ArrayList<DocumentElementFocusEvent>();
        final List<DocumentElementTextInputEvent> textEvents = new ArrayList<DocumentElementTextInputEvent>();
        final List<DocumentElementKeyEvent> keyEvents = new ArrayList<DocumentElementKeyEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setFocusable(true)
                .setFocusHandler(new DocumentElementFocusHandler() {
                    @Override
                    public void onFocusChanged(DocumentElementFocusEvent event) {
                        focusEvents.add(event);
                    }
                })
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        textEvents.add(event);
                        return true;
                    }
                })
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        keyEvents.add(event);
                        return true;
                    }
                });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        Assert.assertTrue(widget.isFocusable());
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onTextInput(new UiTextInputEvent("abc", 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        assertElementUid(input, widget.getFocusedElement());
        Assert.assertEquals(1, focusEvents.size());
        Assert.assertTrue(focusEvents.get(0).isFocused());
        Assert.assertFalse(focusEvents.get(0).isFocusVisible());
        Assert.assertEquals(1, textEvents.size());
        assertElementUid(input, textEvents.get(0).getTarget());
        assertElementUid(input, textEvents.get(0).getCurrentTarget());
        Assert.assertEquals("abc", textEvents.get(0).getText());
        Assert.assertEquals(1, keyEvents.size());
        assertElementUid(input, keyEvents.get(0).getTarget());
        assertElementUid(input, keyEvents.get(0).getCurrentTarget());
        Assert.assertEquals(Keyboard.KEY_BACK, keyEvents.get(0).getKeyCode());
    }

    /**
     * 文本输入事件应按 capture -> target -> bubble 顺序分发。
     */
    @Test
    public void shouldDispatchTextInputInDomOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> events = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("root-capture:" + event.getEventPhase());
                return false;
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("root-bubble:" + event.getEventPhase());
                return false;
            }
        });
        child.setFocusable(true)
                .setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        events.add("child-capture:" + event.getEventPhase());
                        return false;
                    }
                })
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        events.add("child:" + event.getEventPhase() + ":" + event.getText());
                        return false;
                    }
                });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onTextInput(new UiTextInputEvent("x", 2L));

        Assert.assertEquals("[root-capture:CAPTURING, child-capture:AT_TARGET, child:AT_TARGET:x, "
                + "root-bubble:BUBBLING]", events.toString());
    }

    /**
     * 验证祖先 capture 阶段 preventDefault 后，内置 input 默认改值不会执行。
     */
    @Test
    public void shouldPreventDefaultTextInputValueChangeFromAncestorCapture() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl inputControl = new DocumentTextInputControl(document);
        final List<String> events = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(40));
        root.setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("capture");
                event.preventDefault();
                return false;
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("bubble");
                return false;
            }
        });
        inputControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(24));
        root.append(inputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.onFocusTraversalEntered(false);
        widget.onTextInput(new UiTextInputEvent("x", 1L));

        Assert.assertEquals("", inputControl.getText());
        Assert.assertEquals("[capture, bubble]", events.toString());
    }

    /**
     * 验证祖先 capture 阶段 preventDefault 后，内置 textarea 默认改值不会执行。
     */
    @Test
    public void shouldPreventDefaultTextAreaValueChangeFromAncestorCapture() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(80));
        root.setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                event.preventDefault();
                return false;
            }
        });
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(140))
                .setHeight(UiStyleLength.px(54));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 80);

        widget.onFocusTraversalEntered(false);
        widget.onTextInput(new UiTextInputEvent("blocked", 1L));

        Assert.assertEquals("", textAreaControl.getText());
    }

    /**
     * 验证 HTML-like 组件失去 widget 焦点时会清空内部元素焦点。
     */
    @Test
    public void shouldClearFocusedElementWhenWidgetLosesFocus() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode input = document.div();
        final List<Boolean> focusEvents = new ArrayList<Boolean>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setFocusable(true).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusEvents.add(Boolean.valueOf(event.isFocused()));
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onFocusChanged(false);

        Assert.assertNull(widget.getFocusedElement());
        Assert.assertEquals(Boolean.TRUE, focusEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, focusEvents.get(1));
    }

    /**
     * 验证 ElementNode 公开 focus/blur API 会复用当前 HTML-like 焦点运行态。
     */
    @Test
    public void shouldFocusAndBlurThroughElementNodeApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode input = document.div();
        final List<DocumentElementFocusEvent> focusEvents = new ArrayList<DocumentElementFocusEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setFocusable(true).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusEvents.add(event);
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(input.focus());
        assertElementUid(input, widget.getFocusedElement());
        Assert.assertEquals(1, focusEvents.size());
        Assert.assertTrue(focusEvents.get(0).isFocused());
        Assert.assertFalse(focusEvents.get(0).isFocusVisible());

        Assert.assertTrue(input.blur());
        Assert.assertNull(widget.getFocusedElement());
        Assert.assertEquals(2, focusEvents.size());
        Assert.assertFalse(focusEvents.get(1).isFocused());
    }

    /**
     * 验证焦点从一个元素切到另一个元素时按浏览器 focusout/focusin/blur/focus 顺序分发。
     */
    @Test
    public void shouldDispatchFocusTransitionInBrowserOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstInput = document.div();
        ElementNode secondInput = document.div();
        final List<String> events = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        firstInput.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        secondInput.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        firstInput.setFocusable(true)
                .setFocusOutHandler(new DocumentElementFocusOutHandler() {
                    @Override
                    public boolean onFocusOut(DocumentElementFocusOutEvent event) {
                        events.add("focusout:first");
                        return false;
                    }
                })
                .setFocusHandler(new DocumentElementFocusHandler() {
                    @Override
                    public void onFocusChanged(DocumentElementFocusEvent event) {
                        events.add((event.isFocused() ? "focus" : "blur") + ":first");
                    }
                });
        secondInput.setFocusable(true)
                .setFocusInHandler(new DocumentElementFocusInHandler() {
                    @Override
                    public boolean onFocusIn(DocumentElementFocusInEvent event) {
                        events.add("focusin:second");
                        return false;
                    }
                })
                .setFocusHandler(new DocumentElementFocusHandler() {
                    @Override
                    public void onFocusChanged(DocumentElementFocusEvent event) {
                        events.add((event.isFocused() ? "focus" : "blur") + ":second");
                    }
                });
        root.append(firstInput).append(secondInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(firstInput.focus());
        events.clear();
        Assert.assertTrue(secondInput.focus());

        Assert.assertEquals("[focusout:first, focusin:second, blur:first, focus:second]", events.toString());
    }

    /**
     * 验证 ElementNode focus API 对未挂载、不可聚焦、隐藏和 disabled 节点保持无副作用。
     */
    @Test
    public void shouldIgnoreInvalidProgrammaticFocusTargets() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode valid = document.div();
        ElementNode notFocusable = document.div();
        ElementNode hidden = document.div();
        ElementNode detached = document.div();
        ElementNode disabledButton = document.button().setAttribute("disabled", "true");
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        valid.style().setWidth(UiStyleLength.px(20)).setHeight(UiStyleLength.px(20));
        notFocusable.style().setWidth(UiStyleLength.px(20)).setHeight(UiStyleLength.px(20));
        hidden.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setVisibility(UiVisibility.HIDDEN);
        detached.style().setWidth(UiStyleLength.px(20)).setHeight(UiStyleLength.px(20));
        disabledButton.style().setWidth(UiStyleLength.px(20)).setHeight(UiStyleLength.px(20));
        valid.setFocusable(true);
        hidden.setFocusable(true);
        detached.setFocusable(true);
        root.append(valid).append(notFocusable).append(hidden).append(disabledButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        Assert.assertTrue(valid.focus());
        assertElementUid(valid, widget.getFocusedElement());
        Assert.assertFalse(notFocusable.focus());
        Assert.assertFalse(hidden.focus());
        Assert.assertFalse(detached.focus());
        Assert.assertFalse(disabledButton.focus());
        assertElementUid(valid, widget.getFocusedElement());
    }

    /**
     * 验证 HTML-like 组件会按 tabindex 与文档顺序处理内部 Tab 焦点遍历。
     */
    @Test
    public void shouldTraverseFocusableElementsInLayoutOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstInput = document.div();
        ElementNode secondInput = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        firstInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        secondInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        firstInput.setFocusable(true);
        secondInput.setFocusable(true);
        root.append(firstInput).append(secondInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(firstInput, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(false));
        assertElementUid(secondInput, widget.getFocusedElement());
        Assert.assertFalse(widget.onFocusTraversal(false));
        assertElementUid(secondInput, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(true));
        assertElementUid(firstInput, widget.getFocusedElement());
        widget.onFocusChanged(false);
        widget.onFocusTraversalEntered(true);
        assertElementUid(secondInput, widget.getFocusedElement());
    }

    /**
     * 验证 tabindex 运行时语义：正数优先，0 保持文档顺序，-1 跳过 Tab 但可鼠标聚焦。
     */
    @Test
    public void shouldRespectTabIndexDuringFocusTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode normal = document.div();
        ElementNode skipped = document.div();
        ElementNode priority = document.div();
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        normal.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        skipped.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        priority.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        normal.setFocusable(true).setAttribute("tabindex", "0");
        skipped.setFocusable(true).setAttribute("tabindex", "-1");
        priority.setFocusable(true).setAttribute("tabindex", "2");
        root.append(normal).append(skipped).append(priority);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.onFocusTraversalEntered(false);
        assertElementUid(priority, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(false));
        assertElementUid(normal, widget.getFocusedElement());
        Assert.assertFalse(widget.onFocusTraversal(false));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 24, 0, 0, 0, 0, 1L));
        assertElementUid(skipped, widget.getFocusedElement());
    }

    /**
     * 验证 Tab 切换到滚动区外的焦点元素时会自动滚动到可视区域。
     */
    @Test
    public void shouldScrollFocusedElementIntoViewDuringTabTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstInput = document.div();
        ElementNode spacer = document.div();
        ElementNode secondInput = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        firstInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        spacer.style().setHeight(UiStyleLength.px(48));
        secondInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        firstInput.setFocusable(true);
        secondInput.setFocusable(true);
        root.append(firstInput).append(spacer).append(secondInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onFocusTraversalEntered(false);
        Assert.assertEquals(0, widget.getScrollTop(root));

        Assert.assertTrue(widget.onFocusTraversal(false));

        assertElementUid(secondInput, widget.getFocusedElement());
        Assert.assertEquals(48, widget.getScrollTop(root));
    }

    /**
     * 验证 raw button 设置 disabled 属性后，Tab 不聚焦，鼠标不聚焦，移除 disabled 后可重新聚焦。
     */
    @Test
    public void shouldIgnoreRawDisabledButtonInFocusTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        rawButton.setAttribute("disabled", "true");
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 不聚焦 disabled button
        widget.onFocusTraversalEntered(false);
        Assert.assertNull(widget.getFocusedElement());

        // 鼠标点击也不聚焦 disabled button
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        Assert.assertNull(widget.getFocusedElement());
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 4, 4, 0, 0, 0, 0, 2L));

        // 移除 disabled 后可重新聚焦
        rawButton.removeAttribute("disabled");
        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());
    }

    /**
     * 验证 disabled="false" 仍按 HTML 布尔属性语义禁用 raw button。
     */
    @Test
    public void shouldTreatDisabledFalseRawButtonAsDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        rawButton.setAttribute("disabled", "false");
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);

        Assert.assertNull(widget.getFocusedElement());
    }

    /**
     * 验证 raw input 设置 disabled 属性后，Tab 不聚焦，textInput 不响应。
     */
    @Test
    public void shouldIgnoreRawDisabledInputInFocusAndTextInput() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawInput = document.input();
        final List<DocumentElementTextInputEvent> textEvents = new ArrayList<DocumentElementTextInputEvent>();
        rawInput.setAttribute("disabled", "true")
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        textEvents.add(event);
                        return true;
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawInput.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 不聚焦 disabled input
        widget.onFocusTraversalEntered(false);
        Assert.assertNull(widget.getFocusedElement());

        // 即使程序化聚焦后，textInput 也不响应（disabled 拦截）
        rawInput.setFocusable(true);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        // disabled 阻止鼠标聚焦
        Assert.assertNull(widget.getFocusedElement());
        widget.onTextInput(new UiTextInputEvent("abc", 2L));
        Assert.assertTrue(textEvents.isEmpty());
    }

    /**
     * 验证 raw button 绑定 click handler 后，Tab 聚焦，Enter 触发 click，Space pressed 不触发，Space released 触发。
     */
    @Test
    public void shouldFireClickOnRawButtonFromKeyboard() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 聚焦
        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        // Enter 触发 click
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, clicks.size());

        // Space pressed 不触发
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        Assert.assertEquals(1, clicks.size());

        // Space released 触发
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(2, clicks.size());
    }

    /**
     * 验证 raw button 的 key handler 返回 true 只会停止传播，不会隐式取消默认 keyboard click。
     */
    @Test
    public void shouldKeepRawButtonDefaultKeyboardClickWhenKeyHandlerStopsPropagation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        rawButton.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, clicks.size());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        Assert.assertEquals(1, clicks.size());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(2, clicks.size());
    }

    /**
     * 验证 raw button 的默认键盘激活会走完整 click 分发链并向祖先冒泡。
     */
    @Test
    public void shouldBubbleRawButtonKeyboardClickThroughDocumentClickPipeline() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return false;
            }
        });
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        rawButton.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));

        Assert.assertEquals(2, clickEvents.size());
        assertElementUid(rawButton, clickEvents.get(0).getTarget());
        assertElementUid(rawButton, clickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(-1, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(-1, clickEvents.get(0).getDocumentY());
        Assert.assertEquals(0, clickEvents.get(0).getButton());
        Assert.assertEquals(1L, clickEvents.get(0).getTimeNanos());
        assertElementUid(rawButton, clickEvents.get(1).getTarget());
        assertElementUid(root, clickEvents.get(1).getCurrentTarget());
        Assert.assertEquals(0, clickEvents.get(1).getButton());
        Assert.assertEquals(1L, clickEvents.get(1).getTimeNanos());
    }

    /**
     * 验证 raw button 的 key handler 可通过 preventDefault 取消 Enter/Space 默认 click。
     */
    @Test
    public void shouldPreventRawButtonDefaultKeyboardClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        final DocumentElementKeyHandler preventDefaultKeyHandler = new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                event.preventDefault();
                return false;
            }
        };
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        rawButton.setKeyHandler(preventDefaultKeyHandler);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(0, clicks.size());

        rawButton.setKeyHandler(null);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        rawButton.setKeyHandler(preventDefaultKeyHandler);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(0, clicks.size());

        rawButton.setKeyHandler(null);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 4L));
        Assert.assertEquals(0, clicks.size());
    }

    /**
     * 验证 raw button Space pressed 后失焦，released 不触发 click。
     */
    @Test
    public void shouldNotFireClickOnRawButtonSpaceReleaseAfterFocusLost() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        // Space pressed
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(0, clicks.size());

        // 失焦
        widget.onFocusChanged(false);
        Assert.assertNull(widget.getFocusedElement());

        // Space released 不触发（焦点已丢失，spacePressed 已清理）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 2L));
        Assert.assertEquals(0, clicks.size());
    }

    /**
     * 验证 DocumentButtonControl 的键盘激活不会被 raw button 默认行为重复触发。
     */
    @Test
    public void shouldNotDuplicateClickOnDocumentButtonControlFromDefaultKeyBehavior() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentButtonActionEvent> actions = new ArrayList<DocumentButtonActionEvent>();
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "OK");
        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                actions.add(event);
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        buttonControl.getElement().style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(buttonControl.getElement(), widget.getFocusedElement());

        // Enter 触发一次（由 DocumentButtonControl 的 key handler 消费，不走默认行为）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, actions.size());

        // Space pressed + released 触发一次
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(2, actions.size());
    }
}
