package club.heiqi.config.ui;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.SectionSpec;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 用真实字段控件证明屏外配置数量不再扩大每帧重放计划。 */
public class ConfigScreenOffscreenPaintTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void moreOffscreenFieldsDoNotIncreaseReplayWork() throws Exception {
        PaintPlan small = renderFields(20);
        PaintPlan large = renderFields(200);
        Assert.assertTrue(small.size() > 0);
        Assert.assertEquals(small.size(), large.size());
    }

    private PaintPlan renderFields(int count) throws Exception {
        ReactiveScheduler.get().reset();
        ConfigSchema.Builder builder = ConfigSchema.builder("performance");
        SectionSpec.Builder section = builder.section("settings").title("Settings");
        for (int i = 0; i < count; i++) {
            section.bool("field" + i).defaultValue(false).label("Field " + i).build();
        }
        section.endSection();
        ConfigManager manager = ConfigManager.bootstrap(temp.newFile(), builder.build());
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, manager.openDraft());
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, FieldRendererRegistry.defaultRegistry());
        try {
            screen.__doFrameForTest(520, 300);
            screen.__getRuntime().__finishMotionForTest();
            PaintPlan plan = screen.getPaintEngine().paint(screen.__getRoot()).getPlan();
            System.out.println("config fields=" + count + ", replay commands=" + plan.size());
            return plan;
        } finally {
            screen.dispose();
            adapter.dispose();
            ReactiveScheduler.get().reset();
        }
    }
}
