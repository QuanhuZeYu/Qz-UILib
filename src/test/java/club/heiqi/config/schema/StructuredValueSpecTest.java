package club.heiqi.config.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** ValueSpec 递归 schema、默认值冻结、未知 member 与嵌套路径测试。 */
public class StructuredValueSpecTest {

    private static ValueSpec ruleSpec() {
        return Values.object(
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
    }

    private static Map<String, Object> rule(Object id, Object members) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("members", members);
        return value;
    }

    @Test
    public void structuredListBuilderUsesRecursiveSpecAndDefaults() {
        List<Map<String, Object>> defaults = new ArrayList<Map<String, Object>>();
        defaults.add(rule("primary", Arrays.<Object>asList("alpha", "beta")));

        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", ruleSpec())
                .defaultValue(defaults)
                .build()
                .endSection()
                .build();

        FieldSpec field = schema.field("general.rules");
        assertEquals(FieldType.STRUCTURED_LIST, field.type());
        assertEquals(ValueKind.LIST, field.valueSpec().kind());
        assertEquals(defaults, field.defaultValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> returned = (List<Map<String, Object>>) field.defaultValue();
        try {
            returned.add(rule("second", Arrays.<Object>asList("x")));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("structured default must be immutable");
    }

    @Test
    public void normalizeFillsDeclaredMembersAndPreservesUnknownMembers() {
        Map<String, Object> raw = rule("primary", Arrays.<Object>asList("alpha"));
        raw.put("future", new LinkedHashMap<String, Object>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> normalizedRows = (List<Map<String, Object>>)
                Values.list(ruleSpec()).normalize(Arrays.<Object>asList(raw));
        Map<String, Object> normalized = normalizedRows.get(0);
        assertEquals("primary", normalized.get("id"));
        assertEquals(Arrays.asList("alpha"), normalized.get("members"));
        assertTrue(normalized.containsKey("future"));
    }

    @Test
    public void nestedValidationAndPathAcceptanceAreStrict() {
        ValueSpec list = Values.list(ruleSpec());
        Map<String, Object> invalid = rule("primary", Arrays.<Object>asList("ok", Integer.valueOf(2)));
        ValueSpec.Validation validation = list.validate(Arrays.<Object>asList(invalid), "rules");

        assertEquals("值必须是字符串", validation.errors().get("rules[0].members[1]"));
        assertTrue(list.acceptsPath("[0].members[1]"));
        assertFalse(list.acceptsPath("[0].future"));
        assertFalse(list.acceptsPath(".members[1]"));
    }

    @Test
    public void legacyChoiceSpecRemainsUsableWithoutRecursiveOptions() {
        FieldSpec field = new FieldSpec(
                "general.mode", FieldType.CHOICE, "legacy",
                FieldConstraints.none(), null, null, null);
        assertEquals(ValueKind.CHOICE, field.valueSpec().kind());
        assertFalse(field.valueSpec().validate("legacy", "general.mode").hasErrors());
    }

    @Test
    public void identityMemberAcceptsAllStableComparableScalarKinds() {
        assertEquals(ValueKind.STRING, identity(Values.string()).member("identity").spec().kind());
        assertEquals(ValueKind.NUMBER, identity(Values.number()).member("identity").spec().kind());
        assertEquals(ValueKind.BOOLEAN, identity(Values.bool()).member("identity").spec().kind());
        assertEquals(ValueKind.CHOICE,
                identity(Values.choice("alpha", "beta")).member("identity").spec().kind());
    }

    @Test
    public void identityMemberRejectsListWithClearSchemaError() {
        assertIdentityKindRejected(Values.list(Values.string()), "LIST");
    }

    @Test
    public void identityMemberRejectsObjectWithClearSchemaError() {
        assertIdentityKindRejected(Values.object(Values.member("nested", Values.string())), "OBJECT");
    }

    private static ValueSpec identity(ValueSpec identitySpec) {
        return Values.objectWithIdentity("identity", Values.member("identity", identitySpec));
    }

    private static void assertIdentityKindRejected(ValueSpec identitySpec, String kind) {
        try {
            identity(identitySpec);
            fail("identity member of kind " + kind + " must be rejected during schema construction");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("stable comparable scalar"));
            assertTrue(expected.getMessage().contains(kind));
        }
    }
}
