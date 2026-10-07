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
            urls.add(implementationRoot());
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
            Path nativeDirectory=Files.createDirectory(directory.resolve("native"));nativeDirectory.toFile().deleteOnExit();
            bindings=new BindingLoader(urls.toArray(new URL[urls.size()]),application,nativeDirectory);
            Class<?> configuration=Class.forName("org.lwjgl.system.Configuration",true,bindings);
            for(String option:new String[]{"SHARED_LIBRARY_EXTRACT_PATH","LIBRARY_PATH"}){
                Object setting=configuration.getField(option).get(null);
                setting.getClass().getMethod("set",Object.class).invoke(setting,nativeDirectory.toAbsolutePath().toString());
            }
            Object extractForce=configuration.getField("SHARED_LIBRARY_EXTRACT_FORCE").get(null);
            extractForce.getClass().getMethod("set",Object.class).invoke(extractForce,Boolean.FALSE);
            Object factory=Class.forName("club.heiqi.uilib.host.font.freetype.LwjglFontEngine",true,bindings).getConstructor().newInstance();
            engine=(FontEngine)factory;
        }catch(IOException|ReflectiveOperationException failure){throw new IllegalStateException("UILib随包字体绑定初始化失败",failure);}
    }
    /** LaunchWrapper的CodeSource是jar内class资源URL，不能作为URLClassLoader的搜索根。
     * 从自身实际资源归一化jar根或开发classes目录；实现仍只由私有loader定义。 */
    private static URL implementationRoot() throws IOException {
        String entry=IsolatedFontEngine.class.getName().replace('.','/')+".class";
        URL resource=IsolatedFontEngine.class.getResource("/"+entry);
        if(resource==null)throw new IOException("缺少字体隔离入口资源: "+entry);
        java.net.URLConnection connection=resource.openConnection();
        if(connection instanceof java.net.JarURLConnection)return ((java.net.JarURLConnection)connection).getJarFileURL();
        String location=resource.toExternalForm();
        if("file".equals(resource.getProtocol())&&location.endsWith(entry))return new URL(location.substring(0,location.length()-entry.length()));
        throw new IOException("字体实现资源不支持的来源: "+resource);
    }
    @Override public FontSession openSession(){return engine.openSession();}

    private static final class BindingLoader extends URLClassLoader {
        private final ClassLoader application;
        private final Path nativeDirectory;
        BindingLoader(URL[] urls,ClassLoader application,Path nativeDirectory){super(urls,null);this.application=application;this.nativeDirectory=nativeDirectory;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("org.lwjgl.")||name.startsWith("club.heiqi.uilib.host.font.freetype.")){
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
            }
            if(name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("sun."))return super.loadClass(name,resolve);
            return application.loadClass(name);
        }
        @Override public synchronized URL getResource(String name){
            URL owned=findResource(name);
            boolean nativeResource=name.endsWith(".dll")||name.endsWith(".so")||name.endsWith(".dylib");
            if(owned!=null&&nativeResource){
                // 官方SharedLibraryLoader提取jar资源时会写进程全局org.lwjgl.librarypath。
                // 返回私有常规文件让官方Library直接加载；按其请求选择平台/架构，不改JNI或全局属性。
                Path file=nativeDirectory.resolve(name).normalize();
                if(!file.startsWith(nativeDirectory))throw new IllegalStateException("无效字体native资源: "+name);
                try{
                    if(!Files.exists(file)){
                        Files.createDirectories(file.getParent());
                        try(InputStream input=owned.openStream()){Files.copy(input,file);}
                        file.toFile().deleteOnExit();
                    }
                    return file.toUri().toURL();
                }catch(IOException failure){throw new IllegalStateException("私有字体native提取失败: "+name,failure);}
            }
            if(owned!=null||name.contains("org/lwjgl")||nativeResource)return owned;
            return application.getResource(name);
        }
    }
}
