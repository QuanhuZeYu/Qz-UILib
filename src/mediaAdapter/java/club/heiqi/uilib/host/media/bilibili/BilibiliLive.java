package club.heiqi.uilib.host.media.bilibili;

import static club.heiqi.uilib.host.media.bilibili.BilibiliJson.id;
import static club.heiqi.uilib.host.media.bilibili.BilibiliJson.list;
import static club.heiqi.uilib.host.media.bilibili.BilibiliJson.map;
import static club.heiqi.uilib.host.media.bilibili.BilibiliJson.number;
import static club.heiqi.uilib.host.media.bilibili.BilibiliJson.string;
import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.MediaResolver.Option;
import club.heiqi.uilib.ui.media.MediaResolver.Result;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

/** 直播流程依次读取房间、主播和实际流；未开播是内容事实，不创建播放来源。 */
final class BilibiliLive {
    private BilibiliLive() {}

    static Result resolve(BilibiliAddress address, MediaResolver.Request request, MediaCancellation cancellation) throws Exception {
        Map<String, Object> info = BilibiliHttp.api("https://api.live.bilibili.com/room/v1/Room/get_info?id=" + address.roomId(), cancellation);
        String room = id(info.get("room_id"));
        Map<String, Object> anchor = BilibiliHttp.api("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid=" + room, cancellation);
        String author = string(map(anchor.get("info")), "uname", "");
        URI canonical = URI.create("https://live.bilibili.com/" + room);
        if (number(info.get("live_status")) != 1) return offline(canonical, info, author, room);
        String qn = request.quality.isEmpty() ? "10000" : BilibiliAddress.numeric(request.quality);
        Map<String, Object> play = BilibiliHttp.api("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id="
                + room + "&protocol=1&format=1&codec=0&qn=" + qn + "&platform=web", cancellation);
        cancellation.check();
        return result(canonical, info, author, play);
    }

    private static Result offline(URI canonical, Map<String, Object> info, String author, String room) {
        return new Result(null, canonical, string(info, "title", "直播间 " + room), author,
                BilibiliAddress.poster(string(info, "user_cover", "")), true, Collections.emptyList(), Collections.emptyList(),
                "", "", "直播间尚未开播，开播后可刷新地址");
    }

    static Result result(URI canonical, Map<String, Object> info, String author, Map<String, Object> response) throws IOException {
        Map<String, Object> play = map(map(response.get("playurl_info")).get("playurl"));
        Map<String, Object> codec = codec(play);
        if (codec == null || list(codec.get("url_info")).isEmpty()) throw new IOException("当前直播未提供可用的HLS/H.264播放流");
        String selected = id(codec.get("current_qn"));
        // accept_qn是房间宣传档位，匿名高档位请求实测仍返回150；只公布响应实际输入。
        Option quality = new Option(selected, qualityName(play, selected));
        return new Result(MediaSource.http(uri(codec), BilibiliHttp.headers(canonical)), canonical, string(info, "title", "直播间"),
                author, BilibiliAddress.poster(string(info, "user_cover", "")), true, Collections.emptyList(),
                Collections.singletonList(quality), "", selected, "正在直播 · 可定位范围以直播实际窗口为准");
    }

    private static Map<String, Object> codec(Map<String, Object> play) {
        for (Object value : list(play.get("stream"))) {
            Map<String, Object> stream = map(value);
            if (!"http_hls".equals(stream.get("protocol_name"))) continue;
            Map<String, Object> result = codecInFormats(stream);
            if (result != null) return result;
        }
        return null;
    }

    private static Map<String, Object> codecInFormats(Map<String, Object> stream) {
        for (Object value : list(stream.get("format"))) {
            Map<String, Object> format = map(value);
            if (!"ts".equals(format.get("format_name"))) continue;
            for (Object item : list(format.get("codec"))) {
                Map<String, Object> codec = map(item);
                if ("avc".equals(codec.get("codec_name"))) return codec;
            }
        }
        return null;
    }

    private static String qualityName(Map<String, Object> play, String selected) {
        String result = "当前清晰度";
        for (Object value : list(play.get("g_qn_desc"))) {
            Map<String, Object> item = map(value);
            if (selected.equals(id(item.get("qn")))) result = string(item, "desc", "");
        }
        return result;
    }

    private static URI uri(Map<String, Object> codec) {
        Map<String, Object> host = map(list(codec.get("url_info")).get(0));
        return BilibiliAddress.http(URI.create(string(host, "host", "") + string(codec, "base_url", "") + string(host, "extra", "")));
    }
}
