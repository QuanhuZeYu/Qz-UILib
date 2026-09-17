package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.SceneHostAssembly;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

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
    /**
     * 相邻消息的到达间隔（ms）：模拟真实聊天的到达节奏，让每条消息有<b>互不相同</b>的到达时刻。
     *
     * <p><b>为什么不能把所有消息压在同一时刻</b>：HUD 形态的堆叠高度裁剪
     * （{@code ChatSceneController#trimHudGroupsByHeight}）在「未过期组总高 &gt; 视口高 × 0.5」时，
     * 按到达时刻取一个<b>只进不退</b>的阈值剔除更旧的组。N 条消息若同刻到达，该阈值一次就越过全部组
     * ⇒ 整树为空、出图只剩背景：实测 {@code --page=chat --size=1100x720} 命令面 0 条，而同一份内容
     * 在 {@code 1200x720} 有 24 条 —— 两者差别只是内容总高刚好越过 0.5×720=360 这条裁剪线。
     * 真实聊天不可能同刻到达，故这是探针输入失真，不是生产缺陷；探针必须喂真实形状的输入。</p>
     *
     * <p>取值 1 秒：与真实聊天节奏同量级；N 条消息的总跨度（默认 6 条 = 5 秒）远小于 HUD 存活窗口
     * （{@code hudTtlMillis} 12 秒），不会把最早的消息推成「已过期」。</p>
     */
    private static final long ARRIVAL_SPACING_MILLIS = 1000L;

    private final ChatSceneController controller;
    private final SceneNode root;
    /**
     * 虚拟时钟：从<b>请求注入的基准</b>起步，每帧单调步进。
     *
     * <p><b>与消息到达时刻同源</b>：消息以同一基准入史，故入场动画进度 {@code (帧时钟 − 出生时刻) / 180ms}
     * 首帧即 0、随后单调上升，既不依赖构造耗时也不依赖墙钟。旧实现取 {@code System.currentTimeMillis()}
     * 并把初值挪到内容构建之后来躲开构造耗时（否则出生时刻晚于帧时钟起点 → 进度恒负 → 每组 opacity 0 →
     * 零透明子树被 paint 跳过 → 出图纯色，实测偏差 353ms）；同源之后该时序竞态不再存在。
     * 出图可复现性也由此成立：组头 {@code HH:mm} 取自到达时刻，不再随真实时间变化。</p>
     */
    private long clockMillis;

    /**
     * 装配聊天探针。
     *
     * @param width    视口宽（逻辑 px；必须先写入控制器再建树）
     * @param height   视口高（逻辑 px）
     * @param messages 初始消息（语法见类注释）
     * @param input    平台输入源（脚本注入的鼠标键盘）
     * @param clockMillis 虚拟墙钟基准（epoch 毫秒）；同时作为消息到达时刻与帧时钟起点
     * @param environment 宿主环境端口（请求声明的环境事实；诊断采样开关经它下发）
     * @param theme      外观档；{@code null} = 不干预（走库默认）。<b>必须在建树前安装</b>：
     *                   控件的配方派生在构建期捕获主题信号对象，换对象只影响此后构建的控件
     *                   （见 {@code HeadlessThemes}）
     */
    ChatSceneProbeHost(int width, int height, List<String> messages, PlatformInputSource input, long clockMillis,
            UiEnvironment environment, SceneTheme theme) {
        super(input, environment);
        if (theme != null) {
            SceneThemes.install(runtime, Signal.create(theme));
        }
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
        appendWithArrivalCadence(controller, messages, clockMillis);
        controller.notifyDataChanged();
        this.root = controller.buildContent(runtime);
        SceneHostAssembly.attachTree(runtime, root);
        // 帧时钟起点 = 消息到达时刻（理由见 clockMillis 字段 javadoc）。
        this.clockMillis = clockMillis;
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
     * 按真实到达节奏把消息写入历史：最后一条恰为 {@code clockMillis}，更早的按
     * {@link #ARRIVAL_SPACING_MILLIS} 依次前移。
     *
     * <p>到达时刻用注入基准而非进程当前时刻：组头 {@code HH:mm} 与存活窗口因此可复现
     * （二参 {@code append} 读 {@code System.currentTimeMillis}，出图路径不得使用）。
     * 「最后一条 = 基准」保住了另一条同源约束：帧时钟起点等于最后一条的到达时刻，
     * 入场动画进度首帧即 0 并单调上升。</p>
     *
     * @param controller 目标控制器
     * @param messages   消息文本（语法见类注释）
     * @param clockMillis 虚拟墙钟基准 = 最后一条消息的到达时刻 = 帧时钟起点
     */
    static void appendWithArrivalCadence(ChatSceneController controller, List<String> messages,
            long clockMillis) {
        List<IChatComponent> components = new ArrayList<IChatComponent>();
        for (String message : messages) {
            IChatComponent component = componentOf(message);
            if (component != null) {
                components.add(component);
            }
        }
        int messageId = 1;
        for (int i = 0; i < components.size(); i++) {
            long arrived = clockMillis - (long) (components.size() - 1 - i) * ARRIVAL_SPACING_MILLIS;
            controller.history().append(new ChatLineRecord(components.get(i), messageId++, arrived));
        }
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
    static IChatComponent componentOf(String message) {
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
