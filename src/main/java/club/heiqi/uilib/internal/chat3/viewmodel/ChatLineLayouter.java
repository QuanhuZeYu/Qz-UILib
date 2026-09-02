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

    /**
     * 一个显示行片段:行文本(保留格式码)+ 该行的断行来源。
     *
     * <p>{@code continuesWord} = 本行是<b>词内字符硬断</b>的续行(断点两侧原文没有空白,
     * 首字符直接续写上一行末尾那个词);词边界回退断行与 \n 硬断产生的行均为 false。
     * 这个信息<b>只有切分器能给出</b>:词边界回退会丢弃断点处的空白,于是「上一行以 URL
     * 结尾 + 下一行以 URL 字符开头」在两种断行下**文本完全同形**,从行文本字符串反查不可
     * 判别(实测重测宽度也无法区分:两种断行下 line+nextChar 都超宽)。跨显示行的 URL
     * 链接化必须靠它做闸门,否则普通散文会被误接成一条超长 URL。</p>
     */
    public static final class LineFragment {

        private final String text;
        private final boolean continuesWord;

        LineFragment(String text, boolean continuesWord) {
            this.text = text;
            this.continuesWord = continuesWord;
        }

        /** @return 显示行文本(保留格式码,与 {@link #splitLines} 输出口径一致) */
        public String getText() {
            return text;
        }

        /** @return true = 本行由词内字符硬断接上一行而来(首字符是上一行末词的延续) */
        public boolean continuesWord() {
            return continuesWord;
        }

        /**
         * 换文本、保留断行来源(HUD 末行加省略号时用它:裁剪不改变「本行是否续词」)。
         *
         * @param newText 新行文本
         * @return 新片段(continuesWord 不变)
         */
        LineFragment withText(String newText) {
            return new LineFragment(newText, continuesWord);
        }
    }

    /** 一次切分的两个投影(行文本列表 + 行片段列表),同源计算、同一缓存条目。 */
    private static final class Layout {

        private final List<String> lines;
        private final List<LineFragment> fragments;

        Layout(List<String> lines, List<LineFragment> fragments) {
            this.lines = lines;
            this.fragments = fragments;
        }
    }

    private final Measure measure;
    private final int fontSizePx;
    private final Map<String, Layout> cache =
            new LinkedHashMap<String, Layout>(64, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Layout> eldest) {
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
     * <p>与 {@link #layoutFragments} 同源:两者命中同一缓存条目,行文本列表恒为行片段
     * 列表的文本投影(长度相等、逐行同文本),不会各自算一遍而漂移。</p>
     *
     * @param formattedText 带格式码的消息文本
     * @param maxWidthPx    单行最大宽度
     * @return 切好的显示行(保留格式码);空文本 → 单空行
     */
    public synchronized List<String> layout(String formattedText, int maxWidthPx) {
        return layoutInternal(formattedText, maxWidthPx).lines;
    }

    /**
     * 取行切分片段(带断行来源;{@link #layout} 的富信息形态,同一缓存条目)。
     *
     * <p>调用方需要判断「某显示行是否为词内硬断续行」时用本方法——典型是跨显示行的
     * URL 链接化:URL 被字符硬断后,续行不含 scheme 前缀,按行独立识别必然漏判。</p>
     *
     * @param formattedText 带格式码的消息文本
     * @param maxWidthPx    单行最大宽度
     * @return 切好的显示行片段(保留格式码);空文本 → 单空行片段
     */
    public synchronized List<LineFragment> layoutFragments(String formattedText, int maxWidthPx) {
        return layoutInternal(formattedText, maxWidthPx).fragments;
    }

    private Layout layoutInternal(String formattedText, int maxWidthPx) {
        int epoch = measure.epoch();
        if (epoch != cachedEpoch) {
            cache.clear();
            cachedEpoch = epoch;
        }
        String key = formattedText + '@' + epoch + '#' + fontSizePx + '#' + maxWidthPx;
        Layout hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        List<LineFragment> fragments = splitFragments(formattedText, maxWidthPx, measure, fontSizePx);
        List<String> lines = new ArrayList<String>(fragments.size());
        for (LineFragment fragment : fragments) {
            lines.add(fragment.getText());
        }
        Layout layout = new Layout(Collections.unmodifiableList(lines),
                Collections.unmodifiableList(fragments));
        cache.put(key, layout);
        return layout;
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
        List<LineFragment> fragments = splitFragments(text, maxWidthPx, measure, fontSizePx);
        List<String> lines = new ArrayList<String>(fragments.size());
        for (LineFragment fragment : fragments) {
            lines.add(fragment.getText());
        }
        return Collections.unmodifiableList(lines);
    }

    /**
     * 纯函数切分(带断行来源;可独立单测)。
     *
     * <p>与 {@link #splitLines} 唯一差异是返回 {@link LineFragment}(附带
     * {@code continuesWord} 断行来源标记),行文本口径逐行完全一致。</p>
     *
     * @param text        带格式码的消息文本
     * @param maxWidthPx  单行最大宽度
     * @param measure     宽度度量
     * @param fontSizePx  字号
     * @return 切好的显示行片段(不共享可变状态,调用方可安全持有)
     */
    public static List<LineFragment> splitFragments(String text, float maxWidthPx, Measure measure,
            int fontSizePx) {
        List<LineFragment> lines = new ArrayList<LineFragment>();
        StringBuilder current = new StringBuilder();
        StringBuilder pendingSpaces = new StringBuilder();
        FormatState format = new FormatState();
        // 当前在建行是否由「词内字符硬断」从上一行延续而来(逻辑首行恒 false)
        boolean continuesWord = false;
        // 最近一个已并入 current 的可见字符在源文本中的下标(-1 = 本行尚无可见字符);
        // 断点判据要用它去源文本里查「两个可见字符之间有没有空白」,不能拿 pendingSpaces
        // 的长度当数——前导缩进会长期滞留在 pendingSpaces 里而与断点无关(不变量测试抓到)
        int lastVisibleAt = -1;
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(new LineFragment(trimTrailing(current), continuesWord));
                current.setLength(0);
                pendingSpaces.setLength(0);
                continuesWord = false; // 换行符是硬边界,续行不接上一行的词
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
                lastVisibleAt = i;
                i++;
                continue;
            }
            // 超宽:K3 修复——先词边界回退(行内有空白 → 最后空白处断行,空白后的词整词
            // 移入续行,不在英文词中间硬断);无空格长串才字符硬断(设计稿 §5.4)。
            // 两种断行产生的续行行首都重发当前生效格式码,续行颜色/样式不丢。
            // K3 三轮修复:空白之前无可见字符(纯前导缩进 + § 格式码)时不走词边界回退——
            // 否则会把缩进单独断成一行可见内容为空的"§6"行(真机 URL 缩进行复现)。
            int lastWs = lastWhitespace(current);
            if (lastWs >= 0 && hasVisibleChar(current, 0, lastWs)) {
                // 词边界回退:行 = 最后空白之前(去尾空白),续行 = 空白后的整词 + 重发格式码;
                // 断行点之后的待定空白(词间分隔空格)属于续行内容,一并移入而非丢弃
                String rest = current.substring(lastWs + 1);
                lines.add(new LineFragment(trimTrailing(current.substring(0, lastWs + 1)),
                        continuesWord));
                current.setLength(0);
                current.append(format.prefix());
                current.append(rest);
                current.append(pendingSpaces);
                pendingSpaces.setLength(0);
                // 词边界回退丢弃了断点空白 → 续行是一个新词的开始,不是上一行末词的延续
                continuesWord = false;
                continue; // 不推进 i,重试该字符
            }
            String trimmed = trimTrailing(current);
            if (hasVisibleChar(trimmed, 0, trimmed.length())) {
                lines.add(new LineFragment(trimmed, continuesWord));
                // 断点判据只认源文本:上一行末可见字符与本行首(即本字符)之间若无空白,
                // 就是词内硬断。不能用 pendingSpaces 长度当数——词间空白确实还没并入
                // current("hello world" 窄行),但前导缩进也会长期滞留在里面而与断点无关。
                continuesWord = !hasWhitespaceIn(text, lastVisibleAt + 1, i);
                current.setLength(0);
                current.append(format.prefix());
                pendingSpaces.setLength(0);
                continue; // 不推进 i,重试该字符
            }
            // 行内无可见字符(仅前导空白 + § 格式码):不断出空行,硬放该字符并入缩进
            // (窄行下缩进 + 首词作为整体成行,不产生零内容行)
            current.append(c);
            lastVisibleAt = i;
            i++;
        }
        String tail = trimTrailing(current);
        if (lines.isEmpty() && tail.isEmpty()) {
            lines.add(new LineFragment("", false));
        } else if (!tail.isEmpty()) {
            lines.add(new LineFragment(tail, continuesWord));
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

    /**
     * 源文本 {@code [from, to)} 区间内是否存在空白(断点词边界判据)。
     *
     * <p>§ 格式码对零宽、不构成词边界:URL 内部被 § 码切色(GTNH 欢迎语即如此)时,
     * 断点两侧仍属同一个词,必须允许续链。</p>
     *
     * @param text 源文本
     * @param from 区间起点(含)
     * @param to   区间终点(不含)
     * @return true = 区间内有空白
     */
    private static boolean hasWhitespaceIn(String text, int from, int to) {
        for (int i = Math.max(0, from); i < to; i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < to) {
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    /** @return [from, to) 区间内是否存在可见字符(§ 格式码对与空白不计)。 */
    private static boolean hasVisibleChar(CharSequence cs, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = cs.charAt(i);
            if (c == '\u00a7' && i + 1 < cs.length()) {
                i++;
                continue;
            }
            if (!Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }
}
