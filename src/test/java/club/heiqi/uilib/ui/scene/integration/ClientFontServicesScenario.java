package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import static org.lwjgl.opengl.GL32C.*;

/** 不启动MC，使用生产共享owner/scene pipeline/GLconsumer验证原生像素与生命周期。 */
public final class ClientFontServicesScenario extends TestApplication {
    private static final int W=301,H=181;
    private static final class GenerationFonts extends ClassLoader {
        boolean retired;
        GenerationFonts(){super(ClientFontServicesScenario.class.getClassLoader());}
        @Override public InputStream getResourceAsStream(String name){if(retired)throw new IllegalStateException("退休源不能读取新包");return super.getResourceAsStream(name);}
    }
    private static final class Host implements SceneHost {
        final ClientFontServices owner;final FramebufferSceneRenderer screen;int width=W,height=H;
        Host(ClientFontServices owner){this.owner=owner;screen=new FramebufferSceneRenderer(owner);}
        @Override public int width(){return width;}
        @Override public int height(){return height;}
        @Override public long metricsRevision(){return owner.fonts().generation();}
        @Override public void replay(PaintPlan plan){screen.replay(plan,width,height);}
    }
    private static int[] read(){ByteBuffer buffer=MemoryUtil.memAlloc(W*H*4);try{glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,buffer);int[] values=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;values[y*W+x]=((buffer.get(i+3)&255)<<24)|((buffer.get(i)&255)<<16)|((buffer.get(i+1)&255)<<8)|(buffer.get(i+2)&255);}return values;
    }finally{MemoryUtil.memFree(buffer);}}
    private static void image(File file,int[] values)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,W,H,values,0,W);ImageIO.write(image,"png",file);}
    private static void clear(){boolean enabled=glIsEnabled(GL_SCISSOR_TEST);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);if(enabled)glEnable(GL_SCISSOR_TEST);}
    private static void coordinates(){
        for(int height:new int[]{1,181,2161})for(int y=0;y<height;y++)if(FramebufferCoordinates.mouseY(height,height-y-1)!=y)throw new AssertionError("raw Mouse Y丢原生像素");
        float[] m=FramebufferCoordinates.orthographic(W,H);float left=m[12],top=m[13],right=m[0]*W+m[12],bottom=m[5]*H+m[13];
        if(Math.abs(left+1)>0.00001||Math.abs(top-1)>0.00001||Math.abs(right-1)>0.00001||Math.abs(bottom+1)>0.00001)throw new AssertionError("正交映射不是framebuffer");
        LayoutBox clip=FramebufferCoordinates.viewportClip(W,H,true,new int[]{-2,H-8,7,10});
        if(clip.x!=0||clip.y!=0||clip.width!=5||clip.height!=8)throw new AssertionError("scissor交错");
        if(FramebufferCoordinates.viewportClip(W,H,true,new int[]{Integer.MAX_VALUE,Integer.MIN_VALUE,30,40}).width!=0)throw new AssertionError("clip溢出");
    }
    private GlfwTestEnvironment environment;
    private ClientFontServices owner;
    private GenerationFonts initial;
    private TextStyle style;
    private GlyphKey old;
    private Host host;
    private final int[] clicked={0};
    private ReactiveDomain domain;
    private SceneRuntime runtime;
    private ScenePageSession session;
    private SceneNode[] nodes;
    private SceneFramePipeline pipeline;
    private final ScenePresentation[] presentation={null};
    private PaintPlan white;
    private int lit,frames;
    private long cached;
    private File output;

    @Override protected void setup() throws Exception {
        // 无GL构造/度量先执行；宿主的释放位先登记，实际窗口随后按需创建。
        onClose(()->{if(environment!=null)environment.close();});
        initial=new GenerationFonts();
        owner=new ClientFontServices(initial,1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),new Lwjgl3GlApi(),GlProgramProvider.NONE);
        onClose(()->{owner.close();require(owner.awaitRasterizerClose(10000),"退出worker未清理");});
    }
    @Override protected void runTests() throws Exception {
        String[] args=arguments();
        output=new File(args.length==0?"build/font-gl-probe/client-output":args[0]);require(output.isDirectory()||output.mkdirs(),"不能建输出目录");
        scenario("原生像素坐标换算",ClientFontServicesScenario::coordinates);
        scenario("无context度量与组件文本policy",this::lazyMetrics);
        environment=new GlfwTestEnvironment();
        environment.window(W,H,"client shared fonts",Profile.CORE,2,false,false);
        initializeScene();
        scenario("单像素scissor与宿主恢复",this::onePixel);
        scenario("ready通知无需重布局",this::readyWithoutLayout);
        scenario("焦点响应绑定经真实GPU像素观察",this::focusReplay);
        scenario("范围进入与返回经真实GPU回放",this::scopeReplay);
        scenario("Tab历史适配与导航高亮经真实GPU观察",this::navigationReplay);
        scenario("ESC消费关闭及焦点恢复经真实GPU观察",this::escapeReplay);
        scenario("标准控件浅深主题与限高全文提示真实像素",this::standardControls);
        scenario("点击滚动与动态resize",this::inputAndResize);
        scenario("字体来源退休与旧glyph重开",this::reloadSources);
        scenario("screen关闭保留sharedowner",this::screenOwnership);
        scenario("应用退出与幂等owner关闭",this::closeOwner);
    }
    private void lazyMetrics() throws Exception {
        if(owner.hasGlResources()||owner.rasterizer().stats().started)throw new AssertionError("init启动GL/worker");
        style=new TextStyle("default",20,0xfff0d050);TextLayout warm=owner.fonts().layout("中文 e\u0301 👩🏽‍💻",style,W);
        if(owner.hasGlResources()||owner.rasterizer().stats().started)throw new AssertionError("measure启动GL/worker");
        old=GlyphKey.of(warm.lines().get(0).glyphs().get(0));
        TextLayout normal=owner.layout("中文 中文 中文",style,40,TextPolicy.NORMAL);
        TextLayout single=owner.layout("中文 中文 中文",style,40,TextPolicy.SINGLE_LINE);
        if(normal.lines().size()<=1||single.lines().size()!=1)throw new AssertionError("共享facade丢失组件4argpolicy");
    }
    private void initializeScene() throws Exception {
            // owner首次GLconsumer自行创建caps；此前构造和真实measure均无context。
            host=new Host(owner);domain=ReactiveDomain.direct();
            nodes=new SceneNode[3];
            session=ScenePageSession.open(domain,host,owner,ctx->{
                runtime=ctx.runtime();
                ctx.own(host.screen);
        nodes[0]=new SceneNode().size(Size.fill(1),Size.fill(1)).background(0xff182030);
        nodes[1]=new SceneNode().text("中文 click e\u0301 👩🏽‍💻").style(style).size(Size.fill(1),Size.natural()).padding(4).onClick(()->clicked[0]++);
        nodes[1].focusable(true).bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(runtime.focusRouter().focused(),
            (node,target)->node.background(target==node?0xff28b060:0)));
        nodes[2]=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).scrollStep(32);
        for(int i=0;i<30;i++)nodes[2].add(new SceneNode().text("行 "+i+" 原生像素").style(style).size(Size.fill(1),Size.natural()));
        nodes[0].add(nodes[1]).add(nodes[2]);ctx.mount(nodes[0]);
            });
            pipeline=session.pipeline();
            onClose(session::close);
            owner.atlas();System.out.println("vendor="+glGetString(GL_VENDOR)+" renderer="+glGetString(GL_RENDERER)+" version="+glGetString(GL_VERSION)+" Java="+System.getProperty("java.version"));
            int texture=glGenTextures(),fbo=glGenFramebuffers();
            onClose(()->{glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(texture);});
        glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO失败");
        glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);glViewport(7,9,80,60);
        glEnable(GL_SCISSOR_TEST);glScissor(3,H-70,230,65);
    }
    private void onePixel() throws Exception {
        int[] viewport={7,9,80,60},beforeClip={3,H-70,230,65};domain.execute(()->presentation[0]=pipeline.current());
        if(presentation[0].geometry.get(nodes[0]).box.width!=W||presentation[0].geometry.get(nodes[0]).box.height!=H)throw new AssertionError("布局使用scaledGUI尺寸");
        white=new PaintPlanBuilder().add(PaintCommand.rect(new LayoutBox(0,0,W,H),new LayoutBox(4,0,1,H),-1)).build();
        clear();host.screen.replay(white,W,H);int[] pixels=read();lit=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){boolean expected=x==4&&y>=5&&y<70;if((pixels[y*W+x]!=0)!=expected)throw new AssertionError("scissor/framebuffer onePixel不符 x="+x+" y="+y);if(pixels[y*W+x]!=0)lit++;}
        int[] actualViewport=new int[4],actualClip=new int[4];glGetIntegerv(GL_VIEWPORT,actualViewport);glGetIntegerv(GL_SCISSOR_BOX,actualClip);
        if(!Arrays.equals(viewport,actualViewport)||!Arrays.equals(beforeClip,actualClip)||!glIsEnabled(GL_SCISSOR_TEST))throw new AssertionError("宿主viewport/scissor恢复失败");
    }
    private void readyWithoutLayout() throws Exception {
        glDisable(GL_SCISSOR_TEST);long end=System.nanoTime()+15000000000L;frames=0;
        for(;;){clear();session.frame();domain.execute(()->{if(pipeline.current()!=presentation[0])throw new AssertionError("ready导致重布局");});
            if(owner.rasterizer().stats().pending==0&&owner.rasterizer().stats().rasterized>0&&owner.atlas().stats().residents>0){session.frame();break;}
            if(System.nanoTime()>end)throw new AssertionError("shared字形未ready");Thread.sleep(2);frames++;
        }
        if(owner.readyVersion()==0)throw new AssertionError("宿主未显式pump ready");
        image(new File(output,"scene-native.png"),read());
    }
    private void focusReplay() throws Exception {
        domain.execute(()->require(runtime.focusRouter().request(runtime.captureMount(nodes[1])),"GPU焦点请求"));
        clear();session.frame();int[] focused=read();
        require(focused[W+1]==0xff28b060,"焦点绑定未进入真实FBO像素");
        domain.execute(()->runtime.focusRouter().clear());clear();session.frame();int[] cleared=read();
        require(cleared[W+1]==0xff182030,"清除焦点未恢复背景");
        require(clicked[0]==0,"焦点请求不能激活业务");
    }
    private void scopeReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] layer={null};
        domain.execute(()->{
            runtime.focusRouter().request(runtime.captureMount(nodes[1]));
            SceneNode target=new SceneNode().focusable(true).size(Size.fixed(3),Size.fixed(3));
            SceneNode overlay=new SceneNode().modal(true).initialFocus(target).size(Size.fill(1),Size.fill(1))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(runtime.focusRouter().focused(),(node,focus)->node.background(focus==target?0xff4028b0:0xff404040))).add(target);
            layer[0]=runtime.attach(null,overlay);
        });
        try {
            clear();session.frame();require(read()[W+1]==0xff4028b0,"模态初始选择未进入FBO");
        } finally { domain.execute(()->layer[0].detach()); }
        clear();session.frame();require(read()[W+1]==0xff28b060,"返回原焦点未恢复实际像素");
        domain.execute(()->runtime.focusRouter().clear());clear();session.frame();
    }
    private void navigationReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] second={null};
        domain.execute(()->{
            runtime.focusRouter().request(runtime.captureMount(nodes[1]));
            second[0]=runtime.attach(null,new SceneNode().focusable(true).size(Size.fixed(1),Size.fixed(1)));
        });
        club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter keys=new club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter(session::navigation);
        keys.availability(true);keys.historyDrained(false,false,false);
        try {
            keys.tab(true,false);clear();session.frame();require(read()[W+1]==0xff182030,"Tab离开原目标后实际高亮未消失");
            keys.shift(true,true);keys.tab(true,true);clear();session.frame();require(read()[W+1]==0xff28b060,"Shift+Tab历史repeat未恢复高亮");
            keys.availability(false);keys.availability(true);keys.tab(true,true);clear();session.frame();require(read()[W+1]==0xff28b060,"失焦尾部repeat不能导航");
        } finally { domain.execute(()->{second[0].detach();runtime.focusRouter().clear();}); }
        clear();session.frame();
    }
    private void escapeReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] layer={null};
        final club.heiqi.uilib.ui.reactive.Signal<Boolean>[] visible=new club.heiqi.uilib.ui.reactive.Signal[1];
        final int[] fallback={0};
        domain.execute(()->{
            visible[0]=domain.signal(true);runtime.focusRouter().request(runtime.captureMount(nodes[1]));
            SceneNode target=new SceneNode().focusable(true).size(Size.fixed(3),Size.fixed(3));
            SceneNode overlay=new SceneNode().modal(true).initialFocus(target).size(Size.fill(1),Size.fill(1))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(visible[0],SceneNode::visible))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(runtime.focusRouter().focused(),(node,focus)->node.background(focus==target?0xff4028b0:0xff404040)))
                .onEscape(()->{visible[0].set(false);return true;}).add(target);
            layer[0]=runtime.attach(null,overlay);
        });
        club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter keys=new club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter(()->session.escape(()->fallback[0]++));
        keys.availability(true);keys.historyDrained(false);
        try {
            clear();session.frame();require(read()[W+1]==0xff4028b0,"ESC前模态高亮");
            keys.key(true,false);clear();session.frame();require(read()[W+1]==0xff28b060 && fallback[0]==0,"消费ESC应恢复原焦点而不fallback");
            keys.key(true,true);clear();session.frame();require(fallback[0]==0,"repeat不能关闭页面");
            keys.key(false,false);keys.key(true,false);clear();session.frame();require(fallback[0]==1,"新的未消费ESC才fallback");
        } finally {domain.execute(()->{layer[0].detach();runtime.focusRouter().clear();});}
        clear();session.frame();
    }
    private void standardControls() throws Exception {
        ReactiveDomain local=ReactiveDomain.direct();
        club.heiqi.uilib.ui.reactive.Signal<club.heiqi.uilib.ui.scene.control.SceneTheme>[] theme=new club.heiqi.uilib.ui.reactive.Signal[1];
        SceneNode[] button={null}; final String caption="标准按钮长文案：浅色深色切换、完整行省略与全文提示";
        ScenePageSession page=ScenePageSession.open(local,host,owner,ctx -> {
            theme[0]=local.signal(club.heiqi.uilib.ui.scene.control.SceneTheme.dark());
            button[0]=club.heiqi.uilib.ui.scene.control.SceneButton.create(ctx,()->caption,theme[0],()->{})
                .size(Size.fixed(145),Size.fixed(44));ctx.mount(button[0]);
        });
        try {
            clear();page.frame();int dark=read()[10*W+10];
            local.execute(()->{page.runtime().focusRouter().clear();theme[0].set(club.heiqi.uilib.ui.scene.control.SceneTheme.light());});
            clear();page.frame();int light=read()[10*W+10];require(dark!=light,"主题切换未改变实际GL颜色");
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(20,20));
            clear();page.frame();final ScenePresentation[] rendered={null};local.execute(()->rendered[0]=page.pipeline().current());
            require(rendered[0].paint.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT && c.text.originalText.equals(caption)
                && c.box.y>=44),"完整文案提示未进入实际生产绘制计划");
            local.execute(()->{TextLayout displayed=rendered[0].geometry.get(button[0]).text;
                require(displayed.lines().stream().anyMatch(l->l.glyphs().stream().anyMatch(g->g.sourceStart==g.sourceEnd && g.logicalStart==g.logicalEnd)),"真实字体限高缺少合成省略glyph");
                require(displayed.originalText.equals(caption),"省略修改了完整源文案");});
            page.frame();if(glGetError()!=GL_NO_ERROR)throw new AssertionError("标准控件GL error");
        } finally {page.close();}
    }
    private void inputAndResize() throws Exception {
        int rawY=FramebufferCoordinates.mouseY(H,H-6);
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0,5,rawY));session.frame();
        if(clicked[0]!=0)throw new AssertionError("raw像素DOWN不应激活");
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0,5,rawY));session.frame();
        if(clicked[0]!=1)throw new AssertionError("raw像素UP未命中原owner");
        domain.execute(()->presentation[0]=pipeline.current());LayoutBox scroll=presentation[0].geometry.get(nodes[2]).box;
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(scroll.x+5,FramebufferCoordinates.mouseY(H,H-(scroll.y+5)-1),-1));session.frame();
        domain.execute(()->{if(pipeline.current().geometry.get(nodes[2]).scrollOffset!=32)throw new AssertionError("scroll非声明32原生px");});
        host.width=333;host.height=199;domain.execute(()->{LayoutBox box=pipeline.current().geometry.get(nodes[0]).box;if(box.width!=333||box.height!=199)throw new AssertionError("resize不是动态framebuffer布局");});
    }
    private void reloadSources() throws Exception {
        long generation=owner.fonts().generation();owner.reload(new GenerationFonts(),2);initial.retired=true;
        domain.execute(()->{if(pipeline.current().paint==presentation[0].paint)throw new AssertionError("reload未失效布局");});
        if(owner.fonts().generation()<=generation)throw new AssertionError("reload代际未变化");
        GlyphKey oldUncached=new GlyphKey(old.source,26,old.glyphId);owner.rasterizer().request(oldUncached);long end=System.nanoTime()+10000000000L;
        while(owner.rasterizer().peek(oldUncached)==null){owner.pumpReady();if(System.nanoTime()>end||!owner.rasterizer().failures().isEmpty())throw new AssertionError("退休包旧glyph不可重开");Thread.sleep(2);}
    }
    private void screenOwnership() throws Exception {
        cached=owner.rasterizer().stats().bitmapBytes;host.screen.close();
        if(owner.rasterizer().stats().closed||owner.rasterizer().stats().bitmapBytes!=cached||owner.fonts().layout("仍可用",style,100).lines().isEmpty())throw new AssertionError("screen关闭释放sharedowner");
        try(FramebufferSceneRenderer reopened=new FramebufferSceneRenderer(owner)){clear();reopened.replay(white,W,H);}
        GlPaintPlanRenderer shared=owner.sharedPlanRenderer();try(GlPaintPlanRenderer.Prepared prepared=shared.prepare(white,new LayoutBox(0,0,W,H),owner.settings().upload)){prepared.render(FramebufferCoordinates.orthographic(W,H),H,false);}
    }
    private void closeOwner() throws Exception {
        session.close();owner.close();owner.close();host.screen.close();if(!owner.awaitRasterizerClose(10000))throw new AssertionError("退出worker未清理");
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("GL error");
        System.out.println("CLIENT_FONT_SERVICES_SCENARIO_PASS native="+W+"x"+H+" onePixelScissorLit="+lit+" readyFrames="+frames+" screenCloseSharedBytes="+cached+" click=1 scroll=32 resize=333x199 reloadOldSource=true lazyGL=true readyNoRelayout=true viewportRestore=true workerClose=true");
    }
    public static void main(String[] args) throws Exception { new ClientFontServicesScenario().run(args); }
}
