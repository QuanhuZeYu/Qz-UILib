package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatComponentTranslationFormatException;
import net.minecraft.util.IChatComponent;

/**
 * {@link StructuredChatReader} 契约测试（C7 划界第一步，纯 headless）。
 *
 * <p><b>「reader 不碰翻译取文本」怎么钉（三段锁，全部与 classpath 完备性无关）</b>：
 * {@code ChatComponentTranslation} 的取文本方法走 {@code ensureInitialized -> StatCollector}
 * 语言表查找，代价有两条实测事实——
 * ① 形参与格式不符时它抛 {@link ChatComponentTranslationFormatException}（本类
 * {@link #originalTextPathThrowsWhereShapeDoesNotMatchButReaderDoesNot} 当场复现）；
 * ② 它产的是<b>渲染后</b>文本，含 {@code ChatComponentStyle.getFormattedText()} 逐组件注入的
 * 样式码与尾随 RESET（实测 {@code <§rSteve§r> §r<b>hi</b>§r}；母本
 * {@code build/rfg/minecraft-src/.../ChatComponentStyle.java:105-119}，第 115 行无条件
 * {@code append(EnumChatFormatting.RESET)}）——§ 残渣不是服务端塞进来的，是客户端把组件树
 * 转字符串时自己注的。{@link #readerReturnsRawTextNotRenderedText} 用同一对象把两侧钉住。</p>
 *
 * <p>③ 最硬的一段是 {@link #readerNeverWalksTheTranslationRenderPath}：
 * {@code getUnformattedText()}/{@code getFormattedText()} 在 {@code ChatComponentStyle} 里都是
 * <b>final</b>（覆写不了），但两者第一步都是 {@code this.iterator()}——而
 * {@code ChatComponentTranslation} 覆写的 {@code iterator()} 非 final 且第一件事就是
 * {@code ensureInitialized()}。故 {@link CountingTranslation} 数 {@code iterator()} 与
 * {@code getUnformattedTextForChat()}，结构化路径必须恒 0，配「亲手调一次就必须非 0」的
 * 正对照（否则三个 0 是空跑）。</p>
 *
 * <p><b>环境注记（如实登记，防后人拿旧探针结论迷惑）</b>：仓库外的单文件 javac 探针
 * （只挂 recompiled_minecraft + guava）里翻译取文本因缺 commons-io 直接
 * {@code NoClassDefFoundError}；gradle 测试 classpath 上 commons-io 与 guava 都在场、
 * StatCollector 可用，取文本<b>不抛</b>。故本锁<b>不写成</b>「断言取文本抛 Throwable」——
 * 那是探针环境残缺的副产物，不是本层约束（跨环境结论必须标 classpath 前提，见
 * {@code 踩坑记录.md}）。</p>
 */
public class StructuredChatReaderTest {

    private static final String SECTION = String.valueOf((char) 0x00A7);

    /** 正向段（1/2 形态）：sender 是 ChatComponentText（服务端 getDisplayName 的原始形态）。 */
    @Test
    public void readsComponentFormSender() {
        ChatComponentTranslation root = new ChatComponentTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Steve"), "<b>hi</b>"});
        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(root);
        Assert.assertNotNull("chat.type.text + [component, String] 必须命中", hit);
        Assert.assertEquals("Steve", hit.getSender());
        Assert.assertEquals("<b>hi</b>", hit.getContent());
    }

    /**
     * 正向段（2/2 形态）：sender 是 String——{@code IChatComponent.Serializer} 会把「无样式且
     * 无 siblings」的 ChatComponentText 降级成 String，故两形都必须吃。
     */
    @Test
    public void readsStringFormSender() {
        ChatComponentTranslation root = new ChatComponentTranslation("chat.type.text",
                new Object[] {"Alex", SECTION + "7 带格式码字面的内容"});
        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(root);
        Assert.assertNotNull("sender 的 String 形必须命中", hit);
        Assert.assertEquals("Alex", hit.getSender());
        Assert.assertEquals("内容原样透传，本层一个字符都不碰", SECTION + "7 带格式码字面的内容",
                hit.getContent());
    }

    /** 最硬一段：结构化路径对翻译渲染入口（iterator / getUnformattedTextForChat）调用数恒 0。 */
    @Test
    public void readerNeverWalksTheTranslationRenderPath() {
        CountingTranslation root = new CountingTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Steve"), "<b>hi</b>"});
        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(root);
        Assert.assertNotNull("命中形不变", hit);
        Assert.assertEquals("Steve", hit.getSender());
        Assert.assertEquals("<b>hi</b>", hit.getContent());
        Assert.assertEquals("取文本渲染路径调用数必须 0: " + root.report(), 0, root.renderCalls);

        // 正对照（反空跑）：同一个对象亲手调一次原版取文本，计数器必须动。
        root.getUnformattedText();
        Assert.assertTrue("计数链路正对照（不成立则上面的 0 是空跑）: " + root.report(),
                root.renderCalls > 0);
        root.getFormattedText();
        Assert.assertTrue("formatted 侧同样计数: " + root.report(), root.renderCalls > 1);
    }

    /** 原版取文本路径在形参不合时抛，reader 只返回 null 不抛。 */
    @Test
    public void originalTextPathThrowsWhereShapeDoesNotMatchButReaderDoesNot() {
        ChatComponentTranslation malformed = new ChatComponentTranslation("chat.type.text",
                new Object[] {"only-one-arg"});
        boolean originalThrew = false;
        try {
            malformed.getUnformattedText();
        } catch (ChatComponentTranslationFormatException expected) {
            originalThrew = true;
        }
        Assert.assertTrue("前提：原版取文本对同一条不合形组件必须抛（实机同款风险）", originalThrew);
        Assert.assertNull("reader 对同一组件退回兜底、不抛", StructuredChatReader.read(malformed));
    }

    /** reader 取的是组件里的原始文本，不是渲染后文本（后者被 ChatComponentStyle 注了 §）。 */
    @Test
    public void readerReturnsRawTextNotRenderedText() {
        ChatComponentTranslation root = new ChatComponentTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Steve"), "<b>hi</b>"});
        String rendered = root.getFormattedText();
        Assert.assertTrue("前提：原版渲染文本自带 §（§ 残渣的唯一来源，母本 ChatComponentStyle"
                + ":105-119）: " + rendered, rendered.contains(SECTION));
        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(root);
        Assert.assertNotNull(hit);
        Assert.assertFalse("结构内容必须零 §: " + hit.getContent(), hit.getContent().contains(SECTION));
        Assert.assertEquals("<b>hi</b>", hit.getContent());
    }

/**
     * Forge 实形锁（本仓 Forge 10.13.4 打过 `NetHandlerPlayServer:768`）：内容槽不是裸
     * String，而是 {@code ForgeHooks.newChatWithLinks(s)} 产的 {@code ChatComponentText("")}
     * <b>带 URL siblings</b>（每个链接一段挂 ClickEvent.OPEN_URL）。reader 取它的
     * <b>unformatted</b> 拼接文本 ⇒ 原文逐字回来、不带 §（服务端 :755 已按
     * {@code ChatAllowedCharacters} 逐字符拦下 §，:757 直接 kick），也不带事件——
     * markdown 路的链接化本来就由 {@code ChatUrlLinkifier} 在段流上重做，不依赖组件事件。
     */
    @Test
    public void readsForgeLinkWrappedContentComponent() {
        ChatComponentText body = new ChatComponentText("");
        body.appendText("看 ");
        ChatComponentText link = new ChatComponentText("http://a.co");
        link.getChatStyle().setChatClickEvent(
                new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.OPEN_URL,
                        "http://a.co"));
        body.appendSibling(link);
        body.appendText(" 吧");
        CountingTranslation root = new CountingTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Steve"), body});

        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(root);
        Assert.assertNotNull("Forge 打链接后的 ChatComponentText 内容形必须命中", hit);
        Assert.assertEquals("看 http://a.co 吧", hit.getContent());
        Assert.assertEquals("渲染路径调用数仍须 0: " + root.report(), 0, root.renderCalls);
    }


    /** 未登记 key 不命中（可扩展键集合的反面）。 */
    @Test
    public void unregisteredFormatKeyFallsBack() {
        Assert.assertTrue("键集合必须登记原版玩家聊天格式键",
                StructuredChatReader.PLAYER_CHAT_FORMAT_KEYS.contains("chat.type.text"));
        for (String key : new String[] {"chat.type.emote", "multiplayer.player.joined", ""}) {
            ChatComponentTranslation root = new ChatComponentTranslation(key,
                    new Object[] {new ChatComponentText("Steve"), "text"});
            Assert.assertNull("未登记 key 交正则兜底: " + key, StructuredChatReader.read(root));
        }
    }

    /** 形态不合一律 null（根组件类型、参数个数、null 槽位、sender 全空白）。 */
    @Test
    public void malformedShapeFallsBack() {
        Assert.assertNull("根组件非翻译形", StructuredChatReader.read(new ChatComponentText("hi")));
        Assert.assertNull("null 根组件", StructuredChatReader.read(null));
        Assert.assertNull("单参数", StructuredChatReader.read(new ChatComponentTranslation(
                "chat.type.text", new Object[] {"Steve"})));
        Assert.assertNull("三参数", StructuredChatReader.read(new ChatComponentTranslation(
                "chat.type.text", new Object[] {"Steve", "a", "b"})));
        Assert.assertNull("内容槽 null", StructuredChatReader.read(new ChatComponentTranslation(
                "chat.type.text", new Object[] {"Steve", null})));
        Assert.assertNull("内容槽是翻译组件（触碰即触发翻译查找，宁可不命中）",
                StructuredChatReader.read(new ChatComponentTranslation("chat.type.text",
                        new Object[] {"Steve", new ChatComponentTranslation("chat.type.text")})));
        Assert.assertNull("sender 全空白", StructuredChatReader.read(new ChatComponentTranslation(
                "chat.type.text", new Object[] {"   ", "x"})));
    }

    /** 空内容仍算玩家消息（命中形，内容 = 空串）——不得因空串退成系统行。 */
    @Test
    public void emptyContentStillStructuralHit() {
        StructuredChatReader.PlayerChat hit = StructuredChatReader.read(
                new ChatComponentTranslation("chat.type.text",
                        new Object[] {new ChatComponentText("Steve"), ""}));
        Assert.assertNotNull(hit);
        Assert.assertEquals("", hit.getContent());
        Assert.assertEquals("Steve", hit.getSender());
    }

    // ==================== C8 通道③：markdown 键识别与内容读取 ====================

    private static final String MARKDOWN_KEY = "uilib.markdown";

    /** 锁 1（命中/不命中矩阵）：判形只认「root 是翻译组件且 key 命中 markdown 键」。 */
    @Test
    public void rendersAsMarkdownMatchesOnlyMarkdownKeyRoot() {
        Assert.assertTrue("markdown 键 = true", StructuredChatReader.rendersAsMarkdown(
                new ChatComponentTranslation(MARKDOWN_KEY, new Object[] {"**hi**"})));
        Assert.assertFalse("chat.type.text = false", StructuredChatReader.rendersAsMarkdown(
                new ChatComponentTranslation("chat.type.text",
                        new Object[] {"Steve", "hi"})));
        Assert.assertFalse("ChatComponentText = false",
                StructuredChatReader.rendersAsMarkdown(new ChatComponentText(MARKDOWN_KEY)));
        Assert.assertFalse("null = false", StructuredChatReader.rendersAsMarkdown(null));
        // root 判形不递归 siblings：markdown 键只挂在 args 槽里不算递交形
        Assert.assertFalse("markdown 键藏在参数槽不算 root 判形", StructuredChatReader.rendersAsMarkdown(
                new ChatComponentTranslation("chat.type.text", new Object[] {
                        new ChatComponentText("Steve"),
                        new ChatComponentTranslation(MARKDOWN_KEY, new Object[] {"x"})})));
    }

    /** 锁 2（args[0] 原文）：String 与 ChatComponentText 两形都吃，含 **、\n、中文、URL 形状、§ 字面零特判。 */
    @Test
    public void markdownContentOfReturnsRawArgsTextInBothForms() {
        String md = "第一行 **粗**\n第二行 中文 https://a.co/x?y=1 *斜*";
        Assert.assertEquals("String 形原样", md, StructuredChatReader.markdownContentOf(
                new ChatComponentTranslation(MARKDOWN_KEY, new Object[] {md})));
        Assert.assertEquals("ChatComponentText 形同样原文（经 unformatted 拼接）", md,
                StructuredChatReader.markdownContentOf(
                        new ChatComponentTranslation(MARKDOWN_KEY,
                                new Object[] {new ChatComponentText(md)})));
        // § 字面照 plainTextOf 现法原样透传，零特判、零剥离
        Assert.assertEquals("含 § 输入零特判", SECTION + "ab **x**",
                StructuredChatReader.markdownContentOf(new ChatComponentTranslation(MARKDOWN_KEY,
                        new Object[] {SECTION + "ab **x**"})));
        Assert.assertEquals("空串仍是合法内容", "",
                StructuredChatReader.markdownContentOf(
                        new ChatComponentTranslation(MARKDOWN_KEY, new Object[] {""})));
    }

    /** 形不合（槽数、槽形、null、非 markdown 键、null 根）一律不猜：markdownContentOf = null。 */
    @Test
    public void markdownContentOfRejectsEveryMalformedShape() {
        Assert.assertNull("0 参数", StructuredChatReader.markdownContentOf(
                new ChatComponentTranslation(MARKDOWN_KEY)));
        Assert.assertNull("2 参数", StructuredChatReader.markdownContentOf(
                new ChatComponentTranslation(MARKDOWN_KEY, new Object[] {"a", "b"})));
        Assert.assertNull("槽位是翻译组件（触碰即翻译查找，宁可不取）",
                StructuredChatReader.markdownContentOf(new ChatComponentTranslation(MARKDOWN_KEY,
                        new Object[] {new ChatComponentTranslation("chat.type.text")})));
        Assert.assertNull("非 markdown 键", StructuredChatReader.markdownContentOf(
                new ChatComponentTranslation("chat.type.text", new Object[] {"x"})));
        Assert.assertNull("ChatComponentText 根", StructuredChatReader.markdownContentOf(
                new ChatComponentText("md")));
        Assert.assertNull("null 根", StructuredChatReader.markdownContentOf(null));
    }

    /** 分工锁：markdown 键组件不是玩家聊天——read() 对它恒 null（判形归判形、结构归结构）。 */
    @Test
    public void readNeverHitsMarkdownKeyComponent() {
        ChatComponentTranslation markdown = new ChatComponentTranslation(MARKDOWN_KEY,
                new Object[] {"<Steve> looks like player chat"});
        Assert.assertTrue("判形命中", StructuredChatReader.rendersAsMarkdown(markdown));
        Assert.assertNull("结构读取必须 null（两判据互斥）", StructuredChatReader.read(markdown));
    }

    /** 取文本路径零触碰：markdownContentOf 对渲染入口计数组件调用数恒 0（配正对照反空跑）。 */
    @Test
    public void markdownReadNeverWalksTheTranslationRenderPath() {
        CountingTranslation root = new CountingTranslation(MARKDOWN_KEY,
                new Object[] {"**a**\nb 中文 http://x.co"});
        Assert.assertTrue(StructuredChatReader.rendersAsMarkdown(root));
        Assert.assertEquals("**a**\nb 中文 http://x.co", StructuredChatReader.markdownContentOf(root));
        Assert.assertEquals("判形+取内容全程零渲染路径: " + root.report(), 0, root.renderCalls);
        root.getUnformattedText();
        Assert.assertTrue("计数链路正对照: " + root.report(), root.renderCalls > 0);
    }

    /**
     * 翻译渲染路径计数器。{@code getUnformattedText()}/{@code getFormattedText()} 在
     * {@code ChatComponentStyle} 里是 final，覆写不了；但两者第一步都是 {@code iterator()}
     * （{@code ChatComponentStyle:87-99} / {@code :105-119}），而翻译组件的
     * {@code iterator()} 第一件事就是 {@code ensureInitialized()}——覆写它即覆盖两条取文本入口。
     */
    private static final class CountingTranslation extends ChatComponentTranslation {

        private int renderCalls;

        CountingTranslation(String key, Object[] args) {
            super(key, args);
        }

        @Override
        public Iterator<IChatComponent> iterator() {
            renderCalls++;
            return super.iterator();
        }

        @Override
        public String getUnformattedTextForChat() {
            renderCalls++;
            return super.getUnformattedTextForChat();
        }

        String report() {
            return "renderCalls=" + renderCalls;
        }
    }
}