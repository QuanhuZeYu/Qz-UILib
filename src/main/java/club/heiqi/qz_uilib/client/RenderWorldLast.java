package club.heiqi.qz_uilib.client;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.shader.Bluer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

public class RenderWorldLast {
    public Bluer bluer;

    @SubscribeEvent
    public void hookRenderWorldLast(RenderWorldLastEvent event) {
        if (Config.testRender) {
            if (bluer == null) {
                bluer = new Bluer(Display.getWidth(), Display.getHeight());
            }
            else {
                if (bluer.vertical.width != Display.getWidth() || bluer.vertical.height != Display.getHeight()) {
                    bluer.resize(Display.getWidth(), Display.getHeight());
                }
            }
            int previousFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            FBO fbo = bluer.blurTexture(
                    Minecraft.getMinecraft().getFramebuffer().framebufferTexture,
                    Minecraft.getMinecraft().getFramebuffer().framebufferTextureWidth,
                    Minecraft.getMinecraft().getFramebuffer().framebufferTextureHeight,
                    5
            );
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.colorTextureID);

            // --- 视口设置 ---
            IntBuffer buffer = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
            int px = buffer.get(0);
            int py = buffer.get(1);
            int pw = buffer.get(2);
            int ph = buffer.get(3);
            GL11.glViewport(0,0,fbo.width,fbo.height);
            // --- 矩阵设置 ---
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0,1,1,0,-3000,3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            // --- 绘制 ---
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,1);  GL11.glVertex3f(0,0,0);
            GL11.glTexCoord2f(0,0);  GL11.glVertex3f(0,1,0);
            GL11.glTexCoord2f(1,0);  GL11.glVertex3f(1,1,0);
            GL11.glTexCoord2f(1,1);  GL11.glVertex3f(1,0,0);
            GL11.glEnd();
            // --- 绘制 ---
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, Minecraft.getMinecraft().getFramebuffer().framebufferObject);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFBO);
            // --- 还原视口 ---
            GL11.glViewport(px,py,pw,ph);
            // --- 还原矩阵 ---
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glOrtho(0,1,1,0,-3000,3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }
    }

    public void register() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
