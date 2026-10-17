package club.heiqi.uilib.host.media.bilibili;

import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 平台HTTP读取与跳转预算；取消和状态错误在读取边界处理，调用方只消费平台事实。 */
final class BilibiliHttp {
    private static final String AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private BilibiliHttp() {}

    static BilibiliAddress expand(BilibiliAddress address, MediaCancellation cancellation) throws Exception {
        for (int i = 0; i < 5; i++) {
            cancellation.check();
            if (!address.allowed()) throw new IOException("短链接跳转到了非Bilibili地址");
            if (!address.shortLink()) return address;
            address = redirect(address, cancellation);
        }
        throw new IOException("Bilibili短链接跳转超过预算");
    }

    private static BilibiliAddress redirect(BilibiliAddress address, MediaCancellation cancellation) throws Exception {
        HttpURLConnection connection = connection(address.uri);
        connection.setRequestMethod("HEAD");
        try {
            int status = connection.getResponseCode();
            cancellation.check();
            String target = connection.getHeaderField("Location");
            if (status < 300 || status >= 400 || target == null)
                throw new IOException("Bilibili短链接没有提供有效跳转");
            return BilibiliAddress.parse(address.uri.resolve(target).toString());
        } finally { connection.disconnect(); }
    }

    static Map<String, Object> api(String address, MediaCancellation cancellation) throws Exception {
        cancellation.check();
        HttpURLConnection connection = connection(URI.create(address));
        try {
            int status = connection.getResponseCode();
            cancellation.check();
            if (status != 200) throw new IOException("Bilibili接口HTTP " + status + "，可刷新地址重新请求");
            try (InputStream input = connection.getInputStream()) {
                Map<String, Object> response = BilibiliJson.map(BilibiliJson.parse(read(input, cancellation)));
                return data(response);
            }
        } finally { connection.disconnect(); }
    }

    private static String read(InputStream input, MediaCancellation cancellation) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                cancellation.check();
                if (output.size() + count > MAX_BYTES) throw new IOException("Bilibili响应超过字节预算");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> data(Map<String, Object> response) throws IOException {
        if (!response.containsKey("code")) throw new IOException("Bilibili接口响应缺少状态");
        long code = BilibiliJson.number(response.get("code"));
        if (code != 0) throw new IOException("Bilibili拒绝请求（" + code + "）："
                + BilibiliJson.string(response, "message", "内容不可用") + (code == -101 ? "，此内容需要登录" : ""));
        return BilibiliJson.map(response.get("data"));
    }

    static Map<String, String> headers(URI referer) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Referer", referer.toString()); result.put("User-Agent", AGENT);
        return result;
    }

    private static HttpURLConnection connection(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        headers(URI.create("https://www.bilibili.com/")).forEach(connection::setRequestProperty);
        return connection;
    }
}
