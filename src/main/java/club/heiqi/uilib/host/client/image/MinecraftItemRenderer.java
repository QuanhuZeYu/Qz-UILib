package club.heiqi.uilib.host.client.image;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.gl.GlImageResources;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 每次 replay 直接原版 RenderItem，保留 Forge inventory hook/动态 glint。覆盖层不调用原版 FontRenderer overlay。 */
public final class MinecraftItemRenderer implements GlImageResources.HostItems {
    private final ClientFontServices fonts;
    private final RenderItem renderer = new RenderItem();

    public MinecraftItemRenderer(ClientFontServices fonts) {
        this.fonts = java.util.Objects.requireNonNull(fonts, "fonts");
    }

    @Override
    public boolean supports(ImageContent content) {
        return content instanceof MinecraftItemContent;
    }

    @Override
    public void render(ImagePlacement image, LayoutBox clip, int width, int height) {
        if (!supports(image.content)) throw new UnsupportedOperationException("不是 Minecraft 物品快照");
        fonts.withDelegatedConsumer(
                () -> {
                    float z = renderer.zLevel;
                    boolean color = renderer.renderWithColor;
                    try (ItemStateScope scope = new ItemStateScope(fonts.gl())) {
                        scope.place(image, clip, width, height);
                        RenderHelper.enableGUIStandardItemLighting();
                        Minecraft mc = Minecraft.getMinecraft();
                        renderer.renderItemAndEffectIntoGUI(
                                mc.fontRenderer,
                                mc.getTextureManager(),
                                ((MinecraftItemContent) image.content).copy(),
                                0,
                                0);
                    } finally {
                        renderer.zLevel = z;
                        renderer.renderWithColor = color;
                    }
                });
    }
}
