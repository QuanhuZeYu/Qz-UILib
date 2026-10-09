package club.heiqi.uilib.ui.scene.image;
/** 状态带来源与处理阶段；失败/不支持不会伪装为零尺寸成功。 */
public final class ImageState {
    public enum Status { LOADING, READY, FAILED, UNSUPPORTED }
    public final Status status;
    public final ImageSource source;
    public final long request;
    public final ImageContent content;
    public final String stage,diagnostic;
    private ImageState(Status status,ImageSource source,long request,ImageContent content,String stage,String diagnostic){this.status=status;this.source=source;this.request=request;this.content=content;this.stage=stage;this.diagnostic=diagnostic;}
    public static ImageState loading(ImageSource s,long request){return new ImageState(Status.LOADING,s,request,null,"load","");}
    public static ImageState uploading(ImageState current){return new ImageState(Status.LOADING,current.source,current.request,current.content,"upload","");}
    public static ImageState ready(ImageSource s,long request,ImageContent content){return new ImageState(Status.READY,s,request,java.util.Objects.requireNonNull(content,"content"),"ready","");}
    public static ImageState failure(ImageSource s,long request,boolean unsupported,String stage,String message){return new ImageState(unsupported?Status.UNSUPPORTED:Status.FAILED,s,request,null,stage,message);}
}
