package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * Z-4 下行不变式测试：环境代际<b>只经</b> {@code onEnvironmentChanged} 下行；不推送则 {@code version()} 恒等。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.2/§2.4（A2 禁 signal 进 SPI、A3 UILib 拉版本号、
 * A9 源不自注册 listener）。</p>
 */
public class PickerEnvironmentDownlinkTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 环境代际未变：只推基线一次、只发布一次（桥每帧只做常数次比对）。 */
    @Test
    public void unchangedEnvironmentPushesNothing() {
        FakeSource source = new FakeSource();
        long[] name = { 3L };
        long[] resource = { 7L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> name[0], () -> resource[0], () -> 0L);

        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("首次 tick 推一次基线", 1, bridge.pushCount());
        Assert.assertEquals("首帧必须发布源的真实版本", 1, bridge.publishCount());
        Assert.assertEquals("基线推送不 +1：revision 是「变化计数」而非 epoch 镜像",
                new PickerSourceVersion(0L, 0L, 0L), source.version());

        bridge.tick();
        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("环境代际未变不得重复推送", 1, bridge.pushCount());
        Assert.assertEquals("版本未变不得重复发布", 1, bridge.publishCount());
    }

    /** 语言代际变化（UILib 侧 nameEpoch++）：恰好一次推送，源只让 name 段自增。 */
    @Test
    public void languageEpochChangePushesOnceAndBumpsNameSegment() {
        FakeSource source = new FakeSource();
        long[] name = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> name[0], () -> 0L, () -> 0L);
        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(new PickerSourceVersion(0L, 0L, 0L), source.version());

        name[0] = 1L;
        bridge.tick();
        bridge.tick();
        ReactiveScheduler.get().flush();

        Assert.assertEquals("语言代际变化 = 恰好一次推送", 2, bridge.pushCount());
        Assert.assertEquals("源只让 name 段自增（清单与图标不动）", new PickerSourceVersion(0L, 1L, 0L), source.version());
        Assert.assertEquals(new PickerSourceVersion(0L, 1L, 0L), bridge.version());
    }

    /** 资源代际变化：源只让 icon 段自增（不重建候选结构），桥发布新版本。 */
    @Test
    public void resourceEpochChangeBumpsIconSegmentOnly() {
        FakeSource source = new FakeSource();
        long[] resource = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> 0L, () -> resource[0], () -> 0L);
        bridge.tick();
        ReactiveScheduler.get().flush();

        resource[0] = 4L;
        bridge.tick();
        ReactiveScheduler.get().flush();

        Assert.assertEquals(new PickerSourceVersion(0L, 0L, 1L), source.version());
        Assert.assertEquals(2, bridge.publishCount());
        Assert.assertEquals("清单段不得因资源重载变化（不重建候选）", 0L, source.version().registry());
    }

    /** Z-4 反向判据：桥不推送时源的三段版本不可能变化（源不轮询语言/资源状态）。 */
    @Test
    public void versionIsIdentityWhenNotPushed() {
        FakeSource source = new FakeSource();
        final long[] name = { 5L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> name[0], () -> 0L, () -> 0L);
        bridge.tick();
        ReactiveScheduler.get().flush();
        PickerSourceVersion afterBaseline = source.version();

        name[0] = 6L;
        Assert.assertEquals("不推送则版本恒等（源不得自行轮询）", afterBaseline, source.version());

        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(new PickerSourceVersion(0L, 1L, 0L), source.version());
    }

    /** 四段合成：tier 段读分级表代际，且永不下行到源。 */
    @Test
    public void generationComposesTierSegmentWithoutDownlink() {
        FakeSource source = new FakeSource();
        long[] tier = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> 2L, () -> 3L, () -> tier[0]);
        bridge.tick();
        ReactiveScheduler.get().flush();

        Assert.assertEquals("首帧基线推送不 +1，故已发布段全零 + tier 段",
                new PickerGeneration(0L, 0L, 0L, 0L), bridge.generation());
        tier[0] = 9L;
        Assert.assertEquals("tier 段是 UILib 侧合成量，不经源推送",
                new PickerGeneration(0L, 0L, 0L, 9L), bridge.generation());
        Assert.assertEquals("源的三段版本不受 tier 影响", new PickerSourceVersion(0L, 0L, 0L), source.version());
    }

    /** 版本信号只在变化时 set：不变化不产生发布（无逐帧 signal 写入）。 */
    @Test
    public void versionSignalPublishesOnlyOnChange() {
        FakeSource source = new FakeSource();
        final long[] name = { 0L };
        final PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> name[0], () -> 0L, () -> 0L);
        final List<PickerSourceVersion> seen = new ArrayList<PickerSourceVersion>();
        Computed.create(() -> {
            seen.add(bridge.versionSignal().get());
            return Boolean.TRUE;
        }).get();

        bridge.tick();
        ReactiveScheduler.get().flush();
        int afterFirst = seen.size();
        Assert.assertTrue("首帧必须发布源的真实版本", afterFirst >= 1);

        bridge.tick();
        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("无变化不得产生新发布", afterFirst, seen.size());

        name[0] = 1L;
        bridge.tick();
        ReactiveScheduler.get().flush();
        Assert.assertTrue("变化必须发布", seen.size() > afterFirst);
        Assert.assertEquals(new PickerSourceVersion(0L, 1L, 0L), seen.get(seen.size() - 1));
    }

    /** 最小候选源替身：三段版本号只由 onEnvironmentChanged 与显式清单变化驱动（复刻 Miner 语义）。 */
    private static final class FakeSource implements PickerCandidateSource {
        private long registry;
        private long name;
        private long icon;
        private long lastPushedName = Long.MIN_VALUE;
        private long lastPushedResource = Long.MIN_VALUE;

        @Override
        public void onEnvironmentChanged(long nameEpoch, long resourceEpoch) {
            if (lastPushedName != Long.MIN_VALUE && nameEpoch != lastPushedName) {
                name++;
            }
            if (lastPushedResource != Long.MIN_VALUE && resourceEpoch != lastPushedResource) {
                icon++;
            }
            lastPushedName = nameEpoch;
            lastPushedResource = resourceEpoch;
        }

        @Override
        public PickerSourceVersion version() {
            return new PickerSourceVersion(registry, name, icon);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public long registryRevision() {
            return registry;
        }

        @Override
        public long nameRevision() {
            return name;
        }

        @Override
        public long iconRevision() {
            return icon;
        }

        @Override
        public int matchCount(PickerQuery query) {
            return 0;
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            return Collections.emptyList();
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            return null;
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            return Collections.emptyList();
        }
    }
}
