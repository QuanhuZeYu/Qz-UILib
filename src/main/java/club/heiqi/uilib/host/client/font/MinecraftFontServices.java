package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.font.compat.IrisProgramProvider;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import cpw.mods.fml.common.FMLLog;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
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
        try{GlApi gl=MinecraftGlHost.create();shared=new ClientFontServices(initial,++revision,ClientFontServices.Settings.defaults(),true,
            IsolatedFontEngine.shared(),gl,new IrisProgramProvider(MinecraftFontServices.class.getClassLoader(),gl));loader=initial;}
        catch(IOException failure){throw new IllegalStateException("客户端字体服务安装失败",failure);}
        installed=true;
        // MC注册listener会立即回调；安装期也从同一入口建立配置+内置候选的新代际。
        ((IReloadableResourceManager)resources).registerReloadListener(new IResourceManagerReloadListener(){
            @Override public void onResourceManagerReload(IResourceManager manager) {
                if(shared==null)return;checkThread();reloadSources(manager);
            }
        });
    }
    /** 配置入口不安装服务；启动前只保留快照，退出后通知也不能重新创建owner。 */
    static void configurationChanged() {
        if(shared==null)return;checkThread();
        reloadSources(Minecraft.getMinecraft().getResourceManager());
    }
    private static void reloadSources(IResourceManager resources) {
        checkThread();
        ResourceFonts next=new ResourceFonts(resources,thread);long nextRevision=revision+1;
        try {
            List<FontSource> candidates=new ArrayList<FontSource>();
            List<String> paths=VanillaFontOptions.systemFontFiles();
            List<java.nio.file.Path> configured=new ArrayList<java.nio.file.Path>();for(String path:paths)configured.add(Paths.get(path));
            List<java.nio.file.Path> directories=new ArrayList<java.nio.file.Path>();
            String windows=System.getenv("WINDIR"),local=System.getenv("LOCALAPPDATA");
            if(windows!=null)directories.add(Paths.get(windows,"Fonts"));if(local!=null)directories.add(Paths.get(local,"Microsoft","Windows","Fonts"));
            club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver resolver=paths.isEmpty()?null:new club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver(configured,directories,nextRevision,message->FMLLog.warning("[Qz-UILib] %s",message));
            for(int i=0;i<paths.size();i++) {
                String path=paths.get(i);String id="system-file-"+i;
                if(path.trim().isEmpty())throw new IllegalArgumentException("系统字体路径为空: "+id);
                // 文件现在取字节快照；未验证脚本能力，不能凭cmap认定msyh.ttc支持复杂整形。
                try{candidates.add(resolver.family(Paths.get(path),id));}
                catch(IOException failure){throw new IOException("系统字体读取失败: "+id+" origin="+path,failure);}
            }
            candidates.addAll(FontTextService.defaultSources(next,nextRevision));
            shared.reload(candidates);revision=nextRevision;loader.retire();loader=next;
            StringBuilder origins=new StringBuilder();
            for(FontSource source:candidates) {
                if(origins.length()>0)origins.append(", ");
                origins.append(source.id).append(" origin=").append(source.origin);
            }
            FMLLog.info("[Qz-UILib] 字体候选换代 revision=%s candidates=[%s]",revision,origins);
        }catch(IOException|RuntimeException failure) {
            next.retire();throw new IllegalStateException("客户端字体候选重载失败，revision="+nextRevision,failure);
        }
    }
    private static void checkThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("MC字体资源仅限客户端主线程");}
    public static long resourceRevision(){checkThread();return revision;}
    public static ClientFontServices shared(){checkThread();if(shared==null)throw new IllegalStateException("客户端字体服务尚未安装/已关闭");return shared;}
    /** LWJGL窗口交换之前的通知；org.lwjgl类被宿主排除转换，故由MC调用点转交。
     * 无文字消费时不创建呈现范围、worker或GL资源。 */
    public static void beforePresent() {
        if(shared==null)return;checkThread();
        MinecraftVanillaFont.beforePresent();
        if(shared.presentationStats().active)shared.endPresentation();
    }
    public static void closeShared() {
        if(shared==null)return;checkThread();ClientFontServices owner=shared;shared=null;
        try{MinecraftVanillaFont.close();owner.close();}finally{if(loader!=null){loader.retire();loader=null;}}
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
