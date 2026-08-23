package club.heiqi.uilib.internal.chat3.input;

import java.awt.Desktop;
import java.net.URI;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.Animator;
import club.heiqi.uilib.internal.chat3.view.ChatContainer;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * 聊天输入屏的 scene 渲染面(L4 宿主层,薄壳):装配 {@link ChatContainer}(消息列表 + 输入条),
 * 只保留事件路由(滚轮/行点击)与弹出动画。输入条/列表/容器的组装全部下沉到组件层。
 */
public final class ChatInputSurface extends AbstractSceneHostWidget {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Input");

    private final ChatSceneController controller;
    private final SceneNode root;
    private final ChatContainer.Result container;
    /** 屏幕树消息节点 → 记录(命中检测)。 */
    private final Map<SceneNode, ChatLineRecord> screenMessageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();
    /** 打开时刻(弹出动画基准)。 */
    private final long openAtMillis = System.currentTimeMillis();
    /** 周期诊断帧计数(每 120 帧打印一次渲染视口,真机定位坐标系问题)。 */
    private int renderLogCounter;

    public ChatInputSurface(String initialText) {
        super(new LwjglInputSource(new LwjglStateReader()));
        this.controller = ChatHudWindow.ensureRegistered();

        int margin = ChatMarkdownSettings.getChatMarginPx();
        root = SceneNode.column()
                .setHitTestable(true)
                .setFillParentHeight(true)
                .setMainAxisAlign(MainAxisAlign.END)
                .setCrossAxisAlign(CrossAxisAlign.START)
                .setPadding(0, margin, margin, margin);

        container = ChatContainer.mount(runtime, controller, screenMessageNodes, initialText);
        root.appendChild(container.root());

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
        container.setViewport(w, h);
        if ((renderLogCounter++ % 120) == 0) {
            LOG.info("聊天输入屏渲染视口: w={}, h={}, chatWidthFor={}, containerHeightFor={}",
                    Integer.valueOf(w), Integer.valueOf(h),
                    Integer.valueOf(ChatMarkdownSettings.chatWidthFor(Math.max(1, w))),
                    Integer.valueOf(ChatMarkdownSettings.containerHeightFor(Math.max(1, h))));
        }
        int width = ChatMarkdownSettings.chatWidthFor(Math.max(1, w));
        long popMillis = ChatMarkdownSettings.getPopAnimMillis();
        long elapsed = System.currentTimeMillis() - openAtMillis;
        float progress = popMillis <= 0 ? 1.0F : (float) elapsed / (float) popMillis;
        container.root().setTransform(Transform.translate(
                -(float) width * (1.0F - Animator.easeOut(progress)), 0.0F));
        super.render(w, h, ctx, absX, absY);
    }

    /** 屏幕打开:聚焦输入框 + 同步发送历史(委托输入条)。 */
    public void onOpened() {
        container.bar().onOpened();
    }

    /** 屏幕关闭:释放容器句柄(列表 + 滚动绑定)。 */
    public void onClosed() {
        container.dispose();
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return container.bar().takeText();
    }

    /** 记录已发送(发送路径增量同步)。 */
    public void recordSent(String message) {
        container.bar().recordSent(message);
    }

    /** 历史回显(委托输入条)。 */
    public void recallHistory(int direction) {
        container.bar().recallHistory(direction);
    }

    /** Tab 补全(委托输入条)。 */
    public void autocomplete() {
        container.bar().autocomplete();
    }

    /** 服务端补全响应(委托输入条)。 */
    public void applyAutocompleteResponse(String[] options) {
        container.bar().applyAutocompleteResponse(options);
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
                container.bar().setText(click.getValue());
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
