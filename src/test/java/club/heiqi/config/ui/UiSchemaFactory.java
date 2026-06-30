package club.heiqi.config.ui;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.SectionSpec;

/**
 * UI 测试用 schema 构造工厂（public，供 config.ui 测试包共享）。
 *
 * <p>与 {@code club.heiqi.config.runtime.SchemaTestFactory} 同构，但因后者包级私有，
 * ui 测试包无法访问，故在本包新建 public 版本。</p>
 */
public final class UiSchemaFactory {

    private UiSchemaFactory() {
    }

    /**
     * 服务端配置 schema，含 4 个字段（STRING / NUMBER / BOOLEAN / CHOICE）。
     *
     * @return ConfigSchema
     */
    public static ConfigSchema serverSchema() {
        return ConfigSchema.builder("test")
                .section("server")
                    .title("Server")
                    .string("host").defaultValue("localhost").required().maxLength(100)
                        .label("Host").helper("server host").build()
                    .number("port").defaultValue(8080).range(1, 65535).required().slider()
                        .label("Port").helper("server port").build()
                    .bool("debug").defaultValue(false)
                        .label("Debug").helper("debug mode").build()
                    .choice("mode").options("online", "offline", "test").defaultValue("online").required()
                        .label("Mode").helper("run mode").build()
                .endSection()
                .build();
    }

    /**
     * 空 schema，无字段。
     *
     * @return ConfigSchema
     */
    public static ConfigSchema emptySchema() {
        return ConfigSchema.builder("test").build();
    }

    /**
     * 多 section 大量字段 schema（5 section × 4 field），用于性能烟雾测试。
     *
     * @return ConfigSchema
     */
    public static ConfigSchema largeSchema() {
        ConfigSchema.Builder b = ConfigSchema.builder("large");
        for (int s = 0; s < 5; s++) {
            SectionSpec.Builder sec = b.section("sec" + s).title("Section " + s);
            for (int f = 0; f < 4; f++) {
                String key = "f" + f;
                switch (f % 4) {
                    case 0:
                        sec.string(key).defaultValue("v" + f).label("Field " + f).build();
                        break;
                    case 1:
                        sec.number(key).defaultValue(Double.valueOf(f)).range(0, 100).label("Field " + f).build();
                        break;
                    case 2:
                        sec.bool(key).defaultValue(f % 2 == 0).label("Field " + f).build();
                        break;
                    case 3:
                        sec.choice(key).options("a", "b", "c").defaultValue("a").label("Field " + f).build();
                        break;
                    default:
                        break;
                }
            }
            sec.endSection();
        }
        return b.build();
    }

    /**
     * 多 CHOICE 选项 schema（>4 项，触发 SceneSelect 分支）。
     *
     * @return ConfigSchema
     */
    public static ConfigSchema manyChoiceSchema() {
        return ConfigSchema.builder("choice")
                .section("opts")
                    .title("Options")
                    .choice("color").options("red", "green", "blue", "yellow", "purple", "cyan")
                        .defaultValue("red").label("Color").build()
                .endSection()
                .build();
    }
}
