package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;

import club.heiqi.uilib.testing.TestApplication;




import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.reactive.Signal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅正式成品供给生产定义；原LWJGL2/桌面窗口、字体与媒体私有JNI在一个JVM共存。 */
public final class PackagedMediaScenario extends TestApplication {
    private static final String[] PROPERTIES={"org.lwjgl.librarypath","org.bytedeco.javacpp.cachedir","org.bytedeco.javacpp.cacheDir","java.io.tmpdir"};
    private final boolean legacy=System.getProperty("java.specification.version").equals("1.8");
    private Object window;
    private Signal<List<SceneNode>> children;
    private ScenePageContext page;
    private AutoCloseable display;
    private Class<?> legacyAl,legacyAl10;
    private int hostSource;
    private Path temporary;
    private final MediaService service=new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT);
    private static Object call(Method method,Object target,Object...args)throws Exception {
        try{return method.invoke(target,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Error)throw(Error)cause;if(cause instanceof Exception)throw(Exception)cause;throw new RuntimeException(cause);}
    }
    private static String source(Class<?> type){return type.getProtectionDomain().getCodeSource().getLocation().toString();}
    private static Map<String,String> properties(){Map<String,String> result=new LinkedHashMap<>();for(String key:PROPERTIES)result.put(key,System.getProperty(key));return result;}
    @Override protected void setup()throws Exception {
        require(IsolatedMediaAdapters.resources().runtimeLoaders==0,"仅选择工厂就初始化了媒体绑定");
        temporary=Files.createTempDirectory("uilib-packaged-media-");
        if(legacy){
            Class<?> environment=Class.forName("club.heiqi.uilib.testing.DisplayTestEnvironment");
            display=(AutoCloseable)environment.getConstructor(int.class,int.class,String.class).newInstance(320,240,"UILib packaged Java8 media");
            legacyAl=Class.forName("org.lwjgl.openal.AL");legacyAl10=Class.forName("org.lwjgl.openal.AL10");
            require(!(Boolean)call(legacyAl.getMethod("isCreated"),null),"测试进程已有未知音频owner");
            call(legacyAl.getMethod("create"),null);
            hostSource=(Integer)call(legacyAl10.getMethod("alGenSources"),null);
            require(hostSource!=0,"原LWJGL2音频source未创建");
            System.out.println("LEGACY_BINDING "+source(legacyAl)+" display="+source(Class.forName("org.lwjgl.opengl.Display")));
        }else{
            ScenePage content=context->{
                page=context;children=context.domain().signal(Collections.<SceneNode>emptyList());
                SceneNode root=new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.fill(1),Size.fill(1));
                root.bind(NodeBinding.ownedChildren(children));context.mountOwned(root);
            };
            Class<?> type=Class.forName("club.heiqi.uilib.host.desktop.DesktopWindow");
            window=call(type.getMethod("openWithMedia",Path.class,MediaService.class,ScenePage.class),null,temporary.resolve("configuration.yaml"),service,content);
            frame();
            System.out.println("DESKTOP_SOURCE "+source(type));
        }
        font();
        System.out.println("PACKAGED_RUNTIME java="+System.getProperty("java.version")+" core="+source(MediaService.class)+" media="+source(IsolatedMediaAdapters.class));
    }
    private void frame()throws Exception {
        if(legacy)call(Class.forName("org.lwjgl.opengl.Display").getMethod("update"),null);
        else call(window.getClass().getMethod("frame"),window);
    }
    private static void font()throws Exception {
        FontEngine engine=(FontEngine)call(Class.forName("club.heiqi.uilib.host.font.IsolatedFontEngine").getMethod("shared"),null);
        byte[] source;
        try(InputStream input=PackagedMediaScenario.class.getResourceAsStream("/assets/qz_uilib/fonts/noto/NotoSansArabic.ttf");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            require(input!=null,"正式核心缺少字体资源");byte[] block=new byte[8192];for(int count;(count=input.read(block))!=-1;)output.write(block,0,count);source=output.toByteArray();
        }
        try(FontSession session=engine.openSession();FontFace face=session.openFace(ByteBuffer.wrap(source),0,24)){
            int glyphs=0,visible=0;
            for(Object glyph:(Iterable<?>)face.shape("العربية",ShapeDirection.RTL,"ar")){
                int id=glyph.getClass().getField("glyphId").getInt(glyph);require(id>0,"字体整形缺glyph");
                for(byte value:face.rasterize(id).pixels())if(value!=0)visible++;
                glyphs++;
            }
            require(glyphs>0&&visible>0,"真实字体整形/光栅没有像素");
            System.out.println("PACKAGED_FONT glyphs="+glyphs+" visibleBytes="+visible);
        }
    }
    @Override protected void runTests()throws Exception {
        scenario("正式成品媒体绑定隔离与窗口字体原音频共存",()->{
            Map<String,String> before=properties();
            try(MediaFixture fixture=new MediaFixture()){
                // 原始decoder对象的定义/native依赖来源也来自正式私有域。
                MediaCancellation token=()->false;
                try(MediaDecoderSource source=IsolatedMediaAdapters.decoderFactory().openSource(MediaSource.of(fixture.uri()),token);
                    MediaDecoder decoder=source.open(0,false,token)){
                    boolean actual=false;for(int i=0;i<100;i++){MediaDecoded next=decoder.read();if(next==null)break;if(next.video){actual=next.data.remaining()>0;break;}}
                    require(actual,"正式私有FFmpeg没有实际解码像素");
                    ClassLoader child=decoder.getClass().getClassLoader();
                    require(child!=getClass().getClassLoader(),"媒体实现没有隔离");
                    require(child.loadClass("club.heiqi.uilib.ui.media.spi.MediaDecoder")==MediaDecoder.class,"中性SPI跨加载器复制");
                    require(child.loadClass("org.lwjgl.BufferUtils")!=Class.forName("org.lwjgl.BufferUtils"),"媒体覆盖宿主LWJGL");
                    System.out.println("PRIVATE_SOURCES implementation="+source(decoder.getClass())+" javacpp="+source(child.loadClass("org.bytedeco.javacpp.Loader"))+" openal="+source(child.loadClass("org.lwjgl.openal.AL10")));
                }
                for(int iteration=0;iteration<2;iteration++){
                    MediaSession session=service.open(MediaSource.of(fixture.uri()),false);session.muted(true);
                    if(!legacy)page.domain().execute(()->children.set(Collections.singletonList(SceneVideo.create(page,session).size(Size.fill(1),Size.fill(1)))));
                    session.play();long deadline=System.nanoTime()+8_000_000_000L;
                    while(System.nanoTime()<deadline){
                        frame();MediaSnapshot state=session.snapshot();MediaTestSupport.checked(state);
                        if(state.outputReady&&state.positionNanos>150_000_000L&&(legacy||state.statistics.presentedFrames>0))break;
                        Thread.sleep(5);
                    }
                    MediaSnapshot state=session.snapshot();require(state.outputReady&&state.positionNanos>150_000_000L,"真实音频设备没有消费");
                    if(!legacy)require(state.statistics.presentedFrames>0,"正式桌面没有actual swap回执");
                    require(IsolatedMediaAdapters.resources().audioDevices>0&&IsolatedMediaAdapters.resources().formatContexts>0,"活跃期间真实native资源未计数");
                    session.close();if(!legacy)page.domain().execute(()->children.set(Collections.<SceneNode>emptyList()));
                    frame();require(service.activeSessions()==0&&IsolatedMediaAdapters.resources().activeHandles()==0,"关闭后真实所属句柄未归零: "+IsolatedMediaAdapters.resources());
                    require(IsolatedMediaAdapters.resources().runtimeLoaders==1,"重复播放创建了新永久loader");
                    System.out.println("PACKAGED_CLOSE iteration="+iteration+" "+IsolatedMediaAdapters.resources()+" presented="+state.statistics.presentedFrames);
                }
            }
            require(before.equals(properties()),"媒体私有绑定修改全局路径属性: before="+before+" after="+properties());
            font();frame();
            if(legacy){require((Boolean)call(legacyAl.getMethod("isCreated"),null),"媒体关闭了宿主AL");require((Boolean)call(legacyAl10.getMethod("alIsSource",int.class),null,hostSource),"媒体销毁了宿主音频source");}
            System.out.println("PACKAGED_COEXIST propertiesUnchanged=true hostAlive=true loaderReuse=true");
        });
    }
    @Override protected void teardown()throws Exception {
        Throwable failure=null;
        try{service.close();}catch(Throwable next){failure=next;}
        try{if(window!=null){((AutoCloseable)window).close();Object snapshot=call(window.getClass().getMethod("mediaResources"),window);require(snapshot.getClass().getField("textureCount").getInt(snapshot)==0&&snapshot.getClass().getField("retainedBytes").getLong(snapshot)==0,"关闭窗口所属GPU纹理未归零");System.out.println("PACKAGED_GPU closed=true textures=0 bytes=0");}}
        catch(Throwable next){if(failure==null)failure=next;else failure.addSuppressed(next);}
        try{if(hostSource!=0){call(legacyAl10.getMethod("alDeleteSources",int.class),null,hostSource);hostSource=0;}if(legacyAl!=null&&(Boolean)call(legacyAl.getMethod("isCreated"),null))call(legacyAl.getMethod("destroy"),null);}
        catch(Throwable next){if(failure==null)failure=next;else failure.addSuppressed(next);}
        try{if(display!=null)display.close();}catch(Throwable next){if(failure==null)failure=next;else failure.addSuppressed(next);}
        if(temporary!=null)try(java.util.stream.Stream<Path> paths=Files.walk(temporary)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){path.toFile().deleteOnExit();}});}
        if(failure instanceof Error)throw(Error)failure;if(failure instanceof Exception)throw(Exception)failure;
    }
    public static void main(String[] args)throws Exception{new PackagedMediaScenario().run(args);}
}
