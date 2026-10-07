package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/** MC资源管理适配，生产client init安装；字体/GL服务不依赖开发scene。
 * 父级退出挂接必须在 Minecraft.shutdownMinecraftApplet HEAD 主/GL线程调用 closeShared()，
 * 这里不使用shutdownhook，不在任意JVM线程释放主线程FontTextService或GL对象。
 */
public final class MinecraftFontServices {
    private static ClientFontServices shared;
    private static ResourceFonts loader;
    private static Thread thread;
    private static long revision;
    private static boolean installed;
    private MinecraftFontServices(){}
    public static void installShared() {
        if(installed){checkThread();if(shared==null)throw new IllegalStateException("客户端字体owner已退出，不可重新注册reload listener");return;}
        thread=Thread.currentThread();
        IResourceManager resources=Minecraft.getMinecraft().getResourceManager();
        if(!(resources instanceof IReloadableResourceManager))throw new IllegalStateException("字体资源要求可重载MC ResourceManager");
        ResourceFonts initial=new ResourceFonts(resources,thread);
        try{shared=new ClientFontServices(initial,++revision,ClientFontServices.Settings.defaults(),true);loader=initial;}
        catch(IOException failure){throw new IllegalStateException("客户端字体服务安装失败",failure);}
        installed=true;
        ((IReloadableResourceManager)resources).registerReloadListener(new IResourceManagerReloadListener(){
            @Override public void onResourceManagerReload(IResourceManager manager) {
                if(shared==null)return;checkThread();ResourceFonts next=new ResourceFonts(manager,thread);
                try{shared.reload(next,++revision);loader.retire();loader=next;}
                catch(IOException failure){next.retire();throw new IllegalStateException("客户端字体资源重载失败",failure);}
            }
        });
    }
    private static void checkThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("MC字体资源仅限客户端主线程");}
    public static ClientFontServices shared(){checkThread();if(shared==null)throw new IllegalStateException("客户端字体服务尚未安装/已关闭");return shared;}
    /** LWJGL窗口交换之前的通知；org.lwjgl类被宿主排除转换，故由MC调用点转交。
     * 无文字消费时不创建呈现范围、worker或GL资源。 */
    public static void beforePresent() {
        if(shared==null)return;checkThread();
        if(shared.presentationStats().active)shared.endPresentation();
    }
    public static void closeShared() {
        if(shared==null)return;checkThread();ClientFontServices owner=shared;shared=null;
        try{owner.close();}finally{if(loader!=null){loader.retire();loader=null;}}
    }
    /** 仅资源指定的候选列表；读取失败显式报告，不回退classpath或append隐藏字体。
     * 旧plan已加载FontSource保持byte snapshot；退休代际未加载source拒绝重读新包。
     */
    private static final class ResourceFonts extends ClassLoader {
        private final IResourceManager resources;private final Thread thread;private boolean retired;
        ResourceFonts(IResourceManager resources,Thread thread){super(MinecraftFontServices.class.getClassLoader());this.resources=resources;this.thread=thread;}
        void retire(){retired=true;}
        @Override public InputStream getResourceAsStream(String name) {
            if(!name.startsWith("assets/qz_uilib/"))return super.getResourceAsStream(name);
            if(Thread.currentThread()!=thread||retired)throw new IllegalStateException("字体资源代际必须先在主线程measure加载: "+name);
            try{return resources.getResource(new ResourceLocation("qz_uilib",name.substring("assets/qz_uilib/".length()))).getInputStream();}
            catch(IOException failure){throw new IllegalStateException("MC字体资源读取失败: "+name,failure);}
        }
    }
}
