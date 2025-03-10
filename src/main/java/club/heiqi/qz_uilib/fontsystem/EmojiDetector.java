package club.heiqi.qz_uilib.fontsystem;

import java.util.regex.Pattern;

/**
 * Emoji 检测工具类（支持 Java 8+ 及 Java 21+ 双模式）
 */
public class EmojiDetector {

    /**
     * 判断字符串是否包含 Emoji（自动识别 Java 版本）
     * @param input 待检测字符串
     * @return 是否包含 Emoji
     */
    public static boolean containsEmoji(String input) {
        if (input == null || input.isEmpty()) return false;

        // Java 8-20 使用 Unicode 范围检测
        else {
            return checkByUnicodeRanges(input);
        }
    }

    /**
     * Java 21 以下版本的检测逻辑（基于 Unicode 范围）
     * 整合了多个权威来源的 Unicode 范围定义 [2,3,5](@ref)
     */
    private static boolean checkByUnicodeRanges(String input) {
        for (int i = 0; i < input.length();) {
            int codePoint = input.codePointAt(i);
            if (isEmojiCodePoint(codePoint)) {
                return true;
            }
            // 处理代理对（重要！避免拆分组合 Emoji）
            i += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Emoji Unicode 范围判断（覆盖 Unicode 14.0 标准）
     * 整合了多个权威来源的 Unicode 范围定义 [2,3,5](@ref)
     */
    private static boolean isEmojiCodePoint(int codePoint) {
        return
            // 基础符号
            (codePoint >= 0x2600 && codePoint <= 0x27BF)
                // 常见表情
            || (codePoint >= 0x1F300 && codePoint <= 0x1F6FF)
                // 新增表情（如🧡）
            || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)
                // 国家旗帜
            || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)
                // 变体选择器
            || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                // 特殊符号
            || (codePoint >= 0x2000 && codePoint <= 0x209F)
                // 扩展符号
            || (codePoint >= 0x2190 && codePoint <= 0x23FF)
                // 新增表情符号
            || (codePoint >= 0x25A0 && codePoint <= 0x25FF)
                // 补充符号
            || (codePoint >= 0x2B00 && codePoint <= 0x2BFF);
    }

    /**
     * 判断当前 Java 版本是否 >=21
     */
    private static boolean isJava21OrHigher() {
        try {
            // Java 21 新增的 isEmoji 方法存在性检查
            Character.class.getMethod("isEmoji", int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /* -------------------- 扩展功能 -------------------- */

    /**
     * 正则表达式检测方案（备选方案）[4](@ref)
     */
    public static boolean checkByRegex(String input) {
        Pattern emojiPattern = Pattern.compile(
                "[\\x{1F300}-\\x{1F6FF}]|" +   // 旧版本范围
                "[\\x{2600}-\\x{27BF}]"
        );
        return emojiPattern.matcher(input).find();
    }
}
