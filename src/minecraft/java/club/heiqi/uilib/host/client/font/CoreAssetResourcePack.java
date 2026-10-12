package club.heiqi.uilib.host.client.font;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** Core 资产由独立成品持有；MC 将它注册为默认资源包，用户资源包仍按宿主顺序覆盖。
 * 普通 classpath jar 不会被 FML 自动注册，不能把资源读取失败当成 Emoji 缺字。
 * 每次读取走资源管理器的本次重载，字体来源仍由既有代际持有字节快照。 */
public final class CoreAssetResourcePack implements IResourcePack {
    private final ClassLoader loader = FontTextService.class.getClassLoader();

    public static void install(List<IResourcePack> defaults) {
        for (IResourcePack pack : defaults) if (pack instanceof CoreAssetResourcePack) return;
        defaults.add(new CoreAssetResourcePack());
    }

    private String path(ResourceLocation location) {
        if (!"qz_uilib".equals(location.getResourceDomain())) return null;
        String name = location.getResourcePath();
        if ((!name.startsWith("fonts/") && !name.startsWith("unicode/"))
                || name.contains("..") || name.indexOf('\\') >= 0) return null;
        return "assets/qz_uilib/" + name;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String name = path(location);
        InputStream input = name == null ? null : loader.getResourceAsStream(name);
        if (input == null) throw new FileNotFoundException(location.toString());
        return input;
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String name = path(location);
        return name != null && loader.getResource(name) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return Collections.singleton("qz_uilib");
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return null;
    }

    @Override
    public String getPackName() {
        return "Qz UILib Core Assets";
    }
}
