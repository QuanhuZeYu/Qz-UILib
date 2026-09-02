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
 *
 * <p>关闭路径(Esc/提交 Enter)延迟:先请求容器 CLOSING 动画({@link ChatInputSurface#requestClose},
 * 140ms 淡出+下滑,设计稿 §4.1)→ 动画完成回调才真正 {@code displayGuiScreen(null)}
 * (此后 onGuiClosed → surface.onClosed → 容器销毁)。延迟期间屏幕仍是 ChatInputScreen,
 * 控制器保持打开态,时序无改动需求(打开衔接 COLLAPSING/POPPING 已存在)。</p>
 */
public final class ChatInputScreen extends McScreenBridge {

    /** ESC 键码(原版 GuiScreen:1)。 */
    private static final int KEY_ESCAPE = 1;

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

    /**
     * 每游戏 tick(渲染栈外)推进关闭动画兜底:动画完成后取走回调在此真正关屏——
     * 不能在 render 栈内触发(displayGuiScreen → onGuiClosed 会销毁 surface,打断本帧渲染);
     * 渲染停滞时 500ms 超时也在此强制完成,不放任屏幕卡死。
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        surface.tickCloseState();
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
        if (keyCode == KEY_ESCAPE) {
            // 关闭路径:先播容器 CLOSING 动画再关屏(不交父壳——McScreenBridge/原版会立即
            // displayGuiScreen(null),动画不可见;动画期间重复 Esc 由 requestClose 幂等)
            requestClose();
            return;
        }
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
            // 坐标不往下传:命中判据是 scene CLICK 记下的节点身份(见 handleLineClick 注释)
            surface.handleLineClick();
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
     *
     * <p>关闭延迟 140ms(先播容器 CLOSING 动画再关屏,无体感问题):动画期间重复 Enter
     * 幂等跳过(不重发不重关,防按键重复 double-send)。</p>
     */
    private void submit() {
        if (surface.isClosePending()) {
            return; // 关闭动画期间重复 Enter:幂等(首次发送已入历史/已发包)
        }
        String message = surface.submitText();
        if (message != null) {
            ChatBridge.send(message);
        }
        requestClose(); // 发送后:先播关闭动画再关屏
    }

    /** 请求关闭:委托 surface 播放 CLOSING 动画,完成回调 = 真正关屏。 */
    private void requestClose() {
        surface.requestClose(this::closeScreenAfterAnimation);
    }

    /**
     * 关闭动画完成回调:真正关屏(此后 onGuiClosed → surface.onClosed → 容器正常销毁),
     * 随后通知 surface 切 HUD(forceHud,跳过机器 CLOSING 空窗,气泡立即挂回)。
     */
    private void closeScreenAfterAnimation() {
        Minecraft mc = Minecraft.getMinecraft();
        // 只关自己:动画期间若被其他屏幕顶替(异路径打开),不误关新屏幕
        if (mc != null && mc.currentScreen == this) {
            mc.displayGuiScreen((GuiScreen) null);
            surface.notifyScreenClosed();
        }
    }
}
