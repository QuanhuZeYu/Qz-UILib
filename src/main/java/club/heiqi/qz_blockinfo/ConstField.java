package club.heiqi.qz_blockinfo;

import java.io.File;

public class ConstField {
    public static final String MODID = "qz_blockinfo";
    public static final String MOD_NAME = "Qz-方块信息显示";
    public static final String VERSION = Tags.VERSION;

    public static final String CLIENT_SIDE = "club.heiqi.qz_blockinfo.ClientProxy";
    public static final String SERVER_SIDE = "club.heiqi.qz_blockinfo.CommonProxy";

    public static final File MC_DIR = new File(System.getProperty("user.dir"));
}
