package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.guiInject.GuiScreenListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11.*;

@Mixin(
    value = GuiScreen.class,
    priority = 5000
)
public class QzGuiScreen {
    @Unique
    private static Logger LOG = LogManager.getLogger();

//    @Inject(
//        method = "drawScreen",
//        at = @At("TAIL"),
//        remap = true
//    )
//    public void qzuilib$drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
//        GuiScreenListener listener = MyMod.guiScreenListener;
//        Minecraft mc = Minecraft.getMinecraft();
//        if (listener == null) return;
//        if (!listener.showedDebug) {
//            int width = mc.displayWidth;
//            int height = mc.displayHeight;
//            ScaledResolution scale = new ScaledResolution(mc, width, height);
//            width = scale.getScaledWidth();
//            height = scale.getScaledHeight();
//
//            glPushAttrib(GL_ALL_ATTRIB_BITS);
//            glPushMatrix();
//            glClear(GL_ALL_ATTRIB_BITS);
//            glDisable(GL_CULL_FACE);
//            glDisable(GL_TEXTURE_2D);
//            glColor4f(1, 0, 0, 0.5f);
//
//            glBegin(GL_QUADS);
//            glVertex3f(0, 0, 1001); // 左上
//            glVertex3f(0, 50, 1001);// 左下
//            glVertex3f(50, 50, 1001);// 右下
//            glVertex3f(50, 0, 1001);// 右上
//
//            glVertex2f(width/2f-25, height/2f-25); // 左上
//            glVertex2f(width/2f-25, height/2f+25);
//            glVertex2f(width/2f+25, height/2f+25);
//            glVertex2f(width/2f+25, height/2f-25);
//            glEnd();
//
//            glPopMatrix();
//            glPopAttrib();
//        }
//    }
}
