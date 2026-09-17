package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.SceneHostAssembly;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 聊天 3.0 探针宿主：用<b>生产内容构建入口</b>（{@link ChatSceneController#buildContent}）在无游戏进程里出图。
 *
 * <p>生产侧同一个方法的唯一调用点是 HUD 注册工厂
 * {@code ClientHudService.register(spec, rt -> instance.buildContent(rt))}；本宿主把它直接接进来，
 * 于是聊天观感（气泡、组头、markdown、公式、链接、换行、淡出）可以在 headless 下反复出图。</p>
 *
 * <h3>消息源语法（{@code --text}；分隔符见 {@link HeadlessRequest#CHAT_MESSAGE_SEPARATOR}）</h3>
 * <ul>
 *   <li>{@code 发送者:内容} → 玩家消息，构造服务端广播的同一结构
 *       {@code ChatComponentTranslation("chat.type.text", [sender, content])}，气泡 + 组头；
 *       content 走 markdown 管道（与生产一致：名称走原版、内容走 markdown）；</li>
 *   <li>{@code md:内容} → markdown 系统行（{@link ChatAccess#MARKDOWN_CHAT_KEY} 键，
 *       左对齐无气泡无组头）；</li>
 *   <li>其余 → 普通系统文本（居中一行，走 {@code SenderExtractor} 正则兜底路径）；</li>
 *   <li>字面 {@code \n} 转义为换行（命令行传不了真换行）。</li>
 * </ul>
 *
 * <p><b>为什么要构造结构化组件</b>：{@code ChatComponentText("Steve: hi")} 只是一个纯文本组件，
 * 分组器认不出「发送者 + 内容」结构，会把它降级成居中系统行——实测产出「五行白字、无气泡」。
 * 真实玩家消息的形状只有 {@code chat.type.text} 翻译组件一种
 * （见 {@code StructuredChatReader} 的 C7 划界定案），headless 要复现真实观感就得喂同一形状。</p>
 *
 * <p><b>依赖注入而非生产单例</b>：控制器用四参构造注入度量/玩家名/段解析/段宽度，不使用
 * {@code new ChatSceneController()}——默认构造走 {@code mcSelfName()}，会触发 {@code Minecraft}
 * 类初始化。注入后本页零 Minecraft 运行态依赖（组件只作数据载体，全程不触语言表）。</p>
 *
 * <p><b>视口必须先于建树写入</b>：{@code ChatMarkdownSettings.chatWidthFor(0)} 收敛到 1px，
 * 漏写 {@link ChatSceneController#setHostViewport} 会得到「宽度 1px 的根 → 图上一条竖线」
 * 这种不报错的空画面。</p>
 *
 * <p><b>帧数与入场动画</b>：每组消息首次合成会播 180ms 入场动画（组节点 opacity 0→1），
 * 动画期间整树不可见且像素逐帧不变——headless 的「像素指纹收敛」判据会把这段误判为「已稳定」
 * 而提前停帧（实测默认 frames=2 时产出全背景空图）。故本页在命令行层要求最小帧数覆盖动画
 * 时长（见 {@code HeadlessShotMain} 的聊天页默认 frames）。</p>
 *
 * <p><b>边界</b>：本页只渲染内容根，不套 HUD 外壳、不做四角锚定与倍率缩放——那三件事只在
 * {@code client.hud.SceneHudHost.RetainedWindow} 里（宿主装配尚未上提）。产出的是「内容树本身」，
 * 不是「HUD 放置后的画面」。</p>
 */
final class ChatSceneProbeHost extends AbstractSceneHostWidget {

    /** 每帧推进的虚拟时间步（ms）：与 60fps 同量级，动画按真实时间轴推进。 */
    private static final long FRAME_STEP_MILLIS = 16L;
    /** 注入的本地玩家名（决定哪条消息按「自己」的气泡样式渲染）。 */
    private static final String SELF_NAME = "QzAgent";
    /** markdown 系统行前缀。 */
    private static final String MARKDOWN_PREFIX = "md:";
    /** 玩家消息结构键：与生产服务端广播同键（见 {@code StructuredChatReader}）。 */
    private static final String PLAYER_CHAT_KEY = "chat.type.text";

    private final ChatSceneController controller;
    private final SceneNode root;
    /** 虚拟时钟：从进程当前时刻起步，每帧单调步进。 */
    private long clockMillis = System.currentTimeMillis();

    /**
     * 装配聊天探针。
     *
     * @param width    视口宽（逻辑 px；必须先写入控制器再建树）
     * @param height   视口高（逻辑 px）
     * @param messages 初始消息（语法见类注释）
     * @param input    平台输入源（脚本注入的鼠标键盘）
     */
    ChatSceneProbeHost(int width, int height, List<String> messages, PlatformInputSource input) {
        super(input);
        this.controller = new ChatSceneController(ChatSceneController.uiLibMeasure(),
                new ChatSceneController.SelfNameProvider() {
                    @Override
                    public String selfName() {
                        return SELF_NAME;
                    }
                },
                ChatSceneController.uiLibSegmentParser(),
                ChatSceneController.uiLibSegmentMeasurer());
        controller.setHostViewport(width, height);
        int messageId = 1;
        for (String message : messages) {
            IChatComponent component = componentOf(message);
            if (component != null) {
                controller.history().append(component, messageId++);
            }
        }
        controller.notifyDataChanged();
        this.root = controller.buildContent(runtime);
        SceneHostAssembly.attachTree(runtime, root);
    }

    /**
     * 每帧先推进聊天时钟（状态机 / 淡出 / TTL 预算），再走生产帧管线。
     *
     * <p>tick 在 {@code super.render} 之前：它把 {@code frameMillis} 写入 signal 队列，
     * 同帧 flush 才能让透明度落在本帧时刻上（反过来慢一帧）。</p>
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        clockMillis += FRAME_STEP_MILLIS;
        controller.tick(clockMillis);
        super.render(w, h, ctx, absX, absY);
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /**
     * 把 {@code --text} 切成多条消息。
     *
     * @param text 命令行文本；null/空白 = 无消息
     * @return 非空消息列表（给出顺序 = 到达序）
     */
    static List<String> splitMessages(String text) {
        List<String> messages = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return messages;
        }
        String[] parts = text.split(java.util.regex.Pattern
                .quote(HeadlessRequest.CHAT_MESSAGE_SEPARATOR));
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                messages.add(part.trim());
            }
        }
        return messages;
    }

    /**
     * 单条消息 → 组件（语法见类注释）。
     *
     * @param message 消息文本
     * @return 组件；空白消息返回 null
     */
    private static IChatComponent componentOf(String message) {
        String text = unescapeNewlines(message);
        if (text.startsWith(MARKDOWN_PREFIX)) {
            String markdown = text.substring(MARKDOWN_PREFIX.length());
            return markdown.isEmpty() ? null
                    : new ChatComponentTranslation(ChatAccess.MARKDOWN_CHAT_KEY, markdown);
        }
        int colon = text.indexOf(':');
        // 发送者侧排掉空白与路径分隔符：「https://x」这类内容不会被误判成发送者。
        if (colon > 0 && colon <= 32 && text.lastIndexOf(' ', colon) < 0 && text.charAt(0) != '/') {
            String sender = text.substring(0, colon).trim();
            String content = text.substring(colon + 1);
            if (!sender.isEmpty()) {
                return new ChatComponentTranslation(PLAYER_CHAT_KEY, new ChatComponentText(sender), content);
            }
        }
        return text.isEmpty() ? null : new ChatComponentText(text);
    }

    /** 字面 {@code \n} → 换行（命令行无法直接传换行符）。 */
    private static String unescapeNewlines(String value) {
        return value.indexOf('\\') < 0 ? value : value.replace("\\n", "\n");
    }
}
