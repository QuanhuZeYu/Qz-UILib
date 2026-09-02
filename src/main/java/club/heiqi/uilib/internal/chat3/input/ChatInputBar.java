package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.command.ICommandSender;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.MaxLengthUnit;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.control.SceneTextInputPrimitive;
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
    /** 输入上限(原版 GuiTextField.maxStringLength 口径:100 UTF-16 单元,emoji 占 2 单元)。 */
    static final int MAX_INPUT_LENGTH = 100;
    /** 块字符:§(U+00A7)。原版 ChatAllowedCharacters 拒绝 §,服务器对含 § 消息踢
     *  「illegal character in chat」(真机实证);键入/粘贴/TEXT_INPUT 由 primitive
     *  filterForInsert 剔除,外部直写入口(setText/历史回显/补全 commit/记录)走
     *  sanitize 同源剔除,保证任何路径都不放行 §。 */
    private static final String BLOCKED_CHARS = "\u00A7";

    private final SceneRuntime runtime;
    private final Signal<String> inputText;
    private final ChatSentHistory sentHistory = new ChatSentHistory();
    private final SceneNode inputRoot;
    /** SceneTextInput 句柄(autocomplete commit 的 caret 对齐窄操作)。 */
    private final SceneTextInput.Handle inputHandle;
    /** Tab 补全状态机(idle → awaiting → cycling);I5 测试注入口可整体替换为假引擎。 */
    private ChatCompletionEngine completion;
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
        // TA:预填(斜杠开屏等)也过块字符过滤,与外置写入入口同源防御
        this.inputText = Signal.create(sanitize(initialText));
        this.completion = new ChatCompletionEngine(this);
        SceneTextInput.Props props = SceneTextInput.Props.builder(inputText)
                // 设计稿 §3.2:placeholder「输入消息…」色 text-input-placeholder 0xFF6E757E
                // (chat3 层窄口覆盖,SceneTextInput 通用 secondaryText 默认值不动)
                .placeholder("输入消息…")
                .placeholderColor(Integer.valueOf(ChatMarkdownSettings.getInputPlaceholderArgb()))
                .maxLength(MAX_INPUT_LENGTH)
                // 与原版 maxStringLength=100 同口径:UTF-16 单元(emoji 占 2 单元);公共默认 CODEPOINT 不动
                .maxLengthUnit(MaxLengthUnit.UTF16)
                // TA:禁 §(U+00A7)——primitive 输入路径逐字符剔除,与 ChatAllowedCharacters 拒绝表对齐
                .blockChars(BLOCKED_CHARS)
                .onChange(next -> {
                    inputText.set(next);
                    completion.onTextEdited();
                })
                .build();
        this.inputHandle = SceneTextInput.createHandle(runtime, props);
        this.inputRoot = inputHandle.component().get();
        this.inputRoot.setFontSize(INPUT_FONT_SIZE);
        this.inputRoot.setFillParentWidth(true);
        // 输入框圆角走 settings（2026-09-02 起按同心规则 = 容器 20 - 内缩 8 = 12，
        // 不再是设计稿早期的 r-md 8；容器半径再变要同步改 settings 的那个值）
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
        // 液态玻璃：输入条底色 alpha 走同一条覆盖绑定（SceneTextInput 内部每帧重烘焙
        // 背景，只有在这里出半透明值才不会被覆盖回实色），并挂上 backdrop 声明。
        this.backgroundBinding = runtime.bind(Computed.create(() -> Integer.valueOf(
                ChatMarkdownSettings.isGlassEnabled()
                        ? (ChatMarkdownSettings.getInputBackgroundArgb() & 0x00FFFFFF)
                                | (ChatMarkdownSettings.getGlassInputAlpha() << 24)
                        : ChatMarkdownSettings.getInputBackgroundArgb())),
                inputRoot::setBackgroundColor);
        if (ChatMarkdownSettings.isGlassEnabled()) {
            inputRoot.setBackdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                    ChatMarkdownSettings.getGlassBlurRadiusPx(), ChatMarkdownSettings.getGlassLensStrength()));
        }
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

    /** 屏幕打开:同步发送历史(覆盖第三方直调)+ 清补全残留态 + 聚焦与 caret 对齐。 */
    public void onOpened() {
        sentHistory.syncFrom(currentSentMessages());
        sentHistory.resetCursor();
        completion.onTextEdited();
        focusAndAlignCaret();
    }

    /**
     * 聚焦输入框 + 预填 caret 归行尾(原版 GuiChat setText 后光标在末尾;primitive caret 从 0 起,
     * 斜杠预填时不对齐会插成 "t/…")。独立方法便于 headless 单测(vanilla 历史同步触 Minecraft,
     * 测试无法走 onOpened 全路径)。
     */
    void focusAndAlignCaret() {
        runtime.requestFocus(inputRoot);
        inputHandle.moveCaretToEndOf().accept(inputText.get());
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return inputText.get().trim();
    }

    /**
     * 提交文本(trim 后):空串不入发送历史(原版空 Enter 仅关屏,不污染 Up/Down 历史),
     * 非空记录发送历史并返回消息文本。
     *
     * @return 消息文本;空串返回 null
     */
    public String submitText() {
        String message = inputText.get().trim();
        if (message.isEmpty()) {
            return null;
        }
        sentHistory.add(message);
        return message;
    }

    /** 记录已发送(发送路径增量同步;TA:入库前剔除块字符,保证历史回显恒干净)。 */
    public void recordSent(String message) {
        sentHistory.add(sanitize(message));
    }

    /** 历史回显(vanilla getSentHistory 语义:-1 上一条 / +1 下一条;回到底恢复暂存草稿)。 */
    public void recallHistory(int direction) {
        String recalled = sentHistory.recall(direction, inputText.get());
        // I3 草稿清空修复:recall 返回 null 表示无效操作(底槽按 ↓ / 空历史按 ↑),完全早退,
        // 不动 caret/文本/补全态(注意:sanitize 内部 nullSafe 会把 null 变 "" 吞掉哨兵,
        // 必须先判 null 再过滤)
        if (recalled == null) {
            return;
        }
        // TA:历史串过块字符过滤(防御:recordSent 外部直传也可能带 §)
        String clean = sanitize(recalled);
        // caret 归行尾(与 setText 同款:原版回显后光标在末尾,继续输入追加而非插到行首)
        inputHandle.moveCaretToEndOf().accept(clean);
        inputText.set(clean);
        completion.onTextEdited();
    }

    /** 直接回填文本(SUGGEST_COMMAND 点击等外部写入;caret 对齐词尾,清补全态)。 */
    public void setText(String text) {
        // TA:外部直写入口统一过滤 §(语义与输入路径一致)
        String next = sanitize(text == null ? "" : text);
        inputHandle.moveCaretToEndOf().accept(next);
        inputText.set(next);
        completion.onTextEdited();
    }

    /**
     * Tab 补全(委托状态机;direction +1 正向 Tab,-1 Shift+Tab 反向)。
     *
     * <p>I5 caret 非词尾屏蔽(方案 B):补全按 {@link ChatCompletionState#wordStart(String)}
     * 恒取行尾词,caret 在词中/词首/分隔符上按 Tab 会拿行尾词候选整段替换并改写用户编辑位置。
     * 此处 caret 非「当前词词尾」直接 return,不进入状态机——文本/caret/补全态全部不动,状态机零改动。</p>
     *
     * <p>体感说明:原版 1.7.10 实际按行尾词补全;B 是「宁可不补、不改用户编辑位」的安全取舍,
     * 符合体感对齐而非实现对齐——原版体感核心是不打断编辑位置。commit 仍 moveCaretToEndOf
     * 归词尾,补全后 caret 对齐语义不变。</p>
     */
    public void autocomplete(int direction) {
        if (!caretAtWordEnd(inputText.get(), currentCaretCp())) {
            return;
        }
        completion.onTab(direction);
    }

    /**
     * caret 是否恰在「当前词」(即 {@link ChatCompletionState#wordStart(String)} 所取的行尾词)的词尾。
     * 词边界与 wordStart 同源(空格为唯一分隔符):caret == 行尾词结束位(含行尾空格域的空词尾、
     * 越上界 clamp)才放行;词中/词首/分隔符上/空文本一律屏蔽。caretCp 越界按 wordStart 同款 clamp 语义。
     */
    static boolean caretAtWordEnd(String text, int caretCp) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int ws = ChatCompletionState.wordStart(text);
        int wordEndCp = ws + text.substring(ws).codePointCount(0, text.length() - ws);
        return caretCp >= wordEndCp;
    }

    /**
     * caret 码点索引读取路径(I5 关键陷阱):primitive 的 caretIndex signal 是帧末投影
     * (Signal.set 入 pending,flush 才 applyAndNotify),flush 外 get() 读旧值;SceneTextInputPrimitive
     * 内 caretAuthority(int[]) 是即时真值但包外不可达,本任务不改其公共接口。
     * 此处改用帧末显示投影:primitive 结构固定「子 0 = prefixText = caret 前显示子串」,
     * 其文本由 rt.bindComputed 在 flush 时与 caret 同帧更新,码点数即 caret 码点索引。
     * Tab 按键发生在上一帧 flush 之后、下一次按键前,读上一帧末投影无旧值问题;投影不可用时保守返回 0
     * (caret 在词首 → 非词尾 → 屏蔽,宁可不补,与方案 B 的安全方向一致)。
     */
    private int currentCaretCp() {
        if (inputRoot == null || inputRoot.__getChildren().isEmpty()) {
            return 0;
        }
        String prefix = inputRoot.__getChildren().get(0).getText();
        return prefix == null ? 0 : prefix.codePointCount(0, prefix.length());
    }

    /** I5 测试注入口:替换补全状态机(headless 用记录型假宿主观察 Tab 是否驱动状态机;生产恒用真引擎)。 */
    void __setCompletionEngineForTest(ChatCompletionEngine engineForTest) {
        completion = engineForTest;
    }

    /** 剔除块字符(直接写入口与 primitive 输入路径同源:stripBlockedChars;命中才分配)。 */
    private static String sanitize(String text) {
        return SceneTextInputPrimitive.stripBlockedChars(text, BLOCKED_CHARS);
    }

    /** 非 Tab 键清补全循环态(原版 GuiChat:91:任何非 Tab 键清循环;方向键等不改变文本不触发 onChange)。 */
    public void clearCompletionCycle() {
        completion.onTextEdited();
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
        // TA:补全 commit 过块字符过滤(候选来源含客户端命令表/玩家表/服务端响应)
        String next = sanitize(nextText);
        // 先写 value 再对齐 caret 到词尾(primitive moveCaretToEndOf 语义:
        // autocomplete commit 在外部 value signal flush 前同步对齐 caret)
        inputText.set(next);
        inputHandle.moveCaretToEndOf().accept(next);
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
    public List<String> localCommandCompletions(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        return localCommandCompletions(mc == null ? null : mc.thePlayer, text);
    }

    /**
     * 本地客户端命令候选(Forge ClientCommandHandler 注册表,同步纯函数,headless 可测)。
     *
     * <p>与 forge autoComplete 同语义:候选不含 "/",正在补命令名(无空格)时逐候选补 "/" 前缀,
     * 有空格(补子命令/参数)时不补;颜色前缀(§7…§r)一期不加。</p>
     *
     * @param player 命令发送者(客户端玩家;null 返回空)
     * @param text   输入全文(非 "/" 开头返回空)
     * @return 可直接入框的候选(保序;无候选/无效输入返回空列表)
     */
    static List<String> localCommandCompletions(ICommandSender player, String text) {
        List<String> result = new ArrayList<String>();
        if (player == null || text == null || !text.startsWith("/")) {
            return result;
        }
        String commandPart = text.substring(1);
        List<String> options = ClientCommandHandler.instance.getPossibleCommands(player, commandPart);
        if (options == null) {
            return result;
        }
        boolean completingCommandName = commandPart.indexOf(' ') < 0;
        for (String option : options) {
            if (option == null || option.isEmpty()) {
                continue;
            }
            result.add(completingCommandName ? "/" + option : option);
        }
        return result;
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
