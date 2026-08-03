package club.heiqi.uilib.font;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.uilib.font.config.FontCharacterRuleSet;

/** 自定义字体目录 fingerprint 必须读取内容，而不只依赖路径或长度。 */
public class FontResourceFingerprintTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void assetFingerprintChangesWhenSameLengthContentChanges() throws Exception {
        File fontDirectory = temporaryFolder.newFolder("fonts");
        File fontFile = new File(fontDirectory, "custom.ttf");
        write(fontFile, new byte[] { 1, 2, 3, 4 });
        FontGenerationBuildRequest request = request(fontDirectory);

        FontResourceFingerprint first = FontResourceSnapshot.capture(request).getFingerprint();
        FontResourceFingerprint repeated = FontResourceSnapshot.capture(request).getFingerprint();
        Assert.assertEquals(first, repeated);

        File movedDirectory = temporaryFolder.newFolder("other-fonts");
        write(new File(movedDirectory, "custom.ttf"), new byte[] { 1, 2, 3, 4 });
        Assert.assertEquals("绝对目录不得进入资源内容 identity", first,
                FontResourceSnapshot.capture(request(movedDirectory)).getFingerprint());

        File renamedDirectory = temporaryFolder.newFolder("renamed-fonts");
        write(new File(renamedDirectory, "renamed.ttf"), new byte[] { 1, 2, 3, 4 });
        Assert.assertNotEquals("相对文件名必须进入资源内容 identity", first,
                FontResourceSnapshot.capture(request(renamedDirectory)).getFingerprint());

        write(fontFile, new byte[] { 4, 3, 2, 1 });
        FontResourceFingerprint changed = FontResourceSnapshot.capture(request).getFingerprint();

        Assert.assertNotEquals(first, changed);
    }

    @Test
    public void assetFontSortIsDeterministicForInjectedEnumerationOrder() {
        List<File> files = new ArrayList<File>();
        files.add(new File("b.ttf"));
        files.add(new File("a.ttf"));
        files.add(new File("A.ttf"));

        FontResourceSnapshot.sortAssetFontFiles(files);

        Assert.assertEquals("A.ttf", files.get(0).getName());
        Assert.assertEquals("a.ttf", files.get(1).getName());
        Assert.assertEquals("b.ttf", files.get(2).getName());
    }

    @Test
    public void productionSnapshotLimitsRemainBoundedAndCompatible() {
        Assert.assertEquals(256, FontResourceSnapshot.MAX_ASSET_FONT_FILES);
        Assert.assertEquals(128L * 1024L * 1024L, FontResourceSnapshot.MAX_ASSET_FONT_FILE_BYTES);
        Assert.assertEquals(256L * 1024L * 1024L, FontResourceSnapshot.MAX_ASSET_FONT_TOTAL_BYTES);
    }

    @Test
    public void snapshotRejectsUnboundedAssetFontFileCount() throws Exception {
        File fontDirectory = temporaryFolder.newFolder("too-many-fonts");
        for (int index = 0; index <= FontResourceSnapshot.MAX_ASSET_FONT_FILES; index++) {
            Assert.assertTrue(new File(fontDirectory, "font-" + index + ".ttf").createNewFile());
        }

        try {
            FontResourceSnapshot.capture(request(fontDirectory));
            Assert.fail("字体资源文件数不得无界增长");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("文件数量超过上限"));
        }
    }

    @Test
    public void buildRequestCaptureDefersColdDirectoryCreationToSnapshot() throws Exception {
        File gameRoot = temporaryFolder.newFolder("game-root");
        File fontDirectory = new File(gameRoot, "fonts");

        FontGenerationBuildRequest request = FontGenerationBuildRequest.capture(0L, null, gameRoot);

        Assert.assertFalse("render owner 冻结 request 时不得创建目录", fontDirectory.exists());
        Assert.assertTrue(request.shouldCreateFontDirectoryIfMissing());

        FontResourceSnapshot.capture(request, 1, 8L, 8L);

        Assert.assertTrue("冷启动 snapshot 阶段负责创建首次字体目录", fontDirectory.isDirectory());
    }

    @Test
    public void snapshotRejectsPerFileAndAggregateByteLimits() throws Exception {
        File oversizedDirectory = temporaryFolder.newFolder("oversized-fonts");
        write(new File(oversizedDirectory, "oversized.ttf"), new byte[] { 1, 2 });
        assertSnapshotCaptureFails(request(oversizedDirectory), 1, 1L, 2L);

        File aggregateDirectory = temporaryFolder.newFolder("aggregate-fonts");
        write(new File(aggregateDirectory, "a.ttf"), new byte[] { 1, 2 });
        write(new File(aggregateDirectory, "b.ttf"), new byte[] { 3, 4 });
        assertSnapshotCaptureFails(request(aggregateDirectory), 2, 2L, 3L);
    }

    @Test
    public void stableReadRejectsRewriteTruncationAndGrowthAfterFirstRead() throws Exception {
        assertStableReadRejectsMutation("rewritten.ttf", new byte[] { 1, 2, 3, 4 },
                new byte[] { 4, 3, 2, 1 }, true);
        assertStableReadRejectsMutation("truncated.ttf", new byte[] { 1, 2, 3, 4 },
                new byte[] { 1, 2 }, false);
        assertStableReadRejectsMutation("grown.ttf", new byte[] { 1, 2, 3, 4 },
                new byte[] { 1, 2, 3, 4, 5 }, false);
    }

    @Test
    public void snapshotFailsWhenFontResourceDirectoryCannotBeEnumerated() throws Exception {
        File fontPath = temporaryFolder.newFile("fonts");

        assertSnapshotCaptureFails(fontPath);
    }

    @Test
    public void snapshotFailsWhenFrozenFontResourceDirectoryDisappears() throws Exception {
        File fontPath = new File(temporaryFolder.getRoot(), "removed-fonts");
        Assert.assertFalse(fontPath.exists());

        assertSnapshotCaptureFails(fontPath);
    }

    private void assertSnapshotCaptureFails(File fontPath) {
        try {
            FontResourceSnapshot.capture(request(fontPath));
            Assert.fail("字体资源目录无法枚举时不得提交空 snapshot");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("无法枚举字体资源目录"));
        }
    }

    private void assertSnapshotCaptureFails(FontGenerationBuildRequest request, int maxFiles,
            long maxFileBytes, long maxTotalBytes) {
        try {
            FontResourceSnapshot.capture(request, maxFiles, maxFileBytes, maxTotalBytes);
            Assert.fail("字体资源 bytes 不得越过 snapshot 上限");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("无法冻结字体资源"));
            Assert.assertNotNull(expected.getCause());
            Assert.assertTrue(expected.getCause().getMessage().contains("snapshot bytes 上限"));
        }
    }

    private void assertStableReadRejectsMutation(String fileName, byte[] initialContent,
            byte[] replacementContent, boolean restoreModifiedTime) throws Exception {
        File fontFile = new File(temporaryFolder.getRoot(), fileName);
        write(fontFile, initialContent);
        FileTime originalModifiedTime = Files.getLastModifiedTime(fontFile.toPath());
        try {
            FontResourceSnapshot.readStableBytes(fontFile, 16L,
                    () -> mutate(fontFile, replacementContent,
                            restoreModifiedTime ? originalModifiedTime : null));
            Assert.fail("首次读取后的字体资源变化必须使 snapshot 失败");
        } catch (IOException expected) {
            String message = expected.getMessage();
            Assert.assertTrue(message.contains("发生变化") || message.contains("截断")
                    || message.contains("增长"));
        }
    }

    private FontGenerationBuildRequest request(File fontDirectory) {
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.empty());
        return new FontGenerationBuildRequest(1L, 1, 1, 2, 2, settings, fontDirectory,
                new String[] { "Dialog" });
    }

    private void write(File file, byte[] content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content);
        }
    }

    private void mutate(File file, byte[] content, FileTime modifiedTime) {
        try {
            write(file, content);
            if (modifiedTime != null) {
                Files.setLastModifiedTime(file.toPath(), modifiedTime);
            }
        } catch (Exception exception) {
            throw new AssertionError("无法执行 snapshot 并发变化接缝", exception);
        }
    }
}
