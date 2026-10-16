package club.heiqi.uilib.testing.media;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自包含短输入；只有测试Jar携带合成媒体，普通build不依赖外部持续服务或ffmpeg命令。 */
public final class MediaFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    public MediaFixture() throws IOException {
        byte[] segment;
        try(InputStream input=getClass().getResourceAsStream("/uilib-media/segment.ts");ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if(input==null)throw new IOException("缺少测试短媒体");
            byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;)bytes.write(buffer,0,count);segment=bytes.toByteArray();
        }
        final byte[] media=segment;
        final byte[] playlist=("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:4.000000,\nsegment.ts\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"uilib-media-fixture");t.setDaemon(true);return t;});
        server.setExecutor(executor);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();byte[] body;
            if("/vod.m3u8".equals(path)){body=playlist;exchange.getResponseHeaders().set("Content-Type","application/vnd.apple.mpegurl");}
            else if("/segment.ts".equals(path)){body=media;exchange.getResponseHeaders().set("Content-Type","video/mp2t");}
            else {exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            try {exchange.sendResponseHeaders(200,body.length);try(OutputStream output=exchange.getResponseBody()){output.write(body);}}
            finally {exchange.close();}
        });
        server.start();
    }
    public URI uri(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/vod.m3u8");}
    @Override public void close(){server.stop(0);executor.shutdownNow();}
}
