package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 页面拥有的有界读取器；仅返回纯值，正式inputFrame才接收结果。 */
final class GalleryMediaResolver implements AutoCloseable {
    static final class Completion {
        final MediaResolver.Result result;final Exception failure;
        Completion(MediaResolver.Result result,Exception failure){this.result=result;this.failure=failure;}
    }
    private static final class Token implements MediaCancellation {
        volatile boolean ended;
        public boolean cancelled(){return ended||Thread.currentThread().isInterrupted();}
    }
    private final MediaResolver resolver;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),task->{Thread t=new Thread(task,"uilib-media-resolver");t.setDaemon(true);return t;});
    private Token token;
    private Future<?> latest;
    private Completion completion;
    private boolean closed;
    private long serial;
    GalleryMediaResolver(MediaResolver resolver){this.resolver=resolver;}
    synchronized void request(MediaResolver.Request request){
        cancel();if(closed)throw new IllegalStateException("媒体解析器已关闭");
        long selected=serial;Token current=new Token();token=current;
        latest=worker.submit(()->{
            try{MediaResolver.Result result=resolver.resolve(request,current);current.check();publish(selected,new Completion(result,null));}
            catch(CancellationException cancelled){/* 旧请求不发布结果。 */}
            catch(Exception failure){if(!current.cancelled())publish(selected,new Completion(null,failure));}
        });
    }
    private synchronized void publish(long selected,Completion value){if(!closed&&serial==selected)completion=value;}
    synchronized Completion take(){Completion result=completion;completion=null;return result;}
    synchronized void cancel(){serial++;if(token!=null)token.ended=true;if(latest!=null)latest.cancel(true);worker.getQueue().clear();completion=null;latest=null;token=null;}
    @Override public synchronized void close(){if(closed)return;closed=true;cancel();worker.shutdownNow();}
}
