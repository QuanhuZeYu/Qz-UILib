package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * `DocumentSegmentedSelectorControl` 的基础行为契约测试。
 */
public class DocumentSegmentedSelectorControlTest {

    /**
     * 验证鼠标点击会切换选项并触发选择事件。
     */
    @Test
    public void shouldSelectOptionFromMouseClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B", "C")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                });
        Assert.assertEquals("radiogroup", selector.getElement().getAttribute("role"));
        Assert.assertEquals("radio", ((ElementNode) selector.getElement().getChildren().get(0)).getAttribute("role"));
        Assert.assertEquals("true", ((ElementNode) selector.getElement().getChildren().get(0))
                .getAttribute("aria-checked"));
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 80, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 80, 8, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, selector.getSelectedIndex());
        Assert.assertEquals("B", selector.getSelectedOption());
        Assert.assertEquals(1, events.size());
        Assert.assertSame(selector, events.get(0).getSource());
        assertElementUid(selector.getElement(), events.get(0).getElement());
        Assert.assertEquals(1, events.get(0).getSelectedIndex());
        Assert.assertEquals("B", events.get(0).getSelectedOption());
        Assert.assertFalse(events.get(0).isKeyboardTriggered());
        Assert.assertEquals(0, events.get(0).getButton());
        Assert.assertEquals(2L, events.get(0).getTimeNanos());
    }

    /**
     * 验证分段选择器支持 radiogroup 风格的左右方向键切换。
     */
    @Test
    public void shouldSelectOptionWithArrowKeys() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B", "C")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 40);

        widget.onFocusTraversalEntered(false);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RIGHT, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_LEFT, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 4L));

        Assert.assertEquals(0, selector.getSelectedIndex());
        Assert.assertEquals(2, events.size());
        Assert.assertEquals(Keyboard.KEY_RIGHT, events.get(0).getKeyCode());
        Assert.assertEquals("true", ((ElementNode) selector.getElement().getChildren().get(0))
                .getAttribute("aria-checked"));
    }

    /**
     * 验证键盘激活当前焦点选项会触发选择事件。
     */
    @Test
    public void shouldSelectOptionFromKeyboardActivation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B", "C")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 40);

        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));
        Assert.assertEquals(0, selector.getSelectedIndex());
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false, false,
                false, 4L));

        Assert.assertEquals(1, selector.getSelectedIndex());
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isKeyboardTriggered());
        Assert.assertEquals(Keyboard.KEY_SPACE, events.get(0).getKeyCode());
    }

    /**
     * 验证程序化设置选中项不会触发选择事件。
     */
    @Test
    public void shouldNotFireSelectionHandlerForProgrammaticSelection() {
        UiDocument document = UiDocument.create();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                });

        selector.setSelectedIndex(1);

        Assert.assertEquals(1, selector.getSelectedIndex());
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证禁用状态不会响应点击或焦点遍历。
     */
    @Test
    public void shouldSkipSelectionAndFocusWhenDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                })
                .setEnabled(false);
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 80, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 80, 8, 0, 0, 0, 0, 2L));

        Assert.assertEquals(0, selector.getSelectedIndex());
        Assert.assertNull(widget.getFocusedElement());
        Assert.assertTrue(events.isEmpty());
        Assert.assertEquals("true", selector.getElement().getAttribute("disabled"));
    }

    /**
     * 验证选中项和未选中项使用不同背景色。
     */
    @Test
    public void shouldRenderSelectedAndNormalOptionColors() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B");
        selector.setSelectedIndex(1);
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFF2563EB));
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFF334155));
    }

    private static boolean containsFillColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.fillColor == expectedColor) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证方向键切换后焦点与选中态一致：aria-checked、getFocusedElement 指向同一按钮。
     */
    @Test
    public void shouldMoveFocusWithArrowKeySelection() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 40);

        // 初始聚焦第一个选项
        widget.onFocusTraversalEntered(false);
        ElementNode firstButton = (ElementNode) selector.getElement().getChildren().get(0);
        ElementNode secondButton = (ElementNode) selector.getElement().getChildren().get(1);
        assertElementUid(firstButton, widget.getFocusedElement());

        // 按 Right，selectedIndex == 1，焦点移到第二个选项
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RIGHT, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, selector.getSelectedIndex());
        assertElementUid(secondButton, widget.getFocusedElement());
        Assert.assertEquals("true", secondButton.getAttribute("aria-checked"));
        Assert.assertEquals("false", firstButton.getAttribute("aria-checked"));

        // 按 Space，不会切回第一个（Space 触发的是 DocumentButtonControl 的 action，但当前焦点选项已是第二个）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        // Space 触发 DocumentButtonControl 的 action → selectIndex(1, ...)，selectedIndex 不变
        Assert.assertEquals(1, selector.getSelectedIndex());
    }

    /**
     * 验证禁用状态下方向键不改变选中项、不触发事件。
     */
    @Test
    public void shouldNotChangeSelectionWithArrowKeysWhenDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSegmentedSelectionEvent> events = new ArrayList<DocumentSegmentedSelectionEvent>();
        DocumentSegmentedSelectorControl selector = new DocumentSegmentedSelectorControl(document, "A", "B", "C")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        events.add(event);
                    }
                })
                .setEnabled(false);
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        selector.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(selector.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 40);

        // 禁用时 Tab 不聚焦
        widget.onFocusTraversalEntered(false);
        Assert.assertNull(widget.getFocusedElement());

        // 模拟键盘事件（即使发到容器也不改变选中项）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RIGHT, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(0, selector.getSelectedIndex());
        Assert.assertTrue(events.isEmpty());
    }

    private static void assertElementUid(ElementNode expectedElement, ElementNode actualElement) {
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                TextContentMode textContentMode) {}

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(UiSurfaceStyle surfaceStyle) {
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
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
            return text == null || targetWidth <= 0 ? "" : text.substring(0,
                    Math.min(text.length(), targetWidth / 6));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            return Collections.singletonList(text);
        }
    }
}
