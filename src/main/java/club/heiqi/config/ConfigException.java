package club.heiqi.config;

/**
 * 配置异常，用于表示配置加载、解析或访问过程中的错误。
 *
 * <p>可选结构化 {@link Category} 供上层分类（reload/UI），禁止依赖英文 message 子串匹配。</p>
 */
public class ConfigException extends Exception {

    /**
     * 结构化错误分类（可选；未指定时为 {@link Category#UNSPECIFIED}）。
     */
    public enum Category {
        /** 未分类（兼容旧构造） */
        UNSPECIFIED,
        /** 类型/约束/schema 校验失败 */
        VALIDATION,
        /** 读盘/解析/非普通文件等 IO */
        IO,
        /** 并发/基线/写前检测冲突 */
        CONFLICT
    }

    private final Category category;

    public ConfigException(String message) {
        this(message, null, Category.UNSPECIFIED);
    }

    public ConfigException(String message, Throwable cause) {
        this(message, cause, Category.UNSPECIFIED);
    }

    public ConfigException(Throwable cause) {
        this(cause == null ? null : cause.getMessage(), cause, Category.UNSPECIFIED);
    }

    /**
     * @param message  诊断信息
     * @param category 结构化分类，null 视为 UNSPECIFIED
     */
    public ConfigException(String message, Category category) {
        this(message, null, category);
    }

    /**
     * @param message  诊断信息
     * @param cause    原因
     * @param category 结构化分类，null 视为 UNSPECIFIED
     */
    public ConfigException(String message, Throwable cause, Category category) {
        super(message, cause);
        this.category = category == null ? Category.UNSPECIFIED : category;
    }

    /**
     * @return 结构化分类，非 null
     */
    public Category category() {
        return category;
    }
}
