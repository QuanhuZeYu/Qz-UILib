package club.heiqi.uilib.testing.media;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 同一合成TS沿真实90kHz PTS/DTS/PCR平移，动态有限窗口无ENDLIST；不依赖外部ffmpeg进程。 */
public final class MediaLiveFixture implements AutoCloseable {
    private final HttpServer server;private final ExecutorService executor;private final byte[] original;private final long started=System.nanoTime();
    public MediaLiveFixture()throws IOException{
        try(InputStream input=getClass().getResourceAsStream("/uilib-media/segment.ts");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            if(input==null)throw new IOException("缺少正式合成TS样片");byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;)output.write(buffer,0,count);original=output.toByteArray();
        }
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"uilib-live-fixture");t.setDaemon(true);return t;});server.setExecutor(executor);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();byte[] body;
            if(path.equals("/live.m3u8")){
                long sequence=(System.nanoTime()-started)/4_000_000_000L;StringBuilder playlist=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:").append(sequence).append('\n');
                for(int i=0;i<3;i++)playlist.append("#EXTINF:4.000000,\nsegment-").append(sequence+i).append(".ts\n");body=playlist.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/vnd.apple.mpegurl");
            }else if(path.matches("/segment-[0-9]+[.]ts")){
                long sequence=Long.parseLong(path.substring(9,path.length()-3));if(sequence>10000){exchange.sendResponseHeaders(404,-1);exchange.close();return;}body=shift(original,sequence*360_000L);exchange.getResponseHeaders().set("Content-Type","video/mp2t");
            }else {exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            try{exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(200,body.length);try(OutputStream output=exchange.getResponseBody()){output.write(body);}}finally{exchange.close();}
        });server.start();
    }
    public URI uri(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/live.m3u8");}
    private static byte[] shift(byte[] source,long ticks){
        byte[] copy=source.clone();for(int packet=0;packet+188<=copy.length;packet+=188){
            if((copy[packet]&255)!=0x47)throw new IllegalArgumentException("TS不同步");int adaptation=(copy[packet+3]>>4)&3,payload=packet+4;
            if(adaptation==2||adaptation==3){int length=copy[payload]&255;if(length>0&&(copy[payload+1]&0x10)!=0&&length>=7){int at=payload+2;long pcr=((copy[at]&255L)<<25)|((copy[at+1]&255L)<<17)|((copy[at+2]&255L)<<9)|((copy[at+3]&255L)<<1)|((copy[at+4]&0x80L)>>7);pcr=(pcr+ticks)&0x1ffffffffL;
                copy[at]=(byte)(pcr>>25);copy[at+1]=(byte)(pcr>>17);copy[at+2]=(byte)(pcr>>9);copy[at+3]=(byte)(pcr>>1);copy[at+4]=(byte)((copy[at+4]&0x7f)|((pcr&1)<<7));}payload+=1+length;}
            if((adaptation==1||adaptation==3)&&(copy[packet+1]&0x40)!=0&&payload+19<=packet+188&&copy[payload]==0&&copy[payload+1]==0&&copy[payload+2]==1){int flags=(copy[payload+7]>>6)&3;if(flags==2||flags==3)stamp(copy,payload+9,ticks);if(flags==3)stamp(copy,payload+14,ticks);}
        }return copy;
    }
    private static void stamp(byte[] bytes,int at,long ticks){long pts=((bytes[at]&14L)<<29)|((bytes[at+1]&255L)<<22)|((bytes[at+2]&254L)<<14)|((bytes[at+3]&255L)<<7)|((bytes[at+4]&254L)>>1);pts=(pts+ticks)&0x1ffffffffL;
        bytes[at]=(byte)((bytes[at]&0xf1)|((pts>>29)&14));bytes[at+1]=(byte)(pts>>22);bytes[at+2]=(byte)(((pts>>14)&254)|1);bytes[at+3]=(byte)(pts>>7);bytes[at+4]=(byte)(((pts<<1)&254)|1);
    }
    public static byte[] audio(){int rate=22050,samples=rate*3;java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(44+samples*2).order(java.nio.ByteOrder.LITTLE_ENDIAN);bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);for(int i=0;i<samples;i++)bytes.putShort((short)(Math.sin(i*2*Math.PI*440/rate)*2048));return bytes.array();}
    /** 无压缩AVI样片让FFmpeg实际发现横竖轨道并输出帧；字节只属于测试Jar算法。 */
    public static byte[] video(int width,int height)throws IOException{
        if(width<=0||height<=0||width>640||height>640)throw new IllegalArgumentException("测试视频尺寸超预算");
        int stride=(Math.multiplyExact(width,3)+3)&~3,frameSize=Math.multiplyExact(stride,height),frames=20,rate=10;
        java.nio.ByteBuffer avih=le(56);avih.putInt(1_000_000/rate).putInt(frameSize*rate).putInt(0).putInt(0).putInt(frames).putInt(0).putInt(1).putInt(frameSize).putInt(width).putInt(height).putLong(0).putLong(0);
        java.nio.ByteBuffer strh=le(56);strh.put("vidsDIB ".getBytes(StandardCharsets.US_ASCII)).putInt(0).putInt(0).putInt(0).putInt(1).putInt(rate).putInt(0).putInt(frames).putInt(frameSize).putInt(-1).putInt(0).putShort((short)0).putShort((short)0).putShort((short)width).putShort((short)height);
        java.nio.ByteBuffer strf=le(40);strf.putInt(40).putInt(width).putInt(height).putShort((short)1).putShort((short)24).putInt(0).putInt(frameSize).putInt(0).putInt(0).putInt(0).putInt(0);
        byte[] header=list("hdrl",chunk("avih",avih.array()),list("strl",chunk("strh",strh.array()),chunk("strf",strf.array())));
        ByteArrayOutputStream movie=new ByteArrayOutputStream();movie.write("movi".getBytes(StandardCharsets.US_ASCII));
        for(int frame=0;frame<frames;frame++){byte[] pixels=new byte[frameSize];for(int y=0;y<height;y++)for(int x=0;x<width;x++){int at=y*stride+x*3;pixels[at]=(byte)(x<width/2?40:200);pixels[at+1]=(byte)(y<height/2?180:60);pixels[at+2]=(byte)(80+frame*4);}movie.write(chunk("00db",pixels));}
        byte[] body=join("AVI ".getBytes(StandardCharsets.US_ASCII),header,chunk("LIST",movie.toByteArray()));return chunk("RIFF",body);
    }
    private static java.nio.ByteBuffer le(int size){return java.nio.ByteBuffer.allocate(size).order(java.nio.ByteOrder.LITTLE_ENDIAN);}
    private static byte[] chunk(String id,byte[] content)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(id.getBytes(StandardCharsets.US_ASCII));out.write(le(4).putInt(content.length).array());out.write(content);if((content.length&1)!=0)out.write(0);return out.toByteArray();}
    private static byte[] list(String name,byte[]... contents)throws IOException{return chunk("LIST",join(name.getBytes(StandardCharsets.US_ASCII),join(contents)));}
    private static byte[] join(byte[]... contents)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] content:contents)out.write(content);return out.toByteArray();}
    @Override public void close(){server.stop(0);executor.shutdownNow();}
}
