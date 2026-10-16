package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Consumer;

/** 一次资源换代共用目录索引。只保留元数据，非匹配文件的完整字节不进入代际缓存。 */
public final class FontFamilyResolver {
    private static final class Entry {
        final Path path;
        final int face;

        Entry(Path path, int face) {
            this.path = path;
            this.face = face;
        }
    }

    private final Map<String, List<Entry>> families = new LinkedHashMap<String, List<Entry>>();
    private final Map<Path, FontMetadata> seeds = new HashMap<Path, FontMetadata>();
    private final Map<Path, List<FontSource>> loaded = new HashMap<Path, List<FontSource>>();
    private final long revision;

    public FontFamilyResolver(
            List<Path> configured,
            List<Path> availableDirectories,
            long revision,
            Consumer<String> diagnostics)
            throws IOException {
        this.revision = revision;
        LinkedHashSet<Path> files = new LinkedHashSet<Path>();
        LinkedHashSet<Path> directories = new LinkedHashSet<Path>();
        for (Path p : configured) {
            Path absolute = p.toAbsolutePath().normalize();
            files.add(absolute);
            if (absolute.getParent() != null) directories.add(absolute.getParent());
        }
        for (Path p : availableDirectories) directories.add(p.toAbsolutePath().normalize());
        for (Path directory : directories)
            if (Files.isDirectory(directory)) {
                try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
                    entries.filter(Files::isRegularFile)
                            .filter(FontFamilyResolver::fontFile)
                            .sorted()
                            .forEach(p -> files.add(p.toAbsolutePath().normalize()));
                }
            }
        Set<Path> required = new HashSet<Path>();
        for (Path p : configured) required.add(p.toAbsolutePath().normalize());
        for (Path path : files) {
            try {
                ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(path));
                for (int face = 0, n = FontMetadata.faceCount(bytes); face < n; face++) {
                    FontMetadata metadata = FontMetadata.inspect(bytes, face);
                    families.computeIfAbsent(metadata.family, key -> new ArrayList<Entry>())
                            .add(new Entry(path, face));
                    if (face == 0) seeds.put(path, metadata);
                }
            } catch (IOException | RuntimeException error) {
                if (required.contains(path)) throw new IOException("配置字体元数据读取失败: " + path, error);
                diagnostics.accept("忽略不可解析字体: " + path + " / " + error);
            }
        }
    }

    private static boolean fontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf")
                || name.endsWith(".ttc")
                || name.endsWith(".otf")
                || name.endsWith(".otc");
    }

    /** 配置文件声明face0所属字族；显式FontSource.file仍只表示指定face。 */
    public FontSource family(Path configured, String id) throws IOException {
        Path seed = configured.toAbsolutePath().normalize();
        FontMetadata metadata = seeds.get(seed);
        if (metadata == null) throw new IOException("字体没有可用face0: " + seed);
        List<FontSource> members = new ArrayList<FontSource>();
        for (Entry entry : families.get(metadata.family)) {
            List<FontSource> faces = loaded.get(entry.path);
            if (faces == null) {
                // 文件来源没有脚本/彩色声明；未知提示不排除实际 face，匹配时查询后端事实。
                FontSource source =
                        FontSource.file(
                                entry.path.toString(),
                                revision,
                                entry.path,
                                0,
                                false,
                                Collections.<String>emptySet());
                faces = source.collectionFaces();
                loaded.put(entry.path, faces);
            }
            members.add(faces.get(entry.face));
        }
        return FontSource.family(id, members);
    }
}
