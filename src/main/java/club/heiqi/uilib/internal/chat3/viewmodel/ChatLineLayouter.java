package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 3.0 行切分器(L2 视图模型,纯 JVM):把带格式码的消息文本按 UILib 同源度量切成显示行。
 *
 * <p>切分语义(B8 同源,宽度一律走注入度量):</p>
 * <ul>
 *   <li>换行符硬断;§ 格式码对(§x)零宽且不可拆;</li>
 *   <li>空白是断行机会(行尾空白丢弃);普通散文超宽时<b>词边界回退</b>——行内有空白则在
 *       最后一个空白处断行,词整体移到续行,不在英文词中间硬断(K3 修复);</li>
 *   <li>无空格长串(URL/哈希,设计稿 §5.4 word-break:anywhere)才字符级硬断;</li>
 *   <li>断行产生的续行行首重发当前生效格式码(颜色 + 样式位),续行颜色/样式不丢(K3 修复);</li>
 *   <li>宽度口径 = 注入的 {@link Measure#advance}(渲染侧接 TextLayoutService.advance,与渲染推进同源);</li>
 *   <li>缓存:key = 文本@epoch#字号#行宽;度量 epoch 变化(字体 reload)整体失效。</li>
 * </ul>
 */
public final class ChatLineLayouter {

    /** 缓存条目上限(历史 100 行 + 配置切换余量)。 */
    public static final int MAX_ENTRIES = 160;

    /**
     * 度量注入:宽度口径与渲染推进同源。
     */
    public interface Measure {

        /** 文本宽度(UI px,指定字号口径;§ 格式码对按零宽口径)。 */
        float advance(String text, int fontSizePx);

        /** 度量纪元(字体 reload 递增;变化时缓存整体失效)。 */
        int epoch();
    }

    private final Measure measure;
    private final int fontSizePx;
    private final Map<String, List<String>> cache =
            new LinkedHashMap<String, List<String>>(64, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private int cachedEpoch = -1;

    /**
     * @param measure    宽度度量与纪元注入
     * @param fontSizePx 行布局字号
     */
    public ChatLineLayouter(Measure measure, int fontSizePx) {
        this.measure = measure;
        this.fontSizePx = Math.max(1, fontSizePx);
    }

    /**
     * 取行切分(命中返回缓存实例,未命中切分 + 缓存)。
     *
     * @param formattedText 带格式码的消息文本
     * @param maxWidthPx    单行最大宽度
     * @return 切好的显示行(保留格式码);空文本 → 单空行
     */
    public synchronized List<String> layout(String formattedText, int maxWidthPx) {
        int epoch = measure.epoch();
        if (epoch != cachedEpoch) {
            cache.clear();
            cachedEpoch = epoch;
        }
        String key = formattedText + '@' + epoch + '#' + fontSizePx + '#' + maxWidthPx;
        List<String> hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        List<String> lines = splitLines(formattedText, maxWidthPx, measure, fontSizePx);
        cache.put(key, lines);
        return lines;
    }

    /**
     * 文本宽度(委托注入度量,与切分同口径)。
     *
     * @param text 文本(可含格式码,§ 对零宽)
     * @return 宽度(UI px)
     */
    public float measureWidth(String text) {
        return measure.advance(text, fontSizePx);
    }

    /**
     * 纯函数切分(可独立单测)。
     *
     * @param text        带格式码的消息文本
     * @param maxWidthPx  单行最大宽度
     * @param measure     宽度度量
     * @param fontSizePx  字号
     * @return 切好的显示行(不共享可变状态,调用方可安全持有)
     */
    public static List<String> splitLines(String text, float maxWidthPx, Measure measure, int fontSizePx) {
        List<String> lines = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        StringBuilder pendingSpaces = new StringBuilder();
        FormatState format = new FormatState();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(trimTrailing(current));
                current.setLength(0);
                pendingSpaces.setLength(0);
                i++;
                continue;
            }
            if (c == '\u00a7' && i + 1 < text.length()) {
                // 格式码对:零宽、不可拆,始终附加;同时更新"当前生效格式"供续行重发
                char code = text.charAt(i + 1);
                current.append(c).append(code);
                format.apply(code);
                i += 2;
                continue;
            }
            if (Character.isWhitespace(c)) {
                pendingSpaces.append(c);
                i++;
                continue;
            }
            // 普通字符:空格是断行机会——先并入待定空白再测宽
            String candidate = current.toString() + pendingSpaces.toString() + c;
            if (measure.advance(candidate, fontSizePx) <= maxWidthPx) {
                current.append(pendingSpaces).append(c);
                pendingSpaces.setLength(0);
                i++;
                continue;
            }
            // 超宽:K3 修复——先词边界回退(行内有空白 → 最后空白处断行,空白后的词整词
            // 移入续行,不在英文词中间硬断);无空格长串才字符硬断(设计稿 §5.4)。
            // 两种断行产生的续行行首都重发当前生效格式码,续行颜色/样式不丢。
            int lastWs = lastWhitespace(current);
            if (lastWs >= 0) {
                // 词边界回退:行 = 最后空白之前(去尾空白),续行 = 空白后的整词 + 重发格式码;
                // 断行点之后的待定空白(词间分隔空格)属于续行内容,一并移入而非丢弃
                String rest = current.substring(lastWs + 1);
                lines.add(trimTrailing(current.substring(0, lastWs + 1)));
                current.setLength(0);
                current.append(format.prefix());
                current.append(rest);
                current.append(pendingSpaces);
                pendingSpaces.setLength(0);
                continue; // 不推进 i,重试该字符
            }
            String trimmed = trimTrailing(current);
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
                current.setLength(0);
                current.append(format.prefix());
                pendingSpaces.setLength(0);
                continue; // 不推进 i,重试该字符
            }
            current.append(c);
            i++;
        }
        String tail = trimTrailing(current);
        if (lines.isEmpty() && tail.isEmpty()) {
            lines.add("");
        } else if (!tail.isEmpty()) {
            lines.add(tail);
        }
        return Collections.unmodifiableList(lines);
    }

    /**
     * 当前生效格式状态(断行续行重发用,K3 修复②):MC § 格式码语义 = 颜色码后到覆盖、
     * 样式位(k/l/m/n/o)叠加、§r 全清。重发前缀 = 颜色码 + 样式位(均零宽,不影响行宽)。
     */
    private static final class FormatState {

        private char color;
        private final StringBuilder styles = new StringBuilder();

        /** 消化一个格式码字符(不含 § 前缀)。 */
        void apply(char code) {
            char lower = Character.toLowerCase(code);
            if (lower == 'r') {
                color = 0;
                styles.setLength(0);
            } else if (isColorCode(lower)) {
                color = lower;
            } else if (isStyleCode(lower)) {
                // 样式位叠加去重:同一样式位只重发一次(顺序保持首次出现序)
                String pair = String.valueOf('§') + lower;
                if (styles.indexOf(pair) < 0) {
                    styles.append(pair);
                }
            }
        }

        /** @return 续行行首重发前缀("" = 无生效格式);颜色码在前、样式位在后。 */
        String prefix() {
            if (color == 0 && styles.length() == 0) {
                return "";
            }
            StringBuilder prefix = new StringBuilder();
            if (color != 0) {
                prefix.append('§').append(color);
            }
            prefix.append(styles);
            return prefix.toString();
        }

        private static boolean isColorCode(char code) {
            return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
        }

        private static boolean isStyleCode(char code) {
            return code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o';
        }
    }

    /** 去尾空白(与切分语义一致:行尾空白丢弃)。 */
    private static String trimTrailing(CharSequence cs) {
        int end = cs.length();
        while (end > 0 && Character.isWhitespace(cs.charAt(end - 1))) {
            end--;
        }
        return cs.subSequence(0, end).toString();
    }

    /** @return 最后一个空白字符下标;-1 = 无空白(无空格长串,走字符硬断)。 */
    private static int lastWhitespace(StringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(sb.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
