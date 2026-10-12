package club.heiqi.uilib.ui.config;

/** 配置结构或声明校验失败。无节点的语法错误仍保留输入来源。 */
public final class ConfigException extends IllegalArgumentException {
    private final String path;
    public ConfigException(String path, String message) {
        super(path + ": " + message);
        this.path = path;
    }
    ConfigException(String path, String message, Throwable cause) {
        super(path + ": " + message, cause);
        this.path = path;
    }
    public String path() { return path; }
}
