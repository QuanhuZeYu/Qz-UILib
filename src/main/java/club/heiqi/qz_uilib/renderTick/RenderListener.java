package club.heiqi.qz_uilib.renderTick;

import club.heiqi.qz_uilib.skija.FrameBuffer;
import club.heiqi.qz_uilib.skija.GLCanvas;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;

public class RenderListener {
    Logger LOG = LogManager.getLogger();

    public int cacheWidth = Display.getWidth(), cacheHeight = Display.getHeight();
    @SubscribeEvent
    public void onClientTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (Display.getWidth() != cacheWidth || Display.getHeight() != cacheHeight) {
                cacheWidth = Display.getWidth();
                cacheHeight = Display.getHeight();
                onResize(cacheWidth, cacheHeight);
            }
        }
    }


    public void onResize(int width, int height) {
        LOG.info("窗口正在缩放 {} {} | frame数量: {} glCanvas数量: {}", width, height,
                FrameBuffer.GLOBAL.size(), GLCanvas.GLOBALS.size());
        for (FrameBuffer frameBuffer : new ArrayList<>(FrameBuffer.GLOBAL)) {
            frameBuffer.resize(width, height);
        }
        for (GLCanvas glCanvas : new ArrayList<>(GLCanvas.GLOBALS)) {
            glCanvas.resize();
        }
    }


    public RenderListener register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
