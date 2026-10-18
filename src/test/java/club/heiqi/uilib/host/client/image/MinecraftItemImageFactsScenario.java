package club.heiqi.uilib.host.client.image;

import club.heiqi.uilib.ui.scene.integration.ItemImageFactsScenario;

/** 共同物品事实场景通过真实Minecraft格式工厂验证来源转换及palette快照。 */
public final class MinecraftItemImageFactsScenario {
    private MinecraftItemImageFactsScenario() {}

    public static void main(String[] args) throws Exception {
        new ItemImageFactsScenario(MinecraftTooltipContent::fromMinecraft).run(args);
    }
}
