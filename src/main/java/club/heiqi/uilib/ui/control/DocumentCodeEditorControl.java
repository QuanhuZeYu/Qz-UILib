package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderSurface;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.layout.LogicalTextLine;
import club.heiqi.uilib.ui.text.layout.TextLayoutEngine;
import club.heiqi.uilib.ui.text.layout.TextMeasureFunction;
import club.heiqi.uilib.ui.text.layout.VisualLineLayout;

/**
 * 源码编辑器控件：在多行文本输入基础上叠加行号、Tab 插入、错误行提示与基础语法高亮。
 *
 * <p>结构上参考 {@link DocumentTextAreaControl}，但不继承其内部状态，复制必要的文本存储与
 * caret/selection 计算逻辑。本控件额外维护：</p>
 * <ul>
 *   <li>{@link DocumentCodeEditorSyntaxSupport.Language}：决定语法分析器与高亮规则；</li>
 *   <li>错误行集合：{@link #setErrorLines(Set)} / {@link #setError(String)}，由
 *       {@link DocumentCodeEditorErrorHandler} 通知变化；</li>
 *   <li>左侧行号 gutter 与每行按 token 染色的内容层。</li>
 * </ul>
 *
 * <p>事件契约：</p>
 * <ul>
 *   <li>{@link DocumentCodeEditorChangeHandler}：文本或语言变化时触发一次；</li>
 *   <li>{@link DocumentCodeEditorErrorHandler}：错误行集合或错误文案<b>实际变化</b>时触发一次。</li>
 * </ul>
 *
 * <p>本控件不直接执行 JSON/YAML 解析校验，仅做轻量词法染色；完整校验由 5-C 的
 * {@code ModernRawEditorPropertyBinding} 调用 {@code ConfigSerializer} 完成，并通过
 * {@link #setErrorLines(Set)} 把出错行号反馈进来。</p>
 */
public final class DocumentCodeEditorControl {

    /** 默认行高（像素）。 */
    public static final int DEFAULT_LINE_HEIGHT = 18;
    /** Tab 插入的空格数量。 */
    public static final int DEFAULT_TAB_SIZE = 4;
    /** 默认行号 gutter 宽度（像素）。 */
    public static final int DEFAULT_GUTTER_WIDTH = 48;
    private static final int DEFAULT_CARET_WIDTH = 2;
    private static final long BLINK_PERIOD_NANOS = 530_000_000L;
    private static final UiStyleLength DEFAULT_LINE_HEIGHT_LENGTH = UiStyleLength.px(DEFAULT_LINE_HEIGHT);

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode gutterLayer;
    private final ElementNode contentElement;
    private final ElementNode selectionLayer;
    private final ElementNode caretLayer;
    private final StringBuilder textBuilder = new StringBuilder();
    private final List<LogicalLine> logicalLines = new ArrayList<LogicalLine>();
    private final TextLayoutEngine textLayoutEngine = new TextLayoutEngine();
    private final List<LogicalTextLine> layoutLines = new ArrayList<LogicalTextLine>();
    private List<VisualLineLayout> visualLines = Collections.emptyList();

    private DocumentCodeEditorChangeHandler changeHandler;
    private DocumentCodeEditorErrorHandler errorHandler;
    private DocumentCodeEditorSyntaxSupport.SyntaxSupport syntaxSupport =
            DocumentCodeEditorSyntaxSupport.forLanguage(DocumentCodeEditorSyntaxSupport.Language.PLAIN);
    private DocumentCodeEditorSyntaxSupport.Language language =
            DocumentCodeEditorSyntaxSupport.Language.PLAIN;
    private DocumentCodeEditorSyntaxSupport.SyntaxResult lastSyntaxResult =
            DocumentCodeEditorSyntaxSupport.SyntaxResult.EMPTY;

    private Set<Integer> errorLines = Collections.<Integer>emptySet();
    private String errorMessage = "";

    private String placeholder = "";
    private int maxLength = 65536;
    private int tabSize = DEFAULT_TAB_SIZE;
    private int gutterWidth = DEFAULT_GUTTER_WIDTH;
    private boolean enabled = true;
    private boolean focused;
    private boolean hovered;
    private boolean readOnly;
    private int caretIndex;
    private int selectionAnchorIndex;
    private int preferredColumnCodePoints = -1;
    private long caretBlinkResetNanos;

    // 颜色配置
    private int normalBackgroundColor = 0xFF1E1E2E;
    private int normalBorderColor = 0xFF3B3B5C;
    private int hoverBorderColor = 0xFF5A5A8C;
    private int focusBorderColor = 0xFF5A9EF7;
    private int disabledBackgroundColor = 0xFF2A2A3A;
    private int disabledBorderColor = 0xFF3A3A4A;
    private int textColor = 0xFFEEEEFF;
    private int caretColor = 0xFFFFFFFF;
    private int placeholderColor = 0xFF777799;
    private int disabledTextColor = 0xFF666677;
    private int selectionColor = 0x664F86F7;
    private int gutterBackgroundColor = 0xFF181828;
    private int gutterTextColor = 0xFF8888AA;
    private int errorLineBackgroundColor = 0x55FF5555;
    private int errorGutterTextColor = 0xFFFF9999;

    // 语法高亮配色（PLAIN 用默认 textColor）
    private int syntaxKeyColor = 0xFFFFD580;
    private int syntaxStringColor = 0xFF9CFFAB;
    private int syntaxNumberColor = 0xFFFFB3D9;
    private int syntaxBooleanColor = 0xFFFFAAFF;
    private int syntaxNullColor = 0xFFAAAAFF;
    private int syntaxPunctColor = 0xFFCCCCCC;
    private int syntaxCommentColor = 0xFF77AA77;
    private int syntaxPlainColor = 0xFFEEEEFF;

    // 上一次布局信息（用于 caret 渲染与行号绘制）
    private int viewportContentLeft;
    private int viewportContentTop;
    private int viewportContentWidth;
    private int viewportContentHeight;

    /**
     * 创建源码编辑器控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentCodeEditorControl(UiDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        this.element = document.div();
        this.gutterLayer = document.div();
        this.contentElement = document.div();
        this.selectionLayer = document.div();
        this.caretLayer = document.div();
        element.append(gutterLayer);
        element.append(selectionLayer);
        element.append(contentElement);
        element.append(caretLayer);
        configureElement();
        installHandlers();
        rebuildLogicalLines();
        syncContent();
        updateVisualState();
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前文本内容。
     *
     * @return 当前文本内容
     */
    public String getText() {
        return textBuilder.toString();
    }

    /**
     * 设置当前文本内容，同时刷新语法高亮。
     *
     * @param text 文本内容；为 null 时清空
     * @return 当前控件
     */
    public DocumentCodeEditorControl setText(String text) {
        String safe = text == null ? "" : text;
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        textBuilder.setLength(0);
        textBuilder.append(safe);
        caretIndex = textBuilder.length();
        selectionAnchorIndex = caretIndex;
        preferredColumnCodePoints = -1;
        rebuildLogicalLines();
        reanalyzeSyntax();
        syncContent();
        updateVisualState();
        return this;
    }

    /**
     * 设置当前语言，并按新语言重新分析语法。
     *
     * @param language 语言枚举；为 null 视为 {@link DocumentCodeEditorSyntaxSupport.Language#PLAIN}
     * @return 当前控件
     */
    public DocumentCodeEditorControl setLanguage(DocumentCodeEditorSyntaxSupport.Language language) {
        DocumentCodeEditorSyntaxSupport.Language resolved = language == null
                ? DocumentCodeEditorSyntaxSupport.Language.PLAIN : language;
        if (this.language == resolved) {
            return this;
        }
        this.language = resolved;
        this.syntaxSupport = DocumentCodeEditorSyntaxSupport.forLanguage(resolved);
        reanalyzeSyntax();
        syncContent();
        updateVisualState();
        fireChange();
        return this;
    }

    /**
     * 返回当前语言枚举。
     *
     * @return 当前语言枚举
     */
    public DocumentCodeEditorSyntaxSupport.Language getLanguage() {
        return language;
    }

    /**
     * 设置占位文本（仅在文本为空时显示）。
     *
     * @param placeholder 占位文本；为 null 时清空
     * @return 当前控件
     */
    public DocumentCodeEditorControl setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        syncContent();
        updateVisualState();
        return this;
    }

    /**
     * 设置最大输入长度（按 char 计数）。
     *
     * @param maxLength 最大输入长度
     * @return 当前控件
     */
    public DocumentCodeEditorControl setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (textBuilder.length() > this.maxLength) {
            textBuilder.setLength(this.maxLength);
            caretIndex = Math.min(caretIndex, this.maxLength);
            selectionAnchorIndex = Math.min(selectionAnchorIndex, this.maxLength);
            rebuildLogicalLines();
            reanalyzeSyntax();
            syncContent();
        }
        return this;
    }

    /**
     * 设置 Tab 一次插入的空格数。
     *
     * @param tabSize Tab 大小；小于 1 时取 1
     * @return 当前控件
     */
    public DocumentCodeEditorControl setTabSize(int tabSize) {
        this.tabSize = Math.max(1, tabSize);
        return this;
    }

    /**
     * 设置行号 gutter 宽度（像素）。
     *
     * @param gutterWidth gutter 宽度；小于 0 时取 0
     * @return 当前控件
     */
    public DocumentCodeEditorControl setGutterWidth(int gutterWidth) {
        this.gutterWidth = Math.max(0, gutterWidth);
        syncContent();
        return this;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前控件
     */
    public DocumentCodeEditorControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        element.setFocusable(enabled);
        if (!enabled) {
            focused = false;
            element.setAttribute("disabled", "true");
        } else {
            element.removeAttribute("disabled");
        }
        syncContent();
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
     * 判断控件是否聚焦。
     *
     * @return 是否聚焦
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * 设置只读。
     *
     * @param readOnly 是否只读
     * @return 当前控件
     */
    public DocumentCodeEditorControl setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly) {
            element.setAttribute("readonly", "true");
        } else {
            element.removeAttribute("readonly");
        }
        return this;
    }

    /**
     * 判断只读状态。
     *
     * @return 是否只读
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 设置内容变更处理器。
     *
     * @param changeHandler 变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentCodeEditorControl setChangeHandler(DocumentCodeEditorChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置错误行集合变更处理器。
     *
     * @param errorHandler 错误处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentCodeEditorControl setErrorHandler(DocumentCodeEditorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    /**
     * 设置错误行集合。新集合与旧集合相同时不触发事件。
     *
     * @param errorLines 错误行集合（0-based 行号）；为 null 视为空集合
     * @return 当前控件
     */
    public DocumentCodeEditorControl setErrorLines(Set<Integer> errorLines) {
        Set<Integer> next = normalizeErrorLines(errorLines);
        if (next.equals(this.errorLines)) {
            return this;
        }
        this.errorLines = next;
        syncContent();
        fireErrorUpdate();
        return this;
    }

    /**
     * 设置错误提示文案（不影响错误行集合）。
     *
     * @param message 错误提示文案；为 null 视为空串
     * @return 当前控件
     */
    public DocumentCodeEditorControl setError(String message) {
        String next = message == null ? "" : message;
        if (next.equals(errorMessage)) {
            return this;
        }
        errorMessage = next;
        fireErrorUpdate();
        return this;
    }

    /**
     * 返回当前错误行集合（不可变）。
     *
     * @return 错误行集合
     */
    public Set<Integer> getErrorLines() {
        return errorLines;
    }

    /**
     * 返回当前错误提示文案。
     *
     * @return 错误提示文案
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 返回最近一次语法分析结果（不可变），主要用于测试断言与外部展示。
     *
     * @return 语法分析结果
     */
    public DocumentCodeEditorSyntaxSupport.SyntaxResult getSyntaxResult() {
        return lastSyntaxResult;
    }

    /**
     * 返回当前可见行范围（起止行号，0-based，左闭右开）。
     *
     * <p>未经过布局时返回 {@code [0, 逻辑行总数]}。</p>
     *
     * @return 可见行范围
     */
    public VisibleLineRange getVisibleLineRange() {
        int totalLines = Math.max(1, logicalLines.size());
        if (viewportContentHeight <= 0) {
            return new VisibleLineRange(0, totalLines, totalLines);
        }
        int firstLine = Math.max(0, Math.min(totalLines - 1, viewportContentTop / DEFAULT_LINE_HEIGHT));
        int lastExclusive = Math.max(firstLine + 1,
                Math.min(totalLines, (viewportContentTop + viewportContentHeight + DEFAULT_LINE_HEIGHT - 1)
                        / DEFAULT_LINE_HEIGHT));
        return new VisibleLineRange(firstLine, lastExclusive, totalLines);
    }

    /**
     * 设置整体配色。
     *
     * @param normalBackgroundColor 正常背景色
     * @param normalBorderColor 正常边框色
     * @param focusBorderColor 聚焦边框色
     * @param disabledBackgroundColor 禁用背景色
     * @param disabledBorderColor 禁用边框色
     * @return 当前控件
     */
    public DocumentCodeEditorControl setSurfaceColors(int normalBackgroundColor, int normalBorderColor,
            int focusBorderColor, int disabledBackgroundColor, int disabledBorderColor) {
        this.normalBackgroundColor = normalBackgroundColor;
        this.normalBorderColor = normalBorderColor;
        this.focusBorderColor = focusBorderColor;
        this.disabledBackgroundColor = disabledBackgroundColor;
        this.disabledBorderColor = disabledBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置语法高亮配色。
     *
     * @param keyColor key 颜色
     * @param stringColor 字符串颜色
     * @param numberColor 数字颜色
     * @param booleanColor 布尔颜色
     * @param nullColor null 颜色
     * @param punctColor 标点颜色
     * @param commentColor 注释颜色
     * @param plainColor 普通文本颜色
     * @return 当前控件
     */
    public DocumentCodeEditorControl setSyntaxColors(int keyColor, int stringColor, int numberColor,
            int booleanColor, int nullColor, int punctColor, int commentColor, int plainColor) {
        this.syntaxKeyColor = keyColor;
        this.syntaxStringColor = stringColor;
        this.syntaxNumberColor = numberColor;
        this.syntaxBooleanColor = booleanColor;
        this.syntaxNullColor = nullColor;
        this.syntaxPunctColor = punctColor;
        this.syntaxCommentColor = commentColor;
        this.syntaxPlainColor = plainColor;
        syncContent();
        return this;
    }

    /**
     * 设置行号 gutter 配色。
     *
     * @param gutterBackgroundColor gutter 背景色
     * @param gutterTextColor 行号文本色
     * @param errorLineBackgroundColor 错误行背景色
     * @param errorGutterTextColor 错误行号文本色
     * @return 当前控件
     */
    public DocumentCodeEditorControl setGutterColors(int gutterBackgroundColor, int gutterTextColor,
            int errorLineBackgroundColor, int errorGutterTextColor) {
        this.gutterBackgroundColor = gutterBackgroundColor;
        this.gutterTextColor = gutterTextColor;
        this.errorLineBackgroundColor = errorLineBackgroundColor;
        this.errorGutterTextColor = errorGutterTextColor;
        syncContent();
        return this;
    }

    private void configureElement() {
        element.setAttribute("data-document-control", "code-editor");
        element.setAttribute("aria-multiline", "true");
        element.setFocusable(true);
        element.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(180))
                .setPadding(UiStyleLength.px(0))
                .setBackgroundColor(normalBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setOverflowX(UiOverflow.AUTO)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(textColor)
                .setLineHeight(DEFAULT_LINE_HEIGHT_LENGTH)
                .setCursor(UiCursor.TEXT);

        gutterLayer.setAttribute("data-hit-test-hidden", "true");
        gutterLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(gutterWidth))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(0)
                .setBackgroundColor(gutterBackgroundColor)
                .setPointerEvents(UiPointerEvents.NONE);
        gutterLayer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                render(DocumentCustomRenderSurface.live(context, contentLeft, contentTop, contentRight,
                        contentBottom));
            }

            @Override
            public void render(DocumentCustomRenderSurface surface) {
                updateRenderedViewportMetrics(surface, gutterLayer);
                renderGutter(surface);
            }
        });

        selectionLayer.setAttribute("data-hit-test-hidden", "true");
        selectionLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(gutterWidth))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(1)
                .setPointerEvents(UiPointerEvents.NONE);
        selectionLayer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                render(DocumentCustomRenderSurface.live(context, contentLeft, contentTop, contentRight,
                        contentBottom));
            }

            @Override
            public void render(DocumentCustomRenderSurface surface) {
                renderSelection(surface);
            }
        });

        contentElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(2)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMarginLeft(UiStyleLength.px(gutterWidth));

        caretLayer.setAttribute("data-hit-test-hidden", "true");
        caretLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(3)
                .setTextColor(caretColor)
                .setPointerEvents(UiPointerEvents.NONE);
        caretLayer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                render(DocumentCustomRenderSurface.live(context, contentLeft, contentTop, contentRight,
                        contentBottom));
            }

            @Override
            public void render(DocumentCustomRenderSurface surface) {
                renderCaret(surface);
            }
        });
    }

    private void installHandlers() {
        element.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focused = enabled && event.isFocused();
                updateVisualState();
                if (focused) {
                    resetCaretBlink();
                }
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                if (event.isDefaultPrevented() || !focused || !enabled) {
                    return false;
                }
                return replaceSelection(DocumentTextAreaTextSupport.normalizeInput(event.getText()), true);
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!focused || !enabled) {
                    return false;
                }
                return handleKeyEvent(event);
            }
        }).setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                if (event.getTarget() == element || event.getTarget() == contentElement
                        || isContentDescendant(event.getTarget())) {
                    collapseSelection(resolveCaretIndexAtDocumentPoint(event.getDocumentX(), event.getDocumentY()));
                    return true;
                }
                return false;
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hovered = event.isHovered() && enabled;
                updateVisualState();
                return false;
            }
        });
    }

    private boolean isContentDescendant(ElementNode target) {
        for (club.heiqi.uilib.ui.dom.DocumentNode cursor = target; cursor != null; cursor = cursor.getParent()) {
            if (cursor == contentElement) {
                return true;
            }
        }
        return false;
    }

    private boolean handleKeyEvent(DocumentElementKeyEvent event) {
        int keyCode = event.getKeyCode();
        UiKeyEvent.Action action = event.getAction();
        boolean repeatable = action == UiKeyEvent.Action.PRESSED || action == UiKeyEvent.Action.REPEATED;
        // Tab 始终插入空格（Shift+Tab 不做反向缩进，留给 5-D 收口）
        if (keyCode == UiKeyCodes.KEY_TAB && repeatable) {
            return replaceSelection(buildTabString(), true);
        }
        if (event.isControlPressed() && keyCode == UiKeyCodes.KEY_A && action == UiKeyEvent.Action.PRESSED) {
            selectionAnchorIndex = 0;
            caretIndex = textBuilder.length();
            preferredColumnCodePoints = -1;
            resetCaretBlink();
            return true;
        }
        if ((keyCode == UiKeyCodes.KEY_RETURN || keyCode == UiKeyCodes.KEY_NUMPADENTER) && repeatable) {
            return replaceSelection("\n", true);
        }
        if (keyCode == UiKeyCodes.KEY_BACK && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteBackward();
        }
        if (keyCode == UiKeyCodes.KEY_DELETE && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteForward();
        }
        if (keyCode == UiKeyCodes.KEY_LEFT && repeatable) {
            moveCaretHorizontally(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_RIGHT && repeatable) {
            moveCaretHorizontally(1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_HOME && repeatable) {
            moveCaretToLineBoundary(true, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_END && repeatable) {
            moveCaretToLineBoundary(false, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_UP && repeatable) {
            moveCaretVertically(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_DOWN && repeatable) {
            moveCaretVertically(1, event.isShiftPressed());
            return true;
        }
        return false;
    }

    private String buildTabString() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tabSize; index++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private void rebuildLogicalLines() {
        logicalLines.clear();
        String text = textBuilder.toString();
        if (text.isEmpty()) {
            logicalLines.add(new LogicalLine(0, 0, ""));
            syncLayoutLines();
            return;
        }
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\n') {
                continue;
            }
            logicalLines.add(new LogicalLine(lineStart, index, text.substring(lineStart, index)));
            lineStart = index + 1;
        }
        logicalLines.add(new LogicalLine(lineStart, text.length(), text.substring(lineStart)));
        int clampedCaret = Math.min(caretIndex, text.length());
        int clampedAnchor = Math.min(selectionAnchorIndex, text.length());
        caretIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(text, Math.max(0, clampedCaret));
        selectionAnchorIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(text, Math.max(0, clampedAnchor));
        syncLayoutLines();
    }

    /**
     * 将内部逻辑行同步到共享布局引擎使用的逻辑行视图。
     *
     * <p>源码编辑器不软换行，每条逻辑行对应一条视觉行；该视图供 {@link TextLayoutEngine} 计算前缀宽度与命中几何。</p>
     */
    private void syncLayoutLines() {
        layoutLines.clear();
        for (int index = 0; index < logicalLines.size(); index++) {
            LogicalLine line = logicalLines.get(index);
            layoutLines.add(new LogicalTextLine(line.startIndex, line.endIndex, line.text));
        }
    }

    private void reanalyzeSyntax() {
        try {
            lastSyntaxResult = syntaxSupport.analyze(textBuilder.toString());
        } catch (RuntimeException ex) {
            // 合约要求：分析失败返回空集合，不抛异常
            lastSyntaxResult = DocumentCodeEditorSyntaxSupport.SyntaxResult.EMPTY;
        }
    }

    private void syncContent() {
        contentElement.clearChildren();
        boolean showingPlaceholder = textBuilder.length() == 0 && !placeholder.isEmpty();
        if (showingPlaceholder) {
            appendRenderedLine(0, placeholder, placeholderColor, Collections
                    .<DocumentCodeEditorSyntaxSupport.SyntaxToken>emptyList(), false);
        } else {
            for (int index = 0; index < logicalLines.size(); index++) {
                appendRenderedLine(index, logicalLines.get(index).text, syntaxPlainColor,
                        tokensForLine(index), errorLines.contains(index));
            }
        }
        element.setAttribute("value", textBuilder.toString());
        if (placeholder.isEmpty()) {
            element.removeAttribute("placeholder");
        } else {
            element.setAttribute("placeholder", placeholder);
        }
    }

    private List<DocumentCodeEditorSyntaxSupport.SyntaxToken> tokensForLine(int lineIndex) {
        List<DocumentCodeEditorSyntaxSupport.SyntaxToken> result = new ArrayList
                <DocumentCodeEditorSyntaxSupport.SyntaxToken>();
        for (DocumentCodeEditorSyntaxSupport.SyntaxToken token : lastSyntaxResult.getTokens()) {
            if (token.getLineIndex() == lineIndex) {
                result.add(token);
            }
        }
        return result;
    }

    private void appendRenderedLine(int lineIndex, String lineText, int defaultTextColor,
            List<DocumentCodeEditorSyntaxSupport.SyntaxToken> tokens, boolean isErrorLine) {
        ElementNode lineElement = document.div();
        lineElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setMinHeight(DEFAULT_LINE_HEIGHT_LENGTH)
                .setLineHeight(DEFAULT_LINE_HEIGHT_LENGTH)
                .setWhiteSpace(UiWhiteSpace.PRE)
                .setOverflowWrap(UiOverflowWrap.ANYWHERE);
        if (isErrorLine) {
            lineElement.style().setBackgroundColor(errorLineBackgroundColor);
        }
        if (tokens.isEmpty() || lineText.isEmpty()) {
            lineElement.append(createTextSpan(lineText.isEmpty() ? " " : lineText, defaultTextColor));
        } else {
            appendTokenizedLine(lineElement, lineIndex, lineText, tokens);
        }
        contentElement.append(lineElement);
    }

    private void appendTokenizedLine(ElementNode lineElement, int lineIndex, String lineText,
            List<DocumentCodeEditorSyntaxSupport.SyntaxToken> tokens) {
        int column = 0;
        for (DocumentCodeEditorSyntaxSupport.SyntaxToken token : tokens) {
            if (token.getStartColumn() > column) {
                String gap = lineText.substring(column, Math.min(token.getStartColumn(), lineText.length()));
                if (!gap.isEmpty()) {
                    lineElement.append(createTextSpan(gap, syntaxPlainColor));
                }
            }
            int tokenEnd = Math.min(token.getStartColumn() + token.getLength(), lineText.length());
            if (tokenEnd <= token.getStartColumn()) {
                column = Math.max(column, token.getStartColumn());
                continue;
            }
            String tokenText = lineText.substring(token.getStartColumn(), tokenEnd);
            lineElement.append(createTextSpan(tokenText, colorForToken(token.getKind())));
            column = tokenEnd;
        }
        if (column < lineText.length()) {
            lineElement.append(createTextSpan(lineText.substring(column), syntaxPlainColor));
        }
    }

    private ElementNode createTextSpan(String text, int color) {
        ElementNode span = document.span();
        TextNode textNode = span.appendText(text);
        textNode.setTextContentMode(TextContentMode.UILIB_RAW);
        span.style()
                .setWhiteSpace(UiWhiteSpace.PRE)
                .setTextColor(color);
        return span;
    }

    private int colorForToken(DocumentCodeEditorSyntaxSupport.TokenKind kind) {
        switch (kind) {
            case KEY:
                return syntaxKeyColor;
            case STRING:
                return syntaxStringColor;
            case NUMBER:
                return syntaxNumberColor;
            case BOOLEAN:
                return syntaxBooleanColor;
            case NULL:
                return syntaxNullColor;
            case PUNCT:
                return syntaxPunctColor;
            case COMMENT:
                return syntaxCommentColor;
            case PLAIN:
            default:
                return syntaxPlainColor;
        }
    }

    private void updateVisualState() {
        int backgroundColor;
        int borderColor;
        if (!enabled) {
            backgroundColor = disabledBackgroundColor;
            borderColor = disabledBorderColor;
        } else if (focused) {
            backgroundColor = normalBackgroundColor;
            borderColor = focusBorderColor;
        } else if (hovered) {
            backgroundColor = normalBackgroundColor;
            borderColor = hoverBorderColor;
        } else {
            backgroundColor = normalBackgroundColor;
            borderColor = normalBorderColor;
        }
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setTextColor(enabled ? textColor : disabledTextColor)
                .setCursor(enabled ? UiCursor.TEXT : UiCursor.NOT_ALLOWED);
        gutterLayer.style().setWidth(UiStyleLength.px(gutterWidth));
        selectionLayer.style().setLeft(UiStyleLength.px(gutterWidth));
        contentElement.style().setMarginLeft(UiStyleLength.px(gutterWidth));
    }

    private boolean replaceSelection(String replacement, boolean fireChange) {
        if (readOnly) {
            return false;
        }
        String normalized = DocumentTextAreaTextSupport.normalizeInput(replacement);
        if (normalized.isEmpty() && !hasSelection()) {
            return false;
        }
        String currentText = textBuilder.toString();
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        String prefix = currentText.substring(0, selectionStart);
        String suffix = currentText.substring(selectionEnd);
        int remaining = maxLength - prefix.length() - suffix.length();
        String inserted = remaining <= 0 ? "" : truncate(normalized, remaining);
        String nextText = prefix + inserted + suffix;
        if (nextText.equals(currentText)) {
            return false;
        }
        textBuilder.setLength(0);
        textBuilder.append(nextText);
        caretIndex = selectionStart + inserted.length();
        selectionAnchorIndex = caretIndex;
        preferredColumnCodePoints = -1;
        resetCaretBlink();
        rebuildLogicalLines();
        reanalyzeSyntax();
        syncContent();
        updateVisualState();
        if (fireChange) {
            fireChange();
        }
        return true;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars);
    }

    private boolean deleteBackward() {
        if (hasSelection()) {
            return replaceSelection("", true);
        }
        if (caretIndex <= 0) {
            return false;
        }
        int previousIndex = textBuilder.offsetByCodePoints(caretIndex, -1);
        selectionAnchorIndex = previousIndex;
        caretIndex = textBuilder.offsetByCodePoints(previousIndex, 1);
        return replaceSelection("", true);
    }

    private boolean deleteForward() {
        if (hasSelection()) {
            return replaceSelection("", true);
        }
        if (caretIndex >= textBuilder.length()) {
            return false;
        }
        int nextIndex = textBuilder.offsetByCodePoints(caretIndex, 1);
        selectionAnchorIndex = caretIndex;
        caretIndex = nextIndex;
        return replaceSelection("", true);
    }

    private void moveCaretHorizontally(int direction, boolean extendSelection) {
        preferredColumnCodePoints = -1;
        if (!extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            return;
        }
        String currentText = textBuilder.toString();
        if (direction < 0 && caretIndex > 0) {
            moveCaretTo(currentText.offsetByCodePoints(caretIndex, -1), extendSelection);
            return;
        }
        if (direction > 0 && caretIndex < currentText.length()) {
            moveCaretTo(currentText.offsetByCodePoints(caretIndex, 1), extendSelection);
        }
    }

    private void moveCaretToLineBoundary(boolean toStart, boolean extendSelection) {
        preferredColumnCodePoints = -1;
        int lineIndex = resolveLineIndexForCaret(caretIndex);
        LogicalLine line = logicalLines.get(lineIndex);
        moveCaretTo(toStart ? line.startIndex : line.endIndex, extendSelection);
    }

    private void moveCaretVertically(int direction, boolean extendSelection) {
        int currentLineIndex = resolveLineIndexForCaret(caretIndex);
        int targetLineIndex = Math.max(0, Math.min(logicalLines.size() - 1, currentLineIndex + direction));
        if (targetLineIndex == currentLineIndex && !extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            return;
        }
        LogicalLine currentLine = logicalLines.get(currentLineIndex);
        LogicalLine targetLine = logicalLines.get(targetLineIndex);
        int currentOffset = Math.max(0, Math.min(caretIndex - currentLine.startIndex, currentLine.text.length()));
        int currentColumnCodePoints = currentLine.text.codePointCount(0, currentOffset);
        if (preferredColumnCodePoints < 0) {
            preferredColumnCodePoints = currentColumnCodePoints;
        }
        int targetColumnCodePoints = Math.min(preferredColumnCodePoints,
                targetLine.text.codePointCount(0, targetLine.text.length()));
        int targetOffset = targetColumnCodePoints <= 0 ? 0
                : targetLine.text.offsetByCodePoints(0, targetColumnCodePoints);
        moveCaretTo(targetLine.startIndex + targetOffset, extendSelection);
    }

    private void moveCaretTo(int nextCaretIndex, boolean extendSelection) {
        int resolved = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(),
                Math.max(0, Math.min(nextCaretIndex, textBuilder.length())));
        if (extendSelection) {
            caretIndex = resolved;
        } else {
            caretIndex = resolved;
            selectionAnchorIndex = resolved;
        }
        resetCaretBlink();
    }

    private void collapseSelection(int collapsedCaretIndex) {
        int resolved = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(),
                Math.max(0, Math.min(collapsedCaretIndex, textBuilder.length())));
        caretIndex = resolved;
        selectionAnchorIndex = resolved;
        preferredColumnCodePoints = -1;
        resetCaretBlink();
    }

    private void resetCaretBlink() {
        caretBlinkResetNanos = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos();
    }

    private void updateRenderedViewportMetrics(DocumentCustomRenderSurface surface, ElementNode renderLayer) {
        int contentLeft = surface.getContentLeft();
        int contentTop = surface.getContentTop();
        int contentRight = surface.getContentRight();
        int contentBottom = surface.getContentBottom();
        DocumentElementBounds viewportBounds = surface.boundsOf(element);
        DocumentElementBounds layerBounds = surface.boundsOf(renderLayer);
        int offsetX = layerBounds.isAvailable() ? contentLeft - layerBounds.getContentLeft() : 0;
        int offsetY = layerBounds.isAvailable() ? contentTop - layerBounds.getContentTop() : 0;
        viewportContentLeft = contentLeft - offsetX + surface.scrollLeftOf(element);
        viewportContentTop = contentTop - offsetY + surface.scrollTopOf(element);
        viewportContentWidth = viewportBounds.isAvailable() ? viewportBounds.getContentWidth()
                : Math.max(0, contentRight - contentLeft);
        viewportContentHeight = viewportBounds.isAvailable() ? viewportBounds.getContentHeight()
                : Math.max(0, contentBottom - contentTop);
    }

    private void renderGutter(DocumentCustomRenderSurface surface) {
        UiRenderContext context = surface.getContext();
        if (context == null || gutterWidth <= 0) {
            return;
        }
        int scrollLeft = surface.scrollLeftOf(element);
        int scrollTop = surface.scrollTopOf(element);
        DocumentElementBounds viewportBounds = surface.boundsOf(element);
        if (!viewportBounds.isAvailable()) {
            return;
        }
        int viewportTop = viewportBounds.getContentTop() - scrollTop;
        int viewportBottom = viewportTop + viewportBounds.getContentHeight();
        int lineHeight = resolveLineHeight(context);
        int totalLines = logicalLines.size();
        int gutterLeft = viewportBounds.getContentLeft() - scrollLeft;
        int firstLine = Math.max(0, scrollTop / lineHeight);
        int lastExclusive = Math.min(totalLines, (scrollTop + viewportBounds.getContentHeight() + lineHeight - 1)
                / lineHeight);
        for (int lineIndex = firstLine; lineIndex < lastExclusive; lineIndex++) {
            int lineTop = viewportTop + lineIndex * lineHeight;
            if (lineTop + lineHeight <= viewportBounds.getContentTop()) {
                continue;
            }
            if (lineTop >= viewportBottom) {
                break;
            }
            boolean isError = errorLines.contains(lineIndex);
            if (isError) {
                context.fillRect(gutterLeft, lineTop, gutterLeft + gutterWidth, lineTop + lineHeight,
                        errorLineBackgroundColor);
            }
            String label = formatLineNumber(lineIndex + 1, totalLines);
            int textColor = isError ? errorGutterTextColor : gutterTextColor;
            int textY = lineTop + (lineHeight - context.getTextLineHeight()) / 2;
            int labelWidth = context.measureTextWidth(label, TextContentMode.UILIB_RAW);
            int padded = Math.max(2, gutterWidth - 6);
            int labelX = gutterLeft + Math.max(2, padded - labelWidth);
            context.drawText(label, labelX, textY, textColor, false, TextContentMode.UILIB_RAW);
        }
    }

    private void renderSelection(DocumentCustomRenderSurface surface) {
        UiRenderContext context = surface.getContext();
        if (context == null || !hasSelection() || !focused || !enabled) {
            return;
        }
        DocumentElementBounds viewportBounds = surface.boundsOf(element);
        if (!viewportBounds.isAvailable()) {
            return;
        }
        List<VisualLineLayout> lines = ensureVisualLines(context);
        int lineHeight = resolveLineHeight(context);
        int scrollLeft = surface.scrollLeftOf(element);
        int scrollTop = surface.scrollTopOf(element);
        int viewportTop = viewportBounds.getContentTop() - scrollTop;
        int textLeft = viewportBounds.getContentLeft() + gutterWidth - scrollLeft;
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        int startLine = resolveLineIndexForCaret(selectionStart);
        int endLine = resolveLineIndexForCaret(selectionEnd);
        for (int lineIndex = startLine; lineIndex <= endLine; lineIndex++) {
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                continue;
            }
            VisualLineLayout visualLine = lines.get(lineIndex);
            int textLength = visualLine.getText().length();
            int localStart = lineIndex == startLine ? selectionStart - visualLine.getVisualStartIndex() : 0;
            int localEnd = lineIndex == endLine ? selectionEnd - visualLine.getVisualStartIndex() : textLength;
            localStart = Math.max(0, Math.min(localStart, textLength));
            localEnd = Math.max(localStart, Math.min(localEnd, textLength));
            int startX = textLeft + visualLine.resolveBoundaryX(localStart);
            int endX = textLeft + visualLine.resolveBoundaryX(localEnd);
            if (startX == endX) {
                continue;
            }
            int lineTop = viewportTop + lineIndex * lineHeight;
            context.fillRect(startX, lineTop, endX, lineTop + lineHeight, selectionColor);
        }
    }

    private void renderCaret(DocumentCustomRenderSurface surface) {
        UiRenderContext context = surface.getContext();
        if (context == null || !focused || !enabled) {
            return;
        }
        long elapsed = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos() - caretBlinkResetNanos;
        if (elapsed < 0) {
            elapsed = 0;
        }
        if ((elapsed / BLINK_PERIOD_NANOS) % 2 != 0) {
            return;
        }
        DocumentElementBounds viewportBounds = surface.boundsOf(element);
        if (!viewportBounds.isAvailable()) {
            return;
        }
        List<VisualLineLayout> lines = ensureVisualLines(context);
        int lineHeight = resolveLineHeight(context);
        int scrollLeft = surface.scrollLeftOf(element);
        int scrollTop = surface.scrollTopOf(element);
        int viewportTop = viewportBounds.getContentTop() - scrollTop;
        int textLeft = viewportBounds.getContentLeft() + gutterWidth - scrollLeft;
        int lineIndex = resolveLineIndexForCaret(caretIndex);
        int caretX = textLeft;
        if (lineIndex >= 0 && lineIndex < lines.size()) {
            VisualLineLayout visualLine = lines.get(lineIndex);
            int localOffset = Math.max(0, Math.min(caretIndex - visualLine.getVisualStartIndex(),
                    visualLine.getText().length()));
            caretX = textLeft + visualLine.resolveBoundaryX(localOffset);
        }
        int caretTop = viewportTop + lineIndex * lineHeight;
        context.fillRect(caretX, caretTop, caretX + DEFAULT_CARET_WIDTH, caretTop + lineHeight,
                enabled ? caretColor : disabledTextColor);
    }

    /**
     * 通过共享布局引擎刷新（或复用）视觉行。
     *
     * <p>源码编辑器不软换行，引擎以“每逻辑行一条视觉行”模式工作，并按内容与字体测量纪元缓存；稳态下选区层与
     * caret 层在同一帧内复用同一结果实例，不再每帧逐前缀 {@code measureTextWidth(substring)} 测量。</p>
     *
     * @param context 渲染上下文
     * @return 视觉行布局列表
     */
    private List<VisualLineLayout> ensureVisualLines(UiRenderContext context) {
        TextMeasureFunction measure = new TextMeasureFunction() {
            @Override
            public int widthOf(String text) {
                return context.measureTextWidth(text, TextContentMode.UILIB_RAW);
            }

            @Override
            public int[] prefixWidths(String text) {
                return context.measurePrefixWidths(text);
            }
        };
        visualLines = textLayoutEngine.layout(layoutLines, 0, context.getTextMeasureEpoch(),
                resolveLineHeight(context), false, measure);
        return visualLines;
    }

    private static int resolveLineHeight(UiRenderContext context) {
        if (context == null) {
            return DEFAULT_LINE_HEIGHT;
        }
        int measured = context.getTextLineHeight();
        return measured > 0 ? measured : DEFAULT_LINE_HEIGHT;
    }

    private static String formatLineNumber(int lineNumber, int totalLines) {
        int width = Math.max(1, String.valueOf(Math.max(1, totalLines)).length());
        return String.format(Locale.ENGLISH, "%0" + width + "d", lineNumber);
    }

    private int resolveLineIndexForCaret(int targetCaretIndex) {
        for (int index = 0; index < logicalLines.size(); index++) {
            LogicalLine line = logicalLines.get(index);
            if (targetCaretIndex >= line.startIndex && targetCaretIndex <= line.endIndex) {
                return index;
            }
        }
        return Math.max(0, logicalLines.size() - 1);
    }

    private int resolveCaretIndexAtDocumentPoint(int documentX, int documentY) {
        int lineIndex = Math.max(0, Math.min(logicalLines.size() - 1, documentY / DEFAULT_LINE_HEIGHT));
        LogicalLine line = logicalLines.get(lineIndex);
        int x = documentX - gutterWidth;
        if (x <= 0 || line.text.isEmpty()) {
            return line.startIndex;
        }
        int lastWidth = 0;
        int lastOffset = 0;
        for (int offset = 1; offset <= line.text.length(); offset++) {
            int width = estimateTextWidth(line.text.substring(0, offset));
            if (width > x) {
                int deltaCurrent = Math.abs(width - x);
                int deltaPrevious = Math.abs(x - lastWidth);
                return deltaPrevious <= deltaCurrent ? line.startIndex + lastOffset : line.startIndex + offset;
            }
            lastWidth = width;
            lastOffset = offset;
        }
        return line.endIndex;
    }

    /** 在没有渲染上下文时做粗略估算：每个码点 6 像素。 */
    private static int estimateTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() * 6;
    }

    private boolean hasSelection() {
        return selectionAnchorIndex != caretIndex;
    }

    private int getSelectionStart() {
        return Math.min(selectionAnchorIndex, caretIndex);
    }

    private int getSelectionEnd() {
        return Math.max(selectionAnchorIndex, caretIndex);
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onContentChanged(new DocumentCodeEditorChangeEvent(this, element, textBuilder.toString(),
                    language));
        }
    }

    private void fireErrorUpdate() {
        if (errorHandler != null) {
            errorHandler.onErrorsUpdated(new DocumentCodeEditorErrorUpdateEvent(this, element, errorLines,
                    errorMessage));
        }
    }

    private static Set<Integer> normalizeErrorLines(Set<Integer> source) {
        if (source == null || source.isEmpty()) {
            return Collections.<Integer>emptySet();
        }
        Set<Integer> normalized = new LinkedHashSet<Integer>();
        for (Integer line : source) {
            if (line != null && line >= 0) {
                normalized.add(line);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    /** 单行模型：记录逻辑行在整个文本中的起止 char 索引。 */
    private static final class LogicalLine {

        private final int startIndex;
        private final int endIndex;
        private final String text;

        private LogicalLine(int startIndex, int endIndex, String text) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.text = text == null ? "" : text;
        }
    }

    /**
     * 可见行范围快照。
     */
    public static final class VisibleLineRange {

        private final int firstLine;
        private final int lastExclusiveLine;
        private final int totalLines;

        private VisibleLineRange(int firstLine, int lastExclusiveLine, int totalLines) {
            this.firstLine = firstLine;
            this.lastExclusiveLine = lastExclusiveLine;
            this.totalLines = totalLines;
        }

        /** 返回首行索引（0-based）。 */
        public int getFirstLine() {
            return firstLine;
        }

        /** 返回结束行索引（0-based，开区间）。 */
        public int getLastExclusiveLine() {
            return lastExclusiveLine;
        }

        /** 返回逻辑行总数。 */
        public int getTotalLines() {
            return totalLines;
        }
    }
}
