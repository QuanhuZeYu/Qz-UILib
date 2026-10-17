package club.heiqi.uilib.testing.media;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import club.heiqi.uilib.ui.media.MediaSource;

/** 真实HTTP/Range媒体，要求调用方声明的头；供native与Desktop共享，不连接公网。 */
public final class MediaNetworkFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService worker;
    public final AtomicInteger accepted=new AtomicInteger(),rejected=new AtomicInteger();
    public MediaNetworkFixture()throws Exception{
        byte[] video=MediaLiveFixture.video(96,64),audio=MediaLiveFixture.audio();
        byte[] shortAudio=java.util.Arrays.copyOf(audio,44+22050*2);
        ByteBuffer.wrap(shortAudio).order(ByteOrder.LITTLE_ENDIAN).putInt(4,shortAudio.length-8).putInt(40,shortAudio.length-44);
        byte[] segment;
        try(InputStream input=getClass().getResourceAsStream("/uilib-media/segment.ts");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);segment=bytes.toByteArray();
        }
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        worker=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"uilib-network-fixture");t.setDaemon(true);return t;});server.setExecutor(worker);
        server.createContext("/",exchange->{
            try{
                if(!"https://fixture.example/watch".equals(exchange.getRequestHeaders().getFirst("Referer"))
                        ||!"UILib-Network-Verification".equals(exchange.getRequestHeaders().getFirst("User-Agent"))){rejected.incrementAndGet();exchange.sendResponseHeaders(403,-1);return;}
                accepted.incrementAndGet();String path=exchange.getRequestURI().getPath();byte[] data;
                if(path.equals("/video.avi"))data=video;else if(path.equals("/audio.wav"))data=audio;else if(path.equals("/short.wav"))data=shortAudio;
                else if(path.equals("/segment.ts"))data=segment;
                else if(path.equals("/vod.m3u8"))data="#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nsegment.ts\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8);
                else{exchange.sendResponseHeaders(404,-1);return;}
                exchange.getResponseHeaders().set("Accept-Ranges","bytes");int start=0,end=data.length-1,status=200;
                String range=exchange.getRequestHeaders().getFirst("Range");
                if(range!=null&&range.startsWith("bytes=")){
                    String[] parts=range.substring(6).split("-",-1);start=Integer.parseInt(parts[0]);if(parts.length>1&&!parts[1].isEmpty())end=Math.min(end,Integer.parseInt(parts[1]));
                    if(start>=data.length){exchange.sendResponseHeaders(416,-1);return;}status=206;exchange.getResponseHeaders().set("Content-Range","bytes "+start+"-"+end+"/"+data.length);
                }
                if("HEAD".equals(exchange.getRequestMethod())){exchange.getResponseHeaders().set("Content-Length",Integer.toString(data.length));exchange.sendResponseHeaders(200,-1);return;}
                exchange.sendResponseHeaders(status,end-start+1);try(OutputStream output=exchange.getResponseBody()){output.write(data,start,end-start+1);}
            }finally{exchange.close();}
        });server.start();
    }
    public URI uri(String path){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+path);}
    public Map<String,String> headers(){Map<String,String> result=new LinkedHashMap<>();result.put("Referer","https://fixture.example/watch");result.put("User-Agent","UILib-Network-Verification");return Collections.unmodifiableMap(result);}
    public MediaSource source(String path){return MediaSource.http(uri(path),headers());}
    public MediaSource split(boolean shortAudio){return MediaSource.split(source("/video.avi"),source(shortAudio?"/short.wav":"/audio.wav"));}
    @Override public void close(){server.stop(0);worker.shutdownNow();}
}
