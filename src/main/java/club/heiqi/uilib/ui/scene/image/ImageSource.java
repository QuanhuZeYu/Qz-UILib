package club.heiqi.uilib.ui.scene.image;
import java.net.URI;
import java.util.Objects;
/** 业务来源描述；MC物品由专用宿主子类提供快照，不转为位图。 */
public class ImageSource {
    public enum Kind { RESOURCE, BITMAP, HTTP, ITEM, INVALID }
    public final Kind kind;
    public final String location;
    public final ImageContent bitmap;
    public final Region region;
    public static final class Region {
        public final int x,y,width,height;
        public Region(int x,int y,int width,int height){if(x<0||y<0||width<1||height<1)throw new IllegalArgumentException("无效图像区域");this.x=x;this.y=y;this.width=width;this.height=height;}
        @Override public boolean equals(Object o){if(!(o instanceof Region))return false;Region r=(Region)o;return x==r.x&&y==r.y&&width==r.width&&height==r.height;}
        @Override public int hashCode(){return Objects.hash(x,y,width,height);}
    }
    protected ImageSource(Kind kind,String location,ImageContent bitmap,Region region){this.kind=Objects.requireNonNull(kind,"kind");this.location=Objects.requireNonNull(location,"location");this.bitmap=bitmap;this.region=region;}
    public static ImageSource resource(String id){return resource(id,null);}
    public static ImageSource resource(String id,Region region){if(id==null||id.trim().isEmpty())throw new IllegalArgumentException("资源标识为空");return new ImageSource(Kind.RESOURCE,id,null,region);}
    public static ImageSource bitmap(ImageContent content){return new ImageSource(Kind.BITMAP,"runtime:"+Objects.requireNonNull(content,"content").identity,content,null);}
    public static ImageSource http(String address){URI uri=URI.create(address);if(!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null)throw new IllegalArgumentException("仅支持HTTP/HTTPS图像地址");return new ImageSource(Kind.HTTP,uri.toString(),null,null);}
    /** 文档图片的正式来源解析；item URI交给宿主解析器，不在核心解析ItemStack。 */
    public static ImageSource parse(String uri,java.util.function.Function<String,ImageSource> items){
        try{Objects.requireNonNull(uri,"uri");String scheme=URI.create(uri).getScheme();if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))return http(uri);if("resource".equals(scheme))return resource(uri.substring(9));if("item".equals(scheme))return items==null?new ImageSource(Kind.ITEM,uri,null,null):Objects.requireNonNull(items.apply(uri),"item source");throw new IllegalArgumentException("未知图像URI: "+uri);}
        catch(IllegalArgumentException|NullPointerException invalid){return new ImageSource(Kind.INVALID,String.valueOf(uri)+" — "+invalid.getMessage(),null,null);}
    }
    @Override public boolean equals(Object o){if(o==null||o.getClass()!=getClass())return false;ImageSource s=(ImageSource)o;return kind==s.kind&&location.equals(s.location)&&bitmap==s.bitmap&&Objects.equals(region,s.region);}
    @Override public int hashCode(){return Objects.hash(kind,location,bitmap,region);}
    @Override public String toString(){return kind+":"+location;}
}
