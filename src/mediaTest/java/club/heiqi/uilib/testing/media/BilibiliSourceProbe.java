package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.host.media.bilibili.BilibiliResolver;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;

/** 独立公网探针，不进入可重复build；只记录内容身份和解码事实，不记录签名媒体URL。 */
public final class BilibiliSourceProbe {
    public static void main(String[] args)throws Exception{
        BilibiliResolver resolver=new BilibiliResolver();
        for(String address:args){
            long deadline=System.nanoTime()+45_000_000_000L;
            MediaResolver.Result result=resolver.resolve(new MediaResolver.Request(address,"",""),()->System.nanoTime()>deadline);
            System.out.println("BILIBILI_SOURCE canonical="+result.canonical+" title="+result.title+" author="+result.author+" live="+result.live+" parts="+result.parts.size()+" qualities="+result.qualities.size()+" selected="+result.quality+" available="+(result.source!=null));
            if(result.source==null)continue;
            try(MediaDecoderSource source=IsolatedMediaAdapters.decoderFactory().openSource(result.source,()->System.nanoTime()>deadline);MediaDecoder decoder=source.open(0,false,()->System.nanoTime()>deadline)){
                int video=0,audio=0;for(int i=0;i<200&&(video<8||audio<8);i++){
                    MediaDecoded item=decoder.read();if(item==null)break;if(item==MediaDecoded.YIELD)continue;if(item.video)video++;else audio++;
                }
                if(video<8||audio<8)throw new AssertionError("Bilibili真实流没有持续音视频解码");
                System.out.println("BILIBILI_DECODE live="+result.live+" video="+video+" audio="+audio+" size="+decoder.info().width+"x"+decoder.info().height);
            }
        }
    }
}
