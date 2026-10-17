package club.heiqi.uilib.ui.media;

import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 平台适配提供来源事实；resolve 是可取消的后台读取，不操作媒体会话或 UI。 */
public interface MediaResolver {
    boolean supports(String address);
    Result resolve(Request request,MediaCancellation cancellation) throws Exception;

    final class Request {
        public final String address,part,quality;
        public Request(String address,String part,String quality){
            this.address=Objects.requireNonNull(address,"address");
            this.part=part==null?"":part;this.quality=quality==null?"":quality;
        }
    }
    final class Option {
        public final String id,label;
        public Option(String id,String label){this.id=Objects.requireNonNull(id,"id");this.label=Objects.requireNonNull(label,"label");}
    }
    final class Result {
        public final MediaSource source;
        public final URI canonical,poster;
        public final String title,author,part,quality,message;
        public final boolean live;
        public final List<Option> parts,qualities;
        public Result(MediaSource source,URI canonical,String title,String author,URI poster,boolean live,
                List<Option> parts,List<Option> qualities,String part,String quality,String message){
            this.source=source;this.canonical=Objects.requireNonNull(canonical,"canonical");
            this.title=Objects.requireNonNull(title,"title");this.author=Objects.requireNonNull(author,"author");
            this.poster=poster;this.live=live;this.parts=Collections.unmodifiableList(new ArrayList<>(parts));
            this.qualities=Collections.unmodifiableList(new ArrayList<>(qualities));
            this.part=Objects.requireNonNull(part,"part");this.quality=Objects.requireNonNull(quality,"quality");
            this.message=Objects.requireNonNull(message,"message");
        }
    }
}
