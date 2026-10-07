package club.heiqi.uilib.host.client.font.context;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.RasterSampling;

/** 一次caller提交的不可变上下文，不拥有GL对象；只可在同context/pass内回放。
 * revision来自字体/采样策略/target代际，不使用每帧camera计数增加GlyphKey。
 * image/icon资源代际由其自己的key负责，不能用这份context替代资源失效合同。 */
public final class FontRenderContext {
    public final FontProjection projection;
    public final int drawFramebuffer, sourceFramebufferHeight, depthFunction, passKey;
    public final boolean depthTest, depthWrite, scissorEnabled;
    public final long revision;
    public final Object contextIdentity;
    public final MatrixState state;
    private final int[] scissor;
    public FontRenderContext(FontProjection projection,int framebuffer,int height,boolean depthTest,int depthFunction,
            boolean depthWrite,boolean scissorEnabled,int[] scissor,MatrixState state,Object identity,long revision,int passKey) {
        this.projection=Objects.requireNonNull(projection,"projection");
        if(height<=0||scissor==null||scissor.length!=4||scissor[2]<0||scissor[3]<0)throw new IllegalArgumentException("无效target/scissor");
        drawFramebuffer=framebuffer;sourceFramebufferHeight=height;this.depthTest=depthTest;this.depthFunction=depthFunction;
        this.depthWrite=depthWrite;this.scissorEnabled=scissorEnabled;this.scissor=scissor.clone();this.state=Objects.requireNonNull(state,"state");
        contextIdentity=Objects.requireNonNull(identity,"contextIdentity");this.revision=revision;this.passKey=passKey;
    }
    public int[] scissor() { return scissor.clone(); }
    public RasterSampling samplingAt(double x,double y,double logicalEm,RasterPolicy policy) { return projection.samplingAt(x,y,logicalEm,policy,revision); }
    /** effective固定管线/可选宿主状态；所有向量和矩阵均复制，绝不保留JOML stack引用。 */
    public static final class MatrixState {
        private final float[] p,mv,color,normal,lighting;
        public final boolean alphaTest,lightmapActive;
        public final int alphaFunction,lightmapTexture,packedLight,blockEntityId;
        public final float alphaReference,lightmapU,lightmapV;
        public MatrixState(float[] p,float[] mv,float[] color,boolean alphaTest,int alphaFunction,float alphaReference,
                boolean lightmapActive,int lightmapTexture,float lightmapU,float lightmapV,int packedLight,
                float[] normal,float[] lighting,int blockEntityId) {
            this.p=copy(p,16);this.mv=copy(mv,16);this.color=copy(color,4);this.normal=copy(normal,3);this.lighting=copy(lighting,3);
            if(!Float.isFinite(alphaReference)||!Float.isFinite(lightmapU)||!Float.isFinite(lightmapV)||lightmapTexture<0)throw new IllegalArgumentException("无效alpha/lightmap");
            this.alphaTest=alphaTest;this.alphaFunction=alphaFunction;this.alphaReference=alphaReference;this.lightmapActive=lightmapActive;
            this.lightmapTexture=lightmapTexture;this.lightmapU=lightmapU;this.lightmapV=lightmapV;this.packedLight=packedLight;this.blockEntityId=blockEntityId;
        }
        private static float[] copy(float[] v,int size) { if(v==null||v.length!=size)throw new IllegalArgumentException("无效向量长度");float[] c=v.clone();for(float x:c)if(!Float.isFinite(x))throw new IllegalArgumentException("非有限状态");return c; }
        public float[] projection(){return p.clone();}public float[] modelView(){return mv.clone();}
        public float[] color(){return color.clone();}public float[] normal(){return normal.clone();}public float[] lighting(){return lighting.clone();}
        /** own scene/core显式提供P/MV；没有world lightmap/alpha fixed-state的默认UI上下文。 */
        public static MatrixState ui(float[] p,float[] mv) {return new MatrixState(p,mv,new float[]{1,1,1,1},false,0x0204,0,false,0,0,0,0,new float[]{0,0,1},new float[]{1,1,1},-1);}
    }
}
