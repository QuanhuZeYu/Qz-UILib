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
import club.heiqi.uilib.internal.chat3.view.ChatLinkClick;
import club.heiqi.uilib.internal.chat3.view.ChatContainer;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.internal.chat3.view.ChatSurfaceAnimator;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;

/**
 * 聊天输入屏的 scene 渲染面(L4 宿主层,薄壳):装配 {@link ChatContainer}(消息列表 + 输入条),
 * 只保留事件路由(滚轮/行点击)与开合动画。输入条/列表/容器的组装全部下沉到组件层。
 *
 * <p>开合动画由 {@link ChatSurfaceAnimator} 状态机驱动(设计稿 §4.1:弹入 = easeOutBack pop,
 * 关闭 = 140ms easeOutQuad 淡出+下滑,与 ChatSceneController.CLOSING 同参数同曲线);
 * 关闭完成回调不在渲染栈内触发,由屏幕 updateScreen 每 tick 经 {@link #tickCloseState()} 取走
 * (关屏 displayGuiScreen 会销毁本 surface,不能在 render 栈内执行)。</p>
 */
public final class ChatInputSurface extends AbstractSceneHostWidget {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Input");

    private final ChatSceneController controller;
    private final SceneNode root;
    private final ChatContainer.Result container;
    /** 屏幕树消息节点 → 记录(命中检测)。 */
    private final Map<SceneNode, ChatLineRecord> screenMessageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();
    /** 容器开合动画状态机(纯逻辑,时间由渲染帧/updateScreen 注入)。 */
    private final ChatSurfaceAnimator animator;
    /** 周期诊断帧计数(每 120 帧打印一次渲染视口,真机定位坐标系问题)。 */
    private int renderLogCounter;
    /** 当前「打开链接？」确认框(连点顶掉旧框;随 runtime.dispose 一并回收)。 */
    private ScenePortalHandle linkConfirm;

    public ChatInputSurface(String initialText) {
        super(new LwjglInputSource(new LwjglStateReader()));
        this.controller = ChatHudWindow.ensureRegistered();

        // 开合动画状态机:生产参数取自设计稿 §4.1 同源配置(pop 240 / closing 140 可配);
        // 挂起兜底 = closeTimeoutFor(closing):超时永远 ≥ closing+500,任何配置时长下
        // 关闭动画都完整播放(用户高层语义:关闭动画开始→渐入结束整体可配 500ms~5s,
        // 超时只能兜底渲染挂起,不得截断动画);构造即开始弹出动画(与旧 openAtMillis 同语义)
        this.animator = new ChatSurfaceAnimator(
                ChatMarkdownSettings.getPopAnimMillis(),
                ChatMarkdownSettings.getClosingAnimMillis(),
                ChatSurfaceAnimator.closeTimeoutFor(ChatMarkdownSettings.getClosingAnimMillis()));
        this.animator.startOpen(System.currentTimeMillis());

        int margin = ChatMarkdownSettings.getChatMarginPx();
        root = SceneNode.column()
                .setHitTestable(true)
                .setFillParentHeight(true)
                .setMainAxisAlign(MainAxisAlign.END)
                .setCrossAxisAlign(CrossAxisAlign.START)
                .setPadding(0, margin, margin, margin);

        container = ChatContainer.mount(runtime, controller, screenMessageNodes, initialText);
        root.appendChild(container.root());
        // 链接点击:事件当场投递(不在 mouseClicked 里取账 —— CLICK 要到 UP 才合成)
        controller.setMessageLinkClickHandler(this::onSceneLinkClick);

        // 滚轮滚动聊天历史(vanilla ±7/Shift±1 语义)。
        // 方向语义:wheelDelta > 0(滚轮向上)→ 正行数 → history.scrollBy(+) = 向旧消息
        // (scrollOffset 自底部向上,与原版 GuiNewChat.func_146229_b 正号同语义);
        // wheelDelta < 0(滚轮向下)→ 负行数 → 回最新底部。
        runtime.on(root, SceneEventType.SCROLL,
                (SceneEvent event, club.heiqi.uilib.ui.scene.input.SceneEventContext ctx) -> {
                    int wheel = wheelScrollLines(event.getWheelDelta(), event.isShiftDown());
                    if (wheel == 0) {
                        return;
                    }
                    // 滚轮 = 非拖动来源:退出拖动接管直通,恢复 120ms 平滑(拖动结束后的首滚轮不平滑回归)
                    controller.smoothScroll().releaseDrag();
                    controller.history().scrollBy(wheel);
                    controller.notifyDataChanged();
                });
    }

    /**
     * 滚轮增量 → 聊天滚动行数(原版 ±7/Shift±1 语义,包级供 headless 单测锁定符号)。
     *
     * <p>wheelDelta 符号遵循 {@link LwjglInputSource}(正 = 滚轮向上);返回正行数 = 向旧消息
     * (自底部向上偏移,原版 GuiNewChat.func_146229_b 正号同语义),负行数 = 向新消息回底。
     * 幅度 clamp 到 ±1 后:非 Shift × {@code scrollWheelLines}(默认 7),Shift ×1。</p>
     */
    static int wheelScrollLines(int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0) {
            return 0;
        }
        int wheel = Math.max(-1, Math.min(1, wheelDelta));
        if (!shiftDown) {
            wheel *= ChatMarkdownSettings.getScrollWheelLines();
        }
        return wheel;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 每帧同步动态尺寸(视口 1/4 × 1/2)并推进开合动画(设计稿 §4.1);随后走标准帧管线。 */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        container.setViewport(w, h);
        if ((renderLogCounter++ % 120) == 0) {
            LOG.info("聊天输入屏渲染视口: w={}, h={}, chatWidthFor={}, containerHeightFor={}",
                    Integer.valueOf(w), Integer.valueOf(h),
                    Integer.valueOf(ChatMarkdownSettings.chatWidthFor(Math.max(1, w))),
                    Integer.valueOf(ChatMarkdownSettings.containerHeightFor(Math.max(1, h))));
        }
        // 开合动画统一由状态机驱动(设计稿 §4.1,与 ChatSceneController 同参数同曲线):
        // 弹入 = easeOutBack pop,关闭 = easeOutQuad 淡出+下滑;此处只推进状态与取输出,
        // 完成回调由屏幕 updateScreen 经 tickCloseState 在渲染栈外取走(关屏会销毁本 surface)
        long nowMillis = System.currentTimeMillis();
        animator.tick(nowMillis);
        container.root().setTransform(animator.transform(nowMillis));
        container.root().setOpacity(animator.opacity(nowMillis));
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

    /**
     * 容器收回动画完成、真正关屏后调用:委托控制器直接切 HUD(forceHud,跳过机器
     * CLOSING 空窗,气泡立即挂回);CLOSING 期间收到打开请求(pendingOpen)的折算由
     * 控制器状态机按设计稿 §4.2 处理。
     */
    public void notifyScreenClosed() {
        controller.closeToHudImmediately();
    }

    /**
     * 请求关闭(播放容器 CLOSING 动画):首次请求进入 CLOSING 并注册完成回调;
     * 动画期间重复请求幂等返回同一请求(不重置动画、不重复注册)。
     *
     * @param onCloseComplete 关闭动画完成回调(为 null 则只播动画不回调)
     * @return 本次关闭请求令牌(重入时 = 旧令牌)
     */
    public ChatSurfaceAnimator.CloseRequest requestClose(Runnable onCloseComplete) {
        long nowMillis = System.currentTimeMillis();
        // 窗体过渡窗口开启:关闭动画开始 → 内容冻结(消息只入数据层,树/布局/enter/过期
        // 不响应),窗体整体动画独占画面;结束后(稳态)一次性应用(窗体动画抽象 2026-08-29)
        controller.beginCloseTransition(nowMillis);
        return animator.requestClose(onCloseComplete, nowMillis);
    }

    /** @return 关闭动画是否已请求/已完成(提交路径防重入用:动画期间重复 Enter 不重发)。 */
    public boolean isClosePending() {
        return animator.isClosing() || animator.isClosed();
    }

    /**
     * 屏幕级推进(updateScreen 每 tick 调用,渲染栈外):推进关闭动画状态,并在完成后取走
     * 完成回调触发(关屏 displayGuiScreen → onGuiClosed → 容器销毁,不能在 render 栈内执行);
     * 渲染停滞时超时兜底(500ms)也在此强制完成,不放任屏幕卡死。
     */
    public void tickCloseState() {
        long nowMillis = System.currentTimeMillis();
        animator.tick(nowMillis);
        Runnable closeCallback = animator.takeCloseCallback();
        if (closeCallback != null) {
            closeCallback.run();
        }
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return container.bar().takeText();
    }

    /** 提交文本(trim 后);空串返回 null 且不入发送历史,非空记录历史并返回消息文本。 */
    public String submitText() {
        return container.bar().submitText();
    }

    /** 记录已发送(发送路径增量同步)。 */
    public void recordSent(String message) {
        container.bar().recordSent(message);
    }

    /** 历史回显(委托输入条)。 */
    public void recallHistory(int direction) {
        container.bar().recallHistory(direction);
    }

    /** Tab 补全(委托输入条;direction +1 正向 Tab,-1 Shift+Tab 反向)。 */
    public void autocomplete(int direction) {
        container.bar().autocomplete(direction);
    }

    /** 非 Tab 键清补全循环态(原版 GuiChat:91;委托输入条)。 */
    public void clearCompletionCycle() {
        container.bar().clearCompletionCycle();
    }

    /** PageUp/PageDown 聊天区翻页(可见行数 - 1;+1 向旧消息,-1 向新消息)。 */
    public void pageScroll(int direction) {
        int page = Math.max(1, controller.visibleLineCount() - 1);
        // 与滚轮路径同源:退出拖动接管直通后按行滚动,再通知数据变更
        controller.smoothScroll().releaseDrag();
        controller.history().scrollBy(direction > 0 ? page : -page);
        controller.notifyDataChanged();
    }

    /** 服务端补全响应(委托输入条)。 */
    public void applyAutocompleteResponse(String[] options) {
        container.bar().applyAutocompleteResponse(options);
    }

    /**
     * 链接点击回投（scene CLICK 事件当场调用，见 {@link ChatSceneController#setMessageLinkClickHandler}）。
     *
     * <p>优先级：服务端显式下发的 click 事件 > 我们自己链接化出的跨度。前者保持原版语义
     * 不动（RUN_COMMAND / SUGGEST_COMMAND 是命令注入，加确认会改变既有行为，故**不弹窗**）；
     * 但凡是**打开浏览器**，两条来源一律先过确认框。</p>
     *
     * <p>后者是为了玩家手打的裸 URL：原版 {@code IChatComponent} 上不带 clickEvent，
     * 服务端也没下发可点区域，原来点了完全没反应。</p>
     *
     * <p><b>既不接收坐标，也不接收"稍后再取"的账。</b>坐标不可用（MC 回调是 guiScale 缩放值，
     * chat3 几何是物理像素，不可无损换算），账更不能用：CLICK 在 POINTER_UP 才合成，而
     * {@code mouseClicked} 在 POINTER_DOWN 就跑了 —— 在 DOWN 里取账等于每笔都慢一拍，
     * 真机就是「要点第二下才有效」。</p>
     */
    void onSceneLinkClick(ChatLinkClick hit) {
        IChatComponent component = hit.component();
        ClickEvent click = component == null ? null
                : component.getChatStyle().getChatClickEvent();
        if (click != null) {
            Minecraft mc = Minecraft.getMinecraft();
            switch (click.getAction()) {
                case RUN_COMMAND:
                    if (mc != null && mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage(click.getValue());
                    }
                    return;
                case SUGGEST_COMMAND:
                    container.bar().setText(click.getValue());
                    return;
                case OPEN_URL:
                    confirmAndOpenUrl(click.getValue());
                    return;
                default:
                    return;
            }
        }
        if (hit.url() != null) {
            confirmAndOpenUrl(hit.url());
        }
    }

    /**
     * 打开外链前先弹确认框（用户裁定：点链接要真能开浏览器，且必须带确认弹窗）。
     *
     * <p>弹窗用本仓自有 {@link SceneDialog#confirm}，不用原版 {@code GuiConfirmOpenLink}
     * ——「与原版对齐」指体感对齐，实现必须落在自有抽象内（工作站规范）。</p>
     *
     * <p>连点不叠窗：新框先 dispose 旧框（{@code ScenePortalHandle.dispose} 幂等，
     * 已随 runtime 释放的也安全）。原版 {@code gameSettings.chatLinks} 关闭时完全不响应，
     * 连弹窗都不出现 —— 与原版「链接不可用」口径一致。</p>
     */
    private void confirmAndOpenUrl(final String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks
                || url == null || url.isEmpty()) {
            return;
        }
        if (linkConfirm != null) {
            linkConfirm.dispose();
        }
        linkConfirm = SceneDialog.confirm(runtime, "打开链接？", url, new Runnable() {
            @Override
            public void run() {
                openUrl(url);
            }
        });
    }

    /** 链接打开(vanilla chatLinks 设置门控)。 */
    private static void openUrl(String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception | Error failure) {
            // 不能只挡 Exception：没有桌面会话时 AWT 桌面子系统集成失败抛的是 Error
            // （InternalError / UnsatisfiedLinkError）。issue #71 的同一课：AWT 的失败形态不限于 Exception。
            LOG.warn("聊天链接打开失败: {}", failure.toString());
        }
    }
}
