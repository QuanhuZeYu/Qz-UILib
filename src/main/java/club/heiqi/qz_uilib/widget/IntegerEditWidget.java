package club.heiqi.qz_uilib.widget;

public class IntegerEditWidget extends TextEditWidget {

    // 重写该方法实现只输入整数内容（允许负号）
    @Override
    public void addCharFromCursor(char c) {
        if (Character.isDigit(c)) {
            // 允许数字
            String cursorLeft = content.substring(0, currentCursor);
            String cursorRight = content.substring(currentCursor);
            content = cursorLeft + c + cursorRight;
            currentCursor++;
            this.onTextChange(content);
        } else if (c == '-') {
            // 负号只允许在第一个位置插入，且内容中目前没有负号
            if (currentCursor == 0 && !content.contains("-")) {
                String cursorRight = content.substring(currentCursor);
                content = c + cursorRight;
                currentCursor++;
                this.onTextChange(content);
            }
        }
        // 忽略所有其他字符
    }

    /**
     * 将当前的文本内容解析为一个整数。
     * 如果内容为空或无法解析，则返回 0。
     * * @return 控件中的整数值，如果无效则返回 0。
     */
    public int getIntValue() {
        // 1. 检查内容是否为空或只包含空格/负号（在某些情况下可能出现）
        if (content == null || content.trim().isEmpty() || content.equals("-")) {
            return 0;
        }

        try {
            // 2. 尝试解析字符串为整数
            return Integer.parseInt(content);
        } catch (NumberFormatException e) {
            // 3. 捕获解析异常（理论上，如果 addCharFromCursor 实现正确，
            //    这里只会在非常罕见的情况下发生，或者在外部直接设置了 content）
            LOG.error("Error parsing integer from content: " + content);
            return 0; // 解析失败时返回默认值 0
        }
    }
}
