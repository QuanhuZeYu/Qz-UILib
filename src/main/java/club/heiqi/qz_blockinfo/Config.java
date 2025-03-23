package club.heiqi.qz_blockinfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Config {
    public static final Logger LOG = LogManager.getLogger();
    public static final String MOD_ID = ConstField.MODID;
    public static final File CONFIG_FILE = new File(System.getProperty("user.dir"),
        "config" + File.separator + MOD_ID+".yml");

    // ==================== 配置字段 ====================
    public static final String GENERAL = "general";
    public static Map<String, List<Field>> cate = new LinkedHashMap<>();
    public static ConfigInfo greeting = new ConfigInfo("greeting", "打招呼").set("你好世界", "你好世界");
    static {
        try {
            cate.put(GENERAL, Arrays.asList(Config.class.getDeclaredField(greeting.fieldName)));

            cate.forEach((cateName, fields) -> {
                fields.forEach(field -> {
                    field.setAccessible(true);
                });
            });
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 如果没有配置文件则创建一个配置文件
     * 该方法调用后 将该类内的字段保存到配置文件中
     */
    public static void saveConfig() {
        // 构建配置数据的 Map 结构
        Map<String, Object> configMap = new LinkedHashMap<>(); // 最终保存的map
        // 遍历 分类-反射字段列表
        for (Map.Entry<String, List<Field>> entry : cate.entrySet()) {
            List<Field> infos = entry.getValue();
            List<Map> configs = new ArrayList<>(); // 构造map分类下的列表
            // 遍历反射字段列表
            for (Field info : infos) {
                Map infoMap = null;
                try {
                    infoMap = ((ConfigInfo)info.get(Config.class)).genMap();
                } catch (IllegalAccessException e) {
                    LOG.error(e);
                }
                configs.add(infoMap);
            }
            configMap.put(entry.getKey(), configs);
        }

        // 配置 YAML 输出格式（美化排版）
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        // 写入 YAML 文件
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            yaml.dump(configMap, writer);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * 从配置中读取
     */
    public static void loadConfig() {
        createDefaultConfig();
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> configMap = new HashMap<>();
            try {
                configMap = yaml.load(new FileReader(CONFIG_FILE));
            } catch (FileNotFoundException e) {
                LOG.error("加载配置时出现异常 {}", e.getMessage());
            }
            // 遍历文件中 分类
            configMap.forEach((cate, fieldList) -> {
                if (!Config.cate.containsKey(cate)) return; // 类中没有文件中的分类时跳过
                // 遍历该分类下的列表
                ((List<Map<String, Object>>)fieldList).forEach(configs -> {
                    String fieldName = configs.keySet().iterator().next(); // 第一个为字段名称
                    ConfigInfo fileInfo = ConfigInfo.mapToConfig(configs);
                    LOG.info(fileInfo);
                    for (List<Field> value : Config.cate.values()) {
                        for (Field field : value) {
                            if (field.getName().equals(fieldName)) {
                                try {
                                    field.set(Config.class, fileInfo);
                                } catch (IllegalAccessException e) {
                                    LOG.error("赋值时出现错误: {}", e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                });
            });
        } catch (Exception e) {
            LOG.error("加载配置文件失败: {}", e.getMessage());
        }
    }

    public static void createDefaultConfig() {
        // 如果配置文件存在则跳过创建
        try {
            if (CONFIG_FILE.exists()) return;
        } catch (Exception e) {
            LOG.error(e);
        }
        try {
            // 确保配置文件目录存在
            if (!CONFIG_FILE.getParentFile().exists()) {
                Files.createDirectories(CONFIG_FILE.getParentFile().toPath());
            }
            saveConfig();
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    public static class ConfigInfo {
        public static final List<ConfigInfo> allEntry = new ArrayList<>();
        public final String fieldName;
        public Object value;
        public final String description;
        public Object defaultValue;
        @Nullable public Object minValue;
        @Nullable public Object maxValue;

        public ConfigInfo(String fieldName, String description) {
            this.fieldName = fieldName;
            this.description = description;
            allEntry.add(this);
        }
        public ConfigInfo setValue(Object value) {
            this.value = value; return this;
        }
        public ConfigInfo setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue; return this;
        }
        public ConfigInfo setMinValue(Object minValue) {
            this.minValue = minValue; return this;
        }
        public ConfigInfo setMaxValue(Object maxValue) {
            this.maxValue = maxValue; return this;
        }
        public ConfigInfo set(Object value, Object defaultValue) {
            this.value = value; this.defaultValue = defaultValue;
            return this;
        }
        public Map genMap() {
            Map map = new LinkedHashMap();
            Map values = new LinkedHashMap();
            values.put("description", description);
            values.put("defaultValue", defaultValue);
            values.put("value", value);
            if (minValue != null) values.put("min", minValue);
            if (maxValue != null) values.put("max", maxValue);
            map.put(fieldName, values);
            return map;
        }
        public static ConfigInfo mapToConfig(Map<String, Object> map) {
            String fieldName = map.keySet().iterator().next();
            Map values = (Map) map.get(fieldName);
            if (values == null) {
                LOG.error("无法加载该对象为ConfigInfo");
                return null;
            }
            String description = (String) values.get("description");
            Object defaultValue = values.get("defaultValue");
            Object value = values.get("value");
            Object minValue = values.get("min");
            Object maxValue = values.get("max");
            return new ConfigInfo(fieldName, description).set(value, defaultValue)
                .setMinValue(minValue).setMaxValue(maxValue);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ConfigInfo [fieldName=");
            builder.append(fieldName);
            builder.append(", value=");
            builder.append(value);
            builder.append(", defaultValue=");
            builder.append(defaultValue);
            builder.append(", minValue=");
            builder.append(minValue);
            builder.append(", maxValue=");
            builder.append(maxValue);
            builder.append("]");
            return builder.toString();
        }
    }

    public static void main(String[] args) {

    }
}
