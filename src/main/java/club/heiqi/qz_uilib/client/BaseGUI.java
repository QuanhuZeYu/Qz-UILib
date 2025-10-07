package club.heiqi.qz_uilib.client;

import club.heiqi.qz_uilib.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

/**
 * 该类已移动至 {@link club.heiqi.qz_uilib.gui.BaseGUI} 下
 */
@Deprecated
public class BaseGUI extends GuiScreen {
    public static FrameBufferObject frameBuffer;
    public Logger LOG = LogManager.getLogger();
    /**根组件*/
    public Widget root = null;
    public int mouseCount = 0;

    public BaseGUI() {
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Widget.updateTween();
        frameBuffer.bind();
        // 清除fbo内容
        GL11.glClearColor(0,0,0,0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        // GL11.glDisable(GL11.GL_FOG);
        // GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        // GL11.glDisable(GL11.GL_STENCIL_TEST);
        // GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawBackground();
        root.applyLayout();
        root.draw();
        GL11.glPopAttrib();

        frameBuffer.unbind();
        frameBuffer.render(mcGUIWidth, mcGUIHeight);
    }

    public void drawBackground() {
        GL11.glColor4f(0.4f,0.4f,0.4f,0.8f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0,0,0);
        GL11.glVertex3f(frameBuffer.textureWidth,0,0);
        GL11.glVertex3f(frameBuffer.textureWidth,frameBuffer.textureHeight,0);
        GL11.glVertex3f(0,frameBuffer.textureHeight,0);
        GL11.glEnd();
    }

    private int mcGUIWidth, mcGUIHeight;
    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        this.mc = mc;
        this.fontRendererObj = mc.fontRenderer;
        this.mcGUIWidth = width;
        this.mcGUIHeight = height;
        this.width = width;
        this.height = height;
        root.onResize(Display.getWidth(),Display.getHeight());
        // LOG.info("尺寸发生改变 w:{} h:{}", this.width, this.height);
    }

    @Override
    public void initGui() {
        if (frameBuffer == null) frameBuffer = new FrameBufferObject();
        root = new Widget().setSize(Display.getWidth(), Display.getHeight());
        mouseCount = Mouse.getButtonCount();
    }

    @Override
    protected void actionPerformed(GuiButton button) {}


    // ===== 主要的鼠标事件 =====
    /**按下事件*/
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // mouseButton: 0 左键 1 中键 2 右键 3~? 侧键等
        if (root.isMouseInBounds(mouseX, mouseY)) {
            root.onPressPrivate(mouseX, mouseY, mouseButton);
        }
        root.onPressNotInBoundsPrivate(mouseX,mouseY,mouseButton);

    }

    /**拖拽事件*/
    protected void mouseClickMove(int mouseX, int mouseY, Set<Integer> clicked) {
        // 拖动行为
        if (root.isMouseInBounds(mouseX, mouseY)) {
            root.onDragPrivate(mouseX, mouseY, clicked);
        }
    }

    /**鼠标释放事件*/
    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (root.isMouseInBounds(mouseX, mouseY)) {
            // LOG.info("鼠标在根组件上抬起按钮 x:{} y:{} state:{}", mouseX, mouseY, state);
            root.onReleasePrivate(mouseX, mouseY, state);
        }
    }

    /**纯移动事件*/
    protected void mouseMoving(int mouseX, int mouseY, Set<Integer> clicked, Set<Integer> hold) {
        root.onMouseMovingPrivate(mouseX,mouseY, clicked, hold);
        if (root.isMouseInBounds(mouseX, mouseY)) {
            root.onHoverPrivate(mouseX,mouseY);
        }
        else {
            root.onLeavePrivate(mouseX,mouseY);
        }
    }

    public void mouseWheeling(int x, int y, int dWheel) {
        if (root.isMouseInBounds(x,y)) {
            root.onWheelPrivate(x,y,dWheel);
        }
    }

    private Set<Integer> mouseButtonClicked = new HashSet<>();
    private Set<Integer> mouseButtonHold = new HashSet<>();
    private int cacheX = Mouse.getX(), cacheY = Display.getHeight() - Mouse.getY();
    @Override
    public void handleMouseInput() {
        int mouseX = Mouse.getX();
        int mouseY = Display.getHeight() - Mouse.getY();
        int dx = cacheX - mouseX;
        int dy = cacheY - mouseY;
        int dWheel = Mouse.getDWheel();

        // 0-左键 1-右键 2-中键 3、4、5......
        // 检查按键是否按下
        for (int i = 0; i < mouseCount; i++) {
            boolean isDown = Mouse.isButtonDown(i);
            if (isDown) {
                // 初次按下
                if (!mouseButtonClicked.contains(i)) {
                    mouseButtonClicked.add(i);
                    this.mouseClicked(mouseX,mouseY,i);
                } else {
                    mouseButtonHold.add(i);
                    this.mouseClickMove(mouseX, mouseY, mouseButtonHold);
                }
            }
            else {
                // 松开
                if (mouseButtonClicked.contains(i)) {
                    mouseButtonClicked.remove(i);
                    mouseButtonHold.remove(i);
                    this.mouseMovedOrUp(mouseX,mouseY,i);
                }
            }
        }
        // 检查鼠标是否移动
        if (dx != 0 || dy != 0) {
            this.mouseMoving(mouseX, mouseY, mouseButtonClicked, mouseButtonHold);
            if (!mouseButtonHold.isEmpty()) {
                this.mouseClickMove(mouseX, mouseY, mouseButtonHold);
            }
        }
        if (dWheel != 0) {
            this.mouseWheeling(mouseX,mouseY,dWheel);
        }

        cacheX = mouseX;
        cacheY = mouseY;
    }

    @Override
    public void handleKeyboardInput() {
        super.handleKeyboardInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_F5) {
            initGui();
            return;
        }
        super.keyTyped(typedChar, keyCode);
        root.onTypePrivate(typedChar, keyCode);
    }
}
