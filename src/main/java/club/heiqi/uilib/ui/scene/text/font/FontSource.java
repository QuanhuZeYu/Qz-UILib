package club.heiqi.uilib.ui.scene.text.font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/**
 * 实际字体字节的只读资源代际。内置资源首次使用才加载；加载一次后保留完整字节快照。
 * 旧计划的 source 不随重载变化；创建新代际时必须创建新 source。系统文件在选择时取快照。
 * 脚本能力由资源提供方显式声明；不从 cmap 或“没有 .notdef”推导连接能力。
 * 字族成员共享不可变字节；脚本能力仍由来源声明，元数据不冒充整形能力。
 */
public final class FontSource {
    public final String id, origin;

    /** 字节 source 为 SHA256，懒资源为 classpath entry 身份；内容摘要调用 contentFingerprint()。 */
    public final String fingerprint;

    public final long revision, faceIndex;
    public final boolean color;
    private volatile byte[] bytes;
    private volatile IOException loadFailure;
    private volatile String contentHash;
    private final ClassLoader loader;
    private final String resourcePath;
    private final Set<String> scripts;
    private FontSource byteOwner;
    private List<FontSource> members;
    private volatile FontMetadata metadata;
    private Map<String, Float> axes = Collections.emptyMap();
    private FontStyle actualStyle;
    private final Map<Map<String, Float>, FontSource> instances =
            new LinkedHashMap<Map<String, Float>, FontSource>();

    public FontSource(
            String id,
            long revision,
            String origin,
            byte[] bytes,
            long faceIndex,
            boolean color,
            Set<String> scripts) {
        this.id = validId(id);
        this.origin = Objects.requireNonNull(origin, "origin");
        this.revision = revision;
        this.faceIndex = faceIndex;
        this.color = color;
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        if (bytes.length == 0 || faceIndex < 0) throw new IllegalArgumentException("无效字体资源");
        this.scripts = scripts(scripts);
        loader = null;
        resourcePath = null;
        fingerprint = hash(this.bytes);
        contentHash = fingerprint;
    }

    private FontSource(
            String id,
            long revision,
            ClassLoader loader,
            String path,
            boolean color,
            Set<String> scripts) {
        this.id = validId(id);
        this.revision = revision;
        this.faceIndex = 0;
        this.color = color;
        this.loader = Objects.requireNonNull(loader, "loader");
        resourcePath = Objects.requireNonNull(path, "path");
        if (path.isEmpty()) throw new IllegalArgumentException("字体resource path为空");
        origin = "classpath:" + path;
        fingerprint = origin;
        this.scripts = scripts(scripts);
    }

    private static String validId(String id) {
        if (Objects.requireNonNull(id, "id").isEmpty())
            throw new IllegalArgumentException("字体id为空");
        return id;
    }

    private static Set<String> scripts(Set<String> scripts) {
        LinkedHashSet<String> copy =
                new LinkedHashSet<String>(Objects.requireNonNull(scripts, "scripts"));
        for (String script : copy) Objects.requireNonNull(script, "script");
        return Collections.unmodifiableSet(copy);
    }

    public static FontSource file(
            String id, long revision, Path path, long faceIndex, boolean color, Set<String> scripts)
            throws IOException {
        return new FontSource(
                id,
                revision,
                path.toAbsolutePath().toString(),
                Files.readAllBytes(path),
                faceIndex,
                color,
                scripts);
    }

    public static FontSource fromResource(
            String id, long revision, String path, boolean color, String... scripts) {
        return new FontSource(
                id,
                revision,
                FontSource.class.getClassLoader(),
                path,
                color,
                new LinkedHashSet<String>(Arrays.asList(scripts)));
    }

    /** loader 的资源内容需按代际保持不变；修改资源后新建 source 并通知服务重载。 */
    public static FontSource resource(
            String id,
            long revision,
            ClassLoader loader,
            String path,
            boolean color,
            Set<String> scripts)
            throws IOException {
        return new FontSource(id, revision, loader, path, color, scripts);
    }

    private byte[] snapshot() {
        if (byteOwner != null) return byteOwner.snapshot();
        byte[] found = bytes;
        if (found != null) return found;
        synchronized (this) {
            if (bytes != null) return bytes;
            if (loadFailure != null)
                throw new IllegalStateException("字体资源载入失败: " + origin, loadFailure);
            try (InputStream in = loader.getResourceAsStream(resourcePath)) {
                if (in == null) throw new IOException("字体资源不存在: " + origin);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                for (int n; (n = in.read(block)) != -1; ) out.write(block, 0, n);
                byte[] loaded = out.toByteArray();
                if (loaded.length == 0) throw new IOException("字体资源为空: " + origin);
                contentHash = hash(loaded);
                bytes = loaded;
                return loaded;
            } catch (IOException error) {
                loadFailure = error;
                throw new IllegalStateException("字体资源载入失败: " + origin, error);
            }
        }
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private FontSource(
            FontSource owner, long faceIndex, Map<String, Float> axes, FontStyle actual) {
        this(owner, faceIndex, axes, actual, owner.id);
    }

    private FontSource(
            FontSource owner,
            long faceIndex,
            Map<String, Float> axes,
            FontStyle actual,
            String id) {
        this.id = validId(id);
        origin = owner.origin;
        revision = owner.revision;
        fingerprint = owner.fingerprint;
        color = owner.color;
        loader = owner.loader;
        resourcePath = owner.resourcePath;
        scripts = owner.scripts;
        this.faceIndex = faceIndex;
        byteOwner = owner;
        this.axes = Collections.unmodifiableMap(new LinkedHashMap<String, Float>(axes));
        actualStyle = actual;
    }

    public FontMetadata metadata() {
        FontMetadata m = metadata;
        if (m == null) {
            m = FontMetadata.inspect(ByteBuffer.wrap(snapshot()), faceIndex);
            metadata = m;
        }
        return m;
    }

    public FontStyle actualStyle() {
        return actualStyle == null ? metadata().style : actualStyle;
    }

    public Map<String, Float> axisValues() {
        return axes;
    }

    /** 显式字族候选，不合并跨候选的同名字体。 */
    public static FontSource family(String id, List<FontSource> members) {
        if (members.isEmpty()) throw new IllegalArgumentException("字族没有成员");
        FontSource first = members.get(0);
        String family = first.metadata().family;
        for (FontSource member : members)
            if (!family.equals(member.metadata().family))
                throw new IllegalArgumentException("字族成员不一致");
        FontSource result =
                new FontSource(first, first.faceIndex, first.axes, first.actualStyle(), id);
        result.members = Collections.unmodifiableList(new ArrayList<FontSource>(members));
        return result;
    }

    /** 集合内成员共享字节，不复制完整TTC。显式face本身不扩展。 */
    public List<FontSource> collectionFaces() {
        List<FontSource> result = new ArrayList<FontSource>();
        for (int i = 0, n = FontMetadata.faceCount(ByteBuffer.wrap(snapshot())); i < n; i++)
            result.add(new FontSource(this, i, Collections.<String, Float>emptyMap(), null));
        return result;
    }

    private synchronized FontSource instance(Map<String, Float> values, FontStyle actual) {
        FontSource found = instances.get(values);
        if (found == null) {
            found = new FontSource(this, faceIndex, values, actual);
            instances.put(found.axes, found);
            if (instances.size() > 128) instances.remove(instances.keySet().iterator().next());
        }
        return found;
    }

    public List<FontSource> resolve(FontStyle request, boolean variableSupported) {
        List<FontSource> ordered = new ArrayList<FontSource>();
        for (FontSource member : members == null ? Collections.singletonList(this) : members) {
            FontMetadata m = member.metadata();
            Map<String, Float> values = new LinkedHashMap<String, Float>();
            FontStyle actual = m.style;
            if (variableSupported && !m.axes.isEmpty()) {
                for (Map.Entry<String, FontMetadata.Axis> a : m.axes.entrySet())
                    values.put(a.getKey(), a.getValue().def);
                int weight = actual.weight;
                FontStyle.Posture posture = actual.posture;
                if (m.axes.containsKey("wght")) {
                    float value = m.axes.get("wght").clamp(request.weight);
                    values.put("wght", value);
                    weight = Math.round(value);
                }
                if (m.axes.containsKey("ital")) {
                    float value =
                            m.axes.get("ital")
                                    .clamp(request.posture == FontStyle.Posture.ITALIC ? 1 : 0);
                    values.put("ital", value);
                    posture = value >= 1 ? FontStyle.Posture.ITALIC : FontStyle.Posture.NORMAL;
                }
                if (m.axes.containsKey("slnt")) {
                    // CSS Fonts默认oblique角度14度；OpenType slnt正负号相反。
                    // ital已给出真实italic时不再额外请求oblique；normal显式回到直立轴值。
                    boolean slanted =
                            request.posture == FontStyle.Posture.OBLIQUE
                                    || request.posture == FontStyle.Posture.ITALIC
                                            && posture != FontStyle.Posture.ITALIC;
                    float value =
                            m.axes.get("slnt")
                                    .clamp(
                                            slanted
                                                    ? -14
                                                    : posture == FontStyle.Posture.ITALIC
                                                            ? m.axes.get("slnt").def
                                                            : 0);
                    values.put("slnt", value);
                    if (value != 0 && posture != FontStyle.Posture.ITALIC)
                        posture = FontStyle.Posture.OBLIQUE;
                }
                actual = new FontStyle(weight, posture);
                ordered.add(member.instance(values, actual));
            } else ordered.add(member);
        }
        Collections.sort(
                ordered,
                (a, b) -> {
                    int p =
                            Integer.compare(
                                    request.postureRank(a.actualStyle().posture),
                                    request.postureRank(b.actualStyle().posture));
                    return p != 0
                            ? p
                            : Integer.compare(
                                    request.weightRank(a.actualStyle().weight),
                                    request.weightRank(b.actualStyle().weight));
                });
        return ordered;
    }

    public Set<String> supportedScripts() {
        return scripts;
    }

    public boolean supportsScript(String script) {
        return scripts.contains(script);
    }

    public boolean isLoaded() {
        return byteOwner != null ? byteOwner.isLoaded() : bytes != null;
    }

    public int byteSize() {
        return snapshot().length;
    }

    public String contentFingerprint() {
        if (byteOwner != null) return byteOwner.contentFingerprint();
        snapshot();
        return contentHash;
    }

    /** 调用线程所属 native 作用域中创建新 face；不共享服务中的可变 face。 */
    public FontFace openFace(FontSession service, float emSize) {
        return service.openFace(
                ByteBuffer.wrap(snapshot()).asReadOnlyBuffer(), faceIndex, emSize, axes);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FontSource)) return false;
        FontSource s = (FontSource) other;
        return id.equals(s.id)
                && revision == s.revision
                && faceIndex == s.faceIndex
                && color == s.color
                && fingerprint.equals(s.fingerprint)
                && loader == s.loader
                && scripts.equals(s.scripts)
                && axes.equals(s.axes)
                && Objects.equals(members, s.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, revision, faceIndex, fingerprint, color, scripts, loader, axes, members);
    }

    @Override
    public String toString() {
        return id + "@" + revision + ":" + faceIndex + " axes=" + axes;
    }
}
