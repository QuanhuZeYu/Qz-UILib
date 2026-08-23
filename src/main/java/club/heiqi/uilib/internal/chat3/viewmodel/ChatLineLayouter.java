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
 *   <li>空白是断行机会(行尾空白丢弃);超宽词逐字符硬断;</li>
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
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                pendingSpaces.setLength(0);
                i++;
                continue;
            }
            if (c == '\u00a7' && i + 1 < text.length()) {
                // 格式码对:零宽、不可拆,始终附加
                current.append(c).append(text.charAt(i + 1));
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
            // 超宽:当前行非空 → 断行重试该字符;当前行空(超宽词硬断中)→ 硬放
            String trimmed = current.toString().replaceFirst("\\s+$", "");
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
                current.setLength(0);
                pendingSpaces.setLength(0);
                continue; // 不推进 i,重试该字符
            }
            current.append(c);
            i++;
        }
        String tail = current.toString().replaceFirst("\\s+$", "");
        if (lines.isEmpty() && tail.isEmpty()) {
            lines.add("");
        } else if (!tail.isEmpty()) {
            lines.add(tail);
        }
        return Collections.unmodifiableList(lines);
    }
}
