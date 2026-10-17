package club.heiqi.uilib.host.media.bilibili;

import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bilibili匿名来源适配；不持有会话、账户或native资源。平台事实归媒体规格第8节。 */
public final class BilibiliResolver implements MediaResolver {
    private static final String AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
    private static final int MAX_BYTES=4*1024*1024;
    @Override public boolean supports(String address){
        try{URI uri=address(address);return allowed(uri.getHost());}catch(IllegalArgumentException invalid){return false;}
    }
    @Override public Result resolve(Request request,MediaCancellation cancellation)throws Exception{
        cancellation.check();URI uri=address(request.address);
        if(!allowed(uri.getHost()))throw new IOException("请选择Bilibili视频或直播间地址");
        if(uri.getHost().toLowerCase(Locale.ROOT).endsWith("b23.tv"))uri=expand(uri,cancellation);
        if("live.bilibili.com".equalsIgnoreCase(uri.getHost()))return live(uri,request,cancellation);
        String[] path=uri.getPath().split("/");
        if(path.length<3||!"video".equals(path[1])||!path[2].matches("BV[0-9A-Za-z]{10}"))throw new IOException("请粘贴BV号、视频链接或直播间地址");
        String bvid=path[2];Map<String,Object> metadata=api("https://api.bilibili.com/x/web-interface/view?bvid="+bvid,cancellation);
        List<Object> pages=list(metadata.get("pages"));
        if(pages.isEmpty())throw new IOException("视频没有可观看的分P，可能受权限或地区限制");
        String selected=request.part;
        if(selected.isEmpty()){
            String p=query(uri,"p");int index=p.isEmpty()?1:Integer.parseInt(p);
            if(index<1||index>pages.size())throw new IOException("视频分P不存在");selected=id(map(pages.get(index-1)).get("cid"));
        }
        boolean found=false;for(Object p:pages)if(selected.equals(id(map(p).get("cid"))))found=true;
        if(!found)throw new IOException("所选分P已不可用，请重新打开视频");
        String quality=request.quality.isEmpty()?"127":numeric(request.quality);
        Map<String,Object> play=api("https://api.bilibili.com/x/player/playurl?bvid="+bvid+"&cid="+numeric(selected)+"&fnval=16&fourk=1&qn="+quality,cancellation);
        cancellation.check();return videoResult(uri,bvid,selected,request.quality,metadata,play);
    }
    static Result videoResult(URI original,String bvid,String part,String requested,Map<String,Object> metadata,Map<String,Object> play)throws IOException{
        Map<String,Object> dash=map(play.get("dash"));Map<String,Object> video=null,audio=null;
        Map<String,String> names=new LinkedHashMap<>();for(Object f:list(play.get("support_formats"))){Map<String,Object> item=map(f);names.put(id(item.get("quality")),string(item,"new_description",string(item,"display_desc","")));}
        Map<String,Option> qualities=new LinkedHashMap<>();
        for(Object v:list(dash.get("video"))){
            Map<String,Object> item=map(v);if(!string(item,"codecs","").startsWith("avc"))continue;
            String quality=id(item.get("id"));qualities.put(quality,new Option(quality,names.getOrDefault(quality,string(item,"height",quality)+"P")));
            if((requested.isEmpty()||requested.equals(quality))&&(video==null||number(item.get("id"))>number(video.get("id"))))video=item;
        }
        for(Object a:list(dash.get("audio"))){Map<String,Object> item=map(a);if(string(item,"codecs","").startsWith("mp4a")&&(audio==null||number(item.get("bandwidth"))>number(audio.get("bandwidth"))))audio=item;}
        if(video==null||audio==null)throw new IOException(requested.isEmpty()?"当前访问权限没有提供可播放的H.264/AAC内容，可能需要登录":"当前访问权限不提供所选清晰度，请选择可用档位或刷新地址");
        List<Option> parts=new ArrayList<>();int selectedIndex=0;
        for(Object p:list(metadata.get("pages"))){Map<String,Object> item=map(p);String cid=id(item.get("cid"));parts.add(new Option(cid,"P"+id(item.get("page"))+" · "+string(item,"part","")));if(cid.equals(part))selectedIndex=parts.size();}
        URI canonical=URI.create("https://www.bilibili.com/video/"+bvid+"/?p="+selectedIndex);
        Map<String,String> headers=headers(canonical);List<Option> options=new ArrayList<>(qualities.values());options.sort(Comparator.comparingLong((Option o)->Long.parseLong(o.id)).reversed());
        MediaSource source=MediaSource.split(MediaSource.http(mediaUri(video),headers),MediaSource.http(mediaUri(audio),headers));
        return new Result(source,canonical,string(metadata,"title",bvid),string(map(metadata.get("owner")),"name",""),poster(string(metadata,"pic","")),false,
                parts,options,part,id(video.get("id")),"匿名观看 · 清晰度按当前访问权限提供");
    }
    private Result live(URI uri,Request request,MediaCancellation cancellation)throws Exception{
        String path=uri.getPath();if(path.startsWith("/blanc/"))path=path.substring(6);
        String room=path.replace("/","");numeric(room);
        Map<String,Object> info=api("https://api.live.bilibili.com/room/v1/Room/get_info?id="+room,cancellation);room=id(info.get("room_id"));
        Map<String,Object> anchor=api("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid="+room,cancellation);
        String author=string(map(anchor.get("info")),"uname","");URI canonical=URI.create("https://live.bilibili.com/"+room);
        if(number(info.get("live_status"))!=1)return new Result(null,canonical,string(info,"title","直播间 "+room),author,poster(string(info,"user_cover","")),true,Collections.emptyList(),Collections.emptyList(),"","","直播间尚未开播，开播后可刷新地址");
        String qn=request.quality.isEmpty()?"10000":numeric(request.quality);
        Map<String,Object> play=api("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id="+room+"&protocol=1&format=1&codec=0&qn="+qn+"&platform=web",cancellation);
        cancellation.check();return liveResult(canonical,info,author,play);
    }
    static Result liveResult(URI canonical,Map<String,Object> info,String author,Map<String,Object> result)throws IOException{
        Map<String,Object> play=map(map(result.get("playurl_info")).get("playurl")),codec=null;
        for(Object s:list(play.get("stream"))){Map<String,Object> stream=map(s);if(!"http_hls".equals(stream.get("protocol_name")))continue;
            for(Object f:list(stream.get("format"))){Map<String,Object> format=map(f);if(!"ts".equals(format.get("format_name")))continue;
                for(Object c:list(format.get("codec"))){Map<String,Object> item=map(c);if("avc".equals(item.get("codec_name"))&&codec==null)codec=item;}
            }
        }
        if(codec==null||list(codec.get("url_info")).isEmpty())throw new IOException("当前直播未提供可用的HLS/H.264播放流");
        Map<String,Object> host=map(list(codec.get("url_info")).get(0));URI url=checkedHttp(URI.create(string(host,"host","")+string(codec,"base_url","")+string(host,"extra","")));
        Map<String,String> names=new LinkedHashMap<>();for(Object item:list(play.get("g_qn_desc"))){Map<String,Object> q=map(item);names.put(id(q.get("qn")),string(q,"desc",""));}
        String selected=id(codec.get("current_qn"));
        // accept_qn是房间宣传档位：匿名请求400/10000实测均只返回150，不能当作可用流。
        // 只公布当前响应真正给出的档位；以后获得其他实际输入时再扩充选择。
        List<Option> qualities=Collections.singletonList(new Option(selected,names.getOrDefault(selected,"当前清晰度")));
        return new Result(MediaSource.http(url,headers(canonical)),canonical,string(info,"title","直播间"),author,poster(string(info,"user_cover","")),true,
                Collections.emptyList(),qualities,"",selected,"正在直播 · 可定位范围以直播实际窗口为准");
    }
    static URI address(String value){
        String text=value.trim();if(text.matches("BV[0-9A-Za-z]{10}"))text="https://www.bilibili.com/video/"+text;
        URI uri=URI.create(text);checkedHttp(uri);if(uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("不支持带凭据或自定义端口的平台地址");return uri;
    }
    private static boolean allowed(String host){if(host==null)return false;String name=host.toLowerCase(Locale.ROOT);return name.equals("bilibili.com")||name.equals("www.bilibili.com")||name.equals("m.bilibili.com")||name.equals("live.bilibili.com")||name.equals("b23.tv")||name.equals("www.b23.tv");}
    private static URI expand(URI uri,MediaCancellation cancellation)throws Exception{
        for(int i=0;i<5;i++){
            cancellation.check();if(!allowed(uri.getHost()))throw new IOException("短链接跳转到了非Bilibili地址");
            if(!uri.getHost().toLowerCase(Locale.ROOT).endsWith("b23.tv"))return uri;
            HttpURLConnection c=connection(uri);c.setRequestMethod("HEAD");
            try{int status=c.getResponseCode();cancellation.check();String target=c.getHeaderField("Location");if(status<300||status>=400||target==null)throw new IOException("Bilibili短链接没有提供有效跳转");uri=address(uri.resolve(target).toString());}finally{c.disconnect();}
        }
        throw new IOException("Bilibili短链接跳转超过预算");
    }
    private static HttpURLConnection connection(URI uri)throws IOException{
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",AGENT);c.setRequestProperty("Referer","https://www.bilibili.com/");return c;
    }
    private static Map<String,Object> api(String address,MediaCancellation cancellation)throws Exception{
        cancellation.check();HttpURLConnection c=connection(URI.create(address));
        try{
            int status=c.getResponseCode();cancellation.check();if(status!=200)throw new IOException("Bilibili接口HTTP "+status+"，可刷新地址重新请求");
            try(InputStream input=c.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];for(int count;(count=input.read(bytes))!=-1;){cancellation.check();if(output.size()+count>MAX_BYTES)throw new IOException("Bilibili响应超过字节预算");output.write(bytes,0,count);}
                Map<String,Object> result=map(BilibiliJson.parse(new String(output.toByteArray(),StandardCharsets.UTF_8)));
                if(!result.containsKey("code"))throw new IOException("Bilibili接口响应缺少状态");
                long code=number(result.get("code"));if(code!=0)throw new IOException("Bilibili拒绝请求（"+code+"）："+string(result,"message","内容不可用")+(code==-101?"，此内容需要登录":""));
                return map(result.get("data"));
            }
        }finally{c.disconnect();}
    }
    private static Map<String,String> headers(URI referer){Map<String,String> result=new LinkedHashMap<>();result.put("Referer",referer.toString());result.put("User-Agent",AGENT);return result;}
    private static URI mediaUri(Map<String,Object> track){return checkedHttp(URI.create(string(track,"baseUrl",string(track,"base_url",""))));}
    private static URI poster(String address){return address.isEmpty()?null:checkedHttp(URI.create(address.startsWith("//")?"https:"+address:address));}
    private static URI checkedHttp(URI uri){if(uri.getHost()==null||uri.getUserInfo()!=null||!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme())))throw new IllegalArgumentException("无效HTTP媒体地址");return uri;}
    private static String numeric(String value)throws IOException{if(!value.matches("[0-9]{1,20}"))throw new IOException("无效Bilibili内容或清晰度标识");return value;}
    private static String query(URI uri,String key)throws Exception{if(uri.getRawQuery()!=null)for(String item:uri.getRawQuery().split("&")){String[] pair=item.split("=",2);if(pair.length==2&&key.equals(pair[0]))return URLDecoder.decode(pair[1],"UTF-8");}return "";}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object value){return value instanceof Map?(Map<String,Object>)value:Collections.emptyMap();}
    @SuppressWarnings("unchecked") static List<Object> list(Object value){return value instanceof List?(List<Object>)value:Collections.emptyList();}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
    private static String id(Object value){return value==null?"":value.toString();}
    private static String string(Map<String,Object> map,String key,String fallback){Object value=map.get(key);return value==null?fallback:value.toString();}
}
