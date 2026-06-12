package club.heiqi.uilib.ui.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignContent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiAlignSelf;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.props.UiObjectFit;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiVerticalAlign;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiStyleKeyword;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 远程 HTML 安全子集使用的 CSS 白名单解析器。
 */
final class RemoteCssParser {

    private static final Map<String, Integer> NAMED_COLORS = createNamedColors();

    private RemoteCssParser() {}

    /**
     * 解析 `<style>` 文本为 UILib 样式表。
     *
     * @param css CSS 文本
     * @return 样式表
     */
    static UiStyleSheet parseStyleSheet(String css) {
        UiStyleSheet sheet = UiStyleSheet.create();
        String source = stripComments(css);
        int index = 0;
        while (index < source.length()) {
            int braceStart = source.indexOf('{', index);
            if (braceStart < 0) {
                break;
            }
            int braceEnd = findRuleEnd(source, braceStart + 1);
            if (braceEnd < 0) {
                break;
            }
            String selectorText = source.substring(index, braceStart).trim();
            String declarationText = source.substring(braceStart + 1, braceEnd);
            UiStyleDeclaration declaration = parseDeclaration(declarationText);
            if (!selectorText.isEmpty()) {
                for (String selector : splitTopLevel(selectorText, ',')) {
                    String trimmedSelector = selector.trim();
                    if (trimmedSelector.isEmpty()) {
                        continue;
                    }
                    try {
                        sheet.addRule(trimmedSelector, new UiStyleDeclaration().copyFrom(declaration));
                    } catch (IllegalArgumentException exception) {
                        MyMod.LOG.debug("忽略远程页面不支持的 CSS 选择器：{}", trimmedSelector, exception);
                    }
                }
            }
            index = braceEnd + 1;
        }
        return sheet;
    }

    /**
     * 解析 inline style 或 CSS rule 的声明块。
     *
     * @param styleText 声明文本
     * @return 声明
     */
    static UiStyleDeclaration parseDeclaration(String styleText) {
        UiStyleDeclaration declaration = new UiStyleDeclaration();
        applyDeclaration(declaration, styleText);
        return declaration;
    }

    /**
     * 将声明文本叠加到既有样式声明上。
     *
     * @param declaration 目标声明
     * @param styleText 声明文本
     */
    static void applyDeclaration(UiStyleDeclaration declaration, String styleText) {
        if (styleText == null || styleText.trim().isEmpty()) {
            return;
        }
        for (String item : splitTopLevel(styleText, ';')) {
            int colonIndex = findTopLevelColon(item);
            if (colonIndex <= 0) {
                continue;
            }
            String propertyName = item.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String propertyValue = item.substring(colonIndex + 1).trim();
            boolean important = endsWithImportant(propertyValue);
            if (important) {
                propertyValue = propertyValue.substring(0,
                        propertyValue.toLowerCase(Locale.ROOT).lastIndexOf("!important")).trim();
            }
            try {
                UiStyleProperty property = applyProperty(declaration, propertyName, propertyValue);
                if (important && property != null) {
                    declaration.setImportant(property);
                }
            } catch (IllegalArgumentException exception) {
                MyMod.LOG.debug("忽略远程页面不支持的 CSS 声明：{}: {}", propertyName, propertyValue, exception);
            }
        }
    }

    private static UiStyleProperty applyProperty(UiStyleDeclaration declaration, String propertyName,
            String propertyValue) {
        if (propertyName == null || propertyValue == null || propertyValue.trim().isEmpty()) {
            return null;
        }
        String value = propertyValue.trim();
        switch (propertyName) {
            case "display":
                declaration.setDisplay(parseDisplay(value));
                return UiStyleProperty.DISPLAY;
            case "width":
                declaration.setWidth(parseLength(value, true));
                return UiStyleProperty.WIDTH;
            case "height":
                declaration.setHeight(parseLength(value, true));
                return UiStyleProperty.HEIGHT;
            case "min-width":
                declaration.setMinWidth(parseLength(value, false));
                return UiStyleProperty.MIN_WIDTH;
            case "max-width":
                declaration.setMaxWidth(parseLength(value, true));
                return UiStyleProperty.MAX_WIDTH;
            case "min-height":
                declaration.setMinHeight(parseLength(value, false));
                return UiStyleProperty.MIN_HEIGHT;
            case "max-height":
                declaration.setMaxHeight(parseLength(value, true));
                return UiStyleProperty.MAX_HEIGHT;
            case "box-sizing":
                declaration.setBoxSizing(parseEnum(value, UiBoxSizing.class));
                return UiStyleProperty.BOX_SIZING;
            case "position":
                declaration.setPosition(parseEnum(value, UiPositionAlias.class).toPosition());
                return UiStyleProperty.POSITION;
            case "top":
                declaration.setTop(parseLength(value, true));
                return UiStyleProperty.TOP;
            case "right":
                declaration.setRight(parseLength(value, true));
                return UiStyleProperty.RIGHT;
            case "bottom":
                declaration.setBottom(parseLength(value, true));
                return UiStyleProperty.BOTTOM;
            case "left":
                declaration.setLeft(parseLength(value, true));
                return UiStyleProperty.LEFT;
            case "z-index":
                declaration.setZIndex(parseInteger(value));
                return UiStyleProperty.Z_INDEX;
            case "margin":
                declaration.setMargin(parseInsets(value, true));
                return UiStyleProperty.MARGIN;
            case "margin-top":
                declaration.setMarginTop(parseLength(value, true));
                return UiStyleProperty.MARGIN;
            case "margin-right":
                declaration.setMarginRight(parseLength(value, true));
                return UiStyleProperty.MARGIN;
            case "margin-bottom":
                declaration.setMarginBottom(parseLength(value, true));
                return UiStyleProperty.MARGIN;
            case "margin-left":
                declaration.setMarginLeft(parseLength(value, true));
                return UiStyleProperty.MARGIN;
            case "padding":
                declaration.setPadding(parseInsets(value, false));
                return UiStyleProperty.PADDING;
            case "padding-top":
                declaration.setPaddingTop(parseLength(value, false));
                return UiStyleProperty.PADDING;
            case "padding-right":
                declaration.setPaddingRight(parseLength(value, false));
                return UiStyleProperty.PADDING;
            case "padding-bottom":
                declaration.setPaddingBottom(parseLength(value, false));
                return UiStyleProperty.PADDING;
            case "padding-left":
                declaration.setPaddingLeft(parseLength(value, false));
                return UiStyleProperty.PADDING;
            case "border":
                applyBorderShorthand(declaration, value);
                return UiStyleProperty.BORDER_WIDTH;
            case "border-width":
                declaration.setBorderWidth(parseLength(value, false));
                return UiStyleProperty.BORDER_WIDTH;
            case "border-style":
                declaration.setBorderStyle(parseEnum(value, UiBorderStyle.class));
                return UiStyleProperty.BORDER_STYLE;
            case "border-color":
                declaration.setBorderColor(parseColor(value));
                return UiStyleProperty.BORDER_COLOR;
            case "border-radius":
                declaration.setBorderRadius(parseLength(value, false));
                return UiStyleProperty.BORDER_RADIUS;
            case "border-collapse":
                declaration.setBorderCollapse(parseEnum(value, UiBorderCollapse.class));
                return UiStyleProperty.BORDER_COLLAPSE;
            case "overflow":
                UiOverflow overflow = parseEnum(value, UiOverflow.class);
                declaration.setOverflowX(overflow).setOverflowY(overflow);
                return UiStyleProperty.OVERFLOW_X;
            case "overflow-x":
                declaration.setOverflowX(parseEnum(value, UiOverflow.class));
                return UiStyleProperty.OVERFLOW_X;
            case "overflow-y":
                declaration.setOverflowY(parseEnum(value, UiOverflow.class));
                return UiStyleProperty.OVERFLOW_Y;
            case "flex-direction":
                declaration.setFlexDirection(parseEnum(value, UiFlexDirection.class));
                return UiStyleProperty.FLEX_DIRECTION;
            case "flex-wrap":
                declaration.setFlexWrap(parseEnum(value, UiFlexWrap.class));
                return UiStyleProperty.FLEX_WRAP;
            case "flex-grow":
                declaration.setFlexGrow(parseFloat(value));
                return UiStyleProperty.FLEX_GROW;
            case "flex-shrink":
                declaration.setFlexShrink(parseFloat(value));
                return UiStyleProperty.FLEX_SHRINK;
            case "flex-basis":
                declaration.setFlexBasis(parseLength(value, true));
                return UiStyleProperty.FLEX_BASIS;
            case "order":
                declaration.setOrder(parseInteger(value));
                return UiStyleProperty.ORDER;
            case "align-items":
                declaration.setAlignItems(parseAlignItems(value));
                return UiStyleProperty.ALIGN_ITEMS;
            case "align-content":
                declaration.setAlignContent(parseAlignContent(value));
                return UiStyleProperty.ALIGN_CONTENT;
            case "align-self":
                declaration.setAlignSelf(parseAlignSelf(value));
                return UiStyleProperty.ALIGN_SELF;
            case "justify-content":
                declaration.setJustifyContent(parseJustifyContent(value));
                return UiStyleProperty.JUSTIFY_CONTENT;
            case "vertical-align":
                declaration.setVerticalAlign(parseEnum(value, UiVerticalAlign.class));
                return UiStyleProperty.VERTICAL_ALIGN;
            case "gap":
                declaration.setGap(parseLength(value, false));
                return UiStyleProperty.ROW_GAP;
            case "row-gap":
                declaration.setRowGap(parseLength(value, false));
                return UiStyleProperty.ROW_GAP;
            case "column-gap":
                declaration.setColumnGap(parseLength(value, false));
                return UiStyleProperty.COLUMN_GAP;
            case "opacity":
                declaration.setOpacity(clamp(parseFloat(value), 0.0F, 1.0F));
                return UiStyleProperty.OPACITY;
            case "background":
            case "background-color":
                declaration.setBackgroundColor(parseColor(value));
                return UiStyleProperty.BACKGROUND_COLOR;
            case "background-image":
                applyBackgroundImage(declaration, value);
                return UiStyleProperty.BACKGROUND_IMAGE;
            case "color":
                declaration.setTextColor(parseColor(value));
                return UiStyleProperty.TEXT_COLOR;
            case "line-height":
                declaration.setLineHeight(parseLength(value, true));
                return UiStyleProperty.LINE_HEIGHT;
            case "font-size":
                declaration.setFontSize(parseLength(value, false));
                return UiStyleProperty.FONT_SIZE;
            case "text-align":
                declaration.setTextAlign(parseTextAlign(value));
                return UiStyleProperty.TEXT_ALIGN;
            case "white-space":
                declaration.setWhiteSpace(parseEnum(value, UiWhiteSpace.class));
                return UiStyleProperty.WHITE_SPACE;
            case "text-overflow":
                declaration.setTextOverflow(parseEnum(value, UiTextOverflow.class));
                return UiStyleProperty.TEXT_OVERFLOW;
            case "visibility":
                declaration.setVisibility(parseEnum(value, UiVisibility.class));
                return UiStyleProperty.VISIBILITY;
            case "font-weight":
                declaration.setFontWeight(parseFontWeight(value));
                return UiStyleProperty.FONT_WEIGHT;
            case "font-style":
                declaration.setFontStyle(parseEnum(value, UiFontStyle.class));
                return UiStyleProperty.FONT_STYLE;
            case "cursor":
                declaration.setCursor(parseEnum(value, UiCursor.class));
                return UiStyleProperty.CURSOR;
            case "text-decoration":
                declaration.setTextDecoration(parseEnum(firstToken(value), UiTextDecoration.class));
                return UiStyleProperty.TEXT_DECORATION;
            case "object-fit":
                declaration.setObjectFit(parseEnum(value, UiObjectFit.class));
                return UiStyleProperty.OBJECT_FIT;
            case "aspect-ratio":
                declaration.setAspectRatio(parseAspectRatio(value));
                return UiStyleProperty.ASPECT_RATIO;
            case "list-style-type":
                declaration.setListStyleType(parseEnum(value, UiListStyleType.class));
                return UiStyleProperty.LIST_STYLE_TYPE;
            default:
                return null;
        }
    }

    private static UiDisplay parseDisplay(String value) {
        String normalized = normalizeIdentifier(value);
        if ("inline-block".equals(normalized)) {
            return UiDisplay.INLINE_BLOCK;
        }
        if ("table-header-group".equals(normalized)) {
            return UiDisplay.TABLE_HEADER_GROUP;
        }
        if ("table-row-group".equals(normalized)) {
            return UiDisplay.TABLE_ROW_GROUP;
        }
        if ("table-footer-group".equals(normalized)) {
            return UiDisplay.TABLE_FOOTER_GROUP;
        }
        if ("table-row".equals(normalized)) {
            return UiDisplay.TABLE_ROW;
        }
        if ("table-cell".equals(normalized)) {
            return UiDisplay.TABLE_CELL;
        }
        return parseEnum(normalized, UiDisplay.class);
    }

    private static UiAlignItems parseAlignItems(String value) {
        String normalized = normalizeIdentifier(value);
        if ("flex-start".equals(normalized)) {
            return UiAlignItems.START;
        }
        if ("flex-end".equals(normalized)) {
            return UiAlignItems.END;
        }
        return parseEnum(normalized, UiAlignItems.class);
    }

    private static UiAlignContent parseAlignContent(String value) {
        String normalized = normalizeIdentifier(value);
        if ("flex-start".equals(normalized)) {
            return UiAlignContent.START;
        }
        if ("flex-end".equals(normalized)) {
            return UiAlignContent.END;
        }
        return parseEnum(normalized, UiAlignContent.class);
    }

    private static UiAlignSelf parseAlignSelf(String value) {
        String normalized = normalizeIdentifier(value);
        if ("flex-start".equals(normalized)) {
            return UiAlignSelf.START;
        }
        if ("flex-end".equals(normalized)) {
            return UiAlignSelf.END;
        }
        return parseEnum(normalized, UiAlignSelf.class);
    }

    private static UiJustifyContent parseJustifyContent(String value) {
        String normalized = normalizeIdentifier(value);
        if ("flex-start".equals(normalized)) {
            return UiJustifyContent.START;
        }
        if ("flex-end".equals(normalized)) {
            return UiJustifyContent.END;
        }
        return parseEnum(normalized, UiJustifyContent.class);
    }

    private static UiTextAlign parseTextAlign(String value) {
        String normalized = normalizeIdentifier(value);
        if ("left".equals(normalized)) {
            return UiTextAlign.START;
        }
        if ("right".equals(normalized)) {
            return UiTextAlign.END;
        }
        return parseEnum(normalized, UiTextAlign.class);
    }

    private static UiFontWeight parseFontWeight(String value) {
        String normalized = normalizeIdentifier(value);
        if ("bold".equals(normalized) || "bolder".equals(normalized) || "700".equals(normalized)
                || "800".equals(normalized) || "900".equals(normalized)) {
            return UiFontWeight.BOLD;
        }
        return UiFontWeight.NORMAL;
    }

    private static UiStyleInsets parseInsets(String value, boolean allowAuto) {
        List<String> tokens = splitWhitespace(value);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("empty inset");
        }
        if (tokens.size() > 4) {
            tokens = tokens.subList(0, 4);
        }
        UiStyleLength top = parseLength(tokens.get(0), allowAuto);
        UiStyleLength right = tokens.size() > 1 ? parseLength(tokens.get(1), allowAuto) : top;
        UiStyleLength bottom = tokens.size() > 2 ? parseLength(tokens.get(2), allowAuto) : top;
        UiStyleLength left = tokens.size() > 3 ? parseLength(tokens.get(3), allowAuto) : right;
        return UiStyleInsets.of(top, right, bottom, left);
    }

    private static void applyBorderShorthand(UiStyleDeclaration declaration, String value) {
        for (String token : splitWhitespace(value)) {
            if (tryApplyBorderWidth(declaration, token) || tryApplyBorderStyle(declaration, token)
                    || tryApplyBorderColor(declaration, token)) {
                continue;
            }
        }
    }

    private static boolean tryApplyBorderWidth(UiStyleDeclaration declaration, String token) {
        try {
            declaration.setBorderWidth(parseLength(token, false));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean tryApplyBorderStyle(UiStyleDeclaration declaration, String token) {
        try {
            declaration.setBorderStyle(parseEnum(token, UiBorderStyle.class));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean tryApplyBorderColor(UiStyleDeclaration declaration, String token) {
        try {
            declaration.setBorderColor(parseColor(token));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * 解析远程 CSS 的单图 background-image 声明。
     *
     * @param declaration 目标声明
     * @param value CSS 属性值
     */
    private static void applyBackgroundImage(UiStyleDeclaration declaration, String value) {
        if ("none".equals(normalizeIdentifier(value))) {
            declaration.setKeyword(UiStyleProperty.BACKGROUND_IMAGE, UiStyleKeyword.INITIAL);
            return;
        }
        declaration.setBackgroundImage(parseBackgroundImage(value));
    }

    private static UiBackgroundImage parseBackgroundImage(String value) {
        String url = parseCssUrl(value);
        ResourceLocation texture = parseResourceLocation(url);
        return UiBackgroundImage.texture(texture, 1, 1);
    }

    private static String parseCssUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.regionMatches(true, 0, "url(", 0, 4) || !text.endsWith(")")) {
            throw new IllegalArgumentException("background-image must be url(...)");
        }
        String url = text.substring(4, text.length() - 1).trim();
        if (url.length() >= 2 && isMatchingQuote(url.charAt(0), url.charAt(url.length() - 1))) {
            url = url.substring(1, url.length() - 1).trim();
        }
        if (url.isEmpty()) {
            throw new IllegalArgumentException("background-image url is empty");
        }
        return url;
    }

    private static ResourceLocation parseResourceLocation(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || isRemoteUrl(text) || text.indexOf("://") >= 0) {
            throw new IllegalArgumentException("background-image only supports ResourceLocation urls");
        }
        int namespaceSeparator = text.indexOf(':');
        if (namespaceSeparator < 0) {
            return new ResourceLocation(text);
        }
        String namespace = text.substring(0, namespaceSeparator).trim();
        String path = text.substring(namespaceSeparator + 1).trim();
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("invalid ResourceLocation url");
        }
        return new ResourceLocation(namespace, path);
    }

    private static boolean isMatchingQuote(char first, char last) {
        return first == last && (first == '\'' || first == '"');
    }

    private static boolean isRemoteUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    static UiStyleLength parseLength(String value, boolean allowAuto) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("length is empty");
        }
        if ("auto".equals(text)) {
            if (!allowAuto) {
                throw new IllegalArgumentException("auto is not allowed");
            }
            return UiStyleLength.auto();
        }
        if (text.startsWith("calc(") && text.endsWith(")")) {
            return parseCalcLength(text.substring(5, text.length() - 1));
        }
        if (text.endsWith("px")) {
            return UiStyleLength.px(parseFloat(text.substring(0, text.length() - 2)));
        }
        if (text.endsWith("%")) {
            return UiStyleLength.percent(parseFloat(text.substring(0, text.length() - 1)) / 100.0F);
        }
        if ("0".equals(text)) {
            return UiStyleLength.px(0);
        }
        return UiStyleLength.px(parseFloat(text));
    }

    private static UiStyleLength parseCalcLength(String expression) {
        String normalized = expression == null ? "" : expression.replace(" ", "").toLowerCase(Locale.ROOT);
        int percentIndex = normalized.indexOf('%');
        if (percentIndex <= 0) {
            throw new IllegalArgumentException("unsupported calc length");
        }
        float percent = parseFloat(normalized.substring(0, percentIndex)) / 100.0F;
        if (percentIndex == normalized.length() - 1) {
            return UiStyleLength.percent(percent);
        }
        char operator = normalized.charAt(percentIndex + 1);
        if (operator != '+' && operator != '-') {
            throw new IllegalArgumentException("unsupported calc operator");
        }
        String offsetText = normalized.substring(percentIndex + 2);
        if (!offsetText.endsWith("px")) {
            throw new IllegalArgumentException("unsupported calc offset");
        }
        float offset = parseFloat(offsetText.substring(0, offsetText.length() - 2));
        return UiStyleLength.calc(percent, operator == '-' ? -offset : offset);
    }

    static int parseColor(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("color is empty");
        }
        if (text.startsWith("#")) {
            return parseHexColor(text.substring(1));
        }
        if (text.startsWith("rgba(") && text.endsWith(")")) {
            return parseRgbFunction(text.substring(5, text.length() - 1), true);
        }
        if (text.startsWith("rgb(") && text.endsWith(")")) {
            return parseRgbFunction(text.substring(4, text.length() - 1), false);
        }
        Integer named = NAMED_COLORS.get(text);
        if (named != null) {
            return named.intValue();
        }
        throw new IllegalArgumentException("unsupported color: " + value);
    }

    private static int parseHexColor(String hex) {
        if (hex.length() == 3) {
            int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
            int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
            int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
            return argb(255, r, g, b);
        }
        if (hex.length() == 6) {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        }
        if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("unsupported hex color");
    }

    private static int parseRgbFunction(String arguments, boolean hasAlpha) {
        List<String> parts = splitTopLevel(arguments, ',');
        if ((!hasAlpha && parts.size() != 3) || (hasAlpha && parts.size() != 4)) {
            throw new IllegalArgumentException("invalid rgb color");
        }
        int red = parseColorChannel(parts.get(0));
        int green = parseColorChannel(parts.get(1));
        int blue = parseColorChannel(parts.get(2));
        int alpha = hasAlpha ? parseAlphaChannel(parts.get(3)) : 255;
        return argb(alpha, red, green, blue);
    }

    private static int parseColorChannel(String text) {
        String value = text.trim();
        if (value.endsWith("%")) {
            return clampInt(Math.round(parseFloat(value.substring(0, value.length() - 1)) * 2.55F), 0, 255);
        }
        return clampInt(Math.round(parseFloat(value)), 0, 255);
    }

    private static int parseAlphaChannel(String text) {
        String value = text.trim();
        if (value.endsWith("%")) {
            return clampInt(Math.round(parseFloat(value.substring(0, value.length() - 1)) * 2.55F), 0, 255);
        }
        float parsed = parseFloat(value);
        return clampInt(Math.round((parsed <= 1.0F ? parsed * 255.0F : parsed)), 0, 255);
    }

    private static float parseAspectRatio(String value) {
        String text = value == null ? "" : value.trim();
        int slash = text.indexOf('/');
        if (slash >= 0) {
            float width = parseFloat(text.substring(0, slash).trim());
            float height = parseFloat(text.substring(slash + 1).trim());
            if (height == 0.0F) {
                throw new IllegalArgumentException("aspect-ratio height is zero");
            }
            return width / height;
        }
        return parseFloat(text);
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        String enumName = normalizeIdentifier(value).replace('-', '_').toUpperCase(Locale.ROOT);
        return Enum.valueOf(enumType, enumName);
    }

    private static int parseInteger(String value) {
        return Integer.parseInt(value.trim());
    }

    private static float parseFloat(String value) {
        return Float.parseFloat(value.trim());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    private static boolean endsWithImportant(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).endsWith("!important");
    }

    private static int findTopLevelColon(String text) {
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quoted = true;
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            } else if (ch == ':' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findRuleEnd(String source, int start) {
        boolean quoted = false;
        char quote = 0;
        int depth = 0;
        for (int index = start; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quoted = true;
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            } else if (ch == '}' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return parts;
        }
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quoted = true;
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            } else if (ch == separator && depth == 0) {
                parts.add(text.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static List<String> splitWhitespace(String text) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            if (Character.isWhitespace(ch) && depth == 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripComments(String css) {
        if (css == null || css.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(css.length());
        int index = 0;
        while (index < css.length()) {
            int start = css.indexOf("/*", index);
            if (start < 0) {
                builder.append(css.substring(index));
                break;
            }
            builder.append(css.substring(index, start));
            int end = css.indexOf("*/", start + 2);
            if (end < 0) {
                break;
            }
            index = end + 2;
        }
        return builder.toString();
    }

    private static String firstToken(String value) {
        List<String> tokens = splitWhitespace(value);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    private static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Integer> createNamedColors() {
        Map<String, Integer> colors = new LinkedHashMap<String, Integer>();
        colors.put("transparent", Integer.valueOf(0x00000000));
        colors.put("black", Integer.valueOf(0xFF000000));
        colors.put("white", Integer.valueOf(0xFFFFFFFF));
        colors.put("red", Integer.valueOf(0xFFFF0000));
        colors.put("green", Integer.valueOf(0xFF008000));
        colors.put("blue", Integer.valueOf(0xFF0000FF));
        colors.put("yellow", Integer.valueOf(0xFFFFFF00));
        colors.put("cyan", Integer.valueOf(0xFF00FFFF));
        colors.put("magenta", Integer.valueOf(0xFFFF00FF));
        colors.put("gray", Integer.valueOf(0xFF808080));
        colors.put("grey", Integer.valueOf(0xFF808080));
        colors.put("silver", Integer.valueOf(0xFFC0C0C0));
        colors.put("maroon", Integer.valueOf(0xFF800000));
        colors.put("olive", Integer.valueOf(0xFF808000));
        colors.put("lime", Integer.valueOf(0xFF00FF00));
        colors.put("aqua", Integer.valueOf(0xFF00FFFF));
        colors.put("teal", Integer.valueOf(0xFF008080));
        colors.put("navy", Integer.valueOf(0xFF000080));
        colors.put("purple", Integer.valueOf(0xFF800080));
        colors.put("orange", Integer.valueOf(0xFFFFA500));
        return colors;
    }

    /**
     * 避免和真实枚举 import 冲突的 position 映射。
     */
    private enum UiPositionAlias {
        STATIC,
        RELATIVE,
        STICKY,
        ABSOLUTE,
        FIXED;

        private club.heiqi.uilib.ui.style.props.UiPosition toPosition() {
            return club.heiqi.uilib.ui.style.props.UiPosition.valueOf(name());
        }
    }
}
