package club.heiqi.qz_uilib.mixins.early;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Gui.class)
public class QzGui {
    @Unique
    public FontRenderer fontrender = Minecraft.getMinecraft().fontRenderer;
}
