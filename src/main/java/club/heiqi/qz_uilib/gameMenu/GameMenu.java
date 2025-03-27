package club.heiqi.qz_uilib.gameMenu;

import club.heiqi.qz_uilib.skija.GLCanvas;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GameMenu {
    public static Logger LOG = LogManager.getLogger();
    public static GLCanvas canvas;
    public long timer = -1;

    @SubscribeEvent
    public void onClientTick(TickEvent.RenderTickEvent event) {
        /*if (timer == -1) {timer = System.currentTimeMillis();}
        if (System.currentTimeMillis() - timer < 5_000) return;
        // 👆延迟启动
        if (canvas == null) canvas = new GLCanvas();
        if (event.phase != TickEvent.Phase.END) return;
        canvas.render(canvas1 -> {
            Paint strP = new Paint().setColor(Color.makeARGB(255,180,230,255)).setAntiAlias(true); // 抗锯齿
            canvas1.drawString("Qz-UILib测试",0,50-FONT_SIZE/2, FontLoader.getDefaultFont(),strP);
            strP.close();
        });*/
    }

    public GameMenu register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
