package club.heiqi.uilib.internal.devtools.headless;

import java.util.List;

import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.host.SceneHostAssembly;
import club.heiqi.uilib.ui.scene.host.SceneHostWindow;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;

/**
 * HUD 页探针宿主：用<b>生产 HUD 宿主装配</b>（{@link SceneHostWindow}）在无游戏进程里出图。
 *
 * <p>与 chat 页的差别只有宿主装配：同一份聊天内容树，套上 HUD 外壳
 * （{@link SceneHostWindow.Shell#HUD_DEFAULT}：内边距 + 半透明底）并按四角锚定放置。
 * 生产侧同一装配的调用点是 {@code client.hud.SceneHudHost.RetainedWindow}——本页把它接进来后，
 * 外壳几何、空窗隐藏、放置盒裁剪都能在 headless 下反复出图核对，而不必开游戏。</p>
 *
 * <p>消息语法与 chat 页完全一致（{@link ChatSceneProbeHost} 类注释），默认消息集同为
 * {@code HeadlessRequest.CHAT_DEFAULT_TEXT}。</p>
 *
 * <h3>锚点（{@code --page-index}）</h3>
 * <ul>
 *   <li>0 = 左上 / 1 = 右上 / 3 = 右下；</li>
 *   <li>2 或未指定 = 左下（与原版聊天同位）——默认值。</li>
 * </ul>
 *
 * <p><b>本页无输入</b>：{@link SceneHostWindow} 的契约就是「不持有输入源、不参与命中仲裁」，
 * 故 {@code --actions} 对本页不生效（这不是缺省而是语义：HUD 窗口本就不接收输入）。
 * 需要输入驱动的页面走 chat / playground。</p>
 */
final class HudSceneProbeHost implements UiSurface {

    /** 每帧推进的虚拟时间步（ms）：与 60fps 同量级，动画按真实时间轴推进。 */
    private static final long FRAME_STEP_MILLIS = 16L;
    /** HUD 距视口边缘的间距（逻辑 px；与生产 HUD 默认 margin 同量级）。 */
    private static final int MARGIN_PX = 4;
    /** 注入的本地玩家名（决定哪条消息按「自己」的气泡样式渲染）。 */
    private static final String SELF_NAME = "QzAgent";

    private final ChatSceneController controller;
    private final SceneHostWindow window;
    private final HudAnchor anchor;
    /**
     * 虚拟时钟：从<b>请求注入的基准</b>起步，每帧单调步进。
     *
     * <p>与消息到达时刻同源（同一基准入史），故入场进度首帧即 0 并单调上升，不依赖构造耗时、
     * 不依赖墙钟；组头 {@code HH:mm} 因此稳定。完整排查链见 {@link ChatSceneProbeHost} 的同类字段。</p>
     */
    private long clockMillis;

    /**
     * 装配 HUD 探针。
     *
     * @param width       视口宽（逻辑 px；必须先写入控制器再建树）
     * @param height      视口高（逻辑 px）
     * @param messages    初始消息（语法见 {@link ChatSceneProbeHost}）
     * @param anchorIndex 锚点下标（0 左上 / 1 右上 / 2 左下 / 3 右下；越界按左下）
     * @param clockMillis 虚拟墙钟基准（epoch 毫秒）；同时作为消息到达时刻与帧时钟起点
     */
    HudSceneProbeHost(int width, int height, List<String> messages, int anchorIndex, long clockMillis) {
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
            IChatComponent component = ChatSceneProbeHost.componentOf(message);
            if (component != null) {
                // 到达时刻用注入基准而非进程当前时刻（理由见 ChatSceneProbeHost 同名构造）。
                controller.history().append(new ChatLineRecord(component, messageId++, clockMillis));
            }
        }
        controller.notifyDataChanged();
        this.anchor = anchorOf(anchorIndex);
        // 不带装饰层：外接工具栏是 client 侧注册表（HudToolbarService）的事实，
        // headless 无注册表 → 内容直通（装饰层装配路径由 SceneHostWindowTest 覆盖）。
        this.window = new SceneHostWindow(SceneHostAssembly.defaultMeasurer(),
                SceneHostAssembly.defaultEnvironment(), SceneHostWindow.Shell.HUD_DEFAULT,
                rt -> controller.buildContent(rt), null, null);
        // 帧时钟起点 = 消息到达时刻（理由见 clockMillis 字段 javadoc）。
        this.clockMillis = clockMillis;
    }

    /**
     * 每帧：推进聊天时钟（状态机 / 淡出 / TTL）→ 测量外框 → 四角锚定 → 窗口成帧。
     *
     * <p>空内容走窗口的空窗帧推进（flush/layout 照常、不 paint），与生产宿主的空窗合同一致。</p>
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        clockMillis += FRAME_STEP_MILLIS;
        long frameNanos = clockMillis * 1_000_000L;
        controller.tick(clockMillis);
        LayoutBox box = window.measure(w, h);
        if (window.isEmptyContent()) {
            window.settleWithoutPaint(w, h, frameNanos);
            return;
        }
        SceneAnchorResolver.ResolvedViewport placed = SceneAnchorResolver.resolveViewport(
                isRight(), isBottom(), w, h, box.getWidth(), box.getHeight(), MARGIN_PX,
                0, 0, 0, 0, 0);
        window.frame(ctx, placed.getX() + absX, placed.getY() + absY,
                placed.getWidth(), placed.getHeight(), frameNanos);
    }

    private static HudAnchor anchorOf(int pageIndex) {
        switch (pageIndex) {
            case 0:
                return HudAnchor.TOP_LEFT;
            case 1:
                return HudAnchor.TOP_RIGHT;
            case 3:
                return HudAnchor.BOTTOM_RIGHT;
            default:
                return HudAnchor.BOTTOM_LEFT;
        }
    }

    private boolean isRight() {
        return anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
    }

    private boolean isBottom() {
        return anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
    }

    /** HUD 窗口无输入源，键盘事件无接收方（语义见类注释）。 */
    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
    }

    /** HUD 窗口无输入源，外部文本无接收方。 */
    @Override
    public void pushText(String text) {
    }

    /** HUD 窗口无输入源，外部文本模式无接收方。 */
    @Override
    public void setExternalTextMode(boolean external) {
    }

    /** HUD 窗口无输入源，指针按钮无接收方。 */
    @Override
    public void onPointerButton(ScenePointerAction action, int callbackX, int callbackY,
            SceneMouseButton button, long timeNanos) {
    }

    /** HUD 窗口无输入源，外部指针模式无接收方。 */
    @Override
    public void setExternalPointerMode(boolean external) {
    }

    /** 释放窗口 runtime（含环境根摘除，见 {@link SceneHostWindow#dispose()}）。 */
    @Override
    public void dispose() {
        window.dispose();
    }
}
