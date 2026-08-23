package club.heiqi.uilib.internal.chat3.input;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.event.ClickEvent;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.Animator;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;

/**
 * 聊天输入屏的 scene 渲染面(L4 交互层):容器(消息列表 + 底部输入条,输入框纳入容器)。
 *
 * <p>容器动态尺寸 = 视口宽 × 1/8 × 视口高 × 1/2,左下角贴边;打开时自左侧滑入
 * (render 帧钩子驱动 wall-clock 补间)。键盘节流由屏幕壳完成(Enter/Up/Down/Tab 不进 scene
 * 路由);其余按键经桥壳 keyTyped → 输入源 → 路由到聚焦的输入根。</p>
 */
public final class ChatInputSurface extends AbstractSceneHostWidget {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Input");

    /** 输入条四周衬垫(容器内间距)。 */
    private static final int BAR_PADDING = 8;
    /** 输入字号。 */
    private static final int INPUT_FONT_SIZE = 14;

    private final ChatSceneController controller;
    private final ChatSentHistory sentHistory = new ChatSentHistory();
    private final SceneNode root;
    private final SceneNode containerNode;
    private final Signal<String> inputText;
    private final SceneNode inputRoot;
    /** 屏幕树消息节点 → 记录(命中检测)。 */
    private final Map<SceneNode, ChatLineRecord> screenMessageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();
    /** 玩家名补全上次命中缓存(连续 Tab 循环)。 */
    private final List<String> lastFoundNames = new ArrayList<String>();
    /** 屏幕树生命周期句柄。 */
    private SceneListHandle listHandle;
    /** 打开时刻(弹出动画基准)。 */
    private final long openAtMillis = System.currentTimeMillis();

    public ChatInputSurface(String initialText) {
        super(new LwjglInputSource(new LwjglStateReader()));
        this.controller = ChatHudWindow.ensureRegistered();
        this.inputText = Signal.create(initialText == null ? "" : initialText);

        int margin = ChatMarkdownSettings.getChatMarginPx();
        root = SceneNode.column()
                .setHitTestable(true)
                .setFillParentHeight(true)
                .setMainAxisAlign(MainAxisAlign.END)
                .setCrossAxisAlign(CrossAxisAlign.START)
                .setPadding(0, margin, margin, margin);

        // 容器:动态宽高(视口 1/8 × 1/2,render 帧钩子每帧同步),左下贴边,输入框纳入容器底部
        containerNode = SceneNode.column()
                .setHitTestable(false)
                .setBackgroundColor(ChatMarkdownSettings.getContainerBgArgb())
                .setBorderColor(ChatMarkdownSettings.getContainerBorderArgb())
                .setBorderWidth(1)
                .setCornerRadius(ChatMarkdownSettings.getContainerCornerRadius())
                .setPadding(ChatMarkdownSettings.getBubblePaddingY(),
                        ChatMarkdownSettings.getBubblePaddingX(),
                        ChatMarkdownSettings.getBubblePaddingY(),
                        ChatMarkdownSettings.getBubblePaddingX())
                .setClipChildren(true);
        root.appendChild(containerNode);

        // 消息列表(controller 容器形态内容,滚动绑定指向容器节点)
        SceneNode list = SceneNode.column()
                .setHitTestable(false)
                .setGap(Math.max(0, ChatMarkdownSettings.getGroupGapContainerPx()));
        containerNode.appendChild(list);
        listHandle = controller.buildContainerContent(runtime, containerNode, list, screenMessageNodes);

        // 输入条(容器内底部):复用 UILib 现成 SceneTextInput 组件(受控输入,标准样式)
        SceneNode barRow = SceneNode.row()
                .setHitTestable(false)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setPadding(BAR_PADDING);
        containerNode.appendChild(barRow);

        SceneTextInput.Props props = SceneTextInput.Props.builder(inputText)
                .placeholder("消息…")
                .onChange(inputText::set)
                .build();
        inputRoot = SceneTextInput.create(runtime, props).get();
        inputRoot.setFontSize(INPUT_FONT_SIZE);
        inputRoot.setFillParentWidth(true);
        barRow.appendChild(inputRoot);

        // 滚轮滚动聊天历史(vanilla ±7/Shift±1 语义)
        runtime.on(root, SceneEventType.SCROLL,
                (SceneEvent event, club.heiqi.uilib.ui.scene.input.SceneEventContext ctx) -> {
                    int wheel = event.getWheelDelta();
                    if (wheel == 0) {
                        return;
                    }
                    if (wheel > 1) {
                        wheel = 1;
                    }
                    if (wheel < -1) {
                        wheel = -1;
                    }
                    if (!event.isShiftDown()) {
                        wheel *= 7;
                    }
                    controller.history().scrollBy(wheel);
                    controller.notifyDataChanged();
                });
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 每帧同步动态尺寸(视口 1/8 × 1/2)并推进弹出动画(容器自左侧滑入);随后走标准帧管线。 */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        int width = ChatMarkdownSettings.chatWidthFor(Math.max(1, w));
        int height = ChatMarkdownSettings.containerHeightFor(Math.max(1, h));
        containerNode.setPreferredWidth(width);
        containerNode.setPreferredHeight(height);
        long popMillis = ChatMarkdownSettings.getPopAnimMillis();
        long elapsed = System.currentTimeMillis() - openAtMillis;
        float progress = popMillis <= 0 ? 1.0F : (float) elapsed / (float) popMillis;
        containerNode.setTransform(Transform.translate(
                -(float) width * (1.0F - Animator.easeOut(progress)), 0.0F));
        super.render(w, h, ctx, absX, absY);
    }

    /** 屏幕打开:聚焦输入框 + 同步发送历史(覆盖第三方直调)。 */
    public void onOpened() {
        runtime.requestFocus(inputRoot);
        sentHistory.syncFrom(currentSentMessages());
        sentHistory.resetCursor();
    }

    /** 屏幕关闭:释放屏幕树句柄。 */
    public void onClosed() {
        if (listHandle != null) {
            listHandle.dispose();
            listHandle = null;
        }
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return inputText.get().trim();
    }

    /** 记录已发送(发送路径增量同步)。 */
    public void recordSent(String message) {
        sentHistory.add(message);
    }

    /** 历史回显(vanilla getSentHistory 语义:-1 上一条 / +1 下一条)。 */
    public void recallHistory(int direction) {
        inputText.set(sentHistory.recall(direction));
    }

    /** Tab 补全:斜杠开头走服务端命令补全;否则本地玩家名补全。 */
    public void autocomplete() {
        String text = inputText.get();
        if (text.startsWith("/")) {
            requestCommandAutocomplete(text);
        } else {
            autocompletePlayerNames(text);
        }
    }

    /** 服务端补全响应(mixin 转交):最长公共前缀入输入框。 */
    public void applyAutocompleteResponse(String[] options) {
        String prefix = commonPrefix(options);
        if (prefix != null) {
            inputText.set(prefix);
        }
    }

    /** 行点击事件回投:原版 ChatStyle click 事件链。 */
    public void handleLineClick(int mouseX, int mouseY) {
        IChatComponent component = hitTest(mouseX, mouseY);
        if (component == null) {
            return;
        }
        ClickEvent click = component.getChatStyle().getChatClickEvent();
        if (click == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        switch (click.getAction()) {
            case RUN_COMMAND:
                if (mc != null && mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage(click.getValue());
                }
                break;
            case SUGGEST_COMMAND:
                inputText.set(click.getValue());
                break;
            case OPEN_URL:
                openUrl(click.getValue());
                break;
            default:
                break;
        }
    }

    /** 屏幕树命中:注册表节点绝对盒(屏幕全屏,rootAbs = 0,0)包含点 → 组件。 */
    private IChatComponent hitTest(int x, int y) {
        return ChatSceneController.hitTestInRegistry(screenMessageNodes, x, y);
    }

    /** 原版发送列表快照(继承自 Facade)。 */
    private static List<String> currentSentMessages() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return java.util.Collections.emptyList();
        }
        GuiNewChat gui = mc.ingameGUI.getChatGUI();
        return gui.getSentMessages();
    }

    /** 发送补全请求(原版 C14PacketTabComplete)。 */
    private static void requestCommandAutocomplete(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(text));
    }

    /** 本地玩家名补全(vanilla autocompletePlayerNames 简化语义)。 */
    private void autocompletePlayerNames(String text) {
        int lastSpace = text.lastIndexOf(' ');
        String prefix = lastSpace >= 0 ? text.substring(lastSpace + 1) : text;
        if (prefix.isEmpty()) {
            return;
        }
        List<String> candidates = new ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
            for (Object info : mc.thePlayer.sendQueue.playerInfoList) {
                if (info instanceof GuiPlayerInfo && ((GuiPlayerInfo) info).name != null) {
                    candidates.add(((GuiPlayerInfo) info).name);
                }
            }
        }
        candidates.addAll(lastFoundNames);
        List<String> matches = new ArrayList<String>();
        for (String name : candidates) {
            if (name.startsWith(prefix) && !matches.contains(name)) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        lastFoundNames.clear();
        lastFoundNames.addAll(matches);
        String completed = commonPrefix(matches.toArray(new String[0]));
        if (completed == null || completed.length() < prefix.length()) {
            completed = prefix;
        }
        String result = (lastSpace >= 0 ? text.substring(0, lastSpace + 1) : "") + completed;
        inputText.set(result);
    }

    /** 最长公共前缀(纯函数,可测)。 */
    static String commonPrefix(String[] options) {
        if (options == null || options.length == 0) {
            return null;
        }
        String prefix = options[0];
        for (int index = 1; index < options.length && !prefix.isEmpty(); index++) {
            String other = options[index];
            int count = 0;
            while (count < prefix.length() && count < other.length()
                    && prefix.charAt(count) == other.charAt(count)) {
                count++;
            }
            prefix = prefix.substring(0, count);
        }
        return prefix;
    }

    /** 链接打开(vanilla chatLinks 设置门控)。 */
    private static void openUrl(String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception failure) {
            LOG.warn("聊天链接打开失败: {}", failure.toString());
        }
    }

}
