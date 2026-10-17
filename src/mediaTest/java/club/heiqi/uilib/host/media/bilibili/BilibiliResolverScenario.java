package club.heiqi.uilib.host.media.bilibili;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.media.MediaResolver;
import java.net.URI;
import java.io.IOException;

/** 固定平台响应覆盖已知接口边界；公网兼容性由独立实机入口取得。 */
public final class BilibiliResolverScenario extends TestApplication {
    private static final String METADATA="{\"title\":\"受控公开视频\",\"owner\":{\"name\":\"测试作者\"},\"pic\":\"https://example.test/cover.jpg\",\"pages\":[{\"cid\":11,\"page\":1,\"part\":\"第一部分\"},{\"cid\":22,\"page\":2,\"part\":\"第二部分\"}]}";
    private static final String PLAY="{\"support_formats\":[{\"quality\":32,\"new_description\":\"480P 清晰\"},{\"quality\":16,\"new_description\":\"360P 流畅\"},{\"quality\":80,\"new_description\":\"1080P\"}],\"dash\":{\"video\":[{\"id\":32,\"codecs\":\"avc1.64001F\",\"height\":480,\"baseUrl\":\"https://example.test/video480.m4s\"},{\"id\":16,\"codecs\":\"avc1.64001E\",\"height\":360,\"baseUrl\":\"https://example.test/video360.m4s\"},{\"id\":80,\"codecs\":\"hev1\",\"height\":1080,\"baseUrl\":\"https://example.test/video1080.m4s\"}],\"audio\":[{\"id\":30280,\"codecs\":\"mp4a.40.2\",\"bandwidth\":128000,\"baseUrl\":\"https://example.test/audio.m4s\"}]}}";
    private static final String LIVE="{\"playurl_info\":{\"playurl\":{\"g_qn_desc\":[{\"qn\":150,\"desc\":\"高清\"}],\"stream\":[{\"protocol_name\":\"http_hls\",\"format\":[{\"format_name\":\"ts\",\"codec\":[{\"codec_name\":\"avc\",\"current_qn\":150,\"accept_qn\":[10000,400,150],\"base_url\":\"/live.m3u8\",\"url_info\":[{\"host\":\"https://example.test\",\"extra\":\"?expires=123&signature=abc\"}]}]}]}]}}}";
    public static void main(String[] args)throws Exception{new BilibiliResolverScenario().run(args);}
    protected void runTests()throws Exception{
        scenario("平台地址归属和取消不联网",()->{
            BilibiliResolver resolver=new BilibiliResolver();
            for(String input:new String[]{"BV1GJ411x7h7","https://www.bilibili.com/video/BV1GJ411x7h7/?p=2","https://b23.tv/test","https://live.bilibili.com/6"})require(resolver.supports(input),"合法地址未识别");
            for(String input:new String[]{"https://bilibili.com.evil.test/video/a","https://evil.test/","file:///example","https://user:secret@bilibili.com/"})require(!resolver.supports(input),"非平台地址被接管");
            boolean cancelled=false;try{resolver.resolve(new MediaResolver.Request("BV1GJ411x7h7","",""),()->true);}catch(java.util.concurrent.CancellationException expected){cancelled=true;}require(cancelled,"取消后仍解析");
        });
        scenario("分P与匿名实际清晰度组合原始分轨",()->{
            MediaResolver.Result result=BilibiliResolver.videoResult(URI.create("https://www.bilibili.com/"),"BV1GJ411x7h7","22","16",BilibiliResolver.map(BilibiliJson.parse(METADATA)),BilibiliResolver.map(BilibiliJson.parse(PLAY)));
            require(result.parts.size()==2&&result.part.equals("22")&&result.canonical.getQuery().equals("p=2"),"分P身份错误");
            require(result.qualities.size()==2&&result.quality.equals("16")&&result.source.uri.getPath().contains("360")&&result.source.audio!=null,"不可用清晰度混入或分轨选择错误");
            require(result.source.headers.get("Referer").equals(result.canonical.toString())&&result.source.audio.headers.equals(result.source.headers),"两轨未携带同一来源请求头");
            boolean denied=false;try{BilibiliResolver.videoResult(result.canonical,"BV1GJ411x7h7","22","80",BilibiliResolver.map(BilibiliJson.parse(METADATA)),BilibiliResolver.map(BilibiliJson.parse(PLAY)));}catch(IOException expected){denied=true;}require(denied,"缺少可播放轨道仍伪称提供清晰度");
        });
        scenario("直播签名查询和实际流格式保留",()->{
            MediaResolver.Result result=BilibiliResolver.liveResult(URI.create("https://live.bilibili.com/6"),java.util.Collections.emptyMap(),"测试主播",BilibiliResolver.map(BilibiliJson.parse(LIVE)));
            require(result.live&&result.source.uri.getRawQuery().equals("expires=123&signature=abc")&&result.quality.equals("150"),"直播地址签名丢失");
            require(result.qualities.size()==1&&result.qualities.get(0).id.equals("150"),"直播宣传档位冒充匿名可播放清晰度");
        });
        scenario("有限严格JSON拒绝错误和深度溢出",()->{
            for(String invalid:new String[]{"{\"a\":1,}","[01]","{\"a\":1,\"a\":2}","true false",new String(new char[80]).replace('\0','[')+"0"+new String(new char[80]).replace('\0',']')}){
                boolean failed=false;try{BilibiliJson.parse(invalid);}catch(IOException expected){failed=true;}require(failed,"无效响应被接受");
            }
            require("中文\n说明".equals(BilibiliResolver.map(BilibiliJson.parse("{\"text\":\"中文\\n说明\"}")).get("text")),"JSON转义语义丢失");
        });
    }
}
