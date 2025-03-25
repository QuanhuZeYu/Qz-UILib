package club.heiqi.qz_blockinfo.gameMenu;

import club.heiqi.qz_blockinfo.ClientProxy;
import club.heiqi.skija.GLCanvas;
import club.heiqi.skija.font.FontLoader;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GameMenu {
    public static Logger LOG = LogManager.getLogger();

    @SubscribeEvent
    public void inGameMenu(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ClientProxy.canvas == null) ClientProxy.canvas = new GLCanvas();
        GLCanvas canvas = ClientProxy.canvas;
        Paint rrectPaint = new Paint().setColor(Color.makeARGB(255,10,10,10));
        Paint strPaint = new Paint().setColor(Color.makeARGB(255,255,255,255));
        canvas.render(canvas1 -> {
            canvas1.drawRRect(RRect.makeXYWH(0,0,100,100,10), rrectPaint);
            canvas1.drawString("Qz-UILib测试", 0,50, FontLoader.fonts.get(0), strPaint);
        });
        rrectPaint.close(); strPaint.close();
    }

    public GameMenu register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
