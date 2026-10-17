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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 点播只决定分P和实际H.264/AAC输入；HTTP和JSON语法不进入播放选择。 */
final class BilibiliVideo {
    private BilibiliVideo() {}

    static Result resolve(BilibiliAddress address, MediaResolver.Request request, MediaCancellation cancellation) throws Exception {
        String bvid = address.videoId();
        Map<String, Object> metadata = BilibiliHttp.api("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid, cancellation);
        Parts parts = new Parts(metadata, request.part, request.part.isEmpty() ? address.query("p") : "");
        String quality = request.quality.isEmpty() ? "127" : BilibiliAddress.numeric(request.quality);
        Map<String, Object> play = BilibiliHttp.api("https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                + "&cid=" + BilibiliAddress.numeric(parts.selected) + "&fnval=16&fourk=1&qn=" + quality, cancellation);
        cancellation.check();
        return result(bvid, parts, request.quality, metadata, play);
    }

    static Result result(String bvid, String part, String requested, Map<String, Object> metadata, Map<String, Object> play) throws IOException {
        return result(bvid, new Parts(metadata, part, ""), requested, metadata, play);
    }

    private static Result result(String bvid, Parts parts, String requested, Map<String, Object> metadata, Map<String, Object> play) throws IOException {
        Tracks tracks = new Tracks(play, requested);
        URI canonical = URI.create("https://www.bilibili.com/video/" + bvid + "/?p=" + parts.index);
        return new Result(tracks.source(canonical), canonical, string(metadata, "title", bvid),
                string(map(metadata.get("owner")), "name", ""), BilibiliAddress.poster(string(metadata, "pic", "")), false,
                parts.options, tracks.qualities, parts.selected, id(tracks.video.get("id")), "匿名观看 · 清晰度按当前访问权限提供");
    }

    /** 分P列表和选中身份一起确定，避免地址序号与cid各自维护。 */
    private static final class Parts {
        final List<Option> options = new ArrayList<>();
        final String selected;
        final int index;

        Parts(Map<String, Object> metadata, String requested, String page) throws IOException {
            List<Object> pages = list(metadata.get("pages"));
            if (pages.isEmpty()) throw new IOException("视频没有可观看的分P，可能受权限或地区限制");
            selected = requested.isEmpty() ? fromPage(pages, page) : requested;
            int found = 0;
            for (Object value : pages) {
                Map<String, Object> item = map(value);
                String cid = id(item.get("cid"));
                options.add(new Option(cid, "P" + id(item.get("page")) + " · " + string(item, "part", "")));
                if (cid.equals(selected)) found = options.size();
            }
            if (found == 0) throw new IOException("所选分P已不可用，请重新打开视频");
            index = found;
        }

        private static String fromPage(List<Object> pages, String page) throws IOException {
            int index = page.isEmpty() ? 1 : Integer.parseInt(page);
            if (index < 1 || index > pages.size()) throw new IOException("视频分P不存在");
            return id(map(pages.get(index - 1)).get("cid"));
        }
    }

    /** 一次响应的实际轨道候选；只保存选中轨与可播档位，不缓存平台宣传能力。 */
    private static final class Tracks {
        final Map<String, Object> video, audio;
        final List<Option> qualities;

        Tracks(Map<String, Object> play, String requested) throws IOException {
            Map<String, Object> dash = map(play.get("dash"));
            List<Map<String, Object>> videos = matching(list(dash.get("video")), "avc");
            video = video(videos, requested);
            audio = best(matching(list(dash.get("audio")), "mp4a"), "bandwidth");
            if (video == null || audio == null) throw new IOException(requested.isEmpty()
                    ? "当前访问权限没有提供可播放的H.264/AAC内容，可能需要登录"
                    : "当前访问权限不提供所选清晰度，请选择可用档位或刷新地址");
            qualities = qualities(play, videos);
        }

        MediaSource source(URI canonical) {
            Map<String, String> headers = BilibiliHttp.headers(canonical);
            return MediaSource.split(MediaSource.http(uri(video), headers), MediaSource.http(uri(audio), headers));
        }

        private static List<Map<String, Object>> matching(List<Object> tracks, String prefix) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object value : tracks) {
                Map<String, Object> track = map(value);
                if (string(track, "codecs", "").startsWith(prefix)) result.add(track);
            }
            return result;
        }

        private static Map<String, Object> video(List<Map<String, Object>> videos, String requested) {
            if (requested.isEmpty()) return best(videos, "id");
            for (Map<String, Object> video : videos) if (requested.equals(id(video.get("id")))) return video;
            return null;
        }

        private static Map<String, Object> best(List<Map<String, Object>> tracks, String property) {
            Map<String, Object> best = null;
            for (Map<String, Object> track : tracks) {
                if (best == null || number(track.get(property)) > number(best.get(property))) best = track;
            }
            return best;
        }

        private static List<Option> qualities(Map<String, Object> play, List<Map<String, Object>> videos) {
            Map<String, String> names = new LinkedHashMap<>();
            for (Object value : list(play.get("support_formats"))) {
                Map<String, Object> item = map(value);
                names.put(id(item.get("quality")), string(item, "new_description", string(item, "display_desc", "")));
            }
            Map<String, Option> options = new LinkedHashMap<>();
            for (Map<String, Object> video : videos) {
                String quality = id(video.get("id"));
                options.put(quality, new Option(quality, names.getOrDefault(quality, string(video, "height", quality) + "P")));
            }
            List<Option> result = new ArrayList<>(options.values());
            result.sort(Comparator.comparingLong((Option option) -> Long.parseLong(option.id)).reversed());
            return result;
        }

        private static URI uri(Map<String, Object> track) {
            return BilibiliAddress.http(URI.create(string(track, "baseUrl", string(track, "base_url", ""))));
        }
    }
}
