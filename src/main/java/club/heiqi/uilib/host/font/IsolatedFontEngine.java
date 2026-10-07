package club.heiqi.uilib.host.font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** CPU字体绑定的发行隔离。官方JNI类名保留在私有加载器内，不覆盖窗口宿主的org.lwjgl。
 * Java8的URLClassLoader.getPackage会继承parent的sealed包，因此必须null parent并显式共享核心合同。
 * 只装载core/FreeType/HarfBuzz，不装载GL或GLFW；配置对象属于child，不修改全局系统属性。 */
public final class IsolatedFontEngine implements FontEngine {
    private static final String ROOT="assets/qz_uilib/runtime/font/";
    private final FontEngine engine;
    private final URLClassLoader bindings;
    private static IsolatedFontEngine shared;

    public static synchronized FontEngine shared() {
        if(shared==null)shared=new IsolatedFontEngine();
        return shared;
    }
    private IsolatedFontEngine() {
        ClassLoader application=IsolatedFontEngine.class.getClassLoader();
        try {
            Path directory=Files.createTempDirectory("qz-font-bindings-");
            directory.toFile().deleteOnExit();
            List<URL> urls=new ArrayList<URL>();
            urls.add(IsolatedFontEngine.class.getProtectionDomain().getCodeSource().getLocation());
            String index;
            try(InputStream input=application.getResourceAsStream(ROOT+"index.txt")){
                if(input==null)throw new IOException("缺少随包CPU字体绑定清单");
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[8192];
                for(int count;(count=input.read(chunk))!=-1;)bytes.write(chunk,0,count);
                index=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            }
            for(String line:index.split("\r?\n")){
                String name=line.trim();if(name.isEmpty())continue;
                if(!name.matches("[a-zA-Z0-9._-]+")||!name.endsWith(".jar"))throw new IOException("无效CPU绑定资源名称: "+name);
                Path file=directory.resolve(name);
                try(InputStream input=application.getResourceAsStream(ROOT+name)){
                    if(input==null)throw new IOException("缺少CPU绑定资源: "+name);
                    Files.copy(input,file);
                }
                file.toFile().deleteOnExit();urls.add(file.toUri().toURL());
            }
            bindings=new BindingLoader(urls.toArray(new URL[urls.size()]),application);
            Path nativeDirectory=Files.createDirectory(directory.resolve("native"));nativeDirectory.toFile().deleteOnExit();
            Class<?> configuration=Class.forName("org.lwjgl.system.Configuration",true,bindings);
            for(String option:new String[]{"SHARED_LIBRARY_EXTRACT_PATH","LIBRARY_PATH"}){
                Object setting=configuration.getField(option).get(null);
                setting.getClass().getMethod("set",Object.class).invoke(setting,nativeDirectory.toAbsolutePath().toString());
            }
            Object factory=Class.forName("club.heiqi.uilib.host.font.freetype.LwjglFontEngine",true,bindings).getConstructor().newInstance();
            engine=(FontEngine)factory;
        }catch(IOException|ReflectiveOperationException failure){throw new IllegalStateException("UILib随包字体绑定初始化失败",failure);}
    }
    @Override public FontSession openSession(){return engine.openSession();}

    private static final class BindingLoader extends URLClassLoader {
        private final ClassLoader application;
        BindingLoader(URL[] urls,ClassLoader application){super(urls,null);this.application=application;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("org.lwjgl.")||name.startsWith("club.heiqi.uilib.host.font.freetype.")){
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
            }
            if(name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("sun."))return super.loadClass(name,resolve);
            return application.loadClass(name);
        }
        @Override public URL getResource(String name){
            URL owned=findResource(name);
            if(owned!=null||name.contains("org/lwjgl")||name.endsWith(".dll")||name.endsWith(".so")||name.endsWith(".dylib"))return owned;
            return application.getResource(name);
        }
    }
}
