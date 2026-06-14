package club.heiqi.config;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 可变配置测试
 */
public class MutableConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testCreateEmptyConfig() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        
        assertNotNull(config);
        assertEquals(ConfigFormat.JSON, config.getFormat());
        assertNull(config.getSource());
        assertFalse(config.isDirty());
    }

    @Test
    public void testSetAndGet() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);

        // 设置值
        config.set("name", "test");
        config.set("value", 42);
        config.set("enabled", true);

        // 读取值
        assertEquals("test", config.get("name").asString());
        assertEquals(42, config.get("value").asInt());
        assertTrue(config.get("enabled").asBoolean());
        
        // 配置应该被标记为已修改
        assertTrue(config.isDirty());
    }

    @Test
    public void testSetNestedValues() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);

        // 设置嵌套值
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("database.credentials.username", "admin");

        // 读取嵌套值
        assertEquals("localhost", config.get("server.host").asString());
        assertEquals(8080, config.get("server.port").asInt());
        assertEquals("admin", config.get("database.credentials.username").asString());
    }

    @Test
    public void testRemove() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);

        config.set("name", "test");
        config.set("value", 42);

        assertTrue(config.has("name"));
        
        // 移除配置
        config.remove("name");
        
        assertFalse(config.has("name"));
        assertTrue(config.has("value"));
    }

    @Test
    public void testClear() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);

        config.set("name", "test");
        config.set("value", 42);
        config.set("enabled", true);

        // 清空配置
        config.clear();

        assertFalse(config.has("name"));
        assertFalse(config.has("value"));
        assertFalse(config.has("enabled"));
    }

    @Test
    public void testSaveAndLoadJson() throws Exception {
        File file = tempFolder.newFile("config.json");
        
        // 创建并保存配置
        MutableConfig config = Config.createMutable(file, ConfigFormat.JSON);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("debug", true);
        config.save();

        assertFalse(config.isDirty());
        assertTrue(file.exists());

        // 重新加载配置
        MutableConfig loaded = Config.loadMutable(file);
        assertEquals("localhost", loaded.get("server.host").asString());
        assertEquals(8080, loaded.get("server.port").asInt());
        assertTrue(loaded.get("debug").asBoolean());
    }

    @Test
    public void testSaveAndLoadYaml() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        
        // 创建并保存配置
        MutableConfig config = Config.createMutable(file, ConfigFormat.YAML);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("debug", false);
        config.save();

        assertFalse(config.isDirty());
        assertTrue(file.exists());

        // 重新加载配置
        MutableConfig loaded = Config.loadMutable(file);
        assertEquals("localhost", loaded.get("server.host").asString());
        assertEquals(8080, loaded.get("server.port").asInt());
        assertFalse(loaded.get("debug").asBoolean());
    }

    @Test
    public void testReload() throws Exception {
        File file = tempFolder.newFile("config.json");
        
        // 创建并保存初始配置
        MutableConfig config = Config.createMutable(file, ConfigFormat.JSON);
        config.set("version", 1);
        config.save();

        // 修改内存中的配置
        config.set("version", 2);
        assertEquals(2, config.get("version").asInt());

        // 重新加载（应该恢复到文件中的值）
        config.reload();
        assertEquals(1, config.get("version").asInt());
        assertFalse(config.isDirty());
    }

    @Test
    public void testChangeListener() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        
        final AtomicInteger changeCount = new AtomicInteger(0);
        final String[] lastPath = new String[1];

        // 添加监听器
        config.addChangeListener(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                changeCount.incrementAndGet();
                lastPath[0] = event.getPath();
            }
        });

        // 触发变更
        config.set("name", "test");
        assertEquals(1, changeCount.get());
        assertEquals("name", lastPath[0]);

        config.set("value", 42);
        assertEquals(2, changeCount.get());
        assertEquals("value", lastPath[0]);

        config.remove("name");
        assertEquals(3, changeCount.get());
        assertEquals("name", lastPath[0]);
    }

    @Test
    public void testChainedOperations() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);

        // 链式调用
        config.set("name", "test")
              .set("value", 42)
              .set("enabled", true);

        assertEquals("test", config.get("name").asString());
        assertEquals(42, config.get("value").asInt());
        assertTrue(config.get("enabled").asBoolean());
    }

    @Test
    public void testAsImmutable() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("name", "test");
        config.set("value", 42);

        // 转换为不可变节点
        ConfigNode immutable = config.asImmutable();
        
        assertEquals("test", immutable.get("name").asString());
        assertEquals(42, immutable.get("value").asInt());
    }

    @Test
    public void testLoadNonExistentFile() throws Exception {
        File file = new File(tempFolder.getRoot(), "nonexistent.json");
        
        // 加载不存在的文件应该创建空配置
        MutableConfig config = Config.loadMutable(file);
        
        assertNotNull(config);
        assertFalse(config.has("anything"));
        
        // 可以设置值并保存
        config.set("created", true);
        config.save();
        
        assertTrue(file.exists());
    }

    @Test
    public void testDirtyFlag() throws Exception {
        File file = tempFolder.newFile("config.json");
        MutableConfig config = Config.createMutable(file, ConfigFormat.JSON);

        // 初始状态不是脏的
        assertFalse(config.isDirty());

        // 修改后变脏
        config.set("name", "test");
        assertTrue(config.isDirty());

        // 保存后变干净
        config.save();
        assertFalse(config.isDirty());

        // 再次修改后又变脏
        config.set("name", "updated");
        assertTrue(config.isDirty());
    }

    @Test
    public void testComplexStructure() throws Exception {
        File file = tempFolder.newFile("complex.json");
        MutableConfig config = Config.createMutable(file, ConfigFormat.JSON);

        // 设置复杂结构
        config.set("database.primary.host", "db1.example.com");
        config.set("database.primary.port", 3306);
        config.set("database.replica.host", "db2.example.com");
        config.set("database.replica.port", 3306);
        config.set("cache.enabled", true);
        config.set("cache.ttl", 300);

        config.save();

        // 重新加载验证
        MutableConfig loaded = Config.loadMutable(file);
        assertEquals("db1.example.com", loaded.get("database.primary.host").asString());
        assertEquals(3306, loaded.get("database.primary.port").asInt());
        assertEquals("db2.example.com", loaded.get("database.replica.host").asString());
        assertTrue(loaded.get("cache.enabled").asBoolean());
        assertEquals(300, loaded.get("cache.ttl").asInt());
    }
}
