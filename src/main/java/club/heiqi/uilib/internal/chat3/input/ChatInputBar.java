package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天输入条组件(L3 组件层):复用 UILib {@link SceneTextInput} 的受控输入框,
 * 内聚文本真值、发送历史、历史回显与 Tab 补全。键盘节流(Enter/Up/Down/Tab/PageUp/PageDown)
 * 仍由屏幕壳完成。Tab 补全状态机下沉 {@link ChatCompletionEngine}(idle → awaiting → cycling),
 * 本类只做宿主窄端口适配(网络发包/本地玩家表/候选打印)。
 */
public final class ChatInputBar implements ChatCompletionEngine.Host {

    /** 输入字号。 */
    private static final int INPUT_FONT_SIZE = 14;
    /** 输入框高(px,设计稿 §6.2:输入条区 40 - 四周 8×2 内边距 = 24)。 */
    private static final int INPUT_HEIGHT_PX = 24;
    /** 输入框内水平 padding(px,设计稿 §2.3 内边距定值 paddingX=10)。 */
    private static final int INPUT_PADDING_X_PX = 10;
    /** 输入框内垂直 padding(px,24 盒高 - font-input 14 行高 20 = 上下各 2)。 */
    private static final int INPUT_PADDING_Y_PX = 2;
    /** 输入上限(原版 GuiTextField.maxStringLength 口径,100 码点;T5)。 */
    static final int MAX_INPUT_LENGTH = 100;

    private final SceneRuntime runtime;
    private final Signal<String> inputText;
    private final ChatSentHistory sentHistory = new ChatSentHistory();
    private final SceneNode inputRoot;
    /** SceneTextInput 句柄(autocomplete commit 的 caret 对齐窄操作)。 */
    private final SceneTextInput.Handle inputHandle;
    /** Tab 补全状态机(idle → awaiting → cycling)。 */
    private final ChatCompletionEngine completion;
    /** 聊天输入条描边覆盖绑定(设计稿 §2.1:非 focus 无描边,focus 1px 淡蓝 25%)。 */
    private final Binding borderBinding;
    /** 输入底色覆盖绑定(K3 缺陷:F6① SceneTextInput 内部 SceneStateColors.inputBackground
     *  = 0xFF211F26(实测 33,31,38)覆盖了设计令牌,此处按 bg-input 0xFF1E232A 恒值覆盖)。 */
    private final Binding backgroundBinding;

    /**
     * @param runtime     宿主场景运行时(SceneTextInput 挂载 + 焦点)
     * @param initialText 预填文本(斜杠键进入时 = "/",可为空)
     */
    public ChatInputBar(SceneRuntime runtime, String initialText) {
        this.runtime = runtime;
        this.inputText = Signal.create(initialText == null ? "" : initialText);
        this.completion = new ChatCompletionEngine(this);
        SceneTextInput.Props props = SceneTextInput.Props.builder(inputText)
                // 设计稿 §3.2:placeholder「输入消息…」色 text-input-placeholder 0xFF6E757E
                // (chat3 层窄口覆盖,SceneTextInput 通用 secondaryText 默认值不动)
                .placeholder("输入消息…")
                .placeholderColor(Integer.valueOf(ChatMarkdownSettings.getInputPlaceholderArgb()))
                .maxLength(MAX_INPUT_LENGTH)
                .onChange(next -> {
                    inputText.set(next);
                    completion.onTextEdited();
                })
                .build();
        this.inputHandle = SceneTextInput.createHandle(runtime, props);
        this.inputRoot = inputHandle.component().get();
        this.inputRoot.setFontSize(INPUT_FONT_SIZE);
        this.inputRoot.setFillParentWidth(true);
        // 设计稿 §3.2:输入框圆角 r-md = 8(覆盖 SceneTextInput 通用 RADIUS_MD 12)
        this.inputRoot.setCornerRadius(ChatMarkdownSettings.getInputCornerRadiusPx());
        // K3 缺陷 F6②:输入框高钉 24px(40 - 四周 8×2),内 padding 覆盖通用 PAD_MD=8
        // 为 (2,10,2,10)——24 盒高下 font-input 14px 行高 20 恰好撑满(设计稿 §6.2/§2.3)
        this.inputRoot.setPreferredHeight(INPUT_HEIGHT_PX);
        this.inputRoot.setPadding(INPUT_PADDING_Y_PX, INPUT_PADDING_X_PX, INPUT_PADDING_Y_PX,
                INPUT_PADDING_X_PX);
        // K3 缺陷 F6①:输入底色 = 设计令牌 bg-input 0xFF1E232A。SceneTextInput 内部
        // __bindAnimatedColor(SceneStateColors.inputBackground = BG_PRESSED 0xFF211F26,
        // 实测 (33,31,38))每帧覆盖背景,此处恒值覆盖绑定(注册晚于控件内部绑定,
        // 帧末批量提交时覆盖值恒生效,与 borderBinding 同技巧)。
        this.backgroundBinding = runtime.bind(Computed.create(() -> Integer.valueOf(
                ChatMarkdownSettings.getInputBackgroundArgb())),
                inputRoot::setBackgroundColor);
        // 设计稿 §2.1:非 focus 无描边(透明),focus 1px 0x406B9BD8(25% 淡蓝)。
        // SceneTextInput 通用描边(=0xFF938F99 常驻灰紫)不符合聊天设计,此处按交互态覆盖;
        // 绑定注册晚于控件内部绑定,帧末批量提交时覆盖值恒生效。
        this.borderBinding = runtime.bind(Computed.create(() -> Integer.valueOf(
                Boolean.TRUE.equals(runtime.interactionState(inputRoot).focused().get())
                        ? ChatMarkdownSettings.getInputFocusBorderArgb() : 0x00000000)),
                inputRoot::setBorderColor);
    }

    /** 释放描边/底色覆盖绑定(屏幕关闭时由容器统一回收)。 */
    public void dispose() {
        if (borderBinding != null) {
            borderBinding.dispose();
        }
        if (backgroundBinding != null) {
            backgroundBinding.dispose();
        }
    }

    /** @return 输入框根节点(挂到容器输入行) */
    public SceneNode root() {
        return inputRoot;
    }

    /** @return 输入文本真值(受控源) */
    public Signal<String> inputText() {
        return inputText;
    }

    /** 屏幕打开:聚焦输入框 + 同步发送历史(覆盖第三方直调)+ 清补全残留态。 */
    public void onOpened() {
        runtime.requestFocus(inputRoot);
        sentHistory.syncFrom(currentSentMessages());
        sentHistory.resetCursor();
        completion.onTextEdited();
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return inputText.get().trim();
    }

    /** 记录已发送(发送路径增量同步)。 */
    public void recordSent(String message) {
        sentHistory.add(message);
    }

    /** 历史回显(vanilla getSentHistory 语义:-1 上一条 / +1 下一条;回到底恢复暂存草稿)。 */
    public void recallHistory(int direction) {
        String recalled = sentHistory.recall(direction, inputText.get());
        inputText.set(recalled);
        completion.onTextEdited();
    }

    /** 直接回填文本(SUGGEST_COMMAND 点击等外部写入;caret 对齐词尾,清补全态)。 */
    public void setText(String text) {
        String next = text == null ? "" : text;
        inputHandle.moveCaretToEndOf().accept(next);
        inputText.set(next);
        completion.onTextEdited();
    }

    /** Tab 补全(委托状态机;direction +1 正向 Tab,-1 Shift+Tab 反向)。 */
    public void autocomplete(int direction) {
        completion.onTab(direction);
    }

    /** 服务端补全响应(mixin 转交;快照守卫与两段式在状态机内)。 */
    public void applyAutocompleteResponse(String[] options) {
        completion.onResponse(options);
    }

    // ==================== ChatCompletionEngine.Host ====================

    @Override
    public String currentText() {
        return inputText.get();
    }

    @Override
    public void commit(String nextText) {
        // 先写 value 再对齐 caret 到词尾(primitive moveCaretToEndOf 语义:
        // autocomplete commit 在外部 value signal flush 前同步对齐 caret)
        inputText.set(nextText);
        inputHandle.moveCaretToEndOf().accept(nextText);
    }

    @Override
    public boolean networkAvailable() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.thePlayer != null && mc.thePlayer.sendQueue != null;
    }

    @Override
    public void sendRequest(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(text));
    }

    @Override
    public List<String> localPlayerNames() {
        List<String> names = new ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return names;
        }
        for (Object info : mc.thePlayer.sendQueue.playerInfoList) {
            if (info instanceof GuiPlayerInfo && ((GuiPlayerInfo) info).name != null) {
                names.add(((GuiPlayerInfo) info).name);
            }
        }
        return names;
    }

    @Override
    public void printCandidates(List<String> candidates) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(candidates.get(i));
        }
        // 原版同款:messageId=1 同 id 覆盖打印(ChatHistory 对非 0 id 是替换语义,不刷屏)
        mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                new ChatComponentText(sb.toString()), 1);
    }

    /** 原版发送列表快照(继承自 Facade)。 */
    private static List<String> currentSentMessages() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return Collections.emptyList();
        }
        GuiNewChat gui = mc.ingameGUI.getChatGUI();
        return gui.getSentMessages();
    }
}
