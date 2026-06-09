package club.heiqi.uilib.ui.remote;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 远程 UI 内部协议 DTO 与字段校验。
 */
final class RemoteUiProtocol {

    static final int PROTOCOL_VERSION = 1;
    static final String FEATURE_LEASE_V1 = "remote-ui-lease-v1";
    static final String PAGE_PRIMARY_SURFACE_ID = "primary";

    private RemoteUiProtocol() {}

    /**
     * 远程 UI 控制面消息类型。
     */
    enum MessageType {
        OPEN_SURFACE,
        FETCH_HTML,
        MOUNT_ACK,
        MOUNT_ERROR,
        SUBMIT,
        CLOSE_SURFACE,
        SESSION_EXPIRED,
        RENEW,
        RENEW_ACK,
        RENEW_DENIED,
        RESUME
    }

    /**
     * 客户端承载远程 UI 的表面类型。
     */
    enum SurfaceType {
        PAGE,
        HUD
    }

    /**
     * 显式关闭范围。
     */
    enum CloseScope {
        SESSION,
        SURFACE,
        PLAYER_ALL
    }

    /**
     * lease 策略；默认只启用固定 TTL。
     */
    enum LeasePolicy {
        FIXED,
        RENEWABLE,
        RESUMABLE
    }

    /**
     * 服务端 session 状态。
     */
    enum SessionState {
        CREATED,
        OFFER_SENT,
        ACTIVE,
        CLOSING,
        CLOSED,
        EXPIRED,
        FAILED
    }

    /**
     * 客户端 surface 挂载状态。
     */
    enum ClientSurfaceState {
        PENDING_OPEN,
        FETCHING,
        MOUNTING,
        ACTIVE,
        CLOSING,
        CLOSED,
        STALE,
        ERROR
    }

    /**
     * 服务端打开 surface 的控制面 DTO。
     */
    static class OpenSurfacePayload {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.OPEN_SURFACE.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
        String assetId;
        String sha256;
        int htmlBytes;
        long leaseExpiresAtMillis;
        String leasePolicy = LeasePolicy.FIXED.name();
        Map<String, String> metadata = Collections.emptyMap();
    }

    /**
     * 客户端拉取 HTML asset 的 Stream 请求 DTO。
     */
    static class FetchHtmlRequest {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.FETCH_HTML.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
        String assetId;
    }

    /**
     * 客户端挂载成功回执 DTO。
     */
    static class MountAckPayload {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.MOUNT_ACK.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
        long localMountToken;
    }

    /**
     * 客户端挂载失败回执 DTO。
     */
    static class MountErrorPayload extends MountAckPayload {
        String error;

        MountErrorPayload() {
            messageType = MessageType.MOUNT_ERROR.name();
        }
    }

    /**
     * 表单提交 DTO。
     */
    static class SubmitPayload {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.SUBMIT.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
        String pageId;
        String action;
        String formId;
        Map<String, List<String>> values = Collections.emptyMap();
    }

    /**
     * 关闭或过期通知 DTO。
     */
    static class ClosePayload {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.CLOSE_SURFACE.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
        String closeScope;
        String reason;
    }

    /**
     * 显式续期请求 DTO；第一阶段只建模，不默认启用。
     */
    static class RenewPayload {
        int protocolVersion = PROTOCOL_VERSION;
        String messageType = MessageType.RENEW.name();
        String feature = FEATURE_LEASE_V1;
        String sessionId;
        String surfaceType;
        String surfaceId;
        long contentRevision;
    }

    /**
     * 显式续期响应 DTO；第一阶段只建模，不默认启用。
     */
    static class RenewAckPayload extends RenewPayload {
        long leaseExpiresAtMillis;
        String deniedReason;

        RenewAckPayload() {
            messageType = MessageType.RENEW_ACK.name();
        }
    }

    /**
     * 显式恢复请求 DTO；第一阶段只建模，不默认启用。
     */
    static class ResumePayload extends RenewPayload {
        String resumeToken;

        ResumePayload() {
            messageType = MessageType.RESUME.name();
        }
    }

    /**
     * 编码远程 UI DTO。
     */
    static String toJson(Object payload) {
        return RemoteJson.toJson(payload);
    }

    /**
     * 解码远程 UI DTO。
     */
    static <T> T fromJson(String json, Class<T> type) {
        return RemoteJson.fromJson(json, type);
    }

    /**
     * 校验打开 surface DTO。
     */
    static void validateOpenSurface(OpenSurfacePayload payload) {
        requirePayload(payload, "远程 UI open offer 为空");
        validateProtocol(payload.protocolVersion, payload.messageType, MessageType.OPEN_SURFACE);
        requireSurface(payload.surfaceType, payload.surfaceId);
        requireNonBlank(payload.sessionId, "远程 UI open offer 缺少 sessionId");
        requirePositiveRevision(payload.contentRevision);
        requireNonBlank(payload.assetId, "远程 UI open offer 缺少 assetId");
        requireNonBlank(payload.sha256, "远程 UI open offer 缺少 sha256");
        if (payload.htmlBytes < 0) {
            throw new IllegalArgumentException("远程 UI open offer htmlBytes 无效");
        }
        if (payload.leaseExpiresAtMillis <= 0L) {
            throw new IllegalArgumentException("远程 UI open offer 缺少 leaseExpiresAtMillis");
        }
        parseLeasePolicy(payload.leasePolicy);
        if (payload.metadata == null) {
            payload.metadata = Collections.emptyMap();
        }
    }

    /**
     * 校验 HTML 拉取 DTO。
     */
    static void validateFetchHtml(FetchHtmlRequest payload) {
        requirePayload(payload, "远程 UI HTML 拉取请求为空");
        validateProtocol(payload.protocolVersion, payload.messageType, MessageType.FETCH_HTML);
        requireSurface(payload.surfaceType, payload.surfaceId);
        requireNonBlank(payload.sessionId, "远程 UI HTML 拉取缺少 sessionId");
        requirePositiveRevision(payload.contentRevision);
        requireNonBlank(payload.assetId, "远程 UI HTML 拉取缺少 assetId");
    }

    /**
     * 校验提交 DTO。
     */
    static void validateSubmit(SubmitPayload payload) {
        requirePayload(payload, "远程 UI 提交为空");
        validateProtocol(payload.protocolVersion, payload.messageType, MessageType.SUBMIT);
        requireSurface(payload.surfaceType, payload.surfaceId);
        requireNonBlank(payload.sessionId, "远程 UI 提交缺少 sessionId");
        requirePositiveRevision(payload.contentRevision);
        if (payload.values == null) {
            payload.values = Collections.emptyMap();
        }
    }

    /**
     * 校验关闭 DTO。
     */
    static void validateClose(ClosePayload payload) {
        requirePayload(payload, "远程 UI 关闭 payload 为空");
        validateCloseMessage(payload.protocolVersion, payload.messageType);
        CloseScope scope = parseCloseScope(payload.closeScope);
        requireSurface(payload.surfaceType, payload.surfaceId);
        if (scope == CloseScope.SESSION) {
            requireNonBlank(payload.sessionId, "远程 UI session close 缺少 sessionId");
            requirePositiveRevision(payload.contentRevision);
        }
    }

    /**
     * 解析 surface 类型。
     */
    static SurfaceType parseSurfaceType(String value) {
        try {
            return SurfaceType.valueOf(safe(value).trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("远程 UI surfaceType 无效：" + safe(value), exception);
        }
    }

    /**
     * 解析关闭范围。
     */
    static CloseScope parseCloseScope(String value) {
        try {
            return CloseScope.valueOf(safe(value).trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("远程 UI closeScope 无效：" + safe(value), exception);
        }
    }

    /**
     * 解析 lease 策略。
     */
    static LeasePolicy parseLeasePolicy(String value) {
        String normalized = isBlank(value) ? LeasePolicy.FIXED.name() : value.trim();
        try {
            return LeasePolicy.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("远程 UI leasePolicy 无效：" + normalized, exception);
        }
    }

    /**
     * 判断字符串是否为空白。
     */
    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 返回非 null 文本。
     */
    static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void validateProtocol(int protocolVersion, String messageType, MessageType expectedType) {
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("远程 UI protocolVersion 不支持：" + protocolVersion);
        }
        if (!expectedType.name().equals(messageType)) {
            throw new IllegalArgumentException("远程 UI messageType 不匹配：" + safe(messageType));
        }
    }

    private static void validateCloseMessage(int protocolVersion, String messageType) {
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("远程 UI protocolVersion 不支持：" + protocolVersion);
        }
        if (!MessageType.CLOSE_SURFACE.name().equals(messageType)
                && !MessageType.SESSION_EXPIRED.name().equals(messageType)) {
            throw new IllegalArgumentException("远程 UI close messageType 不匹配：" + safe(messageType));
        }
    }

    private static void requireSurface(String surfaceType, String surfaceId) {
        parseSurfaceType(surfaceType);
        requireNonBlank(surfaceId, "远程 UI surfaceId 不能为空");
    }

    private static void requirePositiveRevision(long contentRevision) {
        if (contentRevision <= 0L) {
            throw new IllegalArgumentException("远程 UI contentRevision 必须为正数");
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePayload(Object payload, String message) {
        if (payload == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
