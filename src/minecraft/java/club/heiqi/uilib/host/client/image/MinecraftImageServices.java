package club.heiqi.uilib.host.client.image;

import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import club.heiqi.uilib.ui.scene.image.SharedImageService;

/** 安装/资源换代/退出均使用正式client生命周期；worker仅持独立stream，绝不读取ResourceManager。 */
public final class MinecraftImageServices {
    private static ClientImageServices shared;
    private static Thread thread;
    private static boolean installed;

    private MinecraftImageServices() {}

    public static void installShared() {
        if (installed) {
            check();
            return;
        }
        thread = Thread.currentThread();
        JavaImageLoader loader =
                new JavaImageLoader(
                        id -> {
                            check();
                            InputStream stream =
                                    Minecraft.getMinecraft()
                                            .getResourceManager()
                                            .getResource(new ResourceLocation(id))
                                            .getInputStream();
                            return stream;
                        },
                        JavaImageLoader.Budget.defaults());
        shared =
                new ClientImageServices(
                        MinecraftFontServices.shared().gl(),
                        new MinecraftImageLoader(loader),
                        SharedImageService.Budget.defaults());
        installed = true;
        shared.textures().hostItems(new MinecraftItemRenderer(MinecraftFontServices.shared()));
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (!(manager instanceof IReloadableResourceManager))
            throw new IllegalStateException("图像资源要求可重载ResourceManager");
        ((IReloadableResourceManager) manager)
                .registerReloadListener(
                        resources -> {
                            if (shared != null) {
                                check();
                                shared.resourcesReloaded();
                            }
                        });
    }

    private static void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("图像服务仅限客户端线程");
    }

    public static ClientImageServices shared() {
        check();
        if (shared == null) throw new IllegalStateException("图像服务尚未安装或已退出");
        return shared;
    }

    public static void prepareResources() {
        if (shared == null) return;
        check();
        shared.prepareResources();
    }

    public static void closeShared() {
        if (shared == null) return;
        check();
        ClientImageServices previous = shared;
        shared = null;
        previous.close();
    }
}
