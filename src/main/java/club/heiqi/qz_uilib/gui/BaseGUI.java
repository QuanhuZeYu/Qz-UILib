package club.heiqi.qz_uilib.gui;

import club.heiqi.qz_uilib.client.FBO;
import club.heiqi.qz_uilib.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

public class BaseGUI extends GuiScreen {
    public static FBO frameBuffer;
    public static FBO getFrameBuffer() {
        if (frameBuffer == null) {
            frameBuffer = new FBO(Display.getWidth(), Display.getHeight()).initByDefaultColorAndDepth();
        }
        else {
            if (frameBuffer.width != Display.getWidth() || frameBuffer.height != Display.getHeight()) {
                frameBuffer.resize(Display.getWidth(), Display.getHeight());
            }
        }
        return frameBuffer;
    }
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
        IntBuffer intBuffer = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, intBuffer);
        // 初始化各种矩阵
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0,Display.getWidth(),Display.getHeight(),0,-30000,30000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        // 设置视口
        GL11.glViewport(0,0,Display.getWidth(),Display.getHeight());

        getFrameBuffer().bindAndRecordPreviousFBO();
        // 清除fbo内容
        GL11.glClearColor(0,0,0,0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawBackground();
        root.applyLayout();
        root.draw();
        GL11.glPopAttrib();

        getFrameBuffer().unbindAndRestorePreviousFBO();

        // 恢复矩阵
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        // 恢复视口
        GL11.glViewport(intBuffer.get(0), intBuffer.get(1), intBuffer.get(2), intBuffer.get(3));


        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, getFrameBuffer().colorTextureID);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); // 标准Alpha混合
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1,1,1,1);
        getFrameBuffer().drawDisplayWindow();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void drawBackground() {
        GL11.glColor4f(0.4f,0.4f,0.4f,0.8f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0,0,0);
        GL11.glVertex3f(getFrameBuffer().width,0,0);
        GL11.glVertex3f(getFrameBuffer().width,getFrameBuffer().height,0);
        GL11.glVertex3f(0,getFrameBuffer().height,0);
        GL11.glEnd();
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        this.mc = mc;
        this.fontRendererObj = mc.fontRenderer;
        this.width = width;
        this.height = height;
        root.onResize(Display.getWidth(),Display.getHeight());
        // LOG.info("尺寸发生改变 w:{} h:{}", this.width, this.height);
    }

    @Override
    public void initGui() {
        getFrameBuffer();
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
