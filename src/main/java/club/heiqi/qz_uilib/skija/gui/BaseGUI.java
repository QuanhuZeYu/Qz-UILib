package club.heiqi.qz_uilib.skija.gui;

import club.heiqi.qz_uilib.skija.GLCanvas;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2i;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.*;

/**
 * 内部提供了一些便捷的方法来辅助绘制
 * 绘制逻辑只需要在drawScreen即可
 * 更新逻辑可以放在onTick中
 * 添加的组件需要放入 {@code components} 列表中
 * <p/>
 * 当您使用它做继承时，请注意不要使用被标记为废弃的方法，它们可能导致该类产生意料之外的行为
 * <p/>
 * 需要注意的是窗口缩放会重置该类下所有属性，但是不会重置您自己定义的属性，不过仍然推荐您在{@code onGuiClosed}中定义好清除工作，
 * 由于angelica的诡异机制可能导致GUI类的异常行为，不要在GUI类中定义可长久持有的字段后不清除，angelica会缓存GUI实例导致错误甚至崩溃
 */
public abstract class BaseGUI extends GuiScreen {
    public static Logger LOG = LogManager.getLogger();
    /**
     * 狗操的angelica，缓存你妈的GUI类，纯你妈傻逼，多调用个init会少块肉是吧？
     */
    public boolean isInit = false;
    /**
     * 用栈来表示UI层级, 该字段请勿手动修改, 类的实例化和销毁时会自动更新栈
     */
    public static List<BaseGUI> stack = new ArrayList<>();
    public GLCanvas canvas;
    public boolean init = false;
    public long startTime;

    public MouseInfo mouseInfo = new MouseInfo();
    public KeyboardPressInfo keyboardPressInfo = new KeyboardPressInfo();
    public MouseState mouseState = MouseState.NONE;

    public enum MouseState {
        NONE, // 无任何操作
        MOVE, // 未点击坐标移动
        DRAG, // HOLD状态下坐标变化

        HOLD, // 长按 | 移动时按下
        PRESS, // 按下
        RELEASE, // 释放

        CLICK_MULTI, // 连续点击两次以上
        CLICKED, // 按下 - 释放 完整动作才算点击 位置容差 5
    }
    public static int HOLD_TIME = 300; // 300ms
    // 所有的组件列表
    public List<UIComponent> components = new ArrayList<>();



    /**
     * 绘制 {@code component} 请记得讲组件添加到 {@code components} 中, 否则控件无法更新各类事件
     * @param mouseX       鼠标X坐标
     * @param mouseY       鼠标Y坐标
     * @param partialTicks 部分刻时间（用于动画插值）
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!isInit) initGui();
        canvas.render(canvas -> {
            for (UIComponent component : new ArrayList<>(components)) {
                component.draw(canvas);
            }
        });
    }

    public List<UIComponent> hoveredCache = new ArrayList<>();
    /**
     * 游戏循环更新界面
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        for (UIComponent component : new ArrayList<>(components)) {
            component.onTick(); // 自动触发容器中所有组件的更新
            // 尝试触发onHover
            if (component.contains(mouseInfo.currPos.x, mouseInfo.currPos.y)) {
                if (hoveredCache.contains(component)) continue; // 不能return 否则只会停留在最大的父组件上
                else hoveredCache.add(component);
                component.onHover(mouseInfo);
            }
            else {
                if (hoveredCache.contains(component)) {
                    hoveredCache.remove(component);
                    component.onMouseOut(mouseInfo);
                }
            }
            // 尝试触发click
            if (mouseState == MouseState.CLICKED) {
                component.onClick();
                LOG.info("{} 触发点击事件", component.getClass().getName());
            }
        }
    }




    /**
     * 处理输入事件（包含键盘和鼠标）
     */
    @Override
    public void handleInput() {
        if (Mouse.isCreated()) {
            mouseState = MouseState.NONE;
            while (Mouse.next()) { // 鼠标事件只在 移动 点击时更新
                this.handleMouseInput();
            }
            // 检查长按未移动
            if (!mouseInfo.clicked.isEmpty()) {
                for (Map.Entry<Integer, Long> entry : mouseInfo.clicked.entrySet()) {
                    int key = entry.getKey();
                    long time = entry.getValue();
                    if (System.currentTimeMillis() - time > HOLD_TIME) {
                        if (mouseState != MouseState.DRAG) mouseState = MouseState.HOLD;
                        break;
                    }
                    else {
                        if (mouseState != MouseState.DRAG) mouseState = MouseState.PRESS;
                    }
                }
            }
        }

        if (Keyboard.isCreated()) {
            while (Keyboard.next()) {
                this.handleKeyboardInput();
            }
        }
    }

    /**
     * 处理键盘输入事件
     */
    @Override
    public void handleKeyboardInput() {
        // 清除瞬时状态
        keyboardPressInfo.releasedKey = -1;
        if (Keyboard.getEventKeyState()) {
            long time = System.currentTimeMillis();
            char eventCharacter = Keyboard.getEventCharacter();
            int eventKey = Keyboard.getEventKey();
            /*LOG.info("输入: 编码{}-对应字符{} 名称{}", eventKey, Character.toString(eventCharacter), Keyboard.getKeyName(eventKey));*/
            if (eventKey == 1) {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            }
            if (eventKey != 0) {
                keyboardPressInfo.pressed.put(eventKey, time);
            }
        } else {
            // 遍历检查是谁释放了
            Integer releasedKey = keyboardPressInfo.whoReleased();
            if (releasedKey != null) keyboardPressInfo.releasedKey = releasedKey;
        }
        this.mc.func_152348_aa();
    }

    /**
     * 处理鼠标输入事件
     */
    @Override
    public void handleMouseInput() {
        // 清空状态
        mouseInfo.releasedKey = -1;
        mouseInfo.wheelCount = 0;
        // 👆会被自动清除的信息，具有极强的实时性，不会持久记录，如需使用请确保每帧都访问
        long currentTime = System.currentTimeMillis();

        int x = Mouse.getEventX();
        int y = Display.getHeight() - Mouse.getEventY();
        int key = Mouse.getEventButton();
        int wheel = Mouse.getEventDWheel();
        mouseInfo.wheelCount = wheel;
        // 按键事件
        if (key != -1) {
            if (mouseInfo.clicked.containsKey(key)) { // 再次触发即释放
                mouseInfo.clicked.remove(key);
                mouseInfo.releasedKey = key; // 记录释放的按键
                if (mouseState == MouseState.HOLD) mouseState = MouseState.RELEASE;
                else mouseState = MouseState.CLICKED;
            }
            else { // map中没有该键则是第一次按下
                mouseInfo.clicked.put(key, currentTime);
                mouseState = MouseState.PRESS;
            }
        }
        // 纯移动事件
        else {
            // 更新坐标
            mouseInfo.prevPos = mouseInfo.currPos;
            mouseInfo.currPos = new Vector2i(x, y);
            mouseState = MouseState.MOVE;
            if (!mouseInfo.clicked.isEmpty()) { // 如果有持续按压的按键
                mouseState = MouseState.DRAG;
            }
        }
    }

    /**
     * 初始化时调用一次
     */
    @Override
    public void initGui() {
        super.initGui();
        if (!init) canvas = new GLCanvas();
        // 记录打开的时间
        startTime = System.currentTimeMillis();
        this.width = Display.getWidth(); this.height = Display.getHeight();
        // 向全局记录中添加
        addComponent();
        stack.add(this);
        isInit = true;
    }

    /**
     * 在此处添加各类组件, 组件定义后将会在 {@code drawScreen} 函数中自动使用draw来绘制自身
     * <br>
     * 使用 {@code components.add( UIComponent )} 来添加组件
     */
    public abstract void addComponent();

    /**
     * 界面关闭时调用（用于禁用键盘重复输入）
     */
    @Override
    public void onGuiClosed() {
        // 在此释放资源
        canvas.dispose();
        isInit = false;
        stack.remove(this);
        components.clear();
    }

    /**
     * 重置界面尺寸和游戏实例（等效于Container.validate()）
     * <p/>
     * 当界面尺寸改变后Base GUI会销毁自身的属性并重建
     *
     * @param mc    Minecraft实例
     * @param width  界面宽度
     * @param height 界面高度
     */
    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        if (isInit) onGuiClosed();
        this.mc = mc;
        this.fontRendererObj = mc.fontRenderer;
        this.width = Display.getWidth();
        this.height = Display.getHeight();
        initGui();
    }

    /**
     * 判断当前GUI界面是否在单人游戏时暂停游戏
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }


    // region 废弃

    // ===========================================================================
    // 👇============================= 废弃方法请勿使用 ===========================👇
    // ===========================================================================
    /**
     * 处理键盘按键事件（等效于KeyListener.keyTyped）
     *
     * @param typedChar 输入的字符
     * @param keyCode   按键代码
     */
    @Override
    @Deprecated
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
        }
    }

    /**
     * 处理鼠标点击事件
     *
     * @param mouseX      鼠标X坐标
     * @param mouseY      鼠标Y坐标
     * @param mouseButton 鼠标按钮（0-左键，1-右键）
     */
    @Override
    @Deprecated
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * 处理鼠标拖动事件
     *
     * @param mouseX             当前鼠标X坐标
     * @param mouseY             当前鼠标Y坐标
     * @param clickedMouseButton 被按下的鼠标按钮
     * @param timeSinceLastClick 距离上次点击的时间（毫秒）
     */
    @Override
    @Deprecated
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    /**
     * 处理鼠标移动或释放事件
     *
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param state  事件状态（-1表示移动，0/1表示按钮释放）
     */
    @Override
    @Deprecated
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    @Deprecated
    protected void renderToolTip(ItemStack itemIn, int x, int y) {
        super.renderToolTip(itemIn, x, y);
    }

    /**
     * 绘制居中文本
     *
     * @param fontRendererIn 字体渲染器
     * @param text           文本内容
     * @param x              基准X坐标
     * @param y              基准Y坐标
     * @param color          文本颜色
     */
    @Override
    @Deprecated
    public void drawCenteredString(FontRenderer fontRendererIn, String text, int x, int y, int color) {

    }

    /**
     * 绘制垂直渐变矩形
     *
     * @param left       左边界
     * @param top        上边界
     * @param right      右边界
     * @param bottom     下边界
     * @param startColor 起始颜色
     * @param endColor   结束颜色
     */
    @Override
    @Deprecated
    protected void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {

    }

    @Override
    @Deprecated
    protected void drawHorizontalLine(int startX, int endX, int y, int color) {

    }

    /**
     * 绘制文本
     *
     * @param fontRendererIn 字体渲染器
     * @param text           文本内容
     * @param x              基准X坐标
     * @param y              基准Y坐标
     * @param color          文本颜色
     */
    @Override
    @Deprecated
    public void drawString(FontRenderer fontRendererIn, String text, int x, int y, int color) {

    }

    /**
     * 绘制纹理矩形
     *
     * @param x         界面X坐标
     * @param y         界面Y坐标
     * @param textureX  纹理X坐标
     * @param textureY  纹理Y坐标
     * @param width     绘制宽度
     * @param height    绘制高度
     */
    @Override
    @Deprecated
    public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {

    }

    /**
     * 绘制带图标的纹理矩形
     *
     * @param x     界面X坐标
     * @param y     界面Y坐标
     * @param icon  材质图标对象
     * @param width 绘制宽度
     * @param height 绘制高度
     */
    @Override
    @Deprecated
    public void drawTexturedModelRectFromIcon(int x, int y, IIcon icon, int width, int height) {

    }

    /**
     * 绘制垂直分割线
     *
     * @param x       基准X坐标
     * @param startY 起始Y坐标
     * @param endY   结束Y坐标
     * @param color   线条颜色
     */
    @Override
    @Deprecated
    protected void drawVerticalLine(int x, int startY, int endY, int color) {

    }

    /**
     * 按钮点击事件处理
     *
     * @param button 被点击的按钮对象
     */
    @Override
    @Deprecated
    protected void actionPerformed(GuiButton button) {

    }

    /**
     * 确认对话框回调
     *
     * @param result 用户选择结果（确认/取消）
     * @param id     对话框ID标识
     */
    @Override
    @Deprecated
    public void confirmClicked(boolean result, int id) {

    }

    /**
     * 绘制界面背景（自1.2.2版本起参数i始终为0）
     *
     * @param tint 背景色调
     */
    @Override
    @Deprecated
    public void drawBackground(int tint) {

    }

    /**
     * 绘制创造模式物品栏标签的悬浮提示文本
     *
     * @param tabName 当前标签名称
     * @param mouseX  鼠标X坐标
     * @param mouseY  鼠标Y坐标
     */
    @Override
    @Deprecated
    protected void drawCreativeTabHoveringText(String tabName, int mouseX, int mouseY) {

    }

    /**
     * 绘制默认背景（纯色或渐变背景）
     */
    @Override
    @Deprecated
    public void drawDefaultBackground() {

    }

    @Override
    @Deprecated
    protected void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {

    }

    @Override
    public void drawWorldBackground(int tint) {
        super.drawWorldBackground(tint);
    }

    @Override
    @Deprecated
    protected void func_146283_a(List<String> textLines, int x, int y) {

    }
    // endregion 废弃


    public static class MouseInfo {
        /**记录持续按住的键 键: 按下的时间*/
        public Map<Integer, Long> clicked = new HashMap<>();
        public Vector2i prevPos = new Vector2i(Mouse.getEventX(), Display.getHeight() - Mouse.getEventY()); // 上一个坐标位置
        public Vector2i currPos = new Vector2i(Mouse.getEventX(), Display.getHeight() - Mouse.getEventY()); // 当前的坐标位置
        public int releasedKey = -1;
        public int wheelCount = 0;

        public boolean isKeyPressed(int key) {
            return clicked.containsKey(key);
        }
    }

    public static class KeyboardPressInfo {
        public Map<Integer, Long> pressed = new HashMap<>();
        public int releasedKey = -1;
        public Integer whoReleased() {
            for (Map.Entry<Integer, Long> entry : new ArrayList<>(pressed.entrySet())) {
                int key = entry.getKey();
                if (Keyboard.isKeyDown(key)) continue;
                else {
                    pressed.remove(key);
                    return key;
                }
            }
            return null;
        }
    }
}
