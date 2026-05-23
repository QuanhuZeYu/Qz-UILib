package club.heiqi.uilib.ui.remote;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentCheckboxControl;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 将服务端安全子集 HTML 转换为 `UiDocument` 的轻量解析器。
 */
final class RemoteHtmlDocumentParser {

    private static final int DEFAULT_TEXT_COLOR = 0xFFE5E7EB;
    private static final int DEFAULT_LINK_COLOR = 0xFF60A5FA;

    private static final String[] DANGEROUS_TAGS = {
            "script", "iframe", "object", "embed", "applet", "canvas", "audio", "video"
    };

    private final String source;
    private final Options options;
    private final UiDocument document;
    private final Deque<StackEntry> stack = new ArrayDeque<StackEntry>();
    private final List<String> warnings = new ArrayList<String>();
    private final Map<String, List<DocumentCheckboxControl>> radioGroups =
            new LinkedHashMap<String, List<DocumentCheckboxControl>>();
    private String title = "";
    private int index;

    private RemoteHtmlDocumentParser(String html, Options options, UiDocument document) {
        this.source = html == null ? "" : html;
        this.options = options == null ? Options.defaults() : options;
        this.document = document == null ? UiDocument.create().useRawTextByDefault() : document.useRawTextByDefault();
        installDefaultStyles(this.document);
        this.stack.push(new StackEntry("document", this.document.getRootElement(), null, false));
    }

    /**
     * 解析远程页面。
     *
     * @param html HTML 文本
     * @param options 解析选项
     * @return 解析结果
     */
    static Result parse(String html, Options options) {
        RemoteHtmlDocumentParser parser = new RemoteHtmlDocumentParser(html, options, UiDocument.create());
        parser.parse();
        return new Result(parser.document, parser.title, parser.warnings);
    }

    /**
     * 将远程页面解析到调用方提供的文档中。
     *
     * @param document 目标文档
     * @param html HTML 文本
     * @param options 解析选项
     * @return 解析结果
     */
    static Result parseInto(UiDocument document, String html, Options options) {
        RemoteHtmlDocumentParser parser = new RemoteHtmlDocumentParser(html, options, document);
        parser.parse();
        return new Result(parser.document, parser.title, parser.warnings);
    }

    private void parse() {
        while (index < source.length()) {
            int tagStart = source.indexOf('<', index);
            if (tagStart < 0) {
                appendText(source.substring(index));
                index = source.length();
                break;
            }
            if (tagStart > index) {
                appendText(source.substring(index, tagStart));
            }
            if (source.startsWith("<!--", tagStart)) {
                skipComment(tagStart);
                continue;
            }
            TagToken tag = readTag(tagStart);
            if (tag == null) {
                appendText("<");
                index = tagStart + 1;
                continue;
            }
            index = tag.endIndex;
            handleTag(tag);
        }
    }

    private void handleTag(TagToken tag) {
        String tagName = tag.name;
        if (tagName.isEmpty()) {
            return;
        }
        if (tag.closing) {
            closeTag(tagName);
            return;
        }
        if ("title".equals(tagName)) {
            readRawTextElement("title");
            return;
        }
        if ("style".equals(tagName)) {
            readStyleElement();
            return;
        }
        if (isDangerousTag(tagName)) {
            warn("已忽略危险标签 <" + tagName + ">");
            skipUntilClose(tagName);
            return;
        }
        if ("html".equals(tagName) || "body".equals(tagName) || "head".equals(tagName)) {
            stack.push(new StackEntry(tagName, null, currentForm(), false));
            return;
        }
        if ("br".equals(tagName)) {
            appendText("\n");
            return;
        }
        if ("select".equals(tagName)) {
            handleSelect(tag);
            return;
        }
        if ("textarea".equals(tagName)) {
            handleTextarea(tag);
            return;
        }
        ElementNode element = createElement(tag);
        if (element == null) {
            if (!tag.selfClosing) {
                stack.push(new StackEntry(tagName, null, currentForm(), false));
            }
            return;
        }
        appendNode(element);
        if (!tag.selfClosing && !isVoidTag(tagName)) {
            stack.push(new StackEntry(tagName, element, resolveFormForElement(tagName, element, tag), false));
        }
    }

    private ElementNode createElement(TagToken tag) {
        String tagName = tag.name;
        if ("form".equals(tagName)) {
            ElementNode form = document.element("form");
            applyAttributes(form, tag.attributes);
            applyFormDefaults(form);
            return form;
        }
        if ("input".equals(tagName)) {
            return createInput(tag);
        }
        if ("button".equals(tagName)) {
            return createButton(tag);
        }
        if ("img".equals(tagName)) {
            ElementNode image = document.img();
            applyAttributes(image, tag.attributes);
            sanitizeImageSource(image);
            return image;
        }
        if ("hr".equals(tagName)) {
            ElementNode rule = document.div();
            rule.setAttribute("role", "separator");
            rule.style()
                    .setHeight(UiStyleLength.px(1))
                    .setMargin(UiStyleInsets.symmetric(UiStyleLength.px(8), UiStyleLength.px(0)))
                    .setBackgroundColor(0xFF475569);
            applyAttributes(rule, tag.attributes);
            return rule;
        }
        if ("strong".equals(tagName) || "b".equals(tagName) || "em".equals(tagName) || "i".equals(tagName)) {
            ElementNode span = document.span();
            if ("strong".equals(tagName) || "b".equals(tagName)) {
                span.style().setFontWeight(UiFontWeight.BOLD);
            } else {
                span.style().setFontStyle(UiFontStyle.ITALIC);
            }
            applyAttributes(span, tag.attributes);
            return span;
        }
        if (!isSupportedVisibleTag(tagName)) {
            warn("已忽略不支持标签 <" + tagName + ">，保留其安全子内容");
            return null;
        }
        ElementNode element = document.element(tagName);
        applyAttributes(element, tag.attributes);
        if ("a".equals(tagName)) {
            sanitizeLinkHref(element);
        }
        return element;
    }

    private ElementNode createInput(TagToken tag) {
        String type = normalizeAttribute(tag.attributes.get("type"), "text").toLowerCase(Locale.ROOT);
        if ("hidden".equals(type)) {
            RemoteDocumentFormController.FormState form = currentForm();
            if (form != null) {
                form.addHidden(tag.attributes.get("name"), tag.attributes.get("value"),
                        hasBooleanAttribute(tag.attributes, "disabled"));
            }
            return null;
        }
        if ("checkbox".equals(type)) {
            return createCheckboxLikeInput(tag, false);
        }
        if ("radio".equals(type)) {
            return createCheckboxLikeInput(tag, true);
        }
        if ("submit".equals(type) || "button".equals(type)) {
            return createInputButton(tag, "submit".equals(type));
        }
        DocumentTextInputControl control = new DocumentTextInputControl(document)
                .setText(tag.attributes.get("value"))
                .setPlaceholder(tag.attributes.get("placeholder"))
                .setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"))
                .setReadOnly(hasBooleanAttribute(tag.attributes, "readonly"))
                .setRequired(hasBooleanAttribute(tag.attributes, "required"));
        applyMaxLength(control, tag.attributes.get("maxlength"));
        ElementNode element = control.getElement();
        applyAttributes(element, tag.attributes);
        element.setAttribute("type", "text");
        RemoteDocumentFormController.FormState form = currentForm();
        if (form != null) {
            form.addText(tag.attributes.get("name"), hasBooleanAttribute(tag.attributes, "disabled"), control);
        }
        return element;
    }

    private ElementNode createCheckboxLikeInput(TagToken tag, boolean radio) {
        String label = tag.attributes.get("data-label");
        DocumentCheckboxControl control = new DocumentCheckboxControl(document, label == null ? "" : label)
                .setChecked(hasBooleanAttribute(tag.attributes, "checked"))
                .setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"));
        ElementNode element = control.getElement();
        element.setAttribute("role", radio ? "radio" : "checkbox");
        element.setAttribute("type", radio ? "radio" : "checkbox");
        applyAttributes(element, tag.attributes);
        RemoteDocumentFormController.FormState form = currentForm();
        if (form != null) {
            if (radio) {
                form.addRadio(tag.attributes.get("name"), tag.attributes.get("value"),
                        hasBooleanAttribute(tag.attributes, "disabled"), control);
                installRadioBehavior(control, tag.attributes.get("name"), form.getFormId(),
                        hasBooleanAttribute(tag.attributes, "disabled"));
            } else {
                form.addCheckbox(tag.attributes.get("name"), tag.attributes.get("value"),
                        hasBooleanAttribute(tag.attributes, "disabled"), control);
            }
        }
        return element;
    }

    private ElementNode createInputButton(TagToken tag, boolean submit) {
        DocumentButtonControl button = new DocumentButtonControl(document,
                normalizeAttribute(tag.attributes.get("value"), submit ? "提交" : ""));
        button.setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"));
        ElementNode element = button.getElement();
        element.setAttribute("type", submit ? "submit" : "button");
        applyAttributes(element, tag.attributes);
        RemoteDocumentFormController.FormState form = currentForm();
        if (submit && form != null) {
            form.installSubmitButton(button, new RemoteDocumentFormController.Submitter(tag.attributes.get("name"),
                    tag.attributes.get("value"), hasBooleanAttribute(tag.attributes, "disabled")));
        }
        return element;
    }

    private ElementNode createButton(TagToken tag) {
        String type = normalizeAttribute(tag.attributes.get("type"), "submit").toLowerCase(Locale.ROOT);
        String label = normalizeAttribute(tag.attributes.get("value"), "");
        DocumentButtonControl button = new DocumentButtonControl(document, label);
        button.setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"));
        ElementNode element = button.getElement();
        element.setAttribute("type", type);
        applyAttributes(element, tag.attributes);
        RemoteDocumentFormController.FormState form = currentForm();
        if (form != null && !"button".equals(type) && !"reset".equals(type)) {
            form.installSubmitButton(button, new RemoteDocumentFormController.Submitter(tag.attributes.get("name"),
                    tag.attributes.get("value"), hasBooleanAttribute(tag.attributes, "disabled")));
        }
        return element;
    }

    private void handleTextarea(TagToken tag) {
        String rawText = readRawTextElement("textarea");
        DocumentTextAreaControl control = new DocumentTextAreaControl(document)
                .setText(decodeEntities(rawText))
                .setPlaceholder(tag.attributes.get("placeholder"))
                .setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"))
                .setReadOnly(hasBooleanAttribute(tag.attributes, "readonly"))
                .setRequired(hasBooleanAttribute(tag.attributes, "required"));
        applyTextAreaMaxLength(control, tag.attributes.get("maxlength"));
        ElementNode element = control.getElement();
        applyAttributes(element, tag.attributes);
        appendNode(element);
        RemoteDocumentFormController.FormState form = currentForm();
        if (form != null) {
            form.addTextarea(tag.attributes.get("name"), hasBooleanAttribute(tag.attributes, "disabled"), control);
        }
    }

    private void handleSelect(TagToken tag) {
        String optionHtml = readRawTextElement("select");
        List<SelectOption> options = parseSelectOptions(optionHtml);
        if (options.isEmpty()) {
            options.add(new SelectOption("", "", false));
        }
        String[] labels = new String[options.size()];
        List<String> values = new ArrayList<String>(options.size());
        int selectedIndex = 0;
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            SelectOption option = options.get(optionIndex);
            labels[optionIndex] = option.label;
            values.add(option.value);
            if (option.selected) {
                selectedIndex = optionIndex;
            }
        }
        DocumentSelectControl control = new DocumentSelectControl(document, labels)
                .setSelectedIndex(selectedIndex)
                .setEnabled(!hasBooleanAttribute(tag.attributes, "disabled"));
        ElementNode element = control.getElement();
        applyAttributes(element, tag.attributes);
        appendNode(element);
        RemoteDocumentFormController.FormState form = currentForm();
        if (form != null) {
            form.addSelect(tag.attributes.get("name"), hasBooleanAttribute(tag.attributes, "disabled"), control, values);
        }
    }

    private RemoteDocumentFormController.FormState resolveFormForElement(String tagName, ElementNode element,
            TagToken tag) {
        if (!"form".equals(tagName)) {
            return currentForm();
        }
        String formId = normalizeAttribute(element.getId(), tag.attributes.get("name"));
        String action = normalizeAttribute(tag.attributes.get("data-action"), tag.attributes.get("action"));
        return new RemoteDocumentFormController.FormState(options.sessionId, options.pageId, action, formId);
    }

    private void applyAttributes(ElementNode element, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isEmpty() || name.startsWith("on")) {
                continue;
            }
            if ("style".equals(name)) {
                RemoteCssParser.applyDeclaration(element.style(), value);
            } else if ("class".equals(name)) {
                element.setClassName(value);
            } else if ("id".equals(name)) {
                element.setId(value);
            } else if (isAllowedAttribute(name)) {
                element.setAttribute(name, value == null ? "" : value);
            }
        }
    }

    private void sanitizeLinkHref(ElementNode element) {
        String href = element.getAttribute("href");
        if (href == null) {
            return;
        }
        String trimmed = href.trim();
        if (trimmed.startsWith("#")) {
            element.setAttribute("href", trimmed);
            return;
        }
        if (isHttpUrl(trimmed) && options.resourcePolicy.allowsExternalLinks()) {
            element.setAttribute("href", trimmed);
            return;
        }
        element.removeAttribute("href");
        element.setAttribute("data-qz-blocked-href", trimmed);
        if (isExecutableProtocol(trimmed)) {
            warn("已阻止可执行链接协议：" + trimmed);
        }
    }

    private void sanitizeImageSource(ElementNode element) {
        String src = element.getAttribute("src");
        if (src == null) {
            return;
        }
        String trimmed = src.trim();
        if (isHttpUrl(trimmed) && options.resourcePolicy.allowsHttpImages()) {
            element.setAttribute("src", trimmed);
            return;
        }
        if (!isHttpUrl(trimmed) && !trimmed.contains("://")) {
            element.setAttribute("src", trimmed);
            return;
        }
        element.removeAttribute("src");
        element.setAttribute("data-qz-blocked-src", trimmed);
        warn("已按资源策略阻止图片：" + trimmed);
    }

    private void installRadioBehavior(final DocumentCheckboxControl control, String name, String formId,
            final boolean disabled) {
        final List<DocumentCheckboxControl> group = radioGroup(formId, name);
        group.add(control);
        control.getElement().setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (disabled || event.getButton() != 0) {
                    return false;
                }
                selectRadio(group, control);
                return true;
            }
        });
        control.getElement().setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (disabled || event.getAction() != UiKeyEvent.Action.PRESSED) {
                    return false;
                }
                if (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE) {
                    selectRadio(group, control);
                    return true;
                }
                return false;
            }
        });
        if (control.isChecked()) {
            selectRadio(group, control);
        }
    }

    private List<DocumentCheckboxControl> radioGroup(String formId, String name) {
        String key = normalizeAttribute(formId, "") + ":" + normalizeAttribute(name, "");
        List<DocumentCheckboxControl> group = radioGroups.get(key);
        if (group == null) {
            group = new ArrayList<DocumentCheckboxControl>();
            radioGroups.put(key, group);
        }
        return group;
    }

    private static void selectRadio(List<DocumentCheckboxControl> group, DocumentCheckboxControl selected) {
        for (DocumentCheckboxControl item : group) {
            item.setChecked(item == selected);
        }
    }

    private void closeTag(String tagName) {
        while (stack.size() > 1) {
            StackEntry entry = stack.pop();
            if (entry.tagName.equals(tagName)) {
                return;
            }
        }
    }

    private void appendText(String rawText) {
        String text = decodeEntities(rawText);
        if (text.isEmpty() || currentElement() == null || isInsideHead()) {
            return;
        }
        currentElement().appendRawText(text);
    }

    private void appendNode(DocumentNode node) {
        ElementNode parent = currentElement();
        if (parent != null && node != null) {
            parent.appendChild(node);
        }
    }

    private ElementNode currentElement() {
        for (StackEntry entry : stack) {
            if (entry.element != null) {
                return entry.element;
            }
        }
        return document.getRootElement();
    }

    private RemoteDocumentFormController.FormState currentForm() {
        for (StackEntry entry : stack) {
            if (entry.form != null) {
                return entry.form;
            }
        }
        return null;
    }

    private boolean isInsideHead() {
        for (StackEntry entry : stack) {
            if ("head".equals(entry.tagName)) {
                return true;
            }
        }
        return false;
    }

    private String readRawTextElement(String tagName) {
        int closeStart = indexOfClosingTag(tagName, index);
        if (closeStart < 0) {
            String text = source.substring(index);
            index = source.length();
            if ("title".equals(tagName)) {
                title = decodeEntities(text).trim();
            }
            return text;
        }
        String text = source.substring(index, closeStart);
        int closeEnd = source.indexOf('>', closeStart);
        index = closeEnd < 0 ? source.length() : closeEnd + 1;
        if ("title".equals(tagName)) {
            title = decodeEntities(text).trim();
        }
        return text;
    }

    private void readStyleElement() {
        String css = readRawTextElement("style");
        UiStyleSheet sheet = RemoteCssParser.parseStyleSheet(css);
        if (sheet.getRuleCount() > 0) {
            document.addStyleSheet(sheet);
        }
    }

    private void skipUntilClose(String tagName) {
        int closeStart = indexOfClosingTag(tagName, index);
        if (closeStart < 0) {
            index = source.length();
            return;
        }
        int closeEnd = source.indexOf('>', closeStart);
        index = closeEnd < 0 ? source.length() : closeEnd + 1;
    }

    private int indexOfClosingTag(String tagName, int start) {
        String needle = "</" + tagName.toLowerCase(Locale.ROOT);
        String lower = source.toLowerCase(Locale.ROOT);
        int searchIndex = start;
        while (searchIndex < lower.length()) {
            int found = lower.indexOf(needle, searchIndex);
            if (found < 0) {
                return -1;
            }
            int afterName = found + needle.length();
            if (afterName >= lower.length() || Character.isWhitespace(lower.charAt(afterName))
                    || lower.charAt(afterName) == '>') {
                return found;
            }
            searchIndex = afterName;
        }
        return -1;
    }

    private void skipComment(int commentStart) {
        int end = source.indexOf("-->", commentStart + 4);
        index = end < 0 ? source.length() : end + 3;
    }

    private TagToken readTag(int tagStart) {
        int cursor = tagStart + 1;
        boolean closing = false;
        if (cursor < source.length() && source.charAt(cursor) == '/') {
            closing = true;
            cursor++;
        }
        cursor = skipWhitespace(cursor);
        int nameStart = cursor;
        while (cursor < source.length() && isNameChar(source.charAt(cursor))) {
            cursor++;
        }
        if (cursor == nameStart) {
            return null;
        }
        String name = source.substring(nameStart, cursor).toLowerCase(Locale.ROOT);
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        boolean selfClosing = false;
        while (cursor < source.length()) {
            cursor = skipWhitespace(cursor);
            if (cursor >= source.length()) {
                return null;
            }
            char ch = source.charAt(cursor);
            if (ch == '>') {
                cursor++;
                break;
            }
            if (ch == '/' && cursor + 1 < source.length() && source.charAt(cursor + 1) == '>') {
                selfClosing = true;
                cursor += 2;
                break;
            }
            AttributeToken attribute = readAttribute(cursor);
            if (attribute == null) {
                int nextClose = source.indexOf('>', cursor);
                if (nextClose < 0) {
                    return null;
                }
                cursor = nextClose + 1;
                break;
            }
            attributes.put(attribute.name, decodeEntities(attribute.value));
            cursor = attribute.endIndex;
        }
        return new TagToken(name, closing, selfClosing, attributes, cursor);
    }

    private AttributeToken readAttribute(int start) {
        int cursor = start;
        int nameStart = cursor;
        while (cursor < source.length() && isAttributeNameChar(source.charAt(cursor))) {
            cursor++;
        }
        if (cursor == nameStart) {
            return null;
        }
        String name = source.substring(nameStart, cursor).toLowerCase(Locale.ROOT);
        cursor = skipWhitespace(cursor);
        if (cursor >= source.length() || source.charAt(cursor) != '=') {
            return new AttributeToken(name, "true", cursor);
        }
        cursor++;
        cursor = skipWhitespace(cursor);
        if (cursor >= source.length()) {
            return new AttributeToken(name, "", cursor);
        }
        char quote = source.charAt(cursor);
        if (quote == '\'' || quote == '"') {
            cursor++;
            int valueStart = cursor;
            while (cursor < source.length() && source.charAt(cursor) != quote) {
                cursor++;
            }
            String value = source.substring(valueStart, Math.min(cursor, source.length()));
            if (cursor < source.length()) {
                cursor++;
            }
            return new AttributeToken(name, value, cursor);
        }
        int valueStart = cursor;
        while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))
                && source.charAt(cursor) != '>') {
            cursor++;
        }
        return new AttributeToken(name, source.substring(valueStart, cursor), cursor);
    }

    private List<SelectOption> parseSelectOptions(String html) {
        List<SelectOption> options = new ArrayList<SelectOption>();
        int cursor = 0;
        String lower = html == null ? "" : html.toLowerCase(Locale.ROOT);
        while (cursor < lower.length()) {
            int optionStart = lower.indexOf("<option", cursor);
            if (optionStart < 0) {
                break;
            }
            TagToken optionTag = readDetachedTag(html, optionStart);
            if (optionTag == null || optionTag.closing) {
                cursor = optionStart + 1;
                continue;
            }
            int contentStart = optionTag.endIndex;
            int optionEnd = indexOfClosingTag(lower, "option", contentStart);
            String labelSource;
            if (optionEnd < 0) {
                labelSource = html.substring(contentStart);
                cursor = html.length();
            } else {
                labelSource = html.substring(contentStart, optionEnd);
                int closeEnd = html.indexOf('>', optionEnd);
                cursor = closeEnd < 0 ? html.length() : closeEnd + 1;
            }
            String label = decodeEntities(stripTags(labelSource)).trim();
            String value = optionTag.attributes.containsKey("value") ? optionTag.attributes.get("value") : label;
            options.add(new SelectOption(label, value, hasBooleanAttribute(optionTag.attributes, "selected")));
        }
        return options;
    }

    private TagToken readDetachedTag(String html, int tagStart) {
        RemoteHtmlDocumentParser parser = new RemoteHtmlDocumentParser(html, options, UiDocument.create());
        parser.index = tagStart;
        return parser.readTag(tagStart);
    }

    private static int indexOfClosingTag(String lowerSource, String tagName, int start) {
        String needle = "</" + tagName;
        int found = lowerSource.indexOf(needle, start);
        while (found >= 0) {
            int afterName = found + needle.length();
            if (afterName >= lowerSource.length() || Character.isWhitespace(lowerSource.charAt(afterName))
                    || lowerSource.charAt(afterName) == '>') {
                return found;
            }
            found = lowerSource.indexOf(needle, afterName);
        }
        return -1;
    }

    private static String stripTags(String text) {
        if (text == null || text.indexOf('<') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean insideTag = false;
        for (int cursor = 0; cursor < text.length(); cursor++) {
            char ch = text.charAt(cursor);
            if (ch == '<') {
                insideTag = true;
            } else if (ch == '>') {
                insideTag = false;
            } else if (!insideTag) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    static String decodeEntities(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            char ch = text.charAt(cursor);
            if (ch != '&') {
                builder.append(ch);
                cursor++;
                continue;
            }
            int semicolon = text.indexOf(';', cursor + 1);
            if (semicolon < 0 || semicolon - cursor > 12) {
                builder.append(ch);
                cursor++;
                continue;
            }
            String entity = text.substring(cursor + 1, semicolon);
            String decoded = decodeEntity(entity);
            if (decoded == null) {
                builder.append('&').append(entity).append(';');
            } else {
                builder.append(decoded);
            }
            cursor = semicolon + 1;
        }
        return builder.toString();
    }

    private static String decodeEntity(String entity) {
        if ("amp".equals(entity)) return "&";
        if ("lt".equals(entity)) return "<";
        if ("gt".equals(entity)) return ">";
        if ("quot".equals(entity)) return "\"";
        if ("apos".equals(entity)) return "'";
        if ("nbsp".equals(entity)) return " ";
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return decodeCodePoint(entity.substring(2), 16);
        }
        if (entity.startsWith("#")) {
            return decodeCodePoint(entity.substring(1), 10);
        }
        return null;
    }

    private static String decodeCodePoint(String value, int radix) {
        try {
            int codePoint = Integer.parseInt(value, radix);
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void warn(String warning) {
        warnings.add(warning);
        MyMod.LOG.debug("远程页面解析提示：{}", warning);
    }

    private static void installDefaultStyles(UiDocument document) {
        document.getRootElement().style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setBoxSizing(club.heiqi.uilib.ui.style.props.UiBoxSizing.BORDER_BOX)
                .setPadding(UiStyleLength.px(12))
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(DEFAULT_TEXT_COLOR);
        UiStyleSheet sheet = UiStyleSheet.create();
        sheet.addRule("a", new UiStyleDeclaration()
                .setTextColor(DEFAULT_LINK_COLOR)
                .setTextDecoration(UiTextDecoration.UNDERLINE)
                .setCursor(UiCursor.POINTER));
        sheet.addRule("p", new UiStyleDeclaration()
                .setMargin(UiStyleInsets.vertical(UiStyleLength.px(6))));
        sheet.addRule("h1", headingStyle(12));
        sheet.addRule("h2", headingStyle(10));
        sheet.addRule("h3", headingStyle(8));
        sheet.addRule("h4", headingStyle(6));
        sheet.addRule("h5", headingStyle(4));
        sheet.addRule("h6", headingStyle(2));
        sheet.addRule("ul", new UiStyleDeclaration()
                .setPaddingLeft(UiStyleLength.px(18))
                .setListStyleType(UiListStyleType.DISC));
        sheet.addRule("ol", new UiStyleDeclaration()
                .setPaddingLeft(UiStyleLength.px(18))
                .setListStyleType(UiListStyleType.DECIMAL));
        sheet.addRule("li", new UiStyleDeclaration()
                .setMargin(UiStyleInsets.vertical(UiStyleLength.px(2))));
        sheet.addRule("form", new UiStyleDeclaration()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleInsets.vertical(UiStyleLength.px(8))));
        sheet.addRule("td", tableCellStyle());
        sheet.addRule("th", tableCellStyle().setFontWeight(UiFontWeight.BOLD));
        document.addStyleSheet(sheet);
    }

    private static UiStyleDeclaration headingStyle(int bottomMargin) {
        return new UiStyleDeclaration()
                .setFontWeight(UiFontWeight.BOLD)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0),
                        UiStyleLength.px(bottomMargin), UiStyleLength.px(0)));
    }

    private static UiStyleDeclaration tableCellStyle() {
        return new UiStyleDeclaration()
                .setPadding(UiStyleLength.px(4))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569);
    }

    private static void applyFormDefaults(ElementNode form) {
        form.style().setDisplay(UiDisplay.BLOCK);
    }

    private static boolean isAllowedAttribute(String name) {
        return "href".equals(name) || "target".equals(name) || "src".equals(name) || "alt".equals(name)
                || "title".equals(name) || "role".equals(name) || "name".equals(name) || "value".equals(name)
                || "type".equals(name) || "placeholder".equals(name) || "maxlength".equals(name)
                || "checked".equals(name) || "disabled".equals(name) || "readonly".equals(name)
                || "required".equals(name) || "selected".equals(name) || "action".equals(name)
                || "width".equals(name) || "height".equals(name) || name.startsWith("data-")
                || name.startsWith("aria-");
    }

    private static boolean isSupportedVisibleTag(String tagName) {
        return "div".equals(tagName) || "span".equals(tagName) || "p".equals(tagName) || "a".equals(tagName)
                || "img".equals(tagName) || "form".equals(tagName) || "button".equals(tagName)
                || "table".equals(tagName) || "thead".equals(tagName) || "tbody".equals(tagName)
                || "tfoot".equals(tagName) || "tr".equals(tagName) || "th".equals(tagName)
                || "td".equals(tagName) || "ul".equals(tagName) || "ol".equals(tagName) || "li".equals(tagName)
                || "h1".equals(tagName) || "h2".equals(tagName) || "h3".equals(tagName)
                || "h4".equals(tagName) || "h5".equals(tagName) || "h6".equals(tagName)
                || "strong".equals(tagName) || "b".equals(tagName) || "em".equals(tagName)
                || "i".equals(tagName);
    }

    private static boolean isVoidTag(String tagName) {
        return "input".equals(tagName) || "img".equals(tagName) || "br".equals(tagName) || "hr".equals(tagName)
                || "meta".equals(tagName) || "link".equals(tagName);
    }

    private static boolean isDangerousTag(String tagName) {
        for (String dangerousTag : DANGEROUS_TAGS) {
            if (dangerousTag.equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHttpUrl(String value) {
        return value != null && (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8));
    }

    private static boolean isExecutableProtocol(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:");
    }

    private static boolean hasBooleanAttribute(Map<String, String> attributes, String name) {
        return attributes.containsKey(name);
    }

    private static String normalizeAttribute(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? (fallback == null ? "" : fallback) : value.trim();
    }

    private static void applyMaxLength(DocumentTextInputControl control, String value) {
        if (value == null) {
            return;
        }
        try {
            control.setMaxLength(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            // 非法 maxlength 按 HTML 宽容策略忽略。
        }
    }

    private static void applyTextAreaMaxLength(DocumentTextAreaControl control, String value) {
        if (value == null) {
            return;
        }
        try {
            control.setMaxLength(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            // 非法 maxlength 按 HTML 宽容策略忽略。
        }
    }

    private int skipWhitespace(int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || ch == '_';
    }

    private static boolean isAttributeNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '.';
    }

    /**
     * 解析选项。
     */
    static final class Options {

        private final String sessionId;
        private final String pageId;
        private final RemoteDocumentResourcePolicy resourcePolicy;

        private Options(String sessionId, String pageId, RemoteDocumentResourcePolicy resourcePolicy) {
            this.sessionId = sessionId == null ? "" : sessionId;
            this.pageId = pageId == null ? "" : pageId;
            this.resourcePolicy = resourcePolicy == null
                    ? RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS : resourcePolicy;
        }

        static Options of(String sessionId, String pageId, RemoteDocumentResourcePolicy resourcePolicy) {
            return new Options(sessionId, pageId, resourcePolicy);
        }

        static Options defaults() {
            return new Options("", "", RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS);
        }
    }

    /**
     * 解析结果。
     */
    static final class Result {

        private final UiDocument document;
        private final String title;
        private final List<String> warnings;

        private Result(UiDocument document, String title, List<String> warnings) {
            this.document = document;
            this.title = title == null ? "" : title;
            this.warnings = new ArrayList<String>(warnings);
        }

        UiDocument getDocument() {
            return document;
        }

        String getTitle() {
            return title;
        }

        List<String> getWarnings() {
            return new ArrayList<String>(warnings);
        }
    }

    private static final class StackEntry {

        private final String tagName;
        private final ElementNode element;
        private final RemoteDocumentFormController.FormState form;

        private StackEntry(String tagName, ElementNode element, RemoteDocumentFormController.FormState form,
                boolean ignored) {
            this.tagName = tagName;
            this.element = element;
            this.form = form;
        }
    }

    private static final class TagToken {

        private final String name;
        private final boolean closing;
        private final boolean selfClosing;
        private final Map<String, String> attributes;
        private final int endIndex;

        private TagToken(String name, boolean closing, boolean selfClosing, Map<String, String> attributes,
                int endIndex) {
            this.name = name;
            this.closing = closing;
            this.selfClosing = selfClosing;
            this.attributes = attributes;
            this.endIndex = endIndex;
        }
    }

    private static final class AttributeToken {

        private final String name;
        private final String value;
        private final int endIndex;

        private AttributeToken(String name, String value, int endIndex) {
            this.name = name;
            this.value = value;
            this.endIndex = endIndex;
        }
    }

    private static final class SelectOption {

        private final String label;
        private final String value;
        private final boolean selected;

        private SelectOption(String label, String value, boolean selected) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.selected = selected;
        }
    }
}
