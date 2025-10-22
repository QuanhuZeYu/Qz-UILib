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

    public static int lerpMode = 0, aaMode = 2;

    public static boolean useDebug = false;
    public static double wheelCount = 64;
    /**fontSystem中的配置 - 加载界面依赖默认值！*/
    public static double colorGain = 1, spaceWidth = 4, characterSpacing = 0.1, shadowOffsetX = 0.5,
            shadowOffsetY = 0.5, lineSpacing = 0.1, renderOffset = 0, smoothRangeMin = 0, smoothRangeMax = 0.9, baseLineOffset = 0,
            aaStrength = 12;
    /**fontSystem中的配置*/
    public static String[] fontSort = {};
    public static boolean replaceOrigin, debugFontRender, testRender, centered, customInvCountFont;

    /**字体大小相关配置*/
    public static String FONT_SIZE_SETTING = "fontSizeSetting";
    public static double awtCharSize = 64, charSize = 9, stackFontSize = 8, neiFontSize = 5, aeFontSize = 5,
            fontScale = 0.8;

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

        lerpMode = config.get(FONT_SYSTEM, "lerpMode", 0, "插值模式;0-GL_NEAREST_MIPMAP_NEAREST;1-GL_LINEAR_MIPMAP_NEAREST;2-GL_NEAREST_MIPMAP_LINEAR;3-GL_LINEAR_MIPMAP_LINEAR", 0, 3).getInt();
        aaMode = config.get(FONT_SYSTEM, "aaMode", 2, "AA模式;1:4x; 2:8x", 1, 2).getInt();

        colorGain = config.get(FONT_SYSTEM, "colorGain", 10.0, "亮度增强，低于1就是变暗", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        spaceWidth = config.get(FONT_SYSTEM, "spaceWidth", 4.0, "空格宽度", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        characterSpacing = config.get(FONT_SYSTEM, "characterSpacing", 0.1, "字间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetX = config.get(FONT_SYSTEM, "shadowOffsetX", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetY = config.get(FONT_SYSTEM, "shadowOffsetY", 0.5, "投影偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        lineSpacing = config.get(FONT_SYSTEM, "lineSpacing", 0.1, "行间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        renderOffset = config.get(FONT_SYSTEM, "renderOffset", 0, "字符渲染向前偏移量(用于解决z-fight问题)", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        smoothRangeMin = config.get(FONT_SYSTEM, "smoothRangeMin", 0.0, "平滑透明度最小阈值，低于此值的透明像素会被设置为0（用于锐化边缘可能造成锯齿感）", 0, Double.MAX_VALUE).getDouble();
        smoothRangeMax = config.get(FONT_SYSTEM, "smoothRangeMax", 0.9, "平滑透明度最大阈值，高于此值的透明像素会被设置为1（用于增加亮度提升对比清晰化）", 0, Double.MAX_VALUE).getDouble();
        baseLineOffset = config.get(FONT_SYSTEM, "baseLineOffset", 0.0, "基线偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        aaStrength = config.get(FONT_SYSTEM, "aaStrength", 12.0, "AA抗锯齿强度", 1.0, Double.MAX_VALUE).getDouble();

        awtCharSize = config.get(FONT_SIZE_SETTING, "awtCharSize", 64, "生成字符分辨率", 8, Double.MAX_VALUE).getDouble();
        charSize = config.get(FONT_SIZE_SETTING, "charSize", 9, "游戏内字体字号", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        stackFontSize = config.get(FONT_SIZE_SETTING, "stackFontSize", 8, "原版物品数量显示大小", 4, Double.MAX_VALUE).getDouble();
        neiFontSize = config.get(FONT_SIZE_SETTING, "neiFontSize", 8, "NEI物品数量显示大小", 4, Double.MAX_VALUE).getDouble();
        aeFontSize = config.get(FONT_SIZE_SETTING, "aeFontSize", 8, "ae物品数量显示大小", 4, Double.MAX_VALUE).getDouble();
        fontScale = config.get(FONT_SIZE_SETTING, "fontScale", 0.8, "字符生成阶段控制字体大小乘值--用于解决基线偏移问题", 0, 1).getDouble();

        fontSort = config.get(FONT_SYSTEM, "fontSort", new String[]{}, "字符排序").getStringList();
        replaceOrigin = config.get(FONT_SYSTEM, "replaceOrigin", false, "是否替换原版字体渲染器").getBoolean();
        debugFontRender = config.get(FONT_SYSTEM, "debugFontRender", false, "字体渲染器DEBUG模式").getBoolean();
        testRender = config.get(FONT_SYSTEM, "testRender", false, "测试着色器").getBoolean();
        centered = config.get(FONT_SYSTEM, "centered", false, "物品数量居中对齐").getBoolean();
        customInvCountFont = config.get(FONT_SYSTEM, "customInvCountFont", false, "修改AE、NEI、原版物品数量字体").getBoolean();

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