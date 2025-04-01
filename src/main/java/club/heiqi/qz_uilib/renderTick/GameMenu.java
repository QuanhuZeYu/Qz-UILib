package club.heiqi.qz_uilib.renderTick;

import club.heiqi.qz_uilib.skija.GLCanvas;
import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

public class GameMenu {
    public static Logger LOG = LogManager.getLogger();
    public static GLCanvas canvas;
    public long timer = -1;

    @SubscribeEvent
    public void onClientTick(TickEvent.RenderTickEvent event) {
        if (timer == -1) {timer = System.currentTimeMillis();}
        if (System.currentTimeMillis() - timer < 1_000) return;
        // 👆延迟启动
        if (canvas == null) canvas = new GLCanvas();
        if (event.phase != TickEvent.Phase.END) return;
        canvas.render(canvas1 -> {
            String label = "Qz-UILib测试版";
            Paint strP = new Paint().setColor(Color.makeARGB(255,255,255,255)).setAntiAlias(true); // 抗锯齿
            Font font = new Font(FontLoader.getDefaultFont().getTypeface()).setSize(0.0097f* Display.getHeight());
            Vector2f strPos = StringAlignUtils.textTLToTarget(label,font,new Vector2f(0,0));
            canvas1.drawString(label,strPos.x,strPos.y, font,strP);
            strP.close();
        });
    }

    public GameMenu register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
