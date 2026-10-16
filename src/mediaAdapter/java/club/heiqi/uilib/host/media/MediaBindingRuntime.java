package club.heiqi.uilib.host.media;

import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** 一个应用加载器固定一个媒体运行域；native驻留不随session新增加载器/路径。 */
final class MediaBindingRuntime {
    private static final String ROOT="assets/qz_uilib/runtime/media/";
    private final BindingLoader bindings;
    private final MediaDecoderFactory decoders;
    private final Method snapshot;
    MediaBindingRuntime() {
        ClassLoader application=IsolatedMediaAdapters.class.getClassLoader();
        Path directory=null;BindingLoader pending=null;
        try {
            directory=Files.createTempDirectory("qz-media-bindings-");
            Path jars=Files.createDirectory(directory.resolve("jars"));
            Path natives=Files.createDirectory(directory.resolve("native"));
            Path cache=Files.createDirectory(directory.resolve("javacpp-cache"));
            String override=System.getProperty("uilib.media.runtime");
            Path external=override==null ? null : Paths.get(override).toAbsolutePath().normalize();
            List<URL> urls=new ArrayList<URL>(); urls.add(implementationRoot());
            try(InputStream input=application.getResourceAsStream(ROOT+"index.txt")) {
                if(input==null) throw new IOException("缺少随包媒体绑定清单");
                BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
                for(String line;(line=reader.readLine())!=null;) {
                    if(line.trim().isEmpty())continue;
                    String[] item=line.trim().split(" ");
                    if(item.length!=2 || !item[0].matches("[a-zA-Z0-9._-]+[.]jar") || !item[1].matches("[a-f0-9]{64}")) throw new IOException("无效媒体绑定清单: "+line);
                    Path file=external==null ? jars.resolve(item[0]) : external.resolve(item[0]);
                    if(external==null) {
                        try(InputStream source=application.getResourceAsStream(ROOT+item[0])) {
                            if(source==null)throw new IOException("缺少媒体绑定资源: "+item[0]);
                            Files.copy(source,file);
                        }
                        if(!item[1].equals(hash(file)))throw new IOException("媒体绑定哈希不符: "+item[0]);
                    } else if(!Files.isRegularFile(file))throw new IOException("外部媒体绑定缺项: "+file);
                    urls.add(file.toUri().toURL());
                }
            }
            pending=new BindingLoader(urls.toArray(new URL[urls.size()]),application,natives);
            bindings=pending;
            // 配置对象只属于child；file URL使LWJGL避开会写global librarypath的提取分支。
            Class<?> configuration=Class.forName("org.lwjgl.system.Configuration",true,bindings);
            for(String name:new String[]{"LIBRARY_PATH","SHARED_LIBRARY_EXTRACT_PATH"}) {
                Object option=configuration.getField(name).get(null);
                option.getClass().getMethod("set",Object.class).invoke(option,natives.toString());
            }
            Object force=configuration.getField("SHARED_LIBRARY_EXTRACT_FORCE").get(null);
            force.getClass().getMethod("set",Object.class).invoke(force,Boolean.FALSE);
            Class<?> loader=Class.forName("org.bytedeco.javacpp.Loader",true,bindings);
            // 固定JavaCPP1.5.10局部字段适配。先设child缓存，再让Pointer/global初始化；不触全局属性。
            for(String name:new String[]{"cacheDir","tempDir"}) {
                Field field=loader.getDeclaredField(name); field.setAccessible(true); field.set(null,cache.toFile());
            }
            snapshot=Class.forName("club.heiqi.uilib.host.media.NativeMediaResourceTracker",true,bindings).getMethod("snapshot");
            decoders=(MediaDecoderFactory)Class.forName("club.heiqi.uilib.host.media.ffmpeg.FfmpegDecoderFactory",true,bindings).getConstructor().newInstance();
            // 只有完整构造成功才登记进程级退出清理；失败不能每次永久累积DeleteHook路径。
            List<Path> retained=new ArrayList<Path>();
            try(Stream<Path> paths=Files.walk(directory)){paths.sorted().forEach(retained::add);}
            for(Path path:retained)path.toFile().deleteOnExit();
            pending.initialized=true;
        } catch(Throwable failure) {
            if(pending!=null)try{pending.close();}catch(Throwable next){if(next!=failure)failure.addSuppressed(next);}
            if(directory!=null)cleanupFailed(directory,failure);
            if(failure instanceof Error)throw(Error)failure;
            throw new IllegalStateException("UILib私有媒体绑定初始化失败",failure);
        }
    }
    private static void cleanupFailed(Path directory,Throwable failure) {
        List<Path> paths=new ArrayList<Path>();
        try(Stream<Path> walk=Files.walk(directory)){walk.sorted(Comparator.reverseOrder()).forEach(paths::add);}
        catch(Throwable next){if(next!=failure)failure.addSuppressed(next);}
        for(Path path:paths)try{Files.deleteIfExists(path);}catch(Throwable next){if(next!=failure)failure.addSuppressed(next);}
    }
    MediaDecoderFactory decoderFactory(){return decoders;}
    MediaAudioSinkFactory audioFactory(String deviceName) throws Exception {
        try { return (MediaAudioSinkFactory)Class.forName("club.heiqi.uilib.host.media.openal.OpenAlAudioSinkFactory",true,bindings).getConstructor(String.class).newInstance(deviceName); }
        catch(InvocationTargetException error){throwCause(error);throw new AssertionError();}
    }
    long[] snapshot() {
        try{return ((long[])snapshot.invoke(null)).clone();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("读取所属媒体资源失败",failure);}
    }
    private static void throwCause(InvocationTargetException error) throws Exception {
        Throwable cause=error.getCause();if(cause instanceof Error)throw(Error)cause;if(cause instanceof Exception)throw(Exception)cause;throw new RuntimeException(cause);
    }
    private static String hash(Path path) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=Files.newInputStream(path)){byte[] block=new byte[8192];for(int count;(count=input.read(block))!=-1;)digest.update(block,0,count);}
            StringBuilder text=new StringBuilder();for(byte value:digest.digest())text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();
        } catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    private static URL implementationRoot() throws IOException {
        String entry=IsolatedMediaAdapters.class.getName().replace('.','/')+".class";
        URL resource=IsolatedMediaAdapters.class.getResource("/"+entry);
        if(resource==null)throw new IOException("缺少媒体入口资源");
        URLConnection connection=resource.openConnection();
        if(connection instanceof JarURLConnection)return ((JarURLConnection)connection).getJarFileURL();
        String location=resource.toExternalForm();
        if("file".equals(resource.getProtocol())&&location.endsWith(entry))return new URL(location.substring(0,location.length()-entry.length()));
        throw new IOException("不支持的媒体入口来源: "+resource);
    }
    private static final class BindingLoader extends URLClassLoader {
        private final ClassLoader application;private final Path natives;
        private boolean initialized;
        BindingLoader(URL[] urls,ClassLoader application,Path natives){super(urls,null);this.application=application;this.natives=natives;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            if(name.startsWith("org.bytedeco.")||name.startsWith("org.lwjgl.")||name.startsWith("io.lindstrom.m3u8.")
                    ||name.startsWith("club.heiqi.uilib.host.media.ffmpeg.")||name.startsWith("club.heiqi.uilib.host.media.openal.")
                    ||name.equals("club.heiqi.uilib.host.media.NativeMediaResourceTracker")) {
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
            }
            if(name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("sun."))return super.loadClass(name,resolve);
            return application.loadClass(name);
        }
        private boolean nativeName(String name){return name.endsWith(".dll")||name.endsWith(".dylib")||name.endsWith(".so")||name.contains(".so.");}
        private URL ownedResource(String name,URL owned) {
            if(owned==null||!nativeName(name))return owned;
            Path file=natives.resolve(name).normalize();
            if(!file.startsWith(natives))throw new IllegalStateException("无效媒体native资源: "+name);
            try {
                if(!Files.exists(file)){Files.createDirectories(file.getParent());try(InputStream input=owned.openStream()){Files.copy(input,file);}if(initialized)file.toFile().deleteOnExit();}
                return file.toUri().toURL();
            }catch(IOException failure){throw new IllegalStateException("私有媒体native提取失败: "+name,failure);}
        }
        @Override public synchronized URL getResource(String name) {
            URL owned=ownedResource(name,findResource(name));
            if(owned!=null||nativeName(name)||name.startsWith("org/lwjgl/")||name.startsWith("org/bytedeco/"))return owned;
            return application.getResource(name);
        }
        @Override public synchronized Enumeration<URL> getResources(String name) throws IOException {
            List<URL> result=new ArrayList<URL>();Enumeration<URL> owned=findResources(name);
            while(owned.hasMoreElements())result.add(ownedResource(name,owned.nextElement()));
            if(result.isEmpty()&&!nativeName(name)&&!name.startsWith("org/lwjgl/")&&!name.startsWith("org/bytedeco/"))return application.getResources(name);
            return Collections.enumeration(result);
        }
    }
}
