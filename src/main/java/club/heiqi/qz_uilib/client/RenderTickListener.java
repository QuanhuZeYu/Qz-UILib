package club.heiqi.qz_uilib.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.LinkedBlockingQueue;

public class RenderTickListener {
    public static Logger LOG = LogManager.getLogger();
    public static LinkedBlockingQueue<Runnable> errorCleaners = new LinkedBlockingQueue<>();
    public static LinkedBlockingQueue<Runnable> someTasks = new LinkedBlockingQueue<>();

    @SubscribeEvent
    public void onMainThread(TickEvent.RenderTickEvent event) {
        if (!errorCleaners.isEmpty()) {
            LOG.error("!!!  正在清理错误的未释放内存  !!!");
            Runnable runnable = errorCleaners.poll();
            if (runnable != null) {
                runnable.run();
            }
        }
        if (!someTasks.isEmpty()) {
            Runnable runnable = someTasks.poll();
            if (runnable != null) {
                runnable.run();
            }
        }
    }

    public void register() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
