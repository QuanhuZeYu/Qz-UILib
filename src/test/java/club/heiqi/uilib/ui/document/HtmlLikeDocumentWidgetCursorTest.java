package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.host.DocumentCursorHost;
import club.heiqi.uilib.ui.host.SystemUiCursorHost;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `HtmlLikeDocumentWidget` 的系统光标映射契约测试。
 */
public class HtmlLikeDocumentWidgetCursorTest {

    /**
     * 验证命中带 cursor 声明的元素后会应用对应光标，移出后恢复默认。
     */
    @Test
    public void shouldApplyHoveredElementCursorAndRestoreDefaultOnLeave() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode button = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        button.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setCursor(UiCursor.POINTER);
        root.append(button);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();
        widget.onMouseLeave();
        widget.flushInteractionFrameForTest();

        // cursor 改 effect 驱动：setCursorHost 同步首应用 DEFAULT，hover 进入后帧末应用 POINTER，离开后回 DEFAULT。
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.appliedCursors.get(0));
        Assert.assertEquals(UiCursor.POINTER, cursorHost.appliedCursors.get(1));
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.getLatestCursor());
    }

    /**
     * 验证命中元素本身未声明 cursor，但祖先声明后仍会按继承后的值应用。
     */
    @Test
    public void shouldInheritCursorFromAncestorDeclaration() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setCursor(UiCursor.TEXT);
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        root.append(child);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();

        Assert.assertEquals(UiCursor.TEXT, cursorHost.getLatestCursor());
    }

    /**
     * 验证重叠元素命中时会使用顶层元素声明的 cursor。
     */
    @Test
    public void shouldUseTopmostHitElementCursorWhenElementsOverlap() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode bottom = document.div();
        ElementNode top = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setPosition(UiPosition.RELATIVE);
        bottom.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setCursor(UiCursor.POINTER);
        top.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setCursor(UiCursor.TEXT);
        root.append(bottom).append(top);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();

        Assert.assertEquals(UiCursor.TEXT, cursorHost.getLatestCursor());
    }

    /**
     * 验证样式表中的 `:hover` cursor 会在进入时生效，离开后恢复默认。
     */
    @Test
    public void shouldApplyCursorDeclaredOnHoverPseudoClass() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode probe = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        probe.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        probe.setClassName("hover-probe");
        document.addStyleSheet(UiStyleSheet.create()
                .addRule(".hover-probe:hover", new UiStyleDeclaration().setCursor(UiCursor.POINTER)));
        root.append(probe);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();
        widget.onMouseLeave();
        widget.flushInteractionFrameForTest();

        Assert.assertEquals(UiCursor.POINTER, cursorHost.appliedCursors.get(1));
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.getLatestCursor());
    }

    /**
     * 验证常用 UiCursor 到系统光标的降级映射符合宿主约束。
     */
    @Test
    public void shouldResolveUnsupportedUiCursorToFallbackSystemCursor() {
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.MOVE,
                SystemUiCursorHost.resolveRequestedCursor(UiCursor.GRAB));
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.MOVE,
                SystemUiCursorHost.resolveRequestedCursor(UiCursor.GRABBING));
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.DEFAULT,
                SystemUiCursorHost.resolveRequestedCursor(UiCursor.HELP));
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.HIDDEN,
                SystemUiCursorHost.resolveRequestedCursor(UiCursor.NONE));
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.DEFAULT,
                SystemUiCursorHost.resolveRequestedCursor(null));
    }

    /**
     * 验证系统光标宿主失败时会降级为 no-op，而不是打断输入链路。
     */
    @Test
    public void shouldDegradeSystemCursorHostWhenBackendFails() {
        FailingCursorBackend backend = new FailingCursorBackend();
        SystemUiCursorHost cursorHost = new SystemUiCursorHost(backend);

        cursorHost.applyCursor(UiCursor.POINTER);
        cursorHost.applyCursor(UiCursor.TEXT);
        cursorHost.applyCursor(UiCursor.NONE);

        Assert.assertEquals(0, backend.showCursorCalls);
        Assert.assertEquals(0, backend.applySystemCursorCalls);
        Assert.assertEquals(0, backend.hideCursorCalls);
    }

    /**
     * 验证默认光标恢复与 POINTER 等系统光标走同一 SDL 系统光标路径。
     * 私有 SDL 后端的主线程 runnable 细节不额外暴露生产 API，本测试固定公开后端契约。
     */
    @Test
    public void shouldApplyDefaultThroughSystemCursorBackendAfterPointer() {
        RecordingSystemCursorBackend backend = new RecordingSystemCursorBackend();
        SystemUiCursorHost cursorHost = new SystemUiCursorHost(backend);

        cursorHost.applyCursor(UiCursor.POINTER);
        cursorHost.applyCursor(UiCursor.DEFAULT);

        Assert.assertEquals(2, backend.showCursorCalls);
        Assert.assertEquals(0, backend.applyDefaultCursorCalls);
        Assert.assertEquals(2, backend.appliedSystemCursors.size());
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.POINTER,
                backend.appliedSystemCursors.get(0));
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.DEFAULT,
                backend.appliedSystemCursors.get(1));
    }

    /**
     * 验证强制同步会绕过宿主同值短路，覆盖原生光标真实状态与宿主缓存漂移的场景。
     */
    @Test
    public void shouldForceApplyDefaultWhenSystemCursorCacheAlreadyDefault() {
        RecordingSystemCursorBackend backend = new RecordingSystemCursorBackend();
        SystemUiCursorHost cursorHost = new SystemUiCursorHost(backend);

        cursorHost.forceApplyCursor(UiCursor.DEFAULT);

        Assert.assertEquals(1, backend.showCursorCalls);
        Assert.assertEquals(1, backend.appliedSystemCursors.size());
        Assert.assertEquals(SystemUiCursorHost.ResolvedCursorKind.DEFAULT,
                backend.appliedSystemCursors.get(0));
    }

    /**
     * 验证文档 widget 关闭时会通过强制入口复位默认光标，而不是只走普通 apply 委托。
     */
    @Test
    public void shouldForceApplyDefaultCursorWhenWidgetCloses() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode button = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        button.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setCursor(UiCursor.POINTER);
        root.append(button);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();
        widget.close();

        Assert.assertEquals(UiCursor.POINTER, cursorHost.getLatestCursor());
        Assert.assertEquals(1, cursorHost.forceAppliedCursors.size());
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.forceAppliedCursors.get(0));
    }

    /**
     * 验证样式表中的 `:active` cursor 会在按下时生效，并在抬起后恢复 hover 态光标。
     */
    @Test
    public void shouldSwitchCursorForActivePseudoClassAndRestoreAfterRelease() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode probe = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        probe.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        probe.setClassName("press-probe");
        document.addStyleSheet(UiStyleSheet.create()
                .addRule(".press-probe", new UiStyleDeclaration().setCursor(UiCursor.POINTER))
                .addRule(".press-probe:active", new UiStyleDeclaration().setCursor(UiCursor.MOVE)));
        root.append(probe);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 2L));
        widget.flushInteractionFrameForTest();
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 3L));
        widget.flushInteractionFrameForTest();

        Assert.assertEquals(UiCursor.POINTER, cursorHost.appliedCursors.get(1));
        Assert.assertEquals(UiCursor.MOVE, cursorHost.appliedCursors.get(2));
        Assert.assertEquals(UiCursor.POINTER, cursorHost.getLatestCursor());
    }

    /**
     * 验证 select popup 关闭后会按当前鼠标位置立即刷新光标。
     */
    @Test
    public void shouldRefreshCursorWhenSelectPopupClosesUnderPointer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(160));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 160,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 240, 160);

        click(widget, 20, 12, 1L);
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 20, 72, -1, 0, 0, 0, 3L));
        widget.flushInteractionFrameForTest();
        Assert.assertEquals(UiCursor.POINTER, cursorHost.getLatestCursor());

        click(widget, 20, 72, 4L);
        widget.flushInteractionFrameForTest();

        Assert.assertEquals("B", selectControl.getSelectedOption());
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.getLatestCursor());
    }

    private static void click(HtmlLikeDocumentWidget widget, int x, int y, long timeNanos) {
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, x, y, 0, 0, 0, 0, timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, x, y, 0, 0, 0, 0, timeNanos + 1L));
    }

    /**
     * 记录光标变更的测试宿主。
     */
    private static final class RecordingCursorHost implements DocumentCursorHost {

        private final List<UiCursor> appliedCursors = new ArrayList<UiCursor>();
        private final List<UiCursor> forceAppliedCursors = new ArrayList<UiCursor>();

        @Override
        public void applyCursor(UiCursor cursor) {
            appliedCursors.add(cursor == null ? UiCursor.DEFAULT : cursor);
        }

        @Override
        public void forceApplyCursor(UiCursor cursor) {
            forceAppliedCursors.add(cursor == null ? UiCursor.DEFAULT : cursor);
        }

        private UiCursor getLatestCursor() {
            return appliedCursors.isEmpty() ? UiCursor.DEFAULT : appliedCursors.get(appliedCursors.size() - 1);
        }
    }

    /**
     * 会在显示光标时失败的测试宿主后端。
     */
    private static final class FailingCursorBackend implements SystemUiCursorHost.NativeCursorBackend {

        private int showCursorCalls;
        private int hideCursorCalls;
        private int applySystemCursorCalls;

        @Override
        public boolean isRuntimeAvailable() {
            throw new IllegalStateException("boom");
        }

        @Override
        public void showCursor() {
            showCursorCalls++;
            throw new IllegalStateException("boom");
        }

        @Override
        public void hideCursor() {
            hideCursorCalls++;
            throw new IllegalStateException("boom");
        }

        @Override
        public void applyDefaultCursor() {
            throw new IllegalStateException("boom");
        }

        @Override
        public void applySystemCursor(SystemUiCursorHost.ResolvedCursorKind cursorKind) {
            applySystemCursorCalls++;
        }
    }

    /**
     * 记录系统光标宿主后端调用顺序的测试替身。
     */
    private static final class RecordingSystemCursorBackend implements SystemUiCursorHost.NativeCursorBackend {

        private int showCursorCalls;
        private int hideCursorCalls;
        private int applyDefaultCursorCalls;
        private final List<SystemUiCursorHost.ResolvedCursorKind> appliedSystemCursors =
                new ArrayList<SystemUiCursorHost.ResolvedCursorKind>();

        @Override
        public boolean isRuntimeAvailable() {
            return true;
        }

        @Override
        public void showCursor() {
            showCursorCalls++;
        }

        @Override
        public void hideCursor() {
            hideCursorCalls++;
        }

        @Override
        public void applyDefaultCursor() {
            applyDefaultCursorCalls++;
        }

        @Override
        public void applySystemCursor(SystemUiCursorHost.ResolvedCursorKind cursorKind) {
            appliedSystemCursors.add(cursorKind);
        }
    }

    /**
     * 供光标测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
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
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
