package club.heiqi.uilib.ui.media;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 来源只是不可变值；分轨共享媒体时间线，构造和读取均不加载适配或 native。 */
public final class MediaSource {
    public final URI uri;
    public final Map<String,String> headers;
    /** 非空表示 uri 是视频输入，audio 是同一时间线的独立音频输入。 */
    public final MediaSource audio;
    private MediaSource(URI uri,Map<String,String> headers,MediaSource audio) {
        this.uri=Objects.requireNonNull(uri,"uri");
        if(!uri.isAbsolute())throw new IllegalArgumentException("媒体来源必须是绝对 URI");
        Map<String,String> copy=new LinkedHashMap<>();
        headers.forEach((name,value)->{
            if(name==null||!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||value==null||value.indexOf('\r')>=0||value.indexOf('\n')>=0)
                throw new IllegalArgumentException("无效媒体HTTP请求头");
            copy.put(name,value);
        });
        this.headers=Collections.unmodifiableMap(copy);this.audio=audio;
    }
    public static MediaSource of(URI uri){return new MediaSource(uri,Collections.emptyMap(),null);}
    public static MediaSource http(URI uri,Map<String,String> headers){
        if(!"http".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("请求头仅适用于HTTP媒体来源");
        return new MediaSource(uri,Objects.requireNonNull(headers,"headers"),null);
    }
    public static MediaSource split(MediaSource video,MediaSource audio){
        Objects.requireNonNull(video,"video");Objects.requireNonNull(audio,"audio");
        if(video.audio!=null||audio.audio!=null)throw new IllegalArgumentException("分轨输入不能嵌套");
        return new MediaSource(video.uri,video.headers,audio);
    }
    public MediaSource videoInput(){return audio==null?this:new MediaSource(uri,headers,null);}
    @Override public String toString(){return uri.toString();}
}
