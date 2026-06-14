package club.heiqi.config;

/**
 * 配置异常，用于表示配置加载、解析或访问过程中的错误。
 */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigException(Throwable cause) {
        super(cause);
    }
}
