package club.heiqi.qz_uilib.mixins.late;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@LateMixin
public class LateMixins implements ILateMixinLoader {
    @Override
    public String getMixinConfig() {
        return "mixins.qz_uilib.late.json";
    }

    @NotNull
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Arrays.asList(
                "MixinSmallFontRenderer",
                "MixinTCFontRenderer"
        );
    }
}
