package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerEnvironment;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 候选源版本号「推 / 拉」桥测试（ADR §2.4/§2.2，C7 对账点名的类）。
 *
 * <p>与 {@link PickerEnvironmentDownlinkTest}（Z-4 下行不变式的逐条判据）分工：本类只测
 * <b>桥自身</b>的四件事 ——</p>
 * <ol>
 *   <li><b>推</b>：环境代际只在真变化时下行（首帧推一次基线），Tier 代际<b>不下行</b>；</li>
 *   <li><b>拉</b>：三段版本「有变化才发布」，不推送则 {@code version()} / {@code versionSignal()}
 *       恒等（不重发、不重算）；</li>
 *   <li><b>合成</b>：{@code generation()} = 已发布三段 + 分级表当前代际（Tier 段只在本地合成）；</li>
 *   <li><b>帧驱动</b>：{@code bindTo(rt)} 后每帧推进帧时间信号即自动推/拉，无需外部 tick。</li>
 * </ol>
 *
 * <p>不变量「不推送则 version() 恒等」是 A-04/A-09 的反向判据：源不得自行轮询语言/资源状态。</p>
 */
public class PickerRevisionBridgeTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
    }

    /** 推：环境代际不变时（除首帧基线外）零推送；Tier 代际变化不产生任何推送。 */
    @Test
    public void environmentPushHappensOncePerRealChangeAndNeverCarriesTier() {
        FakeSource source = new FakeSource();
        long[] name = { 2L };
        long[] resource = { 5L };
        long[] tier = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(
                source, () -> name[0], () -> resource[0], () -> tier[0]);

        bridge.tick();
        Assert.assertEquals("首帧必须推一次基线（源与桥对上次摄入值有共同起点）", 1, bridge.pushCount());
        Assert.assertEquals(1, source.environments.size());
        Assert.assertEquals(new PickerEnvironment(2L, 5L), source.environments.get(0));

        for (int frame = 0; frame < 5; frame++) {
            bridge.tick();
        }
        Assert.assertEquals("环境代际不变不得重复推送", 1, bridge.pushCount());

        tier[0] = 99L;
        bridge.tick();
        Assert.assertEquals("分级代际只在本地合成，绝不下行（源不感知分级表）", 1, bridge.pushCount());
        Assert.assertEquals("generation() 的 Tier 段取分级表当前代际",
                99L, bridge.generation().tier());

        resource[0] = 6L;
        bridge.tick();
        bridge.tick();
        Assert.assertEquals("资源代际变化恰好一次推送", 2, bridge.pushCount());
        Assert.assertEquals(new PickerEnvironment(2L, 6L), source.environments.get(1));
    }

    /** 拉：三段版本有真变化才发布；无变化时 version() 与 versionSignal() 恒等（不重发）。 */
    @Test
    public void pullPublishesOnlyOnRealVersionChangeAndSignalStaysIdentical() {
        FakeSource source = new FakeSource();
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> 0L, () -> 0L, () -> 0L);
        List<PickerSourceVersion> emissions = new ArrayList<PickerSourceVersion>();
        ReadableSignal<PickerSourceVersion> signal = bridge.versionSignal();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        rt.bind(signal, emissions::add);
        rt.flush();

        bridge.tick();
        rt.flush();
        Assert.assertEquals("首帧发布一次源真实版本", 1, bridge.publishCount());
        Assert.assertEquals(new PickerSourceVersion(0L, 0L, 0L), bridge.version());
        Assert.assertEquals("发布经信号对外可见", 1, emissions.size());

        for (int frame = 0; frame < 4; frame++) {
            bridge.tick();
        }
        rt.flush();
        Assert.assertEquals("版本未变不得重复发布", 1, bridge.publishCount());
        Assert.assertEquals("版本未变不得重发信号", 1, emissions.size());
        Assert.assertEquals("不推送则 version() 恒等", new PickerSourceVersion(0L, 0L, 0L), bridge.version());

        source.registry = 3L;
        source.name = 1L;
        source.icon = 2L;
        bridge.tick();
        bridge.tick();
        rt.flush();
        Assert.assertEquals("三段变化发布恰好一次", 2, bridge.publishCount());
        Assert.assertEquals(new PickerSourceVersion(3L, 1L, 2L), bridge.version());
        Assert.assertEquals(new PickerSourceVersion(3L, 1L, 2L), emissions.get(emissions.size() - 1));
        Assert.assertEquals("已发布三段与 generation() 同源", new PickerSourceVersion(3L, 1L, 2L),
                new PickerSourceVersion(bridge.generation().registry(), bridge.generation().name(),
                        bridge.generation().icon()));

        rt.dispose();
    }

    /** 帧驱动：bindTo(rt) 后推进帧时间信号即自动推/拉（宿主无需显式 tick）。 */
    @Test
    public void boundBridgeTicksOnHostFrameSignal() {
        FakeSource source = new FakeSource();
        long[] name = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(source, () -> name[0], () -> 0L, () -> 0L);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        bridge.bindTo(rt);

        rt.__tickFrame(1_000L);
        rt.flush();
        Assert.assertEquals("首帧由帧信号驱动推基线", 1, bridge.pushCount());
        Assert.assertEquals("首帧由帧信号驱动发布真实版本", 1, bridge.publishCount());

        name[0] = 4L;
        rt.__tickFrame(2_000L);
        rt.flush();
        Assert.assertEquals("帧推进即拉到新语言代际", 2, bridge.pushCount());
        Assert.assertTrue("源按推送自增 name 段", source.name >= 1L);
        Assert.assertEquals(new PickerSourceVersion(0L, source.name, 0L), bridge.version());

        rt.__tickFrame(3_000L);
        rt.flush();
        Assert.assertEquals("无变化帧不得重复推送", 2, bridge.pushCount());
        rt.dispose();
    }

    /**
     * 「不推送则 version() 恒等」的反向判据：桥未 tick 时，环境代际变化与分级代际变化都不得
     * 让源的三段版本发生变化（源不轮询、不自注册 listener）。
     */
    @Test
    public void versionStaysIdenticalUntilBridgePushes() {
        FakeSource source = new FakeSource();
        long[] name = { 1L };
        long[] tier = { 0L };
        PickerRevisionBridge bridge = new PickerRevisionBridge(
                source, () -> name[0], () -> 0L, () -> tier[0]);
        Assert.assertEquals("未 tick 时零下行推送", 0, source.environments.size());
        Assert.assertEquals("未 tick 时零发布", 0, bridge.publishCount());

        bridge.tick();
        PickerSourceVersion baseline = source.version();
        Assert.assertEquals("基线推送恰好一次", 1, source.environments.size());
        Assert.assertEquals("基线推送不改变源的三段版本", new PickerSourceVersion(0L, 0L, 0L), baseline);

        name[0] = 7L;
        tier[0] = 5L;
        Assert.assertEquals("桥不 tick 时源版本恒等（源不轮询语言/资源状态）", baseline, source.version());
        Assert.assertEquals("桥不 tick 时无任何下行推送", 1, source.environments.size());

        bridge.tick();
        Assert.assertNotEquals("推送后 name 段才随环境代际变化", baseline, source.version());
        Assert.assertEquals(2, source.environments.size());
        Assert.assertEquals("分级代际绝不下行：只改本地 generation()", 0L, source.version().icon());
        Assert.assertEquals(5L, bridge.generation().tier());
    }

    /** 内存候选源替身：三段版本自持快照，环境推送只改自持快照（模拟 Miner 生产形态）。 */
    private static final class FakeSource implements PickerCandidateSource {
        private final List<PickerEnvironment> environments = new ArrayList<PickerEnvironment>();
        private long registry;
        private long name;
        private long icon;
        private long lastPushedName = Long.MIN_VALUE;
        private long lastPushedResource = Long.MIN_VALUE;
        private boolean baselineTaken;

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
        public PickerSourceVersion version() {
            return new PickerSourceVersion(registry, name, icon);
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

        @Override
        public void onEnvironmentChanged(long nameEpoch, long resourceEpoch) {
            environments.add(new PickerEnvironment(nameEpoch, resourceEpoch));
            if (!baselineTaken) {
                // 基线推送只登记「上次摄入值」（与生产契约一致：起点不改变源的三段版本）。
                baselineTaken = true;
                lastPushedName = nameEpoch;
                lastPushedResource = resourceEpoch;
                return;
            }
            if (nameEpoch != lastPushedName) {
                lastPushedName = nameEpoch;
                name++;
            }
            if (resourceEpoch != lastPushedResource) {
                lastPushedResource = resourceEpoch;
                icon++;
            }
        }
    }
}
