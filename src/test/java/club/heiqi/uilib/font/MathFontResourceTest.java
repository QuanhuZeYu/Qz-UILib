package club.heiqi.uilib.font;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Resource/schema checks only: these do not exercise production glyph generation or pages. */
public class MathFontResourceTest {

    private static final String ROOT = "/assets/qz_uilib/fonts/math/stix-two/";
    private static final String FONT_SHA = "95bc2729e41faf93b0bcae9e96c4dc4da45855067fd0581e621e30734fe8d90b";

    private static byte[] resource(String name) throws Exception {
        try (InputStream input = MathFontResourceTest.class.getResourceAsStream(ROOT + name)) {
            Assert.assertNotNull("Missing bundled resource: " + name, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static JsonObject json(String name) throws Exception {
        return new JsonParser().parse(new String(resource(name), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(Character.forDigit((value & 255) >>> 4, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    @Test
    public void manifestBindsOriginalFontLicenseAndSidecar() throws Exception {
        JsonObject manifest = json("source-manifest.json");
        Assert.assertEquals(1, manifest.get("schemaVersion").getAsInt());
        Assert.assertEquals("OFL-1.1", manifest.get("license").getAsString());
        Assert.assertEquals("2.12 b168", manifest.get("version").getAsString());
        Assert.assertFalse(manifest.get("modified").getAsBoolean());
        Assert.assertEquals(FONT_SHA, sha(resource("STIXTwoMath-Regular.otf")));
        Assert.assertEquals("bec17cee6412788db45fd2644b301afbc99cc5d372ddd3345dc4a7bfdefb9f04",
                sha(resource("OFL.txt")));
        for (Map.Entry<String, JsonElement> entry : manifest.getAsJsonObject("files").entrySet()) {
            byte[] content = resource(entry.getKey());
            JsonObject record = entry.getValue().getAsJsonObject();
            Assert.assertEquals(record.get("bytes").getAsInt(), content.length);
            Assert.assertEquals(record.get("sha256").getAsString(), sha(content));
        }
        Assert.assertEquals(FONT_SHA, json("math-data.json").get("fontSha256").getAsString());
    }

    @Test
    public void schemaKeepsUnicodeGlyphAndDesignUnitBoundaries() throws Exception {
        JsonObject data = json("math-data.json");
        Assert.assertEquals(1, data.get("schemaVersion").getAsInt());
        Assert.assertEquals(0, data.get("faceIndex").getAsInt());
        Assert.assertEquals("stix-two-math-v1", data.get("profile").getAsString());
        Assert.assertEquals(1000, data.get("unitsPerEm").getAsInt());
        int glyphCount = data.get("glyphCount").getAsInt();
        Assert.assertEquals(6760, glyphCount);
        Assert.assertEquals(glyphCount, data.getAsJsonArray("advanceWidths").size());
        JsonObject cmap = data.getAsJsonObject("cmap");
        Assert.assertEquals(4605, cmap.entrySet().size());
        for (Map.Entry<String, JsonElement> entry : cmap.entrySet()) {
            int cp = Integer.parseInt(entry.getKey());
            Assert.assertTrue(Character.isValidCodePoint(cp) && !(cp >= 0xd800 && cp <= 0xdfff));
            glyph(entry.getValue().getAsInt(), glyphCount);
        }
        for (String field : new String[] { "italicCorrections", "topAccentAttachments" }) {
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject(field).entrySet()) {
                glyph(Integer.parseInt(entry.getKey()), glyphCount);
                int value = entry.getValue().getAsInt();
                Assert.assertTrue(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE);
            }
        }
        JsonObject constants = data.getAsJsonObject("constants");
        Assert.assertEquals(56, constants.entrySet().size());
        Assert.assertEquals(258, constants.get("AxisHeight").getAsInt());
        Assert.assertEquals(68, constants.get("RadicalRuleThickness").getAsInt());
        // Raw font value is 55%; runtime must retain the separately agreed 0.5 policy.
        Assert.assertEquals(55, constants.get("ScriptScriptPercentScaleDown").getAsInt());
        Assert.assertEquals(4, data.getAsJsonObject("deviceAdjustments").entrySet().size());
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("deviceAdjustments").entrySet()) {
            JsonObject device = entry.getValue().getAsJsonObject();
            Assert.assertEquals(device.get("endPpem").getAsInt() - device.get("startPpem").getAsInt() + 1,
                    device.getAsJsonArray("deltaPixels").size());
        }
    }

    private static void glyph(int id, int count) {
        Assert.assertTrue("Invalid physical glyph: " + id, id > 0 && id < count);
    }

    @Test
    public void variantsPreserveOrderAndUnsafeUpstreamAssemblyIsExplicit() throws Exception {
        JsonObject data = json("math-data.json");
        int count = data.get("glyphCount").getAsInt();
        int invalid = 0;
        for (String axis : new String[] { "vertical", "horizontal" }) {
            JsonObject constructions = data.getAsJsonObject("constructions").getAsJsonObject(axis);
            for (Map.Entry<String, JsonElement> entry : constructions.entrySet()) {
                glyph(Integer.parseInt(entry.getKey()), count);
                JsonObject construction = entry.getValue().getAsJsonObject();
                int previous = -1;
                for (JsonElement element : construction.getAsJsonArray("variants")) {
                    JsonObject variant = element.getAsJsonObject();
                    glyph(variant.get("glyphId").getAsInt(), count);
                    int advance = variant.get("advance").getAsInt();
                    Assert.assertTrue(advance >= previous);
                    previous = advance;
                }
                if (construction.get("assembly").isJsonNull()) continue;
                JsonObject assembly = construction.getAsJsonObject("assembly");
                boolean valid = true;
                for (JsonElement element : assembly.getAsJsonArray("parts")) {
                    JsonObject part = element.getAsJsonObject();
                    glyph(part.get("glyphId").getAsInt(), count);
                    int full = part.get("fullAdvance").getAsInt();
                    int start = part.get("startConnector").getAsInt();
                    int end = part.get("endConnector").getAsInt();
                    Assert.assertTrue(full > 0 && start >= 0 && end >= 0);
                    valid &= start <= full && end <= full;
                }
                Assert.assertEquals(valid, assembly.get("validConnectorLengths").getAsBoolean());
                if (!valid) {
                    // Upstream U+0305 horizontal recipe contains gid 1425 with 1000/1000/451.
                    // This flag only records the comparison; runtime overlap policy needs separate validation.
                    Assert.assertEquals("horizontal", axis);
                    Assert.assertEquals("746", entry.getKey());
                    invalid++;
                }
            }
        }
        Assert.assertEquals(1, invalid);
        for (int cp : new int[] { 0x302, 0x303 }) {
            String id = data.getAsJsonObject("cmap").get(Integer.toString(cp)).getAsString();
            JsonObject wide = data.getAsJsonObject("constructions").getAsJsonObject("horizontal")
                    .getAsJsonObject(id);
            Assert.assertEquals(6, wide.getAsJsonArray("variants").size());
            Assert.assertTrue(wide.get("assembly").isJsonNull());
        }
    }
}
