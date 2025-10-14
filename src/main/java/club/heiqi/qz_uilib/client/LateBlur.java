package club.heiqi.qz_uilib.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 延迟执行模糊效果
 */
public class LateBlur {

    public static LinkedBlockingQueue<Runnable> lateBlurTasks = new LinkedBlockingQueue<>();
    @SubscribeEvent
    public void lateBlur(TickEvent.RenderTickEvent event) {
        if (!lateBlurTasks.isEmpty()) {
            Runnable runnable = lateBlurTasks.poll();
            runnable.run();
        }
    }

    public void register() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
