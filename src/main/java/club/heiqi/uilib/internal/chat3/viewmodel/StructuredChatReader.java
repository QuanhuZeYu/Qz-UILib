package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.api.chat.ChatAccess;

/**
 * 聊天 3.0 结构化聊天读取器(L2 视图模型,纯函数、headless 可测):原版玩家聊天组件
 * → (发送者文本, 消息内容文本)。
 *
 * <p><b>C7 划界定案(用户裁决)的落点</b>:玩家消息「名称走原版解析、发送内容走 UILib
 * markdown、两者不混合」。服务端 PlayerManager 广播玩家聊天时构造的正是
 * {@code ChatComponentTranslation("chat.type.text", [player.getDisplayName(), 原始消息 String])}
 * ——客户端只把该组件原样转交显示层，结构不丢。本类把这一结构读出来，使 chat3 无需再从
 * 「渲染后的整行文本」用正则反解发送者与内容。</p>
 *
 * <p><b>硬约束(为什么只调 getKey()/getFormatArgs())</b>:
 * {@code ChatComponentTranslation} 的取文本方法
 * ({@code getUnformattedText()}/{@code getFormattedText()})会走
 * {@code ensureInitialized -> StatCollector} 的语言表翻译查找：与读取结构无关的开销，且在无
 * Minecraft 实例的环境里不可靠，格式缺参时还会抛
 * {@code ChatComponentTranslationFormatException}。而 {@code getFormattedText()} 由
 * {@code ChatComponentStyle} 逐组件前置样式码、尾追 RESET(母本 :105-119)——那一步正是把 §
 * 注进文本的源头，markdown 输入一律不经它。故本类<b>一律不调</b>翻译组件自身的取文本
 * 路径；内容只从 {@code getFormatArgs()[1]} 取，绝不重新翻译。发送者侧文本取自
 * {@code ChatComponentText}(其 {@code getUnformattedText()} 实现是纯的)。</p>
 *
 * <p><b>不命中即返回 null</b>(不抛、不猜)，调用方 {@link MessageGrouper} 随即退回
 * {@link SenderExtractor} 正则兜底——行为与划界前完全一致。</p>
 *
 * <p><b>C8 通道③（2026-09-07 用户裁决 a1）</b>：{@code ChatAccess.printMarkdown} 递交的
 * 消息落在 {@link ChatAccess#MARKDOWN_CHAT_KEY} 键上。它<b>不是玩家聊天</b>——
 * {@link #read(IChatComponent)} 对它恒返回 null（键未登记进 {@code PLAYER_CHAT_FORMAT_KEYS}，
 * 且形参也不合 [sender, content]）；本类对它只负责两件事：
 * {@link #rendersAsMarkdown(IChatComponent)} 结构判形（渲染路由在任何取文本调用之前短路
 * 用它），{@link #markdownContentOf(IChatComponent)} 取 {@code getFormatArgs()[0]} 的
 * unformatted 原文（String 与 ChatComponentText 两形都吃，照 {@code plainTextOf} 现法，
 * 全程不触翻译查找）。</p>
 */
public final class StructuredChatReader {

    /**
     * 玩家聊天格式键集合(可扩展常量):命中其中任一 key 且形如 {@code [sender, content]} 的
     * 翻译组件才算「玩家消息」。C7 只登记原版 {@code chat.type.text}；将来承接
     * {@code chat.type.action} 一类同源结构，在此追加即可，判据无需改动。
     */
    static final Set<String> PLAYER_CHAT_FORMAT_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("chat.type.text")));

    private StructuredChatReader() {
    }

    /**
     * 结构命中结果(不可变):发送者文本 + 消息内容文本。
     *
     * <p>两值都是「组件里的 unformatted 原始文本」——内容侧即原版保证不含 § 的那段玩家输入
     * (服务端 {@code NetHandlerPlayServer} :753-760 逐字符过
     * {@code ChatAllowedCharacters.isAllowedCharacter}，该判定本体排除 U+00A7，不过就 kick)，
     * 可直接作为 markdown 输入，不需要任何 § 预清洗或后清洗。</p>
     */
    public static final class PlayerChat {

        private final String sender;
        private final String content;

        private PlayerChat(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }

        /** @return 发送者文本(非空白) */
        public String getSender() {
            return sender;
        }

        /** @return 消息内容文本(可为空串;恒非 null) */
        public String getContent() {
            return content;
        }

        @Override
        public String toString() {
            return "PlayerChat[" + sender + " -> " + content + "]";
        }
    }

    /**
     * 读取一条消息组件的玩家聊天结构。
     *
     * @param component 消息根组件(可为 null)
     * @return 结构命中结果;非玩家聊天结构 / key 未登记 / 参数形态不合 / 发送者空白 ⇒
     *         null(交正则兜底)
     */
    public static PlayerChat read(IChatComponent component) {
        if (!(component instanceof ChatComponentTranslation)) {
            return null;
        }
        ChatComponentTranslation translation = (ChatComponentTranslation) component;
        if (!PLAYER_CHAT_FORMAT_KEYS.contains(translation.getKey())) {
            return null;
        }
        Object[] args = translation.getFormatArgs();
        if (args == null || args.length != 2) {
            return null;
        }
        String sender = plainTextOf(args[0]);
        String content = plainTextOf(args[1]);
        if (sender == null || content == null || sender.trim().isEmpty()) {
            return null;
        }
        return new PlayerChat(sender, content);
    }

    /**
     * 该组件是否是「按 markdown 渲染」的系统行（C8 通道③判形，{@link MessageGrouper} 与
     * 渲染路由在任何取文本调用<b>之前</b>用它短路）。
     *
     * <p>判据 = 纯结构：root 是 {@code ChatComponentTranslation} 且 {@code getKey()} 命中
     * {@link ChatAccess#MARKDOWN_CHAT_KEY}。不调 {@code getUnformattedText()} /
     * {@code getFormattedText()}——对翻译组件那是语言表查找，未注册键实机返回 key 字面
     * {@code "uilib.markdown"}，正则兜底若先读到它会把它当普通系统文本（错形）。</p>
     *
     * @param component 消息根组件（可为 null）
     * @return true = markdown 递交键组件；null / 非翻译根 / 其他键 ⇒ false
     */
    public static boolean rendersAsMarkdown(IChatComponent component) {
        return component instanceof ChatComponentTranslation
                && ChatAccess.MARKDOWN_CHAT_KEY.equals(((ChatComponentTranslation) component).getKey());
    }

    /**
     * 取 markdown 递交组件的内容原文（{@code getFormatArgs()[0]}；String 与
     * {@code ChatComponentText} 两形都吃，其余形态不认）。
     *
     * <p>与 {@link #read(IChatComponent)} 的硬约束同款：只走 {@code getKey()} /
     * {@code getFormatArgs()}，永不触翻译取文本。args 缺位、形态不合返回 null——不抛、
     * 不猜，调用方按系统行降级处理。</p>
     *
     * @param component 消息根组件（可为 null；应先用 {@link #rendersAsMarkdown} 判形）
     * @return markdown 内容原文（可为空串）；取不到 ⇒ null
     */
    public static String markdownContentOf(IChatComponent component) {
        if (!rendersAsMarkdown(component)) {
            return null;
        }
        Object[] args = ((ChatComponentTranslation) component).getFormatArgs();
        if (args == null || args.length != 1) {
            return null;
        }
        return plainTextOf(args[0]);
    }

    /**
     * 参数位文本提取(String 形与 {@code ChatComponentText} 形两形都支持；其余形态一律不认)。
     *
     * <p>反序列化侧 {@code IChatComponent.Serializer}(:133-141) 会把「无样式且无 siblings」的
     * ChatComponentText 降级成 String，故同一槽位的实际类型两形都可能出现，必须都吃；
     * ChatComponentText 侧 {@code getUnformattedText()} 会顺带拼接其 siblings(Forge 链接片段)，
     * 该实现在 {@code ChatComponentStyle} 里不触碰语言表。
     * 拒绝 {@code ChatComponentTranslation} 等任何可能触发翻译查找的组件：宁可不命中退回
     * 正则，也不在结构化路径里碰语言表。</p>
     *
     * @param arg 格式参数(可为 null)
     * @return 文本;取不到 ⇒ null
     */
    private static String plainTextOf(Object arg) {
        if (arg instanceof String) {
            return (String) arg;
        }
        if (arg instanceof ChatComponentText) {
            return ((ChatComponentText) arg).getUnformattedText();
        }
        return null;
    }
}