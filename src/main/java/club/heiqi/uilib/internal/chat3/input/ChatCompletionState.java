package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tab 补全纯函数核(T1):当前词定位、大小写折叠匹配、最长公共前缀、循环索引、替换拼回,
 * 全部无状态、headless 可测。一期假定光标在词尾;词边界函数保留 caret 参数版,便于二期
 * 光标处补全升级。
 */
public final class ChatCompletionState {

    private ChatCompletionState() {
    }

    /** 当前词起点(一期语义:光标在文本末尾)。 */
    public static int wordStart(String text) {
        return wordStart(text, text == null ? 0 : text.length());
    }

    /**
     * 光标前最近空格之后的词起点(原版 func_146979_l 语义,空格为唯一分隔符)。
     *
     * @param text       全文
     * @param caretIndex 光标码点索引(二期光标处补全用;一期传 text.length())
     * @return 词起点索引,恒在 [0, text.length()]
     */
    public static int wordStart(String text, int caretIndex) {
        if (text == null) {
            return 0;
        }
        int end = Math.max(0, Math.min(caretIndex, text.length()));
        int index = end - 1;
        while (index >= 0 && text.charAt(index) != ' ') {
            index--;
        }
        return index + 1;
    }

    /** 大小写折叠(逐字符,避免 locale 差异)。 */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(Character.toLowerCase(text.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * 折叠前缀匹配(大小写不敏感),返回原始 case 候选,保序去重。
     *
     * @param candidates 候选集(可为 null)
     * @param word       当前词(折叠后做前缀)
     * @return 匹配候选(空列表表示无匹配)
     */
    public static List<String> matchCaseInsensitive(Collection<String> candidates, String word) {
        List<String> matches = new ArrayList<String>();
        if (candidates == null) {
            return matches;
        }
        String foldedWord = fold(word);
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (fold(candidate).startsWith(foldedWord) && !matches.contains(candidate)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /** 保序去重(响应候选清洗)。 */
    public static List<String> dedupe(String[] options) {
        List<String> result = new ArrayList<String>();
        if (options == null) {
            return result;
        }
        for (String option : options) {
            if (option != null && !result.contains(option)) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * 最长公共前缀(大小写不敏感比较;返回首候选原始 case 片段)。
     *
     * @param options 候选列表(可为 null/空)
     * @return 公共前缀;null/空列表或含 null 元素时返回 null
     */
    public static String commonPrefix(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        String prefix = options.get(0);
        if (prefix == null) {
            return null;
        }
        for (int i = 1; i < options.size() && !prefix.isEmpty(); i++) {
            String other = options.get(i);
            if (other == null) {
                return null;
            }
            int count = 0;
            while (count < prefix.length() && count < other.length()
                    && Character.toLowerCase(prefix.charAt(count))
                            == Character.toLowerCase(other.charAt(count))) {
                count++;
            }
            prefix = prefix.substring(0, count);
        }
        return prefix;
    }

    /** 数组便利重载。 */
    public static String commonPrefix(String[] options) {
        return commonPrefix(options == null ? null : dedupe(options));
    }

    /**
     * 循环索引(正/反向,到尾回卷 0,到首反向回卷 size-1)。
     *
     * @param current 当前索引
     * @param delta   步进(+1 正向,-1 反向)
     * @param size    候选数
     * @return 下一索引;size &lt;= 0 时防御返回 0
     */
    public static int cycleIndex(int current, int delta, int size) {
        if (size <= 0) {
            return 0;
        }
        int next = (current + delta) % size;
        if (next < 0) {
            next += size;
        }
        return next;
    }

    /** 把文本中当前词替换为候选并拼回全文(保留前缀不动,含 "/" 与词前空格)。 */
    public static String replaceWord(String text, String candidate) {
        return replaceWord(text, wordStart(text), candidate);
    }

    /**
     * 显式词起点版(测试/二期光标处补全用)。
     *
     * @param text      全文
     * @param wordStart 词起点(越界按 clamp 防御)
     * @param candidate 替换候选(null 等价空串)
     * @return 拼回后的全文
     */
    public static String replaceWord(String text, int wordStart, String candidate) {
        if (text == null) {
            return candidate == null ? "" : candidate;
        }
        int start = Math.max(0, Math.min(wordStart, text.length()));
        return text.substring(0, start) + (candidate == null ? "" : candidate);
    }
}
