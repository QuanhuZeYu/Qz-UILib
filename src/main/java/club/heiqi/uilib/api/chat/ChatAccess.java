package club.heiqi.uilib.api.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * 聊天渲染公共接入点(api.chat 兼容承诺核心):装饰器链 + 接管状态 + 发送桥 + markdown 递交通道。
 *
 * <p>其他 mod 通过本单例接入聊天渲染管线,无需触碰内部接管实现;本包为公共兼容承诺。</p>
 *
 * <p>线程语义:装饰器注册可在任意线程;应用发生在消息到达路径,链用 CopyOnWrite 保证
 * 注册/迭代并发安全;装饰器异常隔离(单例失败不影响消息与链路)。
 * {@link #printMarkdown} 与原版 {@code printChatMessage} 同主线程约定(投递即入史,
 * 视图刷新经脏标记在主线程冲刷)。</p>
 */
public final class ChatAccess {

    /**
     * markdown 递交键(通道③的结构标识):{@code printMarkdown} 用它把内容包成
     * {@code ChatComponentTranslation(key, [md])},渲染侧
     * {@code internal.chat3.viewmodel.StructuredChatReader#rendersAsMarkdown} 依此键短路进
     * markdown 渲染器。常量唯一定义处在此——internal→api 是既有依赖方向(ChatCore 已调
     * {@code ChatAccess.decorate}),两侧禁止各自字面量。
     */
    public static final String MARKDOWN_CHAT_KEY = "uilib.markdown";

    private static final ChatAccess INSTANCE = new ChatAccess();

    /** 装饰器链(注册序应用)。 */
    private final List<ChatMessageDecorator> decorators =
            new CopyOnWriteArrayList<ChatMessageDecorator>();

    private volatile boolean takeoverActive = false;

    /** markdown 注入 sink(安装器回写,见 setMarkdownSink;null = 未接管,printMarkdown 降级原版显示)。 */
    private volatile java.util.function.Consumer<IChatComponent> markdownSink = null;

    /**
     * 原版显示出口(降级路径的可测缝;默认直连 mc 聊天框)。包内可换 ——
     * headless 无 Minecraft 实例,测试经 {@link #__setVanillaPrintForTest} 观察降级产物,
     * 真机保持默认实现。
     */
    private static volatile java.util.function.Consumer<IChatComponent> vanillaPrint =
            new java.util.function.Consumer<IChatComponent>() {
                @Override
                public void accept(IChatComponent component) {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null && mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
                        mc.ingameGUI.getChatGUI().printChatMessage(component);
                    }
                }
            };

    private ChatAccess() {
    }

    /** @return 全局接入单例 */
    public static ChatAccess getInstance() {
        return INSTANCE;
    }

    // ==================== 接管状态 ====================

    /** @return 聊天框接管当前是否生效(安装器内部回写) */
    public boolean isTakeoverActive() {
        return takeoverActive;
    }

    /** 安装器状态回写(internal.chat3 调用,非公共契约)。 */
    public void setTakeoverActive(boolean active) {
        takeoverActive = active;
    }

    /**
     * markdown 注入 sink 回写(internal.chat3 安装器调用,非公共契约;完全照
     * {@link #setTakeoverActive} 先例:接管装成后写入、回退原版/读回失败时清 null)。
     *
     * <p> sink 存在 = 显式 markdown 递交走接管注入(旁路,不经装饰器链);sink 缺席 =
     * {@link #printMarkdown} 降级原版显示。</p>
     *
     * @param sink 注入出口(null = 撤销)
     */
    public void setMarkdownSink(java.util.function.Consumer<IChatComponent> sink) {
        markdownSink = sink;
    }

    // ==================== 消息装饰器链 ====================

    /**
     * 注册消息装饰器(显示前变换组件链)。
     *
     * @param decorator 装饰器(不可为 null)
     * @return 注销句柄(调用 close 即注销;重复 close 幂等)
     */
    public AutoCloseable registerDecorator(final ChatMessageDecorator decorator) {
        if (decorator == null) {
            throw new IllegalArgumentException("decorator 不能为 null");
        }
        decorators.add(decorator);
        return new AutoCloseable() {
            private boolean closed = false;

            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    decorators.remove(decorator);
                }
            }
        };
    }

    /** @return 当前装饰器数量(诊断) */
    public int decoratorCount() {
        return decorators.size();
    }

    /**
     * 应用装饰器链(接管层调用):按注册序变换,任一装饰器抛异常则隔离并继续。
     *
     * @param component 原始消息组件(不可为 null)
     * @return 变换后组件;链中某装饰器返回 null 表示丢弃(null)
     */
    public IChatComponent decorate(IChatComponent component) {
        IChatComponent current = component;
        for (ChatMessageDecorator decorator : decorators) {
            try {
                IChatComponent result = decorator.decorate(current);
                if (result == null) {
                    return null; // 丢弃语义
                }
                current = result;
            } catch (RuntimeException failure) {
                // 异常隔离:该装饰器失效,消息继续走链
                decorators.remove(decorator);
            }
        }
        return current;
    }

    // ==================== markdown 递交通道(通道③,C8) ====================

    /**
     * 显式递交一条 markdown 消息(AGENTS 三条输入通道的第③条落地):内容即所见,
     * <b>永不过装饰器链</b>——print 家族与装饰链解耦是定案,不存在「默认过链」或
     * 「默认跳过+变体」。需要装饰的调用方主动调 {@link #decorate(IChatComponent)} 把
     * 结果递进本方法(只变换不注入;两者语义完全分开)。
     *
     * <p>实现 = 包成 {@code ChatComponentTranslation(MARKDOWN_CHAT_KEY, [md])} 经安装器
     * 回写的 sink 旁路注入接管历史(不经 ChatFacade.printChatMessage,那条会 decorate);
     * 渲染侧按该键短路进 markdown 渲染器。降级(sink 缺席,= 未接管/未安装)按原版显示
     * 原文 {@code ChatComponentText(md)},零 markdown 键字面外泄。</p>
     *
     * <p>线程语义:与原版 printChatMessage 同主线程约定。null 输入忽略。</p>
     *
     * @param md markdown 原文
     */
    public void printMarkdown(String md) {
        if (md == null) {
            return;
        }
        injectMarkdown(new ChatComponentTranslation(MARKDOWN_CHAT_KEY, new Object[] {md}),
                new ChatComponentText(md));
    }

    /**
     * 显式递交一个已装配好的消息组件进 markdown 通道(典型用法:
     * {@code printMarkdown(decorate(raw))})。本方法只递交、不改写组件——print 路径
     * 依旧零自动装饰。
     *
     * <p>渲染侧按组件 root 的 {@link #MARKDOWN_CHAT_KEY} 键判形;递给本方法的若不是
     * markdown 键组件,接管侧按普通系统行走原版解析链(那是调用方自己的装配选择)。
     * 降级(sink 缺席)时直接把<b>原组件</b>递给原版显示:若它正是 markdown 键翻译组件,
     * 原版会渲染 key 字面 {@code "uilib.markdown"}——这是「未接管时给自定义组件」的
     * 固有形状(组件通道对原版本就没有 markdown 语义),不特判、不改写。</p>
     *
     * @param component 消息组件(null 忽略)
     */
    public void printMarkdown(IChatComponent component) {
        if (component == null) {
            return;
        }
        injectMarkdown(component, component);
    }

    /**
     * 注入本体:有 sink 走接管注入(旁路);sink 缺席降级走原版显示出口
     * ({@code fallback}:String 形 = 原文 ChatComponentText,组件形 = 原组件)。
     */
    private void injectMarkdown(IChatComponent component, IChatComponent fallback) {
        java.util.function.Consumer<IChatComponent> sink = markdownSink;
        if (sink != null) {
            sink.accept(component);
            return;
        }
        vanillaPrint.accept(fallback);
    }

    /** 真机默认降级出口(静态初始化捕获,测试恢复用)。 */
    private static final java.util.function.Consumer<IChatComponent> DEFAULT_VANILLA_PRINT =
            vanillaPrint;

    /**
     * 测试缝(仅 api.chat 包内):替换/恢复降级原版显示出口。headless 无 Minecraft 实例,
     * 降级路径的「直连 mc」不可观察,故把出口抽成可换字段——测试注入捕获器断言降级
     * 产物为 {@code ChatComponentText(md 原文)};null = 恢复真机默认。
     */
    static void __setVanillaPrintForTest(java.util.function.Consumer<IChatComponent> replacement) {
        vanillaPrint = replacement == null ? DEFAULT_VANILLA_PRINT : replacement;
    }

    // ==================== 发送桥 ====================

    /**
     * 发送聊天消息(经 {@link ChatBridge} 映射原版发送链)。
     *
     * @param message 消息文本
     */
    public void send(String message) {
        ChatBridge.send(message);
    }
}