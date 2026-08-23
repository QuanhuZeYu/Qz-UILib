package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.input.ChatInputBar;
import club.heiqi.uilib.ui.reactive.Computed;
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

    /** 输入条四周衬垫(容器内间距)。 */
    private static final int BAR_PADDING = 8;

    /** 容器装配结果:外框节点 + 生命周期句柄 + 输入条。 */
    public static final class Result {

        private final SceneNode root;
        private final SceneListHandle listHandle;
        private final Binding scrollBinding;
        private final ChatInputBar bar;

        private Result(SceneNode root, SceneListHandle listHandle, Binding scrollBinding, ChatInputBar bar) {
            this.root = root;
            this.listHandle = listHandle;
            this.scrollBinding = scrollBinding;
            this.bar = bar;
        }

        /** 释放列表与滚动绑定(屏幕关闭时)。 */
        public void dispose() {
            if (listHandle != null) {
                listHandle.dispose();
            }
            if (scrollBinding != null) {
                scrollBinding.dispose();
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

        /** 每帧同步动态尺寸(视口 1/8 × 1/2)。 */
        public void setViewport(int width, int height) {
            root.setPreferredWidth(ChatMarkdownSettings.chatWidthFor(Math.max(1, width)));
            root.setPreferredHeight(ChatMarkdownSettings.containerHeightFor(Math.max(1, height)));
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

        // 消息列表(controller 容器形态内容,滚动绑定指向容器节点)
        SceneNode list = SceneNode.column().setHitTestable(false);
        containerNode.appendChild(list);
        SceneListHandle listHandle = controller.messageList().mount(rt, list,
                controller.groupsSignal(), ChatMessageList.Style.container(), registry,
                controller.frameMillisSignal());

        // 容器滚动:历史滚动偏移 → 容器滚动属性(结构版本驱动重算)
        Binding scrollBinding = rt.bind(Computed.create(controller::scrollOffsetPx),
                offset -> containerNode.setScrollOffsetY(offset.intValue()));

        // 输入条(容器内底部)
        ChatInputBar bar = new ChatInputBar(rt, initialText);
        SceneNode barRow = SceneNode.row()
                .setHitTestable(false)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setPadding(BAR_PADDING);
        barRow.appendChild(bar.root());
        containerNode.appendChild(barRow);

        return new Result(containerNode, listHandle, scrollBinding, bar);
    }
}
