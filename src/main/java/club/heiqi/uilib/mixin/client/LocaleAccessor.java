package club.heiqi.uilib.mixin.client;

import java.util.Map;
import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 原版已加载表的读取入口；不用反射，也不另解析.lang文件。 */
@Mixin(Locale.class)
public interface LocaleAccessor {
    @Accessor("field_135032_a") Map<String, String> qz$translations();
}
