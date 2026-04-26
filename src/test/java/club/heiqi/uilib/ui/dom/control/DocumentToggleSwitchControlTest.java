package club.heiqi.uilib.ui.dom.control;

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
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `DocumentToggleSwitchControl` 的基础行为契约测试。
 */
public class DocumentToggleSwitchControlTest {

    /**
     * 验证点击切换开关状态并触发变更处理器。
     */
    @Test
    public void shouldToggleStateOnClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        toggleControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(24));
        final List<Boolean> toggleStates = new ArrayList<Boolean>();
        toggleControl.setToggled(false)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        toggleStates.add(event.isToggled());
                    }
                });
        root.append(toggleControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 4L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 5L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 6L));

        Assert.assertTrue(toggleControl.isToggled());
        Assert.assertEquals(3, toggleStates.size());
        Assert.assertTrue(toggleStates.get(0));
        Assert.assertFalse(toggleStates.get(1));
        Assert.assertTrue(toggleStates.get(2));
    }

    /**
     * 验证键盘 Enter/Space 切换开关状态。
     */
    @Test
    public void shouldToggleStateOnKeyboardActivation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        toggleControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(24));
        final List<Boolean> toggleStates = new ArrayList<Boolean>();
        toggleControl.setToggled(false)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        toggleStates.add(event.isToggled());
                    }
                });
        root.append(toggleControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));

        Assert.assertFalse(toggleControl.isToggled());
        Assert.assertEquals(2, toggleStates.size());
        Assert.assertTrue(toggleStates.get(0));
        Assert.assertFalse(toggleStates.get(1));
    }

    /**
     * 验证禁用开关不响应鼠标点击或键盘操作。
     */
    @Test
    public void shouldSkipToggleWhenDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        final List<Boolean> toggleStates = new ArrayList<Boolean>();
        toggleControl.setEnabled(false)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        toggleStates.add(event.isToggled());
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        toggleControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(24));
        root.append(toggleControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 2L));
        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertFalse(toggleControl.isToggled());
        Assert.assertTrue(toggleStates.isEmpty());
        Assert.assertFalse(toggleControl.getElement().isFocusable());
    }

    /**
     * 验证程序化 setToggled 不触发变更处理器。
     */
    @Test
    public void shouldNotFireChangeHandlerOnProgrammaticSetToggled() {
        UiDocument document = UiDocument.create();
        DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        final List<Boolean> toggleStates = new ArrayList<Boolean>();
        toggleControl.setChangeHandler(new DocumentToggleChangeHandler() {
            @Override
            public void onToggleChanged(DocumentToggleChangeEvent event) {
                toggleStates.add(event.isToggled());
            }
        });

        toggleControl.setToggled(true);
        toggleControl.setToggled(false);

        Assert.assertEquals(0, toggleStates.size());
    }

    /**
     * 验证开关开/关态有不同轨道颜色。
     */
    @Test
    public void shouldShowDifferentTrackColorsForOnAndOff() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        toggleControl.setToggled(false);
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        toggleControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(24));
        root.append(toggleControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        RecordingUiRenderContext offRenderContext = new RecordingUiRenderContext();
        widget.render(offRenderContext);
        Assert.assertTrue(containsFillColor(offRenderContext.drawCalls, 0xFF4A5568));

        toggleControl.setToggled(true);
        RecordingUiRenderContext onRenderContext = new RecordingUiRenderContext();
        widget.render(onRenderContext);
        Assert.assertTrue(containsFillColor(onRenderContext.drawCalls, 0xFF38A169));
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
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
