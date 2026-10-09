package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.RasterSampling;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Collections;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL32C.*;
/** 原版意图→共享字体→正式owner→标准GL上下文；图片只读回实际GPU输出。 */
public final class FontRealStyleGlScenario extends TestApplication {
 private static final int W=640,H=256;private static final LayoutBox VIEW=new LayoutBox(0,0,W,H);
 @Override protected void runTests()throws Exception {
  GlfwTestEnvironment environment=own(new GlfwTestEnvironment());own(environment.window(W,H,"real font style",GlfwTestEnvironment.Profile.CORE,2));
  ClientFontServices owner=new ClientFontServices(getClass().getClassLoader(),1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),new Lwjgl3GlApi(),GlProgramProvider.NONE);
  onClose(()->{owner.close();require(owner.awaitRasterizerClose(10000),"GL字体worker关闭失败");});
  Path path=Paths.get(System.getenv("WINDIR"),"Fonts","msyh.ttc");owner.reload(Collections.singletonList(FontRealStyleScenario.family(path,1)));
  VanillaTextService vanilla=new VanillaTextService(owner.fonts());VanillaTextService.Settings settings=new VanillaTextService.Settings(9,9,9,false,false,new int[32]);
  PaintPlanBuilder builder=new PaintPlanBuilder().add(PaintCommand.rect(VIEW,null,0xff182030));String[] raws={"Regular  世界 AiW office","§lBold     世界 AiW office","§oItalic   世界 AiW office","§l§oBoldItalic 世界 AiW office","世§l世§r世   §nunderline§r §mstrike"};
  for(int i=0;i<raws.length;i++){TextLayout layout=vanilla.sample(raws[i],settings,-1,1000,TextPolicy.SINGLE_LINE,new RasterSampling(3,1),0,0);for(TextLayout.Glyph g:FontRealStyleScenario.glyphs(layout))require(g.rasterStyle.normal(),"GL真实face被合成");
   // 原版caller绘制原点上移2保持不变；投影三倍只便于观察实际采样像素。
   builder.add(PaintCommand.text(new LayoutBox(8,6+i*15-2,200,12),null,layout));
  }PaintPlan plan=builder.build();
  float[] p={6f/W,0,0,0,0,-6f/H,0,0,0,0,1,0,-1,1,0,1},mv={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
  FontRenderContext context=new FontRenderContext(new FontProjection(p,mv,new int[]{0,0,W,H}),0,H,false,GL_LESS,false,false,new int[]{0,0,W,H},FontRenderContext.MatrixState.ui(p,mv),GL.getCapabilities(),1,0);
  GlPaintPlanRenderer renderer=owner.sharedPlanRenderer();
  scenario("微软雅黑原版真实样式GPU出图",()->{
   glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);int ready=0,demand=1,frames=0;long deadline=System.nanoTime()+15000000000L;
   while(ready<demand){require(System.nanoTime()<deadline,"GL真实样式等待超时");glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);owner.beginPresentation(++frames);
    try(GlPaintPlanRenderer.Prepared prepared=owner.prepare(renderer,plan,context)){ready=prepared.readyCount();demand=prepared.demandCount();prepared.render(context);}finally{owner.endPresentation();}Thread.yield();}
   ByteBuffer bytes=MemoryUtil.memAlloc(W*H*4);try{glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);int ink=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4,r=bytes.get(i)&255,g=bytes.get(i+1)&255,b=bytes.get(i+2)&255,a=bytes.get(i+3)&255;image.setRGB(x,y,a<<24|r<<16|g<<8|b);if(r>120&&g>120&&b>120)ink++;}require(ink>0,"真实样式GL全空白");Path output=Paths.get(arguments()[0]);Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());System.out.println("REAL_STYLE_GL renderer="+glGetString(GL_RENDERER)+" ready="+ready+" demand="+demand+" frames="+frames+" ink="+ink+" image="+output);}finally{MemoryUtil.memFree(bytes);}require(glGetError()==GL_NO_ERROR,"真实样式GL错误");
  });
 }
 public static void main(String[] args)throws Exception{new FontRealStyleGlScenario().run(args);}
}
