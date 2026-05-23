package club.heiqi.uilib.ui.remote;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 服务端下发给客户端 HUD 显示的远程 HTML-like 浮层。
 *
 * <p>浮层内容仍是远程页面相同的安全子集 HTML：不会执行 JavaScript，也不会嵌入真实浏览器。</p>
 */
public final class RemoteHudOverlay {

    public static final long STICKY_DURATION_MILLIS = 0L;
    public static final long DEFAULT_TOAST_DURATION_MILLIS = 3500L;
    public static final long DEFAULT_DANMAKU_DURATION_MILLIS = 8000L;
    public static final long MAX_DURATION_MILLIS = Duration.ofMinutes(10L).toMillis();

    private final String overlayId;
    private final RemoteHudOverlayMode mode;
    private final RemoteDocumentPage page;
    private final long durationMillis;
    private final boolean defaultCloseButtonVisible;
    private final String closeButtonLabel;
    private final Map<String, String> metadata;

    private RemoteHudOverlay(Builder builder) {
        this.overlayId = requireText(builder.overlayId, "overlayId");
        this.mode = builder.mode == null ? RemoteHudOverlayMode.DIALOG : builder.mode;
        this.page = Objects.requireNonNull(builder.page, "page");
        this.durationMillis = clampDuration(builder.durationMillis);
        this.defaultCloseButtonVisible = builder.defaultCloseButtonVisible;
        this.closeButtonLabel = builder.closeButtonLabel == null || builder.closeButtonLabel.trim().isEmpty()
                ? "关闭" : builder.closeButtonLabel.trim();
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.metadata));
    }

    /**
     * 创建需要按钮关闭的远程 HUD 浮窗。
     *
     * @param overlayId 浮层业务标识
     * @param page 远程 HTML 页面
     * @return 构造器
     */
    public static Builder dialog(String overlayId, RemoteDocumentPage page) {
        return builder(overlayId, RemoteHudOverlayMode.DIALOG, page)
                .durationMillis(STICKY_DURATION_MILLIS)
                .defaultCloseButtonVisible(true);
    }

    /**
     * 创建自动消失的远程 HUD 提示。
     *
     * @param overlayId 浮层业务标识
     * @param page 远程 HTML 页面
     * @return 构造器
     */
    public static Builder toast(String overlayId, RemoteDocumentPage page) {
        return builder(overlayId, RemoteHudOverlayMode.TOAST, page)
                .durationMillis(DEFAULT_TOAST_DURATION_MILLIS)
                .defaultCloseButtonVisible(false);
    }

    /**
     * 创建远程 HUD 弹幕。
     *
     * @param overlayId 浮层业务标识
     * @param page 远程 HTML 页面
     * @return 构造器
     */
    public static Builder danmaku(String overlayId, RemoteDocumentPage page) {
        return builder(overlayId, RemoteHudOverlayMode.DANMAKU, page)
                .durationMillis(DEFAULT_DANMAKU_DURATION_MILLIS)
                .defaultCloseButtonVisible(false);
    }

    /**
     * 创建浮层构造器。
     *
     * @param overlayId 浮层业务标识
     * @param mode 展示模式
     * @param page 远程 HTML 页面
     * @return 构造器
     */
    public static Builder builder(String overlayId, RemoteHudOverlayMode mode, RemoteDocumentPage page) {
        return new Builder(overlayId, mode, page);
    }

    public String getOverlayId() {
        return overlayId;
    }

    public RemoteHudOverlayMode getMode() {
        return mode;
    }

    public RemoteDocumentPage getPage() {
        return page;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public boolean isDefaultCloseButtonVisible() {
        return defaultCloseButtonVisible;
    }

    public String getCloseButtonLabel() {
        return closeButtonLabel;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    private static long clampDuration(long durationMillis) {
        if (durationMillis <= 0L) {
            return STICKY_DURATION_MILLIS;
        }
        return Math.min(durationMillis, MAX_DURATION_MILLIS);
    }

    private static String requireText(String value, String label) {
        String resolved = Objects.requireNonNull(value, label).trim();
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return resolved;
    }

    /**
     * 远程 HUD 浮层构造器。
     */
    public static final class Builder {

        private final String overlayId;
        private final RemoteHudOverlayMode mode;
        private final RemoteDocumentPage page;
        private long durationMillis;
        private boolean defaultCloseButtonVisible;
        private String closeButtonLabel = "关闭";
        private final Map<String, String> metadata = new LinkedHashMap<String, String>();

        private Builder(String overlayId, RemoteHudOverlayMode mode, RemoteDocumentPage page) {
            this.overlayId = overlayId;
            this.mode = mode == null ? RemoteHudOverlayMode.DIALOG : mode;
            this.page = page;
            if (this.mode == RemoteHudOverlayMode.TOAST) {
                this.durationMillis = DEFAULT_TOAST_DURATION_MILLIS;
            } else if (this.mode == RemoteHudOverlayMode.DANMAKU) {
                this.durationMillis = DEFAULT_DANMAKU_DURATION_MILLIS;
            } else {
                this.durationMillis = STICKY_DURATION_MILLIS;
                this.defaultCloseButtonVisible = true;
            }
        }

        /**
         * 设置自动消失时长；小于等于 0 表示不自动消失。
         *
         * @param durationMillis 持续毫秒数
         * @return 当前构造器
         */
        public Builder durationMillis(long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        /**
         * 设置自动消失时长。
         *
         * @param duration 持续时间
         * @return 当前构造器
         */
        public Builder duration(Duration duration) {
            this.durationMillis = duration == null ? STICKY_DURATION_MILLIS : duration.toMillis();
            return this;
        }

        /**
         * 设置是否显示默认关闭按钮。
         *
         * @param visible 是否显示
         * @return 当前构造器
         */
        public Builder defaultCloseButtonVisible(boolean visible) {
            this.defaultCloseButtonVisible = visible;
            return this;
        }

        /**
         * 设置默认关闭按钮文案。
         *
         * @param label 按钮文案
         * @return 当前构造器
         */
        public Builder closeButtonLabel(String label) {
            this.closeButtonLabel = label == null ? "" : label;
            return this;
        }

        /**
         * 添加业务元数据。
         *
         * @param name 名称
         * @param value 值
         * @return 当前构造器
         */
        public Builder metadata(String name, String value) {
            if (name != null && !name.trim().isEmpty()) {
                metadata.put(name.trim(), value == null ? "" : value);
            }
            return this;
        }

        /**
         * 创建浮层实例。
         *
         * @return 浮层实例
         */
        public RemoteHudOverlay build() {
            return new RemoteHudOverlay(this);
        }
    }
}
