package club.heiqi.qz_blockinfo.gameMenu;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GameMenu {
    public static Logger LOG = LogManager.getLogger();

    public GameMenu register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        return this;
    }
}
