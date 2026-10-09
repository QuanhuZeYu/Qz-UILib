package club.heiqi.uilib.ui.scene.image;
import java.util.concurrent.Callable;
/** 宿主线程prepare取得安全资源快照；返回工作在有界worker运行，不能访问节点/GL/ResourceManager。 */
public interface ImageLoader {
    Callable<ImageContent> prepare(ImageSource source) throws Exception;
    /** 可取消的宿主资源快照；排队期间取消也必须释放stream。 */
    interface Task extends Callable<ImageContent>, AutoCloseable { void close() throws Exception; }
    final class Failure extends Exception {
        public final boolean unsupported;public final String stage;
        public Failure(boolean unsupported,String stage,String message){super(message);this.unsupported=unsupported;this.stage=stage;}
        public Failure(String stage,Throwable cause){super(cause.toString(),cause);this.unsupported=false;this.stage=stage;}
    }
}
