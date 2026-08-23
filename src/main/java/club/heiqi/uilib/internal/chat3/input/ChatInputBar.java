package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.network.play.client.C14PacketTabComplete;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天输入条组件(L3 组件层):复用 UILib {@link SceneTextInput} 的受控输入框,
 * 内聚文本真值、发送历史、历史回显与 Tab 补全。键盘节流(Enter/Up/Down/Tab)仍由屏幕壳完成。
 */
public final class ChatInputBar {

    /** 输入字号。 */
    private static final int INPUT_FONT_SIZE = 14;

    private final SceneRuntime runtime;
    private final Signal<String> inputText;
    private final ChatSentHistory sentHistory = new ChatSentHistory();
    private final SceneNode inputRoot;
    /** 玩家名补全上次命中缓存(连续 Tab 循环)。 */
    private final List<String> lastFoundNames = new ArrayList<String>();

    /**
     * @param runtime     宿主场景运行时(SceneTextInput 挂载 + 焦点)
     * @param initialText 预填文本(斜杠键进入时 = "/",可为空)
     */
    public ChatInputBar(SceneRuntime runtime, String initialText) {
        this.runtime = runtime;
        this.inputText = Signal.create(initialText == null ? "" : initialText);
        SceneTextInput.Props props = SceneTextInput.Props.builder(inputText)
                .placeholder("消息…")
                .onChange(inputText::set)
                .build();
        this.inputRoot = SceneTextInput.create(runtime, props).get();
        this.inputRoot.setFontSize(INPUT_FONT_SIZE);
        this.inputRoot.setFillParentWidth(true);
    }

    /** @return 输入框根节点(挂到容器输入行) */
    public SceneNode root() {
        return inputRoot;
    }

    /** @return 输入文本真值(受控源) */
    public Signal<String> inputText() {
        return inputText;
    }

    /** 屏幕打开:聚焦输入框 + 同步发送历史(覆盖第三方直调)。 */
    public void onOpened() {
        runtime.requestFocus(inputRoot);
        sentHistory.syncFrom(currentSentMessages());
        sentHistory.resetCursor();
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

    /** 直接回填文本(SUGGEST_COMMAND 点击等外部写入)。 */
    public void setText(String text) {
        inputText.set(text);
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

    /** 原版发送列表快照(继承自 Facade)。 */
    private static List<String> currentSentMessages() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return Collections.emptyList();
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
}
