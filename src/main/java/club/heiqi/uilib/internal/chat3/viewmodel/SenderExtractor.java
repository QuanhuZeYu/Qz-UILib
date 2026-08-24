package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 3.0 发送者提取(纯函数):从消息文本前缀识别发送者。
 *
 * <p>默认支持两种前缀(&lt;名字&gt; 原版聊天格式 / 名字: 常见服务端格式),正则可由配置替换;
 * 提取失败返回 null = 系统/广播消息(Telegram 服务消息观感,不参与合并)。</p>
 */
public final class SenderExtractor {

    /** 默认:&lt;名字&gt; 消息(原版聊天行格式,名字 1-16 位,不含尖括号)。
     *  (?s)DOTALL:多行消息(如连续引用行 "> a\nb")的 rest 含换行,默认 . 不跨行会整条
     *  误判为系统消息(T6b 连续引用行回归)。 */
    public static final Pattern DEFAULT_ANGLE = Pattern.compile("(?s)^<([^<>]{1,16})> ?(.*)$");

    /**
     * 默认:名字: 消息(名字限 [A-Za-z0-9_],与 1.7.10 玩家名规则一致)。
     *
     * <p>K3 真机修复:冒号模式负向排除两种 URL 前缀,防 "https://..." 整条误判为
     * sender="https"、rest="//..."(真机实锤):
     * <ul>
     *   <li>消息开头是已知 scheme 词 + 冒号(可选空格)——https?/ftps?/sftp/file,
     *       大小写不敏感,后随 "//" 或任何内容都按 URL/非发送者处理;</li>
     *   <li>名字候选 + 冒号(可选空格)后紧跟 "//"(未知 scheme 的 scheme:// 形态)。</li>
     * </ul>
     * "名字: 内容"(含无空格 "名字:内容")与正文中含 URL 的消息("steve: 看 https://a.co")
     * 不受影响:负向排除只作用于消息开头与冒号后紧跟 "//" 的位置。</p> */
    public static final Pattern DEFAULT_COLON = Pattern.compile(
            "^(?!(?i:https?|ftps?|sftp|file): ?)([A-Za-z0-9_]{1,16}): ?+(?!//)(.*)$");

    /** 默认提取器。 */
    public static final SenderExtractor DEFAULT = new SenderExtractor(DEFAULT_ANGLE, DEFAULT_COLON);

    private final Pattern[] patterns;

    /**
     * @param patterns 前缀模式(按序尝试;捕获组 1 = 发送者,组 2 = 消息本体)
     */
    public SenderExtractor(Pattern... patterns) {
        this.patterns = patterns == null ? new Pattern[0] : patterns.clone();
    }

    /**
     * @param text 消息纯文本
     * @return 匹配结果;全部模式不匹配返回 null(系统/广播消息)
     */
    public SenderMatch extract(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches()) {
                return new SenderMatch(matcher.group(1), matcher.group(2));
            }
        }
        return null;
    }

    /**
     * 匹配结果:发送者 + 去掉前缀后的消息本体(气泡内只显示本体)。
     */
    public static final class SenderMatch {

        private final String sender;
        private final String rest;

        private SenderMatch(String sender, String rest) {
            this.sender = sender;
            this.rest = rest;
        }

        /** @return 发送者名 */
        public String getSender() {
            return sender;
        }

        /** @return 去掉发送者前缀后的消息本体(可能为空串) */
        public String getRest() {
            return rest;
        }
    }
}
