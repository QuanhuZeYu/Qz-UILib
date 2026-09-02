package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.input.ChatInputBar;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天容器组件(L3 组件层):外框(背景/描边/圆角/clip)+ 滚动消息列表 + 底部输入条。
 *
 * <p>容器动态尺寸 = 视口宽 × 1/4 × 视口高 × 1/2,由 {@link Result#setViewport(int,int)} 每帧
 * 同步(窗口缩放即时跟随)。消息列表复用 {@link ChatMessageList}(容器形态),输入条复用
 * {@link ChatInputBar}(SceneTextInput)。</p>
 */
public final class ChatContainer {

    /** 输入条区四周内边距(px,设计稿 §2.3 sp-4/§6.2:输入条区四周 8)。 */
    /** 输入区内缩。参与同心不变量：输入框圆角 = 容器圆角 - 本值，由
     *  {@code ChatMarkdownSettingsTest.inputRadiusStaysConcentricWithContainer} 锁定，改这里要同步改那个测试。 */
    private static final int INPUT_AREA_PADDING_PX = 8;
    /** 容器内容区上内边距(px,设计稿 §2.3/§6.2:上 10)。 */
    private static final int CONTENT_PADDING_TOP_PX = 10;
    /** 容器内容区左右内边距(px,设计稿 §2.3/§6.2:左右 10)。 */
    private static final int CONTENT_PADDING_SIDE_PX = 10;
    /** 容器内容区下内边距(px,设计稿 §2.3/§6.2:下 4,留给滚动条视觉余量)。 */
    private static final int CONTENT_PADDING_BOTTOM_PX = 4;

    /** 容器装配结果:外框节点 + 生命周期句柄 + 输入条。 */
    public static final class Result {

        private final SceneNode root;
        private final SceneListHandle listHandle;
        private final Binding scrollBinding;
        private final Binding hintBinding;
        private final InputBinding hintInputBinding;
        private final ChatScrollbar.Result scrollbar;
        private final ChatInputBar bar;
        private final ChatSceneController controller;

        private Result(SceneNode root, SceneListHandle listHandle, Binding scrollBinding,
                Binding hintBinding, InputBinding hintInputBinding,
                ChatScrollbar.Result scrollbar, ChatInputBar bar,
                ChatSceneController controller) {
            this.root = root;
            this.listHandle = listHandle;
            this.scrollBinding = scrollBinding;
            this.hintBinding = hintBinding;
            this.hintInputBinding = hintInputBinding;
            this.scrollbar = scrollbar;
            this.bar = bar;
            this.controller = controller;
        }

        /** 释放列表与滚动绑定(屏幕关闭时)。 */
        public void dispose() {
            if (listHandle != null) {
                listHandle.dispose();
            }
            if (scrollBinding != null) {
                scrollBinding.dispose();
            }
            if (hintBinding != null) {
                hintBinding.dispose();
            }
            if (hintInputBinding != null) {
                hintInputBinding.dispose();
            }
            if (scrollbar != null) {
                scrollbar.dispose();
            }
            if (bar != null) {
                bar.dispose();
            }
        }

        /** @return 容器外框节点(动画 transform / 挂载目标) */
        public SceneNode root() {
            return root;
        }

        /** @return 输入条组件(文本/历史/补全) */
        public ChatInputBar bar() {
            return bar;
        }

        /** 每帧同步动态尺寸(视口 1/4 × 1/2)与气泡最大宽(设计稿 §3.x:气泡 ≤ 0.85 组内容宽)。 */
        public void setViewport(int width, int height) {
            root.setPreferredWidth(ChatMarkdownSettings.chatWidthFor(Math.max(1, width)));
            root.setPreferredHeight(ChatMarkdownSettings.containerHeightFor(Math.max(1, height)));
            int contentWidth = Math.max(1, ChatMarkdownSettings.chatWidthFor(Math.max(1, width))
                    - 2 * ChatMarkdownSettings.getBubblePaddingX());
            controller.messageList().setBubbleMaxWidthPx((int) Math.round(
                    contentWidth * ChatMarkdownSettings.getBubbleMaxWidthRatio()));
        }
    }

    private ChatContainer() {
    }

    /**
     * 装配容器并挂到调用方运行时。
     *
     * @param rt          宿主场景运行时
     * @param controller  聊天场景控制器(数据源:组列表 / 滚动偏移 / 消息列表渲染器 / 帧时钟)
     * @param registry    消息节点 → 记录登记表(命中检测用,调用方持有)
     * @param initialText 输入框预填文本
     * @return 容器装配结果
     */
    public static Result mount(SceneRuntime rt, ChatSceneController controller,
            Map<SceneNode, ChatLineRecord> registry, String initialText) {
        // 液态玻璃：容器与气泡同处一个 backdrop 批次，故二者采样的是<strong>同一张世界
        // 画面</strong>（批次内主层 revision 冻结）——气泡的玻璃不会把容器已糊过的画面
        // 再糊一层。这正是 iOS 一个 visual effect 层级内共享背景采样的语义，
        // 层级差靠 alpha 递进表达（容器 0x59 < 气泡 0x8C）。
        UiBackdrop containerBackdrop = ChatMarkdownSettings.isGlassEnabled()
                ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                        ChatMarkdownSettings.getGlassBlurRadiusPx(), ChatMarkdownSettings.getGlassLensStrength())
                : null;
        int containerBg = ChatMarkdownSettings.isGlassEnabled()
                ? (ChatMarkdownSettings.getContainerBgArgb() & 0x00FFFFFF)
                        | (ChatMarkdownSettings.getGlassContainerAlpha() << 24)
                : ChatMarkdownSettings.getContainerBgArgb();
        SceneNode containerNode = SceneNode.column()
                .setHitTestable(false)
                .setBackdrop(containerBackdrop)
                .setBackgroundColor(containerBg)
                .setBorderColor(ChatMarkdownSettings.getContainerBorderArgb())
                .setBorderWidth(1)
                .setCornerRadius(ChatMarkdownSettings.getContainerCornerRadius())
                // 设计稿 §2.3/§6.2:容器内容区上 10/左右 10/下 4(下留给滚动条视觉余量);
                // 不再复用 bubblePadding(5,10,5,10)——气泡区自身 padding 不受影响
                .setPadding(CONTENT_PADDING_TOP_PX,
                        CONTENT_PADDING_SIDE_PX,
                        CONTENT_PADDING_BOTTOM_PX,
                        CONTENT_PADDING_SIDE_PX)
                .setClipChildren(true);

        // ★ 滚动区行(与消息视口同级):[消息视口 flexGrow=1, 滚动条 column 右对齐]
        // 滚动条必须与视口并列(不进 scrollable 视口),否则随内容平移错位。
        SceneNode listRow = SceneNode.row().setHitTestable(false).setFillParentHeight(true);
        containerNode.appendChild(listRow);

        // 消息视口(scrollable,滚动偏移受体;内容列由控制器挂组)
        SceneNode listViewport = SceneNode.column()
                .setHitTestable(false)
                .setFlexGrow(1)
                .setScrollable(true)
                .setClipChildren(true);
        listRow.appendChild(listViewport);
        // 容器列表挂「容器全量信号」(controller.containerGroupsSignal()),不挂共享 HUD 信号:
        // 打开方向 COLLAPSING 阶段共享信号走 TTL 预算过滤,预算耗尽的历史消息在弹出动画期间
        // 不合成 → 文字在动画尾部瞬间刷出(2026-08-31 真机闪烁);容器信号恒全量即时呈现。
        SceneListHandle listHandle = controller.messageList().mount(rt, listViewport,
                controller.containerGroupsSignal(), ChatMessageList.Style.container(), registry,
                controller.frameMillisSignal());

        // 滚动唯一汇点:历史滚动偏移(px) → 视口滚动属性(结构版本驱动重算)。
        //
        // ★ 聊天↔scene 语义转换(真机「滚轮方向反」修复):chat3 滚动 = 自底部向上偏移
        // (0 = 贴底最新,越大越旧),scene scrollOffsetY = 自顶部向下偏移
        // (0 = 顶部,SceneGeometry.maxScrollY = 底部)。两者方向相反,故
        // 视口偏移 = maxScrollY - 聊天偏移(聊天偏移 clamp 到 [0, maxScrollY],向上最多滚到最旧)。
        // 由此滚轮向上(wheelDelta > 0 → history.scrollBy(+7))→ 聊天偏移↑ → 视口偏移↓ →
        // 内容下移 = 看上方旧消息,与原版 GuiNewChat.func_146229_b(scroll>0 查看更早消息)一致。
        // maxScrollY 是布局后几何:依赖 layoutDoneSignal 在内容变化帧布局完成后重算(与
        // SceneScrollbar 同模式,layout 未跑时兜底 0)。
        //
        // ★ 滚动权威统一在行域(V7 方案甲):滚动状态的唯一事实源 = ChatHistory.scrollOffset
        // (自底部向上的行数) + SmoothScroller 目标行。四类路径共享同一行域通道,不再互相
        // 覆盖权威:
        //   ① 滚轮(ChatInputSurface:releaseDrag + history.scrollBy(±行)+ notifyDataChanged);
        //   ② 回底(scrollToBottom:目标 0,即行域 0);
        //   ③ 贴底跟随(scrollOffsetPx 距底 ≤2 行 → 行域目标归 0,新消息自动贴底);
        //   ④ 拖动(setScrollOffset:scene px → round(chatPx/行高) 折算回行域再 scrollBy;
        //      onDragStart:snapTo 进入直通,display 恒等于目标行)。
        // 显示投影(行 × 18px,scrollOffsetPx)与真实几何 clamp(viewportScrollPx)只发生在
        // 本计算块,不写回权威 → 任何路径都不会把另一套「px 域」真值覆盖进行域。
        Computed<Integer> chatScrollPx = Computed.create(controller::scrollOffsetPx);
        // ★ V7 方案甲语义(行域权威 + 假想几何投影 + 真实几何 clamp):
        // chatPx = scrollOffsetPx() = round(显示行 × 18px) 是「行×18px」假想几何投影
        // (抽象单位,与真实行宽无关);maxScroll = SceneGeometry.maxScrollY(listViewport) 是
        // 真实内容几何(系统行 16 / 组头 16 / 正文行 18 / 气泡内边距与组距,可含多行换行)。
        // 双向 clamp(chatPx ∈ [0, maxScroll]) 保证:
        //   ① 底部恒等恒成立:chatPx=0(贴底)→ 视口偏移 = maxScroll(内容底),
        //      不依赖内容是否 18px 整倍(混合行高下同样成立);
        //   ② 顶部死区 ≤ 17px:chatPx 假想上限 = round(行数 × 18) 相对真实内容的残差
        //      ≤ 行高-1 = 17px,假想上限略超 maxScroll 时视觉已到顶,死区无感;
        //   ③ 无下溢/上溢:双向 clamp 使视口偏移恒 ∈ [0, maxScroll]。
        // 现状实现即方案甲语义,保持不动(契约测试见 ChatContainerTest)。
        Computed<Integer> viewportScrollPx = Computed.create(() -> {
            int chatPx = chatScrollPx.get().intValue();
            rt.layoutDoneSignal().get();
            Object cached = listViewport.getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return Integer.valueOf(0); // flush 前 layout 未跑:兜底 0(下帧 layoutDone 校准)
            }
            int maxScroll = SceneGeometry.maxScrollY(listViewport);
            return Integer.valueOf(maxScroll - Math.max(0, Math.min(chatPx, maxScroll)));
        });
        Binding scrollBinding = rt.bind(viewportScrollPx,
                offset -> listViewport.setScrollOffsetY(offset.intValue()));

        // 滚动条:与视口同级 ROW 内右对齐,右内边距 2(贴容器右缘)。
        // 显示源 = viewportScrollPx(scene px,与视口绑定同源同值):thumb 底部 = 贴底最新、
        // 顶部 = 最旧(滚动条方向与聊天语义对齐)。
        // setScrollOffset 回调(scene px → 聊天行反向换算)与滚轮路径同源(history.scrollBy + notifyDataChanged)。
        // onDragStart:拖动接管时机 → 平滑器 snapTo 当前显示行(取消平滑、进入直通,拖动手感即时;
        // 拖动中每次 setScrollOffset 目标变化经平滑器直通直接到位,display 恒等于目标)。
        //
        // ★ V7 折算互逆(拖动路径):round(chatPx/行高) 与投影 round(display × 行高) 互为逆——
        // onDragStart snapTo 置位后进入直通(direct),scrollOffsetPx 投影的 display 恒为整数行,
        // round(round(x×18)/18) == x 严格成立(18px 整倍线域无损;拖动后视口偏移与拖动目标
        // 逐像素一致);非 18 整倍的残差由上方 clamp 吸收(≤17px,见 viewportScrollPx 注释)。
        // 滚轮/回底/贴底路径不经此折算:scrollBy 直接以「行」写权威,无 px 往返,天然无折损。
        // 注意:非整数 display 时互逆不成立,但只有滚轮平滑插值期 display 非整,且该期无人
        // 经 setScrollOffset 回写行域(权威 = SmoothScroller 行目标)→ 折算冲突不存在。
        final int lineHeight = Math.max(1, ChatMarkdownSettings.getChatLineHeightPx());
        ChatScrollbar.Result scrollbar = ChatScrollbar.create(rt, listViewport, viewportScrollPx,
                offset -> {
                    // scene px(已由 SceneScrollbar clamp 到 [0, maxScrollY])→ 聊天行(自底部向上);
                    // 这是唯一的「px → 行域」折算回写点,与滚轮/回底同写 history.scrollBy 通道
                    int maxScroll = SceneGeometry.maxScrollY(listViewport);
                    int chatPx = Math.max(0, maxScroll - offset.intValue());
                    int targetLines = (int) Math.round(chatPx / (double) lineHeight);
                    int current = controller.history().getScroll();
                    if (targetLines != current) {
                        // scrollBy 下限 0 clamp 与滚轮路径一致(上限由视口偏移 clamp 折算保证)
                        controller.history().scrollBy(targetLines - current);
                        controller.notifyDataChanged();
                    }
                },
                controller.frameMillisSignal(),
                offsetPx -> {
                    int maxScroll = SceneGeometry.maxScrollY(listViewport);
                    int chatPx = Math.max(0, maxScroll - offsetPx.intValue());
                    controller.smoothScroll().snapTo((int) Math.round(chatPx / (double) lineHeight));
                });
        scrollbar.column().setMargin(0, 2, 0, 0);
        listRow.appendChild(scrollbar.column());

        // 输入条(容器内底部,设计稿 §6.2:输入条区高 40 贴容器底)。
        // ★ 固定高(40)是 COLUMN 容器的"先验固定兄弟":缺了它,ConstraintResolver 的
        //   grow 分配会因"固定兄弟高度无法先验"而对 listRow 回退 shrink-to-fit,
        //   消息区不撑满、输入条悬在内容高度之后(重心塌陷,B12 真机）。
        ChatInputBar bar = new ChatInputBar(rt, initialText);
        // K3 缺陷 F6②:输入条区四周 8px 内边距(设计稿 §2.3/§6.2)——修复前 divider 到输入框
        // 顶仅 5px、输入框贴边;8 + 输入框高 24 + 8 = 40 恰好占满输入条区高
        SceneNode barRow = SceneNode.row()
                .setHitTestable(false)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setPadding(INPUT_AREA_PADDING_PX)
                .setPreferredHeight(ChatMarkdownSettings.getInputBarHeightPx());
        barRow.appendChild(bar.root());
        containerNode.appendChild(barRow);

        // 输入条顶部分隔线(设计稿 §6.2:滚动消息区 → 1px 分隔线 → 输入条区;divider-input 8% 白)
        SceneNode divider = new SceneNode()
                .setHitTestable(false)
                .setPreferredHeight(1)
                .setBackgroundColor(ChatMarkdownSettings.getDividerInputArgb());
        containerNode.insertBefore(divider, barRow);

        // 输入条上方「↓ N 条新消息」提示(设计稿 §5.1 P1):unreadSignal > 0 时显示,点击回底。
        // 挂摘式显隐:文本节点空文本也占一行(拆分契约「至少一行」),故 unread=0 时移出树(零占位、
        // 不消费命中);显示时插到分隔线上方(设计稿 §6.2:提示位于 Divider 上方)。
        SceneNode hintNode = new SceneNode()
                .setHitTestable(false)
                .setFontSize(ChatMarkdownSettings.getNameFontSizePx())
                .setTextColor(ChatMarkdownSettings.getNewMessageHintArgb())
                .setAlignSelf(AlignSelf.CENTER);
        Binding hintBinding = rt.bind(controller.unreadSignal(), count -> {
            int n = count.intValue();
            if (n > 0) {
                if (hintNode.__getParent() == null) {
                    containerNode.insertBefore(hintNode, divider);
                }
                hintNode.setText("↓ " + n + " 条新消息");
                hintNode.setHitTestable(true);
            } else if (hintNode.__getParent() != null) {
                containerNode.removeChild(hintNode);
                hintNode.setHitTestable(false);
            }
        });
        InputBinding hintInputBinding = rt.on(hintNode, SceneEventType.POINTER_DOWN,
                (SceneEvent event, SceneEventContext ctx) -> controller.scrollToBottom());

        return new Result(containerNode, listHandle, scrollBinding, hintBinding, hintInputBinding,
                scrollbar, bar, controller);
    }
}
