package club.heiqi.qz_uilib;

import aurelienribon.tweenengine.Tween;
import club.heiqi.qz_uilib.renderTick.GameMenu;
import club.heiqi.qz_uilib.renderTick.RenderListener;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import club.heiqi.qz_uilib.skija.shader.GaussianBlur;
import club.heiqi.qz_uilib.test.OpenTestGUI;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientProxy extends CommonProxy {
    public static Logger LOG = LogManager.getLogger();
    /*public GameMenu gameMenu;*/

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        /*gameMenu = new GameMenu().register();*/
        new RenderListener().register();
        new OpenTestGUI().register();
        new GaussianBlur();
        FontLoader.load();
        Tween.setCombinedAttributesLimit(4);
        LOG.info("Qz-UILib初始化完成");
    }
}
