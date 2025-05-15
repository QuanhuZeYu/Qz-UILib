package club.heiqi.qz_uilib.config;

import club.heiqi.qz_uilib.ConstField;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static String configPath;
    public static Configuration configuration;

    public static String CLIENT = "CLIENT";
    public static boolean debugLOG = false;
    public static Property pDebugLOG;

    public static List<Property> propertyList = new ArrayList<>();

    public enum ConfigField {
        DEBUG_LOG("debugLOG"),
        ;
        public final String field;
        ConfigField(String field) {
            this.field = field;
        }
    }

    public static void init(File configFile) {
        if (configuration == null) {
            configPath = configFile.getAbsolutePath();
            configuration = new Configuration(configFile);
        }
        pDebugLOG = configuration.get(CLIENT,"debugLOG",false,"控制debug输出开关");
        debugLOG = pDebugLOG.getBoolean();
        propertyList.add(pDebugLOG);
    }

    public static void setConfig(String field, Object value) {
        try {
            Field f = Config.class.getField(field);
            f.setAccessible(true);
            f.set(null,value);
            for (Property p : propertyList) {
                if (p.getName().equals(field)) {
                    if (p.isBooleanValue()) {
                        p.set(Boolean.parseBoolean(value.toString()));
                        configuration.save();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void autoSave(ConfigChangedEvent event) {
        if (event.modID.toLowerCase().equals(ConstField.MODID.toLowerCase())) {
            configuration.save();
        }
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
}
