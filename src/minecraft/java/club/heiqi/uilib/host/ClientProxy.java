package club.heiqi.uilib.host;

/** 生产client安装正式字体和图像owner；测试入口由独立人工测试Mod安装。 */
public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        club.heiqi.uilib.host.client.font.VanillaFontOptions.load(
                event.getSuggestedConfigurationFile());
    }

    @Override
    public void init() {
        club.heiqi.uilib.host.client.font.MinecraftFontServices.installShared();
        club.heiqi.uilib.host.client.font.MinecraftVanillaFont.install();
        club.heiqi.uilib.host.client.image.MinecraftImageServices.installShared();
    }
}
