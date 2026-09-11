package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * HUD 布局与缩放的锚点百分比持久化契约（task-11）。
 *
 * <p>覆盖：文本 round-trip（位置 + 缩放同记录）、视口变化按百分比跟随、提交自动锚点、
 * 行程分母与 {@link HudLayoutResolver#clamp} 可行区间同源、行程为 0 的退化、
 * 损坏/未知 schemaVersion 安全降级且禁写回、仅缩放记录、缩放变更合并写盘、
 * 未接线零变化。规格见工作站 `temp/hud-persist-spec.md`。</p>
 */
public class HudLayoutPersistenceTest {

    private static final String ID = "qzuilib:persist";
    private static final String ID2 = "qzuilib:persist-two";

    private final HudLayoutService service = HudLayoutService.getInstance();
    private final HudToolbarService toolbarService = HudToolbarService.getInstance();

    @Before
    public void setUp() {
        service.detachStore();
        service.clear();
        toolbarService.clear();
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        service.detachStore();
        service.clear();
        toolbarService.clear();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void roundTripPersistsAnchorFractionsAndScale() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));

        HudPlacement committed = commitDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 400, 300));

        // 提交时按窗口中心自动改锚：解析盒 (400,200,200,100)，中心 (500,250) 恰在安全区中线 → LEFT/TOP 平局取 TOP_LEFT
        Assert.assertEquals(HudAnchor.TOP_LEFT, committed.getAnchor());
        assertRect(new AnchorRect(400, 200, 200, 100), resolve(committed, 1000, 600, 200, 100));
        Assert.assertEquals("提交即写回一次", 1, store.getSaves());
        Assert.assertTrue("含版本行", store.getText().contains("schemaVersion=1"));
        Assert.assertTrue("只落锚点与百分比", store.getText().contains(
                ID + "\tTOP_LEFT\t0.5\t0.4\t100"));

        // 模拟重启：同一份文本重新加载，位置与缩放逐位一致
        service.detachStore();
        service.clear();
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(committed, service.placement(ID));
    }

    @Test
    public void viewportChangeFollowsSavedFractions() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));
        HudPlacement original = commitDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 400, 300));
        // 盒 (400,200)：TOP_LEFT 下 fraction = (400/800, 200/500) = (0.5, 0.4)
        int spanX = HudLayoutResolver.travelSpan(600, 200, 0, 0);
        int spanY = HudLayoutResolver.travelSpan(400, 100, 0, 0);

        service.observe(ID, metrics(600, 400, 200, 100));
        HudPlacement followed = service.placement(ID);
        Assert.assertEquals(HudAnchor.TOP_LEFT, followed.getAnchor());
        Assert.assertEquals((int) Math.round(0.5 * spanX), followed.getOffsetX());
        Assert.assertEquals((int) Math.round(0.4 * spanY), followed.getOffsetY());
        AnchorRect box = resolve(followed, 600, 400, 200, 100);
        assertRect(new AnchorRect(followed.getOffsetX(), followed.getOffsetY(), 200, 100), box);
        Assert.assertTrue("跟随后的盒不越界", box.getX() >= 0 && box.getBottom() <= 400);

        // 视口回到原尺寸：按同一 fraction 精确还原（百分比是权威，不是上次偏移的插值）
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(original, service.placement(ID));

        // 内容变化（HUD 缩放）同样按行程百分比跟随：内容 100x50 → 行程 (900,550)
        service.observe(ID, metrics(1000, 600, 100, 50));
        HudPlacement withSmallerContent = service.placement(ID);
        Assert.assertEquals((int) Math.round(0.5 * HudLayoutResolver.travelSpan(1000, 100, 0, 0)),
                withSmallerContent.getOffsetX());
        Assert.assertEquals((int) Math.round(0.4 * HudLayoutResolver.travelSpan(600, 50, 0, 0)),
                withSmallerContent.getOffsetY());
    }

    @Test
    public void commitSelectsNearestCornerAnchorWithoutMovingBox() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));

        for (HudAnchor anchor : HudAnchor.values()) {
            HudPlacement drafted = HudPlacement.of(anchor, 0, 0);
            AnchorRect before = resolve(drafted, 1000, 600, 200, 100);
            HudPlacement committed = commitDraft(ID, drafted);
            Assert.assertEquals("锚点跟随窗口所在象限", anchor, committed.getAnchor());
            assertRect(before, resolve(committed, 1000, 600, 200, 100));
        }

        // 平局：中心恰在安全区中线 → LEFT/TOP
        HudPlacement tied = commitDraft(ID, HudPlacement.of(HudAnchor.TOP_LEFT, 400, 250));
        Assert.assertEquals(HudAnchor.TOP_LEFT, tied.getAnchor());
        assertRect(new AnchorRect(400, 250, 200, 100), resolve(tied, 1000, 600, 200, 100));
    }

    @Test
    public void oversizedContentCollapsesToZeroTravelSpan() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        Assert.assertEquals(0, HudLayoutResolver.travelSpan(100, 400, 0, 0));

        service.observe(ID, metrics(100, 100, 400, 400));
        HudPlacement committed = commitDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_RIGHT, 50, 50));

        Assert.assertEquals(0, committed.getOffsetX());
        Assert.assertEquals(0, committed.getOffsetY());
        Assert.assertTrue(store.getText().contains("\t0.0\t0.0\t"));
        // 视口继续缩小也不抛异常、偏移保持 0
        service.observe(ID, metrics(10, 10, 400, 400));
        Assert.assertEquals(HudPlacement.of(committed.getAnchor(), 0, 0), service.placement(ID));
    }

    @Test
    public void corruptOrUnknownVersionDegradesToDefaultAndStopsWriteBack() {
        RecordingStore corrupt = new RecordingStore("this is not a hud layout");
        service.attachStore(corrupt);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertNull("无法识别的数据不得产生覆盖", service.placement(ID));
        Assert.assertFalse(service.hasCommittedOverride(ID));
        toolbarService.scale(ID).setPercent(150);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals("禁写回：不覆写无法识别的数据", 0, corrupt.getSaves());

        RecordingStore future = new RecordingStore("qz_uilib_hud_layout\nschemaVersion=2\n"
                + ID + "\tTOP_LEFT\t0.5\t0.5\t100\n");
        service.detachStore();
        service.clear();
        service.attachStore(future);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertNull("未知 schemaVersion 降级为默认布局", service.placement(ID));
        toolbarService.scale(ID).setPercent(160);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals("未知版本数据禁止降级覆写", 0, future.getSaves());
    }

    @Test
    public void loadFailureDegradesToDefaultWithoutThrowOrWriteBack() {
        RecordingStore store = new RecordingStore("");
        store.setFailLoad(true);
        service.attachStore(store);
        Assert.assertTrue(service.hasStore());
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertNull(service.placement(ID));
        toolbarService.scale(ID).setPercent(150);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(0, store.getSaves());
    }

    @Test
    public void malformedRecordIsSkippedWithoutDroppingOtherRecords() {
        RecordingStore store = new RecordingStore("qz_uilib_hud_layout\n"
                + "schemaVersion=1\n"
                + "broken line without separators\n"
                + ID + "\tBOTTOM_RIGHT\t0.25\t0.5\t150\n"
                + ID2 + "\t-\t0\t0\t90\n"
                + ID + "\tTOP_LEFT\t0.9\t0.9\t999\n");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));

        // 行程 (800,500)：fraction (0.25,0.5) → 偏移 (200,250)；重复 hudId 首条生效（150 而非 999）
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_RIGHT, 200, 250), service.placement(ID));
        Assert.assertEquals(150, toolbarService.scale(ID).percent().get().intValue());
        Assert.assertNull("仅缩放记录不制造位置覆盖", service.placement(ID2));
        Assert.assertEquals(90, toolbarService.scale(ID2).percent().get().intValue());

        // 有效加载后可写回：缩放变更合并为一次 save，其它 hudId 的有效记录不丢
        toolbarService.scale(ID).setPercent(160);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(1, store.getSaves());
        Assert.assertTrue(store.getText().contains(ID + "\tBOTTOM_RIGHT\t0.25\t0.5\t160"));
        Assert.assertTrue(store.getText().contains(ID2 + "\t-\t0.0\t0.0\t90"));
    }

    @Test
    public void scaleOnlyRecordPersistsScaleWithoutPositionOverride() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));
        toolbarService.scale(ID).setPercent(150);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(1, store.getSaves());
        Assert.assertTrue(store.getText().contains(ID + "\t-\t0.0\t0.0\t150"));

        service.detachStore();
        service.clear();
        toolbarService.clear();
        Assert.assertEquals(100, toolbarService.scale(ID).percent().get().intValue());
        service.attachStore(store);
        Assert.assertEquals("重载恢复缩放", 150, toolbarService.scale(ID).percent().get().intValue());
        Assert.assertNull("仅缩放记录不产生位置覆盖", service.placement(ID));
        Assert.assertFalse(service.hasCommittedOverride(ID));
    }

    @Test
    public void scaleChangeIsCoalescedIntoOneWritePerChange() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(0, store.getSaves());

        toolbarService.scale(ID).setPercent(150);
        int before = store.getSaves();
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals("变更后首帧写一次", before + 1, store.getSaves());
        service.observe(ID, metrics(1000, 600, 200, 100));
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals("同一变更不重复写盘", before + 1, store.getSaves());

        toolbarService.scale(ID).setPercent(160);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals(before + 2, store.getSaves());
        Assert.assertTrue(store.getText().contains("\t160"));
    }

    @Test
    public void observationAloneCreatesNeitherOverrideNorWrite() {
        RecordingStore store = new RecordingStore("");
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));
        service.observe(ID, metrics(800, 400, 100, 50));
        Assert.assertNull(service.placement(ID));
        Assert.assertFalse(service.hasCommittedOverride(ID));
        Assert.assertEquals(0, store.getSaves());
        Assert.assertEquals(1, store.getLoads());
    }

    @Test
    public void saveFailureKeepsMemoryStateAndDoesNotThrow() {
        RecordingStore store = new RecordingStore("");
        store.setFailSave(true);
        service.attachStore(store);
        service.observe(ID, metrics(1000, 600, 200, 100));

        HudPlacement committed = commitDraft(ID, HudPlacement.of(HudAnchor.TOP_LEFT, 100, 80));
        Assert.assertEquals(HudPlacement.of(HudAnchor.TOP_LEFT, 100, 80), committed);
        Assert.assertEquals(1, store.getSaves());

        toolbarService.scale(ID).setPercent(120);
        service.observe(ID, metrics(1000, 600, 200, 100));
        Assert.assertEquals("后续变更仍尝试写回（不进入重试风暴）", 2, store.getSaves());
    }

    @Test
    public void unwiredServiceKeepsLegacyBehavior() {
        Assert.assertFalse(service.hasStore());
        service.observe(ID, metrics(1000, 600, 200, 100));
        HudPlacement committed = commitDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 40, 30));
        Assert.assertEquals("未接线不做自动改锚", HudPlacement.of(HudAnchor.BOTTOM_LEFT, 40, 30), committed);
        service.commit(ID2, HudPlacement.of(HudAnchor.TOP_RIGHT, 7, 9));
        Assert.assertEquals(HudPlacement.of(HudAnchor.TOP_RIGHT, 7, 9), service.placement(ID2));
        service.reset(ID);
        Assert.assertNull(service.placement(ID));
    }

    @Test
    public void travelSpanEqualsClampFeasibleUpperBound() {
        HudPlacement huge = HudPlacement.of(HudAnchor.TOP_LEFT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        for (int viewport = 0; viewport <= 40; viewport++) {
            for (int content = 0; content <= 40; content++) {
                for (int leading : new int[] { 0, 3, 40 }) {
                    for (int trailing : new int[] { 0, 7 }) {
                        HudInsets insets = new HudInsets(leading, 0, trailing, 0);
                        int feasible = HudLayoutResolver.clamp(huge, viewport, 100, content, 100, insets)
                                .getOffsetX();
                        Assert.assertEquals("viewport=" + viewport + " content=" + content
                                + " insets=" + leading + "/" + trailing,
                                feasible, HudLayoutResolver.travelSpan(viewport, content, leading, trailing));
                    }
                }
            }
        }
    }

    @Test
    public void textCodecRoundTripAndDegradation() {
        HudLayoutData data = HudLayoutData.builder()
                .put(HudLayoutPreference.of(ID, HudAnchor.BOTTOM_RIGHT, 1.0 / 3.0, 0.25, 150))
                .put(HudLayoutPreference.scaleOnly(ID2, 90))
                .build();
        HudLayoutData parsed = HudLayoutData.parse(data.toText());
        Assert.assertNotNull(parsed);
        Assert.assertEquals(HudLayoutData.SCHEMA_VERSION, parsed.getSchemaVersion());
        Assert.assertEquals(2, parsed.size());
        HudLayoutPreference first = parsed.getEntries().get(ID);
        Assert.assertEquals(HudAnchor.BOTTOM_RIGHT, first.getAnchor());
        Assert.assertEquals(1.0 / 3.0, first.getFractionX(), 0.0);
        Assert.assertEquals(0.25, first.getFractionY(), 0.0);
        Assert.assertEquals(150, first.getScalePercent());
        Assert.assertFalse(parsed.getEntries().get(ID2).hasPlacement());
        Assert.assertEquals(90, parsed.getEntries().get(ID2).getScalePercent());

        Assert.assertNull("空文本不可识别", HudLayoutData.parse(""));
        Assert.assertNull("无魔数不可识别", HudLayoutData.parse("schemaVersion=1\n"));
        Assert.assertNull("未知版本整体不可用", HudLayoutData.parse("qz_uilib_hud_layout\nschemaVersion=2\n"));

        HudLayoutData partial = HudLayoutData.parse("qz_uilib_hud_layout\nschemaVersion=1\njunk\n"
                + ID + "\tNOT_AN_ANCHOR\t0.1\t0.2\t100\n"
                + ID2 + "\tNaN\t0.1\t0.2\t100\n"
                + "third:hud\tTOP_LEFT\t2.0\t-1.0\t9999\n");
        Assert.assertNotNull(partial);
        Assert.assertEquals(1, partial.size());
        HudLayoutPreference clamped = partial.getEntries().get("third:hud");
        Assert.assertEquals(1.0, clamped.getFractionX(), 0.0);
        Assert.assertEquals(0.0, clamped.getFractionY(), 0.0);
        Assert.assertEquals(HudScaleState.MAX_PERCENT, clamped.getScalePercent());
    }

    /** AnchorRect 无 equals：逐项比较（值语义断言）。 */
    private static void assertRect(AnchorRect expected, AnchorRect actual) {
        String detail = "expected=" + describe(expected) + " actual=" + describe(actual);
        Assert.assertEquals(detail, expected.getX(), actual.getX());
        Assert.assertEquals(detail, expected.getY(), actual.getY());
        Assert.assertEquals(detail, expected.getWidth(), actual.getWidth());
        Assert.assertEquals(detail, expected.getHeight(), actual.getHeight());
    }

    private static String describe(AnchorRect rect) {
        return "(" + rect.getX() + "," + rect.getY() + "," + rect.getWidth() + "x" + rect.getHeight() + ")";
    }

    private HudPlacement commitDraft(String hudId, HudPlacement placement) {
        service.beginEdit();
        service.setDraft(hudId, placement);
        service.commitEdit();
        return service.placement(hudId);
    }

    private static HudLayoutMetrics metrics(int viewportWidth, int viewportHeight, int contentWidth,
            int contentHeight) {
        return HudLayoutMetrics.of(viewportWidth, viewportHeight, contentWidth, contentHeight, HudInsets.NONE);
    }

    private static AnchorRect resolve(HudPlacement placement, int viewportWidth, int viewportHeight,
            int contentWidth, int contentHeight) {
        return HudLayoutResolver.resolve(placement, viewportWidth, viewportHeight, contentWidth, contentHeight,
                HudInsets.NONE);
    }

    /** 记录式内存 store：模拟宿主「读原文 + 原子写原文」，并可注入读写失败。 */
    private static final class RecordingStore implements HudLayoutStore {
        private String text;
        private int loads;
        private int saves;
        private boolean failLoad;
        private boolean failSave;

        RecordingStore(String text) {
            this.text = text;
        }

        @Override
        public String load() {
            loads++;
            if (failLoad) {
                throw new IllegalStateException("simulated load failure");
            }
            return text;
        }

        @Override
        public void save(String value) {
            saves++;
            if (failSave) {
                throw new IllegalStateException("simulated save failure");
            }
            text = value;
        }

        String getText() { return text; }
        int getLoads() { return loads; }
        int getSaves() { return saves; }
        void setFailLoad(boolean value) { failLoad = value; }
        void setFailSave(boolean value) { failSave = value; }
    }
}
