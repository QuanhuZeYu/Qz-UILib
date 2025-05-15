package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.config.Config;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class ConstField {
    public static final String MODID = "qz_uilib";
    public static final String MOD_NAME = "Qz-方块信息显示";
    public static final String VERSION = Tags.VERSION;

    public static final String CLIENT_SIDE = "club.heiqi.qz_uilib.ClientProxy";
    public static final String SERVER_SIDE = "club.heiqi.qz_uilib.CommonProxy";

    public static final File MC_DIR = new File(System.getProperty("user.dir"));

    public static void debugLog(Logger logger, String text, Object... args) {
        if (Config.debugLOG) {
            logger.info(text, args);
        }
    }
}
