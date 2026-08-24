package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.input.ChatInputBar;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
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

        /** 每帧同步动态尺寸(视口 1/8 × 1/2)与气泡最大宽(设计稿 §3.x:气泡 ≤ 0.85 组内容宽)。 */
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
        SceneNode containerNode = SceneNode.column()
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
        SceneListHandle listHandle = controller.messageList().mount(rt, listViewport,
                controller.groupsSignal(), ChatMessageList.Style.container(), registry,
                controller.frameMillisSignal());

        // 滚动唯一汇点:历史滚动偏移(px) → 视口滚动属性(结构版本驱动重算)。
        // 滚动条显示源与容器绑定共享同一 Computed(同源同值)。
        Computed<Integer> scrollPx = Computed.create(controller::scrollOffsetPx);
        Binding scrollBinding = rt.bind(scrollPx,
                offset -> listViewport.setScrollOffsetY(offset.intValue()));

        // 滚动条:与视口同级 ROW 内右对齐,右内边距 2(贴容器右缘)。
        // setScrollOffset 回调与滚轮路径同源(history.scrollBy + notifyDataChanged)。
        // onDragStart:拖动接管时机 → 平滑器 snapTo 当前显示行(取消平滑、进入直通,拖动手感即时;
        // 拖动中每次 setScrollOffset 目标变化经平滑器直通直接到位,display 恒等于目标)。
        final int lineHeight = Math.max(1, ChatMarkdownSettings.getChatLineHeightPx());
        ChatScrollbar.Result scrollbar = ChatScrollbar.create(rt, listViewport, scrollPx,
                offset -> {
                    int targetLines = (int) Math.round(offset.doubleValue() / (double) lineHeight);
                    int current = controller.history().getScroll();
                    if (targetLines != current) {
                        // scrollBy 下限 0 clamp 与滚轮路径一致(上限=可滚行数,由 maxScrollY 折算保证)
                        controller.history().scrollBy(targetLines - current);
                        controller.notifyDataChanged();
                    }
                },
                controller.frameMillisSignal(),
                offsetPx -> controller.smoothScroll().snapTo(
                        (int) Math.round(offsetPx.doubleValue() / (double) lineHeight)));
        scrollbar.column().setMargin(0, 2, 0, 0);
        listRow.appendChild(scrollbar.column());

        // 输入条(容器内底部,设计稿 §6.2:输入条区高 40 贴容器底)。
        // ★ 固定高(40)是 COLUMN 容器的"先验固定兄弟":缺了它,ConstraintResolver 的
        //   grow 分配会因"固定兄弟高度无法先验"而对 listRow 回退 shrink-to-fit,
        //   消息区不撑满、输入条悬在内容高度之后(重心塌陷,B12 真机）。
        ChatInputBar bar = new ChatInputBar(rt, initialText);
        SceneNode barRow = SceneNode.row()
                .setHitTestable(false)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
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
