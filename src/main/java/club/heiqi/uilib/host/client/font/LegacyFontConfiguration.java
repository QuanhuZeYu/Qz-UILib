package club.heiqi.uilib.host.client.font;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只读的一次迁移输入；仅接受旧字体入口实际输出的两个 Forge 属性，不调用其带恢复副作用的加载器。 */
public final class LegacyFontConfiguration {
    private static final String ENABLED = "font.replaceVanillaFont";
    private static final String PATHS = "font.systemFontFiles";
    private static final Pattern CATEGORY = Pattern.compile("(?:font|\"font\")\\s*\\{");
    private static final Pattern BOOLEAN = Pattern.compile(
            "B\\s*:\\s*(?:replaceVanillaFont|\"replaceVanillaFont\")\\s*=(.*)");
    private static final Pattern LIST = Pattern.compile(
            "S\\s*:\\s*(?:systemFontFiles|\"systemFontFiles\")\\s*<");

    /** 完整解析成功后才交给配置核心；列表不可被调用者修改。 */
    public static final class Snapshot {
        public final boolean enabled;
        public final List<String> paths;

        private Snapshot(boolean enabled, List<String> paths) {
            this.enabled = enabled;
            this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
        }
    }

    private LegacyFontConfiguration() {}

    /** 保留旧 Forge 实际支持的 BOM 编码，仅只读解码，不沿用其损坏文件恢复。 */
    private static BufferedReader reader(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset encoding = StandardCharsets.UTF_8;
        int start = 0;
        if (bytes.length >= 3 && (bytes[0] & 255) == 239 && (bytes[1] & 255) == 187 && (bytes[2] & 255) == 191) {
            start = 3;
        } else if (bytes.length >= 2 && (bytes[0] & 255) == 254 && (bytes[1] & 255) == 255) {
            encoding = StandardCharsets.UTF_16BE;
            start = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 254) {
            encoding = StandardCharsets.UTF_16LE;
            start = 2;
        } else if (bytes.length >= 4 && bytes[0] == 0 && bytes[1] == 0 && (bytes[2] & 255) == 254 && (bytes[3] & 255) == 255) {
            encoding = Charset.forName("UTF-32BE");
            start = 4;
        }
        String text = encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        return new BufferedReader(new StringReader(text));
    }

    public static Snapshot read(Path file) throws IOException {
        boolean enabled = true;
        boolean category = false;
        boolean categorySeen = false;
        boolean enabledSeen = false;
        boolean pathsSeen = false;
        boolean list = false;
        List<String> paths = new ArrayList<>();
        int lineNumber = 0;
        // Forge.save 写 # Configuration file，不写 encoding 指令；原入口未设置 config version。
        try (BufferedReader reader = reader(file)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && raw.startsWith("\uFEFF")) raw = raw.substring(1);
                String line = raw.trim();
                if (list) {
                    if (line.equals(">")) {
                        list = false;
                    } else if (line.startsWith(">")) {
                        throw error(file, lineNumber, PATHS, "列表闭合符后有多余字符");
                    } else if (line.equals("}") || line.equals("{") || line.equals("<")) {
                        throw error(file, lineNumber, PATHS, "列表未闭合或括号错误");
                    } else if (line.isEmpty()) {
                        throw error(file, lineNumber, PATHS, "字体路径为空");
                    } else {
                        // Forge.load 对列表行 trim；路径内部 #、=、<、> 等字符均为路径数据。
                        paths.add(line);
                    }
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!category) {
                    if (!CATEGORY.matcher(line).matches())
                        throw error(file, lineNumber, "font", "未知语法或类别括号错误: " + line);
                    if (categorySeen) throw error(file, lineNumber, "font", "重复类别");
                    category = true;
                    categorySeen = true;
                    continue;
                }
                if (line.equals("}")) {
                    category = false;
                    continue;
                }
                Matcher value = BOOLEAN.matcher(line);
                if (value.matches()) {
                    if (enabledSeen) throw error(file, lineNumber, ENABLED, "重复键");
                    String text = value.group(1).trim();
                    if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text))
                        throw error(file, lineNumber, ENABLED, "非法 boolean: " + text);
                    enabled = Boolean.parseBoolean(text);
                    enabledSeen = true;
                } else if (LIST.matcher(line).matches()) {
                    if (pathsSeen) throw error(file, lineNumber, PATHS, "重复键");
                    pathsSeen = true;
                    list = true;
                } else {
                    String key = line.contains("systemFontFiles") ? PATHS
                            : line.contains("replaceVanillaFont") ? ENABLED : "font";
                    throw error(file, lineNumber, key, "未知语法或属性括号错误: " + line);
                }
            }
        }
        if (list) throw error(file, lineNumber + 1, PATHS, "文件结束时列表未闭合");
        if (category) throw error(file, lineNumber + 1, "font", "文件结束时类别未闭合");
        return new Snapshot(enabled, paths);
    }

    private static IOException error(Path file, int line, String key, String message) {
        return new IOException("旧字体配置 " + file + ":" + line + " [" + key + "] " + message);
    }
}
