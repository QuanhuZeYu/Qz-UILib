package club.heiqi.uilib.host.client.font;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 真正 MC 资源链与 FT/HB；不启动游戏。复现普通依赖 jar 不会成为 FML 资源包。 */
public final class CoreAssetResourceScenario extends TestApplication {
    @Override
    protected void runTests() throws Exception {
        scenario("Core内置字体进入MC资源链且Emoji不缺字", () -> {
            SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(new IMetadataSerializer());
            ResourceLocation emoji = new ResourceLocation("qz_uilib", "fonts/noto/NotoColorEmoji.ttf");
            try {
                manager.getResource(emoji);
                throw new AssertionError("未注册包却读取成功，未复现拆包故障");
            } catch (FileNotFoundException expected) {
                System.out.println("REPRO 未注册Core资产时MC资源链缺少Emoji字体");
            }
            List<IResourcePack> defaults = new ArrayList<>();
            CoreAssetResourcePack.install(defaults);
            CoreAssetResourcePack.install(defaults);
            require(defaults.size() == 1, "多次刷新重复注册Core资源包");
            manager.reloadResourcePack(defaults.get(0));
            try (InputStream input = manager.getResource(emoji).getInputStream()) {
                require(input.read() != -1, "Emoji字体为空");
            }
            ClassLoader loader = new ClassLoader(FontTextService.class.getClassLoader()) {
                @Override
                public InputStream getResourceAsStream(String name) {
                    if (!name.startsWith("assets/qz_uilib/")) return super.getResourceAsStream(name);
                    try {
                        return manager.getResource(new ResourceLocation("qz_uilib", name.substring("assets/qz_uilib/".length())))
                                .getInputStream();
                    } catch (IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                }
            };
            try (FontTextService fonts = new FontTextService(FontTextService.defaultSources(loader, 1), IsolatedFontEngine.shared())) {
                for (String text : Arrays.asList("😀", "👩‍💻", "🇨🇳", "👍🏽")) {
                    TextLayout layout = fonts.layout(text, new TextStyle("default", 20, -1), 1000, TextPolicy.SINGLE_LINE);
                    require(!layout.lines().get(0).glyphs().isEmpty(), "Emoji无字形: " + text);
                    for (TextLayout.Glyph glyph : layout.lines().get(0).glyphs())
                        require(glyph.color && !glyph.replacement, "Emoji变成替代或失去彩色: " + text);
                    for (TextLayout.Diagnostic diagnostic : layout.diagnostics())
                        require(diagnostic.kind != TextLayout.Diagnostic.Kind.FONT_RESOURCE, "字体来源读取失败: " + diagnostic.message);
                }
            }
            Path zip = Files.createTempFile("uilib-core-override-", ".zip");
            try {
                byte[] override = {7, 8, 9};
                try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
                    out.putNextEntry(new java.util.zip.ZipEntry("assets/qz_uilib/fonts/noto/NotoColorEmoji.ttf"));
                    out.write(override);
                    out.closeEntry();
                }
                // reloadResources 的 Forge 进度UI要求真实 LaunchWrapper；这里验证同一实际资源链的覆盖与换代。
                SimpleReloadableResourceManager overridden = new SimpleReloadableResourceManager(new IMetadataSerializer());
                overridden.reloadResourcePack(defaults.get(0));
                try (FileResourcePack user = new FileResourcePack(zip.toFile())) {
                    overridden.reloadResourcePack(user);
                    try (InputStream input = overridden.getResource(emoji).getInputStream()) {
                        require(input.read() == override[0], "用户包未覆盖Core默认字体");
                    }
                }
                SimpleReloadableResourceManager renewed = new SimpleReloadableResourceManager(new IMetadataSerializer());
                renewed.reloadResourcePack(defaults.get(0));
                try (InputStream input = renewed.getResource(emoji).getInputStream()) {
                    require(input.read() != override[0], "移除用户包后仍读取旧覆盖");
                }
            } finally {
                Files.deleteIfExists(zip);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        new CoreAssetResourceScenario().run();
    }
}
