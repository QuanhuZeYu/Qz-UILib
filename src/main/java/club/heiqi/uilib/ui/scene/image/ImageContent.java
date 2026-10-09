package club.heiqi.uilib.ui.scene.image;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
/** 实际不可变内容；straight RGBA8，像素以左上为原点。身份只标识此份内容，不是宿主纹理。 */
public class ImageContent {
    public enum Kind { PIXELS, HOST_ITEM }
    public final Kind kind;
    private static final AtomicLong IDS = new AtomicLong();
    public final long identity = IDS.incrementAndGet();
    public final int width, height;
    private final byte[] rgba;
    public ImageContent(int width, int height, byte[] rgba) {
        if(width<1 || height<1 || (long)width*height*4>Integer.MAX_VALUE) throw new IllegalArgumentException("无效图像尺寸");
        if(Objects.requireNonNull(rgba,"rgba").length!=(long)width*height*4) throw new IllegalArgumentException("RGBA字节数不符");
        this.kind=Kind.PIXELS;this.width=width;this.height=height;this.rgba=rgba.clone();
    }
    /** 宿主物品内容持不可变专用快照；core只知道自然尺寸与身份，不读MC数据。子类必须保持不可变。 */
    protected ImageContent(int width,int height){if(width<1||height<1)throw new IllegalArgumentException("无效宿主图像尺寸");this.kind=Kind.HOST_ITEM;this.width=width;this.height=height;this.rgba=null;}
    public ItemImageFacts itemFacts(){return null;}
    public long bytes(){return rgba==null?0:rgba.length;}
    public byte[] pixels(){if(rgba==null)throw new UnsupportedOperationException("宿主物品不是位图");return rgba.clone();}
}
