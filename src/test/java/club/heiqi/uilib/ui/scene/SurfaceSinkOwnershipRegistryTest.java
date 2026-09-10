package club.heiqi.uilib.ui.scene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/**
 * surface sink 归属注册制守护（零生产改动）。
 *
 * <p>不变式：{@code src/main} 里每一处 surface 写入点（{@code setBackgroundColor} / {@code setBorderColor}）
 * 都必须在注册表 {@code surface_sink_registry.json} 中登记，且其 owner 节点与类别被显式声明；
 * 反向，注册表里每一条登记都必须仍能对应到现存写入点。</p>
 *
 * <p>为什么是注册制而不是正则全覆盖：合法例外族（占位图、scrim、caret/thumb/dot 等元素级轻量通道、
 * 兼容入口本体、页面级设计底色）无法用一条正则分辨，强证「所有 setter 都归 binder」会误杀它们；
 * 注册制把「新写入者」变成必须显式登记的决策点，失守时红灯来自未登记，而不是来自猜测。</p>
 *
 * <p>登记方式：{@code python tools/audit/surface_sink_ownership.py --summary --category review-required}
 * 取得未归类项，人工判类后写回注册表；类别口径见同目录脚本头部说明。
 * 比对键 = 文件 + owner + property；{@code lines} 仅为追溯线索，允许随代码漂移。</p>
 */
public class SurfaceSinkOwnershipRegistryTest {

    private static final String REGISTRY_RELATIVE =
            "src/test/resources/club/heiqi/uilib/ui/scene/surface_sink_registry.json";
    private static final String MAIN_SOURCE_RELATIVE = "src/main/java";

    private static final Pattern ENTRY = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);
    private static final Pattern FIELD_FILE = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FIELD_OWNER = Pattern.compile("\"owner\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern FIELD_PROPERTY = Pattern.compile("\"property\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FIELD_CATEGORY = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CALL = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$.]*)\\s*\\.\\s*(setBackgroundColor|setBorderColor)\\s*\\(");
    private static final Pattern METHOD_REFERENCE = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$.]*)\\s*::\\s*(setBackgroundColor|setBorderColor)\\b");
    private static final Set<String> ALLOWED_CATEGORIES = new LinkedHashSet<String>();
    static {
        Collections.addAll(ALLOWED_CATEGORIES, "binder", "binder-delegated", "designed-static",
                "light-slot", "compat-exempt");
    }

    @Test
    public void everySinkPointIsRegistered() throws IOException {
        Set<String> actual = new LinkedHashSet<String>(scanSinkKeys());
        Set<String> registered = new LinkedHashSet<String>(registeredKeys());
        Assert.assertFalse("未找到组件源码，测试环境异常", actual.isEmpty());

        List<String> unregistered = new ArrayList<String>();
        for (String key : actual) {
            if (!registered.contains(key)) {
                unregistered.add(key);
            }
        }
        Collections.sort(unregistered);
        Assert.assertTrue(describeUnregistered(unregistered), unregistered.isEmpty());
    }

    @Test
    public void registryHasNoStaleEntries() throws IOException {
        Set<String> actual = new LinkedHashSet<String>(scanSinkKeys());
        List<String> stale = new ArrayList<String>();
        for (String key : new LinkedHashSet<String>(registeredKeys())) {
            if (!actual.contains(key)) {
                stale.add(key);
            }
        }
        Collections.sort(stale);
        Assert.assertTrue("注册表存在已消失的登记（删除对应条目即可）：" + stale, stale.isEmpty());
    }

    @Test
    public void registryEntriesDeclareCategories() throws IOException {
        String json = read(registryFile());
        Matcher matcher = ENTRY.matcher(json);
        int entries = 0;
        while (matcher.find()) {
            String block = matcher.group();
            if (!block.contains("\"file\"")) {
                continue;
            }
            entries++;
            String category = value(FIELD_CATEGORY, block);
            Assert.assertNotNull("注册条目缺 category：" + block, category);
            Assert.assertTrue("未知类别 " + category + "（允许：" + ALLOWED_CATEGORIES + "）",
                    ALLOWED_CATEGORIES.contains(category));
        }
        Assert.assertEquals("注册表条目数应为 60；变化时请同步复核本断言", 60, entries);
    }

    private static String describeUnregistered(List<String> unregistered) {
        StringBuilder message = new StringBuilder();
        message.append("发现未登记的 surface 写入点 ").append(unregistered.size()).append(" 处：");
        for (String key : unregistered) {
            message.append('\n').append("  ").append(key);
        }
        message.append('\n').append("处理：若为合法新写入者，请判类后登记到 ").append(REGISTRY_RELATIVE)
                .append("；扫描器：python tools/audit/surface_sink_ownership.py --summary");
        return message.toString();
    }

    private static Set<String> registeredKeys() throws IOException {
        String json = read(registryFile());
        Set<String> keys = new LinkedHashSet<String>();
        Matcher matcher = ENTRY.matcher(json);
        while (matcher.find()) {
            String block = matcher.group();
            if (!block.contains("\"file\"")) {
                continue;
            }
            keys.add(key(value(FIELD_FILE, block), value(FIELD_OWNER, block), value(FIELD_PROPERTY, block)));
        }
        return keys;
    }

    private static Set<String> scanSinkKeys() throws IOException {
        File sourceRoot = new File(repoRoot(), MAIN_SOURCE_RELATIVE);
        Assert.assertTrue("缺少生产源码目录：" + sourceRoot, sourceRoot.isDirectory());
        Set<String> keys = new LinkedHashSet<String>();
        collect(sourceRoot, sourceRoot, keys);
        return keys;
    }

    private static void collect(File sourceRoot, File current, Set<String> keys) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(sourceRoot, child, keys);
                continue;
            }
            if (!child.getName().endsWith(".java")) {
                continue;
            }
            String relative = sourceRoot.toURI().relativize(child.toURI()).getPath();
            String[] lines = read(child).split("\\r?\\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                if (line.indexOf("setBackgroundColor") < 0 && line.indexOf("setBorderColor") < 0) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                Matcher call = CALL.matcher(line);
                List<int[]> callSpans = new ArrayList<int[]>();
                String owner = null;
                String property = null;
                while (call.find()) {
                    owner = call.group(1);
                    property = call.group(2);
                    callSpans.add(new int[] { call.start(), call.end() });
                }
                if (owner == null) {
                    Matcher reference = METHOD_REFERENCE.matcher(line);
                    if (!reference.find()) {
                        continue;
                    }
                    // 确定性 owner 契约：owner 就是字面接收者。方法引用写作 node::setXxx 时，
                    // owner 记为 node 本身，不做「向上猜声明」的启发式（那会让同一行有两种解释）。
                    owner = reference.group(1);
                    property = reference.group(2);
                }
                keys.add(key(relative, owner, property));
            }
        }
    }

    private static String key(String file, String owner, String property) {
        return file + " | " + owner + "." + property;
    }

    private static String value(Pattern pattern, String block) {
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static File registryFile() {
        File file = new File(repoRoot(), REGISTRY_RELATIVE);
        Assert.assertTrue("缺少注册表：" + file, file.isFile());
        return file;
    }

    private static File repoRoot() {
        File cursor = new File("").getAbsoluteFile();
        while (cursor != null) {
            if (new File(cursor, MAIN_SOURCE_RELATIVE).isDirectory()) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        throw new IllegalStateException("未找到仓库根（含 src/main/java 的目录），cwd=" + new File("").getAbsolutePath());
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}