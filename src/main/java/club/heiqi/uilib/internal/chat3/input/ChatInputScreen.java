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

    /** LWJGL 键码(原版 GuiChat 同表)。 */
    private static final int KEY_RETURN = 28;
    private static final int KEY_UP = 200;
    private static final int KEY_DOWN = 208;
    private static final int KEY_TAB = 15;
    private static final int KEY_PRIOR = 201; // Page Up
    private static final int KEY_NEXT = 209; // Page Down

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
        if (keyCode == KEY_RETURN) {
            submit();
            return;
        }
        if (keyCode == KEY_UP) {
            surface.recallHistory(-1);
            return;
        }
        if (keyCode == KEY_DOWN) {
            surface.recallHistory(1);
            return;
        }
        if (keyCode == KEY_TAB) {
            surface.autocomplete(GuiScreen.isShiftKeyDown() ? -1 : 1);
            return;
        }
        if (keyCode == KEY_PRIOR) {
            surface.pageScroll(1);
            return;
        }
        if (keyCode == KEY_NEXT) {
            surface.pageScroll(-1);
            return;
        }
        super.keyTyped(typedChar, keyCode);
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
     * 发送路径经 {@link ChatBridge} 原版链(入发送历史 → 命令探测 → 发包)。
     */
    private void submit() {
        String message = surface.takeText();
        surface.recordSent(message);
        if (!message.isEmpty()) {
            ChatBridge.send(message);
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.displayGuiScreen((GuiScreen) null);
        }
    }
}
