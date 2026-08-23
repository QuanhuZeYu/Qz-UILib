package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * 聊天 3.0 格式化文本前缀剥离(纯函数):气泡内只显示消息本体(去掉「&lt;名字&gt; 」前缀),
 * 同时保留 § 样式码。
 *
 * <p>对齐口径:格式化文本与纯文本的差异只有 § 格式码对(零宽),按「有效字符数」跳过格式码对
 * 前进 {@code plainPrefixLen} 个字符,截取剩余。</p>
 */
public final class FormatPrefixStripper {

    private FormatPrefixStripper() {
    }

    /**
     * @param formattedText  带格式码的消息文本
     * @param plainPrefixLen 纯文本前缀长度(要剥离的有效字符数)
     * @return 去前缀后的格式化文本(格式码保留);前缀长于文本时返回空串
     */
    public static String strip(String formattedText, int plainPrefixLen) {
        if (formattedText == null || formattedText.isEmpty() || plainPrefixLen <= 0) {
            return formattedText == null ? "" : formattedText;
        }
        int index = 0;
        int remaining = plainPrefixLen;
        while (index < formattedText.length() && remaining > 0) {
            char c = formattedText.charAt(index);
            if (c == '\u00a7' && index + 1 < formattedText.length()) {
                index += 2;
                continue;
            }
            index++;
            remaining--;
        }
        if (remaining > 0) {
            return ""; // 前缀长于文本(防御:返回空)
        }
        return formattedText.substring(index);
    }
}
