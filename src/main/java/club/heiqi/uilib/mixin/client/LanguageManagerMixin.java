package club.heiqi.uilib.mixin.client;

import java.util.Map;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import club.heiqi.uilib.host.client.localization.LanguageManagerAccess;
import club.heiqi.uilib.host.client.localization.MinecraftLocalizationServices;

/** patched LanguageManager方法内顺序为Locale.load、Forge.merge、StringTranslate.replace。
 * 必须在TAIL发布，不能用setCurrentLanguage或独立reload listener拼接新语言和旧资料。 */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin implements LanguageManagerAccess {
    @Shadow private String currentLanguage;
    @Shadow @Final protected static Locale currentLocale;
    @Override public String qz$languageCode() { return currentLanguage; }
    @Override public Map<String, String> qz$translations() { return ((LocaleAccessor) currentLocale).qz$translations(); }
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void qz$loadedLanguage(IResourceManager resources, CallbackInfo callback) { MinecraftLocalizationServices.loaded(this); }
}
