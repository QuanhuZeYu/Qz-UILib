package club.heiqi.qz_uilib.widget;

public class DoubleEditWidget extends TextEditWidget {
    /**
     * 重写该方法实现只输入小数内容（数字、一个负号、一个小数点）。
     * @param c 尝试输入的字符
     */
    @Override
    public void addCharFromCursor(char c) {
        String cursorLeft = content.substring(0, currentCursor);
        String cursorRight = content.substring(currentCursor);

        // 1. 允许数字
        if (Character.isDigit(c)) {
            content = cursorLeft + c + cursorRight;
            currentCursor++;
            this.onTextChange(content);
        }
        // 2. 允许小数点
        else if (c == '.') {
            // 小数点只允许在内容中出现一次
            if (!content.contains(".")) {
                content = cursorLeft + c + cursorRight;
                currentCursor++;
                this.onTextChange(content);
            }
        }
        // 3. 允许负号
        else if (c == '-') {
            // 负号只允许在第一个位置插入，且内容中目前没有负号
            if (currentCursor == 0 && !content.contains("-")) {
                content = cursorLeft + c + cursorRight;
                currentCursor++;
                this.onTextChange(content);
            }
        }
        // 忽略所有其他字符
    }

    /**
     * 将当前的文本内容解析为一个双精度浮点数（double）。
     * 如果内容为空或无法解析，则返回 0.0。
     * * @return 控件中的小数（double）值，如果无效则返回 0.0。
     */
    public double getDoubleValue() {
        // 1. 检查内容是否为空或只包含无效字符
        String trimmedContent = content.trim();
        if (trimmedContent.isEmpty() || trimmedContent.equals("-") || trimmedContent.equals(".")) {
            return 0.0;
        }

        try {
            // 2. 尝试解析字符串为双精度浮点数
            return Double.parseDouble(trimmedContent);
        } catch (NumberFormatException e) {
            // 3. 捕获解析异常，返回默认值
            System.err.println("Error parsing double from content: " + content);
            return 0.0;
        }
    }
}
