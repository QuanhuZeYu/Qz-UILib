package club.heiqi.uilib.host.media.bilibili;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;

/** 平台地址身份与本地语法；不执行网络读取或推断媒体流。 */
final class BilibiliAddress {
    final URI uri;

    private BilibiliAddress(URI uri) { this.uri = uri; }

    static BilibiliAddress parse(String value) {
        String text = value.trim();
        if (text.matches("BV[0-9A-Za-z]{10}")) text = "https://www.bilibili.com/video/" + text;
        URI uri = http(URI.create(text));
        if (uri.getPort() != -1) throw new IllegalArgumentException("不支持带凭据或自定义端口的平台地址");
        return new BilibiliAddress(uri);
    }

    boolean allowed() {
        switch (uri.getHost().toLowerCase(Locale.ROOT)) {
            case "bilibili.com": case "www.bilibili.com": case "m.bilibili.com":
            case "live.bilibili.com": case "b23.tv": case "www.b23.tv": return true;
            default: return false;
        }
    }

    boolean shortLink() { return uri.getHost().toLowerCase(Locale.ROOT).endsWith("b23.tv"); }
    boolean live() { return "live.bilibili.com".equalsIgnoreCase(uri.getHost()); }

    String videoId() throws IOException {
        String[] path = uri.getPath().split("/");
        if (path.length < 3 || !"video".equals(path[1]) || !path[2].matches("BV[0-9A-Za-z]{10}"))
            throw new IOException("请粘贴BV号、视频链接或直播间地址");
        return path[2];
    }

    String roomId() throws IOException {
        String path = uri.getPath();
        if (path.startsWith("/blanc/")) path = path.substring(6);
        return numeric(path.replace("/", ""));
    }

    String query(String key) throws IOException {
        String query = uri.getRawQuery();
        if (query == null) return "";
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) return URLDecoder.decode(pair[1], "UTF-8");
        }
        return "";
    }

    static String numeric(String value) throws IOException {
        if (!value.matches("[0-9]{1,20}")) throw new IOException("无效Bilibili内容或清晰度标识");
        return value;
    }

    static URI poster(String value) {
        return value.isEmpty() ? null : http(URI.create(value.startsWith("//") ? "https:" + value : value));
    }

    static URI http(URI uri) {
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("无效HTTP媒体地址");
        return uri;
    }
}
