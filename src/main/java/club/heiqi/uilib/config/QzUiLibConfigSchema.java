package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * Qz-UILib 配置页与配置同步共享的公共 schema。
 */
public final class QzUiLibConfigSchema {

    private QzUiLibConfigSchema() {}

    /**
     * 页面标题。
     *
     * @return 标题
     */
    public static String title() {
        return MyMod.MOD_NAME + " 配置";
    }

    /**
     * 页面副标题。
     *
     * @return 副标题
     */
    public static String subtitle() {
        return "Forge In-Game Config Replacement";
    }

    /**
     * 页面说明。
     *
     * @return 说明
     */
    public static String description() {
        return "使用 Qz UILib 的 HTML-like 文档页面替代默认 Forge 配置页，并作为可复用模板开放给其他开发者。";
    }

    /**
     * 共享分类定义。
     *
     * @return 分类列表
     */
    public static List<ConfigSyncCategorySpec> categories() {
        List<ConfigSyncCategorySpec> categories = new ArrayList<ConfigSyncCategorySpec>();
        categories.add(new ConfigSyncCategorySpec(Config.GENERAL, "General",
                "基础运行开关、界面调试显示与通用行为配置。"));
        categories.add(new ConfigSyncCategorySpec(FontConfig.CATEGORY, "Font System",
                "字体渲染运行时、排序和 drawString 上传节流相关配置。")
                        .addAlias("fontsystem"));
        categories.add(new ConfigSyncCategorySpec(FontConfig.FONT_SIZE_CATEGORY, "Font Size",
                "默认字号、生成分辨率与缩放系数配置。"));
        return Collections.unmodifiableList(categories);
    }
}
