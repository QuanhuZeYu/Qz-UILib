package club.heiqi.config;

/**
 * 基于字符串的配置源实现
 */
class StringConfigSource implements ConfigSource {

    private final String content;
    private final String description;

    StringConfigSource(String content, String description) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        this.content = content;
        this.description = description != null ? description : "string";
    }

    @Override
    public String read() throws ConfigException {
        return content;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
