package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.mail.MailboxWorkload;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/** 正式字体和页面，核心处理计时不包含GPU回放。 */
public final class MailboxPerformanceScenario extends TestApplication {
    private FontTextService fonts;
    @Override protected void setup() throws Exception { fonts=own(new FontTextService(new LwjglFontEngine())); }
    @Override protected void runTests() throws Exception {
        int scale=Integer.getInteger("performance.scale",200),warmup=Integer.getInteger("performance.warmup",30),frames=Integer.getInteger("performance.frames",120);
        java.nio.file.Path output=Paths.get(System.getProperty("performance.output","build/reports/mailbox-core"));Files.createDirectories(output);
        MailboxWorkload page=new MailboxWorkload(scale);ReactiveDomain domain=ReactiveDomain.direct();
        final int[] size={1100,800},sequence={0};
        SceneHost host=new SceneHost(){public int width(){return size[0];}public int height(){return size[1];}
            public long metricsRevision(){return fonts.generation();}public void replay(PaintPlan plan){require(!plan.commands().isEmpty(),"收件箱绘制为空");}};
        try(ScenePageSession session=ScenePageSession.open(domain,host,fonts,page)){
            scenario("完整消息行计时",()->page.run(new MailboxWorkload.Driver(){
                public void frame(){session.frame();}public void scroll(int x,int y,int delta){session.pointer(ScenePointerEvent.scroll(x,y,delta));}
                public void resize(int w,int h){size[0]=w;size[1]=h;}public int width(){return size[0];}public int height(){return size[1];}
                public SceneGeometryView geometry(){return session.pipeline().current().geometry;}
            },"core-actions+session.frame",System.getProperty("uilib.version","unknown"),warmup,frames,json->{
                try{Files.write(output.resolve("core-"+scale+"-"+(sequence[0]++)+".json"),json.getBytes(StandardCharsets.UTF_8));}
                catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            }));
        }
    }
    public static void main(String[] args)throws Exception{new MailboxPerformanceScenario().run(args);}
}
