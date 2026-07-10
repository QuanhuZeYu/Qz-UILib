package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P2 口径守卫：ConfigManager 生产路径不调用 {@link Persistence#writeAll} 旁路。
 *
 * <p>以 {@link Persistence#writeAllCallCountForTest()} 调用计数为准——
 * 禁止仅查同名方法声明。</p>
 */
public class ConfigManagerWriteAllGuardTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void resetCounter() {
        Persistence.resetWriteAllCallCountForTest();
    }

    @After
    public void clearCounter() {
        Persistence.resetWriteAllCallCountForTest();
    }

    /**
     * save / flushRaw / reload 路径 writeAll 调用计数必须为 0。
     */
    @Test
    public void configManager_saveFlushReload_writeAllCallCountZero() throws Exception {
        File file = tempFolder.newFile("guard-writeall.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", Double.valueOf(1));
        assertTrue(manager.save(draft).isSuccess());
        manager.flushRaw();
        DraftBuffer reloaded = manager.reloadDraftFromDisk();
        assertTrue(manager.owns(reloaded));

        assertEquals("生产 ConfigManager 不得调用 Persistence.writeAll",
                0L, Persistence.writeAllCallCountForTest());

        // Persistence.writeAll 仍存在且 deprecated（旁路可用但非生产）
        Method writeAll = Persistence.class.getMethod("writeAll", java.util.Map.class, ConfigSchema.class);
        assertTrue(writeAll.isAnnotationPresent(Deprecated.class));
    }

    /**
     * 直接调用 writeAll 会计数（证明 hook 有效，非空探针）。
     */
    @Test
    public void writeAll_hookCountsDirectCall() throws Exception {
        File file = tempFolder.newFile("guard-writeall-direct.yaml");
        Persistence persistence = new Persistence(file, club.heiqi.config.ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        java.util.Map<String, Object> typed = new java.util.HashMap<String, Object>();
        typed.put("server.host", "localhost");
        typed.put("server.port", Double.valueOf(8080));
        typed.put("server.debug", Boolean.FALSE);
        typed.put("server.mode", "online");
        persistence.writeAll(typed, schema);
        assertEquals(1L, Persistence.writeAllCallCountForTest());
    }
}
