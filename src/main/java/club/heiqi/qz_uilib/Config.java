package club.heiqi.qz_uilib;

import java.io.File;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

public class Config {
    public static String configPath;
    public static Configuration config;

    public static String GENERAL = Configuration.CATEGORY_GENERAL;
    public static String FONT_SYSTEM = "fontSystem";

    public static int sampleRadius;

    public static boolean useDebug = false;
    public static double wheelCount = 64;
    /**fontSystem中的配置 - 加载界面依赖默认值！*/
    public static double sigma = 1, blurRadius = 1, smoothRangeMin = 0, smoothRangeMax = 1, colorGain = 0,
            awtCharSize = 64, charSize = 8, spaceWidth = 4, characterSpacing = 0.1, shadowOffsetX = 0.5, shadowOffsetY = 0.5,
            lineSpacing = 0.1, renderOffset = 0, alphaGain = 0;
    /**fontSystem中的配置*/
    public static String[] fontSort = {};
    public static boolean replaceOrigin, debugFontRender;

    public void init(File configFile) {
        if (config == null) {
            configPath = configFile.getAbsolutePath();
            config = new Configuration(configFile);
        }
        load();
    }

    public void load() {
        useDebug = config.getBoolean("useDebug", GENERAL, false, "GUI Debug Mode");
        wheelCount = config.get(GENERAL, "wheelCount", 64, "控制滚轮一次移动的距离", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();

        sigma = config.get(FONT_SYSTEM, "sigma", 1, "高斯标准差", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        blurRadius = config.get(FONT_SYSTEM, "blurRadius", 1, "采样模糊程度-当采样半径为0时不生效", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        sampleRadius = config.get(FONT_SYSTEM, "sampleRadius", 0, "采样半径", -Integer.MAX_VALUE, Integer.MAX_VALUE).getInt();
        smoothRangeMin = config.get(FONT_SYSTEM, "smoothRangeMin", 0, "平滑透明度最低阈值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        smoothRangeMax = config.get(FONT_SYSTEM, "smoothRangeMax", 1, "平滑透明度最高阈值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        colorGain = config.get(FONT_SYSTEM, "colorGain", 0, "亮度增强直接加值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        awtCharSize = config.get(FONT_SYSTEM, "awtCharSize", 64, "生成字符分辨率", 8, Double.MAX_VALUE).getDouble();
        charSize = config.get(FONT_SYSTEM, "charSize", 8, "游戏内字体字号", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        spaceWidth = config.get(FONT_SYSTEM, "spaceWidth", 4, "空格宽度", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        characterSpacing = config.get(FONT_SYSTEM, "characterSpacing", 0.1, "字间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetX = config.get(FONT_SYSTEM, "shadowOffsetX", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetY = config.get(FONT_SYSTEM, "shadowOffsetY", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        lineSpacing = config.get(FONT_SYSTEM, "lineSpacing", 0.1, "行间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        renderOffset = config.get(FONT_SYSTEM, "renderOffset", 0, "字符渲染向前偏移量(用于解决z-fight问题)", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        alphaGain = config.get(FONT_SYSTEM, "alphaGain", 0, "字符透明度增益", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();

        fontSort = config.get(FONT_SYSTEM, "fontSort", new String[]{}, "字符排序").getStringList();
        replaceOrigin = config.get(FONT_SYSTEM, "replaceOrigin", false, "是否替换原版字体渲染器").getBoolean();
        debugFontRender = config.get(FONT_SYSTEM, "debugFontRender", false, "字体渲染器DEBUG模式").getBoolean();

        if (config.hasChanged()) {
            config.save();
        }
    }

    @SubscribeEvent
    public void onConfigChangeEvent(ConfigChangedEvent event) {
        if (!event.modID.equalsIgnoreCase(MyMod.MODID)) return;
        load();
    }

    public void registrar() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
}