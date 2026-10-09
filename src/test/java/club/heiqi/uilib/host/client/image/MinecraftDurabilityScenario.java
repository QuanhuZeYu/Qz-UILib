package club.heiqi.uilib.host.client.image;

import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import club.heiqi.uilib.testing.TestApplication;

/** 无游戏启动：按原版注册表达式构造Item/钻石剑并执行Forge patched的Item/ItemStack耐久方法，复现实机钻石0/0。 */
public final class MinecraftDurabilityScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("原版钻石无耐久条比例NaN与工具有效比例",()->{
            ItemStack diamond=new ItemStack(new Item().setUnlocalizedName("diamond"),1,0);
            require(!diamond.getItem().showDurabilityBar(diamond),"钻石不应显示耐久条");
            require(Double.isNaN(diamond.getItem().getDurabilityForDisplay(diamond)),"原版钻石0/0未命中");
            ItemStack tool=new ItemStack(new ItemSword(Item.ToolMaterial.EMERALD),1,0);
            require(!tool.getItem().showDurabilityBar(tool),"未损工具不显示条");
            tool.setItemDamage(900);
            require(tool.getItem().showDurabilityBar(tool),"损伤工具应显示条");
            double damage=tool.getItem().getDurabilityForDisplay(tool);
            require(Double.isFinite(damage)&&damage==900d/tool.getMaxDamage(),"真实工具比例偏离原版");
            System.out.println("ACTUAL_FORGE_ITEM diamondMax="+diamond.getMaxDamage()+" diamondRatio="+diamond.getItem().getDurabilityForDisplay(diamond)+" toolMax="+tool.getMaxDamage()+" toolRatio="+damage);
        });
    }
    public static void main(String[] args) throws Exception { new MinecraftDurabilityScenario().run(); }
}
