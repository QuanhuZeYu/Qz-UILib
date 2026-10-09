package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import static club.heiqi.uilib.host.client.gl.GlTokens.*;
import club.heiqi.uilib.host.client.gl.GlApi;

/** 自有screen的即时framebuffer consumer；只拥有这份quad renderer，关闭不释放共享字体/cache。
 * 不读取固定矩阵/GUIscale。外部scissor在prepare与replay前转换并交入viewport，保证需求与落屏一致。
 */
public final class FramebufferSceneRenderer implements AutoCloseable {
    private final ClientFontServices owner;
    private GlPaintPlanRenderer renderer;
    private boolean closed;
    private final club.heiqi.uilib.host.client.image.ClientImageServices images;
    public FramebufferSceneRenderer(ClientFontServices owner){this(owner,null);}
    public FramebufferSceneRenderer(ClientFontServices owner,club.heiqi.uilib.host.client.image.ClientImageServices images){this.owner=java.util.Objects.requireNonNull(owner,"owner");this.images=images;}
    public void replay(PaintPlan plan,int width,int height) {
        if(closed)throw new IllegalStateException("screen renderer已关闭");owner.pumpReady();
        if(width<=0||height<=0)return;
        if(renderer==null)renderer=owner.createPlanRenderer(images==null?null:images.textures());
        GlApi gl=owner.gl();int[] scissor=new int[4],viewport=new int[4];gl.glGetIntegerv(GL_SCISSOR_BOX,scissor);gl.glGetIntegerv(GL_VIEWPORT,viewport);
        LayoutBox visible=FramebufferCoordinates.viewportClip(width,height,gl.glIsEnabled(GL_SCISSOR_TEST),scissor);
        try(GlPaintPlanRenderer.Prepared prepared=owner.prepare(renderer,plan,visible)) {
            // framebuffer正交坐标与viewport配套；保护宿主实际viewport，固定矩阵保持原样。
            gl.glViewport(0,0,width,height);
            try{prepared.render(FramebufferCoordinates.orthographic(width,height),height,false);}
            finally{gl.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);}
        }
    }
    @Override public void close(){if(closed)return;if(renderer!=null)owner.releasePlanRenderer(renderer);closed=true;}
}
