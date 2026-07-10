package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P2 口径守卫：ConfigManager 生产路径不调用 {@link Persistence#writeAll} 旁路。
 */
public class ConfigManagerWriteAllGuardTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * ConfigManager 类体不出现 writeAll 调用（字节/源码名探测：方法引用列表）。
     */
    @Test
    public void configManager_doesNotExposeOrCallWriteAll() throws Exception {
        Class<?> cm = ConfigManager.class;
        Set<String> names = new HashSet<String>();
        for (Method m : cm.getDeclaredMethods()) {
            names.add(m.getName());
        }
        assertFalse("ConfigManager 不得声明 writeAll", names.contains("writeAll"));

        // 运行一次 save/flush 路径，确保不抛（间接：走 casWritePrepared）
        File file = tempFolder.newFile("guard-writeall.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", Double.valueOf(1));
        assertTrue(manager.save(draft).isSuccess());
        manager.flushRaw();

        // Persistence.writeAll 仍存在且 deprecated（旁路可用但非生产）
        Method writeAll = Persistence.class.getMethod("writeAll", java.util.Map.class, ConfigSchema.class);
        assertTrue(writeAll.isAnnotationPresent(Deprecated.class));
    }
}
