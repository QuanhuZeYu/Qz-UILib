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

    public static int sampleRadius, msaa = 4;

    public static boolean useDebug = false;
    public static double wheelCount = 64;
    /**fontSystem中的配置 - 加载界面依赖默认值！*/
    public static double sigma = 1, blurRadius = 2, smoothRangeMin = 0, smoothRangeMax = 0.5, colorGain = 1,
            awtCharSize = 64, charSize = 8, spaceWidth = 4, characterSpacing = 0.1, shadowOffsetX = 0.5, shadowOffsetY = 0.5,
            lineSpacing = 0.1, renderOffset = 0, alphaGain = 0.3, shrink = 0.1, intensityDivisor = 5.0;
    /**fontSystem中的配置*/
    public static String[] fontSort = {};
    public static boolean replaceOrigin, debugFontRender, testRender;

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

        sampleRadius = config.get(FONT_SYSTEM, "sampleRadius", 0, "采样半径", -Integer.MAX_VALUE, Integer.MAX_VALUE).getInt();
        msaa = config.get(FONT_SYSTEM, "msaa", 4, "多重采样等级", 1, Integer.MAX_VALUE).getInt();

        sigma = config.get(FONT_SYSTEM, "sigma", 1, "高斯标准差", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        blurRadius = config.get(FONT_SYSTEM, "blurRadius", 2, "蒙版模糊半径", 0, Double.MAX_VALUE).getDouble();
        smoothRangeMin = config.get(FONT_SYSTEM, "smoothRangeMin", 0.0, "蒙版平滑最低阈值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        smoothRangeMax = config.get(FONT_SYSTEM, "smoothRangeMax", 0.5, "蒙版平滑最高阈值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        colorGain = config.get(FONT_SYSTEM, "colorGain", 1, "亮度增强，低于1就是变暗", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        awtCharSize = config.get(FONT_SYSTEM, "awtCharSize", 64, "生成字符分辨率", 8, Double.MAX_VALUE).getDouble();
        charSize = config.get(FONT_SYSTEM, "charSize", 8, "游戏内字体字号", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        spaceWidth = config.get(FONT_SYSTEM, "spaceWidth", 4, "空格宽度", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        characterSpacing = config.get(FONT_SYSTEM, "characterSpacing", 0.1, "字间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetX = config.get(FONT_SYSTEM, "shadowOffsetX", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetY = config.get(FONT_SYSTEM, "shadowOffsetY", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        lineSpacing = config.get(FONT_SYSTEM, "lineSpacing", 0.1, "行间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        renderOffset = config.get(FONT_SYSTEM, "renderOffset", 0, "字符渲染向前偏移量(用于解决z-fight问题)", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        alphaGain = config.get(FONT_SYSTEM, "alphaGain", 0.3, "字符透明度增益", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shrink = config.get(FONT_SYSTEM, "shrink", 0.1, "蒙版层采样收缩-用于解决字符方形边框上有杂色的情况", 0, 1).getDouble();
        intensityDivisor = config.get(FONT_SYSTEM, "intensityDivisor", 0.1, "蒙版膨胀效果调整", 1, Double.MAX_VALUE).getDouble();

        fontSort = config.get(FONT_SYSTEM, "fontSort", new String[]{}, "字符排序").getStringList();
        replaceOrigin = config.get(FONT_SYSTEM, "replaceOrigin", false, "是否替换原版字体渲染器").getBoolean();
        debugFontRender = config.get(FONT_SYSTEM, "debugFontRender", false, "字体渲染器DEBUG模式").getBoolean();
        testRender = config.get(FONT_SYSTEM, "testRender", false, "测试着色器").getBoolean();

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