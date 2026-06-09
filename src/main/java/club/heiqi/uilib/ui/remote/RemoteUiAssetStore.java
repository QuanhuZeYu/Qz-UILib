package club.heiqi.uilib.ui.remote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.api.NetResponse;

/**
 * 远程 UI HTML asset 存储。
 */
final class RemoteUiAssetStore {

    static final NetContentType REMOTE_HTML_CONTENT_TYPE = RemoteHtmlSessionGateway.REMOTE_HTML_CONTENT_TYPE;

    private final Map<String, Asset> assets = new ConcurrentHashMap<String, Asset>();

    /**
     * 保存 UTF-8 HTML 文本。
     */
    Asset putHtml(String html, long expiresAtMillis) {
        byte[] bytes = (html == null ? "" : html).getBytes(StandardCharsets.UTF_8);
        return putBytes(bytes, expiresAtMillis);
    }

    /**
     * 保存 HTML 字节。
     */
    Asset putBytes(byte[] bytes, long expiresAtMillis) {
        String assetId = UUID.randomUUID().toString();
        byte[] safeBytes = bytes == null ? new byte[0] : bytes.clone();
        Asset asset = new Asset(assetId, safeBytes, sha256Hex(safeBytes), expiresAtMillis);
        assets.put(assetId, asset);
        return asset;
    }

    /**
     * 获取 asset。
     */
    Asset get(String assetId) {
        return RemoteUiProtocol.isBlank(assetId) ? null : assets.get(assetId);
    }

    /**
     * 移除 asset。
     */
    Asset remove(String assetId) {
        return RemoteUiProtocol.isBlank(assetId) ? null : assets.remove(assetId);
    }

    /**
     * 清理过期 asset。
     */
    void cleanupExpired(long nowMillis) {
        for (Asset asset : assets.values()) {
            if (asset.isExpired(nowMillis)) {
                assets.remove(asset.getAssetId(), asset);
            }
        }
    }

    /**
     * 清空存储。
     */
    void clear() {
        assets.clear();
    }

    /**
     * 计算 SHA-256 十六进制文本。
     */
    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                String hex = Integer.toHexString(value & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /**
     * Stream 响应数据快照。
     */
    static final class StreamData {

        private final NetContentType contentType;
        private final byte[] bytes;
        private final String sha256;

        private StreamData(NetContentType contentType, byte[] bytes, String sha256) {
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
            this.sha256 = sha256 == null ? "" : sha256;
        }

        NetContentType getContentType() {
            return contentType;
        }

        byte[] getBytes() {
            return bytes.clone();
        }

        int getByteCount() {
            return bytes.length;
        }

        String getSha256() {
            return sha256;
        }

        NetResponse toResponse() {
            return NetResponse.ok(NetBody.of(contentType, bytes));
        }
    }

    /**
     * 保存的 HTML asset。
     */
    static final class Asset {

        private final String assetId;
        private final byte[] bytes;
        private final String sha256;
        private final long expiresAtMillis;

        private Asset(String assetId, byte[] bytes, String sha256, long expiresAtMillis) {
            this.assetId = assetId;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
            this.sha256 = sha256 == null ? "" : sha256;
            this.expiresAtMillis = expiresAtMillis;
        }

        String getAssetId() {
            return assetId;
        }

        byte[] getBytes() {
            return bytes.clone();
        }

        int getByteCount() {
            return bytes.length;
        }

        String getSha256() {
            return sha256;
        }

        long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }

        StreamData toStreamData() {
            return new StreamData(REMOTE_HTML_CONTENT_TYPE, bytes, sha256);
        }
    }
}
