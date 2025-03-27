package club.heiqi.qz_uilib.gameMenu;

import club.heiqi.qz_uilib.skija.GLCanvas;
import club.heiqi.qz_uilib.skija.font.FontLoader;
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
    public static GLCanvas canvas;
    public long timer = -1;

    @SubscribeEvent
    public void onClientTick(TickEvent.RenderTickEvent event) {
        if (timer == -1) {timer = System.currentTimeMillis();}
        if (System.currentTimeMillis() - timer < 5_000) return;
        // 👆延迟启动
        if (canvas == null) canvas = new GLCanvas();
        if (event.phase != TickEvent.Phase.END) return;
        canvas.render(canvas1 -> {
            RRect rect = RRect.makeXYWH(0,0,100,100,10);
            Paint rectP = new Paint().setColor(Color.makeARGB(255,50,50,50));
            Paint strP = new Paint().setColor(Color.makeARGB(255,180,230,255));
            canvas1.drawRRect(rect, rectP);
            canvas1.drawString("Qz-UILib测试",0,50-4, FontLoader.getDefaultFont(),strP);
            rectP.close(); strP.close();
        });
    }

    public GameMenu register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
