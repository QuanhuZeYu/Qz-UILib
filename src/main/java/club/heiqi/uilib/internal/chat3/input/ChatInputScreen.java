package club.heiqi.uilib.internal.chat3.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.api.chat.ChatBridge;
import club.heiqi.uilib.ui.screen.McScreenBridge;

/**
 * 聊天输入屏幕(L4 交互层):替换原版 GuiChat 的自研屏幕——容器(消息列表 + 输入条)取代
 * 原版输入框,输入条纳入容器底部。
 *
 * <p>键盘节流在 GuiScreen.keyTyped 层完成(Enter 发送/上下键历史/Tab 补全(Shift+Tab 反向)/
 * PageUp/PageDown 聊天翻页),其余按键经
 * {@link McScreenBridge} 转进 scene 输入管线。发送走 {@link ChatBridge} 原版发送链
 * (已发送历史/命令分发/发包全原版语义)。聊天打开感知由安装器每渲染帧按
 * currentScreen instanceof ChatInputScreen 同步。</p>
 */
public final class ChatInputScreen extends McScreenBridge {

    private final ChatInputSurface surface;

    /**
     * @param initialText 预填文本(斜杠键进入时 = "/",可为空)
     */
    public ChatInputScreen(String initialText) {
        super(null, new ChatInputSurface(initialText));
        this.surface = (ChatInputSurface) getSurface();
    }

    @Override
    public void initGui() {
        super.initGui();
        surface.onOpened();
    }

    @Override
    public void onGuiClosed() {
        try {
            surface.onClosed();
        } finally {
            super.onGuiClosed();
        }
    }

    /** 聊天屏幕与世界叠加(原版 GuiChat 无背景层)。 */
    @Override
    public void drawDefaultBackground() {
        // 透明:容器与输入条直接叠在世界画面上
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        ChatInputKeyAction.Action action = ChatInputKeyAction.of(keyCode);
        if (action != ChatInputKeyAction.Action.TAB) {
            // 原版 GuiChat:91:任何非 Tab 键清补全循环态(方向键/Home/End 移动光标不改变文本,
            // 不触发 onChange 清态,必须显式清,否则旧循环态残留)
            surface.clearCompletionCycle();
        }
        switch (action) {
            case SUBMIT:
                submit();
                return;
            case HISTORY_UP:
                surface.recallHistory(-1);
                return;
            case HISTORY_DOWN:
                surface.recallHistory(1);
                return;
            case TAB:
                surface.autocomplete(GuiScreen.isShiftKeyDown() ? -1 : 1);
                return;
            case PAGE_UP:
                surface.pageScroll(1);
                return;
            case PAGE_DOWN:
                surface.pageScroll(-1);
                return;
            default:
                super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            surface.handleLineClick(mouseX, mouseY);
        }
    }

    /** 服务端补全响应(mixin 转交)。 */
    public void onAutocompleteResponse(String[] options) {
        surface.applyAutocompleteResponse(options);
    }

    /**
     * 提交并关闭(原版 func_146403_a 语义:trim 后非空才发;空输入仅关屏)。
     * 判空后再入发送历史(空 Enter 不污染 Up/Down 历史),发送路径经 {@link ChatBridge}
     * 原版链(入发送历史 → 命令探测 → 发包)。
     */
    private void submit() {
        String message = surface.submitText();
        if (message != null) {
            ChatBridge.send(message);
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.displayGuiScreen((GuiScreen) null);
        }
    }
}
