package club.heiqi.uilib.config.modern;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.FieldRestorePolicy;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link ModernConfigEntry} 恢复默认策略接入测试。
 */
public class ModernConfigEntryRestorePolicyTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String[] savedFontSort;
    private String[] savedCharacterFontRules;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        savedFontSort = FontConfig.fontSort;
        savedCharacterFontRules = FontConfig.characterFontRules;
    }

    @After
    public void tearDown() {
        FontConfig.fontSort = savedFontSort;
        FontConfig.characterFontRules = savedCharacterFontRules;
        ReactiveScheduler.get().reset();
    }

    /**
     * 恢复默认时 fontSort 写入 FontConfig 发现态快照，characterFontRules 保持原草稿不变。
     */
    @Test
    public void restoreDefaultsUsesFontSnapshotAndKeepsCharacterRules() throws Exception {
        FontConfig.fontSort = new String[] {"Detected A", "Detected B"};
        DraftSignalAdapter adapter = buildAdapter("fontSystem:\n"
                + "  fontSort:\n"
                + "    - Configured\n"
                + "  characterFontRules:\n"
                + "    - a=Configured\n");
        try {
            applyModernRestorePolicy(adapter);

            Assert.assertEquals("fontSort draft 应恢复为 FontConfig 发现态快照",
                    Arrays.asList("Detected A", "Detected B"),
                    adapter.draftSignal("fontSystem.fontSort").get());
            Assert.assertTrue("fontSort snapshot != current 时应保持 dirty=true",
                    adapter.dirtySignal("fontSystem.fontSort").get().booleanValue());
            Assert.assertEquals("characterFontRules 应被 skip，草稿保持不变",
                    Arrays.asList("a=Configured"),
                    adapter.draftSignal("fontSystem.characterFontRules").get());
            Assert.assertFalse("characterFontRules 未被恢复默认改动，应保持非 dirty",
                    adapter.dirtySignal("fontSystem.characterFontRules").get().booleanValue());
        } finally {
            adapter.dispose();
        }
    }

    /**
     * fontSort snapshot 与 current 相同时，恢复默认仍走 onFieldEdit，但 dirty 应自然为 false。
     */
    @Test
    public void restoreDefaultsKeepsFontSortCleanWhenSnapshotEqualsCurrent() throws Exception {
        FontConfig.fontSort = new String[] {"Detected A", "Detected B"};
        DraftSignalAdapter adapter = buildAdapter("fontSystem:\n"
                + "  fontSort:\n"
                + "    - Detected A\n"
                + "    - Detected B\n");
        try {
            applyModernRestorePolicy(adapter);

            Assert.assertEquals("fontSort draft 应仍等于发现态快照",
                    Arrays.asList("Detected A", "Detected B"),
                    adapter.draftSignal("fontSystem.fontSort").get());
            Assert.assertFalse("fontSort snapshot == current 时 dirty=false",
                    adapter.dirtySignal("fontSystem.fontSort").get().booleanValue());
        } finally {
            adapter.dispose();
        }
    }

    /**
     * 执行 ModernConfigEntry 注入的恢复默认策略。
     *
     * @param adapter 草稿适配器
     */
    private static void applyModernRestorePolicy(DraftSignalAdapter adapter) {
        FieldRestorePolicy policy = new FieldRestorePolicy();
        ModernConfigEntry.configureRestorePolicy(policy);
        Assert.assertTrue("characterFontRules 应注册为 skip",
                policy.isSkipped("fontSystem.characterFontRules"));
        Consumer<DraftSignalAdapter> custom = policy.getCustom("fontSystem.fontSort");
        Assert.assertNotNull("fontSort 应注册 custom action", custom);
        custom.accept(adapter);
        ReactiveScheduler.get().flush();
    }

    /**
     * 构建测试草稿适配器。
     *
     * @param yaml 初始 YAML 内容
     * @return 草稿适配器
     */
    private DraftSignalAdapter buildAdapter(String yaml) throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        write(file, yaml);
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        return new DraftSignalAdapter(null, manager.openDraft());
    }

    /** 写入测试配置文件。 */
    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
