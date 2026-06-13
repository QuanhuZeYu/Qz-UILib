package club.heiqi.uilib.ui.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport.Language;
import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport.SyntaxResult;
import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport.SyntaxToken;
import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport.TokenKind;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `DocumentCodeEditorControl` 的契约测试，覆盖 Tab 插入、行号计算、错误行集合、语法分析与事件触发。
 */
public class DocumentCodeEditorControlTest {

    /**
     * 验证聚焦后按 Tab 会在当前光标位置插入 4 个空格。
     *
     * <p>{@code setText} 后光标落在文本末尾，按 Tab 在末尾追加空格。</p>
     */
    @Test
    public void shouldInsertSpacesWhenTabPressed() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(document);
        editor.setText("abc");
        root.style().setWidth(UiStyleLength.px(240)).setHeight(UiStyleLength.px(120));
        editor.getElement().style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(80));
        root.append(editor.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals("abc    ", editor.getText());
    }

    /**
     * 验证 Tab 大小受 {@link DocumentCodeEditorControl#setTabSize(int)} 控制。
     */
    @Test
    public void shouldRespectCustomTabSize() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(document);
        editor.setText("x").setTabSize(2);
        root.style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(80));
        editor.getElement().style().setWidth(UiStyleLength.px(160)).setHeight(UiStyleLength.px(54));
        root.append(editor.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 80);

        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals("x  ", editor.getText());
    }

    /**
     * 验证未布局时 {@link DocumentCodeEditorControl#getVisibleLineRange()} 返回全量范围。
     */
    @Test
    public void shouldReportAllLinesAsVisibleBeforeLayout() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setText("a\nb\nc\nd");

        DocumentCodeEditorControl.VisibleLineRange range = editor.getVisibleLineRange();

        Assert.assertEquals(0, range.getFirstLine());
        Assert.assertEquals(4, range.getLastExclusiveLine());
        Assert.assertEquals(4, range.getTotalLines());
    }

    /**
     * 验证 {@link DocumentCodeEditorControl#setErrorLines(Set)} 会更新内部集合，并触发 handler 一次。
     */
    @Test
    public void shouldUpdateErrorLinesAndFireHandlerOnce() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        final AtomicInteger[] capturedSize = new AtomicInteger[] {new AtomicInteger(-1)};
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setErrorHandler(new DocumentCodeEditorErrorHandler() {
            @Override
            public void onErrorsUpdated(DocumentCodeEditorErrorUpdateEvent event) {
                firedCount.incrementAndGet();
                capturedSize[0].set(event.getErrorLines().size());
            }
        });

        editor.setErrorLines(new LinkedHashSet<Integer>(Arrays.asList(2, 5)));

        Assert.assertEquals(new HashSet<Integer>(Arrays.asList(2, 5)), editor.getErrorLines());
        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals(2, capturedSize[0].get());
    }

    /**
     * 验证相同错误行集合不会重复触发 handler。
     */
    @Test
    public void shouldNotFireHandlerWhenErrorLinesUnchanged() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        Set<Integer> errors = new LinkedHashSet<Integer>(Arrays.asList(1, 3));
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setErrorHandler(new DocumentCodeEditorErrorHandler() {
            @Override
            public void onErrorsUpdated(DocumentCodeEditorErrorUpdateEvent event) {
                firedCount.incrementAndGet();
            }
        });
        editor.setErrorLines(errors);
        editor.setErrorLines(new LinkedHashSet<Integer>(Arrays.asList(3, 1)));

        Assert.assertEquals(1, firedCount.get());
    }

    /**
     * 验证 {@link DocumentCodeEditorControl#setError(String)} 文案变化时触发 handler。
     */
    @Test
    public void shouldFireHandlerWhenErrorMessageChanges() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        final String[] captured = new String[] {""};
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setErrorHandler(new DocumentCodeEditorErrorHandler() {
            @Override
            public void onErrorsUpdated(DocumentCodeEditorErrorUpdateEvent event) {
                firedCount.incrementAndGet();
                captured[0] = event.getErrorMessage();
            }
        });
        editor.setError("missing brace");
        editor.setError("missing brace");

        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals("missing brace", captured[0]);
        Assert.assertEquals("missing brace", editor.getErrorMessage());
    }

    /**
     * 验证合法 JSON 会产生 token 且不标记错误行。
     */
    @Test
    public void shouldTokenizeValidJsonWithoutErrors() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.JSON).setText("{\"name\": \"value\", \"count\": 42, \"flag\": true}");

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue("应有 token 产生", result.getTokens().size() >= 6);
        Assert.assertTrue("errorLines 应为空", result.getErrorLines().isEmpty());
        Assert.assertTrue("应识别出 KEY token", containsKind(result.getTokens(), TokenKind.KEY));
        Assert.assertTrue("应识别出 STRING token", containsKind(result.getTokens(), TokenKind.STRING));
        Assert.assertTrue("应识别出 NUMBER token", containsKind(result.getTokens(), TokenKind.NUMBER));
        Assert.assertTrue("应识别出 BOOLEAN token", containsKind(result.getTokens(), TokenKind.BOOLEAN));
        Assert.assertTrue("应识别出 PUNCT token", containsKind(result.getTokens(), TokenKind.PUNCT));
    }

    /**
     * 验证非法 JSON（未闭合字符串）会把对应行加入 errorLines。
     */
    @Test
    public void shouldFlagMalformedJsonLineAsError() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.JSON).setText("{\n  \"key: broken\n}");

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue("第 1 行（0-based）应被标记为错误行", result.getErrorLines().contains(1));
    }

    /**
     * 验证 JSON 裸单词会被标记为错误行。
     */
    @Test
    public void shouldFlagBareWordAsErrorInJson() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.JSON).setText("not_a_string");

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue("裸单词应触发错误行", result.getErrorLines().contains(0));
    }

    /**
     * 验证 YAML 会识别 key、标点和字符串值。
     */
    @Test
    public void shouldTokenizeYamlKeyAndValue() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.YAML).setText("name: Alice\nage: 30\nactive: true");

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue("应识别出 KEY token", containsKind(result.getTokens(), TokenKind.KEY));
        Assert.assertTrue("应识别出 STRING token", containsKind(result.getTokens(), TokenKind.STRING));
        Assert.assertTrue("应识别出 NUMBER token", containsKind(result.getTokens(), TokenKind.NUMBER));
        Assert.assertTrue("应识别出 BOOLEAN token", containsKind(result.getTokens(), TokenKind.BOOLEAN));
        Assert.assertTrue("YAML 合法文本不应标记错误行", result.getErrorLines().isEmpty());
    }

    /**
     * 验证 YAML 注释会被识别为 COMMENT。
     */
    @Test
    public void shouldRecognizeYamlComment() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.YAML).setText("# this is a comment");

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue("应识别出 COMMENT token", containsKind(result.getTokens(), TokenKind.COMMENT));
    }

    /**
     * 验证 PLAIN 语言不做词法分析。
     */
    @Test
    public void shouldReturnEmptyForPlainLanguage() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.JSON).setText("{\"k\":1}");
        editor.setLanguage(Language.PLAIN);

        SyntaxResult result = editor.getSyntaxResult();

        Assert.assertTrue(result.getTokens().isEmpty());
        Assert.assertTrue(result.getErrorLines().isEmpty());
    }

    /**
     * 验证 setText 是程序化设置，不触发 changeHandler（与 {@link DocumentTextAreaControl} 一致）。
     */
    @Test
    public void shouldNotFireChangeHandlerOnSetText() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setChangeHandler(new DocumentCodeEditorChangeHandler() {
            @Override
            public void onContentChanged(DocumentCodeEditorChangeEvent event) {
                firedCount.incrementAndGet();
            }
        });
        editor.setText("hello");

        Assert.assertEquals(0, firedCount.get());
        Assert.assertEquals("hello", editor.getText());
    }

    /**
     * 验证 setLanguage 触发 changeHandler。
     */
    @Test
    public void shouldFireChangeHandlerOnLanguageChange() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        final Language[] captured = new Language[] {null};
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setChangeHandler(new DocumentCodeEditorChangeHandler() {
            @Override
            public void onContentChanged(DocumentCodeEditorChangeEvent event) {
                firedCount.incrementAndGet();
                captured[0] = event.getLanguage();
            }
        });
        editor.setLanguage(Language.YAML);

        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals(Language.YAML, captured[0]);
        Assert.assertEquals(Language.YAML, editor.getLanguage());
    }

    /**
     * 验证只读模式下 Tab 不插入。
     */
    @Test
    public void shouldNotInsertWhenReadOnly() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(document);
        editor.setText("abc").setReadOnly(true);
        root.style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(80));
        editor.getElement().style().setWidth(UiStyleLength.px(160)).setHeight(UiStyleLength.px(54));
        root.append(editor.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 80);

        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals("abc", editor.getText());
    }

    /**
     * 验证 Tab 之外的普通文本输入也走 changeHandler。
     */
    @Test
    public void shouldFireChangeHandlerOnTextInput() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(document);
        final AtomicInteger firedCount = new AtomicInteger(0);
        editor.setChangeHandler(new DocumentCodeEditorChangeHandler() {
            @Override
            public void onContentChanged(DocumentCodeEditorChangeEvent event) {
                firedCount.incrementAndGet();
            }
        });
        root.style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(80));
        editor.getElement().style().setWidth(UiStyleLength.px(160)).setHeight(UiStyleLength.px(54));
        root.append(editor.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 80);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("hello", 3L));

        Assert.assertEquals("hello", editor.getText());
        Assert.assertEquals(1, firedCount.get());
    }

    /**
     * 验证负数与非法错误行号会被规范化过滤。
     */
    @Test
    public void shouldNormalizeErrorLinesAndIgnoreInvalidEntries() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        // 故意混入 null 与负数，验证归一化逻辑
        Set<Integer> safe = new LinkedHashSet<Integer>(Arrays.asList(-1, 0, 2, null));
        editor.setErrorLines(safe);

        Assert.assertEquals(new HashSet<Integer>(Arrays.asList(0, 2)), editor.getErrorLines());
        // 清空错误行不应崩溃
        editor.setErrorLines(Collections.<Integer>emptySet());
        Assert.assertTrue(editor.getErrorLines().isEmpty());
    }

    /**
     * 验证 setLanguage(null) 退化为 PLAIN。
     */
    @Test
    public void shouldFallbackToPlainWhenLanguageNull() {
        DocumentCodeEditorControl editor = new DocumentCodeEditorControl(UiDocument.create());
        editor.setLanguage(Language.JSON);
        editor.setLanguage(null);

        Assert.assertEquals(Language.PLAIN, editor.getLanguage());
    }

    private static boolean containsKind(List<SyntaxToken> tokens, TokenKind kind) {
        for (SyntaxToken token : tokens) {
            if (token.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static final class DeterministicTextMeasureService implements
            club.heiqi.uilib.ui.text.TextMeasureService {

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
            if (text == null || text.isEmpty()) {
                return Collections.singletonList("");
            }
            return Collections.singletonList(text);
        }
    }
}
