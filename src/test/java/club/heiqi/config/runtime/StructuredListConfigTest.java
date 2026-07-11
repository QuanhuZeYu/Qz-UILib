package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.schema.Values;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 结构化列表 Authority/Draft/YAML 严格边界与 validator 路径测试。 */
public class StructuredListConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static ConfigSchema schema() {
        ValueSpec element = Values.object(
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
        return ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", element)
                .defaultValue(new ArrayList<Map<String, Object>>())
                .build()
                .endSection()
                .build();
    }

    private static Map<String, Object> rule(String id, List<String> members) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("members", new ArrayList<String>(members));
        return value;
    }

    private static void write(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    @Test
    public void authorityLoadsAndSaveRoundTripsUnknownMember() throws Exception {
        File file = tempFolder.newFile("structured.yaml");
        write(file, "general:\n"
                + "  rules:\n"
                + "    - id: primary\n"
                + "      members:\n"
                + "        - alpha\n"
                + "      future:\n"
                + "        enabled: true\n");

        ConfigManager manager = ConfigManager.bootstrap(file, schema());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> loaded = (List<Map<String, Object>>)
                manager.authority().get("general.rules");
        assertEquals("primary", loaded.get(0).get("id"));
        assertTrue(loaded.get(0).containsKey("future"));

        DraftBuffer draft = manager.openDraft();
        List<Map<String, Object>> changed = new ArrayList<Map<String, Object>>(loaded);
        changed.add(rule("secondary", Arrays.asList("beta")));
        draft.setDraft("general.rules", changed);
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode disk = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("true", disk.get("general.rules").get(0).get("future.enabled").asString());
        assertEquals("secondary", disk.get("general.rules").get(1).get("id").asString());
    }

    @Test
    public void authorityRejectsWrongNestedYamlType() throws Exception {
        File file = tempFolder.newFile("invalid-structured.yaml");
        write(file, "general:\n"
                + "  rules:\n"
                + "    - id: primary\n"
                + "      members:\n"
                + "        - 7\n");
        try {
            ConfigManager.bootstrap(file, schema());
            fail("expected strict nested type rejection");
        } catch (ConfigException expected) {
            assertTrue(expected.getMessage().contains("general.rules[0].members[0]"));
        }
    }

    @Test
    public void draftAndValidatorKeepNestedErrorPaths() throws Exception {
        File file = tempFolder.newFile("validator-structured.yaml");
        write(file, "general:\n  rules: []\n");
        ConfigManager manager = ConfigManager.bootstrap(file, schema(), view ->
                ValidationResult.error("general.rules[0].members[1]", "member blocked"));

        DraftBuffer draft = manager.openDraft();
        List<Map<String, Object>> invalid = new ArrayList<Map<String, Object>>();
        invalid.add(rule("primary", Arrays.asList("ok", "blocked")));
        draft.setDraft("general.rules", invalid);
        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertEquals("member blocked", outcome.validation().errorFor("general.rules[0].members[1]"));
        assertNotNull(manager.schema().field("general.rules"));
    }

    @Test
    public void builtInNestedErrorIsRejectedBeforeWriting() throws Exception {
        File file = tempFolder.newFile("invalid-draft.yaml");
        write(file, "general:\n  rules: []\n");
        ConfigManager manager = ConfigManager.bootstrap(file, schema());
        DraftBuffer draft = manager.openDraft();
        Map<String, Object> invalid = rule("primary", Arrays.asList("ok"));
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) invalid.get("members");
        members.add(null);
        draft.setDraft("general.rules", Arrays.<Map<String, Object>>asList(invalid));

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertNotNull(outcome.validation().errorFor("general.rules[0].members[1]"));
        assertEquals(0, ((List<?>) manager.authority().get("general.rules")).size());
    }

    @Test
    public void unknownChoiceStaysInvalidWithoutDiskWriteThenDeletionRoundTrips() throws Exception {
        ValueSpec element = Values.object(Values.member("modes",
                Values.list(Values.choice("alpha", "beta"))));
        ConfigSchema choiceSchema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", element).build().endSection().build();
        File file = tempFolder.newFile("choice-list.yaml");
        write(file, "general:\n  rules: []\n");
        ConfigManager manager = ConfigManager.bootstrap(file, choiceSchema);
        DraftBuffer draft = manager.openDraft();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("modes", Arrays.<Object>asList("alpha", "removed"));
        draft.setDraft("general.rules", Arrays.asList(row));

        SaveOutcome invalid = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, invalid.status());
        assertNotNull(invalid.validation().errorFor("general.rules[0].modes[1]"));
        assertEquals(0, Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML)
                .get("general.rules").asList().size());

        row.put("modes", Arrays.<Object>asList("alpha"));
        draft.setDraft("general.rules", Arrays.asList(row));
        assertTrue(manager.save(draft).isSuccess());
        ConfigManager reloaded = ConfigManager.bootstrap(file, choiceSchema);
        assertEquals(Arrays.asList(row), reloaded.authority().get("general.rules"));
    }
}
