package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaInputState;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;


import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 直接消费正式source/decoder；媒体PTS来自真实帧，未把解码当作GPU或设备已呈现。 */
public final class MediaEndlistScenario extends TestApplication {
    private static String get(String url)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(2000);
        try {int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){
                if(input!=null){byte[] bytes=new byte[2048];int n;while((n=input.read(bytes))>=0){if(output.size()+n>65536)throw new IOException("响应预算");output.write(bytes,0,n);}}
                return "HTTP "+status+"\n"+new String(output.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
    }
    @Override protected void runTests()throws Exception {
        scenario("live正常ENDLIST保同源窗口原点并显式seek到真实EOF",()->{
            String origin=arguments()[0];MediaCancellation token=()->false;
            System.out.println("INITIAL_MANIFEST\n"+get(origin+"/live.m3u8"));
            MediaDecoderSource source=IsolatedMediaAdapters.decoderFactory().openSource(MediaSource.of(URI.create(origin+"/live.m3u8")),token);
            try {
                MediaDecoder initial=source.open(0,false,token);
                MediaInfo live=initial.info();long start=source.initialPositionNanos();
                try {
                    MediaDecoded first=null;for(int i=0;i<100;i++){MediaDecoded next=initial.read();if(next==null)break;if(next.video){first=next;break;}}
                    require(first!=null&&Math.abs(first.presentationNanos-start)<50_000_000L,"初始join和真实PTS不一致");
                    System.out.println("LIVE duration="+live.durationNanos+" range="+live.seekable.startNanos+".."+live.seekable.endNanos+" join="+start+" firstVideo="+first.presentationNanos);
                    require(live.durationNanos==-1&&live.seekable.startNanos==0&&live.seekable.endNanos==11_999_999_999L,"初始live窗口异常");
                    System.out.println("ENDLIST_CONTROL "+get(origin+"/__endlist"));
                    System.out.println("FINAL_MANIFEST\n"+get(origin+"/live.m3u8"));
                    long remaining=source.refreshDeadlineNanos()-System.nanoTime();if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining+1_000_000L);
                    MediaInfo ended=source.refresh(live,token),again=initial.info();
                    require(ended.durationNanos==14_000_000_000L&&ended.seekable.startNanos==2_000_000_000L&&ended.seekable.endNanos==13_999_999_999L,"ENDLIST刷新未保累计窗口");
                    require(again.durationNanos==ended.durationNanos&&again.seekable.startNanos==ended.seekable.startNanos&&again.seekable.endNanos==ended.seekable.endNanos,"decoder.info漏出旧零点事实");
                    require(source.refreshDeadlineNanos()==Long.MAX_VALUE,"ENDLIST仍安排正常刷新");
                    System.out.println("ENDED_FACTS duration="+ended.durationNanos+" range="+ended.seekable.startNanos+".."+ended.seekable.endNanos);
                }finally{initial.close();}
                long target=9_500_000_000L;MediaDecoder next=source.open(target,true,token);
                try {
                    long firstVideo=Long.MIN_VALUE,lastVideo=-1,lastAudio=-1;int video=0,audio=0;boolean targetCovered=false,eof=false;
                    for(int i=0;i<3000;i++){
                        MediaDecoded decoded=next.read();if(decoded==null){eof=true;break;}
                        if(decoded.video){if(firstVideo==Long.MIN_VALUE)firstVideo=decoded.presentationNanos;lastVideo=decoded.presentationNanos+decoded.durationNanos;video++;}
                        else{lastAudio=decoded.presentationNanos+decoded.durationNanos;audio++;}
                        if(decoded.presentationNanos<=target&&decoded.presentationNanos+decoded.durationNanos>target)targetCovered=true;
                    }
                    MediaInputState terminal=next.inputState();
                    require(firstVideo>=7_500_000_000L&&firstVideo<=target&&targetCovered,"ENDLIST重开定位丢失source origin");
                    require(eof&&terminal.inputEnded&&terminal.audioEnded&&terminal.videoEnded,"真实ENDLIST未正常终结");
                    require(video>0&&audio>0&&lastVideo>13_000_000_000L&&lastAudio>13_000_000_000L,"最终PTS端点错误");
                    System.out.println("ENDLIST_SEEK target="+target+" firstVideo="+firstVideo+" covered="+targetCovered+" video="+video+" audio="+audio+" lastVideo="+lastVideo+" lastAudio="+lastAudio
                        +" terminalVideo="+terminal.videoEndNanos+" terminalAudio="+terminal.audioEndNanos+" normalEOF="+eof);
                }finally{next.close();}
            }finally{source.close();}
        });
    }
    public static void main(String[] args)throws Exception{new MediaEndlistScenario().run(args);}
}
