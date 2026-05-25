package club.heiqi.uilib.ui.control;

/**
 * 多行文本控件的文本规范化与索引辅助器。
 */
final class DocumentTextAreaTextSupport {

    private DocumentTextAreaTextSupport() {}

    /**
     * 规范化输入文本，统一换行并过滤不可见控制字符。
     *
     * @param input 输入文本
     * @return 规范化后的文本
     */
    static String normalizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < input.length();) {
            char current = input.charAt(index);
            if (current == '\r') {
                builder.append('\n');
                index++;
                if (index < input.length() && input.charAt(index) == '\n') {
                    index++;
                }
                continue;
            }
            int codePoint = input.codePointAt(index);
            if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    /**
     * 按 code point 数截断文本。
     *
     * @param text 原始文本
     * @param maxCodePoints 最大 code point 数
     * @return 截断后的文本
     */
    static String truncateToMaxLength(String text, int maxCodePoints) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxCodePoints <= 0) {
            return "";
        }
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= maxCodePoints) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, endIndex);
    }

    /**
     * 计算文本 code point 数。
     *
     * @param text 文本
     * @return code point 数
     */
    static int countCodePoints(String text) {
        return text == null || text.isEmpty() ? 0 : text.codePointCount(0, text.length());
    }

    /**
     * 将 caret 索引对齐到合法 UTF-16 边界。
     *
     * @param text 文本
     * @param index 原始索引
     * @return 合法索引
     */
    static int normalizeCaretIndex(String text, int index) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int boundedIndex = Math.max(0, Math.min(index, text.length()));
        if (boundedIndex > 0 && boundedIndex < text.length()
                && Character.isLowSurrogate(text.charAt(boundedIndex))
                && Character.isHighSurrogate(text.charAt(boundedIndex - 1))) {
            return boundedIndex - 1;
        }
        return boundedIndex;
    }
}
