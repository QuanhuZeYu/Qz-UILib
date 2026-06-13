package club.heiqi.config;

/**
 * 配置格式枚举，表示支持的配置文件格式。
 */
public enum ConfigFormat {
    /**
     * JSON 格式
     */
    JSON("json"),

    /**
     * YAML 格式
     */
    YAML("yaml", "yml");

    private final String[] extensions;

    ConfigFormat(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * 获取格式支持的文件扩展名
     * 
     * @return 扩展名数组
     */
    public String[] getExtensions() {
        return extensions;
    }

    /**
     * 根据文件名推断配置格式
     * 
     * @param filename 文件名
     * @return 配置格式，如果无法推断则返回 null
     */
    public static ConfigFormat fromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return null;
        }
        
        String extension = filename.substring(lastDot + 1).toLowerCase();
        
        for (ConfigFormat format : values()) {
            for (String ext : format.extensions) {
                if (ext.equals(extension)) {
                    return format;
                }
            }
        }
        
        return null;
    }
}
