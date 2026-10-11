package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;

/** 字体模块的完整候选声明；配置语义归核心，来源字节与字族解析由适配提供。
 * 合同见字体规格第 2.4 节与无平台配置规格第 3 节。 */
public final class FontCandidateConfiguration {
    public static final String KEY = "font.candidates";

    /** 系统文件按明确声明解析；不得借此追加其他候选或改变跨候选顺序。 */
    public interface FileResolver {
        FontSource resolve(String path, int position) throws IOException;
    }

    private FontCandidateConfiguration() {}

    public static ConfigKey<List<String>> key(List<FontSource> builtins) {
        final Map<String, FontSource> available = index(builtins);
        return new ConfigKey<>(KEY, ConfigCodecs.STRING_LIST, defaults(builtins), values -> {
            validate(values, available);
            return true;
        }).withComment("完整有序字体候选：builtin:<内置来源id> 或 file:<字体路径>；空列表不追加默认字体。");
    }

    public static List<String> defaults(List<FontSource> builtins) {
        List<String> result = new ArrayList<>();
        for (FontSource source : builtins) result.add("builtin:" + source.id);
        return Collections.unmodifiableList(result);
    }

    /** 仅供宿主一次迁移：把旧前缀的实际默认尾链显式写入新声明。 */
    public static List<String> migrate(List<String> oldPaths, List<FontSource> builtins) {
        List<String> result = new ArrayList<>();
        for (String path : oldPaths) result.add("file:" + Objects.requireNonNull(path, "path"));
        result.addAll(defaults(builtins));
        validate(result, index(builtins));
        return Collections.unmodifiableList(result);
    }

    public static List<String> files(List<String> declarations) {
        List<String> result = new ArrayList<>();
        for (String declaration : declarations) {
            if (declaration.startsWith("file:")) result.add(declaration.substring(5));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<FontSource> resolve(
            List<String> declarations, List<FontSource> builtins, FileResolver files)
            throws IOException {
        Map<String, FontSource> available = index(builtins);
        validate(declarations, available);
        List<FontSource> result = new ArrayList<>();
        for (int i = 0; i < declarations.size(); i++) {
            String declaration = declarations.get(i);
            if (declaration.startsWith("builtin:")) {
                result.add(available.get(declaration.substring(8)));
            } else {
                try {
                    result.add(Objects.requireNonNull(files.resolve(declaration.substring(5), i), "font"));
                } catch (IOException error) {
                    throw new IOException(KEY + "[" + i + "] 来源读取失败: " + declaration, error);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, FontSource> index(List<FontSource> builtins) {
        Map<String, FontSource> result = new LinkedHashMap<>();
        for (FontSource source : builtins) {
            if (result.put(source.id, source) != null)
                throw new IllegalArgumentException("重复内置字体来源: " + source.id);
        }
        return result;
    }

    private static void validate(List<String> declarations, Map<String, FontSource> builtins) {
        Objects.requireNonNull(declarations, KEY);
        for (int i = 0; i < declarations.size(); i++) {
            String value = declarations.get(i);
            String error = KEY + "[" + i + "] 无效候选: " + value;
            if (value == null) throw new IllegalArgumentException(error);
            if (value.startsWith("builtin:")) {
                if (!builtins.containsKey(value.substring(8))) throw new IllegalArgumentException(error);
            } else if (!value.startsWith("file:") || value.substring(5).trim().isEmpty()) {
                throw new IllegalArgumentException(error);
            }
        }
    }
}
