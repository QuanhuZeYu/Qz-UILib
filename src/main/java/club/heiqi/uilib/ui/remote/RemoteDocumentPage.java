package club.heiqi.uilib.ui.remote;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 服务端下发给客户端显示的远程 HTML-like 页面。
 *
 * <p>页面内容是 Qz UILib 安全子集 HTML，不是完整浏览器文档；不会执行 JavaScript。</p>
 */
public final class RemoteDocumentPage {

    private final String pageId;
    private final String title;
    private final String html;
    private final RemoteDocumentResourcePolicy resourcePolicy;
    private final Map<String, String> metadata;

    private RemoteDocumentPage(Builder builder) {
        this.pageId = requireText(builder.pageId, "pageId");
        this.title = builder.title == null ? "" : builder.title;
        this.html = builder.html == null ? "" : builder.html;
        this.resourcePolicy = builder.resourcePolicy == null
                ? RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS : builder.resourcePolicy;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.metadata));
    }

    /**
     * 快速创建远程文档页面。
     *
     * @param pageId 页面业务标识
     * @param title 页面标题
     * @param html 安全子集 HTML
     * @return 远程页面
     */
    public static RemoteDocumentPage of(String pageId, String title, String html) {
        return builder(pageId).title(title).html(html).build();
    }

    /**
     * 创建页面构造器。
     *
     * @param pageId 页面业务标识
     * @return 构造器
     */
    public static Builder builder(String pageId) {
        return new Builder(pageId);
    }

    /**
     * 返回页面业务标识。
     *
     * @return 页面业务标识
     */
    public String getPageId() {
        return pageId;
    }

    /**
     * 返回页面标题。
     *
     * @return 页面标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 返回页面 HTML 文本。
     *
     * @return HTML 文本
     */
    public String getHtml() {
        return html;
    }

    /**
     * 返回资源访问策略。
     *
     * @return 资源策略
     */
    public RemoteDocumentResourcePolicy getResourcePolicy() {
        return resourcePolicy;
    }

    /**
     * 返回业务元数据。
     *
     * @return 只读元数据
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    private static String requireText(String value, String label) {
        String resolved = Objects.requireNonNull(value, label).trim();
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return resolved;
    }

    /**
     * 远程页面构造器。
     */
    public static final class Builder {

        private final String pageId;
        private String title = "";
        private String html = "";
        private RemoteDocumentResourcePolicy resourcePolicy = RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS;
        private final Map<String, String> metadata = new LinkedHashMap<String, String>();

        private Builder(String pageId) {
            this.pageId = pageId;
        }

        /**
         * 设置页面标题。
         *
         * @param title 页面标题
         * @return 当前构造器
         */
        public Builder title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        /**
         * 设置页面 HTML。
         *
         * @param html 安全子集 HTML
         * @return 当前构造器
         */
        public Builder html(String html) {
            this.html = html == null ? "" : html;
            return this;
        }

        /**
         * 设置资源访问策略。
         *
         * @param resourcePolicy 资源访问策略
         * @return 当前构造器
         */
        public Builder resourcePolicy(RemoteDocumentResourcePolicy resourcePolicy) {
            this.resourcePolicy = resourcePolicy == null
                    ? RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS : resourcePolicy;
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
         * 创建页面实例。
         *
         * @return 页面实例
         */
        public RemoteDocumentPage build() {
            return new RemoteDocumentPage(this);
        }
    }
}
