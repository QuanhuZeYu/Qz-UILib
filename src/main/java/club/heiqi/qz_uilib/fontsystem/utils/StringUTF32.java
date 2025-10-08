package club.heiqi.qz_uilib.fontsystem.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StringUTF32 {
    /**截取真实字符长度而非char索引
     * 包含 start end 两个索引位置的*/
    public static String get(String input, int codepointStart, int codepointEnd) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int i =0; i < input.length();) {
            int codepoint = input.codePointAt(i);
            int charCount = Character.charCount(codepoint);
            String s = new String(Character.toChars(codepoint));
            if (count >= codepointStart) builder.append(s);
            if (count > codepointEnd) break;

            i += charCount;
            count++;
        }
        return builder.toString();
    }

    /**获取字符串真实长度*/
    public static int length(String input) {
        return input.codePointCount(0, input.length());
    }

    /**从字符串中删除指定部分*/
    public static String delete(String input, int startIndex, int endIndex) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int i =0; i < input.length();) {
            int codepoint = input.codePointAt(i);
            int charCount = Character.charCount(codepoint);
            String s = new String(Character.toChars(codepoint));
            if (count < startIndex || count > endIndex) {
                builder.append(s);
            }
            i += charCount;
            count++;
        }
        return builder.toString();
    }


    public static void main(String[] args) {
        Logger LOG = LogManager.getLogger();
        String testS = "0123456789";

        LOG.info("删除测试: {}", StringUTF32.delete(testS, 3,3));
        LOG.info("获取测试: {}", StringUTF32.get(testS, 0,5));
        LOG.info("长度测试: {}", StringUTF32.length(testS));
    }
}
