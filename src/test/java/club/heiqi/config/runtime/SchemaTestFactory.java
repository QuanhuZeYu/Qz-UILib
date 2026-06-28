package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;

/**
 * 测试用 schema 构造工厂。
 *
 * <p>使用 schema 包的 Builder DSL 链式 API 构造测试 schema。
 * 提供两个典型 schema：</p>
 * <ul>
 *   <li>{@link #serverSchema()}：含 server.host(STRING)、server.port(NUMBER)、server.debug(BOOLEAN)、
 *       server.mode(CHOICE) 四个字段，带约束。</li>
 *   <li>{@link #emptySchema()}：无字段的空 schema，用于补默认测试。</li>
 * </ul>
 */
final class SchemaTestFactory {

    private SchemaTestFactory() {
    }

    /**
     * 服务端配置 schema，含 4 个字段。
     *
     * @return ConfigSchema
     */
    static ConfigSchema serverSchema() {
        return ConfigSchema.builder("test")
                .section("server")
                    .title("Server")
                    .string("host").defaultValue("localhost").required().maxLength(100).label("Host").helper("server host").build()
                    .number("port").defaultValue(8080).range(1, 65535).required().label("Port").helper("server port").build()
                    .bool("debug").defaultValue(false).label("Debug").helper("debug mode").build()
                    .choice("mode").options("online", "offline", "test").defaultValue("online").required().label("Mode").helper("run mode").build()
                .endSection()
                .build();
    }

    /**
     * 空 schema，无字段。
     *
     * @return ConfigSchema
     */
    static ConfigSchema emptySchema() {
        return ConfigSchema.builder("test").build();
    }
}
