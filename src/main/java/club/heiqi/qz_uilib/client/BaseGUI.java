package club.heiqi.qz_uilib.client;

import club.heiqi.qz_uilib.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;

public class BaseGUI extends GuiScreen {
    public static FrameBufferObject frameBuffer;
    public Logger LOG = LogManager.getLogger();
    public Widget root = new Widget();

    public BaseGUI() {
        if (frameBuffer == null) frameBuffer = new FrameBufferObject();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        frameBuffer.bind();
        // 清除fbo内容
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        root.draw();
        frameBuffer.unbind();

        frameBuffer.render(width, height);
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        this.mc = mc;
        this.fontRendererObj = mc.fontRenderer;
        this.width = width;
        this.height = height;
        if (Display.getWidth() != frameBuffer.textureWidth || Display.getHeight() != frameBuffer.textureHeight) {
            frameBuffer.resize(Display.getWidth(), Display.getHeight());
        }
        LOG.info("w:{} h:{}", width, height);
    }

    @Override
    public void initGui() {
        super.initGui(); // 空实现
    }

    @Override
    protected void actionPerformed(GuiButton button) {

    }


}
